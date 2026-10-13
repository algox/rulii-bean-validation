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
import jakarta.validation.Payload;
import org.rulii.lib.spring.core.annotation.AnnotationAttributes;
import org.rulii.lib.spring.core.annotation.AnnotationUtils;
import org.rulii.lib.spring.util.Assert;
import org.rulii.lib.spring.util.ReflectionUtils;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.model.UnrulyException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Definition of a single Bean Validation constraint and of the {@link ConstraintSource source} it is
 * declared at: a property declaration (field, getter or record component), a container element
 * position within its type, or a class.
 *
 * <p>A constraint is any annotation whose type is meta-annotated with {@link Constraint}.
 * The original annotation instance is kept and exposed as-is; the three attributes every
 * constraint must declare ({@code message}, {@code groups}, {@code payload}) are read off it
 * once at load time and exposed through getters.</p>
 *
 * <p>{@link #load(PropertyDeclaration)} inspects only the declaration annotations of a property;
 * annotations on its type arguments (container elements) are read per position through
 * {@link #load(PropertyDeclaration, AnnotatedElement)}; {@link #load(Class)} reads the class-level
 * constraints of one class or interface. Repeated constraints are unwrapped from their container
 * (e.g. {@code @Pattern.List}) in place, so the result keeps declaration order.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintDefinition implements Definition {

    private final ConstraintSource constraintSource;
    private final Annotation annotation;
    private final String message;
    private final List<Class<?>> groups;
    private final List<Class<? extends Payload>> payload;
    private final SourceDefinition source;

    private ConstraintDefinition(ConstraintSource constraintSource, Annotation annotation, String message,
                                 List<Class<?>> groups, List<Class<? extends Payload>> payload) {
        super();
        this.constraintSource = constraintSource;
        this.annotation = annotation;
        this.message = message;
        this.groups = groups;
        this.payload = payload;
        this.source = constraintSource.getSourceDefinition();
    }

    /**
     * Loads the constraint definitions declared on the given property declaration (its declaration
     * annotations only).
     *
     * @param sourceDeclaration the property declaration to inspect.
     * @return the constraint definitions, in declaration order; empty if none.
     */
    public static List<ConstraintDefinition> load(PropertyDeclaration sourceDeclaration) {
        Assert.notNull(sourceDeclaration, "sourceDeclaration cannot be null.");
        return load(new PropertyConstraintSource(sourceDeclaration), sourceDeclaration.getDeclaredAnnotations());
    }

    /**
     * Loads the constraint definitions declared on an element within the given property declaration:
     * an {@link java.lang.reflect.AnnotatedType} position within its type (a container element
     * such as the {@code String} in {@code List<@NotBlank String>}).
     *
     * @param sourceDeclaration the property declaration the constraints belong to.
     * @param element the element whose declared annotations are inspected.
     * @return the constraint definitions, in declaration order; empty if none.
     */
    public static List<ConstraintDefinition> load(PropertyDeclaration sourceDeclaration, AnnotatedElement element) {
        Assert.notNull(sourceDeclaration, "sourceDeclaration cannot be null.");
        Assert.notNull(element, "element cannot be null.");
        return load(new PropertyConstraintSource(sourceDeclaration), Arrays.asList(element.getDeclaredAnnotations()));
    }

    /**
     * Loads the class-level constraint definitions declared on the given class or interface (its own
     * declared annotations only; the hierarchy is walked by the bean definition).
     *
     * @param type the class or interface to inspect.
     * @return the constraint definitions, in declaration order; empty if none.
     */
    public static List<ConstraintDefinition> load(Class<?> type) {
        Assert.notNull(type, "type cannot be null.");
        return load(new ClassConstraintSource(type), Arrays.asList(type.getDeclaredAnnotations()));
    }

    private static List<ConstraintDefinition> load(ConstraintSource constraintSource, List<Annotation> annotations) {
        List<ConstraintDefinition> result = new ArrayList<>();

        for (Annotation annotation : annotations) {
            if (isConstraint(annotation)) {
                result.add(load(constraintSource, annotation));
            } else if (isConstraintContainer(annotation)) {
                for (Annotation repeated : repeatedConstraints(annotation)) {
                    result.add(load(constraintSource, repeated));
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Loads the definition of one constraint annotation declared on (or within) the given
     * property declaration.
     *
     * @param sourceDeclaration the property declaration the constraint belongs to.
     * @param annotation the constraint annotation.
     * @return the constraint definition.
     * @throws IllegalArgumentException if the annotation is not a constraint or does not
     *         declare the mandatory {@code message}, {@code groups} and {@code payload} attributes.
     */
    public static ConstraintDefinition load(PropertyDeclaration sourceDeclaration, Annotation annotation) {
        Assert.notNull(sourceDeclaration, "sourceDeclaration cannot be null.");
        return load(new PropertyConstraintSource(sourceDeclaration), annotation);
    }

    /**
     * Loads the definition of one constraint annotation declared at the given source.
     *
     * @param constraintSource where the constraint is declared.
     * @param annotation the constraint annotation.
     * @return the constraint definition.
     * @throws IllegalArgumentException if the annotation is not a constraint or does not
     *         declare the mandatory {@code message}, {@code groups} and {@code payload} attributes.
     */
    @SuppressWarnings("unchecked")
    public static ConstraintDefinition load(ConstraintSource constraintSource, Annotation annotation) {
        Assert.notNull(constraintSource, "constraintSource cannot be null.");
        Assert.notNull(annotation, "annotation cannot be null.");
        Assert.isTrue(isConstraint(annotation), "annotation [" + annotation.annotationType().getName()
                + "] is not a constraint; it must be meta-annotated with @" + Constraint.class.getName());

        AnnotationAttributes attributes = AnnotationUtils.getAnnotationAttributes(constraintSource.getElement(), annotation);
        String message = (String) attribute(annotation, attributes, "message", String.class);
        Class<?>[] groups = (Class<?>[]) attribute(annotation, attributes, "groups", Class[].class);
        Class<? extends Payload>[] payload = (Class<? extends Payload>[]) attribute(annotation, attributes, "payload", Class[].class);

        return new ConstraintDefinition(constraintSource, annotation, message,
                Collections.unmodifiableList(Arrays.asList(groups)),
                Collections.unmodifiableList(Arrays.asList(payload)));
    }

    /**
     * Determines whether the given annotation is a Bean Validation constraint.
     *
     * @param annotation the annotation to check.
     * @return true if the annotation's type is meta-annotated with {@link Constraint}.
     */
    public static boolean isConstraint(Annotation annotation) {
        Assert.notNull(annotation, "annotation cannot be null.");
        return annotation.annotationType().isAnnotationPresent(Constraint.class);
    }

    /**
     * Determines whether the given annotation is a container of repeated constraints, as the
     * Bean Validation specification defines one: an annotation whose {@code value()} attribute
     * returns an array of a constraint annotation type (e.g. {@code @Pattern.List}). The container
     * does not need to be linked through {@link java.lang.annotation.Repeatable}.
     *
     * @param annotation the annotation to check.
     * @return true if the annotation is a constraint container.
     */
    public static boolean isConstraintContainer(Annotation annotation) {
        Assert.notNull(annotation, "annotation cannot be null.");
        Method value = valueMethod(annotation.annotationType());
        if (value == null) return false;
        Class<?> returnType = value.getReturnType();
        return returnType.isArray()
                && returnType.getComponentType().isAnnotation()
                && returnType.getComponentType().isAnnotationPresent(Constraint.class);
    }

    private static Annotation[] repeatedConstraints(Annotation container) {
        Method value = valueMethod(container.annotationType());
        // The container may be declared in a non-public type; the annotation is on the bean, so reading it is legitimate.
        ReflectionUtils.makeAccessible(value);

        try {
            return (Annotation[]) value.invoke(container);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new UnrulyException("unable to read the repeated constraints of container ["
                    + container.annotationType().getName() + "]", e);
        }
    }

    private static Method valueMethod(Class<? extends Annotation> type) {
        try {
            return type.getMethod("value");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object attribute(Annotation annotation, AnnotationAttributes attributes, String name, Class<?> expectedType) {
        Class<? extends Annotation> type = annotation.annotationType();
        Assert.isTrue(attributes.containsKey(name), "constraint [" + type.getName()
                + "] does not declare the mandatory [" + name + "] attribute.");

        Object result = attributes.get(name);
        Assert.isTrue(expectedType.isInstance(result), "constraint [" + type.getName() + "] attribute [" + name
                + "] must be of type [" + expectedType.getSimpleName() + "] but is ["
                + (result == null ? "null" : result.getClass().getSimpleName()) + "].");

        return result;
    }

    /**
     * Simple name of the constraint annotation type (e.g. {@code NotNull}).
     *
     * @return the constraint name.
     */
    @Override
    public String getName() {
        return annotation.annotationType().getSimpleName();
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    /**
     * Where this constraint is declared: the property declaration it is declared on (or whose type
     * declares the position it is declared at), or the class for a class-level constraint.
     *
     * @return the constraint source.
     */
    public ConstraintSource getConstraintSource() {
        return constraintSource;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotation.annotationType();
    }

    public String getMessage() {
        return message;
    }

    public List<Class<?>> getGroups() {
        return groups;
    }

    public List<Class<? extends Payload>> getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstraintDefinition that = (ConstraintDefinition) o;
        return constraintSource.equals(that.constraintSource) && annotation.equals(that.annotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constraintSource, annotation);
    }

    @Override
    public String toString() {
        return "ConstraintDefinition{" +
                "source=" + constraintSource.describe() +
                ", annotation=" + annotation +
                ", message='" + message + '\'' +
                ", groups=" + groups +
                ", payload=" + payload +
                '}';
    }
}
