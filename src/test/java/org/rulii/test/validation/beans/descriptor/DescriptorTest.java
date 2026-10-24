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
package org.rulii.test.validation.beans.descriptor;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.MethodType;
import jakarta.validation.metadata.PropertyDescriptor;
import jakarta.validation.metadata.Scope;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.descriptor.BeanDefinitionDescriptor;
import org.rulii.validation.beans.descriptor.ContainerElementDefinitionDescriptor;
import org.rulii.validation.beans.descriptor.PropertyDefinitionDescriptor;
import org.rulii.validation.beans.group.InvalidGroupException;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spec's metadata API over the model: {@code Validator.getConstraintsForClass}.
 */
public class DescriptorTest {

    private static final BeanValidator VALIDATOR = BeanValidator.builder()
            .mapper(new SimpleConstraintMapper<>(ValidPerson.class, List.of(Object.class),
                    (a, t) -> List.of(t.configure(Validators.notNull(Validators.binding("name")), true).build())))
            .build();

    public DescriptorTest() {
        super();
    }

    private static List<Class<? extends Annotation>> types(Set<ConstraintDescriptor<?>> descriptors) {
        List<Class<? extends Annotation>> result = new ArrayList<>();
        for (ConstraintDescriptor<?> descriptor : descriptors) {
            result.add(descriptor.getAnnotation().annotationType());
        }
        return result;
    }

    private static List<String> names(Set<PropertyDescriptor> descriptors) {
        List<String> result = new ArrayList<>();
        for (PropertyDescriptor descriptor : descriptors) {
            result.add(descriptor.getPropertyName());
        }
        return result;
    }

    @Test
    public void beanDescriptor() {
        BeanDescriptor bean = VALIDATOR.getConstraintsForClass(Person.class);

        assertTrue(bean.isBeanConstrained());
        assertEquals(Person.class, bean.getElementClass());
        assertTrue(bean.hasConstraints(), "the class-level constraint");
        assertEquals(List.of(ValidPerson.class), types(bean.getConstraintDescriptors()));
        assertEquals(List.of("baseName", "name", "nick", "created", "emails", "addresses", "home", "matrix", "age"), names(bean.getConstrainedProperties()));
        assertNull(bean.getConstraintsForProperty("only"), "@Validate alone is not a constraint in the spec's sense");
        assertNull(bean.getConstraintsForProperty("plain"));
        assertNull(bean.getConstraintsForProperty("nope"));
        assertNotNull(bean.getConstraintsForProperty("name"));
        assertThrows(IllegalArgumentException.class, () -> bean.getConstraintsForProperty(null));

        // Executables: the constrained getter is the only constrained method; no constructor is constrained.
        assertNull(bean.getConstraintsForMethod("getName"));
        assertNotNull(bean.getConstraintsForMethod("getNick"));
        assertEquals(1, bean.getConstrainedMethods(MethodType.GETTER, MethodType.NON_GETTER).size());
        assertTrue(bean.getConstrainedMethods(MethodType.NON_GETTER).isEmpty());
        assertNull(bean.getConstraintsForConstructor());
        assertTrue(bean.getConstrainedConstructors().isEmpty());

        BeanDefinitionDescriptor ours = (BeanDefinitionDescriptor) bean;
        assertSame(VALIDATOR.getBeanDefinition(Person.class), ours.getDefinition());
        assertEquals(Person.class, ours.getBeanClass());
        assertTrue(ours.toString().contains("Person"));

        BeanDescriptor plain = VALIDATOR.getConstraintsForClass(Plain.class);
        assertFalse(plain.isBeanConstrained());
        assertFalse(plain.hasConstraints());
        assertTrue(plain.getConstrainedProperties().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> VALIDATOR.getConstraintsForClass(null));
        assertThrows(IllegalArgumentException.class, () -> new BeanDefinitionDescriptor(null));
    }

