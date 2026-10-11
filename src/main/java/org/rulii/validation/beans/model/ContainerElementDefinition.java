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

import jakarta.validation.Valid;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.extract.NoValueExtractorException;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.util.reflect.AnnotatedTypeVisitor;
import org.rulii.validation.beans.util.reflect.AnnotatedTypes;

import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Definition of one container element position within a field's generic type: a type
 * argument such as the {@code String} in {@code List<@NotBlank String>}, the key or value of
 * a {@code Map}, or the component of an array. Positions nest, so
 * {@code Map<String, List<@Email String>>} yields a value node with an element child.
 *
 * <p>A node carries the constraints declared at its position (plus whether {@code @Valid}
 * is present there) and its child positions. Positions with no constraints, no cascade and
 * no constrained descendants are pruned, so an unconstrained {@code List<String>} yields
 * nothing.</p>
 *
 * <p>Every constrained or cascaded position is checked against the
 * {@link ValueExtractorRegistry} at load time, so a position no extractor can serve fails
 * fast instead of being skipped at validation time.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ContainerElementDefinition implements Definition {

    private final Field field;
    private final Class<?> containerType;
    private final int typeArgumentIndex;
    private final AnnotatedType annotatedType;
    private final List<ConstraintDefinition> constraints;
    private final boolean cascaded;
    private final List<ContainerElementDefinition> children;
    private final SourceDefinition source;

    private ContainerElementDefinition(Field field, Class<?> containerType, int typeArgumentIndex,
                                       AnnotatedType annotatedType, List<ConstraintDefinition> constraints,
                                       boolean cascaded, List<ContainerElementDefinition> children) {
        super();
        this.field = field;
        this.containerType = containerType;
        this.typeArgumentIndex = typeArgumentIndex;
        this.annotatedType = annotatedType;
        this.constraints = constraints;
        this.cascaded = cascaded;
        this.children = children;
        this.source = SourceDefinition.forField(field);
    }

    /**
     * Loads the container element definitions of the given field: one node per type-argument
     * position of the field's declared type that is constrained, cascaded or has a constrained
     * descendant. The field's own (declaration-level) annotations are not inspected here.
     *
     * <p>Every constrained or cascaded position must be served by an extractor in the given
     * registry; otherwise the read fails fast. Positions kept only because a descendant is
     * constrained are not checked, as nothing is applied to them.</p>
     *
     * @param field the field.
     * @param registry the value extractors available at validation time.
     * @return the top-level container element definitions, in type-argument order; empty if none.
     * @throws NoValueExtractorException if a constrained or cascaded position has no extractor.
     */
    public static List<ContainerElementDefinition> load(Field field, ValueExtractorRegistry registry) {
        Assert.notNull(field, "field cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        return Collections.unmodifiableList(AnnotatedTypes.accept(field.getAnnotatedType(),
                new Collector(field, registry).childrenOnly()));
    }

    /**
     * Collects the tree. Each visit of a position returns the node for that position, or null
     * when the position (and everything under it) is unconstrained.
     */
    private static final class Collector implements AnnotatedTypeVisitor<ContainerElementDefinition> {

        private final Field field;
        private final ValueExtractorRegistry registry;
        // Context of the position being visited: set by the parent before descending.
        private Class<?> containerType;
        private int typeArgumentIndex;

        private Collector(Field field, ValueExtractorRegistry registry) {
            super();
            this.field = field;
            this.registry = registry;
        }

        /** Visitor for the root type: skips its own annotations, returns only the child nodes. */
        private AnnotatedTypeVisitor<List<ContainerElementDefinition>> childrenOnly() {
            return new AnnotatedTypeVisitor<>() {
                @Override
                public List<ContainerElementDefinition> visitParameterized(AnnotatedParameterizedType type) {
                    return typeArguments(type);
                }

                @Override
                public List<ContainerElementDefinition> visitArray(AnnotatedArrayType type) {
                    return arrayComponent(type);
                }

                @Override
                public List<ContainerElementDefinition> visitWildcard(AnnotatedWildcardType type) {
                    return Collections.emptyList();
                }

                @Override
                public List<ContainerElementDefinition> visitTypeVariable(AnnotatedTypeVariable type) {
                    return Collections.emptyList();
                }

                @Override
                public List<ContainerElementDefinition> visitClass(AnnotatedType type) {
                    return Collections.emptyList();
                }
            };
        }

        @Override
        public ContainerElementDefinition visitParameterized(AnnotatedParameterizedType type) {
            return node(type, typeArguments(type));
        }

        @Override
        public ContainerElementDefinition visitArray(AnnotatedArrayType type) {
            return node(type, arrayComponent(type));
        }

        @Override
        public ContainerElementDefinition visitWildcard(AnnotatedWildcardType type) {
            // Annotations on the wildcard itself (List<@NotNull ?>) belong to this position.
            // Bounds are not descended: ? extends X says nothing certain about the value's type.
            return node(type, Collections.emptyList());
        }

        @Override
        public ContainerElementDefinition visitTypeVariable(AnnotatedTypeVariable type) {
            return node(type, Collections.emptyList());
        }

        @Override
        public ContainerElementDefinition visitClass(AnnotatedType type) {
            return node(type, Collections.emptyList());
        }

        private List<ContainerElementDefinition> typeArguments(AnnotatedParameterizedType type) {
            Class<?> raw = AnnotatedTypes.rawClass(type.getType());
            AnnotatedType[] arguments = type.getAnnotatedActualTypeArguments();
            List<ContainerElementDefinition> result = new ArrayList<>(arguments.length);

            for (int i = 0; i < arguments.length; i++) {
                ContainerElementDefinition child = descend(raw, i, arguments[i]);
                if (child != null) result.add(child);
            }

            return result;
        }

        private List<ContainerElementDefinition> arrayComponent(AnnotatedArrayType type) {
            Class<?> raw = AnnotatedTypes.rawClass(type.getType());
            ContainerElementDefinition child = descend(raw, 0, type.getAnnotatedGenericComponentType());
            return child != null ? List.of(child) : Collections.emptyList();
        }

        private ContainerElementDefinition descend(Class<?> containerType, int index, AnnotatedType position) {
            Class<?> previousContainer = this.containerType;
            int previousIndex = this.typeArgumentIndex;
            this.containerType = containerType;
            this.typeArgumentIndex = index;

            try {
                return AnnotatedTypes.accept(position, this);
            } finally {
                this.containerType = previousContainer;
                this.typeArgumentIndex = previousIndex;
            }
        }

        private ContainerElementDefinition node(AnnotatedType type, List<ContainerElementDefinition> children) {
            List<ConstraintDefinition> constraints = ConstraintDefinition.load(field, type);
            boolean cascaded = type.isAnnotationPresent(Valid.class);

            if (constraints.isEmpty() && !cascaded && children.isEmpty()) return null;

            if ((!constraints.isEmpty() || cascaded) && registry.find(containerType, typeArgumentIndex) == null) {
                throw new NoValueExtractorException(field, containerType, typeArgumentIndex);
            }

            return new ContainerElementDefinition(field, containerType, typeArgumentIndex, type, constraints,
                    cascaded, Collections.unmodifiableList(children));
        }
    }

    /**
     * Name of the position: the container's simple name and the type-argument index,
     * e.g. {@code Map<1>} for a map value.
     *
     * @return the position name.
     */
    @Override
    public String getName() {
        return containerType.getSimpleName() + "<" + typeArgumentIndex + ">";
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    public Field getField() {
        return field;
    }

    /**
     * Raw class of the enclosing container type (e.g. {@code Map.class}, or the array class
     * for an array component).
     *
     * @return the container type.
     */
    public Class<?> getContainerType() {
        return containerType;
    }

    /**
     * Index of this position among the container's type arguments (0 for an array component).
     *
     * @return the type-argument index.
     */
    public int getTypeArgumentIndex() {
        return typeArgumentIndex;
    }

    public AnnotatedType getAnnotatedType() {
        return annotatedType;
    }

    public Type getType() {
        return annotatedType.getType();
    }

    public List<ConstraintDefinition> getConstraints() {
        return constraints;
    }

    public boolean isCascaded() {
        return cascaded;
    }

    public List<ContainerElementDefinition> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return "ContainerElementDefinition{" +
                "field=" + field.getDeclaringClass().getName() + "." + field.getName() +
                ", position=" + getName() +
                ", type=" + annotatedType.getType().getTypeName() +
                ", constraints=" + constraints +
                ", cascaded=" + cascaded +
                ", children=" + children +
                '}';
    }
}
