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

import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import jakarta.validation.groups.Default;
import org.rulii.bind.Binding;
import org.rulii.bind.NamedScope;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.registry.RuleRegistry;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.assembly.MaterializedRules;
import org.rulii.validation.beans.assembly.RulePlacement;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.GroupPlan;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.DeclarationConstraintSource;
import org.rulii.validation.beans.model.GroupConversions;
import org.rulii.validation.beans.model.ValueDeclaration;

import java.lang.annotation.ElementType;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The state of one step of a validation call, bound as {@code $traversal} and shared by every flow
 * the step runs: the groups being processed (a stack, since a {@code @ConvertGroup} cascade processes
 * other groups below the conversion point); the objects validated so far, per group set, so a shared
 * or cyclic object is validated once per group; the flow of a class, by runtime type; and the value
 * extractors, for {@code @Valid} on a container.
 *
 * <p>{@link #descend(RuleContext, Object, PropertyPath, GroupConversions)} is the one way down the
 * object graph: it converts the groups, rebinds {@code $this} and {@code $parent} in a fresh scope,
 * moves the collector's path to the object, and runs the flow of the object's runtime class, once per
 * converted pass and step (a conversion to a sequence runs the sequence for that object alone, stopping
 * at the first step with violations). A class never seen before is read at that moment, so its
 * declaration errors surface mid-call.</p>
 *
 * <p>{@link #forDefaultSequence} applies a class-level {@code @GroupSequence}: when the groups being
 * processed include {@code Default} and the class redefines it, the body runs for the remaining groups
 * once and then for each member of the sequence, stopping after the first member that added
 * violations.</p>
 *
 * <p>Created fresh by the entry point for each step of a call, or by a flow's parameter default when a
 * flow is run on its own (an embedded run), with the Default group.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Traversal {

    private final Deque<RequestedGroups> groups = new ArrayDeque<>();
    private final Function<Class<?>, RuleFlow<?>> flows;
    private final ValueExtractorRegistry extractors;
    private final RuleRegistry registry;
    private final TraversableResolver resolver;
    private final boolean failFast;
    private final Map<Object, Set<RequestedGroups>> validated = new IdentityHashMap<>();
    private final Map<Object, Map<String, Boolean>> resolutions = new IdentityHashMap<>();
    private final Set<Class<?>> currentGroups = new CurrentGroups();

    /**
     * Creates the traversal of a call whose {@code @Validate} names resolve through the context's registry.
     *
     * @param groups the groups being processed.
     * @param flows the flow of a bean class, by runtime type.
     * @param extractors the value extractors of the call.
     */
    public Traversal(RequestedGroups groups, Function<Class<?>, RuleFlow<?>> flows, ValueExtractorRegistry extractors) {
        this(groups, flows, extractors, null);
    }

    /**
     * Creates the traversal of a call.
     *
     * @param groups the groups being processed.
     * @param flows the flow of a bean class, by runtime type.
     * @param extractors the value extractors of the call.
     * @param registry the registry {@code @Validate} names resolve through; null to use the context's.
     */
    public Traversal(RequestedGroups groups, Function<Class<?>, RuleFlow<?>> flows, ValueExtractorRegistry extractors,
                     RuleRegistry registry) {
        this(groups, flows, extractors, registry, null);
    }

    /**
     * Creates the traversal of a call that asks a traversable resolver before touching a property.
     *
     * @param groups the groups being processed.
     * @param flows the flow of a bean class, by runtime type.
     * @param extractors the value extractors of the call.
     * @param registry the registry {@code @Validate} names resolve through; null to use the context's.
     * @param resolver the traversable resolver; null, or the traverse-all one, consults nothing.
     */
    public Traversal(RequestedGroups groups, Function<Class<?>, RuleFlow<?>> flows, ValueExtractorRegistry extractors,
                     RuleRegistry registry, TraversableResolver resolver) {
        this(groups, flows, extractors, registry, resolver, false);
    }

    /**
     * Creates the traversal of a call, fail-fast or not.
     *
     * @param groups the groups being processed.
     * @param flows the flow of a bean class, by runtime type.
     * @param extractors the value extractors of the call.
     * @param registry the registry {@code @Validate} names resolve through; null to use the context's.
     * @param resolver the traversable resolver; null, or the traverse-all one, consults nothing.
     * @param failFast whether the call stops at its first violation: every loop of the traversal
     *                 returns once the collector holds one (the rule sets stop on their own, built with
     *                 a stop condition).
     */
    public Traversal(RequestedGroups groups, Function<Class<?>, RuleFlow<?>> flows, ValueExtractorRegistry extractors,
                     RuleRegistry registry, TraversableResolver resolver, boolean failFast) {
        super();
        Assert.notNull(groups, "groups cannot be null.");
        Assert.notNull(flows, "flows cannot be null.");
        Assert.notNull(extractors, "extractors cannot be null.");
        this.groups.push(groups);
        this.flows = flows;
        this.extractors = extractors;
        this.registry = registry;
        this.resolver = TraversableResolvers.consults(resolver) ? resolver : null;
        this.failFast = failFast;
    }

    /**
     * Whether the call stops at its first violation.
     *
     * @return true for a fail-fast call.
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * Whether a fail-fast call has reported its violation: the loops of the traversal return when it has.
     *
     * @param collector the collector of the call.
     * @return true if nothing more is to be validated.
     */
    public boolean halted(BeanRuleViolations collector) {
        return failFast && collector != null && collector.size() > 0;
    }

    /**
     * The traversable resolver consulted before a property is validated or cascaded into.
     *
     * @return the resolver; null when everything is reachable and cascadable.
     */
    public TraversableResolver getTraversableResolver() {
        return resolver;
    }

    /**
     * Whether a traversable resolver is consulted.
     *
     * @return true if one is.
     */
    public boolean consultsResolver() {
        return resolver != null;
    }

    /**
     * Whether a property of the object in scope may be validated: the resolver's {@code isReachable},
     * asked once per object, property and element type within this traversal; always true without a
     * resolver. The property node, the root bean class and the path to the object come from the
     * collector of the context.
     *
     * @param ruleContext the context of the run.
     * @param bean the object hosting the property.
     * @param propertyName the property.
     * @param kind where the constraint or cascade is declared: a field, or a getter or record component.
     * @return true if reachable.
     * @throws TraversableResolverException if the resolver fails.
     */
    public boolean isReachable(RuleContext ruleContext, Object bean, String propertyName, ValueDeclaration.Kind kind) {
        return ask(true, ruleContext, bean, propertyName, kind);
    }

    /**
     * Whether a property rule of the object in scope may run: {@link #isReachable} for the rule's
     * property, at the element type of the declaration the constraint sits on. Rules that are not
     * property rules are not subject to the resolver.
     *
     * @param ruleContext the context of the run.
     * @param bean the object in scope.
     * @param rule the rule.
     * @return true if the rule may run.
     */
    public boolean isReachable(RuleContext ruleContext, Object bean, MaterializedRule rule) {
        Assert.notNull(rule, "rule cannot be null.");
        if (resolver == null || rule.getPlacement().getKind() != RulePlacement.Kind.PROPERTY) return true;
        return isReachable(ruleContext, bean, rule.getLeaf(), declarationKind(rule));
    }

    /**
     * Whether a reachable property of the object in scope may be cascaded into: the resolver's
     * {@code isCascadable}, asked once per object, property and element type within this traversal;
     * always true without a resolver. Ask {@link #isReachable} first, as the specification orders.
     *
     * @param ruleContext the context of the run.
     * @param bean the object hosting the property.
     * @param propertyName the property.
     * @param kind where the {@code @Valid} is declared.
     * @return true if cascadable.
     * @throws TraversableResolverException if the resolver fails.
     */
    public boolean isCascadable(RuleContext ruleContext, Object bean, String propertyName, ValueDeclaration.Kind kind) {
        return ask(false, ruleContext, bean, propertyName, kind);
    }

    private boolean ask(boolean reachable, RuleContext ruleContext, Object bean, String propertyName, ValueDeclaration.Kind kind) {
        if (resolver == null) return true;
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.hasText(propertyName, "propertyName cannot be null/empty.");
        Assert.notNull(kind, "kind cannot be null.");

        ElementType elementType = kind == ValueDeclaration.Kind.FIELD ? ElementType.FIELD : ElementType.METHOD;
        String key = (reachable ? "reachable:" : "cascadable:") + propertyName + ':' + elementType;
        Map<String, Boolean> answers = resolutions.computeIfAbsent(bean, k -> new HashMap<>());
        Boolean known = answers.get(key);

        if (known != null) return known;

        BeanRuleViolations collector = collector(ruleContext);
        Class<?> rootBeanClass = collector != null ? collector.getRootBeanClass() : (bean != null ? bean.getClass() : null);
        PropertyPath pathToObject = collector != null ? collector.getCurrentPath() : PropertyPath.root();
        Path.Node node = PropertyPath.root().property(propertyName).getLeafNode();
        boolean result;

        try {
            result = reachable ? resolver.isReachable(bean, node, rootBeanClass, pathToObject, elementType)
                    : resolver.isCascadable(bean, node, rootBeanClass, pathToObject, elementType);
        } catch (RuntimeException e) {
            throw new TraversableResolverException(reachable ? "isReachable" : "isCascadable", bean, propertyName, resolver, e);
        }

        answers.put(key, result);
        return result;
    }

    /**
     * Where a rule's constraint is declared; the property's primary declaration when the source is not
     * a declaration (which does not happen for a property rule).
     */
    private static ValueDeclaration.Kind declarationKind(MaterializedRule rule) {
        if (rule.getDefinition().getConstraintSource() instanceof DeclarationConstraintSource source) return source.getDeclaration().getKind();
        return ValueDeclaration.Kind.FIELD;
    }

    /**
     * The registry {@code @Validate} names resolve through, when the call brought its own.
     *
     * @return the registry; null to use the context's.
     */
    public RuleRegistry getRuleRegistry() {
        return registry;
    }

    /**
     * The groups being processed at this point of the traversal.
     *
     * @return the current groups.
     */
    public RequestedGroups getGroups() {
        return groups.peek();
    }

    /**
     * The groups being processed, as a live, read-only view: it follows the traversal through default
     * group sequences and group conversions. Bound as {@code $groups} for rules that branch on the
     * groups requested.
     *
     * @return the current groups.
     */
    public Set<Class<?>> getCurrentGroups() {
        return currentGroups;
    }

    /**
     * The value extractors of the call.
     *
     * @return the registry.
     */
    public ValueExtractorRegistry getExtractors() {
        return extractors;
    }

    /**
     * The flow validating a bean class.
     *
     * @param beanClass the runtime class of an object.
     * @return its flow.
     */
    public RuleFlow<?> getFlow(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        RuleFlow<?> result = flows.apply(beanClass);
        Assert.notNull(result, "no flow for [" + beanClass.getName() + "]");
        return result;
    }

    /**
     * The property and class rules of a bean class active for the current groups.
     *
     * @param rules the bean rules.
     * @return the RuleSet to run.
     */
    public RuleSet<?> ruleSet(BeanRules rules) {
        Assert.notNull(rules, "rules cannot be null.");
        return rules.ruleSet(getGroups());
    }

    /**
     * The rules of a container element position active for the current groups.
     *
     * @param rules the rules of the class or executable in scope.
     * @param node the position.
     * @return the RuleSet to run per element.
     */
    public RuleSet<?> elementRuleSet(MaterializedRules rules, ContainerElementDefinition node) {
        Assert.notNull(rules, "rules cannot be null.");
        Assert.notNull(node, "node cannot be null.");
        return rules.elementRuleSet(node, getGroups());
    }

    /**
     * Records an object as validated under the current groups.
     *
     * @param bean the object.
     * @return true if it had not been validated under these groups yet.
     */
    public boolean markValidated(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        return validated.computeIfAbsent(bean, key -> new HashSet<>()).add(getGroups());
    }

    /**
     * Whether an object was validated under the current groups by this traversal.
     *
     * @param bean the object.
     * @return true if validated.
     */
    public boolean isValidated(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        Set<RequestedGroups> seen = validated.get(bean);
        return seen != null && seen.contains(getGroups());
    }

    /**
     * Runs a body with other groups current, restoring the previous ones afterwards.
     *
     * @param step the groups to process.
     * @param body what to run.
     */
    public void inGroups(RequestedGroups step, Runnable body) {
        Assert.notNull(step, "step cannot be null.");
        Assert.notNull(body, "body cannot be null.");
        groups.push(step);
        try {
            body.run();
        } finally {
            groups.pop();
        }
    }

    /**
     * Whether a class-level default group sequence applies at this point: there is one, and the
     * current groups include {@code Default}.
     *
     * @param defaultSequence the class's redefined Default; null when the class does not redefine it.
     * @return true if the body of {@link #forDefaultSequence} runs the sequence.
     */
    public boolean appliesDefaultSequence(List<Class<?>> defaultSequence) {
        return defaultSequence != null && getGroups().contains(Default.class);
    }

    /**
     * Runs a body for the current groups, honouring a class-level redefinition of {@code Default}:
     * when it applies, the body runs for the groups other than {@code Default} once (if any), then for
     * each member of the sequence in order, each with that group current, stopping after the first
     * member whose run added violations to the collector. Otherwise the body runs once with the
     * current groups.
     *
     * @param ruleContext the context, with the collector bound.
     * @param defaultSequence the class's redefined Default; null when the class does not redefine it.
     * @param body what to run for one group set.
     */
    public void forDefaultSequence(RuleContext ruleContext, List<Class<?>> defaultSequence, Consumer<RequestedGroups> body) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.notNull(body, "body cannot be null.");
        RequestedGroups current = getGroups();

        if (!appliesDefaultSequence(defaultSequence)) {
            body.accept(current);
            return;
        }

        BeanRuleViolations collector = collector(ruleContext);
        Assert.notNull(collector, "a default group sequence needs a path-aware collector bound as [" + BindingPlan.RULE_VIOLATIONS + "].");
        RequestedGroups rest = current.without(Default.class);

        if (!rest.isEmpty()) inGroups(rest, () -> body.accept(rest));
        if (halted(collector)) return;

        for (Class<?> member : defaultSequence) {
            RequestedGroups step = RequestedGroups.of(member);
            int before = collector.size();
            inGroups(step, () -> body.accept(step));
            if (collector.size() > before) break;
        }
    }

    /**
     * Validates an object reached from the object in scope, at the given path, with the current groups.
     *
     * @param ruleContext the context of the run.
     * @param value the object to validate; null validates nothing.
     * @param path the path of the object from the root.
     * @see #descend(RuleContext, Object, PropertyPath, GroupConversions)
     */
    public void descend(RuleContext ruleContext, Object value, PropertyPath path) {
        descend(ruleContext, value, path, GroupConversions.NONE);
    }

    /**
     * Validates an object reached from the object in scope, at the given path: nothing for null or an
     * object already validated under the same groups; otherwise the groups are converted, {@code $this}
     * and {@code $parent} are rebound in a fresh scope, the collector's path moves to the object, and
     * the flow of the object's runtime class runs once per converted step. A conversion to a sequence
     * runs the sequence for that object alone, stopping at the first step that added violations.
     * Scope, path and groups are restored afterwards, whatever happens.
     *
     * @param ruleContext the context of the run.
     * @param value the object to validate; null validates nothing.
     * @param path the path of the object from the root.
     * @param conversions the {@code @ConvertGroup} conversions of the cascade point.
     */
    public void descend(RuleContext ruleContext, Object value, PropertyPath path, GroupConversions conversions) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.notNull(path, "path cannot be null.");
        Assert.notNull(conversions, "conversions cannot be null.");

        if (value == null || halted(collector(ruleContext))) return;

        if (conversions.isEmpty()) {
            descendIn(ruleContext, value, path);
            return;
        }

        BeanRuleViolations collector = collector(ruleContext);
        Assert.notNull(collector, "group conversion needs a path-aware collector bound as [" + BindingPlan.RULE_VIOLATIONS + "].");
        GroupPlan plan = GroupPlan.of(conversions.convert(getGroups()));

        for (GroupPlan.Pass pass : plan.getPasses()) {
            for (RequestedGroups step : pass.steps()) {
                int before = collector.size();
                inGroups(step, () -> descendIn(ruleContext, value, path));
                if (pass.sequence() && collector.size() > before) break;
            }
        }
    }

    private void descendIn(RuleContext ruleContext, Object value, PropertyPath path) {
        if (isValidated(value)) return;

        BeanRuleViolations collector = collector(ruleContext);
        Assert.notNull(collector, "descent needs a path-aware collector bound as [" + BindingPlan.RULE_VIOLATIONS
                + "]; run through the bean flow.");
        RuleFlow<?> flow = getFlow(value.getClass());
        ScopedBindings bindings = ruleContext.getBindings();
        Binding<Object> current = bindings.getBinding(BindingPlan.THIS);
        NamedScope scope = bindings.addScope();

        try (BeanRuleViolations.Frame ignored = collector.enter(path, value)) {
            scope.getBindings().bind(Binding.builder().with(BindingPlan.THIS, Object.class).value(value).build());
            if (current != null) {
                scope.getBindings().bind(Binding.builder().with(BindingPlan.PARENT, Object.class).value(current.getValue()).build());
            }
            flow.run(ruleContext);
        } finally {
            bindings.removeScope(scope);
        }
    }

    /**
     * The traversal bound in a context.
     *
     * @param ruleContext the context.
     * @return the traversal.
     * @throws IllegalArgumentException if none is bound.
     */
    public static Traversal of(RuleContext ruleContext) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Binding<Traversal> binding = ruleContext.getBindings().getBinding(BindingPlan.TRAVERSAL);
        Assert.isTrue(binding != null && binding.getValue() != null, "no traversal is bound; bind [" + BindingPlan.TRAVERSAL + "]");
        return binding.getValue();
    }

    /**
     * Any violations collector bound in a context, path-aware or not.
     *
     * @param ruleContext the context.
     * @return the first collector bound; null if none.
     */
    public static RuleViolations anyCollector(RuleContext ruleContext) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        List<Binding<RuleViolations>> bindings = ruleContext.getBindings().getBindings(RuleViolations.class);
        return bindings.isEmpty() ? null : bindings.get(0).getValue();
    }

    /**
     * The path-aware collector the rules of a context report to, if that is what is bound: the
     * only {@link RuleViolations} binding, and a {@link BeanRuleViolations}.
     *
     * @param ruleContext the context.
     * @return the collector; null when none or a plain one is bound.
     */
    public static BeanRuleViolations collector(RuleContext ruleContext) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        List<Binding<RuleViolations>> bindings = ruleContext.getBindings().getBindings(RuleViolations.class);
        return bindings.size() == 1 && bindings.get(0).getValue() instanceof BeanRuleViolations collector ? collector : null;
    }

    @Override
    public String toString() {
        return "Traversal{groups=" + getGroups() + ", validated=" + validated.size() + '}';
    }

    /**
     * The live view behind {@link #getCurrentGroups()}.
     */
    private final class CurrentGroups extends AbstractSet<Class<?>> {

        @Override
        public Iterator<Class<?>> iterator() {
            return Collections.unmodifiableSet(getGroups().getGroups()).iterator();
        }

        @Override
        public int size() {
            return getGroups().getGroups().size();
        }

        @Override
        public boolean contains(Object o) {
            return getGroups().getGroups().contains(o);
        }
    }
}
