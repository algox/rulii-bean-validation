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
package org.rulii.validation.beans.group;

import jakarta.validation.groups.Default;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.RuleDeclaration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The groups one step of a validation call runs, resolved for activation checks. No groups means the
 * {@link Default} group; requesting groups does not request {@code Default} implicitly. Each
 * requested group is expanded with the interfaces it extends, transitively, so that requesting a
 * subgroup activates the constraints declared for any of its supergroups. A {@code @GroupSequence}
 * given here expands to its members, unordered; order and stopping are {@link GroupPlan}'s business.
 *
 * <p>Resolution fails fast on a group that is not an interface ({@link InvalidGroupException}):
 * nothing group-related is silently approximated.</p>
 *
 * <p>A constraint is {@link #activates(RuleDeclaration) active} when its declared groups, empty
 * meaning {@code Default}, intersect the expanded requested groups; or, implicit grouping (specification
 * 5.4.4), when it is hosted on an interface, belongs to {@code Default}, and that interface is among
 * the expanded requested groups: requesting {@code Auditable.class} validates the {@code Default}
 * constraints declared on {@code Auditable}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RequestedGroups {

    /**
     * The groups of a call that requests none: {@code Default} only.
     */
    public static final RequestedGroups DEFAULT = new RequestedGroups(List.of(), Set.of(Default.class));

    private final List<Class<?>> requested;
    private final Set<Class<?>> groups;

    private RequestedGroups(List<Class<?>> requested, Set<Class<?>> groups) {
        super();
        this.requested = requested;
        this.groups = groups;
    }

    /**
     * Resolves the given requested groups.
     *
     * @param groups the requested groups; none means {@code Default}.
     * @return the resolved groups.
     * @throws IllegalArgumentException if a group is null.
     * @throws InvalidGroupException if a group is not an interface.
     * @throws jakarta.validation.GroupDefinitionException if a group is an ill-defined sequence.
     */
    public static RequestedGroups of(Class<?>... groups) {
        Assert.notNull(groups, "groups cannot be null.");
        return of(Arrays.asList(groups));
    }

    /**
     * Resolves the given requested groups.
     *
     * @param groups the requested groups; none means {@code Default}.
     * @return the resolved groups.
     * @throws IllegalArgumentException if a group is null.
     * @throws InvalidGroupException if a group is not an interface.
     * @throws jakarta.validation.GroupDefinitionException if a group is an ill-defined sequence.
     */
    public static RequestedGroups of(Collection<Class<?>> groups) {
        Assert.notNull(groups, "groups cannot be null.");
        if (groups.isEmpty()) return DEFAULT;
        return resolve(groups);
    }

    private static RequestedGroups resolve(Collection<Class<?>> groups) {
        Set<Class<?>> expanded = new LinkedHashSet<>();

        for (Class<?> group : groups) {
            Assert.notNull(group, "a requested group cannot be null.");
            if (!group.isInterface()) throw new InvalidGroupException(group);
            expand(group, expanded);
        }

        return new RequestedGroups(List.copyOf(groups), Collections.unmodifiableSet(expanded));
    }

    private static void expand(Class<?> group, Set<Class<?>> result) {
        if (GroupPlan.isSequence(group)) {
            for (Class<?> member : GroupPlan.members(group)) {
                expand(member, result);
            }
            return;
        }

        if (!result.add(group)) return;

        for (Class<?> parent : group.getInterfaces()) {
            expand(parent, result);
        }
    }

    /**
     * The groups as requested, before expansion.
     *
     * @return the requested groups; empty when none were requested ({@code Default}).
     */
    public List<Class<?>> getRequested() {
        return requested;
    }

    /**
     * The groups as requested, {@code Default} spelled out when none were.
     *
     * @return the requested groups; never empty unless {@link #isEmpty()}.
     */
    public List<Class<?>> getRequestedOrDefault() {
        return requested.isEmpty() && groups.contains(Default.class) ? List.of(Default.class) : requested;
    }

    /**
     * The expanded groups: the requested ones and every interface they extend.
     *
     * @return the groups to activate constraints for.
     */
    public Set<Class<?>> getGroups() {
        return groups;
    }

    /**
     * The simple names of the expanded groups, sorted: what a rule set built for these groups is
     * tagged with and described by.
     *
     * @return the names; never null.
     */
    public List<String> getGroupNames() {
        List<String> result = new ArrayList<>(groups.size());

        for (Class<?> group : groups) {
            result.add(group.getSimpleName());
        }

        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Whether the call requested no groups, so {@code Default} alone is active.
     *
     * @return true for the default request.
     */
    public boolean isDefault() {
        return requested.isEmpty() && groups.contains(Default.class);
    }

    /**
     * Whether nothing is active: the result of {@link #without(Class)} removing the last group.
     *
     * @return true if no group is active.
     */
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /**
     * Whether the given group is among the expanded groups.
     *
     * @param group the group.
     * @return true if active.
     */
    public boolean contains(Class<?> group) {
        Assert.notNull(group, "group cannot be null.");
        return groups.contains(group);
    }

    /**
     * These groups less the given one: the request without it, re-expanded, so the supergroups of the
     * remaining requested groups stay active.
     *
     * @param group the group to remove.
     * @return the remaining groups; possibly {@link #isEmpty() empty}.
     */
    public RequestedGroups without(Class<?> group) {
        Assert.notNull(group, "group cannot be null.");
        List<Class<?>> remaining = new ArrayList<>(getRequestedOrDefault());
        remaining.remove(group);
        if (remaining.isEmpty()) return new RequestedGroups(List.of(), Set.of());
        return resolve(remaining);
    }

    /**
     * Whether a declaration (a constraint or an {@code @Expr}) is active for these groups.
     *
     * @param declaration the declaration.
     * @return true if its declared groups, empty meaning {@code Default}, intersect the expanded groups.
     */
    public boolean activates(RuleDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        List<Class<?>> declared = declaration.getGroups();

        if (activates(declared)) return true;

        // Implicit grouping (specification 5.4.4): a declaration hosted on an interface and in the Default group
        // belongs to the group named after that interface as well.
        Class<?> host = declaration.getConstraintSource().getDeclaringClass();
        return host.isInterface() && (declared.isEmpty() || declared.contains(Default.class)) && groups.contains(host);
    }

    /**
     * Whether a constraint with the given declared groups is active for these groups.
     *
     * @param declaredGroups the constraint's groups; empty meaning {@code Default}.
     * @return true if they intersect the expanded groups.
     */
    public boolean activates(Collection<Class<?>> declaredGroups) {
        Assert.notNull(declaredGroups, "declaredGroups cannot be null.");
        if (declaredGroups.isEmpty()) return groups.contains(Default.class);

        for (Class<?> group : declaredGroups) {
            if (groups.contains(group)) return true;
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestedGroups that = (RequestedGroups) o;
        return groups.equals(that.groups);
    }

    @Override
    public int hashCode() {
        return groups.hashCode();
    }

    @Override
    public String toString() {
        return "RequestedGroups" + groups.stream().map(Class::getSimpleName).toList();
    }
}
