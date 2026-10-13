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

import org.rulii.model.SourceDefinition;

import java.lang.reflect.AnnotatedElement;

/**
 * Where a constraint is declared: on a {@link PropertyConstraintSource property declaration} (a
 * field, getter or record component, or a container element position within its type) or on a
 * {@link ClassConstraintSource class} (a class-level constraint of the bean class, one of its
 * superclasses or one of its interfaces).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface ConstraintSource {

    /**
     * The class or interface the constraint is declared in: the declaring class of the member for a
     * property source, the type itself for a class source.
     *
     * @return the declaring class.
     */
    Class<?> getDeclaringClass();

    /**
     * The element the constraint annotations are read from: the field, getter, record component or
     * class.
     *
     * @return the annotated element.
     */
    AnnotatedElement getElement();

    /**
     * Core source descriptor of the element.
     *
     * @return the source definition.
     */
    SourceDefinition getSourceDefinition();

    /**
     * Human-readable description for diagnostics, e.g. {@code com.acme.Order.getTotal()} or
     * {@code com.acme.Order}.
     *
     * @return the description.
     */
    String describe();
}
