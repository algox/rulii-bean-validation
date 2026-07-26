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
package org.rulii.test.validation.beans;

import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidationException;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.metadata.BeanMetadata;
import org.rulii.validation.beans.metadata.BeanMetadataReader;
import org.rulii.validation.beans.metadata.PropertyAccessException;
import org.rulii.validation.beans.metadata.PropertyMetadata;
import org.rulii.validation.beans.traversal.GroupSequenceRequestedException;
import org.rulii.test.validation.beans.metadata.TestConstraintMappers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage-hardening round: runtime cascade kinds (sets, arrays, maps, Optionals,
 * primitive arrays), array container walks, generic-hierarchy type resolution,
 * dual-source container merging, the group-sequence fail-fast, exception surfaces, and
 * diagnostic toString output.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class CoverageHardeningTest {

    public CoverageHardeningTest() {
        super();
    }

    private final BeanValidator validator = BeanValidator.builder().build();

    private final BeanMetadataReader reader = BeanMetadataReader.builder()
            .mapperRegistry(TestConstraintMappers.registry())
            .build();

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public static class City {
        @NotBlank private String name;

        public City(String name) {
            super();
            this.name = name;
        }
    }

    public static class Tour {
        @Valid private Set<City> stops;
        @Valid private City[] extras;
        @Valid private Map<String, City> byCode;
        @Valid private Optional<City> favorite;
        @Valid private int[] distances;
        private String[] codes;

        public Tour() {
            super();
        }
    }

    public static class CodedTour {
        // Mixed-target annotation on an array: the JLS mirrors it onto the component —
        // the reader treats it as an element constraint (Phase 2 dedup).
        @NotBlank private String[] codes;

        public CodedTour(String... codes) {
            super();
            this.codes = codes;
        }
    }

    public abstract static class Box<T> {
        @NotBlank private T value;
        private T[] values;

        public Box(T value) {
            super();
            this.value = value;
        }
    }

    public static class StringBox extends Box<String> {

        public StringBox(String value) {
            super(value);
        }
    }

    public static class WildBox {
        @Size(max = 2) private List<? extends String> caps;

        public WildBox(List<String> caps) {
            super();
            this.caps = caps;
        }
    }

    public static class DualContainer {
        private List<@Size(max = 3) String> items;

        public DualContainer(List<String> items) {
            super();
            this.items = items;
        }

        public List<@NotBlank String> getItems() {
            return items;
        }
    }

    public interface StepOne { }

    public interface StepTwo { }

    @GroupSequence({ StepOne.class, StepTwo.class })
    public interface Sequenced { }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<String> paths(RuleViolations violations) {
        List<String> result = new ArrayList<>();
        for (RuleViolation violation : violations.getViolations()) {
            result.add(((BeanRuleViolation) violation).getPropertyPath().render());
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Cascade kinds at runtime
    // -----------------------------------------------------------------------

    @Test
    public void cascadeCoversSetsArraysMapsAndOptionals() {
        Tour tour = new Tour();
        tour.stops = new LinkedHashSet<>(List.of(new City("ok"), new City("  ")));
        tour.extras = new City[] { new City("  ") };
        tour.byCode = Map.of("x", new City("  "));
        tour.favorite = Optional.of(new City("  "));
        tour.distances = new int[] { 1, 2 };

        BeanRuleViolations violations = validator.validate(tour);

        List<String> paths = paths(violations);
        assertEquals(4, paths.size(), "paths were: " + paths);
        assertTrue(paths.containsAll(List.of("stops[].name", "extras[0].name", "byCode[x].name", "favorite.name")),
                "paths were: " + paths);
    }

    @Test
    public void arrayContainerElementsWalk() {
        BeanRuleViolations violations = validator.validate(new CodedTour("ok", "  "));

        assertEquals(List.of("codes[1]"), paths(violations));
    }

    @Test
    public void mirroredValidOnArrayComponentsIsDeduped() {
        // @Valid on an array field is mirrored onto the component by the JLS; the reader
        // keeps the property-level cascade and drops the mirrored element cascade — one
        // descent, no container tree.
        BeanMetadata metadata = reader.read(Tour.class);

        PropertyMetadata extras = metadata.getProperty("extras");
        assertTrue(extras.isCascaded());
        assertNull(extras.getContainer());

        PropertyMetadata distances = metadata.getProperty("distances");
        assertTrue(distances.isCascaded());
        assertNull(distances.getContainer());
    }

    // -----------------------------------------------------------------------
    // Generic type resolution
    // -----------------------------------------------------------------------

    @Test
    public void genericSuperclassPropertiesResolveAgainstSubclass() {
        BeanMetadata metadata = reader.read(StringBox.class);
        PropertyMetadata value = metadata.getProperty("value");

        assertEquals(String.class, value.getDeclaredType());
        // And validation works end-to-end against the resolved type.
        assertEquals(List.of("value"), paths(validator.validate(new StringBox("  "))));
        assertTrue(validator.validate(new StringBox("ok")).getViolations().isEmpty());
    }

    @Test
    public void unresolvedTypeVariablesFallBackToBounds() {
        // Reading the generic class directly: T stays unresolved, applicability falls
        // back to the bound and passes.
        BeanMetadata metadata = reader.read(Box.class);

        assertNotNull(metadata.getProperty("value"));
        assertNotNull(metadata.getProperty("values"));
    }

    @Test
    public void wildcardElementTypesResolve() {
        BeanMetadata metadata = reader.read(WildBox.class);

        assertNotNull(metadata.getProperty("caps"));
        assertEquals(1, validator.validate(new WildBox(List.of("a", "b", "c"))).getViolations().size());
    }

    // -----------------------------------------------------------------------
    // Dual-source container merge
    // -----------------------------------------------------------------------

    @Test
    public void dualSourceContainerConstraintsMerge() {
        // Field declares @Size on elements, getter declares @NotBlank — both apply.
        BeanRuleViolations violations = validator.validate(new DualContainer(List.of("toolong", "  ")));

        List<String> paths = paths(violations);
        assertEquals(2, paths.size(), "paths were: " + paths);
        assertTrue(paths.containsAll(List.of("items[0]", "items[1]")));
    }

    // -----------------------------------------------------------------------
    // Fail-fasts & exception surfaces
    // -----------------------------------------------------------------------

    @Test
    public void requestedGroupSequenceFailsFast() {
        GroupSequenceRequestedException e = assertThrows(GroupSequenceRequestedException.class,
                () -> validator.validate(new City("x"), Sequenced.class));

        assertEquals(Sequenced.class, e.getGroup());
    }

    @Test
    public void propertyAccessExceptionCarriesActionableMessage() throws Exception {
        PropertyAccessException e = new PropertyAccessException(City.class,
                City.class.getDeclaredField("name"), null);

        assertEquals(City.class, e.getBeanClass());
        assertEquals("name", e.getMember().getName());
        assertTrue(e.getMessage().contains("name"), "message was: " + e.getMessage());
    }

    @Test
    public void validationExceptionHierarchy() {
        assertTrue(new GroupSequenceRequestedException(Sequenced.class) instanceof BeanValidationException);
    }

    // -----------------------------------------------------------------------
    // Diagnostics
    // -----------------------------------------------------------------------

    @Test
    public void diagnosticToStringsNeverFail() {
        BeanMetadata metadata = reader.read(DualContainer.class);
        PropertyMetadata items = metadata.getProperty("items");

        assertFalse(metadata.toString().isEmpty());
        assertFalse(items.toString().isEmpty());
        assertFalse(items.getContainer().toString().isEmpty());
        assertFalse(items.getContainer().getElement().toString().isEmpty());
        assertFalse(items.getContainer().getElement().getConstraints().get(0).toString().isEmpty());
        assertFalse(validator.toString().isEmpty());
        assertFalse(validator.getRuleFlow(City.class).toString().isEmpty());
    }
}
