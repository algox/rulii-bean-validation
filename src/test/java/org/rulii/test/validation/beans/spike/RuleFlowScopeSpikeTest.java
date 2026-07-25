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
package org.rulii.test.validation.beans.spike;

import org.junit.jupiter.api.Test;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.model.UnrulyException;
import org.rulii.ruleflow.RuleFlow;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.rulii.model.action.Actions.action;
import static org.rulii.model.function.Functions.function;

/**
 * Verification spikes pinning the RuleFlow scope and parameter behaviors the solution
 * design depends on (SOLUTION S3 verify-at-impl items 2 and 3): scope visibility across
 * nested flow runs, exception-safe scope pop, and param defaults vs bindings arriving
 * from outer scopes.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class RuleFlowScopeSpikeTest {

    public RuleFlowScopeSpikeTest() {
        super();
    }

    // -----------------------------------------------------------------------
    // Nested flows and scopes (S3 in-flow descent, R16.3)
    // -----------------------------------------------------------------------

    @Test
    public void nestedFlowSeesOuterFlowScopeBindings() {
        List<String> captured = new ArrayList<>();

        RuleFlow<?> innerFlow = RuleFlow.builder()
                .name("innerFlow")
                .scope("innerScope", f -> f
                        .bind("y", "fromInner")
                        .execute(action((String x, String y) -> captured.add(x + ":" + y))))
                .build();

        RuleFlow<?> outerFlow = RuleFlow.builder()
                .name("outerFlow")
                .scope("outerScope", f -> f
                        .bind("x", "fromOuter")
                        .run(innerFlow))
                .build();

        outerFlow.run();
        // Innermost-outward lookup must cross the nested-flow boundary (R16.3).
        assertEquals(List.of("fromOuter:fromInner"), captured);
    }

    @Test
    public void scopePoppedAfterRun() {
        ScopedBindings bindings = Bindings.builder().scoped();

        RuleFlow<?> flow = RuleFlow.builder()
                .name("scopeFlow")
                .scope("objectScope", f -> f
                        .bind("x", "value")
                        .execute(action((String x) -> { })))
                .build();

        flow.run(bindings);
        assertFalse(bindings.contains("x"));
    }

    @Test
    public void scopePoppedOnException() {
        ScopedBindings bindings = Bindings.builder().scoped();

        RuleFlow<?> flow = RuleFlow.builder()
                .name("failingScopeFlow")
                .scope("objectScope", f -> f
                        .bind("x", "value")
                        .execute(action((String x) -> {
                            throw new IllegalStateException("boom");
                        })))
                .build();

        assertThrows(UnrulyException.class, () -> flow.run(bindings));
        // User-supplied contexts must never be left dirty (R16.1).
        assertFalse(bindings.contains("x"));
    }

    // -----------------------------------------------------------------------
    // Param defaults vs outer bindings (S3 verify-at-impl item 3)
    // -----------------------------------------------------------------------

    @Test
    public void paramDefaultAppliedWhenBindingAbsent() {
        List<String> captured = new ArrayList<>();

        RuleFlow<?> flow = RuleFlow.builder()
                .name("paramFlow")
                .param("p", String.class, function(() -> "defaultValue"))
                .execute(action((String p) -> captured.add(p)))
                .build();

        flow.run();
        assertEquals(List.of("defaultValue"), captured);
    }

    @Test
    public void suppliedBindingWinsOverParamDefault() {
        List<String> captured = new ArrayList<>();

        RuleFlow<?> flow = RuleFlow.builder()
                .name("paramFlow")
                .param("p", String.class, function(() -> "defaultValue"))
                .execute(action((String p) -> captured.add(p)))
                .build();

        flow.run(p -> "suppliedValue");
        assertEquals(List.of("suppliedValue"), captured);
    }

    @Test
    public void outerFlowScopeBindingSatisfiesInnerFlowParam() {
        List<String> captured = new ArrayList<>();

        RuleFlow<?> innerFlow = RuleFlow.builder()
                .name("innerFlow")
                .param("p", String.class, function(() -> "defaultValue"))
                .execute(action((String p) -> captured.add(p)))
                .build();

        RuleFlow<?> outerFlow = RuleFlow.builder()
                .name("outerFlow")
                .scope("outerScope", f -> f
                        .bind("p", "fromOuterScope")
                        .run(innerFlow))
                .build();

        outerFlow.run();
        // The exact behavior here decides how $traversal/$groups defaults interact
        // with bindings arriving from enclosing scopes (S3/F3).
        assertEquals(List.of("fromOuterScope"), captured);
    }
}
