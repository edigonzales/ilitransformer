# Typed Plan

The `TransformPlan` is the compiled, typed execution plan produced by `MappingCompiler.compileTyped()`. The runtime engine works exclusively with `TransformPlan`, never with raw YAML.

## Plan records

### TransformPlan

```java
public record TransformPlan(
    String mappingVersion,
    JobPlan job,
    List<RulePlan> rules,
    Map<String, TypeSystemSnapshot> sourceTypeSystems,
    Map<String, TypeSystemSnapshot> targetTypeSystems,
    DiagnosticCollector diagnostics,
    OidStrategy oidStrategy,
    String oidNamespace,
    BasketStrategy basketStrategy,
    String failPolicy
) {}
```

### RulePlan

One per mapping rule. Represents a transformation from source objects to target objects.

```java
public record RulePlan(
    String id,
    List<SourcePlan> sources,
    TargetPlan target,
    String where,
    IdentitySpec identity,
    List<AssignmentPlan> assignments,
    List<RefPlan> refs,
    Map<String, BagPlan> bags,
    List<String> identitySourceKeys,
    MetadataSpec metadata
) {}
```

### SourcePlan

Describes a source class and how to read it.

```java
public record SourcePlan(
    String alias,
    String inputId,
    String className,
    String where
) {}
```

### AssignmentPlan

Maps a target attribute to an expression.

```java
public record AssignmentPlan(
    String targetAttribute,
    String expression,
    ExpressionKind kind,
    TypeInfo inferredType
) {}
```

### RefPlan

Describes a reference/association to resolve in Pass 2.

```java
public record RefPlan(
    String associationName,
    String roleName,
    String sourceRefExpression,
    String targetRuleId,
    boolean required,
    String expectedTargetClass
) {}
```

### BagPlan

Describes a BAG OF STRUCTURE mapping.

```java
public record BagPlan(
    String bagAttributeName,
    String sourceAlias,
    String sourceClass,
    String structureType,
    String whereExpression,
    List<AssignmentPlan> assignments
) {}
```

## ExpressionKind

Classifies expression types for type checking:

```java
public enum ExpressionKind {
    LITERAL,       // "#value", "42", "true"
    SOURCE_PATH,   // "${alias.attr}"
    FUNCTION_CALL, // "function(arg1, ...)"
    ENUM_REF,      // "#EnumValue"
    UNKNOWN        // unparseable or untyped
}
```

## TypeInfo

Describes the inferred type of an expression:

```java
public record TypeInfo(
    String typeName,  // "TEXT*12", "BOOLEAN", "Coord2", ...
    boolean isKnown
) {}
```

## Compilation process

1. Parse YAML → `JobConfig` (via `MappingLoader`)
2. Compile INTERLIS models → `TransferDescription` (via `IliModelService`)
3. Build `TypeSystemFacade` per model
4. For each rule in JobConfig:
   - Resolve source class, validate attributes
   - Resolve target class, validate attributes
   - Classify assignment expressions
   - Resolve refs: associations, roles, expected target classes
   - Check mandatory coverage
5. Build `TransformPlan` with all validated plans
6. Return `TransformPlan` with compile diagnostics

## Type checking

The compiler infers expression types and compares against target attribute types:

- `LITERAL` — classify by format: "#text" → TEXT, "42" → NUMERIC, "true" → BOOLEAN
- `SOURCE_PATH` — resolve source attribute type
- `FUNCTION_CALL` — delegate to `FunctionRegistry` for return type inference
- `ENUM_REF` — classify as ENUM

Type mismatch emits `ILITRF-MAP-TYPE-MISMATCH` (WARNING, not ERROR — allows runtime coercion).
