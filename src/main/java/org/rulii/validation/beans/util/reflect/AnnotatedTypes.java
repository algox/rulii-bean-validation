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

import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Utilities for {@link AnnotatedType} trees.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class AnnotatedTypes {

    private AnnotatedTypes() {
        super();
    }

    /**
     * Dispatches the given annotated type to the matching visitor method. No recursion is
     * performed here; the visitor descends where it wants to.
     *
     * @param type the annotated type.
     * @param visitor the visitor.
     * @param <R> result type.
     * @return the visitor's result.
     */
    public static <R> R accept(AnnotatedType type, AnnotatedTypeVisitor<R> visitor) {
        Assert.notNull(type, "type cannot be null.");
        Assert.notNull(visitor, "visitor cannot be null.");

        if (type instanceof AnnotatedParameterizedType) return visitor.visitParameterized((AnnotatedParameterizedType) type);
        if (type instanceof AnnotatedArrayType) return visitor.visitArray((AnnotatedArrayType) type);
        if (type instanceof AnnotatedWildcardType) return visitor.visitWildcard((AnnotatedWildcardType) type);
        if (type instanceof AnnotatedTypeVariable) return visitor.visitTypeVariable((AnnotatedTypeVariable) type);
        return visitor.visitClass(type);
    }

    /**
     * Raw class of a type: the class itself, the raw type of a parameterized type, or the
     * array class of a generic array type.
     *
     * @param type the type.
     * @return the raw class; null when the type has no single raw class (wildcard, type variable).
     */
    public static Class<?> rawClass(Type type) {
        Assert.notNull(type, "type cannot be null.");
        if (type instanceof Class) return (Class<?>) type;
        if (type instanceof ParameterizedType) return (Class<?>) ((ParameterizedType) type).getRawType();
        if (type instanceof java.lang.reflect.GenericArrayType) {
            Class<?> component = rawClass(((java.lang.reflect.GenericArrayType) type).getGenericComponentType());
            return component != null ? java.lang.reflect.Array.newInstance(component, 0).getClass() : null;
        }
        return null;
    }
}
