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

import org.rulii.validation.rules.Validators;
import org.rulii.validation.rules.alpha.AlphaValidationRule;
import org.rulii.validation.rules.alpha.AlphaValidationRuleBuilder;
import org.rulii.validation.rules.alphnumeric.AlphaNumericValidationRule;
import org.rulii.validation.rules.alphnumeric.AlphaNumericValidationRuleBuilder;
import org.rulii.validation.rules.ascii.AsciiValidationRule;
import org.rulii.validation.rules.codepointlength.CodePointLengthValidationRule;
import org.rulii.validation.rules.decimal.DecimalValidationRule;
import org.rulii.validation.rules.decimal.DecimalValidationRuleBuilder;
import org.rulii.validation.rules.duration.DurationMaxValidationRule;
import org.rulii.validation.rules.duration.DurationMinValidationRule;
import org.rulii.validation.rules.ean.EanValidationRule;
import org.rulii.validation.rules.endswith.EndsWithValidationRule;
import org.rulii.validation.rules.in.InValidationRule;
import org.rulii.validation.rules.isbn.IsbnValidationRule;
import org.rulii.validation.rules.lowercase.LowerCaseValidationRule;
import org.rulii.validation.rules.modcheck.LuhnCheckValidationRule;
import org.rulii.validation.rules.modcheck.Mod10CheckValidationRule;
import org.rulii.validation.rules.modcheck.Mod11CheckValidationRule;
import org.rulii.validation.rules.normalized.NormalizedValidationRule;
import org.rulii.validation.rules.numeric.NumericValidationRule;
import org.rulii.validation.rules.numeric.NumericValidationRuleBuilder;
import org.rulii.validation.rules.startswith.StartsWithValidationRule;
import org.rulii.validation.rules.unique.UniqueElementsValidationRule;
import org.rulii.validation.rules.uppercase.UpperCaseValidationRule;
import org.rulii.validation.rules.url.UrlValidationRule;
import org.rulii.validation.rules.uuid.UuidValidationRule;
import org.rulii.validation.beans.constraints.Alpha;
import org.rulii.validation.beans.constraints.AlphaNumeric;
import org.rulii.validation.beans.constraints.Ascii;
import org.rulii.validation.beans.constraints.CodePointLength;
import org.rulii.validation.beans.constraints.Decimal;
import org.rulii.validation.beans.constraints.EAN;
import org.rulii.validation.beans.constraints.EndsWith;
import org.rulii.validation.beans.constraints.ISBN;
import org.rulii.validation.beans.constraints.In;
import org.rulii.validation.beans.constraints.LowerCase;
import org.rulii.validation.beans.constraints.LuhnCheck;
import org.rulii.validation.beans.constraints.Mod10Check;
import org.rulii.validation.beans.constraints.Mod11Check;
import org.rulii.validation.beans.constraints.Normalized;
import org.rulii.validation.beans.constraints.Numeric;
import org.rulii.validation.beans.constraints.StartsWith;
import org.rulii.validation.beans.constraints.URL;
import org.rulii.validation.beans.constraints.UUID;
import org.rulii.validation.beans.constraints.UniqueElements;
import org.rulii.validation.beans.constraints.UpperCase;
import org.rulii.validation.beans.constraints.time.DurationMax;
import org.rulii.validation.beans.constraints.time.DurationMin;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The mappers of this library's own constraints ({@code org.rulii.validation.beans.constraints}): the
 * equivalents of the reference implementation's constraints onto the rulii 2.2 rules, and the rulii-only constraints onto core's
 * rules. {@code @Length}, {@code @Range} and {@code @CreditCardNumber} are pure compositions and need
 * no mapper. Registered after the standard mappers by {@code ConstraintMapperRegistry.builder()}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class LibraryConstraintMappers {

    private static final String ANY_REGEXP = ".*";

    private static final List<ConstraintMapper<?>> MAPPERS = List.of(
            StandardConstraintMappers.of(URL.class, UrlValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.url(t.value())).protocol(a.protocol()).host(a.host()).port(a.port())
                            .regexp(ANY_REGEXP.equals(a.regexp()) ? null : a.regexp()).flags(StandardConstraintMappers.flags(a.flags())))),
            StandardConstraintMappers.of(UUID.class, UuidValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.uuid(t.value())).allowEmpty(a.allowEmpty()).allowNil(a.allowNil()).versions(a.version())
                            .variants(a.variant()).letterCase(UuidValidationRule.LetterCase.valueOf(a.letterCase().name())))),
            StandardConstraintMappers.of(UniqueElements.class, UniqueElementsValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.uniqueElements(t.value())))),
            StandardConstraintMappers.of(Normalized.class, NormalizedValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.normalized(t.value())).form(a.form()))),
            StandardConstraintMappers.of(CodePointLength.class, CodePointLengthValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.codePointLength(t.value(), a.min(), a.max())).normalization(a.normalizationStrategy().getForm()))),
            StandardConstraintMappers.of(LuhnCheck.class, LuhnCheckValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.luhnCheck(t.value())).startIndex(a.startIndex()).endIndex(a.endIndex())
                            .checkDigitIndex(a.checkDigitIndex()).ignoreNonDigitCharacters(a.ignoreNonDigitCharacters()))),
            StandardConstraintMappers.of(Mod10Check.class, Mod10CheckValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.mod10Check(t.value())).multiplier(a.multiplier()).weight(a.weight()).startIndex(a.startIndex())
                            .endIndex(a.endIndex()).checkDigitIndex(a.checkDigitIndex()).ignoreNonDigitCharacters(a.ignoreNonDigitCharacters()))),
            StandardConstraintMappers.of(Mod11Check.class, Mod11CheckValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.mod11Check(t.value())).threshold(a.threshold()).startIndex(a.startIndex()).endIndex(a.endIndex())
                            .checkDigitIndex(a.checkDigitIndex()).ignoreNonDigitCharacters(a.ignoreNonDigitCharacters())
                            .treatCheck10As(a.treatCheck10As()).treatCheck11As(a.treatCheck11As())
                            .processingDirection(Mod11CheckValidationRule.ProcessingDirection.valueOf(a.processingDirection().name())))),
            StandardConstraintMappers.of(EAN.class, EanValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.ean(t.value())).type(EanValidationRule.Type.valueOf(a.type().name())))),
            StandardConstraintMappers.of(ISBN.class, IsbnValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.isbn(t.value())).type(IsbnValidationRule.Type.valueOf(a.type().name())))),
            StandardConstraintMappers.of(DurationMin.class, DurationMinValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.durationMin(t.value(), duration(a.days(), a.hours(), a.minutes(), a.seconds(), a.millis(), a.nanos())))
                            .inclusive(a.inclusive()))),
            StandardConstraintMappers.of(DurationMax.class, DurationMaxValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.durationMax(t.value(), duration(a.days(), a.hours(), a.minutes(), a.seconds(), a.millis(), a.nanos())))
                            .inclusive(a.inclusive()))),
            StandardConstraintMappers.of(Alpha.class, AlphaValidationRule.SUPPORTED_TYPES, (a, t) -> {
                AlphaValidationRuleBuilder builder = t.configure(Validators.alpha(t.value()));
                if (a.allowSpace()) builder.allowSpace();
                return StandardConstraintMappers.single(builder);
            }),
            StandardConstraintMappers.of(AlphaNumeric.class, AlphaNumericValidationRule.SUPPORTED_TYPES, (a, t) -> {
                AlphaNumericValidationRuleBuilder builder = t.configure(Validators.alphaNumeric(t.value()));
                if (a.allowSpace()) builder.allowSpace();
                return StandardConstraintMappers.single(builder);
            }),
            StandardConstraintMappers.of(Numeric.class, NumericValidationRule.SUPPORTED_TYPES, (a, t) -> {
                NumericValidationRuleBuilder builder = t.configure(Validators.numeric(t.value()));
                if (a.allowSpace()) builder.allowSpace();
                return StandardConstraintMappers.single(builder);
            }),
            StandardConstraintMappers.of(Decimal.class, DecimalValidationRule.SUPPORTED_TYPES, (a, t) -> {
                DecimalValidationRuleBuilder builder = t.configure(Validators.decimal(t.value()));
                if (a.allowSpace()) builder.allowSpace();
                return StandardConstraintMappers.single(builder);
            }),
            StandardConstraintMappers.of(Ascii.class, AsciiValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.ascii(t.value())))),
            StandardConstraintMappers.of(LowerCase.class, LowerCaseValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.lowerCase(t.value())))),
            StandardConstraintMappers.of(UpperCase.class, UpperCaseValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.upperCase(t.value())))),
            StandardConstraintMappers.of(StartsWith.class, StartsWithValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.startsWith(t.value(), a.value())))),
            StandardConstraintMappers.of(EndsWith.class, EndsWithValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.endsWith(t.value(), a.value())))),
            StandardConstraintMappers.of(In.class, InValidationRule.SUPPORTED_TYPES, (a, t) -> StandardConstraintMappers.single(
                    t.configure(Validators.in(t.value(), List.of(a.value())))))
    );

    private LibraryConstraintMappers() {
        super();
    }

    /**
     * The mappers, in registration order.
     *
     * @return the mappers; unmodifiable.
     */
    public static List<ConstraintMapper<?>> all() {
        return MAPPERS;
    }

    /**
     * The sum of the components of a {@code @DurationMin} or {@code @DurationMax}.
     */
    static Duration duration(long days, long hours, long minutes, long seconds, long millis, long nanos) {
        return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds).plusMillis(millis).plusNanos(nanos);
    }
}
