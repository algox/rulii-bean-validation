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
import org.rulii.validation.beans.util.reflect.Members;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

/**
 * The source of a cross-parameter constraint: the method or constructor it is declared on, whose
 * parameters, as a whole, it validates.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class CrossParameterConstraintSource implements ConstraintSource {

    private final Executable executable;
    private final Class<?> owner;

    /**
     * Creates the source.
     *
     * @param executable the method or constructor.
     * @param owner the bean class the executable is read for.
     */
    public CrossParameterConstraintSource(Executable executable, Class<?> owner) {
        super();
        Assert.notNull(executable, "executable cannot be null.");
        Assert.notNull(owner, "owner cannot be null.");
        this.executable = executable;
        this.owner = owner;
    }

    public Executable getExecutable() {
        return executable;
    }

    /**
     * The bean class the executable is read for.
     *
     * @return the owner.
     */
    public Class<?> getOwner() {
        return owner;
    }

    @Override
    public Class<?> getDeclaringClass() {
        return executable.getDeclaringClass();
    }

    @Override
    public AnnotatedElement getElement() {
        return executable;
    }

    @Override
    public SourceDefinition getSourceDefinition() {
        return executable instanceof Constructor<?> constructor ? SourceDefinition.forConstructor(constructor)
                : SourceDefinition.forMethod((Method) executable);
    }

    @Override
    public String describe() {
        return Members.describe(executable) + " cross-parameter";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrossParameterConstraintSource that = (CrossParameterConstraintSource) o;
        return executable.equals(that.executable) && owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
        return 31 * executable.hashCode() + owner.hashCode();
    }

    @Override
    public String toString() {
        return "CrossParameterConstraintSource{" + describe() + '}';
    }
}
