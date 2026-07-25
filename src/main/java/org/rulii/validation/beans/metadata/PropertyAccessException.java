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

import java.lang.reflect.Member;

/**
 * Thrown when a bean property cannot be made accessible for validation — typically a
 * JPMS named module that does not open the bean's package. The message contains the
 * exact directive to add. There is no silent getter fallback: a field constraint reads
 * the field, so substituting an accessor could validate a different value.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class PropertyAccessException extends BeanValidationException {

    private final Class<?> beanClass;
    private final Member member;

    /**
     * Creates a new PropertyAccessException.
     *
     * @param beanClass the class being read.
     * @param member the inaccessible field or method.
     * @param cause the underlying access failure; may be null.
     */
    public PropertyAccessException(Class<?> beanClass, Member member, Throwable cause) {
        super(buildMessage(beanClass, member), cause);
        this.beanClass = beanClass;
        this.member = member;
    }

    private static String buildMessage(Class<?> beanClass, Member member) {
        String packageName = beanClass.getPackageName();
        String moduleName = beanClass.getModule().isNamed() ? beanClass.getModule().getName() : null;
        StringBuilder result = new StringBuilder();

        result.append("Unable to access [").append(member.getName()).append("] of class [")
                .append(beanClass.getName()).append("] for validation.");

        if (moduleName != null) {
            result.append(" Class is in JPMS module [").append(moduleName).append("]. Add [opens ")
                    .append(packageName).append(";] to its module-info, or run with [--add-opens ")
                    .append(moduleName).append('/').append(packageName).append("=ALL-UNNAMED].");
        }

        return result.toString();
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public Member getMember() {
        return member;
    }
}
