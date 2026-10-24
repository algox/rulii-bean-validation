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
package org.rulii.validation.beans.validator;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ElementKind;
import jakarta.validation.ValidationException;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The {@link ConstraintValidatorContext} a {@link ConstraintValidatorRule} hands to its validator for
 * one evaluation. It records what the validator asked for, the rule reports it afterwards: whether the
 * default violation is disabled, and the custom violations built through
 * {@link #buildConstraintViolationWithTemplate(String)}, each with its message template and the path
 * nodes added to it.
 *
 * <p>The violation builder covers the specification's node family: property, bean and container element
 * nodes, {@code inIterable}/{@code inContainer}/{@code atKey}/{@code atIndex} on the node last added.
 * The subscript of a node that is "in an iterable" renders on the node before it, as the reference
 * implementation renders {@code address[0].street}; on a bare root path it is dropped.
 * {@code inContainer} alone records nothing, since a subscript here always renders brackets.
 * {@code addParameterNode} belongs to executable validation and throws. The message expressions of a
 * template built here are not evaluated unless its builder's {@link ViolationBuilder#enableExpressionLanguage()}
 * was called.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiConstraintValidatorContext implements ConstraintValidatorContext {

    private final RuleContext ruleContext;
    private final String defaultConstraintMessageTemplate;
    private final List<Violation> violations = new ArrayList<>();
    private boolean defaultConstraintViolationDisabled;

    /**
     * Creates the context for one evaluation.
     *
     * @param ruleContext the rulii context the rule runs in.
     * @param defaultConstraintMessageTemplate the constraint's message template, as declared.
     */
    public RuliiConstraintValidatorContext(RuleContext ruleContext, String defaultConstraintMessageTemplate) {
        super();
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.notNull(defaultConstraintMessageTemplate, "defaultConstraintMessageTemplate cannot be null.");
        this.ruleContext = ruleContext;
        this.defaultConstraintMessageTemplate = defaultConstraintMessageTemplate;
    }

    @Override
    public void disableDefaultConstraintViolation() {
        this.defaultConstraintViolationDisabled = true;
    }

    @Override
    public String getDefaultConstraintMessageTemplate() {
        return defaultConstraintMessageTemplate;
    }

    /**
     * The clock of the rulii context.
     */
    @Override
    public ClockProvider getClockProvider() {
        return ruleContext::getClock;
    }

    @Override
    public ViolationBuilder buildConstraintViolationWithTemplate(String messageTemplate) {
        Assert.notNull(messageTemplate, "messageTemplate cannot be null.");
        return new ViolationBuilder(messageTemplate);
    }

    /**
     * This context, or the rulii context it wraps, viewed as the given type.
     *
     * @throws ValidationException if neither is of that type.
     */
    @Override
    public <T> T unwrap(Class<T> type) {
        Assert.notNull(type, "type cannot be null.");
        if (type.isInstance(this)) return type.cast(this);
        if (type.isInstance(ruleContext)) return type.cast(ruleContext);
        throw new ValidationException("cannot unwrap [" + getClass().getName() + "] to [" + type.getName() + "]");
    }

    public RuleContext getRuleContext() {
        return ruleContext;
    }

    /**
     * Whether the validator disabled the default violation.
     *
     * @return true if disabled.
     */
    public boolean isDefaultConstraintViolationDisabled() {
        return defaultConstraintViolationDisabled;
    }

    /**
     * The custom violations the validator built, in order.
     *
     * @return the violations; unmodifiable.
     */
    public List<Violation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    @Override
    public String toString() {
        return "RuliiConstraintValidatorContext{defaultDisabled=" + defaultConstraintViolationDisabled
                + ", violations=" + violations.size() + '}';
    }

    /**
     * A custom violation: its message template, how the rule's path extends to where it occurred
     * (the identity when no node was added), and whether its message expressions may be evaluated.
     *
     * @param messageTemplate the template, as given to the builder.
     * @param pathExtension the path extension.
     * @param expressionLanguageEnabled whether {@code ${...}} expressions of the template are evaluated.
     */
    public record Violation(String messageTemplate, UnaryOperator<PropertyPath> pathExtension, boolean expressionLanguageEnabled) {}

    /**
     * A pending node of a custom violation's path.
     */
    private static final class Node {

        private final ElementKind kind;
        private final String name;
        private Class<?> containerClass;
        private Integer typeArgumentIndex;
        private boolean inIterable;
        private Object key;
        private Integer index;

        private Node(ElementKind kind, String name, Class<?> containerClass, Integer typeArgumentIndex) {
            super();
            this.kind = kind;
            this.name = name;
            this.containerClass = containerClass;
            this.typeArgumentIndex = typeArgumentIndex;
        }

        private PropertyPath appendTo(PropertyPath path) {
            PropertyPath result = path;

            if (inIterable && !result.isRoot()) {
                int argument = typeArgumentIndex != null ? typeArgumentIndex : ValueExtractorDescriptor.CONTAINER;
                if (key != null) {
                    result = result.withKey(key, containerClass, argument);
                } else if (index != null) {
                    result = result.withIndex(index, containerClass, argument);
                } else {
                    result = result.inIterable(containerClass, argument);
                }
            }

            return switch (kind) {
                case PROPERTY -> result.property(name);
                case CONTAINER_ELEMENT -> result.element(name);
                default -> result.bean();
            };
        }
    }

    /**
     * The one builder behind every step of the specification's violation building: it collects nodes
     * and commits the violation on {@link #addConstraintViolation()}.
     */
    public final class ViolationBuilder implements ConstraintViolationBuilder,
            ConstraintViolationBuilder.NodeBuilderDefinedContext,
            ConstraintViolationBuilder.NodeBuilderCustomizableContext,
            ConstraintViolationBuilder.NodeContextBuilder,
            ConstraintViolationBuilder.LeafNodeBuilderDefinedContext,
            ConstraintViolationBuilder.LeafNodeBuilderCustomizableContext,
            ConstraintViolationBuilder.LeafNodeContextBuilder,
            ConstraintViolationBuilder.ContainerElementNodeBuilderDefinedContext,
            ConstraintViolationBuilder.ContainerElementNodeBuilderCustomizableContext,
            ConstraintViolationBuilder.ContainerElementNodeContextBuilder {

        private final String messageTemplate;
        private final List<Node> nodes = new ArrayList<>();
        private boolean added;
        private boolean expressionLanguageEnabled;

        private ViolationBuilder(String messageTemplate) {
            super();
            this.messageTemplate = messageTemplate;
        }

        /**
         * Lets the message expressions ({@code ${...}}) of this template be evaluated. Off by default,
         * as the specification has it since 3.0: a template built at run time may carry user input,
         * which an expression could execute. A validator building its template from constants may turn
         * it on, the way the reference implementation's own builder offers it.
         *
         * @return this builder.
         */
        public ViolationBuilder enableExpressionLanguage() {
            this.expressionLanguageEnabled = true;
            return this;
        }

        /**
         * @deprecated as in the specification; same as {@link #addPropertyNode(String)}.
         */
        @Override
        @Deprecated
        public ViolationBuilder addNode(String name) {
            return addPropertyNode(name);
        }

        @Override
        public ViolationBuilder addPropertyNode(String name) {
            Assert.hasText(name, "name cannot be null/empty.");
            return add(new Node(ElementKind.PROPERTY, name, null, null));
        }

        @Override
        public ViolationBuilder addBeanNode() {
            return add(new Node(ElementKind.BEAN, null, null, null));
        }

        @Override
        public ViolationBuilder addContainerElementNode(String name, Class<?> containerType, Integer typeArgumentIndex) {
            Assert.hasText(name, "name cannot be null/empty.");
            return add(new Node(ElementKind.CONTAINER_ELEMENT, name, containerType, typeArgumentIndex));
        }

        /**
         * @throws ValidationException always: parameter nodes belong to executable validation.
         */
        @Override
        public ViolationBuilder addParameterNode(int index) {
            throw new ValidationException("parameter nodes belong to executable (method/constructor) validation, which is not supported by rulii bean validation.");
        }

        @Override
        public ViolationBuilder inIterable() {
            current().inIterable = true;
            return this;
        }

        @Override
        public ViolationBuilder inContainer(Class<?> containerClass, Integer typeArgumentIndex) {
            Node node = current();
            node.containerClass = containerClass;
            node.typeArgumentIndex = typeArgumentIndex;
            return this;
        }

        @Override
        public ViolationBuilder atKey(Object key) {
            Node node = current();
            node.inIterable = true;
            node.key = key;
            return this;
        }

        @Override
        public ViolationBuilder atIndex(Integer index) {
            Node node = current();
            node.inIterable = true;
            node.index = index;
            return this;
        }

        /**
         * Commits the violation to the context.
         *
         * @return the context.
         * @throws IllegalStateException if the violation was already committed.
         */
        @Override
        public RuliiConstraintValidatorContext addConstraintViolation() {
            if (added) throw new IllegalStateException("the constraint violation was already added.");
            added = true;
            List<Node> path = List.copyOf(nodes);
            violations.add(new Violation(messageTemplate, base -> {
                PropertyPath result = base;
                for (Node node : path) {
                    result = node.appendTo(result);
                }
                return result;
            }, expressionLanguageEnabled));
            return RuliiConstraintValidatorContext.this;
        }

        private ViolationBuilder add(Node node) {
            nodes.add(node);
            return this;
        }

        private Node current() {
            Assert.isTrue(!nodes.isEmpty(), "add a node before describing it.");
            return nodes.get(nodes.size() - 1);
        }

        @Override
        public String toString() {
            return "ViolationBuilder{" + messageTemplate + ", nodes=" + nodes.size() + '}';
        }
    }
}
