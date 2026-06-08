# Baseline

Stand nach Abschluss von Phase 6 (2026-06-07).

## Technische Basis

| Kategorie | Wert |
|---|---|
| **Build-Tool** | Gradle 9.0 |
| **Java** | 25 (OpenJDK Temurin-25.0.3+9-LTS) |
| **Package** | `guru.interlis.transformer` |
| **Projektname** | `ili-transformer` |
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
| `org.junit.jupiter:junit-jupiter` | 5.10.2 | Test-Framework |
| `org.assertj:assertj-core` | 3.25.3 | Fluent Assertions |
| `org.mockito:mockito-core` | 5.12.0 | Mocking |
| `org.junit.platform:junit-platform-launcher` | (via Gradle) | JUnit Platform (erforderlich für Gradle 9.0) |

## Aktueller Funktionsumfang

### Vorhanden
- Gradle-Java-Projekt mit Java 25 Toolchain
- CLI-Kommandos: `transform`, `validate-mapping`, `inspect-model`
- `IliModelService` – kompiliert Modelle via ili2c und extrahiert Metadaten
- `TypeSystemFacade` – stabile Query-API für Klassen, Attribute, Rollen, Typen
- `IliPath` – Parser für INTERLIS-Pfade (`Model.Topic.Class.Attribute`)
- `ModelInventory` + `InventorySerializer` – generiert JSON- und Markdown-Inventar
- **`MappingCompiler.compileTyped()`** – modellbewusste Validierung (Klassen, Attribute, Rollen, Typen, Mandatory-Coverage, Zyklen) + produziert `TransformPlan`
- **`TypedPlan`-Records** – `TransformPlan`, `RulePlan`, `SourcePlan`, `AssignmentPlan`, `RefPlan`
- **Einfache Typkompatibilitäts-Prüfung** – Expression-Klassifikation (Literal/Path/Function) + Typ-Vergleich
- **`CompilerReport`** – Compiler-Diagnostics als JSON/Markdown
- Zwei-Pass-Transformations-Engine (mit TypedPlan-Unterstützung via `runTyped()`)
- ITF/XTF I/O via iox-ili (Reader/Writer)
- `ExpressionEngine` mit `${alias.attr}`, `if(cond, a, b)` und String-Literalen
- `InMemoryStateStore` mit 3-Tier-Fallback für Referenzauflösung
- `DiagnosticCollector` mit ERROR/WARNING/INFO
- `GeometryAdapter`-Interface mit `IoxGeometryAdapter` als zentralem Geometrie-Adapter
- **OID-Strategien**: `preserve`, `integer`, `uuid`, `deterministicUuid` (UUIDv3 via `java.util.UUID.nameUUIDFromBytes()`), `external` (Stub)
- **Basket-Strategien**: `preserve`, `generateUuid`, `preserveOrGenerateUuid`, `byTopic`, `expression` (Stub) via `BasketRouter`
- **Stable Sorting**: Target-Objekte werden im Writer nach `getobjecttag()` → `getobjectoid()` sortiert
- **OID-Typ-Validierung**: Compiler prüft `integer`-Strategie gegen `UUIDOID`-Zielmodell
- **`TransformResult`** – enthält jetzt `oidStrategy`/`basketStrategy` im Summary
- **RoleResolver** – modellbewusste Rollen-/Referenzauflösung mit Type-Check und Cardinality-Prüfung (Phase 7)
- **failPolicy-Integration**: `strict` → ERROR, `lenient` → WARNING für Referenzauflösung
- **DM01↔DMAV XLSX-Import**: Gradle-Task `importCorrelation` via Apache POI (Build-Time, nicht Runtime)
- **`CorrelationHint`**-Record + Importer/Exporter (250 Hints aus `DMAV_Korrelationstabelle_20260301.xlsx`)
- **Mapping Candidate Generator**: Gradle-Task `generateMappingCandidates` erzeugt klassifizierte Mapping-Vorschläge aus Hints + Model Inventory
- **Synonym-Liste**: `src/main/resources/dmav/synonyms.json` mit DM01↔DMAV Attribut-Paaren
- **YAML-Generierung**: `MappingCandidateExporter` serialisiert `JobConfig` via Jackson → validierbare Mapping-Dateien
- 91 Tests in 18 Testklassen
- Test-ILI-Modelle unter `src/test/data/models/`
- DMAV V1.1 Testmodelle unter `src/test/data/av/models/`

