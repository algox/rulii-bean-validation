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
package org.rulii.validation.beans.metadata;

import org.rulii.lib.spring.util.Assert;

import java.util.Collections;
import java.util.List;

/**
 * The container-element tree of a property: the container kind and its type-argument
 * positions. A Map has key and value positions; Collection, Optional, and arrays have a
 * single element position. Nested containers recurse via
 * {@link ContainerElement#getContainer()}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ContainerMetadata {

    private final ContainerKind kind;
    private final List<ContainerElement> elements;

    ContainerMetadata(ContainerKind kind, List<ContainerElement> elements) {
        super();
        Assert.notNull(kind, "kind cannot be null.");
        Assert.notNull(elements, "elements cannot be null.");
        this.kind = kind;
        this.elements = Collections.unmodifiableList(elements);
    }

    /**
     * Retrieves the container kind.
     *
     * @return the container kind.
     */
    public ContainerKind getKind() {
        return kind;
    }

    /**
     * Retrieves all type-argument positions of this container.
     *
     * @return an unmodifiable list of container elements.
     */
    public List<ContainerElement> getElements() {
        return elements;
    }

    /**
     * Retrieves the single element position (Collection/Optional/array).
     *
     * @return the element position; null for Map containers.
     */
    public ContainerElement getElement() {
        return findByRole(ContainerElementRole.ELEMENT);
    }

    /**
     * Retrieves the key position of a Map container.
     *
     * @return the key position; null for non-Map containers.
     */
    public ContainerElement getKeyElement() {
        return findByRole(ContainerElementRole.KEY);
    }

    /**
     * Retrieves the value position of a Map container.
     *
     * @return the value position; null for non-Map containers.
     */
    public ContainerElement getValueElement() {
        return findByRole(ContainerElementRole.VALUE);
    }

    /**
     * Determines whether any position (recursively) carries constraints or cascades.
     *
     * @return true if this container tree contributes validation work; false otherwise.
     */
    public boolean hasValidation() {

        for (ContainerElement element : elements) {
            if (!element.getConstraints().isEmpty() || element.isCascaded()) return true;
            if (element.getContainer() != null && element.getContainer().hasValidation()) return true;
        }

        return false;
    }

    private ContainerElement findByRole(ContainerElementRole role) {

        for (ContainerElement element : elements) {
            if (element.getRole() == role) return element;
        }

        return null;
    }

    @Override
    public String toString() {
        return "ContainerMetadata{kind=" + kind + ", elements=" + elements.size() + '}';
    }
}
