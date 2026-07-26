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

import org.rulii.bind.Binding;
import org.rulii.bind.Bindings;
import org.rulii.bind.NamedScope;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.context.RuleContextBuilder;
import org.rulii.lib.spring.util.Assert;
import org.rulii.registry.RuleRegistry;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.text.MessageFormatter;
import org.rulii.text.MessageResolver;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.assembly.ClassValidationModel;
import org.rulii.validation.beans.assembly.ValidationFlowMaterializer;
import org.rulii.validation.beans.message.CompositeMessageResolver;
import org.rulii.validation.beans.message.StringifyingMessageFormatter;
import org.rulii.validation.beans.message.TranslatingMessageResolver;
import org.rulii.validation.beans.metadata.BeanMetadata;
import org.rulii.validation.beans.metadata.BeanMetadataReader;
import org.rulii.validation.beans.traversal.Groups;
import org.rulii.validation.beans.traversal.TraversalState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The entry point: validates beans annotated with jakarta constraints by materializing
 * and running rulii rules, reporting through path-aware {@link BeanRuleViolations}.
 * Immutable and thread-safe; all configuration is fixed at build time and per-class
 * artifacts (metadata, rules, flow) are cached per validator instance.
 *
 * <pre>{@code
 * BeanValidator validator = BeanValidator.builder().build();
 * BeanRuleViolations violations = validator.validate(order);
 * }</pre>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidator {

    public static final String VALIDATION_MESSAGES_BUNDLE = "ValidationMessages";

    private final RuleRegistry ruleRegistry;
    private final Locale locale;
    private final MessageResolver messageResolver;
    private final MessageFormatter messageFormatter;
    private final BeanMetadataReader reader;
    private final ValidationFlowMaterializer materializer;
    private final ConcurrentHashMap<Class<?>, ClassValidationModel> models = new ConcurrentHashMap<>();

    BeanValidator(ConstraintMapperRegistry mapperRegistry, RuleRegistry ruleRegistry,
                  MessageResolver userResolver, List<String> bundleBaseNames, Locale locale) {
        super();
        Assert.notNull(mapperRegistry, "mapperRegistry cannot be null.");
        this.ruleRegistry = ruleRegistry;
        this.locale = locale;
        this.reader = BeanMetadataReader.builder().mapperRegistry(mapperRegistry).build();
        this.materializer = new ValidationFlowMaterializer(mapperRegistry);
        this.messageResolver = buildResolver(mapperRegistry, userResolver, bundleBaseNames);
        this.messageFormatter = new StringifyingMessageFormatter(MessageFormatter.builder().build());
    }

    /**
     * Creates a new builder for a BeanValidator.
     *
     * @return a new {@link BeanValidatorBuilder}.
     */
    public static BeanValidatorBuilder builder() {
        return new BeanValidatorBuilder();
    }

    /**
     * Validates the given bean against the requested groups (empty means the Default
     * group), walking the object graph per its runtime classes.
     *
     * @param bean the bean to validate.
     * @param groups the requested validation groups.
     * @return the path-aware violations of the whole graph.
     */
    public BeanRuleViolations validate(Object bean, Class<?>... groups) {
        Assert.notNull(bean, "bean cannot be null.");
        ClassValidationModel model = model(bean.getClass());
        Set<Class<?>> expanded = Groups.expand(groups);
        BeanRuleViolations collector = new BeanRuleViolations();
        TraversalState state = new TraversalState(this::model, expanded, collector);
        state.markValidated(bean);
        state.pushObjectFrame(model.getLeafTable());

        Bindings bindings = Bindings.builder().standard();
        bindRun(bindings, bean, expanded, state);
        bindings.bind(Binding.builder().with("ruleViolations").type(RuleViolations.class).value(collector).build());

        RuleContextBuilder contextBuilder = RuleContext.builder().standard()
                .bindings(bindings)
                .messageResolver(messageResolver)
                .messageFormatter(messageFormatter);
        if (locale != null) contextBuilder.locale(locale);
        if (ruleRegistry != null) contextBuilder.registry(ruleRegistry);

        model.getFlow().run(contextBuilder.build());
        return collector;
    }

    /**
     * Validates the given bean inside the caller's RuleContext (its registry, locale,
     * message setup and bindings — R10.4a). A RuleViolations the caller already bound is
     * found by core's by-type matching and receives undecorated violations; otherwise
     * violations collect into the returned path-aware collector.
     *
     * @param context the caller's context.
     * @param bean the bean to validate.
     * @param groups the requested validation groups.
     * @return this call's collector (empty when the caller's own collector received the
     * violations).
     */
    public BeanRuleViolations validate(RuleContext context, Object bean, Class<?>... groups) {
        Assert.notNull(context, "context cannot be null.");
        Assert.notNull(bean, "bean cannot be null.");
        ClassValidationModel model = model(bean.getClass());
        Set<Class<?>> expanded = Groups.expand(groups);
        BeanRuleViolations collector = new BeanRuleViolations();
        TraversalState state = new TraversalState(this::model, expanded, collector);
        state.markValidated(bean);
        state.pushObjectFrame(model.getLeafTable());

        ScopedBindings bindings = context.getBindings();
        boolean userCollector = hasViolationsBinding(bindings);
        NamedScope scope = bindings.addScope();

        try {
            bindRun(bindings, bean, expanded, state);
            if (!userCollector) {
                bindings.bind(Binding.builder().with("ruleViolations").type(RuleViolations.class).value(collector).build());
            }
            model.getFlow().run(context);
        } finally {
            bindings.removeScope(scope);
        }

        return collector;
    }

    /**
     * Retrieves the validation metadata of the given class (reading and caching on
     * first request; fail-fasts propagate).
     *
     * @param beanClass the bean class.
     * @return the class's metadata.
     */
    public BeanMetadata getMetadata(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        return model(beanClass).getMetadata();
    }

    /**
     * Retrieves the per-class validation RuleFlow (the embeddable artifact, R10.4b).
     *
     * @param beanClass the bean class.
     * @return the class's validation flow.
     */
    public RuleFlow<?> getRuleFlow(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        return model(beanClass).getFlow();
    }

    private ClassValidationModel model(Class<?> beanClass) {
        return models.computeIfAbsent(beanClass, type -> materializer.materialize(reader.read(type), this::model));
    }

    private void bindRun(Bindings bindings, Object bean, Set<Class<?>> expanded, TraversalState state) {
        // $parent is deliberately not pre-bound: the flow's param default supplies the
        // root's null, and descent binds the real parent per nesting level.
        bindings.bind(TraversalState.THIS_BINDING, Object.class, bean);
        bindings.bind(TraversalState.ROOT_BINDING, Object.class, bean);
        bindings.bind("$groups", Set.class, expanded);
        bindings.bind("$traversal", TraversalState.class, state);
    }

    private boolean hasViolationsBinding(ScopedBindings bindings) {
        for (Binding<?> binding : bindings) {
            if (binding.getValue() instanceof RuleViolations) return true;
        }
        return false;
    }

    private static MessageResolver buildResolver(ConstraintMapperRegistry mapperRegistry,
                                                 MessageResolver userResolver, List<String> bundleBaseNames) {
        List<MessageResolver> chain = new ArrayList<>();
        if (userResolver != null) chain.add(userResolver);

        List<String> baseNames = new ArrayList<>(bundleBaseNames);
        baseNames.add(VALIDATION_MESSAGES_BUNDLE);
        chain.add(MessageResolver.builder(baseNames.toArray(new String[0])).build());

        MessageResolver composite = chain.size() == 1 ? chain.get(0) : new CompositeMessageResolver(chain);
        return new TranslatingMessageResolver(composite, mapperRegistry.getAttributeNameMappingsByCode());
    }

    @Override
    public String toString() {
        return "BeanValidator{cachedClasses=" + models.size() + '}';
    }
}
