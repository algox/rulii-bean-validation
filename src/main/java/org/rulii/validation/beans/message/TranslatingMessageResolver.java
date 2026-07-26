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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Decorates a {@link MessageResolver} whose text is jakarta-syntax (user bundles,
 * {@code ValidationMessages}), translating whatever it resolves at render time. This is
 * the render-time half of the exactly-once translation contract (literal annotation
 * messages are translated at materialization).
 *
 * <p>The delegate is consulted WITHOUT the caller's default message: a resolver miss
 * returns the caller's default untouched. The default on this path is a core rule's
 * rulii-native {@code DEFAULT_MESSAGE} — positional {@code {0}} MessageFormat syntax
 * that translation would quote into literal text.</p>
 *
 * <p>Attribute-name mappings, keyed by error code, bridge jakarta attribute names to
 * the parameter names the rules actually emit ({@code {value}} in a bundle entry for
 * {@code jakarta.validation.constraints.Min.message} must become {@code ${min}}) —
 * see {@code ConstraintMapperRegistry#getAttributeNameMappingsByCode()}.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class TranslatingMessageResolver implements MessageResolver {

    private final MessageResolver delegate;
    private final Map<String, Map<String, String>> attributeNameMappingsByCode;

    /**
     * Creates a translating resolver around the given jakarta-syntax delegate with no
     * attribute-name mappings.
     *
     * @param delegate the resolver supplying jakarta-syntax text.
     */
    public TranslatingMessageResolver(MessageResolver delegate) {
        this(delegate, Collections.emptyMap());
    }

    /**
     * Creates a translating resolver around the given jakarta-syntax delegate.
     *
     * @param delegate the resolver supplying jakarta-syntax text.
     * @param attributeNameMappingsByCode attribute-name mappings keyed by error code,
     *                                    applied when translating that code's text.
     */
    public TranslatingMessageResolver(MessageResolver delegate, Map<String, Map<String, String>> attributeNameMappingsByCode) {
        super();
        Assert.notNull(delegate, "delegate cannot be null.");
        Assert.notNull(attributeNameMappingsByCode, "attributeNameMappingsByCode cannot be null.");
        this.delegate = delegate;
        this.attributeNameMappingsByCode = Collections.unmodifiableMap(new LinkedHashMap<>(attributeNameMappingsByCode));
    }

    /**
     * Resolves the code through the delegate and translates the resolved jakarta text;
     * on a delegate miss the default message is returned untranslated (rulii-native).
     *
     * @param locale the locale used to resolve the message.
     * @param code the code of the message to resolve.
     * @param defaultMessage the rulii-native default when the delegate has no text.
     * @return the translated resolved message, or the untouched default message.
     */
    @Override
    public String resolve(Locale locale, String code, String defaultMessage) {
        String resolved = delegate.resolve(locale, code, null);
        if (resolved == null) return defaultMessage;
        return JakartaMessageTranslator.translate(resolved,
                attributeNameMappingsByCode.getOrDefault(code, Collections.emptyMap()));
    }

    @Override
    public String toString() {
        return "TranslatingMessageResolver{delegate=" + delegate + '}';
    }
}
