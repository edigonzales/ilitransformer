# Expression Language

The expression language is a small, sandboxed DSL for transformation logic. It is parsed into an AST and evaluated by the `ExpressionEngine` with a registered `FunctionRegistry`.

## Syntax

### Source attribute references

```
${alias.attribute}
```

Accesses an attribute of a source object. Paths can be nested for structures:

```
${alias.structure.attribute}
```

### Literals

| Type | Syntax | Example |
|---|---|---|
| String | `"text"` or `'text'` | `"Hello World"` |
| Number | integer or decimal | `42`, `3.14` |
| Boolean | keyword | `true`, `false` |
| Null | `null` | `null` |
| Enum literal | `#Value` | `#LFP3`, `#aktiv` |

### Function calls

```
functionName(arg1, arg2, ...)
```

Nested expressions: `truncate(concat(src.first, " ", src.last), 60)`

### Conditionals

```
if(condition, valueIfTrue, valueIfFalse)
```

`condition` can be:
- Boolean literal: `true`, `false`
- Null check: `src.attr != null`, `src.attr == null`
- Function returning boolean: `defined(src.attr)`

## Builtin functions

### Basic functions

| Function | Signature | Description |
|---|---|---|
| `coalesce` | `(a, b, ...) → any` | Returns first non-null argument |
| `defined` | `(value) → boolean` | Returns true if value is not null |
| `notDefined` | `(value) → boolean` | Returns true if value is null |
| `isNull` | `(value) → boolean` | Synonym for notDefined |
| `default` | `(value, fallback) → any` | If value is null, return fallback |
| `null` | `() → null` | Returns null |
| `if` | `(condition, a, b) → any` | Conditional: true → a, false → b |

### String functions

| Function | Signature | Description |
|---|---|---|
| `concat` | `(a, b, ...) → text` | Concatenates strings |
| `substring` | `(value, start, length) → text` | Substring from start, given length |
| `trim` | `(value) → text` | Removes leading/trailing whitespace |
| `upper` | `(value) → text` | Converts to uppercase |
| `lower` | `(value) → text` | Converts to lowercase |
| `replace` | `(value, pattern, replacement) → text` | String replacement |
| `truncate` | `(value, maxLength) → text` | Truncates to maxLength characters |

### Date functions

| Function | Signature | Description |
|---|---|---|
| `toXmlDateTime` | `(value) → XMLDateTime` | Converts DATE to INTERLIS.XMLDateTime |
| `now` | `() → XMLDateTime` | Current timestamp (marked @NonDeterministic) |

### Enum functions

| Function | Signature | Description |
|---|---|---|
| `enumMap` | `(value, mapName) → enum` | Maps enum value using named map (stub) |
| `enumDefault` | `(value, fallback) → enum` | Returns default if value not in target enum |
| `enumName` | `(value) → text` | Returns the string name of an enum value |

### Reference functions

| Function | Signature | Description |
|---|---|---|
| `refOid` | `(object.role) → text` | Returns the OID referenced by a role |
| `refEquals` | `(object.role, other) → boolean` | Checks if role reference equals another object |

### Math functions

| Function | Signature | Description |
|---|---|---|
| `round` | `(value, scale) → number` | Rounds to given decimal scale |
| `abs` | `(value) → number` | Absolute value |

## Type system

Expressions produce typed `Value` objects:

```java
sealed interface Value permits
    TextValue, NumberValue, BooleanValue,
    DateValue, XmlDateTimeValue, EnumValue,
    CoordValue, GeometryObjectValue,
    ReferenceValue, NullValue {}
```

The `MappingCompiler` infers expression types and validates against target attribute types. Type mismatches produce `ILITRF-MAP-TYPE-MISMATCH` (WARNING).

## Non-deterministic functions

Functions marked `@NonDeterministic` (currently only `now()`) produce a warning `ILITRF-EXPR-NON-DETERMINISTIC` when used. They are not suitable for golden tests where stable output is required.

## Security model

- No file system access
- No network access
- No arbitrary Java method calls
- Only registered functions in `FunctionRegistry` can be called
- Unknown functions produce `ILITRF-EXPR-UNKNOWN-FUNC` (ERROR)

## Currently unsupported

The following are not yet supported in the expression engine and produce `ILITRF-EXPR-UNSUPPORTED`:

- Arithmetic operators (`+`, `-`, `*`, `/`) — planned
- Comparison operators (`>`, `<`, `>=`, `<=`) — planned
- `lookupOne`/`lookupMany` (StateStore lookups) — planned for Phase 10+
- Geometry functions beyond pass-through — partially supported via GeometryAdapter
