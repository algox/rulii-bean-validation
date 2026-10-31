# Migrating from Hibernate Validator

This guide is for a project that validates with Hibernate Validator today and wants to run on
_rulii-bean-validation_. The beans do not change. What changes is one dependency, a few configuration calls, and
the handful of Hibernate-specific features that have a different shape here or no equivalent. Each section says
which.

- [Checklist](#checklist)
- [1. The dependency](#1-the-dependency)
- [2. Bootstrap and configuration](#2-bootstrap-and-configuration)
- [3. Constraints](#3-constraints)
- [4. Custom constraint validators](#4-custom-constraint-validators)
- [5. Groups](#5-groups)
- [6. Method and constructor validation](#6-method-and-constructor-validation)
- [7. Messages](#7-messages)
- [8. Exceptions](#8-exceptions)
- [9. What the specification says and this library does not](#what-the-specification-says-and-this-library-does-not)
- [10. What you gain](#10-what-you-gain)

---

## Checklist

1. Replace `org.hibernate.validator:hibernate-validator` with `org.rulii:rulii-bean-validation`. Keep your EL
   implementation if messages use `${...}`.
2. Make sure Hibernate Validator is really gone from the classpath, Spring Boot's validation starter included.
   With rulii-spring 2.2 or later this library is already there, and only the exclusion is left to do.
3. Replace `Validation.byProvider(HibernateValidator.class)` with `RuliiValidationProvider.class`, and
   `HibernateValidatorConfiguration` calls with their `RuliiConfiguration` counterparts from the table below.
4. Change imports of `org.hibernate.validator.constraints.*` to `org.rulii.validation.beans.constraints.*` for the
   constraints that exist here, and replace the ones that do not.
5. In custom validators, replace `HibernateConstraintValidatorContext` with `RuliiConstraintValidatorContext`.
6. Replace `@GroupSequenceProvider`, `@ScriptAssert` and `@ParameterScriptAssert` as described in sections 5
   and 3.
7. Run your tests. Violations, paths and messages are meant to come out the same; section 9 lists the known
   differences.

---

## 1. The dependency

```xml
<dependency>
    <groupId>org.rulii</groupId>
    <artifactId>rulii-bean-validation</artifactId>
    <version>1.0.0</version>
</dependency>
```

It brings rulii core and `jakarta.validation:jakarta.validation-api` and nothing else. If your messages or
`@Expr` conditions use Jakarta Expression Language, keep an implementation such as
`org.glassfish.expressly:expressly` on the classpath, exactly as before; without one, EL in messages is left as
written instead of failing.

**Remove Hibernate Validator.** The Jakarta bootstrap picks the first provider it finds, so with both on the
classpath `Validation.buildDefaultValidatorFactory()` depends on classpath order. Spring Boot's
`spring-boot-starter-validation` brings Hibernate Validator in; exclude it or depend on this library directly.
If you cannot remove it, select the provider explicitly:

```java
Validator validator = Validation.byProvider(RuliiValidationProvider.class)
        .configure()
        .buildValidatorFactory()
        .getValidator();
```

**Spring Boot.** [rulii-spring](https://github.com/algox/rulii-spring) 2.2 and later brings this library in, so there
is nothing to add and nothing to select: its auto-configuration defines the application's `Validator` bean pinned to
this provider, before Boot's own validation auto-configuration runs, so Hibernate Validator is never bootstrapped even
if it is still present. The same wiring gives `@Validate` the Spring rule registry, so a rule bean is attachable by its
bean name. Keep `spring-boot-starter-validation` for Boot's wiring and its EL implementation, but exclude
`hibernate-validator` from it, for the one code path rulii-spring cannot reach: a library that calls
`Validation.buildDefaultValidatorFactory()` itself. To stay on Hibernate Validator for now, set
`rulii.validation.enabled=false`; rulii-spring then pins the `Validator` bean to Hibernate Validator instead.

The `javax.validation` namespace is not supported. A project still on it migrates to `jakarta.validation` first.

---

## 2. Bootstrap and configuration

`Validation.buildDefaultValidatorFactory()`, `Validation.byProvider`, `ValidatorFactory`, `ValidatorContext`
(`usingContext()`), `unwrap` and `close` work as specified. `RuliiConfiguration` is the provider-specific
configuration, where `HibernateValidatorConfiguration` was.

| Hibernate Validator | Here |
|---|---|
| `failFast(true)` or property `hibernate.validator.fail_fast` | `failFast(true)`; the Hibernate property is honoured, as is `rulii.validation.fail_fast` |
| `messageInterpolator`, `traversableResolver`, `constraintValidatorFactory`, `parameterNameProvider`, `clockProvider`, `addValueExtractor` | the same calls, consulted as the specification says |
| `addMapping(InputStream)` and `META-INF/validation.xml` | not supported; `addMapping` throws `ValidationException`, XML is never read |
| other `hibernate.validator.*` properties | ignored |
| `allowOverridingMethodAlterParameterConstraint`, `allowMultipleCascadedValidationOnReturnValues`, `allowParallelMethodsDefineParameterConstraints` | none; the specification's rules for method hierarchies are enforced as written |
| `getterPropertySelectionStrategy`, `propertyNodeNameProvider` | none; properties are the specification's getters and fields, nodes carry the property name |
| `temporalValidationTolerance` | none |
| `enableExpressionLanguage`, `constraintExpressionLanguageFeatureLevel`, `customViolationExpressionLanguageFeatureLevel` | EL is on in constraint messages; in templates built inside a validator it is off until `enableExpressionLanguage()` on the context. There are no feature levels |
| `localeResolver`, `defaultLocale`, `locales` | none; the locale of a message is the one passed to `interpolate`, else the locale of the rulii context options the validator was built with |
| `scriptEvaluatorFactory` | none; scripting is rulii's (see `@Expr` in section 3) |
| `externalClassLoader` | none; bundles are read through the context class loader |
| `beanMetaDataClassNormalizer`, `initializeBeanMetaData`, predefined-scope factory | none; metadata is read lazily per class and cached per validator |
| `constraintMappings`, programmatic constraint declaration | none; declare constraints with annotations, or attach rules with `@Validate` (see [README](README.md)) |
| `ConstraintValidatorFactory` default | backed by rulii's `ObjectFactory`; with rulii-spring, validators are Spring beans with injection |

Provider-specific options that have no Hibernate counterpart: `registry(RuleRegistry)` for `@Validate`,
`mapper(ConstraintMapper)` to change how an annotation becomes rules, and
`options(RuleContextOptions)` for the rulii context validation runs in.

---

## 3. Constraints

**Standard constraints** behave as specified, with the same null semantics and the same applicable types.
`@Email` judges the domain part by shape alone, as Hibernate Validator does, so `user@localhost` and
`bob@intranet` pass.

**Hibernate's additional constraints** exist here under `org.rulii.validation.beans.constraints` with the same
simple names and attributes: `@Length`, `@Range`, `@URL`, `@UUID`, `@CreditCardNumber`, `@LuhnCheck`,
`@Mod10Check`, `@Mod11Check`, `@EAN`, `@ISBN`, `@UniqueElements`, `@Normalized`, `@CodePointLength`,
`@DurationMin` and `@DurationMax`. A message override written for the Hibernate key
(`org.hibernate.validator.constraints.Length.message`) is honoured for them. Changing the import is the whole
migration.

Not provided, and what to use instead:

| Hibernate constraint | Here |
|---|---|
| `@ScriptAssert` | `@Expr` at class level: `@Expr(value = "${end.isAfter(start)}", lang = "jakarta-el", errorCode = "trip.end")`. `lang` names any rulii script language (Jakarta EL, GraalJS, Groovy, any JSR-223 engine); `alias` is not needed, the script sees properties by name and the object as `$this`; `reportOn` becomes the placement of the annotation |
| `@ParameterScriptAssert` | `@Expr` on the method or constructor; the script sees the arguments by parameter name and as `$parameters` |
| `@ConstraintComposition(OR, ALL_FALSE)` | compose with a rulii rule attached with `@Validate`, or a `RuleSet` |
| `@Currency` | a custom constraint, or `@In` from this library over the currency codes you accept |
| `@BitcoinAddress`, country-specific constraints (`@CPF`, `@CNPJ`, `@TituloEleitoral`, `@PESEL`, `@NIP`, `@REGON`, `@INN`) | a custom constraint; the Hibernate validator classes can be reused as-is behind your own annotation |

**Severities.** A constraint may carry one of the payload markers `Warning`, `Info` or `Fatal` from
`org.rulii.validation.beans.payload` to report at that severity instead of `ERROR`; the violation still appears
in the result. Hibernate has no equivalent, so this is additive.

---

## 4. Custom constraint validators

A `ConstraintValidator` runs unchanged: one instance per constraint occurrence, `initialize` once, `isValid` with
the specification's context. The validator becomes the condition of a rulii rule, and the constraint's
attributes become the rule's parameters.

| Hibernate Validator | Here |
|---|---|
| `context.unwrap(HibernateConstraintValidatorContext.class)` | `context.unwrap(RuliiConstraintValidatorContext.class)`; unwrapping a Hibernate type throws a `ValidationException` whose message names the replacement |
| `addMessageParameter(name, value)`, `addExpressionVariable(name, value)` | the same two methods on `RuliiConstraintValidatorContext`; parameters render as `{name}` in default and custom messages, variables as `${name}` in expressions |
| `withDynamicPayload(Object)`, `getConstraintValidatorPayload` | none |
| `HibernateConstraintValidator.initialize(descriptor, initializationContext)` | none; the plain `initialize(annotation)` |
| `enableExpressionLanguage()` on the context | the same |
| `buildConstraintViolationWithTemplate`, `addPropertyNode`, `addBeanNode`, `addContainerElementNode`, `addParameterNode`, `inIterable`, `inContainer`, `getClockProvider`, `disableDefaultConstraintViolation` | the same |

A validator that needs collaborators is created through the configured `ConstraintValidatorFactory`, which is
backed by rulii's `ObjectFactory`; with rulii-spring the Spring object factory makes validators Spring beans.

The same constraint can also be mapped to rulii rules directly, without a `ConstraintValidator`, through a
`ConstraintMapper` registered on the validator. That is how the standard constraints are mapped and how to make
an annotation of yours a rule that is listed, traced and reusable outside validation.

---

## 5. Groups

Groups, group inheritance, `@GroupSequence` on a class and on a sequence interface, `@ConvertGroup`, and the
implicit grouping of constraints declared on an interface all work as specified.

`@GroupSequenceProvider` has no equivalent. A sequence that depends on the object's state is a decision, so it
belongs in a rule: attach a `RuleFlow` with `@Validate` that reads the object and runs what applies, or request
the groups per call from the code that knows the state. The flow sees the groups being processed as `$groups`.

---

## 6. Method and constructor validation

`forExecutables()` returns the `ExecutableValidator`, with all four methods and the specification's rules on
hierarchies. Interception stays with your framework, as it does with Hibernate Validator: Spring's method
validation drives the `ExecutableValidator` of the configured `Validator`, so it works with this one;
`@ValidateOnExecution` is read by containers, not by providers.

Parameter names come from the configured `ParameterNameProvider`; the default tries rulii's `@Param`, then
reflection, then the compiled bytecode, so real names come back even without `-parameters`.

`@Validate` and `@Expr` are accepted on methods and constructors too, validating the arguments or the return
value as `applyTo` says.

---

## 7. Messages

- `ValidationMessages.properties` at the root of the classpath, with locale variants, overrides the defaults,
  as before.
- `{attribute}` references, `{key}` lookups, `${validatedValue}`, `${formatter.format(...)}` and the escaping
  rules work as specified. EL needs an implementation on the classpath.
- The default texts are the specification's Appendix B, with Hibernate's wording for `@Pattern`
  (`must match "{regexp}"`).
- A configured `MessageInterpolator` is called per violation with the specification's `Context`; a
  `MessageInterpolationException` surfaces as itself.
- An `@Expr` message is a rulii template: `${validatedValue}` and `${value}` are the validated value, and the error
  code resolves through the same bundles. A `@Validate` rule reports whatever message it builds, its error code
  resolving the same way.

---

## 8. Exceptions

The specification's API throws the specification's exceptions:

- Declaration problems are found when a class is first read for validation, as with Hibernate Validator, and
  raise `ConstraintDeclarationException`, `ConstraintDefinitionException`, `UnexpectedTypeException`,
  `GroupDefinitionException` or `ValueExtractorDefinitionException`, through subclasses that name the problem.
- A failure inside validation, in a `ConstraintValidator`, a `MessageInterpolator`, a `ValueExtractor`, a
  `TraversableResolver` or a getter, reaches the caller as a `ValidationException`:
  `ConstraintValidatorException`, `MessageInterpolationException`, `TraversableResolverException`,
  `InaccessiblePropertyException`, or `BeanValidationException` for anything else. A `ValidationException` thrown
  by your own code passes through unchanged.
- A `@Validate` name that resolves to nothing raises `ValidateRuleNotFoundException`; a bad `@Expr` raises
  `ExprDeclarationException` naming the element and the script.
- `validateIn`, which runs inside a rulii context you own, keeps rulii's `UnrulyException` wrapping, as the
  rest of rulii does.

---

## What the specification says and this library does not

The library is not a certified implementation and does not run the TCK. These are the known deviations, grouped
by what they mean for a migration; the [gap analysis](GAP-ANALYSIS.md) has them chapter by chapter. None changes the
outcome for an ordinary bean, and most match Hibernate Validator's own behaviour.

**Deliberately excluded**

- `META-INF/validation.xml` and XML constraint mappings.
- `@ValidateOnExecution` and interception; CDI, JSF and JAX-RS integration.
- JavaFX value extractors.
- `javax.validation`.

**Supersets, matching Hibernate Validator**

- `@Min`, `@Max`, `@DecimalMin` and `@DecimalMax` also accept `double`, `float` and `CharSequence`; `NaN` passes
  the numeric constraints; `-0.0` counts as negative.
- Cycle detection is per validation call, not per navigation path: a shared object reached by two paths is
  reported once.
- Parameter names are recovered from bytecode when the compiler did not keep them.
- A constraint on a field declared `Object` or `Number` is accepted; a value of a type the constraint does not
  support is skipped rather than rejected.

**Lenient or different**

- A missing or mistyped `message`, `groups` or `payload` attribute on a constraint annotation raises
  `IllegalArgumentException`, not `ConstraintDefinitionException`. Attribute names starting with `valid`, the
  presence rules for `validationAppliesTo`, an `@OverridesAttribute` naming a constraint that is not composing,
  and more than one cross-parameter validator per constraint are not checked.
- A class-level `@GroupSequence` may not list another sequence or a duplicate group, which is stricter than the
  specification.
- Return-value `@Valid` declared twice along a hierarchy is accepted; methods are compared by erased signature,
  so generic overrides escape the hierarchy checks.
- Only dotted names in a message are looked up as bundle keys; a user key such as `{foo}` is not.
- The one-argument `interpolate` uses the locale captured when the validator was built, not
  `Locale.getDefault()` at call time.
- `validateValue` does not consult the `TraversableResolver`.
- Violations of `@Validate` rules and `@Expr` conditions are not constraints: `getConstraintDescriptor()` and
  `getMessageTemplate()` return null for them.
- In the metadata API, a constraint applied to a container's unwrapped value sits on the container element
  descriptor, not the property; `getContainerClass()` is the declared raw type; `ConstraintFinder` does not
  honour a class's redefined `Default` sequence; executables carrying only `@Validate` or `@Expr` are invisible.
- A `ValidatorFactory` stays usable after `close()`, and retains every validator it built until then.

---

## 10. What you gain

The reason to migrate is on the other side of the ledger, and the [README](README.md) covers it: constraints
that are named, described and tagged rules you can list and trace; cross-field invariants in one `@Expr` line;
business rules that stay out of the bean and are swapped at runtime with `@Validate`, parameterised per class or
method with `@Bind`; `RuleSet` and `RuleFlow` orchestration; scripted conditions in any language; and one engine
for validation and the decisions that follow it.
