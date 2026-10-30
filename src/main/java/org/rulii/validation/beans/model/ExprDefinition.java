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

import jakarta.validation.ConstraintTarget;
import org.rulii.lib.spring.util.Assert;
import org.rulii.lib.spring.util.StringUtils;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.ExprDefaults;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Definition of one {@link Expr @Expr} occurrence: the script, the language it is declared in, the
 * error code, message, severity and groups of its violations, whether a null property value skips
 * it, and where it is declared (a property declaration, a class, the return value of an executable,
 * or the parameters of an executable as a whole). The script is compiled when the rules of the class
 * are materialized; nothing here consults a script engine.
 *
 * <p>The language is read here, as declared: {@link Expr#lang()}, else the {@link ExprDefaults} of
 * the class declaring the member, else the one of its package. {@link #getLanguage()} is null when
 * none of these names a language; the materializer then falls back to the
 * {@code ScriptProcessorManager}'s default and fails when there is none.</p>
 *
 * <p>The script and the error code may not be blank; the groups must be interfaces, empty groups
 * meaning {@code Default}. {@code applyTo} is read by {@link ExecutableDefinition}; declared anywhere
 * but on a method or constructor it must be {@code IMPLICIT}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ExprDefinition implements RuleDeclaration {

    private final ConstraintSource declarationSource;
    private final Expr annotation;
    private final String language;
    private final List<Class<?>> groups;
    private final SourceDefinition source;

    private ExprDefinition(ConstraintSource declarationSource, Expr annotation, String language, List<Class<?>> groups) {
        super();
        this.declarationSource = declarationSource;
        this.annotation = annotation;
        this.language = language;
        this.groups = groups;
        this.source = declarationSource.getSourceDefinition();
    }

    /**
     * Reads the {@code @Expr} occurrences of a property, parameter or return-value declaration, in
     * declaration order.
     *
     * @param sourceDeclaration the declaration.
     * @return the definitions; empty if none.
     * @throws ExprDeclarationException if an occurrence is invalid, or declares an {@code applyTo}
     *         where it does not apply.
     */
    public static List<ExprDefinition> load(ValueDeclaration sourceDeclaration) {
        Assert.notNull(sourceDeclaration, "sourceDeclaration cannot be null.");
        List<ExprDefinition> result = load(new DeclarationConstraintSource(sourceDeclaration), sourceDeclaration.getDeclaredAnnotations());

        for (ExprDefinition definition : result) {
            ConstraintTarget applyTo = definition.getApplyTo();
            ValueDeclaration.Kind kind = sourceDeclaration.getKind();
            // The executable definition resolves applyTo for a return value; a getter has no parameters.
            boolean allowed = kind == ValueDeclaration.Kind.RETURN_VALUE
                    || applyTo == ConstraintTarget.IMPLICIT
                    || (kind == ValueDeclaration.Kind.GETTER && applyTo == ConstraintTarget.RETURN_VALUE);

            if (!allowed) {
                throw new ExprDeclarationException("@Expr on [" + definition.declarationSource.describe() + "] declares applyTo = "
                        + applyTo + ", which is for methods and constructors.", definition.declarationSource);
            }
        }

        return result;
    }

    /**
     * Reads the class-level {@code @Expr} occurrences of one class or interface, in declaration order.
     *
     * @param type the class.
     * @return the definitions; empty if none.
     * @throws ExprDeclarationException if an occurrence is invalid or declares an {@code applyTo}.
     */
    public static List<ExprDefinition> load(Class<?> type) {
        Assert.notNull(type, "type cannot be null.");
        List<ExprDefinition> result = load(new ClassConstraintSource(type), Arrays.asList(type.getDeclaredAnnotations()));

        for (ExprDefinition definition : result) {
            if (definition.getApplyTo() != ConstraintTarget.IMPLICIT) {
                throw new ExprDeclarationException("@Expr on [" + definition.declarationSource.describe() + "] declares applyTo = "
                        + definition.getApplyTo() + ", which is for methods and constructors.", definition.declarationSource);
            }
        }

        return result;
    }

    private static List<ExprDefinition> load(ConstraintSource declarationSource, List<Annotation> annotations) {
        List<ExprDefinition> result = new ArrayList<>();

        for (Annotation annotation : annotations) {
            if (annotation instanceof Expr expr) {
                result.add(load(declarationSource, expr));
            } else if (annotation instanceof Expr.List list) {
                for (Expr expr : list.value()) {
                    result.add(load(declarationSource, expr));
                }
            }
        }

        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    /**
     * Reads one {@code @Expr} declared at the given source.
     *
     * @param declarationSource where the annotation is declared.
     * @param annotation the annotation.
     * @return the definition.
     * @throws ExprDeclarationException if the script or the error code is blank, a group is not an
     *         interface, or an {@code @ExprDefaults} in scope declares a blank language.
     */
    public static ExprDefinition load(ConstraintSource declarationSource, Expr annotation) {
        Assert.notNull(declarationSource, "declarationSource cannot be null.");
        Assert.notNull(annotation, "annotation cannot be null.");

        if (!StringUtils.hasText(annotation.value())) {
            throw new ExprDeclarationException("@Expr on [" + declarationSource.describe() + "] declares no script.", declarationSource);
        }

        if (!StringUtils.hasText(annotation.errorCode())) {
            throw new ExprDeclarationException("@Expr on [" + declarationSource.describe() + "] declares no errorCode.", declarationSource);
        }

        List<Class<?>> groups = new ArrayList<>(annotation.groups().length);

        for (Class<?> group : annotation.groups()) {
            if (group == null || !group.isInterface()) {
                throw new ExprDeclarationException("@Expr on [" + declarationSource.describe() + "] declares the group ["
                        + (group == null ? null : group.getName()) + "], which is not an interface.", declarationSource);
            }

            groups.add(group);
        }

        return new ExprDefinition(declarationSource, annotation, language(declarationSource, annotation.lang(), "@Expr"),
                Collections.unmodifiableList(groups));
    }

    /**
     * The declared language of a script: the one declared on the annotation, else the declaring
     * class's {@code @ExprDefaults}, else its package's; null when none names one. Shared with
     * {@code @Bind}, whose expressions follow the same chain.
     *
     * @param declarationSource where the annotation is declared.
     * @param declared the annotation's own {@code lang}.
     * @param what the annotation, for the message ({@code @Expr}, {@code @Bind [name]}).
     * @return the language name, or null.
     * @throws ExprDeclarationException if the {@code @ExprDefaults} in scope declares a blank language.
     */
    static String language(ConstraintSource declarationSource, String declared, String what) {
        if (StringUtils.hasText(declared)) return declared.trim();

        Class<?> declaringClass = declarationSource.getDeclaringClass();
        ExprDefaults defaults = declaringClass.getDeclaredAnnotation(ExprDefaults.class);
        String owner = "class [" + declaringClass.getName() + "]";

        if (defaults == null && declaringClass.getPackage() != null) {
            defaults = declaringClass.getPackage().getAnnotation(ExprDefaults.class);
            owner = "package [" + declaringClass.getPackage().getName() + "]";
        }

        if (defaults == null) return null;

        if (!StringUtils.hasText(defaults.lang())) {
            throw new ExprDeclarationException("@ExprDefaults on " + owner + " declares a blank lang; needed by " + what + " on ["
                    + declarationSource.describe() + "].", declarationSource);
        }

        return defaults.lang().trim();
    }

    @Override
    public String getName() {
        return Expr.class.getSimpleName();
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    @Override
    public ConstraintSource getConstraintSource() {
        return declarationSource;
    }

    public Expr getAnnotation() {
        return annotation;
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Expr.class;
    }

    /**
     * The script text, as declared.
     *
     * @return the script.
     */
    public String getScript() {
        return annotation.value();
    }

    /**
     * The language the script is declared in: {@code @Expr(lang)}, else the nearest
     * {@code @ExprDefaults}; null when neither names one and the manager's default applies.
     *
     * @return the language name, or null.
     */
    public String getLanguage() {
        return language;
    }

    public String getErrorCode() {
        return annotation.errorCode();
    }

    /**
     * The message template as declared; null when {@code message} is empty and the message resolves
     * from the error code.
     *
     * @return the template, or null.
     */
    @Override
    public String getMessage() {
        return StringUtils.hasText(annotation.message()) ? annotation.message() : null;
    }

    @Override
    public Severity getSeverity() {
        return annotation.severity();
    }

    @Override
    public List<Class<?>> getGroups() {
        return groups;
    }

    public ConstraintTarget getApplyTo() {
        return annotation.applyTo();
    }

    /**
     * Whether a null property value skips the script. Meaningful on a property declaration only.
     *
     * @return true to skip on null.
     */
    public boolean isSkipNull() {
        return annotation.skipNull();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExprDefinition that = (ExprDefinition) o;
        return declarationSource.equals(that.declarationSource) && annotation.equals(that.annotation);
    }

    @Override
    public int hashCode() {
        return 31 * declarationSource.hashCode() + annotation.hashCode();
    }

    @Override
    public String toString() {
        return "@Expr[" + (language != null ? language + ": " : "") + annotation.value() + "] errorCode=" + annotation.errorCode()
                + (groups.isEmpty() ? "" : " groups=" + groups) + " on " + declarationSource.describe();
    }
}
