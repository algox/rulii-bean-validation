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

import org.rulii.model.UnrulyException;

/**
 * Base exception for all bean-validation specific failures raised by this library.
 * Dedicated subclasses exist per failure type (metadata read fail-fasts, property
 * access denials, etc.).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanValidationException extends UnrulyException {

    /**
     * Creates a new BeanValidationException with the given message.
     *
     * @param message the error message.
     */
    public BeanValidationException(String message) {
        super(message);
    }

    /**
     * Creates a new BeanValidationException with the given message and cause.
     *
     * @param message the error message.
     * @param cause the underlying cause.
     */
    public BeanValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
