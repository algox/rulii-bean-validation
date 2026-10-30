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
package org.rulii.validation.beans.traversal;

import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.ruleflow.RuleFlowExecutionContext;
import org.rulii.ruleflow.command.RuleFlowCommand;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ParameterDefinition;
import org.rulii.validation.beans.model.ReturnValueDefinition;

import java.util.List;

/**
 * The single step of an executable flow, wrapping the steps that validate the arguments or the
 * return value of a method or constructor. Around the body it opens the collector frame at the
 * executable's node under the current path ({@code place(Order, int)}, {@code OrderService(String)}),
 * so the body reports at the parameter, cross-parameter and return-value nodes beneath it; and, as
 * every {@link CollectingCommand}, it binds a path-aware collector for an embedded run. Nothing is
 * marked validated: an executable is not an object, and the arguments cascaded into are marked by
 * the descent.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ValidateExecutableCommand extends CollectingCommand {

    private final ExecutableRules rules;
    private final boolean parameters;

    /**
     * Creates the step.
     *
     * @param rules the executable's rules.
     * @param parameters true for the parameters flow, false for the return-value flow.
     * @param body the steps.
     */
    ValidateExecutableCommand(ExecutableRules rules, boolean parameters, List<RuleFlowCommand> body) {
        super(body);
        Assert.notNull(rules, "rules cannot be null.");
        this.rules = rules;
        this.parameters = parameters;
    }

    @Override
    protected BeanRuleViolations collector(RuleContext ruleContext) {
        Bindings bindings = ruleContext.getBindings();
        Object root = bindings.getValue(BindingPlan.ROOT);
        return parameters
                ? new BeanRuleViolations(root, rules.getBeanClass(), bindings.getValue(BindingPlan.PARAMETERS), null)
                : new BeanRuleViolations(root, rules.getBeanClass(), null, bindings.getValue(BindingPlan.RETURN_VALUE));
    }

    @Override
    protected void run(RuleFlowExecutionContext ctx) {
        RuleContext ruleContext = ctx.getRuleContext();
        BeanRuleViolations collector = Traversal.collector(ruleContext);
        rules.getBindingPlan().bind(ruleContext, parameters);

        try (BeanRuleViolations.Frame ignored = collector.enter(path(collector.getCurrentPath()), rules, bean(ruleContext))) {
            super.run(ctx);
        }
    }

    private PropertyPath path(PropertyPath current) {
        ExecutableDefinition definition = rules.getDefinition();
        return definition.isConstructor()
                ? current.constructor(definition.getName(), definition.getParameterTypes())
                : current.method(definition.getName(), definition.getParameterTypes());
    }

    /**
     * The object the executable is called on: null for a constructor's arguments, or when the flow
     * was run without binding {@code $this}.
     *
     * @param ruleContext the context of the run.
     * @return the object; may be null.
     */
    static Object bean(RuleContext ruleContext) {
        Bindings bindings = ruleContext.getBindings();
        return bindings.contains(BindingPlan.THIS) ? bindings.getValue(BindingPlan.THIS) : null;
    }

    /**
     * Walks the constrained container element positions of every argument.
     *
     * @param rules the executable's rules.
     * @param values the arguments.
     * @param base the executable's node.
     * @param walker the walker of the run.
     */
    static void walkParameterElements(ExecutableRules rules, Object[] values, PropertyPath base, ContainerWalker walker) {
        for (ParameterDefinition parameter : rules.getDefinition().getParameters()) {
            PropertyPath path = base.parameter(parameter.getName(), parameter.getIndex());

            for (ContainerElementDefinition node : parameter.getContainerElements()) {
                walker.walk(values[parameter.getIndex()], node, path);
            }
        }
    }

    /**
     * Walks the constrained container element positions of the return value.
     *
     * @param returnValue the return value definition.
     * @param value the value returned.
     * @param path the return-value node.
     * @param walker the walker of the run.
     */
    static void walkReturnValueElements(ReturnValueDefinition returnValue, Object value, PropertyPath path, ContainerWalker walker) {
        for (ContainerElementDefinition node : returnValue.getContainerElements()) {
            walker.walk(value, node, path);
        }
    }

    @Override
    public String toString() {
        return "validate " + (parameters ? "arguments" : "return value") + " of " + rules.getDefinition().getName();
    }
}
