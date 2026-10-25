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
import org.rulii.bind.Binding;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.SourceDefinition;
import org.rulii.model.UnrulyException;
import org.rulii.model.action.Action;
import org.rulii.model.condition.Condition;
import org.rulii.model.function.Function;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleDefinition;
import org.rulii.rule.RuleExecutionStatus;
import org.rulii.rule.RuleResult;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolationBuilder;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.CustomPathRuleViolation;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.message.MessageExpressions;

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
 * <p>The rule is atomic: it implements {@link Rule} directly rather than being built from a condition and
 * actions. A Jakarta validator does not follow rulii's split; it returns its verdict and the details of a
 * failure together, through the context it was handed, and those details exist only while that call's
 * context is in hand. So {@link #run} calls the validator once and maps its outcome in the same breath:
 * there is no condition, no action and nothing to hand from one to the other. {@link #getCondition()},
 * {@link #getPreCondition()} and {@link #getOtherwiseAction()} are therefore null and {@link #getActions()}
 * empty; {@link #isTrue} asks the validator with a throwaway context. The trace sees the rule start, end
 * and fail like any other.</p>
 *
 * <p>The rule runs on null: the specification hands null to the validator and lets it decide. The mapper
 * selected the validator for the declared type of the target, so no runtime type check is needed. The
 * constraint's attributes are reported as parameters beside the value, so templates can reference them.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintValidatorRule implements Rule {

    public static final String ERROR_CODE       = "constraintValidatorRule.errorCode";
    public static final String DEFAULT_MESSAGE  = "Value is not valid.";

    private final String name;
    private final String description;
    private final Function<?> valueFunction;
    private final String valueName;
    private final String errorCode;
    private final Severity severity;
    private final String errorMessage;
    private final ConstraintValidator<Annotation, Object> validator;
    private final Class<? extends ConstraintValidator<?, ?>> validatorClass;
    private final Class<? extends Annotation> annotationType;
    private final String defaultMessageTemplate;
    private final Map<String, Object> parameters;
    private final RuleDefinition definition;

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
    ConstraintValidatorRule(String name, String description, SourceDefinition source, Function<?> valueFunction, String valueName,
                            String errorCode, Severity severity, String errorMessage, ConstraintValidator<?, ?> validator,
                            Class<? extends Annotation> annotationType, String defaultMessageTemplate, Map<String, Object> parameters) {
        super();
        Assert.hasText(name, "name cannot be null/empty.");
        Assert.notNull(valueFunction, "valueFunction cannot be null.");
        Assert.hasText(valueName, "valueName cannot be null/empty.");
        Assert.hasText(errorCode, "errorCode cannot be null/empty.");
        Assert.notNull(severity, "severity cannot be null.");
        Assert.notNull(validator, "validator cannot be null.");
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(defaultMessageTemplate, "defaultMessageTemplate cannot be null.");
        Assert.notNull(parameters, "parameters cannot be null.");
        this.name = name;
        this.description = description != null ? description : "Runs " + validator.getClass().getSimpleName() + " for @" + annotationType.getSimpleName() + ".";
        this.valueFunction = valueFunction;
        this.valueName = valueName;
        this.errorCode = errorCode;
        this.severity = severity;
        this.errorMessage = errorMessage;
        this.validator = (ConstraintValidator<Annotation, Object>) validator;
        this.validatorClass = (Class<? extends ConstraintValidator<?, ?>>) validator.getClass();
        this.annotationType = annotationType;
        this.defaultMessageTemplate = defaultMessageTemplate;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        // No method definitions: the rule has no condition or action methods to describe.
        this.definition = new RuleDefinition(ConstraintValidatorRule.class, false, name, this.description,
                source != null ? source : SourceDefinition.build(), null, null, Collections.emptyList(), null);
    }

    /**
     * Runs the validator once and reports its outcome: the default violation unless the validator disabled
     * it, then the violations the validator built. The result is {@code PASS} or {@code FAIL}; a validator
     * that throws fails the rule with a {@link ConstraintValidatorException}.
     */
    @Override
    public RuleResult run(RuleContext ruleContext) throws UnrulyException {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        ruleContext.getTracer().fireOnRuleStart(this);
        RuleResult result = null;

        try {
            Object value = getValue(ruleContext);
            RuliiConstraintValidatorContext context = new RuliiConstraintValidatorContext(ruleContext, defaultMessageTemplate);
            boolean valid = isValid(value, context);

            if (!valid) {
                report(ruleContext, value, context);
            }

            result = new RuleResult(this, valid ? RuleExecutionStatus.PASS : RuleExecutionStatus.FAIL);
            return result;
        } catch (Exception e) {
            ruleContext.getTracer().fireOnRuleError(this, e);
            result = new RuleResult(this, RuleExecutionStatus.ERROR);
            if (e instanceof UnrulyException unruly) throw unruly;
            throw new UnrulyException("Error trying to run Rule [" + name + "]", e);
        } finally {
            ruleContext.getTracer().fireOnRuleEnd(this, result);
        }
    }

    /**
     * Asks the validator with a throwaway context; nothing is reported.
     */
    @Override
    public boolean isTrue(RuleContext ruleContext) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        return isValid(getValue(ruleContext), new RuliiConstraintValidatorContext(ruleContext, defaultMessageTemplate));
    }

    private boolean isValid(Object value, RuliiConstraintValidatorContext context) {
        try {
            return validator.isValid(value, context);
        } catch (RuntimeException e) {
            throw new ConstraintValidatorException(validatorClass, "failed to validate [" + valueName + "]", e);
        }
    }

    private void report(RuleContext ruleContext, Object value, RuliiConstraintValidatorContext context) {
        RuleViolations ruleViolations = ruleViolations(ruleContext);

        if (!context.isDefaultConstraintViolationDisabled()) {
            ruleViolations.add(violation(value).build(ruleContext));
        }

        for (RuliiConstraintValidatorContext.Violation custom : context.getViolations()) {
            // A template built at run time keeps its expressions as written unless the validator enabled them.
            String template = custom.expressionLanguageEnabled() ? custom.messageTemplate() : MessageExpressions.escape(custom.messageTemplate());
            RuleViolationBuilder builder = violation(value)
                    .errorMessage(RuleMaterializer.message(annotationType, template, Map.of(), valueName));
            ruleViolations.add(new CustomPathRuleViolation(builder.build(ruleContext), custom.pathExtension(), custom.messageTemplate(),
                    custom.expressionLanguageEnabled()));
        }
    }

    /**
     * The collector, resolved as core resolves a rule's {@code ruleViolations} parameter: the only
     * {@link RuleViolations} binding, else the primary one.
     */
    private static RuleViolations ruleViolations(RuleContext ruleContext) {
        List<Binding<RuleViolations>> bindings = ruleContext.getBindings().getAllBindings(RuleViolations.class);

        if (bindings.size() == 1) return bindings.get(0).getValue();

        for (Binding<RuleViolations> binding : bindings) {
            if (binding.isPrimary()) return binding.getValue();
        }

        throw new UnrulyException("RuleViolations not defined. Please define org.rulii.validation.RuleViolations binding and try again.");
    }

    private RuleViolationBuilder violation(Object value) {
        RuleViolationBuilder builder = RuleViolation.builder().with(name, errorCode)
                .severity(severity)
                .errorMessage(errorMessage)
                .defaultMessage(DEFAULT_MESSAGE);
        builder.param(valueName, value);

        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            builder.param(parameter.getKey(), parameter.getValue());
        }

        return builder;
    }

    private Object getValue(RuleContext ruleContext) {
        return valueFunction.run(ruleContext);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public RuleDefinition getDefinition() {
        return definition;
    }

    /**
     * Always null: the rule is atomic (see the class description).
     */
    @Override
    public Condition getPreCondition() {
        return null;
    }

    /**
     * Always null: the rule is atomic (see the class description).
     */
    @Override
    public Condition getCondition() {
        return null;
    }

    /**
     * Always empty: the rule is atomic (see the class description).
     */
    @Override
    public List<Action> getActions() {
        return Collections.emptyList();
    }

    /**
     * Always null: the rule is atomic (see the class description).
     */
    @Override
    public Action getOtherwiseAction() {
        return null;
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

    public Function<?> getValueFunction() {
        return valueFunction;
    }

    public String getValueName() {
        return valueName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Severity getSeverity() {
        return severity;
    }

    /**
     * The declared message, if any; null leaves the message to the resolver.
     *
     * @return the message or null.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ConstraintValidatorRule{" + name + ": " + validatorClass.getName() + " for @" + annotationType.getSimpleName() + '}';
    }
}
