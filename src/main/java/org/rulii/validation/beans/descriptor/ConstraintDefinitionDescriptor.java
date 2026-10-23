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
package org.rulii.validation.beans.descriptor;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.Payload;
import jakarta.validation.ValidationException;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ValidateUnwrappedValue;
import jakarta.validation.valueextraction.Unwrapping;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.validation.beans.model.ConstraintDefinition;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ConstraintDefinition} seen through the spec's {@link ConstraintDescriptor}: what a
 * {@code ConstraintViolation} hands out and what {@code Validator.getConstraintsForClass} describes.
 * Every value comes from the definition or the annotation it was read from; nothing is computed
 * twice. The definition is reachable through {@link #unwrap(Class)}.
 *
 * @param <A> the constraint annotation type.
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintDefinitionDescriptor<A extends Annotation> implements ConstraintDescriptor<A> {

    private final ConstraintDefinition definition;

    /**
     * Describes a definition.
     *
     * @param definition the constraint definition.
     */
    public ConstraintDefinitionDescriptor(ConstraintDefinition definition) {
        super();
        Assert.notNull(definition, "definition cannot be null.");
        this.definition = definition;
    }

    /**
     * Describes a definition, typed by its annotation.
     *
     * @param definition the constraint definition.
     * @return the descriptor.
     */
    public static ConstraintDefinitionDescriptor<?> of(ConstraintDefinition definition) {
        return new ConstraintDefinitionDescriptor<>(definition);
    }

    /**
     * The definition described.
     *
     * @return the definition.
     */
    public ConstraintDefinition getDefinition() {
        return definition;
    }

    @Override
    @SuppressWarnings("unchecked")
    public A getAnnotation() {
        return (A) definition.getAnnotation();
    }

    @Override
    public String getMessageTemplate() {
        return definition.getMessage();
    }

    /**
     * The groups the constraint belongs to; {@code Default} when it declares none, as the spec reads
     * an empty {@code groups} attribute.
     */
    @Override
    public Set<Class<?>> getGroups() {
        if (definition.getGroups().isEmpty()) return Set.of(Default.class);
        return Collections.unmodifiableSet(new LinkedHashSet<>(definition.getGroups()));
    }

    @Override
    public Set<Class<? extends Payload>> getPayload() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(definition.getPayload()));
    }

    /**
     * The {@code validationAppliesTo} attribute of the annotation, when it declares one.
     *
     * @return the declared target; null when the annotation has no such attribute.
     */
    @Override
    public ConstraintTarget getValidationAppliesTo() {
        Object value = attribute("validationAppliesTo");
        return value instanceof ConstraintTarget target ? target : null;
    }

    /**
     * The validators the annotation names in {@code @Constraint(validatedBy)}. They are reported, not
     * run: this library materializes rulii rules through constraint mappers.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Class<? extends ConstraintValidator<A, ?>>> getConstraintValidatorClasses() {
        Constraint constraint = definition.getAnnotationType().getAnnotation(Constraint.class);
        if (constraint == null) return List.of();
        return (List<Class<? extends ConstraintValidator<A, ?>>>) (List<?>) List.of(constraint.validatedBy());
    }

    /**
     * Every attribute of the annotation by name, in name order, {@code message}, {@code groups} and
     * {@code payload} included.
     */
    @Override
    public Map<String, Object> getAttributes() {
        Method[] methods = definition.getAnnotationType().getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        Map<String, Object> result = new LinkedHashMap<>();

        for (Method method : methods) {
            if (method.getParameterCount() == 0) {
                result.put(method.getName(), invoke(method));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    private Object attribute(String name) {
        try {
            return invoke(definition.getAnnotationType().getMethod(name));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Object invoke(Method method) {
        try {
            return method.invoke(definition.getAnnotation());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new UnrulyException("cannot read attribute [" + method.getName() + "] of ["
                    + definition.getAnnotationType().getName() + "]", e);
        }
    }

    @Override
    public Set<ConstraintDescriptor<?>> getComposingConstraints() {
        Set<ConstraintDescriptor<?>> result = new LinkedHashSet<>();

        for (ConstraintDefinition composing : definition.getComposingConstraints()) {
            result.add(of(composing));
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public boolean isReportAsSingleViolation() {
        return definition.isReportAsSingleViolation();
    }

    @Override
    public ValidateUnwrappedValue getValueUnwrapping() {
        if (definition.getPayload().contains(Unwrapping.Unwrap.class)) return ValidateUnwrappedValue.UNWRAP;
        if (definition.getPayload().contains(Unwrapping.Skip.class)) return ValidateUnwrappedValue.SKIP;
        return ValidateUnwrappedValue.DEFAULT;
    }

    /**
     * This descriptor, or the {@link ConstraintDefinition} behind it.
     */
    @Override
    public <U> U unwrap(Class<U> type) {
        Assert.notNull(type, "type cannot be null.");
        if (type.isInstance(this)) return type.cast(this);
        if (type.isInstance(definition)) return type.cast(definition);
        throw new ValidationException("cannot unwrap [" + getClass().getName() + "] to [" + type.getName() + "]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return definition.equals(((ConstraintDefinitionDescriptor<?>) o).definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public String toString() {
        return "ConstraintDescriptor{" + definition + '}';
    }
}
