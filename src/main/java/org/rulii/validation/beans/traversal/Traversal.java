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
import org.rulii.validation.beans.assembly.MaterializedRules;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.GroupPlan;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.GroupConversions;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
    private final Map<Object, Set<RequestedGroups>> validated = new IdentityHashMap<>();

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
        super();
        Assert.notNull(groups, "groups cannot be null.");
        Assert.notNull(flows, "flows cannot be null.");
        Assert.notNull(extractors, "extractors cannot be null.");
        this.groups.push(groups);
        this.flows = flows;
        this.extractors = extractors;
        this.registry = registry;
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

        if (value == null) return;

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
}
