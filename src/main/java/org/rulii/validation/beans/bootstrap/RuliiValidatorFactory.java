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

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.context.RuleContextOptions;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.lib.spring.util.Assert;
import org.rulii.registry.RuleRegistry;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.BeanValidatorBuilder;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.message.RuliiMessageInterpolator;
import org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The spec's {@link ValidatorFactory} for rulii bean validation: built by the provider from a
 * {@link ConfigurationState}, it holds one {@link BeanValidator} configured from it and hands it out from
 * {@link #getValidator()}; {@link #usingContext()} builds validators with per-context overrides.
 * <p>
 * The constraint validator factory, the parameter name provider, the clock provider and the message
 * interpolator are wired into the validator: the first creates the validators of validator-backed
 * constraints, the second names the parameters of constrained executables, the third is the rules'
 * clock, the fourth renders the messages of constraint violations when it is not our own (see
 * {@link BeanValidatorBuilder#messageInterpolator}), and the traversable resolver decides which properties
 * are validated and cascaded into. Closing the factory hands the constraint validator
 * instances of every validator it built back to their factory; the validators stay usable.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiValidatorFactory implements ValidatorFactory {

    private final MessageInterpolator messageInterpolator;
    private final TraversableResolver traversableResolver;
    private final ConstraintValidatorFactory constraintValidatorFactory;
    private final ParameterNameProvider parameterNameProvider;
    private final ClockProvider clockProvider;
    private final Map<String, String> properties;
    private final RuleRegistry registry;
    private final List<ConstraintMapper<?>> mappers;
    private final RuleContextOptions options;
    private final List<ValueExtractor<?>> extractors;
    private final List<BeanValidator> validators = new ArrayList<>();
    private final BeanValidator validator;

    /**
     * Builds the factory, and its validator, from a configuration.
     *
     * @param state the configuration; a {@link RuliiConfiguration} also contributes the registry, the
     *              mappers and the context options.
     */
    public RuliiValidatorFactory(ConfigurationState state) {
        super();
        Assert.notNull(state, "state cannot be null.");
        RuliiConfiguration ours = state instanceof RuliiConfiguration configuration ? configuration : null;
        this.messageInterpolator = state.getMessageInterpolator() != null ? state.getMessageInterpolator()
                : new RuliiMessageInterpolator(ConstraintMapperRegistry.builder().build());
        this.traversableResolver = state.getTraversableResolver() != null ? state.getTraversableResolver() : Defaults.TRAVERSABLE_RESOLVER;
        this.parameterNameProvider = state.getParameterNameProvider() != null ? state.getParameterNameProvider() : Defaults.PARAMETER_NAME_PROVIDER;
        this.clockProvider = state.getClockProvider() != null ? state.getClockProvider() : Defaults.CLOCK_PROVIDER;
        this.properties = state.getProperties() != null ? Map.copyOf(state.getProperties()) : Map.of();
        this.registry = ours != null ? ours.getRuleRegistry() : null;
        this.mappers = ours != null ? ours.getConstraintMappers() : List.of();
        this.options = ours != null ? ours.getOptions() : null;
        this.constraintValidatorFactory = state.getConstraintValidatorFactory() != null ? state.getConstraintValidatorFactory()
                : new DefaultConstraintValidatorFactory((options != null ? options : StandardRuleContextOptions.build()).getObjectFactory());
        this.extractors = state.getValueExtractors() != null ? List.copyOf(state.getValueExtractors()) : List.of();
        this.validator = build(List.of(), clockProvider, constraintValidatorFactory, parameterNameProvider, messageInterpolator, traversableResolver);
    }

    /**
     * Builds a validator from this factory's configuration plus per-context overrides, and remembers it
     * for {@link #close()}.
     */
    BeanValidator build(Collection<ValueExtractor<?>> additionalExtractors, ClockProvider clock,
                        ConstraintValidatorFactory constraintValidatorFactory, ParameterNameProvider parameterNameProvider,
                        MessageInterpolator messageInterpolator, TraversableResolver traversableResolver) {
        BeanValidatorBuilder builder = BeanValidator.builder();
        RuleContextOptions base = options != null ? options : StandardRuleContextOptions.build();

        builder.options(new ClockOptions(base, clock));
        builder.constraintValidatorFactory(constraintValidatorFactory);
        builder.parameterNameProvider(parameterNameProvider);
        builder.messageInterpolator(messageInterpolator);
        builder.traversableResolver(traversableResolver);
        if (registry != null) builder.registry(registry);

        for (ConstraintMapper<?> mapper : mappers) {
            builder.mapper(mapper);
        }

        for (ValueExtractor<?> extractor : extractors) {
            builder.extractor(extractor);
        }

        for (ValueExtractor<?> extractor : additionalExtractors) {
            builder.extractor(extractor);
        }

        BeanValidator result = builder.build();

        synchronized (validators) {
            validators.add(result);
        }

        return result;
    }

    /**
     * The validator configured for this factory; the same instance every time.
     */
    @Override
    public BeanValidator getValidator() {
        return validator;
    }

    @Override
    public RuliiValidatorContext usingContext() {
        return new RuliiValidatorContext(this);
    }

    @Override
    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator;
    }

    @Override
    public TraversableResolver getTraversableResolver() {
        return traversableResolver;
    }

    @Override
    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory;
    }

    @Override
    public ParameterNameProvider getParameterNameProvider() {
        return parameterNameProvider;
    }

    @Override
    public ClockProvider getClockProvider() {
        return clockProvider;
    }

    /**
     * The properties given to the configuration, as given.
     *
     * @return the properties; unmodifiable.
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * The value extractors given to the configuration.
     *
     * @return the extractors; unmodifiable.
     */
    public List<ValueExtractor<?>> getValueExtractors() {
        return Collections.unmodifiableList(new ArrayList<>(extractors));
    }

    /**
     * This factory, or its validator, viewed as the given type.
     *
     * @throws ValidationException if neither is of that type.
     */
    @Override
    public <T> T unwrap(Class<T> type) {
        Assert.notNull(type, "type cannot be null.");
        if (type.isInstance(this)) return type.cast(this);
        if (type.isInstance(validator)) return type.cast(validator);
        throw new ValidationException("cannot unwrap [" + getClass().getName() + "] to [" + type.getName() + "]");
    }

    /**
     * Hands the constraint validator instances created by every validator built by this factory back
     * to their constraint validator factory, as the specification asks. The validators stay usable
     * with the instances their rules hold; what releasing means to those instances is the constraint
     * validator factory's business.
     */
    @Override
    public void close() {
        List<BeanValidator> built;

        synchronized (validators) {
            built = new ArrayList<>(validators);
        }

        for (BeanValidator beanValidator : built) {
            beanValidator.getConstraintMapperRegistry().release();
        }
    }

    @Override
    public String toString() {
        return "RuliiValidatorFactory{" + validator + '}';
    }
}
