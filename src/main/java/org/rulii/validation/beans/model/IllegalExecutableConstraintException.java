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

import jakarta.validation.ConstraintDeclarationException;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.util.reflect.Members;

import java.lang.reflect.Executable;

/**
 * Thrown when a method or constructor is constrained in a way the specification forbids: a
 * return-value constraint or {@code @Valid} on a {@code void} method, a cross-parameter constraint on
 * an executable without parameters, parameter or cross-parameter constraints added by a method
 * overriding or implementing another, the same declared in parallel types, {@code @Validate} on a
 * method that is not a getter, or a cross-parameter constraint whose mapper or validators cannot
 * validate parameters. Raised when the bean class is read.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class IllegalExecutableConstraintException extends ConstraintDeclarationException {

    private static final long serialVersionUID = 0L;

    private final Executable executable;

    /**
     * Creates the exception.
     *
     * @param executable the method or constructor concerned.
     * @param message what is wrong with it.
     */
    public IllegalExecutableConstraintException(Executable executable, String message) {
        super("[" + describe(executable) + "] " + message);
        this.executable = executable;
    }

    private static String describe(Executable executable) {
        Assert.notNull(executable, "executable cannot be null.");
        return Members.describe(executable);
    }

    /**
     * The method or constructor concerned.
     *
     * @return the executable.
     */
    public Executable getExecutable() {
        return executable;
    }
}
