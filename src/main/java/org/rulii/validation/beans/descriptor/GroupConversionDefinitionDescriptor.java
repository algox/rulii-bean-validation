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
package org.rulii.validation.beans.descriptor;

import jakarta.validation.metadata.GroupConversionDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.GroupConversions;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The spec's {@link GroupConversionDescriptor}: one {@code @ConvertGroup}.
 *
 * @param from the group converted.
 * @param to the group it is converted to.
 * @author Max Arulananthan
 * @since 1.0
 */
public record GroupConversionDefinitionDescriptor(Class<?> from, Class<?> to) implements GroupConversionDescriptor {

    public GroupConversionDefinitionDescriptor {
        Assert.notNull(from, "from cannot be null.");
        Assert.notNull(to, "to cannot be null.");
    }

    /**
     * Describes the given conversions, in declaration order.
     *
     * @param conversions the conversions.
     * @return the descriptors; unmodifiable.
     */
    public static Set<GroupConversionDescriptor> describe(GroupConversions conversions) {
        Assert.notNull(conversions, "conversions cannot be null.");
        Set<GroupConversionDescriptor> result = new LinkedHashSet<>();

        for (Map.Entry<Class<?>, Class<?>> conversion : conversions.asMap().entrySet()) {
            result.add(new GroupConversionDefinitionDescriptor(conversion.getKey(), conversion.getValue()));
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public Class<?> getFrom() {
        return from;
    }

    @Override
    public Class<?> getTo() {
        return to;
    }
}
