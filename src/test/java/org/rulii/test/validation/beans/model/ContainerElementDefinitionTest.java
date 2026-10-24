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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.validation.beans.extract.NoValueExtractorException;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.ValueDeclaration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContainerElementDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ContainerElementDefinitionTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    public ContainerElementDefinitionTest() {
        super();
    }

    @Test
    public void nonGenericFieldHasNoElements() throws Exception {
        assertTrue(ContainerElementDefinition.load(source("plain"), REGISTRY).isEmpty());
    }

    @Test
    public void unconstrainedGenericIsPruned() throws Exception {
        assertTrue(ContainerElementDefinition.load(source("unconstrained"), REGISTRY).isEmpty());
        assertTrue(ContainerElementDefinition.load(source("deepUnconstrained"), REGISTRY).isEmpty());
    }

    @Test
    public void listElement() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("names"), REGISTRY);
        assertEquals(1, result.size());

        ContainerElementDefinition element = result.get(0);
        assertEquals(List.class, element.getContainerType());
        assertEquals(0, element.getTypeArgumentIndex());
        assertEquals(String.class, element.getType());
        assertEquals("List<0>", element.getName());
        assertFalse(element.isCascaded());
        assertTrue(element.getChildren().isEmpty());
        assertEquals(1, element.getConstraints().size());
        assertEquals(NotBlank.class, element.getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void fieldLevelConstraintsAreNotCollectedHere() throws Exception {
        // @NotEmpty is on the field; only the element @NotBlank belongs to the element node.
        Field field = field("names");
        assertEquals(NotEmpty.class, ConstraintDefinition.load(ValueDeclaration.ofField(field)).get(0).getAnnotationType());
        ContainerElementDefinition element = ContainerElementDefinition.load(ValueDeclaration.ofField(field), REGISTRY).get(0);
        assertEquals(1, element.getConstraints().size());
        assertEquals(NotBlank.class, element.getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void mapKeyAndValue() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("contacts"), REGISTRY);
        assertEquals(2, result.size());

        ContainerElementDefinition key = result.get(0);
        assertEquals(Map.class, key.getContainerType());
        assertEquals(0, key.getTypeArgumentIndex());
        assertEquals(NotBlank.class, key.getConstraints().get(0).getAnnotationType());

        ContainerElementDefinition value = result.get(1);
        assertEquals(1, value.getTypeArgumentIndex());
        assertEquals("Map<1>", value.getName());
        assertEquals(Positive.class, value.getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void onlyConstrainedPositionsAreKept() throws Exception {
        // Map<String, @Positive Integer>: key position pruned, value kept with index 1.
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("scores"), REGISTRY);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getTypeArgumentIndex());
    }

    @Test
    public void nestedPositions() throws Exception {
        // Map<@NotBlank String, List<@Valid @Size(max = 5) String>>
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("nested"), REGISTRY);
        assertEquals(2, result.size());

        ContainerElementDefinition value = result.get(1);
        assertEquals(Map.class, value.getContainerType());
        assertTrue(value.getConstraints().isEmpty());
        assertFalse(value.isCascaded());
        assertEquals(1, value.getChildren().size());

        ContainerElementDefinition element = value.getChildren().get(0);
        assertEquals(List.class, element.getContainerType());
        assertEquals(0, element.getTypeArgumentIndex());
        assertTrue(element.isCascaded());
        assertEquals(1, element.getConstraints().size());
        assertEquals(Size.class, element.getConstraints().get(0).getAnnotationType());
        assertEquals(5, ((Size) element.getConstraints().get(0).getAnnotation()).max());
    }

    @Test
    public void intermediateUnconstrainedPositionIsKeptForItsChildren() throws Exception {
        // List<Map<String, @Size(max = 5) String>>: the element node has no constraints but owns the value node.
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("listOfMaps"), REGISTRY);
        assertEquals(1, result.size());
        ContainerElementDefinition element = result.get(0);
        assertTrue(element.getConstraints().isEmpty());
        assertEquals(1, element.getChildren().size());
        assertEquals(1, element.getChildren().get(0).getTypeArgumentIndex());
    }

    @Test
    public void optionalValue() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("email"), REGISTRY);
        assertEquals(1, result.size());
        assertEquals(Optional.class, result.get(0).getContainerType());
        assertEquals(Email.class, result.get(0).getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void cascadeOnlyPositionIsKept() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("orders"), REGISTRY);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isCascaded());
        assertTrue(result.get(0).getConstraints().isEmpty());
    }

    @Test
    public void repeatedConstraintAtPositionIsUnwrapped() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("codes"), REGISTRY);
        List<ConstraintDefinition> constraints = result.get(0).getConstraints();
        assertEquals(2, constraints.size());
        assertEquals("a", ((Pattern) constraints.get(0).getAnnotation()).regexp());
        assertEquals("b", ((Pattern) constraints.get(1).getAnnotation()).regexp());
    }

    @Test
    public void typeVariablePosition() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("items"), REGISTRY);
        assertEquals(1, result.size());
        assertEquals("T", result.get(0).getType().getTypeName());
        assertEquals(NotNull.class, result.get(0).getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void annotatedWildcardPosition() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("anything"), REGISTRY);
        assertEquals(1, result.size());
        assertEquals(NotNull.class, result.get(0).getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void wildcardBoundsAreNotDescended() throws Exception {
        assertTrue(ContainerElementDefinition.load(source("boundedUnconstrained"), REGISTRY).isEmpty());
    }

    @Test
    public void arrayComponentInsideContainer() throws Exception {
        // List<String @Size(min = 1) []>: constraint on the array (the element) itself.
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("arrays"), REGISTRY);
        assertEquals(1, result.size());
        assertEquals(List.class, result.get(0).getContainerType());
        assertEquals(Size.class, result.get(0).getConstraints().get(0).getAnnotationType());
        assertTrue(result.get(0).getChildren().isEmpty());

        // List<@NotBlank String[]>: Java puts the annotation on the component String, and
        // arrays are not a built-in container, so there is no extractor for it.
        NoValueExtractorException e = assertThrows(NoValueExtractorException.class,
                () -> ContainerElementDefinition.load(source("arrayComponents"), REGISTRY));
        assertEquals(String[].class, e.getContainerType());
        assertEquals(0, e.getTypeArgumentIndex());
        assertEquals(field("arrayComponents"), e.getMember());
        assertTrue(e.getMessage().contains("arrayComponents"));
    }

    @Test
    public void arrayFieldDeclarationAnnotationsAreDroppedFromTheComponent() throws Exception {
        // @NotBlank String[] tags: Java applies the annotation to the field AND the component type.
        // The field-level read sees it as a field constraint; the container walk drops the mirrored
        // duplicate, so the (extractor-less) array component is not constrained.
        Field field = field("tags");
        assertEquals(NotBlank.class, ConstraintDefinition.load(ValueDeclaration.ofField(field)).get(0).getAnnotationType());
        assertTrue(ContainerElementDefinition.load(ValueDeclaration.ofField(field), REGISTRY).isEmpty());
    }

    @Test
    public void mirroredValidOnArrayComponentIsDropped() throws Exception {
        assertTrue(ContainerElementDefinition.load(source("cascadedArray"), REGISTRY).isEmpty());
    }

    @Test
    public void typeUseOnlyConstraintOnArrayComponentIsAnElementConstraint() throws Exception {
        // @TypeUseOnly cannot target the field, so it is a genuine component constraint, and arrays
        // have no built-in extractor.
        NoValueExtractorException e = assertThrows(NoValueExtractorException.class,
                () -> ContainerElementDefinition.load(source("typeUseArray"), REGISTRY));
        assertEquals(String[].class, e.getContainerType());
    }

    @Test
    public void groupConversionAtPositionIsRead() throws Exception {
        List<ContainerElementDefinition> nodes = ContainerElementDefinition.load(source("convertedElements"), REGISTRY);

        assertEquals(1, nodes.size());
        assertTrue(nodes.get(0).isCascaded());
        assertEquals(Map.of(Default.class, Default.class), nodes.get(0).getGroupConversions().asMap());
    }

    @Test
    public void nestedArrayComponentsAreNotDeduped() throws Exception {
        // The dedup applies to the root array only: List<@NotBlank String[]> keeps failing.
        assertThrows(NoValueExtractorException.class, () -> ContainerElementDefinition.load(source("arrayComponents"), REGISTRY));
    }

    @Test
    public void constrainedPositionWithoutExtractorFailsFast() throws Exception {
        NoValueExtractorException e = assertThrows(NoValueExtractorException.class,
                () -> ContainerElementDefinition.load(source("pair"), REGISTRY));
        assertEquals(Pair.class, e.getContainerType());
        assertEquals(1, e.getTypeArgumentIndex());
        assertTrue(e.getMessage().contains("Pair"));
        assertTrue(e.getMessage().contains("[1]"));
        assertTrue(e.getMessage().contains("ValueExtractor"));
    }

    @Test
    public void cascadedPositionWithoutExtractorFailsFast() throws Exception {
        assertThrows(NoValueExtractorException.class, () -> ContainerElementDefinition.load(source("cascadedPair"), REGISTRY));
    }

    @Test
    public void registeredExtractorMakesThePositionValid() throws Exception {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().extractor(new PairSecondExtractor()).build();
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("pair"), registry);
        assertEquals(1, result.size());
        assertEquals(Pair.class, result.get(0).getContainerType());
        assertEquals(1, result.get(0).getTypeArgumentIndex());
        assertEquals(NotNull.class, result.get(0).getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void intermediateUnconstrainedPositionNeedsAnExtractorToo() throws Exception {
        // Pair<String, List<@NotBlank String>>: nothing is applied at Pair's position, but the walk has to
        // extract the List from the Pair to reach the constrained element, so Pair needs an extractor as well.
        NoValueExtractorException e = assertThrows(NoValueExtractorException.class,
                () -> ContainerElementDefinition.load(source("pairOfList"), REGISTRY));
        assertTrue(e.getMessage().contains("Pair"));

        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().extractor(new PairSecondExtractor()).build();
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("pairOfList"), registry);
        assertEquals(1, result.size());
        assertEquals(Pair.class, result.get(0).getContainerType());
        assertTrue(result.get(0).getConstraints().isEmpty());
        assertEquals(List.class, result.get(0).getChildren().get(0).getContainerType());
        assertEquals(PairSecondExtractor.class, result.get(0).getExtractor().getExtractor().getClass());
        assertEquals(List.class, result.get(0).getChildren().get(0).getExtractor().getContainerType());
    }

    @Test
    public void nullRegistryIsRejected() throws Exception {
        Field field = field("names");
        assertThrows(IllegalArgumentException.class, () -> ContainerElementDefinition.load(ValueDeclaration.ofField(field), null));
    }

    @Test
    public void sourceAndFieldAreCarried() throws Exception {
        ContainerElementDefinition element = ContainerElementDefinition.load(source("names"), REGISTRY).get(0);
        assertEquals(field("names"), element.getSourceDeclaration().getMember());
        assertEquals(Sample.class.getName(), element.getSource().getClassName());
        assertEquals("names", element.getSource().getFieldName());
        assertEquals(field("names"), element.getConstraints().get(0).getConstraintSource().getElement());
        assertNotNull(element.getAnnotatedType());
        assertTrue(element.toString().contains("List<0>"));
    }

    @Test
    public void resultsAreUnmodifiable() throws Exception {
        List<ContainerElementDefinition> result = ContainerElementDefinition.load(source("nested"), REGISTRY);
        assertThrows(UnsupportedOperationException.class, () -> result.add(null));
        assertThrows(UnsupportedOperationException.class, () -> result.get(1).getChildren().clear());
    }

    @Test
    public void nullFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContainerElementDefinition.load(null, REGISTRY));
    }

    private static ValueDeclaration source(String name) throws NoSuchFieldException {
        return ValueDeclaration.ofField(field(name));
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Sample.class.getDeclaredField(name);
    }

    static class Pair<A, B> {
        A first;
        B second;
    }

    static class PairSecondExtractor implements ValueExtractor<Pair<?, @ExtractedValue ?>> {
        @Override
        public void extractValues(Pair<?, ?> originalValue, ValueReceiver receiver) {
            receiver.value("second", originalValue.second);
        }
    }

    @SuppressWarnings("unused")
    private static class Sample<T> {
        private Pair<String, @NotNull Integer> pair;
        private Pair<String, @Valid Sample<?>> cascadedPair;
        private Pair<String, List<@NotBlank String>> pairOfList;
        @NotBlank private String plain;
        private List<String> unconstrained;
        private Map<String, List<Set<String>>> deepUnconstrained;
        @NotEmpty private List<@NotBlank String> names;
        private Map<@NotBlank String, @Positive Integer> contacts;
        private Map<String, @Positive Integer> scores;
        private Map<@NotBlank String, List<@Valid @Size(max = 5) String>> nested;
        private List<Map<String, @Size(max = 5) String>> listOfMaps;
        private Optional<@Email String> email;
        private List<@Valid Sample<?>> orders;
        private List<@Pattern(regexp = "a") @Pattern(regexp = "b") String> codes;
        private List<@NotNull T> items;
        private List<@NotNull ?> anything;
        private List<? extends Number> boundedUnconstrained;
        private List<String @Size(min = 1) []> arrays;
        private List<@NotBlank String[]> arrayComponents;
        @NotBlank private String[] tags;
        @Valid private Sample<?>[] cascadedArray;
        private @TypeUseOnly String[] typeUseArray;
        private List<@Valid @ConvertGroup(from = Default.class, to = Default.class) Sample<?>> convertedElements;
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @interface TypeUseOnly {
        String message() default "type use only";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
}
