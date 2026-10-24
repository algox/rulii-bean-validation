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

import java.util.Formatter;
import java.util.Locale;

/**
 * The {@code formatter} object the specification puts in scope of a message expression:
 * {@code ${formatter.format('%1$.2f', validatedValue)}} formats through {@link Formatter} at the
 * locale of the message.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ElFormatter {

    private final Locale locale;

    /**
     * Creates the formatter.
     *
     * @param locale the locale of the message; null for the default locale.
     */
    public ElFormatter(Locale locale) {
        super();
        this.locale = locale != null ? locale : Locale.getDefault();
    }

    public Locale getLocale() {
        return locale;
    }

    /**
     * Formats as {@link String#format(Locale, String, Object...)} does, at the message's locale.
     *
     * @param format the format string.
     * @param arguments the arguments.
     * @return the formatted text.
     */
    public String format(String format, Object... arguments) {
        Assert.notNull(format, "format cannot be null.");
        try (Formatter formatter = new Formatter(locale)) {
            return formatter.format(format, arguments).toString();
        }
    }

    @Override
    public String toString() {
        return "ElFormatter{" + locale + '}';
    }
}
