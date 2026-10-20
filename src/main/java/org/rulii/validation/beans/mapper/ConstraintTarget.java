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
    private final List<String> ruleNames = new ArrayList<>(1);

    /**
     * Creates the target.
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
    }

    /**
     * Function reading the constrained value from its binding, to pass to a core rule builder.
     *
     * @return the value function.
     */
    public Function<?> value() {
        return Validators.binding(bindingName);
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
     * The constrained property.
     *
     * @return the property name; null for a class-level constraint.
     */
    public String getPropertyName() {
        return propertyName;
    }

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
     * type at a position within it, or the bean class for a class-level constraint.
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

    @Override
    public String toString() {
        return "ConstraintTarget{" + ruleName + " on " + bindingName + " (" + type.getSimpleName() + "), code=" + errorCode
                + ", severity=" + severity + '}';
    }
}
