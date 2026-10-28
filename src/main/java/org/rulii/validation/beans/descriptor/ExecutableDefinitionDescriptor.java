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

import jakarta.validation.metadata.ConstructorDescriptor;
import jakarta.validation.metadata.CrossParameterDescriptor;
import jakarta.validation.metadata.MethodDescriptor;
import jakarta.validation.metadata.ParameterDescriptor;
import jakarta.validation.metadata.ReturnValueDescriptor;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ParameterDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The spec's {@link MethodDescriptor} and {@link ConstructorDescriptor} over an
 * {@link ExecutableDefinition}. As the specification says of executable descriptors, it declares no
 * constraints of its own: they sit on its parameter, return-value and cross-parameter descriptors.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ExecutableDefinitionDescriptor extends DefinitionElementDescriptor implements MethodDescriptor, ConstructorDescriptor {

    private final ExecutableDefinition executable;

    public ExecutableDefinitionDescriptor(Class<?> beanClass, ExecutableDefinition executable) {
        super(beanClass, List.of());
        Assert.notNull(executable, "executable cannot be null.");
        this.executable = executable;
    }

    public ExecutableDefinition getDefinition() {
        return executable;
    }

    @Override
    public String getName() {
        return executable.getName();
    }

    /**
     * The return type of the method, or the constructed class.
     */
    @Override
    public Class<?> getElementClass() {
        return executable.isConstructor() ? executable.getExecutable().getDeclaringClass() : ((Method) executable.getExecutable()).getReturnType();
    }

    @Override
    public List<ParameterDescriptor> getParameterDescriptors() {
        List<ParameterDescriptor> result = new ArrayList<>();

        for (ParameterDefinition parameter : executable.getParameters()) {
            result.add(new ParameterDefinitionDescriptor(getBeanClass(), parameter));
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public CrossParameterDescriptor getCrossParameterDescriptor() {
        return new CrossParameterDefinitionDescriptor(getBeanClass(), executable);
    }

    /**
     * The return value descriptor.
     *
     * @return the descriptor; null for a {@code void} method.
     */
    /**
     * The return value's descriptor; for a {@code void} method, one representing {@code void}, as the
     * specification has it.
     */
    @Override
    public ReturnValueDescriptor getReturnValueDescriptor() {
        return executable.getReturnValue() != null
                ? new ReturnValueDefinitionDescriptor(getBeanClass(), executable.getReturnValue(), executable.isConstructor())
                : new ReturnValueDefinitionDescriptor(getBeanClass());
    }

    /**
     * As the specification defines it: a constraint or cascade on a parameter, or a cross-parameter
     * constraint; the library's own {@code @Validate} and {@code @Expr} rules do not count.
     */
    @Override
    public boolean hasConstrainedParameters() {
        return executable.hasSpecConstrainedParameters();
    }

    /**
     * As the specification defines it: a constraint or cascade on the return value; the library's own
     * {@code @Validate} and {@code @Expr} rules do not count.
     */
    @Override
    public boolean hasConstrainedReturnValue() {
        return executable.hasSpecConstrainedReturnValue();
    }

    @Override
    public String toString() {
        return (executable.isConstructor() ? "ConstructorDescriptor{" : "MethodDescriptor{") + getBeanClass().getName() + "." + executable.getName() + '}';
    }
}
