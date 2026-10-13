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

import jakarta.validation.GroupSequence;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.util.reflect.TypeHierarchies;
import org.rulii.validation.beans.util.reflect.TypeHierarchyVisitor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validation definition of one bean class: its {@link PropertyDefinition properties} in discovery
 * order, followed by its class-level {@link ConstraintDefinition constraints} in hierarchy order.
 * Everything declared on superclasses and interfaces is included, cumulatively.
 *
 * <p>Loading walks the class hierarchy once ({@link TypeHierarchies}); the property declarations and
 * the class-level constraints are collected from the same walk. All declaration errors fail fast
 * here, at metadata-read time.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanDefinition implements Definition {

    private final Class<?> beanClass;
    private final List<PropertyDefinition> properties;
    private final List<ConstraintDefinition> classConstraints;
    private final SourceDefinition source;
    private final boolean constrained;
    private final boolean cascading;

    private BeanDefinition(Class<?> beanClass, List<PropertyDefinition> properties, List<ConstraintDefinition> classConstraints) {
        super();
        this.beanClass = beanClass;
        this.properties = properties;
        this.classConstraints = classConstraints;
        this.source = SourceDefinition.forClass(beanClass);

        boolean constrained = !classConstraints.isEmpty();
        boolean cascading = false;

        for (PropertyDefinition property : properties) {
            constrained |= property.isConstrained();
            cascading |= property.isCascaded() || cascades(property.getContainerElements());
        }

        this.constrained = constrained;
        this.cascading = cascading;
    }

    /**
     * Loads the definition of the given bean class, across its superclasses and interfaces.
     *
     * @param beanClass the bean class.
     * @param registry the value extractors available at validation time.
     * @return the bean definition.
     * @throws GroupSequenceNotSupportedException if {@code @GroupSequence} is declared on the class
     *         or one of its superclasses.
     * @throws ConstraintOnNonGetterException if a constraint, {@code @Valid} or {@code @ConvertGroup}
     *         is declared on a method that is not a getter, at any level.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared on a property.
     * @throws org.rulii.validation.beans.extract.NoValueExtractorException if a constrained or
     *         cascaded container element position has no extractor.
     * @throws jakarta.validation.ConstraintDeclarationException for the other declaration errors
     *         detected while loading the properties.
     * @throws IllegalArgumentException if the class is a primitive or an array type.
     */
    public static BeanDefinition load(Class<?> beanClass, ValueExtractorRegistry registry) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");

        Loader loader = new Loader(beanClass);
        TypeHierarchies.walk(beanClass, loader);

        List<PropertySource> sources = loader.discovery.getSources();
        List<PropertyDefinition> properties = new ArrayList<>(sources.size());

        for (PropertySource propertySource : sources) {
            properties.add(PropertyDefinition.load(propertySource, registry));
        }

        return new BeanDefinition(beanClass, Collections.unmodifiableList(properties),
                Collections.unmodifiableList(loader.classConstraints));
    }

    private static boolean cascades(List<ContainerElementDefinition> elements) {
        for (ContainerElementDefinition element : elements) {
            if (element.isCascaded() || cascades(element.getChildren())) return true;
        }

        return false;
    }

    /**
     * Simple name of the bean class.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return beanClass.getSimpleName();
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * The properties of the bean: field (or record component) properties first, then getter-only
     * properties, each in hierarchy order top-down.
     *
     * @return the properties.
     */
    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * The property with the given name.
     *
     * @param name the property name.
     * @return the property; null if the bean has no such property.
     */
    public PropertyDefinition getProperty(String name) {
        Assert.notNull(name, "name cannot be null.");

        for (PropertyDefinition property : properties) {
            if (property.getName().equals(name)) return property;
        }

        return null;
    }

    /**
     * The class-level constraints of the bean class, its superclasses and its interfaces, in
     * hierarchy order; each knows the {@link ClassConstraintSource class} it is declared on.
     *
     * @return the class-level constraints.
     */
    public List<ConstraintDefinition> getClassConstraints() {
        return classConstraints;
    }

    /**
     * Whether anything is declared to validate: a class-level constraint, or a constrained property.
     *
     * @return true if the bean has constraints.
     */
    public boolean isConstrained() {
        return constrained;
    }

    /**
     * Whether any property, or any container element position within a property type, is cascaded
     * with {@code @Valid}.
     *
     * @return true if validation descends into other objects.
     */
    public boolean hasCascades() {
        return cascading;
    }

    @Override
    public String toString() {
        return "BeanDefinition{beanClass=" + beanClass.getName() + ", properties=" + properties.size()
                + ", classConstraints=" + classConstraints.size() + '}';
    }

    /**
     * One walk, two consumers: the property discovery for the members and the class-level
     * constraints (and the {@code @GroupSequence} fail-fast) for the types.
     */
    private static final class Loader implements TypeHierarchyVisitor {

        private final PropertyDiscovery discovery;
        private final List<ConstraintDefinition> classConstraints = new ArrayList<>();

        private Loader(Class<?> beanClass) {
            super();
            this.discovery = new PropertyDiscovery(beanClass);
        }

        @Override
        public void visitType(Class<?> type) {
            // A sequence on a class redefines its Default group; on an interface it only defines a group.
            if (!type.isInterface() && type.isAnnotationPresent(GroupSequence.class)) {
                throw new GroupSequenceNotSupportedException(type);
            }

            classConstraints.addAll(ConstraintDefinition.load(type));
        }

        @Override
        public void visitField(Field field) {
            discovery.visitField(field);
        }

        @Override
        public void visitMethod(Method method) {
            discovery.visitMethod(method);
        }

        @Override
        public void visitRecordComponent(RecordComponent component) {
            discovery.visitRecordComponent(component);
        }
    }
}
