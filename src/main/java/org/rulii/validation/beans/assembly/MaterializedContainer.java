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

import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.metadata.ContainerElementRole;
import org.rulii.validation.beans.metadata.ContainerKind;

import java.util.Collections;
import java.util.List;

/**
 * The materialized mirror of a container-element tree: per type-argument position, the
 * element rules to run, the cascade marker, and the nested container. Consumed by the
 * traversal's container walk.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MaterializedContainer {

    private final ContainerKind kind;
    private final List<MaterializedElement> elements;

    MaterializedContainer(ContainerKind kind, List<MaterializedElement> elements) {
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
     * Retrieves all materialized positions of this container.
     *
     * @return an unmodifiable list of positions.
     */
    public List<MaterializedElement> getElements() {
        return elements;
    }

    /**
     * Retrieves the single element position (Collection/Optional/array).
     *
     * @return the element position; null for Map containers.
     */
    public MaterializedElement getElement() {
        return findByRole(ContainerElementRole.ELEMENT);
    }

    /**
     * Retrieves the key position of a Map container.
     *
     * @return the key position; null for non-Map containers.
     */
    public MaterializedElement getKeyElement() {
        return findByRole(ContainerElementRole.KEY);
    }

    /**
     * Retrieves the value position of a Map container.
     *
     * @return the value position; null for non-Map containers.
     */
    public MaterializedElement getValueElement() {
        return findByRole(ContainerElementRole.VALUE);
    }

    private MaterializedElement findByRole(ContainerElementRole role) {
        for (MaterializedElement element : elements) {
            if (element.getRole() == role) return element;
        }
        return null;
    }

    @Override
    public String toString() {
        return "MaterializedContainer{kind=" + kind + ", elements=" + elements.size() + '}';
    }
}
