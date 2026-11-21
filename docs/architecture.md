# Architecture

## Component diagram

```
+---------------------------+
| CLI / API / Gradle Task   |
+------------+--------------+
             |
             v
+---------------------------+
| JobRunner                 |
+------------+--------------+
             |
             v
+---------------------------+        +----------------------------+
| IliModelService           |<------>| INTERLIS ili2c facade      |
| TypeSystemFacade          |        | TransferDescription        |
+------------+--------------+        +----------------------------+
             |
             v
+---------------------------+
| MappingLoader             |
| YAML/JSON -> JobConfig    |
+------------+--------------+
             |
             v
+---------------------------+
| MappingCompiler           |
| JobConfig -> TransformPlan|
+------------+--------------+
             |
             v
+---------------------------+
| TransformationEngine      |
| Pass 0/1/2 + write        |
+------+----------+---------+
       |          |
       v          v
+-------------+  +----------------+
| StateStore  |  | Diagnostics    |
+------+------+  +----------------+
       |
       v
+---------------------------+
| INTERLIS I/O adapters     |
| ITF / XTF reader/writer   |
| GeometryAdapter           |
+---------------------------+
```

## Core components

### CliMain / CLI Commands

Entry point via picocli. Subcommands:

- `transform` -- run a transformation
- `validate-mapping` -- compile mapping without executing
- `inspect-model` -- model inventory as JSON/Markdown
- `dm01-dmav import-correlation` -- XLSX correlation hints import

### JobRunner

Orchestrates the full transformation pipeline:

1. Load YAML config via MappingLoader
2. Compile INTERLIS models via IliModelService
3. Build TypeSystemFacade per model
4. Compile mapping via MappingCompiler (produces TransformPlan)
5. Set up I/O adapters (IoxReader, IoxWriter)
6. Run TransformationEngine
7. Collect and report diagnostics

### IliModelService / TypeSystemFacade

Wraps ili2c's `TransferDescription`. Provides:

- Model compilation with configurable `--modeldir`
- Class, attribute, role, domain, enum extraction
- Mandatory/optional detection
- OID type (UUIDOID / STANDARDOID) detection
- Constraint inspection
- Structured path resolution via `IliPath`

### MappingCompiler

Compiles `JobConfig` (raw YAML model) into `TransformPlan` (typed execution plan).
Validation checks:

- Input/output IDs exist in job definition
- Source/target classes exist in compiled models
- Target classes are non-abstract and transferable
- Source/target attributes exist
- Role validity against target model
- Expression type compatibility (via FunctionRegistry)
- Mandatory attribute coverage
- Duplicate target assignments
- Cyclic rule dependencies

### ExpressionEngine / FunctionRegistry

Typed expression system with:

- AST-based parser (no JEXL/JSR-223)
- `sealed interface Value` with subtypes: TextValue, NumberValue, BooleanValue, DateValue, XmlDateTimeValue, EnumValue, CoordValue, GeometryObjectValue, ReferenceValue, NullValue
- Builtin functions: Basic (coalesce, eq, neq, lt, lte, gt, gte, ...), String (concat, truncate, ...), Date (toXmlDateTime, toInterlis1Date, toDate, now), Enum (enumMap, enumDefault, enumName), Reference (refOid, refEquals), Math (div, mul), Lookup (oid, bagFirst, lookup), Geometry (coordEquals)

### TransformationEngine

Multi-pass execution:

- **Pass 0**: Source scan -- read input files, index objects
- **Pass 1**: Build target objects, assign scalar attributes, generate OIDs for identifiable targets and store deferred references with their target IOM object.
- **Pass 2**: `ReferenceResolutionService` resolves deferred references against the source identity and producing rule, then validates the target class, cardinality and output basket.
- **Write**: Stable-sort and write output via `IoxWriter`.

### Runtime state

`InMemoryStateStore` holds source records, registered target objects and deferred
references. `ReferenceIndex` maps source keys to the actual output, class, OID,
basket and producing rule of each indexed target. A standalone link without an OID
is kept in its output basket and can own deferred references through its IOM
object. It has no entry in the reference index. The [state store contract](dev/state-store.md)
describes the lookup scope and transfer encoding.

### BasketRouter

Determines target basket for generated objects:

- `preserve` -- keep source basket ID
- `generateUuid` -- one new UUID per output, target topic, source input and source basket within the run
- `preserveOrGenerateUuid` -- keep a valid source UUID or use the same grouping as `generateUuid`
- `byTopic` -- one basket per target topic
- `expression` -- stub (future)

### Model-aware references

`RulePlan.targetClass()`, `SourcePlan.sourceClass()` and `TypeSystemFacade.resolveClass()`
expose ili2c's `AbstractClassDef` for classes and associations. The compiler uses
`AssociationDef.isLightweight()` to distinguish embedded links from standalone
transfer objects. Each standalone link contains one reference per role. Role
cardinality describes participation across link objects. Direct class
`REFERENCE TO` attributes use the alias-resolved `ReferenceType` for the
destination class and external-reference semantics. The same resolver handles
both kinds of reference.

## Data flow

```
YAML Config -> MappingLoader -> JobConfig
                                 |
INTERLIS Models -> ili2c -> TransferDescription -> TypeSystemFacade
                                 |
           MappingCompiler.compileTyped() -> TransformPlan
                                 |
        TransformationEngine.runTyped(plan)
           /          |           \
    Pass 0         Pass 1        Pass 2/Write
   (source        (target       (ref resolution
    scan)          build)        + stable output)
```

## Design decisions

- **Java-first**: No Groovy, JavaScript, or scripting in engine
- **Compiler + Runtime separation**: All model validation before any data processing
- **No arbitrary code execution**: Expressions are sandboxed to FunctionRegistry
- **Deterministic execution**: Same input + same mapping = same output (OIDs, order, reports)
- **Diagnostics as first-class**: Structured error/warning codes with rule-ID and source/target context

## Deep dives

Technische Tiefendokumentation:

- [docs/dev/adr/](dev/adr/) - Architectural Decision Records
- [docs/dev/diagnostics.md](dev/diagnostics.md) - Diagnostic-Codes
- [docs/dev/state-store.md](dev/state-store.md) - StateStore-Design
- [docs/dev/typed-plan.md](dev/typed-plan.md) - TransformPlan-Typisierung
