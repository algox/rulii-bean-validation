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
package org.rulii.validation.beans;

import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.context.RuleContextOptions;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.registry.MutableRuleRegistry;
import org.rulii.registry.RuleRegistry;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.DefaultParameterNameProvider;
import org.rulii.validation.beans.extract.ValueExtractorRegistryBuilder;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistryBuilder;
import org.rulii.validation.beans.traversal.TraversableResolvers;
import org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory;

/**
 * Builds a {@link BeanValidator}. Everything is fixed at build time:
 * <ul>
 *   <li>{@link #registry(RuleRegistry)}: where {@code @Validate} names resolve;</li>
 *   <li>{@link #mapper(ConstraintMapper)} / {@link #mappers(ConstraintMapperRegistry)}: how constraints
 *       become rules, on top of (or instead of) the standard mappers;</li>
 *   <li>{@link #constraintValidatorFactory(ConstraintValidatorFactory)}: how the validators of
 *       constraints that have no mapper but name {@code ConstraintValidator} classes are created;</li>
 *   <li>{@link #extractor(ValueExtractor)} / {@link #extractors(ValueExtractorRegistry)}: how containers
 *       are walked, on top of (or instead of) the standard extractors;</li>
 *   <li>{@link #options(RuleContextOptions)}: the core context every validation starts from (locale,
 *       message resolver, tracer, clock, ...);</li>
 *   <li>{@link #parameterNameProvider(ParameterNameProvider)}: how the parameters of constrained
 *       methods and constructors are named;</li>
 *   <li>{@link #messageInterpolator(MessageInterpolator)}: a spec interpolator rendering the messages
 *       of constraint violations in place of rulii's message pipeline;</li>
 *   <li>{@link #traversableResolver(TraversableResolver)}: which properties may be validated and
 *       cascaded into (lazy persistence associations, typically);</li>
 *   <li>{@link #registerFlows(boolean)}: whether the flows the validator builds per class are
 *       registered in the registry, so other flows run them by name.</li>
 * </ul>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidatorBuilder {

    private RuleRegistry registry;
    private RuleContextOptions options;
    private ParameterNameProvider parameterNameProvider;
    private MessageInterpolator messageInterpolator;
    private TraversableResolver traversableResolver;
    private ConstraintValidatorFactory constraintValidatorFactory;
    private final ConstraintMapperRegistryBuilder mappers = ConstraintMapperRegistryBuilder.standard();
    private ConstraintMapperRegistry mapperRegistry;
    private final ValueExtractorRegistryBuilder extractors = ValueExtractorRegistryBuilder.standard();
    private ValueExtractorRegistry extractorRegistry;
    private boolean registerFlows;
    private boolean failFast;

    BeanValidatorBuilder() {
        super();
    }

    /**
     * Whether the validator registers the flows it builds in its {@link #registry(RuleRegistry) registry}:
     * the bean flow of every class it validates and the parameters and return-value flows of every
     * executable, as they are first built, under their names ({@code orderValidationFlow},
     * {@code orderService_placeParametersFlow}, ...), categorised under the bean class. Another flow
     * then runs them by name, and a registry listing shows them. Off by default. Needs a
     * {@code MutableRuleRegistry}; a name already in use (a second validator over the same registry
     * reached that class first) is left as it is.
     *
     * @param registerFlows true to register the flows.
     * @return this builder.
     */
    public BeanValidatorBuilder registerFlows(boolean registerFlows) {
        this.registerFlows = registerFlows;
        return this;
    }

    /**
     * Whether validation stops at the first violation (fail-fast mode, the reference implementation's
     * {@code failFast}): the result holds that one violation and nothing after it is validated. Off by
     * default.
     *
     * @param failFast true to stop at the first violation.
     * @return this builder.
     */
    public BeanValidatorBuilder failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    /**
     * The registry {@code @Validate} names resolve through.
     *
     * @param registry the registry.
     * @return this builder.
     */
    public BeanValidatorBuilder registry(RuleRegistry registry) {
        Assert.notNull(registry, "registry cannot be null.");
        this.registry = registry;
        return this;
    }

    /**
     * Adds a constraint mapper to the standard ones; a mapper for a constraint already mapped replaces
     * the standard mapping.
     *
     * @param mapper the mapper.
     * @return this builder.
     * @throws IllegalArgumentException if a whole registry was given through {@link #mappers}.
     */
    public BeanValidatorBuilder mapper(ConstraintMapper<?> mapper) {
        Assert.notNull(mapper, "mapper cannot be null.");
        Assert.isTrue(mapperRegistry == null, "mapper() cannot be combined with mappers(); add the mapper to that registry instead.");
        mappers.mapper(mapper);
        return this;
    }

    /**
     * The factory the validators of validator-backed constraints are created through. When not set, a
     * {@link org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory} over the
     * {@link org.rulii.util.reflect.ObjectFactory} of the {@link #options(RuleContextOptions) options}:
     * no-argument constructor plus post-construct by default, container-created (injected) when the
     * options carry a container-backed object factory.
     *
     * @param factory the factory.
     * @return this builder.
     * @throws IllegalArgumentException if a whole registry was given through {@link #mappers}; that
     *         registry carries its own factory.
     */
    public BeanValidatorBuilder constraintValidatorFactory(ConstraintValidatorFactory factory) {
        Assert.notNull(factory, "factory cannot be null.");
        Assert.isTrue(mapperRegistry == null, "constraintValidatorFactory() cannot be combined with mappers(); give the factory to that registry instead.");
        this.constraintValidatorFactory = factory;
        return this;
    }

    /**
     * Uses the given mapper registry instead of the standard mappers.
     *
     * @param registry the registry.
     * @return this builder.
     */
    public BeanValidatorBuilder mappers(ConstraintMapperRegistry registry) {
        Assert.notNull(registry, "registry cannot be null.");
        this.mapperRegistry = registry;
        return this;
    }

    /**
     * Adds a value extractor to the standard ones; an extractor for a position already served replaces
     * the standard one.
     *
     * @param extractor the extractor.
     * @return this builder.
     * @throws IllegalArgumentException if a whole registry was given through {@link #extractors}.
     */
    public BeanValidatorBuilder extractor(ValueExtractor<?> extractor) {
        Assert.notNull(extractor, "extractor cannot be null.");
        Assert.isTrue(extractorRegistry == null, "extractor() cannot be combined with extractors(); add the extractor to that registry instead.");
        extractors.extractor(extractor);
        return this;
    }

    /**
     * Uses the given extractor registry instead of the standard extractors.
     *
     * @param registry the registry.
     * @return this builder.
     */
    public BeanValidatorBuilder extractors(ValueExtractorRegistry registry) {
        Assert.notNull(registry, "registry cannot be null.");
        this.extractorRegistry = registry;
        return this;
    }

    /**
     * The core context options every validation context is built from. A registry given through
     * {@link #registry(RuleRegistry)} takes precedence over the one in the options.
     *
     * @param options the options.
     * @return this builder.
     */
    public BeanValidatorBuilder options(RuleContextOptions options) {
        Assert.notNull(options, "options cannot be null.");
        this.options = options;
        return this;
    }

    /**
     * The provider naming the parameters of constrained methods and constructors, for the paths and
     * bindings of executable validation. The compiler's names when not set.
     *
     * @param parameterNameProvider the provider.
     * @return this builder.
     */
    public BeanValidatorBuilder parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        Assert.notNull(parameterNameProvider, "parameterNameProvider cannot be null.");
        this.parameterNameProvider = parameterNameProvider;
        return this;
    }

    /**
     * The specification's message interpolator, consulted for the message of every constraint violation
     * with the template as declared, as the reference implementation consults it; the interpolator
     * chooses the locale. Without one, messages render through rulii's message pipeline at the options'
     * locale, which is also exactly what a {@link org.rulii.validation.beans.message.RuliiMessageInterpolator}
     * renders, so that one is never consulted.
     *
     * @param messageInterpolator the interpolator.
     * @return this builder.
     */
    public BeanValidatorBuilder messageInterpolator(MessageInterpolator messageInterpolator) {
        Assert.notNull(messageInterpolator, "messageInterpolator cannot be null.");
        this.messageInterpolator = messageInterpolator;
        return this;
    }

    /**
     * The specification's traversable resolver, asked before a property is validated ({@code isReachable})
     * and before it is cascaded into ({@code isCascadable}), once per object and property per validation
     * call; a property out of reach is neither validated nor read. Class-level constraints, parameters
     * and return values are not subject to it. When not set, {@link TraversableResolvers#getDefault()}:
     * the Jakarta Persistence resolver when the persistence API is on the classpath (a lazy association
     * that was not loaded is left alone), otherwise one that reaches everything and costs nothing.
     *
     * @param traversableResolver the resolver.
     * @return this builder.
     */
    public BeanValidatorBuilder traversableResolver(TraversableResolver traversableResolver) {
        Assert.notNull(traversableResolver, "traversableResolver cannot be null.");
        this.traversableResolver = traversableResolver;
        return this;
    }

    /**
     * Builds the validator.
     *
     * @return the validator.
     */
    public BeanValidator build() {
        RuleContextOptions effective = options != null ? options : StandardRuleContextOptions.build();

        if (mapperRegistry == null) {
            mappers.constraintValidatorFactory(constraintValidatorFactory != null ? constraintValidatorFactory
                    : new DefaultConstraintValidatorFactory(effective.getObjectFactory()));
        }

        RuleRegistry effectiveRegistry = registry != null ? registry : (options != null ? options.getRuleRegistry() : null);

        if (registerFlows && !(effectiveRegistry instanceof MutableRuleRegistry)) {
            throw new UnrulyException("registerFlows needs a MutableRuleRegistry to register the flows in; "
                    + (effectiveRegistry == null ? "no registry is configured." : "the configured registry ["
                    + effectiveRegistry.getClass().getName() + "] is read-only."));
        }

        return new BeanValidator(effectiveRegistry,
                extractorRegistry != null ? extractorRegistry : extractors.build(),
                mapperRegistry != null ? mapperRegistry : mappers.build(),
                effective,
                parameterNameProvider != null ? parameterNameProvider : new DefaultParameterNameProvider(),
                messageInterpolator,
                traversableResolver != null ? traversableResolver : TraversableResolvers.getDefault(),
                registerFlows, failFast);
    }
}
