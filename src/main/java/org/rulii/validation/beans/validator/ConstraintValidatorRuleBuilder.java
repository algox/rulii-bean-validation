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
package org.rulii.validation.beans.validator;

import jakarta.validation.ConstraintValidator;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.function.Function;
import org.rulii.validation.Severity;
import org.rulii.validation.ValueValidationRuleBuilder;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for {@link ConstraintValidatorRule}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintValidatorRuleBuilder extends ValueValidationRuleBuilder<ConstraintValidatorRuleBuilder, ConstraintValidatorRule> {

    private final ConstraintValidator<?, ?> validator;
    private final Class<? extends Annotation> annotationType;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final List<Class<?>> supportedTypes = new ArrayList<>();
    private String defaultMessageTemplate = "";

    public ConstraintValidatorRuleBuilder(Function<?> valueFunction, ConstraintValidator<?, ?> validator,
                                          Class<? extends Annotation> annotationType) {
        super(valueFunction);
        Assert.notNull(validator, "validator cannot be null.");
        Assert.notNull(annotationType, "annotationType cannot be null.");
        this.validator = validator;
        this.annotationType = annotationType;
        errorCode(ConstraintValidatorRule.ERROR_CODE);
        severity(Severity.ERROR);
        supportedTypes.add(Object.class);
    }

    /**
     * The constraint's message template as declared, what the validator sees as the default template.
     *
     * @param defaultMessageTemplate the template.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder defaultMessageTemplate(String defaultMessageTemplate) {
        Assert.notNull(defaultMessageTemplate, "defaultMessageTemplate cannot be null.");
        this.defaultMessageTemplate = defaultMessageTemplate;
        return this;
    }

    /**
     * Parameters the violations report beside the value, typically the constraint's attributes.
     *
     * @param parameters the parameters by name.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder parameters(Map<String, Object> parameters) {
        Assert.notNull(parameters, "parameters cannot be null.");
        this.parameters.putAll(parameters);
        return this;
    }

    /**
     * The value types the validator validates; values of other types are skipped. {@code Object} when
     * not set.
     *
     * @param supportedTypes the types.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder supportedTypes(List<Class<?>> supportedTypes) {
        Assert.notNull(supportedTypes, "supportedTypes cannot be null.");
        Assert.isTrue(!supportedTypes.isEmpty(), "supportedTypes cannot be empty.");
        this.supportedTypes.clear();
        this.supportedTypes.addAll(supportedTypes);
        return this;
    }

    @Override
    public ConstraintValidatorRuleBuilder failOnNull(boolean failOnNull) {
        Assert.isTrue(failOnNull, "failOnNull(false) is not allowed on [constraintValidatorRule]; the validator decides what null means.");
        return super.failOnNull(failOnNull);
    }

    @Override
    protected ConstraintValidatorRule createValueValidationRule() {
        return new ConstraintValidatorRule(getValueFunction(), getErrorCode(), getSeverity(), getErrorMessage(), getValueName(),
                validator, annotationType, defaultMessageTemplate, parameters, supportedTypes);
    }
}
