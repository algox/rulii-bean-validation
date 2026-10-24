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

import jakarta.validation.ParameterNameProvider;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.util.reflect.TypeHierarchies;
import org.rulii.validation.beans.util.reflect.TypeHierarchyVisitor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers the constrained methods and constructors of a bean class. Methods are collected across
 * the hierarchy and grouped by signature (name and parameter types), so a method and the methods it
 * overrides or implements form one group, which {@link ExecutableDefinition} defines together;
 * static and synthetic methods (bridges) are not visited. Constructors are the class's own; they are
 * not inherited.
 *
 * <p>This is a {@link TypeHierarchyVisitor}: walk a class with it through
 * {@link TypeHierarchies#walk(Class, TypeHierarchyVisitor)}, alongside {@link PropertyDiscovery},
 * and read {@link #getMethods}. Only constrained executables are returned: those with a constrained
 * or cascaded parameter or return value, or a cross-parameter constraint.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ExecutableDiscovery implements TypeHierarchyVisitor {

    private final Class<?> owner;
    private final Map<Signature, List<Method>> methods = new LinkedHashMap<>();

    /**
     * Discovery for the given bean class.
     *
     * @param owner the bean class being loaded.
     */
    public ExecutableDiscovery(Class<?> owner) {
        super();
        Assert.notNull(owner, "owner cannot be null.");
        this.owner = owner;
    }

    public Class<?> getOwner() {
        return owner;
    }

    /**
     * Discovers the constrained methods of the given class, across its superclasses and interfaces.
     *
     * @param beanClass the class.
     * @param registry the value extractors available at validation time.
     * @param names the parameter name provider.
     * @return the constrained methods, in discovery order; empty if none.
     */
    public static List<ExecutableDefinition> discover(Class<?> beanClass, ValueExtractorRegistry registry, ParameterNameProvider names) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        ExecutableDiscovery discovery = new ExecutableDiscovery(beanClass);
        TypeHierarchies.walk(beanClass, discovery);
        return discovery.getMethods(registry, names);
    }

    /**
     * The constrained constructors of the given class.
     *
     * @param beanClass the class.
     * @param registry the value extractors available at validation time.
     * @param names the parameter name provider.
     * @return the constrained constructors, in declaration order; empty if none.
     */
    public static List<ExecutableDefinition> constructors(Class<?> beanClass, ValueExtractorRegistry registry, ParameterNameProvider names) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        Assert.notNull(names, "names cannot be null.");
        List<ExecutableDefinition> result = new ArrayList<>();

        for (Constructor<?> constructor : beanClass.getDeclaredConstructors()) {
            if (constructor.isSynthetic()) continue;
            ExecutableDefinition definition = ExecutableDefinition.load(constructor, registry, names);
            if (definition.isConstrained()) result.add(definition);
        }

        return List.copyOf(result);
    }

    @Override
    public void visitMethod(Method method) {
        Assert.notNull(method, "method cannot be null.");
        methods.computeIfAbsent(new Signature(method.getName(), List.of(method.getParameterTypes())), key -> new ArrayList<>()).add(method);
    }

    /**
     * The constrained methods discovered so far, one definition per signature.
     *
     * @param registry the value extractors available at validation time.
     * @param names the parameter name provider.
     * @return the definitions, in discovery order; empty if none is constrained.
     */
    public List<ExecutableDefinition> getMethods(ValueExtractorRegistry registry, ParameterNameProvider names) {
        Assert.notNull(registry, "registry cannot be null.");
        Assert.notNull(names, "names cannot be null.");
        List<ExecutableDefinition> result = new ArrayList<>();

        for (List<Method> chain : methods.values()) {
            ExecutableDefinition definition = ExecutableDefinition.load(owner, chain, registry, names);
            if (definition.isConstrained()) result.add(definition);
        }

        return List.copyOf(result);
    }

    /**
     * A method signature: name and erased parameter types.
     */
    private record Signature(String name, List<Class<?>> parameterTypes) {}

    @Override
    public String toString() {
        return "ExecutableDiscovery{owner=" + owner.getName() + ", signatures=" + methods.size() + '}';
    }
}
