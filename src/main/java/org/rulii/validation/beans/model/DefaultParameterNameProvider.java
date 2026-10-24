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

import jakarta.validation.ParameterNameProvider;
import org.rulii.lib.spring.util.Assert;
import org.rulii.util.reflect.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The {@link ParameterNameProvider} used when none is configured: the names rulii core gives its own
 * rule parameters. In order: the name a rulii {@code @Param} annotation declares, the name the
 * compiler recorded ({@code -parameters}), the name read from the class file's local variable table
 * (debug information, which Maven and Gradle emit by default), and {@code arg0}, {@code arg1}, ...
 * when nothing else is available. The reference implementation's default stops at the second step.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class DefaultParameterNameProvider implements ParameterNameProvider {

    public DefaultParameterNameProvider() {
        super();
    }

    @Override
    public List<String> getParameterNames(Constructor<?> constructor) {
        Assert.notNull(constructor, "constructor cannot be null.");
        return names(constructor, ReflectionUtils.getParameterNames(constructor));
    }

    @Override
    public List<String> getParameterNames(Method method) {
        Assert.notNull(method, "method cannot be null.");
        return names(method, ReflectionUtils.getParameterNames(method));
    }

    private static List<String> names(Executable executable, String[] discovered) {
        Parameter[] parameters = executable.getParameters();
        List<String> result = new ArrayList<>(parameters.length);

        for (int i = 0; i < parameters.length; i++) {
            String name = discovered != null && i < discovered.length && discovered[i] != null && !discovered[i].isEmpty()
                    ? discovered[i]
                    : parameters[i].getName();
            result.add(name);
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString() {
        return "DefaultParameterNameProvider{}";
    }
}
