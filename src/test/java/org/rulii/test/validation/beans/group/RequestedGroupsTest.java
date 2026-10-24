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
package org.rulii.test.validation.beans.group;

import jakarta.validation.GroupSequence;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.group.InvalidGroupException;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ValueDeclaration;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RequestedGroups}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class RequestedGroupsTest {

    public RequestedGroupsTest() {
        super();
    }

    @Test
    public void noGroupsMeansDefault() {
        RequestedGroups groups = RequestedGroups.of();

        assertSame(RequestedGroups.DEFAULT, groups);
        assertSame(RequestedGroups.DEFAULT, RequestedGroups.of(List.of()));
        assertTrue(groups.isDefault());
        assertEquals(List.of(), groups.getRequested());
        assertEquals(Set.of(Default.class), groups.getGroups());
        assertTrue(groups.contains(Default.class));
        assertFalse(groups.contains(OnCreate.class));
        assertTrue(groups.activates(List.of()));
        assertTrue(groups.activates(List.of(Default.class)));
        assertFalse(groups.activates(List.of(OnCreate.class)));
        assertTrue(groups.activates(List.of(OnCreate.class, Default.class)));
    }

    @Test
    public void requestedGroupsDoNotIncludeDefault() {
        RequestedGroups groups = RequestedGroups.of(OnCreate.class);

        assertFalse(groups.isDefault());
        assertEquals(List.of(OnCreate.class), groups.getRequested());
        assertEquals(Set.of(OnCreate.class), groups.getGroups());
        assertFalse(groups.contains(Default.class));
        assertFalse(groups.activates(List.of()));
        assertTrue(groups.activates(List.of(OnCreate.class)));
        assertFalse(groups.activates(List.of(OnUpdate.class)));
    }

    @Test
    public void defaultCanBeRequestedExplicitly() {
        RequestedGroups groups = RequestedGroups.of(OnCreate.class, Default.class);
        assertFalse(groups.isDefault());
        assertTrue(groups.activates(List.of()));
        assertTrue(groups.activates(List.of(OnCreate.class)));
    }

    @Test
    public void supergroupsAreIncluded() {
        RequestedGroups groups = RequestedGroups.of(Full.class);

        assertEquals(List.of(Full.class), groups.getRequested());
        assertEquals(List.of(Full.class, OnCreate.class, OnUpdate.class), List.copyOf(groups.getGroups()));
        assertTrue(groups.activates(List.of(OnUpdate.class)));
        assertTrue(groups.activates(List.of(OnCreate.class)));
        assertFalse(groups.activates(List.of()));

        // Transitively, and a group reached twice appears once.
        RequestedGroups deep = RequestedGroups.of(Deep.class, OnCreate.class);
        assertEquals(List.of(Deep.class, Full.class, OnCreate.class, OnUpdate.class), List.copyOf(deep.getGroups()));
        assertEquals(List.of(Deep.class, OnCreate.class), deep.getRequested());
    }

    @Test
    public void requestOrderIsKept() {
        RequestedGroups groups = RequestedGroups.of(OnUpdate.class, OnCreate.class, OnUpdate.class);
        assertEquals(List.of(OnUpdate.class, OnCreate.class, OnUpdate.class), groups.getRequested());
        assertEquals(List.of(OnUpdate.class, OnCreate.class), List.copyOf(groups.getGroups()));
    }

    @Test
    public void activatesConstraintDefinitions() throws Exception {
        ConstraintDefinition plain = constraint("plain");
        ConstraintDefinition onCreate = constraint("onCreate");
        ConstraintDefinition both = constraint("both");

        assertTrue(RequestedGroups.DEFAULT.activates(plain));
        assertFalse(RequestedGroups.DEFAULT.activates(onCreate));
        assertTrue(RequestedGroups.DEFAULT.activates(both));

        RequestedGroups create = RequestedGroups.of(OnCreate.class);
        assertFalse(create.activates(plain));
        assertTrue(create.activates(onCreate));
        assertTrue(create.activates(both));

        RequestedGroups update = RequestedGroups.of(OnUpdate.class);
        assertFalse(update.activates(plain));
        assertFalse(update.activates(onCreate));
        assertFalse(update.activates(both));

        assertTrue(RequestedGroups.of(Full.class).activates(onCreate));
    }

    @Test
    public void nonInterfaceFailsFast() {
        InvalidGroupException e = assertThrows(InvalidGroupException.class, () -> RequestedGroups.of(NotAGroup.class));
        assertEquals(NotAGroup.class, e.getGroup());
        assertTrue(e.getMessage().contains(NotAGroup.class.getName()));
        assertInstanceOf(ValidationException.class, e);

        assertThrows(InvalidGroupException.class, () -> RequestedGroups.of(OnCreate.class, String.class));
        assertThrows(InvalidGroupException.class, () -> RequestedGroups.of(int.class));
    }

    @Test
    public void groupSequenceExpandsToItsMembers() {
        // Unordered here; order and stopping are the plan's business.
        RequestedGroups groups = RequestedGroups.of(Sequence.class);
        assertTrue(groups.getGroups().containsAll(List.of(OnCreate.class, OnUpdate.class)));
        assertFalse(groups.contains(Sequence.class));

        // A sequence reached through a supergroup expands as well.
        RequestedGroups inherited = RequestedGroups.of(ExtendsSequence.class);
        assertTrue(inherited.getGroups().containsAll(List.of(ExtendsSequence.class, OnCreate.class, OnUpdate.class)));
    }

    @Test
    public void withoutRemovesARequestedGroup() {
        assertEquals(Set.of(OnUpdate.class), RequestedGroups.of(OnCreate.class, OnUpdate.class).without(OnCreate.class).getGroups());
        assertTrue(RequestedGroups.DEFAULT.without(Default.class).isEmpty());
        assertEquals(List.of(Default.class), RequestedGroups.DEFAULT.getRequestedOrDefault());
    }

    @Test
    public void nullsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RequestedGroups.of((Class<?>[]) null));
        assertThrows(IllegalArgumentException.class, () -> RequestedGroups.of((Collection<Class<?>>) null));
        assertThrows(IllegalArgumentException.class, () -> RequestedGroups.of(OnCreate.class, null));
        assertThrows(IllegalArgumentException.class, () -> RequestedGroups.DEFAULT.contains(null));
        assertThrows(IllegalArgumentException.class, () -> RequestedGroups.DEFAULT.activates((ConstraintDefinition) null));
        assertThrows(IllegalArgumentException.class, () -> RequestedGroups.DEFAULT.activates((Collection<Class<?>>) null));
    }

    @Test
    public void valueSemantics() {
        assertEquals(RequestedGroups.of(OnCreate.class), RequestedGroups.of(OnCreate.class));
        assertEquals(RequestedGroups.of(OnCreate.class).hashCode(), RequestedGroups.of(OnCreate.class).hashCode());
        assertNotEquals(RequestedGroups.of(OnCreate.class), RequestedGroups.of(OnUpdate.class));
        assertNotEquals(RequestedGroups.DEFAULT, RequestedGroups.of(OnCreate.class));
        // Equality is over the active groups: requesting a supergroup alongside its subgroup changes nothing.
        assertEquals(RequestedGroups.of(Full.class), RequestedGroups.of(Full.class, OnCreate.class));
        assertEquals(RequestedGroups.of(), RequestedGroups.of(Default.class));
        assertTrue(RequestedGroups.of(Full.class).toString().contains("Full"));

        assertThrows(UnsupportedOperationException.class, () -> RequestedGroups.of(OnCreate.class).getGroups().clear());
        assertThrows(UnsupportedOperationException.class, () -> RequestedGroups.of(OnCreate.class).getRequested().clear());
        assertThrows(UnsupportedOperationException.class, () -> RequestedGroups.DEFAULT.getGroups().clear());
    }

    private static ConstraintDefinition constraint(String field) throws Exception {
        return ConstraintDefinition.load(ValueDeclaration.ofField(Bean.class.getDeclaredField(field))).get(0);
    }

    // ---- fixtures ----

    public interface OnCreate {}

    public interface OnUpdate {}

    public interface Full extends OnCreate, OnUpdate {}

    public interface Deep extends Full {}

    @GroupSequence({OnCreate.class, OnUpdate.class})
    public interface Sequence {}

    public interface ExtendsSequence extends Sequence {}

    public static class NotAGroup {}

    public static class Bean {
        @NotNull
        private String plain;

        @NotNull(groups = OnCreate.class)
        private String onCreate;

        @NotNull(groups = {OnCreate.class, Default.class})
        private String both;
    }
}
