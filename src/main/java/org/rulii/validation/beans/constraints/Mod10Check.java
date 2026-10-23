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
 * The annotated character sequence must pass a modulo 10 check digit test, the odd positions (counted from the right) weighted by {@code multiplier} and the even ones by {@code weight}, over the digits between {@code startIndex} and {@code endIndex}.
 *
 * <p>Error code and message key: {@code org.rulii.validation.beans.constraints.Mod10Check.message}. This constraint is
 * materialized by rulii bean validation and is not portable to another Bean Validation provider.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Mod10Check.List.class)
@Documented
@Constraint(validatedBy = {})
public @interface Mod10Check {

    String message() default "{org.rulii.validation.beans.constraints.Mod10Check.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * The multiplier of the odd positions, counted from the right; 3 by default.
     */
    int multiplier() default 3;

    /**
     * The multiplier of the even positions, counted from the right; 1 by default.
     */
    int weight() default 1;

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
     * Whether non-digit characters among the digits are dropped (true, the default) or fail the check.
     */
    boolean ignoreNonDigitCharacters() default true;

    /**
     * Several {@code @Mod10Check} constraints on the same element.
     */
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        Mod10Check[] value();
    }
}
