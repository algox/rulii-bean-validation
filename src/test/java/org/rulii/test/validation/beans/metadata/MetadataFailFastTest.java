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
package org.rulii.test.validation.beans.metadata;

import jakarta.validation.Constraint;
import jakarta.validation.GroupSequence;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.metadata.BeanMetadataReader;
import org.rulii.validation.beans.metadata.ConstraintApplicabilityException;
import org.rulii.validation.beans.metadata.GroupConversionNotSupportedException;
import org.rulii.validation.beans.metadata.GroupSequenceNotSupportedException;
import org.rulii.validation.beans.metadata.InvalidConstraintPlacementException;
import org.rulii.validation.beans.metadata.UnsupportedConstraintException;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the metadata-read fail-fasts: invalid constraint placement, group sequences
 * and conversions, unmapped constraints, applicability, poisoned cache entries, and the
 * eager cascade-graph read.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class MetadataFailFastTest {

    public MetadataFailFastTest() {
        super();
    }

    private final BeanMetadataReader reader = BeanMetadataReader.builder()
            .mapperRegistry(TestConstraintMappers.registry())
            .build();

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public interface OnCreate {
    }

    public static class ConstrainedNonGetter {
        public ConstrainedNonGetter() {
            super();
        }

        @NotNull
        public String calculateTotal() { return "x"; }
    }

    public static class ConstrainedSetter {
        private String name;

        public ConstrainedSetter() {
            super();
        }

        public void setName(@SuppressWarnings("unused") String name) { this.name = name; }

        @NotBlank
        public void setLabel(String label) { }
    }

    @GroupSequence({Sequenced.class, OnCreate.class})
    public static class Sequenced {
        public Sequenced() {
            super();
        }
    }

    public static class Converted {
        @Valid
        @ConvertGroup(from = Default.class, to = OnCreate.class)
        private Converted child;

        public Converted() {
            super();
        }
    }

    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Unmapped {

        String message() default "unmapped";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class WithUnmapped {
        @Unmapped
        private String value = "x";

        public WithUnmapped() {
            super();
        }
    }

    public static class WrongType {
        @Email
        private Integer number = 42;

        public WrongType() {
            super();
        }
    }

    public static class CascadesToWrongType {
        @Valid
        private WrongType child;

        public CascadesToWrongType() {
            super();
        }
    }

    // -----------------------------------------------------------------------
    // Placement
    // -----------------------------------------------------------------------

    @Test
    public void constrainedNonGetterFailsFast() {
        InvalidConstraintPlacementException e = assertThrows(InvalidConstraintPlacementException.class,
                () -> reader.read(ConstrainedNonGetter.class));
        assertEquals("calculateTotal", e.getMethod().getName());
    }

    @Test
    public void constrainedSetterFailsFast() {
        assertThrows(InvalidConstraintPlacementException.class, () -> reader.read(ConstrainedSetter.class));
    }

    // -----------------------------------------------------------------------
    // Groups features
    // -----------------------------------------------------------------------

    @Test
    public void groupSequenceFailsFast() {
        assertThrows(GroupSequenceNotSupportedException.class, () -> reader.read(Sequenced.class));
    }

    @Test
    public void convertGroupFailsFast() {
        assertThrows(GroupConversionNotSupportedException.class, () -> reader.read(Converted.class));
    }

    // -----------------------------------------------------------------------
    // Mapping & applicability
    // -----------------------------------------------------------------------

    @Test
    public void unmappedConstraintFailsFast() {
        UnsupportedConstraintException e = assertThrows(UnsupportedConstraintException.class,
                () -> reader.read(WithUnmapped.class));
        assertEquals(Unmapped.class, e.getAnnotationType());
        assertTrue(e.getMessage().contains("ConstraintMapper"));
    }

    @Test
    public void inapplicableConstraintFailsFast() {
        ConstraintApplicabilityException e = assertThrows(ConstraintApplicabilityException.class,
                () -> reader.read(WrongType.class));
        assertEquals(Email.class, e.getAnnotationType());
        assertEquals("number", e.getProperty());
    }

    // -----------------------------------------------------------------------
    // Cache poisoning & eager cascade reads
    // -----------------------------------------------------------------------

    @Test
    public void failedReadIsPoisonedAndRethrownDeterministically() {
        ConstraintApplicabilityException first = assertThrows(ConstraintApplicabilityException.class,
                () -> reader.read(WrongType.class));
        ConstraintApplicabilityException second = assertThrows(ConstraintApplicabilityException.class,
                () -> reader.read(WrongType.class));

        assertSame(first, second);
    }

    @Test
    public void eagerCascadeReadSurfacesChildFailuresAtRootRead() {
        // Reading the root eagerly reads the declared cascade target; the child's
        // fail-fast fires on the FIRST read of the root — and on every repeat.
        assertThrows(ConstraintApplicabilityException.class, () -> reader.read(CascadesToWrongType.class));
        assertThrows(ConstraintApplicabilityException.class, () -> reader.read(CascadesToWrongType.class));
    }
}
