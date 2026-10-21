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
package org.rulii.test.validation.beans;

import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.PropertyPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PropertyPath renders the Bean Validation notation and exposes the spec node view.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class PropertyPathTest {

    public PropertyPathTest() {
        super();
    }

    @Test
    public void rootIsEmpty() {
        PropertyPath root = PropertyPath.root();
        assertTrue(root.isRoot());
        assertEquals("", root.toString());
        assertNull(root.getLeafNode());
        assertTrue(root.getNodes().isEmpty());
        assertFalse(root.iterator().hasNext());
        assertSame(root, PropertyPath.root());
        assertThrows(IllegalArgumentException.class, root::getParent);
        assertThrows(IllegalArgumentException.class, () -> root.withIndex(0, List.class, 0));
    }

    @Test
    public void rendersSpecNotation() {
        PropertyPath root = PropertyPath.root();
        assertEquals("name", root.property("name").toString());
        assertEquals("address.city", root.property("address").property("city").toString());
        assertEquals("orders[2].amount", root.property("orders").withIndex(2, List.class, 0).property("amount").toString());
        assertEquals("tags[2].<list element>", root.property("tags").withIndex(2, List.class, 0).element("<list element>").toString());
        assertEquals("contacts[home].<map key>", root.property("contacts").withKey("home", Map.class, 0).element("<map key>").toString());
        assertEquals("contacts[home].<map value>", root.property("contacts").withKey("home", Map.class, 1).element("<map value>").toString());
        assertEquals("tags[].<iterable element>", root.property("tags").inIterable(Set.class, 0).element("<iterable element>").toString());
        assertEquals("matrix[1].<list element>[2].<list element>", root.property("matrix").withIndex(1, List.class, 0)
                .element("<list element>").withIndex(2, List.class, 0).element("<list element>").toString());
    }

    @Test
    public void beanNodeRendersNothing() {
        PropertyPath root = PropertyPath.root();
        assertEquals("", root.bean().toString());
        assertEquals("address", root.property("address").bean().toString());
        assertEquals("orders[2]", root.property("orders").withIndex(2, List.class, 0).bean().toString());
        assertEquals(ElementKind.BEAN, root.property("address").bean().getLeafNode().getKind());
        assertNull(root.bean().getLeafNode().getName());
        assertEquals(1, root.bean().getNodes().size());
    }

    @Test
    public void nodesExposeTheSpecView() {
        PropertyPath path = PropertyPath.root().property("orders").withIndex(2, List.class, 0).property("amount");
        List<PropertyPath.Node> nodes = path.getNodes();
        assertEquals(2, nodes.size());

        PropertyPath.Node orders = nodes.get(0);
        assertEquals("orders", orders.getName());
        assertEquals(ElementKind.PROPERTY, orders.getKind());
        assertFalse(orders.isInIterable());
        assertTrue(orders.isIterable());
        assertNull(orders.getIndex());
        assertNull(orders.getKey());
        assertNull(orders.getContainerClass());
        assertNull(orders.getTypeArgumentIndex());
        assertEquals("orders[2]", orders.toString());

        PropertyPath.Node amount = nodes.get(1);
        assertEquals("amount", amount.getName());
        assertTrue(amount.isInIterable());
        assertFalse(amount.isIterable());
        assertEquals(2, amount.getIndex());
        assertNull(amount.getKey());
        assertEquals(List.class, amount.getContainerClass());
        assertEquals(0, amount.getTypeArgumentIndex());
        assertEquals("amount", amount.toString());
        assertSame(amount, path.getLeafNode());

        PropertyPath.Node key = PropertyPath.root().property("m").withKey("k", Map.class, 0).element("<map key>").getLeafNode();
        assertEquals("k", key.getKey());
        assertNull(key.getIndex());
        assertEquals(ElementKind.CONTAINER_ELEMENT, key.getKind());
        assertEquals(0, key.getTypeArgumentIndex());

        PropertyPath.Node iterable = PropertyPath.root().property("s").inIterable(Set.class, 0).element("<iterable element>").getLeafNode();
        assertTrue(iterable.isInIterable());
        assertNull(iterable.getIndex());
        assertNull(iterable.getKey());
    }

    @Test
    public void iteratesAsAJakartaPath() {
        Path path = PropertyPath.root().property("orders").withIndex(2, List.class, 0).property("amount");
        List<String> names = new ArrayList<>();
        for (Path.Node node : path) {
            names.add(node.getName());
        }
        assertEquals(List.of("orders", "amount"), names);
        assertEquals("orders[2].amount", path.toString());
    }

    @Test
    public void asChecksTheKind() {
        PropertyPath.Node property = PropertyPath.root().property("name").getLeafNode();
        PropertyPath.Node element = PropertyPath.root().property("tags").withIndex(0, List.class, 0).element("<list element>").getLeafNode();
        PropertyPath.Node bean = PropertyPath.root().bean().getLeafNode();

        assertSame(property, property.as(Path.Node.class));
        assertSame(property, property.as(Path.PropertyNode.class));
        assertSame(property, property.as(PropertyPath.Node.class));
        assertThrows(ClassCastException.class, () -> property.as(Path.ContainerElementNode.class));
        assertThrows(ClassCastException.class, () -> property.as(Path.BeanNode.class));
        assertThrows(ClassCastException.class, () -> property.as(Path.MethodNode.class));

        assertSame(element, element.as(Path.ContainerElementNode.class));
        assertThrows(ClassCastException.class, () -> element.as(Path.PropertyNode.class));

        assertSame(bean, bean.as(Path.BeanNode.class));
        assertThrows(ClassCastException.class, () -> bean.as(Path.PropertyNode.class));
        assertThrows(IllegalArgumentException.class, () -> bean.as(null));
    }

    @Test
    public void parentAndSharing() {
        PropertyPath orders = PropertyPath.root().property("orders");
        PropertyPath first = orders.withIndex(0, List.class, 0).property("code");
        PropertyPath second = orders.withIndex(1, List.class, 0).property("code");

        assertEquals("orders[0].code", first.toString());
        assertEquals("orders[1].code", second.toString());
        assertEquals("orders", orders.toString());
        // The subscripted leaf is a sibling copy of "orders", not a child of it.
        assertEquals("orders[0]", first.getParent().toString());
        assertEquals("orders", first.getParent().getLeafNode().getName());
        assertEquals(PropertyPath.root(), first.getParent().getParent());
        assertEquals(PropertyPath.root(), orders.getParent());
        assertNotEquals(orders, first.getParent());
    }

    @Test
    public void equalityIsStructural() {
        PropertyPath a = PropertyPath.root().property("orders").withIndex(0, List.class, 0).property("code");
        PropertyPath b = PropertyPath.root().property("orders").withIndex(0, List.class, 0).property("code");
        PropertyPath c = PropertyPath.root().property("orders").withIndex(1, List.class, 0).property("code");
        PropertyPath d = PropertyPath.root().property("orders").property("code");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(PropertyPath.root().property("x"), PropertyPath.root().element("x"));
        assertEquals(PropertyPath.root(), PropertyPath.root().property("x").getParent());
        assertEquals(a.getLeafNode(), b.getLeafNode());
        assertNotEquals(a.getLeafNode(), c.getLeafNode());
    }

    @Test
    public void rejectsBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> PropertyPath.root().property(null));
        assertThrows(IllegalArgumentException.class, () -> PropertyPath.root().property(" "));
        assertThrows(IllegalArgumentException.class, () -> PropertyPath.root().element(""));
    }
}
