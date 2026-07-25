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

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * One container type-argument position: its role, resolved element type, the constraints
 * declared on it ({@code List<@NotBlank String>}), whether it cascades
 * ({@code List<@Valid Order>}), and — for nested containers — its own container tree
 * ({@code Map<String, List<@Size(max=5) String>>}).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ContainerElement {

    private final ContainerElementRole role;
    private final Type elementType;
    private final List<ConstraintMetadata> constraints;
    private final boolean cascaded;
    private final ContainerMetadata container;

    ContainerElement(ContainerElementRole role, Type elementType, List<ConstraintMetadata> constraints,
                     boolean cascaded, ContainerMetadata container) {
        super();
        Assert.notNull(role, "role cannot be null.");
        Assert.notNull(constraints, "constraints cannot be null.");
        this.role = role;
        this.elementType = elementType;
        this.constraints = Collections.unmodifiableList(constraints);
        this.cascaded = cascaded;
        this.container = container;
    }

    /**
     * Retrieves the role of this position (element, map key, or map value).
     *
     * @return the element role.
     */
    public ContainerElementRole getRole() {
        return role;
    }

    /**
     * Retrieves the resolved element type of this position; Object when the type could
     * not be statically resolved.
     *
     * @return the element type.
     */
    public Type getElementType() {
        return elementType;
    }

    /**
     * Retrieves the constraints declared on this position.
     *
     * @return an unmodifiable list of constraints.
     */
    public List<ConstraintMetadata> getConstraints() {
        return constraints;
    }

    /**
     * Determines whether this position cascades ({@code @Valid} on the type argument).
     *
     * @return true if the position cascades; false otherwise.
     */
    public boolean isCascaded() {
        return cascaded;
    }

    /**
     * Retrieves the nested container tree of this position.
     *
     * @return the nested container metadata; null if the element is not itself a container.
     */
    public ContainerMetadata getContainer() {
        return container;
    }

    @Override
    public String toString() {
        return "ContainerElement{role=" + role
                + ", elementType=" + elementType
                + ", constraints=" + constraints.size()
                + ", cascaded=" + cascaded
                + '}';
    }
}
