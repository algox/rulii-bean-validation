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
package org.rulii.test.validation.beans.util.reflect;

import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.util.reflect.TypeHierarchies;
import org.rulii.validation.beans.util.reflect.TypeHierarchyVisitor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TypeHierarchies}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class TypeHierarchiesTest {

    public TypeHierarchiesTest() {
        super();
    }

    @Test
    public void typesAreSuperclassesTopDownThenInterfacesDepthFirstWithoutDuplicates() {
        assertEquals(List.of(Base.class, Middle.class, Leaf.class, Named.class, Identified.class, Audited.class, Priced.class),
                TypeHierarchies.types(Leaf.class));
    }

    @Test
    public void typesOfAnInterfaceAreItselfThenItsSuperinterfaces() {
        assertEquals(List.of(Audited.class, Named.class, Identified.class), TypeHierarchies.types(Audited.class));
    }

    @Test
    public void objectIsNeverPartOfTheWalk() {
        assertEquals(List.of(), TypeHierarchies.types(Object.class));
        assertEquals(List.of(Point.class, Priced.class), TypeHierarchies.types(Point.class));
        assertEquals(List.of(Color.class), TypeHierarchies.types(Color.class));
        assertFalse(TypeHierarchies.types(Leaf.class).contains(Object.class));
    }

    @Test
    public void primitivesAndArraysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TypeHierarchies.types(int.class));
        assertThrows(IllegalArgumentException.class, () -> TypeHierarchies.types(String[].class));
        assertThrows(IllegalArgumentException.class, () -> TypeHierarchies.types(null));
        assertThrows(IllegalArgumentException.class, () -> TypeHierarchies.walk(Leaf.class, null));
    }

    @Test
    public void walkVisitsEachTypeThenItsInstanceMembers() {
        Recorder recorder = new Recorder();
        TypeHierarchies.walk(Middle.class, recorder);

        assertEquals(List.of(Base.class, Middle.class, Named.class, Identified.class), new ArrayList<>(recorder.members.keySet()));
        assertEquals(Set.of("field id", "method getId", "method getName", "method describe"), recorder.members.get(Base.class));
        assertEquals(Set.of("field label", "method getLabel", "method compute"), recorder.members.get(Middle.class));
        assertEquals(Set.of("method getName"), recorder.members.get(Named.class));
        assertEquals(Set.of("method describe"), recorder.members.get(Identified.class));
    }

    @Test
    public void staticAndSyntheticMembersAreSkipped() {
        Recorder recorder = new Recorder();
        TypeHierarchies.walk(Inner.class, recorder);

        // No this$0 field, no static COUNT field, no static factory, no bridge for the covariant override.
        assertEquals(Set.of("field value", "method getId"), recorder.members.get(Inner.class));
    }

    @Test
    public void recordsVisitComponentsInsteadOfFieldsAndSkipAccessors() {
        Recorder recorder = new Recorder();
        TypeHierarchies.walk(Point.class, recorder);

        assertEquals(List.of(Point.class, Priced.class), new ArrayList<>(recorder.members.keySet()));
        Set<String> members = recorder.members.get(Point.class);
        assertTrue(members.containsAll(Set.of("component x", "component y", "method getDistance")), members.toString());
        assertFalse(members.contains("field x"));
        assertFalse(members.contains("method x"));
        assertFalse(members.contains("method y"));
        assertEquals(Set.of("method getPrice"), recorder.members.get(Priced.class));
    }

    @Test
    public void defaultCallbacksAreNoOps() {
        assertDoesNotThrow(() -> TypeHierarchies.walk(Leaf.class, new TypeHierarchyVisitor() {}));
    }

    /**
     * Records the member events per type; types in visit order, members as a set since the JDK
     * does not define the order of declared members.
     */
    private static final class Recorder implements TypeHierarchyVisitor {
        private final Map<Class<?>, Set<String>> members = new LinkedHashMap<>();
        private Set<String> current;

        private Recorder() {
            super();
        }

        @Override
        public void visitType(Class<?> type) {
            current = new TreeSet<>();
            assertNull(members.put(type, current), "type visited twice: " + type);
        }

        @Override
        public void visitField(Field field) {
            current.add("field " + field.getName());
        }

        @Override
        public void visitMethod(Method method) {
            current.add("method " + method.getName());
        }

        @Override
        public void visitRecordComponent(RecordComponent component) {
            current.add("component " + component.getName());
        }
    }

    private interface Identified { String describe(); }
    private interface Named extends Identified { String getName(); }
    private interface Audited extends Named, Identified { }
    private interface Priced { default int getPrice() { return 0; } }

    @SuppressWarnings("unused")
    private static class Base implements Named {
        private Number id;
        public Number getId() { return id; }
        @Override public String getName() { return "base"; }
        @Override public String describe() { return "base"; }
    }

    @SuppressWarnings("unused")
    private static class Middle extends Base implements Named {
        private String label;
        public String getLabel() { return label; }
        public String compute() { return label; }
    }

    @SuppressWarnings("unused")
    private static class Leaf extends Middle implements Audited, Priced { }

    @SuppressWarnings("unused")
    private class Inner extends Base {
        private static int COUNT;
        private String value;
        @Override public Integer getId() { return 1; }
        public static Inner create() { return null; }
    }

    private enum Color { RED, GREEN }

    @SuppressWarnings("unused")
    private record Point(int x, int y) implements Priced {
        public double getDistance() { return Math.hypot(x, y); }
    }
}
