# Baseline

Stand nach Abschluss von Phase 0 (2026-06-07).

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
- Minimaler CLI-Einstieg via picocli (`--help`, `--version`, `--modeldir`)
- YAML-Mapping-Konfiguration via Jackson
- Zwei-Pass-Transformations-Engine (Pass 1: Index, Pass 2: Build + Deferred Refs, Write)
- INTERLIS-Modellkompilierung via ili2c
- ITF/XTF I/O via iox-ili (Reader/Writer)
- `MappingCompiler` mit struktureller YAML-Validierung
- `ExpressionEngine` mit `${alias.attr}`, `if(cond, a, b)` und String-Literalen
- `InMemoryStateStore` mit 3-Tier-Fallback für Referenzauflösung
- `DiagnosticCollector` mit ERROR/WARNING/INFO
- `GeometryAdapter`-Interface mit NoOp-Implementierung
- 5 Unit-Tests (ExpressionEngine, MappingCompiler, StateStore, TransformationEngine, CliMain)
- DMAV V1.1 Testmodelle unter `src/test/data/av/models/`

### Bekannte Einschränkungen (als TODO dokumentiert)
- `MappingCompiler` validiert nur YAML-Struktur, nicht gegen INTERLIS-Metamodell
- Alle Zielwerte werden als String gesetzt (kein typisiertes Value-System)
- OID-Strategie immer fortlaufende Longs (nicht UUID-kompatibel für DMAV)
- `ExpressionEngine` nur minimal (nur `if`, Literale, `${path}`)
- Keine `where`-Filter, Joins, BAG OF STRUCTURE
- Keine modellbewusste Rollen-/Referenzauflösung
- YAML-Feld `clazz:` statt `class:` (wird in Phase 1 korrigiert)
- Source-Matching verwendet `String.contains()` (fragil, wird in Phase 1 korrigiert)
- Kein `ilivalidator`-Support

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
│   │   ├── diag/        (Diagnostic, DiagnosticCollector, Severity)
│   │   ├── engine/      (TransformationEngine, RuleRuntime)
│   │   ├── expr/        (ExpressionEngine)
│   │   ├── geometry/    (GeometryAdapter, NoOpGeometryAdapter)
│   │   ├── interlis/    (InterlisIoFactory, InterlisModelLoader)
│   │   ├── mapping/
│   │   │   ├── compiler/ (MappingCompiler)
│   │   │   └── model/    (JobConfig, MappingLoader)
│   │   └── state/       (StateStore, InMemoryStateStore, ...)
│   └── test/
│       ├── java/guru/interlis/transformer/  (5 Testklassen)
│       └── data/av/                         (Test-Modelle + Transferdaten)
└── LICENSE
```

## Git-Status

- Branch: `main`
- Letzter Commit vor Phase 0: `9d8f5e7` ("move data and add models")
- Phase 0 umfasst: Hygiene, Umbenennung, CLI-Umbau, Modell-Update

## Nächste Phase: Phase 1 (DSL-/Config-Modell stabilisieren)

Geplante Änderungen:
- `class:` im YAML statt `clazz:` (via `@JsonProperty`)
- `sources[].inputs` als Liste unterstützen
- `version`-Feld für Mapping-Dateien
- CLI-Befehl `validate-mapping`
- JSON Schema für Mapping-DSL
- Fehlerhafte YAML-Strukturen mit klaren Diagnostics melden
