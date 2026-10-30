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
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.util.RuleUtils;
import org.rulii.validation.beans.Bind;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definition of one {@link Bind @Bind} occurrence: the binding name, the literal or the script text,
 * the type the value is bound with, the language the script is declared in, and where it is declared
 * (a class, the return value of an executable, or the parameters of an executable as a whole). Nothing
 * here converts a literal or compiles a script; the materializer does, when the rules of the class
 * are built.
 *
 * <p>The language is read as {@code @Expr}'s is: {@link Bind#lang()}, else the {@code @ExprDefaults}
 * of the declaring class, else the one of its package; {@link #getLanguage()} is null when none of
 * these names one. {@code applyTo} is read by {@link ExecutableDefinition}; declared anywhere but on a
 * method or constructor it must be {@code IMPLICIT}. Fields, getters and record components are not
 * targets: a {@code @Bind} found on a getter fails the read.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BindDefinition implements Definition {

    private final ConstraintSource declarationSource;
    private final Bind annotation;
    private final String language;
    private final SourceDefinition source;

    private BindDefinition(ConstraintSource declarationSource, Bind annotation, String language) {
        super();
        this.declarationSource = declarationSource;
        this.annotation = annotation;
        this.language = language;
        this.source = declarationSource.getSourceDefinition();
    }

    /**
     * Reads the {@code @Bind} occurrences of a return-value declaration, in declaration order.
     *
     * @param sourceDeclaration the declaration.
     * @return the definitions; empty if none.
     * @throws BindDeclarationException if an occurrence is invalid, or the declaration is a field, a
     *         getter or a record component, which are not targets.
     */
    public static List<BindDefinition> load(ValueDeclaration sourceDeclaration) {
        Assert.notNull(sourceDeclaration, "sourceDeclaration cannot be null.");
        DeclarationConstraintSource declarationSource = new DeclarationConstraintSource(sourceDeclaration);
        List<BindDefinition> result = load(declarationSource, sourceDeclaration.getDeclaredAnnotations());

        if (!result.isEmpty() && sourceDeclaration.getKind() != ValueDeclaration.Kind.RETURN_VALUE) {
            throw new BindDeclarationException("@Bind on [" + declarationSource.describe() + "] is not supported: fields,"
                    + " getters and record components are not targets of @Bind; declare it on the class, or on a method"
                    + " or constructor.", declarationSource, result.get(0).getBindingName());
        }

        checkDistinct(result, "the return value of [" + declarationSource.describe() + "]");
        return result;
    }

    /**
     * Reads the class-level {@code @Bind} occurrences of one class or interface, in declaration order
     * (its own declared annotations only; the hierarchy is walked by the bean definition).
     *
     * @param type the class.
     * @return the definitions; empty if none.
     * @throws BindDeclarationException if an occurrence is invalid, declares an {@code applyTo}, or
     *         repeats a name declared on the same type.
     */
    public static List<BindDefinition> load(Class<?> type) {
        Assert.notNull(type, "type cannot be null.");
        ClassConstraintSource declarationSource = new ClassConstraintSource(type);
        List<BindDefinition> result = load(declarationSource, Arrays.asList(type.getDeclaredAnnotations()));

        for (BindDefinition definition : result) {
            if (definition.getApplyTo() != ConstraintTarget.IMPLICIT) {
                throw new BindDeclarationException("@Bind [" + definition.getBindingName() + "] on [" + declarationSource.describe()
                        + "] declares applyTo = " + definition.getApplyTo() + ", which is for methods and constructors.",
                        declarationSource, definition.getBindingName());
            }
        }

        checkDistinct(result, "class [" + type.getName() + "]");
        return result;
    }

    /**
     * Reads the {@code @Bind} occurrences among the given annotations, declared at the given source,
     * in declaration order.
     *
     * @param declarationSource where the annotations are declared.
     * @param annotations the annotations; {@code Bind} and {@code Bind.List} are read, the rest ignored.
     * @return the definitions; empty if none.
     * @throws BindDeclarationException if an occurrence is invalid.
     */
    public static List<BindDefinition> load(ConstraintSource declarationSource, List<Annotation> annotations) {
        Assert.notNull(declarationSource, "declarationSource cannot be null.");
        Assert.notNull(annotations, "annotations cannot be null.");
        List<BindDefinition> result = new ArrayList<>();

        for (Annotation annotation : annotations) {
            if (annotation instanceof Bind bind) {
                result.add(load(declarationSource, bind));
            } else if (annotation instanceof Bind.List list) {
                for (Bind bind : list.value()) {
                    result.add(load(declarationSource, bind));
                }
            }
        }

        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    /**
     * Reads one {@code @Bind} declared at the given source.
     *
     * @param declarationSource where the annotation is declared.
     * @param annotation the annotation.
     * @return the definition.
     * @throws BindDeclarationException if the name is blank or not a valid rulii name, both or
     *         neither of a literal and an expression are declared, or an {@code @ExprDefaults} in
     *         scope declares a blank language. Reserved names and collisions with the bindings of the
     *         site are the plan's to detect.
     */
    public static BindDefinition load(ConstraintSource declarationSource, Bind annotation) {
        Assert.notNull(declarationSource, "declarationSource cannot be null.");
        Assert.notNull(annotation, "annotation cannot be null.");
        String name = annotation.name();

        if (!StringUtils.hasText(name)) {
            throw new BindDeclarationException("@Bind on [" + declarationSource.describe() + "] declares a blank name.",
                    declarationSource, null);
        }

        if (!RuleUtils.isValidName(name)) {
            throw new BindDeclarationException("@Bind on [" + declarationSource.describe() + "] declares the name [" + name
                    + "], which is not a valid binding name; a name matches [" + RuleUtils.NAME_REGEX + "].", declarationSource, name);
        }

        boolean literal = !annotation.value().isEmpty();
        boolean expression = StringUtils.hasText(annotation.expr());

        if (literal == expression) {
            throw new BindDeclarationException("@Bind [" + name + "] on [" + declarationSource.describe() + "] must declare "
                    + (literal ? "either a value or an expr, not both." : "a value or an expr."), declarationSource, name);
        }

        if (annotation.type() == null || annotation.type() == void.class) {
            throw new BindDeclarationException("@Bind [" + name + "] on [" + declarationSource.describe()
                    + "] declares an unusable type [" + annotation.type() + "].", declarationSource, name);
        }

        String language = expression ? ExprDefinition.language(declarationSource, annotation.lang(), "@Bind [" + name + "]") : null;
        return new BindDefinition(declarationSource, annotation, language);
    }

    /**
     * Checks that no name repeats across the given occurrences, which all belong to one site.
     *
     * @param definitions the occurrences, in declaration order.
     * @param owner what they belong to, for the message.
     * @throws BindDeclarationException if a name is declared by more than one occurrence.
     */
    static void checkDistinct(List<BindDefinition> definitions, String owner) {
        if (definitions.size() < 2) return;

        Map<String, BindDefinition> seen = new LinkedHashMap<>();

        for (BindDefinition definition : definitions) {
            BindDefinition previous = seen.putIfAbsent(definition.getBindingName(), definition);

            if (previous != null) {
                throw new BindDeclarationException(owner + " declares @Bind [" + definition.getBindingName() + "] more than once:"
                        + " on [" + previous.declarationSource.describe() + "] and on [" + definition.declarationSource.describe() + "].",
                        definition.declarationSource, definition.getBindingName());
            }
        }
    }

    /**
     * Resolves the occurrences declared across a hierarchy into one binding per name: a declaration
     * replaces an inherited one when its declaring type is a subtype of the holder's, is ignored when
     * the holder's type is the subtype, and fails when the two types are unrelated, since neither
     * declaration can be said to override the other.
     *
     * @param definitions the occurrences, each site's already distinct, in any order.
     * @param owner what they belong to, for the message.
     * @return one definition per name, in the order the names were first met.
     * @throws BindDeclarationException if a name is declared by two unrelated types.
     */
    static List<BindDefinition> resolve(List<BindDefinition> definitions, String owner) {
        if (definitions.size() < 2) return definitions;

        Map<String, BindDefinition> resolved = new LinkedHashMap<>();

        for (BindDefinition definition : definitions) {
            BindDefinition holder = resolved.get(definition.getBindingName());

            if (holder == null) {
                resolved.put(definition.getBindingName(), definition);
                continue;
            }

            Class<?> type = definition.declarationSource.getDeclaringClass();
            Class<?> holderType = holder.declarationSource.getDeclaringClass();

            if (holderType != type && holderType.isAssignableFrom(type)) {
                resolved.put(definition.getBindingName(), definition);
            } else if (!type.isAssignableFrom(holderType)) {
                throw new BindDeclarationException(owner + " inherits @Bind [" + definition.getBindingName() + "] from the unrelated"
                        + " types [" + holderType.getName() + "] and [" + type.getName() + "]; neither overrides the other, so the"
                        + " class must redeclare it.", definition.declarationSource, definition.getBindingName());
            }
        }

        return Collections.unmodifiableList(new ArrayList<>(resolved.values()));
    }

    /**
     * Checks that no binding takes the name of one of the given properties or parameters.
     *
     * @param definitions the bindings.
     * @param names the names taken, by the properties of the class or the parameters of the executable.
     * @param owner what the bindings belong to, for the message.
     * @param kind {@code property} or {@code parameter}, for the message.
     * @throws BindDeclarationException if a binding name is taken.
     */
    static void checkAgainst(List<BindDefinition> definitions, Set<String> names, String owner, String kind) {
        for (BindDefinition definition : definitions) {
            if (names.contains(definition.getBindingName())) {
                throw new BindDeclarationException(owner + " declares @Bind [" + definition.getBindingName() + "] on ["
                        + definition.declarationSource.describe() + "], which is the name of a " + kind + "; a binding may not"
                        + " shadow a " + kind + ".", definition.declarationSource, definition.getBindingName());
            }
        }
    }

    /**
     * Simple name of the annotation ({@code Bind}).
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return Bind.class.getSimpleName();
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    /**
     * Where this occurrence is declared: the class or interface, the executable (parameters side) or
     * its return-value declaration.
     *
     * @return the declaration source.
     */
    public ConstraintSource getDeclarationSource() {
        return declarationSource;
    }

    /**
     * The original annotation instance.
     *
     * @return the annotation.
     */
    public Bind getAnnotation() {
        return annotation;
    }

    /**
     * The binding name.
     *
     * @return the name.
     */
    public String getBindingName() {
        return annotation.name();
    }

    /**
     * The literal, as declared.
     *
     * @return the literal; null for the expression form.
     */
    public String getLiteral() {
        return isExpression() ? null : annotation.value();
    }

    /**
     * The script text, as declared.
     *
     * @return the script; null for the literal form.
     */
    public String getScript() {
        return isExpression() ? annotation.expr() : null;
    }

    /**
     * Whether the value is an expression rather than a literal.
     *
     * @return true for the expression form.
     */
    public boolean isExpression() {
        return StringUtils.hasText(annotation.expr());
    }

    /**
     * The type the value is bound with; {@code Object} when none is declared.
     *
     * @return the type.
     */
    public Class<?> getType() {
        return annotation.type();
    }

    /**
     * The language the expression is declared in: {@code @Bind(lang)}, else the nearest
     * {@code @ExprDefaults}; null when neither names one and the manager's default applies, and for
     * the literal form.
     *
     * @return the language name, or null.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * What the binding of an executable belongs to, as declared; {@code IMPLICIT} resolves to the
     * arguments when the executable has parameters and to the return value otherwise.
     *
     * @return the declared target.
     */
    public ConstraintTarget getApplyTo() {
        return annotation.applyTo();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindDefinition that = (BindDefinition) o;
        return declarationSource.equals(that.declarationSource) && annotation.equals(that.annotation);
    }

    @Override
    public int hashCode() {
        return 31 * declarationSource.hashCode() + annotation.hashCode();
    }

    @Override
    public String toString() {
        return "@Bind[" + getBindingName() + " = " + (isExpression() ? (language != null ? language + ": " : "") + getScript()
                : "\"" + getLiteral() + "\"") + (getType() == Object.class ? "" : " as " + getType().getSimpleName())
                + "] on " + declarationSource.describe();
    }
}
