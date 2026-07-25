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
import java.lang.reflect.Method;

/**
 * Thrown at metadata-read time when a validation annotation is placed on a method that
 * is not a getter. Constraints are honored on fields, getters, and record components;
 * a constraint on any other method would silently validate nothing.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class InvalidConstraintPlacementException extends BeanValidationException {

    private final Class<?> beanClass;
    private final Method method;
    private final Class<? extends Annotation> annotationType;

    /**
     * Creates a new InvalidConstraintPlacementException.
     *
     * @param beanClass the class being read.
     * @param method the non-getter method carrying the annotation.
     * @param annotationType the annotation found on the method.
     */
    public InvalidConstraintPlacementException(Class<?> beanClass, Method method, Class<? extends Annotation> annotationType) {
        super("Validation annotation [@" + annotationType.getSimpleName() + "] is not allowed on non-getter method ["
                + method.getName() + "] of class [" + beanClass.getName() + "]. Constraints are honored on fields, "
                + "getters, and record components only.");
        this.beanClass = beanClass;
        this.method = method;
        this.annotationType = annotationType;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public Method getMethod() {
        return method;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }
}
