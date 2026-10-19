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
package org.rulii.test.validation.beans.model;

import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.BeanDefinitionLoader;
import org.rulii.validation.beans.model.ConstraintOnNonGetterException;
import org.rulii.validation.beans.model.GroupSequenceNotSupportedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BeanDefinitionLoader}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanDefinitionLoaderTest {

    public BeanDefinitionLoaderTest() {
        super();
    }

    private static BeanDefinitionLoader loader() {
        return new BeanDefinitionLoader(ValueExtractorRegistry.builder().build());
    }

    // --- caching ------------------------------------------------------------------------------

    @Test
    public void definitionsAreCachedPerLoader() {
        BeanDefinitionLoader loader = loader();
        assertFalse(loader.isLoaded(Address.class));

        BeanDefinition first = loader.load(Address.class);
        assertSame(first, loader.load(Address.class));
        assertTrue(loader.isLoaded(Address.class));
        assertFalse(loader.isPoisoned(Address.class));
        assertEquals(Address.class, first.getBeanClass());

        assertNotSame(first, loader().load(Address.class));
        assertNotNull(loader.getValueExtractorRegistry());
    }

    @Test
    public void badArgumentsAreRejectedAndNotCached() {
        BeanDefinitionLoader loader = loader();
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
        assertThrows(IllegalArgumentException.class, () -> loader.load(int.class));
        assertThrows(IllegalArgumentException.class, () -> loader.load(Address[].class));
        assertThrows(IllegalArgumentException.class, () -> loader.isLoaded(null));
        assertThrows(IllegalArgumentException.class, () -> loader.isPoisoned(null));
        assertThrows(IllegalArgumentException.class, () -> new BeanDefinitionLoader(null));
        assertFalse(loader.isPoisoned(int.class));
    }

    // --- eager cascade graph ------------------------------------------------------------------

    @Test
    public void cascadeTargetsAreLoadedEagerly() {
        BeanDefinitionLoader loader = loader();
        loader.load(Order.class);

        assertTrue(loader.isLoaded(Order.class));
        assertTrue(loader.isLoaded(Customer.class), "@Valid property");
        assertTrue(loader.isLoaded(Address.class), "List<@Valid Address>");
        assertTrue(loader.isLoaded(Tag.class), "Map<String, @Valid Tag>");
        assertTrue(loader.isLoaded(Note.class), "List<List<@Valid Note>>");
        assertTrue(loader.isLoaded(Shape.class), "List<@Valid ? extends Shape>");
        assertTrue(loader.isLoaded(Payment.class), "Optional<@Valid Payment>");
        assertFalse(loader.isLoaded(Plain.class), "not cascaded");
        assertFalse(loader.isLoaded(String.class), "map key is not cascaded");
    }

    @Test
    public void legacyValidOnAContainerLoadsTheElements() {
        BeanDefinitionLoader loader = loader();
        loader.load(Legacy.class);

        assertTrue(loader.isLoaded(Address.class), "@Valid List<Address>");
        assertTrue(loader.isLoaded(Customer.class), "@Valid ArrayList<Customer>");
        assertTrue(loader.isLoaded(Tag.class), "@Valid Map<String, Tag> cascades into the values");
        assertTrue(loader.isLoaded(Payment.class), "@Valid HashMap<Note, Payment> cascades into the values");
        assertTrue(loader.isLoaded(Shape.class), "@Valid Optional<Shape>");
        assertTrue(loader.isLoaded(Note.class), "@Valid Collection<? extends Note>");
        assertFalse(loader.isLoaded(String.class), "map keys are not cascaded");
        assertFalse(loader.isLoaded(List.class), "containers are not beans");
        assertFalse(loader.isLoaded(Map.class));
        assertFalse(loader.isLoaded(Optional.class));
        assertFalse(loader.isLoaded(OptionalInt.class));
        assertFalse(loader.isLoaded(Object.class));
    }

    @Test
    public void arraysWithoutAnExtractorAreLeftAlone() {
        BeanDefinitionLoader loader = loader();
        assertDoesNotThrow(() -> loader.load(WithArray.class));
        assertFalse(loader.isLoaded(Address.class));
        assertFalse(loader.isPoisoned(WithArray.class));
    }

    @Test
    public void cyclesTerminate() {
        BeanDefinitionLoader loader = loader();
        loader.load(Node.class);
        assertTrue(loader.isLoaded(Node.class));

        loader.load(Parent.class);
        assertTrue(loader.isLoaded(Parent.class));
        assertTrue(loader.isLoaded(Child.class));
        assertSame(loader.load(Child.class), loader.load(Child.class));
    }

    @Test
    public void cascadeTargetsDeclaredWithTypeVariablesResolveAgainstTheOwner() {
        BeanDefinitionLoader loader = loader();
        loader.load(PaymentHolder.class);
        assertTrue(loader.isLoaded(Payment.class), "@Valid T item with T = Payment");
        assertTrue(loader.isLoaded(Tag.class), "List<@Valid U> with U = Tag");

        // On the generic class itself the variables erase to Object and nothing is loaded.
        BeanDefinitionLoader plain = loader();
        plain.load(Holder.class);
        assertFalse(plain.isLoaded(Payment.class));
        assertFalse(plain.isLoaded(Object.class));
    }

    // --- poisoned entries ---------------------------------------------------------------------

    @Test
    public void aFailingClassIsPoisonedAndRethrowsTheSameException() {
        BeanDefinitionLoader loader = loader();

        GroupSequenceNotSupportedException first = assertThrows(GroupSequenceNotSupportedException.class,
                () -> loader.load(Sequenced.class));
        GroupSequenceNotSupportedException second = assertThrows(GroupSequenceNotSupportedException.class,
                () -> loader.load(Sequenced.class));

        assertSame(first, second);
        assertTrue(loader.isPoisoned(Sequenced.class));
        assertFalse(loader.isLoaded(Sequenced.class));
    }

    @Test
    public void aFailingCascadeTargetPoisonsThePathToItButNotItsSiblings() {
        BeanDefinitionLoader loader = loader();

        GroupSequenceNotSupportedException e = assertThrows(GroupSequenceNotSupportedException.class,
                () -> loader.load(RootWithBadTarget.class));
        assertEquals(Sequenced.class, e.getType());

        assertTrue(loader.isPoisoned(RootWithBadTarget.class));
        assertTrue(loader.isPoisoned(MiddleWithBadTarget.class));
        assertTrue(loader.isPoisoned(Sequenced.class));
        assertTrue(loader.isLoaded(Address.class), "sibling resolved before the failure stays loaded");

        assertSame(e, assertThrows(GroupSequenceNotSupportedException.class, () -> loader.load(RootWithBadTarget.class)));
        assertSame(e, assertThrows(GroupSequenceNotSupportedException.class, () -> loader.load(MiddleWithBadTarget.class)));
        assertSame(e, assertThrows(GroupSequenceNotSupportedException.class, () -> loader.load(Sequenced.class)));
    }

    @Test
    public void aPublishedPoisonFailsLaterRootsThatReachIt() {
        BeanDefinitionLoader loader = loader();
        ConstraintOnNonGetterException e = assertThrows(ConstraintOnNonGetterException.class, () -> loader.load(BadMethod.class));

        assertSame(e, assertThrows(ConstraintOnNonGetterException.class, () -> loader.load(ReachesBadMethod.class)));
        assertTrue(loader.isPoisoned(ReachesBadMethod.class));
    }

    @Test
    public void concurrentLoadsObserveOneDefinition() throws Exception {
        BeanDefinitionLoader loader = loader();
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<BeanDefinition>> futures = new ArrayList<>();
            Callable<BeanDefinition> task = () -> loader.load(Order.class);
            for (int i = 0; i < 16; i++) futures.add(executor.submit(task));

            BeanDefinition expected = loader.load(Order.class);
            for (Future<BeanDefinition> future : futures) assertSame(expected, future.get());
        } finally {
            executor.shutdownNow();
        }
    }

    // --- fixtures -----------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private static class Address { @NotBlank private String city; }
    @SuppressWarnings("unused")
    private static class Customer { @NotNull private String name; }
    @SuppressWarnings("unused")
    private static class Tag { @NotBlank private String label; }
    @SuppressWarnings("unused")
    private static class Note { private String text; }
    @SuppressWarnings("unused")
    private static class Payment { @NotNull private String method; }
    @SuppressWarnings("unused")
    private static class Plain { private String value; }
    private interface Shape { @NotNull String getKind(); }

    @SuppressWarnings("unused")
    private static class Order {
        @Valid private Customer customer;
        private List<@Valid Address> addresses;
        private Map<String, @Valid Tag> tags;
        private List<List<@Valid Note>> notes;
        private List<@Valid ? extends Shape> shapes;
        private Optional<@Valid Payment> payment;
        private Plain plain;
        private Set<Plain> plains;
    }

    @SuppressWarnings("unused")
    private static class Legacy {
        @Valid private List<Address> addresses;
        @Valid private ArrayList<Customer> customers;
        @Valid private Map<String, Tag> tags;
        @Valid private HashMap<Note, Payment> payments;
        @Valid private Optional<Shape> shape;
        @Valid private Collection<? extends Note> notes;
        @Valid private OptionalInt count;
        @Valid private Object anything;
    }

    @SuppressWarnings("unused")
    private static class WithArray { @Valid private Address[] addresses; }

    @SuppressWarnings("unused")
    private static class Node { @Valid private Node next; private List<@Valid Node> children; }
    @SuppressWarnings("unused")
    private static class Parent { @Valid private Child child; }
    @SuppressWarnings("unused")
    private static class Child { @Valid private Parent parent; }

    @GroupSequence({Default.class, Sequenced.class})
    @SuppressWarnings("unused")
    private static class Sequenced { @NotNull private String name; }

    @SuppressWarnings("unused")
    private static class MiddleWithBadTarget { @Valid private Sequenced bad; }
    @SuppressWarnings("unused")
    private static class RootWithBadTarget { @Valid private Address good; @Valid private MiddleWithBadTarget middle; }

    @SuppressWarnings("unused")
    private static class BadMethod { @NotNull public Object compute() { return null; } }
    @SuppressWarnings("unused")
    private static class ReachesBadMethod { @Valid private BadMethod bad; }
    @SuppressWarnings("unused")
    private static class Holder<T, U> { @Valid private T item; private List<@Valid U> items; }
    private static class PaymentHolder extends Holder<Payment, Tag> { }
}
