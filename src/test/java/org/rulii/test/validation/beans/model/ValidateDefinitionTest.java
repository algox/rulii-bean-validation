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

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ClassConstraintSource;
import org.rulii.validation.beans.model.ConstraintOnNonGetterException;
import org.rulii.validation.beans.model.PropertyConstraintSource;
import org.rulii.validation.beans.model.PropertyDeclaration;
import org.rulii.validation.beans.model.PropertyDeclaration.Kind;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.ValidateDeclarationException;
import org.rulii.validation.beans.model.ValidateDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ValidateDefinition} and its reading into {@link PropertyDefinition} and
 * {@link BeanDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValidateDefinitionTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    public ValidateDefinitionTest() {
        super();
    }

    @Test
    public void fieldValidate() throws Exception {
        BeanDefinition bean = BeanDefinition.load(OnField.class, REGISTRY);
        PropertyDefinition property = bean.getProperty("code");

        assertEquals(1, property.getValidateRules().size());
        ValidateDefinition definition = property.getValidateRules().get(0);

        assertEquals(List.of("codeRules"), definition.getRuleNames());
        assertEquals("Validate", definition.getName());
        assertEquals("codeRules", definition.getAnnotation().value()[0]);
        assertSame(OnField.class.getDeclaredField("code").getAnnotation(Validate.class), definition.getAnnotation());
        assertEquals(OnField.class.getName(), definition.getSource().getClassName());

        PropertyConstraintSource source = assertInstanceOf(PropertyConstraintSource.class, definition.getDeclarationSource());
        assertEquals(Kind.FIELD, source.getDeclaration().getKind());
        assertEquals(OnField.class, source.getDeclaringClass());
        assertTrue(definition.toString().contains("codeRules"));
        assertTrue(definition.toString().contains(OnField.class.getName()));

        // @Validate alone makes the property, and the bean, constrained.
        assertEquals(List.of(), property.getConstraints());
        assertTrue(property.isConstrained());
        assertTrue(bean.isConstrained());
        assertEquals(List.of(), bean.getValidateRules());
        assertTrue(property.toString().contains("codeRules"));
        assertThrows(UnsupportedOperationException.class, () -> property.getValidateRules().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.getRuleNames().clear());
    }

    @Test
    public void fieldThenGetterOrder() {
        BeanDefinition bean = BeanDefinition.load(FieldAndGetter.class, REGISTRY);
        List<ValidateDefinition> rules = bean.getProperty("name").getValidateRules();

        assertEquals(2, rules.size());
        assertEquals(List.of("fieldRule"), rules.get(0).getRuleNames());
        assertEquals(List.of("getterRule1", "getterRule2"), rules.get(1).getRuleNames());
        assertEquals(Kind.FIELD, ((PropertyConstraintSource) rules.get(0).getDeclarationSource()).getDeclaration().getKind());
        assertEquals(Kind.GETTER, ((PropertyConstraintSource) rules.get(1).getDeclarationSource()).getDeclaration().getKind());
    }

    @Test
    public void propertyRulesFollowTheHierarchy() {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        List<ValidateDefinition> rules = bean.getProperty("code").getValidateRules();

        // Parent field, then Child getter.
        assertEquals(List.of(List.of("parentCode"), List.of("childCode")),
                rules.stream().map(ValidateDefinition::getRuleNames).toList());
        assertEquals(List.of(Parent.class, Child.class),
                rules.stream().<Class<?>>map(r -> r.getDeclarationSource().getDeclaringClass()).toList());
    }

    @Test
    public void classLevelRulesFollowTheHierarchy() {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        List<ValidateDefinition> rules = bean.getValidateRules();

        // Parent (class chain top-down), Child, then the interface.
        assertEquals(List.of(Parent.class, Child.class, Audited.class),
                rules.stream().<Class<?>>map(r -> r.getDeclarationSource().getDeclaringClass()).toList());
        assertEquals(List.of(List.of("parentRules"), List.of("childRules1", "childRules2"), List.of("auditRules")),
                rules.stream().map(ValidateDefinition::getRuleNames).toList());
        assertInstanceOf(ClassConstraintSource.class, rules.get(0).getDeclarationSource());
        assertEquals(List.of(), bean.getClassConstraints());
        assertTrue(bean.isConstrained());
        assertTrue(bean.toString().contains("validateRules=3"));
        assertThrows(UnsupportedOperationException.class, () -> bean.getValidateRules().clear());
    }

    @Test
    public void classLevelOnlyMakesTheBeanConstrained() {
        BeanDefinition bean = BeanDefinition.load(ClassOnly.class, REGISTRY);
        assertEquals(List.of(List.of("whole")), bean.getValidateRules().stream().map(ValidateDefinition::getRuleNames).toList());
        assertFalse(bean.getProperty("value").isConstrained());
        assertTrue(bean.isConstrained());
    }

    @Test
    public void recordComponent() {
        BeanDefinition bean = BeanDefinition.load(Rec.class, REGISTRY);
        List<ValidateDefinition> rules = bean.getProperty("name").getValidateRules();

        // Mirrored onto the backing field and the component; read once.
        assertEquals(1, rules.size());
        assertEquals(List.of("recordRule"), rules.get(0).getRuleNames());
        assertEquals(Kind.RECORD_COMPONENT, ((PropertyConstraintSource) rules.get(0).getDeclarationSource()).getDeclaration().getKind());
        assertEquals(1, bean.getProperty("name").getConstraints().size());
    }

    @Test
    public void directLoad() throws Exception {
        PropertyDeclaration declaration = PropertyDeclaration.ofField(OnField.class.getDeclaredField("code"));
        List<ValidateDefinition> rules = ValidateDefinition.load(declaration);
        assertEquals(1, rules.size());
        assertEquals(List.of("codeRules"), rules.get(0).getRuleNames());
        assertEquals(rules, ValidateDefinition.load(declaration));
        assertEquals(rules.get(0).hashCode(), ValidateDefinition.load(declaration).get(0).hashCode());

        assertEquals(List.of(), ValidateDefinition.load(PropertyDeclaration.ofField(OnField.class.getDeclaredField("other"))));
        assertEquals(List.of(), ValidateDefinition.load(OnField.class));
        assertEquals(1, ValidateDefinition.load(ClassOnly.class).size());

        ValidateDefinition direct = ValidateDefinition.load(new ClassConstraintSource(ClassOnly.class),
                ClassOnly.class.getAnnotation(Validate.class));
        assertEquals(ValidateDefinition.load(ClassOnly.class).get(0), direct);
        assertNotEquals(direct, rules.get(0));

        assertThrows(IllegalArgumentException.class, () -> ValidateDefinition.load((PropertyDeclaration) null));
        assertThrows(IllegalArgumentException.class, () -> ValidateDefinition.load((Class<?>) null));
        assertThrows(IllegalArgumentException.class, () -> ValidateDefinition.load(null, ClassOnly.class.getAnnotation(Validate.class)));
        assertThrows(IllegalArgumentException.class, () -> ValidateDefinition.load(new ClassConstraintSource(ClassOnly.class), null));
    }

    @Test
    public void emptyNamesFailFast() {
        ValidateDeclarationException e = assertThrows(ValidateDeclarationException.class,
                () -> BeanDefinition.load(EmptyNames.class, REGISTRY));
        assertNull(e.getRuleName());
        assertEquals(EmptyNames.class, e.getSource().getDeclaringClass());
        assertTrue(e.getMessage().contains("no rule name"));
        assertTrue(e.getMessage().contains(EmptyNames.class.getName() + ".code"));
    }

    @Test
    public void blankNameFailsFast() {
        ValidateDeclarationException e = assertThrows(ValidateDeclarationException.class,
                () -> BeanDefinition.load(BlankName.class, REGISTRY));
        assertEquals(" ", e.getRuleName());
        assertTrue(e.getMessage().contains("blank"));
    }

    @Test
    public void duplicateWithinOneAnnotationFailsFast() {
        ValidateDeclarationException e = assertThrows(ValidateDeclarationException.class,
                () -> BeanDefinition.load(DuplicateInOne.class, REGISTRY));
        assertEquals("twice", e.getRuleName());
        assertEquals(DuplicateInOne.class, e.getSource().getDeclaringClass());
        assertTrue(e.getMessage().contains("more than once"));
    }

    @Test
    public void duplicateAcrossDeclarationsOfOnePropertyFailsFast() {
        ValidateDeclarationException e = assertThrows(ValidateDeclarationException.class,
                () -> BeanDefinition.load(DuplicateAcrossDeclarations.class, REGISTRY));
        assertEquals("shared", e.getRuleName());
        assertTrue(e.getMessage().contains("property [name]"));
        assertTrue(e.getMessage().contains(DuplicateAcrossDeclarations.class.getName() + ".name"));
        assertTrue(e.getMessage().contains(DuplicateAcrossDeclarations.class.getName() + ".getName()"));
        // The reported source is the later declaration.
        assertEquals(Kind.GETTER, ((PropertyConstraintSource) e.getSource()).getDeclaration().getKind());
    }

    @Test
    public void duplicateAcrossClassHierarchyFailsFast() {
        ValidateDeclarationException e = assertThrows(ValidateDeclarationException.class,
                () -> BeanDefinition.load(DuplicateChild.class, REGISTRY));
        assertEquals("classRule", e.getRuleName());
        assertTrue(e.getMessage().contains("class [" + DuplicateChild.class.getName() + "]"));
        assertTrue(e.getMessage().contains(DuplicateParent.class.getName()));
        assertEquals(DuplicateChild.class, e.getSource().getDeclaringClass());
    }

    @Test
    public void sameNameOnPropertyAndClassIsAllowed() {
        BeanDefinition bean = BeanDefinition.load(SameNameTwoLevels.class, REGISTRY);
        assertEquals(List.of("same"), bean.getValidateRules().get(0).getRuleNames());
        assertEquals(List.of("same"), bean.getProperty("value").getValidateRules().get(0).getRuleNames());
    }

    @Test
    public void nonGetterMethodFailsFast() {
        ConstraintOnNonGetterException e = assertThrows(ConstraintOnNonGetterException.class,
                () -> BeanDefinition.load(OnNonGetter.class, REGISTRY));
        assertEquals("check", e.getMethod().getName());
        assertInstanceOf(Validate.class, e.getAnnotation());
        assertTrue(e.getMessage().contains("@Validate"));
    }

    @Test
    public void staticMembersAreIgnored() {
        BeanDefinition bean = BeanDefinition.load(Statics.class, REGISTRY);
        assertEquals(List.of(), bean.getValidateRules());
        assertEquals(1, bean.getProperties().size());
        assertEquals(List.of(), bean.getProperty("value").getValidateRules());
        assertFalse(bean.isConstrained());
    }

    // ---- fixtures ----

    public static class OnField {
        @Validate("codeRules")
        private String code;
        private String other;
    }

    public static class FieldAndGetter {
        @Validate("fieldRule")
        private String name;

        @Validate({"getterRule1", "getterRule2"})
        public String getName() { return name; }
    }

    @Validate("auditRules")
    public interface Audited {}

    @Validate("parentRules")
    public static class Parent {
        @Validate("parentCode")
        protected String code;
    }

    @Validate({"childRules1", "childRules2"})
    public static class Child extends Parent implements Audited {
        @Validate("childCode")
        public String getCode() { return code; }
    }

    @Validate("whole")
    public static class ClassOnly {
        private String value;
    }

    public record Rec(@Validate("recordRule") @NotNull String name) {}

    public static class EmptyNames {
        @Validate({})
        private String code;
    }

    public static class BlankName {
        @Validate({"ok", " "})
        private String code;
    }

    public static class DuplicateInOne {
        @Validate({"twice", "once", "twice"})
        private String code;
    }

    public static class DuplicateAcrossDeclarations {
        @Validate("shared")
        private String name;

        @Validate({"other", "shared"})
        public String getName() { return name; }
    }

    @Validate("classRule")
    public static class DuplicateParent {}

    @Validate({"classRule"})
    public static class DuplicateChild extends DuplicateParent {}

    @Validate("same")
    public static class SameNameTwoLevels {
        @Validate("same")
        private String value;
    }

    public static class OnNonGetter {
        private String value;

        @Validate("never")
        public void check() {}
    }

    public static class Statics {
        @Validate("staticField")
        private static String constant;
        private String value;

        @Validate("staticMethod")
        public static String getConstant() { return constant; }
    }
}
