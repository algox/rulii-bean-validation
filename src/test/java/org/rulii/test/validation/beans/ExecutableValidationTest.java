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
package org.rulii.test.validation.beans;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintTarget;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.Path;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstructorDescriptor;
import jakarta.validation.metadata.MethodDescriptor;
import jakarta.validation.metadata.MethodType;
import org.junit.jupiter.api.Test;
import org.rulii.annotation.Param;
import org.rulii.bind.Bindings;
import org.rulii.bind.NamedScope;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.model.InputParameter;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleflow.RuleFlowListener;
import org.rulii.ruleflow.command.RuleFlowCommand;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidationResult;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.ExecutableBeanValidator;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.bootstrap.RuliiValidationProvider;
import org.rulii.validation.beans.bootstrap.RuliiValidatorFactory;
import org.rulii.validation.beans.model.AmbiguousConstraintTargetException;
import org.rulii.validation.beans.model.DefaultParameterNameProvider;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.validator.ConstraintValidatorException;
import org.rulii.validation.beans.model.IllegalExecutableConstraintException;
import org.rulii.validation.rules.Validators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.rulii.model.action.Actions.action;

/**
 * Executable validation end to end: parameters, return values, cross-parameter constraints,
 * constructors, cascades and container elements of arguments, the inheritance rules, descriptors,
 * parameter names, the bootstrap wiring, and the flows it runs as. Paths are asserted as the reference
 * implementation renders them.
 */
public class ExecutableValidationTest {

