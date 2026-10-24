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
package org.rulii.validation.beans.descriptor;

import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;
import jakarta.validation.metadata.ParameterDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ParameterDefinition;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The spec's {@link ParameterDescriptor} over a {@link ParameterDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ParameterDefinitionDescriptor extends DefinitionElementDescriptor implements ParameterDescriptor {

    private final ParameterDefinition parameter;

    public ParameterDefinitionDescriptor(Class<?> beanClass, ParameterDefinition parameter) {
        super(beanClass, entries(parameter));
        this.parameter = parameter;
    }

    private static List<Entry> entries(ParameterDefinition parameter) {
        Assert.notNull(parameter, "parameter cannot be null.");
        List<Entry> result = new ArrayList<>();

        for (ConstraintDefinition constraint : parameter.getConstraints()) {
            result.add(new Entry(constraint, ElementType.PARAMETER));
        }

        return result;
    }

    public ParameterDefinition getDefinition() {
        return parameter;
    }

    @Override
    public int getIndex() {
        return parameter.getIndex();
    }

    @Override
    public String getName() {
        return parameter.getName();
    }

    @Override
    public Class<?> getElementClass() {
        return parameter.getType();
    }

    @Override
    public boolean isCascaded() {
        return parameter.isCascaded();
    }

    @Override
    public Set<GroupConversionDescriptor> getGroupConversions() {
        return GroupConversionDefinitionDescriptor.describe(getDefinition().getGroupConversions());
    }

    @Override
    public Set<ContainerElementTypeDescriptor> getConstrainedContainerElementTypes() {
        return ContainerElementDefinitionDescriptor.describe(getBeanClass(), parameter.getContainerElements());
    }

    @Override
    public String toString() {
        return "ParameterDescriptor{" + parameter.getIndex() + ":" + parameter.getName() + '}';
    }
}
