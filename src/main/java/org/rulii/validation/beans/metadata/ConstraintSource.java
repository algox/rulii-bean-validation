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

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Collections;
import java.util.List;

/**
 * One constraint-bearing source of a property: the member that declared the constraints
 * paired with the accessor that reads that member's own value. A property has one source
 * per constrained declaration — field, getter (record components surface through their
 * accessor), and any superclass fields hidden by a subclass redeclaration. Constraints
 * always validate against their source's value (cumulative semantics, no accessor
 * substitution), so consumers process a property with one uniform loop:
 * for each source, validate its constraints against {@link #getValue(Object)}.
 *
 * <p>A source exists only if it carries at least one constraint.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintSource {

    private final List<ConstraintMetadata> constraints;
    private final PropertyAccessor accessor;

    ConstraintSource(List<ConstraintMetadata> constraints, PropertyAccessor accessor) {
        super();
        Assert.notNull(constraints, "constraints cannot be null.");
        Assert.isTrue(!constraints.isEmpty(), "constraints cannot be empty.");
        Assert.notNull(accessor, "accessor cannot be null.");
        this.constraints = Collections.unmodifiableList(constraints);
        this.accessor = accessor;
    }

    /**
     * Retrieves the member that declared this source's constraints (a field or a
     * getter/record-accessor method).
     *
     * @return the declaring member.
     */
    public Member getMember() {
        return accessor.getMember();
    }

    /**
     * Determines whether this source is backed by a field (as opposed to a getter or
     * record accessor).
     *
     * @return true if the source is a field; false otherwise.
     */
    public boolean isFieldSource() {
        return getMember() instanceof Field;
    }

    /**
     * Retrieves the constraints declared on this source.
     *
     * @return an unmodifiable list of constraints.
     */
    public List<ConstraintMetadata> getConstraints() {
        return constraints;
    }

    /**
     * Reads this source's own value off the given bean.
     *
     * @param bean the bean instance.
     * @return the source value.
     */
    public Object getValue(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        return accessor.getValue(bean);
    }

    @Override
    public String toString() {
        return "ConstraintSource{member=" + getMember().getDeclaringClass().getSimpleName() + "." + getMember().getName()
                + ", constraints=" + constraints.size() + '}';
    }
}
