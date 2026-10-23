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

import jakarta.validation.Constraint;
import jakarta.validation.ValidationException;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.model.function.Function;
import org.rulii.model.function.Functions;
import org.rulii.rule.Rule;
import org.rulii.validation.BindingFunction;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperException;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.ConstraintTarget;
import org.rulii.validation.beans.message.MessageTemplates;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.PropertyConstraintSource;
import org.rulii.validation.beans.model.PropertyDeclaration;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.util.reflect.Types;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a {@link BeanDefinition} into {@link BeanRules}: for every constraint occurrence, a
 * {@link ConstraintTarget} is prepared and the constraint's {@link ConstraintMapper mapper} builds
 * the rules. Policy lives here, in one place:
 * <ul>
 *     <li><b>value</b>: a property constraint reads the property binding when it is written on the
 *     primary declaration, and reads its own declaration off {@code $this} otherwise, so a field
 *     constraint validates the field and a getter constraint the getter ({@link BindingPlan});
 *     container element constraints read {@code $element}, or {@code $key} / {@code $value} for map
 *     positions; class constraints read {@code $this};</li>
 *     <li><b>names</b>: {@code <property>_<constraint>} for property rules,
 *     {@code <property>_<Container><index>_<constraint>} for element rules,
 *     {@code <ClassName>_<constraint>} for class rules, composing constraints appending
 *     {@code _<composing>}; a mapper producing several rules gets {@code _2}, {@code _3};</li>
 *     <li><b>error code</b>: the constraint's default message key, {@code <annotation class>.message},
 *     for standard and custom constraints alike;</li>
 *     <li><b>severity</b>: from the payload markers ({@link ConstraintDefinition#getSeverity()});</li>
 *     <li><b>message</b>: a literal message is translated into a rulii template once; a message that
 *     is a bundle key is left to the error code, resolved at render time;</li>
 *     <li><b>composition</b>: composing constraints are materialized recursively, each as its own
 *     rules; a pure composition (no mapper, no validators) contributes no rule of its own. A
 *     {@code @ReportAsSingleViolation} composition becomes one {@link ReportAsSingleViolationRule}
 *     hosting the rules of its composing constraints (and its own, if it has a mapper), reporting
 *     once with the host's code and message.</li>
 * </ul>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuleMaterializer {

    private static final String MESSAGE_KEY_SUFFIX = ".message";

    private final ConstraintMapperRegistry mappers;

    /**
     * Creates the materializer.
     *
     * @param mappers the constraint mappers.
     */
    public RuleMaterializer(ConstraintMapperRegistry mappers) {
        super();
        Assert.notNull(mappers, "mappers cannot be null.");
        this.mappers = mappers;
    }

    public ConstraintMapperRegistry getConstraintMapperRegistry() {
        return mappers;
    }

    /**
     * Materializes the rules of the given bean definition.
     *
     * @param bean the bean definition.
     * @return the rules.
     * @throws ConstraintMapperException if a mapper is missing, misbehaves or produces a rule name
     *         already in use.
     * @throws InaccessiblePropertyException if a declaration validation reads cannot be made accessible.
     */
    public BeanRules materialize(BeanDefinition bean) {
        Assert.notNull(bean, "bean cannot be null.");

        BindingPlan plan = BindingPlan.of(bean);
        Class<?> beanClass = bean.getBeanClass();
        List<MaterializedRule> propertyRules = new ArrayList<>();
        List<BeanRules.ElementRules> elementRules = new ArrayList<>();
        List<MaterializedRule> classRules = new ArrayList<>();
        Set<String> names = new HashSet<>();

        for (PropertyDefinition property : bean.getProperties()) {
            property(plan, property, false, names, propertyRules, elementRules);
        }

        String className = beanClass.getSimpleName().isEmpty() ? "Bean" : beanClass.getSimpleName();

        for (ConstraintDefinition constraint : bean.getClassConstraints()) {
            Site site = new Site(beanClass, null, BindingPlan.THIS, Validators.binding(BindingPlan.THIS), BindingPlan.THIS,
                    beanClass, null, constraint.getConstraintSource().describe());
            materialize(constraint, null, site, unique(className + "_" + decapitalize(constraint.getAnnotationType()), names), classRules);
        }

        return new BeanRules(bean, plan, propertyRules, elementRules, classRules);
    }

    /**
     * Materializes the rules that validate a value offered for one property, without an object: every
     * constraint declared on the property, whichever declaration carries it, reads the property binding,
     * and the property's container element positions are included. Class-level constraints are not.
     *
     * @param rules the rules of the bean, whose binding plan is reused.
     * @param property the property.
     * @return the value rules, as a {@link BeanRules} holding that property alone.
     */
    public BeanRules materializeValue(BeanRules rules, PropertyDefinition property) {
        Assert.notNull(rules, "rules cannot be null.");
        Assert.notNull(property, "property cannot be null.");

        List<MaterializedRule> propertyRules = new ArrayList<>();
        List<BeanRules.ElementRules> elementRules = new ArrayList<>();

        property(rules.getBindingPlan(), property, true, new HashSet<>(), propertyRules, elementRules);

        return new BeanRules(rules.getDefinition(), rules.getBindingPlan(), propertyRules, elementRules, List.of());
    }

    private void property(BindingPlan plan, PropertyDefinition property, boolean bindingOnly, Set<String> names,
                          List<MaterializedRule> propertyRules, List<BeanRules.ElementRules> elementRules) {
        Class<?> beanClass = plan.getBean().getBeanClass();
        String name = property.getName();
        String binding = plan.getBindingName(property);

        for (ConstraintDefinition constraint : property.getConstraints()) {
            PropertyDeclaration declaration = ((PropertyConstraintSource) constraint.getConstraintSource()).getDeclaration();
            Function<?> value = bindingOnly || plan.isPrimary(declaration)
                    ? Validators.binding(binding)
                    : reading(binding, plan.getAccessor(declaration));
            Site site = new Site(beanClass, name, binding, value, name, declaration.getType(), name, declaration.describe());
            materialize(constraint, null, site, unique(name + "_" + decapitalize(constraint.getAnnotationType()), names), propertyRules);
        }

        for (ContainerElementDefinition node : property.getContainerElements()) {
            elements(beanClass, property, node, name, elementRules);
        }
    }

    /**
     * A rule name not yet used in the bean: the same constraint on a field and its getter gets
     * {@code _2} on the second.
     */
    private static String unique(String candidate, Set<String> names) {
        if (names.add(candidate)) return candidate;
        int counter = 2;
        while (!names.add(candidate + "_" + counter)) counter++;
        return candidate + "_" + counter;
    }

    /**
     * A value function reading a declaration off the object in scope, named after the property
     * binding so the rules report the value under the property name.
     */
    private static Function<?> reading(String bindingName, PropertyAccessor accessor) {
        return new BindingFunction<>(bindingName, Functions.function((RuleContext ruleContext) ->
                accessor.read(ruleContext.getBindings().getValue(BindingPlan.THIS))));
    }

    /**
     * Where a constraint occurrence sits: what the materializer knows regardless of which mapper runs.
     */
    private record Site(Class<?> beanClass, String propertyName, String bindingName, Function<?> value, String valueName,
                        Class<?> type, String leaf, String location) {}

    private void elements(Class<?> beanClass, PropertyDefinition property, ContainerElementDefinition node, String prefix,
                          List<BeanRules.ElementRules> out) {
        String name = prefix + "_" + segment(node);

        if (!node.getConstraints().isEmpty()) {
            List<MaterializedRule> rules = new ArrayList<>();
            Class<?> type = node.getType() != null ? Types.erasure(node.getType()) : Object.class;
            String binding = BindingPlan.elementBinding(node);
            Site site = new Site(beanClass, property.getName(), binding, Validators.binding(binding), property.getName(), type,
                    null, node.getSourceDeclaration().describe() + " " + node.getName());

            for (ConstraintDefinition constraint : node.getConstraints()) {
                materialize(constraint, null, site, name + "_" + decapitalize(constraint.getAnnotationType()), rules);
            }

            if (!rules.isEmpty()) out.add(new BeanRules.ElementRules(property, node, rules));
        }

        for (ContainerElementDefinition child : node.getChildren()) {
            elements(beanClass, property, child, name, out);
        }
    }

    /**
     * Rule-name segment of a container position: {@code <ContainerSimpleName><index>}, the index
     * omitted for the container value itself.
     */
    static String segment(ContainerElementDefinition node) {
        String container = node.getContainerType().getSimpleName().replaceAll("[^A-Za-z0-9_$]", "");
        return node.getTypeArgumentIndex() == ValueExtractorDescriptor.CONTAINER ? container : container + node.getTypeArgumentIndex();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void materialize(ConstraintDefinition constraint, ConstraintDefinition host, Site site, String ruleName,
                             List<MaterializedRule> out) {
        Class<? extends Annotation> type = constraint.getAnnotationType();
        ConstraintMapper mapper = mappers.find(type);
        boolean pureComposition = constraint.isComposed() && type.getAnnotation(Constraint.class).validatedBy().length == 0;

        if (mapper == null && !pureComposition) {
            throw new ConstraintMapperException(type, site.location(), "no mapper is registered for the constraint.");
        }

        Map<String, String> parameterNames = mapper != null ? mapper.getParameterNames() : Map.of();
        ConstraintTarget target = new ConstraintTarget(site.beanClass(), site.propertyName(), site.bindingName(),
                site.valueName(), site.type(), ruleName, errorCode(type), constraint.getSeverity(),
                message(constraint, parameterNames, site.valueName()), constraint.getSource(), site.value());
        // Inside a @ReportAsSingleViolation constraint, every rule (its own and its composing ones) is hosted by it.
        ConstraintDefinition ownHost = constraint.isReportAsSingleViolation() ? constraint : host;
        List<MaterializedRule> own = new ArrayList<>();

        if (mapper != null) {
            List<Rule> rules;

            try {
                rules = mapper.map(constraint.getAnnotation(), target);
            } catch (ValidationException | UnrulyException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ConstraintMapperException(type, site.location(), "failed to materialize: " + e.getMessage(), e);
            }

            if (rules == null || rules.isEmpty()) {
                throw new ConstraintMapperException(type, site.location(), "returned no rules.");
            }

            for (Rule rule : rules) {
                if (rule == null) throw new ConstraintMapperException(type, site.location(), "returned a null rule.");
                own.add(new MaterializedRule(rule, constraint, ownHost, target, site.leaf()));
            }
        }

        for (ConstraintDefinition composing : constraint.getComposingConstraints()) {
            materialize(composing, ownHost, site, ruleName + "_" + decapitalize(composing.getAnnotationType()), own);
        }

        if (!constraint.isReportAsSingleViolation() || own.isEmpty()) {
            out.addAll(own);
            return;
        }

        List<Rule> hosted = new ArrayList<>(own.size());

        for (MaterializedRule rule : own) {
            hosted.add(rule.getRule());
        }

        Rule wrapper = target.configure(ReportAsSingleViolationRule.builder(target.value(), hosted), true).build();
        out.add(new MaterializedRule(wrapper, constraint, host, target, site.leaf(), own));
    }

    /**
     * The error code of a constraint's violations: its default message key.
     *
     * @param annotationType the constraint annotation type.
     * @return {@code <annotation class name>.message}.
     */
    public static String errorCode(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        return annotationType.getName() + MESSAGE_KEY_SUFFIX;
    }

    private static String message(ConstraintDefinition constraint, Map<String, String> parameterNames, String valueName) {
        String template = constraint.getMessage();
        if (MessageTemplates.isTemplateKey(template, MessageTemplates.attributeNames(constraint.getAnnotationType()))) return null;
        return MessageTemplates.translate(template, parameterNames, valueName);
    }

    private static String decapitalize(Class<? extends Annotation> annotationType) {
        String name = annotationType.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
