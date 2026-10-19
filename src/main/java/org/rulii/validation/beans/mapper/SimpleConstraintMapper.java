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

import org.rulii.lib.spring.util.Assert;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * A {@link ConstraintMapper} defined by its values rather than by a class: the constraint annotation
 * type and the supported value types (typically the {@code SUPPORTED_TYPES} of the core rule the
 * constraint materializes to). The standard mappers are built this way, and so can a custom one.
 *
 * @param <A> the constraint annotation type.
 * @author Max Arulananthan
 * @since 1.0
 */
public final class SimpleConstraintMapper<A extends Annotation> implements ConstraintMapper<A> {

    private final Class<A> annotationType;
    private final List<Class<?>> supportedTypes;

    /**
     * Creates the mapper.
     *
     * @param annotationType the constraint annotation type.
     * @param supportedTypes the supported value types; empty for no restriction.
     */
    public SimpleConstraintMapper(Class<A> annotationType, List<Class<?>> supportedTypes) {
        super();
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(supportedTypes, "supportedTypes cannot be null.");
        this.annotationType = annotationType;
        this.supportedTypes = List.copyOf(supportedTypes);
    }

    @Override
    public Class<A> getAnnotationType() {
        return annotationType;
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public String toString() {
        return "SimpleConstraintMapper{" + annotationType.getSimpleName() + " -> " + supportedTypes + "}";
    }
}
