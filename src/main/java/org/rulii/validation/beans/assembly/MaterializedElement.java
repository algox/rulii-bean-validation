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

import java.util.Collections;
import java.util.List;

/**
 * One materialized container position: the rules validating each element occupying the
 * position (reading the walk's element binding), whether elements cascade, and the
 * nested container tree for nested generics.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MaterializedElement {

    private final ContainerElementRole role;
    private final List<MaterializedRule> rules;
    private final boolean cascaded;
    private final MaterializedContainer nested;

    MaterializedElement(ContainerElementRole role, List<MaterializedRule> rules, boolean cascaded,
                        MaterializedContainer nested) {
        super();
        Assert.notNull(role, "role cannot be null.");
        Assert.notNull(rules, "rules cannot be null.");
        this.role = role;
        this.rules = Collections.unmodifiableList(rules);
        this.cascaded = cascaded;
        this.nested = nested;
    }

    /**
     * Retrieves the role of this position (element, map key, or map value).
     *
     * @return the position role.
     */
    public ContainerElementRole getRole() {
        return role;
    }

    /**
     * Retrieves the element rules of this position.
     *
     * @return an unmodifiable list of materialized rules.
     */
    public List<MaterializedRule> getRules() {
        return rules;
    }

    /**
     * Determines whether elements at this position cascade.
     *
     * @return true if elements cascade; false otherwise.
     */
    public boolean isCascaded() {
        return cascaded;
    }

    /**
     * Retrieves the nested container tree of this position.
     *
     * @return the nested container; null if the element is not itself a container.
     */
    public MaterializedContainer getNested() {
        return nested;
    }

    @Override
    public String toString() {
        return "MaterializedElement{role=" + role + ", rules=" + rules.size() + ", cascaded=" + cascaded + '}';
    }
}
