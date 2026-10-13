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

import org.rulii.lib.spring.util.Assert;
import org.rulii.model.SourceDefinition;

import java.lang.reflect.AnnotatedElement;

/**
 * A constraint declared on a property declaration: directly on the field, getter or record
 * component, or at a container element position within its type.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyConstraintSource implements ConstraintSource {

    private final PropertyDeclaration declaration;

    public PropertyConstraintSource(PropertyDeclaration declaration) {
        super();
        Assert.notNull(declaration, "declaration cannot be null.");
        this.declaration = declaration;
    }

    /**
     * The property declaration the constraint belongs to.
     *
     * @return the declaration.
     */
    public PropertyDeclaration getDeclaration() {
        return declaration;
    }

    @Override
    public Class<?> getDeclaringClass() {
        return declaration.getDeclaringClass();
    }

    @Override
    public AnnotatedElement getElement() {
        return declaration.getDeclaration();
    }

    @Override
    public SourceDefinition getSourceDefinition() {
        return declaration.getSourceDefinition();
    }

    @Override
    public String describe() {
        return declaration.describe();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyConstraintSource that = (PropertyConstraintSource) o;
        return declaration.equals(that.declaration);
    }

    @Override
    public int hashCode() {
        return declaration.hashCode();
    }

    @Override
    public String toString() {
        return declaration.toString();
    }
}
