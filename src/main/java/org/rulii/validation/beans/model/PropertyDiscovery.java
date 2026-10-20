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
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.util.reflect.AnnotatedTypeVisitor;
import org.rulii.validation.beans.util.reflect.AnnotatedTypes;
import org.rulii.validation.beans.util.reflect.TypeHierarchies;
import org.rulii.validation.beans.util.reflect.TypeHierarchyVisitor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers the properties of a bean class across its hierarchy, as the Bean Validation
 * specification defines them: every instance field, every JavaBeans getter and every record
 * component of the class, its superclasses and its interfaces, grouped by property name into
 * {@link PropertySource property sources}. Constraints on superclasses and interfaces are thereby
 * cumulative; static members are ignored at every level.
 *
 * <p>This is a {@link TypeHierarchyVisitor}: walk a class with it through
 * {@link TypeHierarchies#walk(Class, TypeHierarchyVisitor)} and read {@link #getSources()}, or use
 * {@link #discover(Class)} when nothing else needs the same walk. Fields and record components are
 * grouped as they are visited; getters are collected and joined to their property at the end.</p>
 *
 * <p>Properties are returned in a deterministic order: properties declared by a field (or a record
 * component) in hierarchy order top-down and declaration order within a class, then getter-only
 * properties in the same order. Within a property the declarations follow the hierarchy top-down,
 * fields then getters; see {@link PropertySource}.</p>
 *
 * <p>A constraint, {@code @Valid}, {@code @ConvertGroup} or {@code @Validate} declared on, or within
 * the return type of, an instance method that is not a getter fails fast with
 * {@link ConstraintOnNonGetterException}: method validation is out of scope and the annotation would
 * otherwise be ignored silently.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyDiscovery implements TypeHierarchyVisitor {

    private final Class<?> owner;
    private final Map<String, List<PropertyDeclaration>> fields = new LinkedHashMap<>();
    private final List<PropertyDeclaration> getters = new ArrayList<>();

    /**
     * Discovery for the given bean class: the owner every declaration is read for, against which
     * generic types declared on superclasses and interfaces are resolved.
     *
     * @param owner the bean class being loaded.
     */
    public PropertyDiscovery(Class<?> owner) {
        super();
        Assert.notNull(owner, "owner cannot be null.");
        this.owner = owner;
    }

    /**
     * The bean class the declarations are read for.
     *
     * @return the owner.
     */
    public Class<?> getOwner() {
        return owner;
    }

    /**
     * Discovers the properties of the given class, across its superclasses and interfaces.
     *
     * @param beanClass the class or interface.
     * @return the property sources, in discovery order; empty if the class has no properties.
     * @throws ConstraintOnNonGetterException if a constraint, {@code @Valid}, {@code @ConvertGroup}
     *         or {@code @Validate} is declared on or within the return type of a method that is not
     *         a getter.
     * @throws IllegalArgumentException if the class is a primitive or an array type.
     */
    public static List<PropertySource> discover(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        PropertyDiscovery discovery = new PropertyDiscovery(beanClass);
        TypeHierarchies.walk(beanClass, discovery);
        return discovery.getSources();
    }

    @Override
    public void visitField(Field field) {
        Assert.notNull(field, "field cannot be null.");
        add(fields, PropertyDeclaration.ofField(field, owner));
    }

    @Override
    public void visitRecordComponent(RecordComponent component) {
        Assert.notNull(component, "component cannot be null.");
        add(fields, PropertyDeclaration.ofRecordComponent(component));
    }

    /**
     * Collects the method as a getter, or fails fast if a method that is not a getter carries an
     * annotation only a property may carry.
     *
     * @param method the method.
     * @throws ConstraintOnNonGetterException if the method is not a getter and carries a constraint,
     *         {@code @Valid}, {@code @ConvertGroup} or {@code @Validate}, directly or within its
     *         return type.
     */
    @Override
    public void visitMethod(Method method) {
        Assert.notNull(method, "method cannot be null.");

        if (PropertyDeclaration.isGetter(method)) {
            getters.add(PropertyDeclaration.ofGetter(method, owner));
            return;
        }

        Annotation annotation = findPropertyAnnotation(method);
        if (annotation != null) throw new ConstraintOnNonGetterException(method, annotation);
    }

    /**
     * The properties discovered so far: field (or record component) properties first, then
     * getter-only properties, each in hierarchy order.
     *
     * @return the property sources; empty before any walk.
     */
    public List<PropertySource> getSources() {
        Map<String, List<PropertyDeclaration>> properties = new LinkedHashMap<>();

        for (Map.Entry<String, List<PropertyDeclaration>> entry : fields.entrySet()) {
            properties.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        for (PropertyDeclaration getter : getters) {
            add(properties, getter);
        }

        List<PropertySource> result = new ArrayList<>(properties.size());

        for (List<PropertyDeclaration> declarations : properties.values()) {
            result.add(PropertySource.of(declarations));
        }

        return List.copyOf(result);
    }

    /**
     * Whether the given method carries an annotation that only a property may carry: a constraint,
     * a repeated-constraint container, {@code @Valid}, {@code @ConvertGroup},
     * {@code @ConvertGroup.List} or {@code @Validate}, directly or anywhere within its annotated
     * return type.
     *
     * @param method the method.
     * @return the first such annotation; null if there is none.
     */
    static Annotation findPropertyAnnotation(Method method) {
        Annotation result = propertyAnnotation(method);
        return result != null ? result : AnnotatedTypes.accept(method.getAnnotatedReturnType(), new ReturnTypeScan());
    }

    private static Annotation propertyAnnotation(AnnotatedElement element) {
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (type == Valid.class || type == ConvertGroup.class || type == ConvertGroup.List.class || type == Validate.class
                    || ConstraintDefinition.isConstraint(annotation)
                    || ConstraintDefinition.isConstraintContainer(annotation)) {
                return annotation;
            }
        }

        return null;
    }

    private static void add(Map<String, List<PropertyDeclaration>> target, PropertyDeclaration declaration) {
        target.computeIfAbsent(declaration.getName(), name -> new ArrayList<>()).add(declaration);
    }

    /**
     * Finds the first property-only annotation anywhere in an annotated type tree.
     */
    private static final class ReturnTypeScan implements AnnotatedTypeVisitor<Annotation> {

        private ReturnTypeScan() {
            super();
        }

        @Override
        public Annotation visitParameterized(AnnotatedParameterizedType type) {
            Annotation result = propertyAnnotation(type);

            for (AnnotatedType argument : type.getAnnotatedActualTypeArguments()) {
                if (result != null) break;
                result = AnnotatedTypes.accept(argument, this);
            }

            return result;
        }

        @Override
        public Annotation visitArray(AnnotatedArrayType type) {
            Annotation result = propertyAnnotation(type);
            return result != null ? result : AnnotatedTypes.accept(type.getAnnotatedGenericComponentType(), this);
        }

        @Override
        public Annotation visitWildcard(AnnotatedWildcardType type) {
            Annotation result = propertyAnnotation(type);

            for (AnnotatedType bound : type.getAnnotatedUpperBounds()) {
                if (result != null) break;
                result = AnnotatedTypes.accept(bound, this);
            }

            for (AnnotatedType bound : type.getAnnotatedLowerBounds()) {
                if (result != null) break;
                result = AnnotatedTypes.accept(bound, this);
            }

            return result;
        }

        @Override
        public Annotation visitTypeVariable(AnnotatedTypeVariable type) {
            return propertyAnnotation(type);
        }

        @Override
        public Annotation visitClass(AnnotatedType type) {
            return propertyAnnotation(type);
        }
    }
}
