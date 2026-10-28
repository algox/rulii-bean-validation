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

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.mapper.StandardConstraintMappers;
import org.rulii.validation.rules.asssert.AssertTrueValidationRule;
import org.rulii.validation.rules.digits.DigitsValidationRule;
import org.rulii.validation.rules.email.EmailValidationRule;
import org.rulii.validation.rules.future.FutureValidationRule;
import org.rulii.validation.rules.min.MinValidationRule;
import org.rulii.validation.rules.notempty.NotEmptyValidationRule;
import org.rulii.validation.rules.size.SizeValidationRule;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StandardConstraintMappers}, {@link SimpleConstraintMapper} and the static
 * applicability rule.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class StandardConstraintMappersTest {

    private static final ConstraintMapperRegistry REGISTRY = ConstraintMapperRegistry.builder().build();

    public StandardConstraintMappersTest() {
        super();
    }

    @Test
    public void tableHasOneMapperPerStandardConstraint() {
        List<ConstraintMapper<?>> all = StandardConstraintMappers.all();
        assertEquals(22, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add(null));

        Set<Class<? extends Annotation>> types = new HashSet<>();
        for (ConstraintMapper<?> mapper : all) {
            assertTrue(types.add(mapper.getAnnotationType()), "duplicate " + mapper.getAnnotationType());
            assertEquals("jakarta.validation.constraints", mapper.getAnnotationType().getPackageName());
            assertFalse(mapper.getSupportedTypes().isEmpty(), mapper.toString());
        }
    }

    @Test
    public void supportedTypesAreTheCoreRuleConstants() {
        assertEquals(SizeValidationRule.SUPPORTED_TYPES, supported(Size.class));
        assertEquals(MinValidationRule.SUPPORTED_TYPES, supported(Min.class));
        assertEquals(NotEmptyValidationRule.SUPPORTED_TYPES, supported(NotEmpty.class));
        assertEquals(FutureValidationRule.SUPPORTED_TYPES, supported(Future.class));
        assertEquals(AssertTrueValidationRule.SUPPORTED_TYPES, supported(AssertTrue.class));
        assertEquals(DigitsValidationRule.SUPPORTED_TYPES, supported(Digits.class));
        assertEquals(EmailValidationRule.SUPPORTED_TYPES, supported(Email.class));
        assertThrows(UnsupportedOperationException.class, () -> supported(Size.class).clear());
    }

    @Test
    public void simpleMapper() {
        SimpleConstraintMapper<Size> mapper = new SimpleConstraintMapper<>(Size.class, new ArrayList<>(List.of(CharSequence.class)),
                (a, t) -> List.of());
        assertEquals(Size.class, mapper.getAnnotationType());
        assertEquals(List.of(CharSequence.class), mapper.getSupportedTypes());
        assertThrows(UnsupportedOperationException.class, () -> mapper.getSupportedTypes().clear());
        assertTrue(mapper.toString().contains("Size"));
        assertThrows(IllegalArgumentException.class, () -> new SimpleConstraintMapper<>(null, List.of(), (a, t) -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimpleConstraintMapper<>(Size.class, null, (a, t) -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimpleConstraintMapper<>(Size.class, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new SimpleConstraintMapper<>(Size.class, List.of(), (a, t) -> List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(null, null));
    }

    @Test
    public void applicableWhenDeclaredTypeIsSupported() {
        ConstraintMapper<Size> size = mapper(Size.class);
        assertTrue(size.isApplicable(String.class));
        assertTrue(size.isApplicable(StringBuilder.class));
        assertTrue(size.isApplicable(List.class));
        assertTrue(size.isApplicable(ArrayList.class));
        assertTrue(size.isApplicable(Map.class));
        assertTrue(size.isApplicable(String[].class));
        assertTrue(size.isApplicable(int[].class));
        assertTrue(mapper(Min.class).isApplicable(BigDecimal.class));
        assertTrue(mapper(Future.class).isApplicable(LocalDate.class));
        assertTrue(mapper(Pattern.class).isApplicable(CharSequence.class));
    }

    @Test
    public void applicableWhenDeclaredTypeIsWiderThanASupportedType() {
        // Object, Number, Temporal...: the runtime value may well be supported; core checkType guards it.
        assertTrue(mapper(Size.class).isApplicable(Object.class));
        assertTrue(mapper(Min.class).isApplicable(Object.class));
        assertTrue(mapper(Min.class).isApplicable(Number.class));
        assertTrue(mapper(Future.class).isApplicable(java.time.temporal.Temporal.class));
        assertTrue(mapper(Size.class).isApplicable(Iterable.class));
    }

    @Test
    public void primitivesAreCheckedThroughTheirWrappers() {
        assertTrue(mapper(Min.class).isApplicable(int.class));
        assertTrue(mapper(Min.class).isApplicable(long.class));
        assertTrue(mapper(AssertTrue.class).isApplicable(boolean.class));
        assertTrue(mapper(AssertTrue.class).isApplicable(Boolean.class));
        assertFalse(mapper(AssertTrue.class).isApplicable(int.class));
    }

    @Test
    public void notApplicableWhenProvablyIncompatible() {
        assertFalse(mapper(Size.class).isApplicable(int.class));
        assertFalse(mapper(Size.class).isApplicable(Integer.class));
        assertFalse(mapper(Size.class).isApplicable(LocalDate.class));
        assertFalse(mapper(Pattern.class).isApplicable(Integer.class));
        assertFalse(mapper(Future.class).isApplicable(String.class));
        assertFalse(mapper(Min.class).isApplicable(LocalDate.class));
        assertFalse(mapper(Email.class).isApplicable(List.class));
        // An interface unrelated by assignment to every supported type is rejected too, even though
        // some class could implement it and extend a supported type (Integer is a Comparable Number).
        assertFalse(mapper(Min.class).isApplicable(Comparable.class));
    }

    @Test
    public void unrestrictedMapperAcceptsEverything() {
        ConstraintMapper<NotNull> notNull = mapper(NotNull.class);
        assertTrue(notNull.isApplicable(String.class));
        assertTrue(notNull.isApplicable(int.class));
        assertTrue(notNull.isApplicable(LocalDate.class));

        ConstraintMapper<NotNull> noRestriction = new SimpleConstraintMapper<>(NotNull.class, List.of(), (a, t) -> List.of());
        assertTrue(noRestriction.isApplicable(Void.class));
    }

    @Test
    public void isApplicableRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> mapper(Size.class).isApplicable(null));
    }

    private static <A extends Annotation> ConstraintMapper<A> mapper(Class<A> type) {
        ConstraintMapper<A> result = REGISTRY.find(type);
        assertNotNull(result, type.getSimpleName());
        return result;
    }

    private static List<Class<?>> supported(Class<? extends Annotation> type) {
        return mapper(type).getSupportedTypes();
    }
}
