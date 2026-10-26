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

import org.rulii.bind.Binding;
import org.rulii.bind.NamedScope;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.ruleflow.RuleFlowExecutionContext;
import org.rulii.ruleflow.command.ContainerCommand;
import org.rulii.ruleflow.command.RuleFlowCommand;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.assembly.BindingPlan;

import java.util.List;

/**
 * The single step of a validation flow: wraps the steps that do the work and sees to it that they
 * report into a path-aware collector. When the caller bound a plain {@link RuleViolations} (an
 * embedded run), a {@link BeanRuleViolations} is bound for the whole body, nested flows included, and
 * everything it gathered is handed to the plain one at the end, paths stamped. When a path-aware
 * collector is bound already (a nested flow, or a run the validator set up), the body runs directly.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
abstract class CollectingCommand extends ContainerCommand {

    CollectingCommand(List<RuleFlowCommand> body) {
        super();
        Assert.notNull(body, "body cannot be null.");
        setBody(body);
    }

    @Override
    public void execute(RuleFlowExecutionContext ctx) {
        RuleContext ruleContext = ctx.getRuleContext();

        if (Traversal.collector(ruleContext) != null) {
            run(ctx);
            return;
        }

        RuleViolations plain = Traversal.anyCollector(ruleContext);
        BeanRuleViolations temporary = collector(ruleContext);
        NamedScope scope = ruleContext.getBindings().addScope();

        try {
            scope.getBindings().bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class)
                    .value(temporary).primary(true).build());
            run(ctx);
        } finally {
            ruleContext.getBindings().removeScope(scope);
        }

        if (plain != null) plain.addAll(temporary.getViolations());
    }

    /**
     * The path-aware collector bound for an embedded run.
     *
     * @param ruleContext the context of the run.
     * @return a fresh collector.
     */
    protected abstract BeanRuleViolations collector(RuleContext ruleContext);

    /**
     * Runs the body, with a path-aware collector bound.
     *
     * @param ctx the flow execution context.
     */
    protected void run(RuleFlowExecutionContext ctx) {
        for (RuleFlowCommand command : getBody()) {
            command.execute(ctx);
        }
    }
}
