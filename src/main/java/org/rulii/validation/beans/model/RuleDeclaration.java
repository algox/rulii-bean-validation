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

import org.rulii.model.Definition;
import org.rulii.validation.Severity;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * An annotation declared on a bean element that materializes into rules: a Bean Validation
 * {@link ConstraintDefinition constraint} or an inline {@link ExprDefinition script condition}. What
 * the materialized rule needs to know about its origin: where it is declared, its annotation type,
 * the groups that activate it, the severity of its violations and its message template.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface RuleDeclaration extends Definition {

    /**
     * Where the annotation is declared.
     *
     * @return the source.
     */
    ConstraintSource getConstraintSource();

    /**
     * The annotation type.
     *
     * @return the type.
     */
    Class<? extends Annotation> getAnnotationType();

    /**
     * The groups the declaration belongs to; empty meaning {@code Default}.
     *
     * @return the groups.
     */
    List<Class<?>> getGroups();

    /**
     * The severity of the violations the rules report.
     *
     * @return the severity; never null.
     */
    Severity getSeverity();

    /**
     * The message template as declared; null when the declaration leaves the message to be resolved
     * from its error code.
     *
     * @return the template, or null.
     */
    String getMessage();
}
