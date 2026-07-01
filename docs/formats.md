# Format Support

Dieses Dokument beschreibt die unterstuetzten Ein- und Ausgabeformate von ilitransformer,
die Formatprovider-Architektur und die Grenzen jedes Formats.

## Architektur

ilitransformer liest und schreibt Daten ueber eine Formatprovider-Architektur (`IoxFormatProvider`).
Jeder Provider kapselt die spezifische I/O-Logik eines Formats. Die `IoxFormatRegistry` waehlt den
passenden Provider anhand der im Mapping deklarierten `format`-Angabe oder der Dateiendung aus.

Die Transformationsengine arbeitet ausschliesslich mit `IoxReader`, `IoxWriter`, `IoxEvent` und
`IomObject`. Sie enthaelt keinerlei formatspezifische Logik.

Formatoptionen werden in der Mapping-Datei im `input`- bzw. `output`-Block deklariert und an den
jeweiligen Provider durchgereicht. Sie sind nicht als globale CLI-Flags verfuegbar.

## Format-Matrix

| Format | Input | Output | Geometrie | Strukturen | Referenzen | Bemerkungen |
|---|---|---|---|---|---|---|
| XTF | ja | ja | ja | ja | ja | INTERLIS 2 nativ |
| ITF | ja | ja | eingeschraenkt | modellabhaengig | modellabhaengig | INTERLIS 1 |
| XML | ja | ja | eingeschraenkt | modellabhaengig | modellabhaengig | INTERLIS 2 XML |
| CSV | ja | nein | nein | nein | nein | Nur flache Tabellen |
| GPKG | ja | nein | ja (Simple Features) | nein | nein | Tabellarisch oder Simple Features |
| JDBC | ja | nein | ja (WKT/WKB POINT) | nein | nein | Eine Query je Quellklasse, tabellarisch |
| SHP | ja | ja | ja (Point/MultiPoint/Polyline/Polygon 2D) | nein | nein | Eine Klasse / ein Geometrietyp pro Shapefile |

### Legende

- **ja**: voll unterstuetzt
- **nein**: nicht unterstuetzt
- **eingeschraenkt / modellabhaengig**: Das Format kann Geometrie/Strukturen/Referenzen
  grundsaetzlich abbilden, aber die tatsaechliche Nutzbarkeit haengt vom konkreten
  INTERLIS-Modell ab (z.B. INTERLIS 1 hat andere Strukturkonzepte als INTERLIS 2).
- **reserviert**: Die Format-ID ist im `FormatIdResolver` vorbereitet, es existiert
  aber noch kein Provider und keine Integrationstests.

## INTERLIS-Formate (XTF, ITF, XML)

Die nativen INTERLIS-Transferformate werden anhand der Dateiendung automatisch erkannt:

- `.xtf` → `xtf`
- `.xml` → `xml`
- `.itf` → `itf`

Es werden keine Formatoptionen ausgewertet. Eine explizite `format:`-Angabe ist nicht noetig,
kann aber zur Dokumentation gesetzt werden.

```yaml
inputs:
  - id: source
    path: "input/data.xtf"
    model: "SourceModel"
```

```ilimap
input source {
  path "input/data.xtf";
  model "SourceModel";
}
```

## CSV

CSV ist ein bewusst flaches, **nur lesbares** Eingabeformat. Eine Tabelle, deren Spalten auf die
Attribute genau einer Klasse des Quellmodells abgebildet werden. Strukturen, Referenzen und
Geometrie kann CSV nicht ausdruecken. Der Output bleibt ein normales INTERLIS-Modell.

### Optionen

| Option | Default | Bedeutung |
|---|---|---|
| `firstLineIsHeader` | `true` | Erste Zeile enthaelt die Spaltennamen |
| `separator` | `,` | Trennzeichen (ein Zeichen) |
| `delimiter` | `"` | Anfuehrungszeichen (ein Zeichen) |
| `encoding` | `UTF-8` | Zeichensatz der Datei |

### Mapping (YAML)

```yaml
inputs:
  - id: source
    path: "input/municipalities.csv"
    model: "DemoCsvSource"
    format: csv
    options:
      firstLineIsHeader: "true"
      separator: ";"
      encoding: UTF-8
```

### Mapping (.ilimap)

```ilimap
input source {
  path "input/municipalities.csv";
  model "DemoCsvSource";
  format csv;
  option firstLineIsHeader true;
  option separator ";";
  option encoding "UTF-8";
}
```

### Einschraenkungen

