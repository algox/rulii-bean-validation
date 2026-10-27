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

import org.rulii.lib.spring.util.Assert;
import org.rulii.model.function.Function;
import org.rulii.script.Script;
import org.rulii.validation.Severity;
import org.rulii.validation.ValueValidationRuleBuilder;

/**
 * Builder of {@link ExprValidationRule}. The error code is the caller's to set; the rule's
 * description names the language and the script.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ExprValidationRuleBuilder extends ValueValidationRuleBuilder<ExprValidationRuleBuilder, ExprValidationRule> {

    private final Script<?> script;

    public ExprValidationRuleBuilder(Function<?> valueFunction, Script<?> script) {
        super(valueFunction);
        Assert.notNull(script, "script cannot be null.");
        this.script = script;
        severity(Severity.ERROR);
        description(script.getLanguageName() + ": " + script.getSourceText());
    }

    @Override
    protected ExprValidationRule createValueValidationRule() {
        // The script text stays out of the message: a template is rendered through the placeholder
        // pipeline, which would evaluate an EL script's ${...} as a placeholder.
        return new ExprValidationRule(getValueFunction(), script, getErrorCode(), getSeverity(), getErrorMessage(),
                ExprValidationRule.DEFAULT_MESSAGE, getValueName());
    }
}
