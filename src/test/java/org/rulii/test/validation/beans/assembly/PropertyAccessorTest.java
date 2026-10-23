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

import org.junit.jupiter.api.Test;
import org.rulii.model.UnrulyException;
import org.rulii.validation.beans.assembly.InaccessiblePropertyException;
import org.rulii.validation.beans.assembly.PropertyAccessor;
import org.rulii.validation.beans.model.PropertyDeclaration;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PropertyAccessor}.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class PropertyAccessorTest {

    public PropertyAccessorTest() {
        super();
    }

    @Test
    public void readsFieldsGettersAndRecordComponents() throws Exception {
        Bean bean = new Bean();
        bean.secret = "s";

        PropertyAccessor field = PropertyAccessor.of(PropertyDeclaration.ofField(Bean.class.getDeclaredField("secret")));
        assertEquals("s", field.read(bean));
        assertEquals(PropertyDeclaration.Kind.FIELD, field.getDeclaration().getKind());
        assertTrue(field.toString().contains("secret"));

        PropertyAccessor getter = PropertyAccessor.of(PropertyDeclaration.ofGetter(Bean.class.getDeclaredMethod("getSecret")));
        assertEquals("S", getter.read(bean));

        PropertyAccessor primitive = PropertyAccessor.of(PropertyDeclaration.ofField(Bean.class.getDeclaredField("count")));
        assertEquals(7, primitive.read(bean));

        PropertyAccessor component = PropertyAccessor.of(PropertyDeclaration.ofRecordComponent(Pair.class.getRecordComponents()[0]));
        assertEquals("a", component.read(new Pair("a", 1)));

        PropertyAccessor inherited = PropertyAccessor.of(PropertyDeclaration.ofField(Bean.class.getSuperclass().getDeclaredField("base"), Bean.class));
        assertEquals("b", inherited.read(bean));
    }

    @Test
    public void getterExceptionsPropagateOrWrap() throws Exception {
        PropertyAccessor unchecked = PropertyAccessor.of(PropertyDeclaration.ofGetter(Bean.class.getDeclaredMethod("getBoom")));
        assertThrows(IllegalStateException.class, () -> unchecked.read(new Bean()));

        PropertyAccessor checked = PropertyAccessor.of(PropertyDeclaration.ofGetter(Bean.class.getDeclaredMethod("getChecked")));
        UnrulyException e = assertThrows(UnrulyException.class, () -> checked.read(new Bean()));
        assertInstanceOf(IOException.class, e.getCause());

        assertThrows(IllegalArgumentException.class, () -> unchecked.read(null));
        assertThrows(ClassCastException.class, () -> unchecked.read("wrong type"));
    }

    @Test
    public void inaccessibleMembersFailWithTheOpensDirective() throws Exception {
        // A JDK internal field: java.base does not open java.lang to us.
        PropertyDeclaration declaration = PropertyDeclaration.ofField(String.class.getDeclaredField("hash"));
        InaccessiblePropertyException e = assertThrows(InaccessiblePropertyException.class, () -> PropertyAccessor.of(declaration));
        assertSame(declaration, e.getDeclaration());
        assertTrue(e.getMessage().contains("opens java.lang to org.rulii.validation.beans"));
        assertNotNull(e.getCause());
    }

    @Test
    public void lazyAccessorsResolveOnFirstRead() throws Exception {
        PropertyDeclaration inaccessible = PropertyDeclaration.ofField(String.class.getDeclaredField("hash"));
        PropertyAccessor lazy = PropertyAccessor.lazy(inaccessible);
        assertFalse(lazy.isResolved(), "nothing is touched until a read");
        assertTrue(lazy.toString().contains("unresolved"));
        assertThrows(InaccessiblePropertyException.class, () -> lazy.read("text"));

        Bean bean = new Bean();
        bean.secret = "s";
        PropertyAccessor field = PropertyAccessor.lazy(PropertyDeclaration.ofField(Bean.class.getDeclaredField("secret")));
        assertFalse(field.isResolved());
        assertEquals("s", field.read(bean));
        assertTrue(field.isResolved());
        assertEquals("s", field.read(bean));
        assertTrue(PropertyAccessor.of(PropertyDeclaration.ofField(Bean.class.getDeclaredField("secret"))).isResolved());
        assertThrows(IllegalArgumentException.class, () -> PropertyAccessor.lazy(null));
    }

    public static class Base {
        protected String base = "b";
    }

    public static class Bean extends Base {
        private String secret;
        private int count = 7;

        public String getSecret() { return secret == null ? null : secret.toUpperCase(); }
        public String getBoom() { throw new IllegalStateException("boom"); }
        public String getChecked() throws IOException { throw new IOException("checked"); }
    }

    public record Pair(String left, int right) {}
}
