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

import jakarta.validation.Constraint;
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
 * registered for it (or it is a pure composition of mapped constraints) and the mapper accepts the
 * static type the constraint is declared on. Runs once per bean class after its definition is read,
 * so misplaced or unsupported constraints fail when the class is loaded rather than when a value is
 * validated.
 *
 * <p>Rules: a constraint with a registered mapper must be {@link ConstraintMapper#isApplicable(Class)
 * applicable} to its type. A constraint with no mapper is accepted only when it is a composition
 * ({@link ConstraintDefinition#isComposed()}) whose {@code @Constraint(validatedBy)} is empty: a
 * composed constraint that also names validators would lose those checks silently, so it needs a
 * mapper too. Composing constraints are checked recursively against the same type.</p>
 *
 * <p>The type checked is the one the value will have: the resolved type of the declaration a property
 * constraint is read from (field and getter are checked separately), the (erased) element type for a
 * container-element position, the extracted type for an unwrapped position, and the bean class for a
 * class-level constraint.</p>
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
     * Checks all constraints of the given definition.
     *
     * @param definition the bean definition.
     * @param registry the mappers available.
     * @throws NoConstraintMapperException if a constraint has no mapper and is not a pure composition.
     * @throws ConstraintNotApplicableException if a constraint cannot apply to the type it is declared on.
     */
    static void check(BeanDefinition definition, ConstraintMapperRegistry registry) {
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        new ConstraintSupportCheck(registry).check(definition);
    }

    private void check(BeanDefinition definition) {
        for (ConstraintDefinition constraint : definition.getClassConstraints()) {
            check(constraint, definition.getBeanClass(), constraint.getConstraintSource().describe());
        }

        for (PropertyDefinition property : definition.getProperties()) {
            for (ConstraintDefinition constraint : property.getConstraints()) {
                PropertyDeclaration declaration = ((PropertyConstraintSource) constraint.getConstraintSource()).getDeclaration();
                check(constraint, declaration.getType(), declaration.describe());
            }

            check(property.getContainerElements(), "");
        }
    }

    private void check(List<ContainerElementDefinition> elements, String path) {
        for (ContainerElementDefinition element : elements) {
            String elementPath = path + " " + element.getName();
            Class<?> type = Types.erasure(element.getType());

            for (ConstraintDefinition constraint : element.getConstraints()) {
                check(constraint, type, element.getSourceDeclaration().describe() + elementPath);
            }

            check(element.getChildren(), elementPath);
        }
    }

    private void check(ConstraintDefinition constraint, Class<?> type, String location) {
        Class<? extends Annotation> annotationType = constraint.getAnnotationType();
        ConstraintMapper<?> mapper = registry.find(annotationType);

        if (mapper != null) {
            if (!mapper.isApplicable(type)) {
                throw new ConstraintNotApplicableException(annotationType, location, type, mapper.getSupportedTypes());
            }
        } else if (!constraint.isComposed() || declaresValidators(annotationType)) {
            throw new NoConstraintMapperException(annotationType, location);
        }

        for (ConstraintDefinition composing : constraint.getComposingConstraints()) {
            check(composing, type, location);
        }
    }

    private static boolean declaresValidators(Class<? extends Annotation> annotationType) {
        Constraint constraint = annotationType.getAnnotation(Constraint.class);
        return constraint != null && constraint.validatedBy().length > 0;
    }
}
