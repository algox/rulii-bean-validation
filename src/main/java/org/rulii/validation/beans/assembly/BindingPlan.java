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

import org.rulii.bind.ReservedBindings;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.PropertyConstraintSource;
import org.rulii.validation.beans.model.PropertyDeclaration;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.PropertySource;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The binding names of a bean class's properties in its validation scope: which name each property
 * is bound under, and which name each of its declarations is read through. Pure naming; the
 * bindings themselves are created at validation time from this plan.
 *
 * <p>A property is bound under its own name, read through its primary declaration. A property whose
 * name is reserved by the engine ({@code rule}, {@code ruleContext}, ...) or by this library
 * ({@code $this}, {@code $value}, {@code ruleViolations}, ...) is bound under {@code <name>$}
 * instead, so no class is rejected over a field name. A secondary declaration carrying constraints
 * gets a shadow binding so each constraint reads the declaration it was written on:
 * {@code <name>$field} for the field beside a getter, {@code <name>$<DeclaringClass>} for a
 * superclass field hidden by a subclass one. An unconstrained secondary field needs no binding, and
 * an overriding getter reads the primary binding, since the call is virtual either way. A residual
 * collision appends {@code $2}, {@code $3}, ... deterministically.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BindingPlan {

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
    /** The violations collector. */
    public static final String RULE_VIOLATIONS = "ruleViolations";

    /**
     * Names a property cannot be bound under: the engine's reserved names and this library's own.
     */
    public static final Set<String> RESERVED_NAMES;

    static {
        Set<String> reserved = new HashSet<>(ReservedBindings.reservedBindingNames());
        reserved.addAll(List.of(THIS, ROOT, PARENT, ELEMENT, KEY, VALUE, RULE_VIOLATIONS));
        RESERVED_NAMES = Collections.unmodifiableSet(reserved);
    }

    private final BeanDefinition bean;
    private final Map<String, PropertyDeclaration> bindings;
    private final Map<PropertyDeclaration, String> names;
    private final Map<String, String> properties;

    private BindingPlan(BeanDefinition bean, Map<String, PropertyDeclaration> bindings,
                        Map<PropertyDeclaration, String> names, Map<String, String> properties) {
        super();
        this.bean = bean;
        this.bindings = bindings;
        this.names = names;
        this.properties = properties;
    }

    /**
     * Plans the bindings of the given bean class.
     *
     * @param bean the bean definition.
     * @return the plan.
     */
    public static BindingPlan of(BeanDefinition bean) {
        Assert.notNull(bean, "bean cannot be null.");

        Map<String, PropertyDeclaration> bindings = new LinkedHashMap<>();
        Map<PropertyDeclaration, String> names = new LinkedHashMap<>();
        Map<String, String> properties = new LinkedHashMap<>();

        for (PropertyDefinition property : bean.getProperties()) {
            PropertySource source = property.getPropertySource();
            PropertyDeclaration primary = source.getPrimary();
            String name = property.getName();
            String binding = unique(RESERVED_NAMES.contains(name) ? name + "$" : name, bindings.keySet(), "$");

            bindings.put(binding, primary);
            names.put(primary, binding);
            properties.put(name, binding);

            PropertyDeclaration mostDerivedField = mostDerivedField(source);
            Set<PropertyDeclaration> constrained = constrainedDeclarations(property);

            for (PropertyDeclaration declaration : source.getDeclarations()) {
                if (declaration.equals(primary)) continue;

                if (declaration.getKind() == PropertyDeclaration.Kind.GETTER || !constrained.contains(declaration)) {
                    // An overridden getter yields the primary value; an unconstrained field is never read.
                    names.put(declaration, binding);
                    continue;
                }

                String shadow = declaration.equals(mostDerivedField) && primary.getKind() == PropertyDeclaration.Kind.GETTER
                        ? name + "$field"
                        : name + "$" + declaration.getDeclaringClass().getSimpleName();
                shadow = unique(shadow, bindings.keySet(), "");
                bindings.put(shadow, declaration);
                names.put(declaration, shadow);
            }
        }

        return new BindingPlan(bean, Collections.unmodifiableMap(bindings), Collections.unmodifiableMap(names),
                Collections.unmodifiableMap(properties));
    }

    private static Set<PropertyDeclaration> constrainedDeclarations(PropertyDefinition property) {
        Set<PropertyDeclaration> result = new HashSet<>();
        for (ConstraintDefinition constraint : property.getConstraints()) {
            result.add(((PropertyConstraintSource) constraint.getConstraintSource()).getDeclaration());
        }
        return result;
    }

    private static PropertyDeclaration mostDerivedField(PropertySource source) {
        List<PropertyDeclaration> fields = source.getDeclarations(PropertyDeclaration.Kind.FIELD);
        return fields.isEmpty() ? null : fields.get(fields.size() - 1);
    }

    /**
     * A name not yet in use: the candidate itself, else the candidate with the separator and a
     * counter from 2 upwards appended.
     */
    private static String unique(String candidate, Set<String> taken, String separator) {
        if (!taken.contains(candidate)) return candidate;
        int counter = 2;
        while (taken.contains(candidate + separator + counter)) counter++;
        return candidate + separator + counter;
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
     * Every binding to create, in order: binding name to the declaration whose value it holds.
     * Primary bindings come first, each followed by its property's shadows.
     *
     * @return the bindings; unmodifiable.
     */
    public Map<String, PropertyDeclaration> getBindings() {
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
     * The binding a declaration's constraints read: the property binding for the primary declaration
     * and overriding getters, a shadow binding for other fields.
     *
     * @param declaration the declaration.
     * @return the binding name.
     * @throws IllegalArgumentException if the declaration does not belong to the bean.
     */
    public String getBindingName(PropertyDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        String result = names.get(declaration);
        Assert.notNull(result, "declaration [" + declaration.describe() + "] is not a declaration of [" + bean.getBeanClass().getName() + "]");
        return result;
    }

    /**
     * Whether the binding is a shadow (a secondary declaration's) rather than a property binding.
     *
     * @param bindingName the binding name.
     * @return true if it is a shadow binding.
     */
    public boolean isShadow(String bindingName) {
        Assert.notNull(bindingName, "bindingName cannot be null.");
        return bindings.containsKey(bindingName) && !properties.containsValue(bindingName);
    }

    @Override
    public String toString() {
        return "BindingPlan{" + bean.getBeanClass().getName() + " " + bindings.keySet() + '}';
    }
}
