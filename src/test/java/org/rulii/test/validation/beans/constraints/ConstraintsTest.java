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
package org.rulii.test.validation.beans.constraints;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.junit.jupiter.api.Test;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanValidationResult;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.constraints.Alpha;
import org.rulii.validation.beans.constraints.AlphaNumeric;
import org.rulii.validation.beans.constraints.Ascii;
import org.rulii.validation.beans.constraints.CodePointLength;
import org.rulii.validation.beans.constraints.CreditCardNumber;
import org.rulii.validation.beans.constraints.Decimal;
import org.rulii.validation.beans.constraints.EAN;
import org.rulii.validation.beans.constraints.EndsWith;
import org.rulii.validation.beans.constraints.ISBN;
import org.rulii.validation.beans.constraints.In;
import org.rulii.validation.beans.constraints.Length;
import org.rulii.validation.beans.constraints.LowerCase;
import org.rulii.validation.beans.constraints.LuhnCheck;
import org.rulii.validation.beans.constraints.Mod10Check;
import org.rulii.validation.beans.constraints.Mod11Check;
import org.rulii.validation.beans.constraints.Normalized;
import org.rulii.validation.beans.constraints.Numeric;
import org.rulii.validation.beans.constraints.Range;
import org.rulii.validation.beans.constraints.StartsWith;
import org.rulii.validation.beans.constraints.URL;
import org.rulii.validation.beans.constraints.UUID;
import org.rulii.validation.beans.constraints.UniqueElements;
import org.rulii.validation.beans.constraints.UpperCase;
import org.rulii.validation.beans.constraints.time.DurationMax;
import org.rulii.validation.beans.constraints.time.DurationMin;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.LibraryConstraintMappers;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The library's own constraints end to end through the validator: every annotation materializes,
 * fails on the right values with the reference wording, and passes on valid ones.
 */
public class ConstraintsTest {

