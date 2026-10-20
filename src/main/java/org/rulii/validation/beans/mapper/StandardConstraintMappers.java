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
package org.rulii.validation.beans.mapper;

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
import org.rulii.rule.Rule;
import org.rulii.validation.ValueValidationRuleBuilder;
import org.rulii.validation.rules.Validators;
import org.rulii.validation.rules.asssert.AssertFalseValidationRule;
import org.rulii.validation.rules.asssert.AssertTrueValidationRule;
import org.rulii.validation.rules.digits.DigitsValidationRule;
import org.rulii.validation.rules.email.EmailValidationRule;
import org.rulii.validation.rules.future.FutureOrPresentValidationRule;
import org.rulii.validation.rules.future.FutureValidationRule;
import org.rulii.validation.rules.max.DecimalMaxValidationRule;
import org.rulii.validation.rules.max.MaxValidationRule;
import org.rulii.validation.rules.min.DecimalMinValidationRule;
import org.rulii.validation.rules.min.MinValidationRule;
import org.rulii.validation.rules.negative.NegativeOrZeroValidationRule;
import org.rulii.validation.rules.negative.NegativeValidationRule;
import org.rulii.validation.rules.notblank.NotBlankValidationRule;
import org.rulii.validation.rules.notempty.NotEmptyValidationRule;
import org.rulii.validation.rules.notnull.NotNullValidationRule;
import org.rulii.validation.rules.nulll.NullValidationRule;
import org.rulii.validation.rules.past.PastOrPresentValidationRule;
import org.rulii.validation.rules.past.PastValidationRule;
import org.rulii.validation.rules.pattern.PatternValidationRule;
import org.rulii.validation.rules.positive.PositiveOrZeroValidationRule;
import org.rulii.validation.rules.positive.PositiveValidationRule;
import org.rulii.validation.rules.size.SizeValidationRule;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The mappers of the 22 {@code jakarta.validation.constraints} annotations onto the core validation
 * rules: one table row per constraint, each naming the annotation type, the core rule's
 * {@code SUPPORTED_TYPES} and the materializing function.
 *
 * <p>Policy comes from the {@link ConstraintTarget}: every rule is configured through
 * {@link ConstraintTarget#configure}, which sets {@code failOnNull(false)} so a null value is
 * skipped (the specification validates null everywhere except for the presence constraints);
 * {@code @NotNull}, {@code @NotBlank} and {@code @NotEmpty} ask for {@code failOnNull(true)}, as
 * does {@code @Null}, whose passing case is null. Attribute-to-parameter mappings cover the attributes
 * the core rules report under another name ({@code regexp} as {@code pattern}, {@code value} as
 * {@code min}/{@code max}, {@code integer}/{@code fraction} as {@code maxIntegerLength}/
 * {@code maxFractionLength}).</p>
 *
 * <p>{@code @Email} with a {@code regexp} other than the default materializes two rules: the core
 * email rule and a pattern rule.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class StandardConstraintMappers {

    private static final String EMAIL_ANY_REGEXP = ".*";

    private static final List<ConstraintMapper<?>> MAPPERS = List.of(
            of(AssertFalse.class,     AssertFalseValidationRule.SUPPORTED_TYPES,     (a, t) -> single(t.configure(Validators.assertFalse(t.value())))),
            of(AssertTrue.class,      AssertTrueValidationRule.SUPPORTED_TYPES,      (a, t) -> single(t.configure(Validators.assertTrue(t.value())))),
            of(DecimalMax.class,      DecimalMaxValidationRule.SUPPORTED_TYPES,      (a, t) -> single(t.configure(Validators.decimalMax(t.value(), new BigDecimal(a.value()))).inclusive(a.inclusive())), Map.of("value", "max")),
            of(DecimalMin.class,      DecimalMinValidationRule.SUPPORTED_TYPES,      (a, t) -> single(t.configure(Validators.decimalMin(t.value(), new BigDecimal(a.value()))).inclusive(a.inclusive())), Map.of("value", "min")),
            of(Digits.class,          DigitsValidationRule.SUPPORTED_TYPES,          (a, t) -> single(t.configure(Validators.digits(t.value(), a.integer(), a.fraction()))), Map.of("integer", "maxIntegerLength", "fraction", "maxFractionLength")),
            of(Email.class,           EmailValidationRule.SUPPORTED_TYPES,           StandardConstraintMappers::email, Map.of("regexp", "pattern")),
            of(Future.class,          FutureValidationRule.SUPPORTED_TYPES,          (a, t) -> single(t.configure(Validators.future(t.value())))),
            of(FutureOrPresent.class, FutureOrPresentValidationRule.SUPPORTED_TYPES, (a, t) -> single(t.configure(Validators.futureOrPresent(t.value())))),
            of(Max.class,             MaxValidationRule.SUPPORTED_TYPES,             (a, t) -> single(t.configure(Validators.max(t.value(), a.value()))), Map.of("value", "max")),
            of(Min.class,             MinValidationRule.SUPPORTED_TYPES,             (a, t) -> single(t.configure(Validators.min(t.value(), a.value()))), Map.of("value", "min")),
            of(Negative.class,        NegativeValidationRule.SUPPORTED_TYPES,        (a, t) -> single(t.configure(Validators.negative(t.value())))),
            of(NegativeOrZero.class,  NegativeOrZeroValidationRule.SUPPORTED_TYPES,  (a, t) -> single(t.configure(Validators.negativeOrZero(t.value())))),
            of(NotBlank.class,        NotBlankValidationRule.SUPPORTED_TYPES,        (a, t) -> single(t.configure(Validators.notBlank(t.value()), true))),
            of(NotEmpty.class,        NotEmptyValidationRule.SUPPORTED_TYPES,        (a, t) -> single(t.configure(Validators.notEmpty(t.value()), true))),
            of(NotNull.class,         NotNullValidationRule.SUPPORTED_TYPES,         (a, t) -> single(t.configure(Validators.notNull(t.value()), true))),
            of(Null.class,            NullValidationRule.SUPPORTED_TYPES,            (a, t) -> single(t.configure(Validators.isNull(t.value()), true))),
            of(Past.class,            PastValidationRule.SUPPORTED_TYPES,            (a, t) -> single(t.configure(Validators.past(t.value())))),
            of(PastOrPresent.class,   PastOrPresentValidationRule.SUPPORTED_TYPES,   (a, t) -> single(t.configure(Validators.pastOrPresent(t.value())))),
            of(Pattern.class,         PatternValidationRule.SUPPORTED_TYPES,         (a, t) -> single(t.configure(Validators.pattern(t.value(), a.regexp())).flags(flags(a.flags()))), Map.of("regexp", "pattern")),
            of(Positive.class,        PositiveValidationRule.SUPPORTED_TYPES,        (a, t) -> single(t.configure(Validators.positive(t.value())))),
            of(PositiveOrZero.class,  PositiveOrZeroValidationRule.SUPPORTED_TYPES,  (a, t) -> single(t.configure(Validators.positiveOrZero(t.value())))),
            of(Size.class,            SizeValidationRule.SUPPORTED_TYPES,            (a, t) -> single(t.configure(Validators.size(t.value(), a.min(), a.max()))))
    );

    private StandardConstraintMappers() {
        super();
    }

    /**
     * All standard mappers, in the order of the table.
     *
     * @return the mappers; unmodifiable.
     */
    public static List<ConstraintMapper<?>> all() {
        return MAPPERS;
    }

    /**
     * The regex flag bits of the given jakarta flags, as {@link java.util.regex.Pattern} understands
     * them (each jakarta flag carries its JDK constant).
     *
     * @param flags the jakarta flags.
     * @return the OR of their values; 0 for none.
     */
    public static int flags(Pattern.Flag[] flags) {
        int result = 0;
        for (Pattern.Flag flag : flags) {
            result |= flag.getValue();
        }
        return result;
    }

    private static List<Rule> email(Email email, ConstraintTarget target) {
        List<Rule> result = new ArrayList<>(2);
        result.add(target.configure(Validators.email(target.value())).build());

        if (!EMAIL_ANY_REGEXP.equals(email.regexp())) {
            result.add(target.configure(Validators.pattern(target.value(), email.regexp())).flags(flags(email.flags())).build());
        }

        return result;
    }

    private static List<Rule> single(ValueValidationRuleBuilder<?, ?> builder) {
        return List.of(builder.build());
    }

    private static <A extends Annotation> ConstraintMapper<A> of(Class<A> annotationType, List<Class<?>> supportedTypes,
                                                                 BiFunction<A, ConstraintTarget, List<Rule>> materializer) {
        return new SimpleConstraintMapper<>(annotationType, supportedTypes, materializer);
    }

    private static <A extends Annotation> ConstraintMapper<A> of(Class<A> annotationType, List<Class<?>> supportedTypes,
                                                                 BiFunction<A, ConstraintTarget, List<Rule>> materializer,
                                                                 Map<String, String> parameterNames) {
        return new SimpleConstraintMapper<>(annotationType, supportedTypes, materializer, parameterNames);
    }
}
