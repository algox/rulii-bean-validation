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
package org.rulii.test.validation.beans.traversal;

import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.registry.RuleRegistry;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.ValidateRuleNotFoundException;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.extract.NoValueExtractorException;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.traversal.RuleFlowAssembler;
import org.rulii.validation.beans.traversal.Traversal;
import org.rulii.validation.rules.Validators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The traversal end to end: descent, container walk, legacy {@code @Valid} on containers,
 * {@code @Validate} at run time, cycles and shared instances, runtime-type dispatch, and the
 * plain-collector hand-over through a cascade. Paths are asserted exactly as the reference implementation
 * renders them.
 */
public class TraversalTest {

    private static final ValueExtractorRegistry EXTRACTORS = ValueExtractorRegistry.builder().build();
    private static final RuleMaterializer MATERIALIZER = new RuleMaterializer(ConstraintMapperRegistry.builder().build());
    private static final Map<Class<?>, BeanRules> RULES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, RuleFlow<?>> FLOWS = new ConcurrentHashMap<>();
    private static final RuleFlowAssembler ASSEMBLER = new RuleFlowAssembler(TraversalTest::flow, EXTRACTORS);
    private static final RuleRegistry REGISTRY = RuleRegistry.builder()
            .register(Validators.alpha(Validators.binding(BindingPlan.VALUE)).failOnNull(false).name("nicknameRule").build())
            .register(Validators.notNull(Validators.binding("address")).name("addressRequired").build())
            .register(Validators.notNull(Validators.binding(BindingPlan.PARENT)).name("parentRule").build())
            .build();

    public TraversalTest() {
        super();
    }

    private static BeanRules rules(Class<?> type) {
        return RULES.computeIfAbsent(type, t -> MATERIALIZER.materialize(BeanDefinition.load(t, EXTRACTORS)));
    }

    private static RuleFlow<?> flow(Class<?> type) {
        return FLOWS.computeIfAbsent(type, t -> ASSEMBLER.assemble(rules(t)));
    }

