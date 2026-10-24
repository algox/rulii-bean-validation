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
import jakarta.validation.Valid;
import jakarta.validation.valueextraction.Unwrapping;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.util.reflect.TypeResolver;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one {@link ValueDeclaration} contributes to the value it declares: the constraints on the value
 * itself, the constrained positions within its type, whether it is cascaded, and its {@code @Validate}
 * rules. Shared by properties, parameters and return values, which differ only in how declarations
 * are grouped.
 *
 * <p>Constraints are read from the declaration annotations and from the annotations on the type use;
 * Java mirrors a constraint that targets both onto both places, so the two reads are merged without
 * duplicates. A constraint declared on the value but applying to its <em>unwrapped</em> value (an
 * {@code OptionalInt}, whose extractor is {@code @UnwrapByDefault}; or an explicit
 * {@code payload = Unwrapping.Unwrap.class}) is moved to the container element node of the extractor's
 * position, so the runtime has a single model: constraints at positions reached through extractors.</p>
 *
 * @param constraints the constraints on the value itself.
 * @param elements the constrained positions within the value's type, the unwrapped node included.
 * @param cascaded whether the value is cascaded ({@code @Valid}).
 * @param validateRules the {@code @Validate} rules attached to the declaration.
 * @author Max Arulananthan
 * @since 1.0
 */
