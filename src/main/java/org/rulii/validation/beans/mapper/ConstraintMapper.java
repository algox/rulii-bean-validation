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

import org.rulii.lib.spring.util.Assert;
import org.rulii.lib.spring.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps one constraint annotation type to rulii rules. This is the extension point for custom
 * constraints: rulii bean validation never executes {@code jakarta.validation.ConstraintValidator}
 * implementations; a constraint is supported when a mapper is registered for its annotation type
 * (the standard {@code jakarta.validation.constraints} are registered through the same mechanism,
 * see {@link ConstraintMapperRegistry#builder()}).
 *
 * <p>At metadata-read time a mapper answers which value types the constraint may be declared on
 * ({@link #getSupportedTypes()}, {@link #isApplicable(Class)}), so a misplaced constraint fails when
 * the bean class is read rather than when a value is validated. A mapper backed by a core
 * {@code ValueValidationRule} returns that rule's {@code SUPPORTED_TYPES}; it does not restate them.
 * Materialization into rules is added by the runtime half of the library.</p>
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
