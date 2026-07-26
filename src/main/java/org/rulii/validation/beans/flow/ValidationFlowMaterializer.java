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
package org.rulii.validation.beans.flow;

import org.rulii.bind.Binding;
import org.rulii.bind.Bindings;
import org.rulii.bind.ReservedBindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.apache.commons.logging.Log;
import org.rulii.lib.apache.commons.logging.LogFactory;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.rule.Rule;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleset.RuleSet;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.ConstraintMapper;
import org.rulii.validation.beans.ConstraintMapperRegistry;
import org.rulii.validation.beans.ConstraintTarget;
import org.rulii.validation.beans.message.JakartaMessageTranslator;
import org.rulii.validation.beans.metadata.BeanMetadata;
import org.rulii.validation.beans.metadata.ConstraintMetadata;
import org.rulii.validation.beans.metadata.ConstraintSource;
import org.rulii.validation.beans.metadata.ContainerElement;
import org.rulii.validation.beans.metadata.ContainerElementRole;
import org.rulii.validation.beans.metadata.ContainerMetadata;
import org.rulii.validation.beans.metadata.PropertyMetadata;
import org.rulii.validation.beans.traversal.Groups;
import org.rulii.validation.beans.traversal.TraversalState;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.rulii.model.action.Actions.action;
import static org.rulii.model.function.Functions.function;

