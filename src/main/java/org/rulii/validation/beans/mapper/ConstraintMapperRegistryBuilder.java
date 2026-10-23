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

import jakarta.validation.ConstraintValidatorFactory;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for a {@link ConstraintMapperRegistry}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintMapperRegistryBuilder {

    private final boolean registerDefaults;
    private final List<ConstraintMapper<?>> mappers = new ArrayList<>();
    private ConstraintValidatorFactory constraintValidatorFactory;

    private ConstraintMapperRegistryBuilder(boolean registerDefaults) {
        super();
        this.registerDefaults = registerDefaults;
    }

    /**
     * Builder for an empty registry.
     *
     * @return the builder.
     */
    public static ConstraintMapperRegistryBuilder empty() {
        return new ConstraintMapperRegistryBuilder(false);
    }

    /**
     * Builder for a registry pre-populated with the mappers for the standard
     * {@code jakarta.validation.constraints}.
     *
     * @return the builder.
     */
    public static ConstraintMapperRegistryBuilder standard() {
        return new ConstraintMapperRegistryBuilder(true);
    }

    /**
     * Registers a mapper, replacing any mapper for the same constraint annotation type.
     *
     * @param mapper the mapper.
     * @return this builder.
     */
    public ConstraintMapperRegistryBuilder mapper(ConstraintMapper<?> mapper) {
        Assert.notNull(mapper, "mapper cannot be null.");
        mappers.add(mapper);
        return this;
    }

    /**
     * The factory validator-backed constraints (those naming {@code ConstraintValidator} classes in
     * their {@code @Constraint(validatedBy)} and having no registered mapper) instantiate their
     * validators through. The {@link DefaultConstraintValidatorFactory} when not set.
     *
     * @param constraintValidatorFactory the factory.
     * @return this builder.
     */
    public ConstraintMapperRegistryBuilder constraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
        Assert.notNull(constraintValidatorFactory, "constraintValidatorFactory cannot be null.");
        this.constraintValidatorFactory = constraintValidatorFactory;
        return this;
    }

    public ConstraintMapperRegistry build() {
        ConstraintMapperRegistry result = new DefaultConstraintMapperRegistry(registerDefaults,
                constraintValidatorFactory != null ? constraintValidatorFactory : new DefaultConstraintValidatorFactory());

        for (ConstraintMapper<?> mapper : mappers) {
            result.register(mapper);
        }

        return result;
    }
}
