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

import jakarta.validation.GroupDefinitionException;
import jakarta.validation.GroupSequence;
import org.rulii.lib.spring.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How a validation call processes the groups it requested: in passes, each pass a list of steps run
 * in order. The plain groups requested form one pass with one step, all of them together. Each
 * requested {@code @GroupSequence} forms one pass with one step per member, in sequence order; a
 * sequence pass stops after the first step that produced violations. Plain groups come first, then
 * the sequences in request order, as the reference implementation orders them. No groups means the
 * {@code Default} group alone.
 *
 * <p>A sequence may list another sequence: its members are flattened into place. A cycle among
 * sequences, an empty sequence, or a member that is not an interface is a
 * {@link GroupDefinitionException}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class GroupPlan {

    /**
     * One pass over the object graph: steps run in order; a sequence pass stops at the first step
     * that produced violations.
     *
     * @param steps the groups of each step.
     * @param sequence whether the steps come from a {@code @GroupSequence}.
     */
    public record Pass(List<RequestedGroups> steps, boolean sequence) {

        public Pass {
            Assert.notEmpty(steps, "steps cannot be empty.");
            steps = List.copyOf(steps);
        }
    }

    private static final GroupPlan DEFAULT = new GroupPlan(List.of(new Pass(List.of(RequestedGroups.DEFAULT), false)));

    private final List<Pass> passes;

    private GroupPlan(List<Pass> passes) {
        super();
        this.passes = passes;
    }

    /**
     * Plans the given requested groups.
     *
     * @param groups the requested groups; none means {@code Default}.
     * @return the plan.
     * @throws InvalidGroupException if a group is not an interface.
     * @throws GroupDefinitionException if a sequence is ill-defined.
     */
    public static GroupPlan of(Class<?>... groups) {
        Assert.notNull(groups, "groups cannot be null.");
        return of(Arrays.asList(groups));
    }

    /**
     * Plans the given requested groups.
     *
     * @param groups the requested groups; none means {@code Default}.
     * @return the plan.
     * @throws InvalidGroupException if a group is not an interface.
     * @throws GroupDefinitionException if a sequence is ill-defined.
     */
    public static GroupPlan of(Collection<Class<?>> groups) {
        Assert.notNull(groups, "groups cannot be null.");
        if (groups.isEmpty()) return DEFAULT;

        List<Class<?>> plain = new ArrayList<>();
        List<Pass> sequences = new ArrayList<>();

        for (Class<?> group : groups) {
            Assert.notNull(group, "a requested group cannot be null.");
            if (!group.isInterface()) throw new InvalidGroupException(group);

            if (isSequence(group)) {
                List<RequestedGroups> steps = new ArrayList<>();

                for (Class<?> member : members(group)) {
                    steps.add(RequestedGroups.of(member));
                }

                sequences.add(new Pass(steps, true));
            } else {
                plain.add(group);
            }
        }

        List<Pass> passes = new ArrayList<>(sequences.size() + 1);
        if (!plain.isEmpty()) passes.add(new Pass(List.of(RequestedGroups.of(plain)), false));
        passes.addAll(sequences);

        return new GroupPlan(Collections.unmodifiableList(passes));
    }

    /**
     * The passes, in processing order.
     *
     * @return the passes; never empty.
     */
    public List<Pass> getPasses() {
        return passes;
    }

    /**
     * Whether the plan is one pass of one step: plain groups only.
     *
     * @return true for a single step.
     */
    public boolean isSingleStep() {
        return passes.size() == 1 && passes.get(0).steps().size() == 1;
    }

    /**
     * Whether an interface is a group sequence.
     *
     * @param group the interface.
     * @return true if it carries {@code @GroupSequence}.
     */
    public static boolean isSequence(Class<?> group) {
        Assert.notNull(group, "group cannot be null.");
        return group.isInterface() && group.isAnnotationPresent(GroupSequence.class);
    }

    /**
     * The members of a sequence, in order, nested sequences flattened into place.
     *
     * @param sequence the sequence interface.
     * @return the member groups; never empty.
     * @throws GroupDefinitionException if the sequence is empty, lists a non-interface, or takes part in
     *         a cycle.
     */
    public static List<Class<?>> members(Class<?> sequence) {
        Assert.isTrue(isSequence(sequence), "[" + sequence.getName() + "] is not a @GroupSequence.");
        List<Class<?>> result = new ArrayList<>();
        collect(sequence, result, new LinkedHashSet<>());
        return Collections.unmodifiableList(result);
    }

    private static void collect(Class<?> sequence, List<Class<?>> result, Set<Class<?>> inProgress) {
        if (!inProgress.add(sequence)) {
            throw new GroupDefinitionException("group sequence [" + sequence.getName() + "] takes part in a cycle: " + inProgress);
        }

        Class<?>[] members = sequence.getAnnotation(GroupSequence.class).value();
        if (members.length == 0) throw new GroupDefinitionException("group sequence [" + sequence.getName() + "] lists no groups.");

        for (Class<?> member : members) {
            if (!member.isInterface()) {
                throw new GroupDefinitionException("group sequence [" + sequence.getName() + "] lists [" + member.getName()
                        + "], which is not a group interface.");
            }

            if (isSequence(member)) {
                collect(member, result, inProgress);
            } else if (!result.contains(member)) {
                result.add(member);
            }
        }

        inProgress.remove(sequence);
    }

    @Override
    public String toString() {
        return "GroupPlan" + passes;
    }
}
