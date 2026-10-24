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

import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.BeanValidationException;

import java.lang.annotation.ElementType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The {@link TraversableResolver} of a Jakarta Persistence application: a property is reachable when
 * the persistence provider reports it loaded ({@code Persistence.getPersistenceUtil().isLoaded(bean,
 * property)}), so a lazy association that was never fetched is neither validated nor cascaded into,
 * and validation never triggers a load. Everything reachable is cascadable.
 *
 * <p>Jakarta Persistence is reached by reflection, so the library has no dependency on it; the
 * resolver can only be created when the API is on the classpath, which is how
 * {@link TraversableResolvers#getDefault()} decides to use it.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class JpaTraversableResolver implements TraversableResolver {

    /**
     * The class whose presence means Jakarta Persistence is available.
     */
    public static final String PERSISTENCE_CLASS = "jakarta.persistence.Persistence";

    private static final String PERSISTENCE_UTIL_CLASS = "jakarta.persistence.PersistenceUtil";

    private final Object persistenceUtil;
    private final Method isLoaded;

    /**
     * Creates the resolver over the persistence API visible to the context class loader, or to this
     * class's loader when there is none.
     *
     * @throws BeanValidationException if Jakarta Persistence is not available.
     */
    public JpaTraversableResolver() {
        this(TraversableResolvers.classLoader());
    }

    /**
     * Creates the resolver over the persistence API visible to the given class loader.
     *
     * @param classLoader the loader.
     * @throws BeanValidationException if Jakarta Persistence is not available through it.
     */
    public JpaTraversableResolver(ClassLoader classLoader) {
        super();
        Assert.notNull(classLoader, "classLoader cannot be null.");

        try {
            Class<?> persistence = Class.forName(PERSISTENCE_CLASS, true, classLoader);
            Class<?> util = Class.forName(PERSISTENCE_UTIL_CLASS, true, classLoader);
            this.persistenceUtil = persistence.getMethod("getPersistenceUtil").invoke(null);
            this.isLoaded = util.getMethod("isLoaded", Object.class, String.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : e;
            throw new BeanValidationException("Jakarta Persistence is not available: [" + PERSISTENCE_CLASS + "] cannot be used.", cause);
        }

        Assert.notNull(persistenceUtil, "Persistence.getPersistenceUtil() returned null.");
    }

    /**
     * Whether the persistence provider reports the property loaded; a null object is reachable.
     */
    @Override
    public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                               Path pathToTraversableObject, ElementType elementType) {
        Assert.notNull(traversableProperty, "traversableProperty cannot be null.");

        if (traversableObject == null) return true;

        try {
            return (Boolean) isLoaded.invoke(persistenceUtil, traversableObject, traversableProperty.getName());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new BeanValidationException("PersistenceUtil.isLoaded failed for property [" + traversableProperty.getName() + "].", cause);
        } catch (IllegalAccessException e) {
            throw new BeanValidationException("PersistenceUtil.isLoaded is not accessible.", e);
        }
    }

    @Override
    public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                Path pathToTraversableObject, ElementType elementType) {
        return true;
    }

    @Override
    public String toString() {
        return "JpaTraversableResolver{}";
    }
}
