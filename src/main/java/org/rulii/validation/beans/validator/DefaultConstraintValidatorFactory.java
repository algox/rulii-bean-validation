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
package org.rulii.validation.beans.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import org.rulii.lib.spring.util.Assert;
import org.rulii.util.reflect.ObjectFactory;

/**
 * The {@link ConstraintValidatorFactory} used when none is configured: creates validators through a
 * rulii {@link ObjectFactory}, by default the one of the validator's
 * {@link org.rulii.context.RuleContextOptions}. The default object factory instantiates through the
 * no-argument constructor (made accessible if need be) and invokes a {@code @PostConstruct} method;
 * a container-backed one (rulii-spring's {@code SpringObjectFactory}) creates the validator as a bean
 * of the container, dependencies injected. Validators are never taken from the object factory's cache:
 * one instance exists per constraint occurrence and is initialized with that occurrence's annotation.
 * Nothing is released.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class DefaultConstraintValidatorFactory implements ConstraintValidatorFactory {

    private final ObjectFactory objectFactory;

    /**
     * Creates the factory over a standard object factory (no-argument constructor plus post-construct).
     */
    public DefaultConstraintValidatorFactory() {
        this(ObjectFactory.builder().build());
    }

    /**
     * Creates the factory over an object factory.
     *
     * @param objectFactory the object factory validators are created through.
     */
    public DefaultConstraintValidatorFactory(ObjectFactory objectFactory) {
        super();
        Assert.notNull(objectFactory, "objectFactory cannot be null.");
        this.objectFactory = objectFactory;
    }

    /**
     * The object factory validators are created through.
     *
     * @return the object factory.
     */
    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    /**
     * Creates a validator through the object factory, bypassing its cache.
     *
     * @throws ConstraintValidatorException if the object factory cannot create it.
     */
    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
        Assert.notNull(key, "key cannot be null.");
        try {
            return objectFactory.create(key, false);
        } catch (RuntimeException e) {
            throw new ConstraintValidatorException(key, "could not be instantiated by [" + objectFactory + "]", e);
        }
    }

    @Override
    public void releaseInstance(ConstraintValidator<?, ?> instance) {
        // Nothing to release.
    }

    @Override
    public String toString() {
        return "DefaultConstraintValidatorFactory{" + objectFactory + '}';
    }
}
