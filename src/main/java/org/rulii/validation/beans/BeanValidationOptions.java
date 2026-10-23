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

import org.rulii.bind.match.BindingMatchingStrategy;
import org.rulii.bind.match.ParameterResolver;
import org.rulii.context.RuleContextOptions;
import org.rulii.convert.ConverterRegistry;
import org.rulii.lib.spring.util.Assert;
import org.rulii.registry.RuleRegistry;
import org.rulii.text.MessageFormatter;
import org.rulii.text.MessageResolver;
import org.rulii.trace.Tracer;
import org.rulii.util.reflect.ObjectFactory;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.message.BeanValidationMessageFormatter;
import org.rulii.validation.beans.message.BeanValidationMessageResolver;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Core context options with Bean Validation messages: the options they wrap, with the message
 * resolver replaced by a {@link BeanValidationMessageResolver} consulting the original and the
 * formatter by a {@link BeanValidationMessageFormatter} wrapping the original. The validator builds
 * every context from these; a rulii user embedding the flows in a context of their own can build one
 * with {@link #wrap(RuleContextOptions, ConstraintMapperRegistry)} and get the same messages.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidationOptions implements RuleContextOptions {

    private final RuleContextOptions delegate;
    private final BeanValidationMessageResolver messageResolver;
    private final BeanValidationMessageFormatter messageFormatter;

    private BeanValidationOptions(RuleContextOptions delegate, ConstraintMapperRegistry mappers) {
        super();
        this.delegate = delegate;
        this.messageResolver = new BeanValidationMessageResolver(mappers, delegate.getMessageResolver());
        this.messageFormatter = new BeanValidationMessageFormatter(delegate.getMessageFormatter(), messageResolver);
    }

    /**
     * Wraps options with Bean Validation messages; options already wrapped are returned as they are.
     *
     * @param options the options.
     * @param mappers the constraint mappers.
     * @return the options with Bean Validation messages.
     */
    public static BeanValidationOptions wrap(RuleContextOptions options, ConstraintMapperRegistry mappers) {
        Assert.notNull(options, "options cannot be null.");
        Assert.notNull(mappers, "mappers cannot be null.");
        return options instanceof BeanValidationOptions wrapped ? wrapped : new BeanValidationOptions(options, mappers);
    }

    /**
     * The options wrapped.
     *
     * @return the delegate.
     */
    public RuleContextOptions getDelegate() {
        return delegate;
    }

    @Override
    public BeanValidationMessageResolver getMessageResolver() {
        return messageResolver;
    }

    @Override
    public BeanValidationMessageFormatter getMessageFormatter() {
        return messageFormatter;
    }

    @Override
    public BindingMatchingStrategy getMatchingStrategy() {
        return delegate.getMatchingStrategy();
    }

    @Override
    public ParameterResolver getParameterResolver() {
        return delegate.getParameterResolver();
    }

    @Override
    public ObjectFactory getObjectFactory() {
        return delegate.getObjectFactory();
    }

    @Override
    public ConverterRegistry getConverterRegistry() {
        return delegate.getConverterRegistry();
    }

    @Override
    public Clock getClock() {
        return delegate.getClock();
    }

    @Override
    public Locale getLocale() {
        return delegate.getLocale();
    }

    @Override
    public ExecutorService getExecutorService() {
        return delegate.getExecutorService();
    }

    @Override
    public RuleRegistry getRuleRegistry() {
        return delegate.getRuleRegistry();
    }

    @Override
    public Tracer getTracer() {
        return delegate.getTracer();
    }

    @Override
    public String toString() {
        return "BeanValidationOptions{" + delegate + '}';
    }
}