- Keine Ausgabe in CSV (nur lesbar).
- Keine Strukturen, Referenzen, Assoziationen.
- Keine Geometrie.
- Eine CSV-Datei = eine Quellklasse.

### Beispiel

```bash
ilitransformer transform -m examples/csv-to-xtf/mapping.yaml --modeldir examples/csv-to-xtf/models --validate --report build/reports/csv
```

## GeoPackage

GeoPackage (`gpkg` / `geopackage`) ist ein **nur lesbares** Eingabeformat. Unterstuetzt werden:

- **Tabellarische Daten** (`data_type: attributes`): Spalten werden auf Attribute einer Quellklasse
  abgebildet. Keine Geometrie noetig.
- **Simple Features mit Punktgeometrie** (`data_type: features`): Eine Geometriespalte mit
  `POINT`-Geometrie wird als INTERLIS `COORD` gelesen.

### Optionen

| Option | Default | Bedeutung |
|---|---|---|
| `table` | *(Pflicht)* | Tabellenname in der GeoPackage-Datei |
| `fetchSize` | `10000` | Zeilen pro Datenbank-Roundtrip |

### Mapping (YAML)

```yaml
inputs:
  - id: source
    path: "input/municipalities.gpkg"
    model: "DemoGpkgSource"
    format: gpkg
    options:
      table: "municipalities"
      fetchSize: "10000"
```

### Mapping (.ilimap)

```ilimap
input source {
  path "input/municipalities.gpkg";
  model "DemoGpkgSource";
  format gpkg;
  option table "municipalities";
  option fetchSize 10000;
}
```

### Einschraenkungen

- Keine Ausgabe in GeoPackage (nur lesbar).
- Linien- und Flaechengeometrien (POLYLINE, SURFACE) werden vom `GeoPackageReader`
  unterstuetzt, sind aber nicht durch automatisierte Tests abgedeckt.
- Strukturen, Referenzen und Assoziationen koennen nicht direkt aus einem GeoPackage
  ausgedrueckt werden; sie muessen durch Mapping-Regeln konstruiert werden.
- Die GeoPackage-Datei muss eine `gpkg_contents`-Metadatentabelle enthalten.

### Beispiele

```bash
# Tabellarisch
ilitransformer transform -m examples/gpkg-to-xtf/mapping.yaml --modeldir examples/gpkg-to-xtf/models --validate --report build/reports/gpkg

# Raeumlich (Punktgeometrie)
ilitransformer transform -m examples/gpkg-spatial-to-xtf/mapping.yaml --modeldir examples/gpkg-spatial-to-xtf/models --validate --report build/reports/gpkg-spatial
```

## JDBC

JDBC (`jdbc`) ist ein generisches, **nur lesbares** tabellarisches Eingabeformat ohne Dateipfad.
Statt `path` deklariert der Input einen `connection`-Block und einen oder mehrere `queries`. Jede
Query entspricht genau einer flachen Quellklasse und wird zu einem Korb (`basket`) im Transferstrom.

### Tabellarisch

Jede Query liefert flache Zeilen, deren Spalten auf Attribute der Quellklasse abgebildet werden.

### Raeumlich (WKT/WKB)

Ueber eine `geometry`-Deklaration kann eine Spalte als Geometrie interpretiert werden:

- `encoding: wkt` erwartet WKT-Text (z.B. `POINT (2607600 1228500)`)
- `encoding: wkb` erwartet WKB-Binaerdaten (z.B. via `ST_AsBinary()`)
- `type: coord` bildet die Geometrie auf einen INTERLIS `COORD` ab

### Connection

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `connection.url` | ja | JDBC-URL |
| `connection.driver` | nein | Treiberklasse (sonst per SPI geladen) |
| `connection.user` / `password` | nein | Anmeldedaten inline |
| `connection.userEnv` / `passwordEnv` | nein | Anmeldedaten aus Umgebungsvariablen (bevorzugt) |
| `connection.properties` | nein | Zusaetzliche Treiber-Properties |

### Query

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `queries[].class` | ja | Skopierter Quellklassenname |
| `queries[].sql` | ja | SQL-Abfrage |
| `queries[].topic` | nein | Topic-Name des Korbs |
| `queries[].basketId` | nein | Korb-ID (sonst `b<n>`) |
| `queries[].oidColumn` | nein | Spalte als OID (sonst deterministisch erzeugt) |
| `queries[].columns` | nein | Abbildung DB-Spalte → Attributname |

