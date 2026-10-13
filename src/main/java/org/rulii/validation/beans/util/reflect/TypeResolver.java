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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Resolves type variables against a concrete type. Built for a class (or a parameterized type), the
 * resolver knows the binding of every type variable declared along the generic hierarchy of that
 * type: for {@code class Names extends Wrapper<String>}, the {@code T} of {@code Wrapper<T>} is
 * {@code String}, so the field {@code List<T> items} of {@code Wrapper} resolves to
 * {@code List<String>} when read on {@code Names}.
 *
 * <p>Variables without a binding (the type itself is generic, or a raw supertype breaks the chain)
 * are left in place; callers erase them with {@link Types#erasure(Type)} when a class is needed.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class TypeResolver {

    private final Map<TypeVariable<?>, Type> bindings;

    private TypeResolver(Map<TypeVariable<?>, Type> bindings) {
        super();
        this.bindings = bindings;
    }

    /**
     * Resolver for the members of the given class, binding the type variables of its superclasses and
     * interfaces to the arguments the class (or an intermediate type) supplies.
     *
     * @param type the owning class.
     * @return the resolver.
     */
    public static TypeResolver forClass(Class<?> type) {
        Assert.notNull(type, "type cannot be null.");
        return forType(type);
    }

    /**
     * Resolver for the members of the given type: a class, or a parameterized type whose own
     * arguments bind the type variables of its raw class as well.
     *
     * @param type the owning type.
     * @return the resolver.
     */
    public static TypeResolver forType(Type type) {
        Assert.notNull(type, "type cannot be null.");
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        collect(type, bindings, new HashSet<>());
        return new TypeResolver(bindings);
    }

    private static void collect(Type type, Map<TypeVariable<?>, Type> bindings, Set<Class<?>> visited) {
        Class<?> raw = Types.erasure(type);
        if (raw == Object.class || !visited.add(raw)) return;

        if (type instanceof ParameterizedType) {
            TypeVariable<?>[] parameters = raw.getTypeParameters();
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();

            for (int i = 0; i < parameters.length; i++) {
                bindings.put(parameters[i], substitute(arguments[i], bindings));
            }
        }

        Type superclass = raw.getGenericSuperclass();
        if (superclass != null) collect(superclass, bindings, visited);

        for (Type anInterface : raw.getGenericInterfaces()) {
            collect(anInterface, bindings, visited);
        }
    }

    /**
     * The given type with every bound type variable replaced by its binding, recursively through
     * parameterized, array and wildcard types. Unbound variables are left in place; a type without
     * variables is returned as is.
     *
     * @param type the type to resolve.
     * @return the resolved type.
     */
    public Type resolve(Type type) {
        Assert.notNull(type, "type cannot be null.");
        return substitute(type, bindings);
    }

    /**
     * The given supertype as seen from the given type, with the type arguments the type supplies:
     * {@code List<Integer>} for {@code supertype(MyList<Integer>, List.class)} when
     * {@code MyList<E> implements List<E>}. The type itself when the supertype is its erasure.
     *
     * @param type the type.
     * @param supertype the class or interface to view the type as.
     * @return the supertype, parameterized when the chain supplies arguments; null if the given class
     *         is not a supertype of the type.
     */
    public static Type supertype(Type type, Class<?> supertype) {
        Assert.notNull(type, "type cannot be null.");
        Assert.notNull(supertype, "supertype cannot be null.");
        Class<?> raw = Types.erasure(type);

        if (!supertype.isAssignableFrom(raw)) return null;
        if (raw == supertype) return type;

        Type generic = genericSupertype(raw, supertype);
        return generic == null ? supertype : forType(type).resolve(generic);
    }

    /**
     * Which type parameter of a class supplies the argument at a position of one of its generic
     * supertypes: for {@code MyList<E> implements List<E>}, parameter 0 of {@code MyList} supplies
     * {@code List} position 0. Used to find the annotated type argument of a declared type that an
     * extractor position, declared on the container type, refers to.
     *
     * @param type the class.
     * @param supertype the supertype whose position is asked about.
     * @param position the type-argument position on the supertype.
     * @return the index of the type parameter of the class; -1 when the position is not supplied
     *         directly by a type parameter (a concrete argument, a nested type, or no such supertype).
     */
    public static int parameterIndex(Class<?> type, Class<?> supertype, int position) {
        Assert.notNull(type, "type cannot be null.");
        Assert.notNull(supertype, "supertype cannot be null.");

        if (type == supertype) return position >= 0 && position < type.getTypeParameters().length ? position : -1;

        Type resolved = supertype(type, supertype);
        if (!(resolved instanceof ParameterizedType)) return -1;

        Type[] arguments = ((ParameterizedType) resolved).getActualTypeArguments();
        if (position < 0 || position >= arguments.length) return -1;

        return Arrays.asList(type.getTypeParameters()).indexOf(arguments[position]);
    }

    /**
     * The generic supertype declaration through which a class reaches the given supertype, as
     * written in the extends/implements clause of the class on the path that declares it; null if
     * the supertype is not reached.
     */
    private static Type genericSupertype(Class<?> type, Class<?> supertype) {
        Type superclass = type.getGenericSuperclass();
        if (superclass != null) {
            Type found = genericSupertypeThrough(superclass, supertype);
            if (found != null) return found;
        }

        for (Type anInterface : type.getGenericInterfaces()) {
            Type found = genericSupertypeThrough(anInterface, supertype);
            if (found != null) return found;
        }

        return null;
    }

    private static Type genericSupertypeThrough(Type declared, Class<?> supertype) {
        Class<?> raw = Types.erasure(declared);
        if (raw == supertype) return declared;
        return supertype.isAssignableFrom(raw) ? genericSupertype(raw, supertype) : null;
    }

    private static Type substitute(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof Class) return type;

        if (type instanceof TypeVariable) {
            Type bound = bindings.get(type);
            return bound != null ? bound : type;
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            // The accessors return fresh arrays; read each once so the identity checks below mean "unchanged".
            Type[] declared = parameterized.getActualTypeArguments();
            Type declaredOwner = parameterized.getOwnerType();
            Type[] arguments = substituteAll(declared, bindings);
            Type owner = declaredOwner != null ? substitute(declaredOwner, bindings) : null;

            if (arguments == declared && owner == declaredOwner) return type;
            return new ParameterizedTypeImpl(owner, (Class<?>) parameterized.getRawType(), arguments);
        }

        if (type instanceof GenericArrayType) {
            Type component = ((GenericArrayType) type).getGenericComponentType();
            Type resolved = substitute(component, bindings);

            if (resolved == component) return type;
            return resolved instanceof Class ? Array.newInstance((Class<?>) resolved, 0).getClass() : new GenericArrayTypeImpl(resolved);
        }

        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            Type[] declaredUpper = wildcard.getUpperBounds();
            Type[] declaredLower = wildcard.getLowerBounds();
            Type[] upper = substituteAll(declaredUpper, bindings);
            Type[] lower = substituteAll(declaredLower, bindings);

            if (upper == declaredUpper && lower == declaredLower) return type;
            return new WildcardTypeImpl(upper, lower);
        }

        return type;
    }

    /**
     * Substitutes each type; returns the given array itself when nothing changed.
     */
    private static Type[] substituteAll(Type[] types, Map<TypeVariable<?>, Type> bindings) {
        Type[] result = null;

        for (int i = 0; i < types.length; i++) {
            Type resolved = substitute(types[i], bindings);

            if (resolved != types[i] && result == null) result = types.clone();
            if (result != null) result[i] = resolved;
        }

        return result != null ? result : types;
    }

    @Override
    public String toString() {
        return "TypeResolver" + bindings;
    }

    /**
     * A parameterized type with resolved arguments. Equality follows the JDK contract: same raw
     * type, owner type and arguments, against any {@link ParameterizedType}.
     */
    private static final class ParameterizedTypeImpl implements ParameterizedType {

        private final Type owner;
        private final Class<?> raw;
        private final Type[] arguments;

        private ParameterizedTypeImpl(Type owner, Class<?> raw, Type[] arguments) {
            super();
            this.owner = owner;
            this.raw = raw;
            this.arguments = arguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return owner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParameterizedType)) return false;
            ParameterizedType that = (ParameterizedType) o;
            return raw.equals(that.getRawType()) && Objects.equals(owner, that.getOwnerType())
                    && Arrays.equals(arguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(arguments) ^ Objects.hashCode(owner) ^ raw.hashCode();
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ", raw.getName() + "<", ">");
            for (Type argument : arguments) joiner.add(argument.getTypeName());
            return joiner.toString();
        }
    }

    /**
     * An array type whose component is a resolved parameterized or variable type.
     */
    private static final class GenericArrayTypeImpl implements GenericArrayType {

        private final Type component;

        private GenericArrayTypeImpl(Type component) {
            super();
            this.component = component;
        }

        @Override
        public Type getGenericComponentType() {
            return component;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GenericArrayType)) return false;
            return component.equals(((GenericArrayType) o).getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return component.hashCode();
        }

        @Override
        public String toString() {
            return component.getTypeName() + "[]";
        }
    }

    /**
     * A wildcard type with resolved bounds.
     */
    private static final class WildcardTypeImpl implements WildcardType {

        private final Type[] upper;
        private final Type[] lower;

        private WildcardTypeImpl(Type[] upper, Type[] lower) {
            super();
            this.upper = upper;
            this.lower = lower;
        }

        @Override
        public Type[] getUpperBounds() {
            return upper.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lower.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WildcardType)) return false;
            WildcardType that = (WildcardType) o;
            return Arrays.equals(upper, that.getUpperBounds()) && Arrays.equals(lower, that.getLowerBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(upper) ^ Arrays.hashCode(lower);
        }

        @Override
        public String toString() {
            if (lower.length > 0) return "? super " + lower[0].getTypeName();
            if (upper.length == 0 || upper[0] == Object.class) return "?";
            return "? extends " + upper[0].getTypeName();
        }
    }
}
