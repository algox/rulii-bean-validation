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
package org.rulii.test.validation.beans;

import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.metadata.BeanDescriptor;
import org.junit.jupiter.api.Test;
import org.rulii.rule.RulingClass;
import org.rulii.script.EvaluationException;
import org.rulii.script.ScriptProcessorManager;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.ExecutableBeanValidator;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.ExprDefaults;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.ExprValidationRule;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.assembly.RulePlacement;
import org.rulii.validation.beans.model.ExprDeclarationException;
import org.rulii.validation.beans.model.ExprDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code @Expr} end to end: compiled at the first use of the class, run beside the constraints,
 * reported as violations with the declaration's code, severity and message; on properties, the
 * class, method and constructor arguments and return values.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ExprValidationTest {

    private static final ParameterNameProvider POSITIONAL = new ParameterNameProvider() {
        @Override
        public List<String> getParameterNames(Constructor<?> constructor) {
            return names(constructor.getParameterCount());
        }

        @Override
        public List<String> getParameterNames(Method method) {
            return names(method.getParameterCount());
        }

        private static List<String> names(int count) {
            List<String> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                result.add("p" + i);
            }
            return result;
        }
    };

    public ExprValidationTest() {
        super();
    }

    private static BeanValidator validator() {
        return BeanValidator.builder().parameterNameProvider(POSITIONAL).build();
    }

    private static List<String> report(Iterable<? extends ConstraintViolation<?>> violations) {
        List<String> result = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath() + ":" + ((RuleViolation) violation).getRuleName());
        }
        return result;
    }

    private static Trip trip(LocalDate start, LocalDate end) {
        Trip trip = new Trip();
        trip.start = start;
        trip.end = end;
        trip.seats = 2;
        trip.rooms = 1;
        return trip;
    }

    @Test
    public void exprOnAnInterfaceGetterReadsTheOverride() {
        BeanValidator validator = validator();

        Item item = new Item();
        item.price = -1;
        List<BeanRuleViolation> violations = validator.validate(item).getViolations();
        assertEquals(List.of("price:price_expr"), report(violations));
        assertEquals("price must be positive", violations.get(0).getMessage());
        assertEquals(-1, violations.get(0).getInvalidValue());

        item.price = 5;
        assertTrue(validator.validate(item).isEmpty());

        // Implicit grouping: the Default @Expr hosted on the interface belongs to the interface's group too.
        item.price = -1;
        assertEquals(List.of("price:price_expr"), report(validator.validate(item, Priced.class).getViolations()));
    }

    @Test
    public void propertyExprReportsAtThePropertyWithTheDeclaredCodeAndBundleMessage() {
        BeanValidator validator = validator();
        Trip trip = trip(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1));

        List<BeanRuleViolation> violations = validator.validate(trip).getViolations();
        assertEquals(List.of("end:end_expr"), report(violations));
        BeanRuleViolation violation = violations.get(0);
        assertEquals("trip.end", violation.getErrorCode());
        assertEquals(Severity.ERROR, violation.getSeverity());
        assertEquals("end must follow start", violation.getMessage(), "resolved under the error code from ValidationMessages");
        assertEquals(LocalDate.of(2026, 1, 1), violation.getInvalidValue());
        assertSame(trip, violation.getLeafBean());
        assertNull(violation.getConstraintDescriptor(), "an @Expr is not a constraint");
        assertNull(violation.getMessageTemplate());

        assertTrue(validator.validate(trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))).isEmpty());
        // The sibling is in scope: a missing start is accepted by the property script, caught by the class one.
        assertEquals(List.of(":Trip_expr"), report(validator.validate(trip(null, LocalDate.of(2026, 1, 10)))));
    }

    @Test
    public void nullPropertySkipsTheScriptUnlessAsked() {
        BeanValidator validator = validator();
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));

        // rooms: skipNull by default, so a null value reports nothing although the script would be false.
        trip.rooms = null;
        assertTrue(validator.validate(trip).isEmpty());
        trip.rooms = 0;
        List<BeanRuleViolation> rooms = validator.validate(trip).getViolations();
        assertEquals(List.of("rooms:rooms_expr"), report(rooms));
        assertEquals("rooms must be positive, not 0", rooms.get(0).getMessage(), "${validatedValue} is the formatter's");

        // seats: skipNull = false, so the script runs with the null value and reports; its message is the attribute's.
        trip.rooms = 1;
        trip.seats = null;
        List<BeanRuleViolation> violations = validator.validate(trip).getViolations();
        assertEquals(List.of("seats:seats_expr"), report(violations));
        assertEquals("seats are required", violations.get(0).getMessage());
        assertNull(violations.get(0).getInvalidValue());
    }

    @Test
    public void messageTemplateRendersTheValueAndRepeatedExprsAreNumbered() {
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        trip.seats = 50;

        List<BeanRuleViolation> violations = validator().validate(trip).getViolations();
        assertEquals(List.of("seats:seats_expr_2"), report(violations));
        assertEquals("too many seats: 50", violations.get(0).getMessage());
        assertEquals(50, violations.get(0).getInvalidValue());
    }

    @Test
    public void classLevelExprReportsAtTheBeanAndAlwaysRuns() {
        Trip trip = trip(null, null);

        assertEquals(List.of(":Trip_expr"), report(validator().validate(trip)));
    }

    @Test
    public void groupsAndSeveritySelectAndMarkTheViolation() {
        BeanValidator validator = validator();
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        trip.name = "x";

        assertTrue(validator.validate(trip).isEmpty(), "OnCreate stays out of a Default request");
        List<BeanRuleViolation> violations = validator.validate(trip, OnCreate.class).getViolations();
        assertEquals(List.of("name:name_expr"), report(violations));
        assertEquals(Severity.WARNING, violations.get(0).getSeverity());
        assertEquals(ExprValidationRule.DEFAULT_MESSAGE, violations.get(0).getMessage(),
                "no bundle entry and no attribute: the rule default");
    }

    @Test
    public void validatePropertyRunsThePropertyExpr() {
        Trip trip = trip(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1));
        trip.rooms = 0;

        assertEquals(List.of("end:end_expr"), report(validator().validateProperty(trip, "end")));
        assertEquals(List.of("rooms:rooms_expr"), report(validator().validateProperty(trip, "rooms")));
    }

    @Test
    public void throwingScriptIsAnErrorNotAViolation() {
        Broken broken = new Broken();
        broken.start = LocalDate.of(2026, 1, 1);

        Exception e = assertThrows(Exception.class, () -> validator().validate(broken));
        Throwable cause = e;
        while (cause != null && !(cause instanceof EvaluationException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "the script's own exception is in the chain: " + e);
    }

    @Test
    public void materializedRules() {
        BeanRules rules = validator().getBeanRules(Trip.class);

        assertEquals(List.of("end_expr", "seats_expr", "seats_expr_2", "rooms_expr", "name_expr"),
                rules.getPropertyRules().stream().map(MaterializedRule::getName).toList());
        assertEquals(List.of("Trip_expr"), rules.getClassRules().stream().map(MaterializedRule::getName).toList());

        MaterializedRule end = rules.getRule("end_expr");
        assertInstanceOf(ExprDefinition.class, end.getDefinition());
        assertEquals(RulePlacement.Kind.PROPERTY, end.getPlacement().getKind());
        assertEquals("end", end.getLeaf());
        assertEquals("trip.end", end.getTarget().getErrorCode());
        assertNull(end.getMessageTemplate());
        assertFalse(end.isHosted());
        ExprValidationRule endRule = target(end);
        assertFalse(endRule.isFailOnNull(), "skipNull: a null value is skipped");
        assertEquals("jakarta-el", endRule.getScript().getLanguageName());
        assertEquals("${start == null || end.isAfter(start)}", endRule.getScript().getSourceText());
        assertTrue(end.getRule().getDefinition().getDescription().startsWith("end must satisfy ${start == null || end.isAfter(start)} (@Expr on "),
                end.getRule().getDefinition().getDescription());
        assertEquals(Trip.class.getName(), end.getRule().getDefinition().getCategory());
        assertEquals(List.of("expr", "Expr", "Default"), end.getRule().getDefinition().getTags());
        assertEquals(List.of("expr", "Expr", "OnCreate"), rules.getRule("name_expr").getRule().getDefinition().getTags());
        assertNotNull(endRule.getCondition());
        assertTrue(endRule.toString().contains("trip.end"));

        assertTrue(target(rules.getRule("seats_expr")).isFailOnNull(), "skipNull = false: the script runs on null");
        assertEquals("too many seats: ${value}", rules.getRule("seats_expr_2").getMessageTemplate(), "as declared");
        assertEquals("too many seats: ${seats}", rules.getRule("seats_expr_2").getTarget().getMessage(), "${value} rewritten to the site name");
        assertTrue(target(rules.getRule("Trip_expr")).isFailOnNull(), "a class-level script always runs");
        assertEquals(RulePlacement.Kind.BEAN, rules.getRule("Trip_expr").getPlacement().getKind());
        assertEquals(Severity.WARNING, rules.getRule("name_expr").getDefinition().getSeverity());
    }

    private static ExprValidationRule target(MaterializedRule rule) {
        return assertInstanceOf(ExprValidationRule.class, ((RulingClass<?>) rule.getRule()).getTarget());
    }

    @Test
    public void languageMustBeDeclaredSomewhereAndKnownAndTheScriptMustCompile() {
        BeanValidator validator = validator();
        ScriptProcessorManager manager = ScriptProcessorManager.getInstance();
        assertNull(manager.getDefaultLanguage(), "the test JVM declares no default language");

        ExprDeclarationException none = assertThrows(ExprDeclarationException.class, () -> validator.getBeanRules(NoLanguage.class));
        assertTrue(none.getMessage().contains("names no scripting language"), none.getMessage());
        assertTrue(none.getMessage().contains(ScriptProcessorManager.DEFAULT_LANGUAGE_PROPERTY), none.getMessage());
        assertTrue(none.getMessage().contains("@ExprDefaults"), none.getMessage());
        assertTrue(none.getMessage().contains("jakarta-el"), "lists the available languages: " + none.getMessage());
        assertNotNull(none.getSource());

        // The manager's default is the last resort.
        try {
            manager.setDefaultLanguage("jakarta-el");
            NoLanguage bean = new NoLanguage();
            bean.value = "no";
            assertEquals(List.of("value:value_expr"), report(BeanValidator.builder().build().validate(bean)));
        } finally {
            manager.clearDefaultLanguage("jakarta-el");
        }

        ExprDeclarationException unknown = assertThrows(ExprDeclarationException.class, () -> validator.getBeanRules(UnknownLanguage.class));
        assertTrue(unknown.getMessage().contains("[klingon]"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("no ScriptProcessorFactory or JSR-223 engine"), unknown.getMessage());

        ExprDeclarationException syntax = assertThrows(ExprDeclarationException.class, () -> validator.getBeanRules(BadSyntax.class));
        assertTrue(syntax.getMessage().contains("does not compile in [jakarta-el]"), syntax.getMessage());
        assertTrue(syntax.getMessage().contains("${value >}"), syntax.getMessage());
        assertNotNull(syntax.getCause());
    }

    @Test
    public void exprStaysOutOfTheJakartaDescriptor() {
        BeanValidator validator = validator();
        BeanDescriptor descriptor = validator.getConstraintsForClass(Trip.class);

        assertFalse(descriptor.isBeanConstrained(), "no constraint annotations, only @Expr");
        assertNull(descriptor.getConstraintsForProperty("end"));
        assertTrue(descriptor.getConstrainedProperties().isEmpty());
        assertTrue(validator.getBeanDefinition(Trip.class).isConstrained(), "the library's own view counts it");
    }

    @Test
    public void exprOnExecutables() throws Exception {
        BeanValidator validator = validator();
        ExecutableBeanValidator executables = validator.forExecutables();
        Desk desk = new Desk(1);
        Method book = Desk.class.getMethod("book", int.class, int.class);
        Method label = Desk.class.getMethod("label", String.class);
        Constructor<Desk> constructor = Desk.class.getConstructor(int.class);

        // The arguments are in scope by parameter name; violations report at the cross-parameter node.
        List<BeanRuleViolation> violations = executables.validateParameters(desk, book, new Object[] {5, 3}).getViolations();
        assertEquals(List.of("book.<cross-parameter>:book_expr"), report(violations));
        assertEquals("desk.order", violations.get(0).getErrorCode());
        assertArrayEquals(new Object[] {5, 3}, (Object[]) violations.get(0).getInvalidValue());
        assertTrue(executables.validateParameters(desk, book, new Object[] {3, 5}).isEmpty());

        // The return value is $returnValue, applyTo choosing it for a method with parameters; violations report at the return-value node.
        assertEquals(List.of("label.<return value>:returnValue_expr"), report(executables.validateReturnValue(desk, label, "")));
        assertTrue(executables.validateReturnValue(desk, label, "desk 1").isEmpty());

        // A constructor's arguments, and the object it created.
        assertEquals(List.of("Desk.<cross-parameter>:Desk_expr"),
                report(executables.validateConstructorParameters(constructor, new Object[] {0})));
        assertTrue(executables.validateConstructorParameters(constructor, new Object[] {1}).isEmpty());

        // The scripts alone make the executables constrained: their flows exist.
        assertNotNull(validator.getParametersRuleFlow(Desk.class, book));
        assertNotNull(validator.getReturnValueRuleFlow(Desk.class, label));
        assertNull(validator.getReturnValueRuleFlow(Desk.class, book));
    }

    // --- fixtures -----------------------------------------------------------------------------------

    public interface OnCreate {}

    @ExprDefaults(lang = "jakarta-el")
    public interface Priced {
        @Expr(value = "${price > 0}", errorCode = "item.price", message = "price must be positive")
        Integer getPrice();
    }

    public static class Item implements Priced {
        Integer price;

        @Override
        public Integer getPrice() {
            return price;
        }
    }

    @ExprDefaults(lang = "jakarta-el")
    @Expr(value = "${start != null && end != null}", errorCode = "trip.dates")
    public static class Trip {
        public LocalDate start;

        @Expr(value = "${start == null || end.isAfter(start)}", errorCode = "trip.end")
        public LocalDate end;

        @Expr(value = "${seats != null}", errorCode = "trip.seats", message = "seats are required", skipNull = false)
        @Expr(value = "${seats < 10}", errorCode = "trip.seats.max", message = "too many seats: ${value}")
        public Integer seats;

        @Expr(value = "${rooms > 0}", errorCode = "trip.rooms", message = "rooms must be positive, not ${validatedValue}")
        public Integer rooms;

        @Expr(value = "${name.length() > 1}", errorCode = "trip.name", groups = OnCreate.class, severity = Severity.WARNING)
        public String name;
    }

    public static class Broken {
        @Expr(value = "${start.missing()}", lang = "jakarta-el", errorCode = "broken")
        public LocalDate start;
    }

    public static class NoLanguage {
        @Expr(value = "${value == 'ok'}", errorCode = "no.language")
        public String value;
    }

    public static class UnknownLanguage {
        @Expr(value = "value != null", lang = "klingon", errorCode = "unknown.language")
        public String value;
    }

    public static class BadSyntax {
        @Expr(value = "${value >}", lang = "jakarta-el", errorCode = "bad.syntax")
        public String value;
    }

    @ExprDefaults(lang = "jakarta-el")
    public static class Desk {

        @Expr(value = "${p0 > 0}", errorCode = "desk.id")
        public Desk(int id) {
            super();
        }

        @Expr(value = "${p1 > p0}", errorCode = "desk.order")
        public void book(int start, int end) {
        }

        @Expr(value = "${ctx['$returnValue'].length() > 0}", errorCode = "desk.label", applyTo = ConstraintTarget.RETURN_VALUE)
        public String label(String prefix) {
            return prefix;
        }
    }
}
