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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.registry.RuleRegistry;
import org.rulii.text.MessageResolver;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidationOptions;
import org.rulii.validation.beans.BeanValidationResult;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.message.BeanValidationMessageFormatter;
import org.rulii.validation.beans.message.BeanValidationMessageResolver;
import org.rulii.validation.beans.message.RuliiMessageInterpolator;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Messages: the default bundle, the user's bundle and locale, literal templates, keys and EL, through
 * the core pipeline with the Bean Validation resolver and formatter, and through the spec interpolator.
 */
public class BeanValidationMessagesTest {

    public BeanValidationMessagesTest() {
        super();
    }

    private static Map<String, String> messages(BeanValidationResult<?> result) {
        Map<String, String> messages = new LinkedHashMap<>();
        for (BeanRuleViolation violation : result.getViolations()) {
            messages.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return messages;
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
    public void defaultBundleCoversEveryStandardConstraint() {
        ResourceBundle bundle = ResourceBundle.getBundle(BeanValidationMessageResolver.DEFAULT_BUNDLE);
        List<Class<? extends Annotation>> standard = List.of(jakarta.validation.constraints.AssertFalse.class,
                jakarta.validation.constraints.AssertTrue.class, jakarta.validation.constraints.DecimalMax.class, DecimalMin.class,
                Digits.class, Email.class, jakarta.validation.constraints.Future.class, jakarta.validation.constraints.FutureOrPresent.class,
                Max.class, Min.class, jakarta.validation.constraints.Negative.class, jakarta.validation.constraints.NegativeOrZero.class,
                NotBlank.class, jakarta.validation.constraints.NotEmpty.class, NotNull.class, Null.class,
                jakarta.validation.constraints.Past.class, jakarta.validation.constraints.PastOrPresent.class, Pattern.class,
                Positive.class, jakarta.validation.constraints.PositiveOrZero.class, Size.class);

        assertEquals(22, standard.size());
        for (Class<? extends Annotation> type : standard) {
            assertTrue(bundle.containsKey(type.getName() + ".message"), type.getSimpleName());
        }
    }

    @Test
    public void standardMessagesReadAsTheReferenceImplementationWrites() {
        BeanValidator validator = BeanValidator.builder().options(withLocale(Locale.ENGLISH)).build();

        Map<String, String> messages = messages(validator.validate(new Form()));

        assertEquals("must not be blank", messages.get("name"));
        assertEquals("size must be between 2 and 5", messages.get("code"));
        assertEquals("must match \"[a-z]+\"", messages.get("slug"));
        assertEquals("must be greater than or equal to 3", messages.get("min"));
        assertEquals("must be less than or equal to 10", messages.get("max"));
        assertEquals("numeric value out of bounds (<3 digits>.<1 digits> expected)", messages.get("amount"));
        assertEquals("must be greater than or equal to 0.5", messages.get("decimal"));
        assertEquals("must be a well-formed email address", messages.get("email"));
        assertEquals("must be greater than 0", messages.get("positive"));
        // The user's ValidationMessages bundle (test resources) overrides a standard key.
        assertEquals("must be absent", messages.get("absent"));
    }

    @Test
    public void userBundleFollowsTheContextLocale() {
        BeanValidator validator = BeanValidator.builder().options(withLocale(Locale.GERMAN)).build();

        Map<String, String> messages = messages(validator.validate(new Form()));

        assertEquals("darf nicht leer sein", messages.get("name"));
        assertEquals("muss zwischen 2 und 5 liegen", messages.get("code"));
        assertEquals("must match \"[a-z]+\"", messages.get("slug"), "no German text: the default bundle");
    }

    @Test
    public void ruliiBundlesAreConsultedAfterTheUserBundle() {
        StandardRuleContextOptions options = new StandardRuleContextOptions() {
            private final MessageResolver resolver = MessageResolver.builder("rulii-messages").build();

            @Override
            public MessageResolver getMessageResolver() {
                return resolver;
            }

            @Override
            public Locale getLocale() {
                return Locale.ENGLISH;
            }
        };
        RuleRegistry registry = RuleRegistry.builder()
                .register(Validators.notNull(Validators.binding("absent")).name("codeRule").errorCode("rule.code").build())
                .build();
        BeanValidator validator = BeanValidator.builder().options(options).registry(registry).build();
        RuleForm form = new RuleForm();
        form.absent = null;

        Map<String, String> messages = messages(validator.validate(form));

        assertEquals("rulii says at most 10", messages.get("max"), "a constraint key in rulii syntax, from the rulii bundle");
        assertEquals("must not be blank", messages.get("name"), "everything else as before");
        assertTrue(messages.get("").startsWith("rulii rule message for"), "a @Validate rule's code resolves through the same sources, untranslated");
        assertSame(options, validator.getOptions().getDelegate());
    }

    @Test
    public void literalTemplates() {
        BeanValidator validator = BeanValidator.builder().options(withLocale(Locale.ENGLISH)).build();

        Map<String, String> messages = messages(validator.validate(new Literals()));

        assertEquals("between 2 and 5, got x", messages.get("a"), "attributes and the validated value");
        assertEquals("custom text", messages.get("b"), "a custom key alone");
        assertEquals("Code custom text!", messages.get("c"), "a custom key inside text");
        assertEquals("must be between 2 and 5 characters", messages.get("d"), "a bundle text with attributes");
        assertEquals("nested custom text", messages.get("e"), "keys expand recursively");
        assertTrue(messages.get("f").startsWith("loop "), "a cyclic key terminates: " + messages.get("f"));
        assertEquals("bad short", messages.get("g"), "EL is evaluated through the el scripting language");
        assertEquals("{no.such.key}", messages.get("h"), "an unknown key stays as Bean Validation leaves it");
        assertEquals("it's 2", messages.get("i"), "quotes in bundle text survive formatting");
        assertEquals("plain text", messages.get("j"));
        assertEquals("{my.key}", validator.validate(new Literals()).getViolations().get(1).getMessageTemplate(), "the template is kept raw");
    }

    @Test
    public void optionsBringTheMessagesToAnyContext() {
        BeanValidator validator = BeanValidator.builder().build();
        assertInstanceOf(BeanValidationMessageResolver.class, validator.getOptions().getMessageResolver());
        assertInstanceOf(BeanValidationMessageFormatter.class, validator.getOptions().getMessageFormatter());
        assertSame(validator.getOptions(), BeanValidationOptions.wrap(validator.getOptions(), ConstraintMapperRegistry.builder().build()), "wrapping is idempotent");

        // A user's own context: plain core messages without the options, Bean Validation messages with them.
        Form form = new Form();
        RuleContext plain = RuleContext.builder().with(Bindings.builder().scoped()).build();
        BeanRuleViolations plainReport = (BeanRuleViolations) validator.validateIn(plain, form);
        assertEquals("Value x must be null.", message(plainReport, "absent"), "core's own default message in a plain context");

        BeanValidationOptions options = BeanValidationOptions.wrap(withLocale(Locale.ENGLISH), ConstraintMapperRegistry.builder().build());
        RuleContext ours = RuleContext.builder().with(options).bindings(Bindings.builder().scoped()).build();
        BeanRuleViolations report = (BeanRuleViolations) validator.validateIn(ours, form);
        assertEquals("must be absent", message(report, "absent"));
        assertEquals("size must be between 2 and 5", message(report, "code"));
    }

    private static String message(BeanRuleViolations report, String path) {
        for (BeanRuleViolation violation : report.getBeanViolations()) {
            if (violation.getPropertyPath().toString().equals(path)) return violation.getMessage();
        }
        return null;
    }

    @Test
    public void interpolatorRendersAsTheValidatorDoes() {
        BeanValidator validator = BeanValidator.builder().options(withLocale(Locale.ENGLISH)).build();
        RuliiMessageInterpolator interpolator = new RuliiMessageInterpolator();

        for (BeanRuleViolation violation : validator.validate(new Form()).getViolations()) {
            assertEquals(violation.getMessage(), interpolator.interpolate(violation.getMessageTemplate(), context(violation), Locale.ENGLISH),
                    violation.getPropertyPath().toString());
        }
        for (BeanRuleViolation violation : validator.validate(new Literals()).getViolations()) {
            assertEquals(violation.getMessage(), interpolator.interpolate(violation.getMessageTemplate(), context(violation), Locale.ENGLISH),
                    violation.getPropertyPath().toString());
        }

        assertEquals("{x}", interpolator.interpolate("{x}", null));
        assertEquals("{x}", interpolator.interpolate("{x}", null, Locale.GERMAN));
        assertEquals("plain", interpolator.interpolate("plain", null));
        assertNotNull(interpolator.getMessageResolver());
        assertNotNull(interpolator.getMessageFormatter());
        assertThrows(IllegalArgumentException.class, () -> interpolator.interpolate(null, null));

        // The bootstrap's default interpolator is this one.
        assertInstanceOf(RuliiMessageInterpolator.class, Validation.byDefaultProvider().configure().getDefaultMessageInterpolator());
        assertInstanceOf(RuliiMessageInterpolator.class, Validation.buildDefaultValidatorFactory().getMessageInterpolator());
    }

    private static MessageInterpolator.Context context(ConstraintViolation<?> violation) {
        return new MessageInterpolator.Context() {
            @Override
            public ConstraintDescriptor<?> getConstraintDescriptor() {
                return violation.getConstraintDescriptor();
            }

            @Override
            public Object getValidatedValue() {
                return violation.getInvalidValue();
            }

            @Override
            public <T> T unwrap(Class<T> type) {
                return type.cast(this);
            }
        };
    }

    @Test
    public void resolverAndFormatterUnits() {
        BeanValidationMessageResolver resolver = new BeanValidationMessageResolver((org.rulii.text.MessageResolver) null);

        assertEquals("default", resolver.resolve(Locale.ENGLISH, null, "default"));
        assertEquals("default", resolver.resolve(Locale.ENGLISH, "no.such", "default"));
        assertEquals("must match \"${regexp}\"", resolver.resolve(Locale.ENGLISH, Pattern.class.getName() + ".message", null), "translated into a rulii template");
        assertEquals("must match \"${regexp}\"", resolver.resolve(null, Pattern.class.getName() + ".message", null));
        assertEquals("size must be between {min} and {max}", resolver.lookup(Locale.ENGLISH, Size.class.getName() + ".message"), "as found");
        assertEquals("custom text", resolver.resolve(Locale.ENGLISH, "my.key", null), "a non-constraint key is returned as found");
        assertNull(resolver.getDelegate());

        BeanValidationMessageFormatter formatter = new BeanValidationMessageFormatter(org.rulii.text.MessageFormatter.builder().build(), resolver);
        org.rulii.text.ParameterInfo min = new org.rulii.text.ParameterInfo(0, "min", 2);
        assertEquals("min 2, ${a > b}, {nope}, custom text", formatter.format(Locale.ENGLISH, "min ${min}, ${a > b}, ${nope}, ${#my.key}", min));
        assertEquals("plain", formatter.format(Locale.ENGLISH, "plain", min));
        assertEquals("x 2", formatter.format(Locale.ENGLISH, "x {0}", 2));
        assertNotNull(formatter.getDelegate());
        assertThrows(IllegalArgumentException.class, () -> new BeanValidationMessageFormatter(null, resolver));
    }

    // ---- fixtures ----

    public static class Form {
        @NotBlank
        String name = "";

        @Size(min = 2, max = 5)
        String code = "x";

        @Pattern(regexp = "[a-z]+")
        String slug = "A1";

        @Min(3)
        int min = 1;

        @Max(10)
        int max = 11;

        @Digits(integer = 3, fraction = 1)
        BigDecimal amount = new BigDecimal("1234.56");

        @Null
        String absent = "x";

        @DecimalMin("0.5")
        BigDecimal decimal = new BigDecimal("0.1");

        @Email
        String email = "nope";

        @Positive
        int positive = -1;
    }

    @Validate("codeRule")
    public static class RuleForm extends Form {
    }

    public static class Literals {
        @Size(min = 2, max = 5, message = "between {min} and {max}, got ${validatedValue}")
        String a = "x";

        @Size(min = 2, max = 5, message = "{my.key}")
        String b = "x";

        @Size(min = 2, max = 5, message = "Code {my.key}!")
        String c = "x";

        @Size(min = 2, max = 5, message = "{my.sized}")
        String d = "x";

        @Size(min = 2, max = 5, message = "{my.nested}")
        String e = "x";

        @Size(min = 2, max = 5, message = "{my.loop}")
        String f = "x";

        @Size(min = 2, max = 5, message = "bad ${validatedValue.length() > 3 ? 'long' : 'short'}")
        String g = "x";

        @Size(min = 2, max = 5, message = "{no.such.key}")
        String h = "x";

        @Size(min = 2, max = 5, message = "{my.quoted}")
        String i = "x";

        @NotNull(message = "plain text")
        String j;
    }
}
