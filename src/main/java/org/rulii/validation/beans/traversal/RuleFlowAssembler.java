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
package org.rulii.validation.beans.traversal;

import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.function.Functions;
import org.rulii.ruleflow.DefaultRuleFlowBuilder;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.ExecutableBindingPlan;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ParameterDefinition;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds the {@link RuleFlow}s of a bean class from its rules: the bean flow, the self-contained unit
 * that, given an object bound as {@code $this}, binds its properties, runs its rules and its
 * {@code @Validate} rules, and cascades into the objects it references; and, per constrained method
 * or constructor, the parameters flow and the return-value flow that do the same for the arguments
 * and the result of a call.
 *
 * <p>Parameters of the bean flow: {@code $this} (required, the object); {@code $root} (defaults to
 * {@code $this}); {@code ruleViolations} (defaults to a fresh {@link BeanRuleViolations} for the root);
 * {@code $traversal} (defaults to a fresh {@link Traversal} with the Default group); {@code $groups}
 * (defaults to the traversal's current groups, a live view). The flow's own scope is the object's
 * scope: the properties are bound in it by simple name and gone when the flow ends. Each object
 * descended into gets a scope of its own, nested in its parent's.</p>
 *
 * <p>The parameters flow declares one required input per parameter, under the parameter's binding
 * name ({@link ExecutableBindingPlan}), so a call is embedded by binding the arguments by name; an
 * input must be bound, its value may be null. {@code $this} is optional (the object the method is
 * called on; a constructor has none), {@code $root} defaults to it, and {@code $parameters}, the
 * arguments as a whole for cross-parameter rules, defaults to the array of the named inputs. The
 * return-value flow requires {@code $returnValue} and takes {@code $this} the same way. Both take
 * {@code ruleViolations}, {@code $traversal} and {@code $groups} as the bean flow does.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuleFlowAssembler {

    private static final String FLOW_SUFFIX = "ValidationFlow";
    private static final String PARAMETERS_SUFFIX = "ParametersFlow";
    private static final String RETURN_VALUE_SUFFIX = "ReturnValueFlow";

    private final Function<Class<?>, RuleFlow<?>> flows;
    private final ValueExtractorRegistry extractors;

    /**
     * Creates the assembler.
     *
     * @param flows the flow of a bean class by runtime type, for the traversal a flow creates when
     *              run on its own and for the descent into referenced objects.
     * @param extractors the value extractors, for the traversal a flow creates when run on its own.
     */
    public RuleFlowAssembler(Function<Class<?>, RuleFlow<?>> flows, ValueExtractorRegistry extractors) {
        super();
        Assert.notNull(flows, "flows cannot be null.");
        Assert.notNull(extractors, "extractors cannot be null.");
        this.flows = flows;
        this.extractors = extractors;
    }

    /**
     * Builds the flow of a bean class.
     *
     * @param rules the class's rules.
     * @return the flow, named {@code <simpleName>ValidationFlow} with a lower-case initial.
     */
    public RuleFlow<?> assemble(BeanRules rules) {
        Assert.notNull(rules, "rules cannot be null.");

        DefaultRuleFlowBuilder builder = RuleFlow.builder()
                .name(flowName(rules.getBeanClass()))
                .description("Validates a " + rules.getBeanClass().getName())
                .param(BindingPlan.THIS, Object.class, true)
                .param(BindingPlan.ROOT, Object.class, Functions.function((RuleContext ruleContext) ->
                        ruleContext.getBindings().getValue(BindingPlan.THIS)))
                .param(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, Functions.function((RuleContext ruleContext) ->
                        new BeanRuleViolations(ruleContext.getBindings().getValue(BindingPlan.ROOT))));

        return traversalParameters(builder)
                .command(new ValidateBeanCommand(rules, List.of(
                        new BindPropertiesCommand(rules),
                        new RunRulesCommand(rules),
                        new ValidateRulesCommand(rules),
                        new CascadeCommand(rules))))
                .build();
    }

    /**
     * Builds the parameters flow of a method or constructor: given the arguments bound by parameter
     * name, runs the parameter and cross-parameter rules, walks the container elements of the
     * arguments and cascades into the {@code @Valid} ones.
     *
     * @param owner the rules of the class the executable is defined on.
     * @param rules the executable's rules.
     * @return the flow, named by {@link #parametersFlowName}.
     */
    public RuleFlow<?> assembleParameters(BeanRules owner, ExecutableRules rules) {
        Assert.notNull(owner, "owner cannot be null.");
        Assert.notNull(rules, "rules cannot be null.");
        ExecutableDefinition definition = rules.getDefinition();
        ExecutableBindingPlan plan = rules.getBindingPlan();
        List<Class<?>> sequence = owner.getDefinition().getDefaultGroupSequence();
        ValidateExecutableCommand command = new ValidateExecutableCommand(rules, true, List.of(
                new RunParameterRulesCommand(rules, sequence),
                new CascadeParametersCommand(rules, sequence)));
        DefaultRuleFlowBuilder builder = RuleFlow.builder()
                .name(parametersFlowName(owner.getDefinition(), definition))
                .description("Validates the arguments of " + definition.getExecutable());

        for (ParameterDefinition parameter : definition.getParameters()) {
            builder.param(plan.getBindingName(parameter), parameter.getType(), true);
        }

        builder.param(BindingPlan.THIS, Object.class, false)
                .param(BindingPlan.ROOT, Object.class, Functions.function(ValidateExecutableCommand::bean))
                .param(BindingPlan.PARAMETERS, Object[].class, Functions.function((RuleContext ruleContext) ->
                        arguments(ruleContext, plan)))
                .param(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, Functions.function((RuleContext ruleContext) ->
                        command.collector(ruleContext)));

        return traversalParameters(builder).command(command).build();
    }

    /**
     * Builds the return-value flow of a method or constructor: given the value returned bound as
     * {@code $returnValue}, runs the return-value rules, walks its container elements and cascades
     * into it when it is {@code @Valid}.
     *
     * @param owner the rules of the class the executable is defined on.
     * @param rules the executable's rules; the executable must have a return value.
     * @return the flow, named by {@link #returnValueFlowName}.
     */
    public RuleFlow<?> assembleReturnValue(BeanRules owner, ExecutableRules rules) {
        Assert.notNull(owner, "owner cannot be null.");
        Assert.notNull(rules, "rules cannot be null.");
        ExecutableDefinition definition = rules.getDefinition();
        Assert.notNull(definition.getReturnValue(), "[" + definition.getExecutable() + "] has no return value.");
        List<Class<?>> sequence = owner.getDefinition().getDefaultGroupSequence();
        ValidateExecutableCommand command = new ValidateExecutableCommand(rules, false, List.of(
                new RunReturnValueRulesCommand(rules, sequence),
                new CascadeReturnValueCommand(rules, sequence)));
        DefaultRuleFlowBuilder builder = RuleFlow.builder()
                .name(returnValueFlowName(owner.getDefinition(), definition))
                .description("Validates the return value of " + definition.getExecutable())
                .param(BindingPlan.RETURN_VALUE, Object.class, true)
                .param(BindingPlan.THIS, Object.class, false)
                .param(BindingPlan.ROOT, Object.class, Functions.function(ValidateExecutableCommand::bean))
                .param(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, Functions.function((RuleContext ruleContext) ->
                        command.collector(ruleContext)));

        return traversalParameters(builder).command(command).build();
    }

    /**
     * Declares the traversal and its current groups, the inputs every flow takes last: the groups
     * default reads the traversal, so the traversal is declared first.
     */
    private DefaultRuleFlowBuilder traversalParameters(DefaultRuleFlowBuilder builder) {
        return builder
                .param(BindingPlan.TRAVERSAL, Traversal.class, Functions.function((RuleContext ruleContext) ->
                        new Traversal(RequestedGroups.DEFAULT, flows, extractors)))
                .param(BindingPlan.GROUPS, Set.class, Functions.function((RuleContext ruleContext) ->
                        Traversal.of(ruleContext).getCurrentGroups()));
    }

    /**
     * The arguments of an embedded call, gathered from the named inputs.
     */
    private static Object[] arguments(RuleContext ruleContext, ExecutableBindingPlan plan) {
        List<ParameterDefinition> parameters = plan.getExecutable().getParameters();
        Object[] result = new Object[parameters.size()];

        for (ParameterDefinition parameter : parameters) {
            result[parameter.getIndex()] = ruleContext.getBindings().getValue(plan.getBindingName(parameter));
        }

        return result;
    }

    /**
     * Name of a class's flow.
     *
     * @param beanClass the bean class.
     * @return {@code <simpleName>ValidationFlow} with a lower-case initial.
     */
    public static String flowName(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        return decapitalize(simpleName(beanClass)) + FLOW_SUFFIX;
    }

    /**
     * Name of an executable's parameters flow: {@code <bean>_<name>ParametersFlow}, the class's simple
     * name with a lower-case initial and the method name or, for a constructor, the simple class name
     * ({@code orderService_placeParametersFlow}, {@code orderService_OrderServiceParametersFlow}). An
     * overloaded executable (another constrained one of the same name on the class) adds its parameter
     * types ({@code orderService_place_Order_intParametersFlow}), or {@code _noArgs}.
     *
     * @param owner the class's definition.
     * @param executable the executable's definition.
     * @return the name.
     */
    public static String parametersFlowName(BeanDefinition owner, ExecutableDefinition executable) {
        return flowName(owner, executable, PARAMETERS_SUFFIX);
    }

    /**
     * Name of an executable's return-value flow: as {@link #parametersFlowName}, ending in
     * {@code ReturnValueFlow}.
     *
     * @param owner the class's definition.
     * @param executable the executable's definition.
     * @return the name.
     */
    public static String returnValueFlowName(BeanDefinition owner, ExecutableDefinition executable) {
        return flowName(owner, executable, RETURN_VALUE_SUFFIX);
    }

    private static String flowName(BeanDefinition owner, ExecutableDefinition executable, String suffix) {
        Assert.notNull(owner, "owner cannot be null.");
        Assert.notNull(executable, "executable cannot be null.");
        StringBuilder result = new StringBuilder(decapitalize(simpleName(owner.getBeanClass()))).append("_").append(executable.getName());

        if (overloaded(owner, executable)) {
            Class<?>[] types = executable.getParameterTypes();

            if (types.length == 0) result.append("_noArgs");

            for (Class<?> type : types) {
                result.append("_").append(type.getSimpleName().replace("[]", "Array"));
            }
        }

        return result.append(suffix).toString();
    }

    private static boolean overloaded(BeanDefinition owner, ExecutableDefinition executable) {
        int count = 0;

        for (ExecutableDefinition candidate : executable.isConstructor() ? owner.getConstructors() : owner.getMethods()) {
            if (candidate.getName().equals(executable.getName())) count++;
        }

        return count > 1;
    }

    private static String simpleName(Class<?> beanClass) {
        String name = beanClass.getSimpleName();
        return name.isEmpty() ? "Bean" : name;
    }

    private static String decapitalize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
