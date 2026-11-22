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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.NamedScope;
import org.rulii.context.RuleContext;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.registry.RuleRegistry;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleflow.RuleFlowListener;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanValidationResult;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.Bind;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.group.InvalidGroupException;
import org.rulii.validation.rules.Validators;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A class that declares nothing to validate: known when the class is read, so a validation call sets nothing up
 * for it and a cascade does not descend into it.
 */
public class NothingToValidateTest {

    private static final RuleRegistry REGISTRY = RuleRegistry.builder()
            .register(Validators.notNull(Validators.binding("name")).name("nameRequired").build())
            .build();

    public NothingToValidateTest() {
        super();
    }

    /**
     * A validator whose every context reports the flows it starts.
     */
    private static BeanValidator validator(List<String> flows) {
        StandardRuleContextOptions options = StandardRuleContextOptions.build();
        options.getTracer().addListener(listener(flows));
        return BeanValidator.builder().registry(REGISTRY).options(options).build();
    }

    private static RuleFlowListener listener(List<String> flows) {
        return new RuleFlowListener() {
            @Override
            public void onRuleFlowStart(RuleFlow<?> ruleFlow, NamedScope ruleFlowScope) {
                flows.add(ruleFlow.getName());
            }
        };
    }

    @Test
    public void whatAClassDeclaresIsKnownWhenItIsRead() {
        BeanValidator validator = validator(new ArrayList<>());

        assertTrue(validator.getBeanRules(Plain.class).validatesNothing());
        assertTrue(validator.getBeanRules(Bare.class).validatesNothing());
        assertFalse(validator.getBeanRules(Constrained.class).validatesNothing(), "a constraint");
        assertFalse(validator.getBeanRules(Cascading.class).validatesNothing(), "a cascade, whatever it cascades into");
        assertFalse(validator.getBeanRules(Elements.class).validatesNothing(), "a container element constraint");
        assertFalse(validator.getBeanRules(ElementCascade.class).validatesNothing(), "a container element cascade");
        assertFalse(validator.getBeanRules(WithRule.class).validatesNothing(), "a @Validate rule");
        assertFalse(validator.getBeanRules(Scripted.class).validatesNothing(), "an @Expr");
        assertFalse(validator.getBeanRules(Binding.class).validatesNothing(), "a @Bind, evaluated with or without a rule to read it");
        assertFalse(validator.getBeanRules(Derived.class).validatesNothing(), "an inherited constraint");
    }

    @Test
    public void validatingSuchAnObjectSetsNothingUp() {
        List<String> flows = new ArrayList<>();
        BeanValidator validator = validator(flows);
        Plain plain = new Plain();

        BeanValidationResult<Plain> result = validator.validate(plain);

        assertTrue(result.isEmpty());
        assertSame(plain, result.getRootBean());
        assertTrue(result.getRuleViolations().isEmpty());
        assertEquals(List.of(), flows, "no flow was run");

        assertTrue(validator.validate(new Constrained()).isEmpty());
        assertEquals(List.of("constrainedValidationFlow"), flows, "a class with something to validate runs its flow");
    }

    @Test
    public void theCallIsStillChecked() {
        BeanValidator validator = validator(new ArrayList<>());

        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
        assertThrows(InvalidGroupException.class, () -> validator.validate(new Plain(), String.class));
    }

    @Test
    public void cascadeDoesNotDescendIntoSuchAnObject() {
        List<String> flows = new ArrayList<>();
        BeanValidator validator = validator(flows);
        Holder holder = new Holder();
        holder.constrained.name = null;

        BeanValidationResult<Holder> result = validator.validate(holder);

        assertEquals(List.of("constrained.name"), result.getViolations().stream().map(v -> v.getPropertyPath().toString()).toList());
        assertEquals(List.of("holderValidationFlow", "constrainedValidationFlow"), flows,
                "the plain objects, single and in the list, were left out");

        flows.clear();
        assertTrue(validator.validate(new Cascading()).isEmpty());
        assertEquals(List.of("cascadingValidationFlow"), flows, "a class that only cascades is entered; what it cascades into is not");
    }

    @Test
    public void runInsideAContextOfTheCallersRunsEveryFlow() {
        BeanValidator validator = validator(new ArrayList<>());
        List<String> flows = new ArrayList<>();
        RuleContext context = RuleContext.builder().with(Bindings.builder().scoped()).registry(REGISTRY).build();
        context.getTracer().addListener(listener(flows));

        RuleViolations own = validator.validateIn(context, new Plain());

        assertTrue(own.isEmpty());
        assertEquals(List.of("plainValidationFlow"), flows, "the flow the caller asked for runs, and is traced");

        flows.clear();
        validator.validateIn(context, new Cascading());
        assertEquals(List.of("cascadingValidationFlow", "plainValidationFlow"), flows, "and so does the flow of every object it cascades into");
    }

    @Test
    public void classThatOnlyBindsStillRuns() {
        List<String> flows = new ArrayList<>();
        BeanValidator validator = validator(flows);

        assertTrue(validator.validate(new Binding()).isEmpty());
        assertEquals(List.of("bindingValidationFlow"), flows);
    }

    public static class Bare {

        public Bare() {
            super();
        }
    }

    public static class Plain {
        public String name = "n";
        public int count = 1;

        public Plain() {
            super();
        }

        public String getLabel() {
            return name;
        }
    }

    public static class Constrained {
        @NotNull
        public String name = "n";

        public Constrained() {
            super();
        }
    }

    public static class Derived extends Constrained {

        public Derived() {
            super();
        }
    }

    public static class Cascading {
        @Valid
        public Plain plain = new Plain();

        public Cascading() {
            super();
        }
    }

    public static class Elements {
        public List<@NotNull String> items = new ArrayList<>();

        public Elements() {
            super();
        }
    }

    public static class ElementCascade {
        public List<@Valid Plain> items = new ArrayList<>();

        public ElementCascade() {
            super();
        }
    }

    @Validate("nameRequired")
    public static class WithRule {
        public String name = "n";

        public WithRule() {
            super();
        }
    }

    @Expr(value = "${name != null}", lang = "jakarta-el", errorCode = "scripted.name")
    public static class Scripted {
        public String name = "n";

        public Scripted() {
            super();
        }
    }

    @Bind(name = "limit", value = "2", type = Integer.class)
    public static class Binding {
        public String name = "n";

        public Binding() {
            super();
        }
    }

    public static class Holder {
        @Valid
        public Plain plain = new Plain();

        @Valid
        public Constrained constrained = new Constrained();

        public List<@Valid Plain> plains = List.of(new Plain(), new Plain());

        public Holder() {
            super();
        }
    }
}
