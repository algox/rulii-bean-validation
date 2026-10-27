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
import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;
import org.rulii.lib.spring.core.annotation.AnnotationUtils;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation definition of one method or constructor: its {@link ParameterDefinition parameters},
 * its {@link ReturnValueDefinition return value} (none for a {@code void} method; the created object
 * for a constructor) and its cross-parameter {@link ConstraintDefinition constraints}, those that
 * validate the parameters as a whole.
 *
 * <p>A method is defined together with the methods it overrides or implements, found by signature
 * across the hierarchy. The specification's inheritance rules are enforced here, at read time: a
 * method overriding or implementing another may not declare parameter or cross-parameter constraints
 * of its own (the reference implementation tolerates declaring exactly the same ones, so does this
 * library), methods of parallel types may not both declare them, while return-value constraints
 * accumulate over the chain and {@code @Valid} anywhere in it cascades the return value. A
 * {@code void} method may not constrain or cascade its return value.</p>
 *
 * <p>A {@code @Validate} on the executable attaches registry rules, and an {@code @Expr} a script
 * condition, to its parameters as a whole or to its return value, as its {@code applyTo} says:
 * {@code IMPLICIT} means the parameters when there are any, the return value otherwise. Rules for the
 * parameters follow the inheritance rules of parameter constraints; rules for the return value
 * accumulate over the chain like return-value constraints.</p>
 *
 * <p>A constraint on the method itself is a return-value constraint unless it is a cross-parameter
 * one. The constraint says so with {@code validationAppliesTo}; when it does not (the
 * {@code IMPLICIT} default), the validation targets its validator classes declare decide: parameters
 * only, the annotated element only, or both, in which case the method must have either parameters or
 * a return value but not both ({@link AmbiguousConstraintTargetException} otherwise). A constraint
 * mapped by a mapper rather than validators is a return-value constraint unless it declares
 * {@code validationAppliesTo = PARAMETERS}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ExecutableDefinition implements Definition {

    /**
     * Kinds of executable.
     */
    public enum Kind {
        /** A method that is not a JavaBeans getter. */
        METHOD,
        /** A JavaBeans getter, whose return-value constraints are also property constraints. */
        GETTER,
        /** A constructor, whose return value is the object it creates. */
        CONSTRUCTOR
    }

    private static final String VALIDATION_APPLIES_TO = "validationAppliesTo";

    private final Kind kind;
    private final Executable executable;
    private final Class<?> owner;
    private final List<Executable> declarations;
    private final List<ParameterDefinition> parameters;
    private final ReturnValueDefinition returnValue;
    private final List<ConstraintDefinition> crossParameterConstraints;
    private final List<ValidateDefinition> validateRules;
    private final List<ExprDefinition> exprRules;
    private final SourceDefinition source;

    private ExecutableDefinition(Kind kind, Executable executable, Class<?> owner, List<Executable> declarations,
                                 List<ParameterDefinition> parameters, ReturnValueDefinition returnValue,
                                 List<ConstraintDefinition> crossParameterConstraints, List<ValidateDefinition> validateRules,
                                 List<ExprDefinition> exprRules) {
        super();
        this.kind = kind;
        this.executable = executable;
        this.owner = owner;
        this.declarations = declarations;
        this.parameters = parameters;
        this.returnValue = returnValue;
        this.crossParameterConstraints = crossParameterConstraints;
        this.validateRules = validateRules;
        this.exprRules = exprRules;
        this.source = executable instanceof Constructor<?> constructor ? SourceDefinition.forConstructor(constructor)
                : SourceDefinition.forMethod((Method) executable);
    }

    /**
     * Loads the definition of a method from the methods of its overriding chain.
     *
     * @param owner the bean class the methods are read for.
     * @param chain the method as declared on the owner's hierarchy: one method per type declaring it,
     *              in any order; all of one signature.
     * @param registry the value extractors available at validation time.
     * @param names the parameter name provider.
     * @return the definition.
     * @throws IllegalExecutableConstraintException if the chain breaks an inheritance rule, a
     *         {@code void} method constrains its return value, or a {@code @Validate} names a target the
     *         method does not have.
     * @throws AmbiguousConstraintTargetException if a constraint could validate parameters or return
     *         value and the method has both.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared on a parameter
     *         or the return value.
     */
    public static ExecutableDefinition load(Class<?> owner, List<Method> chain, ValueExtractorRegistry registry,
                                            ParameterNameProvider names) {
        Assert.notNull(owner, "owner cannot be null.");
        Assert.notEmpty(chain, "chain cannot be empty.");
        Assert.notNull(registry, "registry cannot be null.");
        Assert.notNull(names, "names cannot be null.");
        Method primary = primary(owner, chain);
        List<Executable> declarations = new ArrayList<>(chain.size());
        declarations.add(primary);

        for (Method method : chain) {
            if (method != primary) declarations.add(method);
        }

        Kind kind = ValueDeclaration.isGetter(primary) ? Kind.GETTER : Kind.METHOD;
        return load(kind, primary, owner, declarations, names.getParameterNames(primary), registry);
    }

    /**
     * Loads the definition of a constructor.
     *
     * @param constructor the constructor.
     * @param registry the value extractors available at validation time.
     * @param names the parameter name provider.
     * @return the definition.
     */
    public static ExecutableDefinition load(Constructor<?> constructor, ValueExtractorRegistry registry, ParameterNameProvider names) {
        Assert.notNull(constructor, "constructor cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        Assert.notNull(names, "names cannot be null.");
        return load(Kind.CONSTRUCTOR, constructor, constructor.getDeclaringClass(), List.of(constructor),
                names.getParameterNames(constructor), registry);
    }

    /**
     * The method of the chain declared lowest in the hierarchy: the owner's own, else the one every
     * other declaring type is a supertype of, else the first class-declared one.
     */
    private static Method primary(Class<?> owner, List<Method> chain) {
        for (Method method : chain) {
            if (method.getDeclaringClass() == owner) return method;
        }

        for (Method method : chain) {
            boolean lowest = true;

            for (Method other : chain) {
                if (!other.getDeclaringClass().isAssignableFrom(method.getDeclaringClass())) lowest = false;
            }

            if (lowest) return method;
        }

        for (Method method : chain) {
            if (!method.getDeclaringClass().isInterface()) return method;
        }

        return chain.get(0);
    }

    private static ExecutableDefinition load(Kind kind, Executable primary, Class<?> owner, List<Executable> declarations,
                                             List<String> names, ValueExtractorRegistry registry) {
        Assert.isTrue(names != null && names.size() == primary.getParameterCount(), "the parameter name provider gave "
                + (names == null ? "no names" : names.size() + " names") + " for the " + primary.getParameterCount()
                + " parameters of [" + describe(primary) + "]");
        List<Read> reads = new ArrayList<>(declarations.size());

        for (Executable declaration : declarations) {
            reads.add(Read.of(declaration, owner, names, registry));
        }

        checkInheritance(primary, reads);
        Read constrainedRoot = constrainedRoot(reads);
        Read primaryRead = reads.get(0);
        List<ParameterDefinition> parameters = new ArrayList<>(names.size());

        for (int i = 0; i < names.size(); i++) {
            // The constraints come from the highest constrained declaration; a subtype repeating them adds nothing.
            Read source = constrainedRoot != null ? constrainedRoot : primaryRead;
            parameters.add(ParameterDefinition.load(List.of(source.parameters.get(i)), registry));
        }

        List<ConstraintDefinition> crossParameter = List.of();
        List<ValidateDefinition> validateRules = new ArrayList<>();
        List<ExprDefinition> exprRules = new ArrayList<>();

        if (constrainedRoot != null) {
            CrossParameterConstraintSource source = new CrossParameterConstraintSource(constrainedRoot.executable, owner);
            crossParameter = ConstraintDefinition.load(source, constrainedRoot.crossParameterAnnotations);

            for (Annotation annotation : constrainedRoot.parameterValidateAnnotations) {
                validateRules.add(ValidateDefinition.load(source, (Validate) annotation));
            }

            ValidateDefinition.checkDistinct(validateRules, "the parameters of [" + describe(primary) + "]");

            for (Annotation annotation : constrainedRoot.parameterExprAnnotations) {
                exprRules.add(ExprDefinition.load(source, (Expr) annotation));
            }
        }

        ReturnValueDefinition returnValue = null;

        if (hasReturnValue(primary)) {
            List<ValueDeclaration> returnDeclarations = new ArrayList<>(reads.size());

            for (Read read : reads) {
                returnDeclarations.add(read.returnValue);
            }

            returnValue = ReturnValueDefinition.load(returnDeclarations, registry);
        }

        return new ExecutableDefinition(kind, primary, owner, Collections.unmodifiableList(declarations),
                Collections.unmodifiableList(parameters), returnValue, crossParameter, Collections.unmodifiableList(validateRules),
                Collections.unmodifiableList(exprRules));
    }

    /**
     * Enforces the inheritance rules: a method declared on a subtype of another chain member's type
     * may declare parameter or cross-parameter constraints only when they equal that member's; two
     * chain members of unrelated types may not both declare them.
     */
    private static void checkInheritance(Executable primary, List<Read> reads) {
        for (Read read : reads) {
            if (!read.isParameterConstrained()) continue;

            for (Read other : reads) {
                if (other == read) continue;
                Class<?> type = read.executable.getDeclaringClass();
                Class<?> otherType = other.executable.getDeclaringClass();

                if (otherType.isAssignableFrom(type)) {
                    if (!read.isEquallyParameterConstrained(other)) {
                        throw new IllegalExecutableConstraintException(read.executable, "overrides or implements ["
                                + describe(other.executable) + "] and must not declare parameter or cross-parameter constraints"
                                + " of its own; the specification allows them on the overridden method only.");
                    }
                } else if (!type.isAssignableFrom(otherType) && other.isParameterConstrained()) {
                    throw new IllegalExecutableConstraintException(primary, "is declared in the parallel types ["
                            + type.getName() + "] and [" + otherType.getName() + "], which both declare parameter or"
                            + " cross-parameter constraints; the specification allows them in one of the types only.");
                }
            }
        }
    }

    /**
     * The chain member whose parameter and cross-parameter constraints apply: the highest constrained
     * one (the others, if any, repeat it). Null when none is constrained.
     */
    private static Read constrainedRoot(List<Read> reads) {
        Read result = null;

        for (Read read : reads) {
            if (!read.isParameterConstrained()) continue;
            if (result == null || read.executable.getDeclaringClass().isAssignableFrom(result.executable.getDeclaringClass())) {
                result = read;
            }
        }

        return result;
    }

    private static boolean hasReturnValue(Executable executable) {
        return executable instanceof Constructor<?> || ((Method) executable).getReturnType() != void.class;
    }

    private static String describe(Executable executable) {
        return org.rulii.validation.beans.util.reflect.Members.describe(executable);
    }

    /**
     * Whether a constraint declared on an executable validates its parameters as a whole, per the
     * specification's resolution of {@code validationAppliesTo}.
     *
     * @param annotation the constraint annotation.
     * @param executable the method or constructor it is declared on.
     * @return true for a cross-parameter constraint, false for a return-value constraint.
     * @throws AmbiguousConstraintTargetException if either is possible and the executable has both
     *         parameters and a return value.
     * @throws IllegalExecutableConstraintException if the resolved target does not exist on the executable.
     */
    public static boolean isCrossParameter(Annotation annotation, Executable executable) {
        Assert.notNull(annotation, "annotation cannot be null.");
        Assert.notNull(executable, "executable cannot be null.");
        Class<? extends Annotation> type = annotation.annotationType();
        boolean hasParameters = executable.getParameterCount() > 0;
        boolean hasReturnValue = hasReturnValue(executable);
        Object declared = AnnotationUtils.getValue(annotation, VALIDATION_APPLIES_TO);
        ConstraintTarget applies = declared instanceof ConstraintTarget target ? target : ConstraintTarget.IMPLICIT;

        if (applies == ConstraintTarget.PARAMETERS) {
            if (!hasParameters) {
                throw new IllegalExecutableConstraintException(executable, "declares the cross-parameter constraint [" + type.getName()
                        + "] but has no parameters.");
            }
            return true;
        }

        if (applies == ConstraintTarget.RETURN_VALUE) {
            if (!hasReturnValue) {
                throw new IllegalExecutableConstraintException(executable, "declares the return-value constraint [" + type.getName()
                        + "] but returns void.");
            }
            return false;
        }

        Set<ValidationTarget> targets = validationTargets(type);

        if (!targets.contains(ValidationTarget.PARAMETERS)) return false;

        if (targets.contains(ValidationTarget.ANNOTATED_ELEMENT)) {
            if (hasParameters && hasReturnValue) throw new AmbiguousConstraintTargetException(executable, type);
            if (!hasParameters && !hasReturnValue) {
                throw new IllegalExecutableConstraintException(executable, "declares constraint [" + type.getName()
                        + "] but has neither parameters nor a return value to validate.");
            }
            return hasParameters;
        }

        if (!hasParameters) {
            throw new IllegalExecutableConstraintException(executable, "declares the cross-parameter constraint [" + type.getName()
                    + "] but has no parameters.");
        }

        return true;
    }

    /**
     * Whether a {@code @Validate} declared on an executable attaches to its parameters as a whole, per
     * its {@code applyTo}: {@code IMPLICIT} means the parameters when the executable has any, the
     * return value otherwise.
     *
     * @param annotation the annotation.
     * @param executable the method or constructor it is declared on.
     * @return true for the parameters, false for the return value.
     * @throws IllegalExecutableConstraintException if the resolved target does not exist on the executable.
     */
    public static boolean validatesParameters(Validate annotation, Executable executable) {
        Assert.notNull(annotation, "annotation cannot be null.");
        return validatesParameters(annotation.applyTo(), "@Validate", executable);
    }

    /**
     * Whether an {@code @Expr} declared on an executable attaches to its parameters as a whole, per
     * its {@code applyTo}, with the rules of {@link #validatesParameters(Validate, Executable)}.
     *
     * @param annotation the annotation.
     * @param executable the method or constructor it is declared on.
     * @return true for the parameters, false for the return value.
     * @throws IllegalExecutableConstraintException if the resolved target does not exist on the executable.
     */
    public static boolean validatesParameters(Expr annotation, Executable executable) {
        Assert.notNull(annotation, "annotation cannot be null.");
        return validatesParameters(annotation.applyTo(), "@Expr", executable);
    }

    private static boolean validatesParameters(ConstraintTarget applies, String annotationName, Executable executable) {
        Assert.notNull(executable, "executable cannot be null.");
        boolean hasParameters = executable.getParameterCount() > 0;
        boolean hasReturnValue = hasReturnValue(executable);

        if (applies == ConstraintTarget.PARAMETERS) {
            if (!hasParameters) {
                throw new IllegalExecutableConstraintException(executable, "declares " + annotationName + "(applyTo = PARAMETERS) but has no parameters.");
            }
            return true;
        }

        if (applies == ConstraintTarget.RETURN_VALUE) {
            if (!hasReturnValue) {
                throw new IllegalExecutableConstraintException(executable, "declares " + annotationName + "(applyTo = RETURN_VALUE) but returns void.");
            }
            return false;
        }

        if (!hasParameters && !hasReturnValue) {
            throw new IllegalExecutableConstraintException(executable, "carries " + annotationName + " but has neither parameters nor a return value to validate.");
        }

        return hasParameters;
    }

    /**
     * The validation targets of a constraint's validators: what each class in
     * {@code @Constraint(validatedBy)} declares with {@code @SupportedValidationTarget}, the annotated
     * element when it declares nothing; the annotated element alone for a constraint without validators.
     *
     * @param annotationType the constraint annotation type.
     * @return the targets.
     */
    public static Set<ValidationTarget> validationTargets(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Constraint constraint = annotationType.getAnnotation(Constraint.class);
        Set<ValidationTarget> result = EnumSet.noneOf(ValidationTarget.class);

        if (constraint == null || constraint.validatedBy().length == 0) {
            result.add(ValidationTarget.ANNOTATED_ELEMENT);
            return result;
        }

        for (Class<? extends ConstraintValidator<?, ?>> validatorClass : constraint.validatedBy()) {
            result.addAll(validatorTargets(validatorClass));
        }

        return result;
    }

    /**
     * The validation targets of a validator class: its {@code @SupportedValidationTarget}, the
     * annotated element when it declares none.
     *
     * @param validatorClass the validator class.
     * @return the targets.
     */
    public static Set<ValidationTarget> validatorTargets(Class<? extends ConstraintValidator<?, ?>> validatorClass) {
        Assert.notNull(validatorClass, "validatorClass cannot be null.");
        SupportedValidationTarget supported = validatorClass.getAnnotation(SupportedValidationTarget.class);
        Set<ValidationTarget> result = EnumSet.noneOf(ValidationTarget.class);

        if (supported == null || supported.value().length == 0) {
            result.add(ValidationTarget.ANNOTATED_ELEMENT);
        } else {
            result.addAll(Arrays.asList(supported.value()));
        }

        return result;
    }

    /**
     * The method name, or the simple class name for a constructor: the name of the path node.
     */
    @Override
    public String getName() {
        return executable instanceof Constructor<?> ? executable.getDeclaringClass().getSimpleName() : executable.getName();
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isConstructor() {
        return kind == Kind.CONSTRUCTOR;
    }

    public boolean isGetter() {
        return kind == Kind.GETTER;
    }

    /**
     * The executable as declared lowest in the owner's hierarchy: the one validation is requested for.
     *
     * @return the method or constructor.
     */
    public Executable getExecutable() {
        return executable;
    }

    /**
     * The bean class the executable is read for.
     *
     * @return the owner.
     */
    public Class<?> getOwner() {
        return owner;
    }

    /**
     * The executable and the methods it overrides or implements, the executable first.
     *
     * @return the declarations.
     */
    public List<Executable> getDeclarations() {
        return declarations;
    }

    public Class<?>[] getParameterTypes() {
        return executable.getParameterTypes();
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    public ParameterDefinition getParameter(int index) {
        Assert.isTrue(index >= 0 && index < parameters.size(), "parameter index [" + index + "] is out of range for ["
                + describe(executable) + "]");
        return parameters.get(index);
    }

    /**
     * The return value.
     *
     * @return the definition; null for a {@code void} method.
     */
    public ReturnValueDefinition getReturnValue() {
        return returnValue;
    }

    public boolean hasReturnValue() {
        return returnValue != null;
    }

    /**
     * The constraints validating the parameters as a whole.
     *
     * @return the cross-parameter constraints; empty if none.
     */
    public List<ConstraintDefinition> getCrossParameterConstraints() {
        return crossParameterConstraints;
    }

    /**
     * The {@code @Validate} rules attached to the parameters as a whole, in declaration order.
     *
     * @return the rules; empty if none.
     */
    public List<ValidateDefinition> getValidateRules() {
        return validateRules;
    }

    /**
     * The {@code @Expr} conditions attached to the parameters as a whole, in declaration order.
     *
     * @return the conditions; empty if none.
     */
    public List<ExprDefinition> getExprRules() {
        return exprRules;
    }

    /**
     * Whether any parameter is constrained or cascaded, or a cross-parameter constraint, a
     * {@code @Validate} or an {@code @Expr} for the parameters is declared.
     *
     * @return true if parameter validation has anything to do.
     */
    public boolean hasConstrainedParameters() {
        if (!crossParameterConstraints.isEmpty() || !validateRules.isEmpty() || !exprRules.isEmpty()) return true;

        for (ParameterDefinition parameter : parameters) {
            if (parameter.isConstrained()) return true;
        }

        return false;
    }

    /**
     * Whether the return value is constrained or cascaded.
     *
     * @return true if return-value validation has anything to do.
     */
    public boolean hasConstrainedReturnValue() {
        return returnValue != null && returnValue.isConstrained();
    }

    public boolean isConstrained() {
        return hasConstrainedParameters() || hasConstrainedReturnValue();
    }

    /**
     * Whether this definition is the one of the given signature.
     *
     * @param name the method name, or the simple class name for a constructor.
     * @param parameterTypes the parameter types.
     * @return true if name and parameter types match.
     */
    public boolean matches(String name, Class<?>[] parameterTypes) {
        return getName().equals(name) && Arrays.equals(executable.getParameterTypes(), parameterTypes);
    }

    @Override
    public String toString() {
        return "ExecutableDefinition{" + kind + " " + describe(executable) + ", parameters=" + parameters.size()
                + ", returnValue=" + (returnValue != null) + ", crossParameter=" + crossParameterConstraints.size()
                + ", validateRules=" + validateRules.size() + ", exprRules=" + exprRules.size() + '}';
    }

    /**
     * One executable of the chain, read: its parameter declarations, its return-value declaration (with
     * the cross-parameter constraints and the {@code @Validate} and {@code @Expr} for the parameters
     * taken out of its annotations), the cross-parameter annotations and the parameter-side
     * {@code @Validate} and {@code @Expr} annotations.
     */
    private static final class Read {

        private final Executable executable;
        private final List<ValueDeclaration> parameters;
        private final ValueDeclaration returnValue;
        private final List<Annotation> crossParameterAnnotations;
        private final List<Annotation> parameterValidateAnnotations;
        private final List<Annotation> parameterExprAnnotations;
        private final boolean parameterConstrained;

        private Read(Executable executable, List<ValueDeclaration> parameters, ValueDeclaration returnValue,
                     List<Annotation> crossParameterAnnotations, List<Annotation> parameterValidateAnnotations,
                     List<Annotation> parameterExprAnnotations, boolean parameterConstrained) {
            super();
            this.executable = executable;
            this.parameters = parameters;
            this.returnValue = returnValue;
            this.crossParameterAnnotations = crossParameterAnnotations;
            this.parameterValidateAnnotations = parameterValidateAnnotations;
            this.parameterExprAnnotations = parameterExprAnnotations;
            this.parameterConstrained = parameterConstrained;
        }

        private static Read of(Executable executable, Class<?> owner, List<String> names, ValueExtractorRegistry registry) {
            List<ValueDeclaration> parameters = new ArrayList<>(names.size());
            boolean constrained = false;

            for (int i = 0; i < names.size(); i++) {
                ValueDeclaration parameter = ValueDeclaration.ofParameter(executable, i, names.get(i), owner);
                if (DeclarationReader.read(parameter, registry).isConstrained()) constrained = true;
                parameters.add(parameter);
            }

            List<Annotation> crossParameter = new ArrayList<>();
            List<Annotation> parameterValidate = new ArrayList<>();
            List<Annotation> parameterExpr = new ArrayList<>();
            List<Annotation> returnValue = new ArrayList<>();

            for (Annotation annotation : executable.getDeclaredAnnotations()) {
                if (ConstraintDefinition.isConstraintContainer(annotation)) {
                    for (Annotation repeated : ConstraintDefinition.repeatedConstraints(annotation)) {
                        (isCrossParameter(repeated, executable) ? crossParameter : returnValue).add(repeated);
                    }
                } else if (ConstraintDefinition.isConstraint(annotation)) {
                    (isCrossParameter(annotation, executable) ? crossParameter : returnValue).add(annotation);
                } else if (annotation instanceof Validate validate) {
                    (validatesParameters(validate, executable) ? parameterValidate : returnValue).add(annotation);
                } else if (annotation instanceof Expr expr) {
                    (validatesParameters(expr, executable) ? parameterExpr : returnValue).add(annotation);
                } else if (annotation instanceof Expr.List list) {
                    // Each expression decides its own side, so the container is unwrapped here.
                    for (Expr expr : list.value()) {
                        (validatesParameters(expr, executable) ? parameterExpr : returnValue).add(expr);
                    }
                } else {
                    returnValue.add(annotation);
                }
            }

            ValueDeclaration returnDeclaration = null;

            if (hasReturnValue(executable)) {
                returnDeclaration = executable instanceof Constructor<?> constructor
                        ? ValueDeclaration.ofReturnValue(constructor, returnValue)
                        : ValueDeclaration.ofReturnValue((Method) executable, owner, returnValue);
            } else if (constrainsReturnValue(returnValue, ((Method) executable).getAnnotatedReturnType())) {
                throw new IllegalExecutableConstraintException(executable, "returns void and must not constrain or cascade its return value.");
            }

            return new Read(executable, Collections.unmodifiableList(parameters), returnDeclaration,
                    Collections.unmodifiableList(crossParameter), Collections.unmodifiableList(parameterValidate),
                    Collections.unmodifiableList(parameterExpr),
                    constrained || !crossParameter.isEmpty() || !parameterValidate.isEmpty() || !parameterExpr.isEmpty());
        }

        private static boolean constrainsReturnValue(List<Annotation> annotations, AnnotatedType returnType) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof Valid || ConstraintDefinition.isConstraint(annotation)) return true;
            }

            return returnType.isAnnotationPresent(Valid.class) || !ConstraintDefinition.load(new ClassConstraintSource(Object.class),
                    Arrays.asList(returnType.getDeclaredAnnotations())).isEmpty();
        }

        private boolean isParameterConstrained() {
            return parameterConstrained;
        }

        /**
         * Whether this executable declares, parameter for parameter, the same annotations as the other
         * (on the parameters and their types), the same cross-parameter constraints and the same
         * {@code @Validate} for the parameters.
         */
        private boolean isEquallyParameterConstrained(Read other) {
            if (!new HashSet<>(crossParameterAnnotations).equals(new HashSet<>(other.crossParameterAnnotations))) return false;
            if (!new HashSet<>(parameterValidateAnnotations).equals(new HashSet<>(other.parameterValidateAnnotations))) return false;
            if (!new HashSet<>(parameterExprAnnotations).equals(new HashSet<>(other.parameterExprAnnotations))) return false;
            Parameter[] mine = executable.getParameters();
            Parameter[] theirs = other.executable.getParameters();

            for (int i = 0; i < mine.length; i++) {
                if (!sameAnnotations(mine[i].getDeclaredAnnotations(), theirs[i].getDeclaredAnnotations())) return false;
                if (!sameAnnotations(mine[i].getAnnotatedType().getDeclaredAnnotations(), theirs[i].getAnnotatedType().getDeclaredAnnotations())) {
                    return false;
                }
            }

            return true;
        }

        private static boolean sameAnnotations(Annotation[] first, Annotation[] second) {
            return new HashSet<>(Arrays.asList(first)).equals(new HashSet<>(Arrays.asList(second)));
        }
    }
}
