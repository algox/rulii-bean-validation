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

import jakarta.validation.Constraint;
import jakarta.validation.GroupSequence;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.Bind;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ClassConstraintSource;
import org.rulii.validation.beans.model.ConstraintDefinition;
import jakarta.validation.GroupDefinitionException;
import org.rulii.validation.beans.model.ValueDeclaration.Kind;
import org.rulii.validation.beans.model.PropertyDefinition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BeanDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanDefinitionTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    public BeanDefinitionTest() {
        super();
    }

    @Test
    public void simpleBean() {
        BeanDefinition bean = BeanDefinition.load(Simple.class, REGISTRY);

        assertEquals(Simple.class, bean.getBeanClass());
        assertEquals("Simple", bean.getName());
        assertEquals(Simple.class.getName(), bean.getSource().getClassName());
        assertEquals(List.of("name", "age", "label"), names(bean));
        assertEquals(List.of(), bean.getClassConstraints());
        assertTrue(bean.isConstrained());
        assertFalse(bean.hasCascades());

        PropertyDefinition name = bean.getProperty("name");
        assertNotNull(name);
        assertSame(name, bean.getProperties().get(0));
        assertEquals(2, name.getConstraints().size());
        assertEquals(Kind.GETTER, name.getPropertySource().getPrimary().getKind());
        assertNull(bean.getProperty("missing"));
        assertThrows(IllegalArgumentException.class, () -> bean.getProperty(null));
        assertThrows(UnsupportedOperationException.class, () -> bean.getProperties().clear());
        assertTrue(bean.toString().contains(Simple.class.getName()));
    }

    @Test
    public void emptyBeanHasNothing() {
        BeanDefinition bean = BeanDefinition.load(Empty.class, REGISTRY);
        assertEquals(List.of(), bean.getProperties());
        assertEquals(List.of(), bean.getClassConstraints());
        assertFalse(bean.isConstrained());
        assertFalse(bean.hasCascades());
    }

    @Test
    public void unconstrainedPropertiesAreStillProperties() {
        BeanDefinition bean = BeanDefinition.load(Plain.class, REGISTRY);
        assertEquals(List.of("value"), names(bean));
        assertFalse(bean.isConstrained());
    }

    @Test
    public void classLevelConstraintsFollowTheHierarchyOrder() {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        List<ConstraintDefinition> constraints = bean.getClassConstraints();

        // Parent (class chain top-down), Child, then the interfaces: Audited on Parent first, then Tagged on Child.
        assertEquals(List.of(Parent.class, Child.class, Audited.class, Tagged.class),
                constraints.stream().<Class<?>>map(c -> c.getConstraintSource().getDeclaringClass()).toList());
        assertEquals(List.of(ValidBean.class, ValidBean.class, ValidBean.class, ValidBean.class),
                constraints.stream().<Class<?>>map(ConstraintDefinition::getAnnotationType).toList());
        assertEquals(List.of("parent", "child", "audited", "tagged"),
                constraints.stream().map(ConstraintDefinition::getMessage).toList());
        assertEquals(new ClassConstraintSource(Parent.class), constraints.get(0).getConstraintSource());
        assertEquals(Child.class.getName(), constraints.get(1).getSource().getClassName());
        assertTrue(bean.isConstrained());
        assertThrows(UnsupportedOperationException.class, constraints::clear);
    }

    @Test
    public void propertiesComeFromTheWholeHierarchy() throws Exception {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        assertEquals(List.of("name", "code", "tag"), names(bean));

        PropertyDefinition name = bean.getProperty("name");
        // Parent.name field, Child.name field, Parent.getName, Audited.getName.
        assertEquals(4, name.getPropertySource().getDeclarations().size());
        assertEquals(Parent.class.getDeclaredMethod("getName"), name.getPropertySource().getMember());
        assertEquals(List.of(NotNull.class, Size.class, NotBlank.class),
                name.getConstraints().stream().<Class<?>>map(ConstraintDefinition::getAnnotationType).toList());

        PropertyDefinition tag = bean.getProperty("tag");
        assertEquals(Tagged.class.getDeclaredMethod("getTag"), tag.getPropertySource().getMember());
        assertTrue(tag.isConstrained());
    }

    @Test
    public void cascadesAreDetectedOnPropertiesAndContainerElements() {
        assertTrue(BeanDefinition.load(CascadesProperty.class, REGISTRY).hasCascades());
        assertTrue(BeanDefinition.load(CascadesElement.class, REGISTRY).hasCascades());
        assertTrue(BeanDefinition.load(CascadesNestedElement.class, REGISTRY).hasCascades());
        assertTrue(BeanDefinition.load(CascadesProperty.class, REGISTRY).isConstrained());
        assertFalse(BeanDefinition.load(Simple.class, REGISTRY).hasCascades());
    }

    @Test
    public void recordBean() {
        BeanDefinition bean = BeanDefinition.load(Customer.class, REGISTRY);
        assertEquals(List.of("name", "age"), names(bean));
        assertEquals(Kind.RECORD_COMPONENT, bean.getProperty("name").getPropertySource().getPrimary().getKind());
        assertEquals(1, bean.getClassConstraints().size());
        assertEquals(new ClassConstraintSource(Customer.class), bean.getClassConstraints().get(0).getConstraintSource());
        assertTrue(bean.isConstrained());
    }

    // --- fail-fast ----------------------------------------------------------------------------

    @Test
    public void groupSequenceListingDefaultIsIllDefined() {
        GroupDefinitionException e = assertThrows(GroupDefinitionException.class, () -> BeanDefinition.load(Sequenced.class, REGISTRY));
        assertTrue(e.getMessage().contains(Sequenced.class.getName()));
        assertTrue(e.getMessage().contains("Default"));
    }

    @Test
    public void groupSequenceOnASuperclassIsNotInherited() {
        assertNull(BeanDefinition.load(SubOfSequenced.class, REGISTRY).getDefaultGroupSequence());
    }

    @Test
    public void groupSequenceInterfaceImplementedByTheBeanIsOnlyAGroup() {
        // A sequence interface defines a group; it does not redefine the Default group of the bean.
        BeanDefinition bean = assertDoesNotThrow(() -> BeanDefinition.load(ImplementsSequence.class, REGISTRY));
        assertEquals(List.of("name"), names(bean));
    }

    @Test
    public void constrainedNonGetterIsAMethodDefinition() {
        BeanDefinition bean = BeanDefinition.load(ConstrainedMethod.class, REGISTRY);

        assertEquals(List.of(), names(bean));
        assertEquals(1, bean.getMethods().size());
        assertNotNull(bean.getMethod("compute"));
        assertEquals(1, bean.getMethod("compute").getReturnValue().getConstraints().size());
        assertEquals(NotNull.class, bean.getMethod("compute").getReturnValue().getConstraints().get(0).getAnnotationType());
        assertFalse(bean.isConstrained(), "executable constraints do not make the bean constrained");
    }

    @Test
    public void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BeanDefinition.load(null, REGISTRY));
        assertThrows(IllegalArgumentException.class, () -> BeanDefinition.load(Simple.class, null));
        assertThrows(IllegalArgumentException.class, () -> BeanDefinition.load(int[].class, REGISTRY));
    }

    @Test
    public void candidatesDeclareSomethingTheLoaderReads() {
        // Every declaration site the loader reads.
        assertTrue(BeanDefinition.isCandidate(Customer.class), "class-level constraint and record components");
        assertTrue(BeanDefinition.isCandidate(Sequenced.class), "field constraint");
        assertTrue(BeanDefinition.isCandidate(Simple.class), "getter constraint");
        assertTrue(BeanDefinition.isCandidate(ConstrainedMethod.class), "return value constraint");
        assertTrue(BeanDefinition.isCandidate(ConstrainedParameter.class), "parameter constraint");
        assertTrue(BeanDefinition.isCandidate(ConstrainedConstructor.class), "constructor parameter constraint");
        assertTrue(BeanDefinition.isCandidate(CrossParameter.class), "cross-parameter constraint on the method");
        assertTrue(BeanDefinition.isCandidate(CascadesProperty.class), "@Valid alone");
        assertTrue(BeanDefinition.isCandidate(CascadesElement.class), "type-use @Valid on a container element");
        assertTrue(BeanDefinition.isCandidate(CascadesNestedElement.class), "nested container element");
        assertTrue(BeanDefinition.isCandidate(ConstrainedElement.class), "type-use constraint on a container element");
        assertTrue(BeanDefinition.isCandidate(ConstrainedWildcard.class), "type-use constraint on a wildcard bound");
        assertTrue(BeanDefinition.isCandidate(ConstrainedParameterElement.class), "type-use constraint on a parameter's element");
        assertTrue(BeanDefinition.isCandidate(OnlyValidate.class), "@Validate alone");
        assertTrue(BeanDefinition.isCandidate(OnlyExpr.class), "@Expr alone");
        assertTrue(BeanDefinition.isCandidate(OnlyBind.class), "@Bind alone");
        assertTrue(BeanDefinition.isCandidate(ComposedOnly.class), "a composed constraint is a constraint");

        // Inherited declarations count: from a superclass and from an interface.
        assertTrue(BeanDefinition.isCandidate(SubOfSequenced.class), "field constraint on the superclass");
        assertTrue(BeanDefinition.isCandidate(ImplementsAudited.class), "getter constraint on the interface");
        assertTrue(BeanDefinition.isCandidate(Audited.class), "the interface itself");

        // Nothing to read.
        assertFalse(BeanDefinition.isCandidate(Empty.class));
        assertFalse(BeanDefinition.isCandidate(Plain.class));
        assertFalse(BeanDefinition.isCandidate(UnrelatedAnnotations.class), "annotations that are not constraints");
        assertFalse(BeanDefinition.isCandidate(Strict.class), "a group interface");
        assertFalse(BeanDefinition.isCandidate(String.class));
        assertFalse(BeanDefinition.isCandidate(int.class));
        assertFalse(BeanDefinition.isCandidate(int[].class));
        assertThrows(IllegalArgumentException.class, () -> BeanDefinition.isCandidate(null));

        // The answer agrees with a loaded definition.
        assertEquals(BeanDefinition.isCandidate(Simple.class), BeanDefinition.load(Simple.class, REGISTRY).isConstrained());
        assertEquals(BeanDefinition.isCandidate(CascadesElement.class), BeanDefinition.load(CascadesElement.class, REGISTRY).hasCascades());
        assertFalse(BeanDefinition.load(Plain.class, REGISTRY).isConstrained());
    }

    private static List<String> names(BeanDefinition bean) {
        return bean.getProperties().stream().map(PropertyDefinition::getName).toList();
    }

    // --- fixtures -----------------------------------------------------------------------------

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @interface ValidBean {
        String message() default "invalid bean";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @SuppressWarnings("unused")
    private static class Simple {
        @NotNull private String name;
        @Min(0) private int age;
        @Size(max = 10) public String getName() { return name; }
        @NotBlank public String getLabel() { return name; }
    }

    private static class Empty { }

    @SuppressWarnings("unused")
    private static class Plain { private String value; }

    @ValidBean(message = "audited")
    private interface Audited { @NotBlank String getName(); }

    @ValidBean(message = "tagged")
    private interface Tagged { @NotNull default String getTag() { return "t"; } }

    @ValidBean(message = "parent")
    @SuppressWarnings("unused")
    private static class Parent implements Audited {
        @NotNull private String name;
        private String code;
        @Override public String getName() { return name; }
    }

    @ValidBean(message = "child")
    @SuppressWarnings("unused")
    private static class Child extends Parent implements Tagged {
        @Size(max = 5) private String name;
    }

    @SuppressWarnings("unused")
    private static class CascadesProperty { @Valid private Plain child; }

    @SuppressWarnings("unused")
    private static class CascadesElement { private List<@Valid Plain> children; }

    @SuppressWarnings("unused")
    private static class CascadesNestedElement { private List<List<@Valid Plain>> children; }

    @ValidBean
    @SuppressWarnings("unused")
    private record Customer(@NotBlank String name, @Min(18) int age) {}

    @GroupSequence({Default.class, Sequenced.class})
    @SuppressWarnings("unused")
    private static class Sequenced { @NotNull private String name; }

    private static class SubOfSequenced extends Sequenced { }

    private interface Strict { }

    @GroupSequence({Default.class, Strict.class})
    private interface Complete { }

    @SuppressWarnings("unused")
    private static class ImplementsSequence implements Complete { @NotNull private String name; }

    @SuppressWarnings("unused")
    private static class ConstrainedMethod { @NotNull public Object compute() { return null; } }

    @SuppressWarnings("unused")
    private static class ConstrainedParameter { public void take(@NotNull Object value) { } }

    @SuppressWarnings("unused")
    private static class ConstrainedConstructor { public ConstrainedConstructor(@NotNull Object value) { super(); } }

    @Constraint(validatedBy = {})
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Consistent {
        String message() default "inconsistent";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @SuppressWarnings("unused")
    private static class CrossParameter { @Consistent public void take(Object first, Object second) { } }

    @SuppressWarnings("unused")
    private static class ConstrainedElement { private List<@NotBlank String> names; }

    @SuppressWarnings("unused")
    private static class ConstrainedWildcard { private List<? extends @NotNull Object> values; }

    @SuppressWarnings("unused")
    private static class ConstrainedParameterElement { public void take(List<@NotBlank String> names) { } }

    @Validate("someRule")
    private static class OnlyValidate { }

    @Expr(value = "true", errorCode = "always")
    private static class OnlyExpr { }

    @Bind(name = "limit", value = "10")
    private static class OnlyBind { }

    @NotBlank
    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Composed {
        String message() default "composed";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @SuppressWarnings("unused")
    private static class ComposedOnly { @Composed private String name; }

    @SuppressWarnings("unused")
    private static class ImplementsAudited implements Audited { public String getName() { return null; } }

    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Note { }

    @Note
    @SuppressWarnings("unused")
    private static class UnrelatedAnnotations {
        @Note private List<@Note String> names;
        @Note public String getLabel() { return null; }
        @Deprecated public void take(@Note Object value) { }
    }
}
