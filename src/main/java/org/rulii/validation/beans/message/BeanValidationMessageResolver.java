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

import jakarta.validation.Constraint;
import org.rulii.lib.spring.util.Assert;
import org.rulii.text.MessageResolver;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A rulii {@link MessageResolver} that speaks Bean Validation. For an error code it looks up, in
 * order, the user's {@code ValidationMessages} bundle (the name the specification reserves, at the root
 * of the classpath, with locale variants), the resolver it wraps (a rulii user's own bundles), and this
 * library's default bundle. When the code is a constraint's default message key, the text found is
 * Bean Validation syntax: {@code {key}} references are expanded recursively against the same sources,
 * then the text is translated into a rulii template, so
 * {@code must match "{regexp}"} renders through the rule's {@code pattern} parameter. Any other code
 * (a {@code @Validate} rule's, for instance) resolves through the same sources but is returned as found,
 * since its templates are rulii's own.
 * <p>
 * Resolved templates are cached per locale and code; bundles are read through the class loader given,
 * the context class loader by default.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidationMessageResolver implements MessageResolver {

    /**
     * Base name of the user's bundle, reserved by the specification.
     */
    public static final String USER_BUNDLE = "ValidationMessages";

    /**
     * Base name of this library's default bundle.
     */
    public static final String DEFAULT_BUNDLE = "org.rulii.validation.beans.ValidationMessages";

    /**
     * Package prefix of this library's constraints, whose keys are also looked up under {@link #REFERENCE_PREFIX} in
     * the user's bundle.
     */
    public static final String LIBRARY_PREFIX = "org.rulii.validation.beans.constraints.";

    /**
     * Package prefix of the reference implementation's constraints: an override written for one of them applies to
     * the library constraint of the same name.
     */
    public static final String REFERENCE_PREFIX = "org.hibernate.validator.constraints.";

    private static final String MESSAGE_KEY_SUFFIX = ".message";
    private static final int MAX_DEPTH = 8;

    private final MessageResolver delegate;
    private final ClassLoader classLoader;
    private final ConcurrentMap<String, Boolean> constraintKeys = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> templates = new ConcurrentHashMap<>();

    /**
     * Creates the resolver.
     *
     * @param delegate the resolver to consult after the user's bundle; null for none.
     */
    public BeanValidationMessageResolver(MessageResolver delegate) {
        this(delegate, null);
    }

    /**
     * Creates the resolver.
     *
     * @param delegate the resolver to consult after the user's bundle; null for none.
     * @param classLoader the class loader the user's bundle is read and the constraint annotation types
     *                    are looked up through; null for the context class loader, falling back to this class's.
     */
    public BeanValidationMessageResolver(MessageResolver delegate, ClassLoader classLoader) {
        super();
        this.delegate = delegate;
        ClassLoader context = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        this.classLoader = context != null ? context : BeanValidationMessageResolver.class.getClassLoader();
    }

    /**
     * The resolver consulted after the user's bundle.
     *
     * @return the delegate; null when none.
     */
    public MessageResolver getDelegate() {
        return delegate;
    }

    @Override
    public String resolve(Locale locale, String code, String defaultMessage) {
        if (code == null) return defaultMessage;

        Locale effective = locale != null ? locale : Locale.getDefault();
        String key = effective.toLanguageTag() + '|' + code;
        String result = templates.get(key);

        if (result == null) {
            result = render(effective, code);
            if (result != null) templates.put(key, result);
        }

        return result != null ? result : defaultMessage;
    }

    /**
     * The text a code resolves to, as found: the user's bundle, then the delegate, then the default
     * bundle. No expansion, no translation.
     *
     * @param locale the locale.
     * @param code the code.
     * @return the text; null when no source has it.
     */
    public String lookup(Locale locale, String code) {
        Assert.notNull(code, "code cannot be null.");
        Locale effective = locale != null ? locale : Locale.getDefault();
        String result = fromBundle(USER_BUNDLE, effective, code, classLoader);

        if (result == null && code.startsWith(LIBRARY_PREFIX)) {
            result = fromBundle(USER_BUNDLE, effective, REFERENCE_PREFIX + code.substring(LIBRARY_PREFIX.length()), classLoader);
        }

        if (result == null && delegate != null) result = delegate.resolve(effective, code, null);
        if (result == null) result = fromBundle(DEFAULT_BUNDLE, effective, code, BeanValidationMessageResolver.class.getClassLoader());

        return result;
    }

    private String render(Locale locale, String code) {
        String text = lookup(locale, code);

        if (text == null) return null;

        if (!isConstraintKey(code)) return text;

        return MessageTemplates.translate(expand(locale, text, 0), MessageTemplates.VALIDATED_VALUE);
    }

    /**
     * Expands the {@code {key}} references of a Bean Validation text against the message sources,
     * recursively, leaving unknown keys and attribute references in place.
     */
    String expand(Locale locale, String text, int depth) {
        if (depth >= MAX_DEPTH || text.indexOf('{') < 0) return text;

        StringBuilder result = new StringBuilder(text.length() + 16);
        int length = text.length();
        int i = 0;

        while (i < length) {
            char c = text.charAt(i);

            if (c == '\\' && i + 1 < length) {
                result.append(c).append(text.charAt(i + 1));
                i += 2;
            } else if (c == '{') {
                int end = text.indexOf('}', i + 1);
                String key = end > i ? MessageTemplates.templateKey(text.substring(i, end + 1)) : null;
                String replacement = MessageTemplates.isKey(key) ? lookup(locale, key) : null;

                if (replacement != null) {
                    result.append(expand(locale, replacement, depth + 1));
                    i = end + 1;
                } else {
                    result.append(c);
                    i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Whether a code is a constraint's default message key ({@code <annotation>.message} for a
     * {@code @Constraint} annotation type), whose text is a jakarta template to translate; any other
     * code's text is returned as found.
     */
    private boolean isConstraintKey(String code) {
        return constraintKeys.computeIfAbsent(code, this::lookupConstraintKey);
    }

    private boolean lookupConstraintKey(String code) {
        if (!code.endsWith(MESSAGE_KEY_SUFFIX)) return false;

        try {
            Class<?> type = Class.forName(code.substring(0, code.length() - MESSAGE_KEY_SUFFIX.length()), false, classLoader);
            return type.isAnnotation() && type.isAnnotationPresent(Constraint.class);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static String fromBundle(String baseName, Locale locale, String code, ClassLoader loader) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale, loader);
            return bundle.containsKey(code) ? bundle.getString(code) : null;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "BeanValidationMessageResolver{delegate=" + delegate + '}';
    }
}
