/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rulii.test.validation.beans.validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.ValidationException;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.validator.RuliiConstraintValidatorContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The library's own additions to the constraint validator context: message parameters and
 * expression variables, and the refusal of Hibernate Validator's context that says what to use.
 */
public class ConstraintValidatorContextTest {

    public ConstraintValidatorContextTest() {
        super();
    }

    @Test
    public void messageParametersAndExpressionVariablesReachTheMessages() {
        BeanValidator validator = BeanValidator.builder().build();
        Map<String, String> messages = new LinkedHashMap<>();

        for (ConstraintViolation<Capped> violation : validator.validate(new Capped())) {
            messages.put(violation.getMessage(), violation.getPropertyPath().toString());
        }

        // The default message sees the parameter; the custom template sees the parameter and, in its expression, the variable.
        assertEquals(Map.of("3 at most", "value", "at most 3, twice is 6", "value"), messages);
    }

    @Test
    public void hibernateContextIsRefusedWithTheAlternativeNamed() {
        RuliiConstraintValidatorContext context = new RuliiConstraintValidatorContext(
                RuleContext.builder().with(Bindings.builder().scoped()).build(), "template");

        ValidationException e = assertThrows(ValidationException.class, () -> context.unwrap(HibernateConstraintValidatorContext.class));
        assertTrue(e.getMessage().contains(RuliiConstraintValidatorContext.class.getName()), e.getMessage());
        assertTrue(e.getMessage().contains("addMessageParameter"), e.getMessage());
        assertSame(context, context.unwrap(RuliiConstraintValidatorContext.class));
        assertThrows(IllegalArgumentException.class, () -> context.addMessageParameter(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> context.addExpressionVariable(null, 1));
    }

    @Constraint(validatedBy = CapValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Cap {
        String message() default "{limit} at most";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class CapValidator implements ConstraintValidator<Cap, Integer> {
        public CapValidator() {
            super();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null || value <= 3) return true;
            RuliiConstraintValidatorContext ours = context.unwrap(RuliiConstraintValidatorContext.class);
            ours.addMessageParameter("limit", 3).addExpressionVariable("twice", 6);
            ours.buildConstraintViolationWithTemplate("at most {limit}, twice is ${twice}").enableExpressionLanguage().addConstraintViolation();
            return false;
        }
    }

    public static class Capped {
        @Cap Integer value = 5;
    }
}
