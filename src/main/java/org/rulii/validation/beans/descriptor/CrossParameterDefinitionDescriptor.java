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

import jakarta.validation.metadata.CrossParameterDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ExecutableDefinition;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;

/**
 * The spec's {@link CrossParameterDescriptor}: the constraints validating the parameters of an
 * executable as a whole. The element class is {@code Object[]}, as the specification says.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class CrossParameterDefinitionDescriptor extends DefinitionElementDescriptor implements CrossParameterDescriptor {

    private final ExecutableDefinition executable;

    public CrossParameterDefinitionDescriptor(Class<?> beanClass, ExecutableDefinition executable) {
        super(beanClass, entries(executable));
        this.executable = executable;
    }

    private static List<Entry> entries(ExecutableDefinition executable) {
        Assert.notNull(executable, "executable cannot be null.");
        List<Entry> result = new ArrayList<>();
        ElementType elementType = executable.isConstructor() ? ElementType.CONSTRUCTOR : ElementType.METHOD;

        for (ConstraintDefinition constraint : executable.getCrossParameterConstraints()) {
            result.add(new Entry(constraint, elementType));
        }

        return result;
    }

    public ExecutableDefinition getDefinition() {
        return executable;
    }

    @Override
    public Class<?> getElementClass() {
        return Object[].class;
    }

    @Override
    public String toString() {
        return "CrossParameterDescriptor{" + executable.getName() + '}';
    }
}
