# Change log

## [1.1.0] (unreleased)

Requires rulii 2.3.0 (1.0.0 runs on 2.2.0).

#### Performance
- A predefined constraint is no longer run through the rule engine. A rule backed by one of rulii's own validators
  (`@NotNull`, `@Size`, `@Min`, `@Email` and the rest of the standard and library constraints) is asked directly:
  the value is read, the validator decides (`ValueValidationRule.check`, new in rulii 2.3) and a failure is reported
  with the violation the rule builds (`ValueValidationRule.buildViolation`). No parameter is matched and nothing is
  resolved on the way. Everything that is a rulii feature still runs in the engine: `@Expr`, `@Validate`, a
  `@ReportAsSingleViolation` composition, a custom `ConstraintValidator`, and a rule a `ConstraintMapper` of yours
  builds over a value function of its own. The rules run in declaration order whichever way each one runs, so the
  violations arrive in the same order and a fail-fast call stops at the same rule. `RulePlan` is the new type that
  does this; `BeanRules.plan(...)`, `propertyPlan(...)`, `elementPlan(...)` and `ExecutableRules.parameterPlan(...)`,
  `returnValuePlan(...)`, `elementPlan(...)` build one beside each rule set.
- **What changes for a caller:** nothing in the violations. A `RuleListener` sees the same events for every rule
  (start, pre-condition, condition, otherwise, end, error). A `RuleSetListener` no longer hears from a validation
  call, and a rule the engine runs no longer finds `ruleSet` and `ruleSetStatus` bound: the rules of a bean are not
  run as a rule set any more (the rule sets are still built, for tools and for running on your own). A rule that
  fails to run fails with the same exception and the same named layers; one inner layer of the engine's own
  wrapping is gone from the cause chain. The rules asked directly leave no `DEBUG` line in the engine's log.
- A class that declares nothing to validate costs nothing to validate. Whether a class has a constraint, a
  `@Validate` rule, an `@Expr`, a cascade or a `@Bind` is known once the class is read
  (`BeanRules.validatesNothing()`); when it has none, `validate(object)` returns its empty report without building
  bindings, a context or a flow, and a cascade (`@Valid`) does not descend into an object of such a class. The call
  is still checked first: a null object and a group that is not an interface fail as before. **What changes for a
  caller:** a `RuleFlowListener` on the validator's tracer no longer sees a flow start and end for such an object.
  `validateIn` and a flow run on its own are not affected: there every object's flow runs and is traced.
- A validation call does less work around the rules it runs. The violations collector is looked up by the name the
  rules ask for, `ruleViolations`, instead of by searching every scope by type (the traversal asks for it several
  times per object validated), and the `$groups` binding is declared by the class of the traversal's view, which
  a Binding checks far faster than the parameterized `Set<Class<?>>`. Rules match `$groups` as before, by name and
  as a `Set<Class<?>>` by type.
- **Behaviour change, only with two collectors in one context:** when a `RuleViolations` is bound as
  `ruleViolations` and another one is bound under a different name beside it or in a scope further in,
  `validateIn` and an embedded bean flow now report to the one bound as `ruleViolations`, which is where the rules
  look first, and `validateIn` returns that one. Before, the collector found by type took the violations, and in two
  arrangements `validateIn` returned a collector that had stayed empty. A plain `validate` call is not affected.

#### Build
- The jar manifest now carries the default implementation and specification entries (`Implementation-Title`, `Implementation-Version`, `Implementation-Vendor`), so the library version can be read at runtime through `Package.getImplementationVersion()`, as rulii 2.3 does for its own jar.

## [1.0.0]

The first release. Requires rulii 2.2.0, the `jakarta.validation` API 3.1 and Java 17.

#### Provider
- `RuliiValidationProvider` is a Bean Validation 3.1 provider found through `META-INF/services`, so
  `Validation.buildDefaultValidatorFactory()` and `Validation.byProvider(RuliiValidationProvider.class)` both
  work. `BeanValidator` implements `jakarta.validation.Validator` and `ExecutableValidator`; `BeanValidator.builder()`
  builds one without the Jakarta bootstrap, and `validateIn(context, bean)` validates inside an existing rulii
  `RuleContext`, sharing its bindings and trace.
- The standard constraints, groups and group sequences (`@GroupSequence`, `@ConvertGroup`), cascading with `@Valid`,
  container element constraints with `ValueExtractor`s, method and constructor validation, the metadata API and
  the default message interpolation (Jakarta EL in messages) are in place. A bean annotated for Hibernate Validator
  validates unchanged: same null semantics, same property paths, same messages. The [gap analysis](GAP-ANALYSIS.md)
  lists what the specification requires that is not implemented, and the [migration guide](MIGRATION.md) what a
  project coming from Hibernate Validator will notice.
- Constraints beyond the specification, in `org.rulii.validation.beans.constraints`: the ones Hibernate Validator
  users know (`@UUID`, `@ISBN`, `@EAN`, `@CreditCardNumber`, `@LuhnCheck`, `@Mod10Check`, `@Mod11Check`,
  `@UniqueElements`, `@Normalized`, `@CodePointLength`, `@Length`, `@Range`, `@URL` with parts, and
  `@DurationMin` / `@DurationMax` under `time`) and rulii's own (`@Alpha`, `@AlphaNumeric`, `@Ascii`, `@Numeric`,
  `@Decimal`, `@In`, `@StartsWith`, `@EndsWith`, `@LowerCase`, `@UpperCase`), each backed by a rulii validator.

#### Rules
- Every constraint becomes a rulii validation rule: a named, described, categorised and tagged rule the engine
  runs, so it appears in the trace and can be listed from the registry. A constraint's rule is categorised under
  the bean class name and tagged with its validation groups; its `SourceDefinition` names the field, method or
  constructor it was declared on. A custom `@Constraint` runs its `ConstraintValidator` through
  `ConstraintValidatorRule`.
- Every violation is a rulii `RuleViolation` and a `ConstraintViolation` at once: the invalid value, the
  property path, the message template and the interpolated message, and the constraint attributes as
  violation parameters.
- `@Validate("orderRules")` on a class, property, method or constructor runs the named rule, rule set or rule flow
  from a rulii `RuleRegistry` against the bean, so contextual logic stays out of the DTO and can change at
  runtime; `@Bind` names the binding the bean (or a property) is passed under.
- `@Expr` is an inline condition in any scripting language rulii knows (`${end.isAfter(start)}` in Jakarta EL,
  JavaScript, Groovy, Java), with an error code and message; the language comes from the annotation, from
  `@ExprDefaults` on the class or package, or from `rulii.script.defaultLanguage`.
- The rules built for a bean class are materialized once per class and reused; the explorer can list them
  (`rulii-explorer` 1.1 shows beans, their constraints and the rules behind them).

#### Configuration
- `RuliiValidationProvider` accepts a `RuleRegistry`, `RuleContextOptions`, `ConstraintMapper` and
  `ValueExtractor` instances, `failFast`, and the standard `MessageInterpolator`, `TraversableResolver`,
  `ConstraintValidatorFactory`, `ParameterNameProvider` and `ClockProvider`; the Spring wiring lives in
  rulii-spring 2.2 (`org.rulii.spring.validation.BeanValidationConfig`).
