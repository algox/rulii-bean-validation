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

import jakarta.validation.GroupSequence;
import jakarta.validation.groups.Default;
import org.rulii.lib.spring.util.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Requested-group expansion, done once per call: the transitive superinterface closure
 * of the requested groups (group inheritance, R6.3), empty request normalized to the
 * Default group. The expanded immutable set is both the traversal's group filter and
 * the (class, groups) RuleSet cache key.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Groups {

    public static final Set<Class<?>> DEFAULT_GROUPS = Set.of(Default.class);

    private Groups() {
        super();
    }

    /**
     * Expands the requested groups: fail-fast checks (interface-only, no
     * {@code @GroupSequence}), then the transitive superinterface closure.
     *
     * @param requested the requested groups; empty or null means the Default group.
     * @return an unmodifiable expanded group set.
     */
    public static Set<Class<?>> expand(Class<?>... requested) {
        if (requested == null || requested.length == 0) return DEFAULT_GROUPS;
        return expand(Arrays.asList(requested));
    }

    /**
     * Expands the requested groups: fail-fast checks (interface-only, no
     * {@code @GroupSequence}), then the transitive superinterface closure.
     *
     * @param requested the requested groups; empty means the Default group.
     * @return an unmodifiable expanded group set.
     */
    public static Set<Class<?>> expand(Collection<Class<?>> requested) {
        Assert.notNull(requested, "requested cannot be null.");
        if (requested.isEmpty()) return DEFAULT_GROUPS;

        Set<Class<?>> result = new LinkedHashSet<>();

        for (Class<?> group : requested) {
            if (group == null || !group.isInterface()) throw new InvalidGroupException(group);
            if (group.isAnnotationPresent(GroupSequence.class)) throw new GroupSequenceRequestedException(group);
            addWithSuperInterfaces(group, result);
        }

        return Collections.unmodifiableSet(result);
    }

    private static void addWithSuperInterfaces(Class<?> group, Set<Class<?>> result) {
        if (!result.add(group)) return;

        for (Class<?> parent : group.getInterfaces()) {
            addWithSuperInterfaces(parent, result);
        }
    }
}
