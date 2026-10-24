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
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.ParameterDefinition;
import org.rulii.validation.beans.model.ReturnValueDefinition;
import org.rulii.validation.beans.traversal.ContainerWalker;
import org.rulii.validation.beans.traversal.Traversal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

/**
 * The spec's {@link ExecutableValidator} of a {@link BeanValidator}: validates the arguments or the
 * return value of a method or constructor against the constraints declared on it, cascading into
 * {@code @Valid} arguments and return values and walking their container elements, exactly as the
 * bean validation does for properties. Frameworks that intercept method calls ({@code @Validated}
 * services, for instance) drive it through {@link BeanValidator#forExecutables()}.
 * <p>
 * The executable is looked up by signature on the runtime class of the object (the declaring class
 * for a constructor), so a method handed over from an interface or a superclass finds the definition
 * of the overriding chain. An executable with no constraints yields an empty result. Paths start at
 * the method or constructor node and continue with the parameter, return-value or cross-parameter
 * node, as the reference implementation renders them ({@code placeOrder.arg0},
 * {@code placeOrder.<return value>}, {@code placeOrder.<cross-parameter>}, {@code OrderService.arg0}).
 * Results are {@link BeanValidationResult}s whose violations expose the arguments or the return value
 * validated.
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
        BeanRuleViolations violations = new BeanRuleViolations(object, object.getClass(), parameterValues, null);
        ExecutableRules rules = rules(object.getClass(), method, parameterValues);

        if (rules != null) {
            parameters(rules, object, parameterValues, PropertyPath.root().method(method.getName(), method.getParameterTypes()),
                    violations, RequestedGroups.of(groups));
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
        BeanRuleViolations violations = new BeanRuleViolations(object, object.getClass(), null, returnValue);
        ExecutableRules rules = validator.getExecutableRules(object.getClass(), method);

        if (rules != null && rules.getDefinition().getReturnValue() != null) {
            returnValue(rules, object, returnValue, PropertyPath.root().method(method.getName(), method.getParameterTypes()),
                    violations, RequestedGroups.of(groups));
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
        BeanRuleViolations violations = new BeanRuleViolations(null, constructor.getDeclaringClass(), parameterValues, null);
        ExecutableRules rules = rules(constructor.getDeclaringClass(), constructor, parameterValues);

        if (rules != null) {
            parameters(rules, null, parameterValues, constructorPath(constructor), violations, RequestedGroups.of(groups));
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
        BeanRuleViolations violations = new BeanRuleViolations(createdObject, constructor.getDeclaringClass(), null, createdObject);
        ExecutableRules rules = validator.getExecutableRules(constructor.getDeclaringClass(), constructor);

        if (rules != null) {
            returnValue(rules, createdObject, createdObject, constructorPath(constructor), violations, RequestedGroups.of(groups));
        }

        return new BeanValidationResult<>(createdObject, violations);
    }

    private ExecutableRules rules(Class<?> beanClass, Executable executable, Object[] parameterValues) {
        Assert.notNull(parameterValues, "parameterValues cannot be null.");
        Assert.isTrue(parameterValues.length == executable.getParameterCount(), "[" + executable + "] takes "
                + executable.getParameterCount() + " arguments, " + parameterValues.length + " given.");
        return validator.getExecutableRules(beanClass, executable);
    }

    private static PropertyPath constructorPath(Constructor<?> constructor) {
        return PropertyPath.root().constructor(constructor.getDeclaringClass().getSimpleName(), constructor.getParameterTypes());
    }

    private void parameters(ExecutableRules rules, Object object, Object[] values, PropertyPath base, BeanRuleViolations violations,
                            RequestedGroups groups) {
        ScopedBindings bindings = bindings(object, violations, groups);
        rules.getBindingPlan().load(bindings, values);
        RuleContext context = validator.context(bindings);

        try (BeanRuleViolations.Frame ignored = violations.enter(base, rules, object)) {
            rules.parameterRuleSet(groups).run(context);
            ContainerWalker walker = new ContainerWalker(context, rules, true);

            for (ParameterDefinition parameter : rules.getDefinition().getParameters()) {
                if (parameter.getContainerElements().isEmpty() && !parameter.isCascaded()) continue;
                Object value = values[parameter.getIndex()];
                PropertyPath path = base.parameter(parameter.getName(), parameter.getIndex());

                for (ContainerElementDefinition node : parameter.getContainerElements()) {
                    walker.walk(value, node, path);
                }

                if (parameter.isCascaded()) walker.cascade(value, parameter.getGenericType(), path);
            }
        }
    }

    private void returnValue(ExecutableRules rules, Object object, Object value, PropertyPath base, BeanRuleViolations violations,
                             RequestedGroups groups) {
        ReturnValueDefinition returnValue = rules.getDefinition().getReturnValue();
        ScopedBindings bindings = bindings(object, violations, groups);
        bindings.bind(Binding.builder().with(BindingPlan.RETURN_VALUE, Object.class).value(value).editable(false).build());
        RuleContext context = validator.context(bindings);

        try (BeanRuleViolations.Frame ignored = violations.enter(base, rules, object)) {
            rules.returnValueRuleSet(groups).run(context);

            if (returnValue.getContainerElements().isEmpty() && !returnValue.isCascaded()) return;

            ContainerWalker walker = new ContainerWalker(context, rules, true);
            PropertyPath path = base.returnValue();

            for (ContainerElementDefinition node : returnValue.getContainerElements()) {
                walker.walk(value, node, path);
            }

            if (returnValue.isCascaded()) walker.cascade(value, returnValue.getGenericType(), path);
        }
    }

    private ScopedBindings bindings(Object object, BeanRuleViolations violations, RequestedGroups groups) {
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(Binding.builder().with(BindingPlan.THIS, Object.class).value(object).build());
        bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
        bindings.bind(Binding.builder().with(BindingPlan.TRAVERSAL, Traversal.class).value(validator.traversal(groups)).build());
        return bindings;
    }

    @Override
    public String toString() {
        return "ExecutableBeanValidator{" + validator + '}';
    }
}
