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
package org.rulii.validation.beans.traversal;

import org.rulii.validation.beans.BeanValidationException;

/**
 * Raised when a {@code @Validate} rule name cannot be resolved in the RuleContext's
 * rule registry at execution time (R9.4). Resolution is deliberately lazy — the
 * registry is swappable — so the failure carries the full declaration context.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ValidateRuleNotFoundException extends BeanValidationException {

    private final Class<?> beanClass;
    private final String propertyName;
    private final String ruleName;

    public ValidateRuleNotFoundException(Class<?> beanClass, String propertyName, String ruleName) {
        super("No rule named [" + ruleName + "] found in the rule registry. Declared via @Validate on ["
                + beanClass.getName() + (propertyName != null ? "." + propertyName : "") + "].");
        this.beanClass = beanClass;
        this.propertyName = propertyName;
        this.ruleName = ruleName;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getRuleName() {
        return ruleName;
    }
}
