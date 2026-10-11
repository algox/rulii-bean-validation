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

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import jakarta.validation.valueextraction.ValueExtractorDefinitionException;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.extract.standard.IterableValueExtractor;
import org.rulii.validation.beans.extract.standard.ListValueExtractor;
import org.rulii.validation.beans.extract.standard.MapKeyValueExtractor;
import org.rulii.validation.beans.extract.standard.MapValueValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalIntValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalValueExtractor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ValueExtractorDescriptor}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValueExtractorDescriptorTest {

    public ValueExtractorDescriptorTest() {
        super();
    }

    @Test
    public void builtInDescriptors() {
        ValueExtractorDescriptor list = ValueExtractorDescriptor.of(new ListValueExtractor());
        assertEquals(List.class, list.getContainerType());
        assertEquals(0, list.getTypeArgumentIndex());
        assertNull(list.getExtractedType());
        assertFalse(list.isUnwrapByDefault());

        assertEquals(Iterable.class, ValueExtractorDescriptor.of(new IterableValueExtractor()).getContainerType());
        assertEquals(0, ValueExtractorDescriptor.of(new MapKeyValueExtractor()).getTypeArgumentIndex());
        assertEquals(1, ValueExtractorDescriptor.of(new MapValueValueExtractor()).getTypeArgumentIndex());
        assertEquals(Optional.class, ValueExtractorDescriptor.of(new OptionalValueExtractor()).getContainerType());

        ValueExtractorDescriptor optionalInt = ValueExtractorDescriptor.of(new OptionalIntValueExtractor());
        assertEquals(OptionalInt.class, optionalInt.getContainerType());
        assertEquals(ValueExtractorDescriptor.CONTAINER, optionalInt.getTypeArgumentIndex());
        assertEquals(Integer.class, optionalInt.getExtractedType());
        assertTrue(optionalInt.isUnwrapByDefault());
    }

    @Test
    public void customExtractor() {
        ValueExtractor<?> extractor = new PairSecondExtractor();
        ValueExtractorDescriptor descriptor = ValueExtractorDescriptor.of(extractor);
        assertSame(extractor, descriptor.getExtractor());
        assertEquals(Pair.class, descriptor.getContainerType());
        assertEquals(1, descriptor.getTypeArgumentIndex());
        assertEquals(Number.class, descriptor.getExtractedType());
        assertTrue(descriptor.toString().contains("Pair"));
    }

    @Test
    public void containerDeclaredOnSuperclassOrSubInterface() {
        assertEquals(List.class, ValueExtractorDescriptor.of(new ViaSuperclass()).getContainerType());
        assertEquals(Map.class, ValueExtractorDescriptor.of(new ViaSubInterface()).getContainerType());
        assertEquals(1, ValueExtractorDescriptor.of(new ViaSubInterface()).getTypeArgumentIndex());
    }

    @Test
    public void invalidDeclarations() {
        assertThrows(ValueExtractorDefinitionException.class, () -> ValueExtractorDescriptor.of(new RawExtractor()));
        assertThrows(ValueExtractorDefinitionException.class, () -> ValueExtractorDescriptor.of(new NoMarker()));
        assertThrows(ValueExtractorDefinitionException.class, () -> ValueExtractorDescriptor.of(new TwoMarkers()));
        assertThrows(ValueExtractorDefinitionException.class, () -> ValueExtractorDescriptor.of(new ContainerMarkerWithoutType()));
        assertThrows(IllegalArgumentException.class, () -> ValueExtractorDescriptor.of(null));
    }

    public static class Pair<A, B> {
        final A first;
        final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    static class PairSecondExtractor implements ValueExtractor<Pair<?, @ExtractedValue Number>> {
        @Override
        public void extractValues(Pair<?, Number> originalValue, ValueReceiver receiver) {
            receiver.value("second", originalValue.second);
        }
    }

    static abstract class ListBase implements ValueExtractor<List<@ExtractedValue ?>> {}

    static class ViaSuperclass extends ListBase {
        @Override
        public void extractValues(List<?> originalValue, ValueReceiver receiver) {}
    }

    interface MapValueExtractorApi extends ValueExtractor<Map<?, @ExtractedValue ?>> {}

    static class ViaSubInterface implements MapValueExtractorApi {
        @Override
        public void extractValues(Map<?, ?> originalValue, ValueReceiver receiver) {}
    }

    @SuppressWarnings("rawtypes")
    static class RawExtractor implements ValueExtractor {
        @Override
        public void extractValues(Object originalValue, ValueReceiver receiver) {}
    }

    static class NoMarker implements ValueExtractor<List<?>> {
        @Override
        public void extractValues(List<?> originalValue, ValueReceiver receiver) {}
    }

    static class TwoMarkers implements ValueExtractor<Map<@ExtractedValue ?, @ExtractedValue ?>> {
        @Override
        public void extractValues(Map<?, ?> originalValue, ValueReceiver receiver) {}
    }

    static class ContainerMarkerWithoutType implements ValueExtractor<@ExtractedValue OptionalInt> {
        @Override
        public void extractValues(OptionalInt originalValue, ValueReceiver receiver) {}
    }
}
