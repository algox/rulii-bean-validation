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
package org.rulii.validation.beans.util.reflect;

import org.rulii.lib.spring.util.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks a class hierarchy for a {@link TypeHierarchyVisitor}: superclasses top-down, then the
 * interfaces, each type once, followed by its instance members. {@code Object}, {@code Record} and
 * {@code Enum} are never part of the walk, so {@code getClass()} and the other platform methods are
 * never visited.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class TypeHierarchies {

    private TypeHierarchies() {
        super();
    }

    /**
     * Walks the hierarchy of the given type and reports each type and its instance members to the
     * visitor, in the order documented on {@link TypeHierarchyVisitor}.
     *
     * @param root the class or interface whose hierarchy to walk.
     * @param visitor the visitor.
     * @throws IllegalArgumentException if the root is a primitive or an array type.
     */
    public static void walk(Class<?> root, TypeHierarchyVisitor visitor) {
        Assert.notNull(visitor, "visitor cannot be null.");

        for (Class<?> type : types(root)) {
            visitor.visitType(type);

            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Set<Method> accessors = new LinkedHashSet<>();

                for (RecordComponent component : components) {
                    visitor.visitRecordComponent(component);
                    accessors.add(component.getAccessor());
                }

                for (Method method : type.getDeclaredMethods()) {
                    if (isInstance(method.getModifiers(), method.isSynthetic()) && !accessors.contains(method)) {
                        visitor.visitMethod(method);
                    }
                }
            } else {
                for (Field field : type.getDeclaredFields()) {
                    if (isInstance(field.getModifiers(), field.isSynthetic())) visitor.visitField(field);
                }

                for (Method method : type.getDeclaredMethods()) {
                    if (isInstance(method.getModifiers(), method.isSynthetic())) visitor.visitMethod(method);
                }
            }
        }
    }

    /**
     * The types of the hierarchy of the given type in walk order: its superclasses top-down
     * ({@code Object}, {@code Record} and {@code Enum} excluded), the type itself, then the interfaces
     * of each of those classes in {@code implements}-clause order, each followed depth-first by its
     * superinterfaces, without duplicates. For an interface, the interface itself followed by its superinterfaces.
     *
     * @param root the class or interface.
     * @return the types, in walk order; empty for {@code Object}.
     * @throws IllegalArgumentException if the root is a primitive or an array type.
     */
    public static List<Class<?>> types(Class<?> root) {
        Assert.notNull(root, "root cannot be null.");
        Assert.isTrue(!root.isPrimitive() && !root.isArray(), "type [" + root.getName()
                + "] has no hierarchy to walk: it is a primitive or an array type.");

        List<Class<?>> classes = new ArrayList<>();

        for (Class<?> type = root; type != null && !isPlatformRoot(type); type = type.getSuperclass()) {
            classes.add(type);
        }

        Collections.reverse(classes);

        Set<Class<?>> result = new LinkedHashSet<>(classes);

        for (Class<?> type : classes) {
            addInterfaces(type, result);
        }

        return List.copyOf(result);
    }

    /**
     * The platform base classes that end a walk: {@code Object}, {@code Record} and {@code Enum}. They
     * declare no bean properties ({@code Enum} has {@code name} and {@code ordinal} fields that are not
     * properties of the enum) and their methods are never getters.
     */
    private static boolean isPlatformRoot(Class<?> type) {
        return type == Object.class || type == Record.class || type == Enum.class;
    }

    private static void addInterfaces(Class<?> type, Set<Class<?>> result) {
        for (Class<?> anInterface : type.getInterfaces()) {
            if (result.add(anInterface)) addInterfaces(anInterface, result);
        }
    }

    private static boolean isInstance(int modifiers, boolean synthetic) {
        return !Modifier.isStatic(modifiers) && !synthetic;
    }
}
