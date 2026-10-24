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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches named rulii runnables from the validation {@code RuleRegistry} to a bean class or to one
 * of its properties. Each name refers to anything the registry holds (a {@code Rule},
 * {@code RuleSet} or {@code RuleFlow}); the runnables execute in declaration order and report into
 * the same violations as the constraints.
 *
 * <p>At class level the runnable sees the whole bean: the root object and every property are bound.
 * On a field, getter or record component the runnable additionally sees that property's value, and
 * its violations report at that property's path. Setters and other non-getter methods cannot carry
 * the annotation; such a declaration fails fast when the class is read, since executable validation
 * runs constraints, not registry rules.</p>
 *
 * <p>Names are resolved at validation time, so what is registered under a name can change without
 * recompiling the domain class; a name that resolves to nothing fails loudly, never silently. The
 * annotation carries no {@code groups()}: it is group-agnostic and runs on every validation
 * regardless of the requested groups. Operation-specific selection belongs to the referenced rules
 * themselves.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Validate {

    /**
     * The registry names of the runnables to execute, in declaration order. At least one name is
     * required; a name must not be blank and must not repeat.
     *
     * @return the registry names.
     */
    String[] value();
}
