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
package org.rulii.test.validation.beans.mapper;

import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.mapper.ConstraintNotApplicableException;
import org.rulii.validation.beans.mapper.NoConstraintMapperException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the mapper fail-fast exceptions.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintMapperExceptionsTest {

    public ConstraintMapperExceptionsTest() {
        super();
    }

    @Test
    public void noConstraintMapperException() {
        NoConstraintMapperException e = new NoConstraintMapperException(Size.class, "com.acme.Order.total");
        assertInstanceOf(UnexpectedTypeException.class, e);
        assertEquals(Size.class, e.getAnnotationType());
        assertEquals("com.acme.Order.total", e.getLocation());
        assertTrue(e.getMessage().contains(Size.class.getName()));
        assertTrue(e.getMessage().contains("com.acme.Order.total"));
        assertTrue(e.getMessage().contains("ConstraintMapperRegistry.builder()"));
        assertTrue(e.getMessage().contains("ConstraintValidator"));
    }

    @Test
    public void constraintNotApplicableException() {
        List<Class<?>> supported = List.of(CharSequence.class, java.util.Collection.class);
        ConstraintNotApplicableException e = new ConstraintNotApplicableException(Size.class, "com.acme.Order.count",
                int.class, supported);
        assertInstanceOf(UnexpectedTypeException.class, e);
        assertEquals(Size.class, e.getAnnotationType());
        assertEquals("com.acme.Order.count", e.getLocation());
        assertEquals(int.class, e.getType());
        assertEquals(supported, e.getSupportedTypes());
        assertThrows(UnsupportedOperationException.class, () -> e.getSupportedTypes().clear());
        assertTrue(e.getMessage().contains("cannot apply to type [int]"));
        assertTrue(e.getMessage().contains("java.lang.CharSequence"));
    }
}
