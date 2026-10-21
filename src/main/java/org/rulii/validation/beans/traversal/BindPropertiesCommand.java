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

import org.rulii.lib.spring.util.Assert;
import org.rulii.ruleflow.RuleFlowExecutionContext;
import org.rulii.ruleflow.command.RuleFlowCommand;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;

/**
 * Flow step binding the properties of {@code $this} into the flow's scope, per the class's
 * {@link BindingPlan}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class BindPropertiesCommand implements RuleFlowCommand {

    private final BeanRules rules;

    BindPropertiesCommand(BeanRules rules) {
        super();
        Assert.notNull(rules, "rules cannot be null.");
        this.rules = rules;
    }

    @Override
    public void execute(RuleFlowExecutionContext ctx) {
        Object bean = ctx.getRuleContext().getBindings().getValue(BindingPlan.THIS);
        Assert.notNull(bean, "[" + BindingPlan.THIS + "] is not bound; the flow needs the object to validate.");
        rules.getBindingPlan().load(ctx.getRuleContext().getBindings(), bean);
    }

    @Override
    public String toString() {
        return "bind properties of " + rules.getBeanClass().getSimpleName();
    }
}
