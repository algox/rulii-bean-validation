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

import java.util.Collections;
import java.util.Map;

/**
 * Translates jakarta message templates into rulii/MessageFormat templates. The output
 * feeds core's formatting pipeline (placeholder parsing, then
 * {@code java.text.MessageFormat}), which throws on untranslated jakarta syntax such as
 * {@code {min}} — translation is a correctness requirement, not cosmetics.
 *
 * <p>Translation rules:</p>
 * <ul>
 *   <li>A parameter whose content is a bare identifier — {@code {min}} — becomes the
 *   named placeholder {@code ${min}}, renamed through the attribute-name mapping when
 *   the rule parameter name differs from the jakarta attribute name. Every emitted
 *   placeholder must be backed by a rule parameter at render time; core throws on
 *   unresolved placeholders.</li>
 *   <li>{@code ${min}} (bare identifier) is already a rulii placeholder and is kept,
 *   the mapping still applying — already-translated text passes through safely.</li>
 *   <li>A dollar-brace expression that is not a bare identifier is EL, which is never
 *   evaluated (R12.5). It is shielded as {@code $'{expression}'}: the quote between the
 *   dollar sign and the brace hides it from the placeholder parser, and MessageFormat
 *   emits the braced text verbatim.</li>
 *   <li>A parameter whose content is not a bare identifier — a nested bundle key such
 *   as {@code {a.b.c}} — is not supported; it is MessageFormat-quoted so it emerges
 *   verbatim instead of breaking the render.</li>
 *   <li>The jakarta escape sequences (backslash before a brace, dollar sign, or
 *   backslash) become their literal characters. Literal braces are MessageFormat-quoted
 *   and literal single quotes doubled.</li>
 * </ul>
 *
 * <p>Braced content ends at the first closing brace, mirroring core's non-greedy
 * placeholder parser; an unterminated brace is treated as literal text.</p>
 *
 * <p>Translation is applied exactly once per template: literal annotation messages at
 * materialization, bundle-resolved text in {@link TranslatingMessageResolver}. Core
 * rules' rulii-native default messages are never translated — they use positional
 * {@code {0}} syntax that quoting would destroy.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class JakartaMessageTranslator {

    private JakartaMessageTranslator() {
        super();
    }

    /**
     * Translates the given jakarta message template without attribute renaming.
     *
     * @param template the jakarta template.
     * @return the equivalent rulii/MessageFormat template.
     */
    public static String translate(String template) {
        return translate(template, Collections.emptyMap());
    }

    /**
     * Translates the given jakarta message template.
     *
     * @param template the jakarta template.
     * @param attributeNames mapping of jakarta attribute name to rule parameter name,
     *                       applied to bare-identifier placeholders; unmapped names are
     *                       kept as-is.
     * @return the equivalent rulii/MessageFormat template.
     */
    public static String translate(String template, Map<String, String> attributeNames) {
        Assert.notNull(template, "template cannot be null.");
        Assert.notNull(attributeNames, "attributeNames cannot be null.");

        StringBuilder result = new StringBuilder(template.length() + 16);
        StringBuilder literal = new StringBuilder();
        int length = template.length();
        int index = 0;

        while (index < length) {
            char c = template.charAt(index);

            if (c == '\\' && index + 1 < length && isEscapable(template.charAt(index + 1))) {
                literal.append(template.charAt(index + 1));
                index += 2;
            } else if (c == '$' && index + 1 < length && template.charAt(index + 1) == '{') {
                int close = template.indexOf('}', index + 2);

                if (close < 0) {
                    literal.append("${");
                    index += 2;
                } else {
                    String content = template.substring(index + 2, close);

                    if (isIdentifier(content)) {
                        flushLiteral(result, literal);
                        result.append("${").append(attributeNames.getOrDefault(content, content)).append('}');
                    } else {
                        // EL shield: the $ stays bare so no ${ pair survives for the
                        // placeholder parser; the braced expression rides through quoted.
                        flushLiteral(result, literal);
                        result.append('$');
                        literal.append('{').append(content).append('}');
                        flushLiteral(result, literal);
                    }

                    index = close + 1;
                }
            } else if (c == '{') {
                int close = template.indexOf('}', index + 1);

                if (close >= 0 && isIdentifier(template.substring(index + 1, close))) {
                    String content = template.substring(index + 1, close);
                    flushLiteral(result, literal);
                    result.append("${").append(attributeNames.getOrDefault(content, content)).append('}');
                    index = close + 1;
                } else {
                    literal.append('{');
                    index++;
                }
            } else {
                literal.append(c);
                index++;
            }
        }

        flushLiteral(result, literal);
        return result.toString();
    }

    private static boolean isEscapable(char c) {
        return c == '{' || c == '}' || c == '$' || c == '\\';
    }

    private static boolean isIdentifier(String value) {
        if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) return false;

        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) return false;
        }

        return true;
    }

    /**
     * Appends the pending literal text MessageFormat-escaped and clears the buffer:
     * single quotes are doubled, braces wrapped in quoted runs. Quoting covers each
     * contiguous run of braces rather than individual characters — two adjacent quoted
     * chunks would meet in a doubled quote, which MessageFormat reads as a stray
     * literal quote.
     *
     * @param result the output template being assembled.
     * @param literal the pending literal text; empty after this call.
     */
    private static void flushLiteral(StringBuilder result, StringBuilder literal) {
        boolean inQuote = false;

        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);

            if (c == '\'') {
                result.append("''");
            } else if (c == '{' || c == '}') {
                if (!inQuote) {
                    result.append('\'');
                    inQuote = true;
                }
                result.append(c);
            } else {
                if (inQuote) {
                    result.append('\'');
                    inQuote = false;
                }
                result.append(c);
            }
        }

        if (inQuote) result.append('\'');
        literal.setLength(0);
    }
}
