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
import org.rulii.validation.beans.util.reflect.TypeHierarchies;
import org.rulii.validation.beans.util.reflect.TypeHierarchyVisitor;

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
 * <p>Methods that are not getters are not properties: their constraints belong to executable
 * validation and are read by {@link ExecutableDiscovery}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyDiscovery implements TypeHierarchyVisitor {

    private final Class<?> owner;
    private final Map<String, List<ValueDeclaration>> fields = new LinkedHashMap<>();
    private final List<ValueDeclaration> getters = new ArrayList<>();

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
        add(fields, ValueDeclaration.ofField(field, owner));
    }

    @Override
    public void visitRecordComponent(RecordComponent component) {
        Assert.notNull(component, "component cannot be null.");
        add(fields, ValueDeclaration.ofRecordComponent(component));
    }

    /**
     * Collects the method when it is a JavaBeans getter; any other method is left to executable
     * discovery.
     *
     * @param method an instance method.
     */
    @Override
    public void visitMethod(Method method) {
        Assert.notNull(method, "method cannot be null.");
        if (ValueDeclaration.isGetter(method)) getters.add(ValueDeclaration.ofGetter(method, owner));
    }

    /**
     * The properties discovered so far, grouped and ordered as described in the class documentation.
     *
     * @return the property sources; empty if none were discovered.
     */
    public List<PropertySource> getSources() {
        Map<String, List<ValueDeclaration>> properties = new LinkedHashMap<>();

        for (Map.Entry<String, List<ValueDeclaration>> entry : fields.entrySet()) {
            properties.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        for (ValueDeclaration getter : getters) {
            add(properties, getter);
        }

        List<PropertySource> result = new ArrayList<>(properties.size());

        for (List<ValueDeclaration> declarations : properties.values()) {
            result.add(PropertySource.of(declarations));
        }

        return List.copyOf(result);
    }

    private static void add(Map<String, List<ValueDeclaration>> target, ValueDeclaration declaration) {
        target.computeIfAbsent(declaration.getName(), name -> new ArrayList<>()).add(declaration);
    }

    @Override
    public String toString() {
        return "PropertyDiscovery{owner=" + owner.getName() + ", fields=" + fields.size() + ", getters=" + getters.size() + '}';
    }
}
