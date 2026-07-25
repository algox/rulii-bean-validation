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
import org.rulii.bind.Binding;
import org.rulii.bind.BindingAlreadyExistsException;
import org.rulii.bind.Bindings;
import org.rulii.bind.ScopedBindings;
import org.rulii.bind.load.FieldBindingLoader;
import org.rulii.model.UnrulyException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification spikes pinning the core binding behaviors the solution design depends on
 * (SOLUTION S5.2 lazy memoized property bindings, S5.4 shadowing): delegate bindings with
 * a null setter, supplier invocation frequency, computeIfAbsent memoization and its
 * isFinal consequence, and FieldBindingLoader's shadowed-field behavior.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BindingSpikeTest {

    public BindingSpikeTest() {
        super();
    }

    public static class Parent {
        protected String name = "parentValue";

        public Parent() {
            super();
        }
    }

    public static class Child extends Parent {
        protected String name = "childValue";

        public Child() {
            super();
        }
    }

    // -----------------------------------------------------------------------
    // Delegate bindings (S5.2 — read-only property bindings)
    // -----------------------------------------------------------------------

    @Test
    public void delegateWithNullSetterIsNotEditable() {
        Binding<String> binding = Binding.builder().with("prop")
                .type(String.class)
                .delegate(() -> "value", null)
                .build();

        assertFalse(binding.isEditable());
        assertThrows(UnrulyException.class, () -> binding.setValue("other"));
    }

    @Test
    public void delegateSupplierInvokedOnEveryAccess() {
        AtomicInteger count = new AtomicInteger();
        Binding<String> binding = Binding.builder().with("prop")
                .type(String.class)
                .delegate(() -> {
                    count.incrementAndGet();
                    return "value";
                }, null)
                .build();

        binding.getValue();
        binding.getValue();
        binding.getValue();
        // Non-final supplied bindings are live views: one supplier call per access.
        // Memoization (S5.2) must therefore live in our own supplier.
        assertEquals(3, count.get());
    }

    @Test
    public void computeIfAbsentMemoizesButForcesFinal() {
        AtomicInteger count = new AtomicInteger();
        Binding<String> binding = Binding.builder().with("prop")
                .type(String.class)
                .computeIfAbsent(() -> {
                    count.incrementAndGet();
                    return "value";
                })
                .build();

        binding.getValue();
        binding.getValue();
        assertEquals(1, count.get());
        // Native memoization comes only with isFinal = true — which forbids shadowing
        // in any scope (see finalBindingCannotBeShadowed) and is therefore unusable
        // for property bindings.
        assertTrue(binding.isFinal());
    }

    // -----------------------------------------------------------------------
    // Shadowing (S5.4 / R16.4)
    // -----------------------------------------------------------------------

    @Test
    public void finalBindingCannotBeShadowed() {
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind(Binding.builder().with("prop")
                .type(String.class)
                .computeIfAbsent(() -> "value")
                .build());
        bindings.addScope("child");

        assertThrows(BindingAlreadyExistsException.class, () -> bindings.bind("prop", "shadow"));
    }

    @Test
    public void nonFinalBindingCanBeShadowed() {
        ScopedBindings bindings = Bindings.builder().scoped();
        bindings.bind("prop", "parentValue");
        bindings.addScope("child");
        bindings.bind("prop", "childValue");

        assertEquals("childValue", bindings.getValue("prop"));
        bindings.removeScope();
        assertEquals("parentValue", bindings.getValue("prop"));
    }

    // -----------------------------------------------------------------------
    // FieldBindingLoader shadowed fields (S5.1 — why discovery is hand-rolled)
    // -----------------------------------------------------------------------

    @Test
    public void fieldLoaderShadowedFieldBehavior() {
        Bindings bindings = Bindings.builder().standard();
        bindings.load(new FieldBindingLoader<>(), new Child());

        // A single binding results for the shadowed name; which declaration backs it
        // documents the CORE-INVENTORY note-4 behavior (superclass overwrites subclass
        // in the loader's field cache). Our own discovery (S5.1) must keep BOTH.
        assertEquals("parentValue", bindings.getValue("name"));
    }
}
