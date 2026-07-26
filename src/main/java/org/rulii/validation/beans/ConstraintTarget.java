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
import org.rulii.validation.Severity;

import java.lang.reflect.Type;

/**
 * What a constraint occurrence is applied to, prepared by the materializer for
 * {@link ConstraintMapper#map}: where the validated value lives (scope binding name),
 * how to report it (rule name, value name, error code, severity, optional translated
 * message), and what was declared (bean class, property, declared type). Mappers thread
 * these into core rule builders rather than re-deriving policy.
 *
 * <p>The message is the constraint's custom literal message, already translated to
 * rulii/MessageFormat syntax; null means the constraint declared its default message and
 * resolution rides the error code through the MessageResolver at render time.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintTarget {

    private final Class<?> beanClass;
    private final String propertyName;
    private final String ruleName;
    private final String bindingName;
    private final String valueName;
    private final Type declaredType;
    private final String errorCode;
    private final Severity severity;
    private final String message;

    /**
     * Creates a new constraint target.
     *
     * @param beanClass the bean class being validated.
     * @param propertyName the property the constraint is declared on; null for
     *                     class-level constraints.
     * @param ruleName the base rule name; a mapper producing multiple rules suffixes it.
     * @param bindingName the scope binding the rule reads its value from.
     * @param valueName the parameter key the validated value is reported under.
     * @param declaredType the declared type of the validated value.
     * @param errorCode the violation error code (the jakarta template key).
     * @param severity the payload-mapped violation severity.
     * @param message the translated custom literal message; null for the default
     *                (resolver-driven) message.
     */
    public ConstraintTarget(Class<?> beanClass, String propertyName, String ruleName, String bindingName,
                            String valueName, Type declaredType, String errorCode, Severity severity, String message) {
        super();
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.hasText(ruleName, "ruleName cannot be null or empty.");
        Assert.hasText(bindingName, "bindingName cannot be null or empty.");
        Assert.hasText(valueName, "valueName cannot be null or empty.");
        Assert.notNull(declaredType, "declaredType cannot be null.");
        Assert.hasText(errorCode, "errorCode cannot be null or empty.");
        Assert.notNull(severity, "severity cannot be null.");
        this.beanClass = beanClass;
        this.propertyName = propertyName;
        this.ruleName = ruleName;
        this.bindingName = bindingName;
        this.valueName = valueName;
        this.declaredType = declaredType;
        this.errorCode = errorCode;
        this.severity = severity;
        this.message = message;
    }

    /**
     * Retrieves the bean class being validated.
     *
     * @return the bean class.
     */
    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * Retrieves the property the constraint is declared on.
     *
     * @return the property name; null for class-level constraints.
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Retrieves the base rule name. A mapper producing multiple rules suffixes the
     * secondary ones.
     *
     * @return the rule name.
     */
    public String getRuleName() {
        return ruleName;
    }

    /**
     * Retrieves the name of the scope binding the rule reads its value from.
     *
     * @return the binding name.
     */
    public String getBindingName() {
        return bindingName;
    }

    /**
     * Retrieves the parameter key the validated value is reported under.
     *
     * @return the value name.
     */
    public String getValueName() {
        return valueName;
    }

    /**
     * Retrieves the declared type of the validated value.
     *
     * @return the declared type.
     */
    public Type getDeclaredType() {
        return declaredType;
    }

    /**
     * Retrieves the violation error code (the jakarta template key, e.g.
     * {@code jakarta.validation.constraints.Size.message}).
     *
     * @return the error code.
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Retrieves the payload-mapped violation severity.
     *
     * @return the severity.
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Retrieves the constraint's custom literal message, already translated to
     * rulii/MessageFormat syntax.
     *
     * @return the translated message; null when the constraint declared its default
     * message (resolution rides the error code at render time).
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ConstraintTarget{beanClass=" + beanClass.getName()
                + ", propertyName='" + propertyName + '\''
                + ", ruleName='" + ruleName + '\''
                + ", bindingName='" + bindingName + '\''
                + ", errorCode='" + errorCode + '\''
                + '}';
    }
}
