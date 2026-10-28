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
import org.rulii.lib.spring.util.ClassUtils;
import org.rulii.rule.Rule;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Maps a constraint annotation type to rulii rules. The 22 standard constraints are mapped through
 * this same interface ({@link StandardConstraintMappers}); a custom constraint registers its mapper
 * with the {@link ConstraintMapperRegistry}.
 *
 * <p>Two responsibilities: declaring the value types the constraint applies to, checked when the
 * bean class is read so a misplaced constraint fails fast; and materializing the rules for one
 * occurrence of the constraint. {@link #map} receives the annotation, to read the constraint's own
 * attributes from, and a {@link ConstraintTarget} carrying everything else: the value function, the
 * reporting names and the common configuration (error code, severity, message, null handling).
 * A mapper threads the target through {@link ConstraintTarget#configure} rather than deciding policy
 * itself. Rules built by other means are accepted as long as they read the target's value and their
 * names are unique within the bean class.</p>
 *
 * @param <A> the constraint annotation type.
 * @author Max Arulananthan
 * @since 1.0
 */
public interface ConstraintMapper<A extends Annotation> {

    /**
     * The constraint annotation type this mapper handles.
     *
     * @return the annotation type; never null.
     */
    Class<A> getAnnotationType();

    /**
     * The value types the constraint can be declared on, e.g. {@code CharSequence} for a text
     * constraint; for a mapper backed by a core rule, the rule's {@code SUPPORTED_TYPES}. A declared
     * type is applicable when it relates to one of these by assignment in either direction (see
     * {@link #isApplicable(Class)}). An empty list means no restriction.
     *
     * @return the supported types; never null.
     */
    List<Class<?>> getSupportedTypes();

    /**
     * Materializes the rules for one occurrence of the constraint.
     *
     * @param annotation the constraint annotation, to read attributes from.
     * @param target the value, reporting names and common configuration prepared for this occurrence.
     * @return the rules, in execution order; at least one.
     */
    List<Rule> map(A annotation, ConstraintTarget target);

    /**
     * What the constraint validates: the annotated element (a property, parameter, return value or
     * class) for an ordinary constraint; the parameters of a method or constructor as a whole, as an
     * {@code Object[]}, for a cross-parameter constraint. A mapper may support both.
     *
     * @return the validation targets; the annotated element alone by default.
     */
    default List<ValidationTarget> getSupportedTargets() {
        return List.of(ValidationTarget.ANNOTATED_ELEMENT);
    }

    /**
     * Determines whether the constraint may be declared for the given validation target on a value of
     * the given static type: the target is supported and the type is {@link #isApplicable(Class)
     * applicable}.
     *
     * @param type the declared (static) type of the constrained value; {@code Object[]} for the parameters.
     * @param target the validation target.
     * @return true if the constraint can apply.
     */
    default boolean isApplicable(Class<?> type, ValidationTarget target) {
        Assert.notNull(target, "target cannot be null.");
        return getSupportedTargets().contains(target) && isApplicable(type);
    }

    /**
     * Determines whether the constraint may be declared on a value of the given static type.
     * The check is deliberately lenient: the type is applicable when it is assignable to a
     * supported type (the usual case) <em>or</em> a supported type is assignable to it (e.g. a
     * {@code Object} or {@code Number} declaration whose runtime values may well be supported). Only
     * a provably incompatible declaration is rejected, e.g. {@code @Size} on an {@code int}. The
     * runtime value is still type-checked by the materialized rule. Primitives are considered
     * through their wrapper types.
     *
     * @param type the declared (static) type of the constrained value.
     * @return true if the constraint can apply to the type.
     */
    default boolean isApplicable(Class<?> type) {
        Assert.notNull(type, "type cannot be null.");
        List<Class<?>> supportedTypes = getSupportedTypes();
        if (supportedTypes.isEmpty()) return true;

        Class<?> declared = ClassUtils.resolvePrimitiveIfNecessary(type);

        for (Class<?> supportedType : supportedTypes) {
            Class<?> supported = ClassUtils.resolvePrimitiveIfNecessary(supportedType);
            if (supported.isAssignableFrom(declared) || declared.isAssignableFrom(supported)) return true;
        }

        return false;
    }
}
