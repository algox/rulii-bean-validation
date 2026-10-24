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

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Where a bean property comes from: the {@link ValueDeclaration declarations} it is read from
 * and, among them, the <em>primary</em> declaration whose value the property stands for.
 *
 * <p>A property may be declared several times across a class hierarchy: a field and its getter,
 * a superclass field shadowed by a subclass field of the same name, a getter and the getters it
 * overrides or implements on superclasses and interfaces, or a record component and a getter of the
 * same name. The constraints of every declaration are cumulative and each field reads its own value,
 * while a single declaration is primary: it is the public contract of the property, so it provides
 * the property type and the value that is bound in the validation scope and that cascade and
 * container walks descend on. The primary is the record component when there is one, else the most
 * derived getter, else the most derived field.</p>
 *
 * <p>Declarations are ordered fields (or the record component) first, then getters; within a kind
 * the order given to {@link #of(List)} is kept, which discovery supplies top-down through the
 * hierarchy.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertySource {

    private final String name;
    private final List<ValueDeclaration> declarations;
    private final ValueDeclaration primary;

    private PropertySource(String name, List<ValueDeclaration> declarations, ValueDeclaration primary) {
        super();
        this.name = name;
        this.declarations = declarations;
        this.primary = primary;
    }

    /**
     * Source of a property declared by a field alone.
     *
     * @param field the field.
     * @return the property source.
     */
    public static PropertySource ofField(Field field) {
        Assert.notNull(field, "field cannot be null.");
        return of(List.of(ValueDeclaration.ofField(field)));
    }

    /**
     * Source of a property declared by a getter alone.
     *
     * @param getter the JavaBeans getter.
     * @return the property source.
     * @throws IllegalArgumentException if the method is not a getter.
     */
    public static PropertySource ofGetter(Method getter) {
        Assert.notNull(getter, "getter cannot be null.");
        return of(List.of(ValueDeclaration.ofGetter(getter)));
    }

    /**
     * Source of a property declared by a record component.
     *
     * @param component the record component.
     * @return the property source.
     */
    public static PropertySource ofRecordComponent(RecordComponent component) {
        Assert.notNull(component, "component cannot be null.");
        return of(List.of(ValueDeclaration.ofRecordComponent(component)));
    }

    /**
     * Source of a property declared by both a field and its getter.
     *
     * @param field the field.
     * @param getter the JavaBeans getter of the same property.
     * @return the property source.
     * @throws IllegalArgumentException if the method is not a getter, or names a different property.
     */
    public static PropertySource of(Field field, Method getter) {
        Assert.notNull(field, "field cannot be null.");
        Assert.notNull(getter, "getter cannot be null.");
        Class<?> owner = mostDerived(field.getDeclaringClass(), getter.getDeclaringClass());
        return of(List.of(ValueDeclaration.ofField(field, owner), ValueDeclaration.ofGetter(getter, owner)));
    }

    /**
     * Source of a property from its declarations. All declarations must name the same property and
     * be distinct; a record component excludes fields and is the only component. Declarations are
     * ordered fields (or the component) first, then getters, keeping the given order within a kind.
     * The primary is the record component when there is one, else the most derived getter, else the
     * most derived field; the first one given wins when two declaring types are unrelated.
     *
     * @param declarations the declarations of one property.
     * @return the property source.
     * @throws IllegalArgumentException if the declarations do not describe one property.
     */
    public static PropertySource of(List<ValueDeclaration> declarations) {
        Assert.notEmpty(declarations, "declarations cannot be empty.");
        Assert.noNullElements(declarations, "declarations cannot contain null.");

        List<ValueDeclaration> ordered = new ArrayList<>(declarations);
        ordered.sort(Comparator.comparingInt(d -> d.getKind() == ValueDeclaration.Kind.GETTER ? 1 : 0));
        String name = ordered.get(0).getName();
        Class<?> owner = ordered.get(0).getOwner();
        ValueDeclaration component = null;
        ValueDeclaration field = null;
        ValueDeclaration getter = null;

        for (int i = 0; i < ordered.size(); i++) {
            ValueDeclaration declaration = ordered.get(i);
            Assert.isTrue(name.equals(declaration.getName()), "declarations describe different properties: ["
                    + name + "] and [" + declaration.getName() + "] (" + declaration.describe() + ").");
            Assert.isTrue(owner == declaration.getOwner(), "declarations of property [" + name + "] have different owners: ["
                    + owner.getName() + "] and [" + declaration.getOwner().getName() + "] (" + declaration.describe() + ").");
            Assert.isTrue(ordered.indexOf(declaration) == i, "property [" + name + "] has the same declaration twice; "
                    + declaration.describe());

            switch (declaration.getKind()) {
                case RECORD_COMPONENT -> {
                    Assert.isTrue(component == null, "property [" + name + "] has two record components; "
                            + declaration.describe());
                    component = declaration;
                }
                case FIELD -> field = mostDerived(field, declaration);
                case GETTER -> getter = mostDerived(getter, declaration);
            }
        }

        Assert.isTrue(component == null || field == null, "property [" + name
                + "] is a record component; it cannot also be declared by a field (" + field + ").");

        ValueDeclaration primary = component != null ? component : getter != null ? getter : field;
        return new PropertySource(name, Collections.unmodifiableList(ordered), primary);
    }

    /**
     * The more derived of two related classes: the owner of a property declared by a field of one and
     * a getter of the other.
     */
    private static Class<?> mostDerived(Class<?> first, Class<?> second) {
        if (first.isAssignableFrom(second)) return second;
        Assert.isTrue(second.isAssignableFrom(first), "classes [" + first.getName() + "] and [" + second.getName()
                + "] are unrelated; a field and a getter of one property must be declared in one class hierarchy.");
        return first;
    }

    /**
     * The declaration whose declaring type is the subtype of the other's; the current one when
     * the two are unrelated or declared by the same type.
     */
    private static ValueDeclaration mostDerived(ValueDeclaration current, ValueDeclaration candidate) {
        if (current == null) return candidate;
        Class<?> currentType = current.getDeclaringClass();
        Class<?> candidateType = candidate.getDeclaringClass();
        return currentType != candidateType && currentType.isAssignableFrom(candidateType) ? candidate : current;
    }

    /**
     * Name of the property.
     *
     * @return the property name.
     */
    public String getName() {
        return name;
    }

    /**
     * The declarations the property is read from: fields (or the record component) first, then
     * getters, each kind in hierarchy order top-down.
     *
     * @return the declarations, in that order.
     */
    public List<ValueDeclaration> getDeclarations() {
        return declarations;
    }

    /**
     * The declarations of the given kind, in hierarchy order top-down.
     *
     * @param kind the kind.
     * @return the declarations of that kind; empty if none.
     */
    public List<ValueDeclaration> getDeclarations(ValueDeclaration.Kind kind) {
        Assert.notNull(kind, "kind cannot be null.");
        List<ValueDeclaration> result = new ArrayList<>();

        for (ValueDeclaration declaration : declarations) {
            if (declaration.getKind() == kind) result.add(declaration);
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * The declaration whose value the property stands for at validation time: the record component
     * when there is one, else the most derived getter, else the most derived field.
     *
     * @return the primary declaration.
     */
    public ValueDeclaration getPrimary() {
        return primary;
    }

    /**
     * Whether the property has more than one declaration (a field and a getter, shadowed fields,
     * overridden getters, ...).
     *
     * @return true if there are several declarations.
     */
    public boolean hasMultipleDeclarations() {
        return declarations.size() > 1;
    }

    /**
     * The member the property value is read through, per the primary declaration.
     *
     * @return the member.
     */
    public Member getMember() {
        return primary.getMember();
    }

    public Class<?> getDeclaringClass() {
        return primary.getDeclaringClass();
    }

    /**
     * Declared type of the property, per the primary declaration.
     *
     * @return the declared class.
     */
    public Class<?> getType() {
        return primary.getType();
    }

    /**
     * Generic declared type of the property, per the primary declaration.
     *
     * @return the generic type.
     */
    public Type getGenericType() {
        return primary.getGenericType();
    }

    /**
     * Core source descriptor of the primary declaration.
     *
     * @return the source definition.
     */
    public SourceDefinition getSourceDefinition() {
        return primary.getSourceDefinition();
    }

    /**
     * Human-readable description of the primary declaration, e.g. {@code com.acme.Order.getTotal()}.
     *
     * @return the description.
     */
    public String describe() {
        return primary.describe();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertySource that = (PropertySource) o;
        return declarations.equals(that.declarations);
    }

    @Override
    public int hashCode() {
        return declarations.hashCode();
    }

    @Override
    public String toString() {
        return "PropertySource{name=" + name + ", declarations=" + declarations + ", primary=" + primary + '}';
    }
}
