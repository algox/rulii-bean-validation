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
package org.rulii.test.validation.beans.model;

import jakarta.validation.ConstraintTarget;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.Bind;
import org.rulii.validation.beans.ExprDefaults;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.extract.ValueExtractorRegistry;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.BindDeclarationException;
import org.rulii.validation.beans.model.BindDefinition;
import org.rulii.validation.beans.model.ClassConstraintSource;
import org.rulii.validation.beans.model.CrossParameterConstraintSource;
import org.rulii.validation.beans.model.DeclarationConstraintSource;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ExprDeclarationException;
import org.rulii.validation.beans.model.IllegalExecutableConstraintException;
import org.rulii.validation.beans.model.ValueDeclaration.Kind;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BindDefinition} and its reading into {@link BeanDefinition} and
 * {@link ExecutableDefinition}: both forms, the hierarchy resolution, the executable sides and the
 * declaration errors. Nothing here converts a literal or compiles a script.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class BindDefinitionTest {

    private static final ValueExtractorRegistry REGISTRY = ValueExtractorRegistry.builder().build();

    private static final ParameterNameProvider POSITIONAL = new ParameterNameProvider() {
        @Override
        public List<String> getParameterNames(Constructor<?> constructor) {
            return names(constructor.getParameterCount());
        }

        @Override
        public List<String> getParameterNames(Method method) {
            return names(method.getParameterCount());
        }

        private static List<String> names(int count) {
            List<String> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                result.add("p" + i);
            }
            return result;
        }
    };

    public BindDefinitionTest() {
        super();
    }

    private static List<String> names(List<BindDefinition> binds) {
        List<String> result = new ArrayList<>(binds.size());
        for (BindDefinition bind : binds) {
            result.add(bind.getBindingName() + "=" + (bind.isExpression() ? bind.getScript() : bind.getLiteral()));
        }
        return result;
    }

    private static <T extends Throwable> T assertMessage(T thrown, String fragment) {
        assertTrue(thrown.getMessage().contains(fragment), "expected [" + fragment + "] in: " + thrown.getMessage());
        return thrown;
    }

    @Test
    public void bothFormsOnAClass() throws Exception {
        BeanDefinition bean = BeanDefinition.load(Order.class, REGISTRY);
        List<BindDefinition> binds = bean.getBinds();

        assertEquals(2, binds.size());
        BindDefinition limit = binds.get(0);
        assertEquals("limit", limit.getBindingName());
        assertFalse(limit.isExpression());
        assertEquals("10", limit.getLiteral());
        assertNull(limit.getScript());
        assertNull(limit.getLanguage(), "a literal has no language");
        assertEquals(Integer.class, limit.getType());
        assertEquals(ConstraintTarget.IMPLICIT, limit.getApplyTo());
        assertEquals("Bind", limit.getName());
        assertEquals(Order.class.getName(), limit.getSource().getClassName());
        assertSame(Order.class.getAnnotationsByType(Bind.class)[0], limit.getAnnotation());
        ClassConstraintSource source = assertInstanceOf(ClassConstraintSource.class, limit.getDeclarationSource());
        assertEquals(Order.class, source.getDeclaringClass());
        assertTrue(limit.toString().contains("limit = \"10\" as Integer"), limit.toString());

        BindDefinition label = binds.get(1);
        assertEquals("label", label.getBindingName());
        assertTrue(label.isExpression());
        assertEquals("${code}", label.getScript());
        assertNull(label.getLiteral());
        assertEquals("jakarta-el", label.getLanguage());
        assertEquals(Object.class, label.getType());
        assertTrue(label.toString().contains("label = jakarta-el: ${code}"), label.toString());

        assertTrue(bean.toString().contains("binds=2"));
        assertThrows(UnsupportedOperationException.class, () -> bean.getBinds().clear());
    }

    @Test
    public void aBindAloneDoesNotConstrain() {
        BeanDefinition bean = BeanDefinition.load(Inert.class, REGISTRY);

        assertEquals(List.of("x=1"), names(bean.getBinds()));
        assertFalse(bean.isConstrained(), "a binding is context, not a check");
    }

    @Test
    public void languageFollowsTheExprDefaultsChain() {
        List<BindDefinition> binds = BeanDefinition.load(Defaulted.class, REGISTRY).getBinds();

        assertEquals("jakarta-el", binds.get(0).getLanguage(), "the class's @ExprDefaults");
        assertEquals("java", binds.get(1).getLanguage(), "the annotation's own lang wins");

        ExprDeclarationException blank = assertThrows(ExprDeclarationException.class, () -> BeanDefinition.load(BlankDefaults.class, REGISTRY));
        assertMessage(blank, "@ExprDefaults on class [" + BlankDefaults.class.getName() + "] declares a blank lang; needed by @Bind [x]");
    }

    @Test
    public void classLevelBindsAreInheritedAndASubtypeReplacesAName() {
        assertEquals(List.of("limit=10"), names(BeanDefinition.load(Base.class, REGISTRY).getBinds()));
        assertEquals(List.of("limit=20"), names(BeanDefinition.load(Mid.class, REGISTRY).getBinds()));
        // The class wins over the interface it implements; Mid's redeclaration wins over Base's.
        List<BindDefinition> leaf = BeanDefinition.load(Leaf.class, REGISTRY).getBinds();
        assertEquals(List.of("limit=20", "cap=7"), names(leaf));
        assertEquals(Mid.class, leaf.get(0).getDeclarationSource().getDeclaringClass());
        assertEquals(Leaf.class, leaf.get(1).getDeclarationSource().getDeclaringClass());
        // Inherited from the interface when the class says nothing.
        assertEquals(List.of("limit=20", "cap=5"), names(BeanDefinition.load(Quiet.class, REGISTRY).getBinds()));
    }

    @Test
    public void unrelatedTypesBindingOneNameFailUnlessTheClassRedeclaresIt() {
        BindDeclarationException parallel = assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(Torn.class, REGISTRY));
        assertMessage(parallel, "class [" + Torn.class.getName() + "] inherits @Bind [x] from the unrelated types [");
        assertMessage(parallel, Left.class.getName());
        assertMessage(parallel, Right.class.getName());
        assertEquals("x", parallel.getBindingName());
        assertNotNull(parallel.getSource());

        assertEquals(List.of("x=3"), names(BeanDefinition.load(Mended.class, REGISTRY).getBinds()));
    }

    @Test
    public void declarationErrors() {
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(BlankName.class, REGISTRY)), "declares a blank name");
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(BadName.class, REGISTRY)), "[1st] , which is not a valid binding name".replace(" ,", ","));
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(Both.class, REGISTRY)), "must declare either a value or an expr, not both");
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(Neither.class, REGISTRY)), "must declare a value or an expr");
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(Targeted.class, REGISTRY)), "declares applyTo = RETURN_VALUE, which is for methods and constructors");
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(Twice.class, REGISTRY)), "class [" + Twice.class.getName() + "] declares @Bind [x] more than once");
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(Shadowing.class, REGISTRY)), "declares @Bind [code] on [" + Shadowing.class.getName() + "], which is the name of a property");
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(ShadowingInherited.class, REGISTRY)), "which is the name of a property");
        BindDeclarationException getter = assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(OnGetter.class, REGISTRY));
        assertMessage(getter, "fields, getters and record components are not targets of @Bind");
        assertEquals("x", getter.getBindingName());
    }

    @Test
    public void executableBindsSplitBySide() throws Exception {
        BeanDefinition bean = BeanDefinition.load(Service.class, REGISTRY, POSITIONAL);
        ExecutableDefinition place = bean.getMethod("place", String.class);

        assertEquals(List.of("limit=3"), names(place.getBinds()));
        CrossParameterConstraintSource parameters = assertInstanceOf(CrossParameterConstraintSource.class, place.getBinds().get(0).getDeclarationSource());
        assertEquals(Service.class.getMethod("place", String.class), parameters.getExecutable());
        assertEquals(List.of("tag=t"), names(place.getReturnValue().getBinds()));
        DeclarationConstraintSource returned = assertInstanceOf(DeclarationConstraintSource.class, place.getReturnValue().getBinds().get(0).getDeclarationSource());
        assertEquals(Kind.RETURN_VALUE, returned.getDeclaration().getKind());
        assertTrue(place.toString().contains("binds=1"));
        assertTrue(place.getReturnValue().toString().contains("binds=1"));

        // IMPLICIT: the return value when there are no parameters; a constructor binds like a method.
        assertEquals(List.of("tag=label"), names(bean.getMethod("label").getReturnValue().getBinds()));
        assertEquals(List.of(), bean.getMethod("label").getBinds());
        assertEquals(List.of("limit=1"), names(bean.getConstructor(String.class).getBinds()));
        assertEquals(List.of("made=yes"), names(bean.getConstructor(String.class).getReturnValue().getBinds()));

        // A bind alone does not make the parameters constrained.
        assertFalse(bean.getMethod("label").hasConstrainedParameters());
    }

    @Test
    public void executableDeclarationErrors() {
        assertMessage(assertThrows(IllegalExecutableConstraintException.class, () -> BeanDefinition.load(NoParameters.class, REGISTRY, POSITIONAL)),
                "@Bind [x](applyTo = PARAMETERS) but has no parameters");
        assertMessage(assertThrows(IllegalExecutableConstraintException.class, () -> BeanDefinition.load(ReturnsVoid.class, REGISTRY, POSITIONAL)),
                "@Bind [x](applyTo = RETURN_VALUE) but returns void");
        assertMessage(assertThrows(BindDeclarationException.class, () -> BeanDefinition.load(ShadowingParameter.class, REGISTRY, POSITIONAL)),
                "declares @Bind [p0] on [");
        assertMessage(assertThrows(IllegalExecutableConstraintException.class, () -> BeanDefinition.load(AddingBind.class, REGISTRY, POSITIONAL)),
                "must not declare parameter or cross-parameter constraints, or @Validate, @Expr or @Bind for the parameters, of its own");
    }

    @Test
    public void returnValueBindsAccumulateOverTheChainAndAnOverrideReplacesAName() {
        ExecutableDefinition name = BeanDefinition.load(Renamed.class, REGISTRY, POSITIONAL).getMethod("name");

        assertEquals(List.of("tag=sub", "extra=e"), names(name.getReturnValue().getBinds()));
        assertEquals(Renamed.class, name.getReturnValue().getBinds().get(0).getDeclarationSource().getDeclaringClass());

        // The parameters side is inherited as declared on the base; the override repeating it is fine.
        ExecutableDefinition move = BeanDefinition.load(Repeating.class, REGISTRY, POSITIONAL).getMethod("move", int.class);
        assertEquals(List.of("step=1"), names(move.getBinds()));
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    @Bind(name = "limit", value = "10", type = Integer.class)
    @Bind(name = "label", expr = "${code}", lang = "jakarta-el")
    public static class Order {
        @NotNull
        public String code;
    }

    @Bind(name = "x", value = "1")
    public static class Inert {
        public String code;
    }

    @ExprDefaults(lang = "jakarta-el")
    @Bind(name = "a", expr = "${1}")
    @Bind(name = "b", expr = "1", lang = "java")
    public static class Defaulted {}

    @ExprDefaults(lang = " ")
    @Bind(name = "x", expr = "1")
    public static class BlankDefaults {}

    @Bind(name = "limit", value = "10")
    public static class Base {
        @NotNull
        public String code;
    }

    @Bind(name = "limit", value = "20")
    public static class Mid extends Base {}

    @Bind(name = "cap", value = "5")
    public interface Capped {}

    @Bind(name = "cap", value = "7")
    public static class Leaf extends Mid implements Capped {}

    public static class Quiet extends Mid implements Capped {}

    @Bind(name = "x", value = "1")
    public interface Left {}

    @Bind(name = "x", value = "2")
    public interface Right {}

    public static class Torn implements Left, Right {}

    @Bind(name = "x", value = "3")
    public static class Mended implements Left, Right {}

    @Bind(name = " ", value = "1")
    public static class BlankName {}

    @Bind(name = "1st", value = "1")
    public static class BadName {}

    @Bind(name = "x", value = "1", expr = "2")
    public static class Both {}

    @Bind(name = "x")
    public static class Neither {}

    @Bind(name = "x", value = "1", applyTo = ConstraintTarget.RETURN_VALUE)
    public static class Targeted {}

    @Bind(name = "x", value = "1")
    @Bind(name = "x", value = "2")
    public static class Twice {}

    @Bind(name = "code", value = "1")
    public static class Shadowing {
        public String code;
    }

    @Bind(name = "code", value = "1")
    public static class ShadowingInherited extends Base {}

    public static class OnGetter {
        @Bind(name = "x", value = "1")
        public String getCode() {
            return "";
        }
    }

    public static class Service {

        @Bind(name = "limit", value = "1")
        @Bind(name = "made", value = "yes", applyTo = ConstraintTarget.RETURN_VALUE)
        @Validate("x")
        public Service(String code) {
            super();
        }

        @Bind(name = "limit", value = "3")
        @Bind(name = "tag", value = "t", applyTo = ConstraintTarget.RETURN_VALUE)
        @Validate("x")
        public String place(String code) {
            return code;
        }

        @Bind(name = "tag", value = "label")
        @Validate("x")
        public String label() {
            return "";
        }
    }

    public static class NoParameters {
        @Bind(name = "x", value = "1", applyTo = ConstraintTarget.PARAMETERS)
        public String label() {
            return "";
        }
    }

    public static class ReturnsVoid {
        @Bind(name = "x", value = "1", applyTo = ConstraintTarget.RETURN_VALUE)
        public void move(int n) {
        }
    }

    public static class ShadowingParameter {
        @Bind(name = "p0", value = "1")
        @Validate("x")
        public void move(int n) {
        }
    }

    public static class Mover {
        @Validate("x")
        public void move(int n) {
        }
    }

    public static class AddingBind extends Mover {
        @Override
        @Bind(name = "step", value = "1")
        @Validate("x")
        public void move(int n) {
        }
    }

    public static class Stepping {
        @Bind(name = "step", value = "1")
        @Validate("x")
        public void move(int n) {
        }
    }

    public static class Repeating extends Stepping {
        @Override
        @Bind(name = "step", value = "1")
        @Validate("x")
        public void move(int n) {
        }
    }

    public static class Named {
        @Bind(name = "tag", value = "base", applyTo = ConstraintTarget.RETURN_VALUE)
        @Validate("x")
        public String name() {
            return "";
        }
    }

    public static class Renamed extends Named {
        @Override
        @Bind(name = "tag", value = "sub", applyTo = ConstraintTarget.RETURN_VALUE)
        @Bind(name = "extra", value = "e", applyTo = ConstraintTarget.RETURN_VALUE)
        public String name() {
            return "";
        }
    }
}
