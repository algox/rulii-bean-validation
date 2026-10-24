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
import org.rulii.validation.beans.model.ValueDeclaration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ValueDeclaration}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValueDeclarationTest {

    public ValueDeclarationTest() {
        super();
    }

    @Test
    public void fieldSource() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        ValueDeclaration source = ValueDeclaration.ofField(field);
        assertEquals(ValueDeclaration.Kind.FIELD, source.getKind());
        assertEquals("name", source.getName());
        assertEquals(field, source.getMember());
        assertEquals(field, source.getDeclaration());
        assertEquals(Bean.class, source.getDeclaringClass());
        assertEquals(String.class, source.getType());
        assertEquals(String.class, source.getGenericType());
        assertEquals(field.getAnnotatedType(), source.getAnnotatedType());
        assertEquals(List.of(NotNull.class), types(source.getDeclaredAnnotations()));
        assertTrue(source.isAnnotationPresent(NotNull.class));
        assertFalse(source.isAnnotationPresent(Size.class));
        assertEquals("name", source.getSourceDefinition().getFieldName());
        assertEquals(Bean.class.getName() + ".name", source.describe());
        assertEquals("FIELD " + Bean.class.getName() + ".name", source.toString());
    }

    @Test
    public void getterSource() throws Exception {
        Method getter = Bean.class.getDeclaredMethod("getName");
        ValueDeclaration source = ValueDeclaration.ofGetter(getter);
        assertEquals(ValueDeclaration.Kind.GETTER, source.getKind());
        assertEquals("name", source.getName());
        assertEquals(getter, source.getMember());
        assertEquals(getter, source.getDeclaration());
        assertEquals(String.class, source.getType());
        assertEquals(getter.getAnnotatedReturnType(), source.getAnnotatedType());
        assertEquals(List.of(Size.class), types(source.getDeclaredAnnotations()));
        assertEquals("getName", source.getSourceDefinition().getMethodName());
        assertEquals(Bean.class.getName() + ".getName()", source.describe());
    }

    @Test
    public void getterNames() throws Exception {
        assertEquals("name", ValueDeclaration.propertyName(Bean.class.getDeclaredMethod("getName")));
        assertEquals("active", ValueDeclaration.propertyName(Bean.class.getDeclaredMethod("isActive")));
        assertEquals("URL", ValueDeclaration.propertyName(Bean.class.getDeclaredMethod("getURL")));
        assertEquals("x", ValueDeclaration.propertyName(Bean.class.getDeclaredMethod("getX")));
        assertEquals("xRay", ValueDeclaration.propertyName(Bean.class.getDeclaredMethod("getxRay")));
    }

    @Test
    public void getterShapes() throws Exception {
        assertTrue(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("getName")));
        assertTrue(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("isActive")));
        assertTrue(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("getURL")));
        assertTrue(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("getPrivately")));
        assertFalse(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("isWrapped")));
        assertFalse(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("get")));
        assertFalse(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("is")));
        assertFalse(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("getNothing")));
        assertFalse(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("getStatic")));
        assertFalse(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("getWith", String.class)));
        assertFalse(ValueDeclaration.isGetter(Bean.class.getDeclaredMethod("compute")));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofGetter(Bean.class.getDeclaredMethod("compute")));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.propertyName(Bean.class.getDeclaredMethod("compute")));
    }

    @Test
    public void recordComponentSource() throws Exception {
        RecordComponent component = Customer.class.getRecordComponents()[0];
        ValueDeclaration source = ValueDeclaration.ofRecordComponent(component);
        assertEquals(ValueDeclaration.Kind.RECORD_COMPONENT, source.getKind());
        assertEquals("name", source.getName());
        assertEquals(Customer.class.getDeclaredMethod("name"), source.getMember());
        assertEquals(component, source.getDeclaration());
        assertEquals(Customer.class, source.getDeclaringClass());
        assertEquals(String.class, source.getType());
        assertEquals(Customer.class.getDeclaredField("name").getAnnotatedType(), source.getAnnotatedType());
        // Jakarta constraints are not applicable to RECORD_COMPONENT: they are read off the backing field.
        assertTrue(component.getDeclaredAnnotations().length == 0);
        assertEquals(List.of(NotBlank.class, Size.class), types(source.getDeclaredAnnotations()));
        assertTrue(source.isAnnotationPresent(NotBlank.class));
        assertEquals("name", source.getSourceDefinition().getMethodName());
        assertEquals(Customer.class.getName() + ".name()", source.describe());
    }

    @Test
    public void equality() throws Exception {
        ValueDeclaration a = ValueDeclaration.ofField(Bean.class.getDeclaredField("name"));
        ValueDeclaration b = ValueDeclaration.ofField(Bean.class.getDeclaredField("name"));
        ValueDeclaration c = ValueDeclaration.ofGetter(Bean.class.getDeclaredMethod("getName"));
        ValueDeclaration d = ValueDeclaration.ofRecordComponent(Customer.class.getRecordComponents()[0]);
        ValueDeclaration e = ValueDeclaration.ofRecordComponent(Customer.class.getRecordComponents()[0]);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertEquals(d, e);
        assertEquals(d.hashCode(), e.hashCode());
    }

    @Test
    public void genericTypesAreResolvedAgainstTheOwner() throws Exception {
        Field items = Wrapper.class.getDeclaredField("items");
        ValueDeclaration declaration = ValueDeclaration.ofField(items, Names.class);

        assertEquals(Names.class, declaration.getOwner());
        assertEquals(Wrapper.class, declaration.getDeclaringClass());
        assertEquals(List.class, declaration.getType());
        assertEquals("java.util.List<java.lang.String>", declaration.getGenericType().getTypeName());
        assertEquals("java.util.List<T>", declaration.getDeclaredGenericType().getTypeName());
        assertEquals(items.getAnnotatedType(), declaration.getAnnotatedType(), "annotations stay on the declared tree");

        assertEquals(String.class, ValueDeclaration.ofField(Wrapper.class.getDeclaredField("single"), Names.class).getType());
        assertEquals(String[].class, ValueDeclaration.ofField(Wrapper.class.getDeclaredField("array"), Names.class).getType());
        assertEquals(String.class, ValueDeclaration.ofGetter(Wrapper.class.getDeclaredMethod("getSingle"), Names.class).getType());
        assertEquals(String.class, declaration.resolve(Wrapper.class.getTypeParameters()[0]));
        assertEquals(Integer.class, ValueDeclaration.ofGetter(Identified.class.getDeclaredMethod("getId"), Leaf.class).getType());
    }

    @Test
    public void withoutAnOwnerTheDeclaringClassIsTheOwnerAndVariablesErase() throws Exception {
        ValueDeclaration declaration = ValueDeclaration.ofField(Wrapper.class.getDeclaredField("single"));
        assertEquals(Wrapper.class, declaration.getOwner());
        assertEquals(Object.class, declaration.getType());
        assertTrue(declaration.getGenericType() instanceof java.lang.reflect.TypeVariable);
        assertEquals(declaration.getGenericType(), declaration.getDeclaredGenericType());
    }

    @Test
    public void ownerMattersForEqualityAndMustBeASubtype() throws Exception {
        Field single = Wrapper.class.getDeclaredField("single");
        assertNotEquals(ValueDeclaration.ofField(single), ValueDeclaration.ofField(single, Names.class));
        assertEquals(ValueDeclaration.ofField(single, Names.class), ValueDeclaration.ofField(single, Names.class));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofField(single, Bean.class));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofField(single, null));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofGetter(Wrapper.class.getDeclaredMethod("getSingle"), Bean.class));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofField(single).resolve(null));
    }

    @Test
    public void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofField(null));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofGetter(null));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.ofRecordComponent(null));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.isGetter(null));
        assertThrows(IllegalArgumentException.class, () -> ValueDeclaration.propertyName(null));
    }

    private static List<Class<?>> types(List<Annotation> annotations) {
        return annotations.stream().<Class<?>>map(Annotation::annotationType).toList();
    }

    @SuppressWarnings("unused")
    private static class Bean {
        @NotNull private String name;
        private boolean active;

        @Size(max = 10) public String getName() { return name; }
        public boolean isActive() { return active; }
        public String getURL() { return name; }
        public String getX() { return name; }
        public String getxRay() { return name; }
        private String getPrivately() { return name; }

        public Boolean isWrapped() { return Boolean.TRUE; }
        public String get() { return name; }
        public boolean is() { return active; }
        public void getNothing() {}
        public static String getStatic() { return ""; }
        public String getWith(String prefix) { return prefix + name; }
        public String compute() { return name; }
    }

    @SuppressWarnings("unused")
    private record Customer(@NotBlank @Size(max = 10) String name) {}
    @SuppressWarnings("unused")
    private static class Wrapper<T> {
        private T single;
        private List<T> items;
        private T[] array;
        public T getSingle() { return single; }
    }

    private static class Names extends Wrapper<String> { }

    private interface Identified<ID> { ID getId(); }
    private static class Leaf implements Identified<Integer> { @Override public Integer getId() { return 1; } }
}
