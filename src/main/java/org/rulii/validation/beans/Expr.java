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
import org.rulii.validation.Severity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches an inline script condition to a bean class, to one of its properties, or to a method or
 * constructor. The script is compiled once, when the class is first read for validation, through
 * rulii's {@code ScriptProcessorManager}, and runs as a rule beside the constraints of the same
 * element: it must evaluate to {@code true} for the value to be valid, and a {@code false} result
 * reports a violation under {@link #errorCode()}. A script that throws, or returns anything but a
 * boolean, is a bug and propagates as an error, never a violation.
 *
 * <p>The script sees what the validation flow has bound, under the bindings variable of its language
 * ({@code ctx} by default): the bean's properties by name, {@code $this}, {@code $root},
 * {@code $parent} and {@code $groups}; on a method or constructor the arguments by parameter name and
 * as {@code $parameters}, or the return value as {@code $returnValue}. Where the violation reports,
 * and what the validated value is, follows the element: a property's path and value on a field,
 * getter or record component; the bean node and the bean itself at class level; the cross-parameter
 * node and the arguments, or the return-value node and the return value, on a method or constructor
 * as {@link #applyTo()} says.</p>
 *
 * <p>{@link #lang()} names the scripting language; left empty, the nearest {@link ExprDefaults}
 * (on the declaring class, else its package) names it, else the default language declared on the
 * {@code ScriptProcessorManager} (through {@code setDefaultLanguage} or the system property
 * {@code rulii.script.defaultLanguage}). Without any of these the class fails to load. The language
 * is never inferred from the classpath.</p>
 *
 * <p>This is for intrinsic invariants a constraint annotation cannot express, such as
 * {@code end} after {@code start}: conditions that would make the object nonsensical in every use.
 * A condition that may change for a business reason belongs in the registry, reached through
 * {@link Validate}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 * @see ExprDefaults
 * @see Validate
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Expr.List.class)
@Documented
public @interface Expr {

    /**
     * The script: a condition in the language of {@link #lang()}. Must not be blank.
     *
     * @return the script text.
     */
    String value();

    /**
     * The scripting language of the script, as registered with the {@code ScriptProcessorManager}
     * ({@code java}, {@code js}, {@code jakarta-el}, ...). Empty means the nearest
     * {@link ExprDefaults}, else the manager's default language.
     *
     * @return the language name, or empty.
     */
    String lang() default "";

    /**
     * The error code of the violation reported when the script is false. Must not be blank. It is
     * also the message key the violation's message is resolved under when {@link #message()} is empty.
     *
     * @return the error code.
     */
    String errorCode();

    /**
     * The message of the violation, as a rulii template: {@code ${value}} and {@code ${validatedValue}}
     * stand for the validated value (so does the property or parameter name it is reported under). Empty means the
     * message resolved under {@link #errorCode()} through the message bundles
     * ({@code ValidationMessages}, the configured resolver, the library's defaults), else the rule's
     * default text.
     *
     * @return the message template, or empty.
     */
    String message() default "";

    /**
     * The severity of the violation.
     *
     * @return the severity.
     */
    Severity severity() default Severity.ERROR;

    /**
     * The groups the rule belongs to, with the semantics of a constraint's groups: none means
     * {@code Default}. Every group must be an interface.
     *
     * @return the groups.
     */
    Class<?>[] groups() default {};

    /**
     * On a method or constructor, whether the script validates the arguments or the return value.
     * {@code IMPLICIT} means the arguments when the executable has parameters and the return value
     * otherwise; a method with both validates its arguments unless {@code RETURN_VALUE} is declared.
     * Anything but {@code IMPLICIT} elsewhere, or a target the executable does not have, fails fast
     * when the class is read.
     *
     * @return the target.
     */
    ConstraintTarget applyTo() default ConstraintTarget.IMPLICIT;

    /**
     * On a field, getter or record component, whether a null property value skips the script
     * (the default, as constraints leave null to {@code @NotNull}) or runs it with the null value.
     * At class level and on a method or constructor the script always runs.
     *
     * @return true to skip the script on a null property value.
     */
    boolean skipNull() default true;

    /**
     * Several {@link Expr} on one element.
     */
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {

        /**
         * The expressions, in declaration order.
         *
         * @return the expressions.
         */
        Expr[] value();
    }
}
