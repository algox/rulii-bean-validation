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

import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
 * <p>A declaration may also attach registry rules with {@code @Validate}; each occurrence is kept as
 * a {@link ValidateDefinition} in declaration order, and a rule name may not repeat across the
 * occurrences of one property. Likewise each {@code @Expr} is kept as an {@link ExprDefinition}.</p>
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
    private final List<ValueDeclaration> cascadedDeclarations;
    private final GroupConversions groupConversions;
    private final List<ValidateDefinition> validateRules;
    private final List<ExprDefinition> exprRules;
    private final SourceDefinition source;

    private PropertyDefinition(PropertySource propertySource, List<ConstraintDefinition> constraints,
                               List<ContainerElementDefinition> containerElements, List<ValueDeclaration> cascadedDeclarations,
                               GroupConversions groupConversions, List<ValidateDefinition> validateRules, List<ExprDefinition> exprRules) {
        super();
        this.propertySource = propertySource;
        this.constraints = constraints;
        this.containerElements = containerElements;
        this.cascadedDeclarations = cascadedDeclarations;
        this.groupConversions = groupConversions;
        this.validateRules = validateRules;
        this.exprRules = exprRules;
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
     * @throws ValidateDeclarationException if a {@code @Validate} declares no name, a blank name,
     *         or a name already referenced for this property.
     * @throws ConstraintDeclarationException if value unwrapping is requested but cannot be
     *         resolved to exactly one extractor, or both {@code Unwrap} and {@code Skip} are requested.
     */
    public static PropertyDefinition load(PropertySource propertySource, ValueExtractorRegistry registry) {
        Assert.notNull(propertySource, "propertySource cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");

        List<ConstraintDefinition> constraints = new ArrayList<>();
        List<ContainerElementDefinition> elements = Collections.emptyList();
        List<ValidateDefinition> validateRules = new ArrayList<>();
        List<ExprDefinition> exprRules = new ArrayList<>();
        List<ValueDeclaration> cascadedDeclarations = new ArrayList<>(1);
        GroupConversions conversions = GroupConversions.NONE;

        for (ValueDeclaration declaration : propertySource.getDeclarations()) {
            DeclarationReader read = DeclarationReader.read(declaration, registry);
            constraints.addAll(read.constraints());
            elements = ContainerElementDefinition.merge(elements, read.elements());
            if (read.cascaded()) cascadedDeclarations.add(declaration);
            conversions = conversions.merge(read.groupConversions(), "property [" + propertySource.getName() + "]");
            validateRules.addAll(read.validateRules());
            exprRules.addAll(read.exprRules());
        }

        ValidateDefinition.checkDistinct(validateRules, "property [" + propertySource.getName() + "]");

        return new PropertyDefinition(propertySource, Collections.unmodifiableList(constraints), elements,
                Collections.unmodifiableList(cascadedDeclarations), conversions, Collections.unmodifiableList(validateRules),
                Collections.unmodifiableList(exprRules));
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
        return !cascadedDeclarations.isEmpty();
    }

    /**
     * The {@code @ConvertGroup} conversions of the cascade, merged across declarations.
     *
     * @return the conversions; {@link GroupConversions#NONE} when there are none.
     */
    public GroupConversions getGroupConversions() {
        return groupConversions;
    }

    /**
     * The declarations carrying {@code @Valid}, in declaration order. Cascading reads the value of
     * the declaration the annotation is written on: {@code @Valid} on the field validates the field's
     * object, {@code @Valid} on the getter the getter's.
     *
     * @return the cascaded declarations; empty if the property is not cascaded.
     */
    public List<ValueDeclaration> getCascadedDeclarations() {
        return cascadedDeclarations;
    }

    /**
     * Registry rules attached to the property with {@code @Validate}: one definition per annotation
     * occurrence, in declaration order (field declarations, then getters, each top-down); each knows
     * the {@link ValidateDefinition#getDeclarationSource() declaration} it is read from.
     *
     * @return the attached rules; empty if none.
     */
    public List<ValidateDefinition> getValidateRules() {
        return validateRules;
    }

    /**
     * The {@code @Expr} conditions attached to the property, in declaration order over its
     * declarations.
     *
     * @return the attached conditions; empty if none.
     */
    public List<ExprDefinition> getExprRules() {
        return exprRules;
    }

    /**
     * Whether anything is to be validated for this property: constraints, cascade, container
     * elements or attached rules. Unconstrained properties still exist so they can be bound for
     * sibling rules.
     *
     * @return true if the property has validation to perform.
     */
    public boolean isConstrained() {
        return !constraints.isEmpty() || isCascaded() || !containerElements.isEmpty() || !validateRules.isEmpty() || !exprRules.isEmpty();
    }

    @Override
    public String toString() {
        return "PropertyDefinition{" +
                "name=" + getName() +
                ", source=" + propertySource +
                ", type=" + propertySource.getGenericType().getTypeName() +
                ", constraints=" + constraints +
                ", cascaded=" + isCascaded() +
                ", containerElements=" + containerElements +
                ", validateRules=" + validateRules +
                ", exprRules=" + exprRules +
                '}';
    }
}
