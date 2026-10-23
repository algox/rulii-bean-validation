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
package org.rulii.validation.beans.constraints;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated number (or its character sequence form) must be within the given bounds (both inclusive). A composition of {@code @Min} and {@code @Max} reported as a single violation, as the reference implementation's {@code @Range}.
 *
 * <p>Error code and message key: {@code org.rulii.validation.beans.constraints.Range.message}. This constraint is
 * materialized by rulii bean validation and is not portable to another Bean Validation provider.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Range.List.class)
@Documented
@Constraint(validatedBy = {})
@Min(0)
@Max(Long.MAX_VALUE)
@ReportAsSingleViolation
public @interface Range {

    String message() default "{org.rulii.validation.beans.constraints.Range.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * The minimum value; 0 by default.
     */
    @OverridesAttribute(constraint = Min.class, name = "value")
    long min() default 0;

    /**
     * The maximum value; unbounded by default.
     */
    @OverridesAttribute(constraint = Max.class, name = "value")
    long max() default Long.MAX_VALUE;

    /**
     * Several {@code @Range} constraints on the same element.
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        Range[] value();
    }
}
