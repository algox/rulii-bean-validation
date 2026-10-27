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
package org.rulii.test.validation.beans.model;

import jakarta.validation.ConstraintTarget;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.rulii.test.validation.beans.expr.PackageDefaultsBean;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.ExprDefaults;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ClassConstraintSource;
import org.rulii.validation.beans.model.CrossParameterConstraintSource;
import org.rulii.validation.beans.model.DeclarationConstraintSource;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ExprDeclarationException;
import org.rulii.validation.beans.model.ExprDefinition;
import org.rulii.validation.beans.model.IllegalExecutableConstraintException;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.RuleDeclaration;
import org.rulii.validation.beans.model.ValueDeclaration.Kind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ExprDefinition} and its reading into {@link PropertyDefinition},
 * {@link BeanDefinition} and {@link ExecutableDefinition}. Nothing here compiles a script.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ExprDefinitionTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    public ExprDefinitionTest() {
        super();
    }

    @Test
    public void fieldExpr() throws Exception {
        BeanDefinition bean = BeanDefinition.load(Trip.class, REGISTRY);
        PropertyDefinition property = bean.getProperty("end");

        assertEquals(1, property.getExprRules().size());
        ExprDefinition definition = property.getExprRules().get(0);

        assertEquals("${end.isAfter(start)}", definition.getScript());
        assertEquals("jakarta-el", definition.getLanguage());
        assertEquals("trip.end", definition.getErrorCode());
        assertNull(definition.getMessage(), "an empty message resolves from the error code");
        assertEquals(Severity.ERROR, definition.getSeverity());
        assertEquals(List.of(), definition.getGroups());
        assertEquals(ConstraintTarget.IMPLICIT, definition.getApplyTo());
        assertTrue(definition.isSkipNull());
        assertEquals("Expr", definition.getName());
        assertEquals(Expr.class, definition.getAnnotationType());
        assertSame(Trip.class.getDeclaredField("end").getAnnotation(Expr.class), definition.getAnnotation());
        assertEquals(Trip.class.getName(), definition.getSource().getClassName());
        assertInstanceOf(RuleDeclaration.class, definition);

        DeclarationConstraintSource source = assertInstanceOf(DeclarationConstraintSource.class, definition.getConstraintSource());
        assertEquals(Kind.FIELD, source.getDeclaration().getKind());
        assertEquals(Trip.class, source.getDeclaringClass());
        assertTrue(definition.toString().contains("trip.end"));
        assertTrue(definition.toString().contains("jakarta-el: ${end.isAfter(start)}"));

        // @Expr alone makes the property, and the bean, constrained.
        assertEquals(List.of(), property.getConstraints());
        assertTrue(property.isConstrained());
        assertTrue(bean.isConstrained());
        assertEquals(List.of(), bean.getExprRules());
        assertTrue(property.toString().contains("trip.end"));
        assertTrue(bean.toString().contains("exprRules=0"));
        assertThrows(UnsupportedOperationException.class, () -> property.getExprRules().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.getGroups().clear());
    }

    @Test
    public void repeatedOnGetterWithEveryAttribute() {
        BeanDefinition bean = BeanDefinition.load(Trip.class, REGISTRY);
        List<ExprDefinition> rules = bean.getProperty("seats").getExprRules();

        assertEquals(2, rules.size());
        assertEquals("${seats > 0}", rules.get(0).getScript());
        assertEquals("seats must be positive, not ${value}", rules.get(0).getMessage());
        assertEquals(Severity.WARNING, rules.get(0).getSeverity());
        assertEquals(List.of(OnCreate.class), rules.get(0).getGroups());
        assertFalse(rules.get(0).isSkipNull());
        assertEquals("${seats < 100}", rules.get(1).getScript());
        assertEquals(Kind.GETTER, ((DeclarationConstraintSource) rules.get(1).getConstraintSource()).getDeclaration().getKind());
        assertNotEquals(rules.get(0), rules.get(1));
        assertEquals(rules.get(0), BeanDefinition.load(Trip.class, REGISTRY).getProperty("seats").getExprRules().get(0));
    }

    @Test
    public void classLevelFollowsTheHierarchy() {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        List<ExprDefinition> rules = bean.getExprRules();

        // Parent (class chain top-down), Child, then the interface.
        assertEquals(List.of(Parent.class, Child.class, Audited.class),
                rules.stream().<Class<?>>map(r -> r.getConstraintSource().getDeclaringClass()).toList());
        assertEquals(List.of("parent.whole", "child.whole", "audit.whole"), rules.stream().map(ExprDefinition::getErrorCode).toList());
        assertInstanceOf(ClassConstraintSource.class, rules.get(0).getConstraintSource());
        assertEquals(List.of(), bean.getClassConstraints());
        assertTrue(bean.isConstrained());
        assertTrue(bean.toString().contains("exprRules=3"));
        assertTrue(bean.getProperty("code").getExprRules().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> bean.getExprRules().clear());
    }

    @Test
    public void languageFromTheAnnotationThenTheClassThenThePackage() {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        // Parent declares @ExprDefaults; its own members take it, the annotation's lang wins over it.
        assertEquals("java", bean.getExprRules().get(0).getLanguage());
        assertTrue(bean.getProperty("code").getExprRules().isEmpty(), "code carries no @Expr");
        assertEquals("jakarta-el", bean.getExprRules().get(1).getLanguage(), "Child declares its own defaults");
        // The interface declares neither: this test package has no package-info, so nothing is declared.
        assertNull(bean.getExprRules().get(2).getLanguage());
        // Not inherited: a subclass without defaults of its own gets none from its parent.
        assertNull(BeanDefinition.load(Grandchild.class, REGISTRY).getProperty("name").getExprRules().get(0).getLanguage());

        // A package's @ExprDefaults (package-info) covers its classes; a class's own defaults win.
        BeanDefinition packaged = BeanDefinition.load(PackageDefaultsBean.class, REGISTRY);
        assertEquals("jakarta-el", packaged.getProperty("low").getExprRules().get(0).getLanguage());
        assertEquals("java", packaged.getProperty("high").getExprRules().get(0).getLanguage());
        assertEquals("js", BeanDefinition.load(PackageDefaultsBean.Overriding.class, REGISTRY).getProperty("value").getExprRules().get(0).getLanguage());
    }

    @Test
    public void declarationErrors() {
        assertMessage(assertThrows(ExprDeclarationException.class, () -> BeanDefinition.load(BlankScript.class, REGISTRY)), "declares no script");
        assertMessage(assertThrows(ExprDeclarationException.class, () -> BeanDefinition.load(BlankCode.class, REGISTRY)), "declares no errorCode");
        assertMessage(assertThrows(ExprDeclarationException.class, () -> BeanDefinition.load(NotAnInterfaceGroup.class, REGISTRY)), "not an interface");
        assertMessage(assertThrows(ExprDeclarationException.class, () -> BeanDefinition.load(ApplyToOnField.class, REGISTRY)), "applyTo = PARAMETERS");
        assertMessage(assertThrows(ExprDeclarationException.class, () -> BeanDefinition.load(ApplyToOnClass.class, REGISTRY)), "applyTo = RETURN_VALUE");
        ExprDeclarationException blankDefaults = assertThrows(ExprDeclarationException.class, () -> BeanDefinition.load(BlankDefaults.class, REGISTRY));
        assertMessage(blankDefaults, "@ExprDefaults on class [" + BlankDefaults.class.getName() + "] declares a blank lang");
        assertNotNull(blankDefaults.getSource());
    }

    @Test
    public void executableExpr() throws Exception {
        BeanDefinition bean = BeanDefinition.load(Desk.class, REGISTRY);

        // Parameters by default when there are any; the source is the cross-parameter one.
        ExecutableDefinition book = bean.getMethod("book", int.class, int.class);
        assertEquals(1, book.getExprRules().size());
        assertEquals("book.order", book.getExprRules().get(0).getErrorCode());
        assertInstanceOf(CrossParameterConstraintSource.class, book.getExprRules().get(0).getConstraintSource());
        assertTrue(book.hasConstrainedParameters());
        assertTrue(book.getReturnValue().getExprRules().isEmpty());
        assertTrue(book.toString().contains("exprRules=1"));

        // The return value when there are no parameters.
        ExecutableDefinition label = bean.getMethod("label");
        assertTrue(label.getExprRules().isEmpty());
        assertEquals(List.of("label.text"), label.getReturnValue().getExprRules().stream().map(ExprDefinition::getErrorCode).toList());
        assertEquals(Kind.RETURN_VALUE, ((DeclarationConstraintSource) label.getReturnValue().getExprRules().get(0).getConstraintSource()).getDeclaration().getKind());
        assertTrue(label.getReturnValue().isConstrained());
        assertTrue(label.getReturnValue().toString().contains("exprRules=1"));

        // applyTo decides for a method with both; a repeated declaration splits by applyTo.
        ExecutableDefinition move = bean.getMethod("move", int.class);
        assertEquals(List.of("move.arguments"), move.getExprRules().stream().map(ExprDefinition::getErrorCode).toList());
        assertEquals(List.of("move.result"), move.getReturnValue().getExprRules().stream().map(ExprDefinition::getErrorCode).toList());

        // A constructor's arguments.
        ExecutableDefinition constructor = bean.getConstructor(int.class);
        assertEquals(List.of("desk.id"), constructor.getExprRules().stream().map(ExprDefinition::getErrorCode).toList());

        // The target must exist.
        assertMessage(assertThrows(IllegalExecutableConstraintException.class, () -> BeanDefinition.load(ParametersOnParameterless.class, REGISTRY)),
                "@Expr(applyTo = PARAMETERS) but has no parameters");
        assertMessage(assertThrows(IllegalExecutableConstraintException.class, () -> BeanDefinition.load(ReturnValueOnVoid.class, REGISTRY)),
                "@Expr(applyTo = RETURN_VALUE) but returns void");
        assertMessage(assertThrows(IllegalExecutableConstraintException.class, () -> BeanDefinition.load(NothingToValidate.class, REGISTRY)),
                "carries @Expr but has neither parameters nor a return value");
        // An override may not add a parameter @Expr.
        assertThrows(IllegalExecutableConstraintException.class, () -> BeanDefinition.load(AddingDesk.class, REGISTRY));
    }

    private static void assertMessage(Exception e, String expected) {
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    public interface OnCreate {}

    public static class Trip {
        public String start;
        @Expr(value = "${end.isAfter(start)}", lang = "jakarta-el", errorCode = "trip.end")
        public String end;
        private int seats;

        @Expr(value = "${seats > 0}", lang = "jakarta-el", errorCode = "trip.seats", message = "seats must be positive, not ${value}",
                severity = Severity.WARNING, groups = OnCreate.class, skipNull = false)
        @Expr(value = "${seats < 100}", lang = "jakarta-el", errorCode = "trip.seats.max")
        public int getSeats() {
            return seats;
        }
    }

    @ExprDefaults(lang = "java")
    @Expr(value = "true", errorCode = "parent.whole")
    public static class Parent {
        @NotNull public String code;
    }

    @Expr(value = "true", errorCode = "audit.whole")
    public interface Audited {}

    @ExprDefaults(lang = "jakarta-el")
    @Expr(value = "${true}", errorCode = "child.whole")
    public static class Child extends Parent implements Audited {}

    public static class Grandchild extends Child {
        @Expr(value = "name != null", errorCode = "grandchild.name")
        public String name;
    }

    public static class BlankScript {
        @Expr(value = " ", errorCode = "x")
        public String value;
    }

    public static class BlankCode {
        @Expr(value = "true", errorCode = "")
        public String value;
    }

    public static class NotAnInterfaceGroup {
        @Expr(value = "true", errorCode = "x", groups = String.class)
        public String value;
    }

    public static class ApplyToOnField {
        @Expr(value = "true", errorCode = "x", applyTo = ConstraintTarget.PARAMETERS)
        public String value;
    }

    @Expr(value = "true", errorCode = "x", applyTo = ConstraintTarget.RETURN_VALUE)
    public static class ApplyToOnClass {}

    @ExprDefaults(lang = " ")
    public static class BlankDefaults {
        @Expr(value = "true", errorCode = "x")
        public String value;
    }

    public static class Desk {

        @Expr(value = "true", errorCode = "desk.id")
        public Desk(int id) {
            super();
        }

        @Expr(value = "true", errorCode = "book.order")
        public String book(int start, int end) {
            return null;
        }

        @Expr(value = "true", errorCode = "label.text")
        public String label() {
            return null;
        }

        @Expr(value = "true", errorCode = "move.arguments")
        @Expr(value = "true", errorCode = "move.result", applyTo = ConstraintTarget.RETURN_VALUE)
        public String move(int to) {
            return null;
        }
    }

    public static class ParametersOnParameterless {
        @Expr(value = "true", errorCode = "x", applyTo = ConstraintTarget.PARAMETERS)
        public String get() {
            return null;
        }
    }

    public static class ReturnValueOnVoid {
        @Expr(value = "true", errorCode = "x", applyTo = ConstraintTarget.RETURN_VALUE)
        public void run(int a) {
        }
    }

    public static class NothingToValidate {
        @Expr(value = "true", errorCode = "x")
        public void run() {
        }
    }

    public static class AddingDesk extends Desk {
        public AddingDesk() {
            super(0);
        }

        @Override
        @Expr(value = "true", errorCode = "label.more")
        public String label() {
            return null;
        }

        @Override
        @Expr(value = "true", errorCode = "book.more")
        public String book(int start, int end) {
            return null;
        }
    }
}
