# ilitransformer

Java/Gradle-Werkzeug zur modellbewussten Transformation von INTERLIS-Transferdaten. Der aktuelle Referenz-Use-Case ist DM01 ↔ DMAV, die Engine bleibt aber generisch und darf keine DM01/DMAV-Sonderlogik in die generischen Laufzeitpfade ziehen.

## Schnellstart

### Per Release-Download (ohne Gradle)

```bash
unzip ilitransformer-0.1.0.zip
cd ilitransformer-0.1.0/
./bin/ilitransformer --help
cd examples/minimal/
../../bin/ilitransformer transform -m mapping.yaml
```

### Per Build aus Source

```bash
./gradlew installDist
./build/install/ilitransformer/bin/ilitransformer --help
./build/install/ilitransformer/bin/ilitransformer transform -m examples/minimal/mapping.yaml
```

## Wichtige Verzeichnisse

- `profiles/` enthält die autoritativen, versionierten DM01/DMAV-Profile auf Root-Ebene.
- `src/test/data/` enthält Modelle, offizielle AV-Artefakte und vollständige Echtdatensätze.
- `src/test/resources/fixtures/dm01-dmav/` enthält kanonische `*-minimal`- und `*-real-extract`-Fixtures pro Topic.
- `src/test/resources/` enthält kleine kuratierte Test-Artefakte, Test-Mappings und Snapshots.
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
./gradlew distZip distTar
```

DM01/DMAV-spezifische Hilfstasks:

```bash
./gradlew importDmavCorrelation
./gradlew produceDm01BbItf
./gradlew validateTransfer -Ptransfer=build/out/dmav-lfp3.xtf -Pmodel=DMAV_FixpunkteAVKategorie3_V1_1
```

## Produktive Profile

Produktive DM01/DMAV-Profile liegen unter:

```text
profiles/dm01-to-dmav/1.1/
profiles/dmav-to-dm01/1.1/
```

Tests, die produktive Profile prüfen, laden diese Dateien direkt und materialisieren nur Ein-/Ausgabepfade für die jeweilige Testumgebung.

## Eingabeformate

Die nativen INTERLIS-Transferformate `itf`, `xtf` und `xml` werden anhand der Dateiendung erkannt.
Zusätzliche Eingabeformate werden über `format:` deklariert und pro Input mit `options` konfiguriert.

- `csv` — bewusst flaches, nur lesbares Eingabeformat (eine Tabelle → eine Quellklasse). Optionen:
  `firstLineIsHeader`, `separator`, `delimiter`, `encoding`.
- `gpkg` / `geopackage` — tabellarisches oder räumliches, nur lesbares GeoPackage-Eingabeformat
  (eine Tabelle → eine Quellklasse). Optionen: `table` (Pflicht), `fetchSize`. Punktgeometrie (COORD)
  wird als Simple Feature gelesen.
- `jdbc` — generisches, nur lesbares tabellarisches oder räumliches Eingabeformat ohne Pfad. Statt
  `path` ein `connection`-Block und ein oder mehrere `queries` (eine Query → eine Quellklasse).
  Punktgeometrie über WKT/WKB-Spalten. Passwörter werden nie geloggt; auch als `.ilimap` unterstützt.

Shapefile (`shp`) ist als Eingabeformat implementiert (Point, PolyLine, Polygon 2D). Siehe Beispiele
`examples/shp-to-xtf/`, `examples/shp-polyline-to-xtf/`, `examples/shp-polygon-to-xtf/`. Schreibzugriff
ist noch nicht unterstützt.

Die vollständige Format-Matrix mit Einschränkungen ist in [docs/formats.md](docs/formats.md) dokumentiert.

### Beispiele

Ein vollständiges CSV-Beispiel (Source-ILI, Target-ILI, CSV, YAML- und `.ilimap`-Mapping) liegt unter
`examples/csv-to-xtf/`:

```bash
ilitransformer transform -m examples/csv-to-xtf/mapping.yaml --modeldir examples/csv-to-xtf/models --validate --report build/reports/csv
```

Tabellarisches GeoPackage unter `examples/gpkg-to-xtf/`:

```bash
ilitransformer transform -m examples/gpkg-to-xtf/mapping.yaml --modeldir examples/gpkg-to-xtf/models --validate --report build/reports/gpkg
```

Räumliches GeoPackage mit Punktgeometrie unter `examples/gpkg-spatial-to-xtf/`:

```bash
ilitransformer transform -m examples/gpkg-spatial-to-xtf/mapping.yaml --modeldir examples/gpkg-spatial-to-xtf/models --validate --report build/reports/gpkg-spatial
```

JDBC (SQLite) unter `examples/jdbc-to-xtf/`. Für Tests gegen eine reale PostgreSQL/PostGIS-Datenbank
liegt ein Compose-Stack unter `dev/stack/compose.yml`; der zugehörige Opt-in-Test läuft mit
`./gradlew postgisTest` (überspringt sich selbst, wenn keine DB erreichbar ist):

```bash
ilitransformer transform -m examples/jdbc-to-xtf/mapping.yaml --modeldir examples/jdbc-to-xtf/models --validate --report build/reports/jdbc
```

Räumliches JDBC (WKT Point) unter `examples/jdbc-spatial-to-xtf/`:

```bash
ilitransformer transform -m examples/jdbc-spatial-to-xtf/mapping.yaml --modeldir examples/jdbc-spatial-to-xtf/models --validate --report build/reports/jdbc-spatial
```

Shapefile (Point, PolyLine, Polygon) unter `examples/shp-to-xtf/`:

```bash
ilitransformer transform -m examples/shp-to-xtf/mapping.yaml --modeldir examples/shp-to-xtf/models --validate --report build/reports/shp-point
```

## Aktive Dokumentation

- `docs/cli.md`
- `docs/mapping-dsl.md`
- `docs/ilimap-v2.md`
- `docs/formats.md`
- `docs/expressions.md`
- `docs/feature-matrix.md`
- `docs/testing.md`
- `docs/dm01-dmav/README.md`
- `docs/dm01-dmav/status-matrix.md`
