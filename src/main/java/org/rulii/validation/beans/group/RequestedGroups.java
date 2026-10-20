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

import jakarta.validation.GroupSequence;
import jakarta.validation.groups.Default;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.ConstraintDefinition;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The groups requested for one validation call, resolved for activation checks. No groups means the
 * {@link Default} group; requesting groups does not request {@code Default} implicitly. Each
 * requested group is expanded with the interfaces it extends, transitively, so that requesting a
 * subgroup activates the constraints declared for any of its supergroups.
 *
 * <p>Resolution fails fast on a group that is not an interface ({@link InvalidGroupException}) and on
 * a group that is, or extends, a {@code @GroupSequence} ({@link GroupSequenceRequestedException}):
 * nothing group-related is silently approximated.</p>
 *
 * <p>A constraint is {@link #activates(ConstraintDefinition) active} when its declared groups, empty
 * meaning {@code Default}, intersect the expanded requested groups.</p>
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
     * @throws GroupSequenceRequestedException if a group, or an interface it extends, is a
     *         {@code @GroupSequence}.
     */
    public static RequestedGroups of(Class<?>... groups) {
        Assert.notNull(groups, "groups cannot be null.");
        return of(Arrays.asList(groups));
    }

    /**
     * Resolves the given requested groups.
     *
     * @param groups the requested groups; empty means {@code Default}.
     * @return the resolved groups.
     * @throws IllegalArgumentException if a group is null.
     * @throws InvalidGroupException if a group is not an interface.
     * @throws GroupSequenceRequestedException if a group, or an interface it extends, is a
     *         {@code @GroupSequence}.
     */
    public static RequestedGroups of(Collection<Class<?>> groups) {
        Assert.notNull(groups, "groups cannot be null.");
        if (groups.isEmpty()) return DEFAULT;

        Set<Class<?>> expanded = new LinkedHashSet<>();

        for (Class<?> group : groups) {
            Assert.notNull(group, "a requested group cannot be null.");
            if (!group.isInterface()) throw new InvalidGroupException(group);
            expand(group, expanded);
        }

        return new RequestedGroups(List.copyOf(groups), Collections.unmodifiableSet(expanded));
    }

    private static void expand(Class<?> group, Set<Class<?>> result) {
        if (group.isAnnotationPresent(GroupSequence.class)) throw new GroupSequenceRequestedException(group);
        if (!result.add(group)) return;

        for (Class<?> parent : group.getInterfaces()) {
            expand(parent, result);
        }
    }

    /**
     * The groups as requested, in request order; empty for the {@link #DEFAULT default} request.
     *
     * @return the requested groups.
     */
    public List<Class<?>> getRequested() {
        return requested;
    }

    /**
     * The requested groups expanded with the interfaces they extend: request order, each group
     * followed by its supergroups; {@code Default} alone for the default request.
     *
     * @return the active groups.
     */
    public Set<Class<?>> getGroups() {
        return groups;
    }

    /**
     * Whether this is the request for the {@code Default} group only.
     *
     * @return true if no groups were requested.
     */
    public boolean isDefault() {
        return requested.isEmpty();
    }

    /**
     * Whether the given group is among the {@link #getGroups() active groups}.
     *
     * @param group the group.
     * @return true if active.
     */
    public boolean contains(Class<?> group) {
        Assert.notNull(group, "group cannot be null.");
        return groups.contains(group);
    }

    /**
     * Whether the given constraint is active for this request: its declared groups, empty meaning
     * {@code Default}, intersect the active groups.
     *
     * @param constraint the constraint.
     * @return true if the constraint is to be validated.
     */
    public boolean activates(ConstraintDefinition constraint) {
        Assert.notNull(constraint, "constraint cannot be null.");
        return activates(constraint.getGroups());
    }

    /**
     * Whether a constraint declaring the given groups is active for this request.
     *
     * @param declaredGroups the constraint's {@code groups()}; empty means {@code Default}.
     * @return true if the declared groups intersect the active groups.
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
