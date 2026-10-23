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

import jakarta.validation.ConstraintViolation;
import org.rulii.lib.spring.util.Assert;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The result of validating an object, a property or a value: the spec's
 * {@code Set<ConstraintViolation<T>>}, so a call site written for any {@code jakarta.validation.Validator}
 * compiles unchanged, and the owner of the rulii report it was built from, so rulii code reaches the
 * {@link BeanRuleViolations} with its paths, severities and parameters through {@link #getRuleViolations()}.
 * <p>
 * The set is immutable and keeps the report's order (traversal order, then declaration order).
 *
 * @param <T> the type of the root bean.
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanValidationResult<T> extends AbstractSet<ConstraintViolation<T>> {

    private final T rootBean;
    private final BeanRuleViolations ruleViolations;
    private final Set<ConstraintViolation<T>> violations;

    /**
     * Wraps a report.
     *
     * @param rootBean the object validated; null when a value was validated without an object.
     * @param ruleViolations the report.
     */
    @SuppressWarnings("unchecked")
    public BeanValidationResult(T rootBean, BeanRuleViolations ruleViolations) {
        super();
        Assert.notNull(ruleViolations, "ruleViolations cannot be null.");
        this.rootBean = rootBean;
        this.ruleViolations = ruleViolations;
        Set<ConstraintViolation<T>> set = new LinkedHashSet<>();
        for (BeanRuleViolation violation : ruleViolations.getBeanViolations()) {
            set.add((ConstraintViolation<T>) (ConstraintViolation<?>) violation);
        }
        this.violations = Collections.unmodifiableSet(set);
    }

    /**
     * The object validated.
     *
     * @return the root bean; null when a value was validated without an object.
     */
    public T getRootBean() {
        return rootBean;
    }

    /**
     * The rulii report behind this result.
     *
     * @return the report.
     */
    public BeanRuleViolations getRuleViolations() {
        return ruleViolations;
    }

    /**
     * The violations as rulii violations, in report order.
     *
     * @return the violations.
     */
    public List<BeanRuleViolation> getViolations() {
        return ruleViolations.getBeanViolations();
    }

    @Override
    public Iterator<ConstraintViolation<T>> iterator() {
        return violations.iterator();
    }

    @Override
    public int size() {
        return violations.size();
    }

    @Override
    public boolean contains(Object o) {
        return violations.contains(o);
    }

    @Override
    public String toString() {
        return "BeanValidationResult{rootBean=" + (rootBean != null ? rootBean.getClass().getName() : null)
                + ", violations=" + ruleViolations.getBeanViolations() + '}';
    }
}
