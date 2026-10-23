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

import jakarta.validation.ConstraintDefinitionException;
import jakarta.validation.OverridesAttribute;

import java.lang.annotation.Annotation;

/**
 * Thrown when an {@link OverridesAttribute @OverridesAttribute} on a composed constraint cannot be
 * applied: the composing constraint does not declare the targeted attribute, or the attribute types
 * differ. Same failures as the reference implementation reports for an illegal override.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class InvalidAttributeOverrideException extends ConstraintDefinitionException {

    private final Class<? extends Annotation> hostType;
    private final String hostAttribute;
    private final Class<? extends Annotation> composingType;
    private final String targetAttribute;

    /**
     * Creates the exception.
     *
     * @param hostType the composed constraint declaring the override.
     * @param hostAttribute the attribute of the composed constraint carrying the override.
     * @param composingType the composing constraint targeted by the override.
     * @param targetAttribute the attribute of the composing constraint the override targets.
     * @param reason why the override cannot be applied.
     */
    public InvalidAttributeOverrideException(Class<? extends Annotation> hostType, String hostAttribute,
                                             Class<? extends Annotation> composingType, String targetAttribute,
                                             String reason) {
        super("@OverridesAttribute on [" + hostType.getName() + "." + hostAttribute + "()] cannot override ["
                + composingType.getName() + "." + targetAttribute + "()]: " + reason);
        this.hostType = hostType;
        this.hostAttribute = hostAttribute;
        this.composingType = composingType;
        this.targetAttribute = targetAttribute;
    }

    public Class<? extends Annotation> getHostType() {
        return hostType;
    }

    public String getHostAttribute() {
        return hostAttribute;
    }

    public Class<? extends Annotation> getComposingType() {
        return composingType;
    }

    public String getTargetAttribute() {
        return targetAttribute;
    }
}
