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
package org.rulii.validation.beans.extract.standard;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

import java.util.Optional;

/**
 * Built-in extractor for the value of an {@link Optional}; an empty optional yields null.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class OptionalValueExtractor implements ValueExtractor<Optional<@ExtractedValue ?>> {

    public OptionalValueExtractor() {
        super();
    }

    @Override
    public void extractValues(Optional<?> originalValue, ValueReceiver receiver) {
        receiver.value(null, originalValue.orElse(null));
    }
}
