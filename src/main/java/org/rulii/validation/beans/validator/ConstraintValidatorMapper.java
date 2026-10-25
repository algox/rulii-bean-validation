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
package org.rulii.validation.beans.validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.constraintvalidation.ValidationTarget;
import org.rulii.lib.spring.core.annotation.AnnotationUtils;
import org.rulii.lib.spring.util.Assert;
import org.rulii.lib.spring.util.ClassUtils;
import org.rulii.rule.Rule;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintTarget;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.util.reflect.TypeResolver;
import org.rulii.validation.beans.util.reflect.Types;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link ConstraintMapper} of a constraint backed by {@link ConstraintValidator} classes, the ones
 * named in its {@code @Constraint(validatedBy)}. The registry synthesizes one for a constraint that has
 * no registered mapper, so a constraint written for the reference implementation runs unchanged.
 *
 * <p>Applicability follows the specification: of the validator classes supporting the validation
 * target (the annotated element, or the parameters for a cross-parameter constraint, per their
 * {@code @SupportedValidationTarget}), those whose validated type (the {@code T} of
 * {@code ConstraintValidator<A, T>}) is assignable from the declared type of the constrained value are
 * candidates, and the most specific of them is chosen; none makes the constraint inapplicable to the
 * type, several that are equally specific are an {@link AmbiguousConstraintValidatorException}.
 * Primitives are considered through their wrapper types.</p>
 *
 * <p>Materialization creates one validator instance per constraint occurrence through the
 * {@link ConstraintValidatorFactory}, calls {@code initialize} with the annotation once, and builds a
 * {@link ConstraintValidatorRule} around it that runs on null (the validator decides) and reports the
 * constraint's attributes as parameters. The instances created are kept so {@link #release()} can hand
 * them back to the factory when the validator factory closes.</p>
 *
 * @param <A> the constraint annotation type.
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintValidatorMapper<A extends Annotation> implements ConstraintMapper<A> {

    private static final Set<String> COMMON_ATTRIBUTES = Set.of("message", "groups", "payload");

    private final Class<A> annotationType;
    private final List<Class<? extends ConstraintValidator<?, ?>>> validatorClasses;
    private final List<Class<?>> supportedTypes;
    private final List<ValidationTarget> supportedTargets;
    private final ConstraintValidatorFactory factory;
    private final List<ConstraintValidator<?, ?>> instances = new ArrayList<>();

    /**
     * Creates the mapper for a constraint that declares validators.
     *
     * @param annotationType the constraint annotation type; must be meta-annotated with {@link Constraint}
     *                       and name at least one validator.
     * @param factory the factory the validators are created through.
     */
    public ConstraintValidatorMapper(Class<A> annotationType, ConstraintValidatorFactory factory) {
        super();
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(factory, "factory cannot be null.");
        Assert.isTrue(declaresValidators(annotationType), "constraint [" + annotationType.getName()
                + "] does not declare validators; it must be meta-annotated with @Constraint(validatedBy = ...).");
        this.annotationType = annotationType;
        this.validatorClasses = List.of(annotationType.getAnnotation(Constraint.class).validatedBy());
        this.factory = factory;
        Set<Class<?>> types = new LinkedHashSet<>();
        Set<ValidationTarget> targets = EnumSet.noneOf(ValidationTarget.class);

        for (Class<? extends ConstraintValidator<?, ?>> validatorClass : validatorClasses) {
            types.add(validatedType(validatorClass));
            targets.addAll(ExecutableDefinition.validatorTargets(validatorClass));
        }

        this.supportedTypes = List.copyOf(types);
        this.supportedTargets = List.copyOf(targets);
    }

    /**
     * Whether a constraint annotation type names validators.
     *
     * @param annotationType the annotation type.
     * @return true if its {@code @Constraint(validatedBy)} is not empty.
     */
    public static boolean declaresValidators(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Constraint constraint = annotationType.getAnnotation(Constraint.class);
        return constraint != null && constraint.validatedBy().length > 0;
    }

    /**
     * The type a validator class validates: the second type argument of the {@link ConstraintValidator}
     * interface as the class implements it, erased; {@code Object} for a raw implementation.
     *
     * @param validatorClass the validator class.
     * @return the validated type.
     */
    public static Class<?> validatedType(Class<? extends ConstraintValidator<?, ?>> validatorClass) {
        Assert.notNull(validatorClass, "validatorClass cannot be null.");
        Type type = TypeResolver.supertype(validatorClass, ConstraintValidator.class);
        if (type instanceof ParameterizedType parameterized) return Types.erasure(parameterized.getActualTypeArguments()[1]);
        return Object.class;
    }

    @Override
    public Class<A> getAnnotationType() {
        return annotationType;
    }

    /**
     * The validator classes the constraint names, in declaration order.
     *
     * @return the classes; unmodifiable.
     */
    public List<Class<? extends ConstraintValidator<?, ?>>> getValidatorClasses() {
        return validatorClasses;
    }

    /**
     * The types the validators validate, one per validator class, duplicates removed.
     */
    @Override
    public List<Class<?>> getSupportedTypes() {
        return supportedTypes;
    }

    /**
     * The validation targets the validators support, as their {@code @SupportedValidationTarget} declare.
     */
    @Override
    public List<ValidationTarget> getSupportedTargets() {
        return supportedTargets;
    }

    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return factory;
    }

    /**
     * Whether one of the validators of the annotated element validates the type: {@link #select(Class)}
     * finds one.
     *
     * @throws AmbiguousConstraintValidatorException if several do and none is the most specific.
     */
    @Override
    public boolean isApplicable(Class<?> type) {
        return isApplicable(type, ValidationTarget.ANNOTATED_ELEMENT);
    }

    /**
     * Whether one of the validators of the target validates the type: {@link #select(Class, ValidationTarget)}
     * finds one.
     *
     * @throws AmbiguousConstraintValidatorException if several do and none is the most specific.
     */
    @Override
    public boolean isApplicable(Class<?> type, ValidationTarget target) {
        return select(type, target) != null;
    }

    /**
     * The validator class for a declared type of the annotated element.
     *
     * @param type the declared type of the constrained value.
     * @return the class; null if no validator validates the type.
     * @throws AmbiguousConstraintValidatorException if several do and none is the most specific.
     * @see #select(Class, ValidationTarget)
     */
    public Class<? extends ConstraintValidator<?, ?>> select(Class<?> type) {
        return select(type, ValidationTarget.ANNOTATED_ELEMENT);
    }

    /**
     * The validator class for a declared type and a validation target: of the classes supporting the
     * target whose validated type is assignable from the type, the most specific.
     *
     * @param type the declared type of the constrained value; {@code Object[]} for the parameters.
     * @param target the validation target.
     * @return the class; null if no validator validates the type for the target.
     * @throws AmbiguousConstraintValidatorException if several do and none is the most specific.
     */
    public Class<? extends ConstraintValidator<?, ?>> select(Class<?> type, ValidationTarget target) {
        Assert.notNull(type, "type cannot be null.");
        Assert.notNull(target, "target cannot be null.");
        Class<?> declared = ClassUtils.resolvePrimitiveIfNecessary(type);
        List<Class<? extends ConstraintValidator<?, ?>>> candidates = new ArrayList<>();

        for (Class<? extends ConstraintValidator<?, ?>> validatorClass : validatorClasses) {
            if (!ExecutableDefinition.validatorTargets(validatorClass).contains(target)) continue;
            if (validatedType(validatorClass).isAssignableFrom(declared)) candidates.add(validatorClass);
        }

        if (candidates.isEmpty()) return null;

        List<Class<? extends ConstraintValidator<?, ?>>> mostSpecific = new ArrayList<>();

        for (Class<? extends ConstraintValidator<?, ?>> candidate : candidates) {
            if (!isLessSpecific(candidate, candidates)) mostSpecific.add(candidate);
        }

        if (mostSpecific.size() != 1) throw new AmbiguousConstraintValidatorException(annotationType, type, mostSpecific);

        return mostSpecific.get(0);
    }

    /**
     * Whether another candidate validates a strict subtype of this candidate's type.
     */
    private static boolean isLessSpecific(Class<? extends ConstraintValidator<?, ?>> candidate,
                                          List<Class<? extends ConstraintValidator<?, ?>>> candidates) {
        Class<?> type = validatedType(candidate);

        for (Class<? extends ConstraintValidator<?, ?>> other : candidates) {
            Class<?> otherType = validatedType(other);
            if (otherType != type && type.isAssignableFrom(otherType)) return true;
        }

        return false;
    }

    @Override
    public List<Rule> map(A annotation, ConstraintTarget target) {
        Assert.notNull(annotation, "annotation cannot be null.");
        Assert.notNull(target, "target cannot be null.");
        Class<? extends ConstraintValidator<?, ?>> validatorClass = select(target.getType(), target.getValidationTarget());
        Assert.notNull(validatorClass, "no validator of constraint [" + annotationType.getName() + "] validates type ["
                + target.getType().getName() + "] for target [" + target.getValidationTarget() + "]; validators: " + validatorClasses);

        ConstraintValidator<?, ?> validator = create(validatorClass, annotation);
        Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
        String message = String.valueOf(attributes.get("message"));
        attributes.keySet().removeAll(COMMON_ATTRIBUTES);

        return List.of(target.configure(ConstraintValidatorRule.builder(target.value(), validator, annotationType))
                .defaultMessageTemplate(message)
                .parameters(attributes)
                .build());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConstraintValidator<?, ?> create(Class<? extends ConstraintValidator<?, ?>> validatorClass, A annotation) {
        ConstraintValidator validator = factory.getInstance(validatorClass);
        if (validator == null) throw new ConstraintValidatorException(validatorClass, "was not provided by factory [" + factory + "]");

        try {
            validator.initialize(annotation);
        } catch (RuntimeException e) {
            throw new ConstraintValidatorException(validatorClass, "failed to initialize for [" + annotation + "]", e);
        }

        synchronized (instances) {
            instances.add(validator);
        }

        return validator;
    }

    /**
     * The validator instances created so far.
     *
     * @return the instances; a snapshot.
     */
    public List<ConstraintValidator<?, ?>> getInstances() {
        synchronized (instances) {
            return Collections.unmodifiableList(new ArrayList<>(instances));
        }
    }

    /**
     * Hands every validator instance created so far back to the factory.
     */
    public void release() {
        List<ConstraintValidator<?, ?>> released;

        synchronized (instances) {
            released = new ArrayList<>(instances);
            instances.clear();
        }

        for (ConstraintValidator<?, ?> instance : released) {
            factory.releaseInstance(instance);
        }
    }

    @Override
    public String toString() {
        return "ConstraintValidatorMapper{" + annotationType.getSimpleName() + " -> " + validatorClasses + "}";
    }
}
