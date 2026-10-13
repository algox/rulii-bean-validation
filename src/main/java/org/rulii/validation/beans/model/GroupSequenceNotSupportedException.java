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
 * Thrown at metadata-read time when {@code @GroupSequence} is declared on a bean class or on one of
 * its superclasses, which redefines the {@code Default} group of that class. Group sequences are not
 * supported; ignoring the annotation would validate the class against the plain Default group, in a
 * different order and without the sequence short-circuit, so the declaration fails fast instead.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class GroupSequenceNotSupportedException extends ConstraintDeclarationException {

    private final Class<?> type;

    public GroupSequenceNotSupportedException(Class<?> type) {
        super("@GroupSequence declared on class [" + type.getName() + "] is not supported; the Default group"
                + " cannot be redefined as a sequence. Remove the annotation, or validate the groups of the"
                + " sequence explicitly in the order you need.");
        this.type = type;
    }

    /**
     * The class the annotation was found on.
     *
     * @return the type.
     */
    public Class<?> getType() {
        return type;
    }
}
