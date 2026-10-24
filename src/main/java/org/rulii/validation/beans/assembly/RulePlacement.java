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
import org.rulii.validation.beans.PropertyPath;

import java.util.Objects;

/**
 * Where a materialized rule's violations report, relative to the path the traversal is at when the
 * rule runs: at a property of the object in scope, at a parameter of the executable in scope, at its
 * return value or at its parameters as a whole, at the bean itself (a class-level constraint), or
 * nowhere further (a container element rule, whose path the walk has already extended).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RulePlacement {

    /**
     * Kinds of placement.
     */
    public enum Kind {
        /** A property of the object in scope. */
        PROPERTY,
        /** A parameter of the executable in scope. */
        PARAMETER,
        /** The return value of the executable in scope. */
        RETURN_VALUE,
        /** The parameters of the executable in scope, as a whole. */
        CROSS_PARAMETER,
        /** The object in scope itself. */
        BEAN,
        /** The current path, as the traversal set it. */
        NONE
    }

    private static final RulePlacement RETURN_VALUE = new RulePlacement(Kind.RETURN_VALUE, null, -1);
    private static final RulePlacement CROSS_PARAMETER = new RulePlacement(Kind.CROSS_PARAMETER, null, -1);
    private static final RulePlacement BEAN = new RulePlacement(Kind.BEAN, null, -1);
    private static final RulePlacement NONE = new RulePlacement(Kind.NONE, null, -1);

    private final Kind kind;
    private final String name;
    private final int index;

    private RulePlacement(Kind kind, String name, int index) {
        super();
        this.kind = kind;
        this.name = name;
        this.index = index;
    }

    /**
     * At a property of the object in scope.
     *
     * @param name the property name.
     * @return the placement.
     */
    public static RulePlacement property(String name) {
        Assert.hasText(name, "name cannot be null/empty.");
        return new RulePlacement(Kind.PROPERTY, name, -1);
    }

    /**
     * At a parameter of the executable in scope.
     *
     * @param name the parameter name.
     * @param index the parameter index.
     * @return the placement.
     */
    public static RulePlacement parameter(String name, int index) {
        Assert.hasText(name, "name cannot be null/empty.");
        Assert.isTrue(index >= 0, "index cannot be negative.");
        return new RulePlacement(Kind.PARAMETER, name, index);
    }

    /**
     * At the return value of the executable in scope.
     *
     * @return the placement.
     */
    public static RulePlacement returnValue() {
        return RETURN_VALUE;
    }

    /**
     * At the parameters of the executable in scope, as a whole.
     *
     * @return the placement.
     */
    public static RulePlacement crossParameter() {
        return CROSS_PARAMETER;
    }

    /**
     * At the object in scope itself.
     *
     * @return the placement.
     */
    public static RulePlacement bean() {
        return BEAN;
    }

    /**
     * At the current path, as the traversal set it.
     *
     * @return the placement.
     */
    public static RulePlacement none() {
        return NONE;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * The property name for a property placement, the parameter name for a parameter placement.
     *
     * @return the name; null for the other kinds.
     */
    public String getName() {
        return name;
    }

    /**
     * The parameter index for a parameter placement.
     *
     * @return the index; -1 for the other kinds.
     */
    public int getIndex() {
        return index;
    }

    /**
     * The property this placement names.
     *
     * @return the property name; null unless this is a property placement.
     */
    public String getPropertyName() {
        return kind == Kind.PROPERTY ? name : null;
    }

    /**
     * Where a violation reports, given the path the rule runs at.
     *
     * @param base the current path.
     * @return the path of the violation.
     */
    public PropertyPath apply(PropertyPath base) {
        Assert.notNull(base, "base cannot be null.");
        return switch (kind) {
            case PROPERTY -> base.property(name);
            case PARAMETER -> base.parameter(name, index);
            case RETURN_VALUE -> base.returnValue();
            case CROSS_PARAMETER -> base.crossParameter();
            case BEAN -> base.bean();
            case NONE -> base;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RulePlacement that = (RulePlacement) o;
        return kind == that.kind && index == that.index && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, index);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case PROPERTY -> "property " + name;
            case PARAMETER -> "parameter " + name + "[" + index + "]";
            default -> kind.name().toLowerCase().replace('_', ' ');
        };
    }
}
