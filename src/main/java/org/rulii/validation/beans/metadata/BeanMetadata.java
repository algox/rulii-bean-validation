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
package org.rulii.validation.beans.metadata;

import org.rulii.lib.spring.util.Assert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete, immutable validation description of one bean class: its properties in
 * deterministic order (hierarchy top-down; within a class fields first, then
 * getter-only properties, in declaration order), class-level constraints, and
 * class-level attached registry rules. Produced once per class by
 * {@link BeanMetadataReader} and cached.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanMetadata {

    private final Class<?> beanClass;
    private final List<PropertyMetadata> properties;
    private final Map<String, PropertyMetadata> propertiesByName;
    private final List<ConstraintMetadata> classConstraints;
    private final List<String> classValidateRuleNames;
    private final boolean hasConstraints;
    private final boolean hasCascades;

    BeanMetadata(Class<?> beanClass, List<PropertyMetadata> properties, List<ConstraintMetadata> classConstraints, List<String> classValidateRuleNames) {
        super();
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(properties, "properties cannot be null.");
        Assert.notNull(classConstraints, "classConstraints cannot be null.");
        Assert.notNull(classValidateRuleNames, "classValidateRuleNames cannot be null.");
        this.beanClass = beanClass;
        this.properties = Collections.unmodifiableList(properties);

        Map<String, PropertyMetadata> byName = new LinkedHashMap<>();
        for (PropertyMetadata property : properties) {
            byName.put(property.getName(), property);
        }

        this.propertiesByName = Collections.unmodifiableMap(byName);
        this.classConstraints = Collections.unmodifiableList(classConstraints);
        this.classValidateRuleNames = Collections.unmodifiableList(classValidateRuleNames);
        this.hasConstraints = computeHasConstraints();
        this.hasCascades = computeHasCascades();
    }

    /**
     * Retrieves the described bean class.
     *
     * @return the bean class.
     */
    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * Retrieves all properties in deterministic declaration order.
     *
     * @return an unmodifiable list of properties.
     */
    public List<PropertyMetadata> getProperties() {
        return properties;
    }

    /**
     * Retrieves a property by name.
     *
     * @param name the property name.
     * @return the property; null if no such property exists.
     */
    public PropertyMetadata getProperty(String name) {
        Assert.notNull(name, "name cannot be null.");
        return propertiesByName.get(name);
    }

    /**
     * Retrieves the class-level constraints (custom/mapped constraints declared on the
     * class), in declaration order.
     *
     * @return an unmodifiable list of class-level constraints.
     */
    public List<ConstraintMetadata> getClassConstraints() {
        return classConstraints;
    }

    /**
     * Retrieves the registry rule names attached to the class via {@code @Validate}, in
     * declaration order.
     *
     * @return an unmodifiable list of registry rule names.
     */
    public List<String> getClassValidateRuleNames() {
        return classValidateRuleNames;
    }

    /**
     * Determines whether this class contributes any validation work at all (property or
     * class-level constraints, container-element validation, or attached registry
     * rules). Traversal short-circuit.
     *
     * @return true if validation work exists; false otherwise.
     */
    public boolean hasConstraints() {
        return hasConstraints;
    }

    /**
     * Determines whether any property (or container element) cascades. Traversal
     * short-circuit.
     *
     * @return true if cascades exist; false otherwise.
     */
    public boolean hasCascades() {
        return hasCascades;
    }

    private boolean computeHasConstraints() {
        if (!classConstraints.isEmpty() || !classValidateRuleNames.isEmpty()) return true;

        for (PropertyMetadata property : properties) {
            if (property.hasValidation()) return true;
        }

        return false;
    }

    private boolean computeHasCascades() {

        for (PropertyMetadata property : properties) {
            if (property.isCascaded()) return true;
            if (property.getContainer() != null && containerCascades(property.getContainer())) return true;
        }

        return false;
    }

    private boolean containerCascades(ContainerMetadata container) {

        for (ContainerElement element : container.getElements()) {
            if (element.isCascaded()) return true;
            if (element.getContainer() != null && containerCascades(element.getContainer())) return true;
        }

        return false;
    }

    @Override
    public String toString() {
        return "BeanMetadata{beanClass=" + beanClass.getName()
                + ", properties=" + properties.size()
                + ", classConstraints=" + classConstraints.size()
                + ", hasConstraints=" + hasConstraints
                + ", hasCascades=" + hasCascades
                + '}';
    }
}
