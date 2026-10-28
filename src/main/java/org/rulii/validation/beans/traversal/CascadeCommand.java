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
import org.rulii.validation.beans.model.ValueDeclaration;

/**
 * Descends from the object bound as {@code $this} into its properties, in property order: the
 * declared container element positions of a property are walked (element rules and element
 * cascades), then each {@code @Valid} declaration of the property cascades into its value, with the
 * property's {@code @ConvertGroup} conversions.
 *
 * <p>A value is read the way the property's constraints read it: through the property binding for
 * the primary declaration and for getters, through the declaration's accessor for a constrained
 * field beside a getter. When the class's redefined Default group sequence applies, the element rules
 * already ran with the object's rules, so the walk here cascades only.</p>
 *
 * <p>With a traversable resolver in the traversal, a property it puts out of reach is neither walked
 * nor read; a reachable property it declares not cascadable keeps its element constraints and does
 * not cascade, as the specification orders ({@code isReachable} first, then {@code isCascadable}).</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class CascadeCommand implements RuleFlowCommand {

    private final BeanRules rules;

    CascadeCommand(BeanRules rules) {
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
        Traversal traversal = Traversal.of(ruleContext);
        boolean elementRules = !traversal.appliesDefaultSequence(rules.getDefinition().getDefaultGroupSequence());
        ContainerWalker cascading = null;
        ContainerWalker plain = null;

        for (PropertyDefinition property : rules.getDefinition().getProperties()) {
            if (traversal.halted(collector)) return;
            if (property.getContainerElements().isEmpty() && !property.isCascaded()) continue;

            PropertyPath path = base.property(property.getName());
            BindingPlan plan = rules.getBindingPlan();
            String name = property.getName();

            for (ContainerElementDefinition node : property.getContainerElements()) {
                if (traversal.halted(collector)) return;
                ValueDeclaration.Kind kind = node.getSourceDeclaration().getKind();
                if (!traversal.isReachable(ruleContext, bean, name, kind)) continue;
                // Element constraints of a reachable property run; element cascades need the property cascadable too.
                boolean cascade = !cascades(node) || traversal.isCascadable(ruleContext, bean, name, kind);
                ContainerWalker walker;

                if (cascade) {
                    if (cascading == null) cascading = new ContainerWalker(ruleContext, rules, true, elementRules);
                    walker = cascading;
                } else if (elementRules) {
                    if (plain == null) plain = new ContainerWalker(ruleContext, rules, false, true);
                    walker = plain;
                } else {
                    continue;
                }

                walker.walk(plan.read(ruleContext.getBindings(), bean, property, node.getSourceDeclaration()), node, path);
            }

            for (ValueDeclaration declaration : property.getCascadedDeclarations()) {
                if (traversal.halted(collector)) return;
                if (!traversal.isReachable(ruleContext, bean, name, declaration.getKind())
                        || !traversal.isCascadable(ruleContext, bean, name, declaration.getKind())) {
                    continue;
                }
                if (cascading == null) cascading = new ContainerWalker(ruleContext, rules, true, elementRules);
                cascading.cascade(plan.read(ruleContext.getBindings(), bean, property, declaration), declaration.getGenericType(), path,
                        property.getGroupConversions());
            }
        }
    }

    /**
     * Whether a container element position, or one nested in it, is cascaded.
     */
    private static boolean cascades(ContainerElementDefinition node) {
        if (node.isCascaded()) return true;

        for (ContainerElementDefinition child : node.getChildren()) {
            if (cascades(child)) return true;
        }

        return false;
    }

    @Override
    public String toString() {
        return "cascade from " + rules.getBeanClass().getSimpleName();
    }
}
