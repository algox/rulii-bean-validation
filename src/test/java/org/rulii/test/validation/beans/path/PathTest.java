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
package org.rulii.test.validation.beans.path;

import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.path.Path;
import org.rulii.validation.beans.path.PathNodeKind;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the parent-linked {@link Path} model and its Bean Validation notation
 * rendering.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class PathTest {

    public PathTest() {
        super();
    }

    @Test
    public void rootRendersEmpty() {
        assertTrue(Path.root().isRoot());
        assertEquals("", Path.root().render());
        assertNull(Path.root().getParent());
    }

    @Test
    public void propertyChainRendering() {
        Path path = Path.root().property("address").property("city");
        assertEquals("address.city", path.render());
        assertEquals(PathNodeKind.PROPERTY, path.getKind());
        assertEquals("city", path.getName());
        assertEquals("address", path.getParent().getName());
    }

    @Test
    public void indexedElementRendering() {
        Path path = Path.root().property("orders").index(2).property("amount");
        assertEquals("orders[2].amount", path.render());
    }

    @Test
    public void keyedElementRendering() {
        Path path = Path.root().property("contacts").key("home").property("phone");
        assertEquals("contacts[home].phone", path.render());
    }

    @Test
    public void iterableElementRendering() {
        Path path = Path.root().property("tags").iterable();
        assertEquals("tags[]", path.render());
    }

    @Test
    public void nestedContainerRendering() {
        Path path = Path.root().property("matrix").index(0).index(1);
        assertEquals("matrix[0][1]", path.render());
    }

    @Test
    public void firstPropertyHasNoLeadingDot() {
        assertEquals("name", Path.root().property("name").render());
    }

    @Test
    public void toStringMatchesRender() {
        Path path = Path.root().property("orders").index(2);
        assertEquals(path.render(), path.toString());
    }

    @Test
    public void equalityIsStructural() {
        Path first = Path.root().property("orders").index(2).property("amount");
        Path second = Path.root().property("orders").index(2).property("amount");
        Path different = Path.root().property("orders").index(3).property("amount");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    public void captureIsAReference() {
        Path base = Path.root().property("orders").index(2);
        Path extended = base.property("amount");

        // Extending never mutates the captured path.
        assertEquals("orders[2]", base.render());
        assertEquals("orders[2].amount", extended.render());
        assertSame(base, extended.getParent());
    }
}
