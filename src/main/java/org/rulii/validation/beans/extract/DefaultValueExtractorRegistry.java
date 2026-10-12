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

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.lib.spring.core.ResolvableType;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.extract.standard.IterableValueExtractor;
import org.rulii.validation.beans.extract.standard.ListValueExtractor;
import org.rulii.validation.beans.extract.standard.MapKeyValueExtractor;
import org.rulii.validation.beans.extract.standard.MapValueValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalDoubleValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalIntValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalLongValueExtractor;
import org.rulii.validation.beans.extract.standard.OptionalValueExtractor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link ValueExtractorRegistry}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class DefaultValueExtractorRegistry implements ValueExtractorRegistry {

    private final List<ValueExtractorDescriptor> descriptors = Collections.synchronizedList(new ArrayList<>());
    private final Map<LookupKey, Optional<ValueExtractorDescriptor>> cache = new ConcurrentHashMap<>();

    private record LookupKey(Class<?> containerType, int typeArgumentIndex) {}
    private final Map<Class<?>, List<ValueExtractorDescriptor>> containerCache = new ConcurrentHashMap<>();

    public DefaultValueExtractorRegistry(boolean registerDefaults) {
        super();
        if (registerDefaults) registerDefaults();
    }

    @Override
    public ValueExtractorDescriptor register(ValueExtractor<?> extractor) {
        Assert.notNull(extractor, "extractor cannot be null.");
        ValueExtractorDescriptor descriptor = ValueExtractorDescriptor.of(extractor);

        descriptors.removeIf(existing -> existing.getContainerType().equals(descriptor.getContainerType())
                && existing.getTypeArgumentIndex() == descriptor.getTypeArgumentIndex());
        descriptors.add(descriptor);
        cache.clear();
        containerCache.clear();

        return descriptor;
    }

    @Override
    public ValueExtractorDescriptor find(Class<?> containerType, int typeArgumentIndex) {
        Assert.notNull(containerType, "containerType cannot be null.");
        return cache.computeIfAbsent(new LookupKey(containerType, typeArgumentIndex),
                key -> Optional.ofNullable(resolve(key.containerType(), key.typeArgumentIndex()))).orElse(null);
    }

    @Override
    public List<ValueExtractorDescriptor> find(Class<?> containerType) {
        Assert.notNull(containerType, "containerType cannot be null.");
        return containerCache.computeIfAbsent(containerType, this::resolveAll);
    }

    private List<ValueExtractorDescriptor> resolveAll(Class<?> containerType) {
        List<ValueExtractorDescriptor> candidates = new ArrayList<>();

        synchronized (descriptors) {
            for (ValueExtractorDescriptor descriptor : descriptors) {
                if (descriptor.getContainerType().isAssignableFrom(containerType)) candidates.add(descriptor);
            }
        }

        // Per position, the most specific container type wins: drop a candidate when another
        // candidate of a strict subtype serves the same position (mapped through the hierarchy).
        List<ValueExtractorDescriptor> result = new ArrayList<>();
        for (ValueExtractorDescriptor candidate : candidates) {
            boolean dominated = false;

            for (ValueExtractorDescriptor other : candidates) {
                if (other != candidate && isStrictSubtype(other.getContainerType(), candidate.getContainerType())
                        && servesSamePosition(other, candidate)) {
                    dominated = true;
                    break;
                }
            }

            if (!dominated) result.add(candidate);
        }

        for (int i = 0; i < result.size(); i++) {
            for (int j = i + 1; j < result.size(); j++) {
                if (result.get(i).getContainerType().equals(result.get(j).getContainerType())
                        && result.get(i).getTypeArgumentIndex() == result.get(j).getTypeArgumentIndex()) {
                    throw new ConstraintDeclarationException("ambiguous value extractors for ["
                            + containerType.getName() + "]: " + result.get(i) + " and " + result.get(j));
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    private static boolean isStrictSubtype(Class<?> type, Class<?> supertype) {
        return supertype.isAssignableFrom(type) && !supertype.equals(type);
    }

    /**
     * Whether a descriptor of a subtype serves the same position as a descriptor of one of its
     * supertypes: both the container value, or the subtype's type argument maps to the supertype's.
     */
    private static boolean servesSamePosition(ValueExtractorDescriptor subtype, ValueExtractorDescriptor supertype) {
        if (subtype.getTypeArgumentIndex() == ValueExtractorDescriptor.CONTAINER
                || supertype.getTypeArgumentIndex() == ValueExtractorDescriptor.CONTAINER) {
            return subtype.getTypeArgumentIndex() == supertype.getTypeArgumentIndex();
        }

        return mapTypeArgument(subtype.getContainerType(), subtype.getTypeArgumentIndex(), supertype.getContainerType())
                == supertype.getTypeArgumentIndex();
    }

    private ValueExtractorDescriptor resolve(Class<?> containerType, int typeArgumentIndex) {
        List<ValueExtractorDescriptor> candidates = new ArrayList<>();

        synchronized (descriptors) {
            for (ValueExtractorDescriptor descriptor : descriptors) {
                if (applies(descriptor, containerType, typeArgumentIndex)) candidates.add(descriptor);
            }
        }

        // Most specific container type wins: drop any candidate that is a strict supertype of another.
        List<ValueExtractorDescriptor> mostSpecific = new ArrayList<>();

        for (ValueExtractorDescriptor candidate : candidates) {
            boolean dominated = false;

            for (ValueExtractorDescriptor other : candidates) {
                if (other != candidate
                        && candidate.getContainerType().isAssignableFrom(other.getContainerType())
                        && !candidate.getContainerType().equals(other.getContainerType())) {
                    dominated = true;
                    break;
                }
            }

            if (!dominated) mostSpecific.add(candidate);
        }

        if (mostSpecific.size() > 1) {
            throw new ConstraintDeclarationException("ambiguous value extractors for type argument ["
                    + typeArgumentIndex + "] of [" + containerType.getName() + "]: " + mostSpecific);
        }

        return mostSpecific.isEmpty() ? null : mostSpecific.get(0);
    }

    private static boolean applies(ValueExtractorDescriptor descriptor, Class<?> containerType, int typeArgumentIndex) {
        if (!descriptor.getContainerType().isAssignableFrom(containerType)) return false;
        if (typeArgumentIndex == ValueExtractorDescriptor.CONTAINER || descriptor.getTypeArgumentIndex() == ValueExtractorDescriptor.CONTAINER) {
            return typeArgumentIndex == descriptor.getTypeArgumentIndex();
        }

        return mapTypeArgument(containerType, typeArgumentIndex, descriptor.getContainerType()) == descriptor.getTypeArgumentIndex();
    }

    /**
     * Maps a type-argument position of {@code type} to the corresponding position of one of its
     * generic supertypes: {@code HashMap<K, V>} position 1 is {@code Map} position 1;
     * {@code class MyMap<A> extends HashMap<String, A>} position 0 is {@code Map} position 1.
     *
     * @return the position in the supertype, or -2 if it does not correspond to any.
     */
    static int mapTypeArgument(Class<?> type, int index, Class<?> supertype) {
        if (type.equals(supertype)) return index;
        TypeVariable<?>[] parameters = type.getTypeParameters();
        if (index < 0 || index >= parameters.length) return -2;

        // Parameterize the type with a private marker at the requested position and see which
        // generic of the supertype resolves to it; this follows the variables through every level.
        Type[] arguments = new Type[parameters.length];
        Arrays.fill(arguments, Object.class);
        arguments[index] = Marker.class;

        ResolvableType asSupertype = ResolvableType.forType(parameterized(type, arguments)).as(supertype);
        if (asSupertype.resolve() == null) return -2;

        ResolvableType[] generics = asSupertype.getGenerics();
        for (int i = 0; i < generics.length; i++) {
            if (Marker.class.equals(generics[i].resolve())) return i;
        }

        return -2;
    }

    private static final class Marker {}

    private static ParameterizedType parameterized(Class<?> raw, Type[] arguments) {
        return new ParameterizedType() {
            @Override public Type[] getActualTypeArguments() { return arguments; }
            @Override public Type getRawType() { return raw; }
            @Override public Type getOwnerType() { return null; }
            @Override public String toString() { return raw.getName() + Arrays.toString(arguments); }
        };
    }

    @Override
    public List<ValueExtractorDescriptor> getDescriptors() {
        synchronized (descriptors) {
            return List.copyOf(descriptors);
        }
    }

    /**
     * Registers the extractors the specification mandates: Iterable, List, Map key, Map value,
     * Optional, OptionalInt, OptionalLong and OptionalDouble.
     */
    public void registerDefaults() {
        register(new IterableValueExtractor());
        register(new ListValueExtractor());
        register(new MapKeyValueExtractor());
        register(new MapValueValueExtractor());
        register(new OptionalValueExtractor());
        register(new OptionalIntValueExtractor());
        register(new OptionalLongValueExtractor());
        register(new OptionalDoubleValueExtractor());
    }

    @Override
    public String toString() {
        return "DefaultValueExtractorRegistry{" +
                "descriptors=" + descriptors +
                '}';
    }
}
