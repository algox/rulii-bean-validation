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
package org.rulii.validation.beans.path;

import org.rulii.lib.spring.util.Assert;

import java.util.Objects;

/**
 * Immutable, parent-linked property path from the root bean to a validated element.
 * Each node references its parent, so capturing the current path is storing a single
 * reference and extending it is a pointer operation. Rendered on demand in Bean
 * Validation notation: {@code orders[2].customer.name}, {@code contacts[home].phone}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Path {

    private static final Path ROOT = new Path(null, PathNodeKind.ROOT, null, null, null);

    private final Path parent;
    private final PathNodeKind kind;
    private final String name;
    private final Integer index;
    private final Object key;

    private Path(Path parent, PathNodeKind kind, String name, Integer index, Object key) {
        super();
        this.parent = parent;
        this.kind = kind;
        this.name = name;
        this.index = index;
        this.key = key;
    }

    /**
     * Returns the root path (the bean itself; renders as an empty string).
     *
     * @return the root path.
     */
    public static Path root() {
        return ROOT;
    }

    /**
     * Extends this path with a property node.
     *
     * @param name the property name.
     * @return the new path ending in the given property.
     */
    public Path property(String name) {
        Assert.hasText(name, "name cannot be null or empty.");
        return new Path(this, PathNodeKind.PROPERTY, name, null, null);
    }

    /**
     * Extends this path with an indexed element node (List/array).
     *
     * @param index the element index.
     * @return the new path ending in the indexed element.
     */
    public Path index(int index) {
        return new Path(this, PathNodeKind.INDEX, null, index, null);
    }

    /**
     * Extends this path with a keyed element node (Map).
     *
     * @param key the map key.
     * @return the new path ending in the keyed element.
     */
    public Path key(Object key) {
        return new Path(this, PathNodeKind.KEY, null, null, key);
    }

    /**
     * Extends this path with an un-indexed iterable element node.
     *
     * @return the new path ending in the iterable element.
     */
    public Path iterable() {
        return new Path(this, PathNodeKind.ITERABLE, null, null, null);
    }

    /**
     * Retrieves the parent path of this node.
     *
     * @return the parent path; null for the root.
     */
    public Path getParent() {
        return parent;
    }

    /**
     * Retrieves the kind of this path node.
     *
     * @return the node kind.
     */
    public PathNodeKind getKind() {
        return kind;
    }

    /**
     * Retrieves the property name of this node.
     *
     * @return the property name; null unless this is a property node.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the element index of this node.
     *
     * @return the element index; null unless this is an indexed node.
     */
    public Integer getIndex() {
        return index;
    }

    /**
     * Retrieves the map key of this node.
     *
     * @return the map key; null unless this is a keyed node.
     */
    public Object getKey() {
        return key;
    }

    /**
     * Determines whether this is the root path.
     *
     * @return true if this is the root path; false otherwise.
     */
    public boolean isRoot() {
        return kind == PathNodeKind.ROOT;
    }

    /**
     * Renders this path in Bean Validation notation, e.g. {@code orders[2].customer.name}.
     * The root path renders as an empty string.
     *
     * @return the rendered path.
     */
    public String render() {
        StringBuilder result = new StringBuilder();
        render(result);
        return result.toString();
    }

    private void render(StringBuilder result) {
        if (parent != null) parent.render(result);

        switch (kind) {
            case ROOT:
                break;
            case PROPERTY:
                if (!result.isEmpty()) result.append('.');
                result.append(name);
                break;
            case INDEX:
                result.append('[').append(index).append(']');
                break;
            case KEY:
                result.append('[').append(key).append(']');
                break;
            case ITERABLE:
                result.append("[]");
                break;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Path path = (Path) o;
        return kind == path.kind
                && Objects.equals(name, path.name)
                && Objects.equals(index, path.index)
                && Objects.equals(key, path.key)
                && Objects.equals(parent, path.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, kind, name, index, key);
    }

    @Override
    public String toString() {
        return render();
    }
}
