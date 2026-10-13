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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

/**
 * Helpers over {@link Type}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Types {

    private Types() {
        super();
    }

    /**
     * Erasure of a type, as the compiler computes it: the class itself, the raw type of a
     * parameterized type, the array class of a generic array type, and the erasure of the first
     * upper bound of a wildcard or type variable ({@code Object} when unbounded).
     *
     * @param type the type.
     * @return the erased class; never null.
     */
    public static Class<?> erasure(Type type) {
        Assert.notNull(type, "type cannot be null.");

        if (type instanceof Class) return (Class<?>) type;
        if (type instanceof ParameterizedType) return (Class<?>) ((ParameterizedType) type).getRawType();
        if (type instanceof GenericArrayType) {
            return Array.newInstance(erasure(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        }
        if (type instanceof WildcardType) return erasure(((WildcardType) type).getUpperBounds()[0]);
        if (type instanceof TypeVariable) return erasure(((TypeVariable<?>) type).getBounds()[0]);

        throw new IllegalArgumentException("unknown kind of type [" + type + "] (" + type.getClass().getName() + ").");
    }

    /**
     * Component type of an array type, generic or not.
     *
     * @param arrayType the array type.
     * @return the component type.
     * @throws IllegalArgumentException if the type is not an array type.
     */
    public static Type componentType(Type arrayType) {
        Assert.notNull(arrayType, "arrayType cannot be null.");

        if (arrayType instanceof GenericArrayType) return ((GenericArrayType) arrayType).getGenericComponentType();
        if (arrayType instanceof Class && ((Class<?>) arrayType).isArray()) return ((Class<?>) arrayType).getComponentType();

        throw new IllegalArgumentException("type [" + arrayType + "] is not an array type.");
    }
}
