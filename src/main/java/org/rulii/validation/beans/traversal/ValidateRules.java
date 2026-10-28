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
import org.rulii.model.Runnable;
import org.rulii.registry.RuleRegistry;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.ValidateRuleNotFoundException;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.ValidateDefinition;

import java.util.List;

/**
 * Runs the {@code @Validate} rules of one group step, after the constraint rules of the step: the
 * occurrences active for the groups being processed (a constraint's activation: none declared means
 * {@code Default}), each name resolved when it runs, in the registry the call brought when there is
 * one and in the context's otherwise, so what a name validates can change between calls; a name that
 * resolves to nothing fails loudly. The runnable's result is ignored: its violations arrive through
 * the collector.
 *
 * <p>Property-level rules report at the property path and see the value as {@code $value}; class-level
 * rules report at the bean node; the rules of an executable's parameters report at the cross-parameter
 * node with the arguments bound by name; return-value rules report at the return-value node and see
 * the value as {@code $value}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ValidateRules {

    private ValidateRules() {
        super();
    }

    /**
     * Runs the rules of the object in scope: those of its properties, in property order, for the
     * properties the traversable resolver leaves in reach; then those of its class.
     *
     * @param ruleContext the context of the run.
     * @param collector the path-aware collector, positioned at the object.
     * @param traversal the traversal of the run.
     * @param rules the rules of the object's class.
     * @param bean the object.
     * @param groups the groups of the step.
     */
    static void runBean(RuleContext ruleContext, BeanRuleViolations collector, Traversal traversal, BeanRules rules, Object bean,
                        RequestedGroups groups) {
        PropertyPath base = collector.getCurrentPath();

        for (PropertyDefinition property : rules.getDefinition().getProperties()) {
            if (traversal.halted(collector)) return;
            if (!anyActive(property.getValidateRules(), groups)) continue;
            if (!traversal.isReachable(ruleContext, bean, property.getName(), property.getPropertySource().getPrimary().getKind())) continue;

            Object value = ruleContext.getBindings().getValue(rules.getBindingPlan().getBindingName(property));

            try (BeanRuleViolations.Frame ignored = collector.enter(base.property(property.getName()), bean)) {
                runWithValue(ruleContext, traversal, rules.getBeanClass(), property.getValidateRules(), groups, value);
            }
        }

        if (traversal.halted(collector) || !anyActive(rules.getDefinition().getValidateRules(), groups)) return;

        try (BeanRuleViolations.Frame ignored = collector.enter(base.bean(), bean)) {
            run(ruleContext, traversal, rules.getBeanClass(), rules.getDefinition().getValidateRules(), groups);
        }
    }

    /**
     * Runs the rules attached to the parameters of the executable, at the cross-parameter node under
     * the executable node the collector is positioned at.
     *
     * @param ruleContext the context of the run, with the arguments bound by name.
     * @param collector the path-aware collector, positioned at the executable.
     * @param traversal the traversal of the run.
     * @param rules the rules of the executable.
     * @param bean the object the executable is called on; null for a constructor.
     * @param groups the groups of the step.
     */
    static void runParameters(RuleContext ruleContext, BeanRuleViolations collector, Traversal traversal, ExecutableRules rules,
                              Object bean, RequestedGroups groups) {
        List<ValidateDefinition> definitions = rules.getDefinition().getValidateRules();

        if (!anyActive(definitions, groups)) return;

        try (BeanRuleViolations.Frame ignored = collector.enter(collector.getCurrentPath().crossParameter(), bean)) {
            run(ruleContext, traversal, rules.getBeanClass(), definitions, groups);
        }
    }

    /**
     * Runs the rules attached to the return value of the executable, at the return-value node under
     * the executable node the collector is positioned at, with the value bound as {@code $value}.
     *
     * @param ruleContext the context of the run.
     * @param collector the path-aware collector, positioned at the executable.
     * @param traversal the traversal of the run.
     * @param rules the rules of the executable.
     * @param bean the object the executable was called on; the created object for a constructor.
     * @param value the value returned.
     * @param groups the groups of the step.
     */
    static void runReturnValue(RuleContext ruleContext, BeanRuleViolations collector, Traversal traversal, ExecutableRules rules,
                               Object bean, Object value, RequestedGroups groups) {
        List<ValidateDefinition> definitions = rules.getDefinition().getReturnValue().getValidateRules();

        if (!anyActive(definitions, groups)) return;

        try (BeanRuleViolations.Frame ignored = collector.enter(collector.getCurrentPath().returnValue(), bean)) {
            runWithValue(ruleContext, traversal, rules.getBeanClass(), definitions, groups, value);
        }
    }

    private static boolean anyActive(List<ValidateDefinition> definitions, RequestedGroups groups) {
        for (ValidateDefinition definition : definitions) {
            if (groups.activates(definition.getGroups())) return true;
        }

        return false;
    }

    private static void runWithValue(RuleContext ruleContext, Traversal traversal, Class<?> beanClass, List<ValidateDefinition> definitions,
                                     RequestedGroups groups, Object value) {
        NamedScope scope = ruleContext.getBindings().addScope();

        try {
            scope.getBindings().bind(Binding.builder().with(BindingPlan.VALUE, Object.class).value(value).build());
            run(ruleContext, traversal, beanClass, definitions, groups);
        } finally {
            ruleContext.getBindings().removeScope(scope);
        }
    }

    private static void run(RuleContext ruleContext, Traversal traversal, Class<?> beanClass, List<ValidateDefinition> definitions,
                            RequestedGroups groups) {
        BeanRuleViolations collector = Traversal.collector(ruleContext);

        for (ValidateDefinition definition : definitions) {
            if (!groups.activates(definition.getGroups())) continue;

            for (String name : definition.getRuleNames()) {
                if (traversal.halted(collector)) return;
                resolve(ruleContext, traversal, beanClass, definition, name).run(ruleContext);
            }
        }
    }

    private static Runnable<?> resolve(RuleContext ruleContext, Traversal traversal, Class<?> beanClass, ValidateDefinition definition,
                                       String name) {
        RuleRegistry registry = traversal.getRuleRegistry();

        if (registry == null) registry = ruleContext.getRuleRegistry();

        if (registry == null) {
            throw new ValidateRuleNotFoundException(beanClass, definition, name, "no RuleRegistry is configured on the RuleContext.");
        }

        Runnable<?> result = registry.get(name);

        if (result == null) {
            throw new ValidateRuleNotFoundException(beanClass, definition, name, "nothing is registered under that name.");
        }

        return result;
    }
}
