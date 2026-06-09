# ili-transformer

Generic INTERLIS transformation engine.

**Status:** Phase 16 complete, Phase 17 (Modell/Bindings-Korrektur) planned.
**Primary use case:** DM01 ↔ DMAV transformation (LFP3 pilot working in both directions).

## Tech baseline

| Category | Value |
|---|---|
| Package root | `guru.interlis.transformer` |
| Build tool | Gradle 9.0 |
| Java version | 25 (toolchain) |
| CLI library | picocli 4.7.6 |
| Logging | SLF4J 2.0.17 |

## Current implementation status

### Core engine (Phases 0–7)

- Two-pass execution engine (index → build + deferred refs → write)
- `TypedPlan` / `TransformPlan` — typed execution plan instead of raw YAML
- Model-aware `MappingCompiler.compileTyped()` — validates classes, attributes, roles, types, mandatory coverage
- `IliModelService` + `TypeSystemFacade` + `IliPath` + `RoleResolver`
- `ExpressionEngine` with function registry: Basic, String, Date, Enum, Reference, Math builtins
- `Value` type system (sealed interface: TextValue, NumberValue, BooleanValue, DateValue, EnumValue, CoordValue, GeometryObjectValue, ReferenceValue, NullValue)
- OID strategies: `preserve`, `integer`, `uuid`, `deterministicUuid`, `external`
- Basket strategies: `preserve`, `generateUuid`, `preserveOrGenerateUuid`, `byTopic`, `expression`
- Reference resolution with deferred refs, role-aware type checking, cardinality validation
- `failPolicy`: `strict`, `lenient`, `reportOnly`
- Stable output sorting for reproducible results
- `GeometryAdapter` with `IoxGeometryAdapter` and `ItfGeometryWriter`
- Diagnostics with structured codes, rule-IDs, source/target paths

### DM01 ↔ DMAV (Phases 8–14)

- **XLSX Import**: `CorrelationWorkbookImporter` parses the correlation table (250 hints from `DMAV_Korrelationstabelle_20260301.xlsx`)
- **Mapping Candidate Generator**: Classified candidates (high/medium/low/manual) from hints + model inventory + synonyms
- **LFP3 Pilot DM01→DMAV**: Working transformation with golden tests, ilivalidator validation
- **LFP3 Pilot DMAV→DM01**: Reverse direction with lossiness documentation
- **BAG OF STRUCTURE**: Textpositionen (Pos tables ↔ BAG OF Textposition)
- **Geometry MVP**: Coord/Polyline/Surface pass-through with diagnostics
- **Topic Gap Report**: Systematic analysis of remaining DM01/DMAV topics

### CLI commands

| Command | Status |
|---|---|
| `transform` | Working |
| `validate-mapping` | Working |
| `inspect-model` | Working |
| `import-correlation` | Gradle task only (→ Phase 15 CLI) |
| `generate-mapping` | Gradle task only (→ Phase 15 CLI) |

### Known limitations

- XTF-Reader with model context cannot read back own output files (IoxSyntaxException)
- `enumMap()` is a stub (pass-through with diagnostic warning)
- `external` OID strategy and `expression` basket strategy are stubs
- No AREA topology repair, no LINEATTR support
- No joins, splits, or merge semantics in engine
- No persistent StateStore (in-memory only)
- DM01↔DMAV: only LFP3 slice is fully implemented

## Run

```bash
./gradlew test
./gradlew run --args="inspect-model --model src/test/data/models/minimal.ili --modeldir src/test/data/models/"
./gradlew run --args="transform --mapping path/to/mapping.yaml --modeldir path/to/models"
./gradlew run --args="validate-mapping --mapping path/to/mapping.yaml"
```

### DM01 ↔ DMAV tasks

```bash
./gradlew importCorrelation
./gradlew generateMappingCandidates
./gradlew topicGapReport
./gradlew validateOutput
```

### ILI transformer tasks

```bash
./gradlew generateFeatureMatrix
./gradlew generateModelInventory
```

### Transform with validation

```bash
./gradlew run --args="transform --mapping profiles/dm01-to-dmav/lfp3.yaml --modeldir 'src/test/data/av/models/;https://models.interlis.ch' --validate"
```

## Planned phases

See `docs/SPEC.md` for the full 16-phase specification.

| Phase | Title | Status |
|---:|---|---|
| 0 | Baseline, Repository-Hygiene und Namensentscheid | Done |
| 1 | DSL-/Config-Modell stabilisieren | Done |
| 2 | INTERLIS Model Service und Inventory | Done |
| 3 | Typed Mapping Compiler | Done |
| 4 | Expression Engine und Function Registry | Done |
| 5 | Runtime MVP für 1:1 Scalar Mapping | Done |
| 6 | OID-, Basket- und Writer-Strategien | Done |
| 7 | Referenzen, Rollen und Associations | Done |
| 8 | XLSX-Korrelation importieren | Done |
| 9 | Mapping-Kandidatengenerator | Done |
| 10 | DM01→DMAV LFP3 Minimalpilot | Done |
| 11 | DMAV→DM01 LFP3 Minimalpilot | Done |
| 12 | BAG OF STRUCTURE und Textpositionen | Done |
| 13 | Geometrie-MVP | Done |
| 14 | Erweiterter DM01↔DMAV-Analysebericht | Done |
| 16 | Reproduzierbare Baseline, CI und Feature-Matrix | Done |

## Documentation

- `docs/SPEC.md` — Full specification (German)
- `docs/mapping-dsl.md` — Mapping DSL reference
- `docs/dev/baseline.md` — Technical baseline and code status
- `docs/dev/diagnostics.md` — Diagnostic codes reference
- `docs/dev/rename-plan.md` — ilinexus → ili-transformer rename log
- `docs/dm01-dmav/` — DM01↔DMAV correlation table and docs
- `docs/open-questions.md` — Open questions per phase

### Phase 15 documentation (in progress)

- `docs/expressions.md` — Expression language and builtins
- `docs/cli.md` — CLI reference
- `docs/testing.md` — Test strategy
- `docs/dev/architecture.md` — Architecture overview
- `docs/dev/typed-plan.md` — TypedPlan reference
- `docs/dev/state-store.md` — StateStore concept
- `docs/dm01-dmav/README.md` — DM01↔DMAV use case overview
- `docs/dm01-dmav/correlation-table.md` — XLSX correlation table interpretation
- `docs/dm01-dmav/status-matrix.md` — Topic/class/attribute mapping status
- `docs/dm01-dmav/lfp3-pilot.md` — LFP3 pilot documentation
- `docs/dm01-dmav/lossiness.md` — Information loss documentation
- `docs/dm01-dmav/open-questions.md` — DM01/DMAV-specific open questions
