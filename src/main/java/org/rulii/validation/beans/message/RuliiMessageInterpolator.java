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
import jakarta.validation.metadata.ConstraintDescriptor;
import org.rulii.context.RuleContextOptions;
import org.rulii.context.StandardRuleContextOptions;
import org.rulii.lib.spring.util.Assert;
import org.rulii.text.MessageFormatter;
import org.rulii.text.MessageResolver;
import org.rulii.text.ParameterInfo;
import org.rulii.validation.beans.mapper.ConstraintMapper;
import org.rulii.validation.beans.mapper.ConstraintMapperRegistry;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The specification's {@link MessageInterpolator}, rendering exactly as the validator does: the
 * template is resolved through a {@link BeanValidationMessageResolver} when it is a message key and
 * translated as a literal otherwise, then formatted through a {@link BeanValidationMessageFormatter}
 * with the constraint's attributes and the validated value as parameters. It is the bootstrap's
 * default interpolator, and a way to render a template outside a validation.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiMessageInterpolator implements MessageInterpolator {

    private final ConstraintMapperRegistry mappers;
    private final MessageResolver resolver;
    private final MessageFormatter formatter;

    /**
     * Creates the interpolator over the standard context options.
     *
     * @param mappers the constraint mappers.
     */
    public RuliiMessageInterpolator(ConstraintMapperRegistry mappers) {
        this(mappers, StandardRuleContextOptions.build());
    }

    /**
     * Creates the interpolator over the given context options, whose resolver and formatter are
     * consulted the way the validator consults them.
     *
     * @param mappers the constraint mappers.
     * @param options the base options.
     */
    public RuliiMessageInterpolator(ConstraintMapperRegistry mappers, RuleContextOptions options) {
        super();
        Assert.notNull(mappers, "mappers cannot be null.");
        Assert.notNull(options, "options cannot be null.");
        this.mappers = mappers;
        this.resolver = new BeanValidationMessageResolver(mappers, options.getMessageResolver());
        this.formatter = new BeanValidationMessageFormatter(options.getMessageFormatter(), resolver);
    }

    public MessageResolver getMessageResolver() {
        return resolver;
    }

    public MessageFormatter getMessageFormatter() {
        return formatter;
    }

    @Override
    public String interpolate(String messageTemplate, Context context) {
        return interpolate(messageTemplate, context, Locale.getDefault());
    }

    @Override
    public String interpolate(String messageTemplate, Context context, Locale locale) {
        Assert.notNull(messageTemplate, "messageTemplate cannot be null.");
        ConstraintDescriptor<?> descriptor = context != null ? context.getConstraintDescriptor() : null;
        Map<String, Object> attributes = descriptor != null ? descriptor.getAttributes() : Map.of();
        Map<String, String> names = parameterNames(descriptor);
        Locale effective = locale != null ? locale : Locale.getDefault();
        // The constraint's own default key resolves through the resolver, translated with the constraint's
        // parameter names; anything else is a literal whose key placeholders the formatter expands, exactly
        // as the validator's materialized rules do.
        String key = MessageTemplates.templateKey(messageTemplate);
        String template = key != null && descriptor != null && key.equals(descriptor.getAnnotation().annotationType().getName() + ".message")
                ? resolver.resolve(effective, key, null) : null;

        if (template == null) template = MessageTemplates.translate(messageTemplate, names, MessageTemplates.VALIDATED_VALUE);

        List<ParameterInfo> parameters = new ArrayList<>(attributes.size() + 1);

        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            parameters.add(new ParameterInfo(parameters.size(), names.getOrDefault(attribute.getKey(), attribute.getKey()), attribute.getValue()));
        }

        parameters.add(new ParameterInfo(parameters.size(), MessageTemplates.VALIDATED_VALUE, context != null ? context.getValidatedValue() : null));

        return formatter.format(effective, template, parameters.toArray(new ParameterInfo[0]));
    }

    private Map<String, String> parameterNames(ConstraintDescriptor<?> descriptor) {
        if (descriptor == null || descriptor.getAnnotation() == null) return Map.of();
        Class<? extends Annotation> type = descriptor.getAnnotation().annotationType();
        ConstraintMapper<?> mapper = mappers.find(type);
        return mapper != null ? mapper.getParameterNames() : Map.of();
    }

    @Override
    public String toString() {
        return "RuliiMessageInterpolator{}";
    }
}
