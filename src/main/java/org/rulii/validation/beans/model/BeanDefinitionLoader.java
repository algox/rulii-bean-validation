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
package org.rulii.validation.beans.model;

import jakarta.validation.ParameterNameProvider;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.util.reflect.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads and caches {@link BeanDefinition bean definitions}. One loader per validator instance; the
 * cache is never static.
 *
 * <p><b>Eager cascade graph.</b> Loading a class also loads the declared types of its cascade
 * targets: {@code @Valid} properties, {@code @Valid} container element positions, and the elements of
 * a container property that is itself marked {@code @Valid} (an {@code Iterable}'s elements, a
 * {@code Map}'s values, an {@code Optional}'s value). The walk is guarded against cycles at the type
 * level, so every declaration error of the statically reachable graph surfaces on the first load of
 * the root. Container types (anything a value extractor serves), {@code Object} and primitives are
 * never loaded as beans; runtime subtypes are loaded lazily when first encountered.</p>
 *
 * <p><b>Constraint support.</b> Once a definition is read, every constraint in it is checked against
 * the {@link ConstraintMapperRegistry constraint mappers}: an unmapped constraint that is not a pure
 * composition, or a constraint declared on a type it cannot apply to, fails the load (see
 * {@code ConstraintSupportCheck}). The failure poisons the class like any other read-time error.</p>
 *
 * <p><b>Poisoned entries.</b> A class whose load fails is cached with its failure, together with
 * every class on the path from the requested root to it, and later requests for any of them rethrow
 * the same exception instance immediately, without rescanning. Classes resolved before the failure
 * stay loaded.</p>
 *
 * <p><b>Publication.</b> A load resolves its whole graph into a local map and publishes the entries
 * at the end: {@link ConcurrentHashMap#computeIfAbsent} cannot be used for a recursive load, and
 * definitions are immutable, so when two threads load the same graph the first publication wins
 * and both observe equivalent definitions.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanDefinitionLoader {

    private final ValueExtractorRegistry extractorRegistry;
    private final ConstraintMapperRegistry mapperRegistry;
    private final ParameterNameProvider parameterNameProvider;
    private final ConcurrentMap<Class<?>, Entry> cache = new ConcurrentHashMap<>();

    /**
     * Creates a loader using the given value extractors, the standard constraint mappers and the
     * compiler's parameter names.
     *
     * @param extractorRegistry the value extractors available at validation time.
     */
    public BeanDefinitionLoader(ValueExtractorRegistry extractorRegistry) {
        this(extractorRegistry, ConstraintMapperRegistry.builder().build());
    }

    /**
     * Creates a loader using the compiler's parameter names.
     *
     * @param extractorRegistry the value extractors available at validation time.
     * @param mapperRegistry the constraint mappers available at validation time.
     */
    public BeanDefinitionLoader(ValueExtractorRegistry extractorRegistry, ConstraintMapperRegistry mapperRegistry) {
        this(extractorRegistry, mapperRegistry, new DefaultParameterNameProvider());
    }

    /**
     * Creates a loader.
     *
     * @param extractorRegistry the value extractors available at validation time.
     * @param mapperRegistry the constraint mappers available at validation time.
     * @param parameterNameProvider the provider naming the parameters of constrained executables.
     */
    public BeanDefinitionLoader(ValueExtractorRegistry extractorRegistry, ConstraintMapperRegistry mapperRegistry,
                                ParameterNameProvider parameterNameProvider) {
        super();
        Assert.notNull(extractorRegistry, "extractorRegistry cannot be null.");
        Assert.notNull(mapperRegistry, "mapperRegistry cannot be null.");
        Assert.notNull(parameterNameProvider, "parameterNameProvider cannot be null.");
        this.extractorRegistry = extractorRegistry;
        this.mapperRegistry = mapperRegistry;
        this.parameterNameProvider = parameterNameProvider;
    }

    /**
     * The provider naming the parameters of constrained executables.
     *
     * @return the provider.
     */
    public ParameterNameProvider getParameterNameProvider() {
        return parameterNameProvider;
    }

    /**
     * The value extractors the definitions are loaded against.
     *
     * @return the registry.
     */
    public ValueExtractorRegistry getValueExtractorRegistry() {
        return extractorRegistry;
    }

    /**
     * The constraint mappers the definitions are checked against.
     *
     * @return the registry.
     */
    public ConstraintMapperRegistry getConstraintMapperRegistry() {
        return mapperRegistry;
    }

    /**
     * The definition of the given bean class, loading it and its statically reachable cascade
     * targets on first request.
     *
     * @param beanClass the bean class.
     * @return the bean definition.
     * @throws jakarta.validation.ConstraintDeclarationException (or another runtime exception) if
     *         loading the class or one of its cascade targets fails; rethrown as-is on every later
     *         request for the same class.
     * @throws IllegalArgumentException if the class is a primitive or an array type.
     */
    public BeanDefinition load(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.isTrue(!beanClass.isPrimitive() && !beanClass.isArray(), "type [" + beanClass.getName()
                + "] cannot be validated as a bean: it is a primitive or an array type.");

        Entry entry = cache.get(beanClass);

        if (entry == null) {
            Resolution resolution = new Resolution();

            try {
                resolve(beanClass, resolution);
            } catch (RuntimeException e) {
                // Recorded against the root (and the path to the failure) by resolve(); rethrown below from the cache.
            } finally {
                for (Map.Entry<Class<?>, Entry> resolved : resolution.resolved.entrySet()) {
                    cache.putIfAbsent(resolved.getKey(), resolved.getValue());
                }
            }

            entry = cache.get(beanClass);
            if (entry == null) throw new UnrulyException("bean definition of [" + beanClass.getName() + "] was not resolved.");
        }

        return entry.get();
    }

    /**
     * Whether the definition of the given class is cached and usable.
     *
     * @param beanClass the bean class.
     * @return true if loaded without failure.
     */
    public boolean isLoaded(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Entry entry = cache.get(beanClass);
        return entry != null && entry.failure == null;
    }

    /**
     * Whether a load of the given class failed and the failure is cached.
     *
     * @param beanClass the bean class.
     * @return true if requests for the class rethrow a cached failure.
     */
    public boolean isPoisoned(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Entry entry = cache.get(beanClass);
        return entry != null && entry.failure != null;
    }

    /**
     * Resolves a class and, recursively, its cascade targets into the local map. A failure is
     * recorded against the class and propagated, so each class on the path to the failure is
     * poisoned too.
     */
    private void resolve(Class<?> type, Resolution resolution) {
        Entry published = cache.get(type);

        if (published != null) {
            published.get();
            return;
        }

        if (resolution.resolved.containsKey(type) || !resolution.inProgress.add(type)) return;

        try {
            BeanDefinition definition = BeanDefinition.load(type, extractorRegistry, parameterNameProvider);
            ConstraintSupportCheck.check(definition, mapperRegistry);

            for (Class<?> target : cascadeTargets(definition)) {
                resolve(target, resolution);
            }

            resolution.resolved.put(type, new Entry(definition, null));
        } catch (RuntimeException e) {
            resolution.resolved.put(type, new Entry(null, e));
            throw e;
        } finally {
            resolution.inProgress.remove(type);
        }
    }

    /**
     * The bean classes a definition cascades into, as far as they are statically known.
     */
    private Set<Class<?>> cascadeTargets(BeanDefinition definition) {
        Set<Class<?>> result = new LinkedHashSet<>();

        for (PropertyDefinition property : definition.getProperties()) {
            if (property.isCascaded()) addTargets(property.getGenericType(), result);
            addElementTargets(property.getContainerElements(), result);
        }

        for (ExecutableDefinition method : definition.getMethods()) {
            addExecutableTargets(method, result);
        }

        for (ExecutableDefinition constructor : definition.getConstructors()) {
            addExecutableTargets(constructor, result);
        }

        return result;
    }

    private void addExecutableTargets(ExecutableDefinition executable, Set<Class<?>> result) {
        for (ParameterDefinition parameter : executable.getParameters()) {
            if (parameter.isCascaded()) addTargets(parameter.getGenericType(), result);
            addElementTargets(parameter.getContainerElements(), result);
        }

        if (executable.getReturnValue() != null) {
            if (executable.getReturnValue().isCascaded()) addTargets(executable.getReturnValue().getGenericType(), result);
            addElementTargets(executable.getReturnValue().getContainerElements(), result);
        }
    }

    private void addElementTargets(List<ContainerElementDefinition> elements, Set<Class<?>> result) {
        for (ContainerElementDefinition element : elements) {
            if (element.isCascaded()) addTargets(element.getType(), result);
            addElementTargets(element.getChildren(), result);
        }
    }

    /**
     * Adds the bean class a cascaded position of the given declared type descends into: the type
     * itself when it is a bean, or the elements when it is a container ({@code @Valid List<Address>}
     * cascades into the addresses). Positions whose element type cannot be resolved statically are
     * left to the runtime.
     */
    private void addTargets(Type type, Set<Class<?>> result) {
        Class<?> raw = Types.erasure(type);

        if (raw == Object.class || raw.isPrimitive()) return;

        if (raw.isArray()) {
            // Arrays are not a built-in container (R7.2): only a registered array extractor makes the elements reachable.
            if (!extractorRegistry.find(raw).isEmpty()) addTargets(Types.componentType(type), result);
            return;
        }

        if (extractorRegistry.find(raw).isEmpty()) {
            result.add(raw);
            return;
        }

        Type element = legacyElementType(type, raw);
        if (element != null) addTargets(element, result);
    }

    /**
     * The element type a {@code @Valid} container property cascades into (spec legacy container
     * support): the values of a {@code Map}, otherwise the single extracted type argument of the
     * declared type. Null when the declared type is not parameterized or the position is ambiguous.
     */
    private Type legacyElementType(Type type, Class<?> raw) {
        if (!(type instanceof ParameterizedType)) return null;

        Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
        int chosen = -1;

        for (int i = 0; i < arguments.length; i++) {
            ValueExtractorDescriptor descriptor = extractorRegistry.find(raw, i);
            if (descriptor == null) continue;

            if (Map.class.isAssignableFrom(raw)) {
                if (descriptor.getContainerType() == Map.class && descriptor.getTypeArgumentIndex() == 1) chosen = i;
            } else if (chosen == -1) {
                chosen = i;
            } else {
                return null;
            }
        }

        return chosen == -1 ? null : arguments[chosen];
    }

    /**
     * The working state of one load: the entries resolved so far and the classes being resolved
     * (the cycle guard).
     */
    private static final class Resolution {

        private final Map<Class<?>, Entry> resolved = new LinkedHashMap<>();
        private final Set<Class<?>> inProgress = new HashSet<>();

        private Resolution() {
            super();
        }
    }

    /**
     * A cache entry: a definition, or the failure that prevented it.
     */
    private static final class Entry {

        private final BeanDefinition definition;
        private final RuntimeException failure;

        private Entry(BeanDefinition definition, RuntimeException failure) {
            super();
            this.definition = definition;
            this.failure = failure;
        }

        private BeanDefinition get() {
            if (failure != null) throw failure;
            return definition;
        }
    }
}
