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
package org.rulii.test.validation.beans.assembly;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Binding;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.PropertyAccessor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.PropertyDeclaration;
import org.rulii.validation.beans.model.PropertyDeclaration.Kind;
import org.rulii.validation.beans.model.PropertyDefinition;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BindingPlan}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BindingPlanTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    public BindingPlanTest() {
        super();
    }

    @Test
    public void simplePropertiesBindUnderTheirNames() {
        BeanDefinition bean = BeanDefinition.load(Simple.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);

        assertSame(bean, plan.getBean());
        assertEquals(List.of("name", "count"), List.copyOf(plan.getBindings().keySet()));
        assertEquals("name", plan.getBindingName(bean.getProperty("name")));
        assertSame(bean.getProperty("name"), plan.getBindings().get("name"));
        assertTrue(plan.isPrimary(bean.getProperty("name").getPropertySource().getPrimary()));
        assertEquals(2, plan.getAccessors().size(), "one accessor per primary, constrained or not");
        assertThrows(UnsupportedOperationException.class, () -> plan.getBindings().clear());
        assertTrue(plan.toString().contains("name"));
    }

    @Test
    public void fieldBesideGetterReadsTheFieldDirectly() {
        BeanDefinition bean = BeanDefinition.load(Dual.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);
        PropertyDefinition name = bean.getProperty("name");
        PropertyDeclaration field = name.getPropertySource().getDeclarations(Kind.FIELD).get(0);
        PropertyDeclaration getter = name.getPropertySource().getPrimary();

        // One binding, held by the getter; the constrained field gets an accessor, no binding.
        assertEquals(List.of("name"), List.copyOf(plan.getBindings().keySet()));
        assertEquals(Kind.GETTER, getter.getKind());
        assertTrue(plan.isPrimary(getter));
        assertFalse(plan.isPrimary(field));
        assertEquals(List.of(getter, field), List.copyOf(plan.getAccessors().keySet()));

        Dual dual = new Dual();
        dual.name = "raw";
        assertEquals("raw", plan.getAccessor(field).read(dual));
        assertEquals("RAW", plan.getAccessor(getter).read(dual));
    }

    @Test
    public void hiddenSuperclassFieldReadsItsOwnDeclaration() {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);
        PropertyDefinition id = bean.getProperty("id");
        List<PropertyDeclaration> declarations = id.getPropertySource().getDeclarations();

        assertEquals(List.of("id", "label"), List.copyOf(plan.getBindings().keySet()));
        assertEquals(2, declarations.size());
        PropertyDeclaration parentField = declarations.stream().filter(d -> d.getDeclaringClass() == Parent.class).findFirst().orElseThrow();
        PropertyDeclaration childField = declarations.stream().filter(d -> d.getDeclaringClass() == Child.class).findFirst().orElseThrow();
        assertTrue(plan.isPrimary(childField));
        assertFalse(plan.isPrimary(parentField));

        Child child = new Child();
        ((Parent) child).id = "parent";
        child.id = "child";
        assertEquals("child", plan.getAccessor(childField).read(child));
        assertEquals("parent", plan.getAccessor(parentField).read(child));
    }

    @Test
    public void unreadSecondariesGetNoAccessor() {
        BeanDefinition bean = BeanDefinition.load(OverridingChild.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);
        PropertyDefinition label = bean.getProperty("label");

        // Parent.label is an unconstrained field beside the getter: never read. The overridden getter is virtual.
        assertEquals(List.of("id", "label"), List.copyOf(plan.getBindings().keySet()));
        PropertyDeclaration field = label.getPropertySource().getDeclarations(Kind.FIELD).get(0);
        assertThrows(IllegalArgumentException.class, () -> plan.getAccessor(field));
        List<PropertyDeclaration> getters = label.getPropertySource().getDeclarations(Kind.GETTER);
        assertEquals(2, getters.size());
        assertEquals(1, getters.stream().filter(plan::isPrimary).count());
        assertEquals(OverridingChild.class, label.getPropertySource().getPrimary().getDeclaringClass());
        assertEquals(2, plan.getAccessors().size());
    }

    @Test
    public void cascadedAndContainerSecondariesGetAccessors() {
        BeanDefinition bean = BeanDefinition.load(ReadSecondaries.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);

        PropertyDefinition child = bean.getProperty("child");
        PropertyDeclaration childField = child.getPropertySource().getDeclarations(Kind.FIELD).get(0);
        assertEquals(List.of(childField), child.getCascadedDeclarations());
        assertNotNull(plan.getAccessor(childField));

        PropertyDefinition tags = bean.getProperty("tags");
        PropertyDeclaration tagsField = tags.getPropertySource().getDeclarations(Kind.FIELD).get(0);
        assertSame(tagsField, tags.getContainerElements().get(0).getSourceDeclaration());
        assertNotNull(plan.getAccessor(tagsField));
        assertEquals(4, plan.getAccessors().size());
    }

    @Test
    public void reservedNamesAreEscaped() {
        BeanDefinition bean = BeanDefinition.load(Reserved.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);

        assertEquals("rule$", plan.getBindingName(bean.getProperty("rule")));
        assertEquals("ruleContext$", plan.getBindingName(bean.getProperty("ruleContext")));
        assertEquals("ruleViolations$", plan.getBindingName(bean.getProperty("ruleViolations")));
        assertEquals("$value$", plan.getBindingName(bean.getProperty("$value")));
        assertEquals("plain", plan.getBindingName(bean.getProperty("plain")));
        assertTrue(BindingPlan.RESERVED_NAMES.contains("rule"));
        assertTrue(BindingPlan.RESERVED_NAMES.contains(BindingPlan.THIS));
        assertTrue(BindingPlan.RESERVED_NAMES.contains(BindingPlan.TRAVERSAL));
        assertThrows(UnsupportedOperationException.class, () -> BindingPlan.RESERVED_NAMES.add("x"));
    }

    @Test
    public void residualCollisionsAreNumbered() {
        BeanDefinition bean = BeanDefinition.load(Colliding.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);

        // "rule" escapes to "rule$", which an actual property already owns.
        assertEquals("rule$", plan.getBindingName(bean.getProperty("rule$")));
        assertEquals("rule$$2", plan.getBindingName(bean.getProperty("rule")));
    }

    @Test
    public void loadBindsLazyRememberedReadOnlyValues() {
        BeanDefinition bean = BeanDefinition.load(Counting.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);
        Counting counting = new Counting();
        ScopedBindings bindings = Bindings.builder().scoped();

        plan.load(bindings, counting);
        assertEquals(0, counting.reads, "nothing is read until referenced");

        Binding<String> name = bindings.getBinding("name");
        assertNotNull(name);
        assertEquals(String.class, name.getType());
        assertFalse(name.isEditable());
        assertFalse(name.isFinal());
        assertEquals("n", name.getValue());
        assertEquals("n", name.getValue());
        assertEquals("n", bindings.getValue("name"));
        assertEquals(1, counting.reads, "read once, remembered");

        Binding<List<String>> items = bindings.getBinding("items");
        assertInstanceOf(ParameterizedType.class, items.getType());
        assertNull(items.getValue());
        assertNull(items.getValue());
        assertEquals(2, counting.reads, "null is remembered too");

        // A child scope can rebind the same name (shadowing); the plan's binding is not final.
        bindings.addScope();
        bindings.bind("name", String.class, "shadow");
        assertEquals("shadow", bindings.getValue("name"));
        bindings.removeScope();
        assertEquals("n", bindings.getValue("name"));

        assertThrows(IllegalArgumentException.class, () -> plan.load(bindings, "not a Counting"));
        assertThrows(IllegalArgumentException.class, () -> plan.load(null, counting));
        assertThrows(IllegalArgumentException.class, () -> plan.load(bindings, null));
    }

    @Test
    public void foreignDeclarationsAreRejected() {
        BindingPlan plan = BindingPlan.of(BeanDefinition.load(Simple.class, REGISTRY));
        BeanDefinition other = BeanDefinition.load(Child.class, REGISTRY);

        assertThrows(IllegalArgumentException.class, () -> plan.getBindingName(other.getProperty("id")));
        assertThrows(IllegalArgumentException.class, () -> plan.getAccessor(other.getProperty("id").getPropertySource().getPrimary()));
        assertThrows(IllegalArgumentException.class, () -> plan.getBindingName(null));
        assertThrows(IllegalArgumentException.class, () -> plan.getAccessor(null));
        assertThrows(IllegalArgumentException.class, () -> plan.isPrimary(null));
        assertThrows(IllegalArgumentException.class, () -> BindingPlan.of(null));
        assertThrows(IllegalArgumentException.class, () -> PropertyAccessor.of(null));
    }

    // ---- fixtures ----

    public static class Simple {
        @NotNull
        private String name;
        private int count;
    }

    public static class Dual {
        @NotNull
        private String name;

        @Size(max = 3)
        public String getName() { return name == null ? null : name.toUpperCase(); }
    }

    public static class Parent {
        @NotBlank
        protected String id;
        private String label;
        public String getLabel() { return label; }
    }

    public static class Child extends Parent {
        @NotNull
        private String id;
    }

    public static class OverridingChild extends Parent {
        @Override
        @NotNull
        public String getLabel() { return super.getLabel(); }
    }

    public static class ReadSecondaries {
        @Valid
        private Simple child;
        private List<@NotBlank String> tags;

        public Simple getChild() { return child; }
        public List<String> getTags() { return tags; }
    }

    public static class Reserved {
        private String rule;
        private String ruleContext;
        private String ruleViolations;
        private String $value;
        private String plain;
    }

    public static class Colliding {
        private String rule$;
        private String rule;
    }

    public static class Counting {
        int reads;
        private String name = "n";
        private List<String> items;
        private Map<String, Integer> counts = Map.of();

        public String getName() { reads++; return name; }
        public List<String> getItems() { reads++; return items; }
    }
}
