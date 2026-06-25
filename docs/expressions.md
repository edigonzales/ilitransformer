# Expression Language

The expression language is a small, sandboxed DSL for transformation logic. It is parsed into an AST and evaluated by the `ExpressionEngine` with a registered `FunctionRegistry`.

## Syntax

### Source attribute references

```
${alias.attribute}
```

Accesses an attribute of a source object. Only direct `alias.attribute` paths are supported:

```
${src.Name}
```

Nested structure traversal such as `alias.structure.attribute` is reserved and not currently supported by the expression evaluator/type checker. Use the structural DSL features `bags`/`nestedBags` for BAG OF STRUCTURE mappings.

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

The `if()` form is a special parser construct that evaluates lazily: only the chosen branch is evaluated. It is not a regular function and does not appear in `FunctionRegistry`.

`condition` can be:
- Boolean literal: `true`, `false`
- Null check: `src.attr != null`, `src.attr == null`
- Comparison operator: `<`, `<=`, `>`, `>=`, `==`, `!=`
- Function returning boolean: `defined(src.attr)`, `eq(a, b)`
- Combined with `and`, `or`, `not`

### Operators / syntax sugar

The parser supports infix and prefix operators. They are desugared into function calls or special forms:

| Syntax | Desugaring |
|---|---|
| `a == b` | `eq(a, b)`, except `a == null` → `notDefined(a)` |
| `a != b` | `neq(a, b)`, except `a != null` → `defined(a)` |
| `a < b` | `lt(a, b)` |
| `a <= b` | `lte(a, b)` |
| `a > b` | `gt(a, b)` |
| `a >= b` | `gte(a, b)` |
| `a and b` | Lazy `ConditionalExpr`: if `a` then `b` else `false` |
| `a or b` | Lazy `ConditionalExpr`: if `a` then `true` else `b` |
| `not a` | `not(a)` |

All comparison and boolean operators are fully supported and tested.

## Formale Grammatik (EBNF)

Die folgende EBNF definiert die vollstaendige Syntax der Expression-Language. Sie folgt der Praezedenz des Parsers (`ExpressionParser.java`) und beschreibt die syntaktische Struktur bis zu den Terminalsymbolen.

```ebnf
expression        = orExpr ;

orExpr            = andExpr ("or" andExpr)* ;
andExpr           = equalityExpr ("and" equalityExpr)* ;
equalityExpr      = comparisonExpr (("==" | "!=") comparisonExpr)* ;
comparisonExpr    = unaryExpr (("<=" | ">=" | "<" | ">") unaryExpr)* ;
unaryExpr         = "not" unaryExpr | primary ;

primary           = "(" expression ")"
                  | stringLiteral
                  | enumLiteral
                  | pathRef
                  | numberLiteral
                  | "null" | "true" | "false"
                  | functionCall
                  | barePath ;

stringLiteral     = ('"' character* '"') | ("'" character* "'") ;
enumLiteral       = "#" identifier ;
pathRef           = "${" identifier "." identifier "}" ;
numberLiteral     = ("+" | "-")? digit+ ("." digit+)? ;
functionCall      = identifier "(" (expression ("," expression)*)? ")" ;
barePath          = identifier ("." identifier)? ;

identifier        = idStart idCont* ;
idStart           = letter | "_" ;
idCont            = letter | digit | "_" ;
```

### Terminalsymbole

| Symbol | Beschreibung |
|--------|-------------|
| `letter` | `a`-`z`, `A`-`Z` |
| `digit` | `0`-`9` |
| `character` | Beliebiges Zeichen ausser dem begrenzenden Quote; `\` escaped das naechste Zeichen |
| `"..."`, `'...'` | String-Literale (doppelte oder einfache Quotes) |
| `#` | Beginn eines Enum-Literals (`#Wert`) |
| `${` `}` | Path-Referenz auf Quellattribut (`${alias.attribut}`) |

### Semantische Anmerkungen

| Syntaktische Form | Semantik |
|-------------------|---|
| `a == b` | `eq(a, b)`, ausser wenn `b` das Literal `null` ist → `notDefined(a)` |
| `a != b` | `neq(a, b)`, ausser wenn `b` das Literal `null` ist → `defined(a)` |
| `a < b` | `lt(a, b)` |
| `a <= b` | `lte(a, b)` |
| `a > b` | `gt(a, b)` |
| `a >= b` | `gte(a, b)` |
| `not a` | `not(a)` |
| `a and b` | Lazy `ConditionalExpr`: wenn `a` wahr, dann `b`, sonst `false` |
| `a or b` | Lazy `ConditionalExpr`: wenn `a` wahr, dann `true`, sonst `b` |
| `if(cond, then, else)` | Lazy `ConditionalExpr` (keine reguläre Funktion; erscheint nicht in `FunctionRegistry`) |

### Praezedenz (niedrigste zu hoechster)

| Ebene | Operatoren | Assoziativitaet |
|-------|-----------|-----------------|
| 6 | `or` | links |
| 5 | `and` | links |
| 4 | `==`, `!=` | links |
| 3 | `<`, `<=`, `>`, `>=` | links |
| 2 | `not` | rechts |
| 1 | `()`, Literale, Pfade, Funktionsaufrufe | -- |

## Builtin functions

The authoritative list of built-in functions is generated from `FunctionRegistry` by `./gradlew generateExpressionReference`. That task produces `build/reports/expression-functions.md` and `build/reports/expression-functions.json`. The tables below are manually maintained; the generated report is the canonical reference.

### Basic functions

