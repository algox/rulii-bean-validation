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
package org.rulii.validation.beans.metadata;

import org.rulii.lib.spring.util.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One bean property — the path node of everything declared for a name. Constraints are
 * held as a uniform list of {@link ConstraintSource}s (cumulative semantics, no accessor
 * substitution): each source pairs the declaring member with the accessor reading that
 * member's own value, covering fields, getters, shadowed superclass fields, and record
 * components (which surface through their accessor) with one shape. The property's
 * primary value — the one cascaded into and bound on the path — is the getter's when one
 * exists, the field's otherwise.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyMetadata {

    private final String name;
    private final Type declaredType;
    private final Field field;
    private final Method getter;
    private final RecordComponent recordComponent;
    private final List<ConstraintSource> sources;
    private final boolean cascaded;
    private final ContainerMetadata container;
    private final List<String> validateRuleNames;
    private final PropertyAccessor primaryAccessor;

    PropertyMetadata(String name, Type declaredType, Field field, Method getter, RecordComponent recordComponent,
                     List<ConstraintSource> sources, boolean cascaded, ContainerMetadata container,
                     List<String> validateRuleNames, PropertyAccessor primaryAccessor) {
        super();
        Assert.hasText(name, "name cannot be null or empty.");
        Assert.notNull(declaredType, "declaredType cannot be null.");
        Assert.notNull(sources, "sources cannot be null.");
        Assert.notNull(validateRuleNames, "validateRuleNames cannot be null.");
        Assert.notNull(primaryAccessor, "primaryAccessor cannot be null.");
        this.name = name;
        this.declaredType = declaredType;
        this.field = field;
        this.getter = getter;
        this.recordComponent = recordComponent;
        this.sources = Collections.unmodifiableList(sources);
        this.cascaded = cascaded;
        this.container = container;
        this.validateRuleNames = Collections.unmodifiableList(validateRuleNames);
        this.primaryAccessor = primaryAccessor;
    }

    /**
     * Retrieves the property name (the path node name).
     *
     * @return the property name.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the declared generic type of the property's primary source.
     *
     * @return the declared type.
     */
    public Type getDeclaredType() {
        return declaredType;
    }

    /**
     * Retrieves the backing field.
     *
     * @return the field; null for getter-only properties and record components.
     */
    public Field getField() {
        return field;
    }

    /**
     * Retrieves the getter (or record accessor).
     *
     * @return the getter; null for field-only properties.
     */
    public Method getGetter() {
        return getter;
    }

    /**
     * Retrieves the record component backing this property.
     *
     * @return the record component; null unless the bean is a record.
     */
    public RecordComponent getRecordComponent() {
        return recordComponent;
    }

    /**
     * Retrieves the constraint sources of this property in hierarchy declaration order
     * (shadowed superclass fields first, then the backing field, then the getter). Each
     * source's constraints validate against that source's own value. Only sources that
     * carry constraints are present.
     *
     * @return an unmodifiable list of constraint sources; empty for an unconstrained property.
     */
    public List<ConstraintSource> getSources() {
        return sources;
    }

    /**
     * Retrieves all constraints of this property across sources, in source order.
     *
     * @return an unmodifiable list of all constraints.
     */
    public List<ConstraintMetadata> getConstraints() {
        List<ConstraintMetadata> result = new ArrayList<>();
        for (ConstraintSource source : sources) {
            result.addAll(source.getConstraints());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Determines whether this property cascades ({@code @Valid} on any source).
     *
     * @return true if the property cascades; false otherwise.
     */
    public boolean isCascaded() {
        return cascaded;
    }

    /**
     * Retrieves the container-element tree of this property.
     *
     * @return the container metadata; null if the property is not a container or
     * carries no container-element validation.
     */
    public ContainerMetadata getContainer() {
        return container;
    }

    /**
     * Retrieves the registry rule names attached via {@code @Validate}, in declaration
     * order (field source before getter source).
     *
     * @return an unmodifiable list of registry rule names.
     */
    public List<String> getValidateRuleNames() {
        return validateRuleNames;
    }

    /**
     * Reads the property's primary value off the given bean — the getter when one
     * exists, the field otherwise. This is the value cascaded into and bound on the
     * path; per-source constraint values are read via {@link ConstraintSource#getValue(Object)}.
     *
     * @param bean the bean instance.
     * @return the property value.
     */
    public Object getValue(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        return primaryAccessor.getValue(bean);
    }

    /**
     * Determines whether this property contributes any validation work (constraint
     * sources, container-element validation, cascade, or attached registry rules).
     *
     * @return true if the property has validation; false otherwise.
     */
    public boolean hasValidation() {
        if (!sources.isEmpty() || !validateRuleNames.isEmpty()) return true;
        if (cascaded) return true;
        return container != null && container.hasValidation();
    }

    @Override
    public String toString() {
        return "PropertyMetadata{name='" + name + '\''
                + ", sources=" + sources.size()
                + ", cascaded=" + cascaded
                + (container != null ? ", container=" + container.getKind() : "")
                + '}';
    }
}
