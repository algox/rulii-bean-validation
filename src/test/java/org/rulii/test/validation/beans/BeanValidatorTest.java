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
package org.rulii.test.validation.beans;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.registry.RuleRegistry;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.annotation.Validate;
import org.rulii.validation.beans.traversal.InvalidGroupException;
import org.rulii.validation.beans.traversal.ValidateRuleNotFoundException;
import org.rulii.ruleflow.RuleFlow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.rulii.validation.rules.Validators.binding;
import static org.rulii.validation.rules.Validators.notBlank;

/**
 * End-to-end tests for the Phase 5 engine: flat validation with paths, cascading with
 * cycles and runtime types, container walks, group selection, {@code @Validate}
 * dispatch, composed constraints, message wiring, and embedded flow runs.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanValidatorTest {

    public BeanValidatorTest() {
        super();
    }

    private final BeanValidator validator = BeanValidator.builder().build();

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public static class Address {
        @NotBlank private String city;

        public Address(String city) {
            super();
            this.city = city;
        }
    }

    public static class Customer {
        @NotBlank private String name;
        @Min(18) private Integer age;
        @Valid private Address address;
        @Size(min = 2, max = 4) private List<String> tags;

        public Customer(String name, Integer age, Address address, List<String> tags) {
            super();
            this.name = name;
            this.age = age;
            this.address = address;
            this.tags = tags;
        }
    }

    public static class Order {
        private List<@NotBlank String> codes;
        private Map<@NotBlank String, @Min(10) Integer> scores;
        @Valid private List<Address> addresses;

        public Order(List<String> codes, Map<String, Integer> scores, List<Address> addresses) {
            super();
            this.codes = codes;
            this.scores = scores;
            this.addresses = addresses;
        }
    }

    public static class Node {
        @NotBlank private String label;
        @Valid private Node next;

        public Node(String label) {
            super();
            this.label = label;
        }
    }

    public interface OnCreate { }

    public interface OnAdminCreate extends OnCreate { }

    public static class Grouped {
        @NotBlank(groups = OnCreate.class) private String createOnly;
        @NotBlank private String always;

        public Grouped(String createOnly, String always) {
            super();
            this.createOnly = createOnly;
            this.always = always;
        }
    }

    public static class WithRules {
        @Validate("valueNotBlank") private String code;
        private String sibling = "visible";

        public WithRules(String code) {
            super();
            this.code = code;
        }
    }

    @Validate("siblingNotBlank")
    public static class ClassRuled {
        private String sibling;

        public ClassRuled(String sibling) {
            super();
            this.sibling = sibling;
        }
    }

    public static class MissingRule {
        @Validate("nope") private String code = "x";

        public MissingRule() {
            super();
        }
    }

    @Constraint(validatedBy = { })
    @ReportAsSingleViolation
    @NotBlank
    @Size(min = 3, max = 10)
    @Target({ ElementType.FIELD, ElementType.ANNOTATION_TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidName {
        String message() default "{org.rulii.test.validname.message}";
        Class<?>[] groups() default { };
        Class<? extends Payload>[] payload() default { };
    }

    public static class Named {
        @ValidName private String name;

        public Named(String name) {
            super();
            this.name = name;
        }
    }

    public static class Base {
        public Base() {
            super();
        }
    }

    public static class Sub extends Base {
        @NotBlank private String detail;

        public Sub(String detail) {
            super();
            this.detail = detail;
        }
    }

    public static class Holder {
        @Valid private Base child;

        public Holder(Base child) {
            super();
            this.child = child;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<String> paths(RuleViolations violations) {
        List<String> result = new ArrayList<>();
        for (RuleViolation violation : violations.getViolations()) {
            result.add(((BeanRuleViolation) violation).getPropertyPath().render());
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Flat validation & paths
    // -----------------------------------------------------------------------

    @Test
    public void validBeanHasNoViolations() {
        Customer customer = new Customer("Ada", 36, new Address("London"), List.of("a", "b"));

        assertTrue(validator.validate(customer).getViolations().isEmpty());
    }

    @Test
    public void flatViolationsCarryPropertyPaths() {
        Customer customer = new Customer("  ", 16, new Address("London"), List.of("a", "b"));
        BeanRuleViolations violations = validator.validate(customer);

        assertEquals(2, violations.getViolations().size());
        assertTrue(paths(violations).containsAll(List.of("name", "age")));
    }

    @Test
    public void pathRidesTheParameterMap() {
        BeanRuleViolations violations = validator.validate(new Customer(" ", 20, new Address("x"), List.of("a", "b")));

        assertEquals("name", violations.getViolations().get(0).getParameters().get(BeanRuleViolation.PATH_PARAMETER));
    }

    @Test
    public void nullBeanFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    // -----------------------------------------------------------------------
    // Cascading
    // -----------------------------------------------------------------------

    @Test
    public void cascadeReportsUnderChildPath() {
        Customer customer = new Customer("Ada", 36, new Address("  "), List.of("a", "b"));
        BeanRuleViolations violations = validator.validate(customer);

        assertEquals(List.of("address.city"), paths(violations));
    }

    @Test
    public void nullCascadeReferenceIsSkipped() {
        Customer customer = new Customer("Ada", 36, null, List.of("a", "b"));

        assertTrue(validator.validate(customer).getViolations().isEmpty());
    }

    @Test
    public void cyclesTerminateAndValidateOncePerCall() {
        Node a = new Node("  ");
        Node b = new Node("ok");
        a.next = b;
        b.next = a;

        BeanRuleViolations violations = validator.validate(a);

        // a's blank label reported once (under the root, not again under next.next).
        assertEquals(List.of("label"), paths(violations));
    }

    @Test
    public void runtimeTypeDispatchesToSubclassMetadata() {
        Holder holder = new Holder(new Sub("  "));
        BeanRuleViolations violations = validator.validate(holder);

        assertEquals(List.of("child.detail"), paths(violations));
    }

    @Test
    public void legacyValidOnCollectionCascadesElements() {
        Order order = new Order(List.of("a"), Map.of("k", 10), List.of(new Address("ok"), new Address("  ")));
        BeanRuleViolations violations = validator.validate(order);

        assertEquals(List.of("addresses[1].city"), paths(violations));
    }

    // -----------------------------------------------------------------------
    // Container elements
    // -----------------------------------------------------------------------

    @Test
    public void listElementViolationsCarryIndexedPaths() {
        Order order = new Order(List.of("ok", "  "), Map.of("k", 10), List.of());
        BeanRuleViolations violations = validator.validate(order);

        assertEquals(List.of("codes[1]"), paths(violations));
    }

    @Test
    public void mapKeyAndValueViolationsCarryKeyedPaths() {
        Order order = new Order(List.of("ok"), Map.of("  ", 5), List.of());
        BeanRuleViolations violations = validator.validate(order);

        assertEquals(2, violations.getViolations().size());
        assertTrue(paths(violations).containsAll(List.of("scores[  ]")));
    }

    // -----------------------------------------------------------------------
    // Groups
    // -----------------------------------------------------------------------

    @Test
    public void defaultGroupSkipsGroupedConstraints() {
        BeanRuleViolations violations = validator.validate(new Grouped(null, null));

        // Only the Default-group constraint runs.
        assertEquals(List.of("always"), paths(violations));
    }

    @Test
    public void requestedGroupActivatesItsConstraintsOnly() {
        BeanRuleViolations violations = validator.validate(new Grouped(null, null), OnCreate.class);

        // Default is NOT implicit when groups are requested.
        assertEquals(List.of("createOnly"), paths(violations));
    }

    @Test
    public void groupInheritanceActivatesParentGroups() {
        BeanRuleViolations violations = validator.validate(new Grouped(null, null), OnAdminCreate.class);

        assertEquals(List.of("createOnly"), paths(violations));
    }

    @Test
    public void nonInterfaceGroupFailsFast() {
        assertThrows(InvalidGroupException.class, () -> validator.validate(new Grouped("a", "b"), String.class));
    }

    // -----------------------------------------------------------------------
    // @Validate registry dispatch
    // -----------------------------------------------------------------------

    private BeanValidator registryValidator() {
        RuleRegistry registry = RuleRegistry.builder()
                .register(notBlank(binding("$value")).name("valueNotBlank").build())
                .register(notBlank(binding("sibling")).name("siblingNotBlank").build())
                .build();
        return BeanValidator.builder().ruleRegistry(registry).build();
    }

    @Test
    public void propertyValidateRunsAgainstValueBinding() {
        BeanValidator validator = registryValidator();

        BeanRuleViolations violations = validator.validate(new WithRules("  "));
        assertEquals(List.of("code"), paths(violations));
        assertTrue(validator.validate(new WithRules("fine")).getViolations().isEmpty());
    }

    @Test
    public void classValidateSeesSiblingPropertyBindings() {
        BeanValidator validator = registryValidator();

        BeanRuleViolations violations = validator.validate(new ClassRuled("  "));
        // Class-level registry rule reads the property binding directly; object path.
        assertEquals(List.of(""), paths(violations));
        assertTrue(validator.validate(new ClassRuled("ok")).getViolations().isEmpty());
    }

    @Test
    public void missingValidateRuleFailsWithContext() {
        ValidateRuleNotFoundException e = assertThrows(ValidateRuleNotFoundException.class,
                () -> validator.validate(new MissingRule()));

        assertEquals("nope", e.getRuleName());
        assertEquals(MissingRule.class, e.getBeanClass());
    }

    // -----------------------------------------------------------------------
    // Composed constraints (@ReportAsSingleViolation)
    // -----------------------------------------------------------------------

    @Test
    public void reportAsSingleViolationCollapsesComposingViolations() {
        BeanRuleViolations violations = validator.validate(new Named("ab"));

        assertEquals(1, violations.getViolations().size());
        RuleViolation violation = violations.getViolations().get(0);
        assertEquals("org.rulii.test.validname.message", violation.getErrorCode());
        assertEquals(List.of("name"), paths(violations));
    }

    @Test
    public void reportAsSingleViolationPassesWhenCompositionHolds() {
        assertTrue(validator.validate(new Named("Alice")).getViolations().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Messages
    // -----------------------------------------------------------------------

    @Test
    public void bundleMessageRendersThroughValidator() {
        // The test ValidationMessages bundle carries the jakarta Size template.
        Customer customer = new Customer("Ada", 36, new Address("x"), List.of("only"));
        BeanRuleViolations violations = validator.validate(customer);

        assertEquals("size must be between 2 and 4", violations.getViolations().get(0).getErrorMessage());
    }

    // -----------------------------------------------------------------------
    // Embedding (R10.4b)
    // -----------------------------------------------------------------------

    @Test
    public void ruleFlowRunsStandaloneWithPaths() {
        RuleFlow<?> flow = validator.getRuleFlow(Customer.class);

        Bindings bindings = Bindings.builder().standard();
        bindings.bind("$this", Object.class, new Customer("  ", 36, new Address("  "), List.of("a", "b")));
        BeanRuleViolations collector = new BeanRuleViolations();
        bindings.bind("ruleViolations", RuleViolations.class, collector);

        flow.run(bindings);

        // Fresh traversal state from the flow-param default: paths and cascade both work.
        assertTrue(paths(collector).containsAll(List.of("name", "address.city")));
    }

    @Test
    public void metadataAndFlowAreExposed() {
        assertNotNull(validator.getMetadata(Customer.class));
        assertNotNull(validator.getRuleFlow(Customer.class));
        assertSame(validator.getRuleFlow(Customer.class), validator.getRuleFlow(Customer.class));
    }
}
