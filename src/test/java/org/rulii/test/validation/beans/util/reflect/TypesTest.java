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
import org.rulii.validation.beans.util.reflect.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Types}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class TypesTest {

    public TypesTest() {
        super();
    }

    @Test
    public void erasure() throws Exception {
        assertEquals(String.class, Types.erasure(String.class));
        assertEquals(int.class, Types.erasure(int.class));
        assertEquals(List.class, Types.erasure(generic("strings")));
        assertEquals(CharSequence.class, Types.erasure(argument("extendsCharSequence", 0)));
        assertEquals(Object.class, Types.erasure(argument("unbounded", 0)));
        assertEquals(Object.class, Types.erasure(argument("superInteger", 0)));
        assertEquals(Number.class, Types.erasure(Sample.class.getDeclaredField("variable").getGenericType()));
        assertEquals(Object.class, Types.erasure(Sample.class.getDeclaredField("unboundedVariable").getGenericType()));
        assertEquals(List[].class, Types.erasure(generic("genericArray")));
        assertEquals(String[].class, Types.erasure(String[].class));
    }

    @Test
    public void componentType() throws Exception {
        assertEquals(String.class, Types.componentType(String[].class));
        assertEquals(int.class, Types.componentType(int[].class));
        Type component = Types.componentType(generic("genericArray"));
        assertTrue(component instanceof ParameterizedType);
        assertEquals(List.class, ((ParameterizedType) component).getRawType());
        assertThrows(IllegalArgumentException.class, () -> Types.componentType(String.class));
        assertThrows(IllegalArgumentException.class, () -> Types.componentType(generic("strings")));
    }

    @Test
    public void nullIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Types.erasure(null));
        assertThrows(IllegalArgumentException.class, () -> Types.componentType(null));
    }

    private static Type generic(String field) throws NoSuchFieldException {
        return Sample.class.getDeclaredField(field).getGenericType();
    }

    private static Type argument(String field, int index) throws NoSuchFieldException {
        return ((ParameterizedType) generic(field)).getActualTypeArguments()[index];
    }

    @SuppressWarnings("unused")
    private static class Sample<T extends Number, U> {
        List<String> strings;
        List<? extends CharSequence> extendsCharSequence;
        List<?> unbounded;
        List<? super Integer> superInteger;
        T variable;
        U unboundedVariable;
        List<String>[] genericArray;
        Map<String, Integer> map;
    }
}
