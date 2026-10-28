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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.message.MessageTemplates;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MessageTemplates}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class MessageTemplatesTest {


    public MessageTemplatesTest() {
        super();
    }

    @Test
    public void attributePlaceholdersBecomeRuliiPlaceholders() {
        assertEquals("size must be between ${min} and ${max}",
                MessageTemplates.translate("size must be between {min} and {max}", "name"));
        assertEquals("${min}", MessageTemplates.translate("{min}", "name"));
        assertEquals("${ min }".replace(" ", ""), MessageTemplates.translate("{ min }", "name"));
    }

    @Test
    public void validatedValueBecomesTheValueName() {
        assertEquals("${email} is taken", MessageTemplates.translate("${validatedValue} is taken", "email"));
        assertEquals("${email} is taken", MessageTemplates.translate("{validatedValue} is taken", "email"));
        assertEquals("${name$field}", MessageTemplates.translate("${validatedValue}", "name$field"));
    }

    @Test
    public void bareIdentifierInDollarBracesIsAParameter() {
        assertEquals("at least ${min}", MessageTemplates.translate("at least ${min}", "name"));
        assertEquals("at least ${value}", MessageTemplates.translate("at least ${value}", "name"), "attributes keep their own names");
    }

    @Test
    public void expressionsAreLeftVerbatim() {
        String el = "must be less than ${inclusive == true ? 'or equal to ' : ''}{value}";
        assertEquals("must be less than ${inclusive == true ? 'or equal to ' : ''}${value}",
                MessageTemplates.translate(el, "name"));
        assertEquals("${validatedValue.length() > 3}", MessageTemplates.translate("${validatedValue.length() > 3}", "name"));
    }

    @Test
    public void literalTextIsQuotedForMessageFormat() {
        assertEquals("don''t", MessageTemplates.translate("don't", "name"));
        assertEquals("'{'literal'}' ${min}", MessageTemplates.translate("\\{literal\\} {min}", "name"));
        assertEquals("$ and \\", MessageTemplates.translate("\\$ and \\\\", "name"));
        assertEquals("trailing \\", MessageTemplates.translate("trailing \\", "name"));
        assertEquals("$", MessageTemplates.translate("$", "name"));
        assertEquals("$'{'open", MessageTemplates.translate("${open", "name"));
        assertEquals("'{'open", MessageTemplates.translate("{open", "name"));
        assertEquals("close'}'", MessageTemplates.translate("close}", "name"));
    }

    @Test
    public void bundleKeysInsideAMessageAreQuotedVerbatim() {
        assertEquals("${#my.key} and ${min}", MessageTemplates.translate("{my.key} and {min}", "name"));
        assertEquals("'{'some text'}'", MessageTemplates.translate("{some text}", "name"));
        assertEquals("'{'it''s'}'", MessageTemplates.translate("{it's}", "name"));
    }

    @Test
    public void translatedTextFormatsWithMessageFormat() {
        // What rulii does after placeholder substitution: ${x} -> {0}, then MessageFormat. The key placeholder
        // is expanded by the render-time formatter before that; a second argument stands in for it here.
        String translated = MessageTemplates.translate("don't exceed {max} \\{chars\\}: {my.key}", "name");
        String formatted = new MessageFormat(translated.replace("${max}", "{0}").replace("${#my.key}", "{1}")).format(new Object[]{5, "custom"});
        assertEquals("don't exceed 5 {chars}: custom", formatted);
    }

    @Test
    public void templateKeys() {
        Set<String> sizeAttributes = MessageTemplates.attributeNames(Size.class);
        assertEquals(Set.of("message", "groups", "payload", "min", "max"), sizeAttributes);

        assertTrue(MessageTemplates.isTemplateKey("{jakarta.validation.constraints.Size.message}", sizeAttributes));
        assertTrue(MessageTemplates.isTemplateKey("  {my.key} ", sizeAttributes));
        assertTrue(MessageTemplates.isTemplateKey("{tooShort}", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("{min}", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("{min} chars", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("size {my.key}", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("{}", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("{a b}", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("${validatedValue}", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("{a{b}}", sizeAttributes));
        assertFalse(MessageTemplates.isTemplateKey("plain", sizeAttributes));

        assertEquals("my.key", MessageTemplates.templateKey("{my.key}"));
        assertNull(MessageTemplates.templateKey("{my.key} x"));
        assertEquals(Set.of("message", "groups", "payload", "regexp", "flags"), MessageTemplates.attributeNames(Pattern.class));
        assertTrue(MessageTemplates.attributeNames(NotNull.class).contains("message"));
    }

    @Test
    public void nullsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MessageTemplates.translate(null, "name"));
        assertThrows(IllegalArgumentException.class, () -> MessageTemplates.translate("x", " "));
        assertThrows(IllegalArgumentException.class, () -> MessageTemplates.isTemplateKey(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> MessageTemplates.isTemplateKey("{x}", null));
        assertThrows(IllegalArgumentException.class, () -> MessageTemplates.templateKey(null));
        assertThrows(IllegalArgumentException.class, () -> MessageTemplates.attributeNames(null));
    }
}
