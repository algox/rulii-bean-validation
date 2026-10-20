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
 * Thrown at metadata-read time when a {@code @Validate} declaration is malformed: no rule name at
 * all, a blank rule name, or a rule name referenced more than once for the same property or class
 * (within one annotation, or across the declarations and hierarchy levels of the property or class).
 * Each such declaration is a mistake that would otherwise run a rule twice or do nothing, so it
 * fails fast instead.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValidateDeclarationException extends ConstraintDeclarationException {

    private final ConstraintSource source;
    private final String ruleName;

    /**
     * Creates the exception.
     *
     * @param message the message.
     * @param source where the offending annotation is declared.
     * @param ruleName the offending rule name; null when the annotation declares no name.
     */
    public ValidateDeclarationException(String message, ConstraintSource source, String ruleName) {
        super(message);
        this.source = source;
        this.ruleName = ruleName;
    }

    /**
     * Where the offending {@code @Validate} is declared.
     *
     * @return the source.
     */
    public ConstraintSource getSource() {
        return source;
    }

    /**
     * The offending rule name.
     *
     * @return the name; null when the annotation declares no name at all.
     */
    public String getRuleName() {
        return ruleName;
    }
}
