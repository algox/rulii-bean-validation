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
package org.rulii.validation.beans.model;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.groups.ConvertGroup;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.group.GroupPlan;
import org.rulii.validation.beans.group.RequestedGroups;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code @ConvertGroup} conversions declared beside a {@code @Valid}: when the element cascades,
 * each group being processed that matches a {@code from} exactly is replaced by its {@code to}; the
 * others pass through unchanged. There is no group inheritance across a conversion, per the
 * specification. A {@code to} may be a sequence, which the cascaded object then runs stepwise.
 *
 * <p>Read-time rules ({@link ConstraintDeclarationException}): conversions need {@code @Valid} on the
 * same element; a group may not be converted twice; a sequence may not be converted from; a
 * {@code to} must be an interface.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class GroupConversions {

    /**
     * No conversions.
     */
    public static final GroupConversions NONE = new GroupConversions(Map.of());

    private final Map<Class<?>, Class<?>> conversions;

    private GroupConversions(Map<Class<?>, Class<?>> conversions) {
        super();
        this.conversions = conversions;
    }

    /**
     * Reads the conversions declared on an element.
     *
     * @param element the annotated element.
     * @param cascaded whether the element is cascaded ({@code @Valid}).
     * @param location where the element is, for messages.
     * @return the conversions; {@link #NONE} when there are none.
     * @throws ConstraintDeclarationException if a rule is broken.
     */
    public static GroupConversions load(AnnotatedElement element, boolean cascaded, String location) {
        Assert.notNull(element, "element cannot be null.");
        return load(Arrays.asList(element.getDeclaredAnnotations()), cascaded, location);
    }

    /**
     * Reads the conversions among the given annotations. The same annotation given twice (Java mirrors
     * a {@code @ConvertGroup} on a field onto its type use) counts once.
     *
     * @param annotations the annotations.
     * @param cascaded whether the element is cascaded ({@code @Valid}).
     * @param location where the element is, for messages.
     * @return the conversions; {@link #NONE} when there are none.
     * @throws ConstraintDeclarationException if a rule is broken.
     */
    public static GroupConversions load(Collection<Annotation> annotations, boolean cascaded, String location) {
        Assert.notNull(annotations, "annotations cannot be null.");
        Assert.notNull(location, "location cannot be null.");
        List<ConvertGroup> declared = new ArrayList<>();

        for (Annotation annotation : annotations) {
            if (annotation instanceof ConvertGroup conversion) {
                if (!declared.contains(conversion)) declared.add(conversion);
            } else if (annotation instanceof ConvertGroup.List list) {
                for (ConvertGroup conversion : list.value()) {
                    if (!declared.contains(conversion)) declared.add(conversion);
                }
            }
        }

        if (declared.isEmpty()) return NONE;

        if (!cascaded) {
            throw new ConstraintDeclarationException("[" + location + "] declares @ConvertGroup without @Valid; group conversion"
                    + " applies to cascaded validation only.");
        }

        Map<Class<?>, Class<?>> result = new LinkedHashMap<>();

        for (ConvertGroup conversion : declared) {
            add(result, conversion.from(), conversion.to(), location);
        }

        return new GroupConversions(Collections.unmodifiableMap(result));
    }

    private static void add(Map<Class<?>, Class<?>> result, Class<?> from, Class<?> to, String location) {
        Assert.notNull(from, "from cannot be null.");
        Assert.notNull(to, "to cannot be null.");

        if (!from.isInterface() || GroupPlan.isSequence(from)) {
            throw new ConstraintDeclarationException("[" + location + "] converts from [" + from.getName()
                    + "], which is " + (from.isInterface() ? "a group sequence" : "not a group interface") + "; only a plain group can be converted from.");
        }

        if (!to.isInterface()) {
            throw new ConstraintDeclarationException("[" + location + "] converts to [" + to.getName() + "], which is not a group interface.");
        }

        Class<?> existing = result.get(from);

        if (existing != null && existing != to) {
            throw new ConstraintDeclarationException("[" + location + "] converts group [" + from.getName() + "] twice: to ["
                    + existing.getName() + "] and to [" + to.getName() + "].");
        }

        result.put(from, to);
    }

    /**
     * These conversions and another element's, for a property read from several declarations.
     *
     * @param other the other conversions.
     * @param location where the element is, for messages.
     * @return the combined conversions.
     * @throws ConstraintDeclarationException if the two convert one group to different targets.
     */
    public GroupConversions merge(GroupConversions other, String location) {
        Assert.notNull(other, "other cannot be null.");
        if (other.isEmpty()) return this;
        if (isEmpty()) return other;

        Map<Class<?>, Class<?>> result = new LinkedHashMap<>(conversions);

        for (Map.Entry<Class<?>, Class<?>> conversion : other.conversions.entrySet()) {
            add(result, conversion.getKey(), conversion.getValue(), location);
        }

        return new GroupConversions(Collections.unmodifiableMap(result));
    }

    public boolean isEmpty() {
        return conversions.isEmpty();
    }

    /**
     * The conversions, {@code from} to {@code to}, in declaration order.
     *
     * @return the map; unmodifiable.
     */
    public Map<Class<?>, Class<?>> asMap() {
        return conversions;
    }

    /**
     * The group a processed group cascades as.
     *
     * @param group the group being processed.
     * @return its {@code to} when converted, the group itself otherwise.
     */
    public Class<?> convert(Class<?> group) {
        Assert.notNull(group, "group cannot be null.");
        return conversions.getOrDefault(group, group);
    }

    /**
     * The groups a cascaded object is validated with: the groups as requested ({@code Default} spelled
     * out), each converted. Supergroups reached by expansion are not converted, per the specification.
     *
     * @param groups the groups being processed.
     * @return the converted request, to plan anew (a {@code to} may be a sequence).
     */
    public List<Class<?>> convert(RequestedGroups groups) {
        Assert.notNull(groups, "groups cannot be null.");
        List<Class<?>> result = new ArrayList<>();

        for (Class<?> group : groups.getRequestedOrDefault()) {
            Class<?> converted = convert(group);
            if (!result.contains(converted)) result.add(converted);
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return conversions.equals(((GroupConversions) o).conversions);
    }

    @Override
    public int hashCode() {
        return conversions.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("GroupConversions{");
        for (Map.Entry<Class<?>, Class<?>> conversion : conversions.entrySet()) {
            if (result.length() > 17) result.append(", ");
            result.append(conversion.getKey().getSimpleName()).append(" -> ").append(conversion.getValue().getSimpleName());
        }
        return result.append('}').toString();
    }
}
