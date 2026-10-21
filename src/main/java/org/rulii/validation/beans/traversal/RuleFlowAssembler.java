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
import org.rulii.validation.beans.group.RequestedGroups;

import java.util.function.Function;

/**
 * Builds the per-class {@link RuleFlow}: given an object of the class, bind its properties, run its
 * rules, and (from the next step on) run its attached rules and descend into its cascades. The flow
 * runs inside its own scope, so each object validated gets a scope of its own and nested objects see
 * their parents' bindings innermost-outward.
 *
 * <p>Parameters: {@code $this}, the object, required; {@code $root}, defaulting to {@code $this};
 * {@code ruleViolations}, defaulting to a fresh {@link BeanRuleViolations}; {@code $traversal},
 * defaulting to a fresh {@link Traversal} on the Default group. The defaults make a flow complete on
 * its own, for embedding in a user's RuleSet or RuleFlow; the entry point binds all four.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuleFlowAssembler {

    private static final String FLOW_SUFFIX = "ValidationFlow";

    private final Function<Class<?>, BeanRules> lookup;

    /**
     * Creates the assembler.
     *
     * @param lookup the rules of a bean class by runtime type, for the traversal a flow creates when
     *               run on its own.
     */
    public RuleFlowAssembler(Function<Class<?>, BeanRules> lookup) {
        super();
        Assert.notNull(lookup, "lookup cannot be null.");
        this.lookup = lookup;
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
                        new Traversal(RequestedGroups.DEFAULT, lookup)))
                .command(new BindPropertiesCommand(rules))
                .command(new RunRulesCommand(rules))
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
