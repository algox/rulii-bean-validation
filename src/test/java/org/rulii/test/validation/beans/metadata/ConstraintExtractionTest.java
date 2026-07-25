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
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.annotation.Warning;
import org.rulii.validation.beans.metadata.BeanMetadata;
import org.rulii.validation.beans.metadata.BeanMetadataReader;
import org.rulii.validation.beans.metadata.ConstraintMetadata;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for constraint extraction: attribute normalization, groups normalization,
 * payload-to-severity mapping, repeatable constraints, and composed constraints with
 * attribute overrides and host group/severity inheritance.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintExtractionTest {

    public ConstraintExtractionTest() {
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

    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @NotNull
    @Size(min = 5)
    @ReportAsSingleViolation
    public @interface ValidName {

        String message() default "invalid name";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Size.class, name = "max")
        int maxLength() default 50;
    }

    public static class Fixture {
        @Size(min = 2, max = 8, message = "size is off")
        private String code = "abc";

        @NotBlank(groups = OnCreate.class)
        private String createOnly = "x";

        @NotNull(payload = Warning.class)
        private String warned = "x";

        @Pattern(regexp = "[a-z]+")
        @Pattern(regexp = "[abc]+", message = "second")
        private String doubled = "abc";

        @ValidName
        private String named = "abcdef";

        public Fixture() {
            super();
        }
    }

    // -----------------------------------------------------------------------
    // Attributes, groups, severity, message
    // -----------------------------------------------------------------------

    @Test
    public void attributesExtractedAtReadTime() {
        ConstraintMetadata size = firstConstraint("code");

        assertEquals(Size.class, size.getAnnotationType());
        assertEquals(2, (int) size.getAttribute("min"));
        assertEquals(8, (int) size.getAttribute("max"));
        assertEquals("size is off", size.getMessageTemplate());
    }

    @Test
    public void emptyGroupsNormalizedToDefault() {
        assertEquals(Set.of(Default.class), firstConstraint("code").getGroups());
    }

    @Test
    public void declaredGroupsKeptAsDeclared() {
        assertEquals(Set.of(OnCreate.class), firstConstraint("createOnly").getGroups());
    }

    @Test
    public void payloadMapsToSeverity() {
        assertEquals(Severity.WARNING, firstConstraint("warned").getSeverity());
        assertEquals(Severity.ERROR, firstConstraint("code").getSeverity());
    }

    // -----------------------------------------------------------------------
    // Repeatable constraints
    // -----------------------------------------------------------------------

    @Test
    public void repeatedConstraintsBothExtracted() {
        List<ConstraintMetadata> constraints = reader.read(Fixture.class)
                .getProperty("doubled").getConstraints();

        assertEquals(2, constraints.size());
        assertEquals(Pattern.class, constraints.get(0).getAnnotationType());
        assertEquals(Pattern.class, constraints.get(1).getAnnotationType());
    }

    // -----------------------------------------------------------------------
    // Composed constraints
    // -----------------------------------------------------------------------

    @Test
    public void composedConstraintKeepsCompositionTree() {
        ConstraintMetadata named = firstConstraint("named");

        assertEquals(ValidName.class, named.getAnnotationType());
        assertTrue(named.isComposed());
        assertTrue(named.isReportAsSingleViolation());
        assertEquals(2, named.getComposingConstraints().size());
    }

    @Test
    public void overridesAttributeApplied() {
        ConstraintMetadata named = firstConstraint("named");
        ConstraintMetadata size = findComposing(named, Size.class);

        assertEquals(5, (int) size.getAttribute("min"));
        // max overridden by the host's maxLength() default
        assertEquals(50, (int) size.getAttribute("max"));
    }

    @Test
    public void composingInheritHostGroupsAndSeverity() {
        ConstraintMetadata named = firstConstraint("named");

        for (ConstraintMetadata composing : named.getComposingConstraints()) {
            assertEquals(named.getGroups(), composing.getGroups());
            assertEquals(named.getSeverity(), composing.getSeverity());
        }
    }

    private ConstraintMetadata firstConstraint(String property) {
        BeanMetadata metadata = reader.read(Fixture.class);
        return metadata.getProperty(property).getConstraints().get(0);
    }

    private ConstraintMetadata findComposing(ConstraintMetadata host, Class<?> annotationType) {
        for (ConstraintMetadata composing : host.getComposingConstraints()) {
            if (composing.getAnnotationType() == annotationType) return composing;
        }
        fail("composing constraint [" + annotationType + "] not found");
        return null;
    }
}
