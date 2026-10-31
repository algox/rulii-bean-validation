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

import jakarta.validation.GroupDefinitionException;
import jakarta.validation.GroupSequence;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.Bind;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.GroupPlan;
import org.rulii.validation.beans.util.reflect.TypeHierarchies;
import org.rulii.validation.beans.util.reflect.TypeHierarchyVisitor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation definition of one bean class: its {@link PropertyDefinition properties} in discovery
 * order, its class-level {@link ConstraintDefinition constraints} in hierarchy order, the registry
 * rules attached at class level with {@code @Validate} ({@link ValidateDefinition}) and the script
 * conditions declared at class level with {@code @Expr} ({@link ExprDefinition}), also in
 * hierarchy order, the bindings declared at class level with {@code @Bind} ({@link BindDefinition}),
 * its constrained {@link ExecutableDefinition methods and constructors}, and its redefined
 * {@link #getDefaultGroupSequence() Default group}, if any. Everything declared on superclasses and
 * interfaces is included, cumulatively, except the Default redefinition, which the specification does
 * not inherit, and a binding a subtype redeclares, which replaces the inherited one.
 *
 * <p>Loading walks the class hierarchy once ({@link TypeHierarchies}); the property declarations,
 * the class-level constraints and the methods are collected from the same walk. All declaration
 * errors fail fast here, at metadata-read time.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanDefinition implements Definition {

    private final Class<?> beanClass;
    private final List<PropertyDefinition> properties;
    private final List<ConstraintDefinition> classConstraints;
    private final List<ValidateDefinition> validateRules;
    private final List<ExprDefinition> exprRules;
    private final List<BindDefinition> binds;
    private final List<ExecutableDefinition> methods;
    private final List<ExecutableDefinition> constructors;
    private final List<Class<?>> defaultGroupSequence;
    private final SourceDefinition source;
    private final boolean constrained;
    private final boolean cascading;

    private BeanDefinition(Class<?> beanClass, List<PropertyDefinition> properties, List<ConstraintDefinition> classConstraints,
                           List<ValidateDefinition> validateRules, List<ExprDefinition> exprRules, List<BindDefinition> binds,
                           List<ExecutableDefinition> methods, List<ExecutableDefinition> constructors, List<Class<?>> defaultGroupSequence) {
        super();
        this.beanClass = beanClass;
        this.properties = properties;
        this.classConstraints = classConstraints;
        this.validateRules = validateRules;
        this.exprRules = exprRules;
        this.binds = binds;
        this.methods = methods;
        this.constructors = constructors;
        this.defaultGroupSequence = defaultGroupSequence;
        this.source = SourceDefinition.forClass(beanClass);

        boolean constrained = !classConstraints.isEmpty() || !validateRules.isEmpty() || !exprRules.isEmpty();
        boolean cascading = false;

        for (PropertyDefinition property : properties) {
            constrained |= property.isConstrained();
            cascading |= property.isCascaded() || cascades(property.getContainerElements());
        }

        this.constrained = constrained;
        this.cascading = cascading;
    }

    /**
     * Loads the definition of the given bean class, across its superclasses and interfaces, with the
     * compiler's parameter names.
     *
     * @param beanClass the bean class.
     * @param registry the value extractors available at validation time.
     * @return the bean definition.
     * @see #load(Class, ValueExtractorRegistry, ParameterNameProvider)
     */
    public static BeanDefinition load(Class<?> beanClass, ValueExtractorRegistry registry) {
        return load(beanClass, registry, new DefaultParameterNameProvider());
    }

    /**
     * Loads the definition of the given bean class, across its superclasses and interfaces.
     *
     * @param beanClass the bean class.
     * @param registry the value extractors available at validation time.
     * @param names the parameter name provider naming the parameters of constrained executables.
     * @return the bean definition.
     * @throws GroupDefinitionException if the class's {@code @GroupSequence} does not list the class
     *         exactly once, lists {@code Default}, a sequence, or something that is not a group.
     * @throws jakarta.validation.ConstraintDeclarationException if a {@code @ConvertGroup} breaks a rule
     *         (see {@link GroupConversions}), or for the other declaration errors detected while loading.
     * @throws ValidateDeclarationException if a {@code @Validate} declares no name, a blank name, or
     *         a name already referenced for the same property or at class level.
     * @throws BindDeclarationException if a {@code @Bind} is invalid, declared on a getter, named
     *         after a property, repeated on one type, or declared by two unrelated types of the hierarchy.
     * @throws IllegalExecutableConstraintException if a method or constructor is constrained against
     *         the specification's rules (see {@link ExecutableDefinition}).
     * @throws AmbiguousConstraintTargetException if a method constraint could validate the parameters
     *         or the return value and the method has both.
     * @throws org.rulii.validation.beans.extract.NoValueExtractorException if a constrained or
     *         cascaded container element position has no extractor.
     * @throws IllegalArgumentException if the class is a primitive or an array type.
     */
    public static BeanDefinition load(Class<?> beanClass, ValueExtractorRegistry registry, ParameterNameProvider names) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Assert.notNull(registry, "registry cannot be null.");
        Assert.notNull(names, "names cannot be null.");

        Loader loader = new Loader(beanClass);
        TypeHierarchies.walk(beanClass, loader);

        List<PropertySource> sources = loader.properties.getSources();
        List<PropertyDefinition> properties = new ArrayList<>(sources.size());

        for (PropertySource propertySource : sources) {
            properties.add(PropertyDefinition.load(propertySource, registry));
        }

        String owner = "class [" + beanClass.getName() + "]";
        ValidateDefinition.checkDistinct(loader.validateRules, owner);
        List<BindDefinition> binds = BindDefinition.resolve(loader.binds, owner);
        Set<String> propertyNames = new HashSet<>();

        for (PropertyDefinition property : properties) {
            propertyNames.add(property.getName());
        }

        BindDefinition.checkAgainst(binds, propertyNames, owner, "property");

        return new BeanDefinition(beanClass, Collections.unmodifiableList(properties),
                Collections.unmodifiableList(loader.classConstraints), Collections.unmodifiableList(loader.validateRules),
                Collections.unmodifiableList(loader.exprRules), binds, loader.executables.getMethods(registry, names),
                ExecutableDiscovery.constructors(beanClass, registry, names), defaultGroupSequence(beanClass));
    }

    /**
     * The class's redefined Default group: the members of its own {@code @GroupSequence}, in order,
     * the class itself standing for {@code Default}.
     *
     * @param beanClass the class.
     * @return the sequence; null when the class does not carry {@code @GroupSequence}.
     * @throws GroupDefinitionException if the sequence does not list the class exactly once, lists
     *         {@code Default} or a sequence, or lists something that is not a group interface.
     */
    public static List<Class<?>> defaultGroupSequence(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        GroupSequence sequence = beanClass.isInterface() ? null : beanClass.getAnnotation(GroupSequence.class);
        if (sequence == null) return null;

        List<Class<?>> result = new ArrayList<>(sequence.value().length);
        boolean self = false;

        for (Class<?> member : sequence.value()) {
            if (member == beanClass) {
                if (self) throw definition(beanClass, "lists the class itself twice.");
                self = true;
                result.add(Default.class);
                continue;
            }
            if (member == Default.class) throw definition(beanClass, "lists Default; the class itself stands for its Default constraints.");
            if (!member.isInterface()) throw definition(beanClass, "lists [" + member.getName() + "], which is not a group interface.");
            if (GroupPlan.isSequence(member)) throw definition(beanClass, "lists the group sequence [" + member.getName() + "]; members must be plain groups.");
            if (result.contains(member)) throw definition(beanClass, "lists [" + member.getName() + "] twice.");
            result.add(member);
        }

        if (!self) throw definition(beanClass, "does not list the class itself, which stands for its Default constraints.");

        return Collections.unmodifiableList(result);
    }

    private static GroupDefinitionException definition(Class<?> beanClass, String problem) {
        return new GroupDefinitionException("@GroupSequence on [" + beanClass.getName() + "] " + problem);
    }

    /**
     * Whether a class declares anything {@link #load} would read: a constraint (an annotation annotated
     * {@code @Constraint}, or a container of such), {@code @Valid}, {@code @Validate}, {@code @Expr}
     * or {@code @Bind} on the class or a supertype, on a field, a record component, a method or a
     * constructor, on a parameter, or as a type-use annotation of a field, return or parameter type,
     * container element positions included. Reads annotations only: it builds no definition, so it is
     * cheap enough to ask of every class in a package. A class that passes may still fail to load
     * (a declaration error is found by {@link #load}); a class that fails has an empty definition.
     *
     * @param beanClass the class.
     * @return true if the class is a candidate; false for primitives and arrays.
     */
    public static boolean isCandidate(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        if (beanClass.isPrimitive() || beanClass.isArray()) return false;

        Candidate candidate = new Candidate();
        TypeHierarchies.walk(beanClass, candidate);
        if (candidate.found) return true;

        for (Constructor<?> constructor : beanClass.getDeclaredConstructors()) {
            if (!constructor.isSynthetic() && declares(constructor)) return true;
        }

        return false;
    }

    private static boolean declares(Executable executable) {
        if (declares(executable.getDeclaredAnnotations())) return true;
        if (executable instanceof Method method && declares(method.getAnnotatedReturnType())) return true;

        for (Parameter parameter : executable.getParameters()) {
            if (declares(parameter.getDeclaredAnnotations()) || declares(parameter.getAnnotatedType())) return true;
        }

        return false;
    }

    private static boolean declares(AnnotatedType type) {
        if (type == null) return false;
        if (declares(type.getAnnotations())) return true;

        if (type instanceof AnnotatedParameterizedType parameterized) {
            for (AnnotatedType argument : parameterized.getAnnotatedActualTypeArguments()) {
                if (declares(argument)) return true;
            }
        } else if (type instanceof AnnotatedArrayType array) {
            return declares(array.getAnnotatedGenericComponentType());
        } else if (type instanceof AnnotatedWildcardType wildcard) {
            for (AnnotatedType bound : wildcard.getAnnotatedUpperBounds()) {
                if (declares(bound)) return true;
            }

            for (AnnotatedType bound : wildcard.getAnnotatedLowerBounds()) {
                if (declares(bound)) return true;
            }
        }

        return false;
    }

    private static boolean declares(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Valid || annotation instanceof Validate
                    || annotation instanceof Expr || annotation instanceof Expr.List
                    || annotation instanceof Bind || annotation instanceof Bind.List
                    || ConstraintDefinition.isConstraint(annotation) || ConstraintDefinition.isConstraintContainer(annotation)) {
                return true;
            }
        }

        return false;
    }

    private static boolean cascades(List<ContainerElementDefinition> elements) {
        for (ContainerElementDefinition element : elements) {
            if (element.isCascaded() || cascades(element.getChildren())) return true;
        }

        return false;
    }

    /**
     * Simple name of the bean class.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return beanClass.getSimpleName();
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * The properties, in discovery order.
     *
     * @return the property definitions; empty if the class has no properties.
     */
    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * The property with the given name.
     *
     * @param name the property name.
     * @return the property definition, or null if the class has no such property.
     */
    public PropertyDefinition getProperty(String name) {
        Assert.notNull(name, "name cannot be null.");

        for (PropertyDefinition property : properties) {
            if (property.getName().equals(name)) return property;
        }

        return null;
    }

    /**
     * The class-level constraints, in hierarchy order.
     *
     * @return the constraint definitions; empty if none.
     */
    public List<ConstraintDefinition> getClassConstraints() {
        return classConstraints;
    }

    /**
     * The registry rules attached at class level, in hierarchy order.
     *
     * @return the definitions; empty if none.
     */
    public List<ValidateDefinition> getValidateRules() {
        return validateRules;
    }

    /**
     * The {@code @Expr} conditions declared at class level, in hierarchy order.
     *
     * @return the definitions; empty if none.
     */
    public List<ExprDefinition> getExprRules() {
        return exprRules;
    }

    /**
     * The {@code @Bind} bindings declared at class level, resolved over the hierarchy: one per name,
     * a subtype's declaration replacing an inherited one, in the order the names were first met.
     *
     * @return the definitions; empty if none.
     */
    public List<BindDefinition> getBinds() {
        return binds;
    }

    /**
     * The constrained methods, getters included, in discovery order.
     *
     * @return the definitions; empty if none.
     */
    public List<ExecutableDefinition> getMethods() {
        return methods;
    }

    /**
     * The constrained method with the given signature.
     *
     * @param name the method name.
     * @param parameterTypes the parameter types.
     * @return the definition, or null if no such method is constrained.
     */
    public ExecutableDefinition getMethod(String name, Class<?>... parameterTypes) {
        Assert.notNull(name, "name cannot be null.");
        Assert.notNull(parameterTypes, "parameterTypes cannot be null.");

        for (ExecutableDefinition method : methods) {
            if (method.matches(name, parameterTypes)) return method;
        }

        return null;
    }

    /**
     * The constrained constructors of the class itself, in declaration order.
     *
     * @return the definitions; empty if none.
     */
    public List<ExecutableDefinition> getConstructors() {
        return constructors;
    }

    /**
     * The constrained constructor with the given parameter types.
     *
     * @param parameterTypes the parameter types.
     * @return the definition, or null if no such constructor is constrained.
     */
    public ExecutableDefinition getConstructor(Class<?>... parameterTypes) {
        Assert.notNull(parameterTypes, "parameterTypes cannot be null.");

        for (ExecutableDefinition constructor : constructors) {
            if (Arrays.equals(constructor.getParameterTypes(), parameterTypes)) return constructor;
        }

        return null;
    }

    /**
     * The class's redefined Default group, when it carries {@code @GroupSequence}: the member groups
     * in order, {@code Default} standing where the class listed itself. Not inherited from a superclass.
     *
     * @return the sequence; null when the class does not redefine Default.
     */
    public List<Class<?>> getDefaultGroupSequence() {
        return defaultGroupSequence;
    }

    /**
     * Whether validating an instance of the class has anything to do: a constrained property, a
     * class-level constraint, a {@code @Validate} rule or an {@code @Expr}. Executable constraints do
     * not count.
     *
     * @return true if the bean is constrained.
     */
    public boolean isConstrained() {
        return constrained;
    }

    /**
     * Whether validating an instance cascades into other objects.
     *
     * @return true if a property or a container element position is cascaded.
     */
    public boolean hasCascades() {
        return cascading;
    }

    @Override
    public String toString() {
        return "BeanDefinition{beanClass=" + beanClass.getName() + ", properties=" + properties.size()
                + ", classConstraints=" + classConstraints.size() + ", validateRules=" + validateRules.size()
                + ", exprRules=" + exprRules.size() + ", binds=" + binds.size() + ", methods=" + methods.size()
                + ", constructors=" + constructors.size()
                + (defaultGroupSequence != null ? ", defaultGroupSequence=" + defaultGroupSequence : "") + '}';
    }

    /**
     * The hierarchy visitor of one load: properties and methods from the same walk.
     */
    private static final class Loader implements TypeHierarchyVisitor {

        private final PropertyDiscovery properties;
        private final ExecutableDiscovery executables;
        private final List<ConstraintDefinition> classConstraints = new ArrayList<>();
        private final List<ValidateDefinition> validateRules = new ArrayList<>();
        private final List<ExprDefinition> exprRules = new ArrayList<>();
        private final List<BindDefinition> binds = new ArrayList<>();

        private Loader(Class<?> beanClass) {
            super();
            this.properties = new PropertyDiscovery(beanClass);
            this.executables = new ExecutableDiscovery(beanClass);
        }

        @Override
        public void visitType(Class<?> type) {
            classConstraints.addAll(ConstraintDefinition.load(type));
            validateRules.addAll(ValidateDefinition.load(type));
            exprRules.addAll(ExprDefinition.load(type));
            binds.addAll(BindDefinition.load(type));
        }

        @Override
        public void visitField(Field field) {
            properties.visitField(field);
        }

        @Override
        public void visitMethod(Method method) {
            properties.visitMethod(method);
            executables.visitMethod(method);
        }

        @Override
        public void visitRecordComponent(RecordComponent component) {
            properties.visitRecordComponent(component);
        }
    }

    /**
     * The walk behind {@link #isCandidate}: stops looking once something is found.
     */
    private static final class Candidate implements TypeHierarchyVisitor {

        private boolean found;

        private Candidate() {
            super();
        }

        @Override
        public void visitType(Class<?> type) {
            found |= declares(type.getDeclaredAnnotations());
        }

        @Override
        public void visitField(Field field) {
            found |= declares(field.getDeclaredAnnotations()) || declares(field.getAnnotatedType());
        }

        @Override
        public void visitMethod(Method method) {
            found |= declares(method);
        }

        @Override
        public void visitRecordComponent(RecordComponent component) {
            found |= declares(component.getDeclaredAnnotations()) || declares(component.getAnnotatedType());
        }
    }
}
