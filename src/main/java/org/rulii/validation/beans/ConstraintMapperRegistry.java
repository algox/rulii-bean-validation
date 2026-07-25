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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable registry of {@link ConstraintMapper}s keyed by constraint annotation type.
 * Built once (see {@link #builder()}) and consulted at metadata-read time for
 * applicability checks and unmapped-constraint detection, and at materialization time
 * for rule creation.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintMapperRegistry {

    private final Map<Class<? extends Annotation>, ConstraintMapper> mappers;

    ConstraintMapperRegistry(Map<Class<? extends Annotation>, ConstraintMapper> mappers) {
        super();
        Assert.notNull(mappers, "mappers cannot be null.");
        this.mappers = Collections.unmodifiableMap(new LinkedHashMap<>(mappers));
    }

    /**
     * Creates a new builder to construct a ConstraintMapperRegistry.
     *
     * @return a new {@link ConstraintMapperRegistryBuilder}.
     */
    public static ConstraintMapperRegistryBuilder builder() {
        return new ConstraintMapperRegistryBuilder();
    }

    /**
     * Determines whether a mapper is registered for the given constraint annotation type.
     *
     * @param annotationType the constraint annotation type.
     * @return true if a mapper is registered; false otherwise.
     */
    public boolean isRegistered(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        return mappers.containsKey(annotationType);
    }

    /**
     * Retrieves the mapper registered for the given constraint annotation type.
     *
     * @param annotationType the constraint annotation type.
     * @return the registered mapper, or null if none is registered.
     */
    public ConstraintMapper getMapper(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        return mappers.get(annotationType);
    }

    /**
     * Returns all registered constraint annotation types.
     *
     * @return an unmodifiable set of registered annotation types.
     */
    public Set<Class<? extends Annotation>> getRegisteredTypes() {
        return mappers.keySet();
    }

    @Override
    public String toString() {
        return "ConstraintMapperRegistry{types=" + mappers.keySet() + '}';
    }
}
