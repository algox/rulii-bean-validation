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
package org.rulii.validation.beans.model;

import org.rulii.lib.spring.util.Assert;
import org.rulii.model.Definition;
import org.rulii.model.SourceDefinition;
import org.rulii.validation.beans.Validate;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Definition of one {@link Validate @Validate} occurrence: the registry names of the runnables it
 * attaches, in declaration order, and where it is declared (a property declaration or a class).
 * The runnables are resolved at validation time; nothing here consults a registry.
 *
 * <p>A property or a bean class may carry several occurrences across its declarations and hierarchy
 * levels; each keeps its own source so diagnostics can name the exact member. A rule name may not
 * be blank and may not repeat, within one occurrence or across the occurrences of the same property
 * or class.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ValidateDefinition implements Definition {

    private final ConstraintSource declarationSource;
    private final Validate annotation;
    private final List<String> ruleNames;
    private final SourceDefinition source;

    private ValidateDefinition(ConstraintSource declarationSource, Validate annotation, List<String> ruleNames) {
        super();
        this.declarationSource = declarationSource;
        this.annotation = annotation;
        this.ruleNames = ruleNames;
        this.source = declarationSource.getSourceDefinition();
    }

    /**
     * Loads the {@code @Validate} occurrence declared on the given property declaration, if any.
     *
     * @param sourceDeclaration the property declaration to inspect.
     * @return a list holding the definition; empty if the declaration carries no {@code @Validate}.
     * @throws ValidateDeclarationException if the annotation declares no name, a blank name or a
     *         repeated name.
     */
    public static List<ValidateDefinition> load(ValueDeclaration sourceDeclaration) {
        Assert.notNull(sourceDeclaration, "sourceDeclaration cannot be null.");
        return load(new DeclarationConstraintSource(sourceDeclaration), sourceDeclaration.getDeclaredAnnotations());
    }

    /**
     * Loads the {@code @Validate} occurrence declared on the given class or interface, if any (its
     * own declared annotations only; the hierarchy is walked by the bean definition).
     *
     * @param type the class or interface to inspect.
     * @return a list holding the definition; empty if the type carries no {@code @Validate}.
     * @throws ValidateDeclarationException if the annotation declares no name, a blank name or a
     *         repeated name.
     */
    public static List<ValidateDefinition> load(Class<?> type) {
        Assert.notNull(type, "type cannot be null.");
        return load(new ClassConstraintSource(type), Arrays.asList(type.getDeclaredAnnotations()));
    }

    private static List<ValidateDefinition> load(ConstraintSource declarationSource, List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Validate validate) {
                return Collections.singletonList(load(declarationSource, validate));
            }
        }

        return Collections.emptyList();
    }

    /**
     * Loads the definition of one {@code @Validate} annotation declared at the given source.
     *
     * @param declarationSource where the annotation is declared.
     * @param annotation the annotation.
     * @return the definition.
     * @throws ValidateDeclarationException if the annotation declares no name, a blank name or a
     *         repeated name.
     */
    public static ValidateDefinition load(ConstraintSource declarationSource, Validate annotation) {
        Assert.notNull(declarationSource, "declarationSource cannot be null.");
        Assert.notNull(annotation, "annotation cannot be null.");

        String[] names = annotation.value();

        if (names.length == 0) {
            throw new ValidateDeclarationException("@Validate on [" + declarationSource.describe()
                    + "] declares no rule name; at least one registry name is required.", declarationSource, null);
        }

        Set<String> seen = new LinkedHashSet<>();

        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new ValidateDeclarationException("@Validate on [" + declarationSource.describe()
                        + "] declares a blank rule name; every entry must be a registry name.", declarationSource, name);
            }

            if (!seen.add(name)) {
                throw new ValidateDeclarationException("@Validate on [" + declarationSource.describe()
                        + "] references rule [" + name + "] more than once.", declarationSource, name);
            }
        }

        return new ValidateDefinition(declarationSource, annotation, List.copyOf(seen));
    }

    /**
     * Checks that no rule name repeats across the given occurrences, which all belong to one
     * property or one bean class.
     *
     * @param definitions the occurrences, in declaration order.
     * @param owner what they belong to, for the message (e.g. {@code property [name]}).
     * @throws ValidateDeclarationException if a rule name is referenced by more than one occurrence.
     */
    static void checkDistinct(List<ValidateDefinition> definitions, String owner) {
        if (definitions.size() < 2) return;

        List<String> names = new ArrayList<>();
        List<ValidateDefinition> owners = new ArrayList<>();

        for (ValidateDefinition definition : definitions) {
            for (String name : definition.ruleNames) {
                int index = names.indexOf(name);

                if (index >= 0) {
                    throw new ValidateDeclarationException(owner + " references rule [" + name + "] more than once in @Validate:"
                            + " on [" + owners.get(index).declarationSource.describe() + "] and on ["
                            + definition.declarationSource.describe() + "].", definition.declarationSource, name);
                }

                names.add(name);
                owners.add(definition);
            }
        }
    }

    /**
     * Simple name of the annotation ({@code Validate}).
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return Validate.class.getSimpleName();
    }

    @Override
    public SourceDefinition getSource() {
        return source;
    }

    /**
     * Where this occurrence is declared: the property declaration (field, getter or record
     * component) or the class or interface.
     *
     * @return the declaration source.
     */
    public ConstraintSource getDeclarationSource() {
        return declarationSource;
    }

    /**
     * The original annotation instance.
     *
     * @return the annotation.
     */
    public Validate getAnnotation() {
        return annotation;
    }

    /**
     * Registry names of the runnables to execute, in declaration order.
     *
     * @return the rule names; never empty.
     */
    public List<String> getRuleNames() {
        return ruleNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidateDefinition that = (ValidateDefinition) o;
        return declarationSource.equals(that.declarationSource) && ruleNames.equals(that.ruleNames);
    }

    @Override
    public int hashCode() {
        return 31 * declarationSource.hashCode() + ruleNames.hashCode();
    }

    @Override
    public String toString() {
        return "@Validate" + ruleNames + " on " + declarationSource.describe();
    }
}
