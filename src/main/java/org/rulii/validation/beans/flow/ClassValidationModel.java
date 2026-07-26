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
package org.rulii.validation.beans.flow;

import org.rulii.lib.spring.util.Assert;
import org.rulii.rule.Rule;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.beans.metadata.BeanMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything materialized once per bean class: the validation RuleFlow, the shared
 * constraint rules with their activating groups, the rule-name-to-leaf table for path
 * attribution, and the per-(expanded-groups) RuleSet cache. Immutable and shared across
 * calls; the RuleSet cache is the only mutable member and is concurrent.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ClassValidationModel {

    private final Class<?> beanClass;
    private final BeanMetadata metadata;
    private final String ruleSetName;
    private final List<MaterializedRule> ownRules;
    private final Map<String, String> leafTable;
    private final ConcurrentHashMap<Set<Class<?>>, Optional<RuleSet<?>>> ruleSetCache = new ConcurrentHashMap<>();
    private RuleFlow<?> flow;

    ClassValidationModel(Class<?> beanClass, BeanMetadata metadata, String ruleSetName,
                         List<MaterializedRule> ownRules, Map<String, String> leafTable) {
        super();
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(metadata, "metadata cannot be null.");
        Assert.hasText(ruleSetName, "ruleSetName cannot be null or empty.");
        Assert.notNull(ownRules, "ownRules cannot be null.");
        Assert.notNull(leafTable, "leafTable cannot be null.");
        this.beanClass = beanClass;
        this.metadata = metadata;
        this.ruleSetName = ruleSetName;
        this.ownRules = Collections.unmodifiableList(ownRules);
        this.leafTable = Collections.unmodifiableMap(leafTable);
    }

    void setFlow(RuleFlow<?> flow) {
        Assert.notNull(flow, "flow cannot be null.");
        Assert.isTrue(this.flow == null, "flow is already set.");
        this.flow = flow;
    }

    /**
     * Retrieves the bean class this model describes.
     *
     * @return the bean class.
     */
    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * Retrieves the bean's validation metadata.
     *
     * @return the metadata.
     */
    public BeanMetadata getMetadata() {
        return metadata;
    }

    /**
     * Retrieves the per-class validation RuleFlow (the R10.4b embeddable artifact).
     *
     * @return the rule flow.
     */
    public RuleFlow<?> getFlow() {
        return flow;
    }

    /**
     * Retrieves this class's constraint rules (property rules in declaration order,
     * then class-level rules) with their activating groups.
     *
     * @return an unmodifiable list of materialized rules.
     */
    public List<MaterializedRule> getOwnRules() {
        return ownRules;
    }

    /**
     * Retrieves the rule-name-to-leaf-property table used for violation path
     * attribution. Rules without an entry report at the object path.
     *
     * @return an unmodifiable map of rule name to leaf property name.
     */
    public Map<String, String> getLeafTable() {
        return leafTable;
    }

    /**
     * Retrieves (building and caching on first request) the RuleSet of this class's
     * rules active under the given expanded group set.
     *
     * @param expandedGroups the call's expanded groups.
     * @return the group-filtered RuleSet; null when no rule is active.
     */
    public RuleSet<?> selectRuleSet(Set<Class<?>> expandedGroups) {
        Assert.notNull(expandedGroups, "expandedGroups cannot be null.");

        return ruleSetCache.computeIfAbsent(expandedGroups, groups -> {
            List<Rule> active = new ArrayList<>(ownRules.size());

            for (MaterializedRule rule : ownRules) {
                if (rule.isActive(groups)) active.add(rule.getRule());
            }

            if (active.isEmpty()) return Optional.empty();
            return Optional.of(RuleSet.builder().with(ruleSetName).rules(active).build());
        }).orElse(null);
    }

    @Override
    public String toString() {
        return "ClassValidationModel{beanClass=" + beanClass.getName()
                + ", ownRules=" + ownRules.size()
                + ", cachedRuleSets=" + ruleSetCache.size()
                + '}';
    }
}