    private static final BeanValidator VALIDATOR = BeanValidator.builder().options(new StandardRuleContextOptions() {
        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }
    }).build();

    public ConstraintsTest() {
        super();
    }

    private static Map<String, String> messages(BeanValidationResult<?> result) {
        Map<String, String> messages = new LinkedHashMap<>();
        for (BeanRuleViolation violation : result.getViolations()) {
            assertNull(messages.put(violation.getPropertyPath().toString(), violation.getMessage()), "one violation per property");
        }
        return messages;
    }

    @Test
    public void everyLibraryConstraintHasAMapperOrIsAComposition() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().build();
        List<Class<? extends Annotation>> mapped = List.of(URL.class, UUID.class, UniqueElements.class, Normalized.class,
                CodePointLength.class, LuhnCheck.class, Mod10Check.class, Mod11Check.class, EAN.class, ISBN.class, DurationMin.class,
                DurationMax.class, Alpha.class, AlphaNumeric.class, Numeric.class, Decimal.class, Ascii.class, LowerCase.class,
                UpperCase.class, StartsWith.class, EndsWith.class, In.class);

        assertEquals(22, LibraryConstraintMappers.all().size());
        for (Class<? extends Annotation> type : mapped) {
            assertNotNull(registry.find(type), type.getSimpleName());
        }
        assertNull(registry.find(Length.class), "a composition of @Size");
        assertNull(registry.find(Range.class), "a composition of @Min and @Max");
        assertNull(registry.find(CreditCardNumber.class), "a composition of @LuhnCheck");
    }

    @Test
    public void invalidValuesReportWithTheReferenceWording() {
        Map<String, String> messages = messages(VALIDATOR.validate(new Form()));

        assertEquals("length must be between 2 and 5", messages.get("length"));
        assertEquals("reference range override 1-10", messages.get("range"), "an override written under the reference implementation's key applies");
        assertEquals("invalid credit card number", messages.get("card"));
        assertEquals("must be a valid URL", messages.get("url"));
        assertEquals("must be a valid UUID", messages.get("uuid"));
        assertEquals("must only contain unique elements", messages.get("unique"));
        assertEquals("must be normalized", messages.get("normalized"));
        assertEquals("length must be between 2 and 3", messages.get("codePoints"));
        assertEquals("the check digit for 79927398710 is invalid, Luhn Modulo 10 checksum failed", messages.get("luhn"));
        assertEquals("the check digit for 4006381333932 is invalid, Modulo 10 checksum failed", messages.get("mod10"));
        assertEquals("the check digit for 0306406153 is invalid, Modulo 11 checksum failed", messages.get("mod11"));
        assertEquals("invalid EAN13 barcode", messages.get("ean"));
        assertEquals("invalid ISBN", messages.get("isbn"));
        assertEquals("must not be shorter than PT5M", messages.get("atLeast"));
        assertEquals("must be shorter than PT5M", messages.get("under"));
        assertEquals("must contain only letters", messages.get("alpha"));
        assertEquals("must contain only letters and digits", messages.get("alphaNumeric"));
        assertEquals("must contain only digits", messages.get("numeric"));
        assertEquals("must be a decimal number", messages.get("decimal"));
        assertEquals("must contain only ASCII characters", messages.get("ascii"));
        assertEquals("must be in lower case", messages.get("lower"));
        assertEquals("must be in upper case", messages.get("upper"));
        assertEquals("must start with one of [http, https]", messages.get("startsWith"));
        assertEquals("must end with one of [.com, .org]", messages.get("endsWith"));
        assertEquals("must be one of [a, b]", messages.get("in"));
        assertEquals("must be greater than 0.5", messages.get("exclusiveMin"), "the exclusive wording of the conditional");
        assertEquals("must be less than or equal to 10", messages.get("inclusiveMax"), "the inclusive wording of the conditional");
        assertEquals(27, messages.size());
    }

    @Test
    public void validValuesPass() {
        Form form = new Form();
        form.length = "abc";
        form.range = 5;
        form.card = "4111111111111111";
        form.url = "https://www.google.com";
        form.uuid = "123e4567-e89b-12d3-a456-426614174000";
        form.unique = List.of(1, 2, 3);
        form.normalized = "café";
        form.codePoints = "😀😀";
        form.luhn = "79927398713";
        form.mod10 = "4006381333931";
        form.mod11 = "0306406152";
        form.ean = "4006381333931";
        form.isbn = "978-0-306-40615-7";
        form.atLeast = Duration.ofMinutes(5);
        form.under = Duration.ofMinutes(4);
        form.alpha = "abc";
        form.alphaNumeric = "abc123";
        form.numeric = "123";
        form.decimal = "1.25";
        form.ascii = "plain";
        form.lower = "lower";
        form.upper = "UPPER";
        form.startsWith = "https://x";
        form.endsWith = "x.org";
        form.in = "b";
        form.exclusiveMin = new BigDecimal("0.6");
        form.inclusiveMax = new BigDecimal("10");

        assertTrue(VALIDATOR.validate(form).isEmpty(), VALIDATOR.validate(form).toString());

        // Null passes everywhere: none of these constraints implies @NotNull.
        Form nulls = new Form();
        nulls.length = null;
        nulls.range = null;
        nulls.card = null;
        nulls.url = null;
        nulls.uuid = null;
        nulls.unique = null;
        nulls.normalized = null;
        nulls.codePoints = null;
        nulls.luhn = null;
        nulls.mod10 = null;
        nulls.mod11 = null;
        nulls.ean = null;
        nulls.isbn = null;
        nulls.atLeast = null;
        nulls.under = null;
        nulls.alpha = null;
        nulls.alphaNumeric = null;
        nulls.numeric = null;
        nulls.decimal = null;
        nulls.ascii = null;
        nulls.lower = null;
        nulls.upper = null;
        nulls.startsWith = null;
        nulls.endsWith = null;
        nulls.in = null;
        nulls.exclusiveMin = null;
        nulls.inclusiveMax = null;
        assertTrue(VALIDATOR.validate(nulls).isEmpty(), VALIDATOR.validate(nulls).toString());
    }

    @Test
    public void compositionsReportOnceWithTheHostsAttributes() {
        Form form = new Form();
        form.length = "a";

        BeanRuleViolation length = VALIDATOR.validate(form).getViolations().stream()
                .filter(v -> v.getPropertyPath().toString().equals("length")).findFirst().orElseThrow();
        assertEquals(Length.class, length.getConstraint().getAnnotationType(), "the host, not the composing @Size");
        assertEquals("org.rulii.validation.beans.constraints.Length.message", length.getErrorCode());
        assertEquals("{org.rulii.validation.beans.constraints.Length.message}", length.getMessageTemplate());
        assertEquals("a", length.getInvalidValue());
        assertEquals("2", length.getParameters().get("min"));
        assertEquals("5", length.getParameters().get("max"));
        assertEquals(2, length.getConstraintDescriptor().getAttributes().get("min"));
        assertEquals(1, length.getConstraintDescriptor().getComposingConstraints().size());
        assertTrue(length.getConstraintDescriptor().isReportAsSingleViolation());

        // @CreditCardNumber honours its override: spaces fail unless ignoreNonDigitCharacters is set.
        Spaced spaced = new Spaced();
        assertEquals(List.of("strict"), VALIDATOR.validate(spaced).getViolations().stream().map(v -> v.getPropertyPath().toString()).toList());
    }

    @Test
    public void attributesReachTheRules() {
        Options options = new Options();

        Map<String, String> messages = messages(VALIDATOR.validate(options));

        assertEquals(List.of("upperUuid", "nfdNormalized", "ean8", "isbn10", "lettersWithSpace", "leftToRight", "secureUrl"),
                List.copyOf(messages.keySet()));

        options.upperUuid = "123E4567-E89B-42D3-A456-426614174000";
        options.nfdNormalized = "café";
        options.ean8 = "73513537";
        options.isbn10 = "0-8044-2957-X";
        options.lettersWithSpace = "ab cd";
        options.leftToRight = "123457";
        options.secureUrl = "https://www.google.com:8443/x";
        options.mixedCase = "AbC";
        assertTrue(VALIDATOR.validate(options).isEmpty(), VALIDATOR.validate(options).toString());
    }

    @Test
    public void descriptorsDescribeTheLibraryConstraints() {
        assertNotNull(VALIDATOR.getConstraintsForClass(Form.class).getConstraintsForProperty("length"));
        assertEquals(Length.class, VALIDATOR.getConstraintsForClass(Form.class).getConstraintsForProperty("length")
                .getConstraintDescriptors().iterator().next().getAnnotation().annotationType());
        assertEquals(27, VALIDATOR.getConstraintsForClass(Form.class).getConstrainedProperties().size());
    }

    // ---- fixtures ----

    public static class Form {
        @Length(min = 2, max = 5)
        String length = "a";

        @Range(min = 1, max = 10)
        Integer range = 11;

        @CreditCardNumber
        String card = "4111111111111112";

        @URL(protocol = "https")
        String url = "http://www.google.com";

        @UUID
        String uuid = "nope";

        @UniqueElements
        List<Integer> unique = List.of(1, 1);

        @Normalized
        String normalized = "café";

        @CodePointLength(min = 2, max = 3)
        String codePoints = "a";

        @LuhnCheck
        String luhn = "79927398710";

        @Mod10Check
        String mod10 = "4006381333932";

        @Mod11Check
        String mod11 = "0306406153";

        @EAN
        String ean = "4006381333932";

        @ISBN
        String isbn = "9780306406158";

        @DurationMin(minutes = 5)
        Duration atLeast = Duration.ofMinutes(4);

        @DurationMax(minutes = 5, inclusive = false)
        Duration under = Duration.ofMinutes(5);

        @Alpha
        String alpha = "ab1";

        @AlphaNumeric
        String alphaNumeric = "a b";

        @Numeric
        String numeric = "1a";

        @Decimal
        String decimal = "1.2.3";

        @Ascii
        String ascii = "café";

        @LowerCase
        String lower = "Ab";

        @UpperCase
        String upper = "aB";

        @StartsWith({"http", "https"})
        String startsWith = "ftp://x";

        @EndsWith({".com", ".org"})
        String endsWith = "x.net";

        @In({"a", "b"})
        String in = "c";

        @DecimalMin(value = "0.5", inclusive = false)
        BigDecimal exclusiveMin = new BigDecimal("0.5");

        @DecimalMax("10")
        BigDecimal inclusiveMax = new BigDecimal("11");
    }

    public static class Spaced {
        @CreditCardNumber(ignoreNonDigitCharacters = true)
        String lenient = "4111 1111 1111 1111";

        @CreditCardNumber
        String strict = "4111 1111 1111 1111";
    }

    public static class Options {
        @UUID(letterCase = UUID.LetterCase.UPPER_CASE, version = 4, allowNil = false)
        String upperUuid = "123e4567-e89b-12d3-a456-426614174000";

        @Normalized(form = Normalizer.Form.NFD)
        String nfdNormalized = "café";

        @EAN(type = EAN.Type.EAN8)
        String ean8 = "4006381333931";

        @ISBN(type = ISBN.Type.ISBN_10)
        String isbn10 = "9780306406157";

        @Alpha(allowSpace = true)
        String lettersWithSpace = "ab 1";

        @Mod11Check(processingDirection = Mod11Check.ProcessingDirection.LEFT_TO_RIGHT)
        String leftToRight = "123455";

        @URL(protocol = "https", host = "www.google.com", port = 8443, regexp = ".*google.*")
        String secureUrl = "https://www.google.com/x";

        @CodePointLength(min = 1, max = 3, normalizationStrategy = CodePointLength.NormalizationStrategy.NFC)
        String mixedCase = "é";
    }
}
