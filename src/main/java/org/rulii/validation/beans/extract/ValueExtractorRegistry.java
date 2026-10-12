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

import java.util.List;

/**
 * Registry of {@link ValueExtractor}s: the spec's own extension point for validating
 * constraints declared on container type arguments ({@code List<@NotBlank String>},
 * {@code Pair<@NotNull A, B>}).
 *
 * <p>Resolution follows the specification: an extractor applies to a container class and
 * type-argument position when its container type is a supertype of the class and its
 * {@code @ExtractedValue} argument corresponds to that position through the type hierarchy
 * ({@code ArrayList<E>} position 0 resolves to the {@code List} extractor); among applicable
 * extractors the most specific container type wins; two equally specific candidates are a
 * declaration error.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface ValueExtractorRegistry {

    /**
     * Builder pre-loaded with the spec's built-in extractors.
     *
     * @return a registry builder.
     */
    static ValueExtractorRegistryBuilder builder() {
        return builder(true);
    }

    /**
     * Builder with or without the built-in extractors.
     *
     * @param registerDefaults true to pre-load the built-in extractors.
     * @return a registry builder.
     */
    static ValueExtractorRegistryBuilder builder(boolean registerDefaults) {
        return registerDefaults ? ValueExtractorRegistryBuilder.standard() : ValueExtractorRegistryBuilder.empty();
    }

    /**
     * Registers an extractor. An extractor already registered for the same container type and
     * position is replaced, so a custom extractor can override a built-in.
     *
     * @param extractor the extractor.
     * @return the descriptor derived from the extractor's declaration.
     * @throws jakarta.validation.valueextraction.ValueExtractorDefinitionException if the declaration is invalid.
     */
    ValueExtractorDescriptor register(ValueExtractor<?> extractor);

    /**
     * Finds the extractor serving the given type-argument position of a container class.
     *
     * @param containerType the container class as declared on the property (e.g. {@code ArrayList.class}).
     * @param typeArgumentIndex the type-argument position, or {@link ValueExtractorDescriptor#CONTAINER}
     *                          for the container value itself.
     * @return the most specific applicable extractor; null if none.
     * @throws jakarta.validation.ConstraintDeclarationException if several extractors apply and none is most specific.
     */
    ValueExtractorDescriptor find(Class<?> containerType, int typeArgumentIndex);

    /**
     * Finds every extractor applicable to a container class, one per served position: the
     * maximally specific extractor for each type-argument position (or the container value
     * itself) that some registered extractor serves. Used to resolve value unwrapping for
     * constraints declared on the container property: {@code @Positive OptionalInt} applies
     * to the int through the one {@code @UnwrapByDefault} extractor of {@code OptionalInt};
     * {@code @NotNull(payload = Unwrapping.Unwrap.class) Optional<String>} applies to the
     * content through the single extractor of {@code Optional}.
     *
     * @param containerType the container class as declared on the property.
     * @return the applicable extractors, in registration order; empty if none.
     * @throws jakarta.validation.ConstraintDeclarationException if two equally specific extractors serve a position.
     */
    List<ValueExtractorDescriptor> find(Class<?> containerType);

    /**
     * All registered extractors, in registration order.
     *
     * @return the descriptors.
     */
    List<ValueExtractorDescriptor> getDescriptors();
}
