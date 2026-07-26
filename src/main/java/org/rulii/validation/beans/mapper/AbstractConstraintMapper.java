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

import org.rulii.model.function.Function;
import org.rulii.validation.ValueValidationRuleBuilder;
import org.rulii.validation.beans.BeanValidationException;
import org.rulii.validation.beans.ConstraintMapper;
import org.rulii.validation.beans.ConstraintTarget;
import org.rulii.validation.beans.metadata.ConstraintMetadata;
import org.rulii.validation.rules.Validators;

import java.math.BigDecimal;

/**
 * Common plumbing for the standard constraint mappers: value-binding creation and the
 * uniform threading of a target's reporting config into a core rule builder. The two
 * configuration entry points encode the jakarta null contract — {@link #configure}
 * applies null-skip ({@code failOnNull(false)}, every constraint except presence ones
 * passes null), {@link #configurePresence} leaves the core default (null fails).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
abstract class AbstractConstraintMapper implements ConstraintMapper {

    AbstractConstraintMapper() {
        super();
    }

    /**
     * Creates the value function reading the target's scope binding.
     *
     * @param target the constraint target.
     * @return a binding-backed value function.
     */
    protected Function<?> binding(ConstraintTarget target) {
        return Validators.binding(target.getBindingName());
    }

    /**
     * Threads the target's reporting config into the given builder with jakarta
     * null-skip semantics (a null value skips the rule).
     *
     * @param builder the core rule builder.
     * @param target the constraint target.
     * @param <T> the builder type.
     * @return the configured builder.
     */
    protected <T extends ValueValidationRuleBuilder<T, ?>> T configure(T builder, ConstraintTarget target) {
        return configurePresence(builder, target).failOnNull(false);
    }

    /**
     * Threads the target's reporting config into the given builder keeping the core
     * null default (a null value fails) — the presence constraints.
     *
     * @param builder the core rule builder.
     * @param target the constraint target.
     * @param <T> the builder type.
     * @return the configured builder.
     */
    protected <T extends ValueValidationRuleBuilder<T, ?>> T configurePresence(T builder, ConstraintTarget target) {
        builder.name(target.getRuleName())
                .errorCode(target.getErrorCode())
                .severity(target.getSeverity())
                .valueName(target.getValueName());
        if (target.getMessage() != null) builder.message(target.getMessage());
        return builder;
    }

    /**
     * Retrieves a required attribute off the constraint.
     *
     * @param constraint the constraint occurrence.
     * @param name the attribute name.
     * @param <T> the expected attribute type.
     * @return the attribute value.
     */
    protected <T> T attribute(ConstraintMetadata constraint, String name) {
        T result = constraint.getAttribute(name);
        if (result == null) {
            throw new IllegalStateException("Constraint [@" + constraint.getAnnotationType().getSimpleName()
                    + "] has no attribute [" + name + "].");
        }
        return result;
    }

    /**
     * Retrieves a required String attribute as a BigDecimal (the jakarta decimal-bound
     * attributes are String-typed).
     *
     * @param constraint the constraint occurrence.
     * @param target the constraint target (for error reporting).
     * @param name the attribute name.
     * @return the parsed BigDecimal.
     */
    protected BigDecimal decimalAttribute(ConstraintMetadata constraint, ConstraintTarget target, String name) {
        String value = attribute(constraint, name);

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BeanValidationException("Invalid [" + name + "] attribute [" + value + "] of [@"
                    + constraint.getAnnotationType().getSimpleName() + "] on [" + target.getBeanClass().getName()
                    + "." + target.getPropertyName() + "]. It must be a valid BigDecimal representation.", e);
        }
    }
}