    private static final ParameterNameProvider POSITIONAL = new ParameterNameProvider() {
        @Override
        public List<String> getParameterNames(Constructor<?> constructor) {
            return names(constructor.getParameterCount());
        }

        @Override
        public List<String> getParameterNames(Method method) {
            return names(method.getParameterCount());
        }

        private List<String> names(int count) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                result.add("p" + i);
            }
            return result;
        }
    };

    public ExecutableValidationTest() {
        super();
    }

    private static BeanValidator validator() {
        return BeanValidator.builder().parameterNameProvider(POSITIONAL).build();
    }

    private static List<String> report(Iterable<? extends ConstraintViolation<?>> violations) {
        List<String> result = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath() + ":" + ((RuleViolation) violation).getRuleName());
        }
        return result;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return type.getMethod(name, parameterTypes);
    }

    // --- parameters -----------------------------------------------------------------------------

    @Test
    public void parameterConstraintsReportAtTheParameterNodes() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        OrderService service = new OrderService();
        Method place = method(OrderService.class, "place", Order.class, int.class);

        BeanValidationResult<OrderService> violations = (BeanValidationResult<OrderService>) validator.validateParameters(service, place, new Object[] {null, 0});

        assertEquals(List.of("place.p0:p0_notNull", "place.p1:p1_min"), report(violations));
        assertEquals(List.of("place.p0:p0_notNull"), report(BeanValidator.builder().parameterNameProvider(POSITIONAL).failFast(true).build()
                .forExecutables().validateParameters(service, place, new Object[] {null, 0})), "fail-fast: the first violation only");
        ConstraintViolation<OrderService> first = violations.iterator().next();
        assertSame(service, first.getRootBean());
        assertSame(service, first.getLeafBean());
        assertEquals(OrderService.class, first.getRootBeanClass());
        assertArrayEquals(new Object[] {null, 0}, first.getExecutableParameters());
        assertNull(first.getExecutableReturnValue());
        List<Path.Node> nodes = new ArrayList<>();
        first.getPropertyPath().forEach(nodes::add);
        assertEquals(ElementKind.METHOD, nodes.get(0).getKind());
        assertEquals(List.of(Order.class, int.class), nodes.get(0).as(Path.MethodNode.class).getParameterTypes());
        assertEquals(ElementKind.PARAMETER, nodes.get(1).getKind());
        assertEquals(0, nodes.get(1).as(Path.ParameterNode.class).getParameterIndex());
        assertEquals("p0", nodes.get(1).getName());

        Order order = new Order();
        order.code = "ok";
        assertTrue(validator.validateParameters(service, place, new Object[] {order, 1}).isEmpty());
    }

    @Test
    public void cascadedParametersDescendAtTheParameterPath() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        Order order = new Order();
        order.code = "x";

        Set<ConstraintViolation<OrderService>> violations = validator.validateParameters(new OrderService(),
                method(OrderService.class, "place", Order.class, int.class), new Object[] {order, 1});

        assertEquals(List.of("place.p0.code:code_size"), report(violations));
        assertSame(order, violations.iterator().next().getLeafBean());
        assertEquals("x", violations.iterator().next().getInvalidValue());
    }

    @Test
    public void validArrayParametersCascadeIntoTheirElements() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        Order bad = new Order();
        bad.code = "x";
        Order good = new Order();
        good.code = "ok";

        Set<ConstraintViolation<OrderService>> violations = validator.validateParameters(new OrderService(),
                method(OrderService.class, "batch", Order[].class), new Object[] {new Order[] {good, null, bad}});

        assertEquals(List.of("batch.p0[2].code:code_size"), report(violations));
        assertSame(bad, violations.iterator().next().getLeafBean());
    }

    @Test
    public void crossParameterValidatorsCanPointAtAParameter() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        Order order = new Order();
        order.code = "ok";

        // The parameter node takes the place of the cross-parameter node, named by the parameter name provider.
        assertEquals(List.of("pair.p1:pair_pointed"), report(validator.validateParameters(new Pointing(),
                method(Pointing.class, "pair", Order.class, int.class), new Object[] {order, 1})));
        assertEquals(List.of("deep.p0.code:deep_pointed"), report(validator.validateParameters(new Pointing(),
                method(Pointing.class, "deep", Order.class, int.class), new Object[] {order, 1})));

        ConstraintValidatorException range = assertThrows(ConstraintValidatorException.class, () -> validator.validateParameters(new Pointing(),
                method(Pointing.class, "beyond", Order.class, int.class), new Object[] {order, 1}));
        assertInstanceOf(IllegalArgumentException.class, range.getCause());

        // A parameter-level validator is not at the cross-parameter node: refused when the path is built.
        ValidationException misplaced = assertThrows(ValidationException.class, () -> validator.validateParameters(new Pointing(),
                method(Pointing.class, "single", String.class), new Object[] {"x"}));
        assertTrue(misplaced.getMessage().contains("cross-parameter"), misplaced.getMessage());
    }

    @Test
    public void containerElementsOfParametersAreWalked() throws Exception {
        ExecutableValidator validator = validator().forExecutables();

        Set<ConstraintViolation<OrderService>> violations = validator.validateParameters(new OrderService(),
                method(OrderService.class, "tag", List.class), new Object[] {List.of("ok", "")});

        assertEquals(List.of("tag.p0[1].<list element>:p0_List0_notBlank"), report(violations));
        assertEquals("", violations.iterator().next().getInvalidValue());
    }

    @Test
    public void interfaceMethodFindsTheImplementingDefinition() throws Exception {
        // What a proxy hands over: the interface method, on an implementation instance.
        ExecutableValidator validator = validator().forExecutables();
        Method place = method(Service.class, "place", Order.class, int.class);

        assertEquals(List.of("place.p0:p0_notNull", "place.p1:p1_min"),
                report(validator.validateParameters(new OrderService(), place, new Object[] {null, 0})));
        assertEquals(List.of("place.<return value>:returnValue_notNull"),
                report(validator.validateReturnValue(new OrderService(), place, null)));
    }

    @Test
    public void groupsSelectParameterConstraints() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        Method archive = method(OrderService.class, "archive", String.class);

        assertTrue(validator.validateParameters(new OrderService(), archive, new Object[] {null}).isEmpty());
        assertEquals(1, validator.validateParameters(new OrderService(), archive, new Object[] {null}, Admin.class).size());
    }

    @Test
    public void unconstrainedExecutablesYieldNothingAndArgumentsAreChecked() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        OrderService service = new OrderService();
        Method plain = method(OrderService.class, "plain", String.class);

        assertTrue(validator.validateParameters(service, plain, new Object[] {null}).isEmpty());
        assertTrue(validator.validateReturnValue(service, plain, null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> validator.validateParameters(service,
                method(OrderService.class, "place", Order.class, int.class), new Object[] {null}));
        assertThrows(IllegalArgumentException.class, () -> validator.validateParameters(null, plain, new Object[] {null}));
    }

    // --- return values and cross-parameter -------------------------------------------------------

    @Test
    public void returnValueConstraintsReportAtTheReturnValueNode() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        OrderService service = new OrderService();
        Method place = method(OrderService.class, "place", Order.class, int.class);

        BeanValidationResult<OrderService> violations = (BeanValidationResult<OrderService>) validator.validateReturnValue(service, place, null);

        assertEquals(List.of("place.<return value>:returnValue_notNull"), report(violations));
        ConstraintViolation<OrderService> violation = violations.iterator().next();
        assertNull(violation.getExecutableParameters());
        assertNull(violation.getExecutableReturnValue());
        assertEquals(ElementKind.METHOD, violations.getViolations().get(0).getPropertyPath().getNodes().get(0).getKind());
        assertEquals(ElementKind.RETURN_VALUE, violations.getViolations().get(0).getPropertyPath().getNodes().get(1).getKind());

        Order order = new Order();
        order.code = "x";
        assertEquals(List.of("place.<return value>.code:code_size"), report(validator.validateReturnValue(service, place, order)));
        assertSame(order, validator.validateReturnValue(service, place, order).iterator().next().getExecutableReturnValue());
    }

    @Test
    public void returnValueContainerElementsAreWalked() throws Exception {
        ExecutableValidator validator = validator().forExecutables();

        assertEquals(List.of("names.<return value>[0].<list element>:returnValue_List0_notBlank"),
                report(validator.validateReturnValue(new OrderService(), method(OrderService.class, "names"), List.of("", "x"))));
        assertEquals(List.of("names.<return value>:returnValue_notEmpty"),
                report(validator.validateReturnValue(new OrderService(), method(OrderService.class, "names"), List.of())));
    }

    @Test
    public void getterReturnValuesValidateThePropertyConstraints() throws Exception {
        ExecutableValidator validator = validator().forExecutables();

        assertEquals(List.of("getCode.<return value>:returnValue_notNull"),
                report(validator.validateReturnValue(new OrderService(), method(OrderService.class, "getCode"), null)));
    }

    @Test
    public void crossParameterConstraintsReportAtTheCrossParameterNode() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        OrderService service = new OrderService();
        Method schedule = method(OrderService.class, "schedule", LocalDate.class, LocalDate.class);
        Object[] arguments = {LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)};

        Set<ConstraintViolation<OrderService>> violations = validator.validateParameters(service, schedule, arguments);

        assertEquals(List.of("schedule.<cross-parameter>:schedule_consistentDates"), report(violations));
        ConstraintViolation<OrderService> violation = violations.iterator().next();
        assertSame(arguments, violation.getInvalidValue());
        assertSame(service, violation.getLeafBean());
        assertEquals("end must follow start", violation.getMessage());
        assertTrue(validator.validateParameters(service, schedule, new Object[] {arguments[1], arguments[0]}).isEmpty());
    }

    @Test
    public void validationAppliesToResolvesTheTarget() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        OrderService service = new OrderService();

        assertEquals(List.of("both.<cross-parameter>:both_either"),
                report(validator.validateParameters(service, method(OrderService.class, "both", String.class), new Object[] {"x"})));
        assertEquals(List.of("eitherVoid.<cross-parameter>:eitherVoid_either"),
                report(validator.validateParameters(service, method(OrderService.class, "eitherVoid", String.class), new Object[] {"x"})));
        assertEquals(List.of("eitherReturn.<return value>:returnValue_either"),
                report(validator.validateReturnValue(service, method(OrderService.class, "eitherReturn"), "x")));
    }

    // --- constructors -----------------------------------------------------------------------------

    @Test
    public void constructorParametersReportUnderTheConstructorNode() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        Constructor<OrderService> constructor = OrderService.class.getConstructor(String.class);

        BeanValidationResult<OrderService> violations = (BeanValidationResult<OrderService>) validator.validateConstructorParameters(constructor, new Object[] {""});

        assertEquals(List.of("OrderService.p0:p0_notBlank"), report(violations));
        ConstraintViolation<OrderService> violation = violations.iterator().next();
        assertNull(violation.getRootBean());
        assertNull(violation.getLeafBean());
        assertEquals(OrderService.class, violation.getRootBeanClass());
        assertArrayEquals(new Object[] {""}, violation.getExecutableParameters());
        assertEquals(ElementKind.CONSTRUCTOR, violation.getPropertyPath().iterator().next().getKind());
        assertEquals(List.of(String.class), violation.getPropertyPath().iterator().next().as(Path.ConstructorNode.class).getParameterTypes());
    }

    @Test
    public void constructorReturnValueCascadesIntoTheCreatedObject() throws Exception {
        ExecutableValidator validator = validator().forExecutables();
        Constructor<OrderService> constructor = OrderService.class.getConstructor(Order.class);
        OrderService created = new OrderService(new Order());

        Set<ConstraintViolation<OrderService>> violations = validator.validateConstructorReturnValue(constructor, created);

        assertEquals(List.of("OrderService.<return value>.owner:owner_notNull"), report(violations));
        assertSame(created, violations.iterator().next().getRootBean());
        assertSame(created, violations.iterator().next().getExecutableReturnValue());
    }

    // --- declaration rules -------------------------------------------------------------------------

    @Test
    public void overridingMethodsMayNotAddParameterConstraints() {
        BeanValidator validator = validator();

        IllegalExecutableConstraintException e = assertThrows(IllegalExecutableConstraintException.class,
                () -> validator.getBeanDefinition(StrengthensParameters.class));
        assertEquals("run", e.getExecutable().getName());
        assertTrue(e.getMessage().contains("overrides or implements"), e.getMessage());

        // Declaring exactly the same constraints again is tolerated and reports once.
        assertEquals(List.of("go.p0:p0_notNull"), report(validator.forExecutables().validateParameters(new RepeatsParameters(),
                assertDoesNotThrow(() -> RepeatsParameters.class.getMethod("go", String.class)), new Object[] {null})));
    }

    @Test
    public void parallelTypesMayNotBothConstrainParameters() {
        IllegalExecutableConstraintException e = assertThrows(IllegalExecutableConstraintException.class,
                () -> validator().getBeanDefinition(ParallelImplementation.class));
        assertTrue(e.getMessage().contains("parallel types"), e.getMessage());
    }

    @Test
    public void returnValueConstraintsAccumulateOverTheHierarchy() throws Exception {
        ExecutableValidator validator = validator().forExecutables();

        // The validated method's own constraints first, then the overridden method's.
        assertEquals(List.of("compute.<return value>:returnValue_notBlank", "compute.<return value>:returnValue_notNull"),
                report(validator.validateReturnValue(new AccumulatingReturn(), method(AccumulatingReturn.class, "compute"), null)));
        assertEquals(List.of("compute.<return value>:returnValue_notBlank"),
                report(validator.validateReturnValue(new AccumulatingReturn(), method(AccumulatingReturn.class, "compute"), " ")));
    }

    @Test
    public void voidMethodsMayNotConstrainTheirReturnValue() {
        IllegalExecutableConstraintException e = assertThrows(IllegalExecutableConstraintException.class,
                () -> validator().getBeanDefinition(ConstrainedVoid.class));
        assertTrue(e.getMessage().contains("returns void"), e.getMessage());
    }

    @Test
    public void ambiguousConstraintTargetsFailTheLoad() {
        AmbiguousConstraintTargetException e = assertThrows(AmbiguousConstraintTargetException.class,
                () -> validator().getBeanDefinition(Ambiguous.class));
        assertEquals(Either.class, e.getAnnotationType());
        assertEquals("either", e.getExecutable().getName());
    }

    @Test
    public void crossParameterConstraintsNeedParametersAndAValidatorForThem() {
        assertTrue(assertThrows(IllegalExecutableConstraintException.class, () -> validator().getBeanDefinition(CrossWithoutParameters.class))
                .getMessage().contains("has no parameters"));
        assertTrue(assertThrows(IllegalExecutableConstraintException.class, () -> validator().getBeanDefinition(CrossWithoutValidator.class))
                .getMessage().contains("validate parameters"));
    }

    @Test
    public void validationTargetsAreReadOffTheValidators() {
        assertEquals(Set.of(ValidationTarget.PARAMETERS), ExecutableDefinition.validationTargets(ConsistentDates.class));
        assertEquals(Set.of(ValidationTarget.ANNOTATED_ELEMENT), ExecutableDefinition.validationTargets(NotNull.class));
        assertEquals(Set.of(ValidationTarget.ANNOTATED_ELEMENT, ValidationTarget.PARAMETERS), ExecutableDefinition.validationTargets(Either.class));
    }

    // --- descriptors ------------------------------------------------------------------------------

    @Test
    public void descriptorsDescribeConstrainedExecutables() {
        BeanDescriptor descriptor = validator().getConstraintsForClass(OrderService.class);

        MethodDescriptor place = descriptor.getConstraintsForMethod("place", Order.class, int.class);
        assertNotNull(place);
        assertEquals("place", place.getName());
        assertFalse(place.hasConstraints(), "an executable descriptor declares no constraints of its own");
        assertTrue(place.hasConstrainedParameters());
        assertTrue(place.hasConstrainedReturnValue());
        assertEquals(Order.class, place.getElementClass());
        assertEquals(2, place.getParameterDescriptors().size());
        assertEquals("p1", place.getParameterDescriptors().get(1).getName());
        assertEquals(1, place.getParameterDescriptors().get(1).getIndex());
        assertEquals(int.class, place.getParameterDescriptors().get(1).getElementClass());
        assertTrue(place.getParameterDescriptors().get(0).isCascaded());
        assertEquals(1, place.getReturnValueDescriptor().getConstraintDescriptors().size());
        assertFalse(place.getCrossParameterDescriptor().hasConstraints());

        MethodDescriptor schedule = descriptor.getConstraintsForMethod("schedule", LocalDate.class, LocalDate.class);
        assertEquals(Object[].class, schedule.getCrossParameterDescriptor().getElementClass());
        assertEquals(1, schedule.getCrossParameterDescriptor().getConstraintDescriptors().size());
        // A void method's return value is described as void, not as nothing.
        assertEquals(void.class, schedule.getReturnValueDescriptor().getElementClass());
        assertFalse(schedule.getReturnValueDescriptor().hasConstraints());
        assertFalse(schedule.getReturnValueDescriptor().isCascaded());
        assertTrue(schedule.getReturnValueDescriptor().getConstrainedContainerElementTypes().isEmpty());
        assertTrue(schedule.getReturnValueDescriptor().getGroupConversions().isEmpty());
        assertFalse(schedule.hasConstrainedReturnValue());

        assertNull(descriptor.getConstraintsForMethod("plain", String.class));
        List<String> nonGetters = new ArrayList<>();
        for (MethodDescriptor method : descriptor.getConstrainedMethods(MethodType.NON_GETTER)) {
            nonGetters.add(method.getName());
        }
        assertTrue(nonGetters.containsAll(List.of("place", "tag", "names", "schedule")), nonGetters.toString());
        assertFalse(nonGetters.contains("getCode"));
        assertEquals(1, descriptor.getConstrainedMethods(MethodType.GETTER).size());
        assertEquals("getCode", descriptor.getConstrainedMethods(MethodType.GETTER).iterator().next().getName());

        assertEquals(2, descriptor.getConstrainedConstructors().size());
        ConstructorDescriptor constructor = descriptor.getConstraintsForConstructor(String.class);
        assertEquals(OrderService.class, constructor.getElementClass());
        assertTrue(constructor.hasConstrainedParameters());
        assertNull(descriptor.getConstraintsForConstructor());

        // Executables carrying only the library's @Validate rules are validated but are not the specification's metadata.
        BeanDescriptor desk = validator().getConstraintsForClass(Desk.class);
        assertNull(desk.getConstraintsForMethod("book", LocalDate.class, LocalDate.class));
        assertNull(desk.getConstraintsForMethod("label"));
        assertNull(desk.getConstraintsForConstructor(LocalDate.class));
        assertTrue(desk.getConstrainedMethods(MethodType.NON_GETTER, MethodType.GETTER).isEmpty());
        assertTrue(desk.getConstrainedConstructors().isEmpty());
        assertFalse(desk.isBeanConstrained());
        assertTrue(validator().getBeanDefinition(Desk.class).getMethod("book", LocalDate.class, LocalDate.class).isConstrained(),
                "the engine still validates it");
    }

    // --- wiring -------------------------------------------------------------------------------------

    @Test
    public void parameterNamesComeFromTheConfiguredProvider() throws Exception {
        Method place = method(OrderService.class, "place", Order.class, int.class);

        // The default provider reads the real names from the class file, -parameters or not; a rulii @Param renames.
        assertEquals(List.of("order", "quantity"), new DefaultParameterNameProvider().getParameterNames(place));
        assertEquals(List.of("place.order:order_notNull", "place.quantity:quantity_min"),
                report(BeanValidator.builder().build().forExecutables().validateParameters(new OrderService(), place, new Object[] {null, 0})));
        assertEquals(List.of("region"), new DefaultParameterNameProvider().getParameterNames(OrderService.class.getConstructor(String.class)));
        assertEquals(List.of("why"), new DefaultParameterNameProvider().getParameterNames(method(OrderService.class, "archive", String.class)));

        RuliiValidatorFactory factory = (RuliiValidatorFactory) Validation.byProvider(RuliiValidationProvider.class).configure()
                .parameterNameProvider(POSITIONAL).buildValidatorFactory();
        assertSame(POSITIONAL, factory.getParameterNameProvider());
        assertEquals(List.of("place.p0:p0_notNull", "place.p1:p1_min"),
                report(factory.getValidator().forExecutables().validateParameters(new OrderService(), place, new Object[] {null, 0})));

        RuliiValidatorFactory plain = (RuliiValidatorFactory) Validation.buildDefaultValidatorFactory();
        ExecutableBeanValidator overridden = plain.usingContext().parameterNameProvider(POSITIONAL).getValidator().forExecutables();
        assertNotSame(plain.getValidator().forExecutables(), overridden);
        assertEquals(List.of("place.p0:p0_notNull", "place.p1:p1_min"),
                report(overridden.validateParameters(new OrderService(), place, new Object[] {null, 0})));
    }

    @Test
    public void beanValidationOfTheServiceIsUnaffected() {
        BeanValidator validator = validator();

        assertEquals(List.of("owner:owner_notNull", "code:code_notNull"), report(validator.validate(new OrderService())));
        assertFalse(validator.getBeanDefinition(OrderService.class).getMethods().isEmpty());
    }

    // --- flows -------------------------------------------------------------------------------------

    @Test
    public void executableFlowsAreAssembledPerExecutable() throws Exception {
        BeanValidator validator = validator();
        Method place = method(OrderService.class, "place", Order.class, int.class);
        RuleFlow<?> parameters = validator.getParametersRuleFlow(OrderService.class, place);
        RuleFlow<?> returnValue = validator.getReturnValueRuleFlow(OrderService.class, place);

        assertNotNull(parameters);
        assertSame(parameters, validator.getParametersRuleFlow(OrderService.class, place), "assembled once");
        assertEquals("orderService_placeParametersFlow", parameters.getName());
        assertEquals("orderService_placeReturnValueFlow", returnValue.getName());
        // One required input per parameter, by name; the rest as the bean flow has them.
        assertEquals(List.of("p0", "p1", BindingPlan.THIS, BindingPlan.ROOT, BindingPlan.PARAMETERS, BindingPlan.RULE_VIOLATIONS,
                BindingPlan.TRAVERSAL, BindingPlan.GROUPS), inputs(parameters, false));
        assertEquals(List.of("p0", "p1"), inputs(parameters, true));
        assertEquals(List.of(BindingPlan.RETURN_VALUE, BindingPlan.THIS, BindingPlan.ROOT, BindingPlan.RULE_VIOLATIONS,
                BindingPlan.TRAVERSAL, BindingPlan.GROUPS), inputs(returnValue, false));
        assertEquals(List.of(BindingPlan.RETURN_VALUE), inputs(returnValue, true));

        // No flow where there is nothing to validate.
        assertNull(validator.getReturnValueRuleFlow(OrderService.class, method(OrderService.class, "tag", List.class)), "void method");
        assertNull(validator.getParametersRuleFlow(OrderService.class, method(OrderService.class, "names")), "no parameters");
        assertNull(validator.getParametersRuleFlow(OrderService.class, method(OrderService.class, "plain", String.class)), "unconstrained");
        assertNull(validator.getReturnValueRuleFlow(OrderService.class, method(OrderService.class, "plain", String.class)), "unconstrained");

        // Overloaded constructors carry their parameter types in the name.
        assertEquals("orderService_OrderService_StringParametersFlow",
                validator.getParametersRuleFlow(OrderService.class, OrderService.class.getConstructor(String.class)).getName());
        assertEquals("orderService_OrderService_OrderReturnValueFlow",
                validator.getReturnValueRuleFlow(OrderService.class, OrderService.class.getConstructor(Order.class)).getName());
        assertNull(validator.getParametersRuleFlow(OrderService.class, OrderService.class.getConstructor(Order.class)), "parameter unconstrained");
    }

    private static List<String> inputs(RuleFlow<?> flow, boolean requiredOnly) {
        List<String> result = new ArrayList<>();
        for (InputParameter<?> input : flow.getInputParameters()) {
            if (!requiredOnly || input.required()) result.add(input.name());
        }
        return result;
    }

    @Test
    public void methodValidationRunsAsAFlowInAnExistingContext() throws Exception {
        BeanValidator validator = validator();
        OrderService service = new OrderService();
        Method place = method(OrderService.class, "place", Order.class, int.class);
        List<String> events = new ArrayList<>();
        ScopedBindings bindings = Bindings.builder().scoped();
        RuleContext context = RuleContext.builder().with(bindings).build();
        context.getTracer().addListener(new RuleFlowListener() {
            @Override
            public void onRuleFlowStart(RuleFlow<?> ruleFlow, NamedScope ruleFlowScope) {
                events.add("start " + ruleFlow.getName());
            }

            @Override
            public void onRuleFlowCommandExecuted(RuleFlow<?> ruleFlow, RuleFlowCommand command) {
                events.add(command.toString());
            }
        });

        // No collector bound: a path-aware one is bound for the run and returned; the tracer saw the flow and its step.
        RuleViolations own = validator.forExecutables().validateParametersIn(context, service, place, new Object[] {null, 0});
        assertInstanceOf(BeanRuleViolations.class, own);
        assertEquals(List.of("place.p0:p0_notNull", "place.p1:p1_min"), report(((BeanRuleViolations) own).getBeanViolations()));
        assertArrayEquals(new Object[] {null, 0}, ((BeanRuleViolations) own).getExecutableParameters());
        assertEquals(List.of("start orderService_placeParametersFlow", "validate arguments of place"), events);
        assertNull(bindings.getBinding("p0"), "the run's scope is gone");
        assertNull(bindings.getBinding(BindingPlan.PARAMETERS));

        // A plain collector bound by the caller receives the stamped violations and is returned.
        RuleViolations plain = new RuleViolations();
        bindings.bind(BindingPlan.RULE_VIOLATIONS, RuleViolations.class, plain);
        assertSame(plain, validator.forExecutables().validateReturnValueIn(context, service, place, null));
        assertEquals(List.of("place.<return value>:returnValue_notNull"),
                report(plain.getViolations().stream().map(v -> (BeanRuleViolation) v).toList()));
        assertEquals("start orderService_placeReturnValueFlow", events.get(2));

        // Nothing to validate: the collector comes back untouched.
        Method plainMethod = method(OrderService.class, "plain", String.class);
        assertSame(plain, validator.forExecutables().validateParametersIn(context, service, plainMethod, new Object[] {null}));
        assertEquals(1, plain.size());
        assertThrows(IllegalArgumentException.class, () -> validator.forExecutables().validateParametersIn(null, service, place, new Object[] {null, 0}));
        assertThrows(IllegalArgumentException.class, () -> validator.forExecutables().validateParametersIn(context, service, place, new Object[] {null}));
    }

    @Test
    public void rootIsTheObjectTheMethodIsCalledOn() throws Exception {
        List<Object> roots = new ArrayList<>();
        RuleRegistry registry = RuleRegistry.builder()
                .register(Rule.builder().name("rootSpy")
                        .then(action((RuleContext ruleContext) -> roots.add(ruleContext.getBindings().getValue(BindingPlan.ROOT))))
                        .build())
                .build();
        BeanValidator validator = BeanValidator.builder().parameterNameProvider(POSITIONAL).registry(registry).build();
        OrderService service = new OrderService();
        Shipment shipment = new Shipment();
        Method ship = method(OrderService.class, "ship", Shipment.class);

        validator.forExecutables().validateParameters(service, ship, new Object[] {shipment});
        validator.forExecutables().validateReturnValue(service, ship, shipment);
        validator.validate(shipment);

        assertEquals(List.of(service, service, shipment), roots);
    }

    @Test
    public void validateRulesOnExecutables() throws Exception {
        RuleRegistry registry = RuleRegistry.builder()
                .register(Validators.notNull(Validators.binding("p0")).name("startRequired").build())
                .register(Validators.notNull(Validators.binding(BindingPlan.VALUE)).name("resultRequired").build())
                .build();
        BeanValidator validator = BeanValidator.builder().parameterNameProvider(POSITIONAL).registry(registry).build();
        ExecutableBeanValidator executables = validator.forExecutables();
        Desk desk = new Desk("d1");
        Method book = method(Desk.class, "book", LocalDate.class, LocalDate.class);
        Method label = method(Desk.class, "label");
        Method archive = method(Desk.class, "archive", LocalDate.class);

        // The arguments are in scope by name; violations report at the cross-parameter node.
        assertEquals(List.of("book.<cross-parameter>:startRequired"),
                report(executables.validateParameters(desk, book, new Object[] {null, LocalDate.of(2026, 1, 1)})));
        assertTrue(executables.validateParameters(desk, book, new Object[] {LocalDate.of(2026, 1, 1), null}).isEmpty());
        // The return value is $value; violations report at the return-value node.
        assertEquals(List.of("label.<return value>:resultRequired"), report(executables.validateReturnValue(desk, label, null)));
        assertTrue(executables.validateReturnValue(desk, label, "x").isEmpty());
        // Groups select the rules as they select constraints.
        assertTrue(executables.validateParameters(desk, archive, new Object[] {null}).isEmpty());
        assertEquals(List.of("archive.<cross-parameter>:startRequired"),
                report(executables.validateParameters(desk, archive, new Object[] {null}, Admin.class)));
        // A constructor's arguments.
        assertEquals(List.of("Desk.<cross-parameter>:startRequired"),
                report(executables.validateConstructorParameters(Desk.class.getConstructor(LocalDate.class), new Object[] {null})));
        // The rules alone make the executable constrained: its flows exist.
        assertNotNull(validator.getParametersRuleFlow(Desk.class, book));
        assertNotNull(validator.getReturnValueRuleFlow(Desk.class, label));
        assertNull(validator.getReturnValueRuleFlow(Desk.class, book));
    }

    // --- fixtures: constraints -----------------------------------------------------------------------

    public interface Admin {}

    @Constraint(validatedBy = ConsistentDatesValidator.class)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ConsistentDates {
        String message() default "end must follow start";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    public static class ConsistentDatesValidator implements ConstraintValidator<ConsistentDates, Object[]> {
        public ConsistentDatesValidator() {
            super();
        }

        @Override
        public boolean isValid(Object[] values, ConstraintValidatorContext context) {
            return values[0] == null || values[1] == null || ((LocalDate) values[0]).isBefore((LocalDate) values[1]);
        }
    }

    @Constraint(validatedBy = {EitherElement.class, EitherParameters.class})
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Either {
        String message() default "either";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        ConstraintTarget validationAppliesTo() default ConstraintTarget.IMPLICIT;
    }

    public static class EitherElement implements ConstraintValidator<Either, Object> {
        public EitherElement() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return false;
        }
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    public static class EitherParameters implements ConstraintValidator<Either, Object[]> {
        public EitherParameters() {
            super();
        }

        @Override
        public boolean isValid(Object[] values, ConstraintValidatorContext context) {
            return false;
        }
    }

    @Constraint(validatedBy = EitherElement.class)
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ElementOnly {
        String message() default "element only";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        ConstraintTarget validationAppliesTo() default ConstraintTarget.IMPLICIT;
    }

    // --- fixtures: beans -------------------------------------------------------------------------------

    public interface Service {
        Order place(@NotNull @Valid Order order, @Min(1) int quantity);
    }

    public static class Order {
        @NotNull @Size(min = 2) String code;
    }

    public static class Shipment {
        @Validate("rootSpy") String id = "x";
    }

    public static class Desk {
        String id;

        public Desk(String id) {
            super();
            this.id = id;
        }

        @Validate("startRequired")
        public Desk(LocalDate opening) {
            super();
            this.id = String.valueOf(opening);
        }

        @Validate("startRequired")
        public void book(LocalDate start, LocalDate end) {
        }

        @Validate("resultRequired")
        public String label() {
            return id;
        }

        @Validate(value = "startRequired", groups = Admin.class)
        public void archive(LocalDate when) {
        }
    }

    public static class OrderService implements Service {

        @NotNull String owner;
        String code;

        public OrderService() {
            super();
        }

        public OrderService(@NotBlank String region) {
            super();
            this.owner = region;
        }

        @Valid
        public OrderService(Order order) {
            super();
            this.code = "set";
        }

        @Override
        public @NotNull @Valid Order place(Order order, int quantity) {
            return order;
        }

        public void tag(@NotNull List<@NotBlank String> tags) {
        }

        public void batch(@Valid Order[] orders) {
        }

        public @Valid Shipment ship(@Valid Shipment shipment) {
            return shipment;
        }

        public @NotEmpty List<@NotBlank String> names() {
            return List.of();
        }

        @ConsistentDates
        public void schedule(LocalDate start, LocalDate end) {
        }

        public void archive(@Param("why") @NotNull(groups = Admin.class) String reason) {
        }

        public String plain(String value) {
            return value;
        }

        @Either(validationAppliesTo = ConstraintTarget.PARAMETERS)
        public String both(String value) {
            return value;
        }

        @Either
        public void eitherVoid(String value) {
        }

        @Either
        public String eitherReturn() {
            return "x";
        }

        @NotNull
        public String getCode() {
            return code;
        }
    }

    public static class Base {
        public void run(String value) {
        }
    }

    public static class StrengthensParameters extends Base {
        @Override
        public void run(@NotNull String value) {
        }
    }

    public static class Base2 {
        public void go(@NotNull String value) {
        }
    }

    public static class RepeatsParameters extends Base2 {
        @Override
        public void go(@NotNull String value) {
        }
    }

    public interface Left {
        void x(@NotNull String value);
    }

    public interface Right {
        void x(@NotNull String value);
    }

    public static class ParallelImplementation implements Left, Right {
        @Override
        public void x(String value) {
        }
    }

    public interface Computes {
        @NotNull String compute();
    }

    public static class AccumulatingReturn implements Computes {
        @Override
        public @NotBlank String compute() {
            return "";
        }
    }

    public static class ConstrainedVoid {
        @NotNull
        public void nothing() {
        }
    }

    public static class Ambiguous {
        @Either
        public String either(String value) {
            return value;
        }
    }

    public static class CrossWithoutParameters {
        @ConsistentDates
        public void nothing() {
        }
    }

    public static class CrossWithoutValidator {
        @ElementOnly(validationAppliesTo = ConstraintTarget.PARAMETERS)
        public void run(String value) {
        }
    }

    @Constraint(validatedBy = PointedValidator.class)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Pointed {
        String message() default "pointed";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int index();
        String property() default "";
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    public static class PointedValidator implements ConstraintValidator<Pointed, Object[]> {
        private Pointed pointed;

        public PointedValidator() {
            super();
        }

        @Override
        public void initialize(Pointed pointed) {
            this.pointed = pointed;
        }

        @Override
        public boolean isValid(Object[] values, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderDefinedContext node =
                    context.buildConstraintViolationWithTemplate("pointed").addParameterNode(pointed.index());
            if (pointed.property().isEmpty()) {
                node.addConstraintViolation();
            } else {
                node.addPropertyNode(pointed.property()).addConstraintViolation();
            }
            return false;
        }
    }

    @Constraint(validatedBy = MisplacedValidator.class)
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Misplaced {
        String message() default "misplaced";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class MisplacedValidator implements ConstraintValidator<Misplaced, String> {
        public MisplacedValidator() {
            super();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("misplaced").addParameterNode(0).addConstraintViolation();
            return false;
        }
    }

    public static class Pointing {
        @Pointed(index = 1)
        public void pair(Order order, int quantity) {
        }

        @Pointed(index = 0, property = "code")
        public void deep(Order order, int quantity) {
        }

        @Pointed(index = 5)
        public void beyond(Order order, int quantity) {
        }

        public void single(@Misplaced String value) {
        }
    }
}
