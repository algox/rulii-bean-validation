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
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.GroupSequence;
import jakarta.validation.Path;
import jakarta.validation.Payload;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.NamedScope;
import org.rulii.context.RuleContext;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleListener;
import org.rulii.rule.RuleResult;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleflow.RuleFlowListener;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanValidationResult;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.Bind;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.constraints.Length;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A plain class, one with nothing but predefined constraints: validated without bindings and without a flow, at
 * the root of a call and where a cascade reaches it, with the report a flow would have produced.
 */
public class PlainValidationTest {

    private static final RuleRegistry REGISTRY = RuleRegistry.builder()
            .register(Validators.notNull(Validators.binding("name")).name("nameRequired").build())
            .build();

    public PlainValidationTest() {
        super();
    }

    private static BeanValidator validator(List<String> events) {
        return builder(events).build();
    }

    /**
     * A validator whose every context reports the flows it starts and the rules it ends.
     */
    private static org.rulii.validation.beans.BeanValidatorBuilder builder(List<String> events) {
        StandardRuleContextOptions options = StandardRuleContextOptions.build();
        options.getTracer().addListener(flows(events));
        options.getTracer().addListener(new RuleListener() {
            @Override
            public void onRuleEnd(Rule rule, RuleResult result) {
                events.add(rule.getName() + ":" + result.status());
            }
        });
        return BeanValidator.builder().registry(REGISTRY).options(options);
    }

    private static RuleFlowListener flows(List<String> events) {
        return new RuleFlowListener() {
            @Override
            public void onRuleFlowStart(RuleFlow<?> ruleFlow, NamedScope ruleFlowScope) {
                events.add("flow " + ruleFlow.getName());
            }
        };
    }

    private static List<String> report(BeanValidationResult<?> result) {
        List<String> lines = new ArrayList<>();

        for (BeanRuleViolation violation : result.getViolations()) {
            lines.add(violation.getPropertyPath() + " " + violation.getRuleName() + " [" + violation.getMessage() + "] " + violation.getInvalidValue());
        }

        return lines;
    }

    @Test
    public void classWithNothingButPredefinedConstraintsIsPlain() {
        BeanValidator validator = validator(new ArrayList<>());

        assertTrue(validator.getBeanRules(Person.class).isPlain());
        assertTrue(validator.getBeanRules(Address.class).isPlain());
        assertTrue(validator.getBeanRules(Person.class).plan(RequestedGroups.DEFAULT).isPlain());

        assertFalse(validator.getBeanRules(Unconstrained.class).isPlain(), "nothing to validate at all is its own case");
        assertFalse(validator.getBeanRules(Company.class).isPlain(), "a cascade");
        assertFalse(validator.getBeanRules(Tagged.class).isPlain(), "a container element constraint");
        assertFalse(validator.getBeanRules(WithRule.class).isPlain(), "a @Validate rule");
        assertFalse(validator.getBeanRules(Scripted.class).isPlain(), "an @Expr");
        assertFalse(validator.getBeanRules(Binding.class).isPlain(), "a @Bind");
        assertFalse(validator.getBeanRules(Evens.class).isPlain(), "a constraint validator");
        assertFalse(validator.getBeanRules(Composed.class).isPlain(), "a @ReportAsSingleViolation composition");
        assertFalse(validator.getBeanRules(Resequenced.class).isPlain(), "a redefined default group sequence");
        assertFalse(validator.getBeanRules(Company.class).plan(RequestedGroups.DEFAULT).isPlain());
    }

