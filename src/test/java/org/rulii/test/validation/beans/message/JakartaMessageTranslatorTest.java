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
import org.rulii.text.ParameterInfo;
import org.rulii.validation.beans.message.JakartaMessageTranslator;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for jakarta-to-rulii template translation: attribute placeholders, attribute-name
 * mapping, EL shielding, jakarta escapes, MessageFormat quoting, and lenient handling of
 * malformed templates. End-to-end assertions run the translated template through core's
 * real formatting pipeline — the translation contract is "formats correctly", not
 * "matches an expected string".
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class JakartaMessageTranslatorTest {

    public JakartaMessageTranslatorTest() {
        super();
    }

    private final MessageFormatter formatter = MessageFormatter.builder().build();

    private String format(String template, ParameterInfo... parameters) {
        return formatter.format(Locale.US, template, parameters);
    }

    // -----------------------------------------------------------------------
    // Attribute placeholders
    // -----------------------------------------------------------------------

    @Test
    public void attributePlaceholdersTranslated() {
        String result = JakartaMessageTranslator.translate("size must be between {min} and {max}");

        assertEquals("size must be between ${min} and ${max}", result);
        assertEquals("size must be between 2 and 5",
                format(result, new ParameterInfo(0, "min", 2), new ParameterInfo(1, "max", 5)));
    }

    @Test
    public void attributeNameMappingApplied() {
        String result = JakartaMessageTranslator.translate("must match \"{regexp}\"", Map.of("regexp", "pattern"));

        assertEquals("must match \"${pattern}\"", result);
        assertEquals("must match \"[a-z]+\"", format(result, new ParameterInfo(0, "pattern", "[a-z]+")));
    }

    @Test
    public void ruliiPlaceholdersKept() {
        // Already-translated syntax passes through — safe on rulii-authored bundle text.
        String result = JakartaMessageTranslator.translate("size must be between ${min} and ${max}");

        assertEquals("size must be between ${min} and ${max}", result);
    }

    @Test
    public void plainTextUntouched() {
        assertEquals("must be valid", JakartaMessageTranslator.translate("must be valid"));
        assertEquals("", JakartaMessageTranslator.translate(""));
    }

    // -----------------------------------------------------------------------
    // EL shielding (R12.5)
    // -----------------------------------------------------------------------

    @Test
    public void bareValidatedValuePassesThroughAsPlaceholder() {
        String result = JakartaMessageTranslator.translate("value ${validatedValue} rejected");

        assertEquals("value ${validatedValue} rejected", result);
        assertEquals("value 42 rejected", format(result, new ParameterInfo(0, "validatedValue", 42)));
    }

    @Test
    public void elExpressionShieldedVerbatim() {
        String result = JakartaMessageTranslator.translate("${validatedValue > 0 ? 'positive' : 'negative'}");

        // Shielded EL emerges verbatim from the full pipeline — never evaluated, never thrown on.
        assertEquals("${validatedValue > 0 ? 'positive' : 'negative'}", format(result));
    }

    @Test
    public void elFormatterCallShieldedVerbatim() {
        String result = JakartaMessageTranslator.translate("${formatter.format('%1$.2f', validatedValue)}");

        assertEquals("${formatter.format('%1$.2f', validatedValue)}", format(result));
    }

    // -----------------------------------------------------------------------
    // Jakarta escapes
    // -----------------------------------------------------------------------

    @Test
    public void escapedBracesAreLiteral() {
        String result = JakartaMessageTranslator.translate("literal \\{min\\} here");

        assertEquals("literal {min} here", format(result));
    }

    @Test
    public void escapedDollarThenParameter() {
        // \$ is a literal dollar; {min} stays a real parameter.
        String result = JakartaMessageTranslator.translate("costs \\${min}");

        assertEquals("costs $5", format(result, new ParameterInfo(0, "min", 5)));
    }

    @Test
    public void escapedDollarBraceSequenceIsLiteral() {
        // \$\{min\} spells the literal text ${min} — must NOT become a placeholder.
        String result = JakartaMessageTranslator.translate("write \\$\\{min\\} to reference min");

        assertEquals("write ${min} to reference min", format(result));
    }

    @Test
    public void escapedBackslashIsLiteral() {
        String result = JakartaMessageTranslator.translate("path \\\\ sep");

        assertEquals("path \\ sep", format(result));
    }

    // -----------------------------------------------------------------------
    // MessageFormat hazards in literal text
    // -----------------------------------------------------------------------

    @Test
    public void singleQuotesSurviveFormatting() {
        String result = JakartaMessageTranslator.translate("l'année {min} n'est pas valide");

        // NOTE: numeric params ride MessageFormat's locale-aware number formatting —
        // a 4-digit Integer would render grouped ("2,020"). Kept small here; the
        // grouping divergence from jakarta implementations is a Phase 4 decision.
        assertEquals("l'année 99 n'est pas valide", format(result, new ParameterInfo(0, "min", 99)));
    }

    @Test
    public void quotedElContentSurvivesShielding() {
        String result = JakartaMessageTranslator.translate("${v == 'a' ? 'x' : 'y'}");

        assertEquals("${v == 'a' ? 'x' : 'y'}", format(result));
    }

    // -----------------------------------------------------------------------
    // Unsupported / malformed parameters — lenient verbatim
    // -----------------------------------------------------------------------

    @Test
    public void nonIdentifierParameterEmergesVerbatim() {
        // Nested bundle-key parameters are not supported (no recursive resolution) —
        // they must emerge verbatim instead of blowing up the render.
        String result = JakartaMessageTranslator.translate("see {jakarta.validation.constraints.Size.message}");

        assertEquals("see {jakarta.validation.constraints.Size.message}", format(result));
    }

    @Test
    public void unterminatedBraceIsLiteral() {
        assertEquals("oops {min", format(JakartaMessageTranslator.translate("oops {min")));
        assertEquals("oops ${min", format(JakartaMessageTranslator.translate("oops ${min")));
    }

    @Test
    public void emptyBracesAreLiteral() {
        assertEquals("empty {} and ${}",
                format(JakartaMessageTranslator.translate("empty {} and ${}")));
    }

    @Test
    public void adjacentSpecialsQuoteAsOneRun() {
        // Adjacent quoted chunks are the classic MessageFormat trap ('}''{' misparses);
        // the translator quotes contiguous runs.
        assertEquals("{}{}", format(JakartaMessageTranslator.translate("\\{\\}\\{\\}")));
    }

    // -----------------------------------------------------------------------
    // Contract: emitted placeholders need bound params
    // -----------------------------------------------------------------------

    @Test
    public void emittedPlaceholderWithoutParamThrows() {
        // Documents the downstream obligation (S8): every {attr} we turn into ${attr}
        // must be bound as a rule param at render time — core throws otherwise.
        String result = JakartaMessageTranslator.translate("must be at least {min}");

        assertThrows(IllegalArgumentException.class, () -> format(result));
    }
}
