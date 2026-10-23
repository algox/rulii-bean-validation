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
import org.rulii.validation.beans.model.ValidateDefinition;

/**
 * Thrown at validation time when a name declared in {@link Validate @Validate} cannot be run: no
 * {@code RuleRegistry} is configured on the {@code RuleContext}, or the registry holds nothing under
 * the name. Names are resolved when the bean is validated, never when its class is read, so a
 * registration can change between calls; a missing one is reported loudly rather than skipped.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValidateRuleNotFoundException extends BeanValidationException {

    private final Class<?> beanClass;
    private final ValidateDefinition definition;
    private final String ruleName;

    /**
     * Creates the exception.
     *
     * @param beanClass the class whose {@code @Validate} was being run.
     * @param definition the {@code @Validate} declaration.
     * @param ruleName the name that could not be run.
     * @param reason why: no registry, or nothing registered under the name.
     */
    public ValidateRuleNotFoundException(Class<?> beanClass, ValidateDefinition definition, String ruleName, String reason) {
        super("@Validate on [" + describe(definition) + "] of [" + name(beanClass) + "] cannot run rule [" + ruleName + "]: " + reason);
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(ruleName, "ruleName cannot be null.");
        this.beanClass = beanClass;
        this.definition = definition;
        this.ruleName = ruleName;
    }

    private static String describe(ValidateDefinition definition) {
        return definition != null ? definition.getDeclarationSource().describe() : null;
    }

    private static String name(Class<?> beanClass) {
        return beanClass != null ? beanClass.getName() : null;
    }

    /**
     * The class whose {@code @Validate} was being run.
     *
     * @return the bean class.
     */
    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * The {@code @Validate} declaration naming the rule.
     *
     * @return the declaration.
     */
    public ValidateDefinition getDefinition() {
        return definition;
    }

    /**
     * The name that could not be run.
     *
     * @return the registry name.
     */
    public String getRuleName() {
        return ruleName;
    }
}
