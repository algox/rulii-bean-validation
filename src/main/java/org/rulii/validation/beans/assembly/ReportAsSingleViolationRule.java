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
import org.rulii.bind.Binding;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.function.Function;
import org.rulii.rule.Rule;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.ValueValidationRule;
import org.rulii.validation.beans.BeanRuleViolations;

import java.util.List;

/**
 * The rule of a {@code @ReportAsSingleViolation} composition: runs the rules of the composing
 * constraints and fails, reporting once with the host's error code and message, when any of them
 * fails. Their own violations never reach the report.
 *
 * <p>The composing rules deliver through the bound {@link RuleViolations}. When that is a
 * {@link BeanRuleViolations}, the run happens inside {@link BeanRuleViolations#capture(Runnable)} and
 * nothing of theirs is kept. A rule cannot rebind the collector (bindings are immutable while a rule
 * runs), so with a plain collector the composing violations stay in it and the host's violation is
 * added after them; the per-class flow avoids that case by binding a path-aware collector for its
 * rules run.</p>
 *
 * <p>The rule runs on null too: the composing rules decide for themselves what null means, as a
 * composed {@code @NotNull} must still fail.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@org.rulii.annotation.Rule
@Description("Reports one violation when any of the composing rules fails.")
public class ReportAsSingleViolationRule extends ValueValidationRule {

    public static final List<Class<?>> SUPPORTED_TYPES = List.of(Object.class);

    public static final String ERROR_CODE       = "reportAsSingleViolationRule.errorCode";
    public static final String DEFAULT_MESSAGE  = "Value is not valid.";

    private final List<Rule> composingRules;

    /**
     * Creates a builder.
     *
     * @param valueFunction the function reading the validated value.
     * @param composingRules the rules of the composing constraints.
     * @return the builder.
     */
    public static ReportAsSingleViolationRuleBuilder builder(Function<?> valueFunction, List<Rule> composingRules) {
        return new ReportAsSingleViolationRuleBuilder(valueFunction, composingRules);
    }

    ReportAsSingleViolationRule(Function<?> valueFunction, String errorCode, Severity severity, String errorMessage,
                                String valueName, List<Rule> composingRules) {
        super(valueFunction, errorCode, severity, errorMessage, DEFAULT_MESSAGE, valueName);
        Assert.notNull(composingRules, "composingRules cannot be null.");
        Assert.isTrue(!composingRules.isEmpty(), "composingRules cannot be empty.");
        this.composingRules = List.copyOf(composingRules);
    }

    /**
     * The rules run for the composing constraints.
     *
     * @return the composing rules.
     */
    public List<Rule> getComposingRules() {
        return composingRules;
    }

    @Override
    protected boolean isValid(RuleContext ruleContext, Object value) {
        RuleViolations collector = collector(ruleContext);

        if (collector instanceof BeanRuleViolations capturing) {
            return capturing.capture(() -> run(ruleContext)).isEmpty();
        }

        // A plain collector cannot divert: the composing violations land in it, and the host's follows.
        int before = collector != null ? collector.size() : 0;
        run(ruleContext);
        return collector == null || collector.size() == before;
    }

    /**
     * The collector the composing rules will report to, resolved as core resolves their
     * {@code ruleViolations} parameter: the only {@link RuleViolations} binding, else the primary one.
     */
    private static RuleViolations collector(RuleContext ruleContext) {
        List<Binding<RuleViolations>> bindings = ruleContext.getBindings().getAllBindings(RuleViolations.class);

        if (bindings.size() == 1) return bindings.get(0).getValue();

        for (Binding<RuleViolations> binding : bindings) {
            if (binding.isPrimary()) return binding.getValue();
        }

        return null;
    }

    private void run(RuleContext ruleContext) {
        for (Rule rule : composingRules) {
            rule.run(ruleContext);
        }
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public String toString() {
        return "ReportAsSingleViolationRule{" + composingRules.size() + " composing rules}";
    }
}