### Geometry (pro Query)

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `geometry[].attribute` | ja | Zielattributname im Quellmodell |
| `geometry[].column` | ja | Spaltenname im Result-Set |
| `geometry[].encoding` | ja | `wkt` oder `wkb` |
| `geometry[].type` | nein | `coord` (default) |
| `geometry[].srid` | nein | SRID (nur zur Dokumentation) |

### Mapping (YAML, tabellarisch)

```yaml
inputs:
  - id: db
    model: "DemoJdbcSource"
    format: jdbc
    connection:
      driver: org.sqlite.JDBC
      url: "jdbc:sqlite:build/demo.sqlite"
    queries:
      - id: municipalities
        topic: "DemoJdbcSource.Data"
        class: "DemoJdbcSource.Data.Municipality"
        basketId: b1
        oidColumn: id
        sql: |
          select id, bfsnr, name, population
          from municipalities
```

### Mapping (YAML, raeumlich)

```yaml
inputs:
  - id: db
    model: "DemoJdbcSpatialSource"
    format: jdbc
    connection:
      driver: org.sqlite.JDBC
      url: "jdbc:sqlite:build/demo.sqlite"
    queries:
      - id: stations
        topic: "DemoJdbcSpatialSource.Data"
        class: "DemoJdbcSpatialSource.Data.Station"
        basketId: b1
        oidColumn: id
        sql: |
          select id, identifier, name, geom_wkt
          from stations
        geometry:
          - attribute: geom
            column: geom_wkt
            encoding: wkt
            type: coord
```

### Mapping (.ilimap)

```ilimap
input db {
  model "DemoJdbcSource";
  format jdbc;
  connection {
    driver "org.sqlite.JDBC";
    url "jdbc:sqlite:build/demo.sqlite";
  }
  query municipalities {
    topic "DemoJdbcSource.Data";
    class "DemoJdbcSource.Data.Municipality";
    basketId "b1";
    oidColumn "id";
    sql "select id, bfsnr, name, population from municipalities";
  }
}
```

### Einschraenkungen

- Keine Ausgabe in JDBC (nur lesbar).
- Nur `POINT`-Geometrie (COORD) wird aus WKT/WKB gelesen; Linien- und Flaechengeometrien
  werden nicht konvertiert.
- Treiber ausser dem mitgelieferten SQLite-Treiber muessen auf dem Klassenpfad liegen.
- Strukturen, Referenzen und Assoziationen koennen nicht direkt aus einer Query ausgedrueckt
  werden; sie muessen durch Mapping-Regeln konstruiert werden.
- Passwoerter werden nie in Logs, Diagnostics oder Reports geschrieben.

### Beispiele

```bash
# Tabellarisch
ilitransformer transform -m examples/jdbc-to-xtf/mapping.yaml --modeldir examples/jdbc-to-xtf/models --validate --report build/reports/jdbc

# Raeumlich (WKT Point)
ilitransformer transform -m examples/jdbc-spatial-to-xtf/mapping.yaml --modeldir examples/jdbc-spatial-to-xtf/models --validate --report build/reports/jdbc-spatial
```

### Datenbank-Empfehlungen

#### SQLite (immer verfuegbar)

Mitgelieferter Treiber, keine raeumliche Extension noetig:

```sql
-- Tabellarisch
SELECT id, bfsnr, name, population FROM municipalities;

-- Raeumlich (WKT)
SELECT id, identifier, name, geom_wkt FROM stations;
```

#### PostGIS

```sql
-- Raeumlich (WKT)
SELECT id, identifier, name, ST_AsText(geom) AS geom_wkt FROM stations;

-- Raeumlich (WKB)
SELECT id, identifier, name, ST_AsBinary(geom) AS geom_wkb FROM stations;
```

#### DuckDB mit Spatial Extension

```sql
SELECT id, identifier, name, ST_AsText(geom) AS geom_wkt FROM stations;
```

## Shapefile

Shapefile (`shp` / `shapefile`) ist ein Eingabe- **und Ausgabeformat** fuer Simple Features.
Unterstuetzt werden 2D Point-, MultiPoint-, PolyLine- und Polygon-Shapefiles; beim Schreiben kann
`shapeType=null` explizit NullShape-Shapefiles fuer DBF-only Tabellen erzeugen.

Beim Lesen entspricht jedes Shapefile genau einer flachen Quellklasse. Beim Schreiben wird genau
eine IOX-Klasse mit genau einem Geometrietyp in ein Shapefile-Dataset serialisiert. Strukturen,
Referenzen und Assoziationen koennen nicht direkt in einem Shapefile ausgedrueckt werden; sie
muessen durch Mapping-Regeln konstruiert werden.

