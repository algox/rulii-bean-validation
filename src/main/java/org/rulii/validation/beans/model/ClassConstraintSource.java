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
 * A class-level constraint: declared on the bean class, one of its superclasses or one of its
 * interfaces, and validated against the bean instance itself.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ClassConstraintSource implements ConstraintSource {

    private final Class<?> type;
    private final SourceDefinition sourceDefinition;

    public ClassConstraintSource(Class<?> type) {
        super();
        Assert.notNull(type, "type cannot be null.");
        this.type = type;
        this.sourceDefinition = SourceDefinition.forClass(type);
    }

    /**
     * The class or interface the constraint is declared on.
     *
     * @return the type.
     */
    public Class<?> getType() {
        return type;
    }

    @Override
    public Class<?> getDeclaringClass() {
        return type;
    }

    @Override
    public AnnotatedElement getElement() {
        return type;
    }

    @Override
    public SourceDefinition getSourceDefinition() {
        return sourceDefinition;
    }

    @Override
    public String describe() {
        return type.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassConstraintSource that = (ClassConstraintSource) o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return "CLASS " + type.getName();
    }
}
