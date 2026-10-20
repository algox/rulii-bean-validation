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
package org.rulii.validation.beans.model;

import jakarta.validation.ConstraintDeclarationException;
import org.rulii.validation.beans.util.reflect.Members;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Thrown at metadata-read time when a constraint, {@code @Valid}, {@code @ConvertGroup} or
 * {@code @Validate} is declared on, or within the return type of, a method that is not a JavaBeans
 * getter. Method validation is out of scope, so the annotation could never be honoured; failing
 * fast is preferred to silently ignoring it.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintOnNonGetterException extends ConstraintDeclarationException {

    private final Method method;
    private final Annotation annotation;

    public ConstraintOnNonGetterException(Method method, Annotation annotation) {
        super("[" + annotation + "] on method [" + Members.describe(method) + "] is not supported: constraints, @Valid,"
                + " @ConvertGroup and @Validate are read from fields, record components and JavaBeans getters only (getX() with a"
                + " return value and no parameters, or isX() returning boolean); method validation is out of scope."
                + " Move the annotation to a property or remove it.");
        this.method = method;
        this.annotation = annotation;
    }

    /**
     * The method the annotation was found on.
     *
     * @return the method.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * The offending annotation: a constraint, a repeated-constraint container, {@code @Valid},
     * {@code @ConvertGroup}, {@code @ConvertGroup.List} or {@code @Validate}.
     *
     * @return the annotation.
     */
    public Annotation getAnnotation() {
        return annotation;
    }
}
