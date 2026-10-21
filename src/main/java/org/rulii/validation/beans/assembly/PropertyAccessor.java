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
package org.rulii.validation.beans.assembly;

import org.rulii.lib.spring.util.Assert;
import org.rulii.lib.spring.util.ReflectionUtils;
import org.rulii.model.UnrulyException;
import org.rulii.validation.beans.model.PropertyDeclaration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * Reads one property declaration off a bean: the field itself for a field declaration, the getter
 * for a getter or record component. Accessibility is settled once, when the accessor is created
 * ({@code setAccessible} only where needed, a {@link MethodHandle} behind it), so a bean in a closed
 * module fails when its class is materialized, never mid-validation.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyAccessor {

    private static final MethodType READ = MethodType.methodType(Object.class, Object.class);

    private final PropertyDeclaration declaration;
    private final MethodHandle handle;

    private PropertyAccessor(PropertyDeclaration declaration, MethodHandle handle) {
        super();
        this.declaration = declaration;
        this.handle = handle;
    }

    /**
     * Creates the accessor of a declaration.
     *
     * @param declaration the declaration.
     * @return the accessor.
     * @throws InaccessiblePropertyException if the member cannot be made accessible.
     */
    public static PropertyAccessor of(PropertyDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        Member member = declaration.getMember();
        MethodHandle handle;

        try {
            if (member instanceof Field field) {
                ReflectionUtils.makeAccessible(field);
                handle = MethodHandles.lookup().unreflectGetter(field);
            } else if (member instanceof Method method) {
                ReflectionUtils.makeAccessible(method);
                handle = MethodHandles.lookup().unreflect(method);
            } else {
                throw new UnrulyException("declaration [" + declaration.describe() + "] is neither a field nor a method.");
            }
        } catch (InaccessibleObjectException | IllegalAccessException | SecurityException e) {
            throw new InaccessiblePropertyException(declaration, e);
        }

        return new PropertyAccessor(declaration, handle.asType(READ));
    }

    /**
     * The declaration read.
     *
     * @return the declaration.
     */
    public PropertyDeclaration getDeclaration() {
        return declaration;
    }

    /**
     * Reads the declaration off the given bean.
     *
     * @param bean the bean; must be an instance of the declaring class.
     * @return the value.
     * @throws UnrulyException if a getter throws a checked exception; an unchecked one propagates as is.
     */
    public Object read(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");

        try {
            return handle.invokeExact(bean);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UnrulyException("failed to read property [" + declaration.describe() + "] of [" + bean.getClass().getName() + "]", e);
        }
    }

    @Override
    public String toString() {
        return "PropertyAccessor{" + declaration.describe() + '}';
    }
}
