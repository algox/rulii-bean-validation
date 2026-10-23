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
package org.rulii.validation.beans.constraints.time;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated {@code Duration} must be at least the sum of the given components (inclusive by default).
 *
 * <p>Error code and message key: {@code org.rulii.validation.beans.constraints.time.DurationMin.message}. This constraint is
 * materialized by rulii bean validation and is not portable to another Bean Validation provider.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DurationMin.List.class)
@Documented
@Constraint(validatedBy = {})
public @interface DurationMin {

    String message() default "{org.rulii.validation.beans.constraints.time.DurationMin.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    long days() default 0;

    long hours() default 0;

    long minutes() default 0;

    long seconds() default 0;

    long millis() default 0;

    long nanos() default 0;

    /**
     * Whether the minimum itself passes; true by default.
     */
    boolean inclusive() default true;

    /**
     * Several {@code @DurationMin} constraints on the same element.
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        DurationMin[] value();
    }
}
