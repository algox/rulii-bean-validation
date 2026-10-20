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

import org.rulii.model.UnrulyException;

import java.lang.annotation.Annotation;

/**
 * Thrown when a {@link ConstraintMapper} misbehaves while materializing a constraint: it returns no
 * rules, a null rule, a rule whose name is already taken in the bean class, or throws.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintMapperException extends UnrulyException {

    private final Class<? extends Annotation> annotationType;
    private final String location;

    public ConstraintMapperException(Class<? extends Annotation> annotationType, String location, String message) {
        this(annotationType, location, message, null);
    }

    public ConstraintMapperException(Class<? extends Annotation> annotationType, String location, String message, Throwable cause) {
        super("mapper for constraint [" + annotationType.getName() + "] on [" + location + "]: " + message, cause);
        this.annotationType = annotationType;
        this.location = location;
    }

    /**
     * The constraint annotation type being mapped.
     *
     * @return the type.
     */
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    /**
     * Where the constraint is declared.
     *
     * @return the location description.
     */
    public String getLocation() {
        return location;
    }
}
