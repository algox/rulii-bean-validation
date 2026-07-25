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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;

/**
 * Internal type-variable resolution against a concrete bean class. Walks the bean's
 * generic hierarchy once, collecting type-variable substitutions
 * ({@code class Wrapper<T> ... class StringWrapper extends Wrapper<String>} maps
 * {@code T -> String}), so declared types in generic superclasses resolve to concrete
 * classes where statically resolvable. Unresolvable variables fall back to their first
 * bound.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class TypeResolver {

    private final Map<TypeVariable<?>, Type> substitutions;

    TypeResolver(Class<?> contextClass) {
        super();
        this.substitutions = new HashMap<>();
        collect(contextClass);
    }

    private void collect(Class<?> type) {
        if (type == null || type == Object.class) return;

        collectParameterized(type.getGenericSuperclass());

        for (Type interfaceType : type.getGenericInterfaces()) {
            collectParameterized(interfaceType);
        }

        if (type.getSuperclass() != null) collect(type.getSuperclass());

        for (Class<?> interfaceClass : type.getInterfaces()) {
            collect(interfaceClass);
        }
    }

    private void collectParameterized(Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) return;

        Class<?> raw = (Class<?>) parameterizedType.getRawType();
        TypeVariable<?>[] variables = raw.getTypeParameters();
        Type[] arguments = parameterizedType.getActualTypeArguments();

        for (int i = 0; i < variables.length && i < arguments.length; i++) {
            substitutions.put(variables[i], arguments[i]);
        }
    }

    /**
     * Resolves the given type against the collected substitutions: type-variable chains
     * are chased to their concrete substitution where one exists.
     *
     * @param type the type to resolve.
     * @return the resolved type; the original type when no substitution applies.
     */
    Type resolve(Type type) {
        Type result = type;
        while (result instanceof TypeVariable<?> variable && substitutions.containsKey(variable)) {
            result = substitutions.get(variable);
        }
        return result;
    }

    /**
     * Resolves the given type to its raw class: parameterized types to their raw type,
     * generic arrays to array classes, unresolved type variables and wildcards to their
     * first upper bound.
     *
     * @param type the type to resolve.
     * @return the raw class; null when no raw class is determinable.
     */
    Class<?> resolveRaw(Type type) {
        Type resolved = resolve(type);

        if (resolved instanceof Class<?> result) return result;
        if (resolved instanceof ParameterizedType parameterizedType) return (Class<?>) parameterizedType.getRawType();

        if (resolved instanceof GenericArrayType arrayType) {
            Class<?> component = resolveRaw(arrayType.getGenericComponentType());
            return component != null ? Array.newInstance(component, 0).getClass() : null;
        }

        if (resolved instanceof TypeVariable<?> variable) {
            Type[] bounds = variable.getBounds();
            return bounds.length > 0 ? resolveRaw(bounds[0]) : Object.class;
        }

        if (resolved instanceof WildcardType wildcardType) {
            Type[] bounds = wildcardType.getUpperBounds();
            return bounds.length > 0 ? resolveRaw(bounds[0]) : Object.class;
        }

        return null;
    }
}
