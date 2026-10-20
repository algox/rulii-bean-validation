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
package org.rulii.test.validation.beans.mapper;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.rule.RuleExecutionStatus;
import org.rulii.rule.RuleResult;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.StandardConstraintMappers;
import org.rulii.validation.beans.model.BeanDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.rulii.rule.RuleExecutionStatus.FAIL;
import static org.rulii.rule.RuleExecutionStatus.PASS;
import static org.rulii.rule.RuleExecutionStatus.SKIPPED;

/**
 * Runs every standard mapper end to end: materialize a bean carrying all 22 constraints, then
 * execute each rule against a passing value, a failing value and null.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class StandardMaterializationTest {

    private static final BeanRules RULES = new RuleMaterializer(ConstraintMapperRegistry.builder().build())
            .materialize(BeanDefinition.load(Everything.class, ValueExtractorRegistry.builder().build()));

    public StandardMaterializationTest() {
        super();
    }

    @Test
    public void allTwentyTwoMaterialize() {
        assertEquals(22, RULES.getPropertyRules().size());
        assertEquals(22, StandardConstraintMappers.all().size());
        for (MaterializedRule rule : RULES.getPropertyRules()) {
            assertEquals(rule.getDefinition().getAnnotationType().getName() + ".message", rule.getTarget().getErrorCode());
            assertNull(rule.getTarget().getMessage(), rule.getName() + " uses the default key");
        }
    }

    @Test
    public void booleans() {
        check("assertFalse_assertFalse", false, true);
        check("assertTrue_assertTrue", true, false);
    }

    @Test
    public void numbers() {
        check("decimalMax_decimalMax", new BigDecimal("10.5"), new BigDecimal("10.6"));
        check("decimalMin_decimalMin", new BigDecimal("1.6"), new BigDecimal("1.5"));   // exclusive
        check("digits_digits", new BigDecimal("123.45"), new BigDecimal("1234.5"));
        check("max_max", 10, 11);
        check("min_min", 1, 0);
        check("negative_negative", -1, 0);
        check("negativeOrZero_negativeOrZero", 0, 1);
        check("positive_positive", 1, 0);
        check("positiveOrZero_positiveOrZero", 0, -1);
    }

    @Test
    public void text() {
        check("email_email", "bob@acme.com", "nope");
        check("pattern_pattern", "ABC", "ab1");   // CASE_INSENSITIVE flag honoured
        check("size_size", List.of("a"), List.of("a", "b", "c", "d"));
    }

    @Test
    public void temporal() {
        LocalDate today = LocalDate.now();
        check("future_future", today.plusDays(1), today.minusDays(1));
        check("futureOrPresent_futureOrPresent", today, today.minusDays(1));
        check("past_past", today.minusDays(1), today.plusDays(1));
        check("pastOrPresent_pastOrPresent", today, today.plusDays(1));
    }

    @Test
    public void presence() {
        assertEquals(PASS, status("notNull_notNull", "x"));
        assertEquals(FAIL, status("notNull_notNull", null));
        assertEquals(PASS, status("notBlank_notBlank", "x"));
        assertEquals(FAIL, status("notBlank_notBlank", " "));
        assertEquals(FAIL, status("notBlank_notBlank", null));
        assertEquals(PASS, status("notEmpty_notEmpty", List.of("x")));
        assertEquals(FAIL, status("notEmpty_notEmpty", List.of()));
        assertEquals(FAIL, status("notEmpty_notEmpty", null));
        assertEquals(PASS, status("isNull_null", null));
        assertEquals(FAIL, status("isNull_null", "x"));
    }

    @Test
    public void violationsCarryTheConstraintParameters() {
        RuleViolation violation = violation("size_size", List.of("a", "b", "c", "d"));
        assertEquals("size_size", violation.getRuleName());
        assertEquals("jakarta.validation.constraints.Size.message", violation.getErrorCode());
        assertEquals("1", violation.getParameters().get("min"));
        assertEquals("3", violation.getParameters().get("max"));
        assertTrue(violation.getParameters().containsKey("size"));
        assertNotNull(violation.getErrorMessage());

        assertEquals("[a-z]+", violation("pattern_pattern", "1").getParameters().get("pattern"));
        assertEquals("3", violation("digits_digits", new BigDecimal("1234")).getParameters().get("maxIntegerLength"));
    }

    private static void check(String rule, Object passing, Object failing) {
        assertEquals(PASS, status(rule, passing), rule + " with " + passing);
        assertEquals(FAIL, status(rule, failing), rule + " with " + failing);
        assertEquals(SKIPPED, status(rule, null), rule + " with null");
    }

    private static RuleExecutionStatus status(String name, Object value) {
        MaterializedRule rule = RULES.getRule(name);
        assertNotNull(rule, name);
        Bindings bindings = Bindings.builder().standard();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, new RuleViolations());
        bindings.bind(rule.getBindingName(), Object.class, value);
        RuleResult result = rule.getRule().run(bindings);
        return result.status();
    }

    private static RuleViolation violation(String name, Object value) {
        MaterializedRule rule = RULES.getRule(name);
        Bindings bindings = Bindings.builder().standard();
        RuleViolations violations = new RuleViolations();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind(rule.getBindingName(), Object.class, value);
        rule.getRule().run(bindings);
        assertEquals(1, violations.size());
        return violations.getViolation(0);
    }

    // ---- fixture ----

    public static class Everything {
        @AssertFalse private Boolean assertFalse;
        @AssertTrue private Boolean assertTrue;
        @DecimalMax("10.5") private BigDecimal decimalMax;
        @DecimalMin(value = "1.5", inclusive = false) private BigDecimal decimalMin;
        @Digits(integer = 3, fraction = 2) private BigDecimal digits;
        @Email private String email;
        @Future private LocalDate future;
        @FutureOrPresent private LocalDate futureOrPresent;
        @Max(10) private Integer max;
        @Min(1) private Integer min;
        @Negative private Integer negative;
        @NegativeOrZero private Integer negativeOrZero;
        @NotBlank private String notBlank;
        @NotEmpty private List<String> notEmpty;
        @NotNull private Object notNull;
        @Null private Object isNull;
        @Past private LocalDate past;
        @PastOrPresent private LocalDate pastOrPresent;
        @Pattern(regexp = "[a-z]+", flags = Pattern.Flag.CASE_INSENSITIVE) private String pattern;
        @Positive private Integer positive;
        @PositiveOrZero private Integer positiveOrZero;
        @Size(min = 1, max = 3) private List<String> size;
    }
}
