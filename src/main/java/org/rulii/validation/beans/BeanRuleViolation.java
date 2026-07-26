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
package org.rulii.validation.beans;

import org.rulii.lib.spring.util.Assert;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.path.Path;

import java.util.Map;
import java.util.Objects;

/**
 * A {@link RuleViolation} stamped with the property path it was reported at. The
 * rendered path also rides the parameter map under {@link #PATH_PARAMETER} so message
 * templates and plain consumers see it without the subtype. Equality includes the path —
 * the same rule failing at {@code orders[0]} and {@code orders[1]} is two violations.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BeanRuleViolation extends RuleViolation {

    public static final String PATH_PARAMETER = "propertyPath";

    private final Path path;

    /**
     * Creates a path-stamped violation.
     *
     * @param ruleName the reporting rule's name.
     * @param errorCode the violation error code.
     * @param severity the violation severity.
     * @param errorMessage the rendered error message.
     * @param params the violation parameters (should include {@link #PATH_PARAMETER}).
     * @param path the property path the violation was reported at.
     */
    public BeanRuleViolation(String ruleName, String errorCode, Severity severity, String errorMessage,
                             Map<String, String> params, Path path) {
        super(ruleName, errorCode, severity, errorMessage, params);
        Assert.notNull(path, "path cannot be null.");
        this.path = path;
    }

    /**
     * Retrieves the property path this violation was reported at.
     *
     * @return the property path.
     */
    public Path getPropertyPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        BeanRuleViolation other = (BeanRuleViolation) o;
        return path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), path);
    }

    @Override
    public String toString() {
        return "BeanRuleViolation{path=" + path.render() + ", " + super.toString() + '}';
    }
}
