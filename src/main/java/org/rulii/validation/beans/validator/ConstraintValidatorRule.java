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
import org.rulii.annotation.Description;
import org.rulii.annotation.Otherwise;
import org.rulii.annotation.Param;
import org.rulii.bind.match.MatchByTypeMatchingStrategy;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.model.function.Function;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolationBuilder;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.ValueValidationRule;
import org.rulii.validation.beans.CustomPathRuleViolation;
import org.rulii.validation.beans.assembly.RuleMaterializer;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rule of a constraint backed by a {@link ConstraintValidator}: runs the validator's
 * {@code isValid} against the value with a {@link RuliiConstraintValidatorContext}, and reports what the
 * validator asked for. The default violation carries the constraint's error code and message, like any
 * materialized rule; it is withheld when the validator disabled it. Each custom violation the validator
 * built is reported with its own template, translated like a declared message (the constraint's own
 * default key resolves through the error code, another key becomes a placeholder, a literal is translated
 * once), as a {@link CustomPathRuleViolation} carrying the nodes it added.
 *
 * <p>The rule runs on null: the specification hands null to the validator and lets it decide. Values of
 * a type the validator does not validate are skipped by the type check, as with any value rule. The
 * constraint's attributes are reported as parameters beside the value, so templates can reference them.</p>
 *
 * <p>The context a failed evaluation produced travels from the condition to the otherwise action in a
 * thread-local slot: core runs a rule's condition and its actions one after the other on one thread, and
 * the validator instance is shared by every evaluation of the occurrence.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@org.rulii.annotation.Rule
@Description("Runs a jakarta.validation.ConstraintValidator against the value.")
public class ConstraintValidatorRule extends ValueValidationRule {

    /**
     * The types supported before the validator's own are known: core reads them while constructing the
     * rule; {@link #isSupported(Class)} checks against the validator's types.
     */
    public static final List<Class<?>> SUPPORTED_TYPES = List.of(Object.class);

    public static final String ERROR_CODE       = "constraintValidatorRule.errorCode";
    public static final String DEFAULT_MESSAGE  = "Value is not valid.";

    private final ConstraintValidator<Annotation, Object> validator;
    private final Class<? extends ConstraintValidator<?, ?>> validatorClass;
    private final Class<? extends Annotation> annotationType;
    private final String defaultMessageTemplate;
    private final Map<String, Object> parameters;
    private final List<Class<?>> supportedTypes;
    private final ThreadLocal<RuliiConstraintValidatorContext> failed = new ThreadLocal<>();

    /**
     * Creates a builder.
     *
     * @param valueFunction the function reading the validated value.
     * @param validator the validator, initialized.
     * @param annotationType the constraint annotation type.
     * @return the builder.
     */
    public static ConstraintValidatorRuleBuilder builder(Function<?> valueFunction, ConstraintValidator<?, ?> validator,
                                                         Class<? extends Annotation> annotationType) {
        return new ConstraintValidatorRuleBuilder(valueFunction, validator, annotationType);
    }

    @SuppressWarnings("unchecked")
    ConstraintValidatorRule(Function<?> valueFunction, String errorCode, Severity severity, String errorMessage,
                            String valueName, ConstraintValidator<?, ?> validator, Class<? extends Annotation> annotationType,
                            String defaultMessageTemplate, Map<String, Object> parameters, List<Class<?>> supportedTypes) {
        super(valueFunction, errorCode, severity, errorMessage, DEFAULT_MESSAGE, valueName);
        Assert.notNull(validator, "validator cannot be null.");
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(defaultMessageTemplate, "defaultMessageTemplate cannot be null.");
        Assert.notNull(parameters, "parameters cannot be null.");
        Assert.notNull(supportedTypes, "supportedTypes cannot be null.");
        this.validator = (ConstraintValidator<Annotation, Object>) validator;
        this.validatorClass = (Class<? extends ConstraintValidator<?, ?>>) validator.getClass();
        this.annotationType = annotationType;
        this.defaultMessageTemplate = defaultMessageTemplate;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.supportedTypes = List.copyOf(supportedTypes);
    }

    /**
     * The validator run against the value.
     *
     * @return the validator.
     */
    public ConstraintValidator<?, ?> getValidator() {
        return validator;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    /**
     * The constraint's message template as declared, what the validator sees as the default template.
     *
     * @return the template.
     */
    public String getDefaultMessageTemplate() {
        return defaultMessageTemplate;
    }

    /**
     * The parameters the violations report beside the value: the constraint's attributes.
     *
     * @return the parameters; unmodifiable.
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    protected boolean isValid(RuleContext ruleContext, Object value) {
        RuliiConstraintValidatorContext context = new RuliiConstraintValidatorContext(ruleContext, defaultMessageTemplate);
        boolean valid;

        try {
            valid = validator.isValid(value, context);
        } catch (RuntimeException e) {
            failed.remove();
            throw new ConstraintValidatorException(validatorClass, "failed to validate [" + getValueName() + "]", e);
        }

        if (valid) {
            failed.remove();
        } else {
            failed.set(context);
        }

        return valid;
    }

    /**
     * Reports the default violation, unless the validator disabled it, then the violations the validator
     * built.
     */
    @Override
    @Otherwise
    public void otherwise(@Param(matchUsing = MatchByTypeMatchingStrategy.class) RuleContext ruleContext,
                          @Param(matchUsing = MatchByTypeMatchingStrategy.class) RuleViolations ruleViolations) {
        if (ruleContext == null) throw new UnrulyException("RuleContext not defined.");
        if (ruleViolations == null) throw new UnrulyException("RuleViolations not defined. Please define org.rulii.validation.RuleViolations binding and try again.");

        RuliiConstraintValidatorContext context = failed.get();
        failed.remove();
        Object value = getValue(ruleContext);

        if (context == null || !context.isDefaultConstraintViolationDisabled()) {
            ruleViolations.add(violation(value).build(ruleContext));
        }

        if (context == null) return;

        for (RuliiConstraintValidatorContext.Violation custom : context.getViolations()) {
            RuleViolationBuilder builder = violation(value)
                    .errorMessage(RuleMaterializer.message(annotationType, custom.messageTemplate(), Map.of(), getValueName()));
            ruleViolations.add(new CustomPathRuleViolation(builder.build(ruleContext), custom.pathExtension()));
        }
    }

    private RuleViolationBuilder violation(Object value) {
        RuleViolationBuilder builder = RuleViolation.builder().with(this);
        builder.param(getValueName(), value);

        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            builder.param(parameter.getKey(), parameter.getValue());
        }

        return builder;
    }

    /**
     * The types the validator validates.
     */
    @Override
    public List<Class<?>> getSupportedTypes() {
        return supportedTypes != null ? supportedTypes : SUPPORTED_TYPES;
    }

    /**
     * Whether the validator validates values of the given type: it is assignable to one of the
     * validator's types.
     */
    @Override
    public boolean isSupported(Class<?> type) {
        Assert.notNull(type, "type cannot be null.");

        for (Class<?> supportedType : getSupportedTypes()) {
            if (supportedType.isAssignableFrom(type)) return true;
        }

        return false;
    }

    @Override
    public String toString() {
        return "ConstraintValidatorRule{" + validatorClass.getName() + " for " + annotationType.getSimpleName() + '}';
    }
}
