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

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidatorFactory;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.validator.ConstraintValidatorMapper;
import org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link ConstraintMapperRegistry}: one mapper per constraint annotation type, last
 * registration wins. A constraint with no registered mapper that declares validators gets a
 * {@link ConstraintValidatorMapper} synthesized on first lookup and kept; registering a mapper for
 * it afterwards replaces that too. Thread-safe.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class DefaultConstraintMapperRegistry implements ConstraintMapperRegistry {

    private final Map<Class<? extends Annotation>, ConstraintMapper<?>> mappers = new LinkedHashMap<>();
    private final Map<Class<? extends Annotation>, ConstraintValidatorMapper<?>> validatorMappers = new HashMap<>();
    private final ConstraintValidatorFactory constraintValidatorFactory;

    /**
     * Creates a registry whose validator-backed constraints instantiate their validators through the
     * {@link DefaultConstraintValidatorFactory}.
     *
     * @param registerDefaults true to pre-populate the standard mappers.
     */
    public DefaultConstraintMapperRegistry(boolean registerDefaults) {
        this(registerDefaults, new DefaultConstraintValidatorFactory());
    }

    /**
     * Creates a registry.
     *
     * @param registerDefaults true to pre-populate the standard mappers.
     * @param constraintValidatorFactory the factory validator-backed constraints instantiate their
     *                                   validators through.
     */
    public DefaultConstraintMapperRegistry(boolean registerDefaults, ConstraintValidatorFactory constraintValidatorFactory) {
        super();
        Assert.notNull(constraintValidatorFactory, "constraintValidatorFactory cannot be null.");
        this.constraintValidatorFactory = constraintValidatorFactory;
        if (registerDefaults) registerDefaults();
    }

    @Override
    public void register(ConstraintMapper<?> mapper) {
        Assert.notNull(mapper, "mapper cannot be null.");
        Class<? extends Annotation> annotationType = mapper.getAnnotationType();
        Assert.notNull(annotationType, "mapper [" + mapper.getClass().getName() + "] must declare its annotation type.");
        Assert.isTrue(annotationType.isAnnotationPresent(Constraint.class), "mapper [" + mapper.getClass().getName()
                + "] is for annotation [" + annotationType.getName() + "], which is not a constraint; it must be meta-annotated with @"
                + Constraint.class.getName());

        synchronized (mappers) {
            mappers.put(annotationType, mapper);
            validatorMappers.remove(annotationType);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> ConstraintMapper<A> find(Class<A> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");

        synchronized (mappers) {
            ConstraintMapper<A> result = (ConstraintMapper<A>) mappers.get(annotationType);
            if (result != null || !ConstraintValidatorMapper.declaresValidators(annotationType)) return result;
            return (ConstraintMapper<A>) validatorMappers.computeIfAbsent(annotationType,
                    type -> new ConstraintValidatorMapper<>(annotationType, constraintValidatorFactory));
        }
    }

    @Override
    public List<ConstraintMapper<?>> getMappers() {
        synchronized (mappers) {
            return Collections.unmodifiableList(new ArrayList<>(mappers.values()));
        }
    }

    /**
     * The factory validator-backed constraints instantiate their validators through.
     *
     * @return the factory.
     */
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory;
    }

    /**
     * The validator mappers synthesized so far.
     *
     * @return the mappers; unmodifiable snapshot.
     */
    public List<ConstraintValidatorMapper<?>> getValidatorMappers() {
        synchronized (mappers) {
            return Collections.unmodifiableList(new ArrayList<>(validatorMappers.values()));
        }
    }

    @Override
    public void release() {
        for (ConstraintValidatorMapper<?> mapper : getValidatorMappers()) {
            mapper.release();
        }
    }

    private void registerDefaults() {
        StandardConstraintMappers.all().forEach(this::register);
        LibraryConstraintMappers.all().forEach(this::register);
    }

    @Override
    public String toString() {
        return "DefaultConstraintMapperRegistry{" + getMappers().size() + " mappers, " + getValidatorMappers().size()
                + " validator mappers}";
    }
}
