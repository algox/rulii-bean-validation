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
 * Definition of the return value of a method, or of the object a constructor creates: the constraints
 * declared on it, whether it is cascaded ({@code @Valid}), and the constrained positions within its
 * type. Return-value constraints accumulate over the methods a method overrides, so a return value is
 * read from one declaration per method of the chain, the validated method's first.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ReturnValueDefinition implements Definition {

    private final List<ValueDeclaration> declarations;
    private final List<ConstraintDefinition> constraints;
    private final List<ContainerElementDefinition> containerElements;
    private final List<ValueDeclaration> cascadedDeclarations;

    private ReturnValueDefinition(List<ValueDeclaration> declarations, List<ConstraintDefinition> constraints,
                                  List<ContainerElementDefinition> containerElements, List<ValueDeclaration> cascadedDeclarations) {
        super();
        this.declarations = declarations;
        this.constraints = constraints;
        this.containerElements = containerElements;
        this.cascadedDeclarations = cascadedDeclarations;
    }

    /**
     * Loads a return value from its declarations.
     *
     * @param declarations the return-value declarations, the validated method's first.
     * @param registry the value extractors available at validation time.
     * @return the definition.
     * @throws GroupConversionNotSupportedException if {@code @ConvertGroup} is declared on the return value.
     */
    public static ReturnValueDefinition load(List<ValueDeclaration> declarations, ValueExtractorRegistry registry) {
        Assert.notEmpty(declarations, "declarations cannot be empty.");
        Assert.notNull(registry, "registry cannot be null.");
        List<ConstraintDefinition> constraints = new ArrayList<>();
        List<ContainerElementDefinition> elements = Collections.emptyList();
        List<ValueDeclaration> cascaded = new ArrayList<>(1);

        for (ValueDeclaration declaration : declarations) {
            Assert.isTrue(declaration.getKind() == ValueDeclaration.Kind.RETURN_VALUE || declaration.getKind() == ValueDeclaration.Kind.GETTER,
                    "[" + declaration.describe() + "] is not a return value.");
            DeclarationReader read = DeclarationReader.read(declaration, registry);
            constraints.addAll(read.constraints());
            elements = ContainerElementDefinition.merge(elements, read.elements());
            if (read.cascaded()) cascaded.add(declaration);
        }

        return new ReturnValueDefinition(List.copyOf(declarations), Collections.unmodifiableList(constraints), elements,
                Collections.unmodifiableList(cascaded));
    }

    /**
     * The name of the executable whose return value this is.
     */
    @Override
    public String getName() {
        return declarations.get(0).getName();
    }

    @Override
    public SourceDefinition getSource() {
        return declarations.get(0).getSourceDefinition();
    }

    /**
     * The declarations the return value is read from, the validated method's first.
     *
     * @return the declarations.
     */
    public List<ValueDeclaration> getDeclarations() {
        return declarations;
    }

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
     * Whether anything is to be validated for the return value.
     *
     * @return true if constrained, cascaded or holding constrained positions.
     */
    public boolean isConstrained() {
        return !constraints.isEmpty() || !containerElements.isEmpty() || isCascaded();
    }

    @Override
    public String toString() {
        return "ReturnValueDefinition{" + getName() + ", constraints=" + constraints.size() + ", elements="
                + containerElements.size() + ", cascaded=" + isCascaded() + '}';
    }
}
