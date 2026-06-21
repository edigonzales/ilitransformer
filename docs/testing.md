# Testing

## Testsuiten

| Suite | Zweck | Code | Zugehörige Dateien |
|---|---|---|---|
| `test` | Unit-Tests plus schnelle Repo-Vertrags-/Artefakt-Checks | `src/test/java/` | `src/test/data/`, `src/test/resources/` |
| `integrationTest` | synthetische End-to-End-, CLI- und Validator-Integration | `src/integrationTest/java/` | primär kleine Testmodelle und kuratierte Fixtures |
| `realDataTest` | langsame Profil-, Fixture- und Echtdaten-Regression | `src/realDataTest/java/` | `profiles/`, `src/test/data/DMAV_Version_1_1/`, `src/test/resources/fixtures/dm01-dmav/` |

## Gradle-Integration

```bash
./gradlew test
./gradlew integrationTest
./gradlew realDataTest
./gradlew runDm01DmavFullRun -Pdm01DmavFullRunManifest=products/dm01-dmav/full-runs/so-2549/manifest.yaml -Pdm01DmavFullRunSource=/abs/pfad/zur/quelle.itf
./gradlew check
```

- `check` hängt an `integrationTest`, aber bewusst nicht an `realDataTest`.
- `realDataTest` ist für langsame Echtdaten- und Profil-Regression separat ausführbar.
- `runDm01DmavFullRun` ist ein opt-in Produktlauf für manifestgesteuerte externe Volldatensätze und hängt bewusst nicht an `check`.

## CI-Integration

`realDataTest` wird bewusst nicht von `check` ausgeführt, sondern über einen
separaten GitHub-Actions-Workflow (`.github/workflows/real-data.yml`):
- manueller Start via `workflow_dispatch` in der GitHub UI
- automatischer Lauf jeden Montag 03:00 UTC
- Testergebnisse und Reports werden als Artefakte hochgeladen

## Ressourcenablage

| Pfad | Rolle |
|---|---|
| `profiles/` | autoritative, versionierte DM01/DMAV-Profile |
| `src/test/data/models/` | kleine lokale `.ili`-Modelle für Unit- und Integrationstests |
| `src/test/data/av/models/` | eingecheckte AV-Modelle für DM01/DMAV-Tests |
| `src/test/data/DMAV_Version_1_1/` | vollständige reale DM01- und DMAV-Datensätze |
| `src/test/resources/mappings/` | Test-Mappings für synthetische Modelle und CLI-Tests |
| `src/test/resources/transfers/` | kleine kuratierte Transfer-Fixtures |
| `src/test/resources/fixtures/dm01-dmav/` | Topic-Fixtures mit kuratierten `*-minimal`- und extractor-owned `*-real-extract`-Transfers |
| `src/test/resources/dm01-dmav/` | Snapshots für Report-Tests |
| `products/dm01-dmav/full-runs/` | manifestgesteuerte Voll-Run-Bundles mit normalisierten Soll-Reports |

## Was `realDataTest` abdeckt

`realDataTest` ist die Suite für alles, was nicht mehr als schneller synthetischer Test gelten soll:

- Lesen und Inventarisieren vollständiger Echtdatensätze
- End-to-End-Läufe gegen produktive Profile unter `profiles/`
- semantische Roundtrips mit kuratierten Minimal-Fixtures
- semantische Roundtrips und End-to-End-Läufe mit checked-in `real-extract`-Fixtures
- Extraktion und Validierung kleiner `real-extract`-Fixtures aus den vollständigen Datensätzen

Die Suite ist absichtlich separat, damit `check` lokal und in CI schnell bleibt.

## Opt-in Full Runs

Manifestgesteuerte Voll-Läufe gegen externe Originaldatensätze sind Produktartefakte, keine Standard-Testfixtures. Sie laufen bewusst nur manuell:

```bash
./products/dm01-dmav/full-runs/run-full-run.sh so-2549 /abs/pfad/zur/2549.ch.so.agi.av.dm01_ch.itf
```

oder direkt über Gradle:

```bash
./gradlew runDm01DmavFullRun \
  -Pdm01DmavFullRunManifest=products/dm01-dmav/full-runs/so-2549/manifest.yaml \
  -Pdm01DmavFullRunSource=/abs/pfad/zur/2549.ch.so.agi.av.dm01_ch.itf
```

Der Lauf validiert Fingerprint, Transformation, `ilivalidator` und die normalisierte Summary gegen das eingecheckte `expected-summary.yaml`.

## Generische Bundle-Läufe (`run-bundle`)

Die manifestgesteuerte Komposition mehrerer Mapping-Module zu einem Lauf ist als generischer, dataset-agnostischer CLI-Befehl verfügbar:

