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
package org.rulii.test.validation.beans.message;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Payload;
import jakarta.validation.ValidationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.registry.RuleRegistry;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.bootstrap.RuliiConfiguration;
import org.rulii.validation.beans.bootstrap.RuliiValidationProvider;
import org.rulii.validation.beans.bootstrap.RuliiValidatorFactory;
import org.rulii.validation.beans.message.MessageInterpolationException;
import org.rulii.validation.beans.message.RuliiMessageInterpolator;
import org.rulii.validation.beans.message.RuliiMessageInterpolatorContext;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A configured {@code MessageInterpolator} renders the messages of constraint violations: when it is
 * consulted, what it receives, the locale wrapper case, the bootstrap wiring, executables and values,
 * and failures.
 */
public class MessageInterpolationTest {

    private static final String NOT_BLANK_KEY = "{jakarta.validation.constraints.NotBlank.message}";
    private static final RuleRegistry REGISTRY = RuleRegistry.builder()
            .register(Validators.notNull(Validators.binding("note")).name("noteRule").build())
            .build();

    public MessageInterpolationTest() {
        super();
    }

    private static Map<String, String> messages(Iterable<? extends ConstraintViolation<?>> violations) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : violations) {
            result.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return result;
    }

    private static Account invalid() {
        Account account = new Account();
        account.owner = "";
        account.code = "x";
        account.tag = "t";
        return account;
    }

    private static StandardRuleContextOptions withLocale(Locale locale) {
        return new StandardRuleContextOptions() {
            @Override
            public Locale getLocale() {
                return locale;
            }
        };
    }

    @Test
    public void aConfiguredInterpolatorRendersEveryConstraintViolation() {
        Recording recording = new Recording();
        BeanValidator validator = BeanValidator.builder().registry(REGISTRY).messageInterpolator(recording).build();
        List<BeanRuleViolation> violations = validator.validate(invalid()).getViolations();

        Map<String, String> messages = messages(violations);
        assertSame(recording, validator.getMessageInterpolator());
        assertEquals("I:" + NOT_BLANK_KEY, messages.get("owner"), "the template as declared: the key");
        assertEquals("I:needs {min}", messages.get("code"), "the template as declared: the literal");
        assertEquals("I:tag ${validatedValue} bad", messages.get("tag"), "the run-time template as the validator built it");
        assertFalse(messages.get("").startsWith("I:"), "a @Validate rule is not constraint-backed: rulii's text stands");
        assertEquals(4, violations.size());

        for (BeanRuleViolation violation : violations) {
            assertEquals(violation.getErrorMessage(), violation.getMessage(), "one text");
        }

        assertEquals(3, recording.contexts.size(), "consulted once per constraint violation");
        RuliiMessageInterpolatorContext owner = recording.context("owner");
        assertEquals(NotBlank.class, owner.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals("", owner.getValidatedValue());
        assertEquals(Account.class, owner.getRootBeanClass());
        assertTrue(owner.isExpressionLanguageEnabled());
        assertEquals(Set.of("owner"), owner.getParameters().keySet(), "the rule's parameters: the value under the value name");

        RuliiMessageInterpolatorContext code = recording.context("code");
        assertEquals(3, code.getConstraintDescriptor().getAttributes().get("min"));
        assertEquals(3, code.getParameters().get("min"), "the rule's parameters");
        assertEquals("x", code.getParameters().get("code"));

        RuliiMessageInterpolatorContext tag = recording.context("tag");
        assertFalse(tag.isExpressionLanguageEnabled(), "the validator did not enable expressions for its template");
        assertSame(tag, tag.unwrap(MessageInterpolator.Context.class));
        assertThrows(ValidationException.class, () -> tag.unwrap(String.class));
        assertTrue(tag.toString().contains("Tagged"));
    }

    @Test
    public void theTemplateOfARunTimeViolationIsItsOwn() {
        BeanValidator validator = BeanValidator.builder().registry(REGISTRY).build();
        Account account = new Account();
        account.owner = "o";
        account.code = "abc";
        account.tag = "t";

        List<BeanRuleViolation> violations = new ArrayList<>(validator.validate(account).getViolations());
        BeanRuleViolation tag = violations.stream().filter(v -> v.getPropertyPath().toString().equals("tag")).findFirst().orElseThrow();
        assertEquals(2, violations.size(), "the tag and the @Validate rule");
        assertEquals("tag ${validatedValue} bad", tag.getMessageTemplate(), "not the annotation's declared message");
        assertEquals("tag ${validatedValue} bad", tag.getMessage(), "expressions off: as written");
        assertNull(violations.stream().filter(v -> v.getPropertyPath().toString().isEmpty()).findFirst().orElseThrow().getMessageTemplate(),
                "a @Validate rule declares no template");
    }

    @Test
    public void withoutAnInterpolatorAndWithOurOwnTheRulesRender() {
        BeanValidator plain = BeanValidator.builder().registry(REGISTRY).build();
        RuliiMessageInterpolator ours = new RuliiMessageInterpolator();
        BeanValidator withOurs = BeanValidator.builder().registry(REGISTRY).messageInterpolator(ours).build();

        assertNull(plain.getMessageInterpolator());
        assertSame(ours, withOurs.getMessageInterpolator());
        Map<String, String> expected = messages(plain.validate(invalid()));
        assertEquals("must not be blank", expected.get("owner"));
        assertEquals("needs 3", expected.get("code"));
        assertEquals(expected, messages(withOurs.validate(invalid())), "our interpolator renders what the rules render; it is not consulted");
    }

    @Test
    public void aLocaleWrapperOverTheDefaultRendersAtItsLocale() {
        // The shape frameworks use: the default interpolator wrapped, the two-argument form choosing the locale.
        RuliiConfiguration configuration = Validation.byProvider(RuliiValidationProvider.class).configure();
        MessageInterpolator german = new LocaleWrapper(configuration.getDefaultMessageInterpolator(), Locale.GERMAN);
        RuliiValidatorFactory factory = (RuliiValidatorFactory) configuration.registry(REGISTRY).messageInterpolator(german).buildValidatorFactory();

        BeanValidator validator = factory.getValidator();
        assertSame(german, validator.getMessageInterpolator());
        Map<String, String> messages = messages(validator.validate(invalid()));
        assertEquals("darf nicht leer sein", messages.get("owner"));
        assertEquals("needs 3", messages.get("code"), "a literal renders the same at any locale");
        assertEquals("tag ${validatedValue} bad", messages.get("tag"));
        assertFalse(messages.get("").startsWith("I:"));
    }

    @Test
    public void ourInterpolatorUsesTheOptionsLocaleWhenNoneIsGiven() {
        RuliiMessageInterpolator english = new RuliiMessageInterpolator(withLocale(Locale.ENGLISH));
        RuliiMessageInterpolator german = new RuliiMessageInterpolator(withLocale(Locale.GERMAN));
        Recording recording = new Recording();
        BeanValidator.builder().registry(REGISTRY).messageInterpolator(recording).build().validate(invalid());
        MessageInterpolator.Context context = recording.context("owner");

        assertEquals(Locale.GERMAN, german.getLocale());
        assertEquals("must not be blank", english.interpolate(NOT_BLANK_KEY, context));
        assertEquals("darf nicht leer sein", german.interpolate(NOT_BLANK_KEY, context));
        assertEquals("must not be blank", german.interpolate(NOT_BLANK_KEY, context, Locale.ENGLISH), "an explicit locale wins");
    }

    @Test
    public void aContextOverrideReachesANewValidator() {
        RuliiValidatorFactory factory = (RuliiValidatorFactory) Validation.byProvider(RuliiValidationProvider.class).configure().registry(REGISTRY).buildValidatorFactory();
        Recording recording = new Recording();

        BeanValidator validator = factory.usingContext().messageInterpolator(recording).getValidator();

        assertNotSame(factory.getValidator(), validator);
        assertSame(recording, validator.getMessageInterpolator());
        assertEquals("I:" + NOT_BLANK_KEY, messages(validator.validate(invalid())).get("owner"));
        assertEquals("must not be blank", messages(factory.getValidator().validate(invalid())).get("owner"), "the factory's validator is untouched");
    }

    @Test
    public void executablesAndValuesRenderThroughTheInterpolator() throws Exception {
        Recording recording = new Recording();
        BeanValidator validator = BeanValidator.builder().registry(REGISTRY).messageInterpolator(recording).build();
        Method rename = Account.class.getMethod("rename", String.class);

        Map<String, String> parameters = messages(validator.forExecutables().validateParameters(new Account(), rename, new Object[] {""}));
        assertEquals(Map.of("rename.name", "I:" + NOT_BLANK_KEY), parameters);
        assertEquals(Account.class, recording.context("rename.name").getRootBeanClass());
        assertEquals("", recording.context("rename.name").getValidatedValue());

        Map<String, String> values = messages(validator.validateValue(Account.class, "code", "x"));
        assertEquals(Map.of("code", "I:needs {min}"), values);
        assertEquals(Account.class, recording.context("code").getRootBeanClass());
    }

    @Test
    public void aFailingInterpolatorFailsTheCall() {
        BeanValidator failing = BeanValidator.builder().registry(REGISTRY).messageInterpolator(new Throwing(new IllegalStateException("boom"))).build();
        BeanValidator validation = BeanValidator.builder().registry(REGISTRY).messageInterpolator(new Throwing(new ValidationException("mine"))).build();

        // The interpolator runs while the rule reports; the ValidationException surfaces as itself through the specification API.
        MessageInterpolationException interpolation = assertThrows(MessageInterpolationException.class, () -> failing.validate(invalid()));
        assertEquals(NOT_BLANK_KEY, interpolation.getMessageTemplate());
        assertInstanceOf(IllegalStateException.class, interpolation.getCause());
        assertTrue(interpolation.getMessage().contains(NOT_BLANK_KEY));

        ValidationException own = assertThrows(ValidationException.class, () -> validation.validate(invalid()));
        assertEquals("mine", own.getMessage(), "a ValidationException is thrown as itself, never wrapped again");
        assertThrows(IllegalArgumentException.class, () -> BeanValidator.builder().messageInterpolator(null));
    }

    // --- fixtures -------------------------------------------------------------------------------

    public static final class Recording implements MessageInterpolator {

        private final Map<String, RuliiMessageInterpolatorContext> contexts = new LinkedHashMap<>();

        public Recording() {
            super();
        }

        RuliiMessageInterpolatorContext context(String path) {
            RuliiMessageInterpolatorContext result = contexts.get(path);
            assertNotNull(result, "no context recorded for [" + path + "]: " + contexts.keySet());
            return result;
        }

        @Override
        public String interpolate(String messageTemplate, Context context) {
            RuliiMessageInterpolatorContext ours = context.unwrap(RuliiMessageInterpolatorContext.class);
            contexts.put(ours.getPropertyPath().toString(), ours);
            return "I:" + messageTemplate;
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            fail("the validator calls the two-argument form, as the reference implementation does");
            return null;
        }
    }

    public static final class LocaleWrapper implements MessageInterpolator {

        private final MessageInterpolator delegate;
        private final Locale locale;

        public LocaleWrapper(MessageInterpolator delegate, Locale locale) {
            super();
            this.delegate = delegate;
            this.locale = locale;
        }

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return delegate.interpolate(messageTemplate, context, locale);
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return delegate.interpolate(messageTemplate, context, locale);
        }
    }

    public static final class Throwing implements MessageInterpolator {

        private final RuntimeException failure;

        public Throwing(RuntimeException failure) {
            super();
            this.failure = failure;
        }

        @Override
        public String interpolate(String messageTemplate, Context context) {
            throw failure;
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            throw failure;
        }
    }

    @Constraint(validatedBy = TagValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tagged {
        String message() default "tagged";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class TagValidator implements ConstraintValidator<Tagged, String> {
        public TagValidator() {
            super();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("tag ${validatedValue} bad").addConstraintViolation();
            return false;
        }
    }

    @Validate("noteRule")
    public static class Account {
        @NotBlank
        String owner;

        @Size(min = 3, message = "needs {min}")
        String code;

        @Tagged
        String tag;

        String note;

        public void rename(@NotBlank String name) {
            this.owner = name;
        }
    }
}
