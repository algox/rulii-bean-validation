# Change log

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
