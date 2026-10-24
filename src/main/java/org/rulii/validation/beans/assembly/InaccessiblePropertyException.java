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
package org.rulii.validation.beans.assembly;

import org.rulii.validation.beans.BeanValidationException;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.ValueDeclaration;

/**
 * A property validation needs to read cannot be made accessible: the bean lives in a named module
 * that does not open its package to this library. Raised when the bean class is materialized, never
 * mid-validation.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class InaccessiblePropertyException extends BeanValidationException {

    private static final long serialVersionUID = 0L;

    private final ValueDeclaration declaration;

    /**
     * Creates the exception.
     *
     * @param declaration the declaration that cannot be read.
     * @param cause what the JDK reported.
     */
    public InaccessiblePropertyException(ValueDeclaration declaration, Throwable cause) {
        super(message(declaration), cause);
        this.declaration = declaration;
    }

    private static String message(ValueDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        String packageName = declaration.getDeclaringClass().getPackageName();
        return "property [" + declaration.describe() + "] cannot be read: it is not accessible to rulii bean validation. "
                + "If the bean lives in a named module, open its package to this library "
                + "(opens " + packageName + " to org.rulii.validation.beans;) or make the member public.";
    }

    /**
     * The declaration that cannot be read.
     *
     * @return the declaration.
     */
    public ValueDeclaration getDeclaration() {
        return declaration;
    }
}
