# Gap analysis against Jakarta Validation 3.1

What the [specification](https://jakarta.ee/specifications/bean-validation/3.1/) requires, chapter by chapter, and
where this library stands. The library is not a certified implementation and does not run the TCK; this page is
the honest substitute. The [migration guide](MIGRATION.md) says what each difference means for a project coming
from Hibernate Validator.

**In one line:** all 22 built-in constraints, composition, groups, sequences and group conversion, container
element constraints and value extraction, cascading, method and constructor validation, the full `Validator`,
`ExecutableValidator`, `ConstraintViolation` and `Path` API, the bootstrap, the metadata API and the default
message interpolation are in place and tested. The deviations below are deliberate exclusions, supersets that
match Hibernate Validator, or small conformance points that do not change the outcome for an ordinary bean.

| Chapter | Status | Deviations |
|---|---|---|
| 3. Constraint definition | in place | a missing or mistyped `message`, `groups` or `payload` attribute raises `IllegalArgumentException`, not `ConstraintDefinitionException`; attribute names starting with `valid`, the presence rules for `validationAppliesTo`, an `@OverridesAttribute` naming a constraint that is not composing, and more than one cross-parameter validator per constraint are not checked |
| 4. Value extractor definition | in place | `@ExtractedValue(type)` on a type argument is ignored instead of rejected; no JavaFX extractors |
| 5. Constraint declaration and validation | in place | cycle detection is per validation call, not per navigation path (as Hibernate Validator); parameter names are also recovered from bytecode (a superset); a constraint on a field declared `Object` or `Number` is accepted and an unsupported runtime type is skipped; a class `@GroupSequence` may not list another sequence or a duplicate (stricter); return-value `@Valid` twice along a hierarchy is accepted; methods are compared by erased signature, so generic overrides escape the hierarchy checks; a `@ConvertGroup` re-declared in a subtype is not rejected |
| 6. Validation APIs | in place | only dotted names in a message are looked up as bundle keys; the one-argument `interpolate` uses the locale captured at build time; `validateValue` does not consult the `TraversableResolver`; `getConstraintDescriptor()` and `getMessageTemplate()` are null on `@Validate` and `@Expr` violations; `addMapping` throws and XML is never read; with another provider on the classpath the default provider is classpath order; a `ValidatorFactory` stays usable after `close()` |
| 7. Constraint metadata | in place | a constraint on a container's unwrapped value sits on the container element descriptor, not the property; `getContainerClass()` is the declared raw type; `ConstraintFinder.unorderedAndMatchingGroups` does not honour a class's redefined `Default`; executables carrying only `@Validate` or `@Expr` are invisible; `getConstraintValidatorClasses()` lists `validatedBy` only |
| 8. Built-in constraints | in place | `@Min`, `@Max`, `@DecimalMin` and `@DecimalMax` also accept `double`, `float` and `CharSequence` (as Hibernate Validator); `NaN` passes the numeric constraints; `-0.0` counts as negative; `@Pattern` uses Hibernate's message wording (`must match "{regexp}"`) |
| 9. XML configuration | excluded | `META-INF/validation.xml` and constraint mapping files are never read |
| 10. Exception model | in place | declaration problems raise the specification's exceptions through named subclasses; every failure inside validation reaches the caller as a `ValidationException`; `validateIn`, inside a rulii context, keeps rulii's `UnrulyException` |
| 11. Integration | partly | records and the Jakarta Persistence traversable resolver are in; `@ValidateOnExecution` and interception, CDI, JSF and JAX-RS integration are left to frameworks and containers |

**Beyond the specification**, so that nothing here is mistaken for a gap: `@Validate`, `@Expr` and `@ExprDefaults`,
severities through payload markers, `validateIn`, per-class and per-executable rule flows, registry listing with
descriptions, categories and tags, the library's own constraints, and the Hibernate message key fallback.

Found something not listed here? Open an issue with the bean and the expected violations.
