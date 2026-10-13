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
import jakarta.validation.Valid;
import jakarta.validation.valueextraction.Unwrapping;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.util.reflect.TypeResolver;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Definition of one bean property: the constraints declared on it, whether it is cascaded
 * ({@code @Valid}), and the constrained positions within its container type
 * ({@code List<@NotBlank String>}).
 *
 * <p>A property is read from its {@link PropertySource source}: one declaration (field, getter
 * or record component), or a field and its getter. With two declarations the constraints of each
 * are kept per declaration and all apply, each read against the value of its own declaration; the
 * primary declaration (the getter) provides the type of the property and the value cascade and
 * container walks descend on.</p>
 *
 * <p>Property constraints are read from the declaration annotations of each declaration and from
 * the annotations on its type use; Java mirrors a constraint that targets both onto both places, so
 * the two reads are merged without duplicates. A constraint declared on the property but applying
 * to the <em>unwrapped</em> value (an {@code OptionalInt} property, whose extractor is
 * {@code @UnwrapByDefault}; or an explicit {@code payload = Unwrapping.Unwrap.class}) is not a
 * property constraint: it is moved to the container element node of the extractor's position, so
 * the runtime has a single model, constraints at positions reached through extractors. Container
 * element trees read from two declarations are merged by position.</p>
 *
 * <p>Every property gets a definition, constrained or not, since every property is bound in the
 * validation scope; {@link #isConstrained()} tells whether anything is to be validated.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyDefinition implements Definition {

    private final PropertySource propertySource;
    private final List<ConstraintDefinition> constraints;
    private final List<ContainerElementDefinition> containerElements;
    private final boolean cascaded;
    private final SourceDefinition source;

    private PropertyDefinition(PropertySource propertySource, List<ConstraintDefinition> constraints,
                               List<ContainerElementDefinition> containerElements, boolean cascaded) {
        super();
        this.propertySource = propertySource;
        this.constraints = constraints;
        this.containerElements = containerElements;
        this.cascaded = cascaded;
        this.source = propertySource.getSourceDefinition();
    }

    /**
     * Loads the definition of the property declared by the given field alone.
     *
     * @param field the field.
     * @param registry the value extractors available at validation time.
     * @return the property definition.
     * @see #load(PropertySource, ValueExtractorRegistry)
     */
    public static PropertyDefinition load(Field field, ValueExtractorRegistry registry) {
        return load(PropertySource.ofField(field), registry);
    }

    /**
     * Loads the definition of the property declared by the given getter alone.
     *
     * @param getter the JavaBeans getter.
     * @param registry the value extractors available at validation time.
     * @return the property definition.
     * @throws IllegalArgumentException if the method is not a getter.
     * @see #load(PropertySource, ValueExtractorRegistry)
     */
    public static PropertyDefinition load(Method getter, ValueExtractorRegistry registry) {
        return load(PropertySource.ofGetter(getter), registry);
    }

    /**
     * Loads the definition of the property declared by the given record component.
     *
     * @param component the record component.
     * @param registry the value extractors available at validation time.
     * @return the property definition.
     * @see #load(PropertySource, ValueExtractorRegistry)
     */
    public static PropertyDefinition load(RecordComponent component, ValueExtractorRegistry registry) {
        return load(PropertySource.ofRecordComponent(component), registry);
    }

    /**
     * Loads the definition of the property declared by both a field and its getter.
     *
     * @param field the field.
     * @param getter the JavaBeans getter of the same property.
     * @param registry the value extractors available at validation time.
     * @return the property definition.
     * @throws IllegalArgumentException if the method is not a getter, or names a different property.
     * @see #load(PropertySource, ValueExtractorRegistry)
     */
    public static PropertyDefinition load(Field field, Method getter, ValueExtractorRegistry registry) {
        return load(PropertySource.of(field, getter), registry);
    }

    /**
     * Loads the definition of the property with the given source.
     *
     * @param propertySource the property source.
     * @param registry the value extractors available at validation time.
     * @return the property definition.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared on a
     *         declaration or at a position within its type.
     * @throws org.rulii.validation.beans.extract.NoValueExtractorException if a constrained or
     *         cascaded position within a declared type has no extractor.
     * @throws ConstraintDeclarationException if value unwrapping is requested but cannot be
     *         resolved to exactly one extractor, or both {@code Unwrap} and {@code Skip} are requested.
     */
    public static PropertyDefinition load(PropertySource propertySource, ValueExtractorRegistry registry) {
        Assert.notNull(propertySource, "propertySource cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");

        List<ConstraintDefinition> constraints = new ArrayList<>();
        List<ContainerElementDefinition> elements = Collections.emptyList();
        boolean cascaded = false;

        for (PropertyDeclaration declaration : propertySource.getDeclarations()) {
            Read read = read(declaration, registry);
            constraints.addAll(read.constraints);
            elements = ContainerElementDefinition.merge(elements, read.elements);
            cascaded |= read.cascaded;
        }

        return new PropertyDefinition(propertySource, Collections.unmodifiableList(constraints), elements, cascaded);
    }

    /**
     * What one declaration contributes to the property.
     */
    private record Read(List<ConstraintDefinition> constraints, List<ContainerElementDefinition> elements, boolean cascaded) {}

    private static Read read(PropertyDeclaration declaration, ValueExtractorRegistry registry) {
        AnnotatedType annotatedType = declaration.getAnnotatedType();

        if (ContainerElementDefinition.declaresGroupConversion(declaration)
                || ContainerElementDefinition.declaresGroupConversion(annotatedType)) {
            throw new GroupConversionNotSupportedException(declaration);
        }

        boolean cascaded = declaration.isAnnotationPresent(Valid.class) || annotatedType.isAnnotationPresent(Valid.class);
        List<ConstraintDefinition> declared = merge(ConstraintDefinition.load(declaration),
                ConstraintDefinition.load(declaration, annotatedType));
        List<ContainerElementDefinition> elements = new ArrayList<>(ContainerElementDefinition.load(declaration, registry));

        List<ConstraintDefinition> onProperty = new ArrayList<>(declared.size());
        List<ConstraintDefinition> unwrapped = new ArrayList<>();
        ValueExtractorDescriptor unwrapper = null;

        for (ConstraintDefinition constraint : declared) {
            ValueExtractorDescriptor descriptor = unwrapper(declaration, constraint, registry);

            if (descriptor == null) {
                onProperty.add(constraint);
            } else {
                unwrapper = descriptor;
                unwrapped.add(constraint);
            }
        }

        if (!unwrapped.isEmpty()) {
            addUnwrapped(declaration, registry, unwrapper, annotatedType, unwrapped, elements);
        }

        return new Read(onProperty, elements, cascaded);
    }

    private static List<ConstraintDefinition> merge(List<ConstraintDefinition> declaration, List<ConstraintDefinition> typeUse) {
        List<ConstraintDefinition> result = new ArrayList<>(declaration);

        for (ConstraintDefinition constraint : typeUse) {
            if (!result.contains(constraint)) result.add(constraint);
        }

        return result;
    }

    /**
     * Resolves the extractor through which a property constraint applies to the unwrapped value,
     * following the specification: {@code Unwrapping.Skip} keeps the constraint on the property;
     * {@code Unwrapping.Unwrap} requires exactly one extractor for the declared type; otherwise the
     * constraint is unwrapped only through a single {@code @UnwrapByDefault} extractor.
     *
     * @return the extractor, or null when the constraint applies to the property value itself.
     */
    private static ValueExtractorDescriptor unwrapper(PropertyDeclaration declaration, ConstraintDefinition constraint,
                                                      ValueExtractorRegistry registry) {
        boolean unwrap = constraint.getPayload().contains(Unwrapping.Unwrap.class);
        boolean skip = constraint.getPayload().contains(Unwrapping.Skip.class);
        Class<?> type = declaration.getType();

        if (unwrap && skip) {
            throw new ConstraintDeclarationException("constraint [" + constraint.getAnnotationType().getName()
                    + "] on property [" + declaration.describe() + "] requests both Unwrapping.Unwrap and Unwrapping.Skip.");
        }

        if (skip) return null;

        List<ValueExtractorDescriptor> candidates = registry.find(type);

        if (unwrap) {
            if (candidates.isEmpty()) {
                throw new ConstraintDeclarationException("constraint [" + constraint.getAnnotationType().getName()
                        + "] on property [" + declaration.describe() + "] requests Unwrapping.Unwrap but no value extractor is registered for ["
                        + type.getName() + "].");
            }

            if (candidates.size() > 1) {
                throw new ConstraintDeclarationException("constraint [" + constraint.getAnnotationType().getName()
                        + "] on property [" + declaration.describe() + "] requests Unwrapping.Unwrap but [" + type.getName()
                        + "] has several extractable positions: " + candidates + ". Declare the constraint on the type argument instead.");
            }

            return candidates.get(0);
        }

        ValueExtractorDescriptor result = null;

        for (ValueExtractorDescriptor candidate : candidates) {
            if (!candidate.isUnwrapByDefault()) continue;

            if (result != null) {
                throw new ConstraintDeclarationException("property [" + declaration.describe() + "] of type [" + type.getName()
                        + "] has several @UnwrapByDefault value extractors: " + result + " and " + candidate + ".");
            }

            result = candidate;
        }

        return result;
    }

    /**
     * Adds the unwrapped constraints to the element node of the extractor's position, merging
     * into the node the container walk already produced for that position when there is one.
     */
    private static void addUnwrapped(PropertyDeclaration declaration, ValueExtractorRegistry registry,
                                     ValueExtractorDescriptor unwrapper, AnnotatedType annotatedType,
                                     List<ConstraintDefinition> unwrapped, List<ContainerElementDefinition> elements) {
        for (int i = 0; i < elements.size(); i++) {
            ContainerElementDefinition element = elements.get(i);

            if (unwrapper.equals(registry.find(element.getContainerType(), element.getTypeArgumentIndex()))) {
                elements.set(i, element.withConstraints(unwrapped));
                return;
            }
        }

        AnnotatedType position = null;
        Type elementType = null;

        if (unwrapper.getTypeArgumentIndex() != ValueExtractorDescriptor.CONTAINER) {
            // The extractor position is declared on its own container type; view the property type as that container.
            Type container = TypeResolver.supertype(declaration.getGenericType(), unwrapper.getContainerType());

            if (container instanceof ParameterizedType parameterized) {
                elementType = parameterized.getActualTypeArguments()[unwrapper.getTypeArgumentIndex()];
            }

            if (annotatedType instanceof AnnotatedParameterizedType parameterized) {
                AnnotatedType[] arguments = parameterized.getAnnotatedActualTypeArguments();
                int index = TypeResolver.parameterIndex(declaration.getType(), unwrapper.getContainerType(), unwrapper.getTypeArgumentIndex());

                if (index >= 0 && index < arguments.length) position = arguments[index];
            }
        }

        elements.add(ContainerElementDefinition.unwrapped(declaration, unwrapper, position, elementType, Collections.unmodifiableList(unwrapped)));
    }

    /**
     * Name of the property: the field name, the getter-derived name, or the record component name.
     *
     * @return the property name.
     */
    @Override
    public String getName() {
        return propertySource.getName();
    }

    /**
     * Core source descriptor of the primary declaration.
     *
     * @return the source definition.
     */
    @Override
    public SourceDefinition getSource() {
        return source;
    }

    /**
     * Where the property is read from: its declarations and the primary one among them.
     *
     * @return the property source.
     */
    public PropertySource getPropertySource() {
        return propertySource;
    }

    /**
     * Declared type of the property, per its primary declaration.
     *
     * @return the declared class.
     */
    public Class<?> getType() {
        return propertySource.getType();
    }

    /**
     * Generic declared type of the property, per its primary declaration.
     *
     * @return the generic type.
     */
    public Type getGenericType() {
        return propertySource.getGenericType();
    }

    /**
     * Constraints that apply to the property value itself, in declaration order (field constraints,
     * then getter constraints); each knows its {@link ConstraintDefinition#getConstraintSource()
     * declaration}. Constraints declared on the property but applying to its unwrapped value appear
     * under {@link #getContainerElements()} instead.
     *
     * @return the property constraints; empty if none.
     */
    public List<ConstraintDefinition> getConstraints() {
        return constraints;
    }

    /**
     * Constrained positions within the property's container type, merged across declarations,
     * including the node for the unwrapped property value when property constraints apply through
     * an extractor.
     *
     * @return the top-level container element definitions; empty if none.
     */
    public List<ContainerElementDefinition> getContainerElements() {
        return containerElements;
    }

    /**
     * Whether the property value is to be validated recursively ({@code @Valid} on any declaration).
     *
     * @return true if cascaded.
     */
    public boolean isCascaded() {
        return cascaded;
    }

    /**
     * Whether anything is to be validated for this property: constraints, cascade or container
     * elements. Unconstrained properties still exist so they can be bound for sibling rules.
     *
     * @return true if the property has validation to perform.
     */
    public boolean isConstrained() {
        return !constraints.isEmpty() || cascaded || !containerElements.isEmpty();
    }

    @Override
    public String toString() {
        return "PropertyDefinition{" +
                "name=" + getName() +
                ", source=" + propertySource +
                ", type=" + propertySource.getGenericType().getTypeName() +
                ", constraints=" + constraints +
                ", cascaded=" + cascaded +
                ", containerElements=" + containerElements +
                '}';
    }
}
