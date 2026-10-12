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
package org.rulii.test.validation.beans.extract;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.extract.standard.IterableValueExtractor;
import org.rulii.validation.beans.extract.standard.ListValueExtractor;
import org.rulii.validation.beans.extract.standard.MapKeyValueExtractor;
import org.rulii.validation.beans.extract.standard.MapValueValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalIntValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalValueExtractor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ValueExtractorRegistry} resolution.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValueExtractorRegistryTest {

    public ValueExtractorRegistryTest() {
        super();
    }

    @Test
    public void standardRegistryHasTheBuiltIns() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();
        assertEquals(8, registry.getDescriptors().size());
        assertTrue(ValueExtractorRegistry.builder(false).build().getDescriptors().isEmpty());
    }

    @Test
    public void exactContainerTypes() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();
        assertInstanceOf(ListValueExtractor.class, registry.find(List.class, 0).getExtractor());
        assertInstanceOf(IterableValueExtractor.class, registry.find(Iterable.class, 0).getExtractor());
        assertInstanceOf(MapKeyValueExtractor.class, registry.find(Map.class, 0).getExtractor());
        assertInstanceOf(MapValueValueExtractor.class, registry.find(Map.class, 1).getExtractor());
        assertInstanceOf(OptionalValueExtractor.class, registry.find(Optional.class, 0).getExtractor());
        assertInstanceOf(OptionalIntValueExtractor.class,
                registry.find(OptionalInt.class, ValueExtractorDescriptor.CONTAINER).getExtractor());
    }

    @Test
    public void subtypesResolveToTheMostSpecificExtractor() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();
        // ArrayList is both an Iterable and a List: List wins.
        assertInstanceOf(ListValueExtractor.class, registry.find(ArrayList.class, 0).getExtractor());
        // Set / Collection are only Iterables.
        assertInstanceOf(IterableValueExtractor.class, registry.find(Set.class, 0).getExtractor());
        assertInstanceOf(IterableValueExtractor.class, registry.find(HashSet.class, 0).getExtractor());
        assertInstanceOf(IterableValueExtractor.class, registry.find(Collection.class, 0).getExtractor());
        assertInstanceOf(MapKeyValueExtractor.class, registry.find(HashMap.class, 0).getExtractor());
        assertInstanceOf(MapValueValueExtractor.class, registry.find(LinkedHashMap.class, 1).getExtractor());
    }

    @Test
    public void typeArgumentPositionsAreMappedThroughTheHierarchy() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();
        // class StringKeyed<A> extends HashMap<String, A>: its position 0 is Map's value.
        assertInstanceOf(MapValueValueExtractor.class, registry.find(StringKeyed.class, 0).getExtractor());
        // class Swapped<X, Y> implements Map<Y, X>: position 0 is Map's value, position 1 is Map's key.
        assertInstanceOf(MapValueValueExtractor.class, registry.find(Swapped.class, 0).getExtractor());
        assertInstanceOf(MapKeyValueExtractor.class, registry.find(Swapped.class, 1).getExtractor());
        // Out-of-range positions resolve to nothing.
        assertNull(registry.find(StringKeyed.class, 1));
        assertNull(registry.find(List.class, 1));
    }

    @Test
    public void unknownContainersResolveToNothing() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();
        assertNull(registry.find(ValueExtractorDescriptorTest.Pair.class, 0));
        assertNull(registry.find(String[].class, 0));
        assertNull(registry.find(String.class, 0));
        assertNull(registry.find(List.class, ValueExtractorDescriptor.CONTAINER));
        assertNull(registry.find(OptionalInt.class, 0));
    }

    @Test
    public void customExtractorIsResolved() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder()
                .extractor(new ValueExtractorDescriptorTest.PairSecondExtractor())
                .build();
        assertInstanceOf(ValueExtractorDescriptorTest.PairSecondExtractor.class,
                registry.find(ValueExtractorDescriptorTest.Pair.class, 1).getExtractor());
        assertNull(registry.find(ValueExtractorDescriptorTest.Pair.class, 0));
    }

    @Test
    public void laterRegistrationReplacesEarlierForSamePosition() {
        ListValueExtractor custom = new ListValueExtractor();
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().extractor(custom).build();
        assertEquals(8, registry.getDescriptors().size());
        assertSame(custom, registry.find(List.class, 0).getExtractor());
        assertSame(custom, registry.find(ArrayList.class, 0).getExtractor());
    }

    @Test
    public void ambiguousExtractorsFailFast() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder()
                .extractor(new TaggedExtractor())
                .build();
        // TaggedList is a List and a Tagged; neither extractor is more specific than the other.
        ConstraintDeclarationException e = assertThrows(ConstraintDeclarationException.class,
                () -> registry.find(TaggedList.class, 0));
        assertTrue(e.getMessage().contains("ambiguous"));
        // Resolution of a plain List is unaffected.
        assertInstanceOf(ListValueExtractor.class, registry.find(List.class, 0).getExtractor());
    }

    @Test
    public void nullContainerTypeIsRejected() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();
        assertThrows(IllegalArgumentException.class, () -> registry.find(null, 0));
        assertThrows(IllegalArgumentException.class, () -> registry.find(null));
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    @Test
    public void allExtractorsOfAContainerType() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();

        List<ValueExtractorDescriptor> optional = registry.find(Optional.class);
        assertEquals(1, optional.size());
        assertInstanceOf(OptionalValueExtractor.class, optional.get(0).getExtractor());
        assertFalse(optional.get(0).isUnwrapByDefault());

        List<ValueExtractorDescriptor> optionalInt = registry.find(OptionalInt.class);
        assertEquals(1, optionalInt.size());
        assertEquals(ValueExtractorDescriptor.CONTAINER, optionalInt.get(0).getTypeArgumentIndex());
        assertTrue(optionalInt.get(0).isUnwrapByDefault());

        assertEquals(2, registry.find(Map.class).size());
        assertEquals(2, registry.find(LinkedHashMap.class).size());
        assertTrue(registry.find(String.class).isEmpty());
        assertTrue(registry.find(Object.class).isEmpty());
    }

    @Test
    public void allExtractorsKeepOnlyTheMostSpecificPerPosition() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();

        // List and Iterable both serve position 0 of an ArrayList: List wins, one entry.
        List<ValueExtractorDescriptor> list = registry.find(ArrayList.class);
        assertEquals(1, list.size());
        assertInstanceOf(ListValueExtractor.class, list.get(0).getExtractor());

        // A Set is only an Iterable.
        List<ValueExtractorDescriptor> set = registry.find(HashSet.class);
        assertEquals(1, set.size());
        assertInstanceOf(IterableValueExtractor.class, set.get(0).getExtractor());
    }

    @Test
    public void allExtractorsReportsDistinctPositionsOfDifferentSupertypes() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().extractor(new TaggedExtractor()).build();
        // TaggedList<E> is a List<E> and a Tagged<E>: both serve the same position with unrelated
        // container types, so neither dominates and both are reported; the position lookup is the
        // one that flags that ambiguity.
        assertEquals(2, registry.find(TaggedList.class).size());
        assertThrows(ConstraintDeclarationException.class, () -> registry.find(TaggedList.class, 0));
    }

    @Test
    public void allExtractorsIsCachedAndInvalidatedOnRegistration() {
        ValueExtractorRegistry registry = ValueExtractorRegistry.builder().build();
        assertSame(registry.find(Optional.class), registry.find(Optional.class));
        assertTrue(registry.find(Tagged.class).isEmpty());
        registry.register(new TaggedExtractor());
        assertEquals(1, registry.find(Tagged.class).size());
    }

    @SuppressWarnings("serial")
    static class StringKeyed<A> extends HashMap<String, A> {}

    interface Swapped<X, Y> extends Map<Y, X> {}

    interface Tagged<T> {}

    interface TaggedList<E> extends List<E>, Tagged<E> {}

    static class TaggedExtractor implements ValueExtractor<Tagged<@ExtractedValue ?>> {
        @Override
        public void extractValues(Tagged<?> originalValue, ValueReceiver receiver) {}
    }
}
