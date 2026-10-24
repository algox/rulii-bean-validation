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
package org.rulii.test.validation.beans.traversal;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.spi.PersistenceProviderResolverHolder;
import jakarta.persistence.spi.LoadState;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceProviderResolver;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.ProviderUtil;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Payload;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.rulii.validation.beans.BeanValidationException;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.PropertyPath;
import org.rulii.validation.beans.bootstrap.RuliiConfiguration;
import org.rulii.validation.beans.bootstrap.RuliiValidationProvider;
import org.rulii.validation.beans.bootstrap.RuliiValidatorFactory;
import org.rulii.validation.beans.traversal.JpaTraversableResolver;
import org.rulii.validation.beans.traversal.TraversableResolverException;
import org.rulii.validation.beans.traversal.TraversableResolvers;
import org.rulii.validation.beans.traversal.TraverseAllTraversableResolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The traversable resolver: what it is asked and when, what an unreachable or non-cascadable property
 * skips, the per-call cache, the entry points that consult it, the defaults and the JPA resolver, the
 * wiring, and failures.
 */
public class TraversableResolverTest {

    public TraversableResolverTest() {
        super();
    }

    private static Set<String> paths(Iterable<? extends ConstraintViolation<?>> violations) {
        Set<String> result = new TreeSet<>();
        for (ConstraintViolation<?> violation : violations) {
            result.add(violation.getPropertyPath().toString());
        }
        return result;
    }

    private static BeanValidator validator(TraversableResolver resolver) {
        return BeanValidator.builder().traversableResolver(resolver).build();
    }

    @Test
    public void everyReachablePropertyIsAskedAboutOnceAndClassLevelParametersAreNot() {
        Recording recording = new Recording();
        Customer customer = new Customer();

        Set<String> paths = paths(validator(recording).validate(customer));

        assertEquals(Set.of("", "name", "code", "address.city", "tags[0].<list element>", "orders[0].id", "history", "history[0].id", "history[1].id"), paths);
        assertEquals(1, customer.codeReads);
        assertEquals(recording.calls.size(), new HashSet<>(recording.calls).size(), "each question asked once per object and property: " + recording.calls);
        assertTrue(recording.calls.contains("isReachable name FIELD Customer root=Customer at ''"), recording.calls.toString());
        assertTrue(recording.calls.contains("isReachable code METHOD Customer root=Customer at ''"), "a getter constraint is METHOD");
        assertTrue(recording.calls.contains("isReachable address FIELD Customer root=Customer at ''"));
        assertTrue(recording.calls.contains("isCascadable address FIELD Customer root=Customer at ''"));
        assertTrue(recording.calls.contains("isReachable tags FIELD Customer root=Customer at ''"));
        assertFalse(recording.calls.contains("isCascadable tags FIELD Customer root=Customer at ''"), "nothing to cascade into");
        assertTrue(recording.calls.contains("isCascadable orders FIELD Customer root=Customer at ''"), "element cascade");
        assertTrue(recording.calls.contains("isReachable history FIELD Customer root=Customer at ''"), "once for the constraint and the cascade");
        assertTrue(recording.calls.contains("isCascadable history FIELD Customer root=Customer at ''"));
        assertTrue(recording.calls.contains("isReachable city FIELD Address root=Customer at 'address'"), "reached objects are asked about at their path");
        assertTrue(recording.calls.contains("isReachable id FIELD Order root=Customer at 'orders[0]'"));
        assertTrue(recording.calls.contains("isReachable id FIELD Order root=Customer at 'history[1]'"));
        assertEquals(13, recording.calls.size(), recording.calls.toString());
    }

    @Test
    public void anUnreachablePropertyIsNeitherValidatedNorReadNorCascaded() {
        Recording recording = new Recording();
        recording.unreachable.addAll(Set.of("orders", "code", "tags"));
        Customer customer = new Customer();

        Set<String> paths = paths(validator(recording).validate(customer));

        assertEquals(Set.of("", "name", "address.city", "history", "history[0].id", "history[1].id"), paths);
        assertEquals(0, customer.codeReads, "the getter was never invoked");
        assertFalse(recording.calls.stream().anyMatch(call -> call.startsWith("isCascadable orders")), "isCascadable is not asked for an unreachable property");
    }

    @Test
    public void aNonCascadablePropertyKeepsItsConstraintsAndDoesNotCascade() {
        Recording recording = new Recording();
        recording.notCascadable.addAll(Set.of("history", "address", "orders"));

        Set<String> paths = paths(validator(recording).validate(new Customer()));

        assertEquals(Set.of("", "name", "code", "tags[0].<list element>", "history"), paths, "history's @Size still runs; nothing descends");
        assertTrue(recording.calls.contains("isCascadable history FIELD Customer root=Customer at ''"));
    }

