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
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Definition of one parameter of a method or constructor: its name (from the configured
 * {@code ParameterNameProvider}), the constraints declared on it, whether it is cascaded
 * ({@code @Valid}), and the constrained positions within its type ({@code List<@NotBlank String>}).
 *
 * <p>A parameter is read from the declaration of the method of the overriding chain that is allowed
 * to constrain it (see {@link ExecutableDefinition}): the validated method's own, or the highest
 * overridden method declaring constraints, since a subtype may only repeat them.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ParameterDefinition implements Definition {

    private final int index;
    private final String name;
    private final List<ValueDeclaration> declarations;
    private final List<ConstraintDefinition> constraints;
    private final List<ContainerElementDefinition> containerElements;
    private final List<ValueDeclaration> cascadedDeclarations;
    private final GroupConversions groupConversions;

    private ParameterDefinition(int index, String name, List<ValueDeclaration> declarations, List<ConstraintDefinition> constraints,
                                List<ContainerElementDefinition> containerElements, List<ValueDeclaration> cascadedDeclarations, GroupConversions groupConversions) {
        super();
        this.index = index;
        this.name = name;
        this.declarations = declarations;
        this.constraints = constraints;
        this.containerElements = containerElements;
        this.cascadedDeclarations = cascadedDeclarations;
        this.groupConversions = groupConversions;
    }

    /**
     * Loads a parameter from its declarations.
     *
     * @param declarations the declarations of the parameter, all of the same index; the first provides
     *                     the name and the type.
     * @param registry the value extractors available at validation time.
     * @return the parameter definition.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared on the parameter.
     */
    public static ParameterDefinition load(List<ValueDeclaration> declarations, ValueExtractorRegistry registry) {
        Assert.notEmpty(declarations, "declarations cannot be empty.");
        Assert.notNull(registry, "registry cannot be null.");
        ValueDeclaration primary = declarations.get(0);
        List<ConstraintDefinition> constraints = new ArrayList<>();
        List<ContainerElementDefinition> elements = Collections.emptyList();
        List<ValueDeclaration> cascaded = new ArrayList<>(1);
        GroupConversions conversions = GroupConversions.NONE;

        for (ValueDeclaration declaration : declarations) {
            Assert.isTrue(declaration.getKind() == ValueDeclaration.Kind.PARAMETER, "[" + declaration.describe() + "] is not a parameter.");
            Assert.isTrue(declaration.getParameterIndex() == primary.getParameterIndex(), "[" + declaration.describe()
                    + "] is not parameter [" + primary.getParameterIndex() + "].");
            DeclarationReader read = DeclarationReader.read(declaration, registry);
            constraints.addAll(read.constraints());
            elements = ContainerElementDefinition.merge(elements, read.elements());
            if (read.cascaded()) cascaded.add(declaration);
            conversions = conversions.merge(read.groupConversions(), declaration.describe());
        }

        return new ParameterDefinition(primary.getParameterIndex(), primary.getName(), List.copyOf(declarations),
                Collections.unmodifiableList(constraints), elements, Collections.unmodifiableList(cascaded), conversions);
    }

    /**
     * The parameter name, as the configured {@code ParameterNameProvider} gives it.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public SourceDefinition getSource() {
        return declarations.get(0).getSourceDefinition();
    }

    public int getIndex() {
        return index;
    }

    /**
     * The declarations the parameter is read from, the one on the validated method first.
     *
     * @return the declarations.
     */
    public List<ValueDeclaration> getDeclarations() {
        return declarations;
    }

    /**
     * The declaration on the validated method.
     *
     * @return the primary declaration.
     */
    public ValueDeclaration getPrimaryDeclaration() {
        return declarations.get(0);
    }

    public Class<?> getType() {
        return declarations.get(0).getType();
    }

    public Type getGenericType() {
        return declarations.get(0).getGenericType();
    }

    public List<ConstraintDefinition> getConstraints() {
        return constraints;
    }

    public List<ContainerElementDefinition> getContainerElements() {
        return containerElements;
    }

    public boolean isCascaded() {
        return !cascadedDeclarations.isEmpty();
    }

    public List<ValueDeclaration> getCascadedDeclarations() {
        return cascadedDeclarations;
    }

    /**
     * The {@code @ConvertGroup} conversions of the cascade.
     *
     * @return the conversions; {@link GroupConversions#NONE} when there are none.
     */
    public GroupConversions getGroupConversions() {
        return groupConversions;
    }

    /**
     * Whether anything is to be validated for the parameter.
     *
     * @return true if constrained, cascaded or holding constrained positions.
     */
    public boolean isConstrained() {
        return !constraints.isEmpty() || !containerElements.isEmpty() || isCascaded();
    }

    @Override
    public String toString() {
        return "ParameterDefinition{" + index + ":" + name + ", constraints=" + constraints.size() + ", elements="
                + containerElements.size() + ", cascaded=" + isCascaded() + '}';
    }
}
