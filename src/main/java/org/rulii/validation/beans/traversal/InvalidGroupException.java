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
package org.rulii.validation.beans.traversal;

import org.rulii.validation.beans.BeanValidationException;

/**
 * Raised when a requested validation group is not an interface (R6.2) — groups are
 * marker interfaces by definition.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class InvalidGroupException extends BeanValidationException {

    private final Class<?> group;

    public InvalidGroupException(Class<?> group) {
        super("Invalid validation group [" + (group != null ? group.getName() : "null")
                + "]. Groups must be interfaces.");
        this.group = group;
    }

    public Class<?> getGroup() {
        return group;
    }
}
