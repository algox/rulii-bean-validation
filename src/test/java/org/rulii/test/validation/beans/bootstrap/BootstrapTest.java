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
package org.rulii.test.validation.beans.bootstrap;

import jakarta.validation.ClockProvider;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.executable.ExecutableType;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.valueextraction.ValueExtractorDeclarationException;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import org.junit.jupiter.api.Test;
import org.rulii.registry.RuleRegistry;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.extract.NoValueExtractorException;
import org.rulii.validation.beans.traversal.TraversableResolvers;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.bootstrap.RuliiConfiguration;
import org.rulii.validation.beans.bootstrap.RuliiValidationProvider;
import org.rulii.validation.beans.bootstrap.RuliiValidatorContext;
import org.rulii.validation.beans.bootstrap.RuliiValidatorFactory;
import org.rulii.validation.rules.Validators;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spec bootstrap: {@code Validation} finds the rulii provider and every path to a validator works
 * without naming this library.
 */
public class BootstrapTest {

    public BootstrapTest() {
        super();
    }

    @Test
    public void defaultBootstrapFindsTheRuliiProvider() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

        assertInstanceOf(RuliiValidatorFactory.class, factory);
        Validator validator = factory.getValidator();
        assertInstanceOf(BeanValidator.class, validator);
        assertSame(validator, factory.getValidator(), "one validator per factory");

        Order order = new Order();
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertEquals(1, violations.size());
        assertEquals("code", violations.iterator().next().getPropertyPath().toString());

