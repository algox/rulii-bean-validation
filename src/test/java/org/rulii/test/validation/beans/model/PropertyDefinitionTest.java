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
import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.Unwrapping;
import jakarta.validation.valueextraction.ValueExtractor;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.extract.NoValueExtractorException;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.GroupConversionNotSupportedException;
import org.rulii.validation.beans.model.PropertyDeclaration;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.PropertyDiscovery;
import org.rulii.validation.beans.model.PropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PropertyDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class PropertyDefinitionTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    public PropertyDefinitionTest() {
        super();
    }

    // --- field-backed -------------------------------------------------------------------------

    @Test
    public void plainConstrainedField() throws Exception {
        PropertyDefinition property = load("name");
        assertEquals("name", property.getName());
        assertEquals(PropertySource.ofField(field("name")), property.getPropertySource());
        assertEquals(List.of(PropertyDeclaration.ofField(field("name"))), property.getPropertySource().getDeclarations());
        assertEquals(field("name"), property.getPropertySource().getMember());
        assertFalse(property.getPropertySource().hasMultipleDeclarations());
        assertEquals(String.class, property.getType());
        assertEquals(String.class, property.getGenericType());
        assertEquals(Sample.class.getName(), property.getSource().getClassName());
        assertEquals("name", property.getSource().getFieldName());
        assertTrue(property.isConstrained());
        assertFalse(property.isCascaded());
        assertTrue(property.getContainerElements().isEmpty());
        assertEquals(List.of(NotBlank.class, Size.class), types(property.getConstraints()));
        assertTrue(property.toString().contains("name"));
    }

    @Test
    public void unconstrainedFieldStillHasADefinition() throws Exception {
        PropertyDefinition property = load("plain");
        assertFalse(property.isConstrained());
        assertTrue(property.getConstraints().isEmpty());
        assertTrue(property.getContainerElements().isEmpty());
        assertFalse(property.isCascaded());
    }

    @Test
    public void declarationAndTypeUseReadsAreMerged() throws Exception {
        // Jakarta constraints target FIELD and TYPE_USE: Java mirrors each onto both places.
        PropertyDeclaration declaration = PropertyDeclaration.ofField(field("name"));
        assertEquals(2, ConstraintDefinition.load(declaration).size());
        assertEquals(2, ConstraintDefinition.load(declaration, declaration.getAnnotatedType()).size());
        assertEquals(2, load("name").getConstraints().size());
    }

    @Test
    public void repeatedConstraintsAreNotDuplicatedByTheMerge() throws Exception {
        assertEquals(List.of(Pattern.class, Pattern.class), types(load("code").getConstraints()));
    }

    @Test
    public void typeUseOnlyConstraintIsAFieldConstraint() throws Exception {
        PropertyDeclaration declaration = PropertyDeclaration.ofField(field("typeUseOnly"));
        // The declaration read cannot see a TYPE_USE-only annotation; the type-use read adds it.
        assertEquals(List.of(NotNull.class), types(ConstraintDefinition.load(declaration)));
        assertEquals(List.of(NotNull.class, TypeUseOnly.class), types(load("typeUseOnly").getConstraints()));
    }

    @Test
    public void cascadedField() throws Exception {
        PropertyDefinition property = load("address");
        assertTrue(property.isCascaded());
        assertTrue(property.isConstrained());
        assertTrue(property.getConstraints().isEmpty());
    }

    @Test
    public void groupConversionOnFieldFailsFast() throws Exception {
        GroupConversionNotSupportedException e = assertThrows(GroupConversionNotSupportedException.class, () -> load("converted"));
        assertEquals(field("converted"), e.getSourceDeclaration().getMember());
        assertTrue(e.getMessage().contains("@ConvertGroup"));
        assertTrue(e.getMessage().contains("converted"));
        assertThrows(GroupConversionNotSupportedException.class, () -> load("convertedList"));
        assertThrows(GroupConversionNotSupportedException.class, () -> load("convertedElements"));
    }

    @Test
    public void containerElementsAreSeparateFromFieldConstraints() throws Exception {
        PropertyDefinition property = load("names");
        assertEquals(List.of(NotEmpty.class), types(property.getConstraints()));
        assertEquals(1, property.getContainerElements().size());
        ContainerElementDefinition element = property.getContainerElements().get(0);
        assertEquals(List.class, element.getContainerType());
        assertEquals(0, element.getTypeArgumentIndex());
        assertEquals(List.of(NotBlank.class), types(element.getConstraints()));
        assertTrue(property.isConstrained());
    }

    @Test
    public void arrayFieldConstraintIsNotAnElementConstraint() throws Exception {
        PropertyDefinition property = load("tags");
        assertEquals(List.of(NotEmpty.class, Size.class), types(property.getConstraints()));
        assertTrue(property.getContainerElements().isEmpty());
    }

    @Test
    public void cascadedArrayIsPropertyCascadeOnly() throws Exception {
        PropertyDefinition property = load("addresses");
        assertTrue(property.isCascaded());
        assertTrue(property.getContainerElements().isEmpty());
    }

    @Test
    public void positionWithoutExtractorStillFailsFast() throws Exception {
        assertThrows(NoValueExtractorException.class, () -> load("pair"));
    }

    // --- unwrapping ---------------------------------------------------------------------------

    @Test
    public void unwrapByDefaultMovesFieldConstraintsToTheContainerValueNode() throws Exception {
        PropertyDefinition property = load("count");
        assertTrue(property.getConstraints().isEmpty());
        assertEquals(1, property.getContainerElements().size());

        ContainerElementDefinition unwrapped = property.getContainerElements().get(0);
        assertEquals(OptionalInt.class, unwrapped.getContainerType());
        assertEquals(ValueExtractorDescriptor.CONTAINER, unwrapped.getTypeArgumentIndex());
        assertNull(unwrapped.getAnnotatedType());
        assertEquals(Integer.class, unwrapped.getType());
        assertFalse(unwrapped.isCascaded());
        assertTrue(unwrapped.getChildren().isEmpty());
        assertEquals(List.of(Positive.class, Max.class), types(unwrapped.getConstraints()));
        assertEquals(field("count"), unwrapped.getSourceDeclaration().getMember());
        assertEquals("OptionalInt<-1>", unwrapped.getName());
        assertTrue(unwrapped.toString().contains("java.lang.Integer"));
        assertTrue(property.isConstrained());
    }

    @Test
    public void unwrappingSkipKeepsTheConstraintOnTheField() throws Exception {
        PropertyDefinition property = load("skippedCount");
        assertEquals(List.of(NotNull.class), types(property.getConstraints()));
        assertEquals(1, property.getContainerElements().size());
        assertEquals(List.of(Positive.class), types(property.getContainerElements().get(0).getConstraints()));
    }

    @Test
    public void optionalIsNotUnwrappedByDefault() throws Exception {
        PropertyDefinition property = load("maybe");
        assertEquals(List.of(NotNull.class), types(property.getConstraints()));
        assertTrue(property.getContainerElements().isEmpty());
    }

    @Test
    public void explicitUnwrapMovesTheConstraintToTheTypeArgumentNode() throws Exception {
        PropertyDefinition property = load("unwrappedMaybe");
        assertTrue(property.getConstraints().isEmpty());
        assertEquals(1, property.getContainerElements().size());

        ContainerElementDefinition unwrapped = property.getContainerElements().get(0);
        assertEquals(Optional.class, unwrapped.getContainerType());
        assertEquals(0, unwrapped.getTypeArgumentIndex());
        assertNotNull(unwrapped.getAnnotatedType());
        assertEquals(String.class, unwrapped.getType());
        assertEquals(List.of(NotBlank.class), types(unwrapped.getConstraints()));
    }

    @Test
    public void explicitUnwrapMergesIntoTheExistingTypeArgumentNode() throws Exception {
        PropertyDefinition property = load("mergedMaybe");
        assertTrue(property.getConstraints().isEmpty());
        assertEquals(1, property.getContainerElements().size());

        ContainerElementDefinition unwrapped = property.getContainerElements().get(0);
        assertEquals(Optional.class, unwrapped.getContainerType());
        assertEquals(0, unwrapped.getTypeArgumentIndex());
        // Position constraints first, then the unwrapped field constraints.
        assertEquals(List.of(Size.class, NotBlank.class), types(unwrapped.getConstraints()));
        assertEquals(String.class, unwrapped.getType());
    }

    @Test
    public void unwrapAndSkipTogetherFailFast() throws Exception {
        ConstraintDeclarationException e = assertThrows(ConstraintDeclarationException.class, () -> load("contradiction"));
        assertTrue(e.getMessage().contains("Unwrap"));
        assertTrue(e.getMessage().contains("Skip"));
    }

    @Test
    public void unwrapWithoutExtractorFailsFast() throws Exception {
        ConstraintDeclarationException e = assertThrows(ConstraintDeclarationException.class, () -> load("unwrappedString"));
        assertTrue(e.getMessage().contains("no value extractor"));
    }

    @Test
    public void unwrapOnMultiPositionContainerFailsFast() throws Exception {
        ConstraintDeclarationException e = assertThrows(ConstraintDeclarationException.class, () -> load("unwrappedMap"));
        assertTrue(e.getMessage().contains("several extractable positions"));
    }

    @Test
    public void severalUnwrapByDefaultExtractorsFailFast() throws Exception {
        // IntBox<T> implements Box: one @UnwrapByDefault extractor serves the Box container value,
        // another serves IntBox's type argument. Different positions, so both are maximally specific.
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder(false)
                .extractor(new BoxExtractor())
                .extractor(new IntBoxExtractor())
                .build();
        assertEquals(2, registry.find(IntBox.class).size());
        ConstraintDeclarationException e = assertThrows(ConstraintDeclarationException.class,
                () -> PropertyDefinition.load(field("boxed"), registry));
        assertTrue(e.getMessage().contains("@UnwrapByDefault"));

        // Skip sidesteps the ambiguity; Unwrap hits the multi-position error instead.
        assertEquals(List.of(NotNull.class), types(PropertyDefinition.load(field("skippedBox"), registry).getConstraints()));
        assertThrows(ConstraintDeclarationException.class, () -> PropertyDefinition.load(field("unwrappedBox"), registry));
    }

    @Test
    public void unwrapByDefaultOnAUserContainer() throws Exception {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder(false).extractor(new IntBoxExtractor()).build();
        PropertyDefinition property = PropertyDefinition.load(field("boxed"), registry);
        assertTrue(property.getConstraints().isEmpty());
        ContainerElementDefinition unwrapped = property.getContainerElements().get(0);
        assertEquals(IntBox.class, unwrapped.getContainerType());
        assertEquals(0, unwrapped.getTypeArgumentIndex());
        assertEquals(Integer.class, unwrapped.getType());
        assertNotNull(unwrapped.getAnnotatedType());
    }

    @Test
    public void unwrappedConstraintsAreUnmodifiable() throws Exception {
        PropertyDefinition property = load("count");
        assertThrows(UnsupportedOperationException.class, () -> property.getConstraints().add(null));
        assertThrows(UnsupportedOperationException.class, () -> property.getContainerElements().clear());
        assertThrows(UnsupportedOperationException.class, () -> property.getContainerElements().get(0).getConstraints().clear());
    }

    // --- getter-backed ------------------------------------------------------------------------

    @Test
    public void getterOnlyProperty() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(getter("getEmail"), REGISTRY);
        assertEquals("email", property.getName());
        assertEquals(1, property.getPropertySource().getDeclarations().size());
        assertEquals(PropertyDeclaration.Kind.GETTER, property.getPropertySource().getPrimary().getKind());
        assertEquals(getter("getEmail"), property.getPropertySource().getMember());
        assertEquals(String.class, property.getType());
        assertEquals(Bean.class.getName(), property.getSource().getClassName());
        assertEquals("getEmail", property.getSource().getMethodName());
        assertEquals(List.of(NotBlank.class, Email.class), types(property.getConstraints()));
        assertEquals(PropertyDeclaration.Kind.GETTER, property.getConstraints().get(0).getSourceDeclaration().getKind());
        assertTrue(property.getContainerElements().isEmpty());
        assertFalse(property.isCascaded());
        assertTrue(property.toString().contains("getEmail()"));
    }

    @Test
    public void booleanIsGetter() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(getter("isActive"), REGISTRY);
        assertEquals("active", property.getName());
        assertEquals(boolean.class, property.getType());
        assertEquals(List.of(AssertTrue.class), types(property.getConstraints()));
    }

    @Test
    public void getterContainerElementsAndCascade() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(getter("getOrders"), REGISTRY);
        assertEquals("orders", property.getName());
        assertEquals(List.of(NotEmpty.class), types(property.getConstraints()));
        assertEquals(1, property.getContainerElements().size());
        ContainerElementDefinition element = property.getContainerElements().get(0);
        assertEquals(List.class, element.getContainerType());
        assertTrue(element.isCascaded());
        assertEquals(getter("getOrders"), element.getSourceDeclaration().getMember());
        assertFalse(property.isCascaded());
    }

    @Test
    public void getterUnwrapping() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(getter("getAge"), REGISTRY);
        assertTrue(property.getConstraints().isEmpty());
        ContainerElementDefinition unwrapped = property.getContainerElements().get(0);
        assertEquals(OptionalInt.class, unwrapped.getContainerType());
        assertEquals(List.of(Min.class), types(unwrapped.getConstraints()));
        assertEquals(getter("getAge"), unwrapped.getSourceDeclaration().getMember());
    }

    @Test
    public void groupConversionOnGetterFailsFast() throws Exception {
        GroupConversionNotSupportedException e = assertThrows(GroupConversionNotSupportedException.class,
                () -> PropertyDefinition.load(getter("getConverted"), REGISTRY));
        assertEquals(getter("getConverted"), e.getSourceDeclaration().getMember());
        assertTrue(e.getMessage().contains("getConverted()"));
    }

    @Test
    public void nonGettersAreRejected() throws Exception {
        for (String name : List.of("isWrapped", "get", "getNothing", "getStatic", "compute", "isOn")) {
            Method method = getter(name);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> PropertyDefinition.load(method, REGISTRY), name);
            assertTrue(e.getMessage().contains(name), name);
        }
        assertThrows(IllegalArgumentException.class,
                () -> PropertyDefinition.load(Bean.class.getDeclaredMethod("getWith", String.class), REGISTRY));
    }

    // --- field + getter -----------------------------------------------------------------------

    @Test
    public void fieldAndGetterConstraintsAreCumulativeAndPerDeclaration() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        Method getter = getter("getName");
        PropertyDefinition property = PropertyDefinition.load(field, getter, REGISTRY);

        assertEquals("name", property.getName());
        assertEquals(PropertySource.of(field, getter), property.getPropertySource());
        assertTrue(property.getPropertySource().hasMultipleDeclarations());
        assertEquals(PropertyDeclaration.ofGetter(getter), property.getPropertySource().getPrimary());
        assertEquals("getName", property.getSource().getMethodName());
        assertEquals(String.class, property.getType());

        assertEquals(List.of(NotNull.class, Size.class), types(property.getConstraints()));
        assertEquals(field, property.getConstraints().get(0).getSourceDeclaration().getMember());
        assertEquals(getter, property.getConstraints().get(1).getSourceDeclaration().getMember());
        assertTrue(property.getContainerElements().isEmpty());
        assertFalse(property.isCascaded());
    }

    @Test
    public void containerElementsFromBothDeclarationsAreMergedByPosition() throws Exception {
        Field field = Bean.class.getDeclaredField("tags");
        Method getter = getter("getTags");
        PropertyDefinition property = PropertyDefinition.load(field, getter, REGISTRY);

        assertEquals(List.of(NotEmpty.class), types(property.getConstraints()));
        assertEquals(1, property.getContainerElements().size());
        ContainerElementDefinition element = property.getContainerElements().get(0);
        assertEquals(List.class, element.getContainerType());
        assertEquals(0, element.getTypeArgumentIndex());
        // Field position constraints first, then getter position constraints; node kept from the field.
        assertEquals(List.of(NotBlank.class, Size.class), types(element.getConstraints()));
        assertEquals(field, element.getSourceDeclaration().getMember());
        assertEquals(field, element.getConstraints().get(0).getSourceDeclaration().getMember());
        assertEquals(getter, element.getConstraints().get(1).getSourceDeclaration().getMember());
        assertTrue(element.isCascaded());
        assertThrows(UnsupportedOperationException.class, () -> element.getConstraints().clear());
    }

    @Test
    public void nestedPositionsMergeToo() throws Exception {
        Field field = Bean.class.getDeclaredField("scores");
        PropertyDefinition property = PropertyDefinition.load(field, getter("getScores"), REGISTRY);

        assertEquals(1, property.getContainerElements().size());
        ContainerElementDefinition value = property.getContainerElements().get(0);
        assertEquals(Map.class, value.getContainerType());
        assertEquals(1, value.getTypeArgumentIndex());
        assertEquals(List.of(NotNull.class), types(value.getConstraints()));
        assertEquals(1, value.getChildren().size());
        assertEquals(List.of(Positive.class, Max.class), types(value.getChildren().get(0).getConstraints()));
    }

    @Test
    public void cascadeFromEitherDeclaration() throws Exception {
        assertTrue(PropertyDefinition.load(Bean.class.getDeclaredField("home"), getter("getHome"), REGISTRY).isCascaded());
        assertTrue(PropertyDefinition.load(Bean.class.getDeclaredField("work"), getter("getWork"), REGISTRY).isCascaded());
        assertFalse(PropertyDefinition.load(Bean.class.getDeclaredField("name"), getter("getName"), REGISTRY).isCascaded());
    }

    @Test
    public void unwrappedNodesFromBothDeclarationsMerge() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(Bean.class.getDeclaredField("age"), getter("getAge"), REGISTRY);
        assertTrue(property.getConstraints().isEmpty());
        assertEquals(1, property.getContainerElements().size());
        assertEquals(List.of(Positive.class, Min.class), types(property.getContainerElements().get(0).getConstraints()));
    }

    @Test
    public void unconstrainedDualProperty() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(Bean.class.getDeclaredField("note"), getter("getNote"), REGISTRY);
        assertFalse(property.isConstrained());
        assertTrue(property.getPropertySource().hasMultipleDeclarations());
    }

    @Test
    public void mismatchedDeclarationsAreRejected() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PropertyDefinition.load(Bean.class.getDeclaredField("name"), getter("getTags"), REGISTRY));
        assertTrue(e.getMessage().contains("different properties"));
    }

    // --- records ------------------------------------------------------------------------------

    @Test
    public void recordComponentIsReadOnce() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(component("name"), REGISTRY);
        assertEquals("name", property.getName());
        assertEquals(1, property.getPropertySource().getDeclarations().size());
        assertEquals(PropertyDeclaration.Kind.RECORD_COMPONENT, property.getPropertySource().getPrimary().getKind());
        assertEquals(Customer.class.getDeclaredMethod("name"), property.getPropertySource().getMember());
        assertEquals("name", property.getSource().getMethodName());
        assertEquals(String.class, property.getType());
        // Propagated to the field and to the type use; one definition each.
        assertEquals(List.of(NotBlank.class, Size.class), types(property.getConstraints()));
        assertTrue(property.toString().contains("name()"));
    }

    @Test
    public void recordComponentContainerElementsAndCascade() throws Exception {
        PropertyDefinition tags = PropertyDefinition.load(component("tags"), REGISTRY);
        assertTrue(tags.getConstraints().isEmpty());
        assertEquals(1, tags.getContainerElements().size());
        assertEquals(List.of(NotBlank.class), types(tags.getContainerElements().get(0).getConstraints()));
        assertEquals(Customer.class.getDeclaredMethod("tags"), tags.getContainerElements().get(0).getSourceDeclaration().getMember());

        PropertyDefinition address = PropertyDefinition.load(component("address"), REGISTRY);
        assertTrue(address.isCascaded());
        assertTrue(address.getConstraints().isEmpty());
    }

    @Test
    public void recordComponentUnwrapping() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(component("age"), REGISTRY);
        assertTrue(property.getConstraints().isEmpty());
        assertEquals(OptionalInt.class, property.getContainerElements().get(0).getContainerType());
        assertEquals(List.of(Positive.class), types(property.getContainerElements().get(0).getConstraints()));
    }

    @Test
    public void recordOnlyConstraintIsPickedUpFromTheComponent() throws Exception {
        // @Target(RECORD_COMPONENT) lands on the component, not on the backing field.
        PropertyDefinition property = PropertyDefinition.load(component("code"), REGISTRY);
        assertEquals(List.of(NotNull.class, RecordOnly.class), types(property.getConstraints()));
    }

    @Test
    public void groupConversionOnRecordComponentFailsFast() throws Exception {
        GroupConversionNotSupportedException e = assertThrows(GroupConversionNotSupportedException.class,
                () -> PropertyDefinition.load(component("converted"), REGISTRY));
        assertEquals(Customer.class.getDeclaredMethod("converted"), e.getSourceDeclaration().getMember());
    }

    @Test
    public void unconstrainedRecordComponent() throws Exception {
        assertFalse(PropertyDefinition.load(component("plain"), REGISTRY).isConstrained());
    }

    // --- hierarchy ----------------------------------------------------------------------------

    @Test
    public void constraintsOfEveryDeclarationInTheHierarchyAggregateInOrder() throws Exception {
        PropertySource source = discovered(Child.class, "label");
        PropertyDefinition property = PropertyDefinition.load(source, REGISTRY);

        assertEquals(5, source.getDeclarations().size());
        assertEquals(Child.class.getDeclaredMethod("getLabel"), property.getPropertySource().getMember());
        assertEquals("getLabel", property.getSource().getMethodName());
        assertEquals(String.class, property.getType());
        // Parent.label, Child.label, then Parent.getLabel (none), Child.getLabel, Labelled.getLabel.
        assertEquals(List.of(NotNull.class, Size.class, Size.class, Email.class, NotBlank.class), types(property.getConstraints()));
        assertEquals(List.of(Parent.class, Parent.class, Child.class, Child.class, Labelled.class),
                property.getConstraints().stream().<Class<?>>map(c -> c.getSourceDeclaration().getDeclaringClass()).toList());
        assertEquals(PropertyDeclaration.Kind.FIELD, property.getConstraints().get(2).getSourceDeclaration().getKind());
        assertEquals(PropertyDeclaration.Kind.GETTER, property.getConstraints().get(3).getSourceDeclaration().getKind());
        assertFalse(property.isCascaded());
    }

    @Test
    public void containerElementsMergeAcrossTheHierarchy() throws Exception {
        PropertyDefinition property = PropertyDefinition.load(discovered(Child.class, "scores"), REGISTRY);

        assertTrue(property.isCascaded());
        assertEquals(1, property.getContainerElements().size());
        ContainerElementDefinition element = property.getContainerElements().get(0);
        // Parent.scores type use, Child.getScores type use, Labelled.getScores type use.
        assertEquals(List.of(Positive.class, Min.class, Positive.class), types(element.getConstraints()));
        assertEquals(List.of(Parent.class, Child.class, Labelled.class),
                element.getConstraints().stream().<Class<?>>map(c -> c.getSourceDeclaration().getDeclaringClass()).toList());
    }

    private static PropertySource discovered(Class<?> beanClass, String name) {
        for (PropertySource source : PropertyDiscovery.discover(beanClass)) {
            if (source.getName().equals(name)) return source;
        }
        throw new IllegalArgumentException(name);
    }

    // --- arguments ----------------------------------------------------------------------------

    @Test
    public void nullArgumentsAreRejected() throws Exception {
        Field field = field("name");
        assertThrows(IllegalArgumentException.class, () -> PropertyDefinition.load((Field) null, REGISTRY));
        assertThrows(IllegalArgumentException.class, () -> PropertyDefinition.load((Method) null, REGISTRY));
        assertThrows(IllegalArgumentException.class, () -> PropertyDefinition.load((RecordComponent) null, REGISTRY));
        assertThrows(IllegalArgumentException.class, () -> PropertyDefinition.load((PropertySource) null, REGISTRY));
        assertThrows(IllegalArgumentException.class, () -> PropertyDefinition.load(field, (Method) null, REGISTRY));
        assertThrows(IllegalArgumentException.class, () -> PropertyDefinition.load(field, null));
    }

    private static PropertyDefinition load(String name) throws NoSuchFieldException {
        return PropertyDefinition.load(field(name), REGISTRY);
    }

    private static Field field(String name) throws NoSuchFieldException {
        return Sample.class.getDeclaredField(name);
    }

    private static Method getter(String name) throws NoSuchMethodException {
        return Bean.class.getDeclaredMethod(name);
    }

    private static RecordComponent component(String name) {
        for (RecordComponent component : Customer.class.getRecordComponents()) {
            if (component.getName().equals(name)) return component;
        }
        throw new IllegalArgumentException(name);
    }

    private static List<Class<?>> types(List<ConstraintDefinition> constraints) {
        return constraints.stream().<Class<?>>map(ConstraintDefinition::getAnnotationType).toList();
    }

    static class Address {}

    static class Order {}

    static class Pair<A, B> {
        A first;
        B second;
    }

    interface Box {}

    static class IntBox<T> implements Box {}

    @UnwrapByDefault
    static class BoxExtractor implements ValueExtractor<@ExtractedValue(type = Object.class) Box> {
        @Override
        public void extractValues(Box originalValue, ValueReceiver receiver) {}
    }

    @UnwrapByDefault
    static class IntBoxExtractor implements ValueExtractor<IntBox<@ExtractedValue ?>> {
        @Override
        public void extractValues(IntBox<?> originalValue, ValueReceiver receiver) {}
    }

    @SuppressWarnings("unused")
    private static class Sample {
        @NotBlank @Size(max = 10) private String name;
        private String plain;
        @Pattern(regexp = "a") @Pattern(regexp = "b") private String code;
        @NotNull private @TypeUseOnly String typeUseOnly;
        @Valid private Address address;
        @Valid @ConvertGroup(from = Default.class, to = Default.class) private Address converted;
        @Valid @ConvertGroup.List(@ConvertGroup(from = Default.class, to = Default.class)) private Address convertedList;
        private List<@Valid @ConvertGroup(from = Default.class, to = Default.class) Address> convertedElements;
        @NotEmpty private List<@NotBlank String> names;
        @NotEmpty @Size(max = 3) private String[] tags;
        @Valid private Address[] addresses;
        private Pair<String, @NotNull Integer> pair;
        @Positive @Max(10) private OptionalInt count;
        @NotNull(payload = Unwrapping.Skip.class) @Positive private OptionalInt skippedCount;
        @NotNull private IntBox<Integer> boxed;
        @NotNull(payload = Unwrapping.Skip.class) private IntBox<Integer> skippedBox;
        @NotNull(payload = Unwrapping.Unwrap.class) private IntBox<Integer> unwrappedBox;
        @NotNull private Optional<String> maybe;
        @NotBlank(payload = Unwrapping.Unwrap.class) private Optional<String> unwrappedMaybe;
        @NotBlank(payload = Unwrapping.Unwrap.class) private Optional<@Size(max = 5) String> mergedMaybe;
        @NotNull(payload = {Unwrapping.Unwrap.class, Unwrapping.Skip.class}) private Optional<String> contradiction;
        @NotBlank(payload = Unwrapping.Unwrap.class) private String unwrappedString;
        @Size(min = 1, payload = Unwrapping.Unwrap.class) private Map<String, String> unwrappedMap;
    }

    @SuppressWarnings("unused")
    private static class Bean {
        @NotNull private String name;
        @NotEmpty private List<@NotBlank String> tags;
        private Map<String, List<@Positive Integer>> scores;
        @Valid private Address home;
        private Address work;
        @Positive private OptionalInt age;
        private String note;
        private String email;
        private boolean active;
        private List<Order> orders;

        @Size(max = 10) public String getName() { return name; }
        public List<@Size(max = 3) @Valid String> getTags() { return tags; }
        public Map<String, @NotNull List<@Max(100) Integer>> getScores() { return scores; }
        public Address getHome() { return home; }
        @Valid public Address getWork() { return work; }
        @Min(18) public OptionalInt getAge() { return age; }
        public String getNote() { return note; }
        @NotBlank @Email public String getEmail() { return email; }
        @AssertTrue public boolean isActive() { return active; }
        @NotEmpty public List<@Valid Order> getOrders() { return orders; }
        @Valid @ConvertGroup(from = Default.class, to = Default.class) public Address getConverted() { return home; }

        // Not getters.
        public Boolean isWrapped() { return Boolean.TRUE; }
        public String get() { return name; }
        public void getNothing() {}
        public static String getStatic() { return ""; }
        public String compute() { return name; }
        public String isOn() { return name; }
        public String getWith(String prefix) { return prefix + name; }
    }

    @SuppressWarnings("unused")
    private record Customer(@NotBlank @Size(max = 10) String name,
                            List<@NotBlank String> tags,
                            @Valid Address address,
                            @Positive OptionalInt age,
                            @NotNull @RecordOnly String code,
                            @Valid @ConvertGroup(from = Default.class, to = Default.class) Address converted,
                            String plain) {}

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @interface TypeUseOnly {
        String message() default "type use only";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Target(ElementType.RECORD_COMPONENT)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @interface RecordOnly {
        String message() default "record component only";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
    private interface Labelled {
        @NotBlank String getLabel();
        default List<@Positive Integer> getScores() { return List.of(); }
    }

    @SuppressWarnings("unused")
    private static class Parent implements Labelled {
        @NotNull @Size(max = 20) private String label;
        @Valid private List<@Positive Integer> scores;
        @Override public String getLabel() { return label; }
        @Override public List<Integer> getScores() { return scores; }
    }

    @SuppressWarnings("unused")
    private static class Child extends Parent {
        @Size(max = 10) private String label;
        @Override @Email public String getLabel() { return label; }
        @Override public List<@Min(1) Integer> getScores() { return List.of(); }
    }
}
