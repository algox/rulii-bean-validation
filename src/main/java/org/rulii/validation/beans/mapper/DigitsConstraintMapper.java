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
package org.rulii.validation.beans.mapper;

import org.rulii.rule.Rule;
import org.rulii.validation.beans.ConstraintTarget;
import org.rulii.validation.beans.metadata.ConstraintMetadata;
import org.rulii.validation.rules.Validators;

import java.util.List;
import java.util.Map;

/**
 * Maps {@code jakarta.validation.constraints.Digits} onto the core digits rule. The
 * jakarta {@code integer}/{@code fraction} attributes become the rule's
 * {@code maxIntegerLength}/{@code maxFractionLength} parameters. A null value skips the
 * rule (jakarta null contract).
 *
 * <p>Note: the core rule requires {@code maxIntegerLength > 0}; a jakarta-legal
 * {@code @Digits(integer = 0, ...)} declaration fails at materialization.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class DigitsConstraintMapper extends AbstractConstraintMapper {

    private static final List<Class<?>> SUPPORTED_TYPES = List.of(Number.class, CharSequence.class);
    private static final Map<String, String> ATTRIBUTE_NAME_MAPPING = Map.of(
            "integer", "maxIntegerLength",
            "fraction", "maxFractionLength");

    public DigitsConstraintMapper() {
        super();
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target) {
        int integer = this.<Integer>attribute(constraint, "integer");
        int fraction = this.<Integer>attribute(constraint, "fraction");
        return List.of(configure(Validators.digits(binding(target), integer, fraction), target).build());
    }

    @Override
    public Map<String, String> getAttributeNameMapping() {
        return ATTRIBUTE_NAME_MAPPING;
    }
}
