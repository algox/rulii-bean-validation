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

/**
 * Thrown at metadata-read time when {@code @ConvertGroup} is encountered. Group
 * conversion during cascade is not supported; silently ignoring it would cascade the
 * wrong groups into the child object.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class GroupConversionNotSupportedException extends BeanValidationException {

    private final Class<?> beanClass;
    private final String property;

    /**
     * Creates a new GroupConversionNotSupportedException.
     *
     * @param beanClass the class being read.
     * @param property the property carrying the group conversion.
     */
    public GroupConversionNotSupportedException(Class<?> beanClass, String property) {
        super("[@ConvertGroup] on [" + beanClass.getName() + "." + property + "] is not supported. Silently "
                + "ignoring it would cascade the wrong validation groups into the child object.");
        this.beanClass = beanClass;
        this.property = property;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public String getProperty() {
        return property;
    }
}
