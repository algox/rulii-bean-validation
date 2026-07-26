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
import org.rulii.validation.rules.pattern.PatternValidationRuleBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps {@code jakarta.validation.constraints.Email} onto the core email rule; a
 * non-default {@code regexp} attribute additionally materializes a pattern rule (suffix
 * {@code _pattern}) carrying the same reporting config — one annotation, two rules. A
 * null value skips both rules (jakarta null contract).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class EmailConstraintMapper extends AbstractConstraintMapper {

    private static final List<Class<?>> SUPPORTED_TYPES = List.of(CharSequence.class);
    private static final String DEFAULT_REGEXP = ".*";

    public EmailConstraintMapper() {
        super();
    }

    @Override
    public List<Class<?>> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target) {
        List<Rule> result = new ArrayList<>(2);
        result.add(configure(Validators.email(binding(target)), target).build());

        String regexp = attribute(constraint, "regexp");

        if (!DEFAULT_REGEXP.equals(regexp)) {
            Pattern.Flag[] flags = attribute(constraint, "flags");
            PatternValidationRuleBuilder builder = configure(Validators.pattern(binding(target), regexp), target)
                    .name(target.getRuleName() + "_pattern");
            if (flags.length > 0) builder.flags(PatternConstraintMapper.toRegexFlags(flags));
            result.add(builder.build());
        }

        return Collections.unmodifiableList(result);
    }
}
