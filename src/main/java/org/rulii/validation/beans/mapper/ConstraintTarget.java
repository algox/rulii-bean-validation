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
package org.rulii.validation.beans.mapper;

import jakarta.validation.constraintvalidation.ValidationTarget;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.SourceDefinition;
import org.rulii.model.function.Function;
import org.rulii.util.RuleUtils;
import org.rulii.validation.Severity;
import org.rulii.validation.ValueValidationRuleBuilder;
import org.rulii.validation.rules.Validators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the materializer prepared for one constraint occurrence, handed to its
 * {@link ConstraintMapper mapper}: where the value comes from, how the rules are to report, and the
 * common configuration every produced rule carries. Mappers read constraint attributes off the
 * annotation and take policy from here, so that null handling, severity, error codes and messages are
 * decided in one place.
 *
 * <p>{@link #configure(ValueValidationRuleBuilder)} applies that configuration to a core rule builder
 * and returns it, so a mapper can still adjust it before building. It sets {@code failOnNull(false)},
 * the specification's null semantics; a presence constraint ({@code @NotNull} and kin), whose rule
 * must see null, uses {@link #configure(ValueValidationRuleBuilder, boolean)} with {@code true}
 * instead, since core refuses {@code failOnNull(false)} on those rules. Each call assigns the next
 * rule name: the base name first, then {@code <base>_2}, {@code <base>_3}, so a mapper producing
 * several rules names them without further bookkeeping.</p>
 *
 * <p>The {@link #getValidationTarget() validation target} says what the value is: the annotated
 * element (a property, a parameter, a return value, a bean) or, for a cross-parameter constraint, the
 * parameters of a method or constructor as a whole, an {@code Object[]}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintTarget {

    private final Class<?> beanClass;
    private final String propertyName;
    private final String bindingName;
    private final String valueName;
    private final Class<?> type;
    private final String ruleName;
    private final String errorCode;
    private final Severity severity;
    private final String message;
    private final SourceDefinition source;
    private final Function<?> valueFunction;
    private final ValidationTarget validationTarget;
    private final List<String> ruleNames = new ArrayList<>(1);

    /**
     * Creates a target whose rules read the value from its binding.
     *
     * @param beanClass the bean class being materialized.
     * @param propertyName the constrained property; null for a class-level constraint.
     * @param bindingName the binding the rules read the value from.
     * @param valueName the name the rules report the value under (the property name, whatever the binding).
     * @param type the static type of the value.
     * @param ruleName the base rule name.
     * @param errorCode the error code of the violations.
     * @param severity the severity of the violations.
     * @param message the translated message template; null to use the rule's resolved default.
     * @param source where the constraint is declared.
     */
    public ConstraintTarget(Class<?> beanClass, String propertyName, String bindingName, String valueName, Class<?> type,
                            String ruleName, String errorCode, Severity severity, String message, SourceDefinition source) {
        this(beanClass, propertyName, bindingName, valueName, type, ruleName, errorCode, severity, message, source, null);
    }

    /**
     * Creates a target whose rules read the value through the given function instead of the binding:
     * a constraint written on a field beside its getter reads the field off the object in scope.
     *
     * @param beanClass the bean class being materialized.
     * @param propertyName the constrained property; null for a class-level constraint.
     * @param bindingName the binding of the constrained property.
     * @param valueName the name the rules report the value under (the property name, whatever the binding).
     * @param type the static type of the value.
     * @param ruleName the base rule name.
     * @param errorCode the error code of the violations.
     * @param severity the severity of the violations.
     * @param message the translated message template; null to use the rule's resolved default.
     * @param source where the constraint is declared.
     * @param valueFunction the function reading the value; null to read the binding.
     */
    public ConstraintTarget(Class<?> beanClass, String propertyName, String bindingName, String valueName, Class<?> type,
                            String ruleName, String errorCode, Severity severity, String message, SourceDefinition source,
                            Function<?> valueFunction) {
        this(beanClass, propertyName, bindingName, valueName, type, ruleName, errorCode, severity, message, source, valueFunction,
                ValidationTarget.ANNOTATED_ELEMENT);
    }

    /**
     * Creates a target for a validation target other than the annotated element: the parameters of an
     * executable as a whole for a cross-parameter constraint.
     *
     * @param beanClass the bean class being materialized.
     * @param propertyName the constrained property or parameter; null for a class-level or cross-parameter constraint.
     * @param bindingName the binding the rules read the value from.
     * @param valueName the name the rules report the value under.
     * @param type the static type of the value; {@code Object[]} for the parameters.
     * @param ruleName the base rule name.
     * @param errorCode the error code of the violations.
     * @param severity the severity of the violations.
     * @param message the translated message template; null to use the rule's resolved default.
     * @param source where the constraint is declared.
     * @param valueFunction the function reading the value; null to read the binding.
     * @param validationTarget what the value is.
     */
    public ConstraintTarget(Class<?> beanClass, String propertyName, String bindingName, String valueName, Class<?> type,
                            String ruleName, String errorCode, Severity severity, String message, SourceDefinition source,
                            Function<?> valueFunction, ValidationTarget validationTarget) {
        super();
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.hasText(bindingName, "bindingName cannot be empty/null.");
        Assert.hasText(valueName, "valueName cannot be empty/null.");
        Assert.notNull(type, "type cannot be null.");
        Assert.isTrue(ruleName != null && ruleName.matches(RuleUtils.NAME_REGEX), "ruleName [" + ruleName
                + "] must match " + RuleUtils.NAME_REGEX);
        Assert.hasText(errorCode, "errorCode cannot be empty/null.");
        Assert.notNull(severity, "severity cannot be null.");
        Assert.notNull(source, "source cannot be null.");
        Assert.notNull(validationTarget, "validationTarget cannot be null.");
        this.beanClass = beanClass;
        this.propertyName = propertyName;
        this.bindingName = bindingName;
        this.valueName = valueName;
        this.type = type;
        this.ruleName = ruleName;
        this.errorCode = errorCode;
        this.severity = severity;
        this.message = message;
        this.source = source;
        this.valueFunction = valueFunction != null ? valueFunction : Validators.binding(bindingName);
        this.validationTarget = validationTarget;
    }

    /**
     * Function reading the constrained value, to pass to a core rule builder: the property binding,
     * or the declaration the constraint is written on when that is not the one the binding holds.
     *
     * @return the value function.
     */
    public Function<?> value() {
        return valueFunction;
    }

    /**
     * Applies the common configuration to a core rule builder and assigns the next rule name; the
     * rule skips null values.
     *
     * @param builder the builder.
     * @param <T> the builder type.
     * @return the same builder.
     */
    public <T extends ValueValidationRuleBuilder<T, ?>> T configure(T builder) {
        return configure(builder, false);
    }

    /**
     * Applies the common configuration to a core rule builder and assigns the next rule name.
     *
     * @param builder the builder.
     * @param failOnNull whether the rule is to evaluate null values (a presence rule) rather than
     *                   skip them.
     * @param <T> the builder type.
     * @return the same builder.
     */
    public <T extends ValueValidationRuleBuilder<T, ?>> T configure(T builder, boolean failOnNull) {
        Assert.notNull(builder, "builder cannot be null.");
        builder.name(nextRuleName())
                .errorCode(errorCode)
                .severity(severity)
                .valueName(valueName)
                .failOnNull(failOnNull)
                .source(source);
        if (message != null) builder.message(message);
        return builder;
    }

    /**
     * The next rule name: the base name, then {@code <base>_2}, {@code <base>_3}, and so on.
     *
     * @return the name.
     */
    public String nextRuleName() {
        String result = ruleNames.isEmpty() ? ruleName : ruleName + "_" + (ruleNames.size() + 1);
        ruleNames.add(result);
        return result;
    }

    /**
     * The rule names assigned so far, in order.
     *
     * @return the names.
     */
    public List<String> getRuleNames() {
        return Collections.unmodifiableList(ruleNames);
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * The constrained property or parameter.
     *
     * @return the name; null for a class-level or cross-parameter constraint.
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Whether the constraint is declared on the bean class itself, or validates the parameters of an
     * executable as a whole: neither reports at a property.
     *
     * @return true for a class-level or cross-parameter constraint.
     */
    public boolean isClassLevel() {
        return propertyName == null;
    }

    public String getBindingName() {
        return bindingName;
    }

    public String getValueName() {
        return valueName;
    }

    /**
     * The static type of the constrained value: the property's resolved type, the container element
     * type at a position within it, the bean class for a class-level constraint, {@code Object[]} for a
     * cross-parameter constraint.
     *
     * @return the type.
     */
    public Class<?> getType() {
        return type;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Severity getSeverity() {
        return severity;
    }

    /**
     * The translated message template.
     *
     * @return the template; null when the message is resolved from the error code at render time.
     */
    public String getMessage() {
        return message;
    }

    public SourceDefinition getSource() {
        return source;
    }

    /**
     * What the value is: the annotated element, or the parameters of an executable as a whole.
     *
     * @return the validation target.
     */
    public ValidationTarget getValidationTarget() {
        return validationTarget;
    }

    public boolean isCrossParameter() {
        return validationTarget == ValidationTarget.PARAMETERS;
    }

    @Override
    public String toString() {
        return "ConstraintTarget{" + ruleName + " on " + bindingName + " (" + type.getSimpleName() + "), code=" + errorCode
                + ", severity=" + severity + (isCrossParameter() ? ", cross-parameter" : "") + '}';
    }
}
