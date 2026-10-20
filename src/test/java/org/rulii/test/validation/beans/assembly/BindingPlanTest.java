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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.PropertyDeclaration;
import org.rulii.validation.beans.model.PropertyDeclaration.Kind;
import org.rulii.validation.beans.model.PropertyDefinition;

import java.util.List;

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
        assertEquals(Kind.FIELD, plan.getBindings().get("name").getKind());
        assertFalse(plan.isShadow("name"));
        assertFalse(plan.isShadow("other"));
        assertThrows(UnsupportedOperationException.class, () -> plan.getBindings().clear());
        assertTrue(plan.toString().contains("name"));
    }

    @Test
    public void fieldBesideGetterGetsAFieldShadow() {
        BeanDefinition bean = BeanDefinition.load(Dual.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);
        PropertyDefinition name = bean.getProperty("name");

        assertEquals(List.of("name", "name$field"), List.copyOf(plan.getBindings().keySet()));
        assertEquals("name", plan.getBindingName(name));
        assertEquals(Kind.GETTER, plan.getBindings().get("name").getKind());
        assertEquals(Kind.FIELD, plan.getBindings().get("name$field").getKind());
        assertTrue(plan.isShadow("name$field"));

        PropertyDeclaration field = name.getPropertySource().getDeclarations(Kind.FIELD).get(0);
        PropertyDeclaration getter = name.getPropertySource().getPrimary();
        assertEquals("name$field", plan.getBindingName(field));
        assertEquals("name", plan.getBindingName(getter));
    }

    @Test
    public void hiddenSuperclassFieldGetsAClassShadow() {
        BeanDefinition bean = BeanDefinition.load(Child.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);
        PropertyDefinition id = bean.getProperty("id");

        // Child field is primary (no getter); the Parent field is shadowed by its declaring class.
        assertEquals(List.of("id", "id$Parent", "label"), List.copyOf(plan.getBindings().keySet()));
        assertEquals(Child.class, plan.getBindings().get("id").getDeclaringClass());
        assertEquals(Parent.class, plan.getBindings().get("id$Parent").getDeclaringClass());
        assertEquals(2, id.getPropertySource().getDeclarations().size());
    }

    @Test
    public void overriddenGetterReadsThePrimaryBinding() {
        BeanDefinition bean = BeanDefinition.load(OverridingChild.class, REGISTRY);
        BindingPlan plan = BindingPlan.of(bean);
        PropertyDefinition label = bean.getProperty("label");

        // Parent.label is an unconstrained field beside the getter: no shadow for it.
        assertEquals(List.of("id", "label"), List.copyOf(plan.getBindings().keySet()));
        assertEquals("label", plan.getBindingName(label.getPropertySource().getDeclarations(Kind.FIELD).get(0)));
        List<PropertyDeclaration> getters = label.getPropertySource().getDeclarations(Kind.GETTER);
        assertEquals(2, getters.size());
        for (PropertyDeclaration getter : getters) {
            assertEquals("label", plan.getBindingName(getter));
        }
        assertEquals(OverridingChild.class, plan.getBindings().get("label").getDeclaringClass());
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
        assertFalse(plan.isShadow("rule$"));
        assertTrue(BindingPlan.RESERVED_NAMES.contains("rule"));
        assertTrue(BindingPlan.RESERVED_NAMES.contains(BindingPlan.THIS));
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
    public void foreignDeclarationsAreRejected() {
        BindingPlan plan = BindingPlan.of(BeanDefinition.load(Simple.class, REGISTRY));
        BeanDefinition other = BeanDefinition.load(Child.class, REGISTRY);

        assertThrows(IllegalArgumentException.class, () -> plan.getBindingName(other.getProperty("id")));
        assertThrows(IllegalArgumentException.class, () -> plan.getBindingName(other.getProperty("id").getPropertySource().getPrimary()));
        assertThrows(IllegalArgumentException.class, () -> plan.getBindingName((PropertyDefinition) null));
        assertThrows(IllegalArgumentException.class, () -> plan.getBindingName((PropertyDeclaration) null));
        assertThrows(IllegalArgumentException.class, () -> plan.isShadow(null));
        assertThrows(IllegalArgumentException.class, () -> BindingPlan.of(null));
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
        public String getName() { return name; }
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
}
