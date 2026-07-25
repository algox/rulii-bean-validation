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
package org.rulii.validation.beans.metadata;

import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.Severity;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One constraint occurrence, fully normalized at metadata-read time. The annotation
 * class survives as an identity token (mapper-registry key, error reporting); attribute
 * values, groups, the message template, and the payload-mapped severity are all
 * extracted here — downstream layers never read a live annotation instance.
 *
 * <p>Composed constraints keep their composition as a tree: {@link #getComposingConstraints()}
 * holds the composing occurrences (with attribute overrides already applied), and
 * {@link #isReportAsSingleViolation()} records whether the composition reports as one
 * violation.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ConstraintMetadata {

    private final Class<? extends Annotation> annotationType;
    private final Map<String, Object> attributes;
    private final String messageTemplate;
    private final Set<Class<?>> groups;
    private final Severity severity;
    private final List<ConstraintMetadata> composingConstraints;
    private final boolean reportAsSingleViolation;

    ConstraintMetadata(Class<? extends Annotation> annotationType, Map<String, Object> attributes,
                       String messageTemplate, Set<Class<?>> groups, Severity severity,
                       List<ConstraintMetadata> composingConstraints, boolean reportAsSingleViolation) {
        super();
        Assert.notNull(annotationType, "annotationType cannot be null.");
        Assert.notNull(attributes, "attributes cannot be null.");
        Assert.notNull(groups, "groups cannot be null.");
        Assert.notNull(severity, "severity cannot be null.");
        Assert.notNull(composingConstraints, "composingConstraints cannot be null.");
        this.annotationType = annotationType;
        this.attributes = Collections.unmodifiableMap(attributes);
        this.messageTemplate = messageTemplate;
        this.groups = Collections.unmodifiableSet(groups);
        this.severity = severity;
        this.composingConstraints = Collections.unmodifiableList(composingConstraints);
        this.reportAsSingleViolation = reportAsSingleViolation;
    }

    /**
     * Retrieves the constraint annotation type. Identity token only — attribute values
     * are available via {@link #getAttributes()}.
     *
     * @return the annotation type.
     */
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    /**
     * Retrieves the constraint's attribute values (all attributes except message, groups,
     * and payload), extracted at read time. For composing constraints, attribute
     * overrides declared by the host are already applied.
     *
     * @return an unmodifiable map of attribute name to value.
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Retrieves a single attribute value.
     *
     * @param name the attribute name.
     * @param <T> the expected attribute type.
     * @return the attribute value, or null if the attribute does not exist.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        Assert.notNull(name, "name cannot be null.");
        return (T) attributes.get(name);
    }

    /**
     * Retrieves the constraint's message template, exactly as declared (jakarta syntax;
     * translation happens downstream).
     *
     * @return the message template.
     */
    public String getMessageTemplate() {
        return messageTemplate;
    }

    /**
     * Retrieves the constraint's declared groups, normalized: an empty {@code groups()}
     * is stored as the Default group.
     *
     * @return an unmodifiable set of group interfaces.
     */
    public Set<Class<?>> getGroups() {
        return groups;
    }

    /**
     * Retrieves the violation severity, already mapped from the constraint's payload
     * markers (default ERROR).
     *
     * @return the severity.
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Retrieves the composing constraints of a composed constraint, in declaration order,
     * with host attribute overrides applied.
     *
     * @return an unmodifiable list of composing constraints; empty if not composed.
     */
    public List<ConstraintMetadata> getComposingConstraints() {
        return composingConstraints;
    }

    /**
     * Determines whether this constraint is a composition of other constraints.
     *
     * @return true if composing constraints exist; false otherwise.
     */
    public boolean isComposed() {
        return !composingConstraints.isEmpty();
    }

    /**
     * Determines whether a composed constraint reports as a single violation.
     *
     * @return true if the composition reports one violation; false otherwise.
     */
    public boolean isReportAsSingleViolation() {
        return reportAsSingleViolation;
    }

    @Override
    public String toString() {
        return "ConstraintMetadata{annotationType=@" + annotationType.getSimpleName()
                + ", groups=" + groups.size()
                + ", severity=" + severity
                + (isComposed() ? ", composing=" + composingConstraints.size() : "")
                + '}';
    }
}
