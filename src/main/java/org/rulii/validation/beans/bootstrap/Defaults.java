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
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import org.rulii.validation.beans.model.DefaultParameterNameProvider;
import org.rulii.validation.beans.traversal.TraversableResolvers;
import org.rulii.validation.beans.validator.DefaultConstraintValidatorFactory;

import java.time.Clock;

/**
 * The default spec services a {@link RuliiConfiguration} reports when none is configured. They are
 * plain: the traversable resolver is the runtime default ({@link TraversableResolvers#getDefault()}), the constraint validator
 * factory is the {@link DefaultConstraintValidatorFactory} (no-argument constructor), the parameter
 * name provider is the {@link DefaultParameterNameProvider} (the compiled parameter names), the clock is
 * the system clock. The default message interpolator is the {@link org.rulii.validation.beans.message.RuliiMessageInterpolator},
 * built per configuration.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class Defaults {

    static final TraversableResolver TRAVERSABLE_RESOLVER = TraversableResolvers.getDefault();
    static final ConstraintValidatorFactory CONSTRAINT_VALIDATOR_FACTORY = new DefaultConstraintValidatorFactory();
    static final ParameterNameProvider PARAMETER_NAME_PROVIDER = new DefaultParameterNameProvider();
    static final ClockProvider CLOCK_PROVIDER = Clock::systemDefaultZone;

    private Defaults() {
        super();
    }
}
