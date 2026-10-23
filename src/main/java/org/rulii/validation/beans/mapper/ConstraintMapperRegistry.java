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
 * <p>A constraint with no registered mapper that names {@code jakarta.validation.ConstraintValidator}
 * classes in its {@code @Constraint(validatedBy)} is mapped through them: {@link #find} answers with a
 * {@link org.rulii.validation.beans.validator.ConstraintValidatorMapper} synthesized for it, whose
 * validators are created through the registry's {@code ConstraintValidatorFactory}. A registered
 * mapper always takes precedence over the declared validators.</p>
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
     * Finds the mapper for the given constraint annotation type: the registered one, else the one
     * synthesized from the validators the constraint declares.
     *
     * @param annotationType the constraint annotation type.
     * @param <A> the annotation type.
     * @return the mapper, or null if none is registered and the constraint declares no validators.
     */
    <A extends Annotation> ConstraintMapper<A> find(Class<A> annotationType);

    /**
     * All registered mappers, in registration order; synthesized validator mappers are not among them.
     *
     * @return the mappers; unmodifiable snapshot.
     */
    List<ConstraintMapper<?>> getMappers();

    /**
     * Hands the constraint validator instances created through this registry back to their factory.
     * Nothing to do for a registry without validator-backed constraints.
     */
    default void release() {
        // Nothing to release.
    }
}
