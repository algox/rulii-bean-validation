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

import jakarta.validation.ValidationException;

/**
 * Root of the failures this library reports that the spec has no type for: an invalid requested
 * group, a {@code @Validate} name that resolves to nothing, a property that cannot be read. It is a
 * jakarta {@link ValidationException}, so a handler written for any Bean Validation provider catches it unchanged.
 * <p>
 * Declaration errors the spec names keep their spec type ({@code ConstraintDeclarationException} and
 * subclasses); unexpected internal failures are {@code UnrulyException}, as everywhere in rulii.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanValidationException extends ValidationException {

    /**
     * Creates the exception.
     *
     * @param message the message.
     */
    public BeanValidationException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message the message.
     * @param cause the cause.
     */
    public BeanValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
