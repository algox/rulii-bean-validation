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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.model.PropertyDeclaration;
import org.rulii.validation.beans.model.PropertyDeclaration.Kind;
import org.rulii.validation.beans.model.PropertySource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PropertySource}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class PropertySourceTest {

    public PropertySourceTest() {
        super();
    }

    @Test
    public void fieldOnly() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        PropertySource source = PropertySource.ofField(field);
        assertEquals("name", source.getName());
        assertEquals(List.of(PropertyDeclaration.ofField(field)), source.getDeclarations());
        assertEquals(PropertyDeclaration.ofField(field), source.getPrimary());
        assertFalse(source.hasMultipleDeclarations());
        assertEquals(field, source.getMember());
        assertEquals(Bean.class, source.getDeclaringClass());
        assertEquals(String.class, source.getType());
        assertEquals(String.class, source.getGenericType());
        assertEquals("name", source.getSourceDefinition().getFieldName());
        assertEquals(Bean.class.getName() + ".name", source.describe());
        assertTrue(source.toString().contains("FIELD"));
    }

    @Test
    public void getterOnly() throws Exception {
        Method getter = Bean.class.getDeclaredMethod("getName");
        PropertySource source = PropertySource.ofGetter(getter);
        assertEquals("name", source.getName());
        assertEquals(PropertyDeclaration.ofGetter(getter), source.getPrimary());
        assertFalse(source.hasMultipleDeclarations());
        assertEquals(getter, source.getMember());
        assertEquals("getName", source.getSourceDefinition().getMethodName());
    }

    @Test
    public void recordComponent() {
        RecordComponent component = Customer.class.getRecordComponents()[0];
        PropertySource source = PropertySource.ofRecordComponent(component);
        assertEquals("name", source.getName());
        assertEquals(Kind.RECORD_COMPONENT, source.getPrimary().getKind());
        assertEquals(component.getAccessor(), source.getMember());
        assertFalse(source.hasMultipleDeclarations());
    }

    @Test
    public void fieldAndGetterGetterIsPrimary() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        Method getter = Bean.class.getDeclaredMethod("getName");
        PropertySource source = PropertySource.of(field, getter);
        assertEquals("name", source.getName());
        assertTrue(source.hasMultipleDeclarations());
        assertEquals(List.of(PropertyDeclaration.ofField(field), PropertyDeclaration.ofGetter(getter)), source.getDeclarations());
        assertEquals(PropertyDeclaration.ofGetter(getter), source.getPrimary());
        assertEquals(getter, source.getMember());
        assertEquals("getName", source.getSourceDefinition().getMethodName());
        assertEquals(Bean.class.getName() + ".getName()", source.describe());
        assertThrows(UnsupportedOperationException.class, () -> source.getDeclarations().clear());
    }

    @Test
    public void declarationOrderIsFieldThenGetterRegardlessOfInput() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        Method getter = Bean.class.getDeclaredMethod("getName");
        PropertySource source = PropertySource.of(List.of(PropertyDeclaration.ofGetter(getter), PropertyDeclaration.ofField(field)));
        assertEquals(List.of(PropertyDeclaration.ofField(field), PropertyDeclaration.ofGetter(getter)), source.getDeclarations());
        assertEquals(PropertySource.of(field, getter), source);
        assertEquals(PropertySource.of(field, getter).hashCode(), source.hashCode());
    }

    @Test
    public void primaryTypeWinsWhenDeclaredTypesDiffer() throws Exception {
        // Field declares CharSequence, getter narrows to String: the getter is the public contract.
        Field field = Bean.class.getDeclaredField("label");
        Method getter = Bean.class.getDeclaredMethod("getLabel");
        PropertySource source = PropertySource.of(field, getter);
        assertEquals(String.class, source.getType());
        assertEquals(CharSequence.class, source.getDeclarations().get(0).getType());
    }

    // --- hierarchy ----------------------------------------------------------------------------

    @Test
    public void shadowedFieldsKeepInputOrderAndTheSubclassFieldIsPrimary() throws Exception {
        PropertyDeclaration parent = PropertyDeclaration.ofField(Parent.class.getDeclaredField("name"));
        PropertyDeclaration child = PropertyDeclaration.ofField(Child.class.getDeclaredField("name"));

        PropertySource source = PropertySource.of(List.of(parent, child));
        assertEquals(List.of(parent, child), source.getDeclarations());
        assertEquals(child, source.getPrimary());
        assertEquals(Child.class, source.getDeclaringClass());
        assertTrue(source.hasMultipleDeclarations());

        // The most derived field wins whatever the input order; the order itself is kept as given.
        source = PropertySource.of(List.of(child, parent));
        assertEquals(List.of(child, parent), source.getDeclarations());
        assertEquals(child, source.getPrimary());
    }

    @Test
    public void overriddenGettersMostDerivedIsPrimary() throws Exception {
        PropertyDeclaration field = PropertyDeclaration.ofField(Parent.class.getDeclaredField("id"));
        PropertyDeclaration parent = PropertyDeclaration.ofGetter(Parent.class.getDeclaredMethod("getId"));
        PropertyDeclaration child = PropertyDeclaration.ofGetter(Child.class.getDeclaredMethod("getId"));
        PropertyDeclaration iface = PropertyDeclaration.ofGetter(Identified.class.getDeclaredMethod("getId"));

        PropertySource source = PropertySource.of(List.of(iface, child, field, parent));
        assertEquals(List.of(field, iface, child, parent), source.getDeclarations());
        assertEquals(child, source.getPrimary());
        assertEquals(Integer.class, source.getType());
        assertEquals(List.of(field), source.getDeclarations(Kind.FIELD));
        assertEquals(List.of(iface, child, parent), source.getDeclarations(Kind.GETTER));
        assertEquals(List.of(), source.getDeclarations(Kind.RECORD_COMPONENT));
        assertThrows(UnsupportedOperationException.class, () -> source.getDeclarations(Kind.GETTER).clear());
    }

    @Test
    public void unrelatedDeclaringTypesFirstGivenWins() throws Exception {
        PropertyDeclaration a = PropertyDeclaration.ofGetter(Identified.class.getDeclaredMethod("getId"));
        PropertyDeclaration b = PropertyDeclaration.ofGetter(Keyed.class.getDeclaredMethod("getId"));
        assertEquals(a, PropertySource.of(List.of(a, b)).getPrimary());
        assertEquals(b, PropertySource.of(List.of(b, a)).getPrimary());
    }

    @Test
    public void sameClassIsAndGetAccessorsFirstGivenWins() throws Exception {
        PropertyDeclaration is = PropertyDeclaration.ofGetter(Flags.class.getDeclaredMethod("isActive"));
        PropertyDeclaration get = PropertyDeclaration.ofGetter(Flags.class.getDeclaredMethod("getActive"));
        PropertySource source = PropertySource.of(List.of(is, get));
        assertEquals(List.of(is, get), source.getDeclarations());
        assertEquals(is, source.getPrimary());
        assertEquals(boolean.class, source.getType());
    }

    @Test
    public void recordComponentMayBeJoinedByGettersAndStaysPrimary() throws Exception {
        PropertyDeclaration component = PropertyDeclaration.ofRecordComponent(Customer.class.getRecordComponents()[0]);
        PropertyDeclaration own = PropertyDeclaration.ofGetter(Customer.class.getDeclaredMethod("getName"));
        PropertyDeclaration iface = PropertyDeclaration.ofGetter(Named.class.getDeclaredMethod("getName"));

        PropertySource source = PropertySource.of(List.of(own, component, iface));
        assertEquals(List.of(component, own, iface), source.getDeclarations());
        assertEquals(component, source.getPrimary());
        assertEquals(Customer.class.getDeclaredMethod("name"), source.getMember());
        assertTrue(source.hasMultipleDeclarations());
    }

    @Test
    public void mismatchedDeclarationsAreRejected() throws Exception {
        Field name = Bean.class.getDeclaredField("name");
        Field label = Bean.class.getDeclaredField("label");
        Method getLabel = Bean.class.getDeclaredMethod("getLabel");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PropertySource.of(name, getLabel));
        assertTrue(e.getMessage().contains("different properties"));

        e = assertThrows(IllegalArgumentException.class,
                () -> PropertySource.of(List.of(PropertyDeclaration.ofField(name), PropertyDeclaration.ofField(name))));
        assertTrue(e.getMessage().contains("same declaration twice"));

        assertThrows(IllegalArgumentException.class,
                () -> PropertySource.of(List.of(PropertyDeclaration.ofField(name), PropertyDeclaration.ofField(label))));

        e = assertThrows(IllegalArgumentException.class, () -> PropertySource.of(List.of(
                PropertyDeclaration.ofRecordComponent(Customer.class.getRecordComponents()[0]),
                PropertyDeclaration.ofField(Customer.class.getDeclaredField("name")))));
        assertTrue(e.getMessage().contains("record component"));

        e = assertThrows(IllegalArgumentException.class, () -> PropertySource.of(List.of(
                PropertyDeclaration.ofRecordComponent(Customer.class.getRecordComponents()[0]),
                PropertyDeclaration.ofRecordComponent(Other.class.getRecordComponents()[0]))));
        assertTrue(e.getMessage().contains("two record components"));
    }

    @Test
    public void nonGetterIsRejected() throws Exception {
        Field name = Bean.class.getDeclaredField("name");
        assertThrows(IllegalArgumentException.class, () -> PropertySource.ofGetter(Bean.class.getDeclaredMethod("compute")));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.of(name, Bean.class.getDeclaredMethod("compute")));
    }

    @Test
    public void equality() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        Method getter = Bean.class.getDeclaredMethod("getName");
        assertEquals(PropertySource.ofField(field), PropertySource.ofField(field));
        assertNotEquals(PropertySource.ofField(field), PropertySource.ofGetter(getter));
        assertNotEquals(PropertySource.ofField(field), PropertySource.of(field, getter));
        assertNotEquals(PropertySource.ofField(field), null);
    }

    @Test
    public void nullArgumentsAreRejected() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        assertThrows(IllegalArgumentException.class, () -> PropertySource.ofField(null));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.ofGetter(null));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.ofRecordComponent(null));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.of(null, Bean.class.getDeclaredMethod("getName")));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.of(field, null));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.of((List<PropertyDeclaration>) null));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.of(Arrays.asList((PropertyDeclaration) null)));
        assertThrows(IllegalArgumentException.class, () -> PropertySource.ofField(field).getDeclarations(null));
    }

    @SuppressWarnings("unused")
    private static class Bean {
        @NotNull private String name;
        private CharSequence label;

        @Size(max = 10) public String getName() { return name; }
        public String getLabel() { return label.toString(); }
        public String compute() { return name; }
    }

    private interface Identified { Number getId(); }
    private interface Keyed { Object getId(); }
    private interface Named { String getName(); }

    @SuppressWarnings("unused")
    private static class Parent implements Identified {
        private String name;
        private Number id;
        @Override public Number getId() { return id; }
    }

    @SuppressWarnings("unused")
    private static class Child extends Parent {
        private String name;
        @Override public Integer getId() { return 1; }
    }

    @SuppressWarnings("unused")
    private static class Flags {
        public boolean isActive() { return true; }
        public Boolean getActive() { return true; }
    }

    @SuppressWarnings("unused")
    private record Customer(@NotBlank @Size(max = 10) String name) implements Named {
        @Override public String getName() { return name; }
    }

    @SuppressWarnings("unused")
    private record Other(String name) {}
}