        assertNotNull(factory.getMessageInterpolator());
        assertNotNull(factory.getTraversableResolver());
        assertNotNull(factory.getConstraintValidatorFactory());
        assertNotNull(factory.getParameterNameProvider());
        assertNotNull(factory.getClockProvider());
        assertSame(factory, factory.unwrap(RuliiValidatorFactory.class));
        assertSame(validator, factory.unwrap(BeanValidator.class));
        assertThrows(ValidationException.class, () -> factory.unwrap(String.class));
        factory.close();
        assertEquals(1, factory.getValidator().validate(order).size(), "still usable after close");
    }

    @Test
    public void providerSpecificConfiguration() {
        RuleRegistry registry = RuleRegistry.builder()
                .register(Validators.notNull(Validators.binding("code")).name("codeRule").build())
                .build();
        MessageInterpolator interpolator = new CustomInterpolator();
        ClockProvider clock = () -> Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC);

        RuliiConfiguration configuration = Validation.byProvider(RuliiValidationProvider.class).configure();
        ValidatorFactory factory = configuration
                .registry(registry)
                .addValueExtractor(new BoxValueExtractor())
                .addProperty("rulii.example", "yes")
                .messageInterpolator(interpolator)
                .clockProvider(clock)
                .ignoreXmlConfiguration()
                .buildValidatorFactory();

        BeanValidator validator = (BeanValidator) factory.getValidator();
        assertSame(registry, validator.getRuleRegistry());
        assertNotNull(validator.getValueExtractorRegistry().find(Box.class, 0));
        assertSame(interpolator, factory.getMessageInterpolator());
        assertSame(clock, factory.getClockProvider());
        assertEquals("yes", ((RuliiValidatorFactory) factory).getProperties().get("rulii.example"));
        assertEquals(1, ((RuliiValidatorFactory) factory).getValueExtractors().size());

        ConfiguredOrder order = new ConfiguredOrder();
        order.code = null;
        order.box = new Box<>("");
        order.delivery = LocalDate.of(2010, 1, 1);
        // @Validate through the configured registry, the box through the configured extractor, @Future against the configured clock.
        assertEquals(List.of("", "box", "code"), validator.validate(order).getViolations().stream()
                .map(v -> v.getPropertyPath().toString()).sorted().toList());
        order.delivery = LocalDate.of(1990, 1, 1);
        order.code = "ok";
        order.box = null;
        assertEquals(List.of("delivery"), validator.validate(order).getViolations().stream().map(v -> v.getPropertyPath().toString()).toList(),
                "1990 is in the past of the fixed 2000 clock");

        // The configuration is also the state the provider reads.
        assertTrue(configuration.isIgnoreXmlConfiguration());
        assertTrue(configuration.getMappingStreams().isEmpty());
        assertEquals(Set.of("rulii.example"), configuration.getProperties().keySet());
        assertSame(configuration.getDefaultTraversableResolver(), configuration.getTraversableResolver());
        assertSame(interpolator, configuration.getMessageInterpolator());
        assertNotNull(configuration.getDefaultConstraintValidatorFactory());
        assertNotNull(configuration.getDefaultParameterNameProvider());
        assertNotNull(configuration.getDefaultMessageInterpolator());
        assertNotNull(configuration.getDefaultClockProvider());
        assertNull(configuration.getBootstrapConfiguration().getDefaultProviderClassName());
        assertTrue(configuration.getBootstrapConfiguration().isExecutableValidationEnabled(), "the specification's default without XML");
        assertEquals(Set.of(ExecutableType.CONSTRUCTORS, ExecutableType.NON_GETTER_METHODS),
                configuration.getBootstrapConfiguration().getDefaultValidatedExecutableTypes());
        assertTrue(configuration.getBootstrapConfiguration().getProperties().isEmpty());
        assertThrows(ValidationException.class, () -> configuration.addMapping(new ByteArrayInputStream(new byte[0])), "no XML mappings");
        assertThrows(IllegalArgumentException.class, () -> configuration.registry(null));
    }

    @Test
    public void genericConfigurationBuildsThroughTheResolvedProvider() {
        Configuration<?> configuration = Validation.byDefaultProvider().configure();
        MessageInterpolator interpolator = new CustomInterpolator();

        ValidatorFactory factory = configuration.messageInterpolator(interpolator).buildValidatorFactory();

        assertInstanceOf(RuliiValidatorFactory.class, factory);
        assertSame(interpolator, factory.getMessageInterpolator());
        assertInstanceOf(BeanValidator.class, factory.getValidator());
    }

    @Test
    public void validatorContextOverridesPerValidator() {
        RuliiValidatorFactory factory = (RuliiValidatorFactory) Validation.buildDefaultValidatorFactory();
        MessageInterpolator interpolator = new CustomInterpolator();

        RuliiValidatorContext plain = factory.usingContext();
        assertSame(factory.getValidator(), plain.getValidator(), "nothing overridden: the factory's validator");
        assertSame(factory.getMessageInterpolator(), plain.getMessageInterpolator());

        RuliiValidatorContext context = factory.usingContext()
                .addValueExtractor(new BoxValueExtractor())
                .messageInterpolator(interpolator)
                .clockProvider(() -> Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC));
        BeanValidator validator = context.getValidator();
        assertNotSame(factory.getValidator(), validator);
        assertNotNull(validator.getValueExtractorRegistry().find(Box.class, 0));
        assertNull(factory.getValidator().getValueExtractorRegistry().find(Box.class, 0), "the factory's validator is untouched");
        assertSame(interpolator, context.getMessageInterpolator());
        assertNotNull(context.getTraversableResolver());
        assertNotNull(context.getConstraintValidatorFactory());
        assertNotNull(context.getParameterNameProvider());
        assertNotNull(context.getClockProvider());

        Order order = new Order();
        order.code = "ok";
        order.delivery = LocalDate.of(2010, 1, 1);
        assertTrue(validator.validate(order).isEmpty(), "2010 is in the future of the context's fixed 2000 clock");
        assertEquals(1, factory.getValidator().validate(order).size(), "and in the past of the real clock");
    }

    @Test
    public void defaults() {
        RuliiConfiguration configuration = Validation.byProvider(RuliiValidationProvider.class).configure();

        assertEquals("{x}", configuration.getDefaultMessageInterpolator().interpolate("{x}", null));
        assertEquals("{x}", configuration.getDefaultMessageInterpolator().interpolate("{x}", null, Locale.GERMAN));
        assertSame(TraversableResolvers.getDefault(), configuration.getDefaultTraversableResolver(), "the runtime default: JPA-aware when the persistence API is present");
        assertTrue(configuration.getDefaultTraversableResolver().isCascadable(null, null, Order.class, null, null));
        assertNotNull(configuration.getDefaultConstraintValidatorFactory().getInstance(SomeValidator.class));
        configuration.getDefaultConstraintValidatorFactory().releaseInstance(null);
        assertThrows(ValidationException.class, () -> configuration.getDefaultConstraintValidatorFactory().getInstance(NoDefaultConstructorValidator.class));
        assertEquals(1, configuration.getDefaultParameterNameProvider().getParameterNames(Box.class.getConstructors()[0]).size());
        assertNotNull(configuration.getDefaultClockProvider().getClock());
    }

    // ---- fixtures ----

    public static class Order {
        @NotBlank
        String code = "";

        @Future
        LocalDate delivery;
    }

    @Validate("codeRule")
    public static class ConfiguredOrder {
        @NotBlank
        String code = "";

        Box<@NotBlank String> box;

        @Future
        LocalDate delivery;
    }

    public static class Box<V> {
        final V value;

        public Box(V value) {
            super();
            this.value = value;
        }
    }

    public static class BoxValueExtractor implements ValueExtractor<Box<@ExtractedValue ?>> {
        @Override
        public void extractValues(Box<?> originalValue, ValueReceiver receiver) {
            receiver.value(null, originalValue.value);
        }
    }

    public static class CustomInterpolator implements MessageInterpolator {
        @Override
        public String interpolate(String messageTemplate, Context context) {
            return "custom";
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return "custom";
        }
    }

    public static class SomeValidator implements jakarta.validation.ConstraintValidator<NotNull, Object> {
        @Override
        public boolean isValid(Object value, jakarta.validation.ConstraintValidatorContext context) {
            return true;
        }
    }

    public static class NoDefaultConstructorValidator implements jakarta.validation.ConstraintValidator<NotNull, Object> {
        public NoDefaultConstructorValidator(String unused) {
            super();
        }

        @Override
        public boolean isValid(Object value, jakarta.validation.ConstraintValidatorContext context) {
            return true;
        }
    }

    @Test
    public void serviceProvidedExtractorsAreDiscoveredByTheBootstrap() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        // Listed in META-INF/services/jakarta.validation.valueextraction.ValueExtractor: found without any registration.
        assertNotNull(((BeanValidator) validator).getValueExtractorRegistry().find(Crate.class, 0));
        assertEquals(1, validator.validate(new DiscoveredCrateExtractor.Cargo()).size());

        // The rulii-native builder does no discovery: the crate is unknown to it.
        assertThrows(NoValueExtractorException.class, () -> BeanValidator.builder().build().validate(new DiscoveredCrateExtractor.Cargo()));

        // Precedence: a configured extractor beats the discovered one, a context extractor beats both.
        ValueExtractor<?> lenient = new DiscoveredCrateExtractor.LenientCrateExtractor();
        assertTrue(Validation.byProvider(RuliiValidationProvider.class).configure().addValueExtractor(lenient).buildValidatorFactory()
                .getValidator().validate(new DiscoveredCrateExtractor.Cargo()).isEmpty());
        assertTrue(factory.usingContext().addValueExtractor(lenient).getValidator().validate(new DiscoveredCrateExtractor.Cargo()).isEmpty());

        // One extractor per container type and type argument at each level.
        assertThrows(ValueExtractorDeclarationException.class, () -> Validation.byDefaultProvider().configure()
                .addValueExtractor(lenient).addValueExtractor(new DiscoveredCrateExtractor()));
        assertThrows(ValueExtractorDeclarationException.class, () -> factory.usingContext()
                .addValueExtractor(lenient).addValueExtractor(new DiscoveredCrateExtractor()));
    }
}
