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

import jakarta.validation.constraints.Pattern;
import org.rulii.rule.Rule;
import org.rulii.validation.beans.ConstraintTarget;
import org.rulii.validation.beans.metadata.ConstraintMetadata;
import org.rulii.validation.rules.Validators;
import org.rulii.validation.rules.pattern.PatternValidationRule;
import org.rulii.validation.rules.pattern.PatternValidationRuleBuilder;

import java.util.List;
import java.util.Map;

/**
 * Maps {@code jakarta.validation.constraints.Pattern} onto the core pattern rule. The
 * jakarta {@code Flag} bits are literally JDK {@code java.util.regex.Pattern} constants
 * and are OR'd straight through; the jakarta {@code regexp} attribute becomes the rule's
 * {@code pattern} parameter. A null value skips the rule (jakarta null contract).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class PatternConstraintMapper extends AbstractConstraintMapper {

    private static final Map<String, String> ATTRIBUTE_NAME_MAPPING = Map.of("regexp", "pattern");

    public PatternConstraintMapper() {
        super();
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return PatternValidationRule.SUPPORTED_TYPES;
    }

    @Override
    public List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target) {
        String regexp = attribute(constraint, "regexp");
        Pattern.Flag[] flags = attribute(constraint, "flags");
        PatternValidationRuleBuilder builder = configure(Validators.pattern(binding(target), regexp), target);
        if (flags.length > 0) builder.flags(toRegexFlags(flags));
        return List.of(builder.build());
    }

    @Override
    public Map<String, String> getAttributeNameMapping() {
        return ATTRIBUTE_NAME_MAPPING;
    }

    static int toRegexFlags(Pattern.Flag[] flags) {
        int result = 0;
        for (Pattern.Flag flag : flags) {
            result |= flag.getValue();
        }
        return result;
    }
}
