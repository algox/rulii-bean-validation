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
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;

/**
 * Runs the property and class rules of the object bound as {@code $this}, filtered for the call's
 * groups, inside a collector frame at the current path so the violations are stamped with their
 * property paths and provenance.
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
        RuleSet<?> ruleSet = Traversal.of(ruleContext).ruleSet(rules);

        try (BeanRuleViolations.Frame ignored = collector.enter(collector.getCurrentPath(), rules, bean)) {
            ruleSet.run(ruleContext);
        }
    }

    @Override
    public String toString() {
        return "run rules of " + rules.getBeanClass().getSimpleName();
    }
}