/**
 * Materializes a {@link BeanMetadata} into its {@link ClassValidationModel}: the shared
 * constraint rules (composition-aware), the leaf table, and the per-class validation
 * RuleFlow whose steps are BIND (lazy memoized property bindings), OWN RULES (the
 * group-filtered RuleSet), &#64;VALIDATE (registry dispatch), and DESCENT (container
 * walks and cascades via the {@code $traversal} state) — the S3 anatomy.
 *
 * <p>Reserved and navigation binding names shadow to a {@code $}-suffixed name (warn
 * logged, never a fail-fast); dual-source and shadowed superclass fields bind their
 * secondary sources under self-describing shadow names ({@code name$field},
 * {@code name$Person}).</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ValidationFlowMaterializer {

    private static final Log logger = LogFactory.getLog(ValidationFlowMaterializer.class);

    private static final Set<String> NAVIGATION_NAMES = Set.of(TraversalState.THIS_BINDING,
            TraversalState.ROOT_BINDING, TraversalState.PARENT_BINDING, TraversalState.ELEMENT_BINDING,
            TraversalState.KEY_BINDING, TraversalState.VALUE_BINDING);

    private final ConstraintMapperRegistry mapperRegistry;

    public ValidationFlowMaterializer(ConstraintMapperRegistry mapperRegistry) {
        super();
        Assert.notNull(mapperRegistry, "mapperRegistry cannot be null.");
        this.mapperRegistry = mapperRegistry;
    }

    /**
     * Materializes the model (rules, leaf table, flow) for the given bean metadata.
     *
     * @param metadata the bean's metadata.
     * @param modelLookup runtime-class model lookup handed to descent and embedded runs.
     * @return the materialized per-class model.
     */
    public ClassValidationModel materialize(BeanMetadata metadata, Function<Class<?>, ClassValidationModel> modelLookup) {
        Assert.notNull(metadata, "metadata cannot be null.");
        Assert.notNull(modelLookup, "modelLookup cannot be null.");

        Class<?> beanClass = metadata.getBeanClass();
        Build build = new Build(beanClass);

        for (PropertyMetadata property : metadata.getProperties()) {
            build.property(property);
        }
        build.classLevel(metadata);

        ClassValidationModel model = new ClassValidationModel(beanClass, metadata,
                decapitalize(beanClass.getSimpleName()) + "ValidationRules", build.ownRules, build.leafTable);
        model.setFlow(buildFlow(model, build, modelLookup));

        if (logger.isDebugEnabled()) logger.debug("Materialized [" + beanClass.getName() + "] rules ["
                + build.ownRules.size() + "].");

        return model;
    }

    // ------------------------------------------------------------------------
    // Per-class build
    // ------------------------------------------------------------------------

    private final class Build {

        private final Class<?> beanClass;
        private final List<PropertyPlan> plans = new ArrayList<>();
        private final List<MaterializedRule> ownRules = new ArrayList<>();
        private final Map<String, String> leafTable = new HashMap<>();
        private final Set<String> usedBindingNames = new HashSet<>();
        private final Set<String> usedRuleNames = new HashSet<>();
        private final List<String> classValidateNames = new ArrayList<>();

        private Build(Class<?> beanClass) {
            super();
            this.beanClass = beanClass;
        }

        private void property(PropertyMetadata property) {
            PropertyPlan plan = new PropertyPlan();
            plan.propertyName = property.getName();
            plan.primaryBindingName = allocateBindingName(property.getName());
            plan.cascaded = property.isCascaded();
            plan.validateNames = property.getValidateRuleNames();
            plan.bindings.add(new BindingPlan(plan.primaryBindingName, property.getDeclaredType(), property::getValue));

            for (ConstraintSource source : property.getSources()) {
                String bindingName = sourceBindingName(property, source, plan);
                TargetSpec spec = new TargetSpec(bindingName, property.getName(), property.getName(),
                        property.getDeclaredType(), property.getName());

                for (ConstraintMetadata constraint : source.getConstraints()) {
                    materializeConstraint(constraint, spec, ownRules, true);
                }
            }

            if (property.getContainer() != null) {
                plan.container = materializeContainer(property.getContainer(), property.getName(), property.getName());
            }

            plans.add(plan);
        }

        private void classLevel(BeanMetadata metadata) {
            String simpleName = beanClass.getSimpleName();
            TargetSpec spec = new TargetSpec(TraversalState.THIS_BINDING, null, simpleName, beanClass, simpleName);

            for (ConstraintMetadata constraint : metadata.getClassConstraints()) {
                materializeConstraint(constraint, spec, ownRules, false);
            }

            classValidateNames.addAll(metadata.getClassValidateRuleNames());
        }

        // --------------------------------------------------------------------
        // Constraint materialization (composition-aware, S10)
        // --------------------------------------------------------------------

        private void materializeConstraint(ConstraintMetadata constraint, TargetSpec spec,
                                           List<MaterializedRule> out, boolean leaf) {
            if (constraint.isReportAsSingleViolation()) {
                Rule wrapper = materializeReportAsSingle(constraint, spec);
                out.add(new MaterializedRule(wrapper, constraint.getGroups()));
                if (leaf) leafTable.put(wrapper.getName(), spec.leafProperty);
                return;
            }

            Class<? extends java.lang.annotation.Annotation> annotationType = constraint.getAnnotationType();

            if (mapperRegistry.isRegistered(annotationType)) {
                ConstraintMapper mapper = mapperRegistry.getMapper(annotationType);
                ConstraintTarget target = target(constraint, spec, mapper);

                for (Rule rule : mapper.map(constraint, target)) {
                    out.add(new MaterializedRule(rule, constraint.getGroups()));
                    if (leaf) leafTable.put(rule.getName(), spec.leafProperty);
                }
            }

            // Composing constraints flatten: each reports individually (spec behavior),
            // named <prefix>_<host>_<composing> via the inherited prefix chain.
            for (ConstraintMetadata composing : constraint.getComposingConstraints()) {
                materializeConstraint(composing, spec.compose(annotationType.getSimpleName()), out, leaf);
            }
        }

        private Rule materializeReportAsSingle(ConstraintMetadata constraint, TargetSpec spec) {
            String ruleName = allocateRuleName(spec.prefix + "_" + constraint.getAnnotationType().getSimpleName());
            List<Rule> composing = new ArrayList<>();
            TargetSpec inner = spec.compose(constraint.getAnnotationType().getSimpleName());

            // The host's own mapped rules run inside the suppression frame too: any
            // failure of the composition reports as the single host violation.
            if (mapperRegistry.isRegistered(constraint.getAnnotationType())) {
                ConstraintMapper mapper = mapperRegistry.getMapper(constraint.getAnnotationType());
                composing.addAll(mapper.map(constraint, target(constraint, spec.rename(ruleName + "_self"), mapper)));
            }

            List<MaterializedRule> composed = new ArrayList<>();
            for (ConstraintMetadata child : constraint.getComposingConstraints()) {
                materializeConstraint(child, inner, composed, false);
            }
            for (MaterializedRule rule : composed) {
                composing.add(rule.getRule());
            }

            String errorCode = errorCodeFor(constraint);
            String message = customMessage(constraint, mapperFor(constraint));

            ReportAsSingleViolationRule target = new ReportAsSingleViolationRule(errorCode,
                    constraint.getSeverity(), message, null, composing, spec.bindingName, spec.valueName);
            return Rule.builder().with(target).name(ruleName).build();
        }

        private MaterializedContainer materializeContainer(ContainerMetadata container, String propertyName, String prefix) {
            List<MaterializedElement> elements = new ArrayList<>(container.getElements().size());

            for (ContainerElement element : container.getElements()) {
                String positionPrefix = prefix + "_" + roleSuffix(element.getRole());
                String bindingName = roleBinding(element.getRole());
                TargetSpec spec = new TargetSpec(bindingName, propertyName, propertyName,
                        element.getElementType(), positionPrefix);

                List<MaterializedRule> rules = new ArrayList<>();
                for (ConstraintMetadata constraint : element.getConstraints()) {
                    // No leaf entries: the walk pushes the full element path node itself.
                    materializeConstraint(constraint, spec, rules, false);
                }

                MaterializedContainer nested = element.getContainer() != null
                        ? materializeContainer(element.getContainer(), propertyName, positionPrefix)
                        : null;
                elements.add(new MaterializedElement(element.getRole(), rules, element.isCascaded(), nested));
            }

            return new MaterializedContainer(container.getKind(), elements);
        }

        // --------------------------------------------------------------------
        // Target & naming helpers
        // --------------------------------------------------------------------

        private ConstraintTarget target(ConstraintMetadata constraint, TargetSpec spec, ConstraintMapper mapper) {
            String ruleName = spec.fixedRuleName != null ? spec.fixedRuleName
                    : allocateRuleName(spec.prefix + "_" + constraint.getAnnotationType().getSimpleName());
            return new ConstraintTarget(beanClass, spec.propertyName, ruleName, spec.bindingName, spec.valueName,
                    spec.declaredType, errorCodeFor(constraint), constraint.getSeverity(),
                    customMessage(constraint, mapper));
        }

        private ConstraintMapper mapperFor(ConstraintMetadata constraint) {
            return mapperRegistry.getMapper(constraint.getAnnotationType());
        }

        /**
         * The violation error code: the constraint's own declared template key when the
         * default message is a single braced key (custom constraints keep their authors'
         * bundle keys; standard jakarta templates resolve to the same
         * {@code <fqcn>.message} either way), the class-derived key otherwise.
         */
        private String errorCodeFor(ConstraintMetadata constraint) {
            String key = templateKey(constraint.getMessageTemplate());
            return key != null ? key : ConstraintMapperRegistry.errorCode(constraint.getAnnotationType());
        }

        private String customMessage(ConstraintMetadata constraint, ConstraintMapper mapper) {
            String template = constraint.getMessageTemplate();
            if (template == null || templateKey(template) != null) return null;

            Map<String, String> mapping = mapper != null ? mapper.getAttributeNameMapping() : Map.of();
            return JakartaMessageTranslator.translate(template, mapping);
        }

        private String templateKey(String template) {
            if (template == null || template.length() < 3) return null;
            if (!template.startsWith("{") || !template.endsWith("}")) return null;
            String content = template.substring(1, template.length() - 1);
            if (content.indexOf('{') >= 0 || content.indexOf('}') >= 0) return null;
            return content;
        }

        private String sourceBindingName(PropertyMetadata property, ConstraintSource source, PropertyPlan plan) {
            java.lang.reflect.Member member = source.getMember();
            boolean isPrimarySource = property.getGetter() != null
                    ? member.equals(property.getGetter())
                    : member.equals(property.getField());
            if (isPrimarySource) return plan.primaryBindingName;

            String shadowName = member.equals(property.getField())
                    ? plan.primaryBindingName + "$field"
                    : plan.primaryBindingName + "$" + member.getDeclaringClass().getSimpleName();
            while (usedBindingNames.contains(shadowName)) {
                shadowName = shadowName + "$";
            }
            usedBindingNames.add(shadowName);

            Field field = (Field) member;
            plan.bindings.add(new BindingPlan(shadowName, field.getGenericType(), source::getValue));
            return shadowName;
        }

        private String allocateBindingName(String propertyName) {
            String name = propertyName;
            while (ReservedBindings.isReserved(name) || NAVIGATION_NAMES.contains(name)
                    || usedBindingNames.contains(name)) {
                name = name + "$";
            }
            if (!name.equals(propertyName)) {
                logger.warn("Property [" + beanClass.getName() + "." + propertyName + "] collides with a reserved "
                        + "binding name. It is bound as [" + name + "]; @Validate rules must use that name.");
            }
            usedBindingNames.add(name);
            return name;
        }

        private String allocateRuleName(String base) {
            String name = base;
            int counter = 2;
            while (!usedRuleNames.add(name)) {
                name = base + "_" + counter++;
            }
            return name;
        }
    }

    private static String roleSuffix(ContainerElementRole role) {
        return switch (role) {
            case ELEMENT -> "element";
            case KEY -> "key";
            case VALUE -> "value";
        };
    }

    private static String roleBinding(ContainerElementRole role) {
        return switch (role) {
            case ELEMENT -> TraversalState.ELEMENT_BINDING;
            case KEY -> TraversalState.KEY_BINDING;
            case VALUE -> TraversalState.VALUE_BINDING;
        };
    }

    // ------------------------------------------------------------------------
    // Flow assembly (S3 anatomy)
    // ------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RuleFlow<?> buildFlow(ClassValidationModel model, Build build,
                                  Function<Class<?>, ClassValidationModel> modelLookup) {
        Class<?> beanClass = model.getBeanClass();
        List<PropertyPlan> plans = build.plans;
        List<String> classValidateNames = build.classValidateNames;

        return RuleFlow.builder()
                .name(decapitalize(beanClass.getSimpleName()) + "ValidationFlow")
                .param(TraversalState.THIS_BINDING, Object.class, true)
                .param("ruleViolations", RuleViolations.class, function(() -> new BeanRuleViolations()))
                .param("$groups", (Class) Set.class, function(() -> Groups.DEFAULT_GROUPS))
                .param(TraversalState.ROOT_BINDING, Object.class,
                        function((RuleContext ruleContext) -> ruleContext.getBindings().getValue(TraversalState.THIS_BINDING)))
                .param(TraversalState.PARENT_BINDING, Object.class, function(() -> (Object) null))
                .param("$traversal", TraversalState.class,
                        function((RuleContext ruleContext) -> freshState(ruleContext, model, modelLookup)))
                // Anonymous scope: named scopes are unique per bindings, and a class
                // cascading into its own type nests two object scopes of the same class.
                .scope(f -> f
                        .execute(action((RuleContext ruleContext) -> bindProperties(ruleContext, plans)))
                        .execute(action((RuleContext ruleContext) -> runOwnRules(ruleContext, model)))
                        .execute(action((RuleContext ruleContext) -> runValidates(ruleContext, beanClass, plans, classValidateNames)))
                        .execute(action((RuleContext ruleContext) -> runDescent(ruleContext, plans))))
                .build();
    }

    private TraversalState freshState(RuleContext ruleContext, ClassValidationModel model,
                                      Function<Class<?>, ClassValidationModel> modelLookup) {
        Bindings bindings = ruleContext.getBindings();
        Object groups = bindings.contains("$groups") ? bindings.getValue("$groups") : null;
        Set<Class<?>> expanded = groups instanceof Collection<?> requested
                ? Groups.expand((Collection<Class<?>>) castGroups(requested))
                : Groups.expand();

        Object violations = bindings.contains("ruleViolations") ? bindings.getValue("ruleViolations") : null;
        BeanRuleViolations collector = violations instanceof BeanRuleViolations beanViolations ? beanViolations : null;

        TraversalState state = new TraversalState(modelLookup, expanded, collector);
        Object self = bindings.contains(TraversalState.THIS_BINDING)
                ? bindings.getValue(TraversalState.THIS_BINDING) : null;
        if (self != null) state.markValidated(self);
        state.pushObjectFrame(model.getLeafTable());
        return state;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Class<?>> castGroups(Collection<?> requested) {
        for (Object group : requested) {
            if (!(group instanceof Class)) {
                throw new UnrulyException("$groups must contain validation group interfaces; found [" + group + "].");
            }
        }
        return (Collection<Class<?>>) requested;
    }

    // ------------------------------------------------------------------------
    // Flow steps
    // ------------------------------------------------------------------------

    private void bindProperties(RuleContext ruleContext, List<PropertyPlan> plans) {
        Bindings bindings = ruleContext.getBindings();
        Object bean = bindings.getValue(TraversalState.THIS_BINDING);

        for (PropertyPlan plan : plans) {
            for (BindingPlan binding : plan.bindings) {
                bindings.bind(Binding.builder().with(binding.name)
                        .type(binding.type)
                        .delegate(memoize(() -> binding.reader.apply(bean)), null)
                        .build());
            }
        }
    }

    private void runOwnRules(RuleContext ruleContext, ClassValidationModel model) {
        TraversalState state = (TraversalState) ruleContext.getBindings().getValue("$traversal");
        RuleSet<?> ruleSet = model.selectRuleSet(state.getExpandedGroups());
        if (ruleSet != null) ruleSet.run(ruleContext);
    }

    private void runValidates(RuleContext ruleContext, Class<?> beanClass, List<PropertyPlan> plans,
                              List<String> classValidateNames) {
        TraversalState state = (TraversalState) ruleContext.getBindings().getValue("$traversal");

        for (PropertyPlan plan : plans) {
            if (!plan.validateNames.isEmpty()) {
                state.runRegistryRules(ruleContext, beanClass, plan.propertyName, plan.primaryBindingName,
                        plan.validateNames);
            }
        }

        if (!classValidateNames.isEmpty()) {
            state.runRegistryRules(ruleContext, beanClass, null, null, classValidateNames);
        }
    }

    private void runDescent(RuleContext ruleContext, List<PropertyPlan> plans) {
        TraversalState state = (TraversalState) ruleContext.getBindings().getValue("$traversal");
        Bindings bindings = ruleContext.getBindings();

        for (PropertyPlan plan : plans) {
            if (plan.container == null && !plan.cascaded) continue;

            Object value = bindings.getValue(plan.primaryBindingName);
            if (plan.container != null) state.walkContainer(ruleContext, plan.propertyName, value, plan.container);
            if (plan.cascaded) state.descend(ruleContext, plan.propertyName, value);
        }
    }

    private static Supplier<Object> memoize(Supplier<Object> supplier) {
        // Per-run, single-threaded by construction (one object scope, one call).
        return new Supplier<>() {
            private boolean read;
            private Object value;

            @Override
            public Object get() {
                if (!read) {
                    value = supplier.get();
                    read = true;
                }
                return value;
            }
        };
    }

    private static String decapitalize(String name) {
        if (name.isEmpty() || (name.length() > 1 && Character.isUpperCase(name.charAt(1)))) return name;
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    // ------------------------------------------------------------------------
    // Internal plans
    // ------------------------------------------------------------------------

    private static final class PropertyPlan {

        private String propertyName;
        private String primaryBindingName;
        private final List<BindingPlan> bindings = new ArrayList<>();
        private boolean cascaded;
        private MaterializedContainer container;
        private List<String> validateNames = List.of();

        private PropertyPlan() {
            super();
        }
    }

    private static final class BindingPlan {

        private final String name;
        private final Type type;
        private final Function<Object, Object> reader;

        private BindingPlan(String name, Type type, Function<Object, Object> reader) {
            super();
            this.name = name;
            this.type = type;
            this.reader = reader;
        }
    }

    private static final class TargetSpec {

        private final String bindingName;
        private final String propertyName;
        private final String valueName;
        private final Type declaredType;
        private final String prefix;
        private final String leafProperty;
        private final String fixedRuleName;

        private TargetSpec(String bindingName, String propertyName, String valueName, Type declaredType, String prefix) {
            this(bindingName, propertyName, valueName, declaredType, prefix, propertyName, null);
        }

        private TargetSpec(String bindingName, String propertyName, String valueName, Type declaredType,
                           String prefix, String leafProperty, String fixedRuleName) {
            super();
            this.bindingName = bindingName;
            this.propertyName = propertyName;
            this.valueName = valueName;
            this.declaredType = declaredType;
            this.prefix = prefix;
            this.leafProperty = leafProperty;
            this.fixedRuleName = fixedRuleName;
        }

        private TargetSpec compose(String hostSimpleName) {
            return new TargetSpec(bindingName, propertyName, valueName, declaredType,
                    prefix + "_" + hostSimpleName, leafProperty, null);
        }

        private TargetSpec rename(String ruleName) {
            return new TargetSpec(bindingName, propertyName, valueName, declaredType, prefix, leafProperty, ruleName);
        }
    }
}
