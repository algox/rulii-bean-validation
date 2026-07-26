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

import java.util.Collections;
import java.util.Set;

/**
 * One materialized constraint rule paired with the groups that activate it. Rules are
 * built once per class and shared; group filtering happens at RuleSet-assembly time
 * (and per element during container walks) by intersecting these groups with the
 * call's expanded set.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MaterializedRule {

    private final Rule rule;
    private final Set<Class<?>> groups;

    MaterializedRule(Rule rule, Set<Class<?>> groups) {
        super();
        Assert.notNull(rule, "rule cannot be null.");
        Assert.notNull(groups, "groups cannot be null.");
        this.rule = rule;
        this.groups = Collections.unmodifiableSet(groups);
    }

    /**
     * Retrieves the materialized rule.
     *
     * @return the rule.
     */
    public Rule getRule() {
        return rule;
    }

    /**
     * Retrieves the groups that activate this rule.
     *
     * @return an unmodifiable set of groups.
     */
    public Set<Class<?>> getGroups() {
        return groups;
    }

    /**
     * Determines whether this rule is active under the given expanded group set.
     *
     * @param expandedGroups the call's expanded groups.
     * @return true if any of this rule's groups is requested; false otherwise.
     */
    public boolean isActive(Set<Class<?>> expandedGroups) {
        for (Class<?> group : groups) {
            if (expandedGroups.contains(group)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "MaterializedRule{rule=" + rule.getName() + ", groups=" + groups.size() + '}';
    }
}
