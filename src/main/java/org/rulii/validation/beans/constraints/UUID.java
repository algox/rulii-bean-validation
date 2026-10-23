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
 * The annotated character sequence must be a UUID in its canonical textual form: 36 characters, hex digits in groups of 8-4-4-4-12 separated by dashes.
 *
 * <p>Error code and message key: {@code org.rulii.validation.beans.constraints.UUID.message}. This constraint is
 * materialized by rulii bean validation and is not portable to another Bean Validation provider.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(UUID.List.class)
@Documented
@Constraint(validatedBy = {})
public @interface UUID {

    String message() default "{org.rulii.validation.beans.constraints.UUID.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Whether the empty string passes; false by default.
     */
    boolean allowEmpty() default false;

    /**
     * Whether the nil UUID (all zeros) passes; true by default.
     */
    boolean allowNil() default true;

    /**
     * The accepted versions; 1 to 5 by default.
     */
    int[] version() default {1, 2, 3, 4, 5};

    /**
     * The accepted variants; 0 to 2 by default.
     */
    int[] variant() default {0, 1, 2};

    /**
     * The letter case of the hex digits; lower case by default.
     */
    LetterCase letterCase() default LetterCase.LOWER_CASE;

    /**
     * The letter case the hex digits must be in.
     */
    enum LetterCase {
        /** Only lower case is valid. */
        LOWER_CASE,
        /** Only upper case is valid. */
        UPPER_CASE,
        /** Either case is valid. */
        INSENSITIVE
    }

    /**
     * Several {@code @UUID} constraints on the same element.
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        UUID[] value();
    }
}
