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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidationException;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.beans.descriptor.ConstraintDefinitionDescriptor;
import org.rulii.validation.beans.model.ConstraintDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A rule violation stamped with where it occurred in the bean graph. It is a rulii {@link RuleViolation}
 * and a jakarta {@link ConstraintViolation} at once, so code written against either reads it unchanged:
 * {@link #getPropertyPath()}, {@link #getMessage()}, {@link #getInvalidValue()}, {@link #getLeafBean()},
 * {@link #getRootBean()}, {@link #getConstraintDescriptor()}; the constraint definition itself is
 * {@link #getConstraint()}. Executable validation is not supported, so the executable accessors return null.
 * <p>
 * The rendered path is also placed in the parameter map under {@link #PROPERTY_PATH_PARAMETER}, so code
 * that only sees a plain {@link RuleViolation} still finds it. Equality is the rule name, the error code
 * and the path, so the same rule failing on two elements of a list yields two distinct violations.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanRuleViolation extends RuleViolation implements ConstraintViolation<Object> {

    /**
     * Parameter holding the rendered property path.
     */
    public static final String PROPERTY_PATH_PARAMETER = "propertyPath";

    private final PropertyPath propertyPath;
    private final ConstraintDefinition constraint;
    private final Object invalidValue;
    private final Object leafBean;
    private final Object rootBean;
    private final Class<?> rootBeanClass;

    /**
     * Stamps a violation; the root bean class is the root bean's class.
     *
     * @param source the violation as the rule reported it.
     * @param propertyPath where it occurred.
     * @param constraint the constraint whose rule reported it; null for a rule that was not materialized
     *                   from a constraint (a {@code @Validate} registry rule).
     * @param invalidValue the value the rule rejected; null when unknown.
     * @param leafBean the object the rule ran against; null when unknown.
     * @param rootBean the object validation started from; null when unknown.
     */
    public BeanRuleViolation(RuleViolation source, PropertyPath propertyPath, ConstraintDefinition constraint,
                             Object invalidValue, Object leafBean, Object rootBean) {
        this(source, propertyPath, constraint, invalidValue, leafBean, rootBean, rootBean != null ? rootBean.getClass() : null);
    }

    /**
     * Stamps a violation.
     *
     * @param source the violation as the rule reported it.
     * @param propertyPath where it occurred.
     * @param constraint the constraint whose rule reported it; null for a rule that was not materialized
     *                   from a constraint (a {@code @Validate} registry rule).
     * @param invalidValue the value the rule rejected; null when unknown.
     * @param leafBean the object the rule ran against; null when unknown.
     * @param rootBean the object validation started from; null when unknown or when a value was validated
     *                 without an object.
     * @param rootBeanClass the class validation started from; null when unknown.
     */
    public BeanRuleViolation(RuleViolation source, PropertyPath propertyPath, ConstraintDefinition constraint,
                             Object invalidValue, Object leafBean, Object rootBean, Class<?> rootBeanClass) {
        super(source(source).getRuleName(), source.getErrorCode(), source.getSeverity(), source.getErrorMessage(),
                parameters(source, propertyPath));
        this.propertyPath = propertyPath;
        this.constraint = constraint;
        this.invalidValue = invalidValue;
        this.leafBean = leafBean;
        this.rootBean = rootBean;
        this.rootBeanClass = rootBeanClass;
    }

    private static RuleViolation source(RuleViolation source) {
        Assert.notNull(source, "source cannot be null.");
        return source;
    }

    private static Map<String, Object> parameters(RuleViolation source, PropertyPath propertyPath) {
        Assert.notNull(propertyPath, "propertyPath cannot be null.");
        Map<String, Object> result = new LinkedHashMap<>(source.getParameterValues());
        result.put(PROPERTY_PATH_PARAMETER, propertyPath.toString());
        return result;
    }

    /**
     * Where the violation occurred, from the root bean.
     *
     * @return the property path.
     */
    @Override
    public PropertyPath getPropertyPath() {
        return propertyPath;
    }

    /**
     * The resolved message; the same as {@link #getErrorMessage()}, under the name a
     * {@code ConstraintViolation} uses.
     *
     * @return the message.
     */
    @Override
    public String getMessage() {
        return getErrorMessage();
    }

    /**
     * The message template the constraint declared, before interpolation.
     *
     * @return the template; null for a rule that was not materialized from a constraint.
     */
    @Override
    public String getMessageTemplate() {
        return constraint != null ? constraint.getMessage() : null;
    }

    /**
     * The constraint whose rule reported the violation.
     *
     * @return the constraint; null for a rule that was not materialized from a constraint.
     */
    public ConstraintDefinition getConstraint() {
        return constraint;
    }

    /**
     * The constraint whose rule reported the violation, as the spec describes it.
     *
     * @return the descriptor; null for a rule that was not materialized from a constraint.
     */
    @Override
    public ConstraintDescriptor<?> getConstraintDescriptor() {
        return constraint != null ? ConstraintDefinitionDescriptor.of(constraint) : null;
    }

    /**
     * The value the rule rejected, as an object.
     *
     * @return the invalid value; null when the value is null or unknown.
     */
    @Override
    public Object getInvalidValue() {
        return invalidValue;
    }

    /**
     * The object the rule ran against: the bean hosting the property, or the bean itself for a
     * class-level constraint.
     *
     * @return the leaf bean; null when unknown.
     */
    @Override
    public Object getLeafBean() {
        return leafBean;
    }

    /**
     * The object validation started from.
     *
     * @return the root bean; null when unknown or when a value was validated without an object.
     */
    @Override
    public Object getRootBean() {
        return rootBean;
    }

    /**
     * The class validation started from.
     *
     * @return the root bean class; null when unknown.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<Object> getRootBeanClass() {
        return (Class<Object>) rootBeanClass;
    }

    /**
     * Executable validation is not supported.
     *
     * @return null.
     */
    @Override
    public Object[] getExecutableParameters() {
        return null;
    }

    /**
     * Executable validation is not supported.
     *
     * @return null.
     */
    @Override
    public Object getExecutableReturnValue() {
        return null;
    }

    /**
     * This violation, viewed as the given type.
     *
     * @throws ValidationException if it is not of that type.
     */
    @Override
    public <U> U unwrap(Class<U> type) {
        Assert.notNull(type, "type cannot be null.");
        if (type.isInstance(this)) return type.cast(this);
        throw new ValidationException("cannot unwrap [" + getClass().getName() + "] to [" + type.getName() + "]");
    }

    /**
     * Two violations are the same when the same rule reported them with the same code, message and
     * path: one rule may report several violations at one place with different messages (a constraint
     * validator building them), and each counts.
     */
    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        BeanRuleViolation that = (BeanRuleViolation) o;
        return propertyPath.equals(that.propertyPath) && Objects.equals(getErrorMessage(), that.getErrorMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), propertyPath, getErrorMessage());
    }

    @Override
    public String toString() {
        return "BeanRuleViolation{" +
                "propertyPath='" + propertyPath + '\'' +
                ", ruleName='" + getRuleName() + '\'' +
                ", errorCode='" + getErrorCode() + '\'' +
                ", severity=" + getSeverity() +
                ", errorMessage='" + getErrorMessage() + '\'' +
                ", invalidValue=" + invalidValue +
                ", constraint=" + (constraint != null ? constraint.getAnnotationType().getSimpleName() : null) +
                ", params=" + getParameters() +
                '}';
    }
}
