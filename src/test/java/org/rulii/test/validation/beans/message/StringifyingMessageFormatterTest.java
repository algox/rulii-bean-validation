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
package org.rulii.test.validation.beans.message;

import org.junit.jupiter.api.Test;
import org.rulii.text.MessageFormatter;
import org.rulii.text.ParameterInfo;
import org.rulii.validation.beans.message.StringifyingMessageFormatter;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for jakarta-parity number rendering: MessageFormat locale-formats numbers
 * ("2,020" in Locale.US); the stringifying decorator renders attribute values verbatim
 * the way jakarta implementations do.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class StringifyingMessageFormatterTest {

    public StringifyingMessageFormatterTest() {
        super();
    }

    private final MessageFormatter formatter = new StringifyingMessageFormatter(MessageFormatter.builder().build());

    @Test
    public void numbersRenderWithoutLocaleGrouping() {
        // Undecorated MessageFormat would render "2,020" in Locale.US.
        assertEquals("year 2020", formatter.format(Locale.US, "year ${min}", new ParameterInfo(0, "min", 2020)));
    }

    @Test
    public void decimalAndLongValuesRenderVerbatim() {
        assertEquals("max 9999999999", formatter.format(Locale.US, "max ${max}", new ParameterInfo(0, "max", 9999999999L)));
        assertEquals("price 1234.50", formatter.format(Locale.US, "price ${p}", new ParameterInfo(0, "p", new BigDecimal("1234.50"))));
    }

    @Test
    public void nonNumberValuesUntouched() {
        assertEquals("name Ada", formatter.format(Locale.US, "name ${n}", new ParameterInfo(0, "n", "Ada")));
        assertEquals("flag true", formatter.format(Locale.US, "flag ${f}", new ParameterInfo(0, "f", true)));
    }

    @Test
    public void positionalArgumentsAlsoStringified() {
        // Core rules' rulii-native defaults are positional — parity applies there too.
        assertEquals("Value 2020 invalid.", formatter.format(Locale.US, "Value {0} invalid.", 2020));
    }
}
