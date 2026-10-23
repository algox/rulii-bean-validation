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
import org.rulii.model.Runnable;
import org.rulii.registry.RuleRegistry;
import org.rulii.ruleflow.RuleFlowExecutionContext;
import org.rulii.ruleflow.command.RuleFlowCommand;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.ValidateRuleNotFoundException;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.ValidateDefinition;

import java.util.List;

/**
 * Runs the {@code @Validate} rules of the object bound as {@code $this}: the rules named on its
 * properties, in property order, then the rules named on its class. Each name is resolved when the
 * command runs, in the traversal's {@code RuleRegistry} when the call brought one and in the context's
 * otherwise, so what a name validates can change between calls; a name that resolves to nothing fails
 * loudly.
 *
 * <p>A property-level run reports at the property's path and sees the property value as
 * {@code $value}; a class-level run reports at the bean node, like a class-level constraint. The
 * runnable's result is ignored: its violations arrive through the collector.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ValidateRulesCommand implements RuleFlowCommand {

    private final BeanRules rules;

    ValidateRulesCommand(BeanRules rules) {
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
        PropertyPath base = collector.getCurrentPath();

        for (PropertyDefinition property : rules.getDefinition().getProperties()) {
            if (property.getValidateRules().isEmpty()) continue;

            Object value = ruleContext.getBindings().getValue(rules.getBindingPlan().getBindingName(property));
            NamedScope scope = ruleContext.getBindings().addScope();

            try (BeanRuleViolations.Frame ignored = collector.enter(base.property(property.getName()), bean)) {
                scope.getBindings().bind(Binding.builder().with(BindingPlan.VALUE, Object.class).value(value).build());
                run(ruleContext, property.getValidateRules());
            } finally {
                ruleContext.getBindings().removeScope(scope);
            }
        }

        if (rules.getDefinition().getValidateRules().isEmpty()) return;

        try (BeanRuleViolations.Frame ignored = collector.enter(base.bean(), bean)) {
            run(ruleContext, rules.getDefinition().getValidateRules());
        }
    }

    private void run(RuleContext ruleContext, List<ValidateDefinition> definitions) {
        for (ValidateDefinition definition : definitions) {
            for (String name : definition.getRuleNames()) {
                resolve(ruleContext, definition, name).run(ruleContext);
            }
        }
    }

    private Runnable<?> resolve(RuleContext ruleContext, ValidateDefinition definition, String name) {
        RuleRegistry registry = Traversal.of(ruleContext).getRuleRegistry();

        if (registry == null) registry = ruleContext.getRuleRegistry();

        if (registry == null) {
            throw new ValidateRuleNotFoundException(rules.getBeanClass(), definition, name,
                    "no RuleRegistry is configured on the RuleContext.");
        }

        Runnable<?> result = registry.get(name);

        if (result == null) {
            throw new ValidateRuleNotFoundException(rules.getBeanClass(), definition, name,
                    "nothing is registered under that name.");
        }

        return result;
    }

    @Override
    public String toString() {
        return "run @Validate rules of " + rules.getBeanClass().getSimpleName();
    }
}
