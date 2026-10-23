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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistryBuilder;
import org.rulii.validation.beans.mapper.DefaultConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintTarget;
import org.rulii.rule.Rule;
import org.rulii.validation.beans.mapper.StandardConstraintMappers;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConstraintMapperRegistry}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintMapperRegistryTest {

    private static final List<Class<? extends Annotation>> STANDARD = List.of(
            AssertFalse.class, AssertTrue.class, DecimalMax.class, DecimalMin.class, Digits.class, Email.class,
            Future.class, FutureOrPresent.class, Max.class, Min.class, Negative.class, NegativeOrZero.class,
            NotBlank.class, NotEmpty.class, NotNull.class, Null.class, Past.class, PastOrPresent.class,
            Pattern.class, Positive.class, PositiveOrZero.class, Size.class);

    public ConstraintMapperRegistryTest() {
        super();
    }

    @Test
    public void standardRegistryCoversAllStandardConstraints() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().build();
        assertEquals(22, STANDARD.size());
        assertEquals(44, registry.getMappers().size());

        for (Class<? extends Annotation> type : STANDARD) {
            ConstraintMapper<?> mapper = registry.find(type);
            assertNotNull(mapper, type.getSimpleName());
            assertEquals(type, mapper.getAnnotationType());
            assertFalse(mapper.getSupportedTypes().isEmpty(), type.getSimpleName());
        }
    }

    @Test
    public void emptyRegistryHasNoMappers() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder(false).build();
        assertTrue(registry.getMappers().isEmpty());
        assertNull(registry.find(Size.class));
        assertNull(registry.find(Custom.class));
    }

    @Test
    public void builderVariants() {
        assertEquals(44, ConstraintMapperRegistryBuilder.standard().build().getMappers().size());
        assertTrue(ConstraintMapperRegistryBuilder.empty().build().getMappers().isEmpty());
        assertEquals(44, ConstraintMapperRegistry.builder(true).build().getMappers().size());
    }

    @Test
    public void registersCustomMapperTyped() {
        CustomMapper custom = new CustomMapper();
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().mapper(custom).build();
        ConstraintMapper<Custom> found = registry.find(Custom.class);
        assertSame(custom, found);
        assertEquals(45, registry.getMappers().size());
    }

    @Test
    public void registrationReplacesSameType() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().build();
        ConstraintMapper<Size> standard = registry.find(Size.class);
        assertInstanceOf(SimpleConstraintMapper.class, standard);
        assertTrue(StandardConstraintMappers.all().contains(standard));

        ConstraintMapper<Size> replacement = new ConstraintMapper<>() {
            @Override
            public Class<Size> getAnnotationType() {
                return Size.class;
            }

            @Override
            public List<Class<?>> getSupportedTypes() {
                return List.of(Object.class);
            }

            @Override
            public List<Rule> map(Size annotation, ConstraintTarget target) {
                return List.of();
            }
        };
        registry.register(replacement);

        assertSame(replacement, registry.find(Size.class));
        assertEquals(44, registry.getMappers().size());
    }

    @Test
    public void registrationOrderIsKept() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder(false)
                .mapper(new CustomMapper())
                .mapper(new SimpleConstraintMapper<>(Size.class, List.of(CharSequence.class), (a, t) -> List.of()))
                .build();
        List<ConstraintMapper<?>> mappers = registry.getMappers();
        assertEquals(Custom.class, mappers.get(0).getAnnotationType());
        assertEquals(Size.class, mappers.get(1).getAnnotationType());
        assertThrows(UnsupportedOperationException.class, () -> mappers.add(null));
    }

    @Test
    public void rejectsMapperForNonConstraintAnnotation() {
        ConstraintMapperRegistry registry = new DefaultConstraintMapperRegistry(false);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry.register(new NotAConstraintMapper()));
        assertTrue(e.getMessage().contains(NotAConstraint.class.getName()));
        assertTrue(e.getMessage().contains("not a constraint"));
    }

    @Test
    public void rejectsMapperWithoutAnnotationType() {
        ConstraintMapperRegistry registry = new DefaultConstraintMapperRegistry(false);
        ConstraintMapper<Custom> broken = new ConstraintMapper<>() {
            @Override
            public Class<Custom> getAnnotationType() {
                return null;
            }

            @Override
            public List<Class<?>> getSupportedTypes() {
                return List.of();
            }

            @Override
            public List<Rule> map(Custom annotation, ConstraintTarget target) {
                return List.of();
            }
        };
        assertThrows(IllegalArgumentException.class, () -> registry.register(broken));
    }

    @Test
    public void nullArgumentsAreRejected() {
        ConstraintMapperRegistry registry = new DefaultConstraintMapperRegistry(false);
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
        assertThrows(IllegalArgumentException.class, () -> registry.find(null));
        assertThrows(IllegalArgumentException.class, () -> ConstraintMapperRegistryBuilder.empty().mapper(null));
    }

    @Test
    public void toStringShowsCount() {
        assertTrue(new DefaultConstraintMapperRegistry(true).toString().contains("44"));
    }

    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Custom {
        String message() default "custom";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotAConstraint {
    }

    public static final class CustomMapper implements ConstraintMapper<Custom> {

        public CustomMapper() {
            super();
        }

        @Override
        public Class<Custom> getAnnotationType() {
            return Custom.class;
        }

        @Override
        public List<Class<?>> getSupportedTypes() {
            return List.of(CharSequence.class);
        }

        @Override
        public List<Rule> map(Custom annotation, ConstraintTarget target) {
            return List.of();
        }
    }

    public static final class NotAConstraintMapper implements ConstraintMapper<NotAConstraint> {

        public NotAConstraintMapper() {
            super();
        }

        @Override
        public Class<NotAConstraint> getAnnotationType() {
            return NotAConstraint.class;
        }

        @Override
        public List<Class<?>> getSupportedTypes() {
            return List.of();
        }

        @Override
        public List<Rule> map(NotAConstraint annotation, ConstraintTarget target) {
            return List.of();
        }
    }
}
