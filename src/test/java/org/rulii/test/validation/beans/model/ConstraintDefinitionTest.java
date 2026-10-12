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
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.PropertyDeclaration;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConstraintDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintDefinitionTest {

    public ConstraintDefinitionTest() {
        super();
    }

    @Test
    public void noAnnotations() throws Exception {
        assertTrue(ConstraintDefinition.load(source("plain")).isEmpty());
    }

    @Test
    public void singleConstraint() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("name"));
        assertEquals(1, result.size());

        ConstraintDefinition definition = result.get(0);
        assertEquals(NotBlank.class, definition.getAnnotationType());
        assertEquals("NotBlank", definition.getName());
        assertNull(definition.getDescription());
        assertEquals(field("name"), definition.getSourceDeclaration().getMember());
    }

    @Test
    public void multipleConstraintsInDeclarationOrder() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("age"));
        assertEquals(3, result.size());
        assertEquals(NotNull.class, result.get(0).getAnnotationType());
        assertEquals(Min.class, result.get(1).getAnnotationType());
        assertEquals(Max.class, result.get(2).getAnnotationType());
        assertEquals(18, ((Min) result.get(1).getAnnotation()).value());
        assertEquals(120, ((Max) result.get(2).getAnnotation()).value());
    }

    @Test
    public void originalAnnotationInstanceIsKept() throws Exception {
        Field field = field("name");
        ConstraintDefinition definition = ConstraintDefinition.load(PropertyDeclaration.ofField(field)).get(0);
        assertSame(field.getAnnotation(NotBlank.class), definition.getAnnotation());
    }

    @Test
    public void sourceIsTheField() throws Exception {
        SourceDefinition source = ConstraintDefinition.load(source("name")).get(0).getSource();
        assertEquals(Sample.class.getName(), source.getClassName());
        assertEquals("name", source.getFieldName());
        assertNull(source.getMethodName());
    }

    @Test
    public void defaultStandardAttributes() throws Exception {
        ConstraintDefinition definition = ConstraintDefinition.load(source("name")).get(0);
        assertEquals("{jakarta.validation.constraints.NotBlank.message}", definition.getMessage());
        assertTrue(definition.getGroups().isEmpty());
        assertTrue(definition.getPayload().isEmpty());
    }

    @Test
    public void explicitStandardAttributes() throws Exception {
        ConstraintDefinition definition = ConstraintDefinition.load(source("email")).get(0);
        assertEquals("email required", definition.getMessage());
        assertEquals(List.of(OnCreate.class, OnUpdate.class), definition.getGroups());
        assertEquals(List.of(Critical.class), definition.getPayload());
        assertThrows(UnsupportedOperationException.class, () -> definition.getGroups().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.getPayload().clear());
    }

    @Test
    public void nonConstraintAnnotationsAreIgnored() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("mixed"));
        assertEquals(1, result.size());
        assertEquals(Size.class, result.get(0).getAnnotationType());
    }

    @Test
    public void validAloneIsNotAConstraint() throws Exception {
        assertTrue(ConstraintDefinition.load(source("child")).isEmpty());
    }

    @Test
    public void customConstraintIsDetected() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("code"));
        assertEquals(1, result.size());
        assertEquals(CustomCode.class, result.get(0).getAnnotationType());
        assertEquals("invalid code", result.get(0).getMessage());
    }

    @Test
    public void repeatedConstraintIsUnwrapped() throws Exception {
        // Two @Pattern compile to a single @Pattern.List container.
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("repeated"));
        assertEquals(2, result.size());
        assertEquals(Pattern.class, result.get(0).getAnnotationType());
        assertEquals(Pattern.class, result.get(1).getAnnotationType());
        assertEquals("a", ((Pattern) result.get(0).getAnnotation()).regexp());
        assertEquals("b", ((Pattern) result.get(1).getAnnotation()).regexp());
    }

    @Test
    public void explicitContainerIsUnwrapped() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("explicitList"));
        assertEquals(2, result.size());
        assertEquals("c", ((Pattern) result.get(0).getAnnotation()).regexp());
        assertEquals("d", ((Pattern) result.get(1).getAnnotation()).regexp());
    }

    @Test
    public void unwrappedConstraintsAreTheOriginalInstances() throws Exception {
        Field field = field("repeated");
        Pattern[] originals = field.getAnnotation(Pattern.List.class).value();
        List<ConstraintDefinition> result = ConstraintDefinition.load(PropertyDeclaration.ofField(field));
        assertSame(originals[0], result.get(0).getAnnotation());
        assertSame(originals[1], result.get(1).getAnnotation());
    }

    @Test
    public void containerIsUnwrappedInPlace() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("surrounded"));
        assertEquals(4, result.size());
        assertEquals(NotNull.class, result.get(0).getAnnotationType());
        assertEquals("e", ((Pattern) result.get(1).getAnnotation()).regexp());
        assertEquals("f", ((Pattern) result.get(2).getAnnotation()).regexp());
        assertEquals(Size.class, result.get(3).getAnnotationType());
    }

    @Test
    public void legacyContainerWithoutRepeatableIsUnwrapped() throws Exception {
        // Spec-defined container: value() returns an array of a constraint type,
        // but the constraint is not @Repeatable (pre-Java 8 style).
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("legacy"));
        assertEquals(2, result.size());
        assertEquals(LegacyCode.class, result.get(0).getAnnotationType());
        assertEquals("x", ((LegacyCode) result.get(0).getAnnotation()).value());
        assertEquals("y", ((LegacyCode) result.get(1).getAnnotation()).value());
        assertEquals("legacy", result.get(1).getMessage());
    }

    @Test
    public void containerOfNonConstraintsIsIgnored() throws Exception {
        assertTrue(ConstraintDefinition.load(source("tagged")).isEmpty());
    }

    @Test
    public void isConstraintContainer() throws Exception {
        assertTrue(ConstraintDefinition.isConstraintContainer(field("repeated").getAnnotation(Pattern.List.class)));
        assertTrue(ConstraintDefinition.isConstraintContainer(field("legacy").getAnnotation(LegacyCode.List.class)));
        assertFalse(ConstraintDefinition.isConstraintContainer(field("tagged").getAnnotation(Tag.List.class)));
        assertFalse(ConstraintDefinition.isConstraintContainer(field("name").getAnnotation(NotBlank.class)));
        assertFalse(ConstraintDefinition.isConstraintContainer(field("child").getAnnotation(Valid.class)));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.isConstraintContainer(null));
    }

    @Test
    public void resultIsUnmodifiable() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(source("name"));
        assertThrows(UnsupportedOperationException.class, () -> result.add(null));
    }

    @Test
    public void loadSingleRejectsNonConstraint() throws Exception {
        Field field = field("child");
        assertThrows(IllegalArgumentException.class,
                () -> ConstraintDefinition.load(PropertyDeclaration.ofField(field), field.getAnnotation(Valid.class)));
    }

    @Test
    public void malformedConstraintFailsFast() throws Exception {
        Field field = field("broken");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConstraintDefinition.load(PropertyDeclaration.ofField(field)));
        assertTrue(e.getMessage().contains("[message]"));
        assertTrue(e.getMessage().contains(MissingMessage.class.getName()));
    }

    @Test
    public void nullArgumentsAreRejected() throws Exception {
        Field field = field("name");
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load(null));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load(null, field.getAnnotation(NotBlank.class)));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load(PropertyDeclaration.ofField(field), (Annotation) null));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load(PropertyDeclaration.ofField(field), (AnnotatedElement) null));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load(null, field.getAnnotatedType()));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.isConstraint(null));
    }

    @Test
    public void isConstraint() throws Exception {
        assertTrue(ConstraintDefinition.isConstraint(field("name").getAnnotation(NotBlank.class)));
        assertFalse(ConstraintDefinition.isConstraint(field("child").getAnnotation(Valid.class)));
    }

    @Test
    public void equalityAndToString() throws Exception {
        ConstraintDefinition a = ConstraintDefinition.load(source("name")).get(0);
        ConstraintDefinition b = ConstraintDefinition.load(source("name")).get(0);
        ConstraintDefinition c = ConstraintDefinition.load(source("code")).get(0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertTrue(a.toString().contains("source=" + Sample.class.getName() + ".name"));
        assertTrue(a.toString().contains("NotBlank"));
    }

    private static PropertyDeclaration source(String name) throws NoSuchFieldException {
        return PropertyDeclaration.ofField(field(name));
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Sample.class.getDeclaredField(name);
    }

    public interface OnCreate {}
    public interface OnUpdate {}
    public static class Critical implements Payload {}

    @Documented
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomCode {
        String message() default "invalid code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /** Spec-violating constraint: no message() attribute. */
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MissingMessage {
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Unrelated {
    }

    /** Pre-Java 8 style: a hand-written container, constraint NOT marked @Repeatable. */
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LegacyCode {
        String value();
        String message() default "legacy";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            LegacyCode[] value();
        }
    }

    /** A repeatable annotation that is not a constraint; its container must be ignored. */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Repeatable(Tag.List.class)
    public @interface Tag {
        String value();

        @Target({ElementType.FIELD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            Tag[] value();
        }
    }

    @SuppressWarnings("unused")
    private static class Sample {
        private String plain;
        @NotBlank private String name;
        @NotNull @Min(18) @Max(120) private Integer age;
        @NotBlank(message = "email required", groups = {OnCreate.class, OnUpdate.class}, payload = Critical.class)
        private String email;
        @Unrelated @Size(max = 10) @Deprecated private String mixed;
        @Valid private Sample child;
        @CustomCode private String code;
        @Pattern(regexp = "a") @Pattern(regexp = "b") private String repeated;
        @Pattern.List({@Pattern(regexp = "c"), @Pattern(regexp = "d")}) private String explicitList;
        @NotNull @Pattern(regexp = "e") @Pattern(regexp = "f") @Size(max = 3) private String surrounded;
        @LegacyCode.List({@LegacyCode("x"), @LegacyCode("y")}) private String legacy;
        @Tag("t1") @Tag("t2") private String tagged;
        @MissingMessage private String broken;
    }
}
