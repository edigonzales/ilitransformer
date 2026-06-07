# Baseline

Stand nach Abschluss von Phase 3 (2026-06-07).

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
- `GeometryAdapter`-Interface mit NoOp-Implementierung
- 67 Tests in 11 Testklassen
- Test-ILI-Modelle unter `src/test/data/models/`
- DMAV V1.1 Testmodelle unter `src/test/data/av/models/`

### Bekannte Einschränkungen (als TODO dokumentiert)
- Alle Zielwerte werden als String gesetzt (kein typisiertes Value-System) — Phase 4
- OID-Strategie immer fortlaufende Longs (nicht UUID-kompatibel für DMAV) — Phase 6
- `ExpressionEngine` nur minimal (nur `if`, Literale, `${path}`) — Phase 4
- Typ-Inferenz für Funktionsaufrufe noch nicht (alle als UNKNOWN) — Phase 4
- Keine `where`-Filter, Joins, BAG OF STRUCTURE — spätere Phasen
- Keine modellbewusste Rollen-/Referenzauflösung in Runtime — Phase 7
- Kein `ilivalidator`-Support — Phase 10+

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
│   │   ├── geometry/    (GeometryAdapter, NoOpGeometryAdapter)
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

## Nächste Phase: Phase 4 (Expression Engine und Function Registry)

Geplante Änderungen:
- Expression AST oder kontrollierte Integration einer Expression-Library
- `FunctionRegistry` mit INTERLIS-spezifischen Funktionen
- Basisfunktionen: `if`, `coalesce`, `default`, `isNull`, `isDefined`
- Stringfunktionen: `concat`, `substring`, `trim`, `upper`, `lower`, `replace`, `truncate`
- Datumsfunktionen: `date`, `dateTime`, `xmlDateTime`, `today`
- Enumfunktionen: `enumMap`, `enumDefault`, `enumName`
- Typisierte Value-Objekte (keine Strings)
- Typ-Inferenz für Funktionsaufrufe im Compiler
