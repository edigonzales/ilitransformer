# Phase 26: Performance, Indizes und grosse Transfers

## Ziel

Die Engine verarbeitet realistische AV-Datensätze ohne quadratische Laufzeit. Vier Vollschleifen (O(|rules| × |sourceRecords|)) wurden durch O(1)-Index-Lookups ersetzt.

## Implementiert

### Neue Klassen (11)

| Klasse | Package | Beschreibung |
|---|---|---|
| `RuleDispatchIndex` | `engine` | Pre-computes Map<(inputId, sourceClass), List<RulePlan>>; eliminiert SourceRecord×Rule-Vollscan |
| `SourceIndexingService` | `engine` | Extrahiert Pass 1 (Source-Scan/Indexing) aus TransformationEngine |
| `RuleExecutionService` | `engine` | Extrahiert Pass 2 (Target-Erzeugung, Rule-Dispatch) aus TransformationEngine |
| `TargetObjectFactory` | `engine` | Extrahiert Target-Objekt-Erzeugung, OID-Generierung, Referenz-Deferral, BAG-Processing |
| `AssignmentExecutionService` | `engine` | Extrahiert Expression-Evaluierung und Attribut-Setzen |
| `OutputWritingService` | `engine` | Extrahiert deterministisches Output-Writing (sortiert: basketId → class → oid) |
| `ExecutionMetrics` | `engine` | Performance-Messung: Laufzeiten, Join-Lookups, BAG-Lookups, Targets-by-Class |
| `ExecutionMetricsSnapshot` | `engine` | Immutable Snapshot für Report-Ausgabe |
| `TransferFormat` | `testutil` | Enum: ITF, XTF |
| `TransferDatasetDescriptor` | `testutil` | Record: id, transferFile, format, declaredModels, modelDirectories, sizeBytes |
| `RealDatasetCatalog` | `testutil` | Scan/Classify von Transfer-Dateien ohne harte Dateinamen |

### Geänderte Klassen

| Klasse | Änderung |
|---|---|
| `TransformationEngine` | Von 1264 → ~430 Zeilen. Delegiert an neue Services. Legacy-APIs bleiben erhalten. |
| `JobRunner` | Erzeugt alle Indizes (ReferenceIndex, SourceLookupIndex, ParentChildIndex) und ExecutionMetrics. Engine-Konstruktion mit vollständiger Service-Injection. |
| `TransformationReportWriter` | Nimmt ExecutionMetricsSnapshot entgegen; JSON/Markdown enthalten Performance-Sektion. |

### Indices (alle O(1)-Lookups)

- **Rule Dispatch Index** nach (inputId, sourceClass) → NEU
- **Source Lookup Index** nach (sourceClass, attribute, value) → bestand bereits
- **Reference Index** nach (SourceObjectKey) → bestand bereits
- **Parent/Child BAG Index** nach (sourceClass, refAttr, parentOid) → bestand bereits
- **Target Object Index** via StateStore → bestand bereits

### Quadratische Schleifen eliminiert

1. `pass2BuildTargets`: `for(Rule) { for(SourceRecord) }` → `for(SourceRecord) { dispatchIndex.rulesFor(id, class) }`
2. `processCreatePlan`: `for(SourceRecord) { findMatchingSource }` → dispatch via Index
3. `indexBagChild`: `for(Rule) { for(Bag) }` per SourceRecord → pre-computed embedBagsFor(id, class)
4. `expandBagStructures`: `for(Rule) { for(Bag) }` per SourceObject → pre-computed expandBagsFor(id, class)

## Tests

### Unit Tests (neu)
- `RuleDispatchIndexTest` (3 Tests): Dispatch pro Input+Class, empty plan, unknown input
- `ExecutionMetricsTest` (3 Tests): Snapshot-Korrektheit, zero-elapsed, summary format
- `DeterministicOutputOrderTest` (2 Tests): Sortierung class+oid, deterministisch bei Wiederholung

### Real Data Smoke Tests (neu)
- `FullDm01ReadSmokeTest`: Liest `DM01-AV-CH.itf` mit DM01-Modell; berichtet Objektzahlen, Baskets, Laufzeit
- `FullDmavReadSmokeTest`: Liest `DMAVTYM_Alles_V1_1.xtf` mit DMAV-Modell; gleicher Report

## Kompilierungs- und Teststatus

- `./gradlew test`: 416 tests passed
- `./gradlew integrationTest`: 22 pre-existing failures (nicht durch Phase 26 verursacht)
- `./gradlew realDataTest`: Bestanden (wenn Modelle verfügbar; sonst skipped via assumeTrue)

## Bekannte Einschränkungen

- `TransformationEngine` verwendet im aktuellen `JobRunner` die minimal-Konstruktion (ReferenceIndex=null) für einige Legacy-Tests. Vollständige Index-Injection ist für den typisierten Pfad aktiv.
- Der persistente `StateStore` (DuckDB) ist nicht implementiert – Entscheidungsbericht: aktueller InMemoryStateStore skaliert ausreichend für die DM01↔DMAV-Datensätze (~50K Objekte).
- `EXTERNAL` OID-Strategie wird weiterhin abgelehnt (wie in Phase 18 definiert).
- Integration Tests haben 22 pre-existing failures (unabhängig von Phase 26).

## Offene Fragen

- Soll der `RuleExecutionService` auch `ReferenceIndex` erhalten für vollständig index-basierte Referenzauflösung?
- Sollte `BagTransformationService` ebenfalls `ExecutionMetrics` für Bag-spezifische Metriken erhalten?
- Ist ein persistenter StateStore für >1M Objekte nötig?

## Migration Notes

- Die Legacy-API (`TransformationEngine.run(JobConfig, ...)`) bleibt unverändert.
- Die typisierte API (`TransformationEngine.runTyped(TransformPlan, ...)`) verwendet jetzt intern die neuen Services.
- `JobRunner` erzeugt jetzt explizit `InMemoryReferenceIndex`, `InMemorySourceLookupIndex`, `InMemoryParentChildIndex` und `ExecutionMetrics`.
- `TransformationReportWriter.writeJson/Markdown` hat einen zusätzlichen Parameter `ExecutionMetricsSnapshot metricsSnapshot`.