    private static ScopedBindings bindings(Object root, RuleViolations violations) {
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, root);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind(BindingPlan.TRAVERSAL, Traversal.class, new Traversal(RequestedGroups.DEFAULT, TraversalTest::flow, EXTRACTORS));
        return bindings;
    }

    private static BeanRuleViolations validate(Object root) {
        return validate(root, REGISTRY);
    }

    private static BeanRuleViolations validate(Object root, RuleRegistry registry) {
        BeanRuleViolations violations = new BeanRuleViolations(root);
        ScopedBindings bindings = bindings(root, violations);
        RuleContext context = RuleContext.builder().with(bindings).registry(registry).build();
        flow(root.getClass()).run(context);
        assertEquals(PropertyPath.root(), violations.getCurrentPath(), "every frame was closed");
        assertNull(bindings.getBinding(BindingPlan.PARENT), "every descent scope was popped");
        assertNull(bindings.getBinding(BindingPlan.ELEMENT), "every element scope was popped");
        return violations;
    }

    /** {@code rule@path} per violation, in report order. */
    private static List<String> report(RuleViolations violations) {
        List<String> result = new ArrayList<>();
        for (RuleViolation violation : violations.getViolations()) {
            result.add(violation.getRuleName() + "@" + ((BeanRuleViolation) violation).getPropertyPath());
        }
        return result;
    }

    private static Order order(int amount) {
        Order order = new Order();
        order.amount = amount;
        return order;
    }

    private static Address address(String city) {
        Address address = new Address();
        address.city = city;
        return address;
    }

    @Test
    public void cascadesIntoAPropertyWithPrefixedPaths() {
        Customer customer = new Customer();
        customer.name = "";
        customer.address = address("");

        BeanRuleViolations violations = validate(customer);

        assertEquals(List.of("name_notBlank@name", "city_notBlank@address.city"), report(violations));
        BeanRuleViolation city = violations.getBeanViolations().get(1);
        assertSame(customer.address, city.getLeafBean());
        assertSame(customer, city.getRootBean());
        assertEquals("", city.getInvalidValue());
        assertEquals(NotBlank.class, city.getConstraint().getAnnotationType());
        List<Path.Node> nodes = new ArrayList<>();
        city.getPropertyPath().forEach(nodes::add);
        assertEquals(List.of(ElementKind.PROPERTY, ElementKind.PROPERTY), nodes.stream().map(Path.Node::getKind).toList());
        assertEquals(List.of("address", "city"), nodes.stream().map(Path.Node::getName).toList());
    }

    @Test
    public void cascadesIntoArraysOfObjectsOneLevelDeep() {
        Fleet fleet = new Fleet();
        fleet.stops = new Address[] {address("ok"), null, address("")};
        fleet.codes = new int[] {1, 2};
        fleet.routes = List.<Address[]>of(new Address[] {address("")});

        BeanRuleViolations violations = validate(fleet);

        // The null element and the primitive array are skipped; an array inside a container is subscripted under its element node.
        assertEquals(List.of("city_notBlank@stops[2].city", "city_notBlank@routes[0].<list element>[0].city"), report(violations));
        List<Path.Node> nodes = new ArrayList<>();
        violations.getBeanViolations().get(0).getPropertyPath().forEach(nodes::add);
        assertEquals(List.of("stops", "city"), nodes.stream().map(Path.Node::getName).toList());
        assertTrue(nodes.get(1).isInIterable());
        assertEquals(2, nodes.get(1).getIndex());
        assertEquals(Address[].class, nodes.get(1).as(Path.PropertyNode.class).getContainerClass());
        assertNull(nodes.get(1).as(Path.PropertyNode.class).getTypeArgumentIndex());
        assertSame(fleet.stops[2], violations.getBeanViolations().get(0).getLeafBean());

        fleet.stops = null;
        fleet.routes = null;
        assertTrue(validate(fleet).isEmpty());

        // A nested array is a container element position on an array, which is not supported: fail fast at metadata read.
        assertThrows(NoValueExtractorException.class, () -> rules(Grid.class));
    }

    @Test
    public void nullReferencesAreSkipped() {
        Customer customer = new Customer();
        customer.name = "ok";

        BeanRuleViolations violations = validate(customer);

        // address, orders, orderList, ... are all null: nothing from them, and nothing blows up.
        assertEquals(List.of("addressRequired@"), report(violations));
    }

    @Test
    public void cyclesTerminateAndSharedInstancesValidateOnce() {
        Customer customer = new Customer();
        customer.name = "ok";
        customer.address = address("");
        customer.address.owner = customer;      // back reference: a cycle
        customer.alsoAddress = customer.address; // the same instance twice: a diamond
        customer.self = customer;                // self reference

        BeanRuleViolations violations = validate(customer);

        assertEquals(List.of("city_notBlank@address.city"), report(violations), "once, under the first path");

        // Running the root again within the same call is a no-op: it was validated already.
        BeanRuleViolations again = new BeanRuleViolations(customer);
        ScopedBindings bindings = bindings(customer, again);
        Traversal traversal = bindings.getValue(BindingPlan.TRAVERSAL);
        RuleContext context = RuleContext.builder().with(bindings).registry(REGISTRY).build();
        flow(Customer.class).run(context);
        flow(Customer.class).run(context);
        assertEquals(1, again.size());
        assertTrue(traversal.isValidated(customer));
        assertTrue(traversal.isValidated(customer.address));
    }

    @Test
    public void containerElementPathsMatchTheReferenceImplementation() {
        Inventory inventory = new Inventory();
        inventory.tags = List.of("", "ok", " ");
        inventory.tagSet = new LinkedHashSet<>(List.of("ok", ""));
        inventory.counts = new LinkedHashMap<>();
        inventory.counts.put("", 1);
        inventory.counts.put("a", -1);
        inventory.matrix = List.of(List.of("x"), List.of("", "y"));
        inventory.email = Optional.of("not-an-email");

        BeanRuleViolations violations = validate(inventory);

        assertEquals(List.of(
                "tags_List0_notBlank@tags[0].<list element>",
                "tags_List0_notBlank@tags[2].<list element>",
                "tagSet_Set0_notBlank@tagSet[].<iterable element>",
                "counts_Map0_notBlank@counts[].<map key>",
                "counts_Map1_positive@counts[a].<map value>",
                "matrix_List0_List0_notBlank@matrix[1].<list element>[0].<list element>",
                "email_Optional0_email@email"), report(violations));

        BeanRuleViolation tag = violations.getBeanViolations().get(1);
        assertEquals(" ", tag.getInvalidValue());
        assertSame(inventory, tag.getLeafBean());
        PropertyPath.Node element = ((PropertyPath) tag.getPropertyPath()).getLeafNode();
        assertEquals(ElementKind.CONTAINER_ELEMENT, element.getKind());
        assertEquals("<list element>", element.getName());
        assertTrue(element.isInIterable());
        assertEquals(2, element.getIndex());
        assertEquals(List.class, element.getContainerClass());
        assertEquals(0, element.getTypeArgumentIndex());

        BeanRuleViolation count = violations.getBeanViolations().get(4);
        assertEquals(-1, count.getInvalidValue());
        assertEquals("a", count.getPropertyPath().getLeafNode().getKey());
        assertEquals(Positive.class, count.getConstraint().getAnnotationType());

        BeanRuleViolation email = violations.getBeanViolations().get(6);
        assertEquals(ElementKind.PROPERTY, email.getPropertyPath().getLeafNode().getKind(), "Optional unwraps in place");
        assertEquals("not-an-email", email.getInvalidValue());
    }

    @Test
    public void containerElementsCascadeWithoutTheElementNode() {
        Inventory inventory = new Inventory();
        inventory.orderList = List.of(order(1), order(-2));
        inventory.ordersByCode = new LinkedHashMap<>();
        inventory.ordersByCode.put("a", order(-3));
        inventory.byKey = new LinkedHashMap<>();
        inventory.byKey.put(new Key(""), 1);
        inventory.optionalOrder = Optional.of(order(-4));
        inventory.nested = List.of(List.of(order(-5)));

        BeanRuleViolations violations = validate(inventory);

        assertEquals(List.of(
                "amount_positive@orderList[1].amount",
                "amount_positive@ordersByCode[a].amount",
                "id_notBlank@byKey[key:].id",
                "amount_positive@optionalOrder.amount",
                "amount_positive@nested[0].<list element>[0].amount"), report(violations));

        BeanRuleViolation first = violations.getBeanViolations().get(0);
        assertSame(inventory.orderList.get(1), first.getLeafBean());
        assertSame(inventory, first.getRootBean());
        Path.Node leaf = first.getPropertyPath().getLeafNode();
        assertTrue(leaf.isInIterable());
        assertEquals(1, leaf.getIndex());
        assertEquals("a", violations.getBeanViolations().get(1).getPropertyPath().getLeafNode().getKey());
    }

    @Test
    public void legacyValidOnAContainerCascadesIntoItsElements() {
        Customer customer = new Customer();
        customer.name = "ok";
        customer.address = address("here");
        customer.orders = List.of(order(-1), order(2), order(-3));
        customer.legacyMap = new LinkedHashMap<>();
        customer.legacyMap.put(new Key(""), order(-5));
        customer.legacySet = new LinkedHashSet<>(List.of(order(-6)));

        BeanRuleViolations violations = validate(customer);

        // List elements by index, map VALUES by key (keys are not cascaded), a Set through the iterable extractor.
        assertEquals(List.of(
                "amount_positive@orders[0].amount",
                "amount_positive@orders[2].amount",
                "amount_positive@legacyMap[key:].amount",
                "amount_positive@legacySet[].amount"), report(violations));
    }

    @Test
    public void runtimeTypeDecidesTheFlow() {
        Customer customer = new Customer();
        customer.name = "ok";
        customer.address = address("here");
        customer.dynamic = address("");

        assertEquals(List.of("city_notBlank@dynamic.city"), report(validate(customer)));

        // A container at runtime behind an Object declaration: cascaded through the extractor of the runtime type.
        customer.dynamic = new ArrayList<>(List.of(order(-1)));
        assertEquals(List.of("amount_positive@dynamic[0].amount"), report(validate(customer)));

        // A plain value: a flow of its own, with nothing to say.
        customer.dynamic = "text";
        assertTrue(validate(customer).isEmpty());
    }

    @Test
    public void validateRulesRunAtPropertyAndClassLevel() {
        Customer customer = new Customer();
        customer.name = "ok";
        customer.nickname = "n1!";

        BeanRuleViolations violations = validate(customer);

        assertEquals(List.of("nicknameRule@nickname", "addressRequired@"), report(violations));

        BeanRuleViolation nickname = violations.getBeanViolations().get(0);
        assertNull(nickname.getConstraint(), "a registry rule has no constraint provenance");
        assertSame(customer, nickname.getLeafBean());
        assertEquals(ElementKind.PROPERTY, nickname.getPropertyPath().getLeafNode().getKind());

        BeanRuleViolation required = violations.getBeanViolations().get(1);
        assertEquals(ElementKind.BEAN, required.getPropertyPath().getLeafNode().getKind(), "class-level: the bean node");
        assertSame(customer, required.getLeafBean());

        // $value is gone afterwards, and the property-level rule saw the property value.
        customer.nickname = "nick";
        customer.address = address("here");
        assertTrue(validate(customer).isEmpty());
    }

    @Test
    public void validateRulesOfCascadedBeansSeeTheParentAndReportUnderThePrefix() {
        Customer customer = new Customer();
        customer.name = "ok";
        customer.address = address("here");
        customer.address.note = "n1!";

        BeanRuleViolations violations = validate(customer);

        // Address.note runs nicknameRule on $value; parentRule (class-level) reads $parent = the customer and passes.
        assertEquals(List.of("nicknameRule@address.note"), report(violations));
    }

    @Test
    public void validateRulesRunAfterTheRulesAndBeforeTheCascades() {
        Customer customer = new Customer();
        customer.name = "";
        customer.nickname = "n1!";
        customer.address = address("");

        assertEquals(List.of("name_notBlank@name", "nicknameRule@nickname", "city_notBlank@address.city"),
                report(validate(customer)));
    }

    @Test
    public void missingValidateRulesFailLoudly() {
        Customer customer = new Customer();
        customer.name = "ok";
        customer.address = address("here");

        RuleRegistry without = RuleRegistry.builder()
                .register(Validators.alpha(Validators.binding(BindingPlan.VALUE)).failOnNull(false).name("nicknameRule").build())
                .register(Validators.notNull(Validators.binding("address")).name("addressRequired").build())
                .build();
        ValidateRuleNotFoundException missing = assertThrows(ValidateRuleNotFoundException.class, () -> validate(customer, without));
        assertEquals("parentRule", missing.getRuleName());
        assertEquals(Address.class, missing.getBeanClass());
        assertEquals(List.of("parentRule"), missing.getDefinition().getRuleNames());
        assertTrue(missing.getMessage().contains("nothing is registered"));

        // A context built without a registry: whatever the builder defaults to, the name cannot run.
        BeanRuleViolations violations = new BeanRuleViolations(customer);
        RuleContext context = RuleContext.builder().build(bindings(customer, violations));
        ValidateRuleNotFoundException none = assertThrows(ValidateRuleNotFoundException.class, () -> flow(Customer.class).run(context));
        assertEquals("nicknameRule", none.getRuleName(), "the first @Validate run: property level, before class level");
        assertEquals(Customer.class, none.getBeanClass());
        assertTrue(none.getMessage().contains("cannot run rule [nicknameRule]"));
    }

    @Test
    public void plainCollectorGetsStampedViolationsThroughCascades() {
        Customer customer = new Customer();
        customer.name = "";
        customer.address = address("");
        customer.orders = List.of(order(-1));

        RuleViolations plain = new RuleViolations();
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, customer);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, plain);
        flow(Customer.class).run(RuleContext.builder().with(bindings).registry(REGISTRY).build());

        assertEquals(List.of("name_notBlank@name", "city_notBlank@address.city", "amount_positive@orders[0].amount"), report(plain));
        assertSame(customer, ((BeanRuleViolation) plain.getViolation(2)).getRootBean());
        assertSame(plain, bindings.getValue(BindingPlan.RULE_VIOLATIONS), "the caller's collector stays bound");
    }

    @Test
    public void embeddedRunDefaultsEverything() {
        Customer customer = new Customer();
        customer.name = "ok";
        customer.address = address("");

        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, customer);
        flow(Customer.class).run(RuleContext.builder().with(bindings).registry(REGISTRY).build());

        BeanRuleViolations violations = (BeanRuleViolations) bindings.getValue(BindingPlan.RULE_VIOLATIONS);
        assertEquals(List.of("city_notBlank@address.city"), report(violations));
        assertSame(customer, violations.getRootBean());
    }

    @Test
    public void descendIsANoOpForNullAndForValidatedObjects() {
        Traversal traversal = new Traversal(RequestedGroups.DEFAULT, TraversalTest::flow, EXTRACTORS);
        Address address = address("");
        BeanRuleViolations violations = new BeanRuleViolations(address);
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, new Object());
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind(BindingPlan.TRAVERSAL, Traversal.class, traversal);
        RuleContext context = RuleContext.builder().with(bindings).registry(REGISTRY).build();

        traversal.descend(context, null, PropertyPath.root().property("x"));
        assertTrue(violations.isEmpty());

        traversal.markValidated(address);
        traversal.descend(context, address, PropertyPath.root().property("x"));
        assertTrue(violations.isEmpty());

        Address fresh = address("");
        traversal.descend(context, fresh, PropertyPath.root().property("x"));
        assertEquals(List.of("city_notBlank@x.city"), report(violations));
        assertEquals(PropertyPath.root(), violations.getCurrentPath());
        assertNull(bindings.getBinding(BindingPlan.PARENT));

        assertThrows(IllegalArgumentException.class, () -> traversal.descend(context, fresh, null));
        assertThrows(IllegalArgumentException.class, () -> traversal.descend(null, fresh, PropertyPath.root()));
    }

    // ---- fixtures ----

    @Validate("addressRequired")
    public static class Customer {
        @NotBlank
        String name;

        @Validate("nicknameRule")
        String nickname;

        @Valid
        Address address;

        @Valid
        Address alsoAddress;

        @Valid
        Customer self;

        @Valid
        List<Order> orders;

        @Valid
        Map<Key, Order> legacyMap;

        @Valid
        Set<Order> legacySet;

        @Valid
        Object dynamic;
    }

    @Validate("parentRule")
    public static class Address {
        @NotBlank
        String city;

        @Validate("nicknameRule")
        String note;

        @Valid
        Customer owner;
    }

    public static class Fleet {
        @Valid
        Address[] stops;

        @Valid
        int[] codes;

        List<Address @Valid []> routes;
    }

    public static class Grid {
        @Valid
        Address[][] cells;
    }

    public static class Order {
        @Positive
        int amount;

        @Valid
        Customer customer;
    }

    public static class Key {
        @NotBlank
        String id;

        public Key(String id) {
            super();
            this.id = id;
        }

        @Override
        public String toString() {
            return "key:" + id;
        }
    }

    public static class Inventory {
        List<@NotBlank String> tags;
        Set<@NotBlank String> tagSet;
        Map<@NotBlank String, @Positive Integer> counts;
        List<List<@NotBlank String>> matrix;
        Optional<@Email String> email;

        List<@Valid Order> orderList;
        Map<String, @Valid Order> ordersByCode;
        Map<@Valid Key, Integer> byKey;
        Optional<@Valid Order> optionalOrder;
        List<List<@Valid Order>> nested;
    }
}
