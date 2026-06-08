# State Store

The StateStore is the runtime data store for the transformation engine. It indexes source objects, tracks target objects, and maps identities for reference resolution.

## Interface

```java
public interface StateStore {
    // Source records
    void putSourceRecord(SourceRecord record);
    Stream<SourceRecord> getSourceRecords(String className);
    Stream<SourceRecord> getAllSourceRecords(String className);
    void clearSourceRecords();

    // Target records
    void putTargetRecord(TargetRecord record);
    TargetRecord getTargetRecord(String className, String oid);
    Stream<TargetRecord> getAllTargetRecords(String className);
    Stream<TargetRecord> getAllTargetObjects();

    // Identity mapping
    void putIdMapping(IdMapping mapping);
    Optional<IdMapping> findIdMapping(SourceKey key);

    // Deferred references
    void putDeferredRef(DeferredRef ref);
    Stream<DeferredRef> getDeferredRefs(String ruleId);
    List<DeferredRef> allDeferredRefs();

    // OID generation
    String nextOid(OidStrategy strategy, String namespace, List<String> sourceKey, String targetClass);
}
```

## Key records

### SourceRecord

```java
public record SourceRecord(
    String fileId,       // input ID from job config
    String basketId,     // source basket ID
    String className,    // fully qualified class name
    String oid,          // source object OID
    IomObject object     // raw IOM object
) {}
```

### TargetRecord

```java
public record TargetRecord(
    String outputId,     // output ID from job config
    String basketId,     // target basket ID
    String className,    // fully qualified class name
    String oid,          // target object OID
    IomObject object     // built target object
) {}
```

### IdMapping

Maps source identity to target identity. Used in Pass 2 to resolve deferred references.

Two-level storage:
- **Scoped**: `sourceClass` + `sourceFileId` + `sourceBasketId` + `sourceOid` → `TargetKey`
- **Global** (cross-class): `sourceClass=null, sourceFileId=null, sourceBasketId=null` + `sourceOid` → `TargetKey`

The global entry enables finding target objects when the reference doesn't know the class context.

```java
public record SourceKey(
    String sourceClass,
    String sourceFileId,
    String sourceBasketId,
    String sourceOid
) {}
```

### DeferredRef

Stores an unresolved reference for Pass 2 resolution.

```java
public record DeferredRef(
    String ownerRuleId,         // rule that owns the reference
    IomObject ownerObject,      // target object that holds the reference
    String roleName,            // role/attribute name
    String sourceRefOid,        // source object OID from the reference
    String sourceClassName,     // source class name (from source model)
    String expectedTargetClass  // expected target class (from target model)
) {}
```

## Reference resolution (Pass 2)

Resolution order:

1. **Exact**: SourceClass + SourceOid + FileId + BasketId
2. **Input-wide**: SourceClass + SourceOid + FileId
3. **Basket-wide**: SourceClass + SourceOid + BasketId
4. **Global**: SourceClass + SourceOid (cross-class)
5. **Result**:
   - 0 matches → `ILITRF-RUN-REF-UNRESOLVED`
   - 1 match → resolved, check type compatibility
   - >1 match → `ILITRF-RUN-REF-AMBIGUOUS`
6. **Type check**: resolved target class vs expected target class → `ILITRF-RUN-REF-TYPE-MISMATCH`

## OID strategies

| Strategy | Behavior |
|---|---|
| `preserve` | Copy source OID (only if compatible with target OID type) |
| `integer` | Sequential integer (not for UUIDOID target models) |
| `uuid` | Random UUID |
| `deterministicUuid` | UUIDv3 from namespace + source key (MD5-based) |
| `external` | Stub (future: from expression) |

## InMemoryStateStore

Current implementation using in-memory `HashMap`s and `ArrayList`s.
- Source records: indexed by `className`
- IdMapping: indexed by `SourceKey`
- DeferredRefs: lists per `ruleId`, plus global list
- Target records: tracked per class + OID

## Future: PersistentStateStore

Architecture allows plugging in persistent backends (SQLite, H2, DuckDB) for large datasets exceeding memory limits. The `StateStore` interface is designed to be implemented with a database-backed store.
