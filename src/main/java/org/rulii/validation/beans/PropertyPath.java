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
package org.rulii.validation.beans;

import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.rulii.lib.spring.util.Assert;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * The property path of a violation: an immutable {@link jakarta.validation.Path} rendered in the
 * Bean Validation notation ({@code orders[2].customer.name}, {@code tags[2].<list element>},
 * {@code contacts[home].<map key>}, {@code placeOrder.arg0}, {@code placeOrder.<return value>}),
 * node for node what the reference implementation reports.
 * <p>
 * A path is a parent-linked chain of nodes; the path object itself names the leaf. Appending a node
 * ({@link #property(String)}, {@link #element(String)}, {@link #bean()}, {@link #method}, {@link #constructor},
 * {@link #parameter}, {@link #returnValue()}, {@link #crossParameter()}) allocates one node and shares
 * the chain, so the traversal pushes and pops by holding references. The subscript operations
 * ({@link #withIndex}, {@link #withKey}, {@link #inIterable}) describe how the <em>contents</em> of the
 * leaf are addressed and return a sibling copy of the leaf; the spec accessors on the following node
 * ({@code getIndex()}, {@code getKey()}, {@code isInIterable()}, {@code getContainerClass()},
 * {@code getTypeArgumentIndex()}) read that subscript off their parent, exactly as the reference
 * implementation does.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyPath implements Path {

    /**
     * Name of the node standing for the return value of an executable.
     */
    public static final String RETURN_VALUE_NODE = "<return value>";

    /**
     * Name of the node standing for the parameters of an executable as a whole.
     */
    public static final String CROSS_PARAMETER_NODE = "<cross-parameter>";

    private static final PropertyPath ROOT = new PropertyPath(null);
    private static final String SEPARATOR = ".";

    private final Node leaf;

    private PropertyPath(Node leaf) {
        super();
        this.leaf = leaf;
    }

    /**
     * The empty path of the root bean. It has no nodes and renders as the empty string.
     *
     * @return the root path.
     */
    public static PropertyPath root() {
        return ROOT;
    }

    /**
     * Appends a property node.
     *
     * @param name the property name.
     * @return the extended path.
     */
    public PropertyPath property(String name) {
        Assert.hasText(name, "name cannot be null/empty.");
        return new PropertyPath(new Node(leaf, ElementKind.PROPERTY, name, null, null, null));
    }

    /**
     * Appends a container element node, named after the value extractor's node name
     * ({@code <list element>}, {@code <map key>}, ...).
     *
     * @param nodeName the node name.
     * @return the extended path.
     */
    public PropertyPath element(String nodeName) {
        Assert.hasText(nodeName, "nodeName cannot be null/empty.");
        return new PropertyPath(new Node(leaf, ElementKind.CONTAINER_ELEMENT, nodeName, null, null, null));
    }

    /**
     * Appends the nameless bean node that marks a class-level violation. It renders as nothing.
     *
     * @return the extended path.
     */
    public PropertyPath bean() {
        return new PropertyPath(new Node(leaf, ElementKind.BEAN, null, null, null, null));
    }

    /**
     * Appends a method node: the first node of a path reported by executable validation of a method.
     *
     * @param name the method name.
     * @param parameterTypes the method's parameter types.
     * @return the extended path.
     */
    public PropertyPath method(String name, Class<?>... parameterTypes) {
        Assert.hasText(name, "name cannot be null/empty.");
        Assert.notNull(parameterTypes, "parameterTypes cannot be null.");
        return new PropertyPath(new Node(leaf, ElementKind.METHOD, name, null, List.of(parameterTypes), null));
    }

    /**
     * Appends a constructor node: the first node of a path reported by executable validation of a
     * constructor. Named after the class, as the specification names it.
     *
     * @param name the simple name of the constructed class.
     * @param parameterTypes the constructor's parameter types.
     * @return the extended path.
     */
    public PropertyPath constructor(String name, Class<?>... parameterTypes) {
        Assert.hasText(name, "name cannot be null/empty.");
        Assert.notNull(parameterTypes, "parameterTypes cannot be null.");
        return new PropertyPath(new Node(leaf, ElementKind.CONSTRUCTOR, name, null, List.of(parameterTypes), null));
    }

    /**
     * Appends a parameter node.
     *
     * @param name the parameter name.
     * @param index the parameter index.
     * @return the extended path.
     */
    public PropertyPath parameter(String name, int index) {
        Assert.hasText(name, "name cannot be null/empty.");
        Assert.isTrue(index >= 0, "index cannot be negative.");
        return new PropertyPath(new Node(leaf, ElementKind.PARAMETER, name, null, null, index));
    }

    /**
     * Appends the return value node, {@value #RETURN_VALUE_NODE}.
     *
     * @return the extended path.
     */
    public PropertyPath returnValue() {
        return new PropertyPath(new Node(leaf, ElementKind.RETURN_VALUE, RETURN_VALUE_NODE, null, null, null));
    }

    /**
     * Appends the cross-parameter node, {@value #CROSS_PARAMETER_NODE}.
     *
     * @return the extended path.
     */
    public PropertyPath crossParameter() {
        return new PropertyPath(new Node(leaf, ElementKind.CROSS_PARAMETER, CROSS_PARAMETER_NODE, null, null, null));
    }

    /**
     * Marks the leaf as a container addressed by index (List, array): renders {@code leaf[index]}.
     *
     * @param index the index of the contents.
     * @param containerClass the container type.
     * @param typeArgumentIndex the type argument the contents are taken from.
     * @return a path whose leaf carries the subscript.
     */
    public PropertyPath withIndex(int index, Class<?> containerClass, int typeArgumentIndex) {
        return withSubscript(new Subscript(index, null, containerClass, typeArgumentIndex));
    }

    /**
     * Marks the leaf as a container addressed by key (Map): renders {@code leaf[key]}.
     *
     * @param key the key of the contents.
     * @param containerClass the container type.
     * @param typeArgumentIndex the type argument the contents are taken from.
     * @return a path whose leaf carries the subscript.
     */
    public PropertyPath withKey(Object key, Class<?> containerClass, int typeArgumentIndex) {
        return withSubscript(new Subscript(null, key, containerClass, typeArgumentIndex));
    }

    /**
     * Marks the leaf as a container without index or key (Set, Iterable): renders {@code leaf[]}.
     *
     * @param containerClass the container type.
     * @param typeArgumentIndex the type argument the contents are taken from.
     * @return a path whose leaf carries the subscript.
     */
    public PropertyPath inIterable(Class<?> containerClass, int typeArgumentIndex) {
        return withSubscript(new Subscript(null, null, containerClass, typeArgumentIndex));
    }

    private PropertyPath withSubscript(Subscript subscript) {
        Assert.isTrue(leaf != null, "the root path cannot carry a subscript.");
        return new PropertyPath(new Node(leaf.parent, leaf.kind, leaf.name, subscript, leaf.parameterTypes, leaf.parameterIndex));
    }

    /**
     * Whether this is the empty root path.
     *
     * @return true for the root path.
     */
    public boolean isRoot() {
        return leaf == null;
    }

    /**
     * The path without its leaf node.
     *
     * @return the parent path; the root path for a single-node path.
     * @throws IllegalArgumentException on the root path.
     */
    public PropertyPath getParent() {
        Assert.isTrue(leaf != null, "the root path has no parent.");
        return leaf.parent == null ? ROOT : new PropertyPath(leaf.parent);
    }

    /**
     * The last node of the path.
     *
     * @return the leaf node; null for the root path.
     */
    public Node getLeafNode() {
        return leaf;
    }

    /**
     * The nodes from the root to the leaf.
     *
     * @return the nodes; empty for the root path.
     */
    public List<Node> getNodes() {
        if (leaf == null) {
            return Collections.emptyList();
        }
        Deque<Node> result = new ArrayDeque<>();
        for (Node node = leaf; node != null; node = node.parent) {
            result.addFirst(node);
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    @Override
    public Iterator<Path.Node> iterator() {
        return Collections.<Path.Node>unmodifiableList(getNodes()).iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyPath that = (PropertyPath) o;
        return Objects.equals(leaf, that.leaf);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(leaf);
    }

    /**
     * The path in Bean Validation notation: nodes joined with dots, each node rendered as its name
     * followed by its subscript; the nameless bean node contributes nothing.
     *
     * @return the rendered path; empty for the root path.
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Node node : getNodes()) {
            String text = node.toString();
            if (text.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(SEPARATOR);
            }
            result.append(text);
        }
        return result.toString();
    }

    /**
     * How the contents of a node are addressed: by index, by key, or neither (plain iterable).
     */
    private record Subscript(Integer index, Object key, Class<?> containerClass, Integer typeArgumentIndex) {

        private String render() {
            if (key != null) {
                return "[" + key + "]";
            }
            if (index != null) {
                return "[" + index + "]";
            }
            return "[]";
        }
    }

    /**
     * A node of the path. One class serves every kind we produce; {@link #as(Class)} checks the
     * requested view against {@link #getKind()} per the spec.
     */
    public static final class Node implements Path.PropertyNode, Path.ContainerElementNode, Path.BeanNode, Path.MethodNode,
            Path.ConstructorNode, Path.ParameterNode, Path.ReturnValueNode, Path.CrossParameterNode {

        private final Node parent;
        private final ElementKind kind;
        private final String name;
        private final Subscript subscript;
        private final List<Class<?>> parameterTypes;
        private final Integer parameterIndex;

        private Node(Node parent, ElementKind kind, String name, Subscript subscript, List<Class<?>> parameterTypes, Integer parameterIndex) {
            super();
            this.parent = parent;
            this.kind = kind;
            this.name = name;
            this.subscript = subscript;
            this.parameterTypes = parameterTypes;
            this.parameterIndex = parameterIndex;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ElementKind getKind() {
            return kind;
        }

        /**
         * Whether this node's value sits inside a container (its parent carries a subscript).
         */
        @Override
        public boolean isInIterable() {
            return parent != null && parent.subscript != null;
        }

        @Override
        public Integer getIndex() {
            return isInIterable() ? parent.subscript.index() : null;
        }

        @Override
        public Object getKey() {
            return isInIterable() ? parent.subscript.key() : null;
        }

        @Override
        public Class<?> getContainerClass() {
            return isInIterable() ? parent.subscript.containerClass() : null;
        }

        @Override
        public Integer getTypeArgumentIndex() {
            return isInIterable() ? parent.subscript.typeArgumentIndex() : null;
        }

        /**
         * The parameter types of a method or constructor node.
         *
         * @return the types; null for the other kinds.
         */
        @Override
        public List<Class<?>> getParameterTypes() {
            return parameterTypes;
        }

        /**
         * The index of a parameter node.
         *
         * @return the index; -1 for the other kinds.
         */
        @Override
        public int getParameterIndex() {
            return parameterIndex != null ? parameterIndex : -1;
        }

        /**
         * Whether this node's own contents carry a subscript (it is the container side of
         * {@code name[index]}).
         *
         * @return true when a subscript is set on this node.
         */
        public boolean isIterable() {
            return subscript != null;
        }

        @Override
        public <T extends Path.Node> T as(Class<T> nodeType) {
            Assert.notNull(nodeType, "nodeType cannot be null.");
            boolean compatible = nodeType == Path.Node.class
                    || nodeType == Node.class
                    || (nodeType == Path.PropertyNode.class && kind == ElementKind.PROPERTY)
                    || (nodeType == Path.ContainerElementNode.class && kind == ElementKind.CONTAINER_ELEMENT)
                    || (nodeType == Path.BeanNode.class && kind == ElementKind.BEAN)
                    || (nodeType == Path.MethodNode.class && kind == ElementKind.METHOD)
                    || (nodeType == Path.ConstructorNode.class && kind == ElementKind.CONSTRUCTOR)
                    || (nodeType == Path.ParameterNode.class && kind == ElementKind.PARAMETER)
                    || (nodeType == Path.ReturnValueNode.class && kind == ElementKind.RETURN_VALUE)
                    || (nodeType == Path.CrossParameterNode.class && kind == ElementKind.CROSS_PARAMETER);
            if (!compatible) {
                throw new ClassCastException("node [" + this + "] of kind [" + kind + "] cannot be viewed as ["
                        + nodeType.getName() + "]");
            }
            return nodeType.cast(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node that = (Node) o;
            return kind == that.kind && Objects.equals(name, that.name) && Objects.equals(subscript, that.subscript)
                    && Objects.equals(parameterTypes, that.parameterTypes) && Objects.equals(parameterIndex, that.parameterIndex)
                    && Objects.equals(parent, that.parent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parent, kind, name, subscript, parameterTypes, parameterIndex);
        }

        /**
         * The node as it appears in the path: its name followed by its subscript, if any.
         */
        @Override
        public String toString() {
            String result = name != null ? name : "";
            return subscript != null ? result + subscript.render() : result;
        }
    }

    /**
     * Compares two parameter type arrays, for callers matching nodes against executables.
     *
     * @param first the first array.
     * @param second the second array.
     * @return true if equal element for element.
     */
    public static boolean sameParameterTypes(Class<?>[] first, Class<?>[] second) {
        return Arrays.equals(first, second);
    }
}