    @Test
    public void validatePropertyAsksAndValidateValueDoesNot() {
        Recording recording = new Recording();
        recording.unreachable.add("orders");
        BeanValidator validator = validator(recording);
        Customer customer = new Customer();

        assertEquals(Set.of(), paths(validator.validateProperty(customer, "orders")), "out of reach");
        assertEquals(Set.of("tags[0].<list element>"), paths(validator.validateProperty(customer, "tags")));
        assertEquals(Set.of("code"), paths(validator.validateProperty(customer, "code")));
        assertTrue(recording.calls.contains("isReachable orders FIELD Customer root=Customer at ''"), recording.calls.toString());
        assertTrue(recording.calls.contains("isReachable code METHOD Customer root=Customer at ''"));

        recording.calls.clear();
        assertEquals(Set.of("name"), paths(validator.validateValue(Customer.class, "name", "")));
        assertEquals(Set.of("tags[0].<list element>"), paths(validator.validateValue(Customer.class, "tags", List.of(""))), "no object, nothing to ask about");
        assertTrue(recording.calls.isEmpty(), recording.calls.toString());
    }

    @Test
    public void parametersAreNotAskedAboutButReachedObjectsAre() throws Exception {
        Recording recording = new Recording();
        BeanValidator validator = validator(recording);
        Method place = Customer.class.getMethod("place", String.class, Order.class);

        Set<String> paths = paths(validator.forExecutables().validateParameters(new Customer(), place, new Object[] {"", new Order()}));

        assertEquals(Set.of("place.id", "place.order.id"), paths);
        assertEquals(List.of("isReachable id FIELD Order root=Customer at 'place.order'"), recording.calls);
    }

    @Test
    public void defaultsAndWiring() {
        TraversableResolver standard = TraversableResolvers.getDefault();

        assertInstanceOf(JpaTraversableResolver.class, standard, "the persistence API is on the test classpath");
        assertSame(standard, TraversableResolvers.getDefault(), "decided once");
        assertInstanceOf(TraverseAllTraversableResolver.class, TraversableResolvers.getDefault(new ClassLoader(null) { }), "no persistence API: reach everything");
        assertSame(TraversableResolvers.traverseAll(), TraversableResolvers.getDefault(new ClassLoader(null) { }));
        assertFalse(TraversableResolvers.consults(null));
        assertFalse(TraversableResolvers.consults(TraversableResolvers.traverseAll()));
        assertTrue(TraversableResolvers.consults(standard));
        assertTrue(TraversableResolvers.traverseAll().isReachable(null, null, null, null, null));
        assertTrue(TraversableResolvers.traverseAll().isCascadable(null, null, null, null, null));

        assertSame(standard, BeanValidator.builder().build().getTraversableResolver());
        assertSame(standard, ((RuliiValidatorFactory) Validation.buildDefaultValidatorFactory()).getTraversableResolver());
        assertThrows(IllegalArgumentException.class, () -> BeanValidator.builder().traversableResolver(null));

        Recording recording = new Recording();
        recording.unreachable.add("name");
        RuliiConfiguration configuration = Validation.byProvider(RuliiValidationProvider.class).configure();
        RuliiValidatorFactory factory = (RuliiValidatorFactory) configuration.traversableResolver(recording).buildValidatorFactory();
        assertSame(recording, factory.getValidator().getTraversableResolver());
        assertFalse(paths(factory.getValidator().validate(new Customer())).contains("name"));

        RuliiValidatorFactory plain = (RuliiValidatorFactory) Validation.buildDefaultValidatorFactory();
        BeanValidator overridden = plain.usingContext().traversableResolver(recording).getValidator();
        assertNotSame(plain.getValidator(), overridden);
        assertSame(recording, overridden.getTraversableResolver());
        assertFalse(paths(overridden.validate(new Customer())).contains("name"));
        assertTrue(paths(plain.getValidator().validate(new Customer())).contains("name"), "the factory's validator is untouched");
    }

