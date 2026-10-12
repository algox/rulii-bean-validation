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
package org.rulii.validation.beans.extract;

import jakarta.validation.ConstraintDeclarationException;
import org.rulii.validation.beans.util.reflect.Members;

import java.lang.reflect.Member;

/**
 * Thrown at metadata-read time when a constraint (or {@code @Valid}) is declared on a
 * container type argument that no registered {@link jakarta.validation.valueextraction.ValueExtractor}
 * can extract. Nothing is skipped silently: register an extractor for the container.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class NoValueExtractorException extends ConstraintDeclarationException {

    private final Member member;
    private final Class<?> containerType;
    private final int typeArgumentIndex;

    public NoValueExtractorException(Member member, Class<?> containerType, int typeArgumentIndex) {
        super("no value extractor found for type argument [" + typeArgumentIndex + "] of ["
                + containerType.getName() + "] declared on property [" + Members.describe(member)
                + "]; register a jakarta.validation.valueextraction.ValueExtractor for it");
        this.member = member;
        this.containerType = containerType;
        this.typeArgumentIndex = typeArgumentIndex;
    }

    /**
     * The property member (field, getter or record accessor) whose type declares the position.
     *
     * @return the member.
     */
    public Member getMember() {
        return member;
    }

    public Class<?> getContainerType() {
        return containerType;
    }

    public int getTypeArgumentIndex() {
        return typeArgumentIndex;
    }
}
