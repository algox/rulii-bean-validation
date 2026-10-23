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
package org.rulii.validation.beans.descriptor;

import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ElementDescriptor;
import jakarta.validation.metadata.Scope;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.ConstraintDefinition;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What every descriptor of a constrained element shares: the constraints declared on it, each with the
 * kind of element it was declared on, and the {@link ConstraintFinder} filtering them by groups, scope
 * and element type.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
abstract class DefinitionElementDescriptor implements ElementDescriptor {

    /**
     * A constraint and the kind of element it was declared on.
     *
     * @param definition the constraint.
     * @param elementType where it was declared: a field, a getter, a record component, a type or a type use.
     */
    record Entry(ConstraintDefinition definition, ElementType elementType) {}

    private final Class<?> beanClass;
    private final List<Entry> entries;

    DefinitionElementDescriptor(Class<?> beanClass, List<Entry> entries) {
        super();
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(entries, "entries cannot be null.");
        this.beanClass = beanClass;
        this.entries = List.copyOf(entries);
    }

    /**
     * The bean class the element belongs to: what {@link Scope#LOCAL_ELEMENT} is relative to.
     *
     * @return the bean class.
     */
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public boolean hasConstraints() {
        return !entries.isEmpty();
    }

    @Override
    public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
        return describe(entries);
    }

    @Override
    public ConstraintFinder findConstraints() {
        return new Finder();
    }

    private static Set<ConstraintDescriptor<?>> describe(List<Entry> entries) {
        Set<ConstraintDescriptor<?>> result = new LinkedHashSet<>();

        for (Entry entry : entries) {
            result.add(ConstraintDefinitionDescriptor.of(entry.definition()));
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Filters the element's constraints. Groups match when the constraint belongs to one of the requested
     * groups or their superinterfaces ({@code Default} for a constraint declaring none); the local scope
     * keeps the constraints declared by the bean class itself, not by a supertype.
     */
    private final class Finder implements ConstraintFinder {

        private RequestedGroups groups;
        private Scope scope = Scope.HIERARCHY;
        private Set<ElementType> elementTypes;

        private Finder() {
            super();
        }

        @Override
        public ConstraintFinder unorderedAndMatchingGroups(Class<?>... groups) {
            this.groups = RequestedGroups.of(groups);
            return this;
        }

        @Override
        public ConstraintFinder lookingAt(Scope scope) {
            Assert.notNull(scope, "scope cannot be null.");
            this.scope = scope;
            return this;
        }

        @Override
        public ConstraintFinder declaredOn(ElementType... types) {
            Assert.notNull(types, "types cannot be null.");
            this.elementTypes = types.length == 0 ? EnumSet.noneOf(ElementType.class) : EnumSet.of(types[0], types);
            return this;
        }

        @Override
        public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
            return describe(matching());
        }

        @Override
        public boolean hasConstraints() {
            return !matching().isEmpty();
        }

        private List<Entry> matching() {
            List<Entry> result = new ArrayList<>();

            for (Entry entry : entries) {
                if (matches(entry)) {
                    result.add(entry);
                }
            }

            return result;
        }

        private boolean matches(Entry entry) {
            if (groups != null && !groups.activates(entry.definition())) return false;
            if (scope == Scope.LOCAL_ELEMENT && !beanClass.equals(entry.definition().getConstraintSource().getDeclaringClass())) return false;
            return elementTypes == null || elementTypes.contains(entry.elementType());
        }
    }
}
