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
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.ExecutableDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rules materialized for one method or constructor: the parameter rules in parameter order, the
 * cross-parameter rules, the return-value rules, and the rules of each constrained container element
 * position within a parameter or the return value; plus the {@link ExecutableBindingPlan} they were
 * built against.
 *
 * <p>Parameter validation runs {@link #parameterRuleSet(RequestedGroups)}: the parameter and
 * cross-parameter rules active for the request. Return-value validation runs
 * {@link #returnValueRuleSet(RequestedGroups)}. Container positions run per element through
 * {@link #elementRuleSet(ContainerElementDefinition, RequestedGroups)}. The RuleSets are cached per
 * expanded group set and plain, as the bean rule sets are.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ExecutableRules implements MaterializedRules {

    /**
     * The rules of one container element position within a parameter or the return value.
     *
     * @param node the position.
     * @param rules the rules, in declaration order; never empty.
     */
    public record ElementRules(ContainerElementDefinition node, List<MaterializedRule> rules) {

        public ElementRules {
            Assert.notNull(node, "node cannot be null.");
            Assert.notNull(rules, "rules cannot be null.");
            rules = List.copyOf(rules);
        }
    }

    private final ExecutableDefinition definition;
    private final ExecutableBindingPlan bindingPlan;
    private final List<MaterializedRule> parameterRules;
    private final List<MaterializedRule> crossParameterRules;
    private final List<MaterializedRule> returnValueRules;
    private final List<ElementRules> elementRules;
    private final Map<String, MaterializedRule> byName;
    private final Map<ContainerElementDefinition, ElementRules> byNode;
    private final String name;
    private final ConcurrentHashMap<Set<Class<?>>, RuleSet<?>> parameterRuleSets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Set<Class<?>>, RuleSet<?>> returnValueRuleSets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContainerElementDefinition, ConcurrentHashMap<Set<Class<?>>, RuleSet<?>>> elementRuleSets = new ConcurrentHashMap<>();

    ExecutableRules(ExecutableDefinition definition, ExecutableBindingPlan bindingPlan, List<MaterializedRule> parameterRules,
                    List<MaterializedRule> crossParameterRules, List<MaterializedRule> returnValueRules, List<ElementRules> elementRules) {
        super();
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(bindingPlan, "bindingPlan cannot be null.");
        this.definition = definition;
        this.bindingPlan = bindingPlan;
        this.parameterRules = List.copyOf(parameterRules);
        this.crossParameterRules = List.copyOf(crossParameterRules);
        this.returnValueRules = List.copyOf(returnValueRules);
        this.elementRules = List.copyOf(elementRules);

        Map<String, MaterializedRule> names = new LinkedHashMap<>();

        for (MaterializedRule rule : this.parameterRules) {
            register(names, rule);
        }

        for (MaterializedRule rule : this.crossParameterRules) {
            register(names, rule);
        }

        for (MaterializedRule rule : this.returnValueRules) {
            register(names, rule);
        }

        for (ElementRules element : this.elementRules) {
            for (MaterializedRule rule : element.rules()) {
                register(names, rule);
            }
        }

        this.byName = Collections.unmodifiableMap(names);

        Map<ContainerElementDefinition, ElementRules> nodes = new LinkedHashMap<>();

        for (ElementRules element : this.elementRules) {
            nodes.put(element.node(), element);
        }

        this.byNode = Collections.unmodifiableMap(nodes);
        String owner = definition.getOwner().getSimpleName().isEmpty() ? "Bean" : definition.getOwner().getSimpleName();
        this.name = owner + "_" + definition.getName() + "Rules";
    }

    private static void register(Map<String, MaterializedRule> names, MaterializedRule rule) {
        MaterializedRule previous = names.putIfAbsent(rule.getName(), rule);

        if (previous != null) {
            throw new ConstraintMapperException(rule.getDefinition().getAnnotationType(),
                    rule.getDefinition().getConstraintSource().describe(), "rule name [" + rule.getName()
                    + "] is already used by " + previous + "; rule names must be unique within an executable.");
        }

        for (MaterializedRule hosted : rule.getHostedRules()) {
            register(names, hosted);
        }
    }

    public ExecutableDefinition getDefinition() {
        return definition;
    }

    @Override
    public Class<?> getBeanClass() {
        return definition.getOwner();
    }

    public ExecutableBindingPlan getBindingPlan() {
        return bindingPlan;
    }

    /**
     * Rules of the parameter constraints, in parameter order then declaration order.
     *
     * @return the parameter rules.
     */
    public List<MaterializedRule> getParameterRules() {
        return parameterRules;
    }

    /**
     * Rules of the cross-parameter constraints, in declaration order.
     *
     * @return the cross-parameter rules.
     */
    public List<MaterializedRule> getCrossParameterRules() {
        return crossParameterRules;
    }

    /**
     * Rules of the return-value constraints, in declaration order over the overriding chain.
     *
     * @return the return-value rules.
     */
    public List<MaterializedRule> getReturnValueRules() {
        return returnValueRules;
    }

    /**
     * Rules of the constrained container element positions, parameters first then the return value,
     * position order within each (depth-first).
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

    @Override
    public boolean hasElementRules(ContainerElementDefinition node) {
        return getElementRules(node) != null;
    }

    @Override
    public List<MaterializedRule> getRules() {
        return new ArrayList<>(byName.values());
    }

    @Override
    public MaterializedRule getRule(String name) {
        Assert.notNull(name, "name cannot be null.");
        return byName.get(name);
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * The parameter and cross-parameter rules active for the given groups.
     *
     * @param groups the requested groups.
     * @return the RuleSet to run for parameter validation; cached per group set.
     */
    public RuleSet<?> parameterRuleSet(RequestedGroups groups) {
        Assert.notNull(groups, "groups cannot be null.");
        return parameterRuleSets.computeIfAbsent(groups.getGroups(), key -> {
            List<Rule> rules = new ArrayList<>();

            for (MaterializedRule rule : parameterRules) {
                if (rule.isActive(groups)) rules.add(rule.getRule());
            }

            for (MaterializedRule rule : crossParameterRules) {
                if (rule.isActive(groups)) rules.add(rule.getRule());
            }

            return RuleSet.builder().with(name + "_parameters").rules(rules).build();
        });
    }

    /**
     * The return-value rules active for the given groups.
     *
     * @param groups the requested groups.
     * @return the RuleSet to run for return-value validation; cached per group set.
     */
    public RuleSet<?> returnValueRuleSet(RequestedGroups groups) {
        Assert.notNull(groups, "groups cannot be null.");
        return returnValueRuleSets.computeIfAbsent(groups.getGroups(), key -> {
            List<Rule> rules = new ArrayList<>();

            for (MaterializedRule rule : returnValueRules) {
                if (rule.isActive(groups)) rules.add(rule.getRule());
            }

            return RuleSet.builder().with(name + "_returnValue").rules(rules).build();
        });
    }

    @Override
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
        return "ExecutableRules{" + definition + ", parameterRules=" + parameterRules.size() + ", crossParameterRules="
                + crossParameterRules.size() + ", returnValueRules=" + returnValueRules.size() + ", elementPositions=" + elementRules.size() + '}';
    }
}
