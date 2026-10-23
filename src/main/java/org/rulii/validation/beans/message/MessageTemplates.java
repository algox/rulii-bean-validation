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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Translation of Bean Validation message templates into rulii message templates.
 *
 * <p>A jakarta template interpolates {@code {attr}} message parameters from the constraint's
 * attributes (and from resource bundles), uses {@code ${...}} for EL expressions, and escapes with a
 * backslash ({@code \{ \} \$ \\}). A rulii template names its parameters as {@code ${name}} and is
 * then formatted by {@link java.text.MessageFormat}, where a literal brace or apostrophe must be
 * quoted. {@link #translate} bridges the two, once, at materialization:</p>
 * <ul>
 *     <li>{@code {attr}} with an identifier becomes {@code ${param}}, the parameter name taken from the
 *     given mapping when the rule reports the attribute under a different name ({@code {regexp}} to
 *     {@code ${pattern}}), else the attribute name itself;</li>
 *     <li>{@code {some.key}}, a message parameter that is not an identifier, is a bundle reference: it
 *     becomes the placeholder {@code ${some.key}}, which the render-time formatter expands against the
 *     bundles ({@link BeanValidationMessageFormatter});</li>
 *     <li>{@code ${validatedValue}} becomes {@code ${<value name>}}, the parameter rulii rules report the
 *     offending value under; any other bare identifier in {@code ${...}} is treated as a parameter;</li>
 *     <li>every other {@code ${...}} is an EL expression: unsupported, left verbatim for the render-time
 *     formatter to shield;</li>
 *     <li>escaped characters and literal text are MessageFormat-quoted where needed.</li>
 * </ul>
 *
 * <p>The translation is not idempotent on quoted text; a template is translated exactly once.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MessageTemplates {

    /**
     * The EL variable Bean Validation exposes the validated value under.
     */
    public static final String VALIDATED_VALUE = "validatedValue";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z0-9_$]+)+");

    private MessageTemplates() {
        super();
    }

    /**
     * Whether the template is a single message-bundle key reference such as
     * {@code {jakarta.validation.constraints.NotNull.message}} or {@code {my.key}}: exactly one
     * {@code {...}} with nothing around it, whose content is not an attribute of the constraint.
     *
     * @param template the message template.
     * @param attributeNames the attribute names of the constraint annotation.
     * @return true if the template references a key rather than spelling a message.
     */
    public static boolean isTemplateKey(String template, Collection<String> attributeNames) {
        Assert.notNull(template, "template cannot be null.");
        Assert.notNull(attributeNames, "attributeNames cannot be null.");
        String key = templateKey(template);
        return key != null && !attributeNames.contains(key);
    }

    /**
     * The key referenced by a template that is a single key reference.
     *
     * @param template the message template.
     * @return the key without braces; null if the template is not a single reference.
     */
    public static String templateKey(String template) {
        Assert.notNull(template, "template cannot be null.");
        String text = template.trim();

        if (text.length() < 3 || text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') return null;

        String key = text.substring(1, text.length() - 1).trim();

        if (key.isEmpty()) return null;

        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '{' || c == '}' || c == '$' || c == '\\' || Character.isWhitespace(c)) return null;
        }

        return key;
    }

    /**
     * Translates a jakarta message template into a rulii one; see the class description.
     *
     * @param template the jakarta template.
     * @param parameterNames attribute name to rule parameter name, for attributes the rule reports
     *                       under a different name; may be empty.
     * @param valueName the parameter the rule reports the validated value under.
     * @return the rulii template.
     */
    public static String translate(String template, Map<String, String> parameterNames, String valueName) {
        Assert.notNull(template, "template cannot be null.");
        Assert.notNull(parameterNames, "parameterNames cannot be null.");
        Assert.hasText(valueName, "valueName cannot be empty/null.");

        StringBuilder result = new StringBuilder(template.length() + 16);
        int length = template.length();
        int i = 0;

        while (i < length) {
            char c = template.charAt(i);

            if (c == '\\' && i + 1 < length) {
                literal(result, template.charAt(i + 1));
                i += 2;
            } else if (c == '$' && i + 1 < length && template.charAt(i + 1) == '{') {
                int end = template.indexOf('}', i + 2);

                if (end < 0) {
                    literal(result, c);
                    i++;
                    continue;
                }

                String content = template.substring(i + 2, end).trim();

                if (content.equals(VALIDATED_VALUE)) {
                    placeholder(result, valueName);
                } else if (IDENTIFIER.matcher(content).matches()) {
                    placeholder(result, parameterNames.getOrDefault(content, content));
                } else {
                    // EL expression: unsupported, left verbatim for the render-time formatter.
                    result.append(template, i, end + 1);
                }

                i = end + 1;
            } else if (c == '{') {
                int end = template.indexOf('}', i + 1);

                if (end < 0) {
                    literal(result, c);
                    i++;
                    continue;
                }

                String content = template.substring(i + 1, end).trim();

                if (content.equals(VALIDATED_VALUE)) {
                    placeholder(result, valueName);
                } else if (IDENTIFIER.matcher(content).matches()) {
                    placeholder(result, parameterNames.getOrDefault(content, content));
                } else if (isKey(content)) {
                    // Bundle key: a placeholder the render-time formatter expands against the bundles.
                    placeholder(result, content);
                } else {
                    // Arbitrary text: keep verbatim, quoted for MessageFormat.
                    literal(result, '{');
                    for (int j = i + 1; j < end; j++) {
                        literal(result, template.charAt(j));
                    }
                    literal(result, '}');
                }

                i = end + 1;
            } else {
                literal(result, c);
                i++;
            }
        }

        return result.toString();
    }

    /**
     * The attribute names of a constraint annotation type.
     *
     * @param annotationType the annotation type.
     * @return the names of its attributes.
     */
    /**
     * Whether a text is a message key: a dotted name such as {@code my.key} or
     * {@code jakarta.validation.constraints.Size.message}, as opposed to an attribute name or arbitrary text.
     *
     * @param text the text.
     * @return true if it is a dotted key.
     */
    public static boolean isKey(String text) {
        return text != null && KEY.matcher(text).matches();
    }

    public static Set<String> attributeNames(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Set<String> result = new LinkedHashSet<>();
        for (Method method : annotationType.getDeclaredMethods()) {
            result.add(method.getName());
        }
        return result;
    }

    private static void placeholder(StringBuilder result, String name) {
        result.append("${").append(name).append('}');
    }

    private static void literal(StringBuilder result, char c) {
        switch (c) {
            case '\'' -> result.append("''");
            case '{' -> result.append("'{'");
            case '}' -> result.append("'}'");
            default -> result.append(c);
        }
    }
}
