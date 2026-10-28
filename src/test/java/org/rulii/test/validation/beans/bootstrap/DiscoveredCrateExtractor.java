/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rulii.test.validation.beans.bootstrap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * A value extractor the bootstrap discovers through {@code META-INF/services}: see the test resource
 * {@code META-INF/services/jakarta.validation.valueextraction.ValueExtractor}.
 */
public class DiscoveredCrateExtractor implements ValueExtractor<Crate<@ExtractedValue ?>> {

    public DiscoveredCrateExtractor() {
        super();
    }

    @Override
    public void extractValues(Crate<?> originalValue, ValueReceiver receiver) {
        receiver.value(null, originalValue.content);
    }

    /**
     * An extractor for the same crate that hands nothing to validation: registering it over the
     * discovered one makes a crate pass whatever it holds.
     */
    public static class LenientCrateExtractor implements ValueExtractor<Crate<@ExtractedValue ?>> {

        public LenientCrateExtractor() {
            super();
        }

        @Override
        public void extractValues(Crate<?> originalValue, ValueReceiver receiver) {
        }
    }

    public static class Cargo {
        Crate<@NotBlank String> crate = new Crate<>("");
    }
}
