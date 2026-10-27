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
import org.rulii.model.SourceDefinition;
import org.rulii.model.function.Function;
import org.rulii.validation.Severity;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for {@link ConstraintValidatorRule}. The rule is atomic and does not go through the class-based
 * rule builder, so this builder assembles it directly; the configuration surface mirrors what
 * {@link org.rulii.validation.beans.mapper.ConstraintTarget#configure} applies to a core rule builder.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintValidatorRuleBuilder {

    public static final String DEFAULT_VALUE_NAME = "value";

    private final Function<?> valueFunction;
    private final ConstraintValidator<?, ?> validator;
    private final Class<? extends Annotation> annotationType;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private String name;
    private String description;
    private String category;
    private final List<String> tags = new ArrayList<>();
    private SourceDefinition source;
    private String valueName = DEFAULT_VALUE_NAME;
    private String errorCode = ConstraintValidatorRule.ERROR_CODE;
    private Severity severity = Severity.ERROR;
    private String errorMessage;
    private String defaultMessageTemplate = "";

    public ConstraintValidatorRuleBuilder(Function<?> valueFunction, ConstraintValidator<?, ?> validator,
                                          Class<? extends Annotation> annotationType) {
        super();
        Assert.notNull(valueFunction, "valueFunction cannot be null.");
        Assert.notNull(validator, "validator cannot be null.");
        Assert.notNull(annotationType, "annotationType cannot be null.");
        this.valueFunction = valueFunction;
        this.validator = validator;
        this.annotationType = annotationType;
    }

    public ConstraintValidatorRuleBuilder name(String name) {
        Assert.hasText(name, "name cannot be null/empty.");
        this.name = name;
        return this;
    }

    public ConstraintValidatorRuleBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Where the rule belongs, for tools that group rules; null for none.
     *
     * @param category the category.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder category(String category) {
        this.category = category;
        return this;
    }

    /**
     * What the rule is about, for tools that filter rules; adds to the tags given before.
     *
     * @param tags the tags.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder tags(String... tags) {
        if (tags != null) this.tags.addAll(Arrays.asList(tags));
        return this;
    }

    /**
     * What the rule is about, for tools that filter rules; adds to the tags given before.
     *
     * @param tags the tags.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder tags(Collection<String> tags) {
        if (tags != null) this.tags.addAll(tags);
        return this;
    }

    /**
     * Where the constraint was declared; the caller is recorded when none is given.
     *
     * @param source the source.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder source(SourceDefinition source) {
        this.source = source;
        return this;
    }

    public ConstraintValidatorRuleBuilder valueName(String valueName) {
        Assert.hasText(valueName, "valueName cannot be null/empty.");
        this.valueName = valueName;
        return this;
    }

    public ConstraintValidatorRuleBuilder errorCode(String errorCode) {
        Assert.hasText(errorCode, "errorCode cannot be null/empty.");
        this.errorCode = errorCode;
        return this;
    }

    public ConstraintValidatorRuleBuilder severity(Severity severity) {
        Assert.notNull(severity, "severity cannot be null.");
        this.severity = severity;
        return this;
    }

    /**
     * The declared message; null leaves the message to the resolver.
     *
     * @param errorMessage the message.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder message(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    /**
     * The constraint's message template as declared: what the validator's context reports as the default template.
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
     * The parameters the violations report beside the value: the constraint's attributes.
     *
     * @param parameters the parameters.
     * @return this builder.
     */
    public ConstraintValidatorRuleBuilder parameters(Map<String, Object> parameters) {
        Assert.notNull(parameters, "parameters cannot be null.");
        this.parameters.putAll(parameters);
        return this;
    }

    public ConstraintValidatorRule build() {
        return new ConstraintValidatorRule(name, description, category, tags, source, valueFunction, valueName, errorCode, severity,
                errorMessage, validator, annotationType, defaultMessageTemplate, parameters);
    }
}
