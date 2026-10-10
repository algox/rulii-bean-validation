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
package org.rulii.test.validation.beans;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.rulii.test.validation.beans.skipped.SkippedBroken;
import org.rulii.test.validation.beans.skipped.SkippedProfile;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.RuleViolations;
import org.rulii.validation.beans.BeanRuleViolation;
import org.rulii.validation.beans.BeanRuleViolations;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.PackageSkips;
import org.rulii.validation.beans.RootClassSkippedException;
import org.rulii.validation.beans.metadata.ConstraintApplicabilityException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for caller-configured skip packages: cascade no-op (single values and container
 * elements), property-declared constraints still running, eager-read exclusion, the
 * fail-fast root, prefix package-boundary matching, and builder validation.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class SkipPackagesTest {

    private static final String SKIPPED_PACKAGE = "org.rulii.test.validation.beans.skipped";

    public SkipPackagesTest() {
        super();
    }

    private final BeanValidator skipping = BeanValidator.builder().skipPackages(SKIPPED_PACKAGE).build();
    private final BeanValidator plain = BeanValidator.builder().build();

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public static class Account {
        @NotBlank private String name;
        @Valid private SkippedProfile profile;
        @Valid private List<SkippedProfile> profiles;

        public Account(String name, SkippedProfile profile, List<SkippedProfile> profiles) {
            super();
            this.name = name;
            this.profile = profile;
            this.profiles = profiles;
        }
    }

    public static class RequiredProfileHolder {
        @NotNull @Valid private SkippedProfile profile;

        public RequiredProfileHolder(SkippedProfile profile) {
            super();
            this.profile = profile;
        }
    }

    public static class BrokenHolder {
        @Valid private SkippedBroken broken;

        public BrokenHolder(SkippedBroken broken) {
            super();
            this.broken = broken;
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    public void cascadeIntoSkippedPackageIsNoOp() {
        Account account = new Account("Ada", new SkippedProfile("  "), List.of(new SkippedProfile("  ")));

        assertTrue(skipping.validate(account).getViolations().isEmpty());
        assertTrue(paths(plain.validate(account))
                .containsAll(List.of("profile.nickname", "profiles[0].nickname")));
    }

    @Test
    public void constraintsOnSkippedTypePropertyStillRun() {
        BeanRuleViolations violations = skipping.validate(new RequiredProfileHolder(null));

        assertEquals(List.of("profile"), paths(violations));
    }

    @Test
    public void skippedCascadeTargetIsNotReadEagerly() {
        // SkippedBroken fail-fasts at metadata read; skipping its package must keep the
        // eager read (and the runtime descent) away from it entirely.
        assertTrue(skipping.validate(new BrokenHolder(new SkippedBroken(1))).getViolations().isEmpty());
        assertThrows(ConstraintApplicabilityException.class,
                () -> plain.validate(new BrokenHolder(new SkippedBroken(1))));
    }

    @Test
    public void rootInSkippedPackageFailsFast() {
        RootClassSkippedException e = assertThrows(RootClassSkippedException.class,
                () -> skipping.validate(new SkippedProfile("ok")));

        assertEquals(SkippedProfile.class, e.getBeanClass());
    }

    @Test
    public void prefixMatchesOnPackageBoundaryOnly() {
        // "…beans.skip" is a strict prefix of "…beans.skipped" as a string, but not a
        // parent package — it must not match.
        BeanValidator boundary = BeanValidator.builder()
                .skipPackages("org.rulii.test.validation.beans.skip").build();
        Account account = new Account("Ada", new SkippedProfile("  "), List.of());

        assertEquals(List.of("profile.nickname"), paths(boundary.validate(account)));
    }

    @Test
    public void trailingDotNormalizes() {
        PackageSkips skips = PackageSkips.of(List.of(SKIPPED_PACKAGE + "."));

        assertTrue(skips.isUserSkipped(SkippedProfile.class));
        assertTrue(skips.isSkipped(SkippedProfile.class));
        assertFalse(skips.isUserSkipped(Account.class));
    }

    @Test
    public void platformPackagesAreAlwaysSkipped() {
        assertTrue(PackageSkips.none().isSkipped(String.class));
        assertFalse(PackageSkips.none().isUserSkipped(String.class));
    }

    @Test
    public void blankPrefixFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> BeanValidator.builder().skipPackages("  ").build());
    }

    private static List<String> paths(RuleViolations violations) {
        List<String> result = new ArrayList<>();
        for (RuleViolation violation : violations.getViolations()) {
            result.add(((BeanRuleViolation) violation).getPropertyPath().render());
        }
        return result;
    }
}
