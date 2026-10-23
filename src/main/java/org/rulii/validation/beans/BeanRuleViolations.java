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

import org.rulii.lib.spring.util.Assert;
import org.rulii.model.UnrulyException;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.MaterializedRule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * The violations report of one validation call. Core rules deliver their violations through
 * {@link #add(RuleViolation)} (they resolve the {@link RuleViolations} binding by type), and this
 * collector stamps each one with its {@link PropertyPath} and provenance before storing it as a
 * {@link BeanRuleViolation}.
 * <p>
 * The traversal tells the collector where it is through frames: {@link #enter(PropertyPath, BeanRules, Object)}
 * pushes the current path, the rules of the class in scope and the object in scope, and returns a handle
 * whose {@link Frame#close()} pops it, so the traversal writes try-with-resources;
 * {@link #enter(PropertyPath, Object)} pushes a path alone. Stamping an incoming violation then needs
 * no name parsing:
 * <ul>
 *   <li>a rule known to the frame's {@link BeanRules} with a leaf property reports at
 *       {@code framePath.property(leaf)}; a class-level rule at {@code framePath.bean()}; a container
 *       element rule at the frame path, which the walk has already extended with the element node;</li>
 *   <li>a rule unknown to the frame (a {@code @Validate} registry rule) reports at the frame path;</li>
 *   <li>with no frame open (an embedded run) the violation reports at the root path.</li>
 * </ul>
 * {@link #capture(Runnable)} diverts the violations added while a body runs, which is how a
 * {@code @ReportAsSingleViolation} composition collapses its composing violations into one.
 * <p>
 * A report is used by a single call on a single thread.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanRuleViolations extends RuleViolations {

    private final Object rootBean;
    private final Class<?> rootBeanClass;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Deque<List<BeanRuleViolation>> captures = new ArrayDeque<>();

    /**
     * A report with no known root bean, for embedded runs.
     */
    public BeanRuleViolations() {
        this(null);
    }

    /**
     * A report for a validation call starting from the given bean.
     *
     * @param rootBean the root bean; may be null when unknown.
     */
    public BeanRuleViolations(Object rootBean) {
        this(rootBean, rootBean != null ? rootBean.getClass() : null);
    }

    /**
     * A report for a validation call starting from the given bean, or from a value offered for a
     * property of the given class when there is no bean.
     *
     * @param rootBean the root bean; may be null.
     * @param rootBeanClass the root bean class; may be null when unknown.
     */
    public BeanRuleViolations(Object rootBean, Class<?> rootBeanClass) {
        super();
        this.rootBean = rootBean;
        this.rootBeanClass = rootBeanClass;
    }

    /**
     * The object validation started from.
     *
     * @return the root bean; null when unknown.
     */
    public Object getRootBean() {
        return rootBean;
    }

    /**
     * The class validation started from.
     *
     * @return the root bean class; null when unknown.
     */
    public Class<?> getRootBeanClass() {
        return rootBeanClass;
    }

    /**
     * The path of the innermost open frame.
     *
     * @return the current path; the root path when no frame is open.
     */
    public PropertyPath getCurrentPath() {
        Frame frame = frames.peek();
        return frame != null ? frame.getPath() : PropertyPath.root();
    }

    /**
     * The violations, each stamped.
     *
     * @return the violations in the order they were reported.
     */
    public List<BeanRuleViolation> getBeanViolations() {
        List<BeanRuleViolation> result = new ArrayList<>(size());
        for (RuleViolation violation : getViolations()) {
            result.add((BeanRuleViolation) violation);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Opens a frame. The traversal opens one per object descended into, per container element walked,
     * and around each {@code @Validate} run, always with the path already extended to that point.
     *
     * @param path the current path.
     * @param rules the rules of the class in scope, consulted to place known rules.
     * @param bean the object in scope; may be null.
     * @return the frame; close it to pop.
     */
    public Frame enter(PropertyPath path, BeanRules rules, Object bean) {
        Assert.notNull(path, "path cannot be null.");
        Assert.notNull(rules, "rules cannot be null.");
        Frame frame = new Frame(path, rules, bean);
        frames.push(frame);
        return frame;
    }

    /**
     * Opens a frame that only moves the current path: around a descent into an object, a
     * container element, or a {@code @Validate} run. No rules are known to it, so every violation
     * added while it is innermost reports at its path with no provenance.
     *
     * @param path the current path.
     * @param bean the object in scope; may be null.
     * @return the frame; close it to pop.
     */
    public Frame enter(PropertyPath path, Object bean) {
        Assert.notNull(path, "path cannot be null.");
        Frame frame = new Frame(path, null, bean);
        frames.push(frame);
        return frame;
    }

    /**
     * Runs the body and returns the violations it added instead of storing them. Nested captures
     * stack: a violation added inside the inner body goes to the inner capture only.
     *
     * @param body what to run.
     * @return the violations added while the body ran, stamped.
     */
    public List<BeanRuleViolation> capture(Runnable body) {
        Assert.notNull(body, "body cannot be null.");
        List<BeanRuleViolation> captured = new ArrayList<>();
        captures.push(captured);
        try {
            body.run();
        } finally {
            captures.pop();
        }
        return Collections.unmodifiableList(captured);
    }

    /**
     * Stamps the violation and stores it, or diverts it to the open capture.
     *
     * @param error the violation as reported.
     */
    @Override
    public void add(RuleViolation error) {
        Assert.notNull(error, "error cannot be null.");
        BeanRuleViolation stamped = error instanceof BeanRuleViolation ? (BeanRuleViolation) error : stamp(error);
        List<BeanRuleViolation> capture = captures.peek();
        if (capture != null) {
            capture.add(stamped);
            return;
        }
        super.add(stamped);
    }

    private BeanRuleViolation stamp(RuleViolation error) {
        Frame frame = frames.peek();
        if (frame == null) {
            return new BeanRuleViolation(error, PropertyPath.root(), null, null, null, rootBean, rootBeanClass);
        }
        MaterializedRule rule = frame.getRules() != null ? frame.getRules().getRule(error.getRuleName()) : null;
        if (rule == null) {
            return new BeanRuleViolation(error, frame.getPath(), null, null, frame.getBean(), rootBean, rootBeanClass);
        }
        PropertyPath path = frame.getPath();
        if (rule.getLeaf() != null) {
            path = path.property(rule.getLeaf());
        } else if (rule.getTarget().isClassLevel()) {
            path = path.bean();
        }
        Object invalidValue = error.getParameterValues().get(rule.getTarget().getValueName());
        return new BeanRuleViolation(error, path, rule.getDefinition(), invalidValue, frame.getBean(), rootBean, rootBeanClass);
    }

    /**
     * An open frame: where the traversal currently is. Closing it pops it; frames must close in the
     * order they were opened.
     */
    public final class Frame implements AutoCloseable {

        private final PropertyPath path;
        private final BeanRules rules;
        private final Object bean;

        private Frame(PropertyPath path, BeanRules rules, Object bean) {
            super();
            this.path = path;
            this.rules = rules;
            this.bean = bean;
        }

        public PropertyPath getPath() {
            return path;
        }

        /**
         * The rules of the class in scope.
         *
         * @return the rules; null for a path-only frame.
         */
        public BeanRules getRules() {
            return rules;
        }

        public Object getBean() {
            return bean;
        }

        @Override
        public void close() {
            if (frames.peek() != this) {
                throw new UnrulyException("frame [" + path + "] is not the innermost open frame; frames must close in order.");
            }
            frames.pop();
        }

        @Override
        public String toString() {
            return "Frame{path='" + path + "'" + (rules != null ? ", beanClass=" + rules.getBeanClass().getName() : "") + '}';
        }
    }
}
