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
package org.rulii.validation.beans.group;

import org.rulii.validation.beans.BeanValidationException;

/**
 * Thrown when a requested validation group is not an interface. Groups are interfaces by
 * specification; any other class cannot be a group and would match no constraint, so the request
 * fails loudly instead of validating nothing.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class InvalidGroupException extends BeanValidationException {

    private final Class<?> group;

    public InvalidGroupException(Class<?> group) {
        super("requested validation group [" + group.getName() + "] is not an interface; groups must be interfaces.");
        this.group = group;
    }

    /**
     * The offending group.
     *
     * @return the class.
     */
    public Class<?> getGroup() {
        return group;
    }
}
