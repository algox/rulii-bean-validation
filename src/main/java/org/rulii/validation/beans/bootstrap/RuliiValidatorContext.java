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
import jakarta.validation.ValidatorContext;
import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.BeanValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * The spec's {@link ValidatorContext}: per-validator overrides of a factory's configuration. Value
 * extractors, the clock provider, the constraint validator factory and the parameter name provider
 * reach the validator built by {@link #getValidator()}; the message interpolator and the traversable
 * resolver are accepted for the contract and kept, since the validator does not consult them.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiValidatorContext implements ValidatorContext {

    private final RuliiValidatorFactory factory;
    private final List<ValueExtractor<?>> extractors = new ArrayList<>();
    private MessageInterpolator messageInterpolator;
    private TraversableResolver traversableResolver;
    private ConstraintValidatorFactory constraintValidatorFactory;
    private ParameterNameProvider parameterNameProvider;
    private ClockProvider clockProvider;

    RuliiValidatorContext(RuliiValidatorFactory factory) {
        super();
        Assert.notNull(factory, "factory cannot be null.");
        this.factory = factory;
    }

    @Override
    public RuliiValidatorContext messageInterpolator(MessageInterpolator messageInterpolator) {
        this.messageInterpolator = messageInterpolator;
        return this;
    }

    @Override
    public RuliiValidatorContext traversableResolver(TraversableResolver traversableResolver) {
        this.traversableResolver = traversableResolver;
        return this;
    }

    @Override
    public RuliiValidatorContext constraintValidatorFactory(ConstraintValidatorFactory factory) {
        this.constraintValidatorFactory = factory;
        return this;
    }

    @Override
    public RuliiValidatorContext parameterNameProvider(ParameterNameProvider parameterNameProvider) {
        this.parameterNameProvider = parameterNameProvider;
        return this;
    }

    @Override
    public RuliiValidatorContext clockProvider(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
        return this;
    }

    @Override
    public RuliiValidatorContext addValueExtractor(ValueExtractor<?> extractor) {
        Assert.notNull(extractor, "extractor cannot be null.");
        extractors.add(extractor);
        return this;
    }

    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator != null ? messageInterpolator : factory.getMessageInterpolator();
    }

    public TraversableResolver getTraversableResolver() {
        return traversableResolver != null ? traversableResolver : factory.getTraversableResolver();
    }

    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return constraintValidatorFactory != null ? constraintValidatorFactory : factory.getConstraintValidatorFactory();
    }

    public ParameterNameProvider getParameterNameProvider() {
        return parameterNameProvider != null ? parameterNameProvider : factory.getParameterNameProvider();
    }

    public ClockProvider getClockProvider() {
        return clockProvider != null ? clockProvider : factory.getClockProvider();
    }

    /**
     * A validator with this context's overrides: the factory's validator itself when nothing that
     * reaches the validator was overridden, a new one otherwise.
     */
    @Override
    public BeanValidator getValidator() {
        if (extractors.isEmpty() && clockProvider == null && constraintValidatorFactory == null && parameterNameProvider == null) {
            return factory.getValidator();
        }
        return factory.build(extractors, getClockProvider(), getConstraintValidatorFactory(), getParameterNameProvider());
    }

    @Override
    public String toString() {
        return "RuliiValidatorContext{extractors=" + extractors.size() + ", clockProvider=" + (clockProvider != null)
                + ", constraintValidatorFactory=" + (constraintValidatorFactory != null) + '}';
    }
}
