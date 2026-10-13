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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.model.ClassConstraintSource;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ConstraintSource;
import org.rulii.validation.beans.model.PropertyConstraintSource;
import org.rulii.validation.beans.model.PropertyDeclaration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConstraintSource} and the class-level loading of {@link ConstraintDefinition}.
 * The fixture annotations are public: the JDK annotation proxy of a non-public repeatable container
 * cannot return an array of a non-public annotation type (IllegalAccessError from the proxy module).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintSourceTest {

    public ConstraintSourceTest() {
        super();
    }

    @Test
    public void propertySourceOfAField() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        PropertyDeclaration declaration = PropertyDeclaration.ofField(field);
        PropertyConstraintSource source = new PropertyConstraintSource(declaration);

        assertEquals(declaration, source.getDeclaration());
        assertEquals(Bean.class, source.getDeclaringClass());
        assertEquals(field, source.getElement());
        assertEquals("name", source.getSourceDefinition().getFieldName());
        assertEquals(Bean.class.getName() + ".name", source.describe());
        assertEquals(new PropertyConstraintSource(declaration), source);
        assertEquals(new PropertyConstraintSource(declaration).hashCode(), source.hashCode());
        assertNotEquals(source, new PropertyConstraintSource(PropertyDeclaration.ofGetter(Bean.class.getDeclaredMethod("getName"))));
        assertNotEquals(source, new ClassConstraintSource(Bean.class));
        assertTrue(source.toString().contains("FIELD"));
    }

    @Test
    public void propertySourceOfAGetterAndARecordComponent() throws Exception {
        Method getter = Bean.class.getDeclaredMethod("getName");
        ConstraintSource source = new PropertyConstraintSource(PropertyDeclaration.ofGetter(getter));
        assertEquals(getter, source.getElement());
        assertEquals("getName", source.getSourceDefinition().getMethodName());

        RecordComponent component = Customer.class.getRecordComponents()[0];
        source = new PropertyConstraintSource(PropertyDeclaration.ofRecordComponent(component));
        assertEquals(Customer.class, source.getDeclaringClass());
        assertEquals(component, source.getElement());
        assertEquals("name", source.getSourceDefinition().getMethodName());
    }

    @Test
    public void classSource() {
        ClassConstraintSource source = new ClassConstraintSource(Bean.class);
        assertEquals(Bean.class, source.getType());
        assertEquals(Bean.class, source.getDeclaringClass());
        assertEquals(Bean.class, source.getElement());
        assertEquals(Bean.class.getName(), source.getSourceDefinition().getClassName());
        assertEquals(Bean.class.getName(), source.describe());
        assertEquals(new ClassConstraintSource(Bean.class), source);
        assertEquals(new ClassConstraintSource(Bean.class).hashCode(), source.hashCode());
        assertNotEquals(source, new ClassConstraintSource(Customer.class));
        assertTrue(source.toString().contains(Bean.class.getName()));
    }

    @Test
    public void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PropertyConstraintSource(null));
        assertThrows(IllegalArgumentException.class, () -> new ClassConstraintSource(null));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load((Class<?>) null));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load((ConstraintSource) null, Bean.class.getAnnotation(ValidBean.class)));
        assertThrows(IllegalArgumentException.class, () -> ConstraintDefinition.load(new ClassConstraintSource(Bean.class), null));
    }

    @Test
    public void classLevelConstraintsAreLoadedInDeclarationOrderWithRepeatedOnesUnwrapped() {
        List<ConstraintDefinition> constraints = ConstraintDefinition.load(Bean.class);

        assertEquals(3, constraints.size());
        assertEquals(ValidBean.class, constraints.get(0).getAnnotationType());
        assertEquals(Marker.class, constraints.get(1).getAnnotationType());
        assertEquals(Marker.class, constraints.get(2).getAnnotationType());
        assertEquals("a", ((Marker) constraints.get(1).getAnnotation()).value());
        assertEquals("b", ((Marker) constraints.get(2).getAnnotation()).value());

        ConstraintDefinition definition = constraints.get(0);
        assertEquals(new ClassConstraintSource(Bean.class), definition.getConstraintSource());
        assertEquals("ValidBean", definition.getName());
        assertEquals(Bean.class.getName(), definition.getSource().getClassName());
        assertEquals("invalid bean", definition.getMessage());
        assertEquals(List.of(), definition.getGroups());
        assertEquals(List.of(), definition.getPayload());
        assertTrue(definition.toString().contains(Bean.class.getName()));
        assertThrows(UnsupportedOperationException.class, constraints::clear);
    }

    @Test
    public void classWithoutConstraintsLoadsNothing() {
        assertEquals(List.of(), ConstraintDefinition.load(Customer.class));
        assertEquals(List.of(), ConstraintDefinition.load(Object.class));
    }

    @Test
    public void constraintDefinitionsOfDifferentSourcesAreNotEqual() throws Exception {
        Field field = Bean.class.getDeclaredField("name");
        ConstraintDefinition onField = ConstraintDefinition.load(PropertyDeclaration.ofField(field)).get(0);
        ConstraintDefinition onFieldAgain = ConstraintDefinition.load(new PropertyConstraintSource(PropertyDeclaration.ofField(field)),
                field.getAnnotation(NotNull.class));
        assertEquals(onField, onFieldAgain);
        assertNotEquals(onField, ConstraintDefinition.load(Bean.class).get(0));
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface ValidBean {
        String message() default "invalid bean";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface Marker {
        String value();
        String message() default "marker";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};

        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface List {
            Marker[] value();
        }
    }

    @ValidBean
    @Marker.List({@Marker("a"), @Marker("b")})
    @SuppressWarnings("unused")
    private static class Bean {
        @NotNull private String name;
        @Size(max = 10) public String getName() { return name; }
    }

    @SuppressWarnings("unused")
    private record Customer(@NotNull String name) {}
}
