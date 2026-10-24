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
package org.rulii.validation.beans.util.reflect;

import org.rulii.lib.spring.util.Assert;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;

/**
 * Describes reflective members for messages.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Members {

    private Members() {
        super();
    }

    /**
     * Describes a member as {@code declaring.Class.name} for a field, {@code declaring.Class.name()}
     * for a method and {@code declaring.Class.SimpleName()} for a constructor.
     *
     * @param member the member.
     * @return the description.
     */
    public static String describe(Member member) {
        Assert.notNull(member, "member cannot be null.");
        if (member instanceof Constructor<?>) {
            return member.getDeclaringClass().getName() + "." + member.getDeclaringClass().getSimpleName() + "()";
        }
        String result = member.getDeclaringClass().getName() + "." + member.getName();
        return member instanceof Field ? result : result + "()";
    }
}
