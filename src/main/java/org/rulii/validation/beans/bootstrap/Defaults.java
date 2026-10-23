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
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidationException;
import org.rulii.lib.spring.util.Assert;

import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The default spec services a {@link RuliiConfiguration} reports when none is configured. They are
 * plain: the traversable resolver reaches and cascades into everything, the constraint validator
 * factory instantiates through the no-argument constructor (constraint validators are not run by this
 * library, but frameworks ask for the factory), the parameter name provider reads the compiled
 * parameter names, the clock is the system clock, and the message interpolator returns the template
 * (message rendering is rulii's, through the context's message resolver).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class Defaults {

    static final MessageInterpolator MESSAGE_INTERPOLATOR = new TemplateMessageInterpolator();
    static final TraversableResolver TRAVERSABLE_RESOLVER = new ReachEverythingTraversableResolver();
    static final ConstraintValidatorFactory CONSTRAINT_VALIDATOR_FACTORY = new NoArgConstraintValidatorFactory();
    static final ParameterNameProvider PARAMETER_NAME_PROVIDER = new ReflectionParameterNameProvider();
    static final ClockProvider CLOCK_PROVIDER = Clock::systemDefaultZone;

    private Defaults() {
        super();
    }

    static final class TemplateMessageInterpolator implements MessageInterpolator {

        private TemplateMessageInterpolator() {
            super();
        }

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return messageTemplate;
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return messageTemplate;
        }
    }

    static final class ReachEverythingTraversableResolver implements TraversableResolver {

        private ReachEverythingTraversableResolver() {
            super();
        }

        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                   Path pathToTraversableObject, ElementType elementType) {
            return true;
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                    Path pathToTraversableObject, ElementType elementType) {
            return true;
        }
    }

    static final class NoArgConstraintValidatorFactory implements ConstraintValidatorFactory {

        private NoArgConstraintValidatorFactory() {
            super();
        }

        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            Assert.notNull(key, "key cannot be null.");
            try {
                Constructor<T> constructor = key.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new ValidationException("cannot instantiate constraint validator [" + key.getName() + "]", e);
            }
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
            // Nothing to release.
        }
    }

    static final class ReflectionParameterNameProvider implements ParameterNameProvider {

        private ReflectionParameterNameProvider() {
            super();
        }

        @Override
        public List<String> getParameterNames(Constructor<?> constructor) {
            Assert.notNull(constructor, "constructor cannot be null.");
            return names(constructor);
        }

        @Override
        public List<String> getParameterNames(Method method) {
            Assert.notNull(method, "method cannot be null.");
            return names(method);
        }

        private static List<String> names(Executable executable) {
            List<String> result = new ArrayList<>();

            for (Parameter parameter : executable.getParameters()) {
                result.add(parameter.getName());
            }

            return Collections.unmodifiableList(result);
        }
    }
}
