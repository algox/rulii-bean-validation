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

import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.function.Function;
import org.rulii.rule.Rule;
import org.rulii.validation.BindingFunction;
import org.rulii.validation.ValueValidationRule;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintTarget;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.RuleDeclaration;

import java.util.List;

/**
 * One rulii rule materialized from a declaration (a constraint or an {@code @Expr}), with what the
 * runtime needs to know about it: the declaration it came from, the binding it reads, where it
 * reports ({@link RulePlacement}), the composition host it belongs to, and the raw message template
 * for render-time resolution.
 *
 * <p>A {@code @ReportAsSingleViolation} composition is one materialized rule whose
 * {@link #getHostedRules() hosted rules} are the rules of its composing constraints: the rule runs
 * them and reports once for all of them. The hosted rules are not run on their own.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MaterializedRule {

    private static final String CORE_RULES_PACKAGE = "org.rulii.validation.rules.";

    private final Rule rule;
    private final RuleDeclaration definition;
    private final ConstraintDefinition host;
    private final ConstraintTarget target;
    private final RulePlacement placement;
    private final List<MaterializedRule> hostedRules;
    private final PropertyAccessor accessor;
    private final ValueValidationRule check;

    /**
     * Creates the materialized rule.
     *
     * @param rule the built rule.
     * @param definition the declaration the rule was materialized from.
     * @param host the nearest enclosing {@code @ReportAsSingleViolation} composition host; null when
     *             the rule reports on its own.
     * @param target the target the rule was built for.
     * @param placement where the violations report, relative to the path the rule runs at.
     */
    public MaterializedRule(Rule rule, RuleDeclaration definition, ConstraintDefinition host,
                            ConstraintTarget target, RulePlacement placement) {
        this(rule, definition, host, target, placement, List.of());
    }

    /**
     * Creates a materialized rule that runs hosted rules and reports once for them.
     *
     * @param rule the built rule.
     * @param definition the declaration the rule was materialized from.
     * @param host the nearest enclosing {@code @ReportAsSingleViolation} composition host; null when
     *             the rule reports on its own.
     * @param target the target the rule was built for.
     * @param placement where the violations report, relative to the path the rule runs at.
     * @param hostedRules the rules this one runs and reports for; empty for an ordinary rule.
     */
    public MaterializedRule(Rule rule, RuleDeclaration definition, ConstraintDefinition host,
                            ConstraintTarget target, RulePlacement placement, List<MaterializedRule> hostedRules) {
        this(rule, definition, host, target, placement, hostedRules, null);
    }

    /**
     * Creates a materialized rule, saying how its value is read.
     *
     * @param rule the built rule.
     * @param definition the declaration the rule was materialized from.
     * @param host the nearest enclosing {@code @ReportAsSingleViolation} composition host; null when
     *             the rule reports on its own.
     * @param target the target the rule was built for.
     * @param placement where the violations report, relative to the path the rule runs at.
     * @param hostedRules the rules this one runs and reports for; empty for an ordinary rule.
     * @param accessor the declaration the rule reads off the object in scope; null when it reads its binding.
     */
    public MaterializedRule(Rule rule, RuleDeclaration definition, ConstraintDefinition host, ConstraintTarget target,
                            RulePlacement placement, List<MaterializedRule> hostedRules, PropertyAccessor accessor) {
        super();
        Assert.notNull(rule, "rule cannot be null.");
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(target, "target cannot be null.");
        Assert.notNull(placement, "placement cannot be null.");
        Assert.notNull(hostedRules, "hostedRules cannot be null.");
        this.rule = rule;
        this.definition = definition;
        this.host = host;
        this.target = target;
        this.placement = placement;
        this.hostedRules = List.copyOf(hostedRules);
        this.accessor = accessor;
        this.check = checkable(rule, target);
    }

    /**
     * The validator behind a rule that can be asked directly: one of rulii's own validation rules
     * ({@code org.rulii.validation.rules}), built the way their builders build them, with a pre-condition, a
     * condition, an otherwise action and nothing else, and reading the value function the materializer handed
     * the mapper ({@link ConstraintTarget#value()}), which is the one read {@link #read} reproduces. Asking it
     * ({@link ValueValidationRule#check}) and reporting for it ({@link ValueValidationRule#buildViolation}) is
     * then what a run of the rule does. Anything else (an {@code @Expr}, a composition, a constraint
     * validator, a rule a mapper built over a value function of its own) is run by the engine.
     */
    private static ValueValidationRule checkable(Rule rule, ConstraintTarget target) {
        Object ruleTarget = rule.getTarget();

        if (!(ruleTarget instanceof ValueValidationRule validator)) return null;
        if (!ruleTarget.getClass().getName().startsWith(CORE_RULES_PACKAGE)) return null;
        // A rule builder keeps the function inside a BindingFunction and takes its name as the value name.
        Function<?> site = target.value();
        Function<?> read = site instanceof BindingFunction<?> binding ? binding.getTargetFunction() : site;
        if (validator.getValueFunction() != read) return null;
        if (rule.getPreCondition() == null || rule.getCondition() == null || rule.getOtherwiseAction() == null) return null;
        return rule.getActions().isEmpty() ? validator : null;
    }

    /**
     * The validator to ask directly for this rule's verdict, in place of running the rule.
     *
     * @return the validator; null when the rule must be run by the engine.
     * @see RulePlan
     */
    public ValueValidationRule getCheck() {
        return check;
    }

    /**
     * The declaration the rule reads off the object in scope: a constraint written on a field of a property
     * that is bound through its getter reads the field.
     *
     * @return the accessor; null when the rule reads its {@link #getBindingName() binding}.
     */
    public PropertyAccessor getAccessor() {
        return accessor;
    }

    /**
     * Reads the value the rule validates, as the rule's value function reads it: the declaration off the
     * object in scope, else the rule's binding.
     *
     * @param ruleContext the context of the run.
     * @param bean the object in scope ({@code $this}); may be null when the rule reads its binding.
     * @return the value; may be null.
     * @throws org.rulii.bind.NoSuchBindingException if the binding the rule reads is not bound.
     */
    public Object read(RuleContext ruleContext, Object bean) {
        return accessor != null ? accessor.read(bean) : ruleContext.getBindings().getValue(target.getBindingName());
    }

    /**
     * The rules this one runs and reports for, when it is a {@code @ReportAsSingleViolation}
     * composition.
     *
     * @return the hosted rules; empty for an ordinary rule.
     */
    public List<MaterializedRule> getHostedRules() {
        return hostedRules;
    }

    /**
     * Whether this rule is a {@code @ReportAsSingleViolation} composition.
     *
     * @return true if it hosts rules.
     */
    public boolean isComposition() {
        return !hostedRules.isEmpty();
    }

    public Rule getRule() {
        return rule;
    }

    /**
     * Name of the rule, unique within the bean class or executable.
     *
     * @return the rule name.
     */
    public String getName() {
        return rule.getDefinition().getName();
    }

    /**
     * The declaration the rule was materialized from: the constraint (for a composing constraint, the
     * composing definition, which carries the host's groups and payload) or the {@code @Expr}.
     *
     * @return the definition.
     */
    public RuleDeclaration getDefinition() {
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
     * Where the rule's violations report, relative to the path the rule runs at.
     *
     * @return the placement.
     */
    public RulePlacement getPlacement() {
        return placement;
    }

    /**
     * The property the rule's violations report at.
     *
     * @return the property name; null for every rule but a property rule.
     */
    public String getLeaf() {
        return placement.getPropertyName();
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
