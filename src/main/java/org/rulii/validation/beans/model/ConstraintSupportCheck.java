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

import jakarta.validation.constraintvalidation.ValidationTarget;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.ConstraintNotApplicableException;
import org.rulii.validation.beans.mapper.NoConstraintMapperException;
import org.rulii.validation.beans.util.reflect.Types;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Read-time check that every constraint of a bean definition can be materialized: a mapper is
 * registered for it (or synthesized from the validators it declares, or it is a composition of
 * mapped constraints) and the mapper accepts the static type the constraint is declared on. Runs
 * once per bean class after its definition is read, so misplaced or unsupported constraints fail
 * when the class is loaded rather than when a value is validated.
 *
 * <p>Rules: a constraint with a mapper must be {@link ConstraintMapper#isApplicable(Class, ValidationTarget)
 * applicable} to its type and its validation target: the annotated element for every constraint but
 * a cross-parameter one, whose type is {@code Object[]} and whose target is the parameters. A
 * constraint with no mapper is accepted only when it is a composition
 * ({@link ConstraintDefinition#isComposed()}). Composing constraints are checked recursively against
 * the same type.</p>
 *
 * <p>The type checked is the one the value will have: the resolved type of the declaration a property,
 * parameter or return-value constraint is read from (field and getter are checked separately), the
 * (erased) element type for a container-element position, the extracted type for an unwrapped
 * position, and the bean class for a class-level constraint.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ConstraintSupportCheck {

    private final ConstraintMapperRegistry registry;

    private ConstraintSupportCheck(ConstraintMapperRegistry registry) {
        super();
        this.registry = registry;
    }

    /**
     * Checks all constraints of the given definition, executables included.
     *
     * @param definition the bean definition.
     * @param registry the mappers available.
     * @throws NoConstraintMapperException if a constraint has no mapper, declares no validators and is
     *         not a composition.
     * @throws ConstraintNotApplicableException if a constraint cannot apply to the type it is declared on.
     * @throws IllegalExecutableConstraintException if a cross-parameter constraint has no mapper or
     *         validator that validates parameters.
     */
    static void check(BeanDefinition definition, ConstraintMapperRegistry registry) {
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        new ConstraintSupportCheck(registry).check(definition);
    }

    private void check(BeanDefinition definition) {
        for (ConstraintDefinition constraint : definition.getClassConstraints()) {
            check(constraint, definition.getBeanClass(), constraint.getConstraintSource().describe(), ValidationTarget.ANNOTATED_ELEMENT);
        }

        for (PropertyDefinition property : definition.getProperties()) {
            check(property.getConstraints());
            check(property.getContainerElements(), "");
        }

        for (ExecutableDefinition method : definition.getMethods()) {
            check(method);
        }

        for (ExecutableDefinition constructor : definition.getConstructors()) {
            check(constructor);
        }
    }

    private void check(ExecutableDefinition executable) {
        for (ParameterDefinition parameter : executable.getParameters()) {
            check(parameter.getConstraints());
            check(parameter.getContainerElements(), "");
        }

        if (executable.getReturnValue() != null) {
            check(executable.getReturnValue().getConstraints());
            check(executable.getReturnValue().getContainerElements(), "");
        }

        for (ConstraintDefinition constraint : executable.getCrossParameterConstraints()) {
            ConstraintMapper<?> mapper = registry.find(constraint.getAnnotationType());

            if (mapper != null && !mapper.getSupportedTargets().contains(ValidationTarget.PARAMETERS)) {
                throw new IllegalExecutableConstraintException(executable.getExecutable(), "declares the cross-parameter constraint ["
                        + constraint.getAnnotationType().getName() + "] but neither its mapper nor its validators validate parameters;"
                        + " a cross-parameter validator declares @SupportedValidationTarget(ValidationTarget.PARAMETERS).");
            }

            check(constraint, Object[].class, constraint.getConstraintSource().describe(), ValidationTarget.PARAMETERS);
        }
    }

    /**
     * Checks constraints read from declarations, each against the type of its own declaration.
     */
    private void check(List<ConstraintDefinition> constraints) {
        for (ConstraintDefinition constraint : constraints) {
            ValueDeclaration declaration = ((DeclarationConstraintSource) constraint.getConstraintSource()).getDeclaration();
            check(constraint, declaration.getType(), declaration.describe(), ValidationTarget.ANNOTATED_ELEMENT);
        }
    }

    private void check(List<ContainerElementDefinition> elements, String path) {
        for (ContainerElementDefinition element : elements) {
            String elementPath = path + " " + element.getName();
            Class<?> type = Types.erasure(element.getType());

            for (ConstraintDefinition constraint : element.getConstraints()) {
                check(constraint, type, element.getSourceDeclaration().describe() + elementPath, ValidationTarget.ANNOTATED_ELEMENT);
            }

            check(element.getChildren(), elementPath);
        }
    }

    private void check(ConstraintDefinition constraint, Class<?> type, String location, ValidationTarget target) {
        Class<? extends Annotation> annotationType = constraint.getAnnotationType();
        ConstraintMapper<?> mapper = registry.find(annotationType);

        if (mapper != null) {
            if (!mapper.isApplicable(type, target)) {
                throw new ConstraintNotApplicableException(annotationType, location, type, mapper.getSupportedTypes());
            }
        } else if (!constraint.isComposed()) {
            throw new NoConstraintMapperException(annotationType, location);
        }

        for (ConstraintDefinition composing : constraint.getComposingConstraints()) {
            check(composing, type, location, target);
        }
    }
}
