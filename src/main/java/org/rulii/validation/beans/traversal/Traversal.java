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
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.group.RequestedGroups;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The state of one validation call, bound as {@code $traversal} and shared by every flow the call
 * runs: the requested groups, fixed for the whole call; the objects validated so far, so a shared or
 * cyclic object is validated once; and the lookup of the rules of a class, by runtime type.
 *
 * <p>Created fresh by the entry point for each call, or by a flow's parameter default when a flow
 * is run on its own (an embedded run), with the Default group.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Traversal {

    private final RequestedGroups groups;
    private final Function<Class<?>, BeanRules> lookup;
    private final Set<Object> validated = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Creates the traversal of a call.
     *
     * @param groups the requested groups.
     * @param lookup the rules of a bean class, by runtime type.
     */
    public Traversal(RequestedGroups groups, Function<Class<?>, BeanRules> lookup) {
        super();
        Assert.notNull(groups, "groups cannot be null.");
        Assert.notNull(lookup, "lookup cannot be null.");
        this.groups = groups;
        this.lookup = lookup;
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
     * The rules of a bean class.
     *
     * @param beanClass the runtime class of an object.
     * @return its rules.
     */
    public BeanRules getRules(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        BeanRules result = lookup.apply(beanClass);
        Assert.notNull(result, "no rules for [" + beanClass.getName() + "]");
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
