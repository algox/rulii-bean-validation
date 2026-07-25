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
package org.rulii.test.validation.beans.metadata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.metadata.BeanMetadata;
import org.rulii.validation.beans.metadata.BeanMetadataReader;
import org.rulii.validation.beans.metadata.ContainerElement;
import org.rulii.validation.beans.metadata.ContainerKind;
import org.rulii.validation.beans.metadata.ContainerMetadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for container-element trees: collections, maps (keys and values), Optional,
 * arrays, nesting, cascading elements, and generic type-variable resolution through
 * concrete subclasses.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ContainerMetadataTest {

    public ContainerMetadataTest() {
        super();
    }

    private final BeanMetadataReader reader = BeanMetadataReader.builder()
            .mapperRegistry(TestConstraintMappers.registry())
            .build();

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public record Item(@NotBlank String sku) {
    }

    public static class Basket {
        private List<@NotBlank String> tags = List.of();
        private Map<@NotBlank String, @Positive Integer> quantities = Map.of();
        private Optional<@NotBlank String> note = Optional.empty();
        private List<@Valid Item> items = List.of();
        private List<List<@Size(max = 5) String>> matrix = List.of();
        private @NotBlank String[] codes = {};
        private List<String> plain = List.of();

        public Basket() {
            super();
        }
    }

    public static class Wrapper<T> {
        private List<@NotBlank T> entries = List.of();

        public Wrapper() {
            super();
        }
    }

    public static class StringWrapper extends Wrapper<String> {

        public StringWrapper() {
            super();
        }
    }

    // -----------------------------------------------------------------------
    // Container kinds
    // -----------------------------------------------------------------------

    @Test
    public void listElementConstraints() {
        ContainerMetadata container = container("tags");

        assertEquals(ContainerKind.COLLECTION, container.getKind());
        ContainerElement element = container.getElement();
        assertEquals(1, element.getConstraints().size());
        assertEquals(NotBlank.class, element.getConstraints().get(0).getAnnotationType());
        assertEquals(String.class, element.getElementType());
        assertFalse(element.isCascaded());
    }

    @Test
    public void mapKeyAndValueConstraints() {
        ContainerMetadata container = container("quantities");

        assertEquals(ContainerKind.MAP, container.getKind());
        assertEquals(NotBlank.class, container.getKeyElement().getConstraints().get(0).getAnnotationType());
        assertEquals(Positive.class, container.getValueElement().getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void optionalContents() {
        ContainerMetadata container = container("note");

        assertEquals(ContainerKind.OPTIONAL, container.getKind());
        assertEquals(1, container.getElement().getConstraints().size());
    }

    @Test
    public void cascadedElement() {
        ContainerMetadata container = container("items");

        assertTrue(container.getElement().isCascaded());
        assertEquals(Item.class, container.getElement().getElementType());
    }

    @Test
    public void nestedContainers() {
        ContainerMetadata outer = container("matrix");

        assertEquals(ContainerKind.COLLECTION, outer.getKind());
        assertTrue(outer.getElement().getConstraints().isEmpty());
        ContainerMetadata inner = outer.getElement().getContainer();
        assertNotNull(inner);
        assertEquals(ContainerKind.COLLECTION, inner.getKind());
        assertEquals(Size.class, inner.getElement().getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void arrayComponentConstraints() {
        ContainerMetadata container = container("codes");

        assertEquals(ContainerKind.ARRAY, container.getKind());
        assertEquals(NotBlank.class, container.getElement().getConstraints().get(0).getAnnotationType());
    }

    @Test
    public void arrayComponentAnnotationNotDoubleCountedAsFieldConstraint() {
        BeanMetadata metadata = reader.read(Basket.class);

        // The mixed-target @NotBlank on the array belongs to the component, not the field.
        assertTrue(metadata.getProperty("codes").getSources().isEmpty());
    }

    @Test
    public void unconstrainedContainerHasNoTree() {
        BeanMetadata metadata = reader.read(Basket.class);

        assertNull(metadata.getProperty("plain").getContainer());
    }

    @Test
    public void containerCascadeCountsAsCascade() {
        BeanMetadata metadata = reader.read(Basket.class);

        assertTrue(metadata.hasCascades());
        assertFalse(metadata.getProperty("items").isCascaded());
    }

    // -----------------------------------------------------------------------
    // Generic resolution (statically resolvable type variables)
    // -----------------------------------------------------------------------

    @Test
    public void typeVariableResolvedThroughConcreteSubclass() {
        BeanMetadata metadata = reader.read(StringWrapper.class);
        ContainerMetadata container = metadata.getProperty("entries").getContainer();

        assertNotNull(container);
        assertEquals(String.class, container.getElement().getElementType());
        assertEquals(NotBlank.class, container.getElement().getConstraints().get(0).getAnnotationType());
    }

    private ContainerMetadata container(String property) {
        BeanMetadata metadata = reader.read(Basket.class);
        ContainerMetadata result = metadata.getProperty(property).getContainer();
        assertNotNull(result, "expected container tree on [" + property + "]");
        return result;
    }
}
