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
package org.rulii.validation.beans.assembly;

import org.rulii.lib.spring.util.Assert;
import org.rulii.rule.Rule;
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintMapperException;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rules materialized for one bean class: the property rules in property then declaration order,
 * the rules of each constrained container element position, and the class-level rules, each with
 * its {@link MaterializedRule metadata}; plus the {@link BindingPlan} they were built against.
 *
 * <p>Rules are built once per class and shared. What runs depends on the groups requested by a
 * validation call: {@link #ruleSet(RequestedGroups)} assembles the property and class rules active
 * for the request into a {@link RuleSet}, cached per expanded group set, and
 * {@link #elementRuleSet(ContainerElementDefinition, RequestedGroups)} does the same for a container
 * position, whose rules the container walk runs per element. The RuleSets are plain: the violations
 * collector is bound by the caller, and nothing throws on violations.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanRules {

    /**
     * The rules of one container element position within a property.
     *
     * @param property the property whose type the position is in.
     * @param node the position.
     * @param rules the rules, in declaration order; never empty.
     */
    public record ElementRules(PropertyDefinition property, ContainerElementDefinition node, List<MaterializedRule> rules) {

        public ElementRules {
            Assert.notNull(property, "property cannot be null.");
            Assert.notNull(node, "node cannot be null.");
            Assert.notNull(rules, "rules cannot be null.");
            rules = List.copyOf(rules);
        }
    }

    private final BeanDefinition definition;
    private final BindingPlan bindingPlan;
    private final List<MaterializedRule> propertyRules;
    private final List<ElementRules> elementRules;
    private final List<MaterializedRule> classRules;
    private final Map<String, MaterializedRule> byName;
    private final Map<ContainerElementDefinition, ElementRules> byNode;
    private final String name;
    private final ConcurrentHashMap<Set<Class<?>>, RuleSet<?>> ruleSets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContainerElementDefinition, ConcurrentHashMap<Set<Class<?>>, RuleSet<?>>> elementRuleSets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Set<Class<?>>, RuleSet<?>>> propertyRuleSets = new ConcurrentHashMap<>();

    BeanRules(BeanDefinition definition, BindingPlan bindingPlan, List<MaterializedRule> propertyRules,
              List<ElementRules> elementRules, List<MaterializedRule> classRules) {
        super();
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(bindingPlan, "bindingPlan cannot be null.");
        this.definition = definition;
        this.bindingPlan = bindingPlan;
        this.propertyRules = List.copyOf(propertyRules);
        this.elementRules = List.copyOf(elementRules);
        this.classRules = List.copyOf(classRules);

        Map<String, MaterializedRule> names = new LinkedHashMap<>();

        for (MaterializedRule rule : this.propertyRules) {
            register(names, rule);
        }

        for (ElementRules element : this.elementRules) {
            for (MaterializedRule rule : element.rules()) {
                register(names, rule);
            }
        }

        for (MaterializedRule rule : this.classRules) {
            register(names, rule);
        }

        this.byName = Collections.unmodifiableMap(names);

        Map<ContainerElementDefinition, ElementRules> nodes = new LinkedHashMap<>();

        for (ElementRules element : this.elementRules) {
            nodes.put(element.node(), element);
        }

        this.byNode = Collections.unmodifiableMap(nodes);

        String simpleName = definition.getBeanClass().getSimpleName();
        this.name = (simpleName.isEmpty() ? "Bean" : simpleName) + "Rules";
    }

    private static void register(Map<String, MaterializedRule> names, MaterializedRule rule) {
        MaterializedRule previous = names.putIfAbsent(rule.getName(), rule);

        if (previous != null) {
            throw new ConstraintMapperException(rule.getDefinition().getAnnotationType(),
                    rule.getDefinition().getConstraintSource().describe(), "rule name [" + rule.getName()
                    + "] is already used by " + previous + "; rule names must be unique within a bean class.");
        }

        for (MaterializedRule hosted : rule.getHostedRules()) {
            register(names, hosted);
        }
    }

    public BeanDefinition getDefinition() {
        return definition;
    }

    public Class<?> getBeanClass() {
        return definition.getBeanClass();
    }

    public BindingPlan getBindingPlan() {
        return bindingPlan;
    }

    /**
     * Rules of the property constraints, in property order then declaration order (field
     * constraints before getter constraints).
     *
     * @return the property rules.
     */
    public List<MaterializedRule> getPropertyRules() {
        return propertyRules;
    }

    /**
     * Rules of the constrained container element positions, in property order then position order
     * (depth-first).
     *
     * @return the element rules.
     */
    public List<ElementRules> getElementRules() {
        return elementRules;
    }

    /**
     * The rules of one container element position.
     *
     * @param node the position.
     * @return its rules; null if the position has no constraints.
     */
    public ElementRules getElementRules(ContainerElementDefinition node) {
        Assert.notNull(node, "node cannot be null.");
        return byNode.get(node);
    }

    /**
     * Rules of the class-level constraints, in hierarchy order.
     *
     * @return the class rules.
     */
    public List<MaterializedRule> getClassRules() {
        return classRules;
    }

    /**
     * Every rule, in execution order: property rules, element rules, class rules.
     *
     * @return all rules.
     */
    public List<MaterializedRule> getRules() {
        return new ArrayList<>(byName.values());
    }

    /**
     * The rule with the given name.
     *
     * @param name the rule name.
     * @return the rule; null if there is none.
     */
    public MaterializedRule getRule(String name) {
        Assert.notNull(name, "name cannot be null.");
        return byName.get(name);
    }

    /**
     * Whether anything was materialized.
     *
     * @return true if there is at least one rule.
     */
    public boolean isEmpty() {
        return byName.isEmpty();
    }

    /**
     * Name of the RuleSets built here: {@code <SimpleName>Rules}.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * The property and class rules active for the given groups, as a RuleSet named
     * {@link #getName()}; property rules first, class rules last. Cached per expanded group set.
     *
     * @param groups the requested groups.
     * @return the RuleSet; empty when nothing is active.
     */
    public RuleSet<?> ruleSet(RequestedGroups groups) {
        Assert.notNull(groups, "groups cannot be null.");
        return ruleSets.computeIfAbsent(groups.getGroups(), key -> {
            List<Rule> rules = new ArrayList<>();

            for (MaterializedRule rule : propertyRules) {
                if (rule.isActive(groups)) rules.add(rule.getRule());
            }

            for (MaterializedRule rule : classRules) {
                if (rule.isActive(groups)) rules.add(rule.getRule());
            }

            return RuleSet.builder().with(name).rules(rules).build();
        });
    }

    /**
     * The rules of the constraints declared on one property, active for the given groups, as a RuleSet
     * named {@code <getName()>_<property>}: what validating a single property runs. Container element
     * and class rules are not included. Cached per property and expanded group set.
     *
     * @param propertyName the property.
     * @param groups the requested groups.
     * @return the RuleSet; empty when nothing is active.
     */
    public RuleSet<?> propertyRuleSet(String propertyName, RequestedGroups groups) {
        Assert.hasText(propertyName, "propertyName cannot be null/empty.");
        Assert.notNull(groups, "groups cannot be null.");
        return propertyRuleSets.computeIfAbsent(propertyName, n -> new ConcurrentHashMap<>()).computeIfAbsent(groups.getGroups(), key -> {
            List<Rule> rules = new ArrayList<>();

            for (MaterializedRule rule : propertyRules) {
                if (propertyName.equals(rule.getLeaf()) && rule.isActive(groups)) rules.add(rule.getRule());
            }

            return RuleSet.builder().with(name + "_" + propertyName).rules(rules).build();
        });
    }

    /**
     * The rules of a container element position active for the given groups, as a RuleSet. Cached
     * per position and expanded group set.
     *
     * @param node the position.
     * @param groups the requested groups.
     * @return the RuleSet; empty when nothing is active or the position has no constraints.
     */
    public RuleSet<?> elementRuleSet(ContainerElementDefinition node, RequestedGroups groups) {
        Assert.notNull(node, "node cannot be null.");
        Assert.notNull(groups, "groups cannot be null.");
        return elementRuleSets.computeIfAbsent(node, n -> new ConcurrentHashMap<>()).computeIfAbsent(groups.getGroups(), key -> {
            ElementRules element = byNode.get(node);
            List<Rule> rules = new ArrayList<>();

            if (element != null) {
                for (MaterializedRule rule : element.rules()) {
                    if (rule.isActive(groups)) rules.add(rule.getRule());
                }
            }

            return RuleSet.builder().with(name + "_" + RuleMaterializer.segment(node)).rules(rules).build();
        });
    }

    @Override
    public String toString() {
        return "BeanRules{" + getBeanClass().getName() + ", propertyRules=" + propertyRules.size()
                + ", elementPositions=" + elementRules.size() + ", classRules=" + classRules.size() + '}';
    }
}
