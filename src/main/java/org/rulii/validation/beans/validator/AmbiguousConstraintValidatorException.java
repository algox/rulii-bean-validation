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
import jakarta.validation.UnexpectedTypeException;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Thrown when more than one of a constraint's {@code @Constraint(validatedBy)} classes validates the
 * type the constraint is declared on and none is more specific than the others, so no single validator
 * can be chosen. Raised when the bean class is read. Same exception family the reference implementation
 * uses for the case.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class AmbiguousConstraintValidatorException extends UnexpectedTypeException {

    private static final long serialVersionUID = 0L;

    private final Class<? extends Annotation> annotationType;
    private final Class<?> type;
    private final List<Class<? extends ConstraintValidator<?, ?>>> validatorClasses;

    /**
     * Creates the exception.
     *
     * @param annotationType the constraint annotation type.
     * @param type the declared type of the constrained value.
     * @param validatorClasses the validator classes that all validate the type.
     */
    public AmbiguousConstraintValidatorException(Class<? extends Annotation> annotationType, Class<?> type,
                                                 List<Class<? extends ConstraintValidator<?, ?>>> validatorClasses) {
        super("constraint [" + annotationType.getName() + "] declares several validators for type [" + type.getName()
                + "] and none is more specific than the others: " + validatorClasses
                + ". Narrow the validators' types, or register a ConstraintMapper for the constraint.");
        this.annotationType = annotationType;
        this.type = type;
        this.validatorClasses = List.copyOf(validatorClasses);
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    /**
     * The declared type of the constrained value.
     *
     * @return the type.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * The validator classes that all validate the type.
     *
     * @return the classes; unmodifiable.
     */
    public List<Class<? extends ConstraintValidator<?, ?>>> getValidatorClasses() {
        return validatorClasses;
    }
}
