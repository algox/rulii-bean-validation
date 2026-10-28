/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hibernate.validator.constraintvalidation;

import jakarta.validation.ConstraintValidatorContext;

/**
 * A test-only stand-in for Hibernate Validator's context interface, under its real name: what a
 * validator written for the reference implementation asks our context to unwrap. The library does
 * not support it; the test checks that the refusal names the alternative.
 */
public interface HibernateConstraintValidatorContext extends ConstraintValidatorContext {

    HibernateConstraintValidatorContext addMessageParameter(String name, Object value);
}
