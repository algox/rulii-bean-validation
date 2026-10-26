/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rulii.validation.beans;

import jakarta.validation.executable.ExecutableValidator;
import org.rulii.bind.Binding;
import org.rulii.bind.Bindings;
import org.rulii.bind.NamedScope;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.group.GroupPlan;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.traversal.Traversal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * The spec's {@link ExecutableValidator} of a {@link BeanValidator}: validates the arguments or the
 * return value of a method or constructor against the constraints declared on it, cascading into
 * {@code @Valid} arguments and return values (with their {@code @ConvertGroup} conversions) and
 * walking their container elements, exactly as the bean validation does for properties. Frameworks
 * that intercept method calls ({@code @Validated} services, for instance) drive it through
 * {@link BeanValidator#forExecutables()}.
 * <p>
 * Each call runs the executable's flow ({@link BeanValidator#getParametersRuleFlow} or
 * {@link BeanValidator#getReturnValueRuleFlow}) once per group step, with the arguments bound by
 * parameter name, so the tracer sees method validation as it sees bean validation. The executable is
 * looked up by signature on the runtime class of the object (the declaring class for a constructor),
 * so a method handed over from an interface or a superclass finds the definition of the overriding
 * chain. An executable with nothing to validate yields an empty result. Groups are processed as for a
 * bean ({@link GroupPlan}): requested sequences member by member, and the class's redefined Default
 * group, if any, applies to its executables too. Paths start at the method or constructor node and
 * continue with the parameter, return-value or cross-parameter node, as the reference implementation
 * renders them ({@code placeOrder.arg0}, {@code placeOrder.<return value>},
 * {@code placeOrder.<cross-parameter>}, {@code OrderService.arg0}). Results are
 * {@link BeanValidationResult}s whose violations expose the arguments or the return value validated.
 * <p>
 * For rulii-native use, {@link #validateParametersIn} and {@link #validateReturnValueIn} run a
 * method's validation inside an existing {@link RuleContext}, as {@link BeanValidator#validateIn}
 * does for an object.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ExecutableBeanValidator implements ExecutableValidator {

    private final BeanValidator validator;

    ExecutableBeanValidator(BeanValidator validator) {
        super();
        Assert.notNull(validator, "validator cannot be null.");
        this.validator = validator;
    }

    /**
     * The validator this one belongs to.
     *
     * @return the bean validator.
     */
    public BeanValidator getBeanValidator() {
        return validator;
    }

    /**
     * Validates the arguments of a method call: parameter constraints, cross-parameter constraints,
     * container elements and cascades of the arguments.
     *
     * @param object the object the method is called on.
     * @param method the method.
     * @param parameterValues the arguments, one per parameter.
     * @param groups the requested groups.
     * @param <T> the type of the object.
     * @return the violations, also a {@link BeanValidationResult}; empty when the method is not constrained.
     * @throws IllegalArgumentException if the object, the method or the arguments are null, the argument
     *         count does not match, or a group is not an interface.
     */
    @Override
    public <T> BeanValidationResult<T> validateParameters(T object, Method method, Object[] parameterValues, Class<?>... groups) {
        Assert.notNull(object, "object cannot be null.");
        Assert.notNull(method, "method cannot be null.");
        BeanRuleViolations violations = validator.violations(object, object.getClass(), parameterValues, null);
        BeanValidator.ExecutableEntry entry = parameters(object.getClass(), method, parameterValues);

        if (entry != null) {
            run(entry.parametersFlow, object, violations, GroupPlan.of(groups),
                    bindings -> entry.rules.getBindingPlan().load(bindings, parameterValues));
        }

        return new BeanValidationResult<>(object, violations);
    }

    /**
     * Validates the return value of a method call: return-value constraints, container elements and
     * cascade of the value.
     *
     * @param object the object the method was called on.
     * @param method the method.
     * @param returnValue the value returned.
     * @param groups the requested groups.
     * @param <T> the type of the object.
     * @return the violations, also a {@link BeanValidationResult}; empty when the method is not constrained.
     * @throws IllegalArgumentException if the object or the method is null, or a group is not an interface.
     */
    @Override
    public <T> BeanValidationResult<T> validateReturnValue(T object, Method method, Object returnValue, Class<?>... groups) {
        Assert.notNull(object, "object cannot be null.");
        Assert.notNull(method, "method cannot be null.");
        BeanRuleViolations violations = validator.violations(object, object.getClass(), null, returnValue);
        BeanValidator.ExecutableEntry entry = returnValue(object.getClass(), method);

        if (entry != null) {
            run(entry.returnValueFlow, object, violations, GroupPlan.of(groups), bindings -> bindReturnValue(bindings, returnValue));
        }

        return new BeanValidationResult<>(object, violations);
    }

    /**
     * Validates the arguments of a constructor call. There is no object yet: the root bean of the
     * result is null, its class the constructed class.
     *
     * @param constructor the constructor.
     * @param parameterValues the arguments, one per parameter.
     * @param groups the requested groups.
     * @param <T> the constructed type.
     * @return the violations, also a {@link BeanValidationResult}; empty when the constructor is not constrained.
     * @throws IllegalArgumentException if the constructor or the arguments are null, the argument count
     *         does not match, or a group is not an interface.
     */
    @Override
    public <T> BeanValidationResult<T> validateConstructorParameters(Constructor<? extends T> constructor, Object[] parameterValues,
                                                                     Class<?>... groups) {
        Assert.notNull(constructor, "constructor cannot be null.");
        BeanRuleViolations violations = validator.violations(null, constructor.getDeclaringClass(), parameterValues, null);
        BeanValidator.ExecutableEntry entry = parameters(constructor.getDeclaringClass(), constructor, parameterValues);

        if (entry != null) {
            run(entry.parametersFlow, null, violations, GroupPlan.of(groups),
                    bindings -> entry.rules.getBindingPlan().load(bindings, parameterValues));
        }

        return new BeanValidationResult<>(null, violations);
    }

    /**
     * Validates the object a constructor created: the constructor's return-value constraints and
     * cascade. The created object is the root bean of the result.
     *
     * @param constructor the constructor.
     * @param createdObject the object it created.
     * @param groups the requested groups.
     * @param <T> the constructed type.
     * @return the violations, also a {@link BeanValidationResult}; empty when the constructor is not constrained.
     * @throws IllegalArgumentException if the constructor or the object is null, or a group is not an interface.
     */
    @Override
    public <T> BeanValidationResult<T> validateConstructorReturnValue(Constructor<? extends T> constructor, T createdObject,
                                                                      Class<?>... groups) {
        Assert.notNull(constructor, "constructor cannot be null.");
        Assert.notNull(createdObject, "createdObject cannot be null.");
        BeanRuleViolations violations = validator.violations(createdObject, constructor.getDeclaringClass(), null, createdObject);
        BeanValidator.ExecutableEntry entry = returnValue(constructor.getDeclaringClass(), constructor);

        if (entry != null) {
            run(entry.returnValueFlow, createdObject, violations, GroupPlan.of(groups), bindings -> bindReturnValue(bindings, createdObject));
        }

        return new BeanValidationResult<>(createdObject, violations);
    }

    /**
     * Validates the arguments of a method call inside an existing context, the way
     * {@link BeanValidator#validateIn} validates an object: the parameters flow runs in a scope of its
     * own over the context's bindings, so the rules see the arguments by parameter name alongside
     * whatever else is bound, and the violations go to the {@link RuleViolations} bound in the context
     * when there is one (path-stamped either way); otherwise a {@link BeanRuleViolations} is bound for
     * the run and returned.
     *
     * @param context the context to run in.
     * @param object the object the method is called on.
     * @param method the method.
     * @param parameterValues the arguments, one per parameter.
     * @param groups the requested groups.
     * @return the collector the violations went to.
     * @throws IllegalArgumentException if the context, the object, the method or the arguments are null,
     *         the argument count does not match, or a group is not an interface.
     */
    public RuleViolations validateParametersIn(RuleContext context, Object object, Method method, Object[] parameterValues,
                                               Class<?>... groups) {
        Assert.notNull(context, "context cannot be null.");
        Assert.notNull(object, "object cannot be null.");
        Assert.notNull(method, "method cannot be null.");
        BeanValidator.ExecutableEntry entry = parameters(object.getClass(), method, parameterValues);
        return runIn(context, object, entry != null ? entry.parametersFlow : null, RequestedGroups.of(groups), parameterValues, null,
                bindings -> entry.rules.getBindingPlan().load(bindings, parameterValues));
    }

    /**
     * Validates the return value of a method call inside an existing context, as
     * {@link #validateParametersIn} does for the arguments.
     *
     * @param context the context to run in.
     * @param object the object the method was called on.
     * @param method the method.
     * @param returnValue the value returned.
     * @param groups the requested groups.
     * @return the collector the violations went to.
     * @throws IllegalArgumentException if the context, the object or the method is null, or a group is
     *         not an interface.
     */
    public RuleViolations validateReturnValueIn(RuleContext context, Object object, Method method, Object returnValue, Class<?>... groups) {
        Assert.notNull(context, "context cannot be null.");
        Assert.notNull(object, "object cannot be null.");
        Assert.notNull(method, "method cannot be null.");
        BeanValidator.ExecutableEntry entry = returnValue(object.getClass(), method);
        return runIn(context, object, entry != null ? entry.returnValueFlow : null, RequestedGroups.of(groups), null, returnValue,
                bindings -> bindReturnValue(bindings, returnValue));
    }

    /**
     * The entry of an executable whose arguments are validated; null when there is nothing to validate.
     */
    private BeanValidator.ExecutableEntry parameters(Class<?> beanClass, Executable executable, Object[] parameterValues) {
        Assert.notNull(parameterValues, "parameterValues cannot be null.");
        Assert.isTrue(parameterValues.length == executable.getParameterCount(), "[" + executable + "] takes "
                + executable.getParameterCount() + " arguments, " + parameterValues.length + " given.");
        BeanValidator.ExecutableEntry entry = validator.executable(beanClass, executable);
        return entry != null && entry.parametersFlow != null ? entry : null;
    }

    /**
     * The entry of an executable whose return value is validated; null when there is nothing to validate.
     */
    private BeanValidator.ExecutableEntry returnValue(Class<?> beanClass, Executable executable) {
        BeanValidator.ExecutableEntry entry = validator.executable(beanClass, executable);
        return entry != null && entry.returnValueFlow != null ? entry : null;
    }

    private static void bindReturnValue(Bindings bindings, Object returnValue) {
        bindings.bind(Binding.builder().with(BindingPlan.RETURN_VALUE, Object.class).value(returnValue).editable(false).build());
    }

    /**
     * Runs a flow once per step of the plan, each step in fresh bindings: the object, the traversal and
     * its groups, the collector, then the inputs of the flow.
     */
    private void run(RuleFlow<?> flow, Object object, BeanRuleViolations violations, GroupPlan plan, Consumer<Bindings> inputs) {
        BeanValidator.run(plan, violations, step -> {
            ScopedBindings bindings = Bindings.builder().scoped();
            BeanValidator.bind(bindings, object, validator.traversal(step));
            bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
            inputs.accept(bindings);
            flow.run(validator.context(bindings));
        });
    }

    /**
     * Runs a flow in an existing context, in a scope of its own; a null flow validates nothing.
     */
    private RuleViolations runIn(RuleContext context, Object object, RuleFlow<?> flow, RequestedGroups requested, Object[] parameterValues,
                                 Object returnValue, Consumer<Bindings> inputs) {
        ScopedBindings bindings = context.getBindings();
        RuleViolations existing = Traversal.anyCollector(context);
        BeanRuleViolations own = existing == null ? validator.violations(object, object.getClass(), parameterValues, returnValue) : null;

        if (flow == null) return existing != null ? existing : own;

        NamedScope scope = bindings.addScope();

        try {
            BeanValidator.bind(scope.getBindings(), object, validator.traversal(requested));
            if (own != null) {
                scope.getBindings().bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class)
                        .value(own).primary(true).build());
            }
            inputs.accept(scope.getBindings());
            flow.run(context);
        } finally {
            bindings.removeScope(scope);
        }

        return existing != null ? existing : own;
    }

    @Override
    public String toString() {
        return "ExecutableBeanValidator{" + validator + '}';
    }
}
