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
package org.rulii.test.validation.beans.metadata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.annotation.Validate;
import org.rulii.validation.beans.metadata.BeanMetadata;
import org.rulii.validation.beans.metadata.BeanMetadataReader;
import org.rulii.validation.beans.metadata.ConstraintSource;
import org.rulii.validation.beans.metadata.PropertyMetadata;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hand-rolled property discovery: fields of any visibility, getters and
 * getter-only properties, dual sources, shadowed fields, records, hierarchy ordering,
 * and static-member exclusion.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanMetadataDiscoveryTest {

    public BeanMetadataDiscoveryTest() {
        super();
    }

    private final BeanMetadataReader reader = BeanMetadataReader.builder()
            .mapperRegistry(TestConstraintMappers.registry())
            .build();

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public static class Person {
        @NotBlank
        private String name;
        protected int age;
        public static String staticField = "ignored";

        public Person(String name, int age) {
            super();
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }

        @NotNull
        public String getDisplayName() { return name + " (" + age + ")"; }
    }

    public static class Employee extends Person {
        @Size(max = 10)
        private String name;
        private String department;

        public Employee(String name, int age, String department) {
            super(name, age);
            this.name = name;
            this.department = department;
        }

        public String getDepartment() { return department; }
    }

    public interface Named {
        @NotBlank
        String getLabel();
    }

    public static class Tagged implements Named {
        private String label;

        public Tagged(String label) {
            super();
            this.label = label;
        }

        @Override
        public String getLabel() { return label; }
    }

    public record Customer(@NotBlank String name, @Valid Address address, int score) {

        public String getSummary() { return name + ":" + score; }
    }

    public record Address(@NotBlank String city) {
    }

    public static class WithValidate {
        @Validate({"nameRules", "extraNameRules"})
        private String name = "x";

        public WithValidate() {
            super();
        }

        public String getName() { return name; }
    }

    // -----------------------------------------------------------------------
    // Discovery
    // -----------------------------------------------------------------------

    @Test
    public void fieldsOfAnyVisibilityAreDiscovered() {
        BeanMetadata metadata = reader.read(Person.class);

        assertNotNull(metadata.getProperty("name"));
        assertNotNull(metadata.getProperty("age"));
        assertNull(metadata.getProperty("staticField"));
    }

    @Test
    public void getterOnlyPropertyIsDiscovered() {
        BeanMetadata metadata = reader.read(Person.class);
        PropertyMetadata displayName = metadata.getProperty("displayName");

        assertNotNull(displayName);
        assertNull(displayName.getField());
        assertNotNull(displayName.getGetter());
        assertEquals(1, displayName.getSources().size());
        assertEquals(1, displayName.getSources().get(0).getConstraints().size());
        assertSame(displayName.getGetter(), displayName.getSources().get(0).getMember());
    }

    @Test
    public void unconstrainedPropertiesAreDiscoveredToo() {
        BeanMetadata metadata = reader.read(Person.class);

        // age carries no constraints but is a property (scope binding needs it)
        PropertyMetadata age = metadata.getProperty("age");
        assertNotNull(age);
        assertFalse(age.hasValidation());
    }

    @Test
    public void dualSourceKeepsConstraintsPerSource() {
        BeanMetadata metadata = reader.read(Person.class);
        PropertyMetadata name = metadata.getProperty("name");

        assertNotNull(name.getField());
        assertNotNull(name.getGetter());
        // Only the field carries a constraint, so only the field source exists.
        assertEquals(1, name.getSources().size());
        ConstraintSource source = name.getSources().get(0);
        assertTrue(source.isFieldSource());
        assertSame(name.getField(), source.getMember());
        assertEquals(1, source.getConstraints().size());
    }

    @Test
    public void primaryValueIsGetterForDualSource() {
        BeanMetadata metadata = reader.read(Person.class);
        Person person = new Person("Ada", 36);
        PropertyMetadata name = metadata.getProperty("name");

        assertEquals("Ada", name.getValue(person));
        assertEquals("Ada", name.getSources().get(0).getValue(person));
    }

    @Test
    public void shadowedFieldKeepsBothSources() {
        BeanMetadata metadata = reader.read(Employee.class);
        PropertyMetadata name = metadata.getProperty("name");

        // Superclass field stays a secondary source (hierarchy order: shadowed first);
        // the subclass field backs the property.
        assertEquals(2, name.getSources().size());
        ConstraintSource shadowed = name.getSources().get(0);
        ConstraintSource primary = name.getSources().get(1);
        assertEquals(Person.class, shadowed.getMember().getDeclaringClass());
        assertEquals(1, shadowed.getConstraints().size());
        assertEquals(NotBlank.class, shadowed.getConstraints().get(0).getAnnotationType());
        assertEquals(Employee.class, primary.getMember().getDeclaringClass());
        assertEquals(1, primary.getConstraints().size());
        assertEquals(Size.class, primary.getConstraints().get(0).getAnnotationType());
        assertEquals(Employee.class, name.getField().getDeclaringClass());
    }

    @Test
    public void shadowedFieldValuesReadTheirOwnDeclarations() throws Exception {
        BeanMetadata metadata = reader.read(Employee.class);
        Employee employee = new Employee("Grace", 45, "Engineering");
        PropertyMetadata name = metadata.getProperty("name");

        assertEquals("Grace", name.getValue(employee));
        assertEquals("Grace", name.getSources().get(0).getValue(employee));
    }

    @Test
    public void interfaceGetterConstraintsApply() {
        BeanMetadata metadata = reader.read(Tagged.class);
        PropertyMetadata label = metadata.getProperty("label");

        assertNotNull(label);
        // Interface getter and implementing getter both contribute (cumulative).
        assertEquals(1, label.getSources().size());
        assertEquals(1, label.getSources().get(0).getConstraints().size());
        assertEquals(Tagged.class, label.getGetter().getDeclaringClass());
    }

    @Test
    public void hierarchyOrderingIsTopDown() {
        BeanMetadata metadata = reader.read(Employee.class);
        List<String> names = new ArrayList<>();
        for (PropertyMetadata property : metadata.getProperties()) {
            names.add(property.getName());
        }

        // Superclass declarations come first; the shadowed name keeps its first position.
        assertTrue(names.indexOf("name") < names.indexOf("department"));
        assertTrue(names.indexOf("age") < names.indexOf("department"));
    }

    @Test
    public void deterministicRepeatReads() {
        BeanMetadata first = reader.read(Employee.class);
        BeanMetadata second = reader.read(Employee.class);

        assertSame(first, second);
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    @Test
    public void recordComponentsAreSingleSourceOfTruth() {
        BeanMetadata metadata = reader.read(Customer.class);
        PropertyMetadata name = metadata.getProperty("name");

        assertNotNull(name.getRecordComponent());
        assertNotNull(name.getGetter());
        // Propagated copies must not double-count: one source, exactly one @NotBlank.
        assertEquals(1, name.getSources().size());
        assertEquals(1, name.getSources().get(0).getConstraints().size());
        assertSame(name.getGetter(), name.getSources().get(0).getMember());
    }

    @Test
    public void recordValuesReadViaAccessor() {
        BeanMetadata metadata = reader.read(Customer.class);
        Customer customer = new Customer("Ada", new Address("London"), 10);

        assertEquals("Ada", metadata.getProperty("name").getValue(customer));
        assertEquals(10, metadata.getProperty("score").getValue(customer));
    }

    @Test
    public void recordCascadeDetected() {
        BeanMetadata metadata = reader.read(Customer.class);

        assertTrue(metadata.getProperty("address").isCascaded());
        assertTrue(metadata.hasCascades());
    }

    @Test
    public void recordExtraGetterBecomesProperty() {
        BeanMetadata metadata = reader.read(Customer.class);

        assertNotNull(metadata.getProperty("summary"));
    }

    // -----------------------------------------------------------------------
    // @Validate
    // -----------------------------------------------------------------------

    @Test
    public void validateNamesCollectedInDeclarationOrder() {
        BeanMetadata metadata = reader.read(WithValidate.class);

        assertEquals(List.of("nameRules", "extraNameRules"),
                metadata.getProperty("name").getValidateRuleNames());
    }
}