| Function | Signature | Description |
|---|---|---|
| `coalesce` | `(a, b, ...) → any` | Returns first non-null argument |
| `defined` | `(value) → boolean` | Returns true if value is not null |
| `notDefined` | `(value) → boolean` | Returns true if value is null |
| `isNull` | `(value) → boolean` | Synonym for notDefined |
| `default` | `(value, fallback) → text/any` | If value is null, return fallback |
| `null` | `() → null` | Returns null |
| `eq` | `(a, b) → boolean` | Returns true if a equals b |
| `neq` | `(a, b) → boolean` | Returns true if a does not equal b |
| `lt` | `(a, b) → boolean` | Returns true if a is less than b |
| `lte` | `(a, b) → boolean` | Returns true if a is less than or equal to b |
| `gt` | `(a, b) → boolean` | Returns true if a is greater than b |
| `gte` | `(a, b) → boolean` | Returns true if a is greater than or equal to b |
| `not` | `(value) → boolean` | Returns logical negation of value |

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
| `toInterlis1Date` | `(value) → text` | Converts DATE/TEXT to INTERLIS 1 date format |
| `toDate` | `(value) → Date` | Converts DATE/TEXT to INTERLIS.Date |
| `now` | `() → XMLDateTime` | Current timestamp (marked @NonDeterministic) |

### Enum functions

| Function | Signature | Description |
|---|---|---|
| `enumMap` | `(value, mapName) → enum` | Maps enum value using named map under `mapping.enums`. Source value is converted to text and looked up in the table. Target values `true`/`false` produce boolean values, numeric target values produce numeric values, other values produce enum values. Missing source mapping reports a warning and returns null. |
| `enumMapDefault` | `(value, mapName, fallback) → any` | Like `enumMap` but returns `fallback` when the source value has no mapping entry, without diagnostic. |
| `enumMapStrict` | `(value, mapName) → enum` | Like `enumMap` but reports an ERROR diagnostic (instead of WARNING) when the source value has no mapping entry. |
| `enumDefault` | `(value, fallback) → enum` | Returns value if defined, else fallback as enum value |
| `enumName` | `(value) → text` | Returns the string name of an enum value |

### Reference functions

| Function | Signature | Description |
|---|---|---|
| `refOid` | `(ref) → text` | Returns the OID from a reference value |
| `refEquals` | `(a, b) → boolean` | Checks if two references point to the same object |

### Math functions

| Function | Signature | Description |
|---|---|---|
| `div` | `(value, divisor) → numeric` | Divides value by divisor. Returns null on division by zero. |
| `mul` | `(value, factor) → numeric` | Multiplies value by factor |
| `add` | `(value, addend) → numeric` | Adds two numeric values |
| `sub` | `(value, subtrahend) → numeric` | Subtracts subtrahend from value |
| `round` | `(value, scale) → numeric` | Rounds to the given number of decimal places (HALF_UP) |
| `abs` | `(value) → numeric` | Returns the absolute value |
| `min` | `(a, b) → numeric` | Returns the smaller of two values |
| `max` | `(a, b) → numeric` | Returns the larger of two values |
| `toNumber` | `(value) → numeric` | Converts a text value to a number. Returns null on invalid input. |

### Lookup functions

| Function | Signature | Description |
|---|---|---|
| `oid` | `(alias) → text` | Returns the OID of the source object identified by the alias |
| `bagFirst` | `(alias, bagAttr, valueAttr) → text` | Returns the first value from a BAG attribute of a source object |
| `lookup` | `(classPath, keyAttr, keyValue, returnAttr) → text` | Compatibility function. Searches `SourceLookupIndex` across **all inputs** (unscoped). Returns null + warning on no match. Returns first value + warning on multiple matches with different return values. Return type is `UNKNOWN` at compile time. Prefer structural `joins` for modelled relationships. |
| `lookupOptional` | `(classPath, keyAttr, keyValue, returnAttr) → text` | Optional variant of `lookup` for expected 0..1 child/helper records. Searches `SourceLookupIndex` across **all inputs** (unscoped). Returns null without `LOOKUP_NO_MATCH` diagnostics on no match. Ambiguous matches with different return values still warn. Return type is `UNKNOWN` at compile time. |
| `lookupIn` | `(inputId, classPath, keyAttr, keyValue, returnAttr) → text` | Scoped variant of `lookup`. Searches `SourceLookupIndex` filtered to the given `inputId`. Same warning semantics as `lookup`. Return type is `UNKNOWN` at compile time. |
| `existsIn` | `(inputId, classPath, keyAttr, keyValue[, keyAttr, keyValue...]) → boolean` | Scoped existence check. Searches `SourceLookupIndex` filtered to `inputId`; additional key pairs must all match. Returns `false` without `LOOKUP_NO_MATCH` diagnostics when no record exists. |

Use `lookup()` when a missing match should be visible as a data-quality warning. Use `lookupOptional()` when the source model allows the child/helper record to be absent and a null target value is acceptable. Use `existsIn()` for filters and guards that only need to test whether a matching source record exists.

### Geometry functions

| Function | Signature | Description |
|---|---|---|
| `coordEquals` | `(coord1, coord2, tolerance) → boolean` | Returns true if two coordinates are within the given tolerance |
| `pointOnSurface` | `(surface) → coord` | Returns an existing point-on-surface from a geometry value when available; otherwise derives a deterministic interior point from SURFACE/MULTISURFACE geometry. Returns null and reports `GEOM_AREA_POINT_MISSING` when no point can be derived. |

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

The following are not yet supported in the expression engine:

- Arithmetic operators (`+`, `-`, `*`, `/`) — use `div(...)` and `mul(...)` as functions. Additional arithmetic functions may be added later.
- `lookupOne`/`lookupMany` (scoped StateStore lookups) — planned
- Nested structure paths (`alias.structure.attribute`) — reserved. Use `bags`/`nestedBags` for BAG OF STRUCTURE mappings.
