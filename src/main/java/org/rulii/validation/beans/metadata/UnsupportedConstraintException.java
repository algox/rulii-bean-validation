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
package org.rulii.validation.beans.metadata;

import org.rulii.validation.beans.BeanValidationException;

import java.lang.annotation.Annotation;

/**
 * Thrown at metadata-read time when a constraint annotation has no registered
 * ConstraintMapper and is not a composition of supported constraints. Silently skipping
 * it would validate nothing; register a mapper for the annotation type when building
 * the validator instead.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class UnsupportedConstraintException extends BeanValidationException {

    private final Class<?> beanClass;
    private final String property;
    private final Class<? extends Annotation> annotationType;

    /**
     * Creates a new UnsupportedConstraintException.
     *
     * @param beanClass the class being read.
     * @param property the constrained property; null for a class-level constraint.
     * @param annotationType the unmapped constraint annotation type.
     */
    public UnsupportedConstraintException(Class<?> beanClass, String property, Class<? extends Annotation> annotationType) {
        super("Constraint [@" + annotationType.getSimpleName() + "] on [" + beanClass.getName()
                + (property != null ? "." + property : "") + "] has no registered ConstraintMapper and is not a "
                + "composition of supported constraints. Register a mapper for [" + annotationType.getName()
                + "] when building the validator.");
        this.beanClass = beanClass;
        this.property = property;
        this.annotationType = annotationType;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public String getProperty() {
        return property;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }
}
