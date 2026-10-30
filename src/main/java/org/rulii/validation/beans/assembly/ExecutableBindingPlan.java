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
import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.model.BindDeclarationException;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ParameterDefinition;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the parameters of an executable are bound when its parameters or return value are validated:
 * one binding per parameter, named after the parameter, plus {@link BindingPlan#PARAMETERS} holding
 * the arguments as a whole for cross-parameter rules. A parameter whose name is reserved
 * ({@code ruleViolations}, {@code $this}) or already taken is bound under a {@code $}-suffixed name,
 * as the bean plan does for properties. The return value is bound by the caller under
 * {@link BindingPlan#RETURN_VALUE}.
 *
 * <p>The executable's {@code @Bind} bindings ({@link PlannedBind}) live beside the arguments in the
 * parameters flow, or beside the return value in the return-value flow, as declared;
 * {@link #bind(RuleContext, boolean)} creates the side's bindings once the flow's context exists. A
 * parameters-side binding may not take a reserved name or the binding name of a parameter.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ExecutableBindingPlan {

    private final ExecutableDefinition executable;
    private final Map<Integer, String> bindings;
    private final List<PlannedBind> parameterBinds;
    private final List<PlannedBind> returnValueBinds;

    private ExecutableBindingPlan(ExecutableDefinition executable, Map<Integer, String> bindings, List<PlannedBind> parameterBinds,
                                  List<PlannedBind> returnValueBinds) {
        super();
        this.executable = executable;
        this.bindings = bindings;
        this.parameterBinds = parameterBinds;
        this.returnValueBinds = returnValueBinds;
    }

    /**
     * Plans the bindings of an executable's parameters, without {@code @Bind} bindings.
     *
     * @param executable the executable definition.
     * @return the plan.
     */
    public static ExecutableBindingPlan of(ExecutableDefinition executable) {
        return of(executable, List.of(), List.of());
    }

    /**
     * Plans the bindings of an executable's parameters.
     *
     * @param executable the executable definition.
     * @param parameterBinds the {@code @Bind} bindings of the parameters side, planned by the materializer.
     * @param returnValueBinds the {@code @Bind} bindings of the return-value side, planned by the materializer.
     * @return the plan.
     * @throws BindDeclarationException if a binding takes a reserved name, or a parameters-side one the
     *         binding name of a parameter.
     */
    public static ExecutableBindingPlan of(ExecutableDefinition executable, List<PlannedBind> parameterBinds,
                                           List<PlannedBind> returnValueBinds) {
        Assert.notNull(executable, "executable cannot be null.");
        Assert.notNull(parameterBinds, "parameterBinds cannot be null.");
        Assert.notNull(returnValueBinds, "returnValueBinds cannot be null.");
        Map<Integer, String> bindings = new LinkedHashMap<>();

        for (ParameterDefinition parameter : executable.getParameters()) {
            String name = parameter.getName();
            bindings.put(parameter.getIndex(), unique(BindingPlan.RESERVED_NAMES.contains(name) ? name + "$" : name, bindings.values()));
        }

        BindingPlan.check(parameterBinds, bindings.values(), "parameter");
        BindingPlan.check(returnValueBinds, List.of(), "parameter");

        return new ExecutableBindingPlan(executable, Collections.unmodifiableMap(bindings), List.copyOf(parameterBinds),
                List.copyOf(returnValueBinds));
    }

    private static String unique(String candidate, Iterable<String> taken) {
        if (!contains(taken, candidate)) return candidate;
        int counter = 2;
        while (contains(taken, candidate + "$" + counter)) counter++;
        return candidate + "$" + counter;
    }

    private static boolean contains(Iterable<String> taken, String candidate) {
        for (String name : taken) {
            if (name.equals(candidate)) return true;
        }
        return false;
    }

    public ExecutableDefinition getExecutable() {
        return executable;
    }

    /**
     * The binding name of each parameter, by index.
     *
     * @return the names; unmodifiable.
     */
    public Map<Integer, String> getBindings() {
        return bindings;
    }

    /**
     * The {@code @Bind} bindings of the parameters side, as planned.
     *
     * @return the bindings; unmodifiable, empty if none.
     */
    public List<PlannedBind> getParameterBinds() {
        return parameterBinds;
    }

    /**
     * The {@code @Bind} bindings of the return-value side, as planned: one per name, the chain resolved.
     *
     * @return the bindings; unmodifiable, empty if none.
     */
    public List<PlannedBind> getReturnValueBinds() {
        return returnValueBinds;
    }

    /**
     * Binds one side's {@code @Bind} bindings into the current scope of the given context, after the
     * arguments or the return value are bound: a constant as is, an expression lazily, evaluated on
     * first read against this context and remembered for the scope's lifetime.
     *
     * @param ruleContext the context of the flow validating the call.
     * @param parameters true for the parameters side, false for the return-value side.
     */
    public void bind(RuleContext ruleContext, boolean parameters) {
        BindingPlan.bind(ruleContext, parameters ? parameterBinds : returnValueBinds);
    }

    /**
     * The binding a parameter's rules read.
     *
     * @param parameter the parameter.
     * @return the binding name.
     */
    public String getBindingName(ParameterDefinition parameter) {
        Assert.notNull(parameter, "parameter cannot be null.");
        String result = bindings.get(parameter.getIndex());
        Assert.notNull(result, "parameter [" + parameter.getName() + "] is not a parameter of [" + executable.getName() + "]");
        return result;
    }

    /**
     * Binds the arguments: each parameter under its binding name, and the array under
     * {@link BindingPlan#PARAMETERS}.
     *
     * @param bindings the bindings to add to.
     * @param arguments the arguments, one per parameter.
     */
    public void load(Bindings bindings, Object[] arguments) {
        Assert.notNull(bindings, "bindings cannot be null.");
        Assert.notNull(arguments, "arguments cannot be null.");
        Assert.isTrue(arguments.length == executable.getParameters().size(), "[" + executable.getName() + "] takes "
                + executable.getParameters().size() + " arguments, " + arguments.length + " given.");

        for (ParameterDefinition parameter : executable.getParameters()) {
            bindings.bind(Binding.builder().with(bindings(parameter), bindingType(parameter))
                    .value(arguments[parameter.getIndex()])
                    .editable(false)
                    .build());
        }

        bindings.bind(Binding.builder().with(BindingPlan.PARAMETERS, Object[].class).value(arguments).editable(false).build());
    }

    private String bindings(ParameterDefinition parameter) {
        return bindings.get(parameter.getIndex());
    }

    private static Type bindingType(ParameterDefinition parameter) {
        Type type = parameter.getGenericType();
        return isConcrete(type) ? type : parameter.getType();
    }

    private static boolean isConcrete(Type type) {
        if (type instanceof Class<?>) return true;
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (!isConcrete(argument)) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ExecutableBindingPlan{" + executable.getName() + " " + List.copyOf(bindings.values())
                + (parameterBinds.isEmpty() ? "" : " binds=" + parameterBinds)
                + (returnValueBinds.isEmpty() ? "" : " returnValueBinds=" + returnValueBinds) + '}';
    }
}
