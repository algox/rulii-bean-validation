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
package org.rulii.validation.beans.metadata;

import jakarta.validation.Constraint;
import jakarta.validation.GroupSequence;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import org.rulii.lib.apache.commons.logging.Log;
import org.rulii.lib.apache.commons.logging.LogFactory;
import org.rulii.lib.spring.core.annotation.MergedAnnotation;
import org.rulii.lib.spring.core.annotation.MergedAnnotations;
import org.rulii.lib.spring.core.annotation.RepeatableContainers;
import org.rulii.lib.spring.util.Assert;
import org.rulii.util.reflect.ReflectionUtils;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.BeanValidationException;
import org.rulii.validation.beans.ConstraintMapper;
import org.rulii.validation.beans.ConstraintMapperRegistry;
import org.rulii.validation.beans.annotation.Fatal;
import org.rulii.validation.beans.annotation.Info;
import org.rulii.validation.beans.annotation.Validate;
import org.rulii.validation.beans.annotation.Warning;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the complete validation metadata of a bean class: hand-rolled field, getter, and
 * record-component discovery (any visibility, hierarchy top-down, cumulative semantics),
 * constraint extraction with repeatable and composed constraints, container-element
 * trees, cascade markers, and attached registry rules. Every fail-fast lives here, at
 * metadata-read time.
 *
 * <p>Results are cached per reader instance. A read that fails is cached as a poisoned
 * entry — repeated reads re-throw the same failure immediately. Reading a class eagerly
 * reads the declared types of its cascade targets (with a type-level cycle guard), so
 * all fail-fasts of the statically reachable graph fire on the first read of the root.
 * Cascade targets in {@code java.*}, {@code javax.*}, and {@code jakarta.*} packages are
 * not read eagerly — platform types carry no constraint metadata and their internals are
 * not reflectively accessible on modern JVMs.</p>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BeanMetadataReader {

    private static final Log logger = LogFactory.getLog(BeanMetadataReader.class);

    private final ConstraintMapperRegistry mapperRegistry;
    private final ConcurrentHashMap<Class<?>, Object> cache = new ConcurrentHashMap<>();

    BeanMetadataReader(ConstraintMapperRegistry mapperRegistry) {
        super();
        Assert.notNull(mapperRegistry, "mapperRegistry cannot be null.");
        this.mapperRegistry = mapperRegistry;
    }

    /**
     * Creates a new builder for a BeanMetadataReader.
     *
     * @return a new {@link BeanMetadataReaderBuilder}.
     */
    public static BeanMetadataReaderBuilder builder() {
        return new BeanMetadataReaderBuilder();
    }

    /**
     * Reads (or retrieves from cache) the validation metadata of the given class,
     * eagerly reading the statically reachable cascade graph. A failed read is cached
     * and re-thrown deterministically on every subsequent call.
     *
     * @param beanClass the class to describe.
     * @return the bean's metadata.
     */
    public BeanMetadata read(Class<?> beanClass) {
        Assert.notNull(beanClass, "beanClass cannot be null.");
        Object cached = cache.get(beanClass);

        if (cached == null) {
            readInternal(beanClass, new HashSet<>());
            cached = cache.get(beanClass);
        }

        if (cached instanceof Poisoned poisoned) throw poisoned.exception;
        return (BeanMetadata) cached;
    }

    private void readInternal(Class<?> beanClass, Set<Class<?>> inProgress) {
        Object cached = cache.get(beanClass);
        if (cached instanceof Poisoned poisoned) throw poisoned.exception;
        if (cached != null) return;
        if (!inProgress.add(beanClass)) return;

        try {
            Build build = new Build(beanClass);
            BeanMetadata metadata = build.build();

            for (Class<?> target : build.cascadeTargets) {
                readInternal(target, inProgress);
            }

            cache.putIfAbsent(beanClass, metadata);

            if (logger.isDebugEnabled()) logger.debug("Read metadata for [" + beanClass.getName() + "] "
                    + "properties [" + metadata.getProperties().size() + "].");
        } catch (RuntimeException e) {
            cache.putIfAbsent(beanClass, new Poisoned(e));
            throw e;
        }
    }

    // ------------------------------------------------------------------------
    // Per-class build
    // ------------------------------------------------------------------------

    private final class Build {

        private final Class<?> beanClass;
        private final TypeResolver typeResolver;
        private final Map<String, PropertyCollector> collectors = new LinkedHashMap<>();
        private final List<ConstraintMetadata> classConstraints = new ArrayList<>();
        private final List<String> classValidateRuleNames = new ArrayList<>();
        private final Set<Class<?>> cascadeTargets = new LinkedHashSet<>();

        private Build(Class<?> beanClass) {
            super();
            this.beanClass = beanClass;
            this.typeResolver = new TypeResolver(beanClass);
        }

        private BeanMetadata build() {
            for (Class<?> type : linearize(beanClass)) {
                if (type.isAnnotationPresent(GroupSequence.class)) throw new GroupSequenceNotSupportedException(type);

                processClassLevel(type);

                if (beanClass.isRecord() && type == beanClass) {
                    processRecordComponents();
                    processMethods(type, true);
                } else {
                    if (!type.isInterface()) processFields(type);
                    processMethods(type, false);
                }
            }

            List<PropertyMetadata> properties = new ArrayList<>(collectors.size());
            for (PropertyCollector collector : collectors.values()) {
                properties.add(collector.toMetadata());
            }

            return new BeanMetadata(beanClass, properties, classConstraints, classValidateRuleNames);
        }

        private void processClassLevel(Class<?> type) {
            classConstraints.addAll(extractConstraints(type, null, beanClass));

            Validate validate = type.getAnnotation(Validate.class);
            if (validate != null) classValidateRuleNames.addAll(Arrays.asList(validate.value()));
        }

        private void processFields(Class<?> type) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                if (Modifier.isStatic(field.getModifiers())) continue;

                String name = field.getName();
                checkConvertGroup(field, name);
                PropertyCollector collector = collectors.computeIfAbsent(name, PropertyCollector::new);
                List<ConstraintMetadata> constraints = extractConstraints(field, name,
                        typeResolver.resolve(field.getGenericType()), field.getAnnotatedType());

                if (collector.field != null) {
                    // Subclass redeclares the name: the earlier (superclass) field stays a
                    // secondary constraint source; the subclass field backs the property.
                    if (!collector.fieldConstraints.isEmpty()) {
                        collector.shadowedSources.add(new ConstraintSource(collector.fieldConstraints,
                                PropertyAccessor.forField(beanClass, collector.field)));
                    }
                    collector.fieldConstraints = new ArrayList<>();
                }

                collector.field = field;
                collector.fieldConstraints.addAll(constraints);
                if (field.isAnnotationPresent(Valid.class)) collector.cascaded = true;
                collectValidateNames(field, collector.validateRuleNames);
            }
        }

        private void processMethods(Class<?> type, boolean record) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) continue;
                if (Modifier.isStatic(method.getModifiers())) continue;
                if (record && isRecordAccessor(method)) continue;

                if (!isGetter(method)) {
                    Class<? extends Annotation> offending = findValidationAnnotation(method);
                    if (offending != null) throw new InvalidConstraintPlacementException(beanClass, method, offending);
                    continue;
                }

                String name = getterPropertyName(method);
                checkConvertGroup(method, name);
                PropertyCollector collector = collectors.computeIfAbsent(name, PropertyCollector::new);
                collector.getter = method;
                collector.getterConstraints.addAll(extractConstraints(method, name,
                        typeResolver.resolve(method.getGenericReturnType()), method.getAnnotatedReturnType()));
                if (method.isAnnotationPresent(Valid.class)) collector.cascaded = true;
                collectValidateNames(method, collector.validateRuleNames);
            }
        }

        private void processRecordComponents() {
            for (RecordComponent component : beanClass.getRecordComponents()) {
                String name = component.getName();
                checkConvertGroup(component, name);
                checkConvertGroup(component.getAccessor(), name);
                PropertyCollector collector = collectors.computeIfAbsent(name, PropertyCollector::new);
                collector.recordComponent = component;
                collector.getter = component.getAccessor();

                // Union of component and accessor annotations, deduplicated: Java propagates
                // component annotations onto the implicit accessor (annotation equality
                // collapses the copies); an explicitly declared accessor carries its own.
                Set<Annotation> annotations = new LinkedHashSet<>();
                annotations.addAll(collectConstraintAnnotations(component));
                annotations.addAll(collectConstraintAnnotations(component.getAccessor()));
                annotations.removeAll(arrayComponentAnnotations(component.getAnnotatedType()));

                Type declaredType = typeResolver.resolve(component.getGenericType());
                for (Annotation annotation : annotations) {
                    ConstraintMetadata constraint = buildConstraint(annotation, null, null);
                    validateConstraint(constraint, name, declaredType);
                    collector.getterConstraints.add(constraint);
                }

                if (component.isAnnotationPresent(Valid.class)
                        || component.getAccessor().isAnnotationPresent(Valid.class)) {
                    collector.cascaded = true;
                }

                Set<Annotation> validates = new LinkedHashSet<>();
                Validate onComponent = component.getAnnotation(Validate.class);
                Validate onAccessor = component.getAccessor().getAnnotation(Validate.class);
                if (onComponent != null) validates.add(onComponent);
                if (onAccessor != null) validates.add(onAccessor);
                for (Annotation validate : validates) {
                    collector.validateRuleNames.addAll(Arrays.asList(((Validate) validate).value()));
                }
            }
        }

        private boolean isRecordAccessor(Method method) {
            if (method.getParameterCount() != 0) return false;
            for (RecordComponent component : beanClass.getRecordComponents()) {
                if (component.getName().equals(method.getName())) return true;
            }
            return false;
        }

        // --------------------------------------------------------------------
        // Constraint extraction
        // --------------------------------------------------------------------

        private List<ConstraintMetadata> extractConstraints(AnnotatedElement element, String propertyName, Type declaredType) {
            return extractConstraints(element, propertyName, declaredType, null);
        }

        private List<ConstraintMetadata> extractConstraints(AnnotatedElement element, String propertyName,
                                                            Type declaredType, AnnotatedType annotatedType) {
            List<ConstraintMetadata> result = new ArrayList<>();
            // A mixed-target annotation on an array property (@NotBlank String[]) appears both
            // as a declaration annotation and as a type-use annotation on the array component;
            // it is an element constraint, not a declaration constraint.
            Set<Annotation> componentAnnotations = arrayComponentAnnotations(annotatedType);

            for (Annotation annotation : collectConstraintAnnotations(element)) {
                if (componentAnnotations.contains(annotation)) continue;
                ConstraintMetadata constraint = buildConstraint(annotation, null, null);
                validateConstraint(constraint, propertyName, declaredType);
                result.add(constraint);
            }

            return result;
        }

        private ConstraintMetadata buildConstraint(Annotation annotation, Set<Class<?>> inheritedGroups, Severity inheritedSeverity) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            Map<String, Object> attributes = new TreeMap<>();
            String messageTemplate = null;
            Class<?>[] declaredGroups = new Class<?>[0];
            Class<?>[] payload = new Class<?>[0];

            for (Method attribute : annotationType.getDeclaredMethods()) {
                Object value = readAttribute(annotation, attribute);
                switch (attribute.getName()) {
                    case "message" -> messageTemplate = (String) value;
                    case "groups" -> declaredGroups = (Class<?>[]) value;
                    case "payload" -> payload = (Class<?>[]) value;
                    default -> attributes.put(attribute.getName(), value);
                }
            }

            // Composing constraints inherit the host's groups and payload severity.
            Set<Class<?>> groups = inheritedGroups != null ? inheritedGroups : normalizeGroups(declaredGroups);
            Severity severity = inheritedSeverity != null ? inheritedSeverity : mapSeverity(payload);

            List<Annotation> composingAnnotations = collectConstraintAnnotations(annotationType);
            List<ConstraintMetadata> composing = new ArrayList<>(composingAnnotations.size());

            if (!composingAnnotations.isEmpty()) {
                Map<String, Object> hostValues = new TreeMap<>(attributes);
                if (messageTemplate != null) hostValues.put("message", messageTemplate);
                List<Map<String, Object>> overrides = collectOverrides(annotationType, hostValues, composingAnnotations);

                for (int i = 0; i < composingAnnotations.size(); i++) {
                    ConstraintMetadata composed = buildConstraint(composingAnnotations.get(i), groups, severity);
                    composing.add(applyOverrides(composed, overrides.get(i)));
                }
            }

            boolean reportAsSingleViolation = annotationType.isAnnotationPresent(ReportAsSingleViolation.class);
            return new ConstraintMetadata(annotationType, attributes, messageTemplate, groups, severity,
                    composing, reportAsSingleViolation);
        }

        /**
         * Computes, per composing-annotation position, the attribute overrides declared by
         * the host via {@code @OverridesAttribute} (constraintIndex counts occurrences of
         * the same constraint type; -1 targets all occurrences).
         */
        private List<Map<String, Object>> collectOverrides(Class<? extends Annotation> hostType,
                                                           Map<String, Object> hostValues,
                                                           List<Annotation> composingAnnotations) {
            List<Map<String, Object>> result = new ArrayList<>(composingAnnotations.size());
            for (int i = 0; i < composingAnnotations.size(); i++) {
                result.add(new TreeMap<>());
            }

            for (Method attribute : hostType.getDeclaredMethods()) {
                jakarta.validation.OverridesAttribute[] declared =
                        attribute.getAnnotationsByType(jakarta.validation.OverridesAttribute.class);
                if (declared.length == 0) continue;

                Object value = hostValues.get(attribute.getName());
                for (jakarta.validation.OverridesAttribute override : declared) {
                    int occurrence = 0;
                    for (int i = 0; i < composingAnnotations.size(); i++) {
                        if (composingAnnotations.get(i).annotationType() != override.constraint()) continue;
                        if (override.constraintIndex() == -1 || occurrence == override.constraintIndex()) {
                            result.get(i).put(override.name(), value);
                        }
                        occurrence++;
                    }
                }
            }

            return result;
        }

        private ConstraintMetadata applyOverrides(ConstraintMetadata constraint, Map<String, Object> overrides) {
            if (overrides.isEmpty()) return constraint;

            Map<String, Object> attributes = new TreeMap<>(constraint.getAttributes());
            String messageTemplate = constraint.getMessageTemplate();

            for (Map.Entry<String, Object> override : overrides.entrySet()) {
                if ("message".equals(override.getKey())) {
                    messageTemplate = (String) override.getValue();
                } else {
                    attributes.put(override.getKey(), override.getValue());
                }
            }

            return new ConstraintMetadata(constraint.getAnnotationType(), attributes, messageTemplate,
                    constraint.getGroups(), constraint.getSeverity(), constraint.getComposingConstraints(),
                    constraint.isReportAsSingleViolation());
        }

        private void validateConstraint(ConstraintMetadata constraint, String propertyName, Type declaredType) {
            boolean registered = mapperRegistry.isRegistered(constraint.getAnnotationType());

            if (!registered && !constraint.isComposed()) {
                throw new UnsupportedConstraintException(beanClass, propertyName, constraint.getAnnotationType());
            }

            if (registered) {
                ConstraintMapper mapper = mapperRegistry.getMapper(constraint.getAnnotationType());
                List<Class<?>> supportedTypes = mapper.getSupportedTypes();

                if (supportedTypes != null && !supportedTypes.isEmpty()) {
                    Class<?> raw = typeResolver.resolveRaw(declaredType);

                    if (raw != null && raw != Object.class) {
                        Class<?> checked = raw.isPrimitive() ? ReflectionUtils.getWrapperClass(raw) : raw;
                        boolean applicable = false;

                        for (Class<?> supported : supportedTypes) {
                            if (supported.isAssignableFrom(checked)) {
                                applicable = true;
                                break;
                            }
                        }

                        if (!applicable) {
                            throw new ConstraintApplicabilityException(beanClass, propertyName,
                                    constraint.getAnnotationType(), declaredType, supportedTypes);
                        }
                    }
                }
            }

            for (ConstraintMetadata composing : constraint.getComposingConstraints()) {
                validateConstraint(composing, propertyName, declaredType);
            }
        }

        private List<Annotation> collectConstraintAnnotations(AnnotatedElement element) {
            List<Annotation> result = new ArrayList<>();
            MergedAnnotations merged = MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.DIRECT,
                    RepeatableContainers.standardRepeatables());

            for (MergedAnnotation<Annotation> annotation : merged) {
                if (annotation.getDistance() != 0) continue;
                if (!isConstraintAnnotation(annotation.getType())) continue;
                result.add(annotation.synthesize());
            }

            return result;
        }

        // --------------------------------------------------------------------
        // Container elements
        // --------------------------------------------------------------------

        private ContainerMetadata buildContainer(AnnotatedType annotatedType, String propertyName) {
            return buildContainer(annotatedType, propertyName, false);
        }

        private ContainerMetadata buildContainer(AnnotatedType annotatedType, String propertyName,
                                                 boolean declarationCascaded) {
            if (annotatedType == null) return null;

            if (annotatedType instanceof AnnotatedArrayType arrayType) {
                // @Valid is TYPE_USE: on an array property the JLS mirrors the declaration
                // annotation onto the component. That copy is the property-level cascade
                // (which iterates elements anyway), not an element cascade of its own.
                ContainerElement element = buildElement(ContainerElementRole.ELEMENT,
                        arrayType.getAnnotatedGenericComponentType(), propertyName, declarationCascaded);
                return new ContainerMetadata(ContainerKind.ARRAY, List.of(element));
            }

            if (!(annotatedType instanceof AnnotatedParameterizedType parameterizedType)) return null;

            Class<?> raw = typeResolver.resolveRaw(annotatedType.getType());
            if (raw == null) return null;
            AnnotatedType[] arguments = parameterizedType.getAnnotatedActualTypeArguments();

            if (Map.class.isAssignableFrom(raw) && arguments.length == 2) {
                return new ContainerMetadata(ContainerKind.MAP, List.of(
                        buildElement(ContainerElementRole.KEY, arguments[0], propertyName),
                        buildElement(ContainerElementRole.VALUE, arguments[1], propertyName)));
            }
            if (Optional.class == raw && arguments.length == 1) {
                return new ContainerMetadata(ContainerKind.OPTIONAL, List.of(
                        buildElement(ContainerElementRole.ELEMENT, arguments[0], propertyName)));
            }
            if (Iterable.class.isAssignableFrom(raw) && arguments.length == 1) {
                return new ContainerMetadata(ContainerKind.COLLECTION, List.of(
                        buildElement(ContainerElementRole.ELEMENT, arguments[0], propertyName)));
            }

            return null;
        }

        private ContainerElement buildElement(ContainerElementRole role, AnnotatedType argumentType, String propertyName) {
            return buildElement(role, argumentType, propertyName, false);
        }

        private ContainerElement buildElement(ContainerElementRole role, AnnotatedType argumentType,
                                              String propertyName, boolean suppressMirroredValid) {
            checkConvertGroup(argumentType, propertyName);
            Type elementType = typeResolver.resolve(argumentType.getType());
            List<ConstraintMetadata> constraints = extractConstraints(argumentType, propertyName, elementType);
            boolean cascaded = !suppressMirroredValid && argumentType.isAnnotationPresent(Valid.class);
            ContainerMetadata nested = buildContainer(argumentType, propertyName);

            return new ContainerElement(role, elementType, constraints, cascaded, nested);
        }

        private ContainerMetadata mergeContainers(String propertyName, ContainerMetadata primary, ContainerMetadata secondary) {
            if (secondary == null) return primary;
            if (primary == null || primary.getKind() != secondary.getKind()) {
                if (secondary.hasValidation()) {
                    throw new BeanValidationException("Field and getter of property [" + beanClass.getName() + "."
                            + propertyName + "] declare incompatible container types; the secondary source carries "
                            + "container-element validation that cannot be merged.");
                }
                return primary;
            }

            List<ContainerElement> merged = new ArrayList<>(primary.getElements().size());
            for (int i = 0; i < primary.getElements().size(); i++) {
                ContainerElement first = primary.getElements().get(i);
                ContainerElement second = i < secondary.getElements().size() ? secondary.getElements().get(i) : null;

                if (second == null) {
                    merged.add(first);
                    continue;
                }

                List<ConstraintMetadata> constraints = new ArrayList<>(first.getConstraints());
                constraints.addAll(second.getConstraints());
                merged.add(new ContainerElement(first.getRole(), first.getElementType(), constraints,
                        first.isCascaded() || second.isCascaded(),
                        mergeContainers(propertyName, first.getContainer(), second.getContainer())));
            }

            return new ContainerMetadata(primary.getKind(), merged);
        }

        // --------------------------------------------------------------------
        // Cascade targets & helpers
        // --------------------------------------------------------------------

        private void collectCascadeTarget(Type type) {
            Class<?> raw = typeResolver.resolveRaw(type);
            if (raw == null || raw == Object.class || raw.isPrimitive()) return;
            if (raw.isArray()) {
                collectCascadeTarget(raw.getComponentType());
                return;
            }

            String packageName = raw.getPackageName();
            if (packageName.startsWith("java.") || packageName.startsWith("javax.")
                    || packageName.startsWith("jakarta.")) {
                return;
            }

            cascadeTargets.add(raw);
        }

        private void collectContainerCascades(ContainerMetadata container) {
            if (container == null) return;
            for (ContainerElement element : container.getElements()) {
                if (element.isCascaded()) collectCascadeTarget(element.getElementType());
                collectContainerCascades(element.getContainer());
            }
        }

        private void checkConvertGroup(AnnotatedElement element, String propertyName) {
            if (element.isAnnotationPresent(ConvertGroup.class)
                    || element.isAnnotationPresent(ConvertGroup.List.class)) {
                throw new GroupConversionNotSupportedException(beanClass, propertyName);
            }
        }

        private void collectValidateNames(AnnotatedElement element, List<String> target) {
            Validate validate = element.getAnnotation(Validate.class);
            if (validate != null) target.addAll(Arrays.asList(validate.value()));
        }

        private Class<? extends Annotation> findValidationAnnotation(Method method) {
            List<Annotation> constraints = collectConstraintAnnotations(method);
            if (!constraints.isEmpty()) return constraints.get(0).annotationType();
            if (method.isAnnotationPresent(Valid.class)) return Valid.class;
            if (method.isAnnotationPresent(Validate.class)) return Validate.class;
            return null;
        }

        // --------------------------------------------------------------------
        // Property assembly
        // --------------------------------------------------------------------

        private final class PropertyCollector {

            private final String name;
            private Field field;
            private Method getter;
            private RecordComponent recordComponent;
            private List<ConstraintMetadata> fieldConstraints = new ArrayList<>();
            private final List<ConstraintMetadata> getterConstraints = new ArrayList<>();
            private final List<ConstraintSource> shadowedSources = new ArrayList<>();
            private boolean cascaded;
            private final List<String> validateRuleNames = new ArrayList<>();

            private PropertyCollector(String name) {
                super();
                this.name = name;
            }

            private PropertyMetadata toMetadata() {
                Type declaredType = declaredType();
                PropertyAccessor primaryAccessor = getter != null
                        ? PropertyAccessor.forMethod(beanClass, getter)
                        : PropertyAccessor.forField(beanClass, field);

                // Sources in hierarchy declaration order: shadowed superclass fields, the
                // backing field, then the getter. The field source reuses the primary
                // accessor when the field IS the primary source.
                List<ConstraintSource> sources = new ArrayList<>(shadowedSources);
                if (!fieldConstraints.isEmpty()) {
                    sources.add(new ConstraintSource(fieldConstraints,
                            getter != null ? PropertyAccessor.forField(beanClass, field) : primaryAccessor));
                }
                if (!getterConstraints.isEmpty()) {
                    sources.add(new ConstraintSource(getterConstraints, primaryAccessor));
                }

                ContainerMetadata container = buildPropertyContainer();
                if (container != null && !container.hasValidation()) container = null;

                if (cascaded) collectCascadeTarget(declaredType);
                collectContainerCascades(container);

                return new PropertyMetadata(name, declaredType, field, getter, recordComponent,
                        sources, cascaded, container, validateRuleNames, primaryAccessor);
            }

            private Type declaredType() {
                if (recordComponent != null) return typeResolver.resolve(recordComponent.getGenericType());
                if (getter != null) return typeResolver.resolve(getter.getGenericReturnType());
                return typeResolver.resolve(field.getGenericType());
            }

            private ContainerMetadata buildPropertyContainer() {
                if (recordComponent != null) {
                    return buildContainer(recordComponent.getAnnotatedType(), name,
                            recordComponent.isAnnotationPresent(Valid.class));
                }

                ContainerMetadata primary = getter != null
                        ? buildContainer(getter.getAnnotatedReturnType(), name, getter.isAnnotationPresent(Valid.class))
                        : buildContainer(field.getAnnotatedType(), name, field.isAnnotationPresent(Valid.class));
                ContainerMetadata secondary = getter != null && field != null
                        ? buildContainer(field.getAnnotatedType(), name, field.isAnnotationPresent(Valid.class))
                        : null;

                return mergeContainers(name, primary, secondary);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Static helpers
    // ------------------------------------------------------------------------

    private static List<Class<?>> linearize(Class<?> beanClass) {
        List<Class<?>> result = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        List<Class<?>> chain = new ArrayList<>();

        for (Class<?> type = beanClass; type != null && type != Object.class
                && type != Record.class && type != Enum.class; type = type.getSuperclass()) {
            chain.add(type);
        }

        for (int i = chain.size() - 1; i >= 0; i--) {
            Class<?> type = chain.get(i);
            for (Class<?> interfaceType : type.getInterfaces()) {
                addInterface(interfaceType, result, seen);
            }
            if (seen.add(type)) result.add(type);
        }

        return result;
    }

    private static void addInterface(Class<?> interfaceType, List<Class<?>> result, Set<Class<?>> seen) {
        for (Class<?> parent : interfaceType.getInterfaces()) {
            addInterface(parent, result, seen);
        }
        if (seen.add(interfaceType)) result.add(interfaceType);
    }

    private static boolean isGetter(Method method) {
        if (method.getParameterCount() != 0) return false;

        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && method.getReturnType() != void.class) return true;
        return name.startsWith("is") && name.length() > 2 && method.getReturnType() == boolean.class;
    }

    private static String getterPropertyName(Method method) {
        String name = method.getName();
        return decapitalize(name.startsWith("get") ? name.substring(3) : name.substring(2));
    }

    private static String decapitalize(String name) {
        if (name.isEmpty()) return name;
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) return name;

        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    private static boolean isConstraintAnnotation(Class<? extends Annotation> annotationType) {
        return annotationType.isAnnotationPresent(Constraint.class);
    }

    private static Set<Annotation> arrayComponentAnnotations(AnnotatedType annotatedType) {
        if (!(annotatedType instanceof AnnotatedArrayType arrayType)) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(arrayType.getAnnotatedGenericComponentType().getAnnotations()));
    }

    private static Set<Class<?>> normalizeGroups(Class<?>[] declaredGroups) {
        if (declaredGroups.length == 0) return Set.of(Default.class);
        return new LinkedHashSet<>(Arrays.asList(declaredGroups));
    }

    private static Severity mapSeverity(Class<?>[] payload) {
        for (Class<?> marker : payload) {
            if (Fatal.class.isAssignableFrom(marker)) return Severity.FATAL;
            if (Warning.class.isAssignableFrom(marker)) return Severity.WARNING;
            if (Info.class.isAssignableFrom(marker)) return Severity.INFO;
        }
        // Unrecognized payload classes are vendor metadata by definition — ignored.
        return Severity.ERROR;
    }

    private static Object readAttribute(Annotation annotation, Method attribute) {
        try {
            return attribute.invoke(annotation);
        } catch (Exception e) {
            throw new BeanValidationException("Failed reading attribute [" + attribute.getName() + "] of [@"
                    + annotation.annotationType().getSimpleName() + "].", e);
        }
    }

    private static final class Poisoned {

        private final RuntimeException exception;

        private Poisoned(RuntimeException exception) {
            super();
            this.exception = exception;
        }
    }
}
