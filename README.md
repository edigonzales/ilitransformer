# ilitransformer

Java/Gradle-Werkzeug zur modellbewussten Transformation von INTERLIS-Transferdaten. Der aktuelle Referenz-Use-Case ist DM01 ↔ DMAV, die Engine bleibt aber generisch und darf keine DM01/DMAV-Sonderlogik in die generischen Laufzeitpfade ziehen.

## Wichtige Verzeichnisse

- `profiles/` enthält die autoritativen, versionierten DM01/DMAV-Profile auf Root-Ebene.
- `src/test/data/` enthält Modelle, offizielle AV-Artefakte und vollständige Echtdatensätze.
- `src/test/resources/` enthält kleine kuratierte Fixtures, Test-Mappings und Snapshots.
- `docs/` enthält die aktive Benutzer- und Projektdokumentation.
- `docs/dev/`, `docs/SPEC.md`, `docs/SPEC_V2.md` und `docs/open-questions.md` sind historische Arbeitsdokumente.

## Testsuiten

| Suite | Zweck | Pfad | Gradle-Task |
|---|---|---|---|
| `test` | Unit-Tests plus schnelle Repo-Vertrags-/Artefakt-Checks | `src/test/java/` | `./gradlew test` |
| `integrationTest` | synthetische End-to-End-, CLI- und Validator-Integration | `src/integrationTest/java/` | `./gradlew integrationTest` |
| `realDataTest` | langsame Profil-, Fixture- und Echtdaten-Regression | `src/realDataTest/java/` | `./gradlew realDataTest` |

`./gradlew check` führt bewusst `test` und `integrationTest` aus. `realDataTest` bleibt separat.

## Zentrale Kommandos

```bash
./gradlew test
./gradlew integrationTest
./gradlew realDataTest
./gradlew check
./gradlew installDist
```

DM01/DMAV-spezifische Hilfstasks:

```bash
./gradlew importDmavCorrelation
./gradlew generateDm01DmavMappings
./gradlew topicGapReport
./gradlew produceDm01BbItf
```

## Produktive Profile

Produktive DM01/DMAV-Profile liegen unter:

```text
profiles/dm01-to-dmav/1.1/
profiles/dmav-to-dm01/1.1/
```

Tests, die produktive Profile prüfen, laden diese Dateien direkt und materialisieren nur Ein-/Ausgabepfade für die jeweilige Testumgebung.

## Aktive Dokumentation

- `docs/testing.md`
- `docs/cli.md`
- `docs/mapping-dsl.md`
- `docs/dm01-dmav/README.md`
- `docs/dm01-dmav/status-matrix.md`
