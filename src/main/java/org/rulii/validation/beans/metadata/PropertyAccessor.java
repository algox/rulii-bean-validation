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
package org.rulii.validation.beans.metadata;

import org.rulii.validation.beans.BeanValidationException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Internal value accessor backing a property source. Accessibility is resolved once at
 * metadata-read time ({@code trySetAccessible} probe; failure raises
 * {@link PropertyAccessException} with the exact JPMS directive), then the member is
 * wrapped in a {@link MethodHandle} for the read path. Never exposed on the public
 * metadata API — {@code PropertyMetadata} exposes plain {@code getValue} methods.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class PropertyAccessor {

    private final Member member;
    private final MethodHandle handle;

    private PropertyAccessor(Member member, MethodHandle handle) {
        super();
        this.member = member;
        this.handle = handle;
    }

    /**
     * Creates an accessor reading the given field.
     *
     * @param beanClass the class being read (for error reporting).
     * @param field the field to read.
     * @return a new PropertyAccessor.
     * @throws PropertyAccessException if the field cannot be made accessible.
     */
    static PropertyAccessor forField(Class<?> beanClass, Field field) {
        makeAccessible(beanClass, field, field.getModifiers());

        try {
            return new PropertyAccessor(field, MethodHandles.lookup().unreflectGetter(field));
        } catch (IllegalAccessException e) {
            throw new PropertyAccessException(beanClass, field, e);
        }
    }

    /**
     * Creates an accessor invoking the given zero-arg method (getter or record accessor).
     *
     * @param beanClass the class being read (for error reporting).
     * @param method the method to invoke.
     * @return a new PropertyAccessor.
     * @throws PropertyAccessException if the method cannot be made accessible.
     */
    static PropertyAccessor forMethod(Class<?> beanClass, Method method) {
        makeAccessible(beanClass, method, method.getModifiers());

        try {
            return new PropertyAccessor(method, MethodHandles.lookup().unreflect(method));
        } catch (IllegalAccessException e) {
            throw new PropertyAccessException(beanClass, method, e);
        }
    }

    private static void makeAccessible(Class<?> beanClass, java.lang.reflect.AccessibleObject accessibleObject, int modifiers) {
        Member member = (Member) accessibleObject;
        if (Modifier.isPublic(modifiers) && Modifier.isPublic(member.getDeclaringClass().getModifiers())) return;

        try {
            if (!accessibleObject.trySetAccessible()) throw new PropertyAccessException(beanClass, member, null);
        } catch (SecurityException e) {
            throw new PropertyAccessException(beanClass, member, e);
        }
    }

    /**
     * Reads the property value off the given bean.
     *
     * @param bean the bean instance.
     * @return the property value.
     */
    Object getValue(Object bean) {
        try {
            return handle.invoke(bean);
        } catch (Throwable t) {
            throw new BeanValidationException("Failed reading [" + member.getName() + "] of ["
                    + member.getDeclaringClass().getName() + "].", t);
        }
    }

    Member getMember() {
        return member;
    }
}
