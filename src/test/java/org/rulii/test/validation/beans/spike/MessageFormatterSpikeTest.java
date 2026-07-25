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
package org.rulii.test.validation.beans.spike;

import org.junit.jupiter.api.Test;
import org.rulii.text.MessageFormatter;
import org.rulii.text.ParameterInfo;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification spikes pinning the core message-formatting behaviors the S8 message
 * design depends on (SOLUTION S8 verify-at-impl items 1 and 2): named placeholder
 * replacement, unresolvable placeholders, jakarta-syntax brace hazards, and the
 * MessageFormat-quoting EL shield.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class MessageFormatterSpikeTest {

    public MessageFormatterSpikeTest() {
        super();
    }

    private final MessageFormatter formatter = MessageFormatter.builder().build();

    // -----------------------------------------------------------------------
    // Named placeholders (R12.2 baseline)
    // -----------------------------------------------------------------------

    @Test
    public void namedPlaceholderReplaced() {
        String result = formatter.format(Locale.US, "Hello ${name}.",
                new ParameterInfo(0, "name", "World"));
        assertEquals("Hello World.", result);
    }

    @Test
    public void unresolvedPlaceholderThrows() {
        // S8 verify-at-impl item 1, OBSERVED: an unresolved placeholder is NOT left
        // verbatim — the parser rewrites ${unknown} to {unknown} and MessageFormat
        // throws. Every ${name} the translator/decorator emits MUST have a matching
        // param, or be shielded.
        assertThrows(IllegalArgumentException.class,
                () -> formatter.format(Locale.US, "Hello ${unknown}."));
    }

    // -----------------------------------------------------------------------
    // Jakarta-syntax hazards (S8 forcing fact)
    // -----------------------------------------------------------------------

    @Test
    public void jakartaStyleBracesBehavior() {
        // Untranslated jakarta text reaching the formatter: expected to fail loudly
        // (java.text.MessageFormat cannot parse {min}) — the S8 forcing fact.
        assertThrows(IllegalArgumentException.class,
                () -> formatter.format(Locale.US, "must be between {min} and {max}",
                        new ParameterInfo(0, "min", 1), new ParameterInfo(1, "max", 5)));
    }

    // -----------------------------------------------------------------------
    // EL shielding (R12.5 / S8 verify-at-impl item 2)
    // -----------------------------------------------------------------------

    @Test
    public void elExpressionUnshieldedThrows() {
        // OBSERVED: unshielded EL does not merely mangle — it throws (same rewrite as
        // above). R12.5 shielding is mandatory for correctness, not cosmetics.
        assertThrows(IllegalArgumentException.class,
                () -> formatter.format(Locale.US, "${validatedValue > 0 ? 'yes' : 'no'}"));
    }

    @Test
    public void elShieldViaMessageFormatQuoting() {
        // Shield candidate: the translator rewrites ${expr} to $'{expr}' so the
        // placeholder parser cannot match it and MessageFormat emits it verbatim.
        String result = formatter.format(Locale.US, "$'{validatedValue > 0}'");
        assertEquals("${validatedValue > 0}", result);
    }
}