### Bekannte Einschränkungen (als TODO dokumentiert)
- `where`-Filter auf Rule-Ebene wird jetzt ausgewertet (Phase 5) ✅
- OID-Strategie `deterministicUuid` (UUIDv3) funktional, `external` ist Stub (Phase 6) ✅
- Basket-Strategien `preserve`, `generateUuid`, `preserveOrGenerateUuid`, `byTopic` funktional (Phase 6) ✅
- Stable output sorting aktiv (Phase 6) ✅
- Keine Joins, BAG OF STRUCTURE — spätere Phasen
- Keine modellbewusste Rollen-/Referenzauflösung in Runtime — Phase 7
- Kein `ilivalidator`-Support — Phase 10+
- Keine produktive DMAV-Ausgabe — Phase 10+
- XTF-Reader mit Modellkontext liest eigene Output-Dateien nicht zurück (IoxSyntaxException)

## Repository-Struktur (nach Phase 0)

```
.
├── README.md
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── docs/
│   ├── SPEC.md                          # Vollständige Spezifikation
│   ├── dev/
│   │   ├── baseline.md                  # Diese Datei
│   │   ├── rename-plan.md               # Umbenennungsplan
│   │   └── adr/
│   └── dm01-dmav/
│       └── DMAV_Korrelationstabelle_20260301.xlsx
├── src/
│   ├── main/java/guru/interlis/transformer/
│   │   ├── app/         (CliMain, JobRunner)
│   │   ├── cli/         (InspectModelCommand)
│   │   ├── diag/        (Diagnostic, DiagnosticCollector, Severity)
│   │   ├── engine/      (TransformationEngine, RuleRuntime)
│   │   ├── expr/        (ExpressionEngine)
│   │   ├── geometry/    (GeometryAdapter, IoxGeometryAdapter, ItfGeometryWriter)
│   │   ├── interlis/    (InterlisIoFactory, InterlisModelLoader)
│   │   ├── mapping/
│   │   │   ├── compiler/ (MappingCompiler)
│   │   │   └── model/    (JobConfig, MappingLoader)
│   │   ├── model/       (IliPath, IliModelService, TypeSystemFacade, ModelInventory, InventorySerializer)
│   │   └── state/       (StateStore, InMemoryStateStore, ...)
│   └── test/
│       ├── java/guru/interlis/transformer/  (14+ Testklassen inkl. model/, cli/)
│       └── data/
│           ├── av/                           (Test-Modelle + Transferdaten)
│           └── models/                       (minimal.ili, with-enums.ili, with-associations.ili, with-structures.ili)
└── LICENSE
```

## Git-Status

- Branch: `main`
- Letzter Commit vor Phase 0: `9d8f5e7` ("move data and add models")
- Phase 0 umfasst: Hygiene, Umbenennung, CLI-Umbau, Modell-Update

## Nächste Phase: Phase 6 (OID-, Basket- und Writer-Strategien)

Geplante Änderungen:
- OID-Strategien: `preserve`, `integer`, `uuid`, `deterministicUuid`
- Basket-Strategien: `preserve`, `fixed`, `perTopic`, `fromExpression`
- Deterministic UUIDs (UUIDv5) für DMAV
- Stable sorting für reproduzierbare Golden-Tests
- Writer-Reihenfolge nach Modell-/Topic-/Klassenabhängigkeiten