### Optionen

| Option | Default | Bedeutung |
|---|---|---|
| `class` | *(Pflicht)* | Voll qualifizierte Quellklasse, z.B. `Model.Topic.Class` |
| `topic` | aus `class` | Basket-Topic |
| `basketId` | `b1` | Basket-ID |
| `oidField` | `shp.<recordNumber>` | DBF-Feld als Objekt-OID |
| `geometryAttribute` | inferiert | Quellattribut fuer Geometrie |
| `geometryType` | aus Shape-Typ | `coord`, `polyline`, `surface` |
| `dbfEncoding` | `ISO-8859-1` | Charset fuer DBF-Zeichenfelder |
| `column.<DBF>` | DBF-Feldname | DBF-Feldname → INTERLIS-Attribut |
| `deletedRecordPolicy` | `error` | `error` oder `skip` fuer geloeschte DBF-Records |
| `requireShx` | `false` | Erzwingt `.shx`-Sidecar-Datei |

### Mapping (YAML)

```yaml
inputs:
  - id: parcels
    path: "input/parcels.shp"
    model: "DemoShpSource"
    format: shp
    options:
      class: "DemoShpSource.Data.Parcel"
      topic: "DemoShpSource.Data"
      oidField: "TID"
      geometryAttribute: "geometry"
      geometryType: "surface"
      dbfEncoding: "UTF-8"
      column.BFS_NR: "bfsnr"
      column.NAME: "name"
```

### Einschraenkungen

- Nur 2D Point (1), MultiPoint (8), PolyLine (3) und Polygon (5). Z/M/MultiPatch werden mit Fehler abgelehnt.
- Eine Shapefile-Datei = eine Quellklasse.
- `.prj` wird nur als Metadatum erkannt, keine Reprojektion.
- MULTISURFACE/MULTIPOLYLINE-Attribute im Zielmodell erfordern Mapping-Regeln zur Strukturierung.

### Ausgabe (Writer)

Der Writer schreibt genau **eine Klasse** mit genau **einem Geometrietyp** in ein Shapefile-Dataset
(`.shp`, `.shx`, `.dbf`, `.cpg`, optional `.prj`). Mehrdeutige Situationen werden strikt abgelehnt:

- Objekte einer anderen als der konfigurierten/ersten Klasse werden mit Fehler abgewiesen.
- Eine Geometrie, die nicht zum ermittelten Shape-Typ passt, wird mit Fehler abgewiesen.
- Mehrere Baskets werden abgelehnt, ausser `failOnMultipleBaskets=false`.

Das DBF-Schema wird **modellbewusst** aus der INTERLIS-Zielklasse abgeleitet (Feldnamen, -typen und
-laengen). Kann die Klasse nicht im Modell aufgeloest werden, dient das erste Objekt als Fallback
(alle Skalarattribute als Character-Felder; `geometryAttribute` und `shapeType` sind dann Pflicht).
Nicht-skalare Attribute (Strukturen, Referenzen, weitere Geometrieattribute) werden nicht
geschrieben, sondern als Warnung gemeldet.

Mit `shapeType=null` schreibt der Writer explizit ein NullShape-Shapefile fuer Klassen ohne
Geometrie. Klassen mit Geometrieattributen oder die gleichzeitige Angabe von `geometryAttribute`
werden dabei abgelehnt, damit Geometrien nicht still verloren gehen.

| Option | Default | Bedeutung |
|---|---|---|
| `class` | erste geschriebene Objektklasse | Welche IOX-Klasse geschrieben wird |
| `geometryAttribute` | inferiert (genau ein Geometrieattribut) | Attribut, das als `.shp`-Geometrie geschrieben wird |
| `shapeType` | aus Geometrieattribut abgeleitet | `null`, `point`, `multipoint`, `polyline`, `polygon` |
| `dbfEncoding` | `UTF-8` | DBF-Encoding und `.cpg` |
| `fieldNameStrategy` | `strict` | `strict`, `truncate` oder `stable` (DBF-Feldnamen max. 10 Zeichen) |
| `overflowPolicy` | `strict` | `strict` oder `truncate` fuer zu lange DBF-Character-Werte |
| `writeSidecarMapping` | `true` | Schreibt `*.iliattr.json` bei gekuerzten/umbenannten Feldnamen |
| `prj` | *(leer)* | Statischer WKT-Inhalt fuer die `.prj`-Datei |
| `failOnMultipleBaskets` | `true` | Mehrere Baskets ablehnen |

