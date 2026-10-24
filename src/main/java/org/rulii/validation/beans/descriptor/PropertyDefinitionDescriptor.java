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
import jakarta.validation.metadata.PropertyDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.DeclarationConstraintSource;
import org.rulii.validation.beans.model.ValueDeclaration;
import org.rulii.validation.beans.model.PropertyDefinition;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A {@link PropertyDefinition} seen through the spec's {@link PropertyDescriptor}. Its constraints are
 * those declared on the property's field, getter or record component; a constraint on a type argument
 * belongs to the {@link #getConstrainedContainerElementTypes() container element} it was declared on.
 * The group conversions are the property's {@code @ConvertGroup}s.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyDefinitionDescriptor extends DefinitionElementDescriptor implements PropertyDescriptor {

    private final PropertyDefinition property;

    /**
     * Describes a property of a bean class.
     *
     * @param beanClass the bean class.
     * @param property the property.
     */
    public PropertyDefinitionDescriptor(Class<?> beanClass, PropertyDefinition property) {
        super(beanClass, entries(property));
        this.property = property;
    }

    private static List<Entry> entries(PropertyDefinition property) {
        Assert.notNull(property, "property cannot be null.");
        List<Entry> result = new ArrayList<>();

        for (ConstraintDefinition constraint : property.getConstraints()) {
            result.add(new Entry(constraint, elementType(constraint)));
        }

        return result;
    }

    private static ElementType elementType(ConstraintDefinition constraint) {
        if (constraint.getConstraintSource() instanceof DeclarationConstraintSource source) {
            ValueDeclaration.Kind kind = source.getDeclaration().getKind();
            if (kind == ValueDeclaration.Kind.GETTER) return ElementType.METHOD;
            if (kind == ValueDeclaration.Kind.RECORD_COMPONENT) return ElementType.RECORD_COMPONENT;
        }

        return ElementType.FIELD;
    }

    /**
     * The definition described.
     *
     * @return the property definition.
     */
    public PropertyDefinition getDefinition() {
        return property;
    }

    @Override
    public String getPropertyName() {
        return property.getName();
    }

    @Override
    public Class<?> getElementClass() {
        return property.getType();
    }

    @Override
    public boolean isCascaded() {
        return property.isCascaded();
    }

    @Override
    public Set<GroupConversionDescriptor> getGroupConversions() {
        return GroupConversionDefinitionDescriptor.describe(getDefinition().getGroupConversions());
    }

    @Override
    public Set<ContainerElementTypeDescriptor> getConstrainedContainerElementTypes() {
        return ContainerElementDefinitionDescriptor.describe(getBeanClass(), property.getContainerElements());
    }

    @Override
    public String toString() {
        return "PropertyDescriptor{" + getBeanClass().getName() + "." + property.getName() + '}';
    }
}
