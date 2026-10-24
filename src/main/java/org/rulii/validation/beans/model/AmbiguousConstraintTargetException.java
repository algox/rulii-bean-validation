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

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;

/**
 * Thrown when a constraint declared on a method could validate either its parameters as a whole or
 * its return value (its validators target both), the method has both, and the constraint does not say
 * which with {@code validationAppliesTo}. Raised when the bean class is read.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class AmbiguousConstraintTargetException extends ConstraintDeclarationException {

    private static final long serialVersionUID = 0L;

    private final Executable executable;
    private final Class<? extends Annotation> annotationType;

    /**
     * Creates the exception.
     *
     * @param executable the method the constraint is declared on.
     * @param annotationType the constraint annotation type.
     */
    public AmbiguousConstraintTargetException(Executable executable, Class<? extends Annotation> annotationType) {
        super("constraint [" + name(annotationType) + "] on [" + Members.describe(executable) + "] can validate the parameters or"
                + " the return value, and the method has both: declare validationAppliesTo = ConstraintTarget.PARAMETERS or"
                + " ConstraintTarget.RETURN_VALUE on the constraint.");
        this.executable = executable;
        this.annotationType = annotationType;
    }

    private static String name(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        return annotationType.getName();
    }

    public Executable getExecutable() {
        return executable;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }
}
