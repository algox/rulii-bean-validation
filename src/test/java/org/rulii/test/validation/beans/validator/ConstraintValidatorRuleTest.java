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
import jakarta.validation.Payload;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.model.UnrulyException;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleExecutionStatus;
import org.rulii.rule.RuleListener;
import org.rulii.rule.RuleResult;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.CustomPathRuleViolation;
import org.rulii.validation.beans.validator.ConstraintValidatorException;
import org.rulii.validation.beans.validator.ConstraintValidatorRule;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConstraintValidatorRule} as a rule of its own: one call to the validator, its outcome mapped in the
 * same run, nothing carried between evaluations.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintValidatorRuleTest {

    public ConstraintValidatorRuleTest() {
        super();
    }

    private static ConstraintValidatorRule rule(ConstraintValidator<?, ?> validator) {
        return ConstraintValidatorRule.builder(Validators.binding("code"), validator, Code.class)
                .name("code_code")
                .errorCode("code.invalid")
                .defaultMessageTemplate("must be a code")
                .parameters(Map.of("prefix", "X"))
                .build();
    }

    private static RuleContext context(Object code, RuleViolations violations) {
        Bindings bindings = Bindings.builder().standard();
        bindings.bind("code", Object.class, code);
        if (violations != null) bindings.bind("ruleViolations", RuleViolations.class, violations);
        return RuleContext.builder().build(bindings);
    }

    @Test
    public void passFailAndErrorWithTheTrace() {
        ConstraintValidatorRule rule = rule(new PrefixValidator());
        RuleViolations violations = new RuleViolations();
        RuleContext context = context("X1", violations);
        RecordingListener listener = new RecordingListener();
        context.getTracer().addListener(listener);

        RuleResult pass = rule.run(context);
        assertSame(rule, pass.rule());
        assertEquals(RuleExecutionStatus.PASS, pass.status());
        assertTrue(violations.isEmpty());

        context.getBindings().setValue("code", "Y1");
        RuleResult fail = rule.run(context);
        assertEquals(RuleExecutionStatus.FAIL, fail.status());
        assertEquals(1, violations.size());
        RuleViolation violation = violations.getViolation(0);
        assertEquals("code_code", violation.getRuleName());
        assertEquals("code.invalid", violation.getErrorCode());
        assertEquals(Severity.ERROR, violation.getSeverity());
        assertEquals("Y1", violation.getParameterValues().get("value"));
        assertEquals("X", violation.getParameterValues().get("prefix"));

        context.getBindings().setValue("code", "boom");
        // Wrapped as the core strategy wraps a failing rule method; the validator's failure is the cause.
        UnrulyException e = assertThrows(UnrulyException.class, () -> rule.run(context));
        ConstraintValidatorException cause = assertInstanceOf(ConstraintValidatorException.class, e.getCause());
        assertEquals(PrefixValidator.class, cause.getValidatorClass());
        assertInstanceOf(IllegalStateException.class, cause.getCause());
        assertEquals(1, violations.size(), "a failing validator reports nothing");

        assertEquals(List.of("start", "end:PASS", "start", "end:FAIL", "start", "error", "end:ERROR"), listener.events);
    }

    @Test
    public void isTrueAsksTheValidatorAndReportsNothing() {
        ConstraintValidatorRule rule = rule(new PrefixValidator());
        RuleViolations violations = new RuleViolations();

        assertTrue(rule.isTrue(context("X1", violations)));
        assertFalse(rule.isTrue(context("Y1", violations)));
        assertTrue(violations.isEmpty());
        // null goes to the validator, which decides
        assertTrue(rule.isTrue(context(null, violations)));
    }

    @Test
    public void nullReachesTheValidator() {
        List<Object> seen = new ArrayList<>();
        ConstraintValidatorRule rule = rule(new RecordingValidator(seen));

        assertEquals(RuleExecutionStatus.PASS, rule.run(context(null, new RuleViolations())).status());
        assertEquals(1, seen.size());
        assertNull(seen.get(0));
    }

    @Test
    public void missingCollectorFailsOnlyWhenThereIsSomethingToReport() {
        ConstraintValidatorRule rule = rule(new PrefixValidator());

        assertEquals(RuleExecutionStatus.PASS, rule.run(context("X1", null)).status());
        UnrulyException e = assertThrows(UnrulyException.class, () -> rule.run(context("Y1", null)));
        assertTrue(e.getMessage().contains("RuleViolations not defined"));
    }

    @Test
    public void disabledDefaultAndCustomViolations() {
        ConstraintValidatorRule rule = rule(new TalkativeValidator());
        RuleViolations violations = new RuleViolations();

        assertEquals(RuleExecutionStatus.FAIL, rule.run(context("Y1", violations)).status());
        assertEquals(2, violations.size(), "the default is disabled; two custom violations remain");
        assertInstanceOf(CustomPathRuleViolation.class, violations.getViolation(0));
        assertInstanceOf(CustomPathRuleViolation.class, violations.getViolation(1));
        assertEquals("first", ((CustomPathRuleViolation) violations.getViolation(0)).getMessageTemplate());
        assertEquals("second", ((CustomPathRuleViolation) violations.getViolation(1)).getMessageTemplate());
    }

    @Test
    public void evaluationsDoNotLeakIntoOneAnother() throws Exception {
        // The validator writes to its context on every failure; each run must report only its own.
        ConstraintValidatorRule rule = rule(new TalkativeValidator());

        RuleViolations first = new RuleViolations();
        rule.run(context("Y1", first));
        RuleViolations second = new RuleViolations();
        rule.run(context("X1", second));
        RuleViolations third = new RuleViolations();
        rule.run(context("Y2", third));
        assertEquals(2, first.size());
        assertTrue(second.isEmpty());
        assertEquals(2, third.size());

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                boolean valid = i % 2 == 0;
                results.add(executor.submit(() -> {
                    go.await(5, TimeUnit.SECONDS);
                    RuleViolations own = new RuleViolations();
                    int total = 0;
                    for (int j = 0; j < 200; j++) {
                        rule.run(context(valid ? "X" + j : "Y" + j, own));
                        total += own.size();
                        own = new RuleViolations();
                    }
                    return total;
                }));
            }
            go.countDown();
            for (int i = 0; i < threads; i++) {
                assertEquals(i % 2 == 0 ? 0 : 400, results.get(i).get(10, TimeUnit.SECONDS), "thread " + i);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void atomicRuleHasNoPartsButADefinition() {
        ConstraintValidatorRule rule = rule(new PrefixValidator());

        assertNull(rule.getPreCondition());
        assertNull(rule.getCondition());
        assertNull(rule.getOtherwiseAction());
        assertTrue(rule.getActions().isEmpty());
        assertEquals("code_code", rule.getName());
        assertEquals("code_code", rule.getDefinition().getName());
        assertEquals(ConstraintValidatorRule.class, rule.getDefinition().getRuleClass());
        assertNotNull(rule.getDefinition().getSource());
        assertNull(rule.getDefinition().getConditionDefinition());
        assertTrue(rule.getDefinition().getThenActionDefinitions().isEmpty());
        assertTrue(rule.getDescription().contains("PrefixValidator"));
        assertEquals(Code.class, rule.getAnnotationType());
        assertEquals("must be a code", rule.getDefaultMessageTemplate());
        assertEquals(Map.of("prefix", "X"), rule.getParameters());
        assertEquals("value", rule.getValueName());
        assertInstanceOf(Rule.class, rule);
        assertTrue(rule.toString().contains("PrefixValidator"));
    }

    // --- fixtures ----------------------------------------------------------------------------------

    private static final class RecordingListener implements RuleListener {

        final List<String> events = new ArrayList<>();

        @Override
        public void onRuleStart(Rule rule) {
            events.add("start");
        }

        @Override
        public void onRuleError(Rule rule, Exception e) {
            events.add("error");
        }

        @Override
        public void onRuleEnd(Rule rule, RuleResult result) {
            events.add("end:" + result.status());
        }
    }

    @Constraint(validatedBy = PrefixValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Code {
        String message() default "must be a code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String prefix() default "X";
    }

    /** Valid when the value starts with X; throws for "boom". */
    public static class PrefixValidator implements ConstraintValidator<Code, Object> {

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            if (value == null) return true;
            if ("boom".equals(value)) throw new IllegalStateException("boom");
            return value.toString().startsWith("X");
        }
    }

    /** Like the prefix validator, but on failure disables the default and adds two custom violations. */
    public static class TalkativeValidator implements ConstraintValidator<Code, Object> {

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            if (value == null || value.toString().startsWith("X")) return true;
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("first").addConstraintViolation();
            context.buildConstraintViolationWithTemplate("second").addConstraintViolation();
            return false;
        }
    }

    public static class RecordingValidator implements ConstraintValidator<Code, Object> {

        private final List<Object> seen;

        public RecordingValidator(List<Object> seen) {
            super();
            this.seen = seen;
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            seen.add(value);
            return true;
        }
    }
}
