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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.payload.Fatal;
import org.rulii.validation.beans.payload.Info;
import org.rulii.validation.beans.payload.Warning;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.ConflictingSeverityException;
import org.rulii.validation.beans.model.ConstraintDefinition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the payload-to-severity mapping of {@link ConstraintDefinition}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class ConstraintSeverityTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    public ConstraintSeverityTest() {
        super();
    }

    @Test
    public void markersMapToSeverities() {
        BeanDefinition bean = BeanDefinition.load(Marked.class, REGISTRY);
        assertEquals(Severity.ERROR, constraint(bean, "plain").getSeverity());
        assertEquals(Severity.WARNING, constraint(bean, "warning").getSeverity());
        assertEquals(Severity.FATAL, constraint(bean, "fatal").getSeverity());
        assertEquals(Severity.INFO, constraint(bean, "info").getSeverity());
        // Vendor payloads are ignored; the marker still counts.
        assertEquals(Severity.WARNING, constraint(bean, "mixed").getSeverity());
        assertEquals(Severity.ERROR, constraint(bean, "vendorOnly").getSeverity());
        assertEquals(List.of(Other.class, Warning.class), constraint(bean, "mixed").getPayload());
    }

    @Test
    public void composingConstraintsInheritTheSeverity() {
        BeanDefinition bean = BeanDefinition.load(Marked.class, REGISTRY);
        ConstraintDefinition composed = constraint(bean, "composed");
        assertEquals(Severity.FATAL, composed.getSeverity());
        assertEquals(2, composed.getComposingConstraints().size());
        for (ConstraintDefinition composing : composed.getComposingConstraints()) {
            assertEquals(Severity.FATAL, composing.getSeverity());
        }
    }

    @Test
    public void twoMarkersFailFast() {
        ConflictingSeverityException e = assertThrows(ConflictingSeverityException.class,
                () -> BeanDefinition.load(Conflicting.class, REGISTRY));
        assertEquals(NotNull.class, e.getAnnotationType());
        assertEquals(List.of(Warning.class, Fatal.class), e.getMarkers());
        assertEquals(Conflicting.class, e.getSource().getDeclaringClass());
        assertTrue(e.getMessage().contains("Warning"));
        assertTrue(e.getMessage().contains("Fatal"));
        assertTrue(e.getMessage().contains(Conflicting.class.getName() + ".value"));
    }

    @Test
    public void twoMarkersOnAComposedHostFailOnceAtTheHost() {
        ConflictingSeverityException e = assertThrows(ConflictingSeverityException.class,
                () -> BeanDefinition.load(ConflictingComposed.class, REGISTRY));
        assertEquals(Composed.class, e.getAnnotationType());
    }

    private static ConstraintDefinition constraint(BeanDefinition bean, String property) {
        return bean.getProperty(property).getConstraints().get(0);
    }

    // ---- fixtures ----

    public static final class Other implements Payload {}

    @NotNull
    @Size(min = 2)
    @Constraint(validatedBy = {})
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Composed {
        String message() default "{composed}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class Marked {
        @NotNull
        private String plain;
        @NotNull(payload = Warning.class)
        private String warning;
        @NotNull(payload = Fatal.class)
        private String fatal;
        @NotNull(payload = Info.class)
        private String info;
        @NotNull(payload = {Other.class, Warning.class})
        private String mixed;
        @NotNull(payload = Other.class)
        private String vendorOnly;
        @Composed(payload = Fatal.class)
        private String composed;
    }

    public static class Conflicting {
        @NotNull(payload = {Warning.class, Fatal.class})
        private String value;
    }

    public static class ConflictingComposed {
        @Composed(payload = {Info.class, Fatal.class})
        private String value;
    }
}
