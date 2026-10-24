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
package org.rulii.validation.beans.message;

import jakarta.validation.MessageInterpolator;
import org.rulii.validation.beans.BeanValidationException;

/**
 * A configured {@link MessageInterpolator} failed to render a message. Thrown while the rule reports
 * its violation, so it reaches the caller inside core's rule execution exception, as a constraint
 * validator's failure does.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public class MessageInterpolationException extends BeanValidationException {

    private static final long serialVersionUID = 0L;

    private final String messageTemplate;

    /**
     * Creates the exception.
     *
     * @param messageTemplate the template being rendered.
     * @param interpolator the interpolator that failed.
     * @param cause what it threw.
     */
    public MessageInterpolationException(String messageTemplate, MessageInterpolator interpolator, Throwable cause) {
        super("message interpolator [" + interpolator + "] failed to interpolate template [" + messageTemplate + "]", cause);
        this.messageTemplate = messageTemplate;
    }

    /**
     * The template being rendered.
     *
     * @return the template.
     */
    public String getMessageTemplate() {
        return messageTemplate;
    }
}
