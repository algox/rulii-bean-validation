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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;
import org.rulii.script.Script;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.Bind;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.ExprDefaults;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.assembly.BindEvaluationException;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.PlannedBind;
import org.rulii.validation.beans.model.BindDeclarationException;
import org.rulii.validation.beans.model.BindDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.rulii.model.action.Actions.action;
import static org.rulii.model.condition.Conditions.condition;

/**
 * {@code @Bind} end to end: literals converted and expressions compiled at the first use of the class,
 * bound beside the properties or the arguments, read by {@code @Validate} rules and {@code @Expr}
 * scripts, lazily and once per scope; on classes, hierarchies, cascades, methods and constructors.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BindValidationTest {

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

    /**
     * One registered rule, parameterised per site: {@code seats} may not exceed {@code limit}.
     */
    private static final RuleRegistry REGISTRY = RuleRegistry.builder()
            .register(Rule.builder().name("withinLimit")
                    .given(condition((Integer seats, Integer limit) -> seats == null || seats <= limit))
                    .otherwise(action((RuleViolations ruleViolations) -> ruleViolations.add("withinLimit", "seats.limit")))
                    .build())
            // The same check for an executable's first argument, bound as p0 by the positional name provider.
            .register(Rule.builder().name("withinArgumentLimit")
                    .given(condition((Integer p0, Integer limit) -> p0 == null || p0 <= limit))
                    .otherwise(action((RuleViolations ruleViolations) -> ruleViolations.add("withinArgumentLimit", "seats.limit")))
                    .build())
            .build();

    public BindValidationTest() {
        super();
    }

    private static BeanValidator validator() {
        return BeanValidator.builder().registry(REGISTRY).parameterNameProvider(POSITIONAL).build();
    }

    private static List<String> report(Iterable<? extends ConstraintViolation<?>> violations) {
        List<String> result = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath() + ":" + ((RuleViolation) violation).getRuleName());
        }
        return result;
    }

    private static Throwable find(Throwable thrown, Class<? extends Throwable> type) {
        Throwable cause = thrown;
        while (cause != null && !type.isInstance(cause)) {
            cause = cause.getCause();
        }
        return cause;
    }

    @Test
    public void oneRuleParameterisedPerSite() {
        BeanValidator validator = validator();
        Small small = new Small();
        small.seats = 3;
        Large large = new Large();
        large.seats = 3;

        assertEquals(List.of(":withinLimit"), report(validator.validate(small).getViolations()));
        assertTrue(validator.validate(large).isEmpty());

        small.seats = 2;
        assertTrue(validator.validate(small).isEmpty());
        large.seats = 501;
        assertEquals(List.of(":withinLimit"), report(validator.validate(large).getViolations()));

        // The assembled view: one planned binding, converted once.
        List<PlannedBind> binds = validator.getBeanRules(Small.class).getBindingPlan().getBinds();
        assertEquals(1, binds.size());
        assertEquals("limit", binds.get(0).getName());
        assertEquals(Integer.class, binds.get(0).getType());
        assertTrue(binds.get(0).isConstant());
        assertEquals(2, binds.get(0).getConstant());
        assertInstanceOf(BindDefinition.class, binds.get(0).getDefinition());
        assertTrue(binds.get(0).toString().contains("limit : Integer = 2"), binds.get(0).toString());
    }

    @Test
    public void literalsAreStringsUnlessTypedAndExpressionsSeeSiblingsAndInheritedBinds() {
        BeanValidator validator = validator();
        Labelled labelled = new Labelled();

        assertTrue(validator.validate(labelled).isEmpty(), "label is the String 'x', twice is base * 2 = 20");
        List<PlannedBind> binds = validator.getBeanRules(Labelled.class).getBindingPlan().getBinds();
        assertEquals(String.class, binds.get(0).getType());
        assertEquals(Object.class, binds.get(2).getType(), "an untyped expression is bound as Object");
        assertFalse(binds.get(2).isConstant());
        assertNotNull(binds.get(2).getScript());

        // The child inherits base from Labelled and declares thrice from it; the child's own @Expr reads both.
        assertTrue(validator.validate(new Tripled()).isEmpty());
        assertEquals(List.of("label", "base", "twice", "thrice"), names(validator.getBeanRules(Tripled.class).getBindingPlan().getBinds()));
    }

    @Test
    public void expressionsAreLazyAndEvaluatedOncePerScope() {
        BeanValidator validator = validator();
        Counter counter = new Counter();
        RuleContext context = context(counter);

        // Two scripts read the binding; the expression ran once.
        assertTrue(validator.validateIn(context, new Ticking()).isEmpty());
        assertEquals(1, counter.reads.get());

        // A second validation is a new scope: evaluated again, once.
        validator.validateIn(context, new Ticking());
        assertEquals(2, counter.reads.get());

        // Nothing reads it: never evaluated.
        validator.validateIn(context, new Idle());
        assertEquals(2, counter.reads.get());
    }

    @Test
    public void aCycleBetweenBindsFailsNamingTheChain() {
        BeanValidator validator = validator();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> validator.validate(new Circular()));
        BindEvaluationException cycle = (BindEvaluationException) find(thrown, BindEvaluationException.class);
        assertNotNull(cycle, "the cycle is in the cause chain of: " + thrown);
        assertEquals("a", cycle.getBindingName());
        assertTrue(cycle.getMessage().contains("reads itself: a -> b -> a"), cycle.getMessage());
    }

    @Test
    public void anExpressionResultIsCheckedAgainstTheDeclaredType() {
        BeanValidator validator = validator();
        BindDefinition definition = validator.getBeanRules(Small.class).getDefinition().getBinds().get(0);
        Script<?> script = Script.builder().with("jakarta-el", "${'abc'}").returnType(Object.class).build();
        PlannedBind bind = PlannedBind.script(definition, Integer.class, script);
        ScopedBindings bindings = Bindings.builder().scoped();
        RuleContext context = RuleContext.builder().with(bindings).build();

        BindingPlan plan = BindingPlan.of(validator.getBeanRules(Small.class).getDefinition(), List.of(bind));
        plan.bind(context);

        BindEvaluationException mismatch = assertThrows(BindEvaluationException.class, () -> bindings.getValue("limit"));
        assertTrue(mismatch.getMessage().contains("evaluated to [abc] of type [java.lang.String], not the declared type [java.lang.Integer]"), mismatch.getMessage());
    }

    @Test
    public void aCascadedChildSeesItsParentsBindsAndTheObjectsBindWinsOverTheCallers() {
        BeanValidator validator = validator();
        Parent parent = new Parent();
        parent.child = new Child();

        // The child's @Expr reads limit, bound by the parent's scope.
        assertTrue(validator.validate(parent).isEmpty());

        // The caller binds limit = 100; the object's own binding is in the inner scope and wins.
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind("limit", Integer.class, 100);
        RuleContext context = RuleContext.builder().with(bindings).registry(REGISTRY).build();
        Small small = new Small();
        small.seats = 3;
        assertEquals(List.of(":withinLimit"), report(((BeanRuleViolations) validator.validateIn(context, small)).getBeanViolations()));
        assertEquals(100, (Integer) bindings.getValue("limit"), "the caller's binding is untouched");
    }

    @Test
    public void executablesBindBesideTheArgumentsOrTheReturnValue() throws Exception {
        BeanValidator validator = validator();
        Desk desk = new Desk(1);
        Method book = Desk.class.getMethod("book", Integer.class);
        Method label = Desk.class.getMethod("label", String.class);
        Constructor<Desk> constructor = Desk.class.getConstructor(Integer.class);

        assertEquals(List.of("book.<cross-parameter>:withinArgumentLimit"), report(validator.forExecutables().validateParameters(desk, book, new Object[] {3})));
        assertTrue(validator.forExecutables().validateParameters(desk, book, new Object[] {1}).isEmpty());

        assertTrue(validator.forExecutables().validateReturnValue(desk, label, "t").isEmpty());
        assertEquals(List.of("label.<return value>:returnValue_expr"), report(validator.forExecutables().validateReturnValue(desk, label, "u")));

        assertEquals(List.of("Desk.<cross-parameter>:withinArgumentLimit"), report(validator.forExecutables().validateConstructorParameters(constructor, new Object[] {5})));
        assertTrue(validator.forExecutables().validateConstructorParameters(constructor, new Object[] {4}).isEmpty());

        assertEquals(List.of("limit"), names(validator.getExecutableRules(Desk.class, book).getBindingPlan().getParameterBinds()));
        assertEquals(List.of("tag"), names(validator.getExecutableRules(Desk.class, label).getBindingPlan().getReturnValueBinds()));
    }

    @Test
    public void declarationErrorsAtTheFirstUse() {
        BeanValidator validator = validator();

        BindDeclarationException conversion = assertThrows(BindDeclarationException.class, () -> validator.validate(new NotANumber()));
        assertTrue(conversion.getMessage().contains("declares the literal [ten], which does not convert to [java.lang.Integer]"), conversion.getMessage());
        assertEquals("limit", conversion.getBindingName());
        assertNotNull(conversion.getCause());

        BindDeclarationException converter = assertThrows(BindDeclarationException.class, () -> validator.validate(new Unconvertible()));
        assertTrue(converter.getMessage().contains("no converter turns a String into it"), converter.getMessage());

        BindDeclarationException language = assertThrows(BindDeclarationException.class, () -> validator.validate(new NoLanguage()));
        assertTrue(language.getMessage().contains("@Bind [x] on [" + NoLanguage.class.getName() + "] names no scripting language"), language.getMessage());
        assertTrue(language.getMessage().contains("jakarta-el"), "lists the available languages: " + language.getMessage());

        BindDeclarationException syntax = assertThrows(BindDeclarationException.class, () -> validator.validate(new Broken()));
        assertTrue(syntax.getMessage().contains("does not compile in [jakarta-el]"), syntax.getMessage());
        assertNotNull(syntax.getCause());

        BindDeclarationException reserved = assertThrows(BindDeclarationException.class, () -> validator.validate(new Reserved()));
        assertTrue(reserved.getMessage().contains("@Bind [$this] on [" + Reserved.class.getName() + "] takes a name reserved"), reserved.getMessage());
        assertTrue(reserved.getMessage().contains("ruleViolations"), "lists the reserved names: " + reserved.getMessage());
    }

    private static List<String> names(List<PlannedBind> binds) {
        List<String> result = new ArrayList<>(binds.size());
        for (PlannedBind bind : binds) {
            result.add(bind.getName());
        }
        return result;
    }

    private static RuleContext context(Counter counter) {
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind("counter", Counter.class, counter);
        return RuleContext.builder().with(bindings).registry(REGISTRY).build();
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    @Validate("withinLimit")
    @Bind(name = "limit", value = "2", type = Integer.class)
    public static class Small {
        @NotNull
        public Integer seats = 0;
    }

    @Validate("withinLimit")
    @Bind(name = "limit", value = "500", type = Integer.class)
    public static class Large {
        @NotNull
        public Integer seats = 0;
    }

    @ExprDefaults(lang = "jakarta-el")
    @Bind(name = "label", value = "x")
    @Bind(name = "base", value = "10", type = Integer.class)
    @Bind(name = "twice", expr = "${base * 2}")
    @Expr(value = "${label == 'x' && twice == 20}", errorCode = "labelled.binds")
    public static class Labelled {
        @NotNull
        public String code = "c";
    }

    @ExprDefaults(lang = "jakarta-el")
    @Bind(name = "thrice", expr = "${base * 3}")
    @Expr(value = "${thrice == 30 && twice == 20}", errorCode = "tripled.binds")
    public static class Tripled extends Labelled {}

    public static class Counter {
        final AtomicInteger reads = new AtomicInteger();

        public int next() {
            return reads.incrementAndGet();
        }
    }

    @ExprDefaults(lang = "jakarta-el")
    @Bind(name = "tick", expr = "${counter.next()}")
    @Expr(value = "${tick > 0}", errorCode = "tick.first")
    @Expr(value = "${tick > 0}", errorCode = "tick.second")
    public static class Ticking {}

    @ExprDefaults(lang = "jakarta-el")
    @Bind(name = "tick", expr = "${counter.next()}")
    @Expr(value = "${true}", errorCode = "idle")
    public static class Idle {}

    @ExprDefaults(lang = "jakarta-el")
    @Bind(name = "a", expr = "${b}")
    @Bind(name = "b", expr = "${a}")
    @Expr(value = "${a == null}", errorCode = "circular")
    public static class Circular {}

    @Bind(name = "limit", value = "2", type = Integer.class)
    public static class Parent {
        @Valid
        public Child child;
    }

    @ExprDefaults(lang = "jakarta-el")
    @Expr(value = "${limit == 2}", errorCode = "child.limit")
    public static class Child {}

    public static class Desk {

        @Validate("withinArgumentLimit")
        @Bind(name = "limit", value = "4", type = Integer.class)
        public Desk(Integer seats) {
            super();
        }

        @Validate("withinArgumentLimit")
        @Bind(name = "limit", value = "1", type = Integer.class)
        public void book(@NotNull Integer seats) {
        }

        @Bind(name = "tag", value = "t", applyTo = ConstraintTarget.RETURN_VALUE)
        @Expr(value = "${ctx['$returnValue'] == tag}", lang = "jakarta-el", errorCode = "label.tag", applyTo = ConstraintTarget.RETURN_VALUE)
        public String label(String prefix) {
            return prefix;
        }
    }

    @Bind(name = "limit", value = "ten", type = Integer.class)
    public static class NotANumber {
        @NotNull
        public String code = "c";
    }

    @Bind(name = "limit", value = "1", type = AtomicInteger.class)
    public static class Unconvertible {
        @NotNull
        public String code = "c";
    }

    @Bind(name = "x", expr = "1")
    public static class NoLanguage {
        @NotNull
        public String code = "c";
    }

    @Bind(name = "x", expr = "${", lang = "jakarta-el")
    public static class Broken {
        @NotNull
        public String code = "c";
    }

    @Bind(name = "$this", value = "1")
    public static class Reserved {
        @NotNull
        public String code = "c";
    }
}
