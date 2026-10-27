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
 * Defaults for the {@link Expr @Expr} declarations of a class or of a package (declared in
 * {@code package-info.java}). An {@code @Expr} that leaves {@link Expr#lang()} empty takes the
 * language of the {@code @ExprDefaults} on the class declaring the annotated member (the class itself
 * for a class-level {@code @Expr}), else of the one on that class's package, else the default
 * language of the {@code ScriptProcessorManager}. Not inherited: a subclass's members are governed by
 * the subclass's own defaults.
 *
 * @author Max Arulananthan
 * @since 1.0
 * @see Expr
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExprDefaults {

    /**
     * The scripting language of the {@code @Expr} declarations in scope that name none. Must not be
     * blank.
     *
     * @return the language name.
     */
    String lang();
}
