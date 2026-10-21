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
import jakarta.validation.ElementKind;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.model.UnrulyException;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The collector stamps every violation with its path and provenance from the open frame.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanRuleViolationsTest {

    private static final BeanRules ORDER_RULES = new RuleMaterializer(ConstraintMapperRegistry.builder()
            .mapper(new SimpleConstraintMapper<>(ValidOrder.class, List.of(Object.class),
                    (a, t) -> List.of(t.configure(Validators.notNull(t.value()), true).build())))
            .build()).materialize(BeanDefinition.load(Order.class, ValueExtractorRegistry.builder().build()));

    public BeanRuleViolationsTest() {
        super();
    }

    @Test
    public void propertyRuleReportsAtItsLeaf() {
        Order order = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(order);

        try (BeanRuleViolations.Frame frame = violations.enter(PropertyPath.root(), ORDER_RULES, order)) {
            assertNotNull(frame);
            run(violations, "code_size", "x");
            run(violations, "code_notNull", null);
        }

        assertEquals(2, violations.size());
        BeanRuleViolation size = violations.getBeanViolations().get(0);
        assertEquals("code", size.getPropertyPath().toString());
        assertEquals(ElementKind.PROPERTY, size.getPropertyPath().getLeafNode().getKind());
        assertEquals("code_size", size.getRuleName());
        assertEquals(Size.class, size.getConstraint().getAnnotationType());
        assertEquals("x", size.getInvalidValue());
        assertSame(order, size.getLeafBean());
        assertSame(order, size.getRootBean());
        assertEquals("code", size.getParameters().get(BeanRuleViolation.PROPERTY_PATH_PARAMETER));
        assertEquals(size.getErrorMessage(), size.getMessage());
        assertNotNull(size.getMessage());
        assertEquals("x", size.getParameterValues().get("code"));

        BeanRuleViolation notNull = violations.getBeanViolations().get(1);
        assertEquals("code", notNull.getPropertyPath().toString());
        assertEquals(NotNull.class, notNull.getConstraint().getAnnotationType());
        assertNull(notNull.getInvalidValue());
        assertTrue(notNull.toString().contains("propertyPath='code'"));
        assertTrue(notNull.toString().contains("constraint=NotNull"));
    }

    @Test
    public void classRuleReportsAtTheBeanNode() {
        Order order = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(order);
        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), ORDER_RULES, order)) {
            run(violations, "Order_validOrder", null);
        }
        BeanRuleViolation violation = violations.getBeanViolations().get(0);
        assertEquals("", violation.getPropertyPath().toString());
        assertEquals(ElementKind.BEAN, violation.getPropertyPath().getLeafNode().getKind());
        assertEquals(ValidOrder.class, violation.getConstraint().getAnnotationType());
        assertSame(order, violation.getLeafBean());

        // Nested: the bean node follows the object path and renders nothing.
        BeanRuleViolations nested = new BeanRuleViolations();
        try (BeanRuleViolations.Frame ignored = nested.enter(PropertyPath.root().property("order"), ORDER_RULES, order)) {
            run(nested, "Order_validOrder", null);
        }
        assertEquals("order", nested.getBeanViolations().get(0).getPropertyPath().toString());
        assertEquals(2, nested.getBeanViolations().get(0).getPropertyPath().getNodes().size());
        assertNull(nested.getBeanViolations().get(0).getRootBean());
    }

    @Test
    public void elementRuleReportsAtTheFramePath() {
        Order order = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(order);
        PropertyPath tags = PropertyPath.root().property("tags");
        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), ORDER_RULES, order)) {
            try (BeanRuleViolations.Frame element = violations.enter(tags.withIndex(2, List.class, 0).element("<list element>"), ORDER_RULES, order)) {
                assertEquals("tags[2].<list element>", violations.getCurrentPath().toString());
                run(violations, "tags_List0_notBlank", " ");
            }
            try (BeanRuleViolations.Frame value = violations.enter(PropertyPath.root().property("counts").withKey("a", Map.class, 1).element("<map value>"), ORDER_RULES, order)) {
                run(violations, "counts_Map1_positive", -1);
            }
            assertEquals("", violations.getCurrentPath().toString());
        }

        List<BeanRuleViolation> list = violations.getBeanViolations();
        assertEquals("tags[2].<list element>", list.get(0).getPropertyPath().toString());
        PropertyPath.Node leaf = list.get(0).getPropertyPath().getLeafNode();
        assertEquals(ElementKind.CONTAINER_ELEMENT, leaf.getKind());
        assertEquals(2, leaf.getIndex());
        assertEquals(List.class, leaf.getContainerClass());
        assertEquals(NotBlank.class, list.get(0).getConstraint().getAnnotationType());
        assertEquals(" ", list.get(0).getInvalidValue());

        assertEquals("counts[a].<map value>", list.get(1).getPropertyPath().toString());
        assertEquals("a", list.get(1).getPropertyPath().getLeafNode().getKey());
        assertEquals(Positive.class, list.get(1).getConstraint().getAnnotationType());
        assertEquals(-1, list.get(1).getInvalidValue());
    }

    @Test
    public void unknownRuleReportsAtTheFramePathWithoutProvenance() {
        Order order = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(order);
        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root().property("code"), ORDER_RULES, order)) {
            violations.add("registryRule", "registry.code");
        }
        BeanRuleViolation violation = violations.getBeanViolations().get(0);
        assertEquals("code", violation.getPropertyPath().toString());
        assertNull(violation.getConstraint());
        assertNull(violation.getInvalidValue());
        assertSame(order, violation.getLeafBean());
        assertSame(order, violation.getRootBean());
        assertEquals("registryRule", violation.getRuleName());
    }

    @Test
    public void noFrameReportsAtTheRoot() {
        Order order = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(order);
        assertEquals(PropertyPath.root(), violations.getCurrentPath());
        run(violations, "code_size", "x");
        BeanRuleViolation violation = violations.getBeanViolations().get(0);
        assertTrue(violation.getPropertyPath().isRoot());
        assertNull(violation.getConstraint());
        assertNull(violation.getLeafBean());
        assertSame(order, violation.getRootBean());
        assertEquals("", violation.getParameters().get(BeanRuleViolation.PROPERTY_PATH_PARAMETER));
    }

    @Test
    public void descentNestsFrames() {
        Order parent = new Order();
        Order child = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(parent);
        try (BeanRuleViolations.Frame root = violations.enter(PropertyPath.root(), ORDER_RULES, parent)) {
            run(violations, "code_size", "x");
            PropertyPath orders = PropertyPath.root().property("orders");
            for (int i = 0; i < 2; i++) {
                try (BeanRuleViolations.Frame element = violations.enter(orders.withIndex(i, List.class, 0), ORDER_RULES, child)) {
                    run(violations, "code_size", "y");
                }
            }
            run(violations, "code_size", "z");
        }

        List<String> paths = violations.getBeanViolations().stream().map(v -> v.getPropertyPath().toString()).toList();
        assertEquals(List.of("code", "orders[0].code", "orders[1].code", "code"), paths);
        assertSame(child, violations.getBeanViolations().get(1).getLeafBean());
        assertSame(parent, violations.getBeanViolations().get(1).getRootBean());
        assertSame(parent, violations.getBeanViolations().get(3).getLeafBean());

        // Same rule, different element: distinct violations.
        assertNotEquals(violations.getBeanViolations().get(1), violations.getBeanViolations().get(2));
        assertEquals(violations.getBeanViolations().get(0), violations.getBeanViolations().get(3));
        assertNotEquals(violations.getBeanViolations().get(0).hashCode(), violations.getBeanViolations().get(1).hashCode());
    }

    @Test
    public void framesMustCloseInOrder() {
        BeanRuleViolations violations = new BeanRuleViolations();
        BeanRuleViolations.Frame outer = violations.enter(PropertyPath.root(), ORDER_RULES, null);
        BeanRuleViolations.Frame inner = violations.enter(PropertyPath.root().property("a"), ORDER_RULES, null);
        assertThrows(UnrulyException.class, outer::close);
        inner.close();
        outer.close();
        assertThrows(UnrulyException.class, outer::close);
        assertTrue(outer.toString().contains("Order"));
        assertThrows(IllegalArgumentException.class, () -> violations.enter(null, ORDER_RULES, null));
        assertThrows(IllegalArgumentException.class, () -> violations.enter(PropertyPath.root(), null, null));
    }

    @Test
    public void captureDivertsAndNests() {
        Order order = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(order);
        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), ORDER_RULES, order)) {
            run(violations, "code_size", "x");
            List<BeanRuleViolation> outer = violations.capture(() -> {
                run(violations, "code_notNull", null);
                List<BeanRuleViolation> inner = violations.capture(() -> run(violations, "Order_validOrder", null));
                assertEquals(1, inner.size());
                assertEquals("Order_validOrder", inner.get(0).getRuleName());
                // The inner wrapper reports its host while the outer capture is still open.
                violations.add("host", "host.code");
            });
            assertEquals(List.of("code_notNull", "host"), outer.stream().map(RuleViolation::getRuleName).toList());
            assertEquals("code", outer.get(0).getPropertyPath().toString());
            assertThrows(UnsupportedOperationException.class, () -> outer.add(null));
            run(violations, "code_size", "y");
        }
        assertEquals(List.of("code_size", "code_size"), violations.getBeanViolations().stream().map(RuleViolation::getRuleName).toList());

        // A failing body still pops the capture.
        assertThrows(IllegalStateException.class, () -> violations.capture(() -> { throw new IllegalStateException("boom"); }));
        violations.add("after", "after.code");
        assertEquals(3, violations.size());
        assertThrows(IllegalArgumentException.class, () -> violations.capture(null));
    }

    @Test
    public void everyEntryIsStampedAndStampedOnesPassThrough() {
        Order order = new Order();
        BeanRuleViolations violations = new BeanRuleViolations(order);
        RuleViolation plainA = new RuleViolation("a", "code", Severity.WARNING, "warn", Map.of("value", 1));
        RuleViolation plainB = new RuleViolation("b", "code", null, null, null);
        BeanRuleViolation stamped = new BeanRuleViolation(plainB, PropertyPath.root().property("elsewhere"), null, 7, null, null);

        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root().property("here"), ORDER_RULES, order)) {
            violations.addAll(plainA);
            violations.addAll(List.of(plainB, stamped));
            violations.add("c", "code", Severity.INFO, "info");
        }

        List<BeanRuleViolation> list = violations.getBeanViolations();
        assertEquals(4, list.size());
        assertEquals("here", list.get(0).getPropertyPath().toString());
        assertEquals(Severity.WARNING, list.get(0).getSeverity());
        assertEquals("warn", list.get(0).getMessage());
        assertEquals(1, list.get(0).getParameterValues().get("value"));
        assertEquals("here", list.get(1).getPropertyPath().toString());
        assertSame(stamped, list.get(2));
        assertEquals("elsewhere", list.get(2).getPropertyPath().toString());
        assertEquals(7, list.get(2).getInvalidValue());
        assertEquals("here", list.get(3).getPropertyPath().toString());
        assertEquals(Severity.INFO, list.get(3).getSeverity());
        assertTrue(violations.hasWarnings());
        assertThrows(UnsupportedOperationException.class, () -> list.add(null));
        assertThrows(IllegalArgumentException.class, () -> violations.add((RuleViolation) null));
    }

    @Test
    public void violationConstructorChecksItsArguments() {
        RuleViolation plain = new RuleViolation("a", "code", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> new BeanRuleViolation(null, PropertyPath.root(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new BeanRuleViolation(plain, null, null, null, null, null));

        BeanRuleViolation a = new BeanRuleViolation(plain, PropertyPath.root().property("x"), null, null, null, null);
        BeanRuleViolation b = new BeanRuleViolation(plain, PropertyPath.root().property("x"), null, "ignored", null, null);
        BeanRuleViolation c = new BeanRuleViolation(plain, PropertyPath.root().property("y"), null, null, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, plain);
        assertNotEquals(plain, a);
        assertEquals(List.of(BeanRuleViolation.PROPERTY_PATH_PARAMETER), List.copyOf(a.getParameters().keySet()));
    }

    private static void run(BeanRuleViolations violations, String name, Object value) {
        MaterializedRule rule = ORDER_RULES.getRule(name);
        assertNotNull(rule, name);
        Bindings bindings = Bindings.builder().standard();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind(rule.getBindingName(), Object.class, value);
        rule.getRule().run(bindings);
    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface ValidOrder {
        String message() default "invalid order";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @ValidOrder
    public static class Order {
        @NotNull
        @Size(min = 2, max = 5)
        private String code;

        private List<@NotBlank String> tags;
        private Map<String, @Positive Integer> counts;
        private List<Order> orders;
    }
}
