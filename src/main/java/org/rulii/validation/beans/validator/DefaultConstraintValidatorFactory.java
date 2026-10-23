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
import jakarta.validation.ValidationException;
import org.rulii.lib.spring.util.Assert;

import java.lang.reflect.Constructor;

/**
 * The {@link ConstraintValidatorFactory} used when none is configured: instantiates a validator through
 * its no-argument constructor, made accessible if need be, and has nothing to release. A framework
 * that wants its validators injected configures its own factory on the bootstrap or on the
 * {@link org.rulii.validation.beans.BeanValidatorBuilder}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class DefaultConstraintValidatorFactory implements ConstraintValidatorFactory {

    public DefaultConstraintValidatorFactory() {
        super();
    }

    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
        Assert.notNull(key, "key cannot be null.");
        try {
            Constructor<T> constructor = key.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ValidationException("cannot instantiate constraint validator [" + key.getName() + "]", e);
        }
    }

    @Override
    public void releaseInstance(ConstraintValidator<?, ?> instance) {
        // Nothing to release.
    }

    @Override
    public String toString() {
        return "DefaultConstraintValidatorFactory{}";
    }
}
