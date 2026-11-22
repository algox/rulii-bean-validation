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
package org.rulii.test.validation.beans.assembly;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.model.UnrulyException;
import org.rulii.model.action.Action;
import org.rulii.model.condition.Condition;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleListener;
import org.rulii.rule.RuleResult;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.assembly.RulePlan;
import org.rulii.validation.beans.constraints.Length;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@link RulePlan} against the rule set of the same rules: whatever a plan asks a validator directly
 * must come out as the engine has it, violation for violation and event for event.
 */
public class RulePlanTest {

    private static final BeanValidator VALIDATOR = validator(false);
    private static final BeanValidator FAIL_FAST = validator(true);

    private static BeanValidator validator(boolean failFast) {
        return BeanValidator.builder()
                // A mapper that builds over a value function of its own: the rule reads [note], not the property it sits on.
                .mapper(new SimpleConstraintMapper<>(NoteRequired.class, List.of(Object.class),
                        (a, t) -> List.of(t.configure(Validators.notNull(Validators.binding("note")), true).build())))
                .failFast(failFast)
                .build();
    }

    public RulePlanTest() {
        super();
    }

    @Test
    public void rulesOfRuliiValidatorsAreAskedDirectlyAndTheRestAreRun() {
        BeanRules rules = VALIDATOR.getBeanRules(Sample.class);

        for (String name : List.of("text_notNull", "text_size", "text_pattern", "email_email", "number_min", "number_max",
                "amount_decimalMin", "amount_digits", "born_past", "items_notEmpty", "flag_assertTrue",
                "code_code_notNull", "code_code_size")) {
            assertNotNull(rules.getRule(name).getCheck(), name + " is a rulii validator reading its site");
        }

        assertNull(rules.getRule("code_code").getCheck(), "a @ReportAsSingleViolation composition runs its rules");
        assertNull(rules.getRule("even_even").getCheck(), "a constraint validator is run");
        assertNull(rules.getRule("number_expr").getCheck(), "an @Expr is run");
        assertNull(rules.getRule("flag_noteRequired").getCheck(), "a rule built over another value function is run");

        MaterializedRule field = rules.getRule("getterToo_size");
        assertNotNull(field.getAccessor(), "a field constraint of a property bound through its getter reads the field");
        assertNotNull(field.getCheck());
        assertNull(rules.getRule("text_size").getAccessor(), "a rule of the primary declaration reads the binding");
    }

    @Test
    public void planReportsWhatTheRuleSetReports() {
        for (Sample sample : samples()) {
            for (BeanValidator validator : List.of(VALIDATOR, FAIL_FAST)) {
                BeanRules rules = validator.getBeanRules(Sample.class);
                Run viaSet = run(rules, sample, false, null);
                Run viaPlan = run(rules, sample, true, null);

                assertEquals(viaSet.violations, viaPlan.violations, "violations of " + sample + (validator == FAIL_FAST ? " (fail-fast)" : ""));
                assertEquals(viaSet.events, viaPlan.events, "rule events of " + sample + (validator == FAIL_FAST ? " (fail-fast)" : ""));
            }
        }
    }

    @Test
    public void filterIsAskedForEveryRuleBeforeTheFirstRuns() {
        BeanRules rules = VALIDATOR.getBeanRules(Sample.class);
        List<String> order = new ArrayList<>();
        Predicate<MaterializedRule> filter = rule -> {
            order.add("asked " + rule.getName());
            return rule.getName().startsWith("text_");
        };
        Run run = run(rules, samples().get(1), true, filter, order);

        int firstStart = order.indexOf("start text_notNull");
        assertEquals(rules.plan(RequestedGroups.DEFAULT).getRules().size(), firstStart, "every rule was asked about first");
        assertTrue(run.events.stream().filter(event -> !event.startsWith("asked ")).allMatch(event -> event.contains(" text_")), run.events.toString());
        assertFalse(run.violations.isEmpty());
    }

    @Test
    public void failingRuleFailsAsItDoesInTheEngine() {
        BeanRules rules = VALIDATOR.getBeanRules(Broken.class);
        UnrulyException viaSet = assertThrows(UnrulyException.class, () -> run(rules, new Broken(), false, null));
        UnrulyException viaPlan = assertThrows(UnrulyException.class, () -> run(rules, new Broken(), true, null));

        assertEquals(viaSet.getMessage(), viaPlan.getMessage(), "the rule set is named");
        assertEquals(viaSet.getCause().getMessage(), viaPlan.getCause().getMessage(), "the rule and the part that failed are named");
        assertEquals("length failed", root(viaPlan).getMessage());
        assertEquals(root(viaSet).getClass(), root(viaPlan).getClass());
    }

    @Test
    public void emptyPlanRunsNothing() {
        RulePlan plan = VALIDATOR.getBeanRules(Sample.class).plan(RequestedGroups.of(Nothing.class));

        assertTrue(plan.isEmpty());
        assertDoesNotThrow(() -> { run(VALIDATOR.getBeanRules(Sample.class), samples().get(0), true, null); });
        assertEquals(VALIDATOR.getBeanRules(Sample.class).getName(), plan.getName());
    }

    private static Throwable root(Throwable e) {
        Throwable result = e;
        while (result.getCause() != null) result = result.getCause();
        return result;
    }

    /**
     * What one run reported and what a rule listener saw of it.
     */
    private record Run(List<String> violations, List<String> events) {}

