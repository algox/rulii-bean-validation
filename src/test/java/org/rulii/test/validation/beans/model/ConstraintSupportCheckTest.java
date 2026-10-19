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
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.ConstraintNotApplicableException;
import org.rulii.validation.beans.mapper.NoConstraintMapperException;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.model.BeanDefinitionLoader;
import org.rulii.validation.rules.size.SizeValidationRule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the read-time constraint support check run by {@link BeanDefinitionLoader}: unmapped
 * constraints and constraints declared on types they cannot apply to fail the load.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintSupportCheckTest {

    public ConstraintSupportCheckTest() {
        super();
    }

    private static BeanDefinitionLoader loader() {
        return new BeanDefinitionLoader(ValueExtractorRegistry.builder().build());
    }

    private static BeanDefinitionLoader loader(ConstraintMapperRegistry mappers) {
        return new BeanDefinitionLoader(ValueExtractorRegistry.builder().build(), mappers);
    }

    // --- registries ---------------------------------------------------------------------------

    @Test
    public void singleArgumentConstructorUsesTheStandardMappers() {
        BeanDefinitionLoader loader = loader();
        assertEquals(22, loader.getConstraintMapperRegistry().getMappers().size());
        assertNotNull(loader.getValueExtractorRegistry());
        assertThrows(IllegalArgumentException.class, () -> new BeanDefinitionLoader(null, ConstraintMapperRegistry.builder().build()));
        assertThrows(IllegalArgumentException.class, () -> new BeanDefinitionLoader(ValueExtractorRegistry.builder().build(), null));
    }

    @Test
    public void standardConstraintsOnSupportedTypesLoad() {
        BeanDefinitionLoader loader = loader();
        assertNotNull(loader.load(WellPlaced.class));
        assertTrue(loader.isLoaded(WellPlaced.class));
    }

    // --- no mapper ----------------------------------------------------------------------------

    @Test
    public void unmappedConstraintFailsTheLoad() {
        BeanDefinitionLoader loader = loader();
        NoConstraintMapperException e = assertThrows(NoConstraintMapperException.class, () -> loader.load(Unmapped.class));
        assertEquals(Unmapped.class.getName() + ".code", e.getLocation());
        assertEquals(CustomCode.class, e.getAnnotationType());
        assertTrue(loader.isPoisoned(Unmapped.class));
        assertSame(e, assertThrows(NoConstraintMapperException.class, () -> loader.load(Unmapped.class)));
    }

    @Test
    public void registeredMapperMakesTheConstraintLoad() {
        ConstraintMapperRegistry mappers = ConstraintMapperRegistry.builder()
                .mapper(new SimpleConstraintMapper<>(CustomCode.class, List.of(CharSequence.class)))
                .build();
        BeanDefinitionLoader loader = loader(mappers);
        assertNotNull(loader.load(Unmapped.class));
        assertTrue(loader.isLoaded(Unmapped.class));
    }

    @Test
    public void emptyRegistryRejectsStandardConstraints() {
        BeanDefinitionLoader loader = loader(ConstraintMapperRegistry.builder(false).build());
        NoConstraintMapperException e = assertThrows(NoConstraintMapperException.class, () -> loader.load(WellPlaced.class));
        assertEquals(NotBlank.class, e.getAnnotationType());
    }

    @Test
    public void pureCompositionNeedsNoMapper() {
        BeanDefinitionLoader loader = loader();
        assertNotNull(loader.load(Composed.class));
        assertTrue(loader.isLoaded(Composed.class));
    }

    @Test
    public void compositionWithValidatorsNeedsAMapper() {
        // The composing constraints would run, but the declared validators would be dropped silently.
        BeanDefinitionLoader loader = loader();
        NoConstraintMapperException e = assertThrows(NoConstraintMapperException.class, () -> loader.load(ComposedWithValidator.class));
        assertEquals(ValidNameWithValidator.class, e.getAnnotationType());

        ConstraintMapperRegistry mappers = ConstraintMapperRegistry.builder()
                .mapper(new SimpleConstraintMapper<>(ValidNameWithValidator.class, List.of(CharSequence.class)))
                .build();
        assertNotNull(loader(mappers).load(ComposedWithValidator.class));
    }

    @Test
    public void unmappedComposingLeafFailsTheLoad() {
        BeanDefinitionLoader loader = loader();
        NoConstraintMapperException e = assertThrows(NoConstraintMapperException.class, () -> loader.load(ComposedOfUnmapped.class));
        assertEquals(CustomCode.class, e.getAnnotationType(), "the leaf, not the host");
        assertEquals(ComposedOfUnmapped.class.getName() + ".value", e.getLocation());
    }

    @Test
    public void mappedCompositionHostIsCheckedForApplicabilityToo() {
        ConstraintMapperRegistry mappers = ConstraintMapperRegistry.builder()
                .mapper(new SimpleConstraintMapper<>(ValidName.class, List.of(CharSequence.class)))
                .build();
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class,
                () -> loader(mappers).load(ComposedOnNumber.class));
        assertEquals(ValidName.class, e.getAnnotationType());
        assertEquals(Integer.class, e.getType());
    }

    // --- not applicable -----------------------------------------------------------------------

    @Test
    public void constraintOnIncompatibleFieldTypeFailsTheLoad() {
        BeanDefinitionLoader loader = loader();
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class, () -> loader.load(SizeOnInt.class));
        assertEquals(Size.class, e.getAnnotationType());
        assertEquals(SizeOnInt.class.getName() + ".count", e.getLocation());
        assertEquals(int.class, e.getType());
        assertEquals(SizeValidationRule.SUPPORTED_TYPES, e.getSupportedTypes());
        assertTrue(loader.isPoisoned(SizeOnInt.class));
    }

    @Test
    public void temporalConstraintOnStringFailsTheLoad() {
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class, () -> loader().load(FutureOnString.class));
        assertEquals(Future.class, e.getAnnotationType());
        assertEquals(String.class, e.getType());
    }

    @Test
    public void objectDeclarationIsAccepted() {
        // HV accepts it and resolves per runtime value; so do we (core checkType guards the value).
        assertNotNull(loader().load(SizeOnObject.class));
    }

    @Test
    public void fieldAndGetterAreCheckedAgainstTheirOwnTypes() {
        // @Size on the Object field is fine; @Size on the int getter is not.
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class, () -> loader().load(MixedTypes.class));
        assertEquals(MixedTypes.class.getName() + ".getValue()", e.getLocation());
        assertEquals(int.class, e.getType());
    }

    @Test
    public void containerElementConstraintIsCheckedAgainstTheElementType() {
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class, () -> loader().load(SizeOnListElement.class));
        assertEquals(Size.class, e.getAnnotationType());
        assertEquals(SizeOnListElement.class.getName() + ".numbers List<0>", e.getLocation());
        assertEquals(Integer.class, e.getType());
    }

    @Test
    public void nestedContainerElementLocationNamesThePath() {
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class, () -> loader().load(FutureInNestedMap.class));
        assertEquals(Future.class, e.getAnnotationType());
        assertEquals(FutureInNestedMap.class.getName() + ".entries Map<1> List<0>", e.getLocation());
        assertEquals(String.class, e.getType());
    }

    @Test
    public void unwrappedConstraintIsCheckedAgainstTheExtractedType() {
        // @Size on OptionalInt is unwrapped to the Integer value, which @Size cannot apply to.
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class, () -> loader().load(SizeOnOptionalInt.class));
        assertEquals(Size.class, e.getAnnotationType());
        assertEquals(SizeOnOptionalInt.class.getName() + ".count OptionalInt<-1>", e.getLocation());
        assertEquals(Integer.class, e.getType());

        // @Min unwraps to Integer as well and applies.
        assertNotNull(loader().load(MinOnOptionalInt.class));
    }

    @Test
    public void wellPlacedContainerElementConstraintsLoad() {
        assertNotNull(loader().load(WellPlacedElements.class));
    }

    // --- class level --------------------------------------------------------------------------

    @Test
    public void classLevelConstraintNeedsAMapper() {
        BeanDefinitionLoader loader = loader();
        NoConstraintMapperException e = assertThrows(NoConstraintMapperException.class, () -> loader.load(ClassLevel.class));
        assertEquals(ClassCheck.class, e.getAnnotationType());
        assertEquals(ClassLevel.class.getName(), e.getLocation());
    }

    @Test
    public void classLevelConstraintIsCheckedAgainstTheBeanClass() {
        ConstraintMapperRegistry accepting = ConstraintMapperRegistry.builder()
                .mapper(new SimpleConstraintMapper<>(ClassCheck.class, List.of(ClassLevel.class)))
                .build();
        assertNotNull(loader(accepting).load(ClassLevel.class));

        ConstraintMapperRegistry rejecting = ConstraintMapperRegistry.builder()
                .mapper(new SimpleConstraintMapper<>(ClassCheck.class, List.of(CharSequence.class)))
                .build();
        ConstraintNotApplicableException e = assertThrows(ConstraintNotApplicableException.class,
                () -> loader(rejecting).load(ClassLevel.class));
        assertEquals(ClassLevel.class, e.getType());
        assertEquals(ClassLevel.class.getName(), e.getLocation());
    }

    @Test
    public void inheritedClassLevelConstraintIsCheckedAgainstTheConcreteBeanClass() {
        ConstraintMapperRegistry mappers = ConstraintMapperRegistry.builder()
                .mapper(new SimpleConstraintMapper<>(ClassCheck.class, List.of(SubLevel.class)))
                .build();
        assertNotNull(loader(mappers).load(SubLevel.class));
    }

    // --- cascade graph ------------------------------------------------------------------------

    @Test
    public void failureInACascadeTargetPoisonsThePath() {
        BeanDefinitionLoader loader = loader();
        assertThrows(ConstraintNotApplicableException.class, () -> loader.load(ReachesBadTarget.class));
        assertTrue(loader.isPoisoned(ReachesBadTarget.class));
        assertTrue(loader.isPoisoned(SizeOnInt.class));
        assertTrue(loader.isLoaded(WellPlaced.class), "sibling resolved before the failure stays loaded");
    }

    // --- fixtures -----------------------------------------------------------------------------

    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomCode {
        String message() default "invalid code";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @NotNull
    @Size(min = 2)
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidName {
        String message() default "invalid name";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static final class NeverValidator implements ConstraintValidator<ValidNameWithValidator, Object> {
        public NeverValidator() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return false;
        }
    }

    @NotNull
    @Constraint(validatedBy = NeverValidator.class)
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidNameWithValidator {
        String message() default "invalid name";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @CustomCode
    @NotNull
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ComposedOfCustom {
        String message() default "composed";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Constraint(validatedBy = {})
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ClassCheck {
        String message() default "class check";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @SuppressWarnings("unused")
    private static class WellPlaced {
        @NotBlank private String name;
        @Min(1) private int count;
        @Size(max = 3) private List<String> tags;
        @Future private LocalDate due;
        @NotNull private Object anything;
    }

    @SuppressWarnings("unused")
    private static class Unmapped { @CustomCode private String code; }

    @SuppressWarnings("unused")
    private static class Composed { @ValidName private String name; }

    @SuppressWarnings("unused")
    private static class ComposedWithValidator { @ValidNameWithValidator private String name; }

    @SuppressWarnings("unused")
    private static class ComposedOfUnmapped { @ComposedOfCustom private String value; }

    @SuppressWarnings("unused")
    private static class ComposedOnNumber { @ValidName private Integer count; }

    @SuppressWarnings("unused")
    private static class SizeOnInt { @Size(max = 3) private int count; }

    @SuppressWarnings("unused")
    private static class FutureOnString { @Future private String when; }

    @SuppressWarnings("unused")
    private static class SizeOnObject { @Size(max = 3) private Object value; }

    @SuppressWarnings("unused")
    private static class MixedTypes {
        @Size(max = 3) private Object value;
        @Size(max = 3) public int getValue() { return 0; }
    }

    @SuppressWarnings("unused")
    private static class SizeOnListElement { private List<@Size(max = 2) Integer> numbers; }

    @SuppressWarnings("unused")
    private static class FutureInNestedMap { private Map<String, List<@Future String>> entries; }

    @SuppressWarnings("unused")
    private static class SizeOnOptionalInt { @Size(max = 3) private OptionalInt count; }

    @SuppressWarnings("unused")
    private static class MinOnOptionalInt { @Min(1) private OptionalInt count; }

    @SuppressWarnings("unused")
    private static class WellPlacedElements {
        private List<@NotBlank String> names;
        private Map<@Size(max = 3) String, @Min(0) Integer> scores;
        private List<List<@Future LocalDate>> dates;
    }

    @ClassCheck
    private static class ClassLevel { }

    private static class SubLevel extends ClassLevel { }

    @SuppressWarnings("unused")
    private static class ReachesBadTarget {
        @Valid private WellPlaced good;
        @Valid private SizeOnInt bad;
    }
}
