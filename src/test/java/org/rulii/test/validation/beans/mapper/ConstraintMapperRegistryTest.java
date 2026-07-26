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
package org.rulii.test.validation.beans.mapper;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.ConstraintMapper;
import org.rulii.validation.beans.ConstraintMapperRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the standard mapper registration, error-code derivation (S8a), and the
 * registry's attribute-name mappings by error code.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintMapperRegistryTest {

    public ConstraintMapperRegistryTest() {
        super();
    }

    @Test
    public void standardRegistersAllTwentyTwoConstraints() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().standard().build();

        assertEquals(22, registry.getRegisteredTypes().size());
        assertTrue(registry.isRegistered(NotNull.class));
        assertTrue(registry.isRegistered(Size.class));
        assertNotNull(registry.getMapper(Min.class));
    }

    @Test
    public void standardRegistrationCanBeOverridden() {
        ConstraintMapper custom = new ConstraintMapper() {
            @Override
            public List<Class<?>> getSupportedTypes() {
                return List.of();
            }

            @Override
            public List<org.rulii.rule.Rule> map(org.rulii.validation.beans.metadata.ConstraintMetadata constraint,
                                                 org.rulii.validation.beans.ConstraintTarget target) {
                throw new UnsupportedOperationException();
            }
        };
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder()
                .standard()
                .register(NotNull.class, custom)
                .build();

        assertEquals(22, registry.getRegisteredTypes().size());
        assertSame(custom, registry.getMapper(NotNull.class));
    }

    @Test
    public void errorCodeIsJakartaTemplateKey() {
        assertEquals("jakarta.validation.constraints.Size.message", ConstraintMapperRegistry.errorCode(Size.class));
    }

    @Test
    public void attributeNameMappingsKeyedByCode() {
        ConstraintMapperRegistry registry = ConstraintMapperRegistry.builder().standard().build();
        Map<String, Map<String, String>> mappings = registry.getAttributeNameMappingsByCode();

        assertEquals(Map.of("value", "min"), mappings.get(ConstraintMapperRegistry.errorCode(Min.class)));
        assertEquals(Map.of("regexp", "pattern"), mappings.get(ConstraintMapperRegistry.errorCode(Pattern.class)));
        assertEquals(Map.of("integer", "maxIntegerLength", "fraction", "maxFractionLength"),
                mappings.get(ConstraintMapperRegistry.errorCode(Digits.class)));
        // Constraints without a mapping are omitted.
        assertFalse(mappings.containsKey(ConstraintMapperRegistry.errorCode(NotNull.class)));
    }
}
