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

import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.util.reflect.Types;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link ContainerElementDefinition} seen through the spec's {@link ContainerElementTypeDescriptor}:
 * one type-argument position of a property's type, with the constraints declared there, whether it
 * cascades, and its own constrained positions. A node standing for an unwrapped container value has no
 * type-argument index.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ContainerElementDefinitionDescriptor extends DefinitionElementDescriptor implements ContainerElementTypeDescriptor {

    private final ContainerElementDefinition node;

    /**
     * Describes a container element position of a property of a bean class.
     *
     * @param beanClass the bean class.
     * @param node the position.
     */
    public ContainerElementDefinitionDescriptor(Class<?> beanClass, ContainerElementDefinition node) {
        super(beanClass, entries(node));
        this.node = node;
    }

    private static List<Entry> entries(ContainerElementDefinition node) {
        Assert.notNull(node, "node cannot be null.");
        List<Entry> result = new ArrayList<>();

        for (ConstraintDefinition constraint : node.getConstraints()) {
            result.add(new Entry(constraint, ElementType.TYPE_USE));
        }

        return result;
    }

    /**
     * Describes the given positions, in order.
     *
     * @param beanClass the bean class.
     * @param nodes the positions.
     * @return the descriptors; unmodifiable.
     */
    static Set<ContainerElementTypeDescriptor> describe(Class<?> beanClass, List<ContainerElementDefinition> nodes) {
        Set<ContainerElementTypeDescriptor> result = new LinkedHashSet<>();

        for (ContainerElementDefinition node : nodes) {
            result.add(new ContainerElementDefinitionDescriptor(beanClass, node));
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * The definition described.
     *
     * @return the position.
     */
    public ContainerElementDefinition getDefinition() {
        return node;
    }

    @Override
    public Class<?> getContainerClass() {
        return node.getContainerType();
    }

    /**
     * The index of the type argument; null for a node standing for the container value itself.
     */
    @Override
    public Integer getTypeArgumentIndex() {
        return node.getTypeArgumentIndex() == ValueExtractorDescriptor.CONTAINER ? null : node.getTypeArgumentIndex();
    }

    @Override
    public Class<?> getElementClass() {
        if (node.getType() != null) return Types.erasure(node.getType());
        return node.getExtractor() != null ? node.getExtractor().getExtractedType() : Object.class;
    }

    @Override
    public boolean isCascaded() {
        return node.isCascaded();
    }

    @Override
    public Set<GroupConversionDescriptor> getGroupConversions() {
        return Set.of();
    }

    @Override
    public Set<ContainerElementTypeDescriptor> getConstrainedContainerElementTypes() {
        return describe(getBeanClass(), node.getChildren());
    }

    @Override
    public String toString() {
        return "ContainerElementTypeDescriptor{" + node.getSourceDeclaration().describe() + " " + node.getName() + '}';
    }
}
