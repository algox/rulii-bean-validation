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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.model.ValueDeclaration;
import org.rulii.validation.beans.model.ValueDeclaration.Kind;
import org.rulii.validation.beans.model.PropertyDiscovery;
import org.rulii.validation.beans.model.PropertySource;
import org.rulii.validation.beans.util.reflect.TypeHierarchies;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PropertyDiscovery}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class PropertyDiscoveryTest {

    public PropertyDiscoveryTest() {
        super();
    }

    // --- ordering -----------------------------------------------------------------------------

    @Test
    public void fieldPropertiesComeBeforeGetterOnlyProperties() throws Exception {
        List<PropertySource> sources = PropertyDiscovery.discover(Simple.class);
        assertEquals(List.of("name", "age", "label"), names(sources));

        PropertySource name = sources.get(0);
        assertEquals(List.of(ValueDeclaration.ofField(Simple.class.getDeclaredField("name")),
                ValueDeclaration.ofGetter(Simple.class.getDeclaredMethod("getName"))), name.getDeclarations());
        assertEquals(Kind.GETTER, name.getPrimary().getKind());
        assertEquals(1, sources.get(1).getDeclarations().size());
        assertEquals(Kind.GETTER, sources.get(2).getPrimary().getKind());
    }

    @Test
    public void fieldsFollowTheHierarchyTopDown() {
        assertEquals(List.of("id", "label", "code", "summary"), names(PropertyDiscovery.discover(Leaf.class)));
    }

    @Test
    public void emptyClassesAndInterfacesWork() {
        assertEquals(List.of(), PropertyDiscovery.discover(Empty.class));
        assertEquals(List.of(), PropertyDiscovery.discover(Object.class));
        List<PropertySource> sources = PropertyDiscovery.discover(Named.class);
        assertEquals(List.of("name"), names(sources));
        assertEquals(Named.class, sources.get(0).getDeclaringClass());
    }

    @Test
    public void nullIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PropertyDiscovery.discover(null));
    }

    @Test
    public void discoveryIsAVisitorUsableFromAnotherWalk() {
        PropertyDiscovery discovery = new PropertyDiscovery(Employee.class);
        assertEquals(List.of(), discovery.getSources());

        assertEquals(Employee.class, discovery.getOwner());
        assertThrows(IllegalArgumentException.class, () -> new PropertyDiscovery(null));
        TypeHierarchies.walk(Employee.class, discovery);
        assertEquals(PropertyDiscovery.discover(Employee.class), discovery.getSources());
        assertThrows(IllegalArgumentException.class, () -> discovery.visitField(null));
        assertThrows(IllegalArgumentException.class, () -> discovery.visitMethod(null));
        assertThrows(IllegalArgumentException.class, () -> discovery.visitRecordComponent(null));
    }

    // --- hierarchy ----------------------------------------------------------------------------

    @Test
    public void shadowedFieldsAreOnePropertyWithTheSubclassFieldPrimary() throws Exception {
        Map<String, PropertySource> sources = byName(PropertyDiscovery.discover(Employee.class));
        PropertySource name = sources.get("name");

        assertEquals(List.of(ValueDeclaration.ofField(Person.class.getDeclaredField("name"), Employee.class),
                ValueDeclaration.ofField(Employee.class.getDeclaredField("name"), Employee.class)), name.getDeclarations(Kind.FIELD));
        assertTrue(name.getDeclarations().get(0).isAnnotationPresent(NotNull.class));
        assertTrue(name.getDeclarations().get(1).isAnnotationPresent(Size.class));
        // A getter exists for name, so it is primary; code has no getter and the subclass field wins.
        assertEquals(Person.class.getDeclaredMethod("getName"), name.getMember());

        PropertySource code = sources.get("code");
        assertEquals(List.of(ValueDeclaration.ofField(Person.class.getDeclaredField("code"), Employee.class),
                ValueDeclaration.ofField(Employee.class.getDeclaredField("code"), Employee.class)), code.getDeclarations());
        assertEquals(Employee.class, code.getDeclaringClass());
        assertEquals(Kind.FIELD, code.getPrimary().getKind());
        assertTrue(code.hasMultipleDeclarations());
    }

    @Test
    public void overriddenGettersAreOnePropertyWithTheOverridePrimaryAndNoBridgeDuplicate() throws Exception {
        Map<String, PropertySource> sources = byName(PropertyDiscovery.discover(Employee.class));
        PropertySource id = sources.get("id");

        assertEquals(List.of(ValueDeclaration.ofField(Person.class.getDeclaredField("id"), Employee.class),
                ValueDeclaration.ofGetter(Person.class.getDeclaredMethod("getId"), Employee.class),
                ValueDeclaration.ofGetter(Employee.class.getDeclaredMethod("getId"), Employee.class)), id.getDeclarations());
        assertEquals(Employee.class.getDeclaredMethod("getId"), id.getMember());
        assertEquals(Integer.class, id.getType());
    }

    @Test
    public void interfaceGettersIncludingDefaultMethodsParticipate() throws Exception {
        Map<String, PropertySource> sources = byName(PropertyDiscovery.discover(Employee.class));

        PropertySource name = sources.get("name");
        assertEquals(List.of(Kind.FIELD, Kind.FIELD, Kind.GETTER, Kind.GETTER), kinds(name));
        assertEquals(Person.class.getDeclaredMethod("getName"), name.getDeclarations().get(2).getMember());
        assertEquals(Named.class.getDeclaredMethod("getName"), name.getDeclarations().get(3).getMember());
        assertEquals(Person.class.getDeclaredMethod("getName"), name.getMember());

        PropertySource rank = sources.get("rank");
        assertEquals(List.of(Kind.GETTER), kinds(rank));
        assertEquals(Ranked.class.getDeclaredMethod("getRank"), rank.getMember());
        assertTrue(rank.getPrimary().isAnnotationPresent(Positive.class));
    }

    @Test
    public void diamondInterfacesAreVisitedOnce() {
        Map<String, PropertySource> sources = byName(PropertyDiscovery.discover(Diamond.class));
        assertEquals(List.of("name", "rank"), List.copyOf(sources.keySet()).stream().sorted().toList());
        assertEquals(1, sources.get("name").getDeclarations().size());
        assertEquals(1, sources.get("rank").getDeclarations().size());
    }

    @Test
    public void staticAndSyntheticMembersAndObjectAreIgnored() {
        Map<String, PropertySource> sources = byName(PropertyDiscovery.discover(Inner.class));
        assertEquals(List.of("value"), List.copyOf(sources.keySet()));
        assertFalse(sources.containsKey("class"));
        assertFalse(sources.containsKey("instance"));
        assertFalse(sources.containsKey("COUNT"));
    }

    @Test
    public void isAndGetAccessorsOfTheSameNameAreOneProperty() throws Exception {
        PropertySource active = byName(PropertyDiscovery.discover(Flags.class)).get("active");
        assertEquals(2, active.getDeclarations().size());
        assertEquals(List.of(Kind.GETTER, Kind.GETTER), kinds(active));
        assertTrue(List.of(Flags.class.getDeclaredMethod("isActive"), Flags.class.getDeclaredMethod("getActive"))
                .contains(active.getMember()));
    }

    // --- records ------------------------------------------------------------------------------

    @Test
    public void recordsContributeComponentsAndInterfaceGetters() throws Exception {
        List<PropertySource> sources = PropertyDiscovery.discover(Customer.class);
        assertEquals(List.of("name", "age", "rank"), names(sources));

        PropertySource name = sources.get(0);
        assertEquals(List.of(Kind.RECORD_COMPONENT, Kind.GETTER, Kind.GETTER), kinds(name));
        assertEquals(Kind.RECORD_COMPONENT, name.getPrimary().getKind());
        assertEquals(Customer.class.getRecordComponents()[0].getAccessor(), name.getMember());
        assertEquals(Customer.class.getDeclaredMethod("getName"), name.getDeclarations().get(1).getMember());
        assertEquals(Named.class.getDeclaredMethod("getName"), name.getDeclarations().get(2).getMember());
        assertTrue(name.getPrimary().isAnnotationPresent(NotBlank.class));

        assertEquals(List.of(Kind.RECORD_COMPONENT), kinds(sources.get(1)));
        assertEquals(Ranked.class.getDeclaredMethod("getRank"), sources.get(2).getMember());
    }

    @Test
    public void constrainedRecordAccessorsDoNotFailFast() {
        // javac copies the component constraint onto the accessor; the accessor is the component, not a stray method.
        assertDoesNotThrow(() -> PropertyDiscovery.discover(Customer.class));
    }

    // --- non-getters ----------------------------------------------------------------------------

    @Test
    public void constrainedNonGettersAreNotProperties() {
        // Their constraints belong to executable validation; discovery leaves them alone.
        assertEquals(List.of(), names(PropertyDiscovery.discover(ConstrainedMethod.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(ValidMethod.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(ConvertGroupMethod.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(ContainerMethod.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(TypeUseMethod.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(TypeUseArrayMethod.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(SubOfConstrainedMethod.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(ImplementsConstrainedMethod.class)));
    }

    @Test
    public void getterShapedMethodWithParametersOrWrapperBooleanIsNotAGetter() {
        assertEquals(List.of(), names(PropertyDiscovery.discover(ParameterizedGetter.class)));
        assertEquals(List.of(), names(PropertyDiscovery.discover(WrapperIs.class)));
    }

    @Test
    public void unconstrainedNonGettersAndStaticConstrainedMethodsAreIgnored() {
        assertDoesNotThrow(() -> PropertyDiscovery.discover(HarmlessMethods.class));
        assertEquals(List.of("name"), names(PropertyDiscovery.discover(HarmlessMethods.class)));
    }

    // --- helpers ------------------------------------------------------------------------------

    private static List<String> names(List<PropertySource> sources) {
        return sources.stream().map(PropertySource::getName).toList();
    }

    private static Map<String, PropertySource> byName(List<PropertySource> sources) {
        return sources.stream().collect(Collectors.toMap(PropertySource::getName, Function.identity()));
    }

    private static List<Kind> kinds(PropertySource source) {
        return source.getDeclarations().stream().map(ValueDeclaration::getKind).toList();
    }

    // --- fixtures -----------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private static class Simple {
        @NotNull private String name;
        @Min(0) private int age;
        @Size(max = 10) public String getName() { return name; }
        @NotBlank public String getLabel() { return name; }
    }

    private static class Empty { }

    @SuppressWarnings("unused")
    private static class Base { private Number id; public Number getId() { return id; } }
    @SuppressWarnings("unused")
    private static class Middle extends Base { private String label; public String getSummary() { return label; } }
    @SuppressWarnings("unused")
    private static class Leaf extends Middle { private String code; }

    private interface Named { @NotNull String getName(); }
    private interface Ranked { @Positive default int getRank() { return 1; } }
    private interface Audited extends Named, Ranked { }

    @SuppressWarnings("unused")
    private static class Person implements Named {
        @NotNull private String name;
        private Number id;
        private String code;
        @Override public String getName() { return name; }
        public Number getId() { return id; }
    }

    @SuppressWarnings("unused")
    private static class Employee extends Person implements Ranked {
        @Size(max = 10) private String name;
        private String code;
        @Override public Integer getId() { return 1; }
    }

    private abstract static class Diamond implements Audited, Named, Ranked { }

    @SuppressWarnings("unused")
    private class Inner {
        private static int COUNT;
        private static Inner instance;
        private String value;
        public static Inner getInstance() { return instance; }
    }

    @SuppressWarnings("unused")
    private static class Flags {
        public boolean isActive() { return true; }
        public Boolean getActive() { return true; }
    }

    @SuppressWarnings("unused")
    private record Customer(@NotBlank String name, @Min(18) int age) implements Named, Ranked {
        @Override public String getName() { return name; }
    }

    @SuppressWarnings("unused")
    private static class ConstrainedMethod { @NotNull public Number calculateTotal() { return 0; } }
    private static class SubOfConstrainedMethod extends ConstrainedMethod { }
    private interface ConstrainedInterface { @NotNull Object compute(); }
    private static class ImplementsConstrainedMethod implements ConstrainedInterface {
        @Override public Object compute() { return null; }
    }
    @SuppressWarnings("unused")
    private static class ValidMethod { @Valid public Object compute() { return null; } }
    @SuppressWarnings("unused")
    private static class ConvertGroupMethod { @ConvertGroup(from = Default.class, to = Default.class) public Object compute() { return null; } }
    @SuppressWarnings("unused")
    private static class ContainerMethod { @Size.List({@Size(min = 1), @Size(max = 2)}) public String compute() { return ""; } }
    @SuppressWarnings("unused")
    private static class TypeUseMethod { public List<@NotBlank String> values() { return List.of(); } }
    @SuppressWarnings("unused")
    private static class TypeUseArrayMethod { public @Max(1) int[] values() { return new int[0]; } }
    @SuppressWarnings("unused")
    private static class ParameterizedGetter { @NotNull public String getName(String locale) { return locale; } }
    @SuppressWarnings("unused")
    private static class WrapperIs { @NotNull public Boolean isActive() { return true; } }
    @SuppressWarnings("unused")
    private static class HarmlessMethods {
        private String name;
        public String compute() { return name; }
        public void run() { }
        @NotNull public static String getStaticName() { return ""; }
        @NotNull public static String create() { return ""; }
    }
}