    @Test
    public void theJpaResolverAsksThePersistenceProviderWhatIsLoaded() {
        PersistenceProviderResolverHolder.setPersistenceProviderResolver(new PersistenceProviderResolver() {
            @Override
            public List<PersistenceProvider> getPersistenceProviders() {
                return List.of(new LazyOrdersProvider());
            }

            @Override
            public void clearCachedProviders() {
            }
        });

        try {
            JpaTraversableResolver resolver = new JpaTraversableResolver();
            Path.Node orders = PropertyPath.root().property("orders").getLeafNode();
            Path.Node name = PropertyPath.root().property("name").getLeafNode();
            Customer customer = new Customer();

            assertFalse(resolver.isReachable(customer, orders, Customer.class, PropertyPath.root(), ElementType.FIELD));
            assertTrue(resolver.isReachable(customer, name, Customer.class, PropertyPath.root(), ElementType.FIELD));
            assertTrue(resolver.isReachable(null, orders, Customer.class, PropertyPath.root(), ElementType.FIELD), "nothing to load for no object");
            assertTrue(resolver.isCascadable(customer, orders, Customer.class, PropertyPath.root(), ElementType.FIELD));

            // The runtime default is this resolver: validation leaves the association that is not loaded alone.
            Set<String> paths = paths(BeanValidator.builder().build().validate(customer));
            assertEquals(Set.of("", "name", "code", "address.city", "tags[0].<list element>", "history", "history[0].id", "history[1].id"), paths);
        } finally {
            PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);
        }

        BeanValidationException e = assertThrows(BeanValidationException.class, () -> new JpaTraversableResolver(new ClassLoader(null) { }));
        assertTrue(e.getMessage().contains("jakarta.persistence.Persistence"));
    }

    @Test
    public void aFailingResolverFailsTheCall() {
        TraversableResolver failing = new TraversableResolver() {
            @Override
            public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                       Path pathToTraversableObject, ElementType elementType) {
                throw new IllegalStateException("no session");
            }

            @Override
            public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                        Path pathToTraversableObject, ElementType elementType) {
                return true;
            }
        };

        // The resolver is asked by the flow commands, outside any rule, so core does not wrap the failure.
        TraversableResolverException e = assertThrows(TraversableResolverException.class, () -> validator(failing).validate(new Customer()));
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(e.getMessage().contains("isReachable") && e.getMessage().contains(Customer.class.getName()), e.getMessage());
        assertNotNull(e.getPropertyName());
    }

    // --- fixtures -------------------------------------------------------------------------------

    public static final class Recording implements TraversableResolver {

        final List<String> calls = new ArrayList<>();
        final Set<String> unreachable = new HashSet<>();
        final Set<String> notCascadable = new HashSet<>();

        public Recording() {
            super();
        }

        private static String describe(Object object, Path.Node property, Class<?> root, Path path, ElementType type) {
            return property.getName() + " " + type + " " + object.getClass().getSimpleName() + " root=" + root.getSimpleName() + " at '" + path + "'";
        }

        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                   Path pathToTraversableObject, ElementType elementType) {
            calls.add("isReachable " + describe(traversableObject, traversableProperty, rootBeanType, pathToTraversableObject, elementType));
            return !unreachable.contains(traversableProperty.getName());
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                                    Path pathToTraversableObject, ElementType elementType) {
            calls.add("isCascadable " + describe(traversableObject, traversableProperty, rootBeanType, pathToTraversableObject, elementType));
            return !notCascadable.contains(traversableProperty.getName());
        }
    }

    /**
     * A persistence provider whose only job is to report the {@code orders} attribute of any entity as
     * not loaded.
     */
    public static final class LazyOrdersProvider implements PersistenceProvider {

        public LazyOrdersProvider() {
            super();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public EntityManagerFactory createEntityManagerFactory(String emName, Map map) {
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map map) {
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void generateSchema(PersistenceUnitInfo info, Map map) {
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean generateSchema(String persistenceUnitName, Map map) {
            return false;
        }

        @Override
        public ProviderUtil getProviderUtil() {
            return new ProviderUtil() {
                @Override
                public LoadState isLoadedWithoutReference(Object entity, String attributeName) {
                    return "orders".equals(attributeName) ? LoadState.NOT_LOADED : LoadState.LOADED;
                }

                @Override
                public LoadState isLoadedWithReference(Object entity, String attributeName) {
                    return LoadState.UNKNOWN;
                }

                @Override
                public LoadState isLoaded(Object entity) {
                    return LoadState.LOADED;
                }
            };
        }
    }

    @Constraint(validatedBy = AlwaysInvalid.class)
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Consistent {
        String message() default "inconsistent";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class AlwaysInvalid implements ConstraintValidator<Consistent, Object> {
        public AlwaysInvalid() {
            super();
        }

        @Override
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            return false;
        }
    }

    @Consistent
    public static class Customer {
        @NotBlank
        String name = "";

        @Valid
        Address address = new Address();

        List<@NotBlank String> tags = List.of("");

        List<@Valid Order> orders = List.of(new Order());

        @Valid
        @Size(max = 1)
        List<Order> history = List.of(new Order(), new Order());

        int codeReads;

        @NotNull
        public String getCode() {
            codeReads++;
            return null;
        }

        public void place(@NotBlank String id, @Valid Order order) {
        }
    }

    public static class Address {
        @NotBlank
        String city = "";
    }

    public static class Order {
        @NotBlank
        String id = "";
    }
}
