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
import org.rulii.ruleflow.RuleFlow;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.RequestedGroups;

import java.util.List;
import java.util.function.Function;

/**
 * Builds the {@link RuleFlow} of a bean class from its {@link BeanRules}: the self-contained unit
 * that, given an object bound as {@code $this}, binds its properties, runs its rules and its
 * {@code @Validate} rules, and cascades into the objects it references.
 *
 * <p>Parameters: {@code $this} (required, the object); {@code $root} (defaults to {@code $this});
 * {@code ruleViolations} (defaults to a fresh {@link BeanRuleViolations} for the root);
 * {@code $traversal} (defaults to a fresh {@link Traversal} with the Default group). The flow's own
 * scope is the object's scope: the properties are bound in it by simple name and gone when the flow
 * ends. Each object descended into gets a scope of its own, nested in its parent's.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuleFlowAssembler {

    private static final String FLOW_SUFFIX = "ValidationFlow";

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

        return RuleFlow.builder()
                .name(flowName(rules.getBeanClass()))
                .description("Validates a " + rules.getBeanClass().getName())
                .param(BindingPlan.THIS, Object.class, true)
                .param(BindingPlan.ROOT, Object.class, Functions.function((RuleContext ruleContext) ->
                        ruleContext.getBindings().getValue(BindingPlan.THIS)))
                .param(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, Functions.function((RuleContext ruleContext) ->
                        new BeanRuleViolations(ruleContext.getBindings().getValue(BindingPlan.ROOT))))
                .param(BindingPlan.TRAVERSAL, Traversal.class, Functions.function((RuleContext ruleContext) ->
                        new Traversal(RequestedGroups.DEFAULT, flows, extractors)))
                .command(new ValidateBeanCommand(rules, List.of(
                        new BindPropertiesCommand(rules),
                        new RunRulesCommand(rules),
                        new ValidateRulesCommand(rules),
                        new CascadeCommand(rules))))
                .build();
    }

    /**
     * Name of a class's flow.
     *
     * @param beanClass the bean class.
     * @return {@code <simpleName>ValidationFlow} with a lower-case initial.
     */
    public static String flowName(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        String name = beanClass.getSimpleName().isEmpty() ? "Bean" : beanClass.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1) + FLOW_SUFFIX;
    }
}