record DeclarationReader(List<ConstraintDefinition> constraints, List<ContainerElementDefinition> elements, boolean cascaded,
                         List<ValidateDefinition> validateRules) {

    /**
     * Reads a declaration.
     *
     * @param declaration the declaration.
     * @param registry the value extractors available at validation time.
     * @return what the declaration contributes.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared on the declaration
     *         or on its type use.
     * @throws ConstraintDeclarationException if value unwrapping is requested but cannot be resolved to
     *         exactly one extractor, or both {@code Unwrap} and {@code Skip} are requested.
     */
    static DeclarationReader read(ValueDeclaration declaration, ValueExtractorRegistry registry) {
        Assert.notNull(declaration, "declaration cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        AnnotatedType annotatedType = declaration.getAnnotatedType();

        if (ContainerElementDefinition.declaresGroupConversion(declaration)
                || ContainerElementDefinition.declaresGroupConversion(annotatedType)) {
            throw new GroupConversionNotSupportedException(declaration);
        }

        boolean cascaded = declaration.isAnnotationPresent(Valid.class) || annotatedType.isAnnotationPresent(Valid.class);
        List<ConstraintDefinition> declared = merge(ConstraintDefinition.load(declaration),
                ConstraintDefinition.load(declaration, annotatedType));
        List<ContainerElementDefinition> elements = new ArrayList<>(ContainerElementDefinition.load(declaration, registry));

        List<ConstraintDefinition> onValue = new ArrayList<>(declared.size());
        List<ConstraintDefinition> unwrapped = new ArrayList<>();
        ValueExtractorDescriptor unwrapper = null;

        for (ConstraintDefinition constraint : declared) {
            ValueExtractorDescriptor descriptor = unwrapper(declaration, constraint, registry);

            if (descriptor == null) {
                onValue.add(constraint);
            } else {
                unwrapper = descriptor;
                unwrapped.add(constraint);
            }
        }

        if (!unwrapped.isEmpty()) {
            addUnwrapped(declaration, registry, unwrapper, annotatedType, unwrapped, elements);
        }

        return new DeclarationReader(Collections.unmodifiableList(onValue), Collections.unmodifiableList(elements), cascaded,
                ValidateDefinition.load(declaration));
    }

    /**
     * Whether the declaration contributes anything to validate.
     */
    boolean isConstrained() {
        return !constraints.isEmpty() || !elements.isEmpty() || cascaded || !validateRules.isEmpty();
    }

    private static List<ConstraintDefinition> merge(List<ConstraintDefinition> declaration, List<ConstraintDefinition> typeUse) {
        List<ConstraintDefinition> result = new ArrayList<>(declaration);

        for (ConstraintDefinition constraint : typeUse) {
            if (!result.contains(constraint)) result.add(constraint);
        }

        return result;
    }

    /**
     * The extractor through which a constraint on the declaration applies to the unwrapped value, or
     * null when the constraint applies to the value itself.
     */
    private static ValueExtractorDescriptor unwrapper(ValueDeclaration declaration, ConstraintDefinition constraint,
                                                      ValueExtractorRegistry registry) {
        boolean unwrap = constraint.getPayload().contains(Unwrapping.Unwrap.class);
        boolean skip = constraint.getPayload().contains(Unwrapping.Skip.class);
        Class<?> type = declaration.getType();

        if (unwrap && skip) {
            throw new ConstraintDeclarationException("constraint [" + constraint.getAnnotationType().getName()
                    + "] on [" + declaration.describe() + "] requests both Unwrapping.Unwrap and Unwrapping.Skip.");
        }

        if (skip) return null;

        List<ValueExtractorDescriptor> candidates = registry.find(type);

        if (unwrap) {
            if (candidates.isEmpty()) {
                throw new ConstraintDeclarationException("constraint [" + constraint.getAnnotationType().getName()
                        + "] on [" + declaration.describe() + "] requests Unwrapping.Unwrap but no value extractor is registered for ["
                        + type.getName() + "].");
            }
            if (candidates.size() > 1) {
                throw new ConstraintDeclarationException("constraint [" + constraint.getAnnotationType().getName()
                        + "] on [" + declaration.describe() + "] requests Unwrapping.Unwrap but [" + type.getName()
                        + "] has several extractable positions: " + candidates + ". Declare the constraint on the type argument instead.");
            }
            return candidates.get(0);
        }

        ValueExtractorDescriptor result = null;

        for (ValueExtractorDescriptor candidate : candidates) {
            if (!candidate.isUnwrapByDefault()) continue;
            if (result != null) {
                throw new ConstraintDeclarationException("[" + declaration.describe() + "] of type [" + type.getName()
                        + "] has several @UnwrapByDefault value extractors: " + result + " and " + candidate + ".");
            }
            result = candidate;
        }

        return result;
    }

    /**
     * Places the unwrapped constraints on the node of the extractor's position: the existing node for
     * that position when the type arguments declare one, otherwise a node created for the value itself.
     */
    private static void addUnwrapped(ValueDeclaration declaration, ValueExtractorRegistry registry,
                                     ValueExtractorDescriptor unwrapper, AnnotatedType annotatedType,
                                     List<ConstraintDefinition> unwrapped, List<ContainerElementDefinition> elements) {
        for (int i = 0; i < elements.size(); i++) {
            ContainerElementDefinition element = elements.get(i);
            if (unwrapper.equals(registry.find(element.getContainerType(), element.getTypeArgumentIndex()))) {
                elements.set(i, element.withConstraints(unwrapped));
                return;
            }
        }

        AnnotatedType position = null;
        Type elementType = null;

        if (unwrapper.getTypeArgumentIndex() != ValueExtractorDescriptor.CONTAINER) {
            // The extractor position is declared on its own container type; view the declared type as that container.
            Type container = TypeResolver.supertype(declaration.getGenericType(), unwrapper.getContainerType());
            if (container instanceof ParameterizedType parameterized) {
                elementType = parameterized.getActualTypeArguments()[unwrapper.getTypeArgumentIndex()];
            }
            if (annotatedType instanceof AnnotatedParameterizedType parameterized) {
                AnnotatedType[] arguments = parameterized.getAnnotatedActualTypeArguments();
                int index = TypeResolver.parameterIndex(declaration.getType(), unwrapper.getContainerType(), unwrapper.getTypeArgumentIndex());
                if (index >= 0 && index < arguments.length) position = arguments[index];
            }
        }

        elements.add(ContainerElementDefinition.unwrapped(declaration, unwrapper, position, elementType, Collections.unmodifiableList(unwrapped)));
    }
}
