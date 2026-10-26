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
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.model.ReturnValueDefinition;

import java.util.List;

/**
 * Runs the return-value rules of the executable, filtered for the groups being processed, at the
 * return-value node under the frame the executable step opened. A class-level redefinition of Default
 * runs them member by member with the rules of the value's container element positions, as
 * {@link RunParameterRulesCommand} does for the arguments. The {@code @Validate} rules attached to the
 * return value run after the constraint rules of each step ({@link ValidateRules}).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class RunReturnValueRulesCommand implements RuleFlowCommand {

    private final ExecutableRules rules;
    private final List<Class<?>> defaultSequence;

    /**
     * Creates the step.
     *
     * @param rules the executable's rules.
     * @param defaultSequence the owning class's redefined Default; null when the class does not redefine it.
     */
    RunReturnValueRulesCommand(ExecutableRules rules, List<Class<?>> defaultSequence) {
        super();
        Assert.notNull(rules, "rules cannot be null.");
        this.rules = rules;
        this.defaultSequence = defaultSequence;
    }

    @Override
    public void execute(RuleFlowExecutionContext ctx) {
        RuleContext ruleContext = ctx.getRuleContext();
        BeanRuleViolations collector = Traversal.collector(ruleContext);
        Assert.notNull(collector, "no path-aware collector is bound as [" + BindingPlan.RULE_VIOLATIONS + "]; run through the executable flow.");
        Traversal traversal = Traversal.of(ruleContext);
        ReturnValueDefinition returnValue = rules.getDefinition().getReturnValue();
        Object value = ruleContext.getBindings().getValue(BindingPlan.RETURN_VALUE);
        PropertyPath path = collector.getCurrentPath().returnValue();
        boolean redefined = traversal.appliesDefaultSequence(defaultSequence);

        traversal.forDefaultSequence(ruleContext, defaultSequence, groups -> {
            rules.returnValueRuleSet(groups).run(ruleContext);
            if (redefined) ValidateExecutableCommand.walkReturnValueElements(returnValue, value, path, new ContainerWalker(ruleContext, rules, false));
            ValidateRules.runReturnValue(ruleContext, collector, traversal, rules, ValidateExecutableCommand.bean(ruleContext), value, groups);
        });
    }

    @Override
    public String toString() {
        return "run return-value rules of " + rules.getDefinition().getName();
    }
}
