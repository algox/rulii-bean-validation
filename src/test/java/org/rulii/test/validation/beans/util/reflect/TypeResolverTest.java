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
package org.rulii.test.validation.beans.util.reflect;

import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.util.reflect.TypeResolver;
import org.rulii.validation.beans.util.reflect.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TypeResolver}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class TypeResolverTest {

    public TypeResolverTest() {
        super();
    }

    @Test
    public void resolvesFieldTypesOfAGenericSuperclassAgainstTheSubclass() throws Exception {
        TypeResolver resolver = TypeResolver.forClass(Names.class);

        assertEquals(String.class, resolver.resolve(field("single")));
        assertEquals("java.util.List<java.lang.String>", resolver.resolve(field("items")).getTypeName());
        assertEquals(String[].class, resolver.resolve(field("array")));
        assertEquals("java.util.Map<java.lang.String, java.util.List<java.lang.String>>", resolver.resolve(field("nested")).getTypeName());
        assertEquals("java.util.List<? extends java.lang.String>", resolver.resolve(field("wildcard")).getTypeName());
        assertEquals(String.class, resolver.resolve(Wrapper.class.getDeclaredMethod("getSingle").getGenericReturnType()));
        Type plain = field("plain"); // Field.getGenericType() builds a new instance per call
        assertSame(plain, resolver.resolve(plain), "a type without variables is returned as is");
    }

    @Test
    public void resolvesThroughSeveralLevelsAndInterfaces() throws Exception {
        TypeResolver resolver = TypeResolver.forClass(Leaf.class);

        assertEquals("java.util.List<java.lang.Integer>", resolver.resolve(field("items")).getTypeName());
        assertEquals(Integer.class, resolver.resolve(Identified.class.getDeclaredMethod("getId").getGenericReturnType()));
        assertEquals("java.lang.Integer[]", resolver.resolve(field("array")).getTypeName());
    }

    @Test
    public void unboundVariablesAreLeftInPlace() throws Exception {
        TypeResolver resolver = TypeResolver.forClass(Wrapper.class);
        Type single = resolver.resolve(field("single"));
        assertTrue(single instanceof TypeVariable);
        assertEquals(Object.class, Types.erasure(single));
        Type items = field("items");
        assertSame(items, resolver.resolve(items));

        // A raw supertype breaks the chain.
        assertTrue(TypeResolver.forClass(RawNames.class).resolve(field("single")) instanceof TypeVariable);
    }

    @Test
    public void parameterizedTypeSeedsItsOwnArguments() throws Exception {
        Type names = Sample.class.getDeclaredField("wrapperOfInteger").getGenericType();
        assertEquals("java.util.List<java.lang.Integer>", TypeResolver.forType(names).resolve(field("items")).getTypeName());
    }

    @Test
    public void supertypeView() throws Exception {
        Type myListOfInteger = Sample.class.getDeclaredField("myList").getGenericType();

        Type asList = TypeResolver.supertype(myListOfInteger, List.class);
        assertTrue(asList instanceof ParameterizedType);
        assertEquals(List.class, ((ParameterizedType) asList).getRawType());
        assertEquals(Integer.class, ((ParameterizedType) asList).getActualTypeArguments()[0]);
        assertEquals("java.util.Collection<java.lang.Integer>", TypeResolver.supertype(myListOfInteger, Collection.class).getTypeName());

        assertSame(myListOfInteger, TypeResolver.supertype(myListOfInteger, MyList.class));
        assertEquals(Map.class, TypeResolver.supertype(Map.class, Map.class));
        assertNull(TypeResolver.supertype(myListOfInteger, Map.class));

        // A fixed argument in the implements clause.
        assertEquals("java.util.List<java.lang.String>", TypeResolver.supertype(Strings.class, List.class).getTypeName());
        // A raw extends clause leaves the variables beyond it unbound.
        Type rawView = TypeResolver.supertype(RawList.class, List.class);
        assertEquals(List.class, Types.erasure(rawView));
        assertTrue(((ParameterizedType) rawView).getActualTypeArguments()[0] instanceof TypeVariable);
    }

    @Test
    public void parameterIndexMapsAnExtractorPositionOntoTheDeclaredClass() {
        assertEquals(0, TypeResolver.parameterIndex(MyList.class, List.class, 0));
        assertEquals(0, TypeResolver.parameterIndex(List.class, List.class, 0));
        assertEquals(1, TypeResolver.parameterIndex(Swapped.class, Map.class, 0), "Swapped<V, K> implements Map<K, V>");
        assertEquals(0, TypeResolver.parameterIndex(Swapped.class, Map.class, 1));
        assertEquals(-1, TypeResolver.parameterIndex(Strings.class, List.class, 0), "fixed argument");
        assertEquals(-1, TypeResolver.parameterIndex(MyList.class, Map.class, 0), "not a supertype");
        assertEquals(-1, TypeResolver.parameterIndex(MyList.class, List.class, 3), "no such position");
        assertEquals(-1, TypeResolver.parameterIndex(Nested.class, List.class, 0), "argument is a nested type, not a bare parameter");
    }

    @Test
    public void resolvedTypesFollowTheJdkEqualityContract() throws Exception {
        Type resolved = TypeResolver.forClass(Names.class).resolve(field("items"));
        Type declared = Sample.class.getDeclaredField("listOfString").getGenericType();

        assertEquals(declared, resolved);
        assertEquals(resolved, declared);
        assertEquals(declared.hashCode(), resolved.hashCode());

        Type wildcard = ((ParameterizedType) TypeResolver.forClass(Names.class).resolve(field("wildcard"))).getActualTypeArguments()[0];
        assertTrue(wildcard instanceof WildcardType);
        assertEquals(String.class, ((WildcardType) wildcard).getUpperBounds()[0]);
    }

    @Test
    public void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TypeResolver.forClass(null));
        assertThrows(IllegalArgumentException.class, () -> TypeResolver.forType(null));
        assertThrows(IllegalArgumentException.class, () -> TypeResolver.forClass(Names.class).resolve(null));
        assertThrows(IllegalArgumentException.class, () -> TypeResolver.supertype(null, List.class));
        assertThrows(IllegalArgumentException.class, () -> TypeResolver.supertype(Names.class, null));
        assertThrows(IllegalArgumentException.class, () -> TypeResolver.parameterIndex(null, List.class, 0));
    }

    private static Type field(String name) throws NoSuchFieldException {
        return Wrapper.class.getDeclaredField(name).getGenericType();
    }

    @SuppressWarnings("unused")
    private static class Wrapper<T> {
        T single;
        List<T> items;
        T[] array;
        Map<String, List<T>> nested;
        List<? extends T> wildcard;
        Optional<String> plain;
        public T getSingle() { return single; }
    }

    private static class Names extends Wrapper<String> { }

    @SuppressWarnings("rawtypes")
    private static class RawNames extends Wrapper { }

    private interface Identified<ID> { ID getId(); }
    private static class Middle<X> extends Wrapper<X> implements Identified<X> {
        @Override public X getId() { return null; }
    }
    private static class Leaf extends Middle<Integer> { }

    private abstract static class MyList<E> extends AbstractList<E> { }
    private abstract static class Strings extends AbstractList<String> { }
    @SuppressWarnings("rawtypes")
    private abstract static class RawList extends AbstractList { }
    private abstract static class Swapped<V, K> implements Map<K, V> { }
    private abstract static class Nested<E> extends AbstractList<List<E>> { }

    @SuppressWarnings("unused")
    private static class Sample {
        Wrapper<Integer> wrapperOfInteger;
        MyList<Integer> myList;
        List<String> listOfString;
    }
}
