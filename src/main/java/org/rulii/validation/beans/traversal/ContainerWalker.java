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
package org.rulii.validation.beans.traversal;

import jakarta.validation.ValidationException;
import jakarta.validation.valueextraction.ValueExtractor;
import org.rulii.bind.Binding;
import org.rulii.bind.NamedScope;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.assembly.BindingPlan;
import org.rulii.validation.beans.assembly.MaterializedRules;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.util.reflect.Types;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Walks the containers of the object in scope with the spec's value extractors, within one flow run.
 *
 * <p>A declared container element position ({@code List<@NotBlank String>}) is walked with the
 * extractor resolved for it at load time. Per element the path is subscripted on the property node
 * ({@code tags[2]}, {@code contacts[home]}, {@code tags[]}), the element is bound in a scope of its
 * own under the name its rules read ({@code $element}, {@code $key} or {@code $value}, plus
 * {@code $key} beside a keyed value), the position's rules run at the element node
 * ({@code tags[2].<list element>}), a cascaded element descends at the subscripted path without the
 * element node, and nested positions walk on from the element node. An extractor serving the
 * container value itself ({@code Optional}) adds no node. This is how the reference implementation renders
 * these paths.</p>
 *
 * <p>{@code @Valid} on a container-typed property ({@code @Valid List<Order>}) is the spec's legacy
 * form of element cascading: the elements descend as beans, {@code Map} through its values. The
 * extractor is chosen by the declared type, or by the runtime type when the declared type is not a
 * known container.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ContainerWalker {

    private final RuleContext ruleContext;
    private final MaterializedRules rules;
    private final boolean cascading;
    private final Traversal traversal;
    private final BeanRuleViolations collector;
    private final Object bean;

    /**
     * Creates the walker of one run. The context must have {@code $this}, a {@link Traversal} and a
     * {@link BeanRuleViolations} bound.
     *
     * @param ruleContext the context of the run.
     * @param rules the rules of the class or executable in scope.
     * @param cascading whether cascaded elements descend; false validates the elements' own constraints only.
     */
    public ContainerWalker(RuleContext ruleContext, MaterializedRules rules, boolean cascading) {
        super();
        Assert.notNull(ruleContext, "ruleContext cannot be null.");
        Assert.notNull(rules, "rules cannot be null.");
        this.ruleContext = ruleContext;
        this.rules = rules;
        this.cascading = cascading;
        this.traversal = Traversal.of(ruleContext);
        this.collector = Traversal.collector(ruleContext);
        Assert.notNull(collector, "the container walk needs a path-aware collector bound as ["
                + BindingPlan.RULE_VIOLATIONS + "]; run through the bean flow.");
        this.bean = ruleContext.getBindings().getValue(BindingPlan.THIS);
    }

    /**
     * Walks a declared container element position of a property.
     *
     * @param container the property value, or the enclosing element for a nested position; null walks nothing.
     * @param node the position.
     * @param base the path of the container: the property path, or the element node of the enclosing position.
     */
    public void walk(Object container, ContainerElementDefinition node, PropertyPath base) {
        Assert.notNull(node, "node cannot be null.");
        Assert.notNull(base, "base cannot be null.");

        if (container == null) return;

        ValueExtractorDescriptor descriptor = node.getExtractor();
        Assert.notNull(descriptor, "container element [" + node.getName() + "] of [" + node.getSourceDeclaration().describe()
                + "] has no extractor.");

        extract(descriptor, container, node, new Receiver(base, descriptor) {
            @Override
            void on(PropertyPath path, String nodeName, boolean keyed, Object key, Object value) {
                element(node, path, nodeName, keyed, key, value);
            }
        });
    }

    private void element(ContainerElementDefinition node, PropertyPath path, String nodeName, boolean keyed, Object key, Object value) {
        PropertyPath elementPath = nodeName != null ? path.element(nodeName) : path;
        String binding = BindingPlan.elementBinding(node);
        NamedScope scope = ruleContext.getBindings().addScope();

        try {
            scope.getBindings().bind(Binding.builder().with(binding, Object.class).value(value).build());
            if (keyed && !BindingPlan.KEY.equals(binding)) {
                scope.getBindings().bind(Binding.builder().with(BindingPlan.KEY, Object.class).value(key).build());
            }

            if (rules.hasElementRules(node)) {
                try (BeanRuleViolations.Frame ignored = collector.enter(elementPath, rules, bean)) {
                    traversal.elementRuleSet(rules, node).run(ruleContext);
                }
            }

            if (value == null) return;

            if (cascading && node.isCascaded()) traversal.descend(ruleContext, value, path);

            for (ContainerElementDefinition child : node.getChildren()) {
                walk(value, child, elementPath);
            }
        } finally {
            ruleContext.getBindings().removeScope(scope);
        }
    }

    /**
     * Cascades a {@code @Valid} property value: into the elements when the value is a container,
     * into the value itself otherwise.
     *
     * @param value the property value; null cascades nothing.
     * @param declaredType the declared type of the property.
     * @param path the property path.
     */
    public void cascade(Object value, Type declaredType, PropertyPath path) {
        Assert.notNull(declaredType, "declaredType cannot be null.");
        Assert.notNull(path, "path cannot be null.");
        Assert.isTrue(cascading, "this walker does not cascade.");

        if (value == null) return;

        ValueExtractorDescriptor descriptor = legacyExtractor(Types.erasure(declaredType));

        if (descriptor == null) descriptor = legacyExtractor(value.getClass());

        if (descriptor == null) {
            traversal.descend(ruleContext, value, path);
            return;
        }

        extract(descriptor, value, null, new Receiver(path, descriptor) {
            @Override
            void on(PropertyPath elementPath, String nodeName, boolean keyed, Object key, Object element) {
                traversal.descend(ruleContext, element, elementPath);
            }
        });
    }

    /**
     * The extractor a {@code @Valid} container cascades through: the values of a {@code Map},
     * otherwise the single position served for the type; null when the type is not a container.
     */
    private ValueExtractorDescriptor legacyExtractor(Class<?> type) {
        List<ValueExtractorDescriptor> candidates = traversal.getExtractors().find(type);

        if (candidates.isEmpty()) return null;

        if (Map.class.isAssignableFrom(type)) {
            for (ValueExtractorDescriptor candidate : candidates) {
                if (Map.class.isAssignableFrom(candidate.getContainerType()) && candidate.getTypeArgumentIndex() == 1) {
                    return candidate;
                }
            }
            return null;
        }

        if (candidates.size() > 1) {
            throw new UnrulyException("@Valid on a [" + type.getName() + "] is ambiguous: the type has several extractable positions "
                    + candidates + ". Declare @Valid on the type argument to cascade into.");
        }

        return candidates.get(0);
    }

    @SuppressWarnings("unchecked")
    private static void extract(ValueExtractorDescriptor descriptor, Object container, ContainerElementDefinition node, Receiver receiver) {
        try {
            ((ValueExtractor<Object>) descriptor.getExtractor()).extractValues(container, receiver);
        } catch (ValidationException | UnrulyException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UnrulyException("value extractor [" + descriptor.getExtractor().getClass().getName() + "] failed on ["
                    + container.getClass().getName() + "]" + (node != null ? " at " + node.getSourceDeclaration().describe()
                    + " " + node.getName() : "") + ": " + e.getMessage(), e);
        }
    }

    /**
     * Turns the extractor callbacks into subscripted paths: the subscript lands on the container's
     * own node, the element node (if the extractor names one) follows.
     */
    private abstract static class Receiver implements ValueExtractor.ValueReceiver {

        private final PropertyPath base;
        private final Class<?> containerClass;
        private final int typeArgumentIndex;

        private Receiver(PropertyPath base, ValueExtractorDescriptor descriptor) {
            super();
            this.base = base;
            this.containerClass = descriptor.getContainerType();
            this.typeArgumentIndex = descriptor.getTypeArgumentIndex();
        }

        abstract void on(PropertyPath path, String nodeName, boolean keyed, Object key, Object value);

        @Override
        public void value(String nodeName, Object object) {
            on(base, nodeName, false, null, object);
        }

        @Override
        public void iterableValue(String nodeName, Object object) {
            on(base.inIterable(containerClass, typeArgumentIndex), nodeName, false, null, object);
        }

        @Override
        public void indexedValue(String nodeName, int i, Object object) {
            on(base.withIndex(i, containerClass, typeArgumentIndex), nodeName, false, null, object);
        }

        @Override
        public void keyedValue(String nodeName, Object key, Object object) {
            on(base.withKey(key, containerClass, typeArgumentIndex), nodeName, true, key, object);
        }
    }
}
