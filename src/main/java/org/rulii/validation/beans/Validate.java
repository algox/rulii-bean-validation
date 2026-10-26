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
package org.rulii.validation.beans;

import jakarta.validation.ConstraintTarget;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches named rulii runnables from the validation {@code RuleRegistry} to a bean class, to one of
 * its properties, or to a method or constructor. Each name refers to anything the registry holds (a
 * {@code Rule}, {@code RuleSet} or {@code RuleFlow}); the runnables execute in declaration order,
 * after the constraints of the same step, and report into the same violations.
 *
 * <p>At class level the runnable sees the whole bean: the root object and every property are bound,
 * and its violations report at the bean node. On a field, getter or record component the runnable
 * additionally sees that property's value as {@code $value}, and its violations report at that
 * property's path. On a method or constructor it validates the arguments or the return value, as
 * {@link #applyTo()} says: for the arguments, the runnable sees them bound by parameter name (and as
 * {@code $parameters}) and reports at the cross-parameter node; for the return value it sees the value
 * as {@code $value} (and as {@code $returnValue}) and reports at the return-value node. The
 * inheritance rules of parameter constraints apply: an overriding method may not add rules for its
 * arguments. Setters are not scanned as properties, but a setter may carry the annotation for its
 * arguments like any method.</p>
 *
 * <p>Names are resolved at validation time, so what is registered under a name can change without
 * recompiling the domain class; a name that resolves to nothing fails loudly, never silently.
 * {@link #groups()} select when the rules run exactly as a constraint's groups do: none means
 * {@code Default}, otherwise the rules run when a declared group, or a group extending it, is among
 * the groups requested. A rule that wants to branch further reads the groups being processed from the
 * {@code $groups} binding.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Validate {

    /**
     * The registry names of the runnables to execute, in declaration order. At least one name is
     * required; a name must not be blank and must not repeat.
     *
     * @return the registry names.
     */
    String[] value();

    /**
     * The groups the rules belong to, with the semantics of a constraint's groups: none means
     * {@code Default}. Every group must be an interface.
     *
     * @return the groups.
     */
    Class<?>[] groups() default {};

    /**
     * On a method or constructor, whether the rules validate the arguments or the return value.
     * {@code IMPLICIT} means the arguments when the executable has parameters and the return value
     * otherwise; a method with both validates its arguments unless {@code RETURN_VALUE} is declared.
     * Anything but {@code IMPLICIT} elsewhere, or a target the executable does not have, fails fast
     * when the class is read.
     *
     * @return the target.
     */
    ConstraintTarget applyTo() default ConstraintTarget.IMPLICIT;
}
