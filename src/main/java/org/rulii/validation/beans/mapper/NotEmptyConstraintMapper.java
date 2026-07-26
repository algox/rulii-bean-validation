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
import org.rulii.validation.rules.notempty.NotEmptyValidationRule;

import java.util.List;

/**
 * Maps {@code jakarta.validation.constraints.NotEmpty} onto the core not-empty rule.
 * Presence constraint — a null value fails.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class NotEmptyConstraintMapper extends AbstractConstraintMapper {

    public NotEmptyConstraintMapper() {
        super();
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return NotEmptyValidationRule.SUPPORTED_TYPES;
    }

    @Override
    public List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target) {
        return List.of(configurePresence(Validators.notEmpty(binding(target)), target).build());
    }
}
