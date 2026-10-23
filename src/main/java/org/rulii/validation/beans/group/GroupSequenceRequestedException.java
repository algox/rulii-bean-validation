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
 * Thrown when a requested validation group, or one of the groups it extends, is annotated with
 * {@code @GroupSequence}. Group sequences are not supported; treating the sequence as a plain group
 * would validate nothing, since constraints declare the member groups and not the sequence
 * interface, so the request fails fast instead.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class GroupSequenceRequestedException extends BeanValidationException {

    private final Class<?> group;

    public GroupSequenceRequestedException(Class<?> group) {
        super("requested validation group [" + group.getName() + "] is a @GroupSequence, which is not supported;"
                + " request the groups of the sequence directly, in the order you need.");
        this.group = group;
    }

    /**
     * The group carrying the sequence.
     *
     * @return the interface.
     */
    public Class<?> getGroup() {
        return group;
    }
}
