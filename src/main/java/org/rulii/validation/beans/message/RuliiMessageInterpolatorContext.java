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
package org.rulii.validation.beans.message;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.PropertyPath;

import java.util.Collections;
import java.util.Map;

/**
 * The {@link MessageInterpolator.Context} the validator hands a configured interpolator for each
 * constraint violation: the specification's descriptor and validated value, plus what the reference
 * implementation's context extension also exposes, the root bean class, the property path, the
 * message parameters (the constraint's attributes and the validated value, as the rule reported them)
 * and whether the template's expressions may be evaluated. {@link #unwrap} answers this type and its
 * interfaces.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiMessageInterpolatorContext implements MessageInterpolator.Context {

    private final ConstraintDescriptor<?> constraintDescriptor;
    private final Object validatedValue;
    private final Class<?> rootBeanClass;
    private final PropertyPath propertyPath;
    private final Map<String, Object> parameters;
    private final boolean expressionLanguageEnabled;

    /**
     * Creates the context.
     *
     * @param constraintDescriptor the descriptor of the violated constraint.
     * @param validatedValue the value the constraint rejected; may be null.
     * @param rootBeanClass the class validation started from; may be null when unknown.
     * @param propertyPath where the violation occurred.
     * @param parameters the message parameters, by name.
     * @param expressionLanguageEnabled whether {@code ${...}} expressions of the template may be evaluated;
     *                                  false for a template built at run time without opting in.
     */
    public RuliiMessageInterpolatorContext(ConstraintDescriptor<?> constraintDescriptor, Object validatedValue, Class<?> rootBeanClass,
                                           PropertyPath propertyPath, Map<String, Object> parameters, boolean expressionLanguageEnabled) {
        super();
        Assert.notNull(constraintDescriptor, "constraintDescriptor cannot be null.");
        Assert.notNull(propertyPath, "propertyPath cannot be null.");
        Assert.notNull(parameters, "parameters cannot be null.");
        this.constraintDescriptor = constraintDescriptor;
        this.validatedValue = validatedValue;
        this.rootBeanClass = rootBeanClass;
        this.propertyPath = propertyPath;
        this.parameters = Collections.unmodifiableMap(parameters);
        this.expressionLanguageEnabled = expressionLanguageEnabled;
    }

    @Override
    public ConstraintDescriptor<?> getConstraintDescriptor() {
        return constraintDescriptor;
    }

    @Override
    public Object getValidatedValue() {
        return validatedValue;
    }

    /**
     * The class validation started from.
     *
     * @return the root bean class; null when unknown.
     */
    public Class<?> getRootBeanClass() {
        return rootBeanClass;
    }

    /**
     * Where the violation occurred, from the root bean.
     *
     * @return the property path.
     */
    public PropertyPath getPropertyPath() {
        return propertyPath;
    }

    /**
     * The message parameters as the rule reported them: the constraint's attributes under the rule's
     * parameter names, and the validated value under the rule's value name.
     *
     * @return the parameters; unmodifiable.
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Whether the template's {@code ${...}} expressions may be evaluated: always for a declared
     * template, and for one built at run time only when the constraint validator enabled them.
     *
     * @return true if expressions may be evaluated.
     */
    public boolean isExpressionLanguageEnabled() {
        return expressionLanguageEnabled;
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        Assert.notNull(type, "type cannot be null.");
        if (type.isInstance(this)) return type.cast(this);
        throw new ValidationException("Type [" + type.getName() + "] is not supported for unwrapping; the context is a [" + getClass().getName() + "].");
    }

    @Override
    public String toString() {
        return "RuliiMessageInterpolatorContext{constraint=" + constraintDescriptor.getAnnotation().annotationType().getSimpleName()
                + ", propertyPath='" + propertyPath + "', validatedValue=" + validatedValue + '}';
    }
}
