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
package org.rulii.validation.beans.metadata;

import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.ConstraintMapperRegistry;

/**
 * Builder for {@link BeanMetadataReader}. The mapper registry supplies the
 * applicability knowledge consulted at read time; it defaults to an empty registry
 * (under which every constraint fails fast as unmapped — supply the registry with the
 * standard and custom mappers registered).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanMetadataReaderBuilder {

    private ConstraintMapperRegistry mapperRegistry = ConstraintMapperRegistry.builder().build();

    BeanMetadataReaderBuilder() {
        super();
    }

    /**
     * Sets the constraint-mapper registry consulted during metadata reads.
     *
     * @param mapperRegistry the mapper registry.
     * @return this builder for fluent chaining.
     */
    public BeanMetadataReaderBuilder mapperRegistry(ConstraintMapperRegistry mapperRegistry) {
        Assert.notNull(mapperRegistry, "mapperRegistry cannot be null.");
        this.mapperRegistry = mapperRegistry;
        return this;
    }

    /**
     * Builds the reader.
     *
     * @return a new {@link BeanMetadataReader}.
     */
    public BeanMetadataReader build() {
        return new BeanMetadataReader(mapperRegistry);
    }
}
