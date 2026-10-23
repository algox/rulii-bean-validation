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
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.ContainerElementDefinition;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The state of one validation call, bound as {@code $traversal} and shared by every flow the call
 * runs: the requested groups, fixed for the whole call; the objects validated so far, so a shared or
 * cyclic object is validated once; the flow of a class, by runtime type; and the value extractors,
 * for {@code @Valid} on a container.
 *
 * <p>{@link #descend(RuleContext, Object, PropertyPath)} is the one way down the object graph: it
 * rebinds {@code $this} and {@code $parent} in a fresh scope, moves the collector's path to the
 * object, and runs the flow of the object's runtime class. A class never seen before is read at
 * that moment, so its declaration errors surface mid-call.</p>
 *
 * <p>Created fresh by the entry point for each call, or by a flow's parameter default when a flow
 * is run on its own (an embedded run), with the Default group.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Traversal {

    private final RequestedGroups groups;
    private final Function<Class<?>, RuleFlow<?>> flows;
    private final ValueExtractorRegistry extractors;
    private final RuleRegistry registry;
    private final Set<Object> validated = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Creates the traversal of a call whose {@code @Validate} names resolve through the context's registry.
     *
     * @param groups the requested groups.
     * @param flows the flow of a bean class, by runtime type.
     * @param extractors the value extractors of the call.
     */
    public Traversal(RequestedGroups groups, Function<Class<?>, RuleFlow<?>> flows, ValueExtractorRegistry extractors) {
        this(groups, flows, extractors, null);
    }

    /**
     * Creates the traversal of a call.
     *
     * @param groups the requested groups.
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
        this.groups = groups;
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
     * The groups requested for the call.
     *
     * @return the requested groups.
     */
    public RequestedGroups getGroups() {
        return groups;
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
     * The property and class rules of a bean class active for the call's groups.
     *
     * @param rules the bean rules.
     * @return the RuleSet to run.
     */
    public RuleSet<?> ruleSet(BeanRules rules) {
        Assert.notNull(rules, "rules cannot be null.");
        return rules.ruleSet(groups);
    }

    /**
     * The rules of a container element position active for the call's groups.
     *
     * @param rules the bean rules.
     * @param node the position.
     * @return the RuleSet to run per element.
     */
    public RuleSet<?> elementRuleSet(BeanRules rules, ContainerElementDefinition node) {
        Assert.notNull(rules, "rules cannot be null.");
        Assert.notNull(node, "node cannot be null.");
        return rules.elementRuleSet(node, groups);
    }

    /**
     * Records an object as validated by this call.
     *
     * @param bean the object.
     * @return true if it had not been validated yet.
     */
    public boolean markValidated(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        return validated.add(bean);
    }

    /**
     * Whether an object has already been validated by this call.
     *
     * @param bean the object.
     * @return true if it was.
     */
    public boolean isValidated(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        return validated.contains(bean);
    }

    /**
     * Validates an object reached from the object in scope, at the given path: nothing for null
     * (cascading never implies {@code @NotNull}) or for an object this call already validated;
     * otherwise {@code $this} is rebound to the object and {@code $parent} to the object in scope,
     * the collector's path moves to the object, and the flow of the object's runtime class runs.
     * Scope and path are restored afterwards, whatever happens.
     *
     * @param ruleContext the context of the running flow.
     * @param value the object to validate; may be null.
     * @param path the path of the object from the root.
     */
    public void descend(RuleContext ruleContext, Object value, PropertyPath path) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.notNull(path, "path cannot be null.");

        if (value == null || isValidated(value)) return;

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
     * The collector the rules of a context report to, whatever its kind: the innermost
     * {@link RuleViolations} binding.
     *
     * @param ruleContext the context.
     * @return the collector; null when none is bound.
     */
    public static RuleViolations anyCollector(RuleContext ruleContext) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        List<Binding<RuleViolations>> bindings = ruleContext.getBindings().getBindings(RuleViolations.class);
        return bindings.isEmpty() ? null : bindings.get(0).getValue();
    }

    /**
     * The path-aware collector the rules of a context report to, if that is what is bound: the
     * innermost {@link RuleViolations} binding, when it is a {@link BeanRuleViolations}.
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
        return "Traversal{groups=" + groups + ", validated=" + validated.size() + '}';
    }
}
