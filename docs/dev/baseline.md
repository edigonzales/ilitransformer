# Baseline

Stand nach Abschluss von Phase 16 (2026-06-09).

## Technische Basis

| Kategorie | Wert |
|---|---|
| **Build-Tool** | Gradle 9.0 |
| **Java** | 25 (OpenJDK Temurin-25.0.3+9-LTS) |
| **Package** | `guru.interlis.transformer` |
| **Projektname** | `ilitransformer` |
| **Group** | `guru.interlis` |
| **Version** | `0.1.0` |

## Abhängigkeiten

| Dependency | Version | Verwendung |
|---|---|---|
| `ch.interlis:iox-ili` | 1.24.1 | ITF/XTF I/O |
| `ch.interlis:ili2c-core` | 5.6.6 | INTERLIS-Modellkompilierung |
| `ch.interlis:ili2c-tool` | 5.6.6 | INTERLIS-Modellkompilierung |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17.1 | YAML/JSON-Parsing |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | 2.17.1 | YAML-Support |
| `info.picocli:picocli` | 4.7.6 | CLI-Argument-Parsing |
| `org.slf4j:slf4j-api` | 2.0.17 | Logging-API |
| `org.slf4j:slf4j-simple` | 2.0.17 | Logging-Implementierung |
| `org.apache.poi:poi-ooxml` | 5.4.0 | XLSX-Parsing (compileOnly + test) |
| `ch.interlis:ilivalidator` | 1.15.0 | Validierung (compileOnly + test) |
| `org.junit.jupiter:junit-jupiter` | 5.10.2 | Test-Framework |
| `org.assertj:assertj-core` | 3.25.3 | Fluent Assertions |
| `org.mockito:mockito-core` | 5.12.0 | Mocking |
| `org.junit.platform:junit-platform-launcher` | (via Gradle) | JUnit Platform (Gradle 9.0) |

## Aktueller Funktionsumfang

### Core Engine (Phasen 0–7)
- Gradle-Java-Projekt mit Java 25 Toolchain
- CLI-Kommandos: `transform`, `validate-mapping`, `inspect-model`, `import-correlation`, `generate-mapping`
- `IliModelService` + `TypeSystemFacade` + `IliPath` + `RoleResolver`
- `ModelInventory` + `InventorySerializer`
- `MappingCompiler.compileTyped()` → `TransformPlan`
- Typed Plan: `TransformPlan`, `RulePlan`, `SourcePlan`, `AssignmentPlan`, `RefPlan`, `BagPlan`
- Expression Engine mit AST-Parser und typisierten Values
- FunctionRegistry mit Builtins: Basic, String, Date, Enum, Reference, Math
- Zwei-Pass-Engine: Pass 0 (index), Pass 1 (build), Pass 2 (refs), Write (stable sort)
- OID-Strategien: `preserve`, `integer`, `uuid`, `deterministicUuid` (UUIDv3), `external` (Stub)
- Basket-Strategien: `preserve`, `generateUuid`, `preserveOrGenerateUuid`, `byTopic`, `expression` (Stub)
- Reference Resolution mit DeferredRef, Type-Check, Cardinality-Validation
- `GeometryAdapter`-Interface + `IoxGeometryAdapter` + `ItfGeometryWriter`
- ITF/XTF I/O via iox-ili (Reader/Writer)
- `failPolicy`: `strict`, `lenient`, `reportOnly`
- `InMemoryStateStore` mit Cross-Class IdMapping
- `DiagnosticCollector` mit ERROR/WARNING/INFO

### DM01 ↔ DMAV (Phasen 8–14)
- XLSX-Import: Gradle-Task `importDmavCorrelation` + CLI `import-correlation`
- `CorrelationHint`-Record + Importer/Exporter (~250 Hints)
- Mapping Candidate Generator: `generateDm01DmavMappings` + CLI `generate-mapping`
- Klassifizierung: high/medium/low/manual mit Confidence-Score
- Synonym-Liste: `src/main/resources/dmav/synonyms.json`
- YAML-Generierung: `MappingCandidateExporter` via Jackson
- DM01→DMAV LFP3 Pilot mit Golden Test + ilivalidator
- DMAV→DM01 LFP3 Pilot mit Golden Test + Loss-Dokumentation
- BAG OF STRUCTURE: Textpositionen in beide Richtungen
- Geometry MVP: Coord/Polyline/Surface Passthrough
- Topic Gap Report: Systematische Analyse (Phase 14)
- Offizielle AV-Modelle unter `src/test/data/av/models/`

### Tests
- ~90+ Tests in ~30 Testklassen
- Unit-Tests für alle Komponenten
- Integrationstests mit echten Dateien
- Golden-Tests für deterministische Ausgabe
- Snapshot-Tests für Reports
- ilivalidator-basierte Validierungstests

## Bekannte Einschränkungen

- XTF-Reader mit Modellkontext liest eigene Output-Dateien nicht zurück (IoxSyntaxException)
- `enumMap()` ist ein Stub (Pass-through mit Diagnostic-Warnung)
- `external` OID-Strategie und `expression` Basket-Strategie sind Stubs
- Keine AREA-Topologie-Reparatur
- Kein LINEATTR-Support
- Keine Joins, Splits oder Merge-Semantik in der Engine
- Kein persistenter StateStore (nur InMemory)
- DM01↔DMAV: Nur LFP3-Slice vollständig implementiert

## Phase 16: Reproduzierbare Baseline, CI und Feature-Matrix

Abgeschlossen am 2026-06-09.

- Unit-Tests und Integrationstests in getrennten SourceSets (`src/test/` und `src/integrationTest/`)
- `./gradlew clean check` läuft `test` und `integrationTest` gemeinsam
- Optionales `realDataTest` SourceSet für langsame Echtdaten-Validierung
- `FeatureStatus`-Enum und `FeatureMatrix` mit statischer Feature-Liste
- `generateFeatureMatrix` Gradle-Task schreibt Markdown und JSON nach `build/reports/`
- CI-Workflow via GitHub Actions (`.github/workflows/ci.yml`)
- `CheckedInModelsCompileTest`: kompiliert alle eingecheckten `.ili`-Modelle
- `CheckedInTransfersValidateTest`: validiert alle eingecheckten Transferdateien (mit Allowlist)
- `BuildLayoutTest`: prüft Projektstruktur und Build-Konfiguration
- `RealDatasetCatalogTest`: katalogisiert und prüft Echtdaten
- `FeatureMatrixTest`: validiert dass alle `SUPPORTED`-Features Test-Referenzen haben
- Alle Validator-Tests prüfen `valid` (keine `println`-nur Tests mehr)

## Nächste Phase: Phase 17 (Modell-, Input-, Output- und TypeSystem-Bindings korrigieren)

Status: Geplant.

Ziele:
- `InputBinding` / `OutputBinding` / `ModelRegistry` einführen
- `TransformPlan` auf Input-/Output-IDs umstellen
- `failPolicy` von String auf Enum umstellen
- `JobRunner.prepare()` und `MappingCompiler.compileTyped()` mit `ModelRegistry`
