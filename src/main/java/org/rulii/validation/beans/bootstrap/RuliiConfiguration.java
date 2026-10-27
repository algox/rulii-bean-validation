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
package org.rulii.validation.beans.bootstrap;

import jakarta.validation.BootstrapConfiguration;
import jakarta.validation.ClockProvider;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidationProviderResolver;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.executable.ExecutableType;
import jakarta.validation.spi.BootstrapState;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.spi.ValidationProvider;
import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.context.RuleContextOptions;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.lib.spring.util.Assert;
import org.rulii.registry.RuleRegistry;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistryBuilder;
import org.rulii.validation.beans.message.RuliiMessageInterpolator;
import org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The spec's {@link Configuration} for rulii bean validation, and the {@link ConfigurationState} the
 * factory is built from. Besides the spec's settings it takes what {@code BeanValidator.builder()}
 * takes: the {@link #registry(RuleRegistry) rule registry} for {@code @Validate}, extra
 * {@link #mapper(ConstraintMapper) constraint mappers}, and the {@link #options(RuleContextOptions)
 * core context options}.
 * <p>
 * XML is never read: {@link #ignoreXmlConfiguration()} is already the case, and
 * {@link #addMapping(InputStream)} fails, since constraint mapping files are not supported. The
 * message interpolator, traversable resolver, constraint validator factory and parameter name provider
 * are recorded and exposed by the factory for frameworks that set them; the clock provider is wired into
 * the rules' context.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiConfiguration implements Configuration<RuliiConfiguration>, ConfigurationState {

    private final RuliiValidationProvider provider;
    private final BootstrapState state;
    private MessageInterpolator messageInterpolator;
    private TraversableResolver traversableResolver;
    private ConstraintValidatorFactory constraintValidatorFactory;
    private ParameterNameProvider parameterNameProvider;
    private ClockProvider clockProvider;
    private final Set<ValueExtractor<?>> extractors = new LinkedHashSet<>();
    private final Map<String, String> properties = new LinkedHashMap<>();
    private RuleRegistry registry;
    private boolean registerFlows;
    private final List<ConstraintMapper<?>> mappers = new ArrayList<>();
    private RuleContextOptions options;
    private MessageInterpolator defaultMessageInterpolator;
    private ConstraintValidatorFactory defaultConstraintValidatorFactory;

    /**
     * Creates a configuration.
     *
     * @param provider the provider that will build the factory; null for a generic configuration, which
     *                 builds through the first provider the bootstrap resolves.
     * @param state the bootstrap state; may be null.
     */
    RuliiConfiguration(RuliiValidationProvider provider, BootstrapState state) {
        super();
        this.provider = provider;
        this.state = state;
    }

    // ---- rulii settings ----

    /**
     * The registry {@code @Validate} names resolve through.
     *
     * @param registry the registry.
     * @return this configuration.
     */
    public RuliiConfiguration registry(RuleRegistry registry) {
        Assert.notNull(registry, "registry cannot be null.");
        this.registry = registry;
        return this;
    }

    /**
     * Whether the validators register the flows they build in the {@link #registry(RuleRegistry)
     * registry}, as {@code BeanValidator.builder().registerFlows(true)} does. Off by default; needs a
     * mutable registry.
     *
     * @param registerFlows true to register the flows.
     * @return this configuration.
     */
    public RuliiConfiguration registerFlows(boolean registerFlows) {
        this.registerFlows = registerFlows;
        return this;
    }

    /**
     * Adds a constraint mapper to the standard ones.
     *
     * @param mapper the mapper.
     * @return this configuration.
     */
    public RuliiConfiguration mapper(ConstraintMapper<?> mapper) {
        Assert.notNull(mapper, "mapper cannot be null.");
        mappers.add(mapper);
        return this;
    }

    /**
     * The core context options every validation starts from.
     *
     * @param options the options.
     * @return this configuration.
     */
    public RuliiConfiguration options(RuleContextOptions options) {
        Assert.notNull(options, "options cannot be null.");
        this.options = options;
        return this;
    }

    public RuleRegistry getRuleRegistry() {
        return registry;
    }

    public boolean isRegisterFlows() {
        return registerFlows;
    }

    public List<ConstraintMapper<?>> getConstraintMappers() {
        return Collections.unmodifiableList(mappers);
    }

    public RuleContextOptions getOptions() {
        return options;
    }

    // ---- jakarta.validation.Configuration ----

    /**
     * XML configuration is never read; this is a no-op kept for the contract.
     */
    @Override
    public RuliiConfiguration ignoreXmlConfiguration() {
        return this;
    }

    @Override
    public RuliiConfiguration messageInterpolator(MessageInterpolator interpolator) {
        this.messageInterpolator = interpolator;
        return this;
    }

    @Override
    public RuliiConfiguration traversableResolver(TraversableResolver resolver) {
        this.traversableResolver = resolver;
        return this;
    }

    @Override
    public RuliiConfiguration constraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
        this.constraintValidatorFactory = constraintValidatorFactory;
        return this;
    }

    @Override
    public RuliiConfiguration parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        this.parameterNameProvider = parameterNameProvider;
        return this;
    }

    @Override
    public RuliiConfiguration clockProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    public RuliiConfiguration addValueExtractor(ValueExtractor<?> extractor) {
        Assert.notNull(extractor, "extractor cannot be null.");
        extractors.add(extractor);
        return this;
    }

    /**
     * Constraint mapping files are not supported.
     *
     * @throws ValidationException always.
     */
    @Override
    public RuliiConfiguration addMapping(InputStream stream) {
        throw new ValidationException("XML constraint mappings are not supported by rulii bean validation; declare constraints with annotations.");
    }

    @Override
    public RuliiConfiguration addProperty(String name, String value) {
        Assert.notNull(name, "name cannot be null.");
        properties.put(name, value);
        return this;
    }

    /**
     * The interpolator rendering as the validator does, over this configuration's mappers and options.
     */
    @Override
    public MessageInterpolator getDefaultMessageInterpolator() {
        if (defaultMessageInterpolator == null) {
            ConstraintMapperRegistryBuilder registry = ConstraintMapperRegistryBuilder.standard();
            for (ConstraintMapper<?> mapper : mappers) {
                registry.mapper(mapper);
            }
            defaultMessageInterpolator = new RuliiMessageInterpolator(registry.build(),
                    options != null ? options : StandardRuleContextOptions.build());
        }
        return defaultMessageInterpolator;
    }

    @Override
    public TraversableResolver getDefaultTraversableResolver() {
        return Defaults.TRAVERSABLE_RESOLVER;
    }

    /**
     * The factory creating validators through the object factory of this configuration's options.
     */
    @Override
    public ConstraintValidatorFactory getDefaultConstraintValidatorFactory() {
        if (defaultConstraintValidatorFactory == null) {
            defaultConstraintValidatorFactory = new DefaultConstraintValidatorFactory(
                    (options != null ? options : StandardRuleContextOptions.build()).getObjectFactory());
        }
        return defaultConstraintValidatorFactory;
    }

    @Override
    public ParameterNameProvider getDefaultParameterNameProvider() {
        return Defaults.PARAMETER_NAME_PROVIDER;
    }

    @Override
    public ClockProvider getDefaultClockProvider() {
        return Defaults.CLOCK_PROVIDER;
    }

    /**
     * What a {@code META-INF/validation.xml} would have said: nothing, since none is read.
     */
    @Override
    public BootstrapConfiguration getBootstrapConfiguration() {
        return NoXmlBootstrapConfiguration.INSTANCE;
    }

    @Override
    public ValidatorFactory buildValidatorFactory() {
        ValidationProvider<?> target = provider != null ? provider : resolveProvider();
        return target.buildValidatorFactory(this);
    }

    private ValidationProvider<?> resolveProvider() {
        ValidationProviderResolver resolver = state != null
                ? (state.getValidationProviderResolver() != null ? state.getValidationProviderResolver() : state.getDefaultValidationProviderResolver())
                : null;
        List<ValidationProvider<?>> providers = resolver != null ? resolver.getValidationProviders() : List.of();
        return providers.isEmpty() ? new RuliiValidationProvider() : providers.get(0);
    }

    // ---- jakarta.validation.spi.ConfigurationState ----

    @Override
    public boolean isIgnoreXmlConfiguration() {
        return true;
    }

    @Override
    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator != null ? messageInterpolator : getDefaultMessageInterpolator();
    }

    @Override
    public Set<InputStream> getMappingStreams() {
        return Set.of();
    }

    @Override
    public Set<ValueExtractor<?>> getValueExtractors() {
        return Collections.unmodifiableSet(extractors);
    }

    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory != null ? constraintValidatorFactory : getDefaultConstraintValidatorFactory();
    }

    @Override
    public TraversableResolver getTraversableResolver() {
        return traversableResolver != null ? traversableResolver : getDefaultTraversableResolver();
    }

    @Override
    public ParameterNameProvider getParameterNameProvider() {
        return parameterNameProvider != null ? parameterNameProvider : getDefaultParameterNameProvider();
    }

    @Override
    public ClockProvider getClockProvider() {
        return clockProvider != null ? clockProvider : getDefaultClockProvider();
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public String toString() {
        return "RuliiConfiguration{registry=" + (registry != null) + ", mappers=" + mappers.size()
                + ", extractors=" + extractors.size() + ", properties=" + properties.keySet() + '}';
    }

    /**
     * The bootstrap configuration of a deployment without {@code validation.xml}.
     */
    private static final class NoXmlBootstrapConfiguration implements BootstrapConfiguration {

        private static final NoXmlBootstrapConfiguration INSTANCE = new NoXmlBootstrapConfiguration();

        private NoXmlBootstrapConfiguration() {
            super();
        }

        @Override
        public String getDefaultProviderClassName() {
            return null;
        }

        @Override
        public String getConstraintValidatorFactoryClassName() {
            return null;
        }

        @Override
        public String getMessageInterpolatorClassName() {
            return null;
        }

        @Override
        public String getTraversableResolverClassName() {
            return null;
        }

        @Override
        public String getParameterNameProviderClassName() {
            return null;
        }

        @Override
        public String getClockProviderClassName() {
            return null;
        }

        @Override
        public Set<String> getValueExtractorClassNames() {
            return Set.of();
        }

        @Override
        public Set<String> getConstraintMappingResourcePaths() {
            return Set.of();
        }

        @Override
        public boolean isExecutableValidationEnabled() {
            return false;
        }

        @Override
        public Set<ExecutableType> getDefaultValidatedExecutableTypes() {
            return Set.of();
        }

        @Override
        public Map<String, String> getProperties() {
            return Map.of();
        }
    }
}
