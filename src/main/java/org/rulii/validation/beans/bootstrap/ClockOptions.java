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

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Core context options taking their clock from a spec {@link ClockProvider}, everything else from the
 * options they wrap: how a configured clock provider reaches the rules ({@code @Past}, {@code @Future}
 * and friends read the context clock).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ClockOptions implements RuleContextOptions {

    private final RuleContextOptions delegate;
    private final ClockProvider clockProvider;

    ClockOptions(RuleContextOptions delegate, ClockProvider clockProvider) {
        super();
        Assert.notNull(delegate, "delegate cannot be null.");
        Assert.notNull(clockProvider, "clockProvider cannot be null.");
        this.delegate = delegate;
        this.clockProvider = clockProvider;
    }

    @Override
    public Clock getClock() {
        Clock clock = clockProvider.getClock();
        return clock != null ? clock : delegate.getClock();
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
    public MessageResolver getMessageResolver() {
        return delegate.getMessageResolver();
    }

    @Override
    public MessageFormatter getMessageFormatter() {
        return delegate.getMessageFormatter();
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
}
