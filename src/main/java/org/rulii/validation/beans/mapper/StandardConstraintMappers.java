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
import java.util.List;

/**
 * The mappers for the standard {@code jakarta.validation.constraints}: one table from each
 * constraint annotation to the core rule it materializes to, whose {@code SUPPORTED_TYPES} the
 * mapper reports.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class StandardConstraintMappers {

    private static final List<ConstraintMapper<?>> MAPPERS = List.of(
            of(AssertFalse.class,     AssertFalseValidationRule.SUPPORTED_TYPES),
            of(AssertTrue.class,      AssertTrueValidationRule.SUPPORTED_TYPES),
            of(DecimalMax.class,      DecimalMaxValidationRule.SUPPORTED_TYPES),
            of(DecimalMin.class,      DecimalMinValidationRule.SUPPORTED_TYPES),
            of(Digits.class,          DigitsValidationRule.SUPPORTED_TYPES),
            of(Email.class,           EmailValidationRule.SUPPORTED_TYPES),
            of(Future.class,          FutureValidationRule.SUPPORTED_TYPES),
            of(FutureOrPresent.class, FutureOrPresentValidationRule.SUPPORTED_TYPES),
            of(Max.class,             MaxValidationRule.SUPPORTED_TYPES),
            of(Min.class,             MinValidationRule.SUPPORTED_TYPES),
            of(Negative.class,        NegativeValidationRule.SUPPORTED_TYPES),
            of(NegativeOrZero.class,  NegativeOrZeroValidationRule.SUPPORTED_TYPES),
            of(NotBlank.class,        NotBlankValidationRule.SUPPORTED_TYPES),
            of(NotEmpty.class,        NotEmptyValidationRule.SUPPORTED_TYPES),
            of(NotNull.class,         NotNullValidationRule.SUPPORTED_TYPES),
            of(Null.class,            NullValidationRule.SUPPORTED_TYPES),
            of(Past.class,            PastValidationRule.SUPPORTED_TYPES),
            of(PastOrPresent.class,   PastOrPresentValidationRule.SUPPORTED_TYPES),
            of(Pattern.class,         PatternValidationRule.SUPPORTED_TYPES),
            of(Positive.class,        PositiveValidationRule.SUPPORTED_TYPES),
            of(PositiveOrZero.class,  PositiveOrZeroValidationRule.SUPPORTED_TYPES),
            of(Size.class,            SizeValidationRule.SUPPORTED_TYPES)
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

    private static <A extends Annotation> ConstraintMapper<A> of(Class<A> annotationType, List<Class<?>> supportedTypes) {
        return new SimpleConstraintMapper<>(annotationType, supportedTypes);
    }
}