    @Test
    public void plainObjectIsValidatedWithoutAFlow() {
        List<String> events = new ArrayList<>();
        BeanValidator validator = validator(events);

        assertTrue(validator.validate(new Person("Ada", "ada@rulii.org", 36, LocalDate.of(1990, 1, 1))).isEmpty());
        assertEquals(List.of("name_notBlank:PASS", "name_size:PASS", "email_email:PASS", "age_min:PASS", "age_max:PASS", "born_past:PASS",
                "label_notNull:PASS"), events, "every rule was asked and traced; no flow was started");

        events.clear();
        Person invalid = new Person(" ", "nope", 200, null);
        BeanValidationResult<Person> result = validator.validate(invalid);

        assertEquals(List.of(
                "name name_notBlank [must not be blank]  ",
                "name name_size [size must be between 2 and 10]  ",
                "email email_email [must be a well-formed email address] nope",
                "age age_max [must be less than or equal to 150] 200"), report(result));
        assertSame(invalid, result.getRootBean());
        assertSame(invalid, result.getViolations().get(0).getLeafBean());
        assertEquals(List.of("name_notBlank:FAIL", "name_size:FAIL", "email_email:FAIL", "age_min:PASS", "age_max:FAIL", "born_past:SKIPPED",
                "label_notNull:PASS"), events);
    }

    @Test
    public void propertyIsReadOncePerCall() {
        BeanValidator validator = validator(new ArrayList<>());
        Person person = new Person("Ada", "ada@rulii.org", 36, LocalDate.of(1990, 1, 1));

        validator.validate(person);

        assertEquals(1, person.nameReads, "two constraints on the getter, one read");
        assertEquals(1, person.labelReads);
    }

    @Test
    public void groupsSequencesAndFailFastApply() {
        BeanValidator validator = validator(new ArrayList<>());
        Staged staged = new Staged();

        assertEquals(List.of("always always_notNull [must not be null] null"), report(validator.validate(staged)));
        assertEquals(List.of("first first_notNull [must not be null] null"), report(validator.validate(staged, First.class)));
        assertEquals(List.of("always always_notNull [must not be null] null", "first first_notNull [must not be null] null"),
                report(validator.validate(staged, Default.class, First.class)));
        assertEquals(List.of("first first_notNull [must not be null] null"), report(validator.validate(staged, FirstThenDefault.class)),
                "a sequence stops at its first step with violations");

        BeanValidator failFast = builder(new ArrayList<>()).failFast(true).build();
        assertEquals(List.of("name name_notBlank [must not be blank]  "), report(failFast.validate(new Person(" ", "nope", 200, null))));
    }

    @Test
    public void cascadeValidatesPlainObjectsWithoutAFlow() {
        List<String> events = new ArrayList<>();
        BeanValidator validator = validator(events);
        Address shared = new Address(null);
        Company company = new Company();
        company.head = new Person(" ", "ada@rulii.org", 36, null);
        company.office = shared;
        company.branches = List.of(new Address("Oslo"), shared, new Address(""));

        BeanValidationResult<Company> result = validator.validate(company);

        assertEquals(List.of(
                "head.name name_notBlank [must not be blank]  ",
                "head.name name_size [size must be between 2 and 10]  ",
                "office.city city_notBlank [must not be blank] null",
                "branches[2].city city_notBlank [must not be blank] "), report(result),
                "paths are those of the cascade; the shared address is validated once");
        assertSame(company.head, result.getViolations().get(0).getLeafBean());
        assertSame(company, result.getViolations().get(0).getRootBean());
        assertEquals(List.of("flow companyValidationFlow"), events.stream().filter(e -> e.startsWith("flow ")).toList(),
                "only the class that cascades ran a flow");
    }

