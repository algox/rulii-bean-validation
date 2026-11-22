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

import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleExecutionStatus;
import org.rulii.rule.RuleResult;
import org.rulii.ruleset.RuleSet;
import org.rulii.trace.Tracer;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.ValueValidationRule;

import java.util.List;
import java.util.function.Predicate;

/**
 * The rules a validation call runs at one point, in order, each run the cheapest way that gives the same
 * outcome: what the traversal runs where it used to run a {@link RuleSet} of the same rules.
 *
 * <p>A rule backed by one of rulii's own validators ({@link MaterializedRule#getCheck()}) is not run through
 * the engine: its value is read, the validator is asked ({@link ValueValidationRule#check}) and, on a failure,
 * its violation is built ({@link ValueValidationRule#buildViolation}) and added to the collector. Nothing is
 * resolved, matched or wrapped on the way, which is most of what running such a rule costs. Every other rule
 * (an {@code @Expr}, a composition, a constraint validator, a rule from a mapper of yours) is run by the
 * engine, as it always was. The rules run in the order given, whichever way each one runs, so the violations
 * arrive in the same order and a fail-fast call stops at the same rule.</p>
 *
 * <p>What a rule listener sees of a rule asked directly is what it sees of the rule run by the engine: the
 * start, the pre-condition check, the condition check, the otherwise action and the end with its result, or
 * the error. What is gone is the rule set around the rules: no rule set scope, no {@code ruleSet} and
 * {@code ruleSetStatus} bindings, no rule set events. A failure is wrapped the way the engine wraps it, down
 * to the name of the rule set the rules would have run in.</p>
 *
 * @author Max Arulananthan
 * @since 1.1
 */
public final class RulePlan {

    private final String name;
    private final MaterializedRule[] rules;
    private final boolean failFast;

    /**
     * Creates the plan.
     *
     * @param name the name of the rule set the same rules make ({@link BeanRules#ruleSet}), which names
     *             the plan in a failure.
     * @param rules the rules, in the order they run.
     * @param failFast whether the plan stops at its first failing rule.
     */
    RulePlan(String name, List<MaterializedRule> rules, boolean failFast) {
        super();
        Assert.hasText(name, "name cannot be null/empty.");
        Assert.notNull(rules, "rules cannot be null.");
        this.name = name;
        this.rules = rules.toArray(new MaterializedRule[0]);
        this.failFast = failFast;
    }

    public String getName() {
        return name;
    }

    /**
     * The rules of the plan, in the order they run.
     *
     * @return the rules; unmodifiable.
     */
    public List<MaterializedRule> getRules() {
        return List.of(rules);
    }

    public boolean isEmpty() {
        return rules.length == 0;
    }

    /**
     * Runs the rules.
     *
     * @param ruleContext the context of the run.
     * @param collector the collector the rules of the context report to: the one a rule's
     *                  {@code ruleViolations} parameter resolves to.
     * @param bean the object in scope ({@code $this}); null when there is none.
     * @throws UnrulyException if a rule fails to run.
     */
    public void run(RuleContext ruleContext, RuleViolations collector, Object bean) {
        run(ruleContext, collector, bean, null);
    }

    /**
     * Runs the rules a filter accepts. The filter is asked for every rule before the first one runs.
     *
     * @param ruleContext the context of the run.
     * @param collector the collector the rules of the context report to.
     * @param bean the object in scope ({@code $this}); null when there is none.
     * @param filter which rules run; null runs them all.
     * @throws UnrulyException if a rule fails to run.
     */
    public void run(RuleContext ruleContext, RuleViolations collector, Object bean, Predicate<MaterializedRule> filter) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.notNull(collector, "collector cannot be null.");

        if (rules.length == 0) return;

        boolean[] skipped = filter != null ? rejected(filter) : null;

        try {
            for (int i = 0; i < rules.length; i++) {
                if (skipped != null && skipped[i]) continue;

                MaterializedRule rule = rules[i];
                ValueValidationRule check = rule.getCheck();
                RuleExecutionStatus status = check != null
                        ? check(rule, check, ruleContext, collector, bean)
                        : rule.getRule().run(ruleContext).status();

                if (failFast && status.isFail()) break;
            }
        } catch (Exception e) {
            throw new UnrulyException("Error trying to run RuleSet [" + name + "]", e);
        }
    }

    private boolean[] rejected(Predicate<MaterializedRule> filter) {
        boolean[] result = new boolean[rules.length];

        for (int i = 0; i < rules.length; i++) {
            result[i] = !filter.test(rules[i]);
        }

        return result;
    }

    /**
     * Asks the validator behind a rule and reports for it, firing what a run of the rule fires and
     * failing the way a run of the rule fails.
     */
    private static RuleExecutionStatus check(MaterializedRule materialized, ValueValidationRule check, RuleContext ruleContext,
                                             RuleViolations collector, Object bean) {
        Rule rule = materialized.getRule();
        Tracer tracer = ruleContext.getTracer();
        RuleResult result = null;

        tracer.fireOnRuleStart(rule);

        try {
            Object value = read(materialized, rule, ruleContext, bean);
            RuleExecutionStatus status = ask(check, rule, ruleContext, value);

            tracer.fireOnRulePreConditionCheck(rule, rule.getPreCondition(), !status.isSkipped());

            if (!status.isSkipped()) {
                tracer.fireOnRuleConditionCheck(rule, rule.getCondition(), status.isPass());

                if (status.isFail()) {
                    report(check, rule, ruleContext, collector, value);
                    tracer.fireOnRuleOtherwiseAction(rule, rule.getOtherwiseAction());
                }
            }

            result = new RuleResult(rule, status);
            return status;
        } catch (UnrulyException e) {
            tracer.fireOnRuleError(rule, e);
            result = new RuleResult(rule, RuleExecutionStatus.ERROR);
            throw e;
        } finally {
            tracer.fireOnRuleEnd(rule, result);
        }
    }

    private static Object read(MaterializedRule materialized, Rule rule, RuleContext ruleContext, Object bean) {
        try {
            return materialized.read(ruleContext, bean);
        } catch (Exception e) {
            throw new UnrulyException("Rule(" + rule.getName() + ") Pre-condition failed.", e);
        }
    }

    private static RuleExecutionStatus ask(ValueValidationRule check, Rule rule, RuleContext ruleContext, Object value) {
        try {
            return check.check(ruleContext, value);
        } catch (Exception e) {
            throw new UnrulyException("Rule(" + rule.getName() + ") Condition failed.", e);
        }
    }

    private static void report(ValueValidationRule check, Rule rule, RuleContext ruleContext, RuleViolations collector, Object value) {
        try {
            collector.add(check.buildViolation(ruleContext, value));
        } catch (Exception e) {
            throw new UnrulyException("Rule(" + rule.getName() + ") Otherwise Action failed.", e);
        }
    }

    @Override
    public String toString() {
        return "RulePlan{" + name + ", rules=" + rules.length + (failFast ? ", fail-fast" : "") + '}';
    }
}
