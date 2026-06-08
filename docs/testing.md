# Testing

## Test strategy

The project uses a layered testing approach:

| Level | Scope | Location |
|---|---|---|
| **Unit tests** | Individual classes and functions | `src/test/java/` |
| **Integration tests** | Multi-component flows with files | `src/test/java/` |
| **Golden tests** | Stable I/O comparison | `src/test/java/` |
| **Snapshot tests** | Report structure validation | `src/test/java/` |
| **ilivalidator tests** | Output validation against INTERLIS models | `src/test/java/` |

## Gradle commands

```bash
./gradlew test                    # Run all tests
./gradlew integrationTest         # Run integration tests (separate source set)
./gradlew validateGoldenTransfers # Validate golden test output with ilivalidator
```

## Test data structure

### Test models (`src/test/data/models/`)

Small, standalone ILI models for unit and integration tests:

| File | Purpose |
|---|---|
| `minimal.ili` | Minimal model with one class/attribute |
| `with-enums.ili` | Model with enum types |
| `with-references.ili` | Model with classes and associations |
| `with-structures.ili` | Model with STRUCTURE types |
| `with-bags.ili` | Model with BAG OF STRUCTURE |
| `p5-test.ili` | Phase 5 scalar mapping test model |

### AV models (`src/test/data/av/models/`)

Official DM01/DMAV INTERLIS models and real transfer data:

- `DM01AVCH24LV95D.ili` — DM01 model definition
- `DMAV_FixpunkteAVKategorie3_V1_1.ili` — DMAV fix points model
- `DMAV_Grundstuecke_V1_1.ili` — DMAV parcels model
- `DM01AVCH24LV95D_*.ili` — DM01 supporting models
- `so_2549.itf` — Real DM01 test data (municipality SO 2549)
- `DM01_Grundstuecke_449.itf` — Real parcel data
- `DMAV_Grundstuecke_V1_0.449.xtf` — Real DMAV parcel data

### Mapping test files (`src/test/resources/mappings/`)

Valid and invalid YAML mapping configurations for compiler and engine tests.

### Transfer test files (`src/test/resources/transfers/`)

Test ITF/XTF files for golden and integration tests.

## Unit tests

Coverage requirements per component:

- YAML loading and DSL versioning
- IliPath parsing
- Model service and TypeSystemFacade queries
- MappingCompiler validation (all error codes)
- Expression parser and all builtin functions
- Enum mapping logic
- OID strategy (deterministic UUID stability)
- Basket strategy
- CorrelationWorkbookImporter (small synthetic XLSX)
- MappingCandidateGenerator (score calculation, classification)
- StateStore CRUD operations
- ReferenceResolver
- IomObjectFactory

## Integration tests

End-to-end tests with real files:

1. ITF → XTF without references (minimal model)
2. ITF → XTF with references
3. XTF → ITF with references
4. BAG OF STRUCTURE from separate table
5. DM01 → DMAV LFP3 golden test
6. DMAV → DM01 LFP3 golden test
7. Validator pass test
8. Validator fail test (deliberately wrong mapping)

## Golden tests

Golden tests ensure stable, reproducible output:

```
src/test/resources/transfers/p5/
  input/input.xtf
  expected/expected.xtf
```

Comparisons:
- Not blind XML/string comparison
- XTF content normalization
- OID stability check (deterministic strategies)
- Object count comparison
- Attribute value comparison
- Reference integrity check

## Snapshot tests

Used for reports (Topic Gap Report, Correlation Import Report) to detect unintended changes in report structure or content.

## ilivalidator integration

The `IlivalidatorRunner` validates transfer files against INTERLIS models:

```java
IlivalidatorRunner runner = new IlivalidatorRunner();
runner.validate(file, modelDir, modelName, logPath);
```

Tests confirm:
- Valid output passes validation
- Deliberately wrong output fails validation
- Validation report is generated

## CI / local execution

All tests must pass on macOS ARM64 (development platform). The `junite-jupiter` test framework is used with AssertJ for assertions and Mockito for mocking.
