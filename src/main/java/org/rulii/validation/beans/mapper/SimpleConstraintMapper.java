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
import org.rulii.rule.Rule;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A mapper defined by its values: the annotation type, the supported types, the materializing
 * function and, optionally, the attribute-to-parameter name mapping and the validation targets. The
 * standard mappers are instances of this class; a custom constraint backed by a plain rule can be
 * registered the same way:
 * <pre>{@code
 * new SimpleConstraintMapper<>(Iban.class, List.of(CharSequence.class),
 *         (iban, target) -> List.of(target.configure(IbanValidationRule.builder(target.value())).build()))
 * }</pre>
 * A mapper for a cross-parameter constraint declares {@code ValidationTarget.PARAMETERS} among its
 * targets and {@code Object[]} among its types; its rules read the parameters as a whole.
 *
 * @param <A> the constraint annotation type.
 * @author Max Arulananthan
 * @since 1.0
 */
public final class SimpleConstraintMapper<A extends Annotation> implements ConstraintMapper<A> {

    private final Class<A> annotationType;
    private final List<Class<?>> supportedTypes;
    private final BiFunction<A, ConstraintTarget, List<Rule>> materializer;
    private final Map<String, String> parameterNames;
    private final List<ValidationTarget> supportedTargets;

    /**
     * Creates the mapper.
     *
     * @param annotationType the constraint annotation type.
     * @param supportedTypes the supported value types; empty for no restriction.
     * @param materializer produces the rules for one occurrence.
     */
    public SimpleConstraintMapper(Class<A> annotationType, List<Class<?>> supportedTypes,
                                  BiFunction<A, ConstraintTarget, List<Rule>> materializer) {
        this(annotationType, supportedTypes, materializer, Map.of());
    }

    /**
     * Creates the mapper.
     *
     * @param annotationType the constraint annotation type.
     * @param supportedTypes the supported value types; empty for no restriction.
     * @param materializer produces the rules for one occurrence.
     * @param parameterNames attribute name to rule parameter name, where they differ.
     */
    public SimpleConstraintMapper(Class<A> annotationType, List<Class<?>> supportedTypes,
                                  BiFunction<A, ConstraintTarget, List<Rule>> materializer, Map<String, String> parameterNames) {
        this(annotationType, supportedTypes, materializer, parameterNames, List.of(ValidationTarget.ANNOTATED_ELEMENT));
    }

    /**
     * Creates the mapper.
     *
     * @param annotationType the constraint annotation type.
     * @param supportedTypes the supported value types; empty for no restriction.
     * @param materializer produces the rules for one occurrence.
     * @param parameterNames attribute name to rule parameter name, where they differ.
     * @param supportedTargets the validation targets: the annotated element, the parameters, or both.
     */
    public SimpleConstraintMapper(Class<A> annotationType, List<Class<?>> supportedTypes,
                                  BiFunction<A, ConstraintTarget, List<Rule>> materializer, Map<String, String> parameterNames,
                                  List<ValidationTarget> supportedTargets) {
        super();
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(supportedTypes, "supportedTypes cannot be null.");
        Assert.notNull(materializer, "materializer cannot be null.");
        Assert.notNull(parameterNames, "parameterNames cannot be null.");
        Assert.notEmpty(supportedTargets, "supportedTargets cannot be empty.");
        this.annotationType = annotationType;
        this.supportedTypes = List.copyOf(supportedTypes);
        this.materializer = materializer;
        this.parameterNames = Map.copyOf(parameterNames);
        this.supportedTargets = List.copyOf(supportedTargets);
    }

    @Override
    public Class<A> getAnnotationType() {
        return annotationType;
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public List<Rule> map(A annotation, ConstraintTarget target) {
        Assert.notNull(annotation, "annotation cannot be null.");
        Assert.notNull(target, "target cannot be null.");
        return materializer.apply(annotation, target);
    }

    @Override
    public Map<String, String> getParameterNames() {
        return parameterNames;
    }

    @Override
    public List<ValidationTarget> getSupportedTargets() {
        return supportedTargets;
    }

    @Override
    public String toString() {
        return "SimpleConstraintMapper{" + annotationType.getSimpleName() + " -> " + supportedTypes + "}";
    }
}
