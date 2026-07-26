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

import org.rulii.rule.Rule;
import org.rulii.validation.beans.metadata.ConstraintMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Maps a constraint annotation type onto rulii rules. A mapper is registered per
 * annotation type (see {@link ConstraintMapperRegistry}) — the standard jakarta
 * constraints ride the exact same mechanism that is the extension point for custom
 * constraints.
 *
 * <p>Two responsibilities: applicability ({@link #getSupportedTypes()}, consulted at
 * metadata-read time — a constraint placed on a declared type none of the supported
 * types are assignable from fails fast, rather than silently validating nothing) and
 * materialization ({@link #map} — one constraint occurrence to one or more rules,
 * threading the target's pre-computed config into core rule builders). Mappers own the
 * jakarta null-skip semantics of their constraint: every rule skips null values
 * ({@code failOnNull(false)}) except the presence constraints.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface ConstraintMapper {

    /**
     * Returns the types this constraint can be applied to. A constraint placed on a
     * declared type that is not assignable to any of these fails fast at metadata-read
     * time. An empty list means no type restriction.
     *
     * @return the list of supported types; never null.
     */
    List<Class<?>> getSupportedTypes();

    /**
     * Materializes one constraint occurrence into rules. Most constraints produce a
     * single rule; producers of multiple rules suffix the target's base rule name for
     * the secondary ones.
     *
     * @param constraint the normalized constraint occurrence (attributes, groups,
     *                   severity — never a live annotation).
     * @param target what the constraint is applied to and the pre-computed reporting config.
     * @return the materialized rules, in execution order; never null or empty.
     */
    List<Rule> map(ConstraintMetadata constraint, ConstraintTarget target);

    /**
     * Returns the mapping of jakarta attribute names to rule parameter names for this
     * constraint, applied when translating message templates ({@code {value}} in a
     * jakarta \@Min template must become {@code ${min}} — the parameter the core rule
     * actually emits). Constraints whose attribute names match their rule's parameter
     * names need no mapping.
     *
     * @return the attribute-name mapping; empty by default.
     */
    default Map<String, String> getAttributeNameMapping() {
        return Collections.emptyMap();
    }
}
