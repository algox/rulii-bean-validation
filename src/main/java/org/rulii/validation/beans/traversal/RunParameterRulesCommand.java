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

import java.util.List;

/**
 * Runs the parameter and cross-parameter rules of the executable, filtered for the groups being
 * processed, under the frame the executable step opened.
 *
 * <p>When the class redefines its Default group with {@code @GroupSequence} and Default is being
 * processed, the rules run member by member ({@link Traversal#forDefaultSequence}), and the rules of
 * the arguments' container element positions run with them, as the bean step does for properties;
 * the cascade step then walks the containers for cascades only. The {@code @Validate} rules attached
 * to the parameters run after the constraint rules of each step ({@link ValidateRules}).</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class RunParameterRulesCommand implements RuleFlowCommand {

    private final ExecutableRules rules;
    private final List<Class<?>> defaultSequence;

    /**
     * Creates the step.
     *
     * @param rules the executable's rules.
     * @param defaultSequence the owning class's redefined Default; null when the class does not redefine it.
     */
    RunParameterRulesCommand(ExecutableRules rules, List<Class<?>> defaultSequence) {
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
        Object[] values = ruleContext.getBindings().getValue(BindingPlan.PARAMETERS);
        PropertyPath base = collector.getCurrentPath();
        boolean redefined = traversal.appliesDefaultSequence(defaultSequence);

        traversal.forDefaultSequence(ruleContext, defaultSequence, groups -> {
            rules.parameterRuleSet(groups).run(ruleContext);
            if (redefined) ValidateExecutableCommand.walkParameterElements(rules, values, base, new ContainerWalker(ruleContext, rules, false));
            ValidateRules.runParameters(ruleContext, collector, traversal, rules, ValidateExecutableCommand.bean(ruleContext), groups);
        });
    }

    @Override
    public String toString() {
        return "run parameter rules of " + rules.getDefinition().getName();
    }
}
