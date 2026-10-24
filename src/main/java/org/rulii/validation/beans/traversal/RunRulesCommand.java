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
import org.rulii.ruleflow.RuleFlowExecutionContext;
import org.rulii.ruleflow.command.RuleFlowCommand;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;

import java.util.List;

/**
 * Runs the property and class rules of the object bound as {@code $this}, filtered for the groups
 * being processed, inside a collector frame at the current path so the violations are stamped with
 * their property paths and provenance.
 *
 * <p>When the class redefines its Default group with {@code @GroupSequence} and Default is being
 * processed, the rules run member by member ({@link Traversal#forDefaultSequence}), and the rules of
 * the container element positions run here with them, since element constraints are constraints of the
 * bean and take part in the sequence; the cascade step then walks the containers for cascades only.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class RunRulesCommand implements RuleFlowCommand {

    private final BeanRules rules;

    RunRulesCommand(BeanRules rules) {
        super();
        Assert.notNull(rules, "rules cannot be null.");
        this.rules = rules;
    }

    @Override
    public void execute(RuleFlowExecutionContext ctx) {
        RuleContext ruleContext = ctx.getRuleContext();
        BeanRuleViolations collector = Traversal.collector(ruleContext);
        Assert.notNull(collector, "no path-aware collector is bound as [" + BindingPlan.RULE_VIOLATIONS + "]; run through the bean flow.");
        Object bean = ruleContext.getBindings().getValue(BindingPlan.THIS);
        Traversal traversal = Traversal.of(ruleContext);
        List<Class<?>> sequence = rules.getDefinition().getDefaultGroupSequence();
        boolean redefined = traversal.appliesDefaultSequence(sequence);

        try (BeanRuleViolations.Frame ignored = collector.enter(collector.getCurrentPath(), rules, bean)) {
            traversal.forDefaultSequence(ruleContext, sequence, groups -> {
                rules.ruleSet(groups).run(ruleContext);
                if (redefined) elementRules(ruleContext, collector, bean);
            });
        }
    }

    /**
     * Runs the rules of every container element position of the object, without cascading.
     */
    private void elementRules(RuleContext ruleContext, BeanRuleViolations collector, Object bean) {
        ContainerWalker walker = new ContainerWalker(ruleContext, rules, false);
        PropertyPath base = collector.getCurrentPath();
        BindingPlan plan = rules.getBindingPlan();

        for (PropertyDefinition property : rules.getDefinition().getProperties()) {
            for (ContainerElementDefinition node : property.getContainerElements()) {
                walker.walk(plan.read(ruleContext.getBindings(), bean, property, node.getSourceDeclaration()), node,
                        base.property(property.getName()));
            }
        }
    }

    @Override
    public String toString() {
        return "run rules of " + rules.getBeanClass().getSimpleName();
    }
}
