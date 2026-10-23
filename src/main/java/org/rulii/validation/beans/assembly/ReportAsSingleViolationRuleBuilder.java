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
import org.rulii.rule.Rule;
import org.rulii.validation.Severity;
import org.rulii.validation.ValueValidationRuleBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for {@link ReportAsSingleViolationRule}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ReportAsSingleViolationRuleBuilder extends ValueValidationRuleBuilder<ReportAsSingleViolationRuleBuilder, ReportAsSingleViolationRule> {

    private final List<Rule> composingRules;
    private final Map<String, Object> parameters = new LinkedHashMap<>();

    public ReportAsSingleViolationRuleBuilder(Function<?> valueFunction, List<Rule> composingRules) {
        super(valueFunction);
        Assert.notNull(composingRules, "composingRules cannot be null.");
        this.composingRules = composingRules;
        errorCode(ReportAsSingleViolationRule.ERROR_CODE);
        severity(Severity.ERROR);
    }

    /**
     * Parameters the violation reports beside the value, typically the host constraint's attributes.
     *
     * @param parameters the parameters by name.
     * @return this builder.
     */
    public ReportAsSingleViolationRuleBuilder parameters(Map<String, Object> parameters) {
        Assert.notNull(parameters, "parameters cannot be null.");
        this.parameters.putAll(parameters);
        return this;
    }

    @Override
    public ReportAsSingleViolationRuleBuilder failOnNull(boolean failOnNull) {
        Assert.isTrue(failOnNull, "failOnNull(false) is not allowed on [reportAsSingleViolationRule]; the composing rules handle null.");
        return super.failOnNull(failOnNull);
    }

    @Override
    protected ReportAsSingleViolationRule createValueValidationRule() {
        return new ReportAsSingleViolationRule(getValueFunction(), getErrorCode(), getSeverity(), getErrorMessage(),
                getValueName(), composingRules, parameters);
    }
}