DBF-Feldnamen sind auf 10 Zeichen begrenzt. `strict` lehnt zu lange oder kollidierende Namen ab;
`truncate` kuerzt auf 10 Zeichen (Kollision = Fehler); `stable` kuerzt und nummeriert deterministisch
(`ATTRIBUT`, `ATTRIBU_1`) und schreibt ein `*.iliattr.json`-Sidecar mit der Namenszuordnung.

DBF-Wertueberlaeufe bleiben standardmaessig strikt. Mit `overflowPolicy=truncate` werden nur
Character-Werte byte-sicher im Ziel-Encoding gekuerzt; numerische, logische und Datumsfelder bleiben
strict.

#### Mapping (YAML)

```yaml
outputs:
  - id: out
    path: "output/parcels.shp"
    model: "DemoShpTarget"
    format: shp
    options:
      class: "DemoShpTarget.Data.Parcel"
      geometryAttribute: "geometry"
      shapeType: "polygon"
      dbfEncoding: "UTF-8"
      fieldNameStrategy: "stable"
```

### Beispiele

```bash
# Point
ilitransformer transform -m examples/shp-to-xtf/mapping.yaml --modeldir examples/shp-to-xtf/models --validate --report build/reports/shp-point

# PolyLine
ilitransformer transform -m examples/shp-polyline-to-xtf/mapping.yaml --modeldir examples/shp-polyline-to-xtf/models --validate --report build/reports/shp-polyline

# Polygon
ilitransformer transform -m examples/shp-polygon-to-xtf/mapping.yaml --modeldir examples/shp-polygon-to-xtf/models --validate --report build/reports/shp-polygon
```

## Validierung

Alle Ausgaben koennen mit `--validate` gegen ihr INTERLIS-Zielmodell validiert werden. Der
Validator (`ilivalidator`) wird im selben Prozess ausgefuehrt und das Ergebnis im Report
festgehalten:

```bash
ilitransformer transform -m mapping.yaml --modeldir models --validate --report build/reports/demo
```

Exit-Code `0` bedeutet: Transformation erfolgreich und Validierung ohne Fehler.

## Fehlerbehandlung und Diagnostics

Unbekannte Formate oder fehlerhafte Optionen werden mit praezisen Diagnostics abgelehnt:

```
[ERROR] IO_FORMAT_UNKNOWN: Unknown input format 'xyz' for input 'source'.
Available input formats: itf, xtf, xml, csv, gpkg, jdbc, shp.

[ERROR] IO_OPTION_INVALID: Invalid option 'separator' for input 'source':
expected a single character, got '::'.
Suggestion: Use separator: ';' or separator: ','

[ERROR] IO_FORMAT_UNSUPPORTED_DIRECTION: Format 'csv' does not support output.
Suggestion: Use an INTERLIS format (xtf, itf) for output.
```

## Troubleshooting

### CSV wird nicht gelesen

- Ist `firstLineIsHeader` korrekt gesetzt? Ohne Header werden Spalten nach Position gematcht.
- Stimmen die Spaltennamen exakt mit den Attributnamen im Source-ILI ueberein?
- Enthaelt die Datei die erwartete Kodierung (default: `UTF-8`)?

### GeoPackage-Reader meldet Fehler

- Enthaelt die Datei eine `gpkg_contents`-Tabelle mit dem korrekten `table_name`?
- Ist `table` in den Optionen gesetzt?
- Bei raeumlichen Daten: Enthaelt die Datei `gpkg_geometry_columns` und `gpkg_spatial_ref_sys`?

### JDBC-Verbindung schlaegt fehl

- Ist der Treiber auf dem Klassenpfad? SQLite ist mitgeliefert; fuer PostGIS muss
  der PostgreSQL-Treiber separat bereitgestellt werden.
- Relative `jdbc:sqlite:`-Pfade werden relativ zum Mapping-Verzeichnis aufgeloest.
- Bei `userEnv`/`passwordEnv`: Sind die Umgebungsvariablen gesetzt?

### Validierung schlaegt fehl

- Sind alle `modeldir`-Pfade gesetzt, die das Zielmodell benoetigt?
- Enthaelt das Ausgabe-XTF alle Pflichtattribute der Zielklasse?
- Fehlen Enum-Mappings fuer referenzierte Werte?

## Weitere Dokumentation

- [CLI Reference](cli.md)
- [Mapping DSL (YAML)](mapping-dsl.md)
- [Mapping DSL (.ilimap)](ilimap-v2.md)
- [Expression Reference](expressions.md)
