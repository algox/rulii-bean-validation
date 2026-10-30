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
package org.rulii.validation.beans.model;

import jakarta.validation.ConstraintDeclarationException;

/**
 * A {@code @Bind} declaration that cannot be used: a blank, malformed or reserved name, a name that
 * repeats at the site or collides with a property or a parameter, neither or both of a literal and an
 * expression, an {@code applyTo} declared where it does not apply, a declaration on a field, getter or
 * record component, a literal no converter turns into the declared type, or an expression in a language
 * nobody declared, no engine answers to, or that does not compile. Raised when the class is read for
 * validation, naming the exact member.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BindDeclarationException extends ConstraintDeclarationException {

    private final ConstraintSource source;
    private final String bindingName;

    /**
     * Creates the exception.
     *
     * @param message the message.
     * @param source where the offending annotation is declared.
     * @param bindingName the offending binding name; null when the annotation declares none.
     */
    public BindDeclarationException(String message, ConstraintSource source, String bindingName) {
        super(message);
        this.source = source;
        this.bindingName = bindingName;
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message the message.
     * @param source where the offending annotation is declared.
     * @param bindingName the offending binding name.
     * @param cause the cause.
     */
    public BindDeclarationException(String message, ConstraintSource source, String bindingName, Throwable cause) {
        super(message, cause);
        this.source = source;
        this.bindingName = bindingName;
    }

    /**
     * Where the offending {@code @Bind} is declared.
     *
     * @return the source.
     */
    public ConstraintSource getSource() {
        return source;
    }

    /**
     * The offending binding name.
     *
     * @return the name; null when the annotation declares none.
     */
    public String getBindingName() {
        return bindingName;
    }
}
