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
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;

import java.util.List;

/**
 * The single step of a bean flow, wrapping the steps that validate the object bound as
 * {@code $this}: bind its properties, run its rules, run its {@code @Validate} rules, cascade.
 *
 * <p>Around the body it does what must happen once per object and once per call: the object is
 * recorded in the traversal and skipped if this call validated it already; and when the caller
 * bound a plain {@link RuleViolations} (an embedded run), a path-aware collector is bound for the
 * whole body, nested flows included, and everything it gathered is handed to the plain one at the
 * end, paths stamped. A nested flow finds the path-aware collector already bound and runs its body
 * directly.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ValidateBeanCommand extends ContainerCommand {

    private final BeanRules rules;

    ValidateBeanCommand(BeanRules rules, List<RuleFlowCommand> body) {
        super();
        Assert.notNull(rules, "rules cannot be null.");
        Assert.notNull(body, "body cannot be null.");
        this.rules = rules;
        setBody(body);
    }

    @Override
    public void execute(RuleFlowExecutionContext ctx) {
        RuleContext ruleContext = ctx.getRuleContext();
        Object bean = ruleContext.getBindings().getValue(BindingPlan.THIS);
        Assert.notNull(bean, "[" + BindingPlan.THIS + "] is not bound; the flow needs the object to validate.");

        if (!Traversal.of(ruleContext).markValidated(bean)) return;

        if (Traversal.collector(ruleContext) != null) {
            run(ctx);
            return;
        }

        RuleViolations plain = Traversal.anyCollector(ruleContext);
        BeanRuleViolations temporary = new BeanRuleViolations(ruleContext.getBindings().getValue(BindingPlan.ROOT));
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

    private void run(RuleFlowExecutionContext ctx) {
        for (RuleFlowCommand command : getBody()) {
            command.execute(ctx);
        }
    }

    @Override
    public String toString() {
        return "validate " + rules.getBeanClass().getSimpleName();
    }
}
