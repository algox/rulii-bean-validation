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
 * Thrown at metadata-read time when a class carries {@code @GroupSequence}.
 * Group sequences (which redefine the Default group) are not supported; silently
 * ignoring one would distort which constraints run.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class GroupSequenceNotSupportedException extends BeanValidationException {

    private final Class<?> beanClass;

    /**
     * Creates a new GroupSequenceNotSupportedException.
     *
     * @param beanClass the class carrying the group sequence.
     */
    public GroupSequenceNotSupportedException(Class<?> beanClass) {
        super("[@GroupSequence] on [" + beanClass.getName() + "] is not supported. Plain validation groups are "
                + "honored exactly; group sequences would distort validation behavior if silently ignored.");
        this.beanClass = beanClass;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }
}
