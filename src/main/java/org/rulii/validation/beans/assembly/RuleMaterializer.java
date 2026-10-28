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

import jakarta.validation.ValidationException;
import jakarta.validation.constraintvalidation.ValidationTarget;
import jakarta.validation.groups.Default;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.model.function.Function;
import org.rulii.model.function.Functions;
import org.rulii.rule.Rule;
import org.rulii.script.Script;
import org.rulii.script.ScriptProcessorManager;
import org.rulii.validation.BindingFunction;
import org.rulii.validation.beans.descriptor.ConstraintDefinitionDescriptor;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperException;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;
import org.rulii.validation.beans.mapper.ConstraintTarget;
import org.rulii.validation.beans.message.MessageTemplates;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.DeclarationConstraintSource;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ExprDeclarationException;
import org.rulii.validation.beans.model.ExprDefinition;
import org.rulii.validation.beans.model.ParameterDefinition;
import org.rulii.validation.beans.model.ValueDeclaration;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.ReturnValueDefinition;
import org.rulii.validation.beans.util.reflect.Types;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 *     <li><b>{@code @Expr}</b>: each occurrence becomes one {@link ExprValidationRule} named
 *     {@code <property>_expr}, {@code <ClassName>_expr}, {@code <method>_expr} or
 *     {@code returnValue_expr} ({@code _2}, {@code _3} for the next on the same element), reading the
 *     value its site reads, with the declaration's error code, severity and message. The script is
 *     compiled here, in the language the declaration names, else the
 *     {@link ScriptProcessorManager#getDefaultLanguage() default language}; no language, an unknown
 *     one or a script that does not compile fails with an {@link ExprDeclarationException}. A null
 *     property value skips the rule unless {@code skipNull = false}; a class, parameters or
 *     return-value rule always runs.</li>
 *     <li><b>description, category, tags</b>: every rule describes its declaration and where it is
 *     ({@code @Size(max=5, min=2) on Order.code}, {@code @Size(min=3) composing @ValidCode on ...},
 *     {@code @Expr(jakarta-el: ${...}) on ...}), is categorised under the bean class's name (an
 *     executable's rules under its owner), and is tagged with its kind ({@link #CONSTRAINT_TAG} or
 *     {@link #EXPR_TAG}), the annotation's simple name, the groups' simple names ({@code Default}
 *     when none) and {@link #HOSTED_TAG} inside a {@code @ReportAsSingleViolation} composition. Tools
 *     list and filter by these; rulii never reads them when it runs.</li>
 * </ul>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuleMaterializer {

    /**
     * The name return-value rules report the value under, and their rule-name prefix.
     */
    public static final String RETURN_VALUE_NAME = "returnValue";

    /**
     * The name cross-parameter rules report the arguments under.
     */
    public static final String PARAMETERS_VALUE = "parameters";

    /**
     * The tag of every rule materialized from a constraint annotation.
     */
    public static final String CONSTRAINT_TAG = "constraint";

    /**
     * The tag of every rule materialized from an {@code @Expr}.
     */
    public static final String EXPR_TAG = "expr";

    /**
     * The tag of a rule reported for by an enclosing {@code @ReportAsSingleViolation} composition.
     */
    public static final String HOSTED_TAG = "hosted";

    private static final String MESSAGE_KEY_SUFFIX = ".message";
    private static final String EXPR_SUFFIX = "_expr";
    private static final String EXPR_VALUE = "value";

    private final ConstraintMapperRegistry mappers;
    private final boolean failFast;

    /**
     * Creates the materializer.
     *
     * @param mappers the constraint mappers.
     */
    public RuleMaterializer(ConstraintMapperRegistry mappers) {
        this(mappers, false);
    }

    /**
     * Creates the materializer, fail-fast or not.
     *
     * @param mappers the constraint mappers.
     * @param failFast whether the rule sets it builds stop at their first failing rule.
     */
    public RuleMaterializer(ConstraintMapperRegistry mappers, boolean failFast) {
        super();
        Assert.notNull(mappers, "mappers cannot be null.");
        this.mappers = mappers;
        this.failFast = failFast;
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
                    beanClass, RulePlacement.bean(), constraint.getConstraintSource().describe(), ValidationTarget.ANNOTATED_ELEMENT);
            materialize(constraint, null, site, unique(className + "_" + decapitalize(constraint.getAnnotationType()), names), classRules);
        }

        for (ExprDefinition expr : bean.getExprRules()) {
            Site site = new Site(beanClass, null, BindingPlan.THIS, Validators.binding(BindingPlan.THIS), BindingPlan.THIS,
                    beanClass, RulePlacement.bean(), expr.getConstraintSource().describe(), ValidationTarget.ANNOTATED_ELEMENT);
            expr(expr, site, unique(className + EXPR_SUFFIX, names), true, classRules);
        }

        return new BeanRules(bean, plan, propertyRules, elementRules, classRules, failFast);
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

        return new BeanRules(rules.getDefinition(), rules.getBindingPlan(), propertyRules, elementRules, List.of(), failFast);
    }

    private void property(BindingPlan plan, PropertyDefinition property, boolean bindingOnly, Set<String> names,
                          List<MaterializedRule> propertyRules, List<BeanRules.ElementRules> elementRules) {
        Class<?> beanClass = plan.getBean().getBeanClass();
        String name = property.getName();
        String binding = plan.getBindingName(property);

        for (ConstraintDefinition constraint : property.getConstraints()) {
            ValueDeclaration declaration = ((DeclarationConstraintSource) constraint.getConstraintSource()).getDeclaration();
            Function<?> value = bindingOnly || plan.readsBinding(declaration)
                    ? Validators.binding(binding)
                    : reading(binding, plan.getAccessor(declaration));
            Site site = new Site(beanClass, name, binding, value, name, declaration.getType(), RulePlacement.property(name),
                    declaration.describe(), ValidationTarget.ANNOTATED_ELEMENT);
            materialize(constraint, null, site, unique(name + "_" + decapitalize(constraint.getAnnotationType()), names), propertyRules);
        }

        for (ExprDefinition expr : property.getExprRules()) {
            ValueDeclaration declaration = ((DeclarationConstraintSource) expr.getConstraintSource()).getDeclaration();
            Function<?> value = bindingOnly || plan.readsBinding(declaration)
                    ? Validators.binding(binding)
                    : reading(binding, plan.getAccessor(declaration));
            Site site = new Site(beanClass, name, binding, value, name, declaration.getType(), RulePlacement.property(name),
                    declaration.describe(), ValidationTarget.ANNOTATED_ELEMENT);
            expr(expr, site, unique(name + EXPR_SUFFIX, names), !expr.isSkipNull(), propertyRules);
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
     * Materializes the rules of a method or constructor: parameter rules reading the parameter
     * bindings, cross-parameter rules reading the arguments as a whole ({@link BindingPlan#PARAMETERS}),
     * return-value rules reading {@link BindingPlan#RETURN_VALUE}, and the rules of the constrained
     * container element positions within parameters and return value. Names:
     * {@code <parameter>_<constraint>}, {@code <executable>_<constraint>} for cross-parameter rules,
     * {@code returnValue_<constraint>}, element rules as for properties.
     *
     * @param executable the executable definition.
     * @return the rules.
     * @throws ConstraintMapperException if a mapper is missing, misbehaves or produces a rule name
     *         already in use.
     */
    public ExecutableRules materialize(ExecutableDefinition executable) {
        Assert.notNull(executable, "executable cannot be null.");

        ExecutableBindingPlan plan = ExecutableBindingPlan.of(executable);
        Class<?> beanClass = executable.getOwner();
        List<MaterializedRule> parameterRules = new ArrayList<>();
        List<MaterializedRule> crossParameterRules = new ArrayList<>();
        List<MaterializedRule> returnValueRules = new ArrayList<>();
        List<ExecutableRules.ElementRules> elementRules = new ArrayList<>();
        Set<String> names = new HashSet<>();

        for (ParameterDefinition parameter : executable.getParameters()) {
            String name = parameter.getName();
            String binding = plan.getBindingName(parameter);
            RulePlacement placement = RulePlacement.parameter(name, parameter.getIndex());

            for (ConstraintDefinition constraint : parameter.getConstraints()) {
                ValueDeclaration declaration = ((DeclarationConstraintSource) constraint.getConstraintSource()).getDeclaration();
                Site site = new Site(beanClass, name, binding, Validators.binding(binding), name, declaration.getType(), placement,
                        declaration.describe(), ValidationTarget.ANNOTATED_ELEMENT);
                materialize(constraint, null, site, unique(name + "_" + decapitalize(constraint.getAnnotationType()), names), parameterRules);
            }

            for (ContainerElementDefinition node : parameter.getContainerElements()) {
                executableElements(beanClass, name, node, name, names, elementRules);
            }
        }

        for (ConstraintDefinition constraint : executable.getCrossParameterConstraints()) {
            Site site = new Site(beanClass, null, BindingPlan.PARAMETERS, Validators.binding(BindingPlan.PARAMETERS), PARAMETERS_VALUE,
                    Object[].class, RulePlacement.crossParameter(), constraint.getConstraintSource().describe(), ValidationTarget.PARAMETERS);
            materialize(constraint, null, site, unique(executable.getName() + "_" + decapitalize(constraint.getAnnotationType()), names),
                    crossParameterRules);
        }

        for (ExprDefinition expr : executable.getExprRules()) {
            Site site = new Site(beanClass, null, BindingPlan.PARAMETERS, Validators.binding(BindingPlan.PARAMETERS), PARAMETERS_VALUE,
                    Object[].class, RulePlacement.crossParameter(), expr.getConstraintSource().describe(), ValidationTarget.PARAMETERS);
            expr(expr, site, unique(executable.getName() + EXPR_SUFFIX, names), true, crossParameterRules);
        }

        if (executable.getReturnValue() != null) {
            ReturnValueDefinition returnValue = executable.getReturnValue();

            for (ConstraintDefinition constraint : returnValue.getConstraints()) {
                ValueDeclaration declaration = ((DeclarationConstraintSource) constraint.getConstraintSource()).getDeclaration();
                Site site = new Site(beanClass, RETURN_VALUE_NAME, BindingPlan.RETURN_VALUE, Validators.binding(BindingPlan.RETURN_VALUE),
                        RETURN_VALUE_NAME, declaration.getType(), RulePlacement.returnValue(), declaration.describe(), ValidationTarget.ANNOTATED_ELEMENT);
                materialize(constraint, null, site, unique(RETURN_VALUE_NAME + "_" + decapitalize(constraint.getAnnotationType()), names),
                        returnValueRules);
            }

            for (ExprDefinition expr : returnValue.getExprRules()) {
                ValueDeclaration declaration = ((DeclarationConstraintSource) expr.getConstraintSource()).getDeclaration();
                Site site = new Site(beanClass, RETURN_VALUE_NAME, BindingPlan.RETURN_VALUE, Validators.binding(BindingPlan.RETURN_VALUE),
                        RETURN_VALUE_NAME, declaration.getType(), RulePlacement.returnValue(), declaration.describe(), ValidationTarget.ANNOTATED_ELEMENT);
                expr(expr, site, unique(RETURN_VALUE_NAME + EXPR_SUFFIX, names), true, returnValueRules);
            }

            for (ContainerElementDefinition node : returnValue.getContainerElements()) {
                executableElements(beanClass, RETURN_VALUE_NAME, node, RETURN_VALUE_NAME, names, elementRules);
            }
        }

        return new ExecutableRules(executable, plan, parameterRules, crossParameterRules, returnValueRules, elementRules, failFast);
    }

    /**
     * Where a constraint occurrence sits: what the materializer knows regardless of which mapper runs.
     */
    private record Site(Class<?> beanClass, String propertyName, String bindingName, Function<?> value, String valueName,
                        Class<?> type, RulePlacement placement, String location, ValidationTarget validationTarget) {}

    private void elements(Class<?> beanClass, PropertyDefinition property, ContainerElementDefinition node, String prefix,
                          List<BeanRules.ElementRules> out) {
        String name = prefix + "_" + segment(node);
        List<MaterializedRule> rules = elementRules(beanClass, property.getName(), node, name, null);

        if (!rules.isEmpty()) out.add(new BeanRules.ElementRules(property, node, rules));

        for (ContainerElementDefinition child : node.getChildren()) {
            elements(beanClass, property, child, name, out);
        }
    }

    private void executableElements(Class<?> beanClass, String valueName, ContainerElementDefinition node, String prefix,
                                    Set<String> names, List<ExecutableRules.ElementRules> out) {
        String name = prefix + "_" + segment(node);
        List<MaterializedRule> rules = elementRules(beanClass, valueName, node, name, names);

        if (!rules.isEmpty()) out.add(new ExecutableRules.ElementRules(node, rules));

        for (ContainerElementDefinition child : node.getChildren()) {
            executableElements(beanClass, valueName, child, name, names, out);
        }
    }

    /**
     * The rules of one container element position, reading the element binding; the path is left to
     * the container walk.
     */
    private List<MaterializedRule> elementRules(Class<?> beanClass, String valueName, ContainerElementDefinition node, String name,
                                                Set<String> names) {
        List<MaterializedRule> rules = new ArrayList<>();

        if (node.getConstraints().isEmpty()) return rules;

        Class<?> type = node.getType() != null ? Types.erasure(node.getType()) : Object.class;
        String binding = BindingPlan.elementBinding(node);
        Site site = new Site(beanClass, valueName, binding, Validators.binding(binding), valueName, type, RulePlacement.none(),
                node.getSourceDeclaration().describe() + " " + node.getName(), ValidationTarget.ANNOTATED_ELEMENT);

        for (ConstraintDefinition constraint : node.getConstraints()) {
            String ruleName = name + "_" + decapitalize(constraint.getAnnotationType());
            materialize(constraint, null, site, names != null ? unique(ruleName, names) : ruleName, rules);
        }

        return rules;
    }

    /**
     * Rule-name segment of a container position: {@code <ContainerSimpleName><index>}, the index
     * omitted for the container value itself.
     */
    static String segment(ContainerElementDefinition node) {
        String container = node.getContainerType().getSimpleName().replaceAll("[^A-Za-z0-9_$]", "");
        return node.getTypeArgumentIndex() == ValueExtractorDescriptor.CONTAINER ? container : container + node.getTypeArgumentIndex();
    }

    private void materialize(ConstraintDefinition constraint, ConstraintDefinition host, Site site, String ruleName,
                             List<MaterializedRule> out) {
        materialize(constraint, host, null, site, ruleName, out);
    }

    /**
     * Materializes one constraint occurrence.
     *
     * @param constraint the constraint.
     * @param host the nearest enclosing {@code @ReportAsSingleViolation} composition; null when none.
     * @param composedBy the constraint this one composes; null for a declared constraint.
     * @param site where it is declared and what it validates.
     * @param ruleName the base rule name.
     * @param out where the materialized rules go.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void materialize(ConstraintDefinition constraint, ConstraintDefinition host, ConstraintDefinition composedBy, Site site,
                             String ruleName, List<MaterializedRule> out) {
        Class<? extends Annotation> type = constraint.getAnnotationType();
        ConstraintMapper mapper = mappers.find(type);

        if (mapper == null && !constraint.isComposed()) {
            throw new ConstraintMapperException(type, site.location(), "no mapper is registered for the constraint and it declares no validators.");
        }

        ConstraintTarget target = new ConstraintTarget(site.beanClass(), site.propertyName(), site.bindingName(),
                site.valueName(), site.type(), ruleName, errorCode(type), constraint.getSeverity(),
                message(type, constraint.getMessage(), site.valueName()), constraint.getSource(), site.value(),
                site.validationTarget(), description(constraint, composedBy, site), site.beanClass().getName(),
                tags(CONSTRAINT_TAG, type, constraint.getGroups(), host != null), attributes(constraint));
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
                own.add(new MaterializedRule(rule, constraint, ownHost, target, site.placement()));
            }
        }

        for (ConstraintDefinition composing : constraint.getComposingConstraints()) {
            materialize(composing, ownHost, constraint, site, ruleName + "_" + decapitalize(composing.getAnnotationType()), own);
        }

        if (!constraint.isReportAsSingleViolation() || own.isEmpty()) {
            out.addAll(own);
            return;
        }

        List<Rule> hosted = new ArrayList<>(own.size());

        for (MaterializedRule rule : own) {
            hosted.add(rule.getRule());
        }

        Rule wrapper = target.configure(ReportAsSingleViolationRule.builder(target.value(), hosted), true)
                .parameters(attributes(constraint)).build();
        out.add(new MaterializedRule(wrapper, constraint, host, target, site.placement(), own));
    }

    /**
     * The error code of a constraint's violations: its default message key.
     *
     * @param annotationType the constraint annotation type.
     * @return {@code <annotation class name>.message}.
     */
    /**
     * The attributes of a constraint other than {@code message}, {@code groups} and {@code payload}: what a
     * composition's violation reports so the host's message can reference them.
     */
    /**
     * Materializes one {@code @Expr} at a site: compiles the script and builds the rule.
     *
     * @param expr the declaration.
     * @param site where it is declared and what it validates.
     * @param ruleName the rule name, unique within the bean class or executable.
     * @param runOnNull whether the script runs when the value is null (false skips the rule).
     * @param out where the materialized rule goes.
     */
    private void expr(ExprDefinition expr, Site site, String ruleName, boolean runOnNull, List<MaterializedRule> out) {
        Script<?> script = compile(expr, site);
        String description = "@Expr(" + script.getLanguageName() + ": " + script.getSourceText() + ") on " + site.location();
        ConstraintTarget target = new ConstraintTarget(site.beanClass(), site.propertyName(), site.bindingName(), site.valueName(),
                site.type(), ruleName, expr.getErrorCode(), expr.getSeverity(), exprMessage(expr.getMessage(), site.valueName()),
                expr.getSource(), site.value(), site.validationTarget(), description, site.beanClass().getName(),
                tags(EXPR_TAG, expr.getAnnotationType(), expr.getGroups(), false));
        Rule rule = target.configure(ExprValidationRule.builder(target.value(), script), runOnNull).build();
        out.add(new MaterializedRule(rule, expr, null, target, site.placement()));
    }

    /**
     * The description of a constraint's rules: the annotation with its attributes (the three every
     * constraint declares left out), what it composes if anything, and where it is declared, as in
     * {@code @Size(max=5, min=2) on Order.code}.
     */
    private static String description(ConstraintDefinition constraint, ConstraintDefinition composedBy, Site site) {
        StringBuilder result = new StringBuilder("@").append(constraint.getAnnotationType().getSimpleName());
        Map<String, Object> attributes = attributes(constraint);

        if (!attributes.isEmpty()) {
            result.append('(');
            boolean first = true;

            for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
                if (!first) result.append(", ");
                result.append(attribute.getKey()).append('=').append(render(attribute.getValue()));
                first = false;
            }

            result.append(')');
        }

        if (composedBy != null) result.append(" composing @").append(composedBy.getAnnotationType().getSimpleName());
        return result.append(" on ").append(site.location()).toString();
    }

    private static String render(Object value) {
        if (value instanceof String text) return "\"" + text + "\"";
        if (value instanceof Class<?> type) return type.getSimpleName();
        if (value instanceof Object[] array) {
            StringBuilder result = new StringBuilder("[");

            for (int i = 0; i < array.length; i++) {
                if (i > 0) result.append(", ");
                result.append(render(array[i]));
            }

            return result.append(']').toString();
        }
        return String.valueOf(value);
    }

    /**
     * The tags of a declaration's rules: the kind of declaration, the annotation's simple name, the
     * groups ({@code Default} when none) and {@code hosted} inside a {@code @ReportAsSingleViolation}
     * composition.
     */
    private static List<String> tags(String kind, Class<? extends Annotation> annotationType, List<Class<?>> groups, boolean hosted) {
        List<String> result = new ArrayList<>(groups.size() + 3);
        result.add(kind);
        result.add(annotationType.getSimpleName());

        if (groups.isEmpty()) {
            result.add(Default.class.getSimpleName());
        } else {
            for (Class<?> group : groups) {
                result.add(group.getSimpleName());
            }
        }

        if (hosted) result.add(HOSTED_TAG);
        return result;
    }

    /**
     * Prepares an {@code @Expr} message for the formatter: the violation reports the validated value
     * under the site's name (the property or parameter name), so {@code ${value}} is rewritten to
     * that name. {@code ${validatedValue}} needs no rewriting, the formatter resolves it.
     *
     * @param template the message as declared, or null.
     * @param valueName the name the value is reported under.
     * @return the template to format, or null.
     */
    static String exprMessage(String template, String valueName) {
        if (template == null) return null;
        return template.replace("${" + EXPR_VALUE + "}", "${" + valueName + "}");
    }

    /**
     * Compiles the script of an {@code @Expr} in the language it declares, else the manager's default
     * language.
     *
     * @throws ExprDeclarationException if no language is declared anywhere, no engine answers to the
     *         language, or the script does not compile.
     */
    private static Script<?> compile(ExprDefinition expr, Site site) {
        ScriptProcessorManager manager = ScriptProcessorManager.getInstance();
        String language = expr.getLanguage();

        if (language == null) language = manager.getDefaultLanguage();

        if (language == null) {
            throw new ExprDeclarationException("@Expr on [" + site.location() + "] names no scripting language and none is declared"
                    + " as the default. Declare one with @Expr(lang), with @ExprDefaults on the class or its package, with"
                    + " ScriptProcessorManager.getInstance().setDefaultLanguage(name) or with the system property ["
                    + ScriptProcessorManager.DEFAULT_LANGUAGE_PROPERTY + "]. Available languages: " + manager.getAvailableLanguages(),
                    expr.getConstraintSource());
        }

        if (manager.getScriptProcessorFactory(language) == null) {
            throw new ExprDeclarationException("@Expr on [" + site.location() + "] is declared in scripting language [" + language
                    + "] but no ScriptProcessorFactory or JSR-223 engine is available for it. Available languages: "
                    + manager.getAvailableLanguages(), expr.getConstraintSource());
        }

        try {
            return Script.builder().with(language, expr.getScript()).returnType(Boolean.class).build();
        } catch (UnrulyException e) {
            throw new ExprDeclarationException("@Expr on [" + site.location() + "] does not compile in [" + language + "]: "
                    + e.getMessage() + " Script [" + expr.getScript() + "]", expr.getConstraintSource(), e);
        }
    }

    /**
     * The constraint's attributes as violation parameters: its own, less {@code message},
     * {@code groups} and {@code payload}; an array attribute as a list, so that it renders as
     * {@code [A, B]} in a message, as the reference implementation renders it.
     */
    private static Map<String, Object> attributes(ConstraintDefinition constraint) {
        Map<String, Object> result = new LinkedHashMap<>(ConstraintDefinitionDescriptor.of(constraint).getAttributes());
        result.remove("message");
        result.remove("groups");
        result.remove("payload");
        result.replaceAll((name, value) -> value instanceof Object[] array ? List.of(array) : value);
        return result;
    }

    public static String errorCode(Class<? extends Annotation> annotationType) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        return annotationType.getName() + MESSAGE_KEY_SUFFIX;
    }

    /**
     * The rulii message template for a constraint's jakarta template, declared or built at run time by
     * a constraint validator: the constraint's own default key resolves through the error code, so it
     * yields no template; any other key becomes a placeholder the render-time formatter expands; a
     * literal is translated.
     *
     * @param annotationType the constraint annotation type.
     * @param template the jakarta template.
     * @param valueName the parameter the rules report the value under.
     * @return the rulii template; null when the message is to be resolved from the error code.
     */
    public static String message(Class<? extends Annotation> annotationType, String template, String valueName) {
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(template, "template cannot be null.");
        if (MessageTemplates.isTemplateKey(template, MessageTemplates.attributeNames(annotationType))
                && errorCode(annotationType).equals(MessageTemplates.templateKey(template))) {
            return null;
        }
        return MessageTemplates.translate(template, valueName);
    }

    private static String decapitalize(Class<? extends Annotation> annotationType) {
        String name = annotationType.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
