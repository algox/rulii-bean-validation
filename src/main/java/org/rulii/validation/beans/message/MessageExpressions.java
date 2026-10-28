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

import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.apache.commons.logging.Log;
import org.rulii.lib.apache.commons.logging.LogFactory;
import org.rulii.lib.spring.util.Assert;
import org.rulii.script.Script;
import org.rulii.script.ScriptProcessor;
import org.rulii.script.ScriptProcessorFactory;
import org.rulii.script.el.ElScriptProcessorFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Evaluates the {@code ${...}} message expressions of Bean Validation templates through rulii's
 * {@code jakarta-el} scripting language (Jakarta Expression Language), with the scope the specification
 * defines: the constraint's attributes, {@code validatedValue}, and {@code formatter}
 * ({@link ElFormatter}). The attributes are the parameters the rule reported, so they are also
 * reachable through rulii's root object ({@code ${ctx.min}}).
 *
 * <p>The language is available when the Jakarta EL API and an implementation are on the classpath
 * (see {@link ElScriptProcessorFactory}); {@link #standard()} sets it up once.
 * Without it, {@link #isAvailable()} is false and {@link #evaluate} answers null, which leaves an
 * expression as it was written, the behaviour before expressions were supported. An expression that
 * fails to compile or to evaluate answers null as well, after a warning, as the reference
 * implementation does.</p>
 *
 * <p>Expressions compile once and are cached per text; evaluation builds a small rulii context per
 * message, so one instance serves any number of threads.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MessageExpressions {

    /**
     * The rulii scripting language message expressions are evaluated in: Jakarta Expression Language,
     * as core registers it. The factory is taken from core directly, not looked up by name, so a
     * registration in the application (rulii-spring's Spring Expression Language, for instance) can
     * never stand in for it.
     */
    public static final String LANGUAGE = ElScriptProcessorFactory.LANGUAGE_NAME;

    /**
     * The name of the {@link ElFormatter} in scope of an expression.
     */
    public static final String FORMATTER = "formatter";

    /**
     * Evaluates nothing: every expression stays as written.
     */
    public static final MessageExpressions NONE = new MessageExpressions(null);

    private static final Log logger = LogFactory.getLog(MessageExpressions.class);
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]*)}");
    private static volatile MessageExpressions standard;

    private final ScriptProcessorFactory factory;
    private final ScriptProcessor processor;
    private final ConcurrentMap<String, Optional<Script<Object>>> compiled = new ConcurrentHashMap<>();

    private MessageExpressions(ScriptProcessorFactory factory) {
        super();
        this.factory = factory;
        this.processor = factory != null ? factory.getScriptProcessor() : null;
    }

    /**
     * The expressions evaluator of this runtime: the {@code el} language when it is available,
     * {@link #NONE} otherwise. Resolved once.
     *
     * @return the evaluator.
     */
    public static MessageExpressions standard() {
        MessageExpressions result = standard;

        if (result == null) {
            synchronized (MessageExpressions.class) {
                result = standard;
                if (result == null) {
                    result = resolve();
                    standard = result;
                }
            }
        }

        return result;
    }

    /**
     * An evaluator over the given language.
     *
     * @param factory the scripting language; must be available.
     * @return the evaluator.
     */
    public static MessageExpressions of(ScriptProcessorFactory factory) {
        Assert.notNull(factory, "factory cannot be null.");
        Assert.isTrue(factory.isAvailable(), "scripting language [" + factory.getLanguageName() + "] is not available.");
        return new MessageExpressions(factory);
    }

    private static MessageExpressions resolve() {
        ScriptProcessorFactory factory;

        try {
            factory = new ElScriptProcessorFactory();
        } catch (RuntimeException | LinkageError e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Message expressions are not evaluated: the [" + LANGUAGE + "] scripting language cannot be set up.", e);
            }
            return NONE;
        }

        if (!factory.isAvailable()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Message expressions are not evaluated: the [" + LANGUAGE + "] scripting language is not available;"
                        + " add a Jakarta Expression Language implementation to enable them.");
            }
            return NONE;
        }

        return new MessageExpressions(factory);
    }

    /**
     * Whether expressions are evaluated.
     *
     * @return true if the language is available.
     */
    public boolean isAvailable() {
        return factory != null;
    }

    /**
     * The scripting language expressions are evaluated in.
     *
     * @return the factory; null when {@link #isAvailable() unavailable}.
     */
    public ScriptProcessorFactory getFactory() {
        return factory;
    }

    /**
     * Evaluates one message expression.
     *
     * @param expression the expression as written, delimiters included ({@code ${...}}).
     * @param attributes the parameters the rule reported, by name: the constraint's attributes and
     *                   the validated value under the rule's value name.
     * @param validatedValue the validated value, bound as {@code validatedValue} unless an attribute
     *                       of that name exists.
     * @param locale the locale of the message, for {@code formatter}.
     * @return the result as text (an empty string for null); null when expressions are unavailable
     *         or this one cannot be compiled or evaluated.
     */
    public String evaluate(String expression, Map<String, Object> attributes, Object validatedValue, Locale locale) {
        Assert.notNull(expression, "expression cannot be null.");
        Assert.notNull(attributes, "attributes cannot be null.");

        if (processor == null) return null;

        Script<Object> script = compile(expression);
        if (script == null) return null;

        Bindings bindings = Bindings.builder().standard();

        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            bind(bindings, attribute.getKey(), attribute.getValue());
        }

        if (!attributes.containsKey(MessageTemplates.VALIDATED_VALUE)) bind(bindings, MessageTemplates.VALIDATED_VALUE, validatedValue);
        if (!attributes.containsKey(FORMATTER)) bind(bindings, FORMATTER, new ElFormatter(locale));

        try {
            Object result = processor.evaluate(script, RuleContext.builder().with(bindings).build());
            return result != null ? String.valueOf(result) : "";
        } catch (RuntimeException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Message expression [" + expression + "] could not be evaluated and is left as written: " + e.getMessage());
            }
            return null;
        }
    }

    private Script<Object> compile(String expression) {
        return compiled.computeIfAbsent(expression, text -> {
            try {
                return Optional.of(factory.getScriptCompiler().compile(text, Object.class));
            } catch (RuntimeException e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Message expression [" + text + "] could not be compiled and is left as written: " + e.getMessage());
                }
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * Escapes the message expressions of a jakarta template so that they survive translation and
     * formatting as written: each {@code ${...}} becomes {@code \$\{...\}}, which
     * {@link MessageTemplates#translate} renders literally. For templates built at run time whose
     * expressions must not run.
     *
     * @param template the jakarta template.
     * @return the template with its expressions escaped.
     */
    public static String escape(String template) {
        Assert.notNull(template, "template cannot be null.");
        return EXPRESSION.matcher(template).replaceAll("\\\\\\$\\\\{$1\\\\}");
    }

    private static void bind(Bindings bindings, String name, Object value) {
        try {
            bindings.bind(name, value != null ? value.getClass() : Object.class, value);
        } catch (RuntimeException e) {
            // A name the binding model refuses (a reserved name, say) is simply not in scope of the expression.
            if (logger.isDebugEnabled()) {
                logger.debug("Message expression variable [" + name + "] cannot be bound: " + e.getMessage());
            }
        }
    }

    @Override
    public String toString() {
        return "MessageExpressions{" + (factory != null ? factory.getLanguageName() : "none") + ", compiled=" + compiled.size() + '}';
    }
}
