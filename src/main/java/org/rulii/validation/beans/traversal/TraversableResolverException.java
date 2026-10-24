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
package org.rulii.validation.beans.traversal;

import jakarta.validation.TraversableResolver;
import org.rulii.validation.beans.BeanValidationException;

/**
 * A configured {@link TraversableResolver} failed while being asked about a property.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class TraversableResolverException extends BeanValidationException {

    private static final long serialVersionUID = 0L;

    private final String propertyName;

    /**
     * Creates the exception.
     *
     * @param method the resolver method that failed: {@code isReachable} or {@code isCascadable}.
     * @param bean the object whose property was asked about.
     * @param propertyName the property.
     * @param resolver the resolver.
     * @param cause what it threw.
     */
    public TraversableResolverException(String method, Object bean, String propertyName, TraversableResolver resolver, Throwable cause) {
        super("traversable resolver [" + resolver + "] failed in " + method + " for property [" + propertyName + "] of ["
                + (bean != null ? bean.getClass().getName() : null) + "]", cause);
        this.propertyName = propertyName;
    }

    /**
     * The property asked about.
     *
     * @return the property name.
     */
    public String getPropertyName() {
        return propertyName;
    }
}
