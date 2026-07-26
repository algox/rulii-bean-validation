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

import org.rulii.lib.spring.util.Assert;
import org.rulii.registry.RuleRegistry;
import org.rulii.text.MessageResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Builder for {@link BeanValidator}. All configuration is build-time: the rule registry
 * backing {@code @Validate} resolution, the constraint-mapper registry (defaults to the
 * 22 standard mappers), user message resolution (a resolver and/or bundle base names —
 * the {@code ValidationMessages} bundle is always consulted last), and the default
 * locale.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidatorBuilder {

    private ConstraintMapperRegistry mapperRegistry = ConstraintMapperRegistry.builder().standard().build();
    private RuleRegistry ruleRegistry;
    private MessageResolver messageResolver;
    private final List<String> bundleBaseNames = new ArrayList<>();
    private Locale locale;

    BeanValidatorBuilder() {
        super();
    }

    /**
     * Sets the constraint-mapper registry (replacing the standard default; compose via
     * {@code ConstraintMapperRegistry.builder().standard().register(...)}).
     *
     * @param mapperRegistry the mapper registry.
     * @return this builder for fluent chaining.
     */
    public BeanValidatorBuilder mapperRegistry(ConstraintMapperRegistry mapperRegistry) {
        Assert.notNull(mapperRegistry, "mapperRegistry cannot be null.");
        this.mapperRegistry = mapperRegistry;
        return this;
    }

    /**
     * Sets the rule registry {@code @Validate} names resolve against.
     *
     * @param ruleRegistry the rule registry.
     * @return this builder for fluent chaining.
     */
    public BeanValidatorBuilder ruleRegistry(RuleRegistry ruleRegistry) {
        Assert.notNull(ruleRegistry, "ruleRegistry cannot be null.");
        this.ruleRegistry = ruleRegistry;
        return this;
    }

    /**
     * Sets a user message resolver, consulted before any bundles.
     *
     * @param messageResolver the resolver supplying jakarta-syntax text.
     * @return this builder for fluent chaining.
     */
    public BeanValidatorBuilder messageResolver(MessageResolver messageResolver) {
        Assert.notNull(messageResolver, "messageResolver cannot be null.");
        this.messageResolver = messageResolver;
        return this;
    }

    /**
     * Adds resource-bundle base names, consulted in order before the standard
     * {@code ValidationMessages} bundle.
     *
     * @param baseNames the bundle base names.
     * @return this builder for fluent chaining.
     */
    public BeanValidatorBuilder bundles(String... baseNames) {
        Assert.notNull(baseNames, "baseNames cannot be null.");
        this.bundleBaseNames.addAll(Arrays.asList(baseNames));
        return this;
    }

    /**
     * Sets the default locale for message resolution and formatting.
     *
     * @param locale the default locale.
     * @return this builder for fluent chaining.
     */
    public BeanValidatorBuilder locale(Locale locale) {
        Assert.notNull(locale, "locale cannot be null.");
        this.locale = locale;
        return this;
    }

    /**
     * Builds the validator.
     *
     * @return a new {@link BeanValidator}.
     */
    public BeanValidator build() {
        return new BeanValidator(mapperRegistry, ruleRegistry, messageResolver, bundleBaseNames, locale);
    }
}
