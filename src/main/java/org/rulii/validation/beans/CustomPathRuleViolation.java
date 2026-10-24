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
package org.rulii.validation.beans;

import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.RuleViolation;

import java.util.function.UnaryOperator;

/**
 * A violation that says where it occurred relative to the rule that reported it. A rule normally
 * reports at the place the traversal put it (the property, the container element, the bean); a rule
 * that knows better, like one running a {@code ConstraintValidator} whose context added property nodes,
 * reports one of these, and {@link BeanRuleViolations} extends the rule's path with it when stamping.
 * The extension replaces the default placement entirely: a class-level rule's violation extended with a
 * property node reports at that property, with no bean node, as the reference implementation does.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class CustomPathRuleViolation extends RuleViolation {

    private final UnaryOperator<PropertyPath> pathExtension;
    private final String messageTemplate;
    private final boolean expressionLanguageEnabled;

    /**
     * Creates a violation with the message template of the rule.
     *
     * @param source the violation as the rule built it.
     * @param pathExtension how the rule's path extends to where the violation occurred.
     */
    public CustomPathRuleViolation(RuleViolation source, UnaryOperator<PropertyPath> pathExtension) {
        this(source, pathExtension, null, true);
    }

    /**
     * Creates a violation with its own message template, one built at run time.
     *
     * @param source the violation as the rule built it.
     * @param pathExtension how the rule's path extends to where the violation occurred.
     * @param messageTemplate the template as given, before any interpolation; null to report the rule's.
     * @param expressionLanguageEnabled whether {@code ${...}} expressions of the template may be evaluated.
     */
    public CustomPathRuleViolation(RuleViolation source, UnaryOperator<PropertyPath> pathExtension, String messageTemplate,
                                   boolean expressionLanguageEnabled) {
        super(source(source).getRuleName(), source.getErrorCode(), source.getSeverity(), source.getErrorMessage(),
                source.getParameterValues());
        Assert.notNull(pathExtension, "pathExtension cannot be null.");
        this.pathExtension = pathExtension;
        this.messageTemplate = messageTemplate;
        this.expressionLanguageEnabled = expressionLanguageEnabled;
    }

    /**
     * The message template as given, when the violation carries its own.
     *
     * @return the template; null when the rule's template applies.
     */
    public String getMessageTemplate() {
        return messageTemplate;
    }

    /**
     * Whether {@code ${...}} expressions of the template may be evaluated.
     *
     * @return true unless a run-time template left them disabled.
     */
    public boolean isExpressionLanguageEnabled() {
        return expressionLanguageEnabled;
    }

    private static RuleViolation source(RuleViolation source) {
        Assert.notNull(source, "source cannot be null.");
        return source;
    }

    /**
     * How the rule's path extends to where the violation occurred.
     *
     * @return the extension.
     */
    public UnaryOperator<PropertyPath> getPathExtension() {
        return pathExtension;
    }

    /**
     * Where the violation occurred, given the rule's path.
     *
     * @param rulePath the path of the rule: the property for a property rule, the current path otherwise.
     * @return the extended path.
     */
    public PropertyPath extend(PropertyPath rulePath) {
        Assert.notNull(rulePath, "rulePath cannot be null.");
        PropertyPath result = pathExtension.apply(rulePath);
        return result != null ? result : rulePath;
    }
}
