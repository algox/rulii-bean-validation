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

import org.rulii.lib.spring.util.Assert;
import org.rulii.model.SourceDefinition;
import org.rulii.model.UnrulyException;
import org.rulii.validation.beans.util.reflect.Members;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One declaration a property is read from: a field, a JavaBeans getter, or a record component.
 * A {@link PropertySource property source} groups the declarations of one property, possibly from
 * several classes of a hierarchy (a field and its getter, shadowed fields, overridden getters); their
 * constraints are cumulative and each field reads its own value.
 *
 * <p>Each declaration exposes the declaration annotations to read constraints from, the annotated
 * type to walk for container elements, the declared type, and the reflective member the value is
 * read through at validation time. For a record component the member is the public accessor; its
 * constraint annotations are gathered from the component and from its backing field, since Java
 * places an annotation only on the record elements its {@code @Target} allows (the Bean Validation
 * constraints are not applicable to {@code RECORD_COMPONENT} and land on the field instead).</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyDeclaration {

    /**
     * Kinds of property declaration.
     */
    public enum Kind {
        /** An instance field. */
        FIELD,
        /** A JavaBeans getter: zero-arg {@code getX()}, or {@code isX()} returning {@code boolean}. */
        GETTER,
        /** A record component, read through its accessor. */
        RECORD_COMPONENT
    }

    private final Kind kind;
    private final String name;
    private final Member member;
    private final AnnotatedElement declaration;
    private final List<Annotation> declaredAnnotations;
    private final AnnotatedType annotatedType;
    private final Class<?> type;
    private final Type genericType;
    private final SourceDefinition sourceDefinition;

    private PropertyDeclaration(Kind kind, String name, Member member, AnnotatedElement declaration,
                           List<Annotation> declaredAnnotations, AnnotatedType annotatedType, Class<?> type,
                           Type genericType, SourceDefinition sourceDefinition) {
        super();
        this.kind = kind;
        this.name = name;
        this.member = member;
        this.declaration = declaration;
        this.declaredAnnotations = declaredAnnotations;
        this.annotatedType = annotatedType;
        this.type = type;
        this.genericType = genericType;
        this.sourceDefinition = sourceDefinition;
    }

    /**
     * Declaration backed by a field. The property name is the field name.
     *
     * @param field the field.
     * @return the declaration.
     */
    public static PropertyDeclaration ofField(Field field) {
        Assert.notNull(field, "field cannot be null.");

        return new PropertyDeclaration(Kind.FIELD, field.getName(), field, field,
                List.of(field.getDeclaredAnnotations()), field.getAnnotatedType(), field.getType(),
                field.getGenericType(), SourceDefinition.forField(field));
    }

    /**
     * Declaration backed by a JavaBeans getter. The property name is derived from the method name
     * ({@code getName()} is {@code name}, {@code getURL()} is {@code URL}).
     *
     * @param getter the getter.
     * @return the declaration.
     * @throws IllegalArgumentException if the method is not a getter; see {@link #isGetter(Method)}.
     */
    public static PropertyDeclaration ofGetter(Method getter) {
        Assert.notNull(getter, "getter cannot be null.");
        Assert.isTrue(isGetter(getter), "method [" + Members.describe(getter) + "] is not a JavaBeans getter:"
                + " a getter is a non-static getX() with no parameters and a return value, or isX() returning boolean.");

        return new PropertyDeclaration(Kind.GETTER, propertyName(getter), getter, getter,
                List.of(getter.getDeclaredAnnotations()), getter.getAnnotatedReturnType(), getter.getReturnType(),
                getter.getGenericReturnType(), SourceDefinition.forMethod(getter));
    }

    /**
     * Declaration backed by a record component: the single source of truth for a record property.
     * Values are read through the public accessor. Annotations are gathered from the backing
     * field of the component and from the component itself, without duplicates, and the annotated
     * type is that of the backing field: on some JDKs the annotated type of the component itself
     * lacks the type-use annotations nested in its type arguments ({@code List<@NotBlank String>}).
     *
     * @param component the record component.
     * @return the declaration.
     */
    public static PropertyDeclaration ofRecordComponent(RecordComponent component) {
        Assert.notNull(component, "component cannot be null.");
        Field backing;

        try {
            backing = component.getDeclaringRecord().getDeclaredField(component.getName());
        } catch (NoSuchFieldException e) {
            throw new UnrulyException("record [" + component.getDeclaringRecord().getName()
                    + "] has no backing field for component [" + component.getName() + "]", e);
        }

        List<Annotation> annotations = new ArrayList<>(Arrays.asList(backing.getDeclaredAnnotations()));

        for (Annotation annotation : component.getDeclaredAnnotations()) {
            if (!annotations.contains(annotation)) annotations.add(annotation);
        }

        return new PropertyDeclaration(Kind.RECORD_COMPONENT, component.getName(), component.getAccessor(), component,
                Collections.unmodifiableList(annotations), backing.getAnnotatedType(), component.getType(),
                component.getGenericType(), SourceDefinition.forMethod(component.getAccessor()));
    }

    /**
     * Whether the method is a JavaBeans getter as the Bean Validation specification defines one:
     * non-static, not synthetic, no parameters, and named {@code getX()} with a non-void return
     * type or {@code isX()} with a primitive {@code boolean} return type.
     *
     * @param method the method.
     * @return true if the method is a getter.
     */
    public static boolean isGetter(Method method) {
        Assert.notNull(method, "method cannot be null.");
        if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) return false;

        String name = method.getName();

        if (name.startsWith("get") && name.length() > 3) return method.getReturnType() != void.class;
        return name.startsWith("is") && name.length() > 2 && method.getReturnType() == boolean.class;
    }

    /**
     * Property name of a getter, per the JavaBeans rule: the method name without its
     * {@code get}/{@code is} prefix, decapitalized unless it starts with two upper-case letters
     * ({@code getURL()} is {@code URL}).
     *
     * @param getter the getter.
     * @return the property name.
     */
    public static String propertyName(Method getter) {
        Assert.notNull(getter, "getter cannot be null.");
        Assert.isTrue(isGetter(getter), "method [" + Members.describe(getter) + "] is not a JavaBeans getter.");
        String name = getter.getName();
        String result = name.startsWith("get") ? name.substring(3) : name.substring(2);

        if (result.length() > 1 && Character.isUpperCase(result.charAt(0)) && Character.isUpperCase(result.charAt(1))) {
            return result;
        }

        return Character.toLowerCase(result.charAt(0)) + result.substring(1);
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * Name of the property this declaration belongs to.
     *
     * @return the property name.
     */
    public String getName() {
        return name;
    }

    /**
     * The reflective member the property value is read through: the field, the getter, or the
     * record accessor.
     *
     * @return the member.
     */
    public Member getMember() {
        return member;
    }

    public Class<?> getDeclaringClass() {
        return member.getDeclaringClass();
    }

    /**
     * The element the constraints are declared on: the field, the getter, or the record component.
     *
     * @return the declaring element.
     */
    public AnnotatedElement getDeclaration() {
        return declaration;
    }

    /**
     * Declaration annotations to read constraints from. For a record component, those of the
     * backing field followed by any present on the component only.
     *
     * @return the annotations, in declaration order.
     */
    public List<Annotation> getDeclaredAnnotations() {
        return declaredAnnotations;
    }

    /**
     * Whether an annotation of the given type is among the {@link #getDeclaredAnnotations()
     * declaration annotations}.
     *
     * @param annotationType the annotation type.
     * @return true if present.
     */
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");

        for (Annotation annotation : declaredAnnotations) {
            if (annotation.annotationType().equals(annotationType)) return true;
        }

        return false;
    }

    /**
     * The annotated declared type: the field type, the getter return type, or the component type.
     *
     * @return the annotated type.
     */
    public AnnotatedType getAnnotatedType() {
        return annotatedType;
    }

    public Class<?> getType() {
        return type;
    }

    public Type getGenericType() {
        return genericType;
    }

    /**
     * Core source descriptor of this declaration (field or method).
     *
     * @return the source definition.
     */
    public SourceDefinition getSourceDefinition() {
        return sourceDefinition;
    }

    /**
     * Human-readable description for diagnostics, e.g. {@code com.acme.Order.total} or
     * {@code com.acme.Order.getTotal()}.
     *
     * @return the description.
     */
    public String describe() {
        return Members.describe(member);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyDeclaration that = (PropertyDeclaration) o;
        return kind == that.kind && member.equals(that.member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, member);
    }

    @Override
    public String toString() {
        return kind + " " + describe();
    }
}
