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
 * Walks the container elements of the return value and cascades into it when it is {@code @Valid},
 * at the return-value node under the executable's. Element constraints run here unless a class-level
 * default group sequence already ran them with the return-value rules.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class CascadeReturnValueCommand implements RuleFlowCommand {

    private final ExecutableRules rules;
    private final List<Class<?>> defaultSequence;

    /**
     * Creates the step.
     *
     * @param rules the executable's rules.
     * @param defaultSequence the owning class's redefined Default; null when the class does not redefine it.
     */
    CascadeReturnValueCommand(ExecutableRules rules, List<Class<?>> defaultSequence) {
        super();
        Assert.notNull(rules, "rules cannot be null.");
        this.rules = rules;
        this.defaultSequence = defaultSequence;
    }

    @Override
    public void execute(RuleFlowExecutionContext ctx) {
        ReturnValueDefinition returnValue = rules.getDefinition().getReturnValue();

        if (returnValue.getContainerElements().isEmpty() && !returnValue.isCascaded()) return;

        RuleContext ruleContext = ctx.getRuleContext();
        BeanRuleViolations collector = Traversal.collector(ruleContext);
        Assert.notNull(collector, "no path-aware collector is bound as [" + BindingPlan.RULE_VIOLATIONS + "]; run through the executable flow.");
        Traversal traversal = Traversal.of(ruleContext);
        Object value = ruleContext.getBindings().getValue(BindingPlan.RETURN_VALUE);
        PropertyPath path = collector.getCurrentPath().returnValue();
        boolean elementRules = !traversal.appliesDefaultSequence(defaultSequence);
        ContainerWalker walker = new ContainerWalker(ruleContext, rules, true, elementRules);

        if (elementRules) ValidateExecutableCommand.walkReturnValueElements(returnValue, value, path, walker);

        if (returnValue.isCascaded()) walker.cascade(value, returnValue.getGenericType(), path, returnValue.getGroupConversions());
    }

    @Override
    public String toString() {
        return "cascade from the return value of " + rules.getDefinition().getName();
    }
}
