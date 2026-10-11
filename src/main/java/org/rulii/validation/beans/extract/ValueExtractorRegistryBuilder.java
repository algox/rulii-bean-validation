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
package org.rulii.validation.beans.extract;

import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.lib.spring.util.Assert;

/**
 * Builder for a {@link ValueExtractorRegistry}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ValueExtractorRegistryBuilder {

    private final ValueExtractorRegistry registry;

    private ValueExtractorRegistryBuilder(ValueExtractorRegistry registry) {
        super();
        Assert.notNull(registry, "registry cannot be null.");
        this.registry = registry;
    }

    /**
     * Builder for an empty registry.
     *
     * @return the builder.
     */
    public static ValueExtractorRegistryBuilder empty() {
        return new ValueExtractorRegistryBuilder(new DefaultValueExtractorRegistry(false));
    }

    /**
     * Builder for a registry pre-loaded with the spec's built-in extractors.
     *
     * @return the builder.
     */
    public static ValueExtractorRegistryBuilder standard() {
        return new ValueExtractorRegistryBuilder(new DefaultValueExtractorRegistry(true));
    }

    /**
     * Registers an extractor; replaces any extractor for the same container type and position.
     *
     * @param extractor the extractor.
     * @return this builder.
     */
    public ValueExtractorRegistryBuilder extractor(ValueExtractor<?> extractor) {
        Assert.notNull(extractor, "extractor cannot be null.");
        registry.register(extractor);
        return this;
    }

    public ValueExtractorRegistry build() {
        return registry;
    }
}
