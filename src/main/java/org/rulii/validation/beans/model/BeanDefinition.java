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
import jakarta.validation.ParameterNameProvider;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Validation definition of one bean class: its {@link PropertyDefinition properties} in discovery
 * order, its class-level {@link ConstraintDefinition constraints} in hierarchy order, the registry
 * rules attached at class level with {@code @Validate} ({@link ValidateDefinition}), also in
 * hierarchy order, and its constrained {@link ExecutableDefinition methods and constructors}.
 * Everything declared on superclasses and interfaces is included, cumulatively.
 *
 * <p>Loading walks the class hierarchy once ({@link TypeHierarchies}); the property declarations,
 * the class-level constraints and the methods are collected from the same walk. All declaration
 * errors fail fast here, at metadata-read time.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanDefinition implements Definition {

    private final Class<?> beanClass;
    private final List<PropertyDefinition> properties;
    private final List<ConstraintDefinition> classConstraints;
    private final List<ValidateDefinition> validateRules;
    private final List<ExecutableDefinition> methods;
    private final List<ExecutableDefinition> constructors;
    private final SourceDefinition source;
    private final boolean constrained;
    private final boolean cascading;

    private BeanDefinition(Class<?> beanClass, List<PropertyDefinition> properties, List<ConstraintDefinition> classConstraints,
                           List<ValidateDefinition> validateRules, List<ExecutableDefinition> methods, List<ExecutableDefinition> constructors) {
        super();
        this.beanClass = beanClass;
        this.properties = properties;
        this.classConstraints = classConstraints;
        this.validateRules = validateRules;
        this.methods = methods;
        this.constructors = constructors;
        this.source = SourceDefinition.forClass(beanClass);

        boolean constrained = !classConstraints.isEmpty() || !validateRules.isEmpty();
        boolean cascading = false;

        for (PropertyDefinition property : properties) {
            constrained |= property.isConstrained();
            cascading |= property.isCascaded() || cascades(property.getContainerElements());
        }

        this.constrained = constrained;
        this.cascading = cascading;
    }

    /**
     * Loads the definition of the given bean class, across its superclasses and interfaces, with the
     * compiler's parameter names.
     *
     * @param beanClass the bean class.
     * @param registry the value extractors available at validation time.
     * @return the bean definition.
     * @see #load(Class, ValueExtractorRegistry, ParameterNameProvider)
     */
    public static BeanDefinition load(Class<?> beanClass, ValueExtractorRegistry registry) {
        return load(beanClass, registry, new DefaultParameterNameProvider());
    }

    /**
     * Loads the definition of the given bean class, across its superclasses and interfaces.
     *
     * @param beanClass the bean class.
     * @param registry the value extractors available at validation time.
     * @param names the parameter name provider naming the parameters of constrained executables.
     * @return the bean definition.
     * @throws GroupSequenceNotSupportedException if {@code @GroupSequence} is declared on the class
     *         or one of its superclasses.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared on a property,
     *         a parameter or a return value.
     * @throws ValidateDeclarationException if a {@code @Validate} declares no name, a blank name, or
     *         a name already referenced for the same property or at class level.
     * @throws IllegalExecutableConstraintException if a method or constructor is constrained against
     *         the specification's rules (see {@link ExecutableDefinition}).
     * @throws AmbiguousConstraintTargetException if a method constraint could validate the parameters
     *         or the return value and the method has both.
     * @throws org.rulii.validation.beans.extract.NoValueExtractorException if a constrained or
     *         cascaded container element position has no extractor.
     * @throws jakarta.validation.ConstraintDeclarationException for the other declaration errors
     *         detected while loading.
     * @throws IllegalArgumentException if the class is a primitive or an array type.
     */
    public static BeanDefinition load(Class<?> beanClass, ValueExtractorRegistry registry, ParameterNameProvider names) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        Assert.notNull(names, "names cannot be null.");

        Loader loader = new Loader(beanClass);
        TypeHierarchies.walk(beanClass, loader);

        List<PropertySource> sources = loader.properties.getSources();
        List<PropertyDefinition> properties = new ArrayList<>(sources.size());

        for (PropertySource propertySource : sources) {
            properties.add(PropertyDefinition.load(propertySource, registry));
        }

        ValidateDefinition.checkDistinct(loader.validateRules, "class [" + beanClass.getName() + "]");

        return new BeanDefinition(beanClass, Collections.unmodifiableList(properties),
                Collections.unmodifiableList(loader.classConstraints), Collections.unmodifiableList(loader.validateRules),
                loader.executables.getMethods(registry, names), ExecutableDiscovery.constructors(beanClass, registry, names));
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
     * The properties, in discovery order.
     *
     * @return the property definitions; empty if the class has no properties.
     */
    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * The property with the given name.
     *
     * @param name the property name.
     * @return the property definition, or null if the class has no such property.
     */
    public PropertyDefinition getProperty(String name) {
        Assert.notNull(name, "name cannot be null.");

        for (PropertyDefinition property : properties) {
            if (property.getName().equals(name)) return property;
        }

        return null;
    }

    /**
     * The class-level constraints, in hierarchy order.
     *
     * @return the constraint definitions; empty if none.
     */
    public List<ConstraintDefinition> getClassConstraints() {
        return classConstraints;
    }

    /**
     * The registry rules attached at class level, in hierarchy order.
     *
     * @return the definitions; empty if none.
     */
    public List<ValidateDefinition> getValidateRules() {
        return validateRules;
    }

    /**
     * The constrained methods, getters included, in discovery order.
     *
     * @return the definitions; empty if none.
     */
    public List<ExecutableDefinition> getMethods() {
        return methods;
    }

    /**
     * The constrained method with the given signature.
     *
     * @param name the method name.
     * @param parameterTypes the parameter types.
     * @return the definition, or null if no such method is constrained.
     */
    public ExecutableDefinition getMethod(String name, Class<?>... parameterTypes) {
        Assert.notNull(name, "name cannot be null.");
        Assert.notNull(parameterTypes, "parameterTypes cannot be null.");

        for (ExecutableDefinition method : methods) {
            if (method.matches(name, parameterTypes)) return method;
        }

        return null;
    }

    /**
     * The constrained constructors of the class itself, in declaration order.
     *
     * @return the definitions; empty if none.
     */
    public List<ExecutableDefinition> getConstructors() {
        return constructors;
    }

    /**
     * The constrained constructor with the given parameter types.
     *
     * @param parameterTypes the parameter types.
     * @return the definition, or null if no such constructor is constrained.
     */
    public ExecutableDefinition getConstructor(Class<?>... parameterTypes) {
        Assert.notNull(parameterTypes, "parameterTypes cannot be null.");

        for (ExecutableDefinition constructor : constructors) {
            if (Arrays.equals(constructor.getParameterTypes(), parameterTypes)) return constructor;
        }

        return null;
    }

    /**
     * Whether validating an instance of the class has anything to do: a constrained property, a
     * class-level constraint or a {@code @Validate} rule. Executable constraints do not count.
     *
     * @return true if the bean is constrained.
     */
    public boolean isConstrained() {
        return constrained;
    }

    /**
     * Whether validating an instance cascades into other objects.
     *
     * @return true if a property or a container element position is cascaded.
     */
    public boolean hasCascades() {
        return cascading;
    }

    @Override
    public String toString() {
        return "BeanDefinition{beanClass=" + beanClass.getName() + ", properties=" + properties.size()
                + ", classConstraints=" + classConstraints.size() + ", validateRules=" + validateRules.size()
                + ", methods=" + methods.size() + ", constructors=" + constructors.size() + '}';
    }

    /**
     * The hierarchy visitor of one load: properties and methods from the same walk.
     */
    private static final class Loader implements TypeHierarchyVisitor {

        private final PropertyDiscovery properties;
        private final ExecutableDiscovery executables;
        private final List<ConstraintDefinition> classConstraints = new ArrayList<>();
        private final List<ValidateDefinition> validateRules = new ArrayList<>();

        private Loader(Class<?> beanClass) {
            super();
            this.properties = new PropertyDiscovery(beanClass);
            this.executables = new ExecutableDiscovery(beanClass);
        }

        @Override
        public void visitType(Class<?> type) {
            // A sequence on a class redefines its Default group; on an interface it only defines a group.
            if (!type.isInterface() && type.isAnnotationPresent(GroupSequence.class)) {
                throw new GroupSequenceNotSupportedException(type);
            }
            classConstraints.addAll(ConstraintDefinition.load(type));
            validateRules.addAll(ValidateDefinition.load(type));
        }

        @Override
        public void visitField(Field field) {
            properties.visitField(field);
        }

        @Override
        public void visitMethod(Method method) {
            properties.visitMethod(method);
            executables.visitMethod(method);
        }

        @Override
        public void visitRecordComponent(RecordComponent component) {
            properties.visitRecordComponent(component);
        }
    }
}
