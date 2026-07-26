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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.rule.Rule;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.ConstraintMapper;
import org.rulii.validation.beans.ConstraintMapperRegistry;
import org.rulii.validation.beans.ConstraintTarget;
import org.rulii.validation.beans.annotation.Warning;
import org.rulii.validation.beans.metadata.ConstraintMetadata;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case coverage for the engine: records, dual-source and shadowed fields,
 * reserved-name properties, nested and Optional containers, group-filtered element
 * rules, lazy memoized property reads, custom mappers, severity threading, and the
 * user-context variant's collector semantics.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanValidatorEdgeTest {

    public BeanValidatorEdgeTest() {
        super();
    }

    private final BeanValidator validator = BeanValidator.builder().build();

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public record Wire(@NotBlank String label) {
    }

    public record Circuit(@NotBlank String name, @Valid Wire wire) {
    }

    public static class DualSource {
        @Size(min = 3) private String name;

        public DualSource(String name) {
            super();
            this.name = name;
        }

        @NotBlank
        public String getName() {
            // Getter reports a derived value; the field constraint must see the field.
            return name != null ? name.trim() : null;
        }
    }

    public static class ShadowParent {
        @NotBlank private String code;

        public ShadowParent(String code) {
            super();
            this.code = code;
        }
    }

    public static class ShadowChild extends ShadowParent {
        @Size(min = 2) private String code;

        public ShadowChild(String parentCode, String childCode) {
            super(parentCode);
            this.code = childCode;
        }
    }

    public static class ReservedNamed {
        @NotBlank private String rule;

        public ReservedNamed(String rule) {
            super();
            this.rule = rule;
        }
    }

    public static class Nested {
        private Map<String, List<@NotBlank String>> entries;

        public Nested(Map<String, List<String>> entries) {
            super();
            this.entries = entries;
        }
    }

    public static class Boxed {
        private Optional<@NotBlank String> nickname;

        public Boxed(Optional<String> nickname) {
            super();
            this.nickname = nickname;
        }
    }

    public interface OnCreate { }

    public static class GroupedElements {
        private List<@NotBlank(groups = OnCreate.class) String> codes;

        public GroupedElements(List<String> codes) {
            super();
            this.codes = codes;
        }
    }

    public static class Lazy {
        static final AtomicInteger CONSTRAINED_READS = new AtomicInteger();
        static final AtomicInteger UNCONSTRAINED_READS = new AtomicInteger();

        public Lazy() {
            super();
        }

        @NotBlank
        @Size(min = 2)
        public String getTracked() {
            CONSTRAINED_READS.incrementAndGet();
            return "ok";
        }

        public String getUntouched() {
            UNCONSTRAINED_READS.incrementAndGet();
            return "never";
        }
    }

    @Constraint(validatedBy = { })
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Even {
        String message() default "{org.rulii.test.even.message}";
        Class<?>[] groups() default { };
        Class<? extends Payload>[] payload() default { };
    }

    public static class EvenMapper implements ConstraintMapper {

        public EvenMapper() {
            super();
        }

        @Override
        public List<Class<?>> getSupportedTypes() {
            return List.of(boolean.class, Boolean.class);
        }

        @Override
        public List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target) {
            return List.of(Validators.assertTrue(Validators.binding(target.getBindingName()))
                    .name(target.getRuleName())
                    .errorCode(target.getErrorCode())
                    .severity(target.getSeverity())
                    .valueName(target.getValueName())
                    .failOnNull(false)
                    .build());
        }
    }

    public static class Counted {
        @Even private Boolean evenFlag;

        public Counted(Boolean evenFlag) {
            super();
            this.evenFlag = evenFlag;
        }
    }

    public static class Warned {
        @NotNull(payload = Warning.class) private String hint;

        public Warned() {
            super();
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
    // Records
    // -----------------------------------------------------------------------

    @Test
    public void recordsValidateAndCascade() {
        BeanRuleViolations violations = validator.validate(new Circuit("  ", new Wire("  ")));

        assertTrue(paths(violations).containsAll(List.of("name", "wire.label")));
        assertTrue(validator.validate(new Circuit("main", new Wire("live"))).getViolations().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Dual-source & shadowed fields
    // -----------------------------------------------------------------------

    @Test
    public void dualSourceConstraintsValidateTheirOwnSource() {
        // Field "ab " (length 3) passes @Size on the field; the trimmed getter value
        // "ab" would have failed it — proving the field constraint reads the field.
        BeanRuleViolations violations = validator.validate(new DualSource("ab "));
        assertTrue(violations.getViolations().isEmpty(), "violations: " + violations.getViolations());

        // Blank field: getter yields "" failing @NotBlank; field passes @Size(3).
        violations = validator.validate(new DualSource("   "));
        assertEquals(1, violations.getViolations().size());
        assertEquals("jakarta.validation.constraints.NotBlank.message",
                violations.getViolations().get(0).getErrorCode());
    }

    @Test
    public void shadowedSuperclassFieldValidatesSeparately() {
        // Parent field blank (fails @NotBlank), child field long enough (passes @Size).
        BeanRuleViolations violations = validator.validate(new ShadowChild("  ", "ok"));

        assertEquals(1, violations.getViolations().size());
        assertEquals("jakarta.validation.constraints.NotBlank.message",
                violations.getViolations().get(0).getErrorCode());
        assertEquals(List.of("code"), paths(violations));

        assertTrue(validator.validate(new ShadowChild("present", "ok")).getViolations().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Reserved binding names
    // -----------------------------------------------------------------------

    @Test
    public void reservedNamedPropertyValidatesUnderItsRealName() {
        BeanRuleViolations violations = validator.validate(new ReservedNamed("  "));

        assertEquals(List.of("rule"), paths(violations));
        assertTrue(validator.validate(new ReservedNamed("ok")).getViolations().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Containers
    // -----------------------------------------------------------------------

    @Test
    public void nestedContainerViolationsCarryFullPaths() {
        BeanRuleViolations violations = validator.validate(new Nested(Map.of("a", List.of("ok", "  "))));

        assertEquals(List.of("entries[a][1]"), paths(violations));
    }

    @Test
    public void optionalContentValidatesInPlace() {
        assertEquals(List.of("nickname"), paths(validator.validate(new Boxed(Optional.of("  ")))));
        assertTrue(validator.validate(new Boxed(Optional.of("ok"))).getViolations().isEmpty());
        // Empty Optional: the unwrapped value is null — presence constraints fail (spec).
        assertEquals(1, validator.validate(new Boxed(Optional.empty())).getViolations().size());
    }

    @Test
    public void elementRulesAreGroupFiltered() {
        GroupedElements bean = new GroupedElements(List.of("  "));

        assertTrue(validator.validate(bean).getViolations().isEmpty());
        assertEquals(List.of("codes[0]"), paths(validator.validate(bean, OnCreate.class)));
    }

    // -----------------------------------------------------------------------
    // Lazy memoized reads
    // -----------------------------------------------------------------------

    @Test
    public void propertiesReadOnceAndUnreferencedPropertiesNeverRead() {
        Lazy.CONSTRAINED_READS.set(0);
        Lazy.UNCONSTRAINED_READS.set(0);

        validator.validate(new Lazy());

        // Two constraints on the property, one read (memoized).
        assertEquals(1, Lazy.CONSTRAINED_READS.get());
        // Unreferenced property bound lazily, never read.
        assertEquals(0, Lazy.UNCONSTRAINED_READS.get());
    }

    // -----------------------------------------------------------------------
    // Custom mapper & severity
    // -----------------------------------------------------------------------

    @Test
    public void customMapperRidesTheStandardPipeline() {
        BeanValidator custom = BeanValidator.builder()
                .mapperRegistry(ConstraintMapperRegistry.builder().standard()
                        .register(Even.class, new EvenMapper())
                        .build())
                .build();

        BeanRuleViolations violations = custom.validate(new Counted(false));
        assertEquals(1, violations.getViolations().size());
        assertEquals("org.rulii.test.even.message", violations.getViolations().get(0).getErrorCode());
        assertEquals(List.of("evenFlag"), paths(violations));

        assertTrue(custom.validate(new Counted(true)).getViolations().isEmpty());
        assertTrue(custom.validate(new Counted(null)).getViolations().isEmpty());
    }

    @Test
    public void payloadSeverityReachesTheViolation() {
        BeanRuleViolations violations = validator.validate(new Warned());

        assertEquals(Severity.WARNING, violations.getViolations().get(0).getSeverity());
    }

    // -----------------------------------------------------------------------
    // User-context variant (R10.4a)
    // -----------------------------------------------------------------------

    @Test
    public void userBoundCollectorReceivesUndecoratedViolations() {
        Bindings bindings = Bindings.builder().standard();
        RuleViolations userCollector = new RuleViolations();
        bindings.bind("ruleViolations", userCollector);
        RuleContext context = RuleContext.builder().build(bindings);

        BeanRuleViolations returned = validator.validate(context, new ReservedNamed("  "));

        assertTrue(returned.getViolations().isEmpty());
        assertEquals(1, userCollector.getViolations().size());
        assertFalse(userCollector.getViolations().get(0) instanceof BeanRuleViolation);
    }

    @Test
    public void userContextWithoutCollectorGetsPathAwareViolations() {
        RuleContext context = RuleContext.builder().standard().build();

        BeanRuleViolations returned = validator.validate(context, new ReservedNamed("  "));

        assertEquals(List.of("rule"), paths(returned));
        // The pushed scope was popped — the caller's context is clean.
        assertFalse(context.getBindings().contains("$this"));
    }
}
