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

import jakarta.validation.UnexpectedTypeException;

import java.lang.annotation.Annotation;

/**
 * Thrown when a bean declares a constraint that has no registered {@link ConstraintMapper}, names no
 * {@code jakarta.validation.ConstraintValidator} in its {@code @Constraint(validatedBy)}, and is not a
 * pure composition of mapped constraints. Raised when the bean class is read, not when a value is
 * validated. Same exception family the reference implementation uses when no validator can be found.
 *
 * <p>The fix is either: register a mapper for the constraint,
 * {@code ConstraintMapperRegistry.builder().mapper(new MyConstraintMapper()).build()}, or name a
 * validator class in the annotation's {@code @Constraint(validatedBy)}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class NoConstraintMapperException extends UnexpectedTypeException {

    private final Class<? extends Annotation> annotationType;
    private final String location;

    /**
     * Creates the exception.
     *
     * @param annotationType the unmapped constraint annotation type.
     * @param location where the constraint is declared, e.g. {@code com.acme.Order.total} or
     *                 {@code com.acme.Order.tags List<0>}.
     */
    public NoConstraintMapperException(Class<? extends Annotation> annotationType, String location) {
        super("no constraint mapper is registered for constraint [" + annotationType.getName() + "] declared on ["
                + location + "], and the constraint declares no validators. Either register a "
                + ConstraintMapper.class.getName() + " for it: ConstraintMapperRegistry.builder().mapper(new YourConstraintMapper()).build(),"
                + " or name a jakarta.validation.ConstraintValidator in its @Constraint(validatedBy).");
        this.annotationType = annotationType;
        this.location = location;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    public String getLocation() {
        return location;
    }
}
