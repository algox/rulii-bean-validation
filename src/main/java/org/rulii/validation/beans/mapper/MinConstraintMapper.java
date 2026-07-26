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
import org.rulii.validation.rules.min.MinValidationRule;

import java.util.List;
import java.util.Map;

/**
 * Maps {@code jakarta.validation.constraints.Min} onto the core min rule. The jakarta
 * {@code value} attribute becomes the rule's {@code min} parameter (attribute-name
 * mapping). A null value skips the rule (jakarta null contract).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class MinConstraintMapper extends AbstractConstraintMapper {

    private static final Map<String, String> ATTRIBUTE_NAME_MAPPING = Map.of("value", "min");

    public MinConstraintMapper() {
        super();
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return MinValidationRule.SUPPORTED_TYPES;
    }

    @Override
    public List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target) {
        long min = this.<Long>attribute(constraint, "value");
        return List.of(configure(Validators.min(binding(target), min), target).build());
    }

    @Override
    public Map<String, String> getAttributeNameMapping() {
        return ATTRIBUTE_NAME_MAPPING;
    }
}
