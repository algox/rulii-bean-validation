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

import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import org.rulii.bind.Binding;
import org.rulii.bind.Bindings;
import org.rulii.bind.NamedScope;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.context.RuleContextBuilder;
import org.rulii.context.RuleContextOptions;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.lib.spring.util.Assert;
import org.rulii.registry.RuleRegistry;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.descriptor.BeanDefinitionDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.BeanDefinitionLoader;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.traversal.ContainerWalker;
import org.rulii.validation.beans.traversal.RuleFlowAssembler;
import org.rulii.validation.beans.traversal.Traversal;

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
 * which owns the rulii {@link BeanRuleViolations} the rules reported into. Executable validation is not
 * supported, so {@link #forExecutables()} throws. The validator is wired explicitly; it is not found by
 * {@code Validation.buildDefaultValidatorFactory()}.
 * <p>
 * rulii-native use: {@link #validateIn} runs a validation inside an existing {@link RuleContext} so
 * annotation rules, hand-written rules and the trace share one execution; {@link #getRuleFlow} hands out
 * the per-class {@link RuleFlow} to embed in a flow of your own.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidator implements Validator {

    private final RuleRegistry registry;
    private final BeanValidationOptions options;
    private final BeanDefinitionLoader loader;
    private final RuleMaterializer materializer;
    private final RuleFlowAssembler assembler;
    private final ConcurrentMap<Class<?>, Entry> cache = new ConcurrentHashMap<>();

    BeanValidator(RuleRegistry registry, ValueExtractorRegistry extractors, ConstraintMapperRegistry mappers,
                  RuleContextOptions options) {
        super();
        Assert.notNull(extractors, "extractors cannot be null.");
        Assert.notNull(mappers, "mappers cannot be null.");
        this.registry = registry;
        this.options = BeanValidationOptions.wrap(options != null ? options : StandardRuleContextOptions.build(), mappers);
        this.loader = new BeanDefinitionLoader(extractors, mappers);
        this.materializer = new RuleMaterializer(mappers);
        this.assembler = new RuleFlowAssembler(this::getRuleFlow, extractors);
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
        RequestedGroups requested = RequestedGroups.of(groups);
        BeanRuleViolations violations = new BeanRuleViolations(object);
        ScopedBindings bindings = Bindings.builder().scoped();

        bindings.bind(Binding.builder().with(BindingPlan.THIS, Object.class).value(object).build());
        bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
        bindings.bind(Binding.builder().with(BindingPlan.TRAVERSAL, Traversal.class).value(traversal(requested)).build());
        getRuleFlow(object.getClass()).run(context(bindings));

        return new BeanValidationResult<>(object, violations);
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
        RequestedGroups requested = RequestedGroups.of(groups);
        BeanRuleViolations violations = new BeanRuleViolations(object);
        ScopedBindings bindings = Bindings.builder().scoped();

        bindings.bind(Binding.builder().with(BindingPlan.THIS, Object.class).value(object).build());
        bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
        bindings.bind(Binding.builder().with(BindingPlan.TRAVERSAL, Traversal.class).value(traversal(requested)).build());
        rules.getBindingPlan().load(bindings, object);
        RuleContext context = context(bindings);

        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), rules, object)) {
            rules.propertyRuleSet(propertyName, requested).run(context);
            ContainerWalker walker = new ContainerWalker(context, rules, false);

            for (ContainerElementDefinition node : property.getContainerElements()) {
                Object container = rules.getBindingPlan().read(bindings, object, property, node.getSourceDeclaration());
                walker.walk(container, node, PropertyPath.root().property(propertyName));
            }
        }

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
        RequestedGroups requested = RequestedGroups.of(groups);
        BeanRuleViolations violations = new BeanRuleViolations(null, beanType);
        ScopedBindings bindings = Bindings.builder().scoped();

        bindings.bind(Binding.builder().with(BindingPlan.THIS, Object.class).value((Object) null).build());
        bindings.bind(Binding.builder().with(rules.getBindingPlan().getBindingName(property), Object.class).value(value).build());
        bindings.bind(Binding.builder().with(BindingPlan.RULE_VIOLATIONS, RuleViolations.class).value(violations).build());
        bindings.bind(Binding.builder().with(BindingPlan.TRAVERSAL, Traversal.class).value(traversal(requested)).build());
        RuleContext context = context(bindings);

        try (BeanRuleViolations.Frame ignored = violations.enter(PropertyPath.root(), rules, null)) {
            rules.ruleSet(requested).run(context);
            ContainerWalker walker = new ContainerWalker(context, rules, false);

            for (ContainerElementDefinition node : property.getContainerElements()) {
                walker.walk(value, node, PropertyPath.root().property(propertyName));
            }
        }

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
     * Executable (method and constructor) validation is not supported.
     *
     * @throws ValidationException always.
     */
    @Override
    public ExecutableValidator forExecutables() {
        throw new ValidationException("executable (method/constructor) validation is not supported by rulii bean validation.");
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
        BeanRuleViolations own = existing == null ? new BeanRuleViolations(object) : null;
        NamedScope scope = bindings.addScope();

        try {
            scope.getBindings().bind(Binding.builder().with(BindingPlan.THIS, Object.class).value(object).build());
            scope.getBindings().bind(Binding.builder().with(BindingPlan.TRAVERSAL, Traversal.class).value(traversal(requested)).build());
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
            return new Entry(rules, assembler.assemble(rules));
        });
    }

    private Traversal traversal(RequestedGroups groups) {
        return new Traversal(groups, this::getRuleFlow, loader.getValueExtractorRegistry(), registry);
    }

    private RuleContext context(Bindings bindings) {
        RuleContextBuilder builder = RuleContext.builder().with(options);
        builder.bindings(bindings);
        if (registry != null) builder.registry(registry);
        return builder.build();
    }

    @Override
    public String toString() {
        return "BeanValidator{classes=" + cache.size() + ", registry=" + (registry != null) + '}';
    }

    /**
     * What the validator keeps per class.
     */
    private static final class Entry {

        private final BeanRules rules;
        private final RuleFlow<?> flow;
        private final ConcurrentMap<String, BeanRules> valueRules = new ConcurrentHashMap<>();

        private Entry(BeanRules rules, RuleFlow<?> flow) {
            super();
            this.rules = rules;
            this.flow = flow;
        }
    }
}
