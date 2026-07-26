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

import org.junit.jupiter.api.Test;
import org.rulii.text.MessageFormatter;
import org.rulii.text.MessageResolver;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.beans.message.CompositeMessageResolver;
import org.rulii.validation.beans.message.TranslatingMessageResolver;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the S8 resolver wiring: the translating decorator (bundle text translated at
 * render time, rulii-native default messages passed through untouched), the resolver
 * chain (first hit wins), and the full R12.3 stack rendering a violation through core's
 * real RuleViolationBuilder pipeline.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class MessageResolverTest {

    public MessageResolverTest() {
        super();
    }

    private static final String SIZE_CODE = "jakarta.validation.constraints.Size.message";

    private final MessageFormatter formatter = MessageFormatter.builder().build();
    private final MessageResolver bundleResolver = MessageResolver.builder("ValidationMessages").build();

    // -----------------------------------------------------------------------
    // TranslatingMessageResolver
    // -----------------------------------------------------------------------

    @Test
    public void bundleTextTranslatedAtResolveTime() {
        MessageResolver resolver = new TranslatingMessageResolver(bundleResolver);

        assertEquals("size must be between ${min} and ${max}",
                resolver.resolve(Locale.US, SIZE_CODE, "unused default"));
    }

    @Test
    public void missReturnsDefaultMessageUntranslated() {
        // The pass-through default is a core rule's rulii-native DEFAULT_MESSAGE —
        // positional {0} syntax that translation would quote into literal text.
        MessageResolver resolver = new TranslatingMessageResolver(bundleResolver);

        assertEquals("Value {0} must be between {1} and {2}.",
                resolver.resolve(Locale.US, "no.such.code", "Value {0} must be between {1} and {2}."));
    }

    @Test
    public void missWithoutDefaultReturnsNull() {
        MessageResolver resolver = new TranslatingMessageResolver(bundleResolver);

        assertNull(resolver.resolve(Locale.US, "no.such.code", null));
    }

    // -----------------------------------------------------------------------
    // CompositeMessageResolver
    // -----------------------------------------------------------------------

    @Test
    public void firstResolverWins() {
        MessageResolver first = (locale, code, defaultMessage) -> "first";
        MessageResolver second = (locale, code, defaultMessage) -> "second";

        assertEquals("first", new CompositeMessageResolver(first, second).resolve(Locale.US, "any.code", null));
    }

    @Test
    public void chainFallsThroughMisses() {
        MessageResolver miss = (locale, code, defaultMessage) -> null;
        MessageResolver resolver = new CompositeMessageResolver(miss, bundleResolver);

        assertEquals("must not be null",
                resolver.resolve(Locale.US, "jakarta.validation.constraints.NotNull.message", "unused"));
    }

    @Test
    public void allMissesResolveToDefaultMessage() {
        MessageResolver miss = (locale, code, defaultMessage) -> null;
        MessageResolver resolver = new CompositeMessageResolver(miss, miss);

        assertEquals("the default", resolver.resolve(Locale.US, "no.such.code", "the default"));
    }

    // -----------------------------------------------------------------------
    // Full S8 stack through core's violation pipeline
    // -----------------------------------------------------------------------

    private MessageResolver s8Stack(MessageResolver userResolver) {
        // R12.3 order: user resolver -> ValidationMessages -> (rulii-native default below).
        return new TranslatingMessageResolver(new CompositeMessageResolver(userResolver, bundleResolver));
    }

    @Test
    public void violationRendersBundleMessageWithParams() {
        RuleViolation violation = RuleViolation.builder().with("name_Size")
                .errorCode(SIZE_CODE)
                .defaultMessage("Value {0} must be between {1} and {2}.")
                .param("min", 2)
                .param("max", 5)
                .build(s8Stack((locale, code, defaultMessage) -> null), formatter, Locale.US);

        assertEquals("size must be between 2 and 5", violation.getErrorMessage());
    }

    @Test
    public void violationFallsBackToRuliiNativeDefault() {
        RuleViolation violation = RuleViolation.builder().with("name_Custom")
                .errorCode("no.bundle.entry")
                .defaultMessage("Value {0} must be valid.")
                .param("value", 42)
                .build(s8Stack((locale, code, defaultMessage) -> null), formatter, Locale.US);

        // Core's positional default formats against param order — untouched by translation.
        assertEquals("Value 42 must be valid.", violation.getErrorMessage());
    }

    @Test
    public void userResolverOverridesBundle() {
        MessageResolver user = (locale, code, defaultMessage) ->
                SIZE_CODE.equals(code) ? "length must be {min}-{max}" : null;

        RuleViolation violation = RuleViolation.builder().with("name_Size")
                .errorCode(SIZE_CODE)
                .defaultMessage("unused {0}")
                .param("min", 2)
                .param("max", 5)
                .build(s8Stack(user), formatter, Locale.US);

        assertEquals("length must be 2-5", violation.getErrorMessage());
    }

    @Test
    public void elBundleEntryEmergesVerbatim() {
        RuleViolation violation = RuleViolation.builder().with("name_El")
                .errorCode("org.rulii.test.el.message")
                .defaultMessage("unused")
                .build(s8Stack((locale, code, defaultMessage) -> null), formatter, Locale.US);

        assertEquals("${validatedValue > 0 ? 'positive' : 'negative'}", violation.getErrorMessage());
    }

    @Test
    public void quotedBundleEntrySurvivesFormatting() {
        RuleViolation violation = RuleViolation.builder().with("name_Quoted")
                .errorCode("org.rulii.test.quoted.message")
                .defaultMessage("unused")
                .param("min", 7)
                .build(s8Stack((locale, code, defaultMessage) -> null), formatter, Locale.US);

        assertEquals("l'id 7 n'est pas valide", violation.getErrorMessage());
    }

    @Test
    public void literalErrorMessageBypassesResolverButIsFormatted() {
        // S8 verify-at-impl item 4: a literal errorMessage never reaches the resolver —
        // it must arrive already translated (materialization's job) and is still formatted.
        RuleViolation violation = RuleViolation.builder().with("name_Literal")
                .errorCode(SIZE_CODE)
                .errorMessage("custom: at least ${min}")
                .param("min", 3)
                .build(s8Stack((locale, code, defaultMessage) -> {
                    throw new AssertionError("resolver must not be consulted for literal messages");
                }), formatter, Locale.US);

        assertEquals("custom: at least 3", violation.getErrorMessage());
    }
}
