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
package org.rulii.validation.beans.util.reflect;

import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;

/**
 * Visitor over the five shapes of {@link AnnotatedType}. Dispatch is done by
 * {@link AnnotatedTypes#accept(AnnotatedType, AnnotatedTypeVisitor)}; the visitor decides
 * for itself whether and where to descend (type arguments, array component, bounds).
 *
 * @param <R> result type.
 * @author Max Arulananthan
 * @since 1.0
 */
public interface AnnotatedTypeVisitor<R> {

    /**
     * A parameterized type such as {@code List<@NotBlank String>}.
     *
     * @param type the annotated parameterized type.
     * @return visitor result.
     */
    R visitParameterized(AnnotatedParameterizedType type);

    /**
     * An array type such as {@code String @Size(min = 1) []}.
     *
     * @param type the annotated array type.
     * @return visitor result.
     */
    R visitArray(AnnotatedArrayType type);

    /**
     * A wildcard such as {@code ? extends Number}.
     *
     * @param type the annotated wildcard type.
     * @return visitor result.
     */
    R visitWildcard(AnnotatedWildcardType type);

    /**
     * A type variable use such as {@code @NotBlank T}.
     *
     * @param type the annotated type variable.
     * @return visitor result.
     */
    R visitTypeVariable(AnnotatedTypeVariable type);

    /**
     * A plain class use such as {@code @NotBlank String}; the leaf of every tree.
     *
     * @param type the annotated type.
     * @return visitor result.
     */
    R visitClass(AnnotatedType type);
}