    @Test
    public void propertyDescriptors() {
        BeanDescriptor bean = VALIDATOR.getConstraintsForClass(Person.class);

        PropertyDescriptor name = bean.getConstraintsForProperty("name");
        assertEquals("name", name.getPropertyName());
        assertEquals(String.class, name.getElementClass());
        assertTrue(name.hasConstraints());
        assertEquals(List.of(NotBlank.class), types(name.getConstraintDescriptors()));
        assertFalse(name.isCascaded());
        assertTrue(name.getGroupConversions().isEmpty());
        assertTrue(name.getConstrainedContainerElementTypes().isEmpty());
        assertInstanceOf(PropertyDefinition.class, ((PropertyDefinitionDescriptor) name).getDefinition());
        assertTrue(name.toString().contains("name"));

        PropertyDescriptor home = bean.getConstraintsForProperty("home");
        assertTrue(home.isCascaded());
        assertFalse(home.hasConstraints(), "cascaded, but no constraint of its own");
        assertEquals(Address.class, home.getElementClass());

        PropertyDescriptor nick = bean.getConstraintsForProperty("nick");
        assertEquals(List.of(Size.class), types(nick.getConstraintDescriptors()));
        assertEquals(2, ((Size) nick.getConstraintDescriptors().iterator().next().getAnnotation()).min());
    }

    @Test
    public void constraintFinderFiltersByGroupScopeAndElementType() {
        BeanDescriptor bean = VALIDATOR.getConstraintsForClass(Person.class);

        // Element type: the field constraint vs the getter constraint.
        PropertyDescriptor nick = bean.getConstraintsForProperty("nick");
        assertEquals(List.of(Size.class), types(nick.findConstraints().declaredOn(ElementType.METHOD).getConstraintDescriptors()));
        assertTrue(nick.findConstraints().declaredOn(ElementType.FIELD).getConstraintDescriptors().isEmpty());
        assertFalse(nick.findConstraints().declaredOn(ElementType.FIELD).hasConstraints());
        assertTrue(nick.findConstraints().declaredOn().getConstraintDescriptors().isEmpty());
        assertEquals(List.of(NotBlank.class), types(bean.getConstraintsForProperty("name").findConstraints()
                .declaredOn(ElementType.FIELD, ElementType.METHOD).getConstraintDescriptors()));
        assertEquals(List.of(ValidPerson.class), types(bean.findConstraints().declaredOn(ElementType.TYPE).getConstraintDescriptors()));

        // Scope: baseName is declared by the supertype.
        PropertyDescriptor baseName = bean.getConstraintsForProperty("baseName");
        assertEquals(List.of(NotNull.class), types(baseName.findConstraints().lookingAt(Scope.HIERARCHY).getConstraintDescriptors()));
        assertTrue(baseName.findConstraints().lookingAt(Scope.LOCAL_ELEMENT).getConstraintDescriptors().isEmpty());
        assertEquals(List.of(NotBlank.class), types(bean.getConstraintsForProperty("name").findConstraints().lookingAt(Scope.LOCAL_ELEMENT).getConstraintDescriptors()));
        assertThrows(IllegalArgumentException.class, () -> baseName.findConstraints().lookingAt(null));

        // Groups: expanded along superinterfaces, Default for constraints declaring none.
        PropertyDescriptor created = bean.getConstraintsForProperty("created");
        assertEquals(List.of(NotNull.class), types(created.findConstraints().unorderedAndMatchingGroups(OnCreate.class).getConstraintDescriptors()));
        assertEquals(List.of(NotNull.class), types(created.findConstraints().unorderedAndMatchingGroups(Extended.class).getConstraintDescriptors()));
        assertTrue(created.findConstraints().unorderedAndMatchingGroups(Default.class).getConstraintDescriptors().isEmpty());
        assertTrue(created.findConstraints().unorderedAndMatchingGroups().getConstraintDescriptors().isEmpty(), "no groups means Default");
        assertEquals(List.of(NotBlank.class), types(bean.getConstraintsForProperty("name").findConstraints().unorderedAndMatchingGroups(Default.class).getConstraintDescriptors()));
        assertThrows(InvalidGroupException.class, () -> created.findConstraints().unorderedAndMatchingGroups(String.class));

        // Filters combine.
        assertTrue(created.findConstraints().unorderedAndMatchingGroups(OnCreate.class).declaredOn(ElementType.METHOD).getConstraintDescriptors().isEmpty());
        assertEquals(List.of(NotNull.class), types(created.findConstraints().unorderedAndMatchingGroups(OnCreate.class)
                .declaredOn(ElementType.FIELD).lookingAt(Scope.LOCAL_ELEMENT).getConstraintDescriptors()));
    }