    @Test
    public void traversableResolverIsAskedAsInAFlow() {
        List<String> asked = new ArrayList<>();
        TraversableResolver resolver = new TraversableResolver() {
            @Override
            public boolean isReachable(Object bean, Path.Node node, Class<?> rootBeanType, Path pathToBean, ElementType elementType) {
                asked.add("reachable " + bean.getClass().getSimpleName() + "." + node.getName() + " at [" + pathToBean + "] of " + rootBeanType.getSimpleName());
                return !node.getName().equals("email");
            }

            @Override
            public boolean isCascadable(Object bean, Path.Node node, Class<?> rootBeanType, Path pathToBean, ElementType elementType) {
                return true;
            }
        };
        BeanValidator validator = builder(new ArrayList<>()).traversableResolver(resolver).build();

        assertEquals(List.of("name name_notBlank [must not be blank]  ", "name name_size [size must be between 2 and 10]  "),
                report(validator.validate(new Person(" ", "nope", 36, null))), "the unreachable email was not validated");
        assertEquals(List.of("reachable Person.name at [] of Person", "reachable Person.email at [] of Person",
                "reachable Person.age at [] of Person", "reachable Person.born at [] of Person", "reachable Person.label at [] of Person"), asked);

        asked.clear();
        Company company = new Company();
        company.head = new Person("Ada", "nope", 36, null);
        assertTrue(validator.validate(company).isEmpty());
        assertTrue(asked.contains("reachable Person.email at [head] of Company"), asked.toString());
    }

    @Test
    public void runInsideAContextOfTheCallersStillRunsTheFlow() {
        BeanValidator validator = validator(new ArrayList<>());
        List<String> events = new ArrayList<>();
        RuleContext context = RuleContext.builder().with(Bindings.builder().scoped()).registry(REGISTRY).build();
        context.getTracer().addListener(flows(events));
        Company company = new Company();
        company.head = new Person(" ", "ada@rulii.org", 36, null);

        RuleViolations own = validator.validateIn(context, company);

        assertEquals(2, own.size());
        assertEquals(List.of("flow companyValidationFlow", "flow personValidationFlow"), events);
    }

    public interface First {}

    @GroupSequence({First.class, Default.class})
    public interface FirstThenDefault {}

    public static class Person {
        private final String name;

        @Email
        private final String email;

        @Min(0)
        @Max(150)
        private final int age;

        @Past
        private final LocalDate born;

        int nameReads;
        int labelReads;

        Person(String name, String email, int age, LocalDate born) {
            super();
            this.name = name;
            this.email = email;
            this.age = age;
            this.born = born;
        }

        @NotBlank
        @Size(min = 2, max = 10)
        public String getName() {
            nameReads++;
            return name;
        }

        @NotNull
        public String getLabel() {
            labelReads++;
            return name + " <" + email + ">";
        }
    }

    public static class Address {
        @NotBlank
        private final String city;

        Address(String city) {
            super();
            this.city = city;
        }
    }

    public static class Staged {
        @NotNull
        public String always;

        @NotNull(groups = First.class)
        public String first;

        public Staged() {
            super();
        }
    }

    public static class Unconstrained {
        public String name = "n";

        public Unconstrained() {
            super();
        }
    }

    public static class Company {
        @Valid
        public Person head;

        @Valid
        public Address office;

        public List<@Valid Address> branches = List.of();

        public Company() {
            super();
        }
    }

    public static class Tagged {
        public List<@NotBlank String> tags = List.of();

        public Tagged() {
            super();
        }
    }

    @Validate("nameRequired")
    public static class WithRule {
        @NotNull
        public String name = "n";

        public WithRule() {
            super();
        }
    }

    public static class Scripted {
        @NotNull
        @Expr(value = "${name != 'x'}", lang = "jakarta-el", errorCode = "scripted.name")
        public String name = "n";

        public Scripted() {
            super();
        }
    }

    @Bind(name = "limit", value = "2", type = Integer.class)
    public static class Binding {
        @NotNull
        public String name = "n";

        public Binding() {
            super();
        }
    }

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = EvenValidator.class)
    public @interface Even {
        String message() default "must be even";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class EvenValidator implements ConstraintValidator<Even, Integer> {

        public EvenValidator() {
            super();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || value % 2 == 0;
        }
    }

    public static class Evens {
        @Even
        @NotNull
        public Integer number = 2;

        public Evens() {
            super();
        }
    }

    public static class Composed {
        @Length(min = 2, max = 4)
        public String code = "abc";

        public Composed() {
            super();
        }
    }

    @GroupSequence({First.class, Resequenced.class})
    public static class Resequenced {
        @NotNull
        public String name = "n";

        public Resequenced() {
            super();
        }
    }
}
