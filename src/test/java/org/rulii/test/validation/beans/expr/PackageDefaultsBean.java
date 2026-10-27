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
package org.rulii.test.validation.beans.expr;

import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.ExprDefaults;

/**
 * A bean whose {@code @Expr} declarations take their language from the package, except where the
 * class or the annotation says otherwise.
 */
public class PackageDefaultsBean {

    @Expr(value = "${low <= high}", errorCode = "range.low")
    public int low;

    @Expr(value = "high >= low", lang = "java", errorCode = "range.high")
    public int high;

    public PackageDefaultsBean(int low, int high) {
        super();
        this.low = low;
        this.high = high;
    }

    /**
     * A nested class's own defaults win over the package's.
     */
    @ExprDefaults(lang = "js")
    public static class Overriding {

        @Expr(value = "ctx.value > 0", errorCode = "positive")
        public int value;
    }
}