```bash
ilitransformer run-bundle --manifest <bundle.yaml> [--source <file>] [--report-dir <dir>] [--output <file>] [--repo-root <dir>] [--no-validate]
```

Ein Bundle-Manifest beschreibt `name`, `source` (optional mit `sha256`), `output`, Mapping-Strategie, `modeldirs`, eine Liste `modules: [{ id, mapping }]` (Format per Endung erkannt) und optional `expectedSummary`. `run-bundle` lädt und validiert das Manifest, mergt die Module zu einer kombinierten `combined.generated.yaml`, führt sie über die Engine aus und vergleicht – falls `expectedSummary` gesetzt ist – die normalisierte Summary. Der DM01/DMAV-Full-Run (`runDm01DmavFullRun`) delegiert auf dieselbe Engine und ergänzt nur die DM01/DMAV-spezifischen Summary- und Topic-Inventar-Felder.

## Wichtige Testklassen

### `test`

- `CheckedInModelsCompileTest`: kompiliert die eingecheckten `.ili`-Modelle und schützt den Repo-Vertrag für Modelle unter `src/test/data/`.
- `CheckedInTransfersValidateTest`: validiert kleine kuratierte ITF-/XTF-Fixtures, Topic-Fixtures und Golden-Files unter `src/test/resources/`.
- `MappingLoaderTest`: lädt produktive Profile aus `profiles/` sowie gezielte Test-Mappings und prüft Compiler-/Loader-Verträge.
- `BagTransformationTest`, `NestedBagTransformationTest`, `ReferenceResolutionServiceTest`: Kerntests für BAG-Expansion, verschachtelte Strukturen und Referenzauflösung.

### `integrationTest`

- `ValidateMappingTypedCliTest`: deckt CLI- und Compiler-Validierung für Mapping-Dateien ab.
- `Dm01ToDmavLfp3IntegrationTest` und `DmavToDm01Lfp3IntegrationTest`: synthetische End-to-End-Integration für LFP3.
- `Dm01ToDmavBbIntegrationTest` und `DmavToDm01BbIntegrationTest`: synthetische End-to-End-Integration für BB inklusive verschachtelter BAG-Pfade.
- `ScalarMappingIntegrationTest` und `AssociationXtfIntegrationTest`: gezielte Verträge für Skalare, OIDs und Referenzen.

### `realDataTest`

- `Lfp3MinimalFixtureRoundtripTest`: kuratierter LFP3-Minimal-Roundtrip mit produktiven Profilen.
- `RealDm01ToDmavLfp3EndToEndTest`, `RealDmavToDm01Lfp3EndToEndTest` und `Lfp3RealExtractRoundtripTest`: produktive LFP3-Gates auf checked-in `real-extract`-Fixtures.
- `BbMinimalFixtureRoundtripTest`: kuratierter BB-Minimal-Roundtrip mit produktiven Profilen.
- `BbFullDatasetForwardGateTest`, `BbFullDatasetRoundtripSmokeTest` und `BbItfAreaReadDiagnosticTest`: produktive BB-Full-Dataset-Forward-/Smoke-/Diagnose-Gates.
- `ExtractedDm01FixtureValidationTest`, `ExtractedDmavFixtureValidationTest`, `ExtractedBbDm01FixtureValidationTest` und `ExtractedBbDmavFixtureValidationTest`: extrahieren kleine `real-extract`-Fixtures aus den Volldatensätzen und validieren sie.
- `FullDatasetInventoryTest` und `RealDatasetGeometrySmokeTest`: Inventar- und Geometrie-Smoke-Checks auf den vollständigen Datensätzen.

## Fixture-Updates

- Normale Läufe von `realDataTest` validieren extrahierte Fixtures nur im Temp-Verzeichnis und lassen eingecheckte `real-extract`-Dateien unverändert.
- Ein bewusstes Update der eingecheckten `real-extract`-Fixtures erfolgt nur mit `-PupdateFixtures=true`.

```bash
./gradlew realDataTest -PupdateFixtures=true --tests "guru.interlis.transformer.ExtractedDm01FixtureValidationTest"
./gradlew realDataTest -PupdateFixtures=true --tests "guru.interlis.transformer.ExtractedDmavFixtureValidationTest"
./gradlew realDataTest -PupdateFixtures=true --tests "guru.interlis.transformer.ExtractedBbDm01FixtureValidationTest"
./gradlew realDataTest -PupdateFixtures=true --tests "guru.interlis.transformer.ExtractedBbDmavFixtureValidationTest"
```

## Hinweise

- Produktive DM01/DMAV-Profile werden nicht mehr unter `src/test/resources/mappings/` gespiegelt.
- Phasenbezeichnungen bleiben in historischer Doku und in der `FeatureMatrix`, nicht in aktiven Ressourcenpfaden.
