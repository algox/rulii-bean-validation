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
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.rule.Rule;
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link BeanDefinition} into {@link BeanRules}: for every constraint occurrence, a
 * {@link ConstraintTarget} is prepared and the constraint's {@link ConstraintMapper mapper} builds
 * the rules. Policy lives here, in one place:
 * <ul>
 *     <li><b>value</b>: property constraints read the binding of the declaration they were written
 *     on ({@link BindingPlan}); container element constraints read {@code $element}, or {@code $key} /
 *     {@code $value} for map positions; class constraints read {@code $this};</li>
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
 *     rules; a pure composition (no mapper, no validators) contributes no rule of its own; rules
 *     under a {@code @ReportAsSingleViolation} host record the host for the runtime to wrap.</li>
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
     */
    public BeanRules materialize(BeanDefinition bean) {
        Assert.notNull(bean, "bean cannot be null.");

        BindingPlan plan = BindingPlan.of(bean);
        Class<?> beanClass = bean.getBeanClass();
        List<MaterializedRule> propertyRules = new ArrayList<>();
        List<BeanRules.ElementRules> elementRules = new ArrayList<>();
        List<MaterializedRule> classRules = new ArrayList<>();

        for (PropertyDefinition property : bean.getProperties()) {
            String name = property.getName();

            for (ConstraintDefinition constraint : property.getConstraints()) {
                PropertyDeclaration declaration = ((PropertyConstraintSource) constraint.getConstraintSource()).getDeclaration();
                Site site = new Site(beanClass, name, plan.getBindingName(declaration), name, declaration.getType(), name,
                        declaration.describe());
                materialize(constraint, null, site, name + "_" + decapitalize(constraint.getAnnotationType()), propertyRules);
            }

            for (ContainerElementDefinition node : property.getContainerElements()) {
                elements(beanClass, property, node, name, elementRules);
            }
        }

        String className = beanClass.getSimpleName().isEmpty() ? "Bean" : beanClass.getSimpleName();

        for (ConstraintDefinition constraint : bean.getClassConstraints()) {
            Site site = new Site(beanClass, null, BindingPlan.THIS, BindingPlan.THIS, beanClass, null,
                    constraint.getConstraintSource().describe());
            materialize(constraint, null, site, className + "_" + decapitalize(constraint.getAnnotationType()), classRules);
        }

        return new BeanRules(bean, plan, propertyRules, elementRules, classRules);
    }

    /**
     * Where a constraint occurrence sits: what the materializer knows regardless of which mapper runs.
     */
    private record Site(Class<?> beanClass, String propertyName, String bindingName, String valueName, Class<?> type,
                        String leaf, String location) {}

    private void elements(Class<?> beanClass, PropertyDefinition property, ContainerElementDefinition node, String prefix,
                          List<BeanRules.ElementRules> out) {
        String name = prefix + "_" + segment(node);

        if (!node.getConstraints().isEmpty()) {
            List<MaterializedRule> rules = new ArrayList<>();
            Class<?> type = node.getType() != null ? Types.erasure(node.getType()) : Object.class;
            Site site = new Site(beanClass, property.getName(), elementBinding(node), property.getName(), type, null,
                    node.getSourceDeclaration().describe() + " " + node.getName());

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
     * The binding a container position's rules read: {@code $key} / {@code $value} for the two
     * positions of a map, {@code $element} elsewhere.
     */
    static String elementBinding(ContainerElementDefinition node) {
        if (Map.class.isAssignableFrom(node.getContainerType())) {
            return node.getTypeArgumentIndex() == 0 ? BindingPlan.KEY : BindingPlan.VALUE;
        }

        return BindingPlan.ELEMENT;
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

        if (mapper == null) {
            if (!(constraint.isComposed() && type.getAnnotation(Constraint.class).validatedBy().length == 0)) {
                throw new ConstraintMapperException(type, site.location(), "no mapper is registered for the constraint.");
            }
        } else {
            ConstraintTarget target = new ConstraintTarget(site.beanClass(), site.propertyName(), site.bindingName(),
                    site.valueName(), site.type(), ruleName, errorCode(type), constraint.getSeverity(),
                    message(constraint, mapper, site.valueName()), constraint.getSource());
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
                out.add(new MaterializedRule(rule, constraint, host, target, site.leaf()));
            }
        }

        ConstraintDefinition composingHost = constraint.isReportAsSingleViolation() ? constraint : host;

        for (ConstraintDefinition composing : constraint.getComposingConstraints()) {
            materialize(composing, composingHost, site, ruleName + "_" + decapitalize(composing.getAnnotationType()), out);
        }
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

    private static String message(ConstraintDefinition constraint, ConstraintMapper<?> mapper, String valueName) {
        String template = constraint.getMessage();
        if (MessageTemplates.isTemplateKey(template, MessageTemplates.attributeNames(constraint.getAnnotationType()))) return null;
        return MessageTemplates.translate(template, mapper.getParameterNames(), valueName);
    }

    private static String decapitalize(Class<? extends Annotation> annotationType) {
        String name = annotationType.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
