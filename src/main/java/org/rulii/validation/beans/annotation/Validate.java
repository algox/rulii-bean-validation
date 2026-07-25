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
package org.rulii.validation.beans.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches one or more named runnables from the rulii RuleRegistry (a Rule, RuleSet, or
 * RuleFlow) to a bean class, field, getter, or record component. Names are resolved at
 * validation time — swapping what is registered under a name changes validation behavior
 * with no recompile of the domain class. A name that resolves to nothing fails loudly.
 *
 * <p>At class level the runnable sees the whole bean (root and properties bound); at
 * property level the annotated property's value is additionally in scope. Multiple names
 * execute in declaration order, all reporting into the same violations report.</p>
 *
 * <p>{@code @Validate} is group-agnostic: it runs on every validation call regardless of
 * requested validation groups. Operation-specific selection belongs in the referenced
 * registry rules themselves.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Validate {

    /**
     * The registry names of the runnables to execute, in declaration order.
     *
     * @return the registry names.
     */
    String[] value();
}
