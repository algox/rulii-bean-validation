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
package org.rulii.validation.beans;

import org.rulii.lib.spring.util.Assert;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for {@link ConstraintMapperRegistry}. Registration order is preserved.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintMapperRegistryBuilder {

    private final Map<Class<? extends Annotation>, ConstraintMapper> mappers = new LinkedHashMap<>();

    ConstraintMapperRegistryBuilder() {
        super();
    }

    /**
     * Registers a mapper for the given constraint annotation type. Registering a type
     * twice replaces the earlier registration.
     *
     * @param annotationType the constraint annotation type.
     * @param mapper the mapper for the annotation type.
     * @return this builder for fluent chaining.
     */
    public ConstraintMapperRegistryBuilder register(Class<? extends Annotation> annotationType, ConstraintMapper mapper) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(mapper, "mapper cannot be null.");
        mappers.put(annotationType, mapper);
        return this;
    }

    /**
     * Builds the immutable registry.
     *
     * @return a new {@link ConstraintMapperRegistry}.
     */
    public ConstraintMapperRegistry build() {
        return new ConstraintMapperRegistry(mappers);
    }
}
