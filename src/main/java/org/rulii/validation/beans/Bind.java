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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a named value at a declaration site, for the rules that run there: a bean class, or a method or
 * constructor. It states a fact about the declaration, the way a constraint attribute does
 * ({@code @Size(max = 10)}), generalised to registry rules: one registered rule becomes a template
 * parameterised per site, {@code @Validate("withinLimit") @Bind(name = "limit", value = "10")} on one
 * method and {@code value = "500"} on another. A rule reads the binding by name, as it reads a property
 * or an argument.
 *
 * <p>The value is a {@link #value() literal} or an {@link #expr() expression}, one of the two. A literal
 * is bound as the {@code String} it is, or converted once, when the class is first read for validation,
 * to the {@link #type()} declared, through the validator's converter registry. An expression is a script
 * in the language of {@link #lang()} (the {@code @Expr} rules apply: the nearest {@link ExprDefaults},
 * else the {@code ScriptProcessorManager}'s default), compiled once with the class and evaluated
 * lazily: on the first read within the validation of the object or the call, remembered for its
 * duration, never when nothing reads it. It sees what the validation has bound, the other bindings
 * declared at the site included.</p>
 *
 * <p>At class level the binding lives beside the properties of the object: every constraint rule,
 * {@code @Validate} runnable and {@code @Expr} script of the object sees it, and so do the objects
 * cascaded into. On a method or constructor it lives beside the arguments or the return value, as
 * {@link #applyTo()} says, and nowhere else. Class-level bindings are inherited like class-level
 * constraints; a subtype redeclaring a name replaces the inherited binding. Bindings for the
 * parameters follow the inheritance rules of parameter constraints (an override may not add or
 * redeclare them); bindings for the return value accumulate over the chain, an override's declaration
 * replacing an inherited name.</p>
 *
 * <p>The name must be a valid rulii name, not one the engine or this library reserves, not the name of
 * a property of the class or a parameter of the executable, and declared once per site; any of these
 * fails fast when the class is read. Fields, getters and record components are not targets.</p>
 *
 * <p>A value that would change for a business reason does not belong here: it belongs in the registry,
 * wired into the rule. Nothing here reaches a service or a bean.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 * @see Validate
 * @see Expr
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Bind.List.class)
@Documented
public @interface Bind {

    /**
     * The binding name the rules read the value under. Must be a valid rulii name.
     *
     * @return the name.
     */
    String name();

    /**
     * The value, as a literal. Bound as a {@code String}, or converted to {@link #type()} when one is
     * declared. Exclusive with {@link #expr()}.
     *
     * @return the literal, or empty.
     */
    String value() default "";

    /**
     * The value, as a script in the language of {@link #lang()}, evaluated lazily against the validation
     * scope. Exclusive with {@link #value()}.
     *
     * @return the script, or empty.
     */
    String expr() default "";

    /**
     * The type the value is bound with. {@code Object} means none declared: a literal stays a
     * {@code String}, an expression result is bound as it comes. Any other type converts the literal
     * to it when the class is read, and checks the expression result against it when it is evaluated.
     *
     * @return the type.
     */
    Class<?> type() default Object.class;

    /**
     * The scripting language of {@link #expr()}, as registered with the {@code ScriptProcessorManager}.
     * Empty means the nearest {@link ExprDefaults}, else the manager's default language.
     *
     * @return the language name, or empty.
     */
    String lang() default "";

    /**
     * On a method or constructor, whether the binding belongs to the validation of the arguments or
     * of the return value. {@code IMPLICIT} means the arguments when the executable has parameters and
     * the return value otherwise. Anything but {@code IMPLICIT} elsewhere, or a target the executable
     * does not have, fails fast when the class is read.
     *
     * @return the target.
     */
    ConstraintTarget applyTo() default ConstraintTarget.IMPLICIT;

    /**
     * Several {@link Bind} on one element.
     */
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {

        /**
         * The bindings, in declaration order.
         *
         * @return the bindings.
         */
        Bind[] value();
    }
}
