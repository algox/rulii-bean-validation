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
 * Thrown when a bean declares a constraint that has no registered {@link ConstraintMapper} and is
 * not a pure composition of mapped constraints. Raised when the bean class is read, not when a value
 * is validated. Same exception family Hibernate Validator uses when no validator can be found.
 *
 * <p>rulii bean validation does not execute {@code jakarta.validation.ConstraintValidator}
 * implementations; the fix is to register a mapper for the constraint:
 * {@code ConstraintMapperRegistry.builder().mapper(new MyConstraintMapper()).build()}.</p>
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
                + location + "]. rulii bean validation does not execute ConstraintValidator implementations;"
                + " register a " + ConstraintMapper.class.getName() + " for the constraint:"
                + " ConstraintMapperRegistry.builder().mapper(new YourConstraintMapper()).build()");
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
