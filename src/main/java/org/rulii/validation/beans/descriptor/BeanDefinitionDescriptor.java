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

import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstructorDescriptor;
import jakarta.validation.metadata.MethodDescriptor;
import jakarta.validation.metadata.MethodType;
import jakarta.validation.metadata.PropertyDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link BeanDefinition} seen through the spec's {@link BeanDescriptor}: what
 * {@code Validator.getConstraintsForClass} returns. Class-level constraints are the element's own;
 * the constrained properties are those carrying a constraint, a cascade or a constrained container
 * element position, as the spec defines them ({@code @Validate} rules are not constraints). The
 * constrained methods (getters and non-getters alike) and constructors are described through
 * {@link ExecutableDefinitionDescriptor}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanDefinitionDescriptor extends DefinitionElementDescriptor implements BeanDescriptor {

    private final BeanDefinition definition;

    /**
     * Describes a bean definition.
     *
     * @param definition the definition.
     */
    public BeanDefinitionDescriptor(BeanDefinition definition) {
        super(beanClass(definition), entries(definition));
        this.definition = definition;
    }

    private static Class<?> beanClass(BeanDefinition definition) {
        Assert.notNull(definition, "definition cannot be null.");
        return definition.getBeanClass();
    }

    private static List<Entry> entries(BeanDefinition definition) {
        List<Entry> result = new ArrayList<>();

        for (ConstraintDefinition constraint : definition.getClassConstraints()) {
            result.add(new Entry(constraint, ElementType.TYPE));
        }

        return result;
    }

    /**
     * The definition described.
     *
     * @return the definition.
     */
    public BeanDefinition getDefinition() {
        return definition;
    }

    @Override
    public Class<?> getElementClass() {
        return definition.getBeanClass();
    }

    @Override
    public boolean isBeanConstrained() {
        if (!definition.getClassConstraints().isEmpty()) return true;

        for (PropertyDefinition property : definition.getProperties()) {
            if (isConstrained(property)) return true;
        }

        return false;
    }

    @Override
    public PropertyDescriptor getConstraintsForProperty(String propertyName) {
        Assert.notNull(propertyName, "propertyName cannot be null.");
        PropertyDefinition property = definition.getProperty(propertyName);
        return property != null && isConstrained(property) ? new PropertyDefinitionDescriptor(definition.getBeanClass(), property) : null;
    }

    @Override
    public Set<PropertyDescriptor> getConstrainedProperties() {
        Set<PropertyDescriptor> result = new LinkedHashSet<>();

        for (PropertyDefinition property : definition.getProperties()) {
            if (isConstrained(property)) {
                result.add(new PropertyDefinitionDescriptor(definition.getBeanClass(), property));
            }
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Whether a property is constrained in the spec's sense: a constraint, a cascade or a constrained
     * container element position. {@code @Validate} rules do not count.
     */
    static boolean isConstrained(PropertyDefinition property) {
        return !property.getConstraints().isEmpty() || property.isCascaded() || !property.getContainerElements().isEmpty();
    }

    @Override
    public MethodDescriptor getConstraintsForMethod(String methodName, Class<?>... parameterTypes) {
        Assert.notNull(methodName, "methodName cannot be null.");
        ExecutableDefinition method = definition.getMethod(methodName, parameterTypes);
        return method != null ? new ExecutableDefinitionDescriptor(definition.getBeanClass(), method) : null;
    }

    @Override
    public Set<MethodDescriptor> getConstrainedMethods(MethodType methodType, MethodType... methodTypes) {
        Assert.notNull(methodType, "methodType cannot be null.");
        Set<MethodType> types = EnumSet.of(methodType, methodTypes);
        Set<MethodDescriptor> result = new LinkedHashSet<>();

        for (ExecutableDefinition method : definition.getMethods()) {
            if (types.contains(method.isGetter() ? MethodType.GETTER : MethodType.NON_GETTER)) {
                result.add(new ExecutableDefinitionDescriptor(definition.getBeanClass(), method));
            }
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public ConstructorDescriptor getConstraintsForConstructor(Class<?>... parameterTypes) {
        ExecutableDefinition constructor = definition.getConstructor(parameterTypes);
        return constructor != null ? new ExecutableDefinitionDescriptor(definition.getBeanClass(), constructor) : null;
    }

    @Override
    public Set<ConstructorDescriptor> getConstrainedConstructors() {
        Set<ConstructorDescriptor> result = new LinkedHashSet<>();

        for (ExecutableDefinition constructor : definition.getConstructors()) {
            result.add(new ExecutableDefinitionDescriptor(definition.getBeanClass(), constructor));
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public String toString() {
        return "BeanDescriptor{" + definition.getBeanClass().getName() + '}';
    }
}
