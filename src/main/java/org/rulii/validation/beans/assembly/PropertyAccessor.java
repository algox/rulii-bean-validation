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
 * Reads one property declaration (a field or a getter) off a bean through a {@link MethodHandle}.
 * The member is made accessible once, when the handle is resolved.
 *
 * <p>{@link #of(PropertyDeclaration)} resolves the handle immediately, so a declaration the
 * validation reads fails fast if it cannot be read. {@link #lazy(PropertyDeclaration)} resolves it
 * on the first read: for a property nothing in the library reads, which only a user rule might
 * ask for by name, an inaccessible member (a JDK class reached through {@code @Valid}, say) must not
 * fail the class.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PropertyAccessor {

    private static final MethodType READ = MethodType.methodType(Object.class, Object.class);

    private final PropertyDeclaration declaration;
    private volatile MethodHandle handle;

    private PropertyAccessor(PropertyDeclaration declaration, MethodHandle handle) {
        super();
        this.declaration = declaration;
        this.handle = handle;
    }

    /**
     * An accessor resolved now.
     *
     * @param declaration the declaration to read.
     * @return the accessor.
     * @throws InaccessiblePropertyException if the member cannot be made accessible.
     */
    public static PropertyAccessor of(PropertyDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        return new PropertyAccessor(declaration, resolve(declaration));
    }

    /**
     * An accessor resolved on its first read.
     *
     * @param declaration the declaration to read.
     * @return the accessor.
     */
    public static PropertyAccessor lazy(PropertyDeclaration declaration) {
        Assert.notNull(declaration, "declaration cannot be null.");
        return new PropertyAccessor(declaration, null);
    }

    private static MethodHandle resolve(PropertyDeclaration declaration) {
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

        return handle.asType(READ);
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
     * Whether the handle has been resolved.
     *
     * @return true once the member is accessible and the handle built.
     */
    public boolean isResolved() {
        return handle != null;
    }

    /**
     * Reads the property.
     *
     * @param bean the bean.
     * @return the value.
     * @throws InaccessiblePropertyException if the member cannot be made accessible (lazy accessors).
     * @throws UnrulyException if the read itself fails.
     */
    public Object read(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        MethodHandle resolved = handle;

        if (resolved == null) {
            resolved = resolve(declaration);
            handle = resolved;
        }

        try {
            return resolved.invokeExact(bean);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UnrulyException("failed to read property [" + declaration.describe() + "] of [" + bean.getClass().getName() + "]", e);
        }
    }

    @Override
    public String toString() {
        return "PropertyAccessor{" + declaration.describe() + (handle == null ? ", unresolved" : "") + '}';
    }
}
