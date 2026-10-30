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
package org.rulii.validation.beans;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import org.rulii.bind.Binding;
import org.rulii.bind.Bindings;
import org.rulii.bind.NamedScope;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.context.RuleContextBuilder;
import org.rulii.context.RuleContextOptions;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.registry.AlreadyRegisteredException;
import org.rulii.registry.MutableRuleRegistry;
import org.rulii.registry.RuleRegistry;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.descriptor.BeanDefinitionDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.GroupPlan;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.message.RuliiMessageInterpolator;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.BeanDefinitionLoader;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.traversal.ContainerWalker;
import org.rulii.validation.beans.traversal.RuleFlowAssembler;
import org.rulii.validation.beans.traversal.Traversal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The entry point: a {@link Validator} that reads {@code jakarta.validation} constraints off beans,
 * materializes rulii rules from them and runs those rules. Built once through {@link #builder()},
 * immutable, thread-safe, reusable; everything per class (definition, rules, flow) is read on first use
 * and cached for the validator's lifetime.
 * <p>
 * It is a drop-in for any consumer of a {@code jakarta.validation.Validator}: {@link #validate},
 * {@link #validateProperty} and {@link #validateValue} return the spec's set of constraint violations,
 * {@link #getConstraintsForClass} describes a class. The result is also a {@link BeanValidationResult},
 * which owns the rulii {@link BeanRuleViolations} the rules reported into. {@link #forExecutables()}
 * validates method and constructor calls. The validator is built explicitly here, or by the bootstrap
 * ({@code Validation.buildDefaultValidatorFactory()}) when the library is on the classpath.
 * <p>
 * rulii-native use: {@link #validateIn} runs a validation inside an existing {@link RuleContext} so
 * annotation rules, hand-written rules and the trace share one execution; {@link #getRuleFlow} hands out
 * the per-class {@link RuleFlow} to embed in a flow of your own, {@link #getParametersRuleFlow} and
 * {@link #getReturnValueRuleFlow} the flows of a method or constructor. Every flow binds the groups being
 * processed as {@code $groups}, for rules that branch on them.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidator implements Validator {

    private final RuleRegistry registry;
    private final TraversableResolver traversableResolver;
    private final MessageInterpolator messageInterpolator;
    private final MessageInterpolator consultedInterpolator;
    private final BeanValidationOptions options;
    private final BeanDefinitionLoader loader;
    private final RuleMaterializer materializer;
    private final RuleFlowAssembler assembler;
    private final ExecutableBeanValidator executableValidator;
    private final boolean registerFlows;
    private final boolean failFast;
    private final ConcurrentMap<Class<?>, Entry> cache = new ConcurrentHashMap<>();

    BeanValidator(RuleRegistry registry, ValueExtractorRegistry extractors, ConstraintMapperRegistry mappers,
                  RuleContextOptions options, ParameterNameProvider parameterNameProvider, MessageInterpolator messageInterpolator,
                  TraversableResolver traversableResolver, boolean registerFlows, boolean failFast) {
        super();
        Assert.notNull(extractors, "extractors cannot be null.");
        Assert.notNull(mappers, "mappers cannot be null.");
        Assert.notNull(parameterNameProvider, "parameterNameProvider cannot be null.");
        Assert.notNull(traversableResolver, "traversableResolver cannot be null.");
        Assert.isTrue(!registerFlows || registry instanceof MutableRuleRegistry, "registerFlows needs a MutableRuleRegistry.");
        this.registry = registry;
        this.registerFlows = registerFlows;
        this.failFast = failFast;
        this.traversableResolver = traversableResolver;
        this.messageInterpolator = messageInterpolator;
        // Our own interpolator renders exactly what the rules render; consulting it would only render twice.
        this.consultedInterpolator = messageInterpolator instanceof RuliiMessageInterpolator ? null : messageInterpolator;
        this.options = BeanValidationOptions.wrap(options != null ? options : StandardRuleContextOptions.build(), mappers);
        this.loader = new BeanDefinitionLoader(extractors, mappers, parameterNameProvider);
        this.materializer = new RuleMaterializer(mappers, this.options.getConverterRegistry(), failFast);
        this.assembler = new RuleFlowAssembler(this::getRuleFlow, extractors);
        this.executableValidator = new ExecutableBeanValidator(this);
    }

    /**
     * Starts building a validator.
     *
     * @return the builder.
     */
    public static BeanValidatorBuilder builder() {
        return new BeanValidatorBuilder();
    }

    // ---- jakarta.validation.Validator ----

    /**
     * Validates an object: its constraints, its {@code @Validate} rules, and everything it cascades
     * into, for the requested groups (none means {@code Default}).
     *
     * @param object the object to validate.
     * @param groups the requested groups.
     * @param <T> the type of the object.
     * @return the violations, also a {@link BeanValidationResult}.
     * @throws IllegalArgumentException if the object is null, or a group is not an interface.
     */
    @Override
    public <T> BeanValidationResult<T> validate(T object, Class<?>... groups) {
        Assert.notNull(object, "object cannot be null.");
        GroupPlan plan = GroupPlan.of(groups);
        BeanRuleViolations violations = violations(object, object.getClass(), null, null);

        run(plan, violations, failFast, step -> {
            ScopedBindings bindings = Bindings.builder().scoped();
            bind(bindings, object, traversal(step));
            bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
            getRuleFlow(object.getClass()).run(context(bindings));
        });

        return new BeanValidationResult<>(object, violations);
    }

    /**
     * Runs a step body for every step of a plan, in order: plain groups first, then each requested
     * sequence member by member, a sequence stopping after the first step that added violations.
     *
     * @param plan the plan.
     * @param violations the collector the steps report into.
     * @param step the body of one step.
     */
    /**
     * Runs one validation call of the specification API: a step per requested group, a sequence stopping
     * at its first step with violations. A failure inside the run surfaces the way the specification
     * has it: rulii wraps whatever a rule throws in {@link UnrulyException}; the {@link ValidationException}
     * underneath (a failing constraint validator, interpolator or resolver, or one thrown on purpose) is
     * rethrown as itself, anything else as a {@link BeanValidationException} keeping the rulii chain as
     * its cause. The rulii-native entry points ({@link #validateIn}, the flows) keep rulii's wrapping.
     */
    static void run(GroupPlan plan, BeanRuleViolations violations, boolean failFast, Consumer<RequestedGroups> step) {
        try {
            for (GroupPlan.Pass pass : plan.getPasses()) {
                for (RequestedGroups groups : pass.steps()) {
                    int before = violations.size();
                    step.accept(groups);
                    if (failFast && violations.size() > 0) return;
                    if (pass.sequence() && violations.size() > before) break;
                }
            }
        } catch (UnrulyException e) {
            throw unwrap(e);
        }
    }

    /**
     * The exception a failed run reports to a caller of the specification API: the first
     * {@link ValidationException} in the cause chain of the rulii wrapper, else a
     * {@link BeanValidationException} wrapping the whole chain.
     *
     * @param e the rulii wrapper.
     * @return the exception to throw.
     */
    static ValidationException unwrap(UnrulyException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ValidationException validation) return validation;
        }

        return new BeanValidationException("validation failed: " + e.getMessage(), e);
    }

    /**
     * Validates the constraints declared on one property of an object, container element constraints
     * included: no class-level constraints, no {@code @Validate} rules, no cascading, exactly as the
     * spec reads it.
     *
     * @param object the object.
     * @param propertyName the property.
     * @param groups the requested groups.
     * @param <T> the type of the object.
     * @return the violations, also a {@link BeanValidationResult}.
     * @throws IllegalArgumentException if the object is null, the property is not a property of its
     *         class, or a group is not an interface.
     */
    @Override
    public <T> BeanValidationResult<T> validateProperty(T object, String propertyName, Class<?>... groups) {
        Assert.notNull(object, "object cannot be null.");
        BeanRules rules = getBeanRules(object.getClass());
        PropertyDefinition property = property(rules, propertyName);
        GroupPlan plan = GroupPlan.of(groups);
        BeanRuleViolations violations = violations(object, object.getClass(), null, null);
        List<Class<?>> sequence = rules.getDefinition().getDefaultGroupSequence();

        run(plan, violations, failFast, step -> {
            Traversal traversal = traversal(step);
            ScopedBindings bindings = Bindings.builder().scoped();
            bind(bindings, object, traversal);
            bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
            rules.getBindingPlan().load(bindings, object);
            RuleContext context = context(bindings);
            rules.getBindingPlan().bind(context);

            try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), rules, object)) {
                traversal.forDefaultSequence(context, sequence, current -> {
                    // The traversable resolver is asked for this property as it is for any; validateValue, with no object, asks nothing.
                    RuleSet<?> ruleSet = traversal.consultsResolver()
                            ? rules.ruleSet(current, rule -> propertyName.equals(rule.getLeaf()) && traversal.isReachable(context, object, rule))
                            : rules.propertyRuleSet(propertyName, current);
                    ruleSet.run(context);
                    ContainerWalker walker = new ContainerWalker(context, rules, false);

                    for (ContainerElementDefinition node : property.getContainerElements()) {
                        if (!traversal.isReachable(context, object, propertyName, node.getSourceDeclaration().getKind())) continue;
                        Object container = rules.getBindingPlan().read(bindings, object, property, node.getSourceDeclaration());
                        walker.walk(container, node, PropertyPath.root().property(propertyName));
                    }
                });
            }
        });

        return new BeanValidationResult<>(object, violations);
    }

    /**
     * Validates a value as if it were the given property of an object of the given class: every
     * constraint declared on the property, whichever declaration carries it, and its container element
     * constraints; no class-level constraints, no {@code @Validate} rules, no cascading. The violations
     * have no root or leaf bean, only the root bean class.
     *
     * @param beanType the class.
     * @param propertyName the property.
     * @param value the value to validate; may be null.
     * @param groups the requested groups.
     * @param <T> the type of the class.
     * @return the violations, also a {@link BeanValidationResult}.
     * @throws IllegalArgumentException if the class is null, the property is not a property of it, or a
     *         group is not an interface.
     */
    @Override
    public <T> BeanValidationResult<T> validateValue(Class<T> beanType, String propertyName, Object value, Class<?>... groups) {
        Assert.notNull(beanType, "beanType cannot be null.");
        Entry entry = entry(beanType);
        PropertyDefinition property = property(entry.rules, propertyName);
        BeanRules rules = entry.valueRules.computeIfAbsent(propertyName, name -> materializer.materializeValue(entry.rules, property));
        GroupPlan plan = GroupPlan.of(groups);
        BeanRuleViolations violations = violations(null, beanType, null, null);
        List<Class<?>> sequence = entry.rules.getDefinition().getDefaultGroupSequence();

        run(plan, violations, failFast, step -> {
            Traversal traversal = traversal(step);
            ScopedBindings bindings = Bindings.builder().scoped();
            bind(bindings, null, traversal);
            bindings.bind(Binding.builder().with(rules.getBindingPlan().getBindingName(property), Object.class).value(value).build());
            bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
            RuleContext context = context(bindings);

            try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), rules, null)) {
                traversal.forDefaultSequence(context, sequence, current -> {
                    rules.ruleSet(current).run(context);
                    ContainerWalker walker = new ContainerWalker(context, rules, false);

                    for (ContainerElementDefinition node : property.getContainerElements()) {
                        walker.walk(value, node, PropertyPath.root().property(propertyName));
                    }
                });
            }
        });

        return new BeanValidationResult<>(null, violations);
    }

    private static PropertyDefinition property(BeanRules rules, String propertyName) {
        Assert.hasText(propertyName, "propertyName cannot be null/empty.");
        PropertyDefinition result = rules.getDefinition().getProperty(propertyName);
        Assert.notNull(result, "[" + propertyName + "] is not a property of [" + rules.getBeanClass().getName() + "]");
        return result;
    }

    /**
     * Describes the constraints of a class, reading it on first request.
     *
     * @param clazz the class.
     * @return its descriptor, also a {@link BeanDefinitionDescriptor}.
     * @throws IllegalArgumentException if the class is null.
     */
    @Override
    public BeanDefinitionDescriptor getConstraintsForClass(Class<?> clazz) {
        return new BeanDefinitionDescriptor(getBeanDefinition(clazz));
    }

    /**
     * This validator, viewed as the given type.
     *
     * @throws ValidationException if it is not of that type.
     */
    @Override
    public <T> T unwrap(Class<T> type) {
        Assert.notNull(type, "type cannot be null.");
        if (type.isInstance(this)) return type.cast(this);
        throw new ValidationException("cannot unwrap [" + getClass().getName() + "] to [" + type.getName() + "]");
    }

    /**
     * The validator of method and constructor calls: parameters, return values and cross-parameter
     * constraints. One per validator, sharing its cache.
     *
     * @return the executable validator.
     */
    @Override
    public ExecutableBeanValidator forExecutables() {
        return executableValidator;
    }

    // ---- rulii-native ----

    /**
     * Validates an object inside an existing context, so its rules share the context's bindings, trace
     * and registry with whatever else runs there. The violations go to the {@link RuleViolations} bound in
     * the context when there is one (path-stamped either way); otherwise a {@link BeanRuleViolations} is
     * bound for the run and returned. {@code @Validate} names resolve through this validator's registry
     * when it has one (a context always carries a registry, so the context's cannot signal "none");
     * a validator built without a registry resolves through the context's. Messages render through the
     * context's own resolver and formatter: build the context from {@link BeanValidationOptions} to get
     * Bean Validation messages there too.
     *
     * @param context the context to run in.
     * @param object the object to validate.
     * @param groups the requested groups.
     * @return the collector the violations went to.
     */
    public RuleViolations validateIn(RuleContext context, Object object, Class<?>... groups) {
        Assert.notNull(context, "context cannot be null.");
        Assert.notNull(object, "object cannot be null.");
        RequestedGroups requested = RequestedGroups.of(groups);
        ScopedBindings bindings = context.getBindings();
        RuleViolations existing = Traversal.anyCollector(context);
        BeanRuleViolations own = existing == null ? violations(object, object.getClass(), null, null) : null;
        NamedScope scope = bindings.addScope();

        try {
            bind(scope.getBindings(), object, traversal(requested));
            if (own != null) {
                scope.getBindings().bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class)
                        .value(own).primary(true).build());
            }
            getRuleFlow(object.getClass()).run(context);
        } finally {
            bindings.removeScope(scope);
        }

        return existing != null ? existing : own;
    }

    /**
     * The definition of a bean class, read on first request.
     *
     * @param beanClass the class.
     * @return its definition.
     */
    public BeanDefinition getBeanDefinition(Class<?> beanClass) {
        return entry(beanClass).rules.getDefinition();
    }

    /**
     * The rules materialized for a bean class, read on first request.
     *
     * @param beanClass the class.
     * @return its rules.
     */
    public BeanRules getBeanRules(Class<?> beanClass) {
        return entry(beanClass).rules;
    }

    /**
     * The flow validating a bean class, assembled on first request: run it with {@code $this} bound to
     * embed the validation in a flow of your own.
     *
     * @param beanClass the class.
     * @return its flow.
     */
    public RuleFlow<?> getRuleFlow(Class<?> beanClass) {
        return entry(beanClass).flow;
    }

    /**
     * The rules materialized for a method or constructor, as defined on the given class: the method
     * is matched by signature, so the method of an interface or a superclass finds the definition of
     * the overriding chain on the class. Read on first request, along with the executable's flows.
     *
     * @param beanClass the class the executable is called on, or the class a constructor constructs.
     * @param executable the method or constructor.
     * @return its rules; null when the executable is not constrained on the class.
     */
    public ExecutableRules getExecutableRules(Class<?> beanClass, Executable executable) {
        ExecutableEntry entry = executable(beanClass, executable);
        return entry != null ? entry.rules : null;
    }

    /**
     * The flow validating the arguments of a method or constructor, assembled on first request: run it
     * with the arguments bound by parameter name (and {@code $this} bound to the object, for a method)
     * to embed the validation in a flow of your own.
     *
     * @param beanClass the class the executable is called on, or the class a constructor constructs.
     * @param executable the method or constructor.
     * @return its parameters flow; null when no parameter is constrained or cascaded and no
     *         cross-parameter constraint is declared.
     * @see RuleFlowAssembler#assembleParameters
     */
    public RuleFlow<?> getParametersRuleFlow(Class<?> beanClass, Executable executable) {
        ExecutableEntry entry = executable(beanClass, executable);
        return entry != null ? entry.parametersFlow : null;
    }

    /**
     * The flow validating the return value of a method or constructor, assembled on first request: run
     * it with {@code $returnValue} bound (and {@code $this}, for a method).
     *
     * @param beanClass the class the executable is called on, or the class a constructor constructs.
     * @param executable the method or constructor.
     * @return its return-value flow; null when the return value is neither constrained nor cascaded.
     * @see RuleFlowAssembler#assembleReturnValue
     */
    public RuleFlow<?> getReturnValueRuleFlow(Class<?> beanClass, Executable executable) {
        ExecutableEntry entry = executable(beanClass, executable);
        return entry != null ? entry.returnValueFlow : null;
    }

    /**
     * What the validator keeps for an executable, materialized and assembled on first request.
     *
     * @param beanClass the class the executable is called on, or the class a constructor constructs.
     * @param executable the method or constructor.
     * @return the entry; null when the executable is not constrained on the class.
     */
    ExecutableEntry executable(Class<?> beanClass, Executable executable) {
        Assert.notNull(executable, "executable cannot be null.");
        Entry entry = entry(beanClass);
        BeanDefinition definition = entry.rules.getDefinition();
        ExecutableDefinition found = executable instanceof Constructor<?>
                ? definition.getConstructor(executable.getParameterTypes())
                : definition.getMethod(executable.getName(), executable.getParameterTypes());

        if (found == null) return null;

        return entry.executables.computeIfAbsent(found, def -> {
            ExecutableRules rules = materializer.materialize(def);
            RuleFlow<?> parametersFlow = def.hasConstrainedParameters() ? assembler.assembleParameters(entry.rules, rules) : null;
            RuleFlow<?> returnValueFlow = def.hasConstrainedReturnValue() ? assembler.assembleReturnValue(entry.rules, rules) : null;
            register(parametersFlow);
            register(returnValueFlow);
            return new ExecutableEntry(rules, parametersFlow, returnValueFlow);
        });
    }

    /**
     * Whether the flows this validator builds are registered in its registry as they are built.
     *
     * @return true if they are.
     * @see BeanValidatorBuilder#registerFlows(boolean)
     */
    public boolean isRegisteringFlows() {
        return registerFlows;
    }

    /**
     * Registers a flow just built, when asked to. A name already in use is left alone: another
     * validator over the same registry built that class first, or the application holds something
     * under that name; this validator runs its own flow from its cache either way.
     */
    private void register(RuleFlow<?> flow) {
        if (!registerFlows || flow == null) return;
        MutableRuleRegistry mutable = (MutableRuleRegistry) registry;
        if (mutable.isNameInUse(flow.getName())) return;

        try {
            mutable.register(flow);
        } catch (AlreadyRegisteredException e) {
            // Registered between the check and now, by another thread or validator: theirs stands.
        }
    }

    /**
     * The provider naming the parameters of constrained executables.
     *
     * @return the provider.
     */
    public ParameterNameProvider getParameterNameProvider() {
        return loader.getParameterNameProvider();
    }

    /**
     * The message interpolator given to the builder. A {@link RuliiMessageInterpolator} renders what the
     * rules render and is not consulted; any other interpolator renders the message of every constraint
     * violation from the template as declared, and chooses the locale.
     *
     * @return the interpolator; null when none was given, and the rules' messages stand.
     */
    public MessageInterpolator getMessageInterpolator() {
        return messageInterpolator;
    }

    /**
     * A report for a validation call, rendering through the interpolator when one is consulted.
     *
     * @param rootBean the object validation started from; null for constructor parameters or a value.
     * @param rootBeanClass the class validation started from.
     * @param executableParameters the arguments validated; null unless parameters are.
     * @param executableReturnValue the return value validated; null unless a return value is.
     * @return the report.
     */
    BeanRuleViolations violations(Object rootBean, Class<?> rootBeanClass, Object[] executableParameters, Object executableReturnValue) {
        return new BeanRuleViolations(rootBean, rootBeanClass, executableParameters, executableReturnValue, consultedInterpolator);
    }

    /**
     * The registry {@code @Validate} names resolve through.
     *
     * @return the registry; null when none was configured.
     */
    public RuleRegistry getRuleRegistry() {
        return registry;
    }

    /**
     * The context options every validation context is built from: the options given to the builder
     * (the standard ones when none were), with Bean Validation messages.
     *
     * @return the options.
     */
    public BeanValidationOptions getOptions() {
        return options;
    }

    /**
     * The value extractors.
     *
     * @return the registry.
     */
    public ValueExtractorRegistry getValueExtractorRegistry() {
        return loader.getValueExtractorRegistry();
    }

    /**
     * The constraint mappers.
     *
     * @return the registry.
     */
    public ConstraintMapperRegistry getConstraintMapperRegistry() {
        return loader.getConstraintMapperRegistry();
    }

    private Entry entry(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        return cache.computeIfAbsent(beanClass, type -> {
            BeanRules rules = materializer.materialize(loader.load(type));
            RuleFlow<?> flow = assembler.assemble(rules);
            register(flow);
            return new Entry(rules, flow);
        });
    }

    /**
     * Whether a call stops at its first violation (fail-fast mode): the result then holds that one
     * violation and nothing after it was validated. Off unless the builder or the bootstrap turned it on.
     *
     * @return true for fail-fast.
     */
    public boolean isFailFast() {
        return failFast;
    }

    Traversal traversal(RequestedGroups groups) {
        return new Traversal(groups, this::getRuleFlow, loader.getValueExtractorRegistry(), registry, traversableResolver, failFast);
    }

    /**
     * Binds what every run starts from: the object as {@code $this} (null for a value, or for the
     * arguments of a constructor), the traversal, and its current groups as {@code $groups}.
     *
     * @param bindings the bindings of the run.
     * @param object the object; may be null.
     * @param traversal the traversal of the run.
     */
    static void bind(Bindings bindings, Object object, Traversal traversal) {
        bindings.bind(Binding.builder().with(BindingPlan.THIS, Object.class).value(object).build());
        bindings.bind(Binding.builder().with(BindingPlan.TRAVERSAL, Traversal.class).value(traversal).build());
        bindings.bind(Binding.builder().with(BindingPlan.GROUPS, BindingPlan.GROUPS_TYPE).value(traversal.getCurrentGroups())
                .editable(false).build());
    }

    /**
     * The traversable resolver asked before a property is validated or cascaded into: the one given
     * to the builder, else the runtime's default ({@link org.rulii.validation.beans.traversal.TraversableResolvers#getDefault()}:
     * the Jakarta Persistence one when the persistence API is present, otherwise one that reaches everything).
     *
     * @return the resolver.
     */
    public TraversableResolver getTraversableResolver() {
        return traversableResolver;
    }

    RuleContext context(Bindings bindings) {
        RuleContextBuilder builder = RuleContext.builder().with(options);
        builder.bindings(bindings);
        if (registry != null) builder.registry(registry);
        return builder.build();
    }

    @Override
    public String toString() {
        return "BeanValidator{classes=" + cache.size() + ", registry=" + (registry != null) + ", registerFlows=" + registerFlows + '}';
    }

    /**
     * What the validator keeps per class.
     */
    private static final class Entry {

        private final BeanRules rules;
        private final RuleFlow<?> flow;
        private final ConcurrentMap<String, BeanRules> valueRules = new ConcurrentHashMap<>();
        private final ConcurrentMap<ExecutableDefinition, ExecutableEntry> executables = new ConcurrentHashMap<>();

        private Entry(BeanRules rules, RuleFlow<?> flow) {
            super();
            this.rules = rules;
            this.flow = flow;
        }
    }

    /**
     * What the validator keeps per constrained executable: its rules and its flows, a flow null when
     * that side of the executable has nothing to validate.
     */
    static final class ExecutableEntry {

        final ExecutableRules rules;
        final RuleFlow<?> parametersFlow;
        final RuleFlow<?> returnValueFlow;

        private ExecutableEntry(ExecutableRules rules, RuleFlow<?> parametersFlow, RuleFlow<?> returnValueFlow) {
            super();
            this.rules = rules;
            this.parametersFlow = parametersFlow;
            this.returnValueFlow = returnValueFlow;
        }
    }
}
