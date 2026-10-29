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
package org.rulii.validation.beans.assembly;

import org.rulii.annotation.Description;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.condition.Condition;
import org.rulii.model.function.Function;
import org.rulii.script.Script;
import org.rulii.validation.Severity;
import org.rulii.validation.ValueValidationRule;

import java.util.List;

/**
 * The rule an {@code @Expr} materializes into: a value validation rule whose condition is a compiled
 * script. The value is read as every value validation rule reads it (the property, the bean, the
 * arguments or the return value, through the value function), so the violation carries it and the
 * null policy rides {@code failOnNull}: skipped on null when false, run with the null value when
 * true. The script itself reads the bindings of the validation scope, not the value parameter.
 *
 * <p>A script that throws propagates as its exception; a script that returns null or a non-boolean
 * fails with an {@code UnrulyException}. Neither is a violation.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 * @see org.rulii.validation.beans.Expr
 */
@org.rulii.annotation.Rule
@Description("Script condition must be true.")
public class ExprValidationRule extends ValueValidationRule {

    public static final List<Class<?>> SUPPORTED_TYPES = List.of(Object.class);

    public static final String DEFAULT_MESSAGE  = "Script condition is not satisfied.";

    private final Script<?> script;
    private final Condition condition;

    public static ExprValidationRuleBuilder builder(Function<?> valueFunction, Script<?> script) {
        return new ExprValidationRuleBuilder(valueFunction, script);
    }

    ExprValidationRule(Function<?> valueFunction, Script<?> script, String errorCode, Severity severity, String errorMessage,
                       String defaultMessage, String valueName) {
        super(valueFunction, errorCode, severity, errorMessage, defaultMessage, valueName);
        Assert.notNull(script, "script cannot be null.");
        this.script = script;
        this.condition = Condition.builder().build(script);
    }

    /**
     * The compiled script.
     *
     * @return the script.
     */
    public Script<?> getScript() {
        return script;
    }

    /**
     * The condition evaluating the script.
     *
     * @return the condition.
     */
    public Condition getCondition() {
        return condition;
    }

    @Override
    protected boolean isValid(RuleContext ruleContext, Object value) {
        return condition.isTrue(ruleContext);
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public String describe() {
        return getValueName() + " must satisfy " + script.getSourceText();
    }

    @Override
    public String toString() {
        return "ExprValidationRule{" + script.getLanguageName() + ": " + script.getSourceText() + ", errorCode=" + getErrorCode() + '}';
    }
}
