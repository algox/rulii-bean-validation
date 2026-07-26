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
package org.rulii.validation.beans;

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
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.mapper.AssertFalseConstraintMapper;
import org.rulii.validation.beans.mapper.AssertTrueConstraintMapper;
import org.rulii.validation.beans.mapper.DecimalMaxConstraintMapper;
import org.rulii.validation.beans.mapper.DecimalMinConstraintMapper;
import org.rulii.validation.beans.mapper.DigitsConstraintMapper;
import org.rulii.validation.beans.mapper.EmailConstraintMapper;
import org.rulii.validation.beans.mapper.FutureConstraintMapper;
import org.rulii.validation.beans.mapper.FutureOrPresentConstraintMapper;
import org.rulii.validation.beans.mapper.MaxConstraintMapper;
import org.rulii.validation.beans.mapper.MinConstraintMapper;
import org.rulii.validation.beans.mapper.NegativeConstraintMapper;
import org.rulii.validation.beans.mapper.NegativeOrZeroConstraintMapper;
import org.rulii.validation.beans.mapper.NotBlankConstraintMapper;
import org.rulii.validation.beans.mapper.NotEmptyConstraintMapper;
import org.rulii.validation.beans.mapper.NotNullConstraintMapper;
import org.rulii.validation.beans.mapper.NullConstraintMapper;
import org.rulii.validation.beans.mapper.PastConstraintMapper;
import org.rulii.validation.beans.mapper.PastOrPresentConstraintMapper;
import org.rulii.validation.beans.mapper.PatternConstraintMapper;
import org.rulii.validation.beans.mapper.PositiveConstraintMapper;
import org.rulii.validation.beans.mapper.PositiveOrZeroConstraintMapper;
import org.rulii.validation.beans.mapper.SizeConstraintMapper;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for {@link ConstraintMapperRegistry}. Registration order is preserved.
 * {@link #standard()} registers the mappers of the 22 standard jakarta constraints;
 * custom constraints register through the same {@link #register} mechanism.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintMapperRegistryBuilder {

    private final Map<Class<? extends Annotation>, ConstraintMapper> mappers = new LinkedHashMap<>();

    ConstraintMapperRegistryBuilder() {
        super();
    }

    /**
     * Registers a mapper for the given constraint annotation type. Registering a type
     * twice replaces the earlier registration.
     *
     * @param annotationType the constraint annotation type.
     * @param mapper the mapper for the annotation type.
     * @return this builder for fluent chaining.
     */
    public ConstraintMapperRegistryBuilder register(Class<? extends Annotation> annotationType, ConstraintMapper mapper) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(mapper, "mapper cannot be null.");
        mappers.put(annotationType, mapper);
        return this;
    }

    /**
     * Registers the mappers of the 22 standard jakarta constraints. Individual
     * registrations can be overridden by a subsequent {@link #register} of the same
     * annotation type.
     *
     * @return this builder for fluent chaining.
     */
    public ConstraintMapperRegistryBuilder standard() {
        register(AssertFalse.class, new AssertFalseConstraintMapper());
        register(AssertTrue.class, new AssertTrueConstraintMapper());
        register(DecimalMax.class, new DecimalMaxConstraintMapper());
        register(DecimalMin.class, new DecimalMinConstraintMapper());
        register(Digits.class, new DigitsConstraintMapper());
        register(Email.class, new EmailConstraintMapper());
        register(Future.class, new FutureConstraintMapper());
        register(FutureOrPresent.class, new FutureOrPresentConstraintMapper());
        register(Max.class, new MaxConstraintMapper());
        register(Min.class, new MinConstraintMapper());
        register(Negative.class, new NegativeConstraintMapper());
        register(NegativeOrZero.class, new NegativeOrZeroConstraintMapper());
        register(NotBlank.class, new NotBlankConstraintMapper());
        register(NotEmpty.class, new NotEmptyConstraintMapper());
        register(NotNull.class, new NotNullConstraintMapper());
        register(Null.class, new NullConstraintMapper());
        register(Past.class, new PastConstraintMapper());
        register(PastOrPresent.class, new PastOrPresentConstraintMapper());
        register(Pattern.class, new PatternConstraintMapper());
        register(Positive.class, new PositiveConstraintMapper());
        register(PositiveOrZero.class, new PositiveOrZeroConstraintMapper());
        register(Size.class, new SizeConstraintMapper());
        return this;
    }

    /**
     * Builds the immutable registry.
     *
     * @return a new {@link ConstraintMapperRegistry}.
     */
    public ConstraintMapperRegistry build() {
        return new ConstraintMapperRegistry(mappers);
    }
}
