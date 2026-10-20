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

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Thrown at metadata-read time when a constraint's {@code payload()} names more than one severity
 * marker ({@code Fatal}, {@code Warning}, {@code Info}). A violation carries one severity, so the
 * declaration has no single reading and fails fast instead of being guessed at.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConflictingSeverityException extends ConstraintDeclarationException {

    private final ConstraintSource source;
    private final Class<? extends Annotation> annotationType;
    private final List<Class<?>> markers;

    public ConflictingSeverityException(ConstraintSource source, Class<? extends Annotation> annotationType,
                                        List<Class<?>> markers) {
        super("constraint [" + annotationType.getName() + "] on [" + source.describe() + "] declares several severity"
                + " markers in its payload: " + markers.stream().map(Class::getSimpleName).toList()
                + ". A constraint may carry at most one of Fatal, Warning and Info.");
        this.source = source;
        this.annotationType = annotationType;
        this.markers = List.copyOf(markers);
    }

    /**
     * Where the constraint is declared.
     *
     * @return the source.
     */
    public ConstraintSource getSource() {
        return source;
    }

    /**
     * The constraint annotation type.
     *
     * @return the type.
     */
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    /**
     * The conflicting markers, in payload order.
     *
     * @return the marker classes.
     */
    public List<Class<?>> getMarkers() {
        return markers;
    }
}
