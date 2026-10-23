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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated character sequence must pass a modulo 11 check digit test: the digits weighted 2, 3, 4, ... in the processing direction (starting over at 2 past {@code threshold}), the check value eleven minus the sum modulo eleven, ten and eleven written as {@code treatCheck10As} and {@code treatCheck11As}.
 *
 * <p>Error code and message key: {@code org.rulii.validation.beans.constraints.Mod11Check.message}. This constraint is
 * materialized by rulii bean validation and is not portable to another Bean Validation provider.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Mod11Check.List.class)
@Documented
@Constraint(validatedBy = {})
public @interface Mod11Check {

    String message() default "{org.rulii.validation.beans.constraints.Mod11Check.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * The largest weight before starting over at 2; no limit by default.
     */
    int threshold() default Integer.MAX_VALUE;

    /**
     * Where the digits start (inclusive); 0 by default.
     */
    int startIndex() default 0;

    /**
     * Where the digits end (exclusive); the end of the value by default.
     */
    int endIndex() default Integer.MAX_VALUE;

    /**
     * Where the check digit is; the last character of the range by default.
     */
    int checkDigitIndex() default -1;

    /**
     * Whether non-digit characters among the digits are dropped or fail the check (false, the default).
     */
    boolean ignoreNonDigitCharacters() default false;

    /**
     * The character standing for a check value of 10; {@code X} by default.
     */
    char treatCheck10As() default 'X';

    /**
     * The character standing for a check value of 11; {@code 0} by default.
     */
    char treatCheck11As() default '0';

    /**
     * The direction the digits are weighted in; right to left by default.
     */
    ProcessingDirection processingDirection() default ProcessingDirection.RIGHT_TO_LEFT;

    /**
     * The direction the digits are weighted in.
     */
    enum ProcessingDirection {
        /** The rightmost digit gets weight 2. */
        RIGHT_TO_LEFT,
        /** The leftmost digit gets weight 2. */
        LEFT_TO_RIGHT
    }

    /**
     * Several {@code @Mod11Check} constraints on the same element.
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        Mod11Check[] value();
    }
}
