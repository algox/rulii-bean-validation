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
import org.rulii.validation.rules.min.DecimalMinValidationRule;

import java.util.List;
import java.util.Map;

/**
 * Maps {@code jakarta.validation.constraints.DecimalMin} onto the core decimal-min rule.
 * The String {@code value} attribute is parsed to BigDecimal at materialization (invalid
 * literals fail there); the jakarta {@code value} attribute becomes the rule's
 * {@code min} parameter. A null value skips the rule (jakarta null contract).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class DecimalMinConstraintMapper extends AbstractConstraintMapper {

    private static final Map<String, String> ATTRIBUTE_NAME_MAPPING = Map.of("value", "min");

    public DecimalMinConstraintMapper() {
        super();
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return DecimalMinValidationRule.SUPPORTED_TYPES;
    }

    @Override
    public List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target) {
        boolean inclusive = this.<Boolean>attribute(constraint, "inclusive");
        return List.of(configure(Validators.decimalMin(binding(target), decimalAttribute(constraint, target, "value")), target)
                .inclusive(inclusive)
                .build());
    }

    @Override
    public Map<String, String> getAttributeNameMapping() {
        return ATTRIBUTE_NAME_MAPPING;
    }
}
