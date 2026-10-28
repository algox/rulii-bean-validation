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
import org.rulii.text.MessageFormatter;
import org.rulii.text.MessageResolver;
import org.rulii.text.ParameterInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rulii {@link MessageFormatter} that keeps Bean Validation templates safe through rulii's
 * placeholder pipeline. Before delegating it walks every {@code ${...}} of the template:
 * <ul>
 *     <li>a placeholder naming one of the parameters is left to the formatter it wraps;</li>
 *     <li>{@code ${validatedValue}} with no parameter of that name stands for the first parameter,
 *     which is the validated value of every rulii value validation rule;</li>
 *     <li>a placeholder naming nothing known renders as {@code {name}}, as Bean Validation leaves an
 *     unknown reference, instead of a parser complaint;</li>
 *     <li>a placeholder marked as a message key ({@code ${#my.key}}, which is how a {@code {my.key}} in a
 *     literal message arrives from {@link MessageTemplates#translate}) is expanded through the resolver,
 *     recursively, at the locale in hand; an unknown key renders as {@code {my.key}};</li>
 *     <li>anything else is a message expression, evaluated by the {@link MessageExpressions} with the
 *     specification's scope: the parameters, {@code validatedValue} and {@code formatter}. Without the
 *     expression language, the one conditional form {@code ${param == true ? 'a' : 'b'}} (or
 *     {@code == false}) is still decided against the parameter, which is how the default messages tell
 *     an inclusive bound from an exclusive one, and every other expression is shielded from the
 *     placeholder parser and emitted verbatim.</li>
 * </ul>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidationMessageFormatter implements MessageFormatter {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(.*?)}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern CONDITIONAL = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*==\\s*(true|false)\\s*\\?\\s*'([^']*)'\\s*:\\s*'([^']*)'");
    private static final Set<String> EL_LITERALS = Set.of("null", "true", "false");
    private static final char MARK = '⁣';
    private static final int MAX_DEPTH = 8;

    private final MessageFormatter delegate;
    private final MessageResolver resolver;
    private final MessageExpressions expressions;

    /**
     * Creates the formatter, evaluating expressions when the expression language is available.
     *
     * @param delegate the formatter doing the formatting.
     * @param resolver the resolver message keys expand through; null to leave keys unexpanded.
     */
    public BeanValidationMessageFormatter(MessageFormatter delegate, MessageResolver resolver) {
        this(delegate, resolver, MessageExpressions.standard());
    }

    /**
     * Creates the formatter.
     *
     * @param delegate the formatter doing the formatting.
     * @param resolver the resolver message keys expand through; null to leave keys unexpanded.
     * @param expressions the evaluator of message expressions; {@link MessageExpressions#NONE} leaves
     *                    them as written.
     */
    public BeanValidationMessageFormatter(MessageFormatter delegate, MessageResolver resolver, MessageExpressions expressions) {
        super();
        Assert.notNull(delegate, "delegate cannot be null.");
        Assert.notNull(expressions, "expressions cannot be null.");
        this.delegate = delegate;
        this.resolver = resolver;
        this.expressions = expressions;
    }

    /**
     * The evaluator of message expressions.
     *
     * @return the expressions.
     */
    public MessageExpressions getExpressions() {
        return expressions;
    }

    /**
     * The formatter doing the formatting.
     *
     * @return the delegate.
     */
    public MessageFormatter getDelegate() {
        return delegate;
    }

    @Override
    public String format(Locale locale, String message, Object... args) {
        return delegate.format(locale, message, args);
    }

    @Override
    public String format(Locale locale, String message, ParameterInfo... parameters) {
        Assert.notNull(message, "message cannot be null.");

        if (message.indexOf('$') < 0) return delegate.format(locale, message, parameters);

        Map<String, Object> values = new LinkedHashMap<>();
        String first = null;

        if (parameters != null) {
            ParameterInfo lowest = null;
            for (ParameterInfo parameter : parameters) {
                values.put(parameter.getName(), parameter.getValue());
                if (lowest == null || parameter.getIndex() < lowest.getIndex()) lowest = parameter;
            }
            first = lowest != null ? lowest.getName() : null;
        }

        List<String> shielded = new ArrayList<>();
        String prepared = prepare(locale, message, values, first, shielded, 0);
        String formatted = delegate.format(locale, prepared, parameters);

        for (int i = 0; i < shielded.size(); i++) {
            formatted = formatted.replace(mark(i), shielded.get(i));
        }

        return formatted;
    }

    private String prepare(Locale locale, String message, Map<String, Object> values, String first, List<String> shielded, int depth) {
        Matcher matcher = PLACEHOLDER.matcher(message);
        StringBuilder result = new StringBuilder(message.length() + 16);
        int last = 0;

        while (matcher.find()) {
            result.append(message, last, matcher.start());
            String content = matcher.group(1).trim();
            int comma = content.indexOf(',');
            String name = comma > 0 ? content.substring(0, comma).trim() : content;
            String options = comma > 0 ? content.substring(comma) : "";
            Matcher conditional = CONDITIONAL.matcher(content);

            if (values.containsKey(name)) {
                result.append(matcher.group());
            } else if (name.equals(MessageTemplates.VALIDATED_VALUE) && first != null) {
                result.append("${").append(first).append(options).append('}');
            } else if (IDENTIFIER.matcher(content).matches() && !EL_LITERALS.contains(content)) {
                result.append(shield(shielded, "{" + content + "}"));
            } else {
                String key = content.startsWith(MessageTemplates.KEY_MARK) ? content.substring(MessageTemplates.KEY_MARK.length()) : null;
                String text = key != null && resolver != null && depth < MAX_DEPTH ? resolver.resolve(locale, key, null) : null;

                if (text != null) {
                    result.append(prepare(locale, MessageTemplates.translate(text, MessageTemplates.VALIDATED_VALUE),
                            values, first, shielded, depth + 1));
                } else if (key != null) {
                    result.append(shield(shielded, "{" + key + "}"));
                } else {
                    String evaluated = expressions.evaluate(matcher.group(), values, first != null ? values.get(first) : null, locale);

                    if (evaluated != null) {
                        result.append(quote(evaluated));
                    } else if (conditional.matches() && values.containsKey(conditional.group(1))) {
                        boolean holds = String.valueOf(values.get(conditional.group(1))).equals(conditional.group(2));
                        result.append(quote(holds ? conditional.group(3) : conditional.group(4)));
                    } else {
                        result.append(shield(shielded, matcher.group()));
                    }
                }
            }

            last = matcher.end();
        }

        result.append(message, last, message.length());
        return result.toString();
    }

    /**
     * Quotes text for {@link java.text.MessageFormat}, so an apostrophe or a brace in it is literal.
     */
    private static String quote(String text) {
        StringBuilder result = new StringBuilder(text.length() + 4);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '\'' -> result.append("''");
                case '{' -> result.append("'{'");
                case '}' -> result.append("'}'");
                default -> result.append(c);
            }
        }

        return result.toString();
    }

    private static String shield(List<String> shielded, String verbatim) {
        shielded.add(verbatim);
        return mark(shielded.size() - 1);
    }

    private static String mark(int index) {
        return MARK + Integer.toString(index) + MARK;
    }

    @Override
    public String toString() {
        return "BeanValidationMessageFormatter{delegate=" + delegate + '}';
    }
}
