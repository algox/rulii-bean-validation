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

import jakarta.validation.Constraint;
import jakarta.validation.ElementKind;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.model.UnrulyException;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.traversal.RuleFlowAssembler;
import org.rulii.validation.beans.traversal.Traversal;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The per-class flow binds the properties, runs the rules, and reports with paths.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class RuleFlowAssemblerTest {

    private static final ValueExtractorRegistry EXTRACTORS = ValueExtractorRegistry.builder().build();
    private static final RuleMaterializer MATERIALIZER = new RuleMaterializer(ConstraintMapperRegistry.builder()
            // A class-level rule reading a sibling property by its binding name: proves the properties are in scope.
            .mapper(new SimpleConstraintMapper<>(ValidOrder.class, List.of(Object.class),
                    (a, t) -> List.of(t.configure(Validators.notNull(Validators.binding("name")), true).build())))
            .build());
    private static final Map<Class<?>, BeanRules> RULES = new ConcurrentHashMap<>();
    private static final RuleFlowAssembler ASSEMBLER = new RuleFlowAssembler(RuleFlowAssemblerTest::flow, EXTRACTORS);

    public RuleFlowAssemblerTest() {
        super();
    }

    private static final Map<Class<?>, RuleFlow<?>> FLOWS = new ConcurrentHashMap<>();

    private static BeanRules rules(Class<?> type) {
        return RULES.computeIfAbsent(type, t -> MATERIALIZER.materialize(BeanDefinition.load(t, EXTRACTORS)));
    }

    private static RuleFlow<?> flow(Class<?> type) {
        return FLOWS.computeIfAbsent(type, t -> ASSEMBLER.assemble(rules(t)));
    }

    @Test
    public void flowRunsTheRulesAndReportsWithPaths() {
        RuleFlow<?> flow = ASSEMBLER.assemble(rules(Order.class));
        assertEquals("orderValidationFlow", flow.getName());
        assertEquals("orderValidationFlow", RuleFlowAssembler.flowName(Order.class));
        assertEquals(Order.class.getName(), flow.getDefinition().getCategory(), "flows are categorised under the bean class");
        assertEquals(List.of(RuleFlowAssembler.BEAN_TAG), flow.getDefinition().getTags());

        Order order = new Order();
        order.code = "x";
        BeanRuleViolations violations = new BeanRuleViolations(order);
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, order);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind(BindingPlan.TRAVERSAL, Traversal.class, new Traversal(RequestedGroups.DEFAULT, RuleFlowAssemblerTest::flow, EXTRACTORS));

        flow.run(bindings);

        List<BeanRuleViolation> list = violations.getBeanViolations();
        assertEquals(List.of("code_size", "name_notNull", "Order_validOrder"),
                list.stream().map(RuleViolation::getRuleName).toList());
        assertEquals(List.of("code", "name", ""), list.stream().map(v -> v.getPropertyPath().toString()).toList());
        assertEquals(ElementKind.BEAN, list.get(2).getPropertyPath().getLeafNode().getKind());
        assertEquals("x", list.get(0).getInvalidValue());
        assertSame(order, list.get(0).getLeafBean());
        assertSame(order, list.get(0).getRootBean());
        assertEquals(Size.class, list.get(0).getConstraint().getAnnotationType());
        assertEquals(1, order.nameReads, "the getter was read once for name_notNull and the class rule alike");

        // The flow's scope is gone: the properties are not left in the caller's bindings.
        assertNull(bindings.getBinding("code"));
        assertNull(bindings.getBinding("name"));
    }

    @Test
    public void flowDefaultsItsParametersForEmbeddedRuns() {
        RuleFlow<?> flow = ASSEMBLER.assemble(rules(Order.class));
        Order order = new Order();
        order.code = "ok";

        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, order);
        flow.run(bindings);

        // The defaults land in the caller's bindings: the report is readable afterwards.
        Object report = bindings.getValue(BindingPlan.RULE_VIOLATIONS);
        assertInstanceOf(BeanRuleViolations.class, report);
        BeanRuleViolations violations = (BeanRuleViolations) report;
        assertSame(order, violations.getRootBean());
        assertSame(order, bindings.getValue(BindingPlan.ROOT));
        assertInstanceOf(Traversal.class, bindings.getValue(BindingPlan.TRAVERSAL));
        assertEquals(List.of("name_notNull", "Order_validOrder"), violations.getBeanViolations().stream().map(RuleViolation::getRuleName).toList());
        assertEquals("name", violations.getBeanViolations().get(0).getPropertyPath().toString());
        assertEquals("", violations.getBeanViolations().get(1).getPropertyPath().toString());

        // Without $this the flow refuses to run.
        assertThrows(UnrulyException.class, () -> flow.run(Bindings.builder().standard()));
    }

    @Test
    public void plainCollectorGetsUndecoratedViolations() {
        RuleFlow<?> flow = ASSEMBLER.assemble(rules(Order.class));
        Order order = new Order();
        order.code = "x";
        RuleViolations plain = new RuleViolations();

        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, order);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, plain);
        flow.run(bindings);

        // The flow ran its rules into a temporary path-aware collector and handed everything over, paths included.
        assertEquals(List.of("code_size", "name_notNull", "Order_validOrder"), plain.getViolations().stream().map(RuleViolation::getRuleName).toList());
        assertInstanceOf(BeanRuleViolation.class, plain.getViolation(0));
        assertEquals("code", ((BeanRuleViolation) plain.getViolation(0)).getPropertyPath().toString());
        assertSame(order, ((BeanRuleViolation) plain.getViolation(0)).getRootBean());
        assertSame(plain, bindings.getValue(BindingPlan.RULE_VIOLATIONS), "the caller's collector stays bound");
    }

    @Test
    public void groupsComeFromTheTraversal() {
        RuleFlow<?> flow = ASSEMBLER.assemble(rules(Order.class));
        Order order = new Order();
        order.code = "x";
        BeanRuleViolations violations = new BeanRuleViolations(order);

        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, order);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind(BindingPlan.TRAVERSAL, Traversal.class, new Traversal(RequestedGroups.of(OnCreate.class), RuleFlowAssemblerTest::flow, EXTRACTORS));
        flow.run(bindings);

        assertEquals(List.of("created_notNull"), violations.getBeanViolations().stream().map(RuleViolation::getRuleName).toList());
        assertEquals("created", violations.getBeanViolations().get(0).getPropertyPath().toString());
    }

    @Test
    public void reportAsSingleViolationCollapsesEndToEnd() {
        RuleFlow<?> flow = ASSEMBLER.assemble(rules(Composed.class));
        Composed composed = new Composed();
        composed.strict = "ab";

        BeanRuleViolations violations = new BeanRuleViolations(composed);
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, composed);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        flow.run(bindings);

        assertEquals(1, violations.size());
        BeanRuleViolation violation = violations.getBeanViolations().get(0);
        assertEquals("strict_strictCode", violation.getRuleName());
        assertEquals("strict", violation.getPropertyPath().toString());
        assertEquals("strict", violation.getMessage());
        assertEquals(StrictCode.class, violation.getConstraint().getAnnotationType());
        assertEquals("ab", violation.getInvalidValue());

        // The same through a plain collector: still one violation.
        RuleViolations plain = new RuleViolations();
        ScopedBindings plainBindings = Bindings.builder().scoped();
        plainBindings.bind(BindingPlan.THIS, Object.class, composed);
        plainBindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, plain);
        flow.run(plainBindings);
        assertEquals(List.of("strict_strictCode"), plain.getViolations().stream().map(RuleViolation::getRuleName).toList());

        // A valid value: nothing at all.
        composed.strict = "abc";
        BeanRuleViolations none = new BeanRuleViolations(composed);
        ScopedBindings okBindings = Bindings.builder().scoped();
        okBindings.bind(BindingPlan.THIS, Object.class, composed);
        okBindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, none);
        flow.run(okBindings);
        assertTrue(none.isEmpty());
    }

    @Test
    public void flowEmbedsInAnOuterFlow() {
        RuleFlow<?> outer = RuleFlow.builder()
                .name("outer")
                .run(ASSEMBLER.assemble(rules(Order.class)))
                .build();
        Order order = new Order();
        order.code = "ok";
        BeanRuleViolations violations = new BeanRuleViolations(order);

        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.THIS, Object.class, order);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        outer.run(bindings);

        // The class rule read "name" through the flow's scope, which is gone again afterwards.
        assertEquals(List.of("name_notNull", "Order_validOrder"), violations.getBeanViolations().stream().map(RuleViolation::getRuleName).toList());
        assertEquals(1, order.nameReads);
        assertNull(bindings.getBinding("name"));
    }

    @Test
    public void traversalBasics() {
        Traversal traversal = new Traversal(RequestedGroups.DEFAULT, RuleFlowAssemblerTest::flow, EXTRACTORS);
        Object a = new Object();
        assertTrue(traversal.markValidated(a));
        assertFalse(traversal.markValidated(a));
        assertTrue(traversal.isValidated(a));
        assertFalse(traversal.isValidated(new Object()));
        assertSame(flow(Order.class), traversal.getFlow(Order.class));
        assertSame(rules(Order.class).ruleSet(RequestedGroups.DEFAULT), traversal.ruleSet(rules(Order.class)));
        assertTrue(traversal.toString().contains("validated=1"));
        assertThrows(IllegalArgumentException.class, () -> new Traversal(null, RuleFlowAssemblerTest::flow, EXTRACTORS));
        assertThrows(IllegalArgumentException.class, () -> new Traversal(RequestedGroups.DEFAULT, null, EXTRACTORS));
        assertThrows(IllegalArgumentException.class, () -> new Traversal(RequestedGroups.DEFAULT, t -> null, EXTRACTORS).getFlow(Order.class));

        ScopedBindings bindings = Bindings.builder().scoped();
        RuleContext context = RuleContext.builder().build(bindings);
        assertThrows(IllegalArgumentException.class, () -> Traversal.of(context));
        assertNull(Traversal.collector(context));
        bindings.bind(BindingPlan.TRAVERSAL, Traversal.class, traversal);
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, new RuleViolations());
        assertSame(traversal, Traversal.of(context));
        assertNull(Traversal.collector(context), "a plain collector is not path-aware");
        bindings.addScope();
        BeanRuleViolations inner = new BeanRuleViolations();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, inner);
        assertSame(inner, Traversal.collector(context), "the innermost collector wins");
        assertEquals(PropertyPath.root(), inner.getCurrentPath());
    }

    // ---- fixtures ----

    public interface OnCreate {}

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface ValidOrder {
        String message() default "invalid order";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @NotNull
    @Size(min = 3)
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StrictCode {
        String message() default "strict";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @ValidOrder
    public static class Order {
        int nameReads;

        @NotNull
        @Size(min = 2, max = 5)
        private String code;

        private String name;

        @NotNull(groups = OnCreate.class)
        private String created;

        public String getCode() { return code; }

        @NotNull
        public String getName() { nameReads++; return name; }

        public String getCreated() { return created; }
    }

    public static class Composed {
        @StrictCode
        private String strict;

        public String getStrict() { return strict; }
    }
}
