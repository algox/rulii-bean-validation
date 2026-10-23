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
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a constraint is declared on a value whose static type it can never apply to, e.g.
 * {@code @Size} on an {@code int} (see {@link ConstraintMapper#isApplicable(Class)}). Raised when the
 * bean class is read, not when a value is validated. Same exception family the reference implementation uses
 * when no validator fits the validated type.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintNotApplicableException extends UnexpectedTypeException {

    private final Class<? extends Annotation> annotationType;
    private final String location;
    private final Type type;
    private final List<Class<?>> supportedTypes;

    /**
     * Creates the exception.
     *
     * @param annotationType the constraint annotation type.
     * @param location where the constraint is declared, e.g. {@code com.acme.Order.total}.
     * @param type the (static) type of the constrained value.
     * @param supportedTypes the types the constraint supports.
     */
    public ConstraintNotApplicableException(Class<? extends Annotation> annotationType, String location,
                                            Type type, List<Class<?>> supportedTypes) {
        super("constraint [" + annotationType.getName() + "] declared on [" + location + "] cannot apply to type ["
                + type.getTypeName() + "]; supported types: ["
                + supportedTypes.stream().map(Class::getTypeName).collect(Collectors.joining(", ")) + "]");
        this.annotationType = annotationType;
        this.location = location;
        this.type = type;
        this.supportedTypes = List.copyOf(supportedTypes);
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    public String getLocation() {
        return location;
    }

    public Type getType() {
        return type;
    }

    public List<Class<?>> getSupportedTypes() {
        return supportedTypes;
    }
}
