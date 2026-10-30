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

import org.rulii.bind.Binding;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.script.Script;
import org.rulii.validation.beans.model.BindDefinition;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@code @Bind} ready to bind: its definition, the type it is bound with, and either the constant
 * its literal converted to or the compiled script of its expression. Built by the materializer, held
 * by the {@link BindingPlan} of a class or the {@link ExecutableBindingPlan} of an executable, and
 * bound into a scope when the flow sets it up.
 *
 * <p>A constant is bound as is. A script is bound lazily: it is evaluated on the first read, against
 * the context of the flow that bound it, and the value is remembered for the scope's lifetime; a
 * binding nothing reads is never evaluated. The bindings of one site share an evaluation stack, so a
 * script reading a sibling binding that reads it back fails naming the cycle. A result not of the
 * declared type fails too.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PlannedBind {

    private final BindDefinition definition;
    private final Class<?> type;
    private final Object constant;
    private final Script<?> script;

    private PlannedBind(BindDefinition definition, Class<?> type, Object constant, Script<?> script) {
        super();
        this.definition = definition;
        this.type = type;
        this.constant = constant;
        this.script = script;
    }

    /**
     * A binding of a constant: the literal, or what it converted to.
     *
     * @param definition the declaration.
     * @param type the type the value is bound with.
     * @param value the value; may be null.
     * @return the planned binding.
     */
    public static PlannedBind constant(BindDefinition definition, Class<?> type, Object value) {
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(type, "type cannot be null.");
        return new PlannedBind(definition, type, value, null);
    }

    /**
     * A binding of a script, evaluated lazily.
     *
     * @param definition the declaration.
     * @param type the type the value is bound with; {@code Object} when none is declared.
     * @param script the compiled script.
     * @return the planned binding.
     */
    public static PlannedBind script(BindDefinition definition, Class<?> type, Script<?> script) {
        Assert.notNull(definition, "definition cannot be null.");
        Assert.notNull(type, "type cannot be null.");
        Assert.notNull(script, "script cannot be null.");
        return new PlannedBind(definition, type, null, script);
    }

    /**
     * The declaration.
     *
     * @return the definition.
     */
    public BindDefinition getDefinition() {
        return definition;
    }

    /**
     * The binding name.
     *
     * @return the name.
     */
    public String getName() {
        return definition.getBindingName();
    }

    /**
     * The type the value is bound with: the declared type, boxed; {@code String} for a literal and
     * {@code Object} for an expression when none is declared.
     *
     * @return the type.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Whether the binding is a constant rather than a script.
     *
     * @return true for a constant.
     */
    public boolean isConstant() {
        return script == null;
    }

    /**
     * The constant.
     *
     * @return the value; null for a script, or a null constant.
     */
    public Object getConstant() {
        return constant;
    }

    /**
     * The compiled script.
     *
     * @return the script; null for a constant.
     */
    public Script<?> getScript() {
        return script;
    }

    /**
     * Creates the binding for one scope: the constant, or a lazy, remembered evaluation of the script
     * against the given context.
     *
     * @param ruleContext the context of the flow binding it, which the script sees.
     * @param evaluating the evaluation stack the bindings of the site share, for cycle detection.
     * @return the binding, read-only.
     */
    Binding<?> binding(RuleContext ruleContext, Deque<String> evaluating) {
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.notNull(evaluating, "evaluating cannot be null.");

        if (isConstant()) {
            return Binding.builder().with(getName(), type).value(constant).editable(false).build();
        }

        return Binding.builder().with(getName(), type).value(new Evaluation(ruleContext, evaluating)).editable(false).build();
    }

    /**
     * Evaluates the script once, on first access, and remembers the value (null included).
     */
    private final class Evaluation implements Supplier<Object> {

        private final RuleContext ruleContext;
        private final Deque<String> evaluating;
        private boolean inProgress;
        private boolean evaluated;
        private Object value;

        private Evaluation(RuleContext ruleContext, Deque<String> evaluating) {
            super();
            this.ruleContext = ruleContext;
            this.evaluating = evaluating;
        }

        @Override
        public Object get() {
            if (evaluated) return value;

            if (inProgress) {
                throw new BindEvaluationException("@Bind [" + getName() + "] on [" + definition.getDeclarationSource().describe()
                        + "] reads itself: " + String.join(" -> ", chain()) + " -> " + getName() + ".", getName());
            }

            inProgress = true;
            evaluating.push(getName());

            try {
                Object result = script.run(ruleContext);

                if (type != Object.class && result != null && !type.isInstance(result)) {
                    throw new BindEvaluationException("@Bind [" + getName() + "] on [" + definition.getDeclarationSource().describe()
                            + "] evaluated to [" + result + "] of type [" + result.getClass().getName() + "], not the declared type ["
                            + type.getName() + "].", getName());
                }

                value = result;
                evaluated = true;
                return value;
            } finally {
                evaluating.pop();
                inProgress = false;
            }
        }

        /**
         * The bindings being evaluated, outermost first.
         */
        private List<String> chain() {
            List<String> result = new ArrayList<>(evaluating.size());

            for (Iterator<String> names = evaluating.descendingIterator(); names.hasNext(); ) {
                result.add(names.next());
            }

            return result;
        }
    }

    @Override
    public String toString() {
        return "PlannedBind{" + getName() + " : " + type.getSimpleName() + " = " + (isConstant() ? constant : script.getSourceText()) + '}';
    }
}
