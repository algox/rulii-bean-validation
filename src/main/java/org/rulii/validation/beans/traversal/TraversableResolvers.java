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

import jakarta.validation.TraversableResolver;
import org.rulii.lib.spring.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The traversable resolvers the library provides, and the default: the {@link JpaTraversableResolver}
 * when Jakarta Persistence is on the classpath, the {@link TraverseAllTraversableResolver} otherwise,
 * as the reference implementation chooses. The default is decided once.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class TraversableResolvers {

    private static final Logger logger = LoggerFactory.getLogger(TraversableResolvers.class);
    private static final TraverseAllTraversableResolver TRAVERSE_ALL = new TraverseAllTraversableResolver();
    private static volatile TraversableResolver standard;

    private TraversableResolvers() {
        super();
    }

    /**
     * The resolver that reaches and cascades into everything.
     *
     * @return the resolver; one instance.
     */
    public static TraverseAllTraversableResolver traverseAll() {
        return TRAVERSE_ALL;
    }

    /**
     * The default resolver of this runtime, decided once against the class loader of the first call.
     *
     * @return the JPA resolver when Jakarta Persistence is available, else {@link #traverseAll()}.
     */
    public static TraversableResolver getDefault() {
        TraversableResolver result = standard;

        if (result == null) {
            synchronized (TraversableResolvers.class) {
                result = standard;
                if (result == null) {
                    result = getDefault(classLoader());
                    standard = result;
                }
            }
        }

        return result;
    }

    /**
     * The default resolver for the persistence API visible to a class loader; not cached.
     *
     * @param classLoader the loader.
     * @return the JPA resolver when Jakarta Persistence is available through it, else {@link #traverseAll()}.
     */
    public static TraversableResolver getDefault(ClassLoader classLoader) {
        Assert.notNull(classLoader, "classLoader cannot be null.");

        try {
            Class.forName(JpaTraversableResolver.PERSISTENCE_CLASS, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            logger.debug("Jakarta Persistence is not on the classpath; the traversable resolver reaches everything.");
            return TRAVERSE_ALL;
        }

        try {
            TraversableResolver result = new JpaTraversableResolver(classLoader);
            logger.debug("Jakarta Persistence found; the traversable resolver asks the persistence provider what is loaded.");
            return result;
        } catch (RuntimeException e) {
            logger.debug("Jakarta Persistence is on the classpath but cannot be used; the traversable resolver reaches everything.", e);
            return TRAVERSE_ALL;
        }
    }

    /**
     * Whether a resolver has to be asked: any resolver but the {@link #traverseAll() traverse-all} one.
     *
     * @param resolver the resolver; may be null, meaning traverse all.
     * @return true if the traversal consults it.
     */
    public static boolean consults(TraversableResolver resolver) {
        return resolver != null && !(resolver instanceof TraverseAllTraversableResolver);
    }

    static ClassLoader classLoader() {
        ClassLoader result = Thread.currentThread().getContextClassLoader();
        return result != null ? result : TraversableResolvers.class.getClassLoader();
    }
}
