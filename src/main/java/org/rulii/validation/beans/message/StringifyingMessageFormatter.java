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
import org.rulii.text.ParameterInfo;

import java.util.Locale;

/**
 * Decorates a {@link MessageFormatter}, converting Number parameter values to plain
 * strings before delegating. {@code java.text.MessageFormat} locale-formats numbers —
 * {@code @Size(min = 2000)} would render "2,000" in {@code Locale.US} where jakarta
 * implementations interpolate attribute values verbatim ("2000"). Stringifying restores
 * jakarta parity for constraint attributes and the validated value alike.
 *
 * <p>The trade: explicit number subformats ({@code {0,number,#.##}}) in user-authored
 * rulii-native templates stop working on stringified values — plain placeholders are
 * unaffected.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class StringifyingMessageFormatter implements MessageFormatter {

    private final MessageFormatter delegate;

    /**
     * Creates a stringifying formatter around the given delegate.
     *
     * @param delegate the formatter doing the actual work.
     */
    public StringifyingMessageFormatter(MessageFormatter delegate) {
        super();
        Assert.notNull(delegate, "delegate cannot be null.");
        this.delegate = delegate;
    }

    /**
     * Formats the message after stringifying Number arguments.
     *
     * @param locale the locale to be used for formatting.
     * @param message the message to be formatted.
     * @param args the optional arguments to be inserted into the placeholders.
     * @return the formatted message.
     */
    @Override
    public String format(Locale locale, String message, Object... args) {
        Object[] converted = null;

        if (args != null) {
            converted = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                converted[i] = stringify(args[i]);
            }
        }

        return delegate.format(locale, message, converted);
    }

    /**
     * Formats the message after stringifying Number parameter values.
     *
     * @param locale the locale to be used for formatting.
     * @param message the message to be formatted.
     * @param parameters the parameter information to be inserted into the placeholders.
     * @return the formatted message.
     */
    @Override
    public String format(Locale locale, String message, ParameterInfo... parameters) {
        ParameterInfo[] converted = null;

        if (parameters != null) {
            converted = new ParameterInfo[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                ParameterInfo parameter = parameters[i];
                converted[i] = parameter.getValue() instanceof Number
                        ? new ParameterInfo(parameter.getIndex(), parameter.getName(), parameter.getValue().toString())
                        : parameter;
            }
        }

        return delegate.format(locale, message, converted);
    }

    private static Object stringify(Object value) {
        return value instanceof Number ? value.toString() : value;
    }

    @Override
    public String toString() {
        return "StringifyingMessageFormatter{delegate=" + delegate + '}';
    }
}
