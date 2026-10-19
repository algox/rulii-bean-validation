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

import jakarta.validation.ConstraintDefinitionException;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a composed constraint (directly or through its composing constraints) is composed of
 * itself, e.g. {@code @A} composed of {@code @B} composed of {@code @A}. Such a definition can never
 * be materialized; it is rejected when the constraint is read.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintCompositionCycleException extends ConstraintDefinitionException {

    private final ConstraintSource constraintSource;
    private final List<Class<? extends Annotation>> path;

    /**
     * Creates the exception.
     *
     * @param constraintSource where the outermost constraint is declared.
     * @param path the composition path from the outermost constraint down to (and excluding) the
     *             repeated constraint.
     * @param repeated the constraint annotation type that reappears on its own composition path.
     */
    public ConstraintCompositionCycleException(ConstraintSource constraintSource,
                                               List<Class<? extends Annotation>> path,
                                               Class<? extends Annotation> repeated) {
        super("constraint [" + repeated.getName() + "] declared on [" + constraintSource.describe()
                + "] is composed of itself: " + render(path, repeated)
                + ". A constraint cannot appear in its own composition.");
        this.constraintSource = constraintSource;
        List<Class<? extends Annotation>> fullPath = new ArrayList<>(path);
        fullPath.add(repeated);
        this.path = Collections.unmodifiableList(fullPath);
    }

    private static String render(List<Class<? extends Annotation>> path, Class<? extends Annotation> repeated) {
        return path.stream().map(Class::getSimpleName).collect(Collectors.joining(" -> "))
                + " -> " + repeated.getSimpleName();
    }

    /**
     * Where the outermost constraint is declared.
     *
     * @return the constraint source.
     */
    public ConstraintSource getConstraintSource() {
        return constraintSource;
    }

    /**
     * The composition path, from the outermost constraint to the repeated one (which appears twice:
     * where it was first entered and at the end).
     *
     * @return the composition path.
     */
    public List<Class<? extends Annotation>> getPath() {
        return path;
    }
}
