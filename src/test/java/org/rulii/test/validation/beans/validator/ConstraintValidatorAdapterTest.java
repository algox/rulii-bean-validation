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

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;
import org.rulii.context.RuleContext;
import org.rulii.model.UnrulyException;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.beans.BeanValidationResult;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.bootstrap.RuliiValidationProvider;
import org.rulii.validation.beans.bootstrap.RuliiValidatorFactory;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.ConstraintNotApplicableException;
import org.rulii.validation.beans.mapper.NoConstraintMapperException;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.validator.AmbiguousConstraintValidatorException;
import org.rulii.validation.beans.validator.ConstraintValidatorException;
import org.rulii.validation.beans.validator.ConstraintValidatorMapper;
import org.rulii.validation.beans.validator.RuliiConstraintValidatorContext;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Constraints backed by {@code ConstraintValidator} classes run through the adapter: validator
 * selection by declared type, the factory and the instance lifecycle, null handling, the validator
 * context's default and custom violations with their templates and paths, and mapper precedence.
 */
public class ConstraintValidatorAdapterTest {

    public ConstraintValidatorAdapterTest() {
        super();
    }

    private static List<String> report(Iterable<? extends ConstraintViolation<?>> violations) {
        List<String> result = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath() + ":" + ((RuleViolation) violation).getRuleName() + ":" + violation.getMessage());
        }
        return result;
    }

    // --- running validators ---------------------------------------------------------------------

    @Test
    public void validatorBackedConstraintRunsUnchanged() {
        BeanValidator validator = BeanValidator.builder().build();
        Account account = new Account();
        account.iban = "FR12";

        BeanValidationResult<Account> violations = validator.validate(account);

        assertEquals(List.of("iban:iban_iban:must be an IBAN"), report(violations));
        ConstraintViolation<Account> violation = violations.iterator().next();
        assertEquals("FR12", violation.getInvalidValue());
        assertEquals("must be an IBAN", violation.getMessageTemplate());
        assertEquals(Iban.class, violation.getConstraintDescriptor().getAnnotation().annotationType());

        account.iban = "GB12";
        assertTrue(validator.validate(account).isEmpty());
    }

    @Test
    public void initializeReceivesTheAnnotation() {
        BeanValidator validator = BeanValidator.builder().build();
        Prefixed prefixed = new Prefixed();
        prefixed.code = "GB12";

        assertEquals(1, validator.validate(prefixed).size(), "the configured prefix is DE");
        prefixed.code = "DE12";
        assertTrue(validator.validate(prefixed).isEmpty());
    }

    @Test
    public void nullReachesTheValidator() {
        BeanValidator validator = BeanValidator.builder().build();
        Account account = new Account();

        assertTrue(validator.validate(account).isEmpty(), "the IBAN validator accepts null");

        Required required = new Required();
        assertEquals(List.of("value:value_nullRejecting:must be present"), report(validator.validate(required)));
    }

    @Test
    public void validateValueRunsTheValidator() {
        BeanValidator validator = BeanValidator.builder().build();

        assertEquals(1, validator.validateValue(Account.class, "iban", "FR12").size());
        assertTrue(validator.validateValue(Account.class, "iban", "GB12").isEmpty());
    }

    @Test
    public void descriptorReportsTheValidatorClasses() {
        BeanValidator validator = BeanValidator.builder().build();
        ConstraintDescriptor<?> descriptor = validator.getConstraintsForClass(Account.class).getConstraintsForProperty("iban")
                .getConstraintDescriptors().iterator().next();

        assertEquals(List.of(IbanValidator.class), descriptor.getConstraintValidatorClasses());
    }

    @Test
    public void compositionWithValidatorsRunsBoth() {
        BeanValidator validator = BeanValidator.builder().build();
        Named named = new Named();

        assertEquals(List.of("name:name_validName:never valid", "name:name_validName_notNull:must not be null"),
                report(validator.validate(named)));
    }

    // --- selection ------------------------------------------------------------------------------

    @Test
    public void validatorIsSelectedByDeclaredType() {
        BeanValidator validator = BeanValidator.builder().build();
        Signed signed = new Signed();
        signed.count = -1;
        signed.text = "-1";

        assertEquals(List.of("count:count_positiveSign:number", "text:text_positiveSign:text"), report(validator.validate(signed)));
    }

    @Test
    public void mostSpecificValidatorWins() {
        BeanValidator validator = BeanValidator.builder().build();
        Specific specific = new Specific();
        specific.text = "x";
        specific.chars = new StringBuilder("x");

        assertEquals(List.of("text:text_rejectAll:string", "chars:chars_rejectAll:chars"), report(validator.validate(specific)));
    }

    @Test
    public void noValidatorForTheDeclaredTypeFailsTheLoad() {
        BeanValidator validator = BeanValidator.builder().build();

        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class, () -> validator.getBeanDefinition(Untyped.class));
        assertEquals(PositiveSign.class, e.getAnnotationType());
        assertInstanceOf(UnexpectedTypeException.class, e);
    }

    @Test
    public void equallySpecificValidatorsFailTheLoad() {
        BeanValidator validator = BeanValidator.builder().build();

        AmbiguousConstraintValidatorException e = assertThrows(AmbiguousConstraintValidatorException.class,
                () -> validator.getBeanDefinition(Ambiguous.class));
        assertEquals(Twice.class, e.getAnnotationType());
        assertEquals(String.class, e.getType());
        assertEquals(2, e.getValidatorClasses().size());
    }

    @Test
    public void validatedTypeIsResolvedThroughTheHierarchy() {
        assertEquals(CharSequence.class, ConstraintValidatorMapper.validatedType(IbanValidator.class));
        assertEquals(String.class, ConstraintValidatorMapper.validatedType(StringRejecter.class));
        assertTrue(ConstraintValidatorMapper.declaresValidators(Iban.class));
        assertFalse(ConstraintValidatorMapper.declaresValidators(NotNull.class));
    }

    @Test
    public void constraintWithNeitherMapperNorValidatorsStillFails() {
        BeanValidator validator = BeanValidator.builder().build();

        NoConstraintMapperException e = assertThrows(NoConstraintMapperException.class, () -> validator.getBeanDefinition(Unmapped.class));
        assertTrue(e.getMessage().contains("validatedBy"), e.getMessage());
    }

    // --- precedence and registry ----------------------------------------------------------------

    @Test
    public void registeredMapperTakesPrecedenceOverTheValidators() {
        CountingFactory factory = new CountingFactory();
        BeanValidator validator = BeanValidator.builder()
                .constraintValidatorFactory(factory)
                .mapper(new SimpleConstraintMapper<>(Iban.class, List.of(CharSequence.class),
                        (iban, target) -> List.of(target.configure(Validators.startsWith(target.value(), "XX")).build())))
                .build();
        Account account = new Account();
        account.iban = "GB12";

        assertEquals(1, validator.validate(account).size(), "the mapper's rule runs instead of the validator");
        assertEquals(0, factory.created.get(), "no validator was created");
        assertTrue(validator.getConstraintMapperRegistry().getMappers().stream().anyMatch(m -> m.getAnnotationType() == Iban.class));
    }

    @Test
    public void registryFindsTheValidatorMapperAndListsOnlyRegisteredOnes() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().build();
        int registered = registry.getMappers().size();

        assertInstanceOf(ConstraintValidatorMapper.class, registry.find(Iban.class));
        assertSame(registry.find(Iban.class), registry.find(Iban.class), "synthesized once");
        assertNull(registry.find(Plain.class), "no validators, no mapper");
        assertEquals(registered, registry.getMappers().size());

        registry.register(new SimpleConstraintMapper<>(Iban.class, List.of(CharSequence.class), (a, t) -> List.of()));
        assertInstanceOf(SimpleConstraintMapper.class, registry.find(Iban.class), "registration replaces the synthesized mapper");
    }

    @Test
    public void factoryCannotBeCombinedWithAWholeRegistry() {
        assertThrows(IllegalArgumentException.class, () -> BeanValidator.builder()
                .mappers(ConstraintMapperRegistry.builder().build())
                .constraintValidatorFactory(new CountingFactory()));
    }

    // --- lifecycle ------------------------------------------------------------------------------

    @Test
    public void oneInstancePerOccurrenceThroughTheConfiguredFactory() {
        CountingFactory factory = new CountingFactory();
        BeanValidator validator = BeanValidator.builder().constraintValidatorFactory(factory).build();
        TwoIbans bean = new TwoIbans();

        validator.validate(bean);
        validator.validate(bean);
        assertEquals(2, factory.created.get(), "one per occurrence, reused across validations");

        ConstraintValidatorMapper<?> mapper = (ConstraintValidatorMapper<?>) validator.getConstraintMapperRegistry().find(Iban.class);
        assertEquals(2, mapper.getInstances().size());
        assertSame(factory, mapper.getConstraintValidatorFactory());
    }

    @Test
    public void bootstrapFactoryIsUsedAndReleasedOnClose() {
        CountingFactory factory = new CountingFactory();
        RuliiValidatorFactory validatorFactory = (RuliiValidatorFactory) Validation.byProvider(RuliiValidationProvider.class).configure()
                .constraintValidatorFactory(factory)
                .buildValidatorFactory();
        BeanValidator validator = validatorFactory.getValidator();
        Account account = new Account();
        account.iban = "FR12";

        assertEquals(1, validator.validate(account).size());
        assertEquals(1, factory.created.get());
        assertEquals(0, factory.released.get());

        validatorFactory.close();
        assertEquals(1, factory.released.get());
        assertEquals(1, validator.validate(account).size(), "still usable with the instance it holds");
        assertEquals(1, factory.created.get());
    }

    @Test
    public void validatorContextFactoryOverrideBuildsANewValidator() {
        RuliiValidatorFactory validatorFactory = (RuliiValidatorFactory) Validation.buildDefaultValidatorFactory();
        CountingFactory factory = new CountingFactory();
        BeanValidator validator = validatorFactory.usingContext().constraintValidatorFactory(factory).getValidator();
        Account account = new Account();
        account.iban = "FR12";

        assertNotSame(validatorFactory.getValidator(), validator);
        assertEquals(1, validator.validate(account).size());
        assertEquals(1, factory.created.get());

        validatorFactory.close();
        assertEquals(1, factory.released.get(), "validators built through the context are released with the factory");
    }

    @Test
    public void factoryReturningNothingFails() {
        BeanValidator validator = BeanValidator.builder().constraintValidatorFactory(new ConstraintValidatorFactory() {
            @Override
            public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                return null;
            }

            @Override
            public void releaseInstance(ConstraintValidator<?, ?> instance) {
                // Nothing to release.
            }
        }).build();

        ConstraintValidatorException e = assertThrows(ConstraintValidatorException.class, () -> validator.validate(new Account()));
        assertEquals(IbanValidator.class, e.getValidatorClass());
    }

    @Test
    public void initializeFailureFailsTheMaterialization() {
        BeanValidator validator = BeanValidator.builder().build();

        ConstraintValidatorException e = assertThrows(ConstraintValidatorException.class, () -> validator.validate(new BadlyConfigured()));
        assertEquals(FailingInitializer.class, e.getValidatorClass());
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    public void isValidFailureSurfacesTheValidator() {
        BeanValidator validator = BeanValidator.builder().build();
        Exploding exploding = new Exploding();
        exploding.value = "x";

        UnrulyException e = assertThrows(UnrulyException.class, () -> validator.validate(exploding));
        Throwable cause = e;
        while (cause != null && !(cause instanceof ConstraintValidatorException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "the validator's failure is in the chain");
        assertEquals(Thrower.class, ((ConstraintValidatorException) cause).getValidatorClass());
    }

    // --- the context ----------------------------------------------------------------------------

    @Test
    public void classLevelCustomViolationsReportAtTheirProperties() {
        BeanValidator validator = BeanValidator.builder().build();
        Address address = new Address();

        assertEquals(List.of("street:Address_validAddress:street is required", "city:Address_validAddress:city needs 3 characters"),
                report(validator.validate(address)));
    }

    @Test
    public void defaultViolationIsKeptUnlessDisabled() {
        BeanValidator validator = BeanValidator.builder().build();
        Address address = new Address();
        address.street = "Main";
        address.city = "Rome";
        address.extra = true;

        assertEquals(List.of(":Address_validAddress:invalid address", "extra:Address_validAddress:no extras"),
                report(validator.validate(address)));
    }

    @Test
    public void propertyLevelCustomViolationsExtendThePropertyPath() {
        BeanValidator validator = BeanValidator.builder().build();
        Parcel parcel = new Parcel();
        parcel.postcode = new Postcode();
        parcel.tags = List.of("ok", "bad");

        assertEquals(List.of("postcode.code:postcode_validPostcode:code is required",
                        "postcode:postcode_validPostcode:the whole thing",
                        "tags[1].<list element>:tags_cleanTags:[ok, bad] is dirty",
                        "tags[bad]:tags_cleanTags:keyed",
                        "tags[]:tags_cleanTags:somewhere"),
                report(validator.validate(parcel)));
    }

    @Test
    public void customTemplatesInterpolateLikeDeclaredOnes() {
        BeanValidator validator = BeanValidator.builder().build();
        Templated templated = new Templated();
        templated.value = "v";

        assertEquals(List.of("value:value_templates:custom text", "value:value_templates:v is below 3",
                "value:value_templates:must be templated"), report(validator.validate(templated)));
    }

    @Test
    public void contextExposesTheClockAndUnwraps() {
        Clock clock = Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC);
        BeanValidator validator = ((RuliiValidatorFactory) Validation.byProvider(RuliiValidationProvider.class).configure()
                .clockProvider(() -> clock).buildValidatorFactory()).getValidator();
        Clocked clocked = new Clocked();
        ClockChecker.seen = null;

        assertTrue(validator.validate(clocked).isEmpty());
        assertSame(clock, ClockChecker.seen);
    }

    @Test
    public void parameterNodesAreRefused() {
        BeanValidator validator = BeanValidator.builder().build();
        ParameterNoded bean = new ParameterNoded();

        UnrulyException e = assertThrows(UnrulyException.class, () -> validator.validate(bean));
        Throwable cause = e;
        while (cause != null && !(cause instanceof ConstraintValidatorException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause);
        assertInstanceOf(ValidationException.class, cause.getCause());
    }

    @Test
    public void violationCanOnlyBeAddedOnce() {
        BeanValidator validator = BeanValidator.builder().build();
        DoubleAdder bean = new DoubleAdder();

        UnrulyException e = assertThrows(UnrulyException.class, () -> validator.validate(bean));
        Throwable cause = e;
        while (cause != null && !(cause instanceof IllegalStateException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause);
    }

    // --- fixtures: constraints and validators ------------------------------------------------------

    public static final class CountingFactory implements ConstraintValidatorFactory {

        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger released = new AtomicInteger();

        public CountingFactory() {
            super();
        }

        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            created.incrementAndGet();
            try {
                return key.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
            released.incrementAndGet();
        }
    }

    @Constraint(validatedBy = IbanValidator.class)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Iban {
        String message() default "must be an IBAN";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String prefix() default "GB";
    }

    public static class IbanValidator implements ConstraintValidator<Iban, CharSequence> {

        private String prefix;

        public IbanValidator() {
            super();
        }

        @Override
        public void initialize(Iban iban) {
            this.prefix = iban.prefix();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            return value == null || value.toString().startsWith(prefix);
        }
    }

    @Constraint(validatedBy = NullRejecter.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NullRejecting {
        String message() default "must be present";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class NullRejecter implements ConstraintValidator<NullRejecting, Object> {
        public NullRejecter() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return value != null;
        }
    }

    @NotNull
    @Constraint(validatedBy = NeverValid.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidName {
        String message() default "never valid";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class NeverValid implements ConstraintValidator<ValidName, Object> {
        public NeverValid() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return false;
        }
    }

    @Constraint(validatedBy = {NumberSign.class, StringSign.class})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PositiveSign {
        String message() default "{kind}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class NumberSign implements ConstraintValidator<PositiveSign, Number> {
        public NumberSign() {
            super();
        }

        @Override
        public boolean isValid(Number value, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("number").addConstraintViolation();
            return value == null || value.doubleValue() >= 0;
        }
    }

    public static class StringSign implements ConstraintValidator<PositiveSign, String> {
        public StringSign() {
            super();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("text").addConstraintViolation();
            return value == null || !value.startsWith("-");
        }
    }

    @Constraint(validatedBy = {CharsRejecter.class, StringRejecter.class})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RejectAll {
        String message() default "rejected";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class CharsRejecter implements ConstraintValidator<RejectAll, CharSequence> {
        public CharsRejecter() {
            super();
        }

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("chars").addConstraintViolation();
            return false;
        }
    }

    public abstract static class AbstractRejecter<T> implements ConstraintValidator<RejectAll, T> {
        public AbstractRejecter() {
            super();
        }
    }

    public static class StringRejecter extends AbstractRejecter<String> {
        public StringRejecter() {
            super();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("string").addConstraintViolation();
            return false;
        }
    }

    @Constraint(validatedBy = {ObjectA.class, ObjectB.class})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Twice {
        String message() default "twice";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class ObjectA implements ConstraintValidator<Twice, Object> {
        public ObjectA() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return true;
        }
    }

    public static class ObjectB implements ConstraintValidator<Twice, Object> {
        public ObjectB() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return true;
        }
    }

    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Plain {
        String message() default "plain";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Constraint(validatedBy = FailingInitializer.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Misconfigured {
        String message() default "misconfigured";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class FailingInitializer implements ConstraintValidator<Misconfigured, Object> {
        public FailingInitializer() {
            super();
        }

        @Override
        public void initialize(Misconfigured annotation) {
            throw new IllegalArgumentException("bad configuration");
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return true;
        }
    }

    @Constraint(validatedBy = Thrower.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Explosive {
        String message() default "boom";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class Thrower implements ConstraintValidator<Explosive, Object> {
        public Thrower() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            throw new IllegalStateException("boom");
        }
    }

    @Constraint(validatedBy = AddressValidator.class)
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidAddress {
        String message() default "invalid address";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int min() default 3;
    }

    public static class AddressValidator implements ConstraintValidator<ValidAddress, Address> {
        public AddressValidator() {
            super();
        }

        @Override
        public boolean isValid(Address address, ConstraintValidatorContext context) {
            boolean valid = true;

            if (address.street == null || address.city == null) {
                context.disableDefaultConstraintViolation();
                if (address.street == null) {
                    context.buildConstraintViolationWithTemplate("street is required").addPropertyNode("street").addConstraintViolation();
                }
                if (address.city == null) {
                    context.buildConstraintViolationWithTemplate("city needs {min} characters").addPropertyNode("city").addConstraintViolation();
                }
                valid = false;
            }

            if (address.extra) {
                // The default violation stays, at the bean; this one goes to the property.
                context.buildConstraintViolationWithTemplate("no extras").addPropertyNode("extra").addConstraintViolation();
                valid = false;
            }

            return valid;
        }
    }

    @Constraint(validatedBy = PostcodeValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidPostcode {
        String message() default "invalid postcode";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class PostcodeValidator implements ConstraintValidator<ValidPostcode, Postcode> {
        public PostcodeValidator() {
            super();
        }

        @Override
        public boolean isValid(Postcode postcode, ConstraintValidatorContext context) {
            if (postcode == null || postcode.code != null) return true;
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("code is required").addPropertyNode("code").addConstraintViolation();
            context.buildConstraintViolationWithTemplate("the whole thing").addBeanNode().addConstraintViolation();
            return false;
        }
    }

    @Constraint(validatedBy = TagsValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CleanTags {
        String message() default "dirty tags";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class TagsValidator implements ConstraintValidator<CleanTags, List<String>> {
        public TagsValidator() {
            super();
        }

        @Override
        public boolean isValid(List<String> tags, ConstraintValidatorContext context) {
            if (tags == null) return true;
            boolean valid = true;

            for (int i = 0; i < tags.size(); i++) {
                if (tags.get(i).equals("bad")) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate("${validatedValue} is dirty")
                            .addContainerElementNode("<list element>", List.class, 0).inIterable().atIndex(i)
                            .addConstraintViolation();
                    context.buildConstraintViolationWithTemplate("keyed")
                            .addBeanNode().inIterable().atKey(tags.get(i))
                            .addConstraintViolation();
                    context.buildConstraintViolationWithTemplate("somewhere")
                            .addBeanNode().inContainer(List.class, 0).inIterable()
                            .addConstraintViolation();
                    valid = false;
                }
            }

            return valid;
        }
    }

    @Constraint(validatedBy = TemplateValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Templates {
        String message() default "must be templated";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int min() default 3;
    }

    public static class TemplateValidator implements ConstraintValidator<Templates, String> {
        public TemplateValidator() {
            super();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            assertEquals("must be templated", context.getDefaultConstraintMessageTemplate());
            context.buildConstraintViolationWithTemplate("{my.key}").addConstraintViolation();
            context.buildConstraintViolationWithTemplate("${validatedValue} is below {min}").addConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()).addConstraintViolation();
            context.disableDefaultConstraintViolation();
            return false;
        }
    }

    @Constraint(validatedBy = ClockChecker.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Timed {
        String message() default "timed";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class ClockChecker implements ConstraintValidator<Timed, Object> {

        static Clock seen;

        public ClockChecker() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            seen = context.getClockProvider().getClock();
            assertNotNull(context.unwrap(RuleContext.class));
            assertSame(context, context.unwrap(RuliiConstraintValidatorContext.class));
            assertThrows(ValidationException.class, () -> context.unwrap(String.class));
            return true;
        }
    }

    @Constraint(validatedBy = ParameterNoder.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Parametered {
        String message() default "parametered";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class ParameterNoder implements ConstraintValidator<Parametered, Object> {
        public ParameterNoder() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            context.buildConstraintViolationWithTemplate("x").addParameterNode(0).addConstraintViolation();
            return false;
        }
    }

    @Constraint(validatedBy = DoubleAdding.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AddedTwice {
        String message() default "twice";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class DoubleAdding implements ConstraintValidator<AddedTwice, Object> {
        public DoubleAdding() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            ConstraintValidatorContext.ConstraintViolationBuilder builder = context.buildConstraintViolationWithTemplate("x");
            builder.addConstraintViolation();
            builder.addConstraintViolation();
            return false;
        }
    }

    // --- fixtures: beans --------------------------------------------------------------------------

    public static class Account {
        @Iban String iban;
    }

    public static class TwoIbans {
        @Iban String first;
        @Iban String second;
    }

    public static class Prefixed {
        @Iban(prefix = "DE") String code;
    }

    public static class Required {
        @NullRejecting String value;
    }

    public static class Named {
        @ValidName String name;
    }

    public static class Signed {
        @PositiveSign int count;
        @PositiveSign String text;
    }

    public static class Specific {
        @RejectAll String text;
        @RejectAll StringBuilder chars;
    }

    public static class Untyped {
        @PositiveSign Object anything;
    }

    public static class Ambiguous {
        @Twice String value;
    }

    public static class Unmapped {
        @Plain String value;
    }

    public static class BadlyConfigured {
        @Misconfigured String value;
    }

    public static class Exploding {
        @Explosive String value;
    }

    @ValidAddress
    public static class Address {
        String street;
        String city;
        boolean extra;
    }

    public static class Postcode {
        String code;
    }

    public static class Parcel {
        @ValidPostcode Postcode postcode;
        @CleanTags List<String> tags;
    }

    public static class Templated {
        @Templates String value;
    }

    public static class Clocked {
        @Timed String value = "x";
    }

    public static class ParameterNoded {
        @Parametered String value = "x";
    }

    public static class DoubleAdder {
        @AddedTwice String value = "x";
    }
}
