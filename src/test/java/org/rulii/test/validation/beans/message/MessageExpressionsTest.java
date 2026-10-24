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
package org.rulii.test.validation.beans.message;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.text.MessageFormatter;
import org.rulii.text.ParameterInfo;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.message.BeanValidationMessageFormatter;
import org.rulii.validation.beans.message.ElFormatter;
import org.rulii.validation.beans.message.MessageExpressions;
import org.rulii.validation.beans.validator.RuliiConstraintValidatorContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Message expressions through rulii's {@code el} language: the specification's scope, the formatter,
 * the root object, failures left as written, the fallback without the language, and the run-time
 * template restriction.
 */
public class MessageExpressionsTest {

    public MessageExpressionsTest() {
        super();
    }

    private static List<String> messages(Iterable<? extends ConstraintViolation<?>> violations) {
        List<String> result = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath() + ":" + violation.getMessage());
        }
        return result;
    }

    private static BeanValidationMessageFormatter formatter(MessageExpressions expressions) {
        return new BeanValidationMessageFormatter(MessageFormatter.builder().build(), null, expressions);
    }

    @Test
    public void theElLanguageIsAvailableInTheTests() {
        MessageExpressions expressions = MessageExpressions.standard();

        assertTrue(expressions.isAvailable());
        assertEquals("jakarta-el", expressions.getFactory().getLanguageName());
        assertSame(expressions, MessageExpressions.standard(), "resolved once");
        assertFalse(MessageExpressions.NONE.isAvailable());
        assertNull(MessageExpressions.NONE.evaluate("${1 + 1}", Map.of(), null, Locale.ENGLISH));
    }

    @Test
    public void expressionsSeeTheSpecScope() {
        BeanValidationMessageFormatter formatter = formatter(MessageExpressions.standard());
        ParameterInfo value = new ParameterInfo(0, "name", "abc");
        ParameterInfo min = new ParameterInfo(1, "min", 5);

        assertEquals("abc has 3 of 5", formatter.format(Locale.ENGLISH, "${validatedValue} has ${validatedValue.length()} of ${min}", value, min));
        assertEquals("10 and 5", formatter.format(Locale.ENGLISH, "${min * 2} and ${ctx.min}", value, min));
        assertEquals("some", formatter.format(Locale.ENGLISH, "${empty validatedValue ? 'none' : 'some'}", value, min));
        assertEquals("or equal to ", formatter.format(Locale.ENGLISH, "${inclusive == true ? 'or equal to ' : ''}", value, new ParameterInfo(1, "inclusive", true)));
        assertEquals("", formatter.format(Locale.ENGLISH, "${null}", value));
    }

    @Test
    public void formatterFormatsAtTheMessageLocale() {
        BeanValidationMessageFormatter formatter = formatter(MessageExpressions.standard());
        ParameterInfo value = new ParameterInfo(0, "price", 3.14159);

        assertEquals("3.14", formatter.format(Locale.ENGLISH, "${formatter.format('%1$.2f', validatedValue)}", value));
        assertEquals("3,14", formatter.format(Locale.GERMAN, "${formatter.format('%1$.2f', validatedValue)}", value));
        assertEquals("3,14", new ElFormatter(Locale.GERMAN).format("%1$.2f", 3.14159));
        assertEquals(Locale.GERMAN, new ElFormatter(Locale.GERMAN).getLocale());
    }

    @Test
    public void failuresLeaveTheExpressionAsWritten() {
        BeanValidationMessageFormatter formatter = formatter(MessageExpressions.standard());
        ParameterInfo value = new ParameterInfo(0, "name", "abc");

        assertEquals("x ${nobody.name} y", formatter.format(Locale.ENGLISH, "x ${nobody.name} y", value), "unknown identifier");
        assertEquals("x ${1 +} y", formatter.format(Locale.ENGLISH, "x ${1 +} y", value), "parse error");
    }

    @Test
    public void withoutTheLanguageTheOldBehaviourStays() {
        BeanValidationMessageFormatter formatter = formatter(MessageExpressions.NONE);
        ParameterInfo value = new ParameterInfo(0, "name", "abc");

        assertEquals("abc, ${validatedValue.length()}, or equal to ",
                formatter.format(Locale.ENGLISH, "${validatedValue}, ${validatedValue.length()}, ${inclusive == true ? 'or equal to ' : ''}",
                        value, new ParameterInfo(1, "inclusive", true)));
    }

    @Test
    public void constraintMessagesEvaluateEndToEnd() {
        BeanValidator validator = BeanValidator.builder().build();
        Product product = new Product();
        product.code = "x";
        product.price = new BigDecimal("1.5");

        assertEquals(List.of("code:needs 3 chars, got 1 (X)", "price:must be greater than 1.5"), messages(validator.validate(product)));
    }

    @Test
    public void runTimeTemplatesKeepTheirExpressionsUnlessEnabled() {
        BeanValidator validator = BeanValidator.builder().build();
        Product product = new Product();
        product.code = "abc";
        product.price = new BigDecimal("2");
        product.note = "n";

        assertEquals(List.of("note:got ${validatedValue.length()} for 2: 2", "note:got 1 for 2"), messages(validator.validate(product)),
                "the first template is escaped (parameters still interpolated), the second enabled expressions");
        assertEquals("a \\$\\{b\\} c {d}", MessageExpressions.escape("a ${b} c {d}"));
    }

    // --- fixtures -------------------------------------------------------------------------------

    @Constraint(validatedBy = NoteValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Noted {
        String message() default "noted";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int min() default 2;
    }

    public static class NoteValidator implements ConstraintValidator<Noted, String> {
        public NoteValidator() {
            super();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("got ${validatedValue.length()} for {min}: {min}").addConstraintViolation();
            context.unwrap(RuliiConstraintValidatorContext.class).buildConstraintViolationWithTemplate("got ${validatedValue.length()} for {min}")
                    .enableExpressionLanguage().addConstraintViolation();
            return false;
        }
    }

    public static class Product {
        @Size(min = 3, message = "needs {min} chars, got ${validatedValue.length()} (${validatedValue.toUpperCase()})")
        String code;

        @DecimalMin(value = "1.5", inclusive = false)
        BigDecimal price;

        @Noted
        String note;
    }
}
