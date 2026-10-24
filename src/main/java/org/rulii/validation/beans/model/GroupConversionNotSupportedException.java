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
 * Thrown at metadata-read time when {@code @ConvertGroup} is declared on a property declaration
 * (field, getter or record component) or on a container element position within its type.
 * Group conversion is not supported; ignoring the annotation would silently validate the
 * cascaded object against the wrong groups, so the declaration fails fast instead.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class GroupConversionNotSupportedException extends ConstraintDeclarationException {

    private final ValueDeclaration sourceDeclaration;

    public GroupConversionNotSupportedException(ValueDeclaration sourceDeclaration) {
        super("@ConvertGroup declared on property [" + sourceDeclaration.describe()
                + "] is not supported; cascaded objects are validated against the groups of the root call."
                + " Remove the annotation or validate the cascaded object separately with the groups you need.");
        this.sourceDeclaration = sourceDeclaration;
    }

    /**
     * The property declaration the annotation was found on or within.
     *
     * @return the source declaration.
     */
    public ValueDeclaration getSourceDeclaration() {
        return sourceDeclaration;
    }
}