    private static Run run(BeanRules rules, Object bean, boolean plan, Predicate<MaterializedRule> filter) {
        return run(rules, bean, plan, filter, new ArrayList<>());
    }

    private static Run run(BeanRules rules, Object bean, boolean plan, Predicate<MaterializedRule> filter, List<String> events) {
        BeanRuleViolations violations = new BeanRuleViolations(bean);
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, bean);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        rules.getBindingPlan().load(bindings, bean);
        RuleContext context = RuleContext.builder().with(bindings).build();
        context.getTracer().addListener(new RuleListener() {
            @Override public void onRuleStart(Rule rule) { events.add("start " + rule.getName()); }
            @Override public void onPreConditionCheck(Rule rule, Condition condition, boolean result) { events.add("pre " + rule.getName() + " " + (condition == rule.getPreCondition()) + " " + result); }
            @Override public void onGiven(Rule rule, Condition condition, boolean result) { events.add("given " + rule.getName() + " " + (condition == rule.getCondition()) + " " + result); }
            @Override public void onThen(Rule rule, Action action) { events.add("then " + rule.getName()); }
            @Override public void onOtherwise(Rule rule, Action action) { events.add("otherwise " + rule.getName() + " " + (action == rule.getOtherwiseAction())); }
            @Override public void onRuleError(Rule rule, Exception e) { events.add("error " + rule.getName()); }
            @Override public void onRuleEnd(Rule rule, RuleResult result) { events.add("end " + rule.getName() + " " + (result != null ? result.status() : null)); }
        });

        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), rules, bean)) {
            if (plan) {
                rules.plan(RequestedGroups.DEFAULT).run(context, violations, bean, filter);
            } else {
                rules.ruleSet(RequestedGroups.DEFAULT).run(context);
            }
        }

        List<String> reported = new ArrayList<>();

        for (BeanRuleViolation violation : violations.getBeanViolations()) {
            RuleViolation plain = violation;
            reported.add(plain.getRuleName() + " | " + plain.getErrorCode() + " | " + plain.getSeverity() + " | " + violation.getPropertyPath()
                    + " | " + violation.getMessage() + " | " + violation.getMessageTemplate() + " | " + violation.getInvalidValue()
                    + " | " + plain.getParameters());
        }

        return new Run(reported, events);
    }

    private static List<Sample> samples() {
        return List.of(
                new Sample("abc", "a@rulii.org", 5, "1.50", LocalDate.of(2000, 1, 1), List.of("x"), true, "ABC", 4, "abc", "note", "abcd"),
                new Sample(null, "nope", 1, "0.001", LocalDate.of(2999, 1, 1), List.of(), false, "ab", 3, "a", null, "a"),
                new Sample("A", "", 99, "12345.678", null, null, true, null, 7, "abcdef", "note", null),
                new Sample("abcdefgh", null, null, null, LocalDate.of(1990, 5, 5), Arrays.asList("a", null), false, "ABCDEFGH", null, null, null, "abcdefgh"));
    }

    public interface Unused {}

    public interface Nothing {}

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface NoteRequired {
        String message() default "a note is required";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = EvenValidator.class)
    public @interface Even {
        String message() default "must be even";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class EvenValidator implements ConstraintValidator<Even, Integer> {

        public EvenValidator() {
            super();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || value % 2 == 0;
        }
    }

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @NotNull
    @Size(min = 3, max = 6)
    @ReportAsSingleViolation
    public @interface Code {
        String message() default "not a code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class Sample {

        @NotNull
        @Size(min = 2, max = 5)
        @Pattern(regexp = "[a-z]+", message = "letters only, not ${validatedValue}")
        private final String text;

        @Email
        @NotBlank
        private final String email;

        @Min(2)
        @Max(value = 50, message = "at most {value}")
        @Positive
        @Expr(value = "${number == null || number != 7}", lang = "jakarta-el", errorCode = "sample.seven", message = "not seven")
        private final Integer number;

        @DecimalMin("0.01")
        @DecimalMax("999")
        @Digits(integer = 3, fraction = 2)
        private final BigDecimal amount;

        @Past
        @NotNull
        private final LocalDate born;

        @NotEmpty
        @Size(max = 1)
        private final List<String> items;

        @AssertTrue
        @NoteRequired
        private final Boolean flag;

        @Code
        private final String code;

        @Even
        private final Integer even;

        @Length(min = 2, max = 4)
        @Null(groups = Unused.class)
        private final String length;

        private final String note;

        @Size(min = 2, max = 4)
        private final String getterToo;

        Sample(String text, String email, Integer number, String amount, LocalDate born, List<String> items, Boolean flag, String code,
               Integer even, String length, String note, String getterToo) {
            super();
            this.text = text;
            this.email = email;
            this.number = number;
            this.amount = amount != null ? new BigDecimal(amount) : null;
            this.born = born;
            this.items = items;
            this.flag = flag;
            this.code = code;
            this.even = even;
            this.length = length;
            this.note = note;
            this.getterToo = getterToo;
        }

        @NotNull
        public String getGetterToo() {
            return getterToo != null ? getterToo + getterToo : null;
        }

        @Override
        public String toString() {
            return "Sample[" + text + ", " + number + "]";
        }
    }

    public static class Broken {

        @Size(min = 2)
        private final CharSequence text = new CharSequence() {
            @Override
            public int length() {
                throw new IllegalStateException("length failed");
            }

            @Override
            public char charAt(int index) {
                return 'a';
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return this;
            }
        };

        public Broken() {
            super();
        }
    }
}
