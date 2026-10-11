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

import jakarta.validation.valueextraction.ValueExtractor;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.extract.standard.IterableValueExtractor;
import org.rulii.validation.beans.extract.standard.ListValueExtractor;
import org.rulii.validation.beans.extract.standard.MapKeyValueExtractor;
import org.rulii.validation.beans.extract.standard.MapValueValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalDoubleValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalIntValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalLongValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalValueExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the built-in value extractors: what they hand to the receiver.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class StandardValueExtractorsTest {

    public StandardValueExtractorsTest() {
        super();
    }

    /** Records every callback as "kind:node:key/index:value". */
    private static final class Recorder implements ValueExtractor.ValueReceiver {
        final List<String> calls = new ArrayList<>();

        @Override public void value(String nodeName, Object object) { calls.add("value:" + nodeName + ":" + object); }
        @Override public void iterableValue(String nodeName, Object object) { calls.add("iterable:" + nodeName + ":" + object); }
        @Override public void indexedValue(String nodeName, int i, Object object) { calls.add("indexed:" + nodeName + ":" + i + ":" + object); }
        @Override public void keyedValue(String nodeName, Object key, Object object) { calls.add("keyed:" + nodeName + ":" + key + ":" + object); }
    }

    @Test
    public void iterable() {
        Recorder recorder = new Recorder();
        new IterableValueExtractor().extractValues(Set.of("a"), recorder);
        assertEquals(List.of("iterable:<iterable element>:a"), recorder.calls);
    }

    @Test
    public void list() {
        Recorder recorder = new Recorder();
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add(null);
        new ListValueExtractor().extractValues(list, recorder);
        assertEquals(List.of("indexed:<list element>:0:a", "indexed:<list element>:1:null"), recorder.calls);
    }

    @Test
    public void mapKeysAndValues() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("x", 1);
        map.put("y", null);

        Recorder keys = new Recorder();
        new MapKeyValueExtractor().extractValues(map, keys);
        assertEquals(List.of("keyed:<map key>:x:x", "keyed:<map key>:y:y"), keys.calls);

        Recorder values = new Recorder();
        new MapValueValueExtractor().extractValues(map, values);
        assertEquals(List.of("keyed:<map value>:x:1", "keyed:<map value>:y:null"), values.calls);
    }

    @Test
    public void optionals() {
        Recorder recorder = new Recorder();
        new OptionalValueExtractor().extractValues(Optional.of("a"), recorder);
        new OptionalValueExtractor().extractValues(Optional.empty(), recorder);
        new OptionalIntValueExtractor().extractValues(OptionalInt.of(1), recorder);
        new OptionalIntValueExtractor().extractValues(OptionalInt.empty(), recorder);
        new OptionalLongValueExtractor().extractValues(OptionalLong.of(2L), recorder);
        new OptionalLongValueExtractor().extractValues(OptionalLong.empty(), recorder);
        new OptionalDoubleValueExtractor().extractValues(OptionalDouble.of(3.5), recorder);
        new OptionalDoubleValueExtractor().extractValues(OptionalDouble.empty(), recorder);
        assertEquals(List.of("value:null:a", "value:null:null", "value:null:1", "value:null:null",
                "value:null:2", "value:null:null", "value:null:3.5", "value:null:null"), recorder.calls);
    }
}
