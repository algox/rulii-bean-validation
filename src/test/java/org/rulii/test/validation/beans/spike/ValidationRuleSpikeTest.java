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
import org.rulii.rule.Rule;
import org.rulii.rule.RuleResult;
import org.rulii.text.MessageFormatter;
import org.rulii.text.MessageResolver;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.rulii.validation.rules.Validators.*;

/**
 * Verification spikes pinning core validation-rule reporting behaviors the S8 message
 * design depends on (SOLUTION S8 verify-at-impl items 3 and 4): which violation params
 * core rules populate (ground truth for the attribute-name mapping table), and how
 * errorMessage vs defaultMessage interact with the MessageResolver.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValidationRuleSpikeTest {

    public ValidationRuleSpikeTest() {
        super();
    }

    private final MessageFormatter formatter = MessageFormatter.builder().build();

    // -----------------------------------------------------------------------
    // Violation params emitted by core rules (S8 verify-at-impl item 3)
    // -----------------------------------------------------------------------

    @Test
    public void sizeRuleViolationParams() {
        Rule rule = size(binding("tags"), 1, 3).build();
        RuleViolations errors = new RuleViolations();

        RuleResult result = rule.run(ruleViolations -> errors, tags -> List.of());
        assertTrue(result.status().isFail());

        RuleViolation violation = errors.getViolations().get(0);
        // Ground truth for the S8 attribute-name mapping table: which keys does the
        // core size rule emit? Jakarta expects {min}/{max}.
        assertTrue(violation.getParameters().containsKey("tags"),
                "params were: " + violation.getParameters());
        assertTrue(violation.getParameters().containsKey("min"),
                "params were: " + violation.getParameters());
        assertTrue(violation.getParameters().containsKey("max"),
                "params were: " + violation.getParameters());
    }

    @Test
    public void minRuleViolationParams() {
        Rule rule = min(binding("age"), 18L).build();
        RuleViolations errors = new RuleViolations();

        RuleResult result = rule.run(ruleViolations -> errors, age -> 16);
        assertTrue(result.status().isFail());

        RuleViolation violation = errors.getViolations().get(0);
        // Jakarta expects {value} for @Min; document what core actually emits.
        assertTrue(violation.getParameters().containsKey("age"),
                "params were: " + violation.getParameters());
        assertTrue(violation.getParameters().containsKey("min"),
                "params were: " + violation.getParameters());
    }

    // -----------------------------------------------------------------------
    // errorMessage vs defaultMessage vs resolver (S8 verify-at-impl item 4)
    // -----------------------------------------------------------------------

    @Test
    public void literalErrorMessageBypassesResolverButIsFormatted() {
        AtomicBoolean resolverCalled = new AtomicBoolean(false);
        MessageResolver resolver = (locale, code, defaultMessage) -> {
            resolverCalled.set(true);
            return "fromBundle";
        };

        RuleViolation violation = RuleViolation.builder().with("testRule", "test.code")
                .errorMessage("literal ${v} message")
                .param("v", 42)
                .build(resolver, formatter, Locale.US);

        // The invariant S8 relies on: literals skip resolution (translated once, at
        // materialization) but still ride the formatter.
        assertFalse(resolverCalled.get());
        assertEquals("literal 42 message", violation.getErrorMessage());
    }

    @Test
    public void resolverResultIsFormatted() {
        MessageResolver resolver = (locale, code, defaultMessage) -> "resolved ${v} message";

        RuleViolation violation = RuleViolation.builder().with("testRule", "test.code")
                .param("v", 7)
                .build(resolver, formatter, Locale.US);

        // The decorator interposition point: resolver output flows into the formatter,
        // so render-time translation in a MessageResolver decorator reaches the
        // formatter exactly as S8 requires.
        assertEquals("resolved 7 message", violation.getErrorMessage());
    }

    @Test
    public void defaultMessageUsedWhenResolverPassesItThrough() {
        MessageResolver resolver = (locale, code, defaultMessage) -> defaultMessage;

        RuleViolation violation = RuleViolation.builder().with("testRule", "test.code")
                .defaultMessage("default ${v} message")
                .param("v", 9)
                .build(resolver, formatter, Locale.US);

        assertEquals("default 9 message", violation.getErrorMessage());
    }
}
