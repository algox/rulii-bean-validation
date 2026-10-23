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

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleExecutionStatus;
import org.rulii.rule.RuleResult;
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.payload.Warning;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.assembly.ReportAsSingleViolationRule;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintMapperException;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.validator.ConstraintValidatorRule;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RuleMaterializer} and {@link BeanRules}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class RuleMaterializerTest {

    private static final ValueExtractorRegistry EXTRACTORS = ValueExtractorRegistry.builder().build();

    public RuleMaterializerTest() {
        super();
    }

    @Test
    public void rulesFollowPropertyThenDeclarationOrder() {
        BeanRules rules = materialize(Order.class, mappers());

        assertEquals(Order.class, rules.getBeanClass());
        assertEquals("OrderRules", rules.getName());
        assertEquals(List.of("code_notNull", "code_size", "name_notNull", "name_size", "quantity_min", "email_email", "email_email_2"),
                names(rules.getPropertyRules()));
        assertEquals(List.of("Order_validOrder"), names(rules.getClassRules()));
        assertEquals(List.of("tags_List0_notBlank", "counts_Map0_notBlank", "counts_Map1_positive"),
                rules.getElementRules().stream().flatMap(e -> e.rules().stream()).map(MaterializedRule::getName).toList());
        assertEquals(11, rules.getRules().size());
        assertFalse(rules.isEmpty());
        assertNotNull(rules.getRule("quantity_min"));
        assertNull(rules.getRule("missing"));
        assertTrue(rules.toString().contains("propertyRules=7"));
    }

    @Test
    public void rulesReadTheirDeclarationsBinding() {
        BeanRules rules = materialize(Order.class, mappers());
        BindingPlan plan = rules.getBindingPlan();

        // name: field @NotNull reads the field off $this, getter @Size reads the primary binding; both report as "name".
        assertEquals("name", rules.getRule("name_notNull").getBindingName());
        assertEquals("name", rules.getRule("name_notNull").getTarget().getValueName());
        assertEquals("name", rules.getRule("name_size").getBindingName());
        assertEquals("code", rules.getRule("code_size").getBindingName());
        assertEquals("quantity", rules.getRule("quantity_min").getBindingName());
        assertEquals(BindingPlan.THIS, rules.getRule("Order_validOrder").getBindingName());
        assertEquals(BindingPlan.ELEMENT, rules.getRule("tags_List0_notBlank").getBindingName());
        assertEquals(BindingPlan.KEY, rules.getRule("counts_Map0_notBlank").getBindingName());
        assertEquals(BindingPlan.VALUE, rules.getRule("counts_Map1_positive").getBindingName());
        assertFalse(plan.isPrimary(rules.getDefinition().getProperty("name").getPropertySource().getDeclarations(
                org.rulii.validation.beans.model.PropertyDeclaration.Kind.FIELD).get(0)));

        // Leaves: property rules report at the property; class and element rules leave it to the traversal.
        assertEquals("name", rules.getRule("name_notNull").getLeaf());
        assertNull(rules.getRule("Order_validOrder").getLeaf());
        assertNull(rules.getRule("tags_List0_notBlank").getLeaf());
    }

    @Test
    public void commonConfigurationIsApplied() {
        BeanRules rules = materialize(Order.class, mappers());
        MaterializedRule size = rules.getRule("code_size");

        assertEquals("jakarta.validation.constraints.Size.message", size.getTarget().getErrorCode());
        assertEquals(Severity.ERROR, size.getTarget().getSeverity());
        assertEquals("len ${min}-${max}", size.getTarget().getMessage());
        assertEquals("len {min}-{max}", size.getMessageTemplate());
        assertEquals(Size.class, size.getDefinition().getAnnotationType());
        assertEquals(String.class, size.getTarget().getType());
        assertEquals("code", size.getTarget().getValueName());
        assertFalse(size.isHosted());
        assertTrue(size.toString().contains("code_size"));

        MaterializedRule min = rules.getRule("quantity_min");
        assertEquals(Severity.WARNING, min.getTarget().getSeverity());
        assertNull(min.getTarget().getMessage(), "default message is a key: resolved from the code at render time");
        assertEquals("{jakarta.validation.constraints.Min.message}", min.getMessageTemplate());
        assertEquals(int.class, min.getTarget().getType());

        MaterializedRule classRule = rules.getRule("Order_validOrder");
        assertTrue(classRule.getTarget().isClassLevel());
        assertEquals(Order.class, classRule.getTarget().getType());
        assertEquals(BindingPlan.THIS, classRule.getTarget().getValueName());
    }

    @Test
    public void rulesRunAgainstBindings() {
        BeanRules rules = materialize(Order.class, mappers());

        RuleViolations violations = run(rules.getRule("code_size"), "code", "x");
        assertEquals(1, violations.size());
        RuleViolation violation = violations.getViolation(0);
        assertEquals("code_size", violation.getRuleName());
        assertEquals("jakarta.validation.constraints.Size.message", violation.getErrorCode());
        assertEquals("len 2-5", violation.getErrorMessage());
        assertEquals("x", violation.getParameters().get("code"));
        assertEquals("2", violation.getParameters().get("min"));

        assertEquals(0, run(rules.getRule("code_size"), "code", "abc").size());
        // Null is skipped by every rule except the presence rules.
        assertEquals(RuleExecutionStatus.SKIPPED, status(rules.getRule("code_size"), "code", null));
        assertEquals(RuleExecutionStatus.FAIL, status(rules.getRule("code_notNull"), "code", null));

        // The field constraint reads the field off $this, whatever the "name" binding holds.
        Order order = new Order();
        order.name = "set";
        assertEquals(RuleExecutionStatus.PASS, status(rules.getRule("name_notNull"), BindingPlan.THIS, order));
        assertEquals(RuleExecutionStatus.FAIL, status(rules.getRule("name_notNull"), BindingPlan.THIS, new Order()));
        RuleViolations fieldViolations = run(rules.getRule("name_notNull"), BindingPlan.THIS, new Order());
        assertEquals("name_notNull", fieldViolations.getViolation(0).getRuleName());
        assertTrue(fieldViolations.getViolation(0).getParameters().containsKey("name"));

        RuleViolations warning = run(rules.getRule("quantity_min"), "quantity", 0);
        assertEquals(Severity.WARNING, warning.getViolation(0).getSeverity());
        assertNotNull(warning.getViolation(0).getErrorMessage());

        // @Email with a regexp: two rules, both on the same binding.
        assertEquals(RuleExecutionStatus.PASS, status(rules.getRule("email_email"), "email", "bob@other.com"));
        assertEquals(RuleExecutionStatus.FAIL, status(rules.getRule("email_email_2"), "email", "bob@other.com"));
        assertEquals(RuleExecutionStatus.PASS, status(rules.getRule("email_email_2"), "email", "bob@acme.com"));

        assertEquals(RuleExecutionStatus.FAIL, status(rules.getRule("tags_List0_notBlank"), BindingPlan.ELEMENT, " "));
        assertEquals(RuleExecutionStatus.FAIL, status(rules.getRule("counts_Map1_positive"), BindingPlan.VALUE, -1));
    }

    @Test
    public void ruleSetsAreFilteredByGroupsAndCached() {
        BeanRules rules = materialize(Grouped.class, mappers());

        RuleSet<?> defaults = rules.ruleSet(RequestedGroups.DEFAULT);
        assertEquals("GroupedRules", defaults.getName());
        assertEquals(List.of("always_notNull", "Grouped_validOrder"), defaults.getRules().stream().map(r -> r.getDefinition().getName()).toList());
        assertSame(defaults, rules.ruleSet(RequestedGroups.of()));
        assertSame(defaults, rules.ruleSet(RequestedGroups.of(Default.class)));

        RuleSet<?> create = rules.ruleSet(RequestedGroups.of(OnCreate.class));
        assertEquals(List.of("onCreate_notNull", "both_size"), defaults(create));
        assertSame(create, rules.ruleSet(RequestedGroups.of(OnCreate.class)));
        assertNotSame(defaults, create);

        RuleSet<?> both = rules.ruleSet(RequestedGroups.of(OnCreate.class, Default.class));
        assertEquals(List.of("always_notNull", "onCreate_notNull", "both_size", "Grouped_validOrder"), defaults(both));

        assertEquals(List.of("both_size"), defaults(rules.ruleSet(RequestedGroups.of(Unused.class))));
        assertTrue(rules.ruleSet(RequestedGroups.of(Nobody.class)).getRules().isEmpty());

        // Running the RuleSet collects into the bound RuleViolations and does not throw.
        Bindings bindings = Bindings.builder().standard();
        RuleViolations violations = new RuleViolations();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind("always", String.class, null);
        bindings.bind("onCreate", String.class, null);
        bindings.bind("both", String.class, "x");
        bindings.bind(BindingPlan.THIS, Grouped.class, new Grouped());
        create.run(bindings);
        assertEquals(List.of("onCreate_notNull", "both_size"), violations.getViolations().stream().map(RuleViolation::getRuleName).toList());
    }

    @Test
    public void elementRuleSets() {
        BeanRules rules = materialize(Order.class, mappers());
        PropertyDefinition counts = rules.getDefinition().getProperty("counts");
        ContainerElementDefinition keyNode = counts.getContainerElements().get(0);
        ContainerElementDefinition valueNode = counts.getContainerElements().get(1);

        BeanRules.ElementRules keyRules = rules.getElementRules(keyNode);
        assertNotNull(keyRules);
        assertSame(counts, keyRules.property());
        assertSame(keyNode, keyRules.node());
        assertEquals(List.of("counts_Map0_notBlank"), names(keyRules.rules()));
        assertThrows(UnsupportedOperationException.class, () -> keyRules.rules().clear());

        RuleSet<?> keySet = rules.elementRuleSet(keyNode, RequestedGroups.DEFAULT);
        assertEquals("OrderRules_Map0", keySet.getName());
        assertEquals(1, keySet.size());
        assertSame(keySet, rules.elementRuleSet(keyNode, RequestedGroups.DEFAULT));
        assertNotSame(keySet, rules.elementRuleSet(valueNode, RequestedGroups.DEFAULT));
        assertEquals("OrderRules_Map1", rules.elementRuleSet(valueNode, RequestedGroups.DEFAULT).getName());

        // An unconstrained position: no rules, an empty RuleSet.
        PropertyDefinition tags = rules.getDefinition().getProperty("tags");
        ContainerElementDefinition tagsNode = tags.getContainerElements().get(0);
        assertNotNull(rules.getElementRules(tagsNode));
        assertTrue(rules.elementRuleSet(tagsNode, RequestedGroups.of(Unused.class)).getRules().isEmpty());
    }

    @Test
    public void compositionsFlattenAndRecordTheirHost() {
        BeanRules rules = materialize(Composed.class, mappers());

        // A plain composition flattens; a @ReportAsSingleViolation one is a single rule hosting its composing rules.
        assertEquals(List.of("code_validCode_notNull", "code_validCode_size", "strict_strictCode"), names(rules.getPropertyRules()));

        MaterializedRule flat = rules.getRule("code_validCode_notNull");
        assertFalse(flat.isHosted());
        assertFalse(flat.isComposition());
        assertNull(flat.getHost());
        assertEquals(NotNull.class, flat.getDefinition().getAnnotationType());
        assertEquals("jakarta.validation.constraints.NotNull.message", flat.getTarget().getErrorCode());

        MaterializedRule composition = rules.getRule("strict_strictCode");
        assertTrue(composition.isComposition());
        assertFalse(composition.isHosted());
        assertEquals(StrictCode.class, composition.getDefinition().getAnnotationType());
        assertEquals(StrictCode.class.getName() + ".message", composition.getTarget().getErrorCode());
        assertEquals("strict", composition.getTarget().getMessage());
        assertEquals("strict", composition.getLeaf());
        assertEquals(List.of("strict_strictCode_notNull", "strict_strictCode_size"), names(composition.getHostedRules()));
        assertInstanceOf(ReportAsSingleViolationRule.class, composition.getRule().getTarget());
        assertEquals(2, ((ReportAsSingleViolationRule) composition.getRule().getTarget()).getComposingRules().size());

        MaterializedRule hosted = rules.getRule("strict_strictCode_size");
        assertTrue(hosted.isHosted());
        assertEquals(StrictCode.class, hosted.getHost().getAnnotationType());
        assertEquals(Size.class, hosted.getDefinition().getAnnotationType());
        assertTrue(hosted.toString().contains("hosted by StrictCode"));
        assertEquals(5, rules.getRules().size(), "hosted rules are registered by name too");

        // Composing constraints inherit the host groups: both activate with the host's group.
        assertTrue(hosted.isActive(RequestedGroups.of(OnCreate.class)));
        assertFalse(hosted.isActive(RequestedGroups.DEFAULT));
        assertTrue(composition.isActive(RequestedGroups.of(OnCreate.class)));
        assertEquals(RuleExecutionStatus.FAIL, status(hosted, "strict", "ab"));

        // The RuleSet runs the composition, never the hosted rules on their own.
        assertEquals(List.of("strict_strictCode"), defaults(rules.ruleSet(RequestedGroups.of(OnCreate.class))));

        // One violation for the host, with the host's code and message, into a path-aware collector.
        Bindings bindings = Bindings.builder().standard();
        org.rulii.validation.beans.BeanRuleViolations collected = new org.rulii.validation.beans.BeanRuleViolations();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, collected);
        bindings.bind("strict", Object.class, "ab");
        composition.getRule().run(bindings);
        assertEquals(1, collected.size());
        assertEquals("strict_strictCode", collected.getViolation(0).getRuleName());
        assertEquals("strict", collected.getViolation(0).getErrorMessage());
        assertEquals("ab", collected.getViolation(0).getParameters().get("strict"));
        assertEquals(RuleExecutionStatus.PASS, status(composition, "strict", "abc"));

        // A plain collector cannot divert the composing violations: they stay, the host's follows.
        RuleViolations plain = run(composition, "strict", "ab");
        assertEquals(List.of("strict_strictCode_size", "strict_strictCode"), plain.getViolations().stream().map(RuleViolation::getRuleName).toList());
        // Null reaches the composing @NotNull.
        assertEquals(RuleExecutionStatus.FAIL, status(composition, "strict", null));
        assertThrows(IllegalArgumentException.class, () -> ReportAsSingleViolationRule.builder(Validators.binding("x"), List.of()).failOnNull(false));
    }

    @Test
    public void unmappedCompositionWithValidatorsMaterializesThroughTheValidators() {
        // The host's own rule runs its declared validator; the composing constraint keeps its own rule.
        BeanRules rules = new RuleMaterializer(mappers()).materialize(BeanDefinition.load(WithValidator.class, EXTRACTORS));

        assertEquals(List.of("code_validatedCode", "code_validatedCode_notNull"), rules.getPropertyRules().stream().map(MaterializedRule::getName).toList());
        assertInstanceOf(ConstraintValidatorRule.class, rules.getRule("code_validatedCode").getRule().getTarget());
    }

    @Test
    public void misbehavingMappersFailFast() {
        BeanDefinition bean = BeanDefinition.load(Custom.class, EXTRACTORS);

        ConstraintMapperException empty = assertThrows(ConstraintMapperException.class, () -> new RuleMaterializer(
                mappers(new SimpleConstraintMapper<>(Checked.class, List.of(), (a, t) -> List.of()))).materialize(bean));
        assertTrue(empty.getMessage().contains("no rules"));
        assertEquals(Checked.class, empty.getAnnotationType());

        ConstraintMapperException nullRule = assertThrows(ConstraintMapperException.class, () -> new RuleMaterializer(
                mappers(new SimpleConstraintMapper<>(Checked.class, List.of(), (a, t) -> {
                    List<Rule> result = new java.util.ArrayList<>();
                    result.add(null);
                    return result;
                }))).materialize(bean));
        assertTrue(nullRule.getMessage().contains("null rule"));

        ConstraintMapperException thrown = assertThrows(ConstraintMapperException.class, () -> new RuleMaterializer(
                mappers(new SimpleConstraintMapper<>(Checked.class, List.of(), (a, t) -> { throw new IllegalArgumentException("bad attribute"); }))).materialize(bean));
        assertTrue(thrown.getMessage().contains("bad attribute"));
        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());

        // Declaration errors from a mapper pass through untouched.
        assertThrows(ConstraintDeclarationException.class, () -> new RuleMaterializer(
                mappers(new SimpleConstraintMapper<>(Checked.class, List.of(), (a, t) -> { throw new ConstraintDeclarationException("declared"); }))).materialize(bean));

        // A hand-built rule whose name collides with another rule of the class.
        ConstraintMapperException duplicate = assertThrows(ConstraintMapperException.class, () -> new RuleMaterializer(
                mappers(new SimpleConstraintMapper<>(Checked.class, List.of(),
                        (a, t) -> List.of(Validators.notNull(t.value()).name("code_notNull").build())))).materialize(bean));
        assertTrue(duplicate.getMessage().contains("code_notNull"));
        assertTrue(duplicate.getMessage().contains("already used"));

        // A well-behaved hand-built rule is accepted as is.
        BeanRules rules = new RuleMaterializer(mappers(new SimpleConstraintMapper<>(Checked.class, List.of(),
                (a, t) -> List.of(Validators.notBlank(t.value()).name(t.nextRuleName()).build())))).materialize(bean);
        assertEquals(List.of("code_notNull", "code_checked"), names(rules.getPropertyRules()));
        assertEquals(RuleExecutionStatus.FAIL, status(rules.getRule("code_checked"), "code", " "));
    }

    @Test
    public void nullsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RuleMaterializer(null));
        assertThrows(IllegalArgumentException.class, () -> new RuleMaterializer(mappers()).materialize(null));
        BeanRules rules = materialize(Order.class, mappers());
        assertThrows(IllegalArgumentException.class, () -> rules.getRule(null));
        assertThrows(IllegalArgumentException.class, () -> rules.ruleSet(null));
        assertThrows(IllegalArgumentException.class, () -> rules.getElementRules(null));
        assertThrows(IllegalArgumentException.class, () -> rules.elementRuleSet(null, RequestedGroups.DEFAULT));
        assertSame(mappers().getClass(), new RuleMaterializer(mappers()).getConstraintMapperRegistry().getClass());
    }

    // ---- helpers ----

    private static BeanRules materialize(Class<?> type, ConstraintMapperRegistry mappers) {
        return new RuleMaterializer(mappers).materialize(BeanDefinition.load(type, EXTRACTORS));
    }

    private static ConstraintMapperRegistry mappers(SimpleConstraintMapper<?>... extra) {
        var builder = ConstraintMapperRegistry.builder()
                .mapper(new SimpleConstraintMapper<>(ValidOrder.class, List.of(Object.class),
                        (a, t) -> List.of(t.configure(Validators.notNull(t.value()), true).build())));
        for (SimpleConstraintMapper<?> mapper : extra) {
            builder.mapper(mapper);
        }
        return builder.build();
    }

    private static List<String> names(List<MaterializedRule> rules) {
        return rules.stream().map(MaterializedRule::getName).toList();
    }

    private static List<String> defaults(RuleSet<?> ruleSet) {
        return ruleSet.getRules().stream().map(r -> r.getDefinition().getName()).toList();
    }

    private static RuleViolations run(MaterializedRule rule, String binding, Object value) {
        Bindings bindings = Bindings.builder().standard();
        RuleViolations violations = new RuleViolations();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, violations);
        bindings.bind(binding, Object.class, value);
        rule.getRule().run(bindings);
        return violations;
    }

    private static RuleExecutionStatus status(MaterializedRule rule, String binding, Object value) {
        Bindings bindings = Bindings.builder().standard();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, new RuleViolations());
        bindings.bind(binding, Object.class, value);
        RuleResult result = rule.getRule().run(bindings);
        return result.status();
    }

    // ---- fixtures ----

    public interface OnCreate {}

    public interface Unused {}

    public interface Nobody {}

    @Constraint(validatedBy = {})
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidOrder {
        String message() default "{validOrder}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Checked {
        String message() default "{checked}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @NotNull
    @Size(min = 3)
    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidCode {
        String message() default "{validCode}";
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

    @NotNull
    @Constraint(validatedBy = SomeValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidatedCode {
        String message() default "{validatedCode}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class SomeValidator implements jakarta.validation.ConstraintValidator<ValidatedCode, String> {
        @Override
        public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) { return true; }
    }

    @ValidOrder
    public static class Order {
        @NotNull
        @Size(min = 2, max = 5, message = "len {min}-{max}")
        private String code;

        @NotNull
        private String name;

        private int quantity;
        private List<@NotBlank String> tags;
        private Map<@NotBlank String, @Positive Integer> counts;

        @Email(regexp = "[a-z]+@acme\\.com")
        private String email;

        @Size(max = 3)
        public String getName() { return name; }

        @Min(value = 1, payload = Warning.class)
        public int getQuantity() { return quantity; }
    }

    @ValidOrder
    public static class Grouped {
        @NotNull
        private String always;
        @NotNull(groups = OnCreate.class)
        private String onCreate;
        @Size(min = 2, groups = {OnCreate.class, Unused.class})
        private String both;
    }

    public static class Composed {
        @ValidCode
        private String code;
        @StrictCode(groups = OnCreate.class)
        private String strict;
    }

    public static class WithValidator {
        @ValidatedCode
        private String code;
    }

    public static class Custom {
        @NotNull
        @Checked
        private String code;
    }
}
