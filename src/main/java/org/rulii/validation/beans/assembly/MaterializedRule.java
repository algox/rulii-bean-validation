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
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintTarget;
import org.rulii.validation.beans.model.ConstraintDefinition;

/**
 * One rulii rule materialized from a constraint, with what the runtime needs to know about it: the
 * constraint it came from, the binding it reads, the property it reports at, the composition host it
 * belongs to, and the raw message template for render-time resolution.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MaterializedRule {

    private final Rule rule;
    private final ConstraintDefinition definition;
    private final ConstraintDefinition host;
    private final ConstraintTarget target;
    private final String leaf;

    /**
     * Creates the materialized rule.
     *
     * @param rule the built rule.
     * @param definition the constraint the rule was materialized from.
     * @param host the nearest enclosing {@code @ReportAsSingleViolation} composition host; null when
     *             the rule reports on its own.
     * @param target the target the rule was built for.
     * @param leaf the property the violations report at; null for class-level and container element
     *             rules, whose path is set by the traversal.
     */
    public MaterializedRule(Rule rule, ConstraintDefinition definition, ConstraintDefinition host,
                            ConstraintTarget target, String leaf) {
        super();
        Assert.notNull(rule, "rule cannot be null.");
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(target, "target cannot be null.");
        this.rule = rule;
        this.definition = definition;
        this.host = host;
        this.target = target;
        this.leaf = leaf;
    }

    public Rule getRule() {
        return rule;
    }

    /**
     * Name of the rule, unique within the bean class.
     *
     * @return the rule name.
     */
    public String getName() {
        return rule.getDefinition().getName();
    }

    /**
     * The constraint the rule was materialized from; for a composing constraint, the composing
     * definition (which carries the host's groups and payload).
     *
     * @return the definition.
     */
    public ConstraintDefinition getDefinition() {
        return definition;
    }

    /**
     * The nearest enclosing composition marked {@code @ReportAsSingleViolation}, whose single
     * violation stands in for this rule's.
     *
     * @return the host definition; null when the rule reports on its own.
     */
    public ConstraintDefinition getHost() {
        return host;
    }

    /**
     * Whether the rule's violations are reported by a composition host instead of individually.
     *
     * @return true if hosted.
     */
    public boolean isHosted() {
        return host != null;
    }

    public ConstraintTarget getTarget() {
        return target;
    }

    /**
     * The binding the rule reads its value from.
     *
     * @return the binding name.
     */
    public String getBindingName() {
        return target.getBindingName();
    }

    /**
     * The property the rule's violations report at.
     *
     * @return the property name; null for class-level and container element rules.
     */
    public String getLeaf() {
        return leaf;
    }

    /**
     * The constraint's message template as declared, before translation: the key or literal the
     * render-time resolver works from.
     *
     * @return the raw template.
     */
    public String getMessageTemplate() {
        return definition.getMessage();
    }

    /**
     * Whether the rule runs for the given requested groups.
     *
     * @param groups the requested groups.
     * @return true if the constraint's groups intersect them.
     */
    public boolean isActive(RequestedGroups groups) {
        Assert.notNull(groups, "groups cannot be null.");
        return groups.activates(definition);
    }

    @Override
    public String toString() {
        return "MaterializedRule{" + getName() + " <- " + definition.getAnnotationType().getSimpleName()
                + " on " + definition.getConstraintSource().describe() + (host != null ? ", hosted by " + host.getAnnotationType().getSimpleName() : "") + '}';
    }
}
