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
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.BeanValidationException;

/**
 * Thrown when a {@link ConstraintValidator} cannot be used: the configured factory returned no
 * instance for it, its {@code initialize} failed, or its {@code isValid} threw.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintValidatorException extends BeanValidationException {

    private static final long serialVersionUID = 0L;

    private final Class<? extends ConstraintValidator<?, ?>> validatorClass;

    /**
     * Creates the exception.
     *
     * @param validatorClass the validator class concerned.
     * @param message what went wrong.
     */
    public ConstraintValidatorException(Class<? extends ConstraintValidator<?, ?>> validatorClass, String message) {
        this(validatorClass, message, null);
    }

    /**
     * Creates the exception.
     *
     * @param validatorClass the validator class concerned.
     * @param message what went wrong.
     * @param cause what the validator or the factory threw; may be null.
     */
    public ConstraintValidatorException(Class<? extends ConstraintValidator<?, ?>> validatorClass, String message, Throwable cause) {
        super("constraint validator [" + name(validatorClass) + "] " + message, cause);
        this.validatorClass = validatorClass;
    }

    private static String name(Class<?> validatorClass) {
        Assert.notNull(validatorClass, "validatorClass cannot be null.");
        return validatorClass.getName();
    }

    /**
     * The validator class concerned.
     *
     * @return the class.
     */
    public Class<? extends ConstraintValidator<?, ?>> getValidatorClass() {
        return validatorClass;
    }
}
