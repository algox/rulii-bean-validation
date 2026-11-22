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
    private final PropertyAccessor[] readers;

    /**
     * Creates the plan.
     *
     * @param name the name of the rule set the same rules make ({@link BeanRules#ruleSet}), which names
     *             the plan in a failure.
     * @param rules the rules, in the order they run.
     * @param failFast whether the plan stops at its first failing rule.
     */
    RulePlan(String name, List<MaterializedRule> rules, boolean failFast) {
        this(name, rules, failFast, null);
    }

    /**
     * Creates a plan that can also run without bindings ({@link #runPlain}).
     *
     * @param name the name of the rule set the same rules make.
     * @param rules the rules, in the order they run.
     * @param failFast whether the plan stops at its first failing rule.
     * @param readers for each rule, in the same order, what reads its value off the object in scope: the
     *                declaration the rule reads, or the one its property binding holds; a null entry is the object
     *                itself. Null when the plan runs with bindings only.
     */
    RulePlan(String name, List<MaterializedRule> rules, boolean failFast, PropertyAccessor[] readers) {
        super();
        Assert.hasText(name, "name cannot be null/empty.");
        Assert.notNull(rules, "rules cannot be null.");
        Assert.isTrue(readers == null || readers.length == rules.size(), "a reader per rule is expected.");
        this.name = name;
        this.rules = rules.toArray(new MaterializedRule[0]);
        this.failFast = failFast;
        this.readers = readers;
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
        run(ruleContext, collector, bean, filter, null);
    }

    /**
     * Whether the plan can run without bindings: every rule is asked directly and reads its value off the
     * object in scope.
     *
     * @return true if {@link #runPlain} is available.
     * @see BeanRules#isPlain()
     */
    public boolean isPlain() {
        return readers != null;
    }

    /**
     * Runs the rules without bindings: each value is read off the object in scope, the declaration a property's
     * binding would hold being read once for all the rules of that property, as the binding has it read once.
     * The context is not looked into: it carries what a validator and its violation need (the clock, the message
     * resolver and formatter, the locale) and the tracer the rule events go to.
     *
     * @param ruleContext the context carrying the clock, the messages and the tracer.
     * @param collector the collector the violations go to.
     * @param bean the object the values are read off.
     * @param filter which rules run; null runs them all. Asked for every rule before the first one runs.
     * @throws UnrulyException if a rule fails to run.
     */
    public void runPlain(RuleContext ruleContext, RuleViolations collector, Object bean, Predicate<MaterializedRule> filter) {
        Assert.isTrue(readers != null, "plan [" + name + "] needs bindings to run.");
        run(ruleContext, collector, bean, filter, new Reads());
    }

    private void run(RuleContext ruleContext, RuleViolations collector, Object bean, Predicate<MaterializedRule> filter, Reads reads) {
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
                        ? check(rule, check, ruleContext, collector, bean, reads, reads != null ? readers[i] : null)
                        : rule.getRule().run(ruleContext).status();

                if (failFast && status.isFail()) break;
            }
        } catch (Exception e) {
            throw new UnrulyException("Error trying to run RuleSet [" + name + "]", e);
        }
    }

    /**
     * The last value read off the object in a run without bindings: the rules of a property follow one another,
     * so holding one read is holding the property's.
     */
    private static final class Reads {

        private PropertyAccessor accessor;
        private Object value;

        private Reads() {
            super();
        }

        private Object read(PropertyAccessor reader, Object bean) {
            if (reader == null) return bean;

            if (accessor != reader) {
                value = reader.read(bean);
                accessor = reader;
            }

            return value;
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
                                             RuleViolations collector, Object bean, Reads reads, PropertyAccessor reader) {
        Rule rule = materialized.getRule();
        Tracer tracer = ruleContext.getTracer();
        RuleResult result = null;

        tracer.fireOnRuleStart(rule);

        try {
            Object value = read(materialized, rule, ruleContext, bean, reads, reader);
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

    private static Object read(MaterializedRule materialized, Rule rule, RuleContext ruleContext, Object bean, Reads reads,
                               PropertyAccessor reader) {
        try {
            return reads != null ? reads.read(reader, bean) : materialized.read(ruleContext, bean);
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
