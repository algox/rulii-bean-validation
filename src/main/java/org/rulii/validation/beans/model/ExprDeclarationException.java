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
 * An {@code @Expr} declaration that cannot be used: a blank script or error code, a group that is not
 * an interface, an {@code applyTo} declared where it does not apply, a language nobody declared or
 * no engine answers to, or a script that does not compile. Raised when the class is read for
 * validation, naming the exact member.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ExprDeclarationException extends ConstraintDeclarationException {

    private final ConstraintSource source;

    public ExprDeclarationException(String message, ConstraintSource source) {
        super(message);
        this.source = source;
    }

    public ExprDeclarationException(String message, ConstraintSource source, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    /**
     * Where the offending {@code @Expr} is declared.
     *
     * @return the source.
     */
    public ConstraintSource getSource() {
        return source;
    }
}
