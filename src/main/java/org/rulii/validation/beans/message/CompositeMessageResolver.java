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
package org.rulii.validation.beans.message;

import org.rulii.lib.spring.util.Assert;
import org.rulii.text.MessageResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Chains {@link MessageResolver}s: the first resolver that produces text for a code
 * wins; the default message is only reached when every resolver misses. Core's
 * composite chains resource bundles — this one chains arbitrary resolvers, which the
 * R12.3 order (user-supplied resolver, then the {@code ValidationMessages} bundle,
 * then the rule's built-in default) requires.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class CompositeMessageResolver implements MessageResolver {

    private final List<MessageResolver> resolvers;

    /**
     * Creates a composite over the given resolvers, consulted in order.
     *
     * @param resolvers the resolvers to chain.
     */
    public CompositeMessageResolver(List<MessageResolver> resolvers) {
        super();
        Assert.notNull(resolvers, "resolvers cannot be null.");
        Assert.isTrue(!resolvers.isEmpty(), "resolvers cannot be empty.");
        Assert.noNullElements(resolvers, "resolvers cannot contain nulls.");
        this.resolvers = Collections.unmodifiableList(resolvers);
    }

    /**
     * Creates a composite over the given resolvers, consulted in order.
     *
     * @param resolvers the resolvers to chain.
     */
    public CompositeMessageResolver(MessageResolver... resolvers) {
        this(resolvers != null ? Arrays.asList(resolvers) : null);
    }

    /**
     * Resolves the code through the chain; first non-null result wins.
     *
     * @param locale the locale used to resolve the message.
     * @param code the code of the message to resolve.
     * @param defaultMessage the default when every resolver misses.
     * @return the first resolved message, or the default message.
     */
    @Override
    public String resolve(Locale locale, String code, String defaultMessage) {
        for (MessageResolver resolver : resolvers) {
            String result = resolver.resolve(locale, code, null);
            if (result != null) return result;
        }

        return defaultMessage;
    }

    @Override
    public String toString() {
        return "CompositeMessageResolver{resolvers=" + resolvers + '}';
    }
}
