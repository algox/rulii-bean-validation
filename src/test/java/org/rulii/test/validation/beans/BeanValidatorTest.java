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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.GroupSequence;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ValidateUnwrappedValue;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.model.UnrulyException;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidationException;
import org.rulii.validation.beans.BeanValidationResult;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.ExecutableBeanValidator;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.ValidateRuleNotFoundException;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.group.InvalidGroupException;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.SimpleConstraintMapper;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.rulii.model.action.Actions.action;

/**
 * The entry point as a {@code jakarta.validation.Validator} and as a rulii validator.
 */
public class BeanValidatorTest {

    private static final RuleRegistry REGISTRY = RuleRegistry.builder()
            .register(Validators.notNull(Validators.binding("customer")).name("customerRequired").build())
            .build();

    public BeanValidatorTest() {
        super();
    }

    private static BeanValidator validator() {
        return BeanValidator.builder().registry(REGISTRY).build();
    }

    private static List<String> report(Iterable<? extends ConstraintViolation<?>> violations) {
        List<String> result = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath() + ":" + ((RuleViolation) violation).getRuleName());
        }
        return result;
    }

    @Test
    public void failuresInsideARunSurfaceAsValidationExceptionsOnTheSpecApi() throws Exception {
        BeanValidator validator = validator();

        // A getter that throws: no ValidationException in rulii's chain, so the chain is wrapped and kept.
        BeanValidationException getter = assertThrows(BeanValidationException.class, () -> validator.validate(new Boom()));
        assertInstanceOf(UnrulyException.class, getter.getCause());
        Throwable cause = getter;
        while (cause != null && !(cause instanceof IllegalStateException)) {
            cause = cause.getCause();
        }
        assertEquals("boom", cause.getMessage());

        // The same through the executable validator: the guard is shared.
        assertThrows(BeanValidationException.class, () -> validator.forExecutables().validateParameters(new BoomService(),
                BoomService.class.getMethod("take", Boom.class), new Object[] {new Boom()}));

        // A value extractor that throws.
        BeanValidator extracting = BeanValidator.builder().registry(REGISTRY).extractor(new BombExtractor()).build();
        BeanValidationException extractor = assertThrows(BeanValidationException.class, () -> extracting.validate(new Bombed()));
        assertTrue(extractor.getMessage().contains("value extractor"), extractor.getMessage());

        // The rulii-native entry point keeps rulii's contract: the wrapper as thrown by the rule engine.
        ScopedBindings bindings = Bindings.builder().scoped();
        RuleContext context = RuleContext.builder().with(bindings).registry(REGISTRY).build();
        assertThrows(UnrulyException.class, () -> validator.validateIn(context, new Boom()));
    }

    @Test
    public void failFastStopsAtTheFirstViolation() {
        Order order = new Order();
        order.code = "x";
        order.customer = new Customer();

        assertEquals(List.of("code:code_size", "customer.name:name_notBlank"), report(validator().validate(order)), "off by default");

        BeanValidator failFast = BeanValidator.builder().registry(REGISTRY).failFast(true).build();
        assertTrue(failFast.isFailFast());
        assertEquals(List.of("code:code_size"), report(failFast.validate(order)), "the first violation only");

        order.code = "ok";
        assertEquals(List.of("customer.name:name_notBlank"), report(failFast.validate(order)), "cascades still run when nothing failed before");
    }

    @Test
    public void constraintsOnOverriddenGettersReadTheOverride() {
        BeanValidator validator = validator();

        // An abstract interface getter implemented by the class, backed by a field or computed.
        assertEquals(List.of("name:name_notNull"), report(validator.validate(new Badge(null))));
        assertTrue(validator.validate(new Badge("ok")).isEmpty());
        assertEquals(List.of("name:name_notNull"), report(validator.validate(new Computed())));

        // A superclass getter overridden by the subclass.
        assertEquals(List.of("code:code_notNull"), report(validator.validate(new Tag(null))));
        assertTrue(validator.validate(new Tag("ok")).isEmpty());

        // Constraints on the interface getter and on the override accumulate: two rules, both reading the override.
        assertEquals(List.of("name:name_notNull"), report(validator.validate(new Both(null))));
        assertEquals(List.of("name:name_size"), report(validator.validate(new Both("x"))));
        assertTrue(validator.validate(new Both("ok")).isEmpty());
        assertEquals(2, validator.getConstraintsForClass(Both.class).getConstraintsForProperty("name").getConstraintDescriptors().size());
    }

    @Test
    public void validatorCallSitesCompileUnchanged() {
        Validator validator = validator();
        Order order = new Order();
        order.code = "x";
        order.customer = new Customer();

        Set<ConstraintViolation<Order>> violations = validator.validate(order);

        assertEquals(List.of("code:code_size", "customer.name:name_notBlank"), report(violations));
        for (ConstraintViolation<Order> violation : violations) {
            Order root = violation.getRootBean();
            assertSame(order, root);
            assertEquals(Order.class, violation.getRootBeanClass());
            assertNotNull(violation.getMessage());
            assertNull(violation.getExecutableParameters());
            assertNull(violation.getExecutableReturnValue());
            assertSame(violation, violation.unwrap(BeanRuleViolation.class));
            assertSame(violation, violation.unwrap(ConstraintViolation.class));
            assertThrows(ValidationException.class, () -> violation.unwrap(String.class));
        }
        ConstraintViolation<Order> code = violations.iterator().next();
        assertEquals("x", code.getInvalidValue());
        assertSame(order, code.getLeafBean());
        assertEquals("{jakarta.validation.constraints.Size.message}", code.getMessageTemplate());
        assertEquals(ElementKind.PROPERTY, code.getPropertyPath().iterator().next().getKind());

        ConstraintDescriptor<?> descriptor = code.getConstraintDescriptor();
        assertInstanceOf(Size.class, descriptor.getAnnotation());
        assertEquals(2, ((Size) descriptor.getAnnotation()).min());
        assertEquals("{jakarta.validation.constraints.Size.message}", descriptor.getMessageTemplate());
        assertEquals(Set.of(Default.class), descriptor.getGroups(), "no groups declared reads as Default");
        assertTrue(descriptor.getPayload().isEmpty());
        assertNull(descriptor.getValidationAppliesTo());
        assertEquals(2, descriptor.getAttributes().get("min"));
        assertEquals(List.of("groups", "max", "message", "min", "payload"), new ArrayList<>(descriptor.getAttributes().keySet()));
        assertTrue(descriptor.getComposingConstraints().isEmpty());
        assertFalse(descriptor.isReportAsSingleViolation());
        assertEquals(ValidateUnwrappedValue.DEFAULT, descriptor.getValueUnwrapping());
        assertTrue(descriptor.getConstraintValidatorClasses().isEmpty(), "built-in constraints declare validatedBy = {}; the provider supplies them");
        assertInstanceOf(ConstraintDefinition.class, descriptor.unwrap(ConstraintDefinition.class));
        assertThrows(ValidationException.class, () -> descriptor.unwrap(String.class));
    }

    @Test
    public void resultCarriesTheRuliiReport() {
        BeanValidator validator = validator();
        Order order = new Order();
        order.code = "x";
        order.customer = new Customer();
        order.customer.name = "ok";

        BeanValidationResult<Order> result = validator.validate(order);

        assertSame(order, result.getRootBean());
        BeanRuleViolations report = result.getRuleViolations();
        assertEquals(1, report.size());
        assertEquals(1, result.size());
        assertEquals(result.getViolations(), report.getBeanViolations());
        assertTrue(result.contains(report.getBeanViolations().get(0)));
        assertEquals("code", result.getViolations().get(0).getPropertyPath().toString());
        assertThrows(UnsupportedOperationException.class, () -> result.add(null), "the spec's set is immutable");
        assertTrue(result.toString().contains("Order"));

        order.code = "ok";
        assertTrue(validator.validate(order).isEmpty(), "nothing wrong: the spec's empty set");
    }

    @Test
    public void validateRulesAndGroupsAtTheEntryPoint() {
        BeanValidator validator = validator();
        Order order = new Order();
        order.code = "ok";

        assertEquals(List.of(":customerRequired"), report(validator.validate(order)), "class-level @Validate through the configured registry");
        // @Validate has a constraint's groups (R9.6): none means Default, so it stays out of an OnCreate request.
        assertEquals(List.of("reference:reference_notNull"), report(validator.validate(order, OnCreate.class)));
        assertEquals(List.of("reference:reference_notNull"), report(validator.validate(order, Extended.class)), "groups expand along superinterfaces");
        assertEquals(List.of("reference:reference_notNull", ":customerRequired"), report(validator.validate(order, Default.class, OnCreate.class)));
        order.customer = new Customer();
        order.customer.name = "ok";
        assertEquals(List.of("reference:reference_notNull"), report(validator.validate(order, OnCreate.class)), "Default rules stay out");

        InvalidGroupException invalid = assertThrows(InvalidGroupException.class, () -> validator.validate(order, String.class));
        assertInstanceOf(BeanValidationException.class, invalid);
        assertInstanceOf(ValidationException.class, invalid);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));

        BeanValidator without = BeanValidator.builder().build();
        assertThrows(ValidateRuleNotFoundException.class, () -> without.validate(order));
    }

    @Test
    public void validateInAnExistingContext() {
        BeanValidator validator = validator();
        Order order = new Order();
        order.code = "x";

        // A plain collector bound by the caller: it receives the stamped violations and is returned.
        RuleViolations plain = new RuleViolations();
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, plain);
        RuleContext context = RuleContext.builder().with(bindings).build();
        assertSame(plain, validator.validateIn(context, order));
        assertEquals(List.of("code:code_size", ":customerRequired"), report(plain.getViolations().stream().map(v -> (BeanRuleViolation) v).toList()));
        assertNull(bindings.getBinding(BindingPlan.THIS), "the run's scope is gone");

        // No collector: a path-aware one is bound for the run and returned; the ungrouped @Validate stays out of OnCreate.
        RuleContext bare = RuleContext.builder().with(Bindings.builder().scoped()).build();
        RuleViolations own = validator.validateIn(bare, order, OnCreate.class);
        assertInstanceOf(BeanRuleViolations.class, own);
        assertEquals(List.of("reference:reference_notNull"), report(((BeanRuleViolations) own).getBeanViolations()));

        // A validator built without a registry resolves @Validate through the context's registry.
        RuleRegistry other = RuleRegistry.builder()
                .register(Validators.notNull(Validators.binding("code")).name("customerRequired").build())
                .build();
        RuleContext withRegistry = RuleContext.builder().with(Bindings.builder().scoped()).registry(other).build();
        BeanValidator without = BeanValidator.builder().build();
        assertEquals(List.of("code:code_size"), report(((BeanRuleViolations) without.validateIn(withRegistry, order)).getBeanViolations()),
                "the context's customerRequired checks code, which is set");
        assertThrows(ValidateRuleNotFoundException.class, () -> without.validateIn(bare, order), "the context's default registry is empty");
        // With a registry of its own, the validator's wins over the context's.
        assertEquals(List.of("code:code_size", ":customerRequired"), report(((BeanRuleViolations) validator.validateIn(withRegistry, order)).getBeanViolations()));

        assertThrows(IllegalArgumentException.class, () -> validator.validateIn(null, order));
        assertThrows(IllegalArgumentException.class, () -> validator.validateIn(bare, null));
    }

    @Test
    public void groupsBeingProcessedAreBoundForRules() {
        List<Set<Class<?>>> seen = new ArrayList<>();
        RuleRegistry registry = RuleRegistry.builder()
                .register(Rule.builder().name("groupsSpy")
                        .then(action((RuleContext ruleContext) -> {
                            Set<Class<?>> groups = ruleContext.getBindings().getValue(BindingPlan.GROUPS);
                            seen.add(Set.copyOf(groups));
                        }))
                        .build())
                .build();
        BeanValidator validator = BeanValidator.builder().registry(registry).build();
        Audited audited = new Audited();

        validator.validate(audited);
        validator.validate(audited, OnCreate.class);
        validator.validate(audited, Extended.class);

        // The expanded groups of the step being processed: the requested ones and the interfaces they extend.
        assertEquals(List.of(Set.of(Default.class), Set.of(OnCreate.class), Set.of(Extended.class, OnCreate.class)), seen);
    }

    @Test
    public void validateGroupsSelectTheRulesAndJoinSequences() {
        BeanValidator validator = validator();
        Staged staged = new Staged();

        assertEquals(List.of("code:code_notNull"), report(validator.validate(staged)), "OnCreate not requested: the @Validate stays out");
        assertEquals(List.of(":customerRequired"), report(validator.validate(staged, OnCreate.class)));
        assertEquals(List.of(":customerRequired"), report(validator.validate(staged, Extended.class)), "a group extending OnCreate activates it");
        // A requested sequence stops on a @Validate violation as on a constraint, and runs it once.
        assertEquals(List.of(":customerRequired"), report(validator.validate(staged, CreateThenDefault.class)));
        staged.customer = new Customer();
        staged.customer.name = "ok";
        assertEquals(List.of("code:code_notNull"), report(validator.validate(staged, CreateThenDefault.class)), "OnCreate passed, Default ran");
    }

    @Test
    public void builderConfiguration() {
        StandardRuleContextOptions options = StandardRuleContextOptions.build();
        BeanValidator validator = BeanValidator.builder()
                .registry(REGISTRY)
                .options(options)
                .mapper(new SimpleConstraintMapper<>(Even.class, List.of(Integer.class),
                        (a, t) -> List.of(t.configure(Validators.positive(t.value())).build())))
                .extractor(new BoxValueExtractor())
                .build();

        assertSame(REGISTRY, validator.getRuleRegistry());
        assertSame(options, validator.getOptions().getDelegate());
        assertNotNull(validator.getConstraintMapperRegistry().find(Even.class));
        assertNotNull(validator.getValueExtractorRegistry().find(Box.class, 0));

        Boxed boxed = new Boxed();
        boxed.count = -2;
        boxed.box = new Box<>("");
        assertEquals(List.of("count:count_even", "box:box_Box0_notBlank"), report(validator.validate(boxed)));

        // Whole registries instead of additions, and the two styles do not mix.
        BeanValidator replaced = BeanValidator.builder()
                .mappers(ConstraintMapperRegistry.builder().build())
                .extractors(org.rulii.validation.beans.extract.ValueExtractorRegistry.builder().build())
                .build();
        assertNull(replaced.getConstraintMapperRegistry().find(Even.class));
        assertThrows(IllegalArgumentException.class, () -> BeanValidator.builder()
                .mappers(ConstraintMapperRegistry.builder().build()).mapper(new SimpleConstraintMapper<>(Even.class, List.of(Integer.class), (a, t) -> List.of())));
        assertThrows(IllegalArgumentException.class, () -> BeanValidator.builder()
                .extractors(org.rulii.validation.beans.extract.ValueExtractorRegistry.builder().build()).extractor(new BoxValueExtractor()));
        assertThrows(IllegalArgumentException.class, () -> BeanValidator.builder().registry(null));
        assertThrows(IllegalArgumentException.class, () -> BeanValidator.builder().options(null));

        // A registry inside the options serves @Validate when none is given explicitly.
        StandardRuleContextOptions withRegistry = new StandardRuleContextOptions() {
            @Override
            public RuleRegistry getRuleRegistry() {
                return REGISTRY;
            }
        };
        assertSame(REGISTRY, BeanValidator.builder().options(withRegistry).build().getRuleRegistry());
    }

    @Test
    public void cachesPerClassAndIsReusable() {
        BeanValidator validator = validator();

        assertSame(validator.getRuleFlow(Order.class), validator.getRuleFlow(Order.class));
        assertSame(validator.getBeanRules(Order.class), validator.getBeanRules(Order.class));
        assertEquals(Order.class, validator.getBeanDefinition(Order.class).getBeanClass());
        assertSame(validator.getBeanRules(Order.class).getDefinition(), validator.getBeanDefinition(Order.class));
        assertTrue(validator.toString().contains("classes="));
        assertThrows(IllegalArgumentException.class, () -> validator.getRuleFlow(null));

        // Many threads, one validator, every result its own.
        List<String> expected = List.of("code:code_size", "customer.name:name_notBlank");
        IntStream.range(0, 200).parallel().forEach(i -> {
            Order order = new Order();
            order.code = "x";
            order.customer = new Customer();
            assertEquals(expected, report(validator.validate(order)));
        });
    }

    @Test
    public void beanClassesAreASnapshotOfWhatWasRead() {
        BeanValidator validator = validator();
        assertEquals(Set.of(), validator.getBeanClasses(), "nothing read yet");

        validator.getBeanDefinition(Customer.class);
        Set<Class<?>> afterOne = validator.getBeanClasses();
        assertEquals(Set.of(Customer.class), afterOne);

        // Validating an Order cascades into its Customer; both are read. The earlier snapshot does not move.
        Order order = new Order();
        order.code = "x";
        order.customer = new Customer();
        validator.validate(order);
        assertEquals(Set.of(Order.class, Customer.class), validator.getBeanClasses());
        assertEquals(Set.of(Customer.class), afterOne);
        assertThrows(UnsupportedOperationException.class, () -> validator.getBeanClasses().clear());
    }

    @Test
    public void validatorInterfaceEdges() {
        Validator validator = validator();

        assertSame(validator, validator.unwrap(BeanValidator.class));
        assertSame(validator, validator.unwrap(Validator.class));
        assertThrows(ValidationException.class, () -> validator.unwrap(String.class));
        assertSame(validator.forExecutables(), validator.forExecutables(), "one executable validator per validator");
        assertSame(validator, ((ExecutableBeanValidator) validator.forExecutables()).getBeanValidator());
    }

    @Test
    public void validatePropertyChecksOnePropertyOnly() {
        BeanValidator validator = validator();
        Order order = new Order();
        order.code = "x";
        order.customer = new Customer();
        order.quantity = -1;

        Set<ConstraintViolation<Order>> violations = validator.validateProperty(order, "code");
        assertEquals(List.of("code:code_size"), report(violations), "no other property, no class rule, no @Validate, no cascade");
        ConstraintViolation<Order> code = violations.iterator().next();
        assertSame(order, code.getRootBean());
        assertSame(order, code.getLeafBean());
        assertEquals("x", code.getInvalidValue());

        assertEquals(List.of("quantity:quantity_positive"), report(validator.validateProperty(order, "quantity")));
        assertTrue(validator.validateProperty(order, "customer").isEmpty(), "cascading is not part of property validation");
        assertTrue(validator.validateProperty(order, "reference").isEmpty());
        assertEquals(List.of("reference:reference_notNull"), report(validator.validateProperty(order, "reference", OnCreate.class)));

        // Constraints on the field beside the getter are read off the object; the same constraint twice gets _2.
        Account account = new Account();
        account.tags = List.of("", "ok");
        assertEquals(List.of("id:id_notNull", "id:id_notNull_2"), report(validator.validateProperty(account, "id")));
        assertEquals(List.of("tags[0].<list element>:tags_List0_notBlank"), report(validator.validateProperty(account, "tags")));
        assertEquals(List.of("id:id_notNull", "id:id_notNull_2", "tags[0].<list element>:tags_List0_notBlank"), report(validator.validate(account)));

        assertThrows(IllegalArgumentException.class, () -> validator.validateProperty(order, "nope"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateProperty(order, ""));
        assertThrows(IllegalArgumentException.class, () -> validator.validateProperty(null, "code"));
    }

    @Test
    public void validateValueChecksAValueWithoutAnObject() {
        BeanValidator validator = validator();

        Set<ConstraintViolation<Account>> violations = validator.validateValue(Account.class, "id", null);
        assertEquals(List.of("id:id_notNull", "id:id_notNull_2"), report(violations), "field and getter constraints alike, against the value");
        ConstraintViolation<Account> id = violations.iterator().next();
        assertNull(id.getRootBean());
        assertNull(id.getLeafBean());
        assertEquals(Account.class, id.getRootBeanClass());
        assertNull(id.getInvalidValue());
        assertNull(((BeanValidationResult<Account>) violations).getRootBean());

        assertEquals(List.of("id:id_size"), report(validator.validateValue(Account.class, "id", "a")));
        assertTrue(validator.validateValue(Account.class, "id", "abc").isEmpty());
        assertEquals(List.of("tags[1].<list element>:tags_List0_notBlank"), report(validator.validateValue(Account.class, "tags", List.of("x", " "))));
        assertTrue(validator.validateValue(Account.class, "tags", null).isEmpty());
        assertTrue(validator.validateValue(Order.class, "customer", new Customer()).isEmpty(), "cascading is not part of value validation");
        assertTrue(validator.validateValue(Order.class, "reference", null).isEmpty());
        assertEquals(List.of("reference:reference_notNull"), report(validator.validateValue(Order.class, "reference", null, OnCreate.class)));

        assertThrows(IllegalArgumentException.class, () -> validator.validateValue(Account.class, "nope", "x"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateValue(null, "id", "x"));
    }

    // ---- fixtures ----

    public interface OnCreate {}

    public interface Extended extends OnCreate {}

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface Even {
        String message() default "must be even";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Validate("customerRequired")
    public static class Order {
        @NotNull
        @Size(min = 2, max = 5)
        String code;

        @NotNull(groups = OnCreate.class)
        String reference;

        @Valid
        Customer customer;

        @Positive
        int quantity = 1;
    }

    public static class Customer {
        @NotBlank
        String name;
    }

    @Validate(value = "groupsSpy", groups = {Default.class, OnCreate.class})
    public static class Audited {
        String note;
    }

    @GroupSequence({OnCreate.class, Default.class})
    public interface CreateThenDefault {}

    @Validate(value = "customerRequired", groups = OnCreate.class)
    public static class Staged {
        @NotNull
        String code;

        Customer customer;
    }

    public static class Account {
        @NotNull
        private String id;

        private List<@NotBlank String> tags;

        @NotNull
        @Size(min = 2)
        public String getId() {
            return id;
        }

        public List<String> getTags() {
            return tags;
        }
    }

    public static class Box<V> {
        final V value;

        public Box(V value) {
            super();
            this.value = value;
        }
    }

    public static class BoxValueExtractor implements ValueExtractor<Box<@ExtractedValue ?>> {
        @Override
        public void extractValues(Box<?> originalValue, ValueReceiver receiver) {
            receiver.value(null, originalValue.value);
        }
    }

    public static class Boxed {
        @Even
        Integer count;

        Box<@NotBlank String> box;
    }

    public static class Boom {
        @NotNull
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }

    public static class BoomService {
        public void take(@Valid Boom boom) {
        }
    }

    public static class Bomb<V> {
    }

    public static class BombExtractor implements ValueExtractor<Bomb<@ExtractedValue ?>> {
        @Override
        public void extractValues(Bomb<?> originalValue, ValueReceiver receiver) {
            throw new IllegalStateException("bomb");
        }
    }

    public static class Bombed {
        Bomb<@NotBlank String> bomb = new Bomb<>();
    }

    public interface Named {
        @NotNull
        String getName();
    }

    public static class Badge implements Named {
        private final String name;

        public Badge(String name) {
            super();
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static class Computed implements Named {
        @Override
        public String getName() {
            return null;
        }
    }

    public static class Both implements Named {
        private final String name;

        public Both(String name) {
            super();
            this.name = name;
        }

        @Override
        @Size(min = 2)
        public String getName() {
            return name;
        }
    }

    public abstract static class Labelled {
        @NotNull
        public abstract String getCode();
    }

    public static class Tag extends Labelled {
        private final String code;

        public Tag(String code) {
            super();
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }
    }
}
