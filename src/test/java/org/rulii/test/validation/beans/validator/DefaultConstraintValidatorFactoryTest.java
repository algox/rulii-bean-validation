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
package org.rulii.test.validation.beans.validator;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.model.UnrulyException;
import org.rulii.util.reflect.DefaultObjectFactory;
import org.rulii.util.reflect.ObjectFactory;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.bootstrap.RuliiConfiguration;
import org.rulii.validation.beans.bootstrap.RuliiValidationProvider;
import org.rulii.validation.beans.bootstrap.RuliiValidatorFactory;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.validator.ConstraintValidatorException;
import org.rulii.validation.beans.validator.ConstraintValidatorMapper;
import org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultConstraintValidatorFactory}: validators are created through the object factory of the
 * options, uncached, post-construct honoured; an explicit factory wins.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class DefaultConstraintValidatorFactoryTest {

    public DefaultConstraintValidatorFactoryTest() {
        super();
    }

    @Test
    public void standardFactoryInstantiatesAndPostConstructs() {
        DefaultConstraintValidatorFactory factory = new DefaultConstraintValidatorFactory();

        PrefixedValidator validator = factory.getInstance(PrefixedValidator.class);
        assertNotNull(validator);
        assertTrue(validator.constructed, "@PostConstruct is invoked by the object factory");
        assertNotSame(validator, factory.getInstance(PrefixedValidator.class), "never cached");
        assertNotNull(factory.getObjectFactory());
        factory.releaseInstance(validator);
        assertTrue(factory.toString().contains("DefaultObjectFactory"));
    }

    @Test
    public void objectFactoryFailureIsAConstraintValidatorException() {
        DefaultConstraintValidatorFactory factory = new DefaultConstraintValidatorFactory();

        ConstraintValidatorException e = assertThrows(ConstraintValidatorException.class, () -> factory.getInstance(NoDefaultConstructorValidator.class));
        assertEquals(NoDefaultConstructorValidator.class, e.getValidatorClass());
        assertInstanceOf(UnrulyException.class, e.getCause());
        assertInstanceOf(ValidationException.class, e, "the spec failure type");
        assertThrows(IllegalArgumentException.class, () -> factory.getInstance(null));
        assertThrows(IllegalArgumentException.class, () -> new DefaultConstraintValidatorFactory(null));
    }

    @Test
    public void defaultFactoryUsesTheObjectFactoryOfTheOptions() {
        RecordingObjectFactory objectFactory = new RecordingObjectFactory();
        BeanValidator validator = BeanValidator.builder().options(new OptionsWith(objectFactory)).build();

        assertTrue(validator.validate(new TwoCodes()).isEmpty());
        assertEquals(List.of(PrefixedValidator.class, PrefixedValidator.class), objectFactory.created, "one validator per constraint occurrence");
        assertEquals(List.of(false, false), objectFactory.cached, "never from the object factory cache");

        ConstraintValidatorMapper<?> mapper = (ConstraintValidatorMapper<?>) validator.getConstraintMapperRegistry().find(Prefixed.class);
        assertNotNull(mapper);
        DefaultConstraintValidatorFactory factory = assertInstanceOf(DefaultConstraintValidatorFactory.class, mapper.getConstraintValidatorFactory());
        assertSame(objectFactory, factory.getObjectFactory());
    }

    @Test
    public void explicitFactoryWinsOverTheOptions() {
        RecordingObjectFactory objectFactory = new RecordingObjectFactory();
        ConstraintValidatorFactory explicit = new DefaultConstraintValidatorFactory();
        BeanValidator validator = BeanValidator.builder().options(new OptionsWith(objectFactory)).constraintValidatorFactory(explicit).build();

        assertTrue(validator.validate(new TwoCodes()).isEmpty());
        assertTrue(objectFactory.created.isEmpty(), "the object factory of the options is not consulted");
        ConstraintValidatorMapper<?> mapper = (ConstraintValidatorMapper<?>) validator.getConstraintMapperRegistry().find(Prefixed.class);
        assertNotNull(mapper);
        assertSame(explicit, mapper.getConstraintValidatorFactory());
    }

    @Test
    public void prebuiltRegistryKeepsItsOwnFactory() {
        RecordingObjectFactory objectFactory = new RecordingObjectFactory();
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().build();
        BeanValidator validator = BeanValidator.builder().options(new OptionsWith(objectFactory)).mappers(registry).build();

        assertTrue(validator.validate(new TwoCodes()).isEmpty());
        assertTrue(objectFactory.created.isEmpty(), "a prebuilt registry carries its own factory");
    }

    @Test
    public void bootstrapDerivesTheDefaultFromTheConfiguredOptions() {
        RecordingObjectFactory objectFactory = new RecordingObjectFactory();
        RuliiConfiguration configuration = Validation.byProvider(RuliiValidationProvider.class).configure().options(new OptionsWith(objectFactory));

        DefaultConstraintValidatorFactory reported = assertInstanceOf(DefaultConstraintValidatorFactory.class, configuration.getDefaultConstraintValidatorFactory());
        assertSame(objectFactory, reported.getObjectFactory());
        assertSame(reported, configuration.getDefaultConstraintValidatorFactory(), "built once");

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            assertInstanceOf(RuliiValidatorFactory.class, factory);
            DefaultConstraintValidatorFactory used = assertInstanceOf(DefaultConstraintValidatorFactory.class, factory.getConstraintValidatorFactory());
            assertSame(objectFactory, used.getObjectFactory());
            assertTrue(factory.getValidator().validate(new TwoCodes()).isEmpty());
            assertEquals(2, objectFactory.created.size());
        }
    }

    @Test
    public void bootstrapWithoutOptionsUsesAStandardObjectFactory() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            DefaultConstraintValidatorFactory used = assertInstanceOf(DefaultConstraintValidatorFactory.class, factory.getConstraintValidatorFactory());
            assertInstanceOf(DefaultObjectFactory.class, used.getObjectFactory());
        }
    }

    // --- fixtures ----------------------------------------------------------------------------------

    public static final class OptionsWith extends StandardRuleContextOptions {

        private final ObjectFactory objectFactory;

        public OptionsWith(ObjectFactory objectFactory) {
            super();
            this.objectFactory = objectFactory;
        }

        @Override
        public ObjectFactory getObjectFactory() {
            return objectFactory;
        }
    }

    public static final class RecordingObjectFactory extends DefaultObjectFactory {

        final List<Class<?>> created = new ArrayList<>();
        final List<Boolean> cached = new ArrayList<>();

        public RecordingObjectFactory() {
            super(true);
        }

        @Override
        public <T> T create(Class<T> type, boolean isUseCache) {
            // Core creates its own helpers (matching strategies of @Param) through the same factory; record validators only.
            if (ConstraintValidator.class.isAssignableFrom(type)) {
                created.add(type);
                cached.add(isUseCache);
            }

            return super.create(type, isUseCache);
        }
    }

    @Constraint(validatedBy = PrefixedValidator.class)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Prefixed {
        String message() default "must start with the prefix";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String prefix() default "X";
    }

    public static class PrefixedValidator implements ConstraintValidator<Prefixed, CharSequence> {

        boolean constructed;
        private String prefix;

        public PrefixedValidator() {
            super();
        }

        @PostConstruct
        public void init() {
            constructed = true;
        }

        @Override
        public void initialize(Prefixed prefixed) {
            this.prefix = prefixed.prefix();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            if (!constructed) throw new IllegalStateException("not post-constructed");
            return value == null || value.toString().startsWith(prefix);
        }
    }

    public static class NoDefaultConstructorValidator implements ConstraintValidator<Prefixed, Object> {

        public NoDefaultConstructorValidator(String unused) {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return true;
        }
    }

    public static class TwoCodes {
        @Prefixed String first = "X1";
        @Prefixed(prefix = "Y") String second = "Y2";
    }
}
