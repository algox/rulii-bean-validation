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
package org.rulii.validation.beans.assembly;

import org.rulii.bind.Binding;
import org.rulii.bind.Bindings;
import org.rulii.bind.ReservedBindings;
import org.rulii.bind.load.BindingLoader;
import org.rulii.lib.spring.util.Assert;
import org.rulii.util.TypeReference;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.DeclarationConstraintSource;
import org.rulii.validation.beans.model.ExprDefinition;
import org.rulii.validation.beans.model.ValueDeclaration;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.PropertySource;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * How a bean class's properties appear in its validation scope, and how its declarations are read.
 *
 * <p>Every property is bound under its own name, holding the value of its primary declaration
 * (record component, else the getter, else the field), read lazily on first access and remembered
 * for the scope's lifetime, read-only: a property nobody references is never read, and one that
 * several rules reference is read once. {@link #load(Bindings, Object)} creates those bindings for a
 * bean. A property whose name is reserved by the engine ({@code rule}, {@code ruleContext}, ...) or
 * by this library ({@code $this}, {@code $value}, {@code ruleViolations}, ...) is bound under
 * {@code <name>$} instead, so no class is rejected over a field name; a residual collision appends
 * {@code $2}, {@code $3}, ... deterministically.</p>
 *
 * <p>A constraint written on a declaration that is not the primary one (a field beside its getter,
 * a superclass field hidden by a subclass one) does not get a binding of its own: its rule reads the
 * declaration directly through a {@link PropertyAccessor} on the object in scope, so a field
 * constraint validates the field and a getter constraint the getter, as the specification has it.
 * Accessors exist for every declaration validation reads: the primaries, and the secondaries that
 * carry a constraint, a cascade or constrained container elements. An overriding getter reads the
 * primary binding, since the call is virtual either way.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BindingPlan implements BindingLoader<Object> {

    /** The object being validated. */
    public static final String THIS = "$this";
    /** The root object of the validation call. */
    public static final String ROOT = "$root";
    /** The object a cascaded object was reached from. */
    public static final String PARENT = "$parent";
    /** The current element of a container walk. */
    public static final String ELEMENT = "$element";
    /** The current key of a map walk. */
    public static final String KEY = "$key";
    /** The current value of a map walk, or the value of a property under a property-level {@code @Validate}. */
    public static final String VALUE = "$value";
    /** The traversal of the current validation call. */
    public static final String TRAVERSAL = "$traversal";
    /** The groups being processed, a live {@code Set<Class<?>>} view that follows sequences and conversions. */
    public static final String GROUPS = "$groups";
    /** The arguments of the executable being validated, as a whole. */
    public static final String PARAMETERS = "$parameters";
    /** The return value of the executable being validated. */
    public static final String RETURN_VALUE = "$returnValue";
    /** The violations collector. */
    public static final String RULE_VIOLATIONS = "ruleViolations";
    /** The type of the {@link #GROUPS} binding. */
    public static final Type GROUPS_TYPE = new TypeReference<Set<Class<?>>>() {}.getType();

    /**
     * Names a property or parameter cannot be bound under: the engine's reserved names and this
     * library's own.
     */
    public static final Set<String> RESERVED_NAMES;

    static {
        Set<String> reserved = new HashSet<>(ReservedBindings.reservedBindingNames());
        reserved.addAll(List.of(THIS, ROOT, PARENT, ELEMENT, KEY, VALUE, TRAVERSAL, GROUPS, PARAMETERS, RETURN_VALUE, RULE_VIOLATIONS));
        RESERVED_NAMES = Collections.unmodifiableSet(reserved);
    }

    private final BeanDefinition bean;
    private final Map<String, PropertyDefinition> bindings;
    private final Map<String, String> properties;
    private final Map<ValueDeclaration, PropertyAccessor> accessors;
    private final Set<ValueDeclaration> primaries;

    private BindingPlan(BeanDefinition bean, Map<String, PropertyDefinition> bindings, Map<String, String> properties,
                        Map<ValueDeclaration, PropertyAccessor> accessors, Set<ValueDeclaration> primaries) {
        super();
        this.bean = bean;
        this.bindings = bindings;
        this.properties = properties;
        this.accessors = accessors;
        this.primaries = primaries;
    }

    /**
     * Plans the bindings of the given bean class and creates the accessors it needs.
     *
     * @param bean the bean definition.
     * @return the plan.
     * @throws InaccessiblePropertyException if a declaration validation reads cannot be made accessible.
     */
    public static BindingPlan of(BeanDefinition bean) {
        Assert.notNull(bean, "bean cannot be null.");

        Map<String, PropertyDefinition> bindings = new LinkedHashMap<>();
        Map<String, String> properties = new LinkedHashMap<>();
        Map<ValueDeclaration, PropertyAccessor> accessors = new LinkedHashMap<>();
        Set<ValueDeclaration> primaries = new HashSet<>();

        for (PropertyDefinition property : bean.getProperties()) {
            PropertySource source = property.getPropertySource();
            ValueDeclaration primary = source.getPrimary();
            String name = property.getName();
            String binding = unique(RESERVED_NAMES.contains(name) ? name + "$" : name, bindings.keySet());

            bindings.put(binding, property);
            properties.put(name, binding);
            primaries.add(primary);
            // A property nothing in this library reads is resolved on first read (by a user rule), so an
            // inaccessible but unconstrained member (a JDK class reached through @Valid) costs nothing.
            accessors.put(primary, property.isConstrained() ? PropertyAccessor.of(primary) : PropertyAccessor.lazy(primary));

            for (ValueDeclaration declaration : readDeclarations(property)) {
                if (!declaration.equals(primary) && declaration.getKind() != ValueDeclaration.Kind.GETTER) {
                    accessors.computeIfAbsent(declaration, PropertyAccessor::of);
                }
            }
        }

        return new BindingPlan(bean, Collections.unmodifiableMap(bindings), Collections.unmodifiableMap(properties),
                Collections.unmodifiableMap(accessors), Collections.unmodifiableSet(primaries));
    }

    /**
     * The declarations of a property that validation reads: those carrying a constraint, a cascade
     * or container elements.
     */
    private static Set<ValueDeclaration> readDeclarations(PropertyDefinition property) {
        Set<ValueDeclaration> result = new HashSet<>();

        for (ConstraintDefinition constraint : property.getConstraints()) {
            result.add(((DeclarationConstraintSource) constraint.getConstraintSource()).getDeclaration());
        }

        for (ExprDefinition expr : property.getExprRules()) {
            result.add(((DeclarationConstraintSource) expr.getConstraintSource()).getDeclaration());
        }

        result.addAll(property.getCascadedDeclarations());

        for (ContainerElementDefinition node : property.getContainerElements()) {
            result.add(node.getSourceDeclaration());
        }

        return result;
    }

    /**
     * A name not yet in use: the candidate itself, else the candidate with {@code $2}, {@code $3}, ...
     * appended.
     */
    private static String unique(String candidate, Set<String> taken) {
        if (!taken.contains(candidate)) return candidate;
        int counter = 2;
        while (taken.contains(candidate + "$" + counter)) counter++;
        return candidate + "$" + counter;
    }

    /**
     * The name the rules of a container element position read the element under: {@code $key} for a
     * {@code Map} key, {@code $value} for a {@code Map} value, {@code $element} otherwise.
     *
     * @param node the position.
     * @return the binding name.
     */
    public static String elementBinding(ContainerElementDefinition node) {
        Assert.notNull(node, "node cannot be null.");

        if (Map.class.isAssignableFrom(node.getContainerType())) {
            return node.getTypeArgumentIndex() == 0 ? KEY : VALUE;
        }

        return ELEMENT;
    }

    /**
     * The value of a declaration of a property, read the way its constraints read it: through the
     * property binding for the primary declaration and for getters, through the declaration's accessor
     * off the bean otherwise.
     *
     * @param bindings the bindings the property is bound in.
     * @param bean the bean.
     * @param property the property.
     * @param declaration the declaration to read.
     * @return the value.
     */
    public Object read(Bindings bindings, Object bean, PropertyDefinition property, ValueDeclaration declaration) {
        Assert.notNull(bindings, "bindings cannot be null.");
        Assert.notNull(declaration, "declaration cannot be null.");

        if (isPrimary(declaration) || declaration.getKind() == ValueDeclaration.Kind.GETTER) {
            return bindings.getValue(getBindingName(property));
        }

        return getAccessor(declaration).read(bean);
    }

    /**
     * The bean the plan is for.
     *
     * @return the bean definition.
     */
    public BeanDefinition getBean() {
        return bean;
    }

    /**
     * The bindings to create, in property order: binding name to the property it holds.
     *
     * @return the bindings; unmodifiable.
     */
    public Map<String, PropertyDefinition> getBindings() {
        return bindings;
    }

    /**
     * The binding a property is bound under: its name, or its escaped name when reserved.
     *
     * @param property the property.
     * @return the binding name.
     * @throws IllegalArgumentException if the property does not belong to the bean.
     */
    public String getBindingName(PropertyDefinition property) {
        Assert.notNull(property, "property cannot be null.");
        String result = properties.get(property.getName());
        Assert.notNull(result, "property [" + property.getName() + "] is not a property of [" + bean.getBeanClass().getName() + "]");
        return result;
    }

    /**
     * Whether a declaration is the primary of its property, whose value the property binding holds.
     *
     * @param declaration the declaration.
     * @return true for a primary declaration.
     */
    public boolean isPrimary(ValueDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        return primaries.contains(declaration);
    }

    /**
     * The accessor reading a declaration off the bean.
     *
     * @param declaration the declaration.
     * @return the accessor.
     * @throws IllegalArgumentException if the declaration is not one validation reads.
     */
    public PropertyAccessor getAccessor(ValueDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        PropertyAccessor result = accessors.get(declaration);
        Assert.notNull(result, "declaration [" + declaration.describe() + "] is not read by the validation of ["
                + bean.getBeanClass().getName() + "]");
        return result;
    }

    /**
     * Every accessor, in property order: primaries first per property, then the secondaries read.
     *
     * @return the accessors by declaration; unmodifiable.
     */
    public Map<ValueDeclaration, PropertyAccessor> getAccessors() {
        return accessors;
    }

    /**
     * Binds the properties of the given bean into the current scope: one lazy, remembered,
     * read-only binding per property, holding its primary declaration's value.
     *
     * @param bindings the bindings to load into.
     * @param bean the bean; an instance of the planned class.
     */
    @Override
    public void load(Bindings bindings, Object bean) {
        Assert.notNull(bindings, "bindings cannot be null.");
        Assert.notNull(bean, "bean cannot be null.");
        Assert.isTrue(this.bean.getBeanClass().isInstance(bean), "bean [" + bean.getClass().getName()
                + "] is not an instance of [" + this.bean.getBeanClass().getName() + "]");

        for (Map.Entry<String, PropertyDefinition> entry : this.bindings.entrySet()) {
            PropertyDefinition property = entry.getValue();
            PropertyAccessor accessor = accessors.get(property.getPropertySource().getPrimary());
            Binding<?> binding = Binding.builder().with(entry.getKey(), bindingType(property))
                    .value(new Memoized(() -> accessor.read(bean)))
                    .editable(false)
                    .build();
            bindings.bind(binding);
        }
    }

    /**
     * The type a property is bound with: its resolved generic type when fully known, else its class.
     */
    private static Type bindingType(PropertyDefinition property) {
        Type type = property.getGenericType();
        return isConcrete(type) ? type : property.getType();
    }

    private static boolean isConcrete(Type type) {
        if (type instanceof Class<?>) return true;
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (!isConcrete(argument)) return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Reads once, on first access, and remembers the value (null included).
     */
    private static final class Memoized implements Supplier<Object> {

        private final Supplier<Object> source;
        private boolean read;
        private Object value;

        private Memoized(Supplier<Object> source) {
            super();
            this.source = source;
        }

        @Override
        public Object get() {
            if (!read) {
                value = source.get();
                read = true;
            }
            return value;
        }
    }

    @Override
    public String toString() {
        return "BindingPlan{" + bean.getBeanClass().getName() + " " + bindings.keySet() + '}';
    }
}
