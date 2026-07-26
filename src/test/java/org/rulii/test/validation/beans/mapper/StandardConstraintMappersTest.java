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
package org.rulii.test.validation.beans.mapper;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleResult;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.BeanValidationException;
import org.rulii.validation.beans.ConstraintMapperRegistry;
import org.rulii.validation.beans.ConstraintTarget;
import org.rulii.text.MessageFormatter;
import org.rulii.text.MessageResolver;
import org.rulii.validation.beans.annotation.Warning;
import org.rulii.validation.beans.message.JakartaMessageTranslator;
import org.rulii.validation.beans.message.StringifyingMessageFormatter;
import org.rulii.validation.beans.message.TranslatingMessageResolver;
import org.rulii.validation.beans.metadata.BeanMetadataReader;
import org.rulii.validation.beans.metadata.ConstraintMetadata;
import org.rulii.validation.beans.metadata.PropertyMetadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 22 standard constraint mappers: constraints read off a real annotated
 * fixture through the metadata layer, materialized through the registry's mappers, and
 * executed as real rules — pass, fail, and jakarta null-contract cases, attribute
 * threading, severity mapping, and translated custom messages.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class StandardConstraintMappersTest {

    public StandardConstraintMappersTest() {
        super();
    }

    private static final ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().standard().build();

    private final BeanMetadataReader reader = BeanMetadataReader.builder().mapperRegistry(registry).build();

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    // Property names deliberately differ from constraint attribute names — the validated
    // value is reported under the property name, the bounds under the attribute names
    // (a property literally named "min" would collide with @Min's own param).
    public static class Fixture {
        @NotNull private String notNull;
        @NotBlank private String notBlank;
        @NotEmpty private List<String> notEmpty;
        @Null private String isNull;
        @AssertTrue private Boolean active;
        @AssertFalse private Boolean deleted;
        @Min(18) private Integer age;
        @Max(100) private Integer score;
        @DecimalMin(value = "0.50", inclusive = false) private BigDecimal price;
        @DecimalMax("99.99") private BigDecimal total;
        @Digits(integer = 5, fraction = 2) private BigDecimal amount;
        @Size(min = 2, max = 4) private List<String> tags;
        @Pattern(regexp = "[a-z]+", flags = Pattern.Flag.CASE_INSENSITIVE) private String code;
        @Email private String email;
        @Email(regexp = ".+@corp\\.com") private String corpEmail;
        @Past private LocalDate started;
        @PastOrPresent private LocalDate updated;
        @Future private LocalDate expires;
        @FutureOrPresent private LocalDate due;
        @Positive private Integer count;
        @PositiveOrZero private Integer stock;
        @Negative private Integer depth;
        @NegativeOrZero private Integer debt;
        @Min(value = 5, payload = Warning.class) private Integer warnedAge;

        public Fixture() {
            super();
        }
    }

    public static class BadDecimalFixture {
        @DecimalMin("not-a-number") private BigDecimal amount;

        public BadDecimalFixture() {
            super();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private List<Rule> map(Class<?> beanClass, String property) {
        return map(beanClass, property, null);
    }

    private List<Rule> map(Class<?> beanClass, String property, String message) {
        PropertyMetadata metadata = reader.read(beanClass).getProperty(property);
        ConstraintMetadata constraint = metadata.getConstraints().get(0);
        ConstraintTarget target = new ConstraintTarget(beanClass, property,
                property + "_" + constraint.getAnnotationType().getSimpleName(), property, property,
                metadata.getDeclaredType(), ConstraintMapperRegistry.errorCode(constraint.getAnnotationType()),
                constraint.getSeverity(), message);
        return registry.getMapper(constraint.getAnnotationType()).map(constraint, target);
    }

    private RuleViolations run(List<Rule> rules, String bindingName, Object value) {
        Bindings bindings = Bindings.builder().standard();
        bindings.bind(bindingName, Object.class, value);
        RuleViolations violations = new RuleViolations();
        bindings.bind("ruleViolations", violations);
        RuleContext context = RuleContext.builder().build(bindings);

        for (Rule rule : rules) {
            rule.run(context);
        }

        return violations;
    }

    private void assertFails(String property, Object failing) {
        RuleViolations violations = run(map(Fixture.class, property), property, failing);
        assertEquals(1, violations.getViolations().size(), "expected a violation for [" + property + "]");
    }

    private void assertPasses(String property, Object passing) {
        RuleViolations violations = run(map(Fixture.class, property), property, passing);
        assertTrue(violations.getViolations().isEmpty(),
                "unexpected violations for [" + property + "]: " + violations.getViolations());
    }

    // -----------------------------------------------------------------------
    // Presence constraints — null fails
    // -----------------------------------------------------------------------

    @Test
    public void notNullFailsOnNull() {
        assertFails("notNull", null);
        assertPasses("notNull", "x");
    }

    @Test
    public void notBlankFailsOnBlank() {
        assertFails("notBlank", "   ");
        assertFails("notBlank", null);
        assertPasses("notBlank", "x");
    }

    @Test
    public void notEmptyFailsOnEmpty() {
        assertFails("notEmpty", List.of());
        assertFails("notEmpty", null);
        assertPasses("notEmpty", List.of("x"));
    }

    @Test
    public void nullConstraintRequiresNull() {
        assertPasses("isNull", null);
        assertFails("isNull", "x");
    }

    // -----------------------------------------------------------------------
    // Jakarta null contract — every other constraint skips null
    // -----------------------------------------------------------------------

    @Test
    public void nonPresenceConstraintsSkipNull() {
        for (String property : List.of("active", "age", "price", "amount", "tags",
                "code", "email", "started", "count")) {
            List<Rule> rules = map(Fixture.class, property);

            Bindings bindings = Bindings.builder().standard();
            bindings.bind(property, Object.class, null);
            RuleViolations violations = new RuleViolations();
            bindings.bind("ruleViolations", violations);
            RuleContext context = RuleContext.builder().build(bindings);

            for (Rule rule : rules) {
                RuleResult result = rule.run(context);
                assertTrue(result.status().isSkipped(), "[" + property + "] must skip on null");
            }

            assertTrue(violations.getViolations().isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Boolean constraints
    // -----------------------------------------------------------------------

    @Test
    public void assertTrueConstraint() {
        assertFails("active", false);
        assertPasses("active", true);
    }

    @Test
    public void assertFalseConstraint() {
        assertFails("deleted", true);
        assertPasses("deleted", false);
    }

    // -----------------------------------------------------------------------
    // Numeric bounds
    // -----------------------------------------------------------------------

    @Test
    public void minConstraint() {
        assertFails("age", 16);
        assertPasses("age", 18);
    }

    @Test
    public void maxConstraint() {
        assertFails("score", 101);
        assertPasses("score", 100);
    }

    @Test
    public void minViolationCarriesJakartaNamedParam() {
        RuleViolations violations = run(map(Fixture.class, "age"), "age", 16);

        assertEquals("jakarta.validation.constraints.Min.message", violations.getViolations().get(0).getErrorCode());
        assertTrue(violations.getViolations().get(0).getParameters().containsKey("min"));
    }

    @Test
    public void decimalMinExclusiveBoundary() {
        // inclusive = false: the boundary itself fails.
        assertFails("price", new BigDecimal("0.50"));
        assertPasses("price", new BigDecimal("0.51"));
    }

    @Test
    public void decimalMaxConstraint() {
        assertFails("total", new BigDecimal("100.00"));
        assertPasses("total", new BigDecimal("99.99"));
    }

    @Test
    public void invalidDecimalLiteralFailsAtMaterialization() {
        assertThrows(BeanValidationException.class, () -> map(BadDecimalFixture.class, "amount"));
    }

    @Test
    public void digitsConstraint() {
        assertFails("amount", new BigDecimal("123456.78"));
        assertFails("amount", new BigDecimal("12345.678"));
        assertPasses("amount", new BigDecimal("12345.67"));
    }

    // -----------------------------------------------------------------------
    // Size / Pattern / Email
    // -----------------------------------------------------------------------

    @Test
    public void sizeConstraint() {
        assertFails("tags", List.of("a"));
        assertPasses("tags", List.of("a", "b", "c"));
    }

    @Test
    public void patternFlagsCarriedThrough() {
        // regexp is [a-z]+ — upper case only passes because CASE_INSENSITIVE rode through.
        assertPasses("code", "ABC");
        assertFails("code", "123");
    }

    @Test
    public void emailConstraint() {
        assertPasses("email", "ada@example.com");
        assertFails("email", "not-an-email");
    }

    @Test
    public void emailWithRegexpMaterializesTwoRules() {
        List<Rule> rules = map(Fixture.class, "corpEmail");

        assertEquals(2, rules.size());
        assertEquals("corpEmail_Email", rules.get(0).getName());
        assertEquals("corpEmail_Email_pattern", rules.get(1).getName());

        // Valid email matching the regexp: no violations.
        assertTrue(run(rules, "corpEmail", "ada@corp.com").getViolations().isEmpty());
        // Valid email failing the regexp: exactly the pattern rule fails.
        assertEquals(1, run(rules, "corpEmail", "ada@other.com").getViolations().size());
        // Invalid email failing both: two violations (F4 — one annotation, two rules).
        assertEquals(2, run(rules, "corpEmail", "nonsense").getViolations().size());
    }

    // -----------------------------------------------------------------------
    // Temporal
    // -----------------------------------------------------------------------

    @Test
    public void pastAndFutureConstraints() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertPasses("started", yesterday);
        assertFails("started", tomorrow);
        assertPasses("updated", yesterday);
        assertFails("updated", tomorrow);
        assertPasses("expires", tomorrow);
        assertFails("expires", yesterday);
        assertPasses("due", tomorrow);
        assertFails("due", yesterday);
    }

    // -----------------------------------------------------------------------
    // Sign
    // -----------------------------------------------------------------------

    @Test
    public void signConstraints() {
        assertPasses("count", 1);
        assertFails("count", 0);
        assertPasses("stock", 0);
        assertFails("stock", -1);
        assertPasses("depth", -1);
        assertFails("depth", 0);
        assertPasses("debt", 0);
        assertFails("debt", 1);
    }

    // -----------------------------------------------------------------------
    // Reporting config threading
    // -----------------------------------------------------------------------

    @Test
    public void payloadSeverityThreadedToViolation() {
        RuleViolations violations = run(map(Fixture.class, "warnedAge"), "warnedAge", 3);

        assertEquals(Severity.WARNING, violations.getViolations().get(0).getSeverity());
    }

    @Test
    public void translatedCustomMessageRendersWithMappedAttribute() {
        // The materializer contract: a custom literal message arrives translated with the
        // mapper's attribute-name mapping — jakarta {value} becomes ${min}, the param the
        // core rule actually emits.
        String translated = JakartaMessageTranslator.translate("must be at least {value}",
                registry.getMapper(Min.class).getAttributeNameMapping());
        RuleViolations violations = run(map(Fixture.class, "age", translated), "age", 16);

        assertEquals("must be at least 18", violations.getViolations().get(0).getErrorMessage());
    }

    @Test
    public void bundleMessageRendersThroughFullStack() {
        // The full S8 render stack on a real rule execution: the ValidationMessages
        // bundle entry for @Min uses jakarta's {value}; the registry's mappings-by-code
        // turn it into ${min}; the stringifying formatter renders numbers verbatim.
        Bindings bindings = Bindings.builder().standard();
        bindings.bind("age", Object.class, 16);
        RuleViolations violations = new RuleViolations();
        bindings.bind("ruleViolations", violations);
        RuleContext context = RuleContext.builder().standard()
                .bindings(bindings)
                .messageResolver(new TranslatingMessageResolver(
                        MessageResolver.builder("ValidationMessages").build(),
                        registry.getAttributeNameMappingsByCode()))
                .messageFormatter(new StringifyingMessageFormatter(MessageFormatter.builder().build()))
                .build();

        for (Rule rule : map(Fixture.class, "age")) {
            rule.run(context);
        }

        assertEquals("must be greater than or equal to 18", violations.getViolations().get(0).getErrorMessage());
    }

    @Test
    public void ruleNameAndValueNameThreaded() {
        RuleViolations violations = run(map(Fixture.class, "age"), "age", 16);

        assertEquals("age_Min", violations.getViolations().get(0).getRuleName());
        // The validated value under the property name; the bound under the attribute name.
        assertTrue(violations.getViolations().get(0).getParameters().containsKey("age"));
        assertTrue(violations.getViolations().get(0).getParameters().containsKey("min"));
    }
}
