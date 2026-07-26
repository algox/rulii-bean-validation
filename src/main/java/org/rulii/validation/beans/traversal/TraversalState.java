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

import org.rulii.bind.NamedScope;
import org.rulii.bind.ScopedBindings;
import org.rulii.context.RuleContext;
import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Runnable;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.assembly.ClassValidationModel;
import org.rulii.validation.beans.assembly.MaterializedContainer;
import org.rulii.validation.beans.assembly.MaterializedElement;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.path.Path;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The per-call traversal engine: identity cycle set (validate-once-per-call), the
 * current property path, the frame-stacked rule-name-to-leaf tables, the expanded
 * requested groups, and the runtime-class model lookup. Created fresh by every
 * {@code validate(...)} call (and by the flow-param default for embedded runs) — never
 * cached, never shared across calls.
 *
 * <p>Its services are called from flow steps: {@link #descend} runs a cascaded child's
 * flow while the parent scope is open (R16.3), {@link #walkContainer} iterates container
 * elements running their materialized rules, {@link #runRegistryRules} dispatches
 * {@code @Validate} declarations with path attribution.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class TraversalState {

    public static final String THIS_BINDING = "$this";
    public static final String ROOT_BINDING = "$root";
    public static final String PARENT_BINDING = "$parent";
    public static final String ELEMENT_BINDING = "$element";
    public static final String KEY_BINDING = "$key";
    public static final String VALUE_BINDING = "$value";

    private final Function<Class<?>, ClassValidationModel> modelLookup;
    private final Set<Class<?>> expandedGroups;
    private final Set<Object> validated = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<Map<String, String>> leafTables = new ArrayDeque<>();
    private Path currentPath = Path.root();

    /**
     * Creates a fresh per-call state.
     *
     * @param modelLookup runtime-class to per-class model lookup (the validator cache).
     * @param expandedGroups the call's expanded requested groups.
     * @param collector the call's collector; attached for path stamping (null for runs
     *                  collecting into a plain RuleViolations — undecorated).
     */
    public TraversalState(Function<Class<?>, ClassValidationModel> modelLookup, Set<Class<?>> expandedGroups,
                          BeanRuleViolations collector) {
        super();
        Assert.notNull(modelLookup, "modelLookup cannot be null.");
        Assert.notNull(expandedGroups, "expandedGroups cannot be null.");
        this.modelLookup = modelLookup;
        this.expandedGroups = expandedGroups;
        if (collector != null) collector.attach(this);
    }

    /**
     * Retrieves the call's expanded requested groups — the single source of truth for
     * all group filtering.
     *
     * @return an unmodifiable set of groups.
     */
    public Set<Class<?>> getExpandedGroups() {
        return expandedGroups;
    }

    /**
     * Marks an object as validated (cycle set). The entry point marks the root; descent
     * marks every child before running its flow.
     *
     * @param bean the object being validated.
     * @return true if the object was not validated before; false if already seen.
     */
    public boolean markValidated(Object bean) {
        Assert.notNull(bean, "bean cannot be null.");
        return validated.add(bean);
    }

    /**
     * Pushes the rule-name-to-leaf table of the class whose flow is about to run.
     *
     * @param leafTable the class's leaf table.
     */
    public void pushObjectFrame(Map<String, String> leafTable) {
        Assert.notNull(leafTable, "leafTable cannot be null.");
        leafTables.push(leafTable);
    }

    /**
     * Pops the current class's leaf table.
     */
    public void popObjectFrame() {
        leafTables.pop();
    }

    /**
     * Resolves the path a violation from the given rule reports at: the current path
     * plus the rule's leaf property when the current class's table knows the rule.
     *
     * @param ruleName the reporting rule's name.
     * @return the violation path.
     */
    public Path resolvePath(String ruleName) {
        Map<String, String> table = leafTables.peek();
        String leaf = table != null ? table.get(ruleName) : null;
        return leaf != null ? currentPath.property(leaf) : currentPath;
    }

    /**
     * Cascades into a child value: null is a no-op (cascading never implies
     * {@code @NotNull}, R5.5); collections, maps, and arrays cascade per element;
     * anything else is validated once per call (identity cycle set) via its runtime
     * class's flow, run while the parent scope is open.
     *
     * @param ruleContext the live context.
     * @param propertyName the property being descended into; null when the path is
     *                     already positioned (container-element cascades).
     * @param value the child value.
     */
    public void descend(RuleContext ruleContext, String propertyName, Object value) {
        if (value == null) return;

        Path saved = currentPath;
        if (propertyName != null) currentPath = currentPath.property(propertyName);

        try {
            descendValue(ruleContext, value);
        } finally {
            currentPath = saved;
        }
    }

    /**
     * Walks a container property: runs the group-filtered element rules per position
     * and cascades marked positions, in a single pass (S4.5).
     *
     * @param ruleContext the live context.
     * @param propertyName the container property; null when the path is already
     *                     positioned (nested containers).
     * @param value the container value; null is a no-op.
     * @param container the materialized container tree.
     */
    public void walkContainer(RuleContext ruleContext, String propertyName, Object value, MaterializedContainer container) {
        if (value == null) return;

        Path saved = currentPath;
        if (propertyName != null) currentPath = currentPath.property(propertyName);

        try {
            walkValue(ruleContext, value, container);
        } finally {
            currentPath = saved;
        }
    }

    /**
     * Dispatches {@code @Validate} registry rules. Property-level declarations push the
     * property path node and open an anonymous scope binding the property value as
     * {@code $value} (S7b); class-level declarations run at the current object path.
     *
     * @param ruleContext the live context.
     * @param beanClass the declaring bean class (error reporting).
     * @param propertyName the declaring property; null for class-level declarations.
     * @param valueBindingName the property's scope binding; null for class-level.
     * @param ruleNames the registry rule names, in declaration order.
     */
    public void runRegistryRules(RuleContext ruleContext, Class<?> beanClass, String propertyName,
                                 String valueBindingName, List<String> ruleNames) {
        if (propertyName == null) {
            for (String ruleName : ruleNames) {
                resolveAndRun(ruleContext, beanClass, null, ruleName);
            }
            return;
        }

        ScopedBindings bindings = (ScopedBindings) ruleContext.getBindings();
        Path saved = currentPath;
        currentPath = currentPath.property(propertyName);
        NamedScope scope = bindings.addScope();

        try {
            bindings.bind(VALUE_BINDING, Object.class, bindings.getValue(valueBindingName));
            for (String ruleName : ruleNames) {
                resolveAndRun(ruleContext, beanClass, propertyName, ruleName);
            }
        } finally {
            bindings.removeScope(scope);
            currentPath = saved;
        }
    }

    // ------------------------------------------------------------------------
    // Descent internals
    // ------------------------------------------------------------------------

    private void descendValue(RuleContext ruleContext, Object value) {
        if (value == null) return;

        if (value instanceof Optional<?> optional) {
            optional.ifPresent(element -> descendValue(ruleContext, element));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                descendElement(ruleContext, currentPath.index(i), list.get(i));
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                descendElement(ruleContext, currentPath.iterable(), element);
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                descendElement(ruleContext, currentPath.key(entry.getKey()), entry.getValue());
            }
        } else if (value instanceof Object[] array) {
            for (int i = 0; i < array.length; i++) {
                descendElement(ruleContext, currentPath.index(i), array[i]);
            }
        } else if (value.getClass().isArray()) {
            // Primitive arrays carry no beans to descend into.
        } else {
            descendObject(ruleContext, value);
        }
    }

    private void descendElement(RuleContext ruleContext, Path elementPath, Object element) {
        Path saved = currentPath;
        currentPath = elementPath;

        try {
            descendValue(ruleContext, element);
        } finally {
            currentPath = saved;
        }
    }

    private void descendObject(RuleContext ruleContext, Object bean) {
        // Platform types carry no constraint metadata and their internals are not
        // reflectively accessible on modern JVMs (mirrors the reader's eager-read skip).
        // Also absorbs @Valid mirrored onto array components by the JLS: cascading a
        // boxed primitive or String must be a no-op, not a metadata read of java.base.
        String packageName = bean.getClass().getPackageName();
        if (packageName.startsWith("java.") || packageName.startsWith("javax.")
                || packageName.startsWith("jakarta.")) {
            return;
        }

        if (!markValidated(bean)) return;

        ClassValidationModel model = modelLookup.apply(bean.getClass());
        ScopedBindings bindings = (ScopedBindings) ruleContext.getBindings();
        Object parent = bindings.getValue(THIS_BINDING);
        NamedScope scope = bindings.addScope();
        pushObjectFrame(model.getLeafTable());

        try {
            bindings.bind(THIS_BINDING, Object.class, bean);
            bindings.bind(PARENT_BINDING, Object.class, parent);
            model.getFlow().run(ruleContext);
        } finally {
            popObjectFrame();
            bindings.removeScope(scope);
        }
    }

    // ------------------------------------------------------------------------
    // Container-walk internals
    // ------------------------------------------------------------------------

    private void walkValue(RuleContext ruleContext, Object value, MaterializedContainer container) {
        if (value == null) return;

        switch (container.getKind()) {
            case OPTIONAL -> {
                // Optional unwraps in place — no path node (spec).
                if (value instanceof Optional<?> optional) {
                    runElement(ruleContext, currentPath, optional.orElse(null), ELEMENT_BINDING, container.getElement());
                }
            }
            case COLLECTION -> {
                if (value instanceof List<?> list) {
                    for (int i = 0; i < list.size(); i++) {
                        runElement(ruleContext, currentPath.index(i), list.get(i), ELEMENT_BINDING, container.getElement());
                    }
                } else if (value instanceof Iterable<?> iterable) {
                    for (Object element : iterable) {
                        runElement(ruleContext, currentPath.iterable(), element, ELEMENT_BINDING, container.getElement());
                    }
                }
            }
            case ARRAY -> {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    runElement(ruleContext, currentPath.index(i), Array.get(value, i), ELEMENT_BINDING, container.getElement());
                }
            }
            case MAP -> {
                if (value instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        walkMapEntry(ruleContext, entry, container);
                    }
                }
            }
        }
    }

    private void walkMapEntry(RuleContext ruleContext, Map.Entry<?, ?> entry, MaterializedContainer container) {
        runElement(ruleContext, currentPath.key(entry.getKey()), entry.getKey(), KEY_BINDING, container.getKeyElement());
        runElement(ruleContext, currentPath.key(entry.getKey()), entry.getValue(), VALUE_BINDING, container.getValueElement());
    }

    private void runElement(RuleContext ruleContext, Path elementPath, Object element, String bindingName,
                            MaterializedElement spec) {
        if (spec == null) return;

        ScopedBindings bindings = (ScopedBindings) ruleContext.getBindings();
        Path saved = currentPath;
        currentPath = elementPath;
        NamedScope scope = bindings.addScope();

        try {
            bindings.bind(bindingName, Object.class, element);

            for (MaterializedRule rule : spec.getRules()) {
                if (rule.isActive(expandedGroups)) rule.getRule().run(ruleContext);
            }

            if (spec.getNested() != null && element != null) walkValue(ruleContext, element, spec.getNested());
            if (spec.isCascaded()) descend(ruleContext, null, element);
        } finally {
            bindings.removeScope(scope);
            currentPath = saved;
        }
    }

    private void resolveAndRun(RuleContext ruleContext, Class<?> beanClass, String propertyName, String ruleName) {
        Runnable<?> target = ruleContext.getRuleRegistry().get(ruleName);
        if (target == null) throw new ValidateRuleNotFoundException(beanClass, propertyName, ruleName);
        target.run(ruleContext);
    }

    @Override
    public String toString() {
        return "TraversalState{validated=" + validated.size()
                + ", currentPath=" + currentPath.render()
                + ", groups=" + expandedGroups.size()
                + '}';
    }
}
