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

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.rulii.validation.beans.ConstraintMapperRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Test-scope mapper registry with spec-shaped supported types for the constraints used
 * by the metadata fixtures. The real built-in mappers arrive with the flow
 * materialization layer; metadata-read behavior only needs the applicability knowledge.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class TestConstraintMappers {

    private TestConstraintMappers() {
        super();
    }

    public static ConstraintMapperRegistry registry() {
        return ConstraintMapperRegistry.builder()
                .register(NotNull.class, List::of)
                .register(NotBlank.class, () -> List.of(CharSequence.class))
                .register(NotEmpty.class, () -> List.of(CharSequence.class, Collection.class, Map.class, Object[].class))
                .register(Size.class, () -> List.of(CharSequence.class, Collection.class, Map.class, Object[].class))
                .register(Email.class, () -> List.of(CharSequence.class))
                .register(Pattern.class, () -> List.of(CharSequence.class))
                .register(Min.class, () -> List.of(Number.class))
                .register(Max.class, () -> List.of(Number.class))
                .register(Positive.class, () -> List.of(Number.class))
                .register(AssertTrue.class, () -> List.of(Boolean.class))
                .build();
    }
}
