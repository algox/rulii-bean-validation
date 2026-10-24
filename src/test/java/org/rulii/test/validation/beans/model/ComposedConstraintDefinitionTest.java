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
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.model.ConstraintCompositionCycleException;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.InvalidAttributeOverrideException;
import org.rulii.validation.beans.model.ValueDeclaration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for composed constraints in {@link ConstraintDefinition}: composing constraints,
 * {@code @OverridesAttribute}, {@code @ReportAsSingleViolation} and the composition cycle guard.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ComposedConstraintDefinitionTest {

    public ComposedConstraintDefinitionTest() {
        super();
    }

    @Test
    public void standardConstraintIsNotComposed() throws Exception {
        ConstraintDefinition definition = single("plainName");
        assertFalse(definition.isComposed());
        assertTrue(definition.getComposingConstraints().isEmpty());
        assertFalse(definition.isReportAsSingleViolation());
    }

    @Test
    public void composingConstraintsInDeclarationOrder() throws Exception {
        ConstraintDefinition definition = single("validName");
        assertEquals(ValidName.class, definition.getAnnotationType());
        assertTrue(definition.isComposed());
        assertFalse(definition.isReportAsSingleViolation());

        List<ConstraintDefinition> composing = definition.getComposingConstraints();
        assertEquals(2, composing.size());
        assertEquals(NotNull.class, composing.get(0).getAnnotationType());
        assertEquals(Size.class, composing.get(1).getAnnotationType());
        assertEquals(5, ((Size) composing.get(1).getAnnotation()).min());
        assertEquals(10, ((Size) composing.get(1).getAnnotation()).max());
        assertFalse(composing.get(0).isComposed());
    }

    @Test
    public void hostAnnotationIsTheOriginalInstance() throws Exception {
        Field field = field("validName");
        ConstraintDefinition definition = ConstraintDefinition.load(ValueDeclaration.ofField(field)).get(0);
        assertSame(field.getAnnotation(ValidName.class), definition.getAnnotation());
    }

    @Test
    public void composingConstraintsShareTheSource() throws Exception {
        ConstraintDefinition definition = single("validName");
        for (ConstraintDefinition composing : definition.getComposingConstraints()) {
            assertSame(definition.getConstraintSource(), composing.getConstraintSource());
            assertEquals("validName", composing.getSource().getFieldName());
        }
    }

    @Test
    public void composingConstraintsInheritGroupsAndPayload() throws Exception {
        ConstraintDefinition definition = single("validName");
        assertEquals(List.of(OnCreate.class), definition.getGroups());
        assertEquals(List.of(Critical.class), definition.getPayload());

        for (ConstraintDefinition composing : definition.getComposingConstraints()) {
            assertEquals(List.of(OnCreate.class), composing.getGroups());
            assertEquals(List.of(Critical.class), composing.getPayload());
        }

        // The synthesized annotation carries the effective values as well.
        Size size = (Size) definition.getComposingConstraints().get(1).getAnnotation();
        assertArrayEquals(new Class<?>[] {OnCreate.class}, size.groups());
        assertArrayEquals(new Class<?>[] {Critical.class}, size.payload());
    }

    @Test
    public void composingConstraintsKeepTheirOwnMessage() throws Exception {
        ConstraintDefinition definition = single("validName");
        assertEquals("invalid name", definition.getMessage());
        assertEquals("{jakarta.validation.constraints.NotNull.message}", definition.getComposingConstraints().get(0).getMessage());
        assertEquals("{jakarta.validation.constraints.Size.message}", definition.getComposingConstraints().get(1).getMessage());
    }

    @Test
    public void overridesAttributeByNameAndBySameName() throws Exception {
        ConstraintDefinition definition = single("boundedName");
        Size size = (Size) definition.getComposingConstraints().get(0).getAnnotation();
        // maxLength() -> Size.max (explicit name), min() -> Size.min (same name).
        assertEquals(3, size.min());
        assertEquals(7, size.max());
        // Unrelated attributes keep their declared values.
        assertEquals("{jakarta.validation.constraints.Size.message}", size.message());
    }

    @Test
    public void overridesAttributeDefaultsApplyWhenHostUsesDefaults() throws Exception {
        ConstraintDefinition definition = single("defaultBoundedName");
        Size size = (Size) definition.getComposingConstraints().get(0).getAnnotation();
        assertEquals(2, size.min());
        assertEquals(20, size.max());
    }

    @Test
    public void overridesAttributeListTargetsSeveralComposingConstraints() throws Exception {
        ConstraintDefinition definition = single("exact");
        assertEquals(Min.class, definition.getComposingConstraints().get(0).getAnnotationType());
        assertEquals(Max.class, definition.getComposingConstraints().get(1).getAnnotationType());
        assertEquals(42L, ((Min) definition.getComposingConstraints().get(0).getAnnotation()).value());
        assertEquals(42L, ((Max) definition.getComposingConstraints().get(1).getAnnotation()).value());
    }

    @Test
    public void constraintIndexTargetsOneRepeatedConstraint() throws Exception {
        ConstraintDefinition definition = single("twoPatterns");
        List<ConstraintDefinition> composing = definition.getComposingConstraints();
        assertEquals(2, composing.size());
        assertEquals("first", ((Pattern) composing.get(0).getAnnotation()).regexp());
        assertEquals("overridden", ((Pattern) composing.get(1).getAnnotation()).regexp());
    }

    @Test
    public void defaultConstraintIndexDoesNotTargetRepeatedConstraints() throws Exception {
        // Reference implementation semantics: index -1 means "the single direct declaration";
        // constraints inside a repeatable container are indexed 0..n-1 and are left untouched.
        ConstraintDefinition definition = single("twoPatternsNoIndex");
        List<ConstraintDefinition> composing = definition.getComposingConstraints();
        assertEquals("first", ((Pattern) composing.get(0).getAnnotation()).regexp());
        assertEquals("second", ((Pattern) composing.get(1).getAnnotation()).regexp());
    }

    @Test
    public void reportAsSingleViolationIsRecordedOnTheHostOnly() throws Exception {
        ConstraintDefinition definition = single("singleViolation");
        assertTrue(definition.isReportAsSingleViolation());
        assertEquals(2, definition.getComposingConstraints().size());
        for (ConstraintDefinition composing : definition.getComposingConstraints()) {
            assertFalse(composing.isReportAsSingleViolation());
        }
    }

    @Test
    public void nestedCompositionPropagatesGroupsDownTheTree() throws Exception {
        ConstraintDefinition outer = single("nested");
        assertEquals(Outer.class, outer.getAnnotationType());
        assertEquals(List.of(OnUpdate.class), outer.getGroups());

        assertEquals(1, outer.getComposingConstraints().size());
        ConstraintDefinition validName = outer.getComposingConstraints().get(0);
        assertEquals(ValidName.class, validName.getAnnotationType());
        assertEquals(List.of(OnUpdate.class), validName.getGroups());
        assertEquals("invalid name", validName.getMessage());

        assertEquals(2, validName.getComposingConstraints().size());
        for (ConstraintDefinition leaf : validName.getComposingConstraints()) {
            assertEquals(List.of(OnUpdate.class), leaf.getGroups());
            assertTrue(leaf.getPayload().isEmpty());
        }
    }

    @Test
    public void nestedOverrideIsAppliedThroughTheSynthesizedHost() throws Exception {
        // Outer overrides ValidName.maxLength(); ValidName in turn overrides Size.max().
        ConstraintDefinition outer = single("nestedOverride");
        ConstraintDefinition bounded = outer.getComposingConstraints().get(0);
        assertEquals(BoundedName.class, bounded.getAnnotationType());
        assertEquals(99, ((BoundedName) bounded.getAnnotation()).maxLength());
        Size size = (Size) bounded.getComposingConstraints().get(0).getAnnotation();
        assertEquals(99, size.max());
    }

    @Test
    public void compositionOnClassLevelConstraint() throws Exception {
        List<ConstraintDefinition> result = ConstraintDefinition.load(Composite.class);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isComposed());
        assertEquals(ClassLevelComposed.class, result.get(0).getAnnotationType());
        assertEquals(Marker.class, result.get(0).getComposingConstraints().get(0).getAnnotationType());
        assertSame(result.get(0).getConstraintSource(), result.get(0).getComposingConstraints().get(0).getConstraintSource());
    }

    @Test
    public void overrideOfMissingAttributeFailsFast() throws Exception {
        Field field = field("badAttribute");
        InvalidAttributeOverrideException e = assertThrows(InvalidAttributeOverrideException.class,
                () -> ConstraintDefinition.load(ValueDeclaration.ofField(field)));
        assertEquals(BadAttribute.class, e.getHostType());
        assertEquals("length", e.getHostAttribute());
        assertEquals(Size.class, e.getComposingType());
        assertEquals("length", e.getTargetAttribute());
        assertTrue(e.getMessage().contains("does not declare"));
    }

    @Test
    public void overrideWithDifferentTypeFailsFast() throws Exception {
        Field field = field("badType");
        InvalidAttributeOverrideException e = assertThrows(InvalidAttributeOverrideException.class,
                () -> ConstraintDefinition.load(ValueDeclaration.ofField(field)));
        assertEquals(BadType.class, e.getHostType());
        assertEquals("max", e.getTargetAttribute());
        assertTrue(e.getMessage().contains("java.lang.String"));
        assertTrue(e.getMessage().contains("int"));
    }

    @Test
    public void mutualCompositionCycleFailsFast() throws Exception {
        Field field = field("cycle");
        ConstraintCompositionCycleException e = assertThrows(ConstraintCompositionCycleException.class,
                () -> ConstraintDefinition.load(ValueDeclaration.ofField(field)));
        assertEquals(List.of(CycleA.class, CycleB.class, CycleA.class), e.getPath());
        assertTrue(e.getMessage().contains("CycleA -> CycleB -> CycleA"));
        assertTrue(e.getMessage().contains(Sample.class.getName() + ".cycle"));
    }

    @Test
    public void selfCompositionFailsFast() throws Exception {
        Field field = field("selfCycle");
        ConstraintCompositionCycleException e = assertThrows(ConstraintCompositionCycleException.class,
                () -> ConstraintDefinition.load(ValueDeclaration.ofField(field)));
        assertEquals(List.of(SelfRef.class, SelfRef.class), e.getPath());
    }

    @Test
    public void sameConstraintTwiceOnDifferentBranchesIsNotACycle() throws Exception {
        // Diamond: Both -> ValidName (NotNull, Size) + NotNull: NotNull appears twice but never on its own path.
        ConstraintDefinition definition = single("diamond");
        assertEquals(2, definition.getComposingConstraints().size());
        assertEquals(ValidName.class, definition.getComposingConstraints().get(0).getAnnotationType());
        assertEquals(NotNull.class, definition.getComposingConstraints().get(1).getAnnotationType());
    }

    @Test
    public void toStringMentionsComposition() throws Exception {
        String text = single("singleViolation").toString();
        assertTrue(text.contains("composing=2"));
        assertTrue(text.contains("reportAsSingleViolation=true"));
        assertFalse(single("plainName").toString().contains("composing="));
    }

    private static ConstraintDefinition single(String name) throws NoSuchFieldException {
        List<ConstraintDefinition> result = ConstraintDefinition.load(ValueDeclaration.ofField(field(name)));
        assertEquals(1, result.size());
        return result.get(0);
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Sample.class.getDeclaredField(name);
    }

    public interface OnCreate {}
    public interface OnUpdate {}
    public static class Critical implements Payload {}

    @NotNull
    @Size(min = 5, max = 10)
    @Documented
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidName {
        String message() default "invalid name";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Size
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BoundedName {
        String message() default "bad length";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Size.class, name = "max")
        int maxLength() default 20;

        @OverridesAttribute(constraint = Size.class)
        int min() default 2;
    }

    @Min(0)
    @Max(0)
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Exactly {
        String message() default "not exact";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute.List({
                @OverridesAttribute(constraint = Min.class, name = "value"),
                @OverridesAttribute(constraint = Max.class, name = "value")
        })
        long value();
    }

    @Pattern(regexp = "first")
    @Pattern(regexp = "second")
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TwoPatterns {
        String message() default "bad";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Pattern.class, name = "regexp", constraintIndex = 1)
        String second() default "overridden";
    }

    @Pattern(regexp = "first")
    @Pattern(regexp = "second")
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TwoPatternsNoIndex {
        String message() default "bad";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Pattern.class, name = "regexp")
        String regexp() default "ignored";
    }

    @NotBlank
    @Size(max = 3)
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ShortCode {
        String message() default "invalid short code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @ValidName
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Outer {
        String message() default "outer";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @BoundedName
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface OuterBounded {
        String message() default "outer bounded";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = BoundedName.class, name = "maxLength")
        int limit() default 99;
    }

    @ValidName
    @NotNull
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Both {
        String message() default "both";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Size
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BadAttribute {
        String message() default "bad";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Size.class)
        int length() default 1;
    }

    @Size
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BadType {
        String message() default "bad";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Size.class)
        String max() default "5";
    }

    @CycleB
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CycleA {
        String message() default "a";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @CycleA
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CycleB {
        String message() default "b";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @SelfRef
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SelfRef {
        String message() default "self";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Constraint(validatedBy = {})
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
        String message() default "marker";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Marker
    @Constraint(validatedBy = {})
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ClassLevelComposed {
        String message() default "class level";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @ClassLevelComposed
    private static class Composite {
    }

    @SuppressWarnings("unused")
    private static class Sample {
        @NotBlank private String plainName;
        @ValidName(groups = OnCreate.class, payload = Critical.class) private String validName;
        @BoundedName(min = 3, maxLength = 7) private String boundedName;
        @BoundedName private String defaultBoundedName;
        @Exactly(42) private long exact;
        @TwoPatterns private String twoPatterns;
        @TwoPatternsNoIndex private String twoPatternsNoIndex;
        @ShortCode private String singleViolation;
        @Outer(groups = OnUpdate.class) private String nested;
        @OuterBounded private String nestedOverride;
        @Both private String diamond;
        @BadAttribute private String badAttribute;
        @BadType private String badType;
        @CycleA private String cycle;
        @SelfRef private String selfCycle;
    }
}
