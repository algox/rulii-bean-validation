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
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.path.Path;
import org.rulii.validation.beans.traversal.TraversalState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The path-aware violation collector. Core rules deliver every violation through
 * {@link RuleViolations#add}, resolving their collector binding by type — this subclass
 * intercepts exactly that method, stamps each incoming violation with the current
 * property path from the attached {@link TraversalState}, and stores it as a
 * {@link BeanRuleViolation}. Without an attached state (a standalone or embedded use),
 * violations pass through undecorated.
 *
 * <p>Suppression frames back {@code @ReportAsSingleViolation}: while a frame is open,
 * added violations divert to the frame instead of the collection; closing the frame
 * returns them. Frames stack, so nested compositions nest.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanRuleViolations extends RuleViolations {

    private TraversalState state;
    private final Deque<List<RuleViolation>> frames = new ArrayDeque<>();

    public BeanRuleViolations() {
        super();
    }

    /**
     * Attaches the traversal state supplying the current path; done once, by the call
     * that creates the state.
     *
     * @param state the traversal state.
     */
    public void attach(TraversalState state) {
        Assert.notNull(state, "state cannot be null.");
        Assert.isTrue(this.state == null, "a TraversalState is already attached.");
        this.state = state;
    }

    /**
     * Adds a violation: diverted to the open suppression frame if one exists, otherwise
     * stamped with the current property path and collected.
     *
     * @param violation the incoming violation.
     */
    @Override
    public void add(RuleViolation violation) {
        if (!frames.isEmpty()) {
            frames.peek().add(violation);
            return;
        }

        super.add(decorate(violation));
    }

    /**
     * Opens a suppression frame; subsequent additions divert to it.
     */
    public void pushFrame() {
        frames.push(new ArrayList<>());
    }

    /**
     * Closes the innermost suppression frame.
     *
     * @return the violations diverted while the frame was open.
     */
    public List<RuleViolation> popFrame() {
        Assert.isTrue(!frames.isEmpty(), "no suppression frame is open.");
        return frames.pop();
    }

    private RuleViolation decorate(RuleViolation violation) {
        if (state == null || violation instanceof BeanRuleViolation) return violation;

        Path path = state.resolvePath(violation.getRuleName());
        Map<String, String> params = new LinkedHashMap<>(violation.getParameters());
        params.put(BeanRuleViolation.PATH_PARAMETER, path.render());

        return new BeanRuleViolation(violation.getRuleName(), violation.getErrorCode(), violation.getSeverity(),
                violation.getErrorMessage(), params, path);
    }
}
