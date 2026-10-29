[rulii Maven Central]:https://central.sonatype.com/artifact/org.rulii/rulii
[Apache 2.0 License]:https://opensource.org/licenses/Apache-2.0

# _rulii-bean-validation_
**Jakarta Bean Validation annotations, run by the rulii rule engine** <br/>
<sub> _Java 17+_ &middot; _Drop-in `jakarta.validation.Validator`_ &middot; _Every constraint is a rulii rule_ &middot; _Attach your own rules with `@Validate`_ &middot; _Inline conditions with `@Expr`_ &middot; _Two dependencies_ </sub>

---

[![License](https://img.shields.io/badge/license-Apache%202.0-orange.svg)][Apache 2.0 License]

---

**📖 rulii documentation at [rulii.com](https://rulii.com)** &middot; **🧪 Sample projects at [rulii-samples](https://github.com/algox/rulii-samples)**

**➡️ Coming from Hibernate Validator? Read the [migration guide](MIGRATION.md).**

---

## Table of Contents

- [What is it?](#what-is-it)
- [Why rulii bean validation?](#why-rulii-bean-validation)
- [Annotations for invariants, rules for decisions](#annotations-for-invariants-rules-for-decisions)
- [Getting Started](#getting-started)
- [Validating a Bean](#validating-a-bean)
- [Attaching rulii Rules with `@Validate`](#attaching-rulii-rules-with-validate)
- [Inline Conditions with `@Expr`](#inline-conditions-with-expr)
- [Cross-Field Validation with Rules, Rule Sets and Flows](#cross-field-validation-with-rules-rule-sets-and-flows)
- [Constraints](#constraints)
- [Your Constraints as rulii Rules](#your-constraints-as-rulii-rules)
- [Configuration](#configuration)
- [What is Different by Design](#what-is-different-by-design)
- [Documentation](#documentation)
- [Contributing](#contributing)

---

## What is it?

_rulii-bean-validation_ reads the `jakarta.validation` constraints on your beans (`@NotNull`, `@Size`, `@Valid`,
your own `@Constraint` annotations) and turns them into [rulii](https://github.com/algox/rulii) rules. Validation
then runs on the rulii engine: every constraint is an ordinary rule with a name, a description, a category and
tags, every violation is a rulii `RuleViolation`, and the whole run is visible in the rulii trace.

To the rest of your code it is a Bean Validation provider. `BeanValidator` implements `jakarta.validation.Validator`,
the result of a validation is a `Set<ConstraintViolation<T>>`, and the standard bootstrap finds the provider through
`META-INF/services`. A bean annotated for Hibernate Validator validates unchanged, with the same null semantics, the
same paths and the same messages.

---

## Why rulii bean validation?

Bean Validation stops at the annotation. A constraint checks one value; anything that looks at two fields, at
the state of the system, or at who is asking means a `ConstraintValidator` class, a class-level annotation
and a place to put the logic that the specification does not give you. This library keeps everything the
annotations do and adds what they cannot:

| | Bean Validation alone | with rulii bean validation |
|---|---|---|
| Standard constraints, groups, cascading, containers, method validation | ✅ | ✅ unchanged |
| Cross-field invariant (`end` after `start`) | class-level annotation + a validator class | one line: `@Expr("${end.isAfter(start)}", errorCode = "trip.end")` |
| Validation that depends on context (the customer, the operation, a limit that changes) | not its job | a rulii `Rule`, `RuleSet` or `RuleFlow`, attached with `@Validate`, swappable at runtime |
| Orchestration (run these checks only if those passed, branch, loop over items, run in parallel) | none | `RuleFlow` |
| Scripted conditions | EL inside messages only | any JSR-223 language, GraalJS, Groovy, Jakarta EL, in `@Expr` and in registry rules |
| Seeing what runs | the metadata API | every constraint is a named, described, tagged rule: list it, trace it, filter it |
| Entry point | `Validator` | the same `Validator`, plus the builder and `validateIn` for an existing rulii context |

- **Keep the annotations, gain the engine.** The constraints you already have become rules you can list, trace,
  filter by tag, embed in a `RuleSet` or `RuleFlow`, and run inside a rulii context next to your business rules.
- **Business rules stay out of the DTO.** `@Validate("orderRules")` names rules in a rulii `RuleRegistry`; what runs
  under that name can change without touching, or recompiling, the bean.
- **One engine for validation and decisions.** The rules that validate an order and the rules that price it run on
  the same engine, share bindings, and appear in one trace.
- **Drop-in for the reference implementation.** `Validator`, `ExecutableValidator`, `ConstraintValidator` execution,
  groups and sequences, group conversion, container element constraints with value extractors, method and
  constructor validation, the metadata API and the default message interpolation are all in place. The
  [migration guide](MIGRATION.md) lists what differs.
- **Two dependencies.** rulii core and the `jakarta.validation` API. Nothing else is pulled in.

---

## Annotations for invariants, rules for decisions

The library draws one line, and the rest of the design follows from it:

- **Annotations describe what is intrinsic to the data.** A code is between 2 and 5 characters, a quantity is
  positive, a trip ends after it starts. These hold in every conceivable use of the object, so they belong on the
  class, as constraints or as an `@Expr`.
- **Rules decide what is contextual.** Whether a customer may place this order, whether a discount applies, what
  counts as complete during a draft: these change for business reasons. They belong in the registry, attached with
  `@Validate` or run as your own `RuleFlow`, where they are versioned, listed and swapped without touching the bean.

The test for an `@Expr` is simple: *would this condition ever change for a business reason?* If yes, it is a rule.

---

## Getting Started

Java 17 or later. The library depends on rulii core and the Jakarta Validation API, both brought in transitively.

**Maven**
```xml
<dependency>
    <groupId>org.rulii</groupId>
    <artifactId>rulii-bean-validation</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle**
```groovy
implementation 'org.rulii:rulii-bean-validation:1.0.0'
```

If a message or an `@Expr` uses Jakarta Expression Language (`${...}`), add an EL implementation such as
`org.glassfish.expressly:expressly`, exactly as with any other provider. Without one, EL in messages is left
as written.

---

## Validating a Bean

A bean annotated for Bean Validation needs no change:

```java
public class Order {
    @NotNull @Size(min = 2, max = 5)
    String code;

    @NotNull(groups = OnCreate.class)
    String reference;

    @Valid
    Customer customer;

    @Positive
    int quantity = 1;
}
```

**Through the standard bootstrap.** With this library as the only provider on the classpath, the Jakarta bootstrap
hands out a `BeanValidator`, and so does every framework that bootstraps through `Validation`:

```java
Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
Set<ConstraintViolation<Order>> violations = validator.validate(order);
```

Provider-specific options go through `Validation.byProvider`:

```java
Validator validator = Validation.byProvider(RuliiValidationProvider.class)
        .configure()
        .registry(registry)
        .failFast(true)
        .buildValidatorFactory()
        .getValidator();
```

**Through the builder.** The same validator, built the rulii way, when you do not need the bootstrap:

```java
BeanValidator validator = BeanValidator.builder()
        .registry(registry)
        .build();

BeanValidationResult<Order> result = validator.validate(order, OnCreate.class);
```

`BeanValidationResult` is both the spec's `Set<ConstraintViolation<T>>` and the owner of the rulii
`BeanRuleViolations`, so code written against either model reads it:

```java
for (ConstraintViolation<Order> violation : result) {
    System.out.println(violation.getPropertyPath() + " " + violation.getMessage());
}

RuleViolations violations = result.getRuleViolations();
```

A built validator is immutable and thread-safe. Build it once; the metadata of a class is read, its rules
materialized and its scripts compiled the first time the class is validated, then cached.

**Inside an existing rulii context.** Annotation rules, your own rules and the trace share one execution:

```java
RuleContext context = RuleContext.builder().with(bindings).registry(registry).build();
RuleViolations violations = validator.validateIn(context, order);
```

`validateProperty`, `validateValue` and `forExecutables()` for method and constructor validation are there too,
with the semantics the specification gives them.

---

## Attaching rulii Rules with `@Validate`

`@Validate` names runnables in the validator's `RuleRegistry`: a `Rule`, a `RuleSet` or a `RuleFlow`. They run in
declaration order, after the constraints of the same group step, and report into the same violations.

```java
RuleRegistry registry = RuleRegistry.builder()
        .register(Validators.notNull(Validators.binding("customer")).name("customerRequired").build())
        .register(orderRules)   // a RuleSet or RuleFlow of your own
        .build();
```

```java
@Validate({"customerRequired", "orderRules"})
public class Order {

    @Validate("codeFormat")
    String code;

    @Validate(value = "auditTrail", groups = OnCreate.class)
    void place(@NotNull Customer by) { ... }
}
```

What a rule sees depends on where the annotation sits:

| Placement | In scope | Violations reported at |
|---|---|---|
| Class | every property by name, `$this`, `$root`, `$parent`, `$groups` | the bean |
| Field, getter, record component | the above and the property's value as `$value` | the property |
| Method or constructor | the arguments by parameter name and as `$parameters`, or the return value as `$value` and `$returnValue`, as `applyTo` says | the cross-parameter or return-value node |

Names are resolved at validation time, so what is registered under a name can change without recompiling the
bean. A name that resolves to nothing fails loudly, never silently. `groups` select when the rules run exactly as
a constraint's groups do; a rule that wants to branch further reads the groups being processed from `$groups`.

---

## Inline Conditions with `@Expr`

For an invariant between fields that no annotation expresses, `@Expr` attaches a script condition, compiled once
when the class is first read:

```java
@ExprDefaults(lang = "jakarta-el")
public class Trip {
    public LocalDate start;

    @Expr(value = "${start == null || end.isAfter(start)}", errorCode = "trip.end")
    public LocalDate end;

    @Expr(value = "${seats < 10}", errorCode = "trip.seats.max", message = "too many seats: ${value}")
    public Integer seats;
}
```

- The script sees the same scope a `@Validate` rule does: sibling properties by name, `$this`, `$root`, `$parent`,
  `$groups`, and on an executable the arguments or `$returnValue`.
- The language comes from `lang` on the annotation, else the nearest `@ExprDefaults` on the class or its package,
  else the default declared on rulii's `ScriptProcessorManager` (or the system property
  `rulii.script.defaultLanguage`). It is never guessed from the classpath; a class with no language declared
  anywhere fails to load with a message listing the ways to declare one.
- `errorCode` is required; `message`, `severity`, `groups` and `applyTo` work as on a constraint. At property level
  a null value skips the script unless `skipNull = false`.
- A script that throws, or returns something other than a boolean, is an error, not a violation.

**Any language.** The script engine is rulii's. `jakarta-el` ships with core; any JSR-223 engine on the classpath
(GraalJS, Groovy, ...) and any factory registered on `ScriptProcessorManager` can be named, per expression or for
a whole class or package:

```java
@Expr(value = "end.isAfter(start)", lang = "js", errorCode = "trip.end")
public LocalDate end;
```

The same engines back script conditions in registry rules (`Condition.builder().build(script)`), so a rule attached
with `@Validate` can be a script too, in a language your domain experts read.

Remember the line above: `@Expr` is for conditions that make an object nonsensical in every use. A condition that
can change for a business reason belongs in the registry.

---

## Cross-Field Validation with Rules, Rule Sets and Flows

Validation that looks at more than one value is where annotations run out. Three tools, from the narrowest to
the widest, and none of them needs a `ConstraintValidator` class or a class-level annotation of its own.

**An invariant: `@Expr`.** One line on the class, no validator class, no registration:

```java
@Expr(value = "${end == null || end.isAfter(start)}", errorCode = "trip.end")
public class Trip { ... }
```

**A rule: write it in Java, attach it by name.** A rulii rule receives the bean's properties as parameters, by
name, and reports through the same `RuleViolations` the constraints use. Nothing in it mentions Bean Validation,
so it is testable on its own and reusable outside validation:

```java
@Rule
@Description("The deposit covers the minimum for the room category.")
public class DepositRule {

    @PreCondition
    public boolean check(BigDecimal deposit, RoomCategory category) {
        return deposit != null && category != null;
    }

    @Given
    public boolean isValid(BigDecimal deposit, RoomCategory category) {
        return deposit.compareTo(category.minimumDeposit()) >= 0;
    }

    @Otherwise
    public void otherwise(BigDecimal deposit, RoomCategory category, RuleViolations violations) {
        violations.add(RuleViolation.builder().with("depositRule", "booking.deposit")
                .errorMessage("deposit " + deposit + " is below the minimum " + category.minimumDeposit())
                .build());
    }
}
```

```java
RuleRegistry registry = RuleRegistry.builder()
        .register(Rule.builder().build(DepositRule.class))
        .build();
```

```java
@Validate("depositRule")
public class Booking {
    @NotNull BigDecimal deposit;
    @NotNull RoomCategory category;
}
```

The violation reports at the bean with the rule's error code and message, next to the constraint violations. The
rule runs after the constraints of its group step; a missing value is still handed to it as null, which is what
the precondition is for, as in any rulii rule. On a property the rule also receives `$value`; on a method, the
arguments by parameter name.

**A rule set or a flow: when the logic has shape.** Group the rules of a context in a `RuleSet`, or orchestrate
them in a `RuleFlow` that branches, loops over items, stops early or runs checks in parallel. The flow sees the
same scope as a rule: properties by name, `$this`, `$root`, `$groups`:

```java
RuleFlow<?> orderRules = RuleFlow.builder()
        .name("orderRules")
        .run(creditRules)
        .when(condition((Customer customer) -> customer.isWholesale()), body -> body
                .run(wholesaleLimits))
        .forEach(function((List<Item> items) -> items), "item", body -> body
                .run(itemAvailabilityRule))
        .build();
```

```java
@Validate("orderRules")
public class Order { ... }
```

Register the flow under its name and the bean picks it up on its next validation. Replace what is registered
under the name and the next validation runs the new logic: no change to the bean, no rebuild of the validator.
With a `MutableRuleRegistry` that can happen while the application runs.

---

## Constraints

- **All built-in Jakarta constraints**, with the specification's null semantics and applicable types, mapped to
  rulii's built-in validators.
- **Constraints known from other providers**, under `org.rulii.validation.beans.constraints`: `@Length`, `@Range`,
  `@URL`, `@UUID`, `@CreditCardNumber`, `@LuhnCheck`, `@Mod10Check`, `@Mod11Check`, `@EAN`, `@ISBN`,
  `@UniqueElements`, `@Normalized`, `@CodePointLength`, `@DurationMin`, `@DurationMax`, plus `@Alpha`,
  `@AlphaNumeric`, `@Ascii`, `@Numeric`, `@Decimal`, `@In`, `@StartsWith`, `@EndsWith`, `@LowerCase` and
  `@UpperCase`.
- **Your own `@Constraint` annotations.** A `ConstraintValidator` is executed as the rule's condition, with a
  `ConstraintValidatorContext` that supports custom templates, message parameters, expression variables and
  dynamic payloads. Composed constraints and `@ReportAsSingleViolation` work as specified.
- **Groups, sequences, conversion.** `groups`, `@GroupSequence` (including a class's redefined default) and
  `@ConvertGroup`.
- **Containers.** Type-use constraints and `@Valid` on `List<@NotNull String>`, `Map<K, @Valid V>`, `Optional`,
  arrays and your own containers through `ValueExtractor`, registered on the builder or, on the bootstrap path,
  discovered through `META-INF/services`.
- **Messages.** A `ValidationMessages.properties` at the root of the classpath overrides the defaults, with
  locale variants; `{parameter}` references, `${validatedValue}` and EL work as specified, and a configured
  `MessageInterpolator` is consulted.

---

## Your Constraints as rulii Rules

Every materialized rule says what it checks and where it was declared, carries the bean class as its category,
and is tagged `constraint`, `expr` or `hosted`, the annotation's name, and its groups:

```
code size must be between 2 and 5 (@Size on com.acme.Order.code)
reference must not be null (@NotNull on com.acme.Order.reference)
end must satisfy ${start == null || end.isAfter(start)} (@Expr on com.acme.Trip.end)
```

The transformation is public. For a class you can get its rules and its per-class `RuleFlow`, a self-contained
pipeline that binds the object and runs everything, to embed in a flow of your own:

```java
BeanRules rules = validator.getBeanRules(Order.class);
for (MaterializedRule rule : rules.getRules()) {
    System.out.println(rule.getName() + ": " + rule.getRule().getDescription());
}

RuleFlow<?> flow = validator.getRuleFlow(Order.class);
```

With `registerFlows(true)` the validator registers each class's flow in its registry as the class is first used,
so tools that list the registry see validation next to everything else:

```java
List<Runnable<?>> orderValidation = validator.getRuleRegistry().getByCategory(Order.class.getName());
```

Methods and constructors get a parameters flow and a return-value flow of their own, through
`getParametersRuleFlow` and `getReturnValueRuleFlow`.

---

## Configuration

Everything is fixed when the validator is built. `BeanValidator.builder()` and the bootstrap's `RuliiConfiguration` take the
same options, the latter also the specification's `clockProvider`:

| Option | What it does |
|---|---|
| `registry(RuleRegistry)` | where `@Validate` names resolve; without one, the registry of the context the validation runs in |
| `failFast(boolean)` | stop at the first violation; also the properties `rulii.validation.fail_fast` and `hibernate.validator.fail_fast` |
| `registerFlows(boolean)` | register each class's validation flow in the registry as the class is first used |
| `mapper(ConstraintMapper)` | how a constraint annotation becomes rules; add your own or replace a built-in mapping |
| `extractor(ValueExtractor)` | value extraction for a container type of your own |
| `constraintValidatorFactory`, `messageInterpolator`, `traversableResolver`, `parameterNameProvider` | the specification's hooks, consulted as specified |
| `options(RuleContextOptions)` | the rulii context options validation runs with: matching strategy, message resolver, tracer, clock |

---

## What is Different by Design

- This is not a certified implementation of the specification and does not aim to pass its TCK. The
  [gap analysis](GAP-ANALYSIS.md) lists every known difference, chapter by chapter.
- XML configuration (`META-INF/validation.xml`, constraint mapping files) is never read.
- Method validation is a call you make through `forExecutables()`; interception (`@ValidateOnExecution`, proxies)
  stays with your framework, as it does with every provider.
- The legacy `javax.validation` namespace is not supported.
- Hibernate-specific extensions have no equivalent unless listed above; the migration guide says what to use
  instead.

---

## Documentation

- [rulii core](https://github.com/algox/rulii): rules, rule sets, rule flows, the registry, scripting, tracing.
- [Migration guide](MIGRATION.md): moving from Hibernate Validator.
- [Gap analysis](GAP-ANALYSIS.md): the specification, chapter by chapter, and where this library deviates.
- Javadoc on every public type; the Jakarta Validation [specification](https://jakarta.ee/specifications/bean-validation/3.1/)
  for the semantics this library follows.

---

## Contributing

Issues and pull requests are welcome. Build with `mvn verify` on Java 17 or later; the test suite needs no
external services.

Licensed under the [Apache License, Version 2.0][Apache 2.0 License].
