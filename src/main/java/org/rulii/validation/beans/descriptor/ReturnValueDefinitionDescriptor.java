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
import jakarta.validation.metadata.ReturnValueDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ReturnValueDefinition;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The spec's {@link ReturnValueDescriptor} over a {@link ReturnValueDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ReturnValueDefinitionDescriptor extends DefinitionElementDescriptor implements ReturnValueDescriptor {

    private final ReturnValueDefinition returnValue;

    public ReturnValueDefinitionDescriptor(Class<?> beanClass, ReturnValueDefinition returnValue, boolean constructor) {
        super(beanClass, entries(returnValue, constructor));
        this.returnValue = returnValue;
    }

    private static List<Entry> entries(ReturnValueDefinition returnValue, boolean constructor) {
        Assert.notNull(returnValue, "returnValue cannot be null.");
        List<Entry> result = new ArrayList<>();

        for (ConstraintDefinition constraint : returnValue.getConstraints()) {
            result.add(new Entry(constraint, constructor ? ElementType.CONSTRUCTOR : ElementType.METHOD));
        }

        return result;
    }

    public ReturnValueDefinition getDefinition() {
        return returnValue;
    }

    @Override
    public Class<?> getElementClass() {
        return returnValue.getType();
    }

    @Override
    public boolean isCascaded() {
        return returnValue.isCascaded();
    }

    @Override
    public Set<GroupConversionDescriptor> getGroupConversions() {
        return GroupConversionDefinitionDescriptor.describe(getDefinition().getGroupConversions());
    }

    @Override
    public Set<ContainerElementTypeDescriptor> getConstrainedContainerElementTypes() {
        return ContainerElementDefinitionDescriptor.describe(getBeanClass(), returnValue.getContainerElements());
    }

    @Override
    public String toString() {
        return "ReturnValueDescriptor{" + returnValue.getName() + '}';
    }
}
