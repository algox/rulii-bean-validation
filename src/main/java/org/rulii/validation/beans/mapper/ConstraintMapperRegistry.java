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

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Registry of {@link ConstraintMapper constraint mappers}, one per constraint annotation type.
 * Registering a mapper for a type that already has one replaces it, which is how a standard
 * mapping is overridden.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface ConstraintMapperRegistry {

    /**
     * Builder for a registry pre-populated with the mappers for the standard
     * {@code jakarta.validation.constraints}.
     *
     * @return the builder.
     */
    static ConstraintMapperRegistryBuilder builder() {
        return builder(true);
    }

    /**
     * Builder for a registry.
     *
     * @param registerDefaults true to pre-populate the standard mappers, false for an empty registry.
     * @return the builder.
     */
    static ConstraintMapperRegistryBuilder builder(boolean registerDefaults) {
        return registerDefaults ? ConstraintMapperRegistryBuilder.standard() : ConstraintMapperRegistryBuilder.empty();
    }

    /**
     * Registers a mapper, replacing any mapper registered for the same annotation type.
     *
     * @param mapper the mapper.
     * @throws IllegalArgumentException if the mapper or its annotation type is null, or the annotation
     *         type is not a constraint (not meta-annotated with {@code jakarta.validation.Constraint}).
     */
    void register(ConstraintMapper<?> mapper);

    /**
     * Finds the mapper registered for the given constraint annotation type.
     *
     * @param annotationType the constraint annotation type.
     * @param <A> the annotation type.
     * @return the mapper, or null if none is registered.
     */
    <A extends Annotation> ConstraintMapper<A> find(Class<A> annotationType);

    /**
     * All registered mappers, in registration order.
     *
     * @return the mappers; unmodifiable snapshot.
     */
    List<ConstraintMapper<?>> getMappers();
}
