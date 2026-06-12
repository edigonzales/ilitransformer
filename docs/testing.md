# Testing

## Testsuiten

| Suite | Zweck | Code | Zugehörige Dateien |
|---|---|---|---|
| `test` | Unit-Tests plus schnelle Repo-Vertrags-/Artefakt-Checks | `src/test/java/` | `src/test/data/`, `src/test/resources/` |
| `integrationTest` | synthetische End-to-End-, CLI- und Validator-Integration | `src/integrationTest/java/` | primär kleine Testmodelle und kuratierte Fixtures |
| `realDataTest` | langsame Profil-, Fixture- und Echtdaten-Regression | `src/realDataTest/java/` | `profiles/`, `src/test/data/DMAV_Version_1_1/`, `src/test/resources/real-dm01-dmav/` |

## Gradle-Integration

```bash
./gradlew test
./gradlew integrationTest
./gradlew realDataTest
./gradlew check
```

- `check` hängt an `integrationTest`, aber bewusst nicht an `realDataTest`.
- `realDataTest` ist für langsame Echtdaten- und Profil-Regression separat ausführbar.

## Ressourcenablage

| Pfad | Rolle |
|---|---|
| `profiles/` | autoritative, versionierte DM01/DMAV-Profile |
| `src/test/data/models/` | kleine lokale `.ili`-Modelle für Unit- und Integrationstests |
| `src/test/data/av/models/` | eingecheckte AV-Modelle für DM01/DMAV-Tests |
| `src/test/data/DMAV_Version_1_1/` | vollständige reale DM01- und DMAV-Datensätze |
| `src/test/resources/mappings/` | Test-Mappings für synthetische Modelle und CLI-Tests |
| `src/test/resources/transfers/` | kleine kuratierte Transfer-Fixtures |
| `src/test/resources/real-dm01-dmav/` | aus realen Datensätzen extrahierte, validierte Minimal-Fixtures |
| `src/test/resources/dm01-dmav/` | Snapshots für Report-Tests |

## Was `realDataTest` abdeckt

`realDataTest` ist die Suite für alles, was nicht mehr als schneller synthetischer Test gelten soll:

- Lesen und Inventarisieren vollständiger Echtdatensätze
- End-to-End-Läufe gegen produktive Profile unter `profiles/`
- semantische Roundtrips mit realen Fixtures
- Extraktion und Validierung kleiner Fixtures aus den vollständigen Datensätzen

Die Suite ist absichtlich separat, damit `check` lokal und in CI schnell bleibt.

## Wichtige Testklassen

### `test`

- `CheckedInModelsCompileTest`: kompiliert die eingecheckten `.ili`-Modelle und schützt den Repo-Vertrag für Modelle unter `src/test/data/`.
- `CheckedInTransfersValidateTest`: validiert kleine kuratierte ITF-/XTF-Fixtures und Golden-Files unter `src/test/resources/`.
- `MappingLoaderTest`: lädt produktive Profile aus `profiles/` sowie gezielte Test-Mappings und prüft Compiler-/Loader-Verträge.
- `BagTransformationTest`, `NestedBagTransformationTest`, `ReferenceResolutionServiceTest`: Kerntests für BAG-Expansion, verschachtelte Strukturen und Referenzauflösung.

### `integrationTest`

- `ValidateMappingTypedCliTest`: deckt CLI- und Compiler-Validierung für Mapping-Dateien ab.
- `Dm01ToDmavLfp3IntegrationTest` und `DmavToDm01Lfp3IntegrationTest`: synthetische End-to-End-Integration für LFP3.
- `Dm01ToDmavBbIntegrationTest` und `DmavToDm01BbIntegrationTest`: synthetische End-to-End-Integration für BB inklusive verschachtelter BAG-Pfade.
- `ScalarMappingIntegrationTest` und `AssociationXtfIntegrationTest`: gezielte Verträge für Skalare, OIDs und Referenzen.

### `realDataTest`

- `RealDm01ToDmavLfp3EndToEndTest` und `RealLfp3SemanticRoundtripTest`: produktiver LFP3-Gate auf echten Fixtures/Echtdaten.
- `RealBbSemanticRoundtripTest`, `RealBbReverseSemanticRoundtripTest` und `ItfAreaReadDiagnosticTest`: produktiver BB-Forward-/Reverse-/Lesegate.
- `ExtractedDm01FixtureValidationTest` und `ExtractedDmavFixtureValidationTest`: extrahieren kleine LFP3-Fixtures aus den Volldatensätzen und validieren sie.
- `FullDatasetInventoryTest` und `RealDatasetGeometrySmokeTest`: Inventar- und Geometrie-Smoke-Checks auf den vollständigen Datensätzen.

## Fixture-Updates

- Normale Läufe von `realDataTest` validieren extrahierte Fixtures nur im Temp-Verzeichnis und lassen eingecheckte Dateien unverändert.
- Ein bewusstes Update der eingecheckten LFP3-Fixtures erfolgt nur mit `-PupdateFixtures=true`.

```bash
./gradlew realDataTest -PupdateFixtures=true --tests "guru.interlis.transformer.ExtractedDm01FixtureValidationTest"
./gradlew realDataTest -PupdateFixtures=true --tests "guru.interlis.transformer.ExtractedDmavFixtureValidationTest"
```

## Hinweise

- Produktive DM01/DMAV-Profile werden nicht mehr unter `src/test/resources/mappings/` gespiegelt.
- Phasenbezeichnungen bleiben in historischer Doku und in der `FeatureMatrix`, nicht in aktiven Ressourcenpfaden.
