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

import org.rulii.annotation.Given;
import org.rulii.annotation.Otherwise;
import org.rulii.annotation.Param;
import org.rulii.bind.match.MatchByTypeMatchingStrategy;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.rule.Rule;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolationBuilder;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.ValidationRule;
import org.rulii.validation.beans.BeanRuleViolations;

import java.util.List;

/**
 * The {@code @ReportAsSingleViolation} wrapper (R8.3): runs the materialized composing
 * rules through the live RuleContext inside a collector suppression frame — trace
 * preserved, their individual violations diverted — and reports one violation with the
 * host's reporting config if anything was diverted. Frames stack, so nested
 * compositions nest.
 *
 * <p>Collecting into a plain {@link RuleViolations} (no suppression support) degrades
 * gracefully: composing rules run and report individually.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ReportAsSingleViolationRule extends ValidationRule {

    private final List<Rule> composingRules;
    private final String valueBindingName;
    private final String valueName;

    ReportAsSingleViolationRule(String errorCode, Severity severity, String errorMessage, String defaultMessage,
                                List<Rule> composingRules, String valueBindingName, String valueName) {
        super(errorCode, severity, errorMessage, defaultMessage);
        Assert.notNull(composingRules, "composingRules cannot be null.");
        Assert.hasText(valueBindingName, "valueBindingName cannot be null or empty.");
        Assert.hasText(valueName, "valueName cannot be null or empty.");
        this.composingRules = composingRules;
        this.valueBindingName = valueBindingName;
        this.valueName = valueName;
    }

    /**
     * Runs the composing rules inside a suppression frame; the composition holds when
     * nothing was diverted.
     *
     * @param ruleContext the live context.
     * @param ruleViolations the call's collector.
     * @return true if no composing rule reported a violation; false otherwise.
     */
    @Given
    public boolean isValid(@Param(matchUsing = MatchByTypeMatchingStrategy.class) RuleContext ruleContext,
                           @Param(matchUsing = MatchByTypeMatchingStrategy.class) RuleViolations ruleViolations) {
        if (!(ruleViolations instanceof BeanRuleViolations collector)) {
            for (Rule rule : composingRules) {
                rule.run(ruleContext);
            }
            return true;
        }

        List<RuleViolation> diverted;
        collector.pushFrame();

        try {
            for (Rule rule : composingRules) {
                rule.run(ruleContext);
            }
        } finally {
            diverted = collector.popFrame();
        }

        return diverted.isEmpty();
    }

    /**
     * Reports the single host violation.
     *
     * @param ruleContext the live context.
     * @param ruleViolations the call's collector.
     */
    @Otherwise
    public void otherwise(@Param(matchUsing = MatchByTypeMatchingStrategy.class) RuleContext ruleContext,
                          @Param(matchUsing = MatchByTypeMatchingStrategy.class) RuleViolations ruleViolations) {
        RuleViolationBuilder builder = RuleViolation.builder().with(this);
        builder.param(valueName, ruleContext.getBindings().getValue(valueBindingName));
        ruleViolations.add(builder.build(ruleContext));
    }

    @Override
    public String toString() {
        return "ReportAsSingleViolationRule{name=" + getName() + ", composing=" + composingRules.size() + '}';
    }
}
