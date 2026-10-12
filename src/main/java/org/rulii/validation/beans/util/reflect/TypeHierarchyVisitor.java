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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

/**
 * Visitor over a class hierarchy as walked by {@link TypeHierarchies#walk(Class, TypeHierarchyVisitor)}:
 * each type is visited once, followed by its instance members. All methods are no-ops by default so a
 * visitor implements only the callbacks it needs.
 *
 * <p>Order: the superclasses of the root top-down ({@code Object}, {@code Record} and {@code Enum}
 * excluded), the root itself, then the interfaces of each of those classes in {@code implements}-clause
 * order, each interface followed by its superinterfaces depth-first; a type reached by several paths is
 * visited once. For each type, {@link #visitType(Class)} is called first, then its record components if
 * it is a record or its instance fields otherwise, then its instance methods. Static and synthetic members are never
 * visited, which excludes bridge methods, outer-instance fields of inner classes and compiler or
 * instrumentation artefacts.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface TypeHierarchyVisitor {

    /**
     * A class or interface of the hierarchy, before any of its members.
     *
     * @param type the type.
     */
    default void visitType(Class<?> type) {}

    /**
     * A non-static, non-synthetic field of a class that is not a record.
     *
     * @param field the field.
     */
    default void visitField(Field field) {}

    /**
     * A non-static, non-synthetic method of a class or interface, in declaration order of the type.
     *
     * @param method the method.
     */
    default void visitMethod(Method method) {}

    /**
     * A component of a record, in component order. Neither the fields of a record nor the component
     * accessors are visited: the component stands for both.
     *
     * @param component the record component.
     */
    default void visitRecordComponent(RecordComponent component) {}
}