    @Test
    public void containerElementDescriptors() {
        BeanDescriptor bean = VALIDATOR.getConstraintsForClass(Person.class);

        PropertyDescriptor emails = bean.getConstraintsForProperty("emails");
        assertFalse(emails.hasConstraints(), "the constraint sits on the type argument, not the property");
        List<ContainerElementTypeDescriptor> elements = new ArrayList<>(emails.getConstrainedContainerElementTypes());
        assertEquals(1, elements.size());
        ContainerElementTypeDescriptor element = elements.get(0);
        assertEquals(List.class, element.getContainerClass());
        assertEquals(0, element.getTypeArgumentIndex());
        assertEquals(String.class, element.getElementClass());
        assertEquals(List.of(Email.class), types(element.getConstraintDescriptors()));
        assertEquals(List.of(Email.class), types(element.findConstraints().declaredOn(ElementType.TYPE_USE).getConstraintDescriptors()));
        assertTrue(element.findConstraints().declaredOn(ElementType.FIELD).getConstraintDescriptors().isEmpty());
        assertFalse(element.isCascaded());
        assertTrue(element.getGroupConversions().isEmpty());
        assertTrue(element.getConstrainedContainerElementTypes().isEmpty());
        assertNotNull(((ContainerElementDefinitionDescriptor) element).getDefinition());
        assertTrue(element.toString().contains("emails"));

        // Map: a constrained key position and a cascaded value position.
        List<ContainerElementTypeDescriptor> positions = new ArrayList<>(bean.getConstraintsForProperty("addresses").getConstrainedContainerElementTypes());
        assertEquals(2, positions.size());
        assertEquals(0, positions.get(0).getTypeArgumentIndex());
        assertEquals(List.of(NotBlank.class), types(positions.get(0).getConstraintDescriptors()));
        assertFalse(positions.get(0).isCascaded());
        assertEquals(1, positions.get(1).getTypeArgumentIndex());
        assertTrue(positions.get(1).getConstraintDescriptors().isEmpty());
        assertTrue(positions.get(1).isCascaded());
        assertEquals(Address.class, positions.get(1).getElementClass());
        assertEquals(Map.class, positions.get(1).getContainerClass());

        // Nested: the inner position hangs off the outer one.
        List<ContainerElementTypeDescriptor> outer = new ArrayList<>(bean.getConstraintsForProperty("matrix").getConstrainedContainerElementTypes());
        assertEquals(1, outer.size());
        assertTrue(outer.get(0).getConstraintDescriptors().isEmpty());
        List<ContainerElementTypeDescriptor> inner = new ArrayList<>(outer.get(0).getConstrainedContainerElementTypes());
        assertEquals(1, inner.size());
        assertEquals(List.of(NotBlank.class), types(inner.get(0).getConstraintDescriptors()));

        // An unwrapped container value: the property's constraint applies to the value, with no type argument.
        PropertyDescriptor age = bean.getConstraintsForProperty("age");
        assertTrue(age.getConstraintDescriptors().isEmpty());
        ContainerElementTypeDescriptor unwrapped = age.getConstrainedContainerElementTypes().iterator().next();
        assertNull(unwrapped.getTypeArgumentIndex());
        assertEquals(OptionalInt.class, unwrapped.getContainerClass());
        assertEquals(List.of(NotNull.class), types(unwrapped.getConstraintDescriptors()));
    }

    @Test
    public void descriptorsReachTheModel() {
        BeanDefinition definition = VALIDATOR.getBeanDefinition(Person.class);
        PropertyDefinitionDescriptor name = new PropertyDefinitionDescriptor(Person.class, definition.getProperty("name"));
        assertSame(definition.getProperty("name"), name.getDefinition());
        assertThrows(IllegalArgumentException.class, () -> new PropertyDefinitionDescriptor(null, definition.getProperty("name")));
        assertThrows(IllegalArgumentException.class, () -> new PropertyDefinitionDescriptor(Person.class, null));
        assertThrows(IllegalArgumentException.class, () -> new ContainerElementDefinitionDescriptor(Person.class, null));
    }

    // ---- fixtures ----

    public interface OnCreate {}

    public interface Extended extends OnCreate {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface ValidPerson {
        String message() default "invalid";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class Base {
        @NotNull
        String baseName;
    }

    @ValidPerson
    public static class Person extends Base {
        @NotBlank
        String name;

        String nick;

        @NotNull(groups = OnCreate.class)
        String created;

        List<@Email String> emails;

        Map<@NotBlank String, @Valid Address> addresses;

        @Valid
        Address home;

        List<List<@NotBlank String>> matrix;

        @NotNull
        OptionalInt age;

        @Validate("someRule")
        String only;

        String plain;

        @Size(min = 2)
        public String getNick() {
            return nick;
        }
    }

    public static class Address {
        @NotBlank
        String city;
    }

    public static class Plain {
        String nothing;
        Optional<String> maybe;
    }
}
