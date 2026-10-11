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
import org.rulii.validation.beans.util.reflect.AnnotatedTypeVisitor;
import org.rulii.validation.beans.util.reflect.AnnotatedTypes;

import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AnnotatedTypes} and {@link AnnotatedTypeVisitor} dispatch.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class AnnotatedTypesTest {

    public AnnotatedTypesTest() {
        super();
    }

    /** Names the shape visited; descends nowhere. */
    private static final AnnotatedTypeVisitor<String> SHAPE = new AnnotatedTypeVisitor<>() {
        @Override public String visitParameterized(AnnotatedParameterizedType type) { return "parameterized"; }
        @Override public String visitArray(AnnotatedArrayType type) { return "array"; }
        @Override public String visitWildcard(AnnotatedWildcardType type) { return "wildcard"; }
        @Override public String visitTypeVariable(AnnotatedTypeVariable type) { return "typeVariable"; }
        @Override public String visitClass(AnnotatedType type) { return "class"; }
    };

    @Test
    public void dispatchesEachShape() throws Exception {
        assertEquals("class", AnnotatedTypes.accept(annotatedType("plain"), SHAPE));
        assertEquals("parameterized", AnnotatedTypes.accept(annotatedType("list"), SHAPE));
        assertEquals("array", AnnotatedTypes.accept(annotatedType("array"), SHAPE));
        assertEquals("typeVariable", AnnotatedTypes.accept(annotatedType("variable"), SHAPE));

        AnnotatedParameterizedType wildcarded = (AnnotatedParameterizedType) annotatedType("wildcarded");
        assertEquals("wildcard", AnnotatedTypes.accept(wildcarded.getAnnotatedActualTypeArguments()[0], SHAPE));
    }

    @Test
    public void rawClass() throws Exception {
        assertEquals(String.class, AnnotatedTypes.rawClass(field("plain").getGenericType()));
        assertEquals(List.class, AnnotatedTypes.rawClass(field("list").getGenericType()));
        assertEquals(String[].class, AnnotatedTypes.rawClass(field("array").getGenericType()));
        assertEquals(List[].class, AnnotatedTypes.rawClass(field("genericArray").getGenericType()));
        assertNull(AnnotatedTypes.rawClass(field("variable").getGenericType()));

        AnnotatedParameterizedType wildcarded = (AnnotatedParameterizedType) annotatedType("wildcarded");
        assertNull(AnnotatedTypes.rawClass(wildcarded.getAnnotatedActualTypeArguments()[0].getType()));
    }

    @Test
    public void nullArgumentsAreRejected() throws Exception {
        AnnotatedType type = annotatedType("plain");
        assertThrows(IllegalArgumentException.class, () -> AnnotatedTypes.accept(null, SHAPE));
        assertThrows(IllegalArgumentException.class, () -> AnnotatedTypes.accept(type, null));
        assertThrows(IllegalArgumentException.class, () -> AnnotatedTypes.rawClass(null));
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Sample.class.getDeclaredField(name);
    }

    private static AnnotatedType annotatedType(String name) throws NoSuchFieldException {
        return field(name).getAnnotatedType();
    }

    @SuppressWarnings("unused")
    private static class Sample<T> {
        private String plain;
        private List<String> list;
        private String[] array;
        private List<String>[] genericArray;
        private T variable;
        private Map<? extends Number, String> wildcarded;
    }
}
