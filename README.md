# ili-transformer

Generic INTERLIS transformation engine.

**Status:** Phase 3 (Typed Mapping Compiler) — in progress.
**Primary use case:** DM01 ↔ DMAV transformation (scheduled for Phase 10+).

## Tech baseline

| Category | Value |
|---|---|
| Package root | `guru.interlis.transformer` |
| Build tool | Gradle 9.0 |
| Java version | 25 (toolchain) |
| CLI library | picocli 4.7.6 |
| Logging | SLF4J 2.0.17 |

## Current implementation status

- Two-pass execution engine (index → build + deferred refs → write)
- `TypedPlan` / `TransformPlan` — typed execution plan instead of raw YAML
- Model-aware `MappingCompiler.compileTyped()` — validates classes, attributes, roles, types, mandatory coverage
- `inspect-model` CLI for INTERLIS model inventory (JSON + Markdown output)
- `IliModelService` + `TypeSystemFacade` + `IliPath` for model metadata extraction
- `validate-mapping` and `transform` CLI commands
- INTERLIS model compilation via ili2c (ITF/XTF read/write via iox-ili)
- Basic expression support (`${alias.attr}`, `if(...)`, string literals)
- Diagnostics for unresolved/ambiguous references with 3-tier fallback
- 67 automated tests (11 test classes)
- DMAV V1.1 test models under `src/test/data/av/models/`
- Test ILI models under `src/test/data/models/`

### Known limitations (Phase 3)

- All target values set as strings (no typed value system)
- OID always sequential Long (not UUID-compatible for DMAV)
- No `where`-filter, Joins, BAG OF STRUCTURE in engine
- No modellbewusste Rollen-/Referenzauflösung at runtime
- `Engine`-`isAbstract()` class check only at compile time
- No `ilivalidator`-Support

## Run

```bash
./gradlew test
./gradlew run --args="inspect-model --model src/test/data/models/minimal.ili --modeldir src/test/data/models/"
./gradlew run --args="transform path/to/mapping.yaml --modeldir path/to/models"
./gradlew run --args="validate-mapping --mapping path/to/mapping.yaml"
ili-transformer --help
```

## Planned phases

See `docs/SPEC.md` for the full 15-phase specification.

| Phase | Title | Status |
|---:|---|---|
| 0 | Baseline, Repository-Hygiene und Namensentscheid | ✅ Done |
| 1 | DSL-/Config-Modell stabilisieren | Next |
| 2 | INTERLIS Model Service und Inventory | In Progress |
| 3 | Typed Mapping Compiler | In Progress |
| 4 | Expression Engine und Function Registry | Planned |
| 5 | Runtime MVP für 1:1 Scalar Mapping | Planned |
| 6 | OID-, Basket- und Writer-Strategien | Planned |
| 7 | Referenzen, Rollen und Associations | Planned |
| 8 | XLSX-Korrelation importieren | Planned |
| 9 | Mapping-Kandidatengenerator | Planned |
| 10 | DM01→DMAV LFP3 Minimalpilot | Planned |
| 11 | DMAV→DM01 LFP3 Minimalpilot | Planned |
| 12 | BAG OF STRUCTURE und Textpositionen | Planned |
| 13 | Geometrie-MVP | Planned |
| 14 | Erweiterter DM01↔DMAV-Analysebericht | ✅ Done |
| 15 | Stabilisierung, CLI-UX und Dokumentation | Planned |

## Documentation

- `docs/SPEC.md` — Full specification (German)
- `docs/dev/baseline.md` — Technical baseline and code status
- `docs/dev/rename-plan.md` — ilinexus → ili-transformer rename log
- `docs/dm01-dmav/` — DM01↔DMAV correlation table and future docs
