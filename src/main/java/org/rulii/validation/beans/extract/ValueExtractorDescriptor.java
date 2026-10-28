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

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;
import jakarta.validation.valueextraction.ValueExtractorDeclarationException;
import jakarta.validation.valueextraction.ValueExtractorDefinitionException;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.util.reflect.AnnotatedTypes;

import java.lang.reflect.AnnotatedParameterizedType;
import java.util.Collection;
import java.lang.reflect.AnnotatedType;

/**
 * What a registered {@link ValueExtractor} serves, derived once from its declaration:
 * the container type and the type argument marked {@link ExtractedValue}.
 *
 * <p>The extractor must name its container in its {@code implements ValueExtractor<...>}
 * clause (or an ancestor's), e.g. {@code ValueExtractor<Map<?, @ExtractedValue ?>>}. Exactly
 * one {@code @ExtractedValue} must be present. For a non-generic container it sits on the
 * container type itself and must declare the extracted {@code type}
 * ({@code ValueExtractor<@ExtractedValue(type = Integer.class) OptionalInt>}); such a
 * descriptor reports {@link #CONTAINER} as its type-argument index.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ValueExtractorDescriptor {

    /** Type-argument index meaning "the container value itself" (non-generic containers). */
    public static final int CONTAINER = -1;

    private final ValueExtractor<?> extractor;
    private final Class<?> containerType;
    private final int typeArgumentIndex;
    private final Class<?> extractedType;
    private final boolean unwrapByDefault;

    private ValueExtractorDescriptor(ValueExtractor<?> extractor, Class<?> containerType, int typeArgumentIndex,
                                     Class<?> extractedType, boolean unwrapByDefault) {
        super();
        this.extractor = extractor;
        this.containerType = containerType;
        this.typeArgumentIndex = typeArgumentIndex;
        this.extractedType = extractedType;
        this.unwrapByDefault = unwrapByDefault;
    }

    /**
     * Describes the given extractor.
     *
     * @param extractor the extractor.
     * @return its descriptor.
     * @throws ValueExtractorDefinitionException if the extractor's declaration is not valid.
     */
    /**
     * Checks that a candidate extractor does not serve the same container type and type argument as
     * one already declared at the same level (the bootstrap configuration, a validator context), which
     * the specification refuses with {@link ValueExtractorDeclarationException}; across levels the
     * later level wins instead.
     *
     * @param declared the extractors declared at the level so far.
     * @param candidate the extractor being added.
     * @param level the level, for the message ("the configuration", say).
     * @return the candidate's descriptor.
     * @throws ValueExtractorDeclarationException if the candidate duplicates a declared extractor.
     * @throws ValueExtractorDefinitionException if the candidate is not a legal extractor.
     */
    public static ValueExtractorDescriptor requireDistinct(Collection<? extends ValueExtractor<?>> declared, ValueExtractor<?> candidate,
                                                           String level) {
        Assert.notNull(declared, "declared cannot be null.");
        Assert.notNull(candidate, "candidate cannot be null.");
        Assert.hasText(level, "level cannot be null/empty.");
        ValueExtractorDescriptor descriptor = of(candidate);

        for (ValueExtractor<?> existing : declared) {
            ValueExtractorDescriptor other = of(existing);

            if (other.getContainerType().equals(descriptor.getContainerType()) && other.getTypeArgumentIndex() == descriptor.getTypeArgumentIndex()) {
                throw new ValueExtractorDeclarationException("value extractor [" + candidate.getClass().getName() + "] serves the same "
                        + "container type and type argument as [" + existing.getClass().getName() + "] already added to " + level
                        + ": " + descriptor + "; one extractor per container type and type argument at each level.");
            }
        }

        return descriptor;
    }

    public static ValueExtractorDescriptor of(ValueExtractor<?> extractor) {
        Assert.notNull(extractor, "extractor cannot be null.");
        Class<?> extractorClass = extractor.getClass();
        AnnotatedType container = findContainerType(extractorClass);

        if (container == null) {
            throw new ValueExtractorDefinitionException("value extractor [" + extractorClass.getName()
                    + "] must declare its container type, e.g. implements ValueExtractor<List<@ExtractedValue ?>>");
        }

        Class<?> containerType = AnnotatedTypes.rawClass(container.getType());

        if (containerType == null) {
            throw new ValueExtractorDefinitionException("value extractor [" + extractorClass.getName()
                    + "] container type [" + container.getType().getTypeName() + "] must be a class");
        }

        int index = CONTAINER;
        Class<?> extractedType = null;
        int found = 0;

        ExtractedValue onContainer = container.getAnnotation(ExtractedValue.class);
        if (onContainer != null) {
            found++;
            extractedType = onContainer.type();
            if (extractedType == void.class) {
                throw new ValueExtractorDefinitionException("value extractor [" + extractorClass.getName()
                        + "] places @ExtractedValue on the container type [" + containerType.getName()
                        + "] and must therefore specify its type attribute");
            }
        }

        if (container instanceof AnnotatedParameterizedType) {
            AnnotatedType[] arguments = ((AnnotatedParameterizedType) container).getAnnotatedActualTypeArguments();
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i].isAnnotationPresent(ExtractedValue.class)) {
                    found++;
                    index = i;
                    extractedType = AnnotatedTypes.rawClass(arguments[i].getType());
                }
            }
        }

        if (found != 1) {
            throw new ValueExtractorDefinitionException("value extractor [" + extractorClass.getName()
                    + "] must mark exactly one type argument of [" + containerType.getName()
                    + "] with @ExtractedValue; found " + found);
        }

        return new ValueExtractorDescriptor(extractor, containerType, index, extractedType,
                extractorClass.isAnnotationPresent(UnwrapByDefault.class));
    }

    private static AnnotatedType findContainerType(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            AnnotatedType result = findInInterfaces(c);
            if (result != null) return result;
        }
        return null;
    }

    private static AnnotatedType findInInterfaces(Class<?> type) {
        for (AnnotatedType itf : type.getAnnotatedInterfaces()) {
            Class<?> raw = AnnotatedTypes.rawClass(itf.getType());

            if (raw == ValueExtractor.class) {
                return itf instanceof AnnotatedParameterizedType
                        ? ((AnnotatedParameterizedType) itf).getAnnotatedActualTypeArguments()[0]
                        : null;
            }

            if (raw != null) {
                AnnotatedType nested = findInInterfaces(raw);
                if (nested != null) return nested;
            }
        }

        return null;
    }

    public ValueExtractor<?> getExtractor() {
        return extractor;
    }

    /**
     * Raw container class the extractor serves (e.g. {@code Map.class}).
     *
     * @return the container type.
     */
    public Class<?> getContainerType() {
        return containerType;
    }

    /**
     * Index of the served type argument, or {@link #CONTAINER} when the extractor serves the
     * container value itself.
     *
     * @return the type-argument index.
     */
    public int getTypeArgumentIndex() {
        return typeArgumentIndex;
    }

    /**
     * Raw type of the extracted value when statically known: the {@code type} attribute for a
     * container-level {@code @ExtractedValue}, or the raw class of the marked type argument.
     *
     * @return the extracted type; null when the type argument is a wildcard or variable.
     */
    public Class<?> getExtractedType() {
        return extractedType;
    }

    /**
     * Whether the extractor is marked {@link UnwrapByDefault}: constraints on a property of the
     * container type apply to the extracted value unless {@code Unwrapping.Skip} is requested.
     *
     * @return true if unwrapped by default.
     */
    public boolean isUnwrapByDefault() {
        return unwrapByDefault;
    }

    @Override
    public String toString() {
        return "ValueExtractorDescriptor{" +
                "extractor=" + extractor.getClass().getName() +
                ", containerType=" + containerType.getName() +
                ", typeArgumentIndex=" + typeArgumentIndex +
                ", extractedType=" + (extractedType != null ? extractedType.getName() : null) +
                ", unwrapByDefault=" + unwrapByDefault +
                '}';
    }
}
