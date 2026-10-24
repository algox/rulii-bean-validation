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
package org.rulii.validation.beans.traversal;

import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;

import java.lang.annotation.ElementType;

/**
 * The {@link TraversableResolver} that reaches and cascades into everything: the default outside a
 * persistence context. The traversal never consults it, so it costs nothing.
 *
 * @author Max Arulananthan
 * @since 1.0
 * @see TraversableResolvers#traverseAll()
 */
public final class TraverseAllTraversableResolver implements TraversableResolver {

    TraverseAllTraversableResolver() {
        super();
    }

    @Override
    public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                               Path pathToTraversableObject, ElementType elementType) {
        return true;
    }

    @Override
    public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                Path pathToTraversableObject, ElementType elementType) {
        return true;
    }

    @Override
    public String toString() {
        return "TraverseAllTraversableResolver{}";
    }
}
