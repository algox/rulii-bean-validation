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
import jakarta.validation.groups.ConvertGroup;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.extract.NoValueExtractorException;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.util.reflect.AnnotatedTypeVisitor;
import org.rulii.validation.beans.util.reflect.AnnotatedTypes;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Definition of one container element position within the generic type of a property declaration
 * (field, getter or record component): a type argument such as the {@code String} in
 * {@code List<@NotBlank String>}, the key or value of a {@code Map}, or the component of an
 * array. Positions nest, so {@code Map<String, List<@Email String>>} yields a value node with an
 * element child.
 *
 * <p>A node carries the constraints declared at its position (plus whether {@code @Valid}
 * is present there) and its child positions. Positions with no constraints, no cascade and
 * no constrained descendants are pruned, so an unconstrained {@code List<String>} yields
 * nothing.</p>
 *
 * <p>Every kept position is resolved against the {@link ValueExtractorRegistry} at load time
 * and keeps its {@link #getExtractor() extractor}, so a position no extractor can serve fails
 * fast instead of being skipped at validation time. {@code @ConvertGroup} at a position
 * fails fast as well (group conversion is not supported).</p>
 *
 * <p>A node may also stand for the <em>unwrapped</em> value of the property itself: a constraint
 * declared on an {@code OptionalInt} property applies to the int through the container's
 * {@code @UnwrapByDefault} extractor. {@link PropertyDefinition} creates such nodes; they have
 * no {@link #getAnnotatedType() annotated type} when the extractor serves the container value
 * rather than a type argument.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ContainerElementDefinition implements Definition {

    private final ValueDeclaration sourceDeclaration;
    private final Class<?> containerType;
    private final int typeArgumentIndex;
    private final AnnotatedType annotatedType;
    private final Type type;
    private final List<ConstraintDefinition> constraints;
    private final boolean cascaded;
    private final List<ContainerElementDefinition> children;
    private final ValueExtractorDescriptor extractor;
    private final SourceDefinition source;

    private ContainerElementDefinition(ValueDeclaration sourceDeclaration, Class<?> containerType, int typeArgumentIndex,
                                       ValueExtractorDescriptor extractor, AnnotatedType annotatedType, Type type,
                                       List<ConstraintDefinition> constraints, boolean cascaded,
                                       List<ContainerElementDefinition> children) {
        super();
        this.sourceDeclaration = sourceDeclaration;
        this.containerType = containerType;
        this.typeArgumentIndex = typeArgumentIndex;
        this.extractor = extractor;
        this.annotatedType = annotatedType;
        this.type = type;
        this.constraints = constraints;
        this.cascaded = cascaded;
        this.children = children;
        this.source = sourceDeclaration.getSourceDefinition();
    }

    /**
     * Loads the container element definitions of the given property declaration: one node per
     * type-argument position of its declared type that is constrained, cascaded or has a constrained
     * descendant. The declaration-level annotations of the source itself are not inspected here.
     *
     * <p>Every kept position must be served by an extractor in the given registry; otherwise the
     * read fails fast. That includes a position kept only because a descendant is constrained:
     * the walk has to extract its values to reach the descendant.</p>
     *
     * <p>For an array-typed property Java places a declaration annotation such as
     * {@code @NotEmpty String[] tags} on the component type as well. Those duplicates are property
     * constraints, not element constraints, and are dropped from the component position; only
     * annotations that can solely target a type use remain there.</p>
     *
     * @param sourceDeclaration the property declaration.
     * @param registry the value extractors available at validation time.
     * @return the top-level container element definitions, in type-argument order; empty if none.
     * @throws NoValueExtractorException if a constrained or cascaded position has no extractor.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared at a position.
     */
    public static List<ContainerElementDefinition> load(ValueDeclaration sourceDeclaration, ValueExtractorRegistry registry) {
        Assert.notNull(sourceDeclaration, "sourceDeclaration cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");

        return Collections.unmodifiableList(AnnotatedTypes.accept(sourceDeclaration.getAnnotatedType(),
                new Collector(sourceDeclaration, registry).childrenOnly()));
    }

    /**
     * Creates the node standing for the unwrapped value of the property itself, served by the given
     * extractor: the position the constraints declared on the property actually apply to.
     *
     * @param sourceDeclaration the property declaration.
     * @param descriptor the extractor unwrapping the property value.
     * @param annotatedType the annotated type argument the extractor serves; null when it serves
     *                      the container value itself or the argument cannot be located.
     * @param type the resolved type of the unwrapped value; null to fall back to the extractor's
     *             extracted type.
     * @param constraints the property constraints that apply to the unwrapped value.
     * @return the node.
     */
    static ContainerElementDefinition unwrapped(ValueDeclaration sourceDeclaration, ValueExtractorDescriptor descriptor,
                                                AnnotatedType annotatedType, Type type, List<ConstraintDefinition> constraints) {
        if (type == null) type = descriptor.getExtractedType();

        return new ContainerElementDefinition(sourceDeclaration, descriptor.getContainerType(), descriptor.getTypeArgumentIndex(),
                descriptor, annotatedType, type, constraints, false, Collections.emptyList());
    }

    /**
     * Copy of this node with the given constraints appended.
     *
     * @param additional the constraints to append.
     * @return the new node.
     */
    ContainerElementDefinition withConstraints(List<ConstraintDefinition> additional) {
        List<ConstraintDefinition> merged = new ArrayList<>(constraints);

        merged.addAll(additional);

        return new ContainerElementDefinition(sourceDeclaration, containerType, typeArgumentIndex, extractor, annotatedType, type,
                Collections.unmodifiableList(merged), cascaded, children);
    }

    /**
     * Whether this node and the given one stand for the same position: same container type and
     * type-argument index.
     *
     * @param other the other node.
     * @return true if the positions coincide.
     */
    boolean samePosition(ContainerElementDefinition other) {
        return containerType.equals(other.containerType) && typeArgumentIndex == other.typeArgumentIndex;
    }

    /**
     * Merges the node of the same position read from another property declaration into this one: the
     * constraints are appended, cascade is combined, and the children are merged by position. The
     * source declaration and annotated type of this node are kept.
     *
     * @param other the node for the same position from another source.
     * @return the merged node.
     */
    ContainerElementDefinition merge(ContainerElementDefinition other) {
        Assert.isTrue(samePosition(other), "cannot merge container element [" + other.getName()
                + "] into [" + getName() + "]: different positions.");
        List<ConstraintDefinition> merged = new ArrayList<>(constraints);

        for (ConstraintDefinition constraint : other.constraints) {
            if (!merged.contains(constraint)) merged.add(constraint);
        }

        return new ContainerElementDefinition(sourceDeclaration, containerType, typeArgumentIndex, extractor,
                annotatedType != null ? annotatedType : other.annotatedType, type, Collections.unmodifiableList(merged),
                cascaded || other.cascaded, merge(children, other.children));
    }

    /**
     * Merges two lists of nodes by position: nodes present in both are
     * {@link #merge(ContainerElementDefinition) merged}, the rest are appended in order.
     *
     * @param base the nodes to start from.
     * @param additional the nodes to merge in.
     * @return the merged, unmodifiable list.
     */
    static List<ContainerElementDefinition> merge(List<ContainerElementDefinition> base, List<ContainerElementDefinition> additional) {
        List<ContainerElementDefinition> result = new ArrayList<>(base);

        for (ContainerElementDefinition node : additional) {
            int index = -1;

            for (int i = 0; i < result.size() && index < 0; i++) {
                if (result.get(i).samePosition(node)) index = i;
            }

            if (index < 0) result.add(node); else result.set(index, result.get(index).merge(node));
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Whether the given element declares {@code @ConvertGroup} or {@code @ConvertGroup.List}.
     *
     * @param element the element.
     * @return true if group conversion is requested on it.
     */
    static boolean declaresGroupConversion(AnnotatedElement element) {
        return element.isAnnotationPresent(ConvertGroup.class) || element.isAnnotationPresent(ConvertGroup.List.class);
    }

    /**
     * Whether the given property declaration declares {@code @ConvertGroup} or {@code @ConvertGroup.List}.
     *
     * @param sourceDeclaration the property declaration.
     * @return true if group conversion is requested on it.
     */
    static boolean declaresGroupConversion(ValueDeclaration sourceDeclaration) {
        return sourceDeclaration.isAnnotationPresent(ConvertGroup.class)
                || sourceDeclaration.isAnnotationPresent(ConvertGroup.List.class);
    }

    /**
     * Collects the tree. Each visit of a position returns the node for that position, or null
     * when the position (and everything under it) is unconstrained.
     */
    private static final class Collector implements AnnotatedTypeVisitor<ContainerElementDefinition> {

        private final ValueDeclaration sourceDeclaration;
        private final ValueExtractorRegistry registry;
        // Context of the position being visited: set by the parent before descending.
        private Class<?> containerType;
        private int typeArgumentIndex;
        private Set<Annotation> excluded = Collections.emptySet();

        private Collector(ValueDeclaration sourceDeclaration, ValueExtractorRegistry registry) {
            super();
            this.sourceDeclaration = sourceDeclaration;
            this.registry = registry;
        }

        private AnnotatedTypeVisitor<List<ContainerElementDefinition>> childrenOnly() {
            return new AnnotatedTypeVisitor<>() {
                @Override
                public List<ContainerElementDefinition> visitParameterized(AnnotatedParameterizedType type) {
                    return typeArguments(type);
                }

                @Override
                public List<ContainerElementDefinition> visitArray(AnnotatedArrayType type) {
                    // Root array: the declaration annotations of the property are mirrored onto the component.
                    return arrayComponent(type, new HashSet<>(sourceDeclaration.getDeclaredAnnotations()));
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
            return node(type, arrayComponent(type, Collections.emptySet()));
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
                ContainerElementDefinition child = descend(raw, i, arguments[i], Collections.emptySet());
                if (child != null) result.add(child);
            }

            return result;
        }

        private List<ContainerElementDefinition> arrayComponent(AnnotatedArrayType type, Set<Annotation> excluded) {
            Class<?> raw = AnnotatedTypes.rawClass(type.getType());
            ContainerElementDefinition child = descend(raw, 0, type.getAnnotatedGenericComponentType(), excluded);
            return child != null ? List.of(child) : Collections.emptyList();
        }

        private ContainerElementDefinition descend(Class<?> containerType, int index, AnnotatedType position,
                                                   Set<Annotation> excluded) {
            Class<?> previousContainer = this.containerType;
            int previousIndex = this.typeArgumentIndex;
            Set<Annotation> previousExcluded = this.excluded;
            this.containerType = containerType;
            this.typeArgumentIndex = index;
            this.excluded = excluded;

            try {
                return AnnotatedTypes.accept(position, this);
            } finally {
                this.containerType = previousContainer;
                this.typeArgumentIndex = previousIndex;
                this.excluded = previousExcluded;
            }
        }

        private ContainerElementDefinition node(AnnotatedType type, List<ContainerElementDefinition> children) {
            if (declaresGroupConversion(type)) throw new GroupConversionNotSupportedException(sourceDeclaration);

            List<ConstraintDefinition> constraints = constraints(type);
            boolean cascaded = type.isAnnotationPresent(Valid.class) && !excluded.contains(type.getAnnotation(Valid.class));

            if (constraints.isEmpty() && !cascaded && children.isEmpty()) return null;

            // Every kept position is walked at validation time, so every kept position needs its extractor.
            ValueExtractorDescriptor extractor = registry.find(containerType, typeArgumentIndex);

            if (extractor == null) {
                throw new NoValueExtractorException(sourceDeclaration.getMember(), containerType, typeArgumentIndex);
            }

            // The annotated tree carries the annotations as declared; the type is resolved against the owning class.
            return new ContainerElementDefinition(sourceDeclaration, containerType, typeArgumentIndex, extractor, type,
                    sourceDeclaration.resolve(type.getType()), constraints, cascaded, Collections.unmodifiableList(children));
        }

        private List<ConstraintDefinition> constraints(AnnotatedType type) {
            List<ConstraintDefinition> all = ConstraintDefinition.load(sourceDeclaration, type);
            if (excluded.isEmpty()) return all;

            List<ConstraintDefinition> kept = new ArrayList<>(all.size());
            for (ConstraintDefinition constraint : all) {
                if (!excluded.contains(constraint.getAnnotation())) kept.add(constraint);
            }

            return Collections.unmodifiableList(kept);
        }
    }

    /**
     * Name of the position: the container's simple name and the type-argument index,
     * e.g. {@code Map<1>} for a map value, {@code OptionalInt<-1>} for an unwrapped container value.
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

    /**
     * The property declaration whose type declares this position.
     *
     * @return the source declaration.
     */
    public ValueDeclaration getSourceDeclaration() {
        return sourceDeclaration;
    }

    /**
     * Raw class of the enclosing container type (e.g. {@code Map.class}, or the array class
     * for an array component). For an unwrapped node this is the extractor's declared container type.
     *
     * @return the container type.
     */
    public Class<?> getContainerType() {
        return containerType;
    }

    /**
     * Index of this position among the container's type arguments (0 for an array component),
     * or {@link ValueExtractorDescriptor#CONTAINER} when the node stands for the unwrapped
     * container value itself.
     *
     * @return the type-argument index.
     */
    public int getTypeArgumentIndex() {
        return typeArgumentIndex;
    }

    /**
     * The extractor serving this position, resolved at load time: what the traversal walks the
     * container with.
     *
     * @return the extractor descriptor.
     */
    public ValueExtractorDescriptor getExtractor() {
        return extractor;
    }

    /**
     * The annotated type at this position.
     *
     * @return the annotated type; null for an unwrapped node whose extractor serves the container
     *         value itself (e.g. {@code OptionalInt}).
     */
    public AnnotatedType getAnnotatedType() {
        return annotatedType;
    }

    /**
     * The type of the values at this position.
     *
     * @return the type; for an unwrapped container-value node the extractor's extracted type.
     */
    public Type getType() {
        return type;
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
                "source=" + sourceDeclaration.describe() +
                ", position=" + getName() +
                ", type=" + (type != null ? type.getTypeName() : null) +
                ", constraints=" + constraints +
                ", cascaded=" + cascaded +
                ", children=" + children +
                '}';
    }
}
