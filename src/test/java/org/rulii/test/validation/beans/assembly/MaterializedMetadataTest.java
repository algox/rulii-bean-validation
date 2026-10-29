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
package org.rulii.test.validation.beans.assembly;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.rule.RuleDefinition;
import org.rulii.ruleset.RuleSetDefinition;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.assembly.RuleMaterializer;
import org.rulii.validation.beans.group.RequestedGroups;
import org.rulii.validation.beans.mapper.ConstraintTarget;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What materialized rules and rule sets say about themselves: description, category and tags, read
 * off their definitions.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class MaterializedMetadataTest {

    private static final BeanValidator VALIDATOR = BeanValidator.builder().build();

    public MaterializedMetadataTest() {
        super();
    }

    private static RuleDefinition definition(BeanRules rules, String name) {
        return rules.getRule(name).getRule().getDefinition();
    }

    @Test
    public void rulesDescribeTheirDeclarationAndWhereItIs() {
        BeanRules rules = VALIDATOR.getBeanRules(Item.class);

        RuleDefinition size = definition(rules, "code_size");
        assertTrue(size.getDescription().startsWith("code size must be between 2 and 5 (@Size on "), size.getDescription());
        assertTrue(size.getDescription().contains("code"), size.getDescription());
        assertEquals(Item.class.getName(), size.getCategory());
        assertEquals(List.of(RuleMaterializer.CONSTRAINT_TAG, "Size", "Default"), size.getTags());

        // The rule says what it checks in its own words, the annotation name and the declaration follow; the groups are tagged by simple name.
        RuleDefinition pattern = definition(rules, "code_pattern");
        assertTrue(pattern.getDescription().startsWith("code must match [a-z]+ (@Pattern on "), pattern.getDescription());
        assertEquals(List.of(RuleMaterializer.CONSTRAINT_TAG, "Pattern", "OnCreate"), pattern.getTags());

        // An annotation without attributes of its own.
        assertTrue(definition(rules, "name_notBlank").getDescription().startsWith("name must not be blank (@NotBlank on "));

        // The target carries the declaration, for mappers that build rules their own way; the rule appends it to what it checks.
        ConstraintTarget target = rules.getRule("code_size").getTarget();
        assertEquals("@Size on " + Item.class.getName() + ".code", target.getDescription());
        assertTrue(size.getDescription().endsWith(" (" + target.getDescription() + ")"), size.getDescription());
        assertEquals(size.getCategory(), target.getCategory());
        assertEquals(size.getTags(), target.getTags());
        assertThrows(UnsupportedOperationException.class, () -> target.getTags().clear());
    }

    @Test
    public void composingAndHostedRulesSaySo() {
        BeanRules rules = VALIDATOR.getBeanRules(RuleMaterializerTest.Composed.class);

        RuleDefinition composing = definition(rules, "code_validCode_size");
        assertTrue(composing.getDescription().startsWith("code size must be between 3 and 2147483647 (@Size composing @ValidCode on "), composing.getDescription());
        assertEquals(List.of(RuleMaterializer.CONSTRAINT_TAG, "Size", "Default"), composing.getTags(), "a flattened composition hosts nothing");

        RuleDefinition composition = definition(rules, "strict_strictCode");
        assertTrue(composition.getDescription().contains(" composing constraints (@StrictCode on "), composition.getDescription());
        assertEquals(List.of(RuleMaterializer.CONSTRAINT_TAG, "StrictCode", "OnCreate"), composition.getTags());

        RuleDefinition hosted = definition(rules, "strict_strictCode_size");
        assertTrue(hosted.getDescription().contains(" composing @StrictCode on "), hosted.getDescription());
        assertEquals(List.of(RuleMaterializer.CONSTRAINT_TAG, "Size", "OnCreate", RuleMaterializer.HOSTED_TAG), hosted.getTags(),
                "composing constraints inherit the host's groups and are reported for by it");
        assertEquals(RuleMaterializerTest.Composed.class.getName(), hosted.getCategory());
    }

    @Test
    public void validatorBackedRulesCarryTheSame() {
        BeanRules rules = VALIDATOR.getBeanRules(RuleMaterializerTest.WithValidator.class);

        RuleDefinition validated = definition(rules, "code_validatedCode");
        assertTrue(validated.getDescription().startsWith("@ValidatedCode on "), validated.getDescription());
        assertEquals(RuleMaterializerTest.WithValidator.class.getName(), validated.getCategory());
        assertEquals(List.of(RuleMaterializer.CONSTRAINT_TAG, "ValidatedCode", "Default"), validated.getTags());
    }

    @Test
    public void ruleSetsAreTaggedWithTheGroupsTheyWereBuiltFor() throws Exception {
        BeanRules rules = VALIDATOR.getBeanRules(Item.class);

        RuleSetDefinition defaults = rules.ruleSet(RequestedGroups.DEFAULT).getDefinition();
        assertEquals("Rules of " + Item.class.getName() + " for groups [Default]", defaults.getDescription());
        assertEquals(Item.class.getName(), defaults.getCategory());
        assertEquals(List.of("Default"), defaults.getTags());

        RuleSetDefinition onCreate = rules.ruleSet(RequestedGroups.of(OnCreate.class)).getDefinition();
        assertEquals(List.of("OnCreate"), onCreate.getTags());
        assertEquals(List.of("Default", "OnCreate"), rules.ruleSet(RequestedGroups.of(OnCreate.class, jakarta.validation.groups.Default.class))
                .getDefinition().getTags(), "sorted by simple name");

        RuleSetDefinition property = rules.propertyRuleSet("code", RequestedGroups.DEFAULT).getDefinition();
        assertEquals("Rules of property code of " + Item.class.getName() + " for groups [Default]", property.getDescription());
        assertEquals(List.of("Default"), property.getTags());

        ExecutableRules executable = VALIDATOR.getExecutableRules(Item.class, Item.class.getMethod("rename", String.class));
        RuleSetDefinition parameters = executable.parameterRuleSet(RequestedGroups.DEFAULT).getDefinition();
        assertTrue(parameters.getDescription().startsWith("Rules of the arguments of "), parameters.getDescription());
        assertEquals(Item.class.getName(), parameters.getCategory());
        assertEquals(List.of("Default"), parameters.getTags());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    public interface OnCreate {}

    public static class Item {
        @Size(min = 2, max = 5)
        @Pattern(regexp = "[a-z]+", groups = OnCreate.class)
        public String code;

        @NotBlank
        public String name;

        public void rename(@NotBlank String name) {
        }
    }
}
