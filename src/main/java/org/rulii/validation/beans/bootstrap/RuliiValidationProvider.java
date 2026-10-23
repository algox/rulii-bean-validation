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

import jakarta.validation.Configuration;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.BootstrapState;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.spi.ValidationProvider;
import org.rulii.lib.spring.util.Assert;

/**
 * The Bean Validation provider of rulii bean validation: what the spec's bootstrap finds through
 * {@code META-INF/services/jakarta.validation.spi.ValidationProvider}. With this library as the only
 * provider on the classpath, {@code Validation.buildDefaultValidatorFactory().getValidator()}
 * hands out a {@code BeanValidator} with no code change, and so does every framework that bootstraps
 * through {@code Validation}. Provider-specific configuration:
 * <pre>
 * Validator validator = Validation.byProvider(RuliiValidationProvider.class)
 *         .configure()
 *         .registry(registry)
 *         .buildValidatorFactory()
 *         .getValidator();
 * </pre>
 * XML configuration ({@code META-INF/validation.xml}, constraint mapping files) is not supported and
 * never read.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiValidationProvider implements ValidationProvider<RuliiConfiguration> {

    /**
     * Creates the provider; the bootstrap instantiates it through this constructor.
     */
    public RuliiValidationProvider() {
        super();
    }

    @Override
    public RuliiConfiguration createSpecializedConfiguration(BootstrapState state) {
        return new RuliiConfiguration(this, state);
    }

    @Override
    public Configuration<?> createGenericConfiguration(BootstrapState state) {
        return new RuliiConfiguration(null, state);
    }

    @Override
    public ValidatorFactory buildValidatorFactory(ConfigurationState configurationState) {
        Assert.notNull(configurationState, "configurationState cannot be null.");
        return new RuliiValidatorFactory(configurationState);
    }

    @Override
    public String toString() {
        return "RuliiValidationProvider";
    }
}
