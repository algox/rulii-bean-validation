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

import org.rulii.ruleset.RuleSet;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.ContainerElementDefinition;

import java.util.List;

/**
 * A set of rules materialized for one scope the traversal enters: a bean class ({@link BeanRules})
 * or a method or constructor ({@link ExecutableRules}). The violations collector asks the rules in
 * scope where a reporting rule is placed, and the container walk asks for the rules of a position.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface MaterializedRules {

    /**
     * A name for the rule sets built from these rules.
     *
     * @return the name.
     */
    String getName();

    /**
     * The bean class the rules belong to: the class itself, or the class owning the executable.
     *
     * @return the bean class.
     */
    Class<?> getBeanClass();

    /**
     * Every rule, hosted ones included.
     *
     * @return all rules.
     */
    List<MaterializedRule> getRules();

    /**
     * The rule with the given name.
     *
     * @param name the rule name.
     * @return the rule; null if there is none.
     */
    MaterializedRule getRule(String name);

    /**
     * Whether a container element position has rules.
     *
     * @param node the position.
     * @return true if it has.
     */
    boolean hasElementRules(ContainerElementDefinition node);

    /**
     * The rules of a container element position active for the given groups, as a RuleSet to run per
     * element.
     *
     * @param node the position.
     * @param groups the requested groups.
     * @return the RuleSet; empty when the position has no active rules.
     */
    RuleSet<?> elementRuleSet(ContainerElementDefinition node, RequestedGroups groups);
}
