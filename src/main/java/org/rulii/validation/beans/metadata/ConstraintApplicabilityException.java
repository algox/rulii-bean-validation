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
import java.lang.reflect.Type;
import java.util.List;

/**
 * Thrown at metadata-read time when a constraint is placed on a declared type it cannot
 * apply to (e.g. {@code @Email} on {@code Integer}). Applicability is statically
 * checkable against the declared type, so a typo'd annotation fails at read time rather
 * than silently validating nothing.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintApplicabilityException extends BeanValidationException {

    private final Class<?> beanClass;
    private final String property;
    private final Class<? extends Annotation> annotationType;
    private final Type declaredType;
    private final List<Class<?>> supportedTypes;

    /**
     * Creates a new ConstraintApplicabilityException.
     *
     * @param beanClass the class being read.
     * @param property the constrained property; null for a class-level constraint.
     * @param annotationType the constraint annotation type.
     * @param declaredType the declared type the constraint was placed on.
     * @param supportedTypes the types the constraint supports.
     */
    public ConstraintApplicabilityException(Class<?> beanClass, String property, Class<? extends Annotation> annotationType,
                                            Type declaredType, List<Class<?>> supportedTypes) {
        super("Constraint [@" + annotationType.getSimpleName() + "] on [" + beanClass.getName()
                + (property != null ? "." + property : "") + "] cannot apply to declared type [" + declaredType
                + "]. Supported types " + supportedTypes + ".");
        this.beanClass = beanClass;
        this.property = property;
        this.annotationType = annotationType;
        this.declaredType = declaredType;
        this.supportedTypes = supportedTypes;
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

    public Type getDeclaredType() {
        return declaredType;
    }

    public List<Class<?>> getSupportedTypes() {
        return supportedTypes;
    }
}
