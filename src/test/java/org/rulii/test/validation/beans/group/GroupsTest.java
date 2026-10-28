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
package org.rulii.test.validation.beans.group;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.GroupDefinitionException;
import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.GroupConversionDescriptor;
import org.junit.jupiter.api.Test;
import org.rulii.validation.RuleViolation;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.group.GroupPlan;
import org.rulii.validation.beans.group.RequestedGroups;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Group sequences and group conversions end to end: requested sequences stop at the first failing
 * member, class-level redefinition of Default runs the bean's own constraints in order, conversions
 * replace the processed group at the cascade point (exactly, sequences included), and ill-defined
 * declarations fail the load with the specification's exceptions.
 */
public class GroupsTest {

    public GroupsTest() {
        super();
    }

    private static List<String> report(Iterable<? extends ConstraintViolation<?>> violations) {
        List<String> result = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath() + ":" + ((RuleViolation) violation).getRuleName());
        }
        return result;
    }

    // --- requested sequences ----------------------------------------------------------------------

    @Test
    public void requestedSequenceStopsAtTheFirstFailingMember() {
        BeanValidator validator = BeanValidator.builder().build();
        Account account = new Account();
        account.name = "x";

        assertEquals(List.of("id:id_notNull"), report(validator.validate(account, Ordered.class)), "Basic fails, Advanced is not run");

        account.id = "1";
        assertEquals(List.of("name:name_size"), report(validator.validate(account, Ordered.class)));

        account.name = "long enough";
        assertTrue(validator.validate(account, Ordered.class).isEmpty());
    }

    @Test
    public void sequencePassesCoverTheWholeGraphBeforeStopping() {
        BeanValidator validator = BeanValidator.builder().build();
        Holder holder = new Holder();
        holder.plain = new Account();
        holder.plain.id = "1";
        holder.plain.name = "x";
        holder.plainOwner = new Owner();

        // Basic passes everywhere (nothing of Basic fails in the graph), so Advanced runs and fails on the cascaded account.
        assertEquals(List.of("plain.name:name_size", "plainOwner.email:email_notNull"), report(validator.validate(holder, Ordered.class)));

        holder.plain.id = null;
        assertEquals(List.of("plain.id:id_notNull"), report(validator.validate(holder, Ordered.class)), "a Basic failure deep in the graph stops the sequence");
    }

    @Test
    public void plainGroupsRunBeforeSequencesAndIndependently() {
        BeanValidator validator = BeanValidator.builder().build();
        Account account = new Account();

        // Default (code) first in one pass, then the sequence, which stops after Basic.
        assertEquals(List.of("code:code_notBlank", "id:id_notNull"), report(validator.validate(account, Default.class, Ordered.class)));
    }

    @Test
    public void nestedSequencesAreFlattenedAndCyclesFail() {
        List<GroupPlan.Pass> passes = GroupPlan.of(Nested.class).getPasses();

        assertEquals(1, passes.size());
        assertTrue(passes.get(0).sequence());
        assertEquals(List.of(RequestedGroups.of(Basic.class), RequestedGroups.of(Advanced.class), RequestedGroups.of(Final.class)), passes.get(0).steps());
        assertEquals(List.of(Basic.class, Advanced.class, Final.class), GroupPlan.members(Nested.class));

        assertThrows(GroupDefinitionException.class, () -> GroupPlan.of(CycleA.class));
        assertThrows(GroupDefinitionException.class, () -> GroupPlan.of(Empty.class));
        assertTrue(GroupPlan.of().isSingleStep());
        assertFalse(GroupPlan.of(Ordered.class).isSingleStep());
    }

    // --- class-level Default redefinition ----------------------------------------------------------

    @Test
    public void redefinedDefaultRunsTheBeanMemberByMember() {
        BeanValidator validator = BeanValidator.builder().build();
        Staged staged = new Staged();

        assertEquals(List.of(Quick.class, Thorough.class, Default.class), validator.getBeanDefinition(Staged.class).getDefaultGroupSequence());
        assertEquals(List.of("a:a_notNull"), report(validator.validate(staged)), "Quick fails, nothing further");

        staged.a = "a";
        staged.b = "x";
        assertEquals(List.of("b:b_size"), report(validator.validate(staged)), "Thorough fails before the class's own Default");

        staged.b = "ok";
        assertEquals(List.of("c:c_notBlank"), report(validator.validate(staged)), "the class itself stands for Default");

        staged.c = "c";
        assertTrue(validator.validate(staged).isEmpty());
    }

    @Test
    public void redefinedDefaultIncludesContainerElementsAndCascadesAfterwards() {
        BeanValidator validator = BeanValidator.builder().build();
        Staged staged = new Staged();
        staged.a = "a";
        staged.b = "ok";
        staged.tags = List.of("");
        staged.owner = new Owner();

        // The Thorough element constraint fails, so the class's Default constraint (c) is not run; the cascade still happens.
        assertEquals(List.of("tags[0].<list element>:tags_List0_notBlank", "owner.phone:phone_notNull"), report(validator.validate(staged)));
    }

    @Test
    public void redefinedDefaultLeavesOtherRequestedGroupsAlone() {
        BeanValidator validator = BeanValidator.builder().build();
        Staged staged = new Staged();

        assertEquals(List.of("d:d_notNull"), report(validator.validate(staged, Extra.class)), "Extra alone: the sequence does not apply");
        assertEquals(List.of("d:d_notNull", "a:a_notNull"), report(validator.validate(staged, Extra.class, Default.class)), "Extra first, then the sequence");
    }

    @Test
    public void redefinedDefaultAppliesToPropertyValueAndExecutableValidation() throws Exception {
        BeanValidator validator = BeanValidator.builder().build();
        Staged staged = new Staged();
        staged.b = "x";

        assertEquals(List.of("b:b_size"), report(validator.validateProperty(staged, "b")), "Quick has nothing on b; Thorough fails");
        assertEquals(List.of("b:b_size"), report(validator.validateValue(Staged.class, "b", "x")));

        Method process = Staged.class.getMethod("process", String.class, String.class);
        assertEquals(List.of("process.arg0:arg0_notNull"),
                report(validator.forExecutables().validateParameters(staged, process, new Object[] {null, "x"})).stream()
                        .map(path -> path.replaceAll("process\\.[^:]+:[^_]+_", "process.arg0:arg0_")).toList(),
                "Quick fails on the first parameter, Thorough is not run on the second");
    }

    @Test
    public void redefinedDefaultIsNotInherited() {
        BeanValidator validator = BeanValidator.builder().build();

        assertNull(validator.getBeanDefinition(SubStaged.class).getDefaultGroupSequence());
        // Without the inherited sequence only the plain Default constraint runs; Quick and Thorough are not Default.
        assertEquals(List.of("c:c_notBlank"), report(validator.validate(new SubStaged())));
    }

    @Test
    public void illDefinedClassSequencesFailTheLoad() {
        BeanValidator validator = BeanValidator.builder().build();

        assertTrue(assertThrows(GroupDefinitionException.class, () -> validator.getBeanDefinition(MissingSelf.class)).getMessage().contains("itself"));
        assertTrue(assertThrows(GroupDefinitionException.class, () -> validator.getBeanDefinition(ListsDefault.class)).getMessage().contains("Default"));
        assertTrue(assertThrows(GroupDefinitionException.class, () -> validator.getBeanDefinition(ListsSequence.class)).getMessage().contains("sequence"));
    }

    // --- conversions --------------------------------------------------------------------------------

    @Test
    public void conversionReplacesTheProcessedGroupAtTheCascadePoint() {
        BeanValidator validator = BeanValidator.builder().build();
        Holder holder = new Holder();
        holder.tag = "t";
        holder.converted = new Owner();

        assertEquals(List.of("converted.email:email_notNull"), report(validator.validate(holder)), "Default becomes Advanced for the owner");
        assertTrue(validator.validate(holder, Basic.class).isEmpty(), "no conversion from Basic, and the owner has no Basic constraints");
    }

    @Test
    public void conversionMatchesTheRequestedGroupExactly() {
        BeanValidator validator = BeanValidator.builder().build();
        Holder holder = new Holder();
        holder.exact = new Owner();

        assertEquals(List.of("exact.email:email_notNull"), report(validator.validate(holder, Sub.class)), "Sub is converted to Advanced");
        assertEquals(List.of("exact.nickname:nickname_notNull"), report(validator.validate(holder, SubSub.class)),
                "SubSub is not Sub: no conversion, and SubSub's expansion includes Sub");
    }

    @Test
    public void conversionToASequenceRunsItForTheCascadedObjectAlone() {
        BeanValidator validator = BeanValidator.builder().build();
        Holder holder = new Holder();
        holder.sequenced = new Account();
        holder.sequenced.name = "x";

        assertEquals(List.of("tag:tag_notBlank", "sequenced.id:id_notNull"), report(validator.validate(holder)),
                "the holder's own Default runs in full; the account's sequence stops after Basic");

        holder.sequenced.id = "1";
        assertEquals(List.of("tag:tag_notBlank", "sequenced.name:name_size"), report(validator.validate(holder)));
    }

    @Test
    public void conversionsApplyToContainerElementsParametersAndReturnValues() throws Exception {
        BeanValidator validator = BeanValidator.builder().build();
        Holder holder = new Holder();
        holder.tag = "t";
        holder.owners = List.of(new Owner());

        assertEquals(List.of("owners[0].email:email_notNull"), report(validator.validate(holder)));

        Method register = Holder.class.getMethod("register", Owner.class);
        List<String> parameters = report(validator.forExecutables().validateParameters(holder, register, new Object[] {new Owner()}));
        assertEquals(1, parameters.size());
        assertTrue(parameters.get(0).startsWith("register.") && parameters.get(0).endsWith(".email:email_notNull"), parameters.toString());

        assertEquals(List.of("find.<return value>.email:email_notNull"),
                report(validator.forExecutables().validateReturnValue(holder, Holder.class.getMethod("find"), new Owner())));
    }

    @Test
    public void conversionDeclarationErrorsFailTheLoad() {
        BeanValidator validator = BeanValidator.builder().build();

        assertTrue(assertThrows(ConstraintDeclarationException.class, () -> validator.getBeanDefinition(WithoutValid.class)).getMessage().contains("@Valid"));
        assertTrue(assertThrows(ConstraintDeclarationException.class, () -> validator.getBeanDefinition(DuplicateFrom.class)).getMessage().contains("twice"));
        assertTrue(assertThrows(ConstraintDeclarationException.class, () -> validator.getBeanDefinition(FromSequence.class)).getMessage().contains("sequence"));
    }

    @Test
    public void descriptorsExposeTheConversions() {
        BeanValidator validator = BeanValidator.builder().build();

        Set<GroupConversionDescriptor> conversions = validator.getConstraintsForClass(Holder.class).getConstraintsForProperty("converted").getGroupConversions();
        assertEquals(1, conversions.size());
        GroupConversionDescriptor conversion = conversions.iterator().next();
        assertEquals(Default.class, conversion.getFrom());
        assertEquals(Advanced.class, conversion.getTo());
        assertEquals(1, validator.getConstraintsForClass(Holder.class).getConstraintsForMethod("register", Owner.class)
                .getParameterDescriptors().get(0).getGroupConversions().size());
    }

    @Test
    public void sharedObjectIsValidatedOncePerGroup() {
        BeanValidator validator = BeanValidator.builder().build();
        Holder holder = new Holder();
        Owner shared = new Owner();
        holder.tag = "t";
        holder.converted = shared;
        holder.plainOwner = shared;

        // Reached once as Default (phone) and once as Advanced (email): validated under each, once; property order.
        assertEquals(List.of("plainOwner.phone:phone_notNull", "converted.email:email_notNull"), report(validator.validate(holder)));
    }

    // --- fixtures: groups -----------------------------------------------------------------------------

    public interface Basic {}
    public interface Advanced {}
    public interface Final {}
    public interface Extra {}
    public interface Quick {}
    public interface Thorough {}
    public interface Sub {}
    public interface SubSub extends Sub {}

    @GroupSequence({Basic.class, Advanced.class})
    public interface Ordered {}

    @GroupSequence({Ordered.class, Final.class})
    public interface Nested {}

    @GroupSequence(CycleB.class)
    public interface CycleA {}

    @GroupSequence(CycleA.class)
    public interface CycleB {}

    @GroupSequence({})
    public interface Empty {}

    // --- fixtures: beans -------------------------------------------------------------------------------

    public static class Account {
        @NotNull(groups = Basic.class) String id;
        @Size(min = 3, groups = Advanced.class) String name;
        @NotBlank String code;
    }

    public static class Owner {
        @NotNull(groups = Advanced.class) String email;
        @NotNull String phone;
        @NotNull(groups = Sub.class) String nickname;
    }

    public static class Holder {
        @NotBlank String tag;
        @Valid Account plain;
        @Valid Owner plainOwner;
        @Valid @ConvertGroup(from = Default.class, to = Advanced.class) Owner converted;
        @Valid @ConvertGroup(from = Sub.class, to = Advanced.class) Owner exact;
        @Valid @ConvertGroup(from = Default.class, to = Ordered.class) Account sequenced;
        List<@Valid @ConvertGroup(from = Default.class, to = Advanced.class) Owner> owners;

        public void register(@Valid @ConvertGroup(from = Default.class, to = Advanced.class) Owner owner) {
        }

        @Valid
        @ConvertGroup(from = Default.class, to = Advanced.class)
        public Owner find() {
            return null;
        }
    }

    @GroupSequence({Quick.class, Thorough.class, Staged.class})
    public static class Staged {
        @NotNull(groups = Quick.class) String a;
        @Size(min = 2, groups = Thorough.class) String b;
        @NotBlank String c;
        @NotNull(groups = Extra.class) String d;
        List<@NotBlank(groups = Thorough.class) String> tags;
        @Valid Owner owner;

        public void process(@NotNull(groups = Quick.class) String first, @Size(min = 2, groups = Thorough.class) String second) {
        }
    }

    public static class SubStaged extends Staged {
        public SubStaged() {
            super();
            b = "x";
        }
    }

    @GroupSequence({Quick.class})
    public static class MissingSelf {
        @NotNull String a;
    }

    @GroupSequence({Default.class, ListsDefault.class})
    public static class ListsDefault {
        @NotNull String a;
    }

    @GroupSequence({Ordered.class, ListsSequence.class})
    public static class ListsSequence {
        @NotNull String a;
    }

    public static class WithoutValid {
        @ConvertGroup(from = Default.class, to = Advanced.class) Owner owner;
    }

    public static class DuplicateFrom {
        @Valid
        @ConvertGroup.List({@ConvertGroup(from = Default.class, to = Advanced.class), @ConvertGroup(from = Default.class, to = Basic.class)})
        Owner owner;
    }

    public static class FromSequence {
        @Valid @ConvertGroup(from = Ordered.class, to = Advanced.class) Owner owner;
    }

    // --- implicit grouping (specification 5.4.4) ------------------------------------------------------

    @Test
    public void defaultConstraintsHostedOnAnInterfaceBelongToTheInterfaceGroup() {
        BeanValidator validator = BeanValidator.builder().build();
        AuditedOrder order = new AuditedOrder();

        // Default: everything; the interface as a group: only what the interface hosts in Default.
        assertEquals(List.of("orderNumber:orderNumber_notNull", "createdBy:createdBy_notNull"), report(validator.validate(order)));
        assertEquals(List.of("createdBy:createdBy_notNull"), report(validator.validate(order, Auditable.class)));

        // Explicitly grouped constraints on the interface are not in the implicit group.
        assertEquals(List.of("lastModifier:lastModifier_notNull"), report(validator.validate(order, Auditing.class)));
        assertTrue(validator.validate(order, Unrelated.class).isEmpty());

        // Through the metadata finder as well.
        assertEquals(1, validator.getConstraintsForClass(AuditedOrder.class).getConstraintsForProperty("createdBy")
                .findConstraints().unorderedAndMatchingGroups(Auditable.class).getConstraintDescriptors().size());
        assertTrue(validator.getConstraintsForClass(AuditedOrder.class).getConstraintsForProperty("orderNumber")
                .findConstraints().unorderedAndMatchingGroups(Auditable.class).getConstraintDescriptors().isEmpty());
    }

    public interface Auditing {
    }

    public interface Unrelated {
    }

    public interface Auditable {
        @NotNull
        String getCreatedBy();

        @NotNull(groups = Auditing.class)
        String getLastModifier();
    }

    public static class AuditedOrder implements Auditable {
        @NotNull
        String orderNumber;

        @Override
        public String getCreatedBy() {
            return null;
        }

        @Override
        public String getLastModifier() {
            return null;
        }
    }
}
