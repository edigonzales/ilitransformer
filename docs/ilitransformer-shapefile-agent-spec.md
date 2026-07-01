# Spec: Shapefile-Unterstützung für ilitransformer

**Zielgruppe:** LLM Coding Agent, der im Repository `https://github.com/edigonzales/ilinexus` arbeitet.  
**Stand der Analyse:** 2026-06-25.  
**Ziel:** Shapefile (`shp` / `shapefile`) als schlankes, GeoTools-freies Format in die bestehende Formatprovider-Architektur von `ilitransformer` integrieren. Reader zuerst, Writer danach. Jede Phase muss kompilieren, getestet sein und ein brauchbares Artefakt hinterlassen.

---

## 1. Ausgangslage und harte Leitplanken

`ilitransformer` ist ein Java-21/Gradle-Werkzeug zur modellbewussten Transformation von INTERLIS-Transferdaten. Die generische Engine darf keine formatspezifische Logik enthalten. Sie sieht nur `IoxReader`, `IoxWriter`, `IoxEvent` und `IomObject`.

Das aktuelle Repository hat bereits eine Formatprovider-Architektur:

- `guru.interlis.transformer.io.IoxFormatProvider`
- `guru.interlis.transformer.io.IoxFormatRegistry`
- `guru.interlis.transformer.io.FormatIdResolver`
- `guru.interlis.transformer.io.FormatOptions`
- `guru.interlis.transformer.io.FormatOpenContext`

Bereits vorhandene Zusatzformate sind:

- `csv` als flacher Input über `CsvFormatProvider`
- `gpkg` / `geopackage` als Input über `WkfGeoPackageFormatProvider`
- `jdbc` als Input über `JdbcFormatProvider` und `JdbcIoxReader`

`shp` ist im `FormatIdResolver` bereits für `.shp` reserviert. In `docs/formats.md` und `README.md` ist Shapefile als reserviert beziehungsweise noch nicht implementiert dokumentiert. Diese Arbeit soll genau dort anschliessen und keine neue Parallelarchitektur einführen.

### 1.1 Nicht verhandelbare technische Entscheidungen

1. **Keine GeoTools-Dependency.**
   - `iox-wkf` bleibt verwendbar für GeoPackage, aber der Shapefile-Code aus `iox-wkf` darf nicht verwendet werden, weil er GeoTools voraussetzt.
   - `build.gradle` schliesst GeoTools aus der `iox-wkf`-Dependency bereits aus. Das ist beizubehalten.

2. **Kein neues Git-Repository.**
   - Die Implementierung erfolgt im bestehenden `ilitransformer`-Repo.
   - Später kann ein extrahierbares Package entstehen, aber nicht als erster Schritt.

3. **Kein neues Gradle-Multimodul.**
   - Das aktuelle Repo ist ein Single-Gradle-Projekt. Die Shapefile-Implementierung wird als Package ergänzt.
   - Kein `settings.gradle`-Umbau, keine Modulgrenzen, keine Publishing-Architektur.

4. **Core und IOX-Adapter trennen.**
   - Der Core liest/schreibt Shapefile-Dateien und kennt weder INTERLIS noch IOX.
   - Der Adapter (`ShapefileIoxReader` / `ShapefileIoxWriter`) übersetzt zwischen Core-Records und IOX-Events.

5. **Reader und Writer müssen streaming-basiert sein.**
   - Keine `List<Feature>`.
   - Keine Collection pro Feature.
   - Keine vollständige Datei im Heap.
   - Writer muss direkt in `.shp`, `.shx`, `.dbf` schreiben und Header am Ende patchen.

6. **JTS nur in der bereits vorhandenen Form verwenden.**
   - Im aktuellen Code wird `com.vividsolutions.jts.*` verwendet, z.B. in `JdbcGeometryConverter` zusammen mit `ch.interlis.iox_j.jts.Jts2iox`.
   - Verwende deshalb ebenfalls `com.vividsolutions.jts.*`.
   - Füge keine neue `org.locationtech.jts`-Dependency ein, solange kein zwingender Grund besteht.
   - Falls der Compiler eine explizite Dependency braucht, füge eine minimale Dependency hinzu, die mit `iox-ili`/`iox_j` kompatibel ist. Keine GeoTools-Transitives.

7. **Die generische Transformationsengine bleibt formatneutral.**
   - Keine Shapefile-Imports in `engine`, `mapping`, `expr`, `state`, `model`.
   - Shapefile-spezifische Logik gehört ausschliesslich nach `guru.interlis.transformer.io.shp` und Unterpackages.

8. **AGENTS.md beachten.**
   - Vor Codeänderungen müssen die in `AGENTS.md` genannten Dateien und Skills gelesen werden.
   - Keine opportunistischen Refactorings.
   - Keine öffentlichen API-/DSL-Semantikänderungen ohne explizite Dokumentation.
   - Tests nie behaupten, wenn sie nicht wirklich gelaufen sind.

---

## 2. Warum GeoTools nicht nachbauen, aber seine Low-Level-Strategie übernehmen

Die alte `iox-wkf`-Shapefile-Writer-Implementierung war langsam, weil sie pro Feature eine `ListFeatureCollection` erzeugte und via `SimpleFeatureStore.addFeatures(...)` schrieb. PR #32 in `claeis/iox-wkf` hat auf `FeatureWriter` umgestellt. Der Effekt war dramatisch: 10'000 Features wurden in ungefähr 2 Sekunden statt ungefähr 2 Minuten geschrieben.

Die eigene Implementierung muss daraus eine harte Architekturregel ableiten:

> Shapefile-Export darf niemals über eine High-Level-Collection-API, pro-Feature-Collections oder pro-Feature-Transaktionen laufen.

GeoTools intern macht beim schnellen Writer konzeptionell Folgendes:

- `.shp`, `.shx`, `.dbf` einmal öffnen.
- Platzhalter-Header schreiben.
- Pro Feature direkt Geometrie und DBF-Record schreiben.
- Offset und Content-Length parallel in `.shx` schreiben.
- Bounds, Record Count und Dateilängen während des Schreibens aktualisieren.
- Am Ende Header patchen.
- Temp-/Storage-Dateien erst am Ende ersetzen.

Diese Strategie wird übernommen. Nicht übernommen werden:

- `DataStore`
- `SimpleFeatureType`
- `FeatureSource`
- `FeatureStore`
- `FeatureCollection`
- `Transaction`
- GeoTools CRS-Handling
- GeoTools Spatial Indexes

---

## 3. Shapefile-Scope

### 3.1 MVP Reader

Der Reader soll zuerst flache Simple-Feature-Shapefiles lesen:

- `.shp` Pflicht
- `.dbf` Pflicht
- `.shx` optional, aber wenn vorhanden validierbar
- `.prj` optional, nur als Metadatum / Report-Hinweis, keine Transformation
- `.cpg` optional, zur DBF-Encoding-Ermittlung

Unterstützte Shape Types im MVP:

- `0` Null Shape
- `1` Point
- `3` PolyLine
- `5` Polygon

Nicht im MVP:

- `8` MultiPoint
- `11/13/15/18` Z-Typen
- `21/23/25/28` M-Typen
- `31` MultiPatch
- Spatial Indexes `.sbn/.sbx/.qix`
- Reprojektion
- mehrere Feature-Klassen pro Dataset

Z/M/MultiPatch müssen mit klarer Fehlermeldung abgelehnt werden. Sie dürfen nicht still falsch gelesen werden.

### 3.2 Reader-Zielmodell

Ein Shapefile entspricht genau einer flachen Quellklasse im Source-INTERLIS-Modell. Strukturen, Referenzen und Assoziationen werden nicht direkt aus Shapefile gelesen. Sie können später in den Mapping-Regeln konstruiert werden.

Das passt zur bestehenden Linie von CSV, GeoPackage und JDBC: Zusatzformate sind Input-Formate, die flache Source-Objekte erzeugen; der Output bleibt ein normales INTERLIS-Zielmodell oder später ein Shapefile-Output.

### 3.3 MVP Writer

Der Writer folgt nach dem Reader. Er schreibt genau eine IOX-Klasse in genau ein Shapefile-Dataset.

MVP Writer unterstützt:

- Point → Shape Type 1
- PolyLine → Shape Type 3
- Surface / Area-artige Polygonobjekte → Shape Type 5, soweit via `Jts2iox`/bestehende Geometrie-Helfer sauber konvertierbar
- Attribute → DBF-Felder
- `.shp`, `.shx`, `.dbf`
- optional `.cpg`
- optional `.prj` als statische WKT-Ausgabe, wenn per Option angegeben

Nicht im Writer-MVP:

- gemischte Geometrietypen
- mehrere Klassen
- mehrere Baskets
- Z/M/MultiPatch
- Update/Edit bestehender Shapefiles
- DBF Memo-Felder
- Spatial Indexes
- Reprojektion

---

## 4. User-facing Mapping-Syntax

Die bestehende Architektur unterstützt generische `options` als flache Key-Value-Map. Für Shapefile wird zuerst **keine neue DSL-Struktur** eingeführt. Alle Optionen kommen in `options`.

### 4.1 YAML Input-Beispiel

```yaml
version: 1
job:
  inputs:
    - id: parcels
      path: input/parcels.shp
      model: DemoShpSource
      format: shp
      options:
        class: DemoShpSource.Data.Parcel
        topic: DemoShpSource.Data
        basketId: b1
        oidField: TID
        geometryAttribute: geometry
        geometryType: surface
        dbfEncoding: UTF-8
        column.BFS_NR: bfsnr
        column.NAME: name
  outputs:
    - id: out
      path: output.xtf
      model: DemoTarget
      format: xtf
mapping:
  rules:
    - id: parcel-rule
      target:
        output: out
        class: DemoTarget.Data.Parcel
      sources:
        - alias: p
          input: parcels
          class: DemoShpSource.Data.Parcel
      assign:
        bfsnr: ${p.bfsnr}
        name: ${p.name}
        geometry: ${p.geometry}
```

### 4.2 `.ilimap` Input-Beispiel

```ilimap
input parcels {
  path "input/parcels.shp";
  model "DemoShpSource";
  format shp;
  option class "DemoShpSource.Data.Parcel";
  option topic "DemoShpSource.Data";
  option basketId "b1";
  option oidField "TID";
  option geometryAttribute "geometry";
  option geometryType "surface";
  option dbfEncoding "UTF-8";
  option column.BFS_NR "bfsnr";
  option column.NAME "name";
}
```

### 4.3 Input-Optionen

| Option | Pflicht | Default | Bedeutung |
|---|---:|---|---|
| `class` | empfohlen | inferierbar, wenn Modell genau eine passende konkrete Klasse enthält | Voll qualifizierte Source-Klasse, z.B. `Model.Topic.Class` |
| `topic` | nein | Topic aus `class` | Basket-Type |
| `basketId` | nein | `b1` | Basket-ID |
| `oidField` | nein | deterministisch `shp.<recordNumber>` | DBF-Feld als Objekt-OID |
| `geometryAttribute` | nein | inferierbar, wenn Klasse genau ein Geometrieattribut enthält | Source-Attribut für Geometrie |
| `geometryType` | nein | aus Shape Type bzw. Modell inferieren | `coord`, `polyline`, `surface` |
| `dbfEncoding` | nein | `.cpg`, sonst `ISO-8859-1` | Charset für DBF-Zeichenfelder |
| `column.<DBF>` | nein | DBF-Feldname unverändert | DBF-Feldname → INTERLIS-Attributname |
| `deletedRecordPolicy` | nein | `error` | `error` oder `skip` bei DBF-Deleted-Records |
| `requireShx` | nein | `false` | Wenn `true`, muss `.shx` vorhanden und konsistent sein |

### 4.4 Output-Optionen, späterer Writer

| Option | Pflicht | Default | Bedeutung |
|---|---:|---|---|
| `class` | empfohlen | erste geschriebene ObjectEvent-Klasse | Welche IOX-Klasse geschrieben wird |
| `geometryAttribute` | nein | inferierbar, wenn Klasse genau ein Geometrieattribut enthält | Attribut, das als `.shp`-Geometrie geschrieben wird |
| `shapeType` | nein | aus Geometrieattribut inferieren | `null`, `point`, `polyline`, `polygon` |
| `dbfEncoding` | nein | `UTF-8` | DBF-Encoding und `.cpg` |
| `fieldNameStrategy` | nein | `strict` | `strict`, `truncate`, `stable` |
| `overflowPolicy` | nein | `strict` | `strict`, `truncate` fuer Character-Werte |
| `writeSidecarMapping` | nein | `true` bei gekürzten Namen | Schreibt `*.iliattr.json` |
| `prj` | nein | leer | WKT oder Pfad zu `.prj`-Template |
| `failOnMultipleBaskets` | nein | `true` | Mehrere Baskets ablehnen |

---

## 5. Package- und Klassenstruktur

Neue Klassen gehören unter:

```text
guru/interlis/transformer/io/shp/
  ShapefileFormatProvider.java
  ShapefileIoxReader.java
  ShapefileIoxWriter.java
  ShapefileOptions.java
  ShapefileReadPlan.java
  ShapefileWritePlan.java
  ShapefileMappingException.java

  core/
    ShapefileDataset.java
    ShapefileSidecars.java
    ShapefileHeader.java
    ShapeType.java
    ShapeRecord.java
    ShpReader.java
    ShxReader.java
    ShpWriter.java
    ShxWriter.java
    DbfHeader.java
    DbfField.java
    DbfFieldType.java
    DbfReader.java
    DbfWriter.java
    DbfRecord.java
    BoundsAccumulator.java
    EndianByteBuffer.java

  geom/
    ShpGeometryDecoder.java
    ShpGeometryEncoder.java
    GeometryKind.java
    GeometryAttributeResolver.java

  mapping/
    DbfToIomMapper.java
    IomToDbfMapper.java
    DbfNameMapper.java
    ShapefileSchema.java
    ShapefileField.java
```

Nur wenn eine Klasse sehr klein bleibt, darf sie als package-private Hilfsklasse zusammengelegt werden. Öffentliche Klassen so klein wie möglich halten.

---

## 6. Zentrale Klassen und Methoden

### 6.1 `ShapefileFormatProvider`

Package: `guru.interlis.transformer.io.shp`

Aufgabe: Formatprovider für `shp` und `shapefile`.

```java
public final class ShapefileFormatProvider implements IoxFormatProvider {
    private static final Set<String> FORMAT_IDS = Set.of("shp", "shapefile");

    @Override
    public String id();

    @Override
    public Set<String> formatIds();

    @Override
    public FormatCapabilities capabilities();

    @Override
    public boolean supportsInput(InputBinding binding);

    @Override
    public boolean supportsOutput(OutputBinding binding);

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception;

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) throws Exception;
}
```

Reader-Phase:

- `capabilities()` liefert zuerst `FormatCapabilities.readPathModelWithOptions()`.
- `supportsInput()` prüft `binding.format()` gegen `shp` / `shapefile`.
- `supportsOutput()` liefert bis zur Writer-Phase `false`.
- `openReader()` baut `ShapefileOptions`, `ShapefileReadPlan`, `ShapefileIoxReader`.

Writer-Phase:

- `capabilities()` muss dann ein neues Capability-Profil unterstützen. Falls `FormatCapabilities` keine passende Factory hat, ergänze eine Factory-Methode:

```java
public static FormatCapabilities readWritePathModelWithOptions() {
    return new FormatCapabilities(true, true, true, true, true);
}
```

- `supportsOutput()` akzeptiert `shp` / `shapefile`.
- `openWriter()` baut `ShapefileOptions`, `ShapefileWritePlan`, `ShapefileIoxWriter`.

Integration:

- `IoxFormatRegistry.defaultRegistry()` um `new ShapefileFormatProvider()` ergänzen.
- Keine Änderung an der Engine.

### 6.2 `ShapefileOptions`

Package: `guru.interlis.transformer.io.shp`

Aufgabe: Typisierte Optionen aus `FormatOptions` lesen und validieren.

```java
public final class ShapefileOptions {
    public static ShapefileOptions input(FormatOptions options);
    public static ShapefileOptions output(FormatOptions options);

    public Optional<String> className();
    public Optional<String> topicName();
    public String basketId();
    public Optional<String> oidField();
    public Optional<String> geometryAttribute();
    public Optional<GeometryKind> geometryType();
    public Charset dbfCharset(Optional<Charset> cpgCharset);
    public Map<String, String> columnMappings();
    public DeletedRecordPolicy deletedRecordPolicy();
    public boolean requireShx();

    public FieldNameStrategy fieldNameStrategy();
    public boolean writeSidecarMapping();
    public Optional<String> prj();
}
```

Methodenregeln:

- `columnMappings()` liest alle Optionskeys mit Prefix `column.`.
- DBF-Feldnamen in Mappings case-insensitive behandeln, aber Ausgabe deterministisch halten.
- Fehlermeldungen müssen Option, Input-ID/Output-ID und erwartete Werte enthalten.
- Keine unbekannten Optionen still ignorieren. Mindestens Shapefile-spezifische Optionen validieren. Optional: `warnUnknownOptions(...)` über `DiagnosticCollector`, falls im Repo ein passendes Muster existiert.

### 6.3 `ShapefileDataset`

Package: `guru.interlis.transformer.io.shp.core`

Aufgabe: Sidecar-Dateien eines Shapefile-Datasets kapseln.

```java
public record ShapefileDataset(
    Path shp,
    Path shx,
    Path dbf,
    Optional<Path> prj,
    Optional<Path> cpg
) {
    public static ShapefileDataset fromPath(Path path, boolean requireShx);
    public String baseName();
}
```

Regeln:

- `fromPath` akzeptiert im MVP nur `.shp`.
- Explizit `format: shp` mit `.zip` darf in einer späteren Phase ergänzt werden, aber nicht im ersten Reader-MVP.
- `.dbf` ist Pflicht.
- `.shx` ist optional, ausser `requireShx=true`.
- Sidecars case-insensitive suchen, damit `PARCELS.SHP` / `parcels.dbf` auf case-sensitiven Systemen robust funktionieren.
- Fehlermeldungen nennen alle erwarteten Sidecars.

### 6.4 `ShapeType`

Package: `guru.interlis.transformer.io.shp.core`

```java
public enum ShapeType {
    NULL(0),
    POINT(1),
    POLYLINE(3),
    POLYGON(5),
    MULTIPOINT(8),
    POINT_Z(11),
    POLYLINE_Z(13),
    POLYGON_Z(15),
    MULTIPOINT_Z(18),
    POINT_M(21),
    POLYLINE_M(23),
    POLYGON_M(25),
    MULTIPOINT_M(28),
    MULTIPATCH(31);

    public int code();
    public static ShapeType fromCode(int code);
    public boolean isSupported2dMvp();
    public GeometryKind defaultGeometryKind();
}
```

Regeln:

- Unbekannte/reservierte Shape Types mit klarer Exception ablehnen.
- Null Shapes sind als Record erlaubt, aber nicht als File-Header-Type ausser leeres Dataset.

### 6.5 `ShapefileHeader`

Package: `guru.interlis.transformer.io.shp.core`

```java
public record ShapefileHeader(
    int fileCode,
    int fileLengthWords,
    int version,
    ShapeType shapeType,
    double xmin,
    double ymin,
    double xmax,
    double ymax,
    double zmin,
    double zmax,
    double mmin,
    double mmax
) {
    public static ShapefileHeader read(FileChannel channel) throws IOException;
    public void write(FileChannel channel) throws IOException;
    public void validateMainFileHeader() throws ShapefileMappingException;
    public void validateIndexFileHeader() throws ShapefileMappingException;
}
```

Wichtig:

- Header ist 100 Bytes.
- File Code `9994` big endian.
- File Length big endian, in 16-bit words.
- Version `1000` little endian.
- Shape Type little endian.
- Bounding Box little endian doubles.
- Record Header: Record Number und Content Length big endian.
- Record Content: Shape Type und Nutzdaten little endian.

### 6.6 `ShpReader`

Package: `guru.interlis.transformer.io.shp.core`

```java
public final class ShpReader implements AutoCloseable {
    public static ShpReader open(Path shpPath) throws IOException;

    public ShapefileHeader header();
    public Optional<ShapeRecord> readNext() throws IOException;
    public long currentRecordNumber();
    public long currentByteOffset();

    @Override
    public void close() throws IOException;
}
```

Regeln:

- Sequentiell lesen.
- Keine `.shx`-Abhängigkeit für normales Lesen.
- Pro Record:
  - Record Header lesen.
  - Content Length prüfen.
  - Content in wiederverwendbaren oder passend allokierten ByteBuffer lesen.
  - Shape Type des Records lesen.
  - Nicht-null Shape Type muss zum Header Shape Type passen.
- Negative/ungerade/zu grosse Content Lengths ablehnen.
- EOF genau nach letztem vollständigem Record akzeptieren; partiellen Record ablehnen.

### 6.7 `ShapeRecord`

Package: `guru.interlis.transformer.io.shp.core`

```java
public record ShapeRecord(
    int recordNumber,
    ShapeType shapeType,
    ByteBuffer content,
    Bounds bounds
) {}
```

Hinweis:

- `content` darf nur innerhalb der aktuellen Verarbeitung verwendet werden, wenn ein wiederverwendbarer Buffer eingesetzt wird.
- Wenn `ShapeRecord` länger lebt, muss `content` read-only oder kopiert sein.
- Für einfache Implementierung zuerst pro Record genau passenden Buffer allokieren; danach in Performance-Phase wiederverwendbare Buffer einführen.

### 6.8 `DbfReader`

Package: `guru.interlis.transformer.io.shp.core`

```java
public final class DbfReader implements AutoCloseable {
    public static DbfReader open(Path dbfPath, Charset charset) throws IOException;

    public DbfHeader header();
    public List<DbfField> fields();
    public Optional<DbfRecord> readNext() throws IOException;
    public long currentRecordNumber();

    @Override
    public void close() throws IOException;
}
```

DBF-MVP:

- Unterstützte Feldtypen:
  - `C` Character → `String`
  - `N` Numeric → `String` oder passende Zahl? Für IOX zuerst als getrimmter String schreiben.
  - `F` Float → String oder `BigDecimal.toPlainString()`
  - `L` Logical → `true` / `false` / null
  - `D` Date → `yyyy-MM-dd`
- Nicht unterstützte Feldtypen (`M`, `B`, `G`, usw.) mit klarer Exception ablehnen.
- Deleted-Flag beachten:
  - `0x20` aktiv
  - `0x2A` gelöscht
  - Default `deletedRecordPolicy=error`, optional `skip`
- Header-Record-Count mit SHP-Record-Anzahl vergleichen, wenn möglich.

### 6.9 `DbfField`

Package: `guru.interlis.transformer.io.shp.core`

```java
public record DbfField(
    String name,
    DbfFieldType type,
    int length,
    int decimalCount
) {}
```

Regeln:

- Name aus DBF-Felddeskriptor lesen, Nullbytes entfernen, trimmen.
- Namen case-insensitive matchen, aber Originalnamen für Diagnostics behalten.
- Länge muss plausibel sein.
- Record-Länge muss zur Summe der Felder plus Deleted-Flag passen.

### 6.10 `ShpGeometryDecoder`

Package: `guru.interlis.transformer.io.shp.geom`

```java
public final class ShpGeometryDecoder {
    public Geometry decode(ShapeRecord record) throws ShapefileMappingException;
}
```

Verwendet `com.vividsolutions.jts.geom.GeometryFactory`.

MVP-Verhalten:

- `NULL` → `null`
- `POINT` → `Point`
- `POLYLINE` mit einem Part → `LineString`
- `POLYLINE` mit mehreren Parts → `MultiLineString`
- `POLYGON` mit einem Shell und optional Holes → `Polygon`
- `POLYGON` mit mehreren Shells → `MultiPolygon`

Polygon-Regeln:

- Ringe müssen geschlossen sein. Wenn nicht, Fehler.
- Ringe mit weniger als 4 Koordinaten ablehnen.
- Orientierung auswerten.
- Shapefile-Konvention: Aussenringe und Innenringe anhand Orientierung und/oder Enthaltensein gruppieren.
- Robustheit:
  1. Ringe lesen.
  2. Ringflächen/Orientierung berechnen.
  3. Shells und Holes bestimmen.
  4. Holes der kleinsten enthaltenden Shell zuordnen.
  5. Mehrere Shells zu `MultiPolygon`.
- Wenn die Orientierung widersprüchlich oder die Zuordnung unklar ist: im MVP Fehler, später optional `polygonRepairPolicy`.

### 6.11 `GeometryAttributeResolver`

Package: `guru.interlis.transformer.io.shp.geom`

```java
public final class GeometryAttributeResolver {
    public ResolvedGeometryAttribute resolveForInput(
        TypeSystemFacade typeSystem,
        String className,
        ShapeType shapeType,
        Optional<String> configuredAttribute,
        Optional<GeometryKind> configuredKind
    );

    public ResolvedGeometryAttribute resolveForOutput(
        TypeSystemFacade typeSystem,
        String className,
        Optional<String> configuredAttribute,
        Optional<GeometryKind> configuredKind
    );
}
```

`ResolvedGeometryAttribute`:

```java
public record ResolvedGeometryAttribute(
    String attributeName,
    GeometryKind kind
) {}
```

Regeln:

- Wenn `geometryAttribute` gesetzt ist, muss das Attribut in der Klasse existieren.
- Wenn nicht gesetzt, darf nur inferiert werden, wenn genau ein Geometrieattribut existiert.
- Für Point/COORD: `GeometryKind.COORD`.
- Für PolyLine: `GeometryKind.POLYLINE`.
- Für Polygon: `GeometryKind.SURFACE`.
- Falls `TypeSystemFacade` noch keine Geometrie-Hilfsmethoden hat, ergänze minimale, generische Methoden dort oder in einem separaten Resolver, ohne DM01/DMAV-Sonderlogik.

### 6.12 `DbfToIomMapper`

Package: `guru.interlis.transformer.io.shp.mapping`

```java
public final class DbfToIomMapper {
    public DbfToIomMapper(
        String className,
        Optional<String> oidField,
        Map<String, String> columnMappings,
        List<DbfField> fields,
        TypeSystemFacade typeSystem
    );

    public Iom_jObject map(long recordNumber, DbfRecord dbfRecord) throws IoxException;
}
```

Regeln:

- OID:
  - Wenn `oidField` gesetzt: Wert aus DBF lesen, trimmen, als OID verwenden.
  - Wenn leer/null: `shp.` + recordNumber.
- Attribute:
  - Feldname durch `column.<DBF>`-Mapping übersetzen.
  - Null/Leerwerte als `null` behandeln, ausser String-Felder, bei denen leer fachlich erlaubt ist. Default: leere DBF-Felder nicht als Attribut schreiben.
  - Nur skalare Attribute setzen. Geometrie wird separat gesetzt.
- Wenn ein DBF-Feld auf ein nicht vorhandenes Attribut gemappt wird: Fehler.
- Wenn ein DBF-Feld nicht gemappt ist und kein gleichnamiges Attribut existiert: im strict-MVP Fehler oder Diagnostic, nicht still ignorieren. Empfehlung: Fehler, Option `unmappedColumnPolicy=ignore|warn|error` kann später folgen.

### 6.13 `ShapefileIoxReader`

Package: `guru.interlis.transformer.io.shp`

```java
public final class ShapefileIoxReader implements IoxReader {
    private enum State { START, BEFORE_BASKET, INSIDE_BASKET, DONE }

    public static ShapefileIoxReader open(InputBinding binding, FormatOpenContext context) throws IOException;

    @Override
    public IoxEvent read() throws IoxException;

    @Override
    public void close() throws IoxException;

    @Override
    public void setFactory(IoxFactoryCollection factory) throws IoxException;

    @Override
    public IoxFactoryCollection getFactory() throws IoxException;

    @Override
    public IomObject createIomObject(String type, String oid) throws IoxException;
}
```

Event-Reihenfolge wie bei `JdbcIoxReader`:

```text
StartTransferEvent
  StartBasketEvent
  ObjectEvent*
  EndBasketEvent
EndTransferEvent
null
```

Reader-Mapping:

```java
private IoxEvent readFeatureOrEndBasket() throws IoxException;
private IomObject mapCurrentRecord(ShapeRecord shape, DbfRecord dbf) throws IoxException;
private String resolveBasketType();
```

Wichtige Regeln:

- SHP- und DBF-Records müssen synchron gelesen werden.
- Wenn SHP mehr Records als DBF hat oder umgekehrt: Fehler mit Record-Nummer.
- Null Shape:
  - Objekt wird erzeugt, Geometrieattribut bleibt leer.
  - Optional später: `nullShapePolicy=skip|emit|error`.
- Geometry wird via `ShpGeometryDecoder` zu JTS und via `Jts2iox` zu IOX-Geometrie konvertiert.
- `Jts2iox`-Methoden analog `JdbcGeometryConverter` nutzen:
  - `Jts2iox.JTS2coord(...)`
  - `Jts2iox.JTS2polyline(...)`
  - `Jts2iox.JTS2multipolyline(...)`
  - `Jts2iox.JTS2surface(...)`
  - `Jts2iox.JTS2multisurface(...)`

---

## 7. Writer-Architektur im Detail

Der Writer kommt nach dem Reader. Er muss von Anfang an die Performance-Erkenntnis aus `iox-wkf` PR #32 berücksichtigen.

### 7.1 `ShapefileDatasetWriter`

Package: `guru.interlis.transformer.io.shp.core`

```java
public final class ShapefileDatasetWriter implements AutoCloseable {
    public static ShapefileDatasetWriter open(
        Path targetShp,
        ShapefileSchema schema,
        ShapefileWriteOptions options
    ) throws IOException;

    public void write(Geometry geometry, Object[] dbfValues) throws IOException;

    public void finish() throws IOException;

    @Override
    public void close() throws IOException;
}
```

Interne Felder:

```java
private final FileChannel shpChannel;
private final FileChannel shxChannel;
private final FileChannel dbfChannel;
private final ShpWriter shpWriter;
private final ShxWriter shxWriter;
private final DbfWriter dbfWriter;
private final BoundsAccumulator bounds;
private long shpLengthBytes = 100;
private long shxLengthBytes = 100;
private int recordNumber = 0;
private boolean finished = false;
```

Ablauf:

```text
open()
  - Zielverzeichnis anlegen
  - Temp-Dateien anlegen: *.shp.tmp, *.shx.tmp, *.dbf.tmp
  - Placeholder .shp header schreiben
  - Placeholder .shx header schreiben
  - .dbf header mit recordCount=0 schreiben

write(geometry, dbfValues)
  - recordNumber++
  - Geometry in Shape-Record serialisieren
  - shpOffsetWords = shpLengthBytes / 2
  - contentLengthWords = encodedContentBytes / 2
  - SHP record header und content schreiben
  - SHX index entry schreiben
  - DBF record schreiben
  - bounds aktualisieren
  - shpLengthBytes += 8 + encodedContentBytes
  - shxLengthBytes += 8
  - Grössenlimits prüfen

finish()
  - .shp Header mit finaler File Length, Shape Type, Bounds patchen
  - .shx Header mit finaler File Length, Shape Type, Bounds patchen
  - .dbf recordCount patchen
  - Channels schliessen
  - Temp-Dateien atomar oder möglichst atomar an Zielnamen verschieben

close()
  - Wenn nicht finished: Ressourcen schliessen und Temp-Dateien löschen
```

Keine dieser Methoden darf eine FeatureCollection, DataStore-ähnliche Abstraktion oder Transaktion pro Feature erzeugen.

### 7.2 `ShpWriter`

Package: `guru.interlis.transformer.io.shp.core`

```java
public final class ShpWriter implements AutoCloseable {
    public ShpWriter(FileChannel channel, ShapeType shapeType);

    public EncodedShape encode(Geometry geometry) throws IOException;
    public void writeRecord(int recordNumber, EncodedShape encoded) throws IOException;
    public void writeHeader(ShapefileHeader header) throws IOException;

    @Override
    public void close() throws IOException;
}
```

`EncodedShape`:

```java
public record EncodedShape(
    ByteBuffer content,
    int contentLengthBytes,
    Bounds bounds
) {}
```

Buffer-Regeln:

- Ein wiederverwendbarer `ByteBuffer` für Shape-Content ist erlaubt und erwünscht.
- Wenn ein Record grösser ist als der Buffer, Buffer vergrössern.
- Keine Memory-Mapped-Files im MVP.
- Byte Order pro Abschnitt korrekt setzen:
  - Record Header big endian
  - Record Content little endian

### 7.3 `ShxWriter`

Package: `guru.interlis.transformer.io.shp.core`

```java
public final class ShxWriter implements AutoCloseable {
    public ShxWriter(FileChannel channel);
    public void writeHeader(ShapefileHeader header) throws IOException;
    public void writeIndexEntry(long shpOffsetWords, int contentLengthWords) throws IOException;
    @Override public void close() throws IOException;
}
```

Regeln:

- Jeder Index Entry ist 8 Bytes:
  - Offset big endian, in 16-bit words
  - Content Length big endian, in 16-bit words
- Offset bezieht sich auf Beginn des SHP-Records inklusive Record Header.

### 7.4 `DbfWriter`

Package: `guru.interlis.transformer.io.shp.core`

```java
public final class DbfWriter implements AutoCloseable {
    public DbfWriter(FileChannel channel, List<DbfField> fields, Charset charset);

    public void writeHeader(int recordCount) throws IOException;
    public void writeRecord(Object[] values) throws IOException;
    public void patchRecordCount(int recordCount) throws IOException;

    @Override
    public void close() throws IOException;
}
```

Regeln:

- DBF-Header am Anfang mit `recordCount=0` schreiben.
- Record Count am Ende patchen.
- Pro Record exakt ein Deleted-Flag plus alle Feldwerte schreiben.
- Strings gemäss Charset encodieren und auf Feldlänge rechts auffüllen.
- Numeric/Floating rechtsbündig schreiben.
- Date als `yyyyMMdd`.
- Logical als `T`, `F` oder Space.
- Zu lange Werte:
  - `strict`: Fehler
  - später optional `truncate`: kürzen mit Diagnostic

### 7.5 `ShapefileIoxWriter`

Package: `guru.interlis.transformer.io.shp`

```java
public final class ShapefileIoxWriter implements IoxWriter {
    public static ShapefileIoxWriter open(OutputBinding binding, FormatOpenContext context) throws IOException;

    @Override
    public void write(IoxEvent event) throws IoxException;

    @Override
    public void flush() throws IoxException;

    @Override
    public void close() throws IoxException;
}
```

Event-Regeln:

- `StartTransferEvent`: akzeptieren, keine Datei finalisieren.
- `StartBasketEvent`: ersten Basket merken; weitere Baskets bei `failOnMultipleBaskets=true` ablehnen.
- `ObjectEvent`: Objektklasse prüfen, in Geometry + DBF-Werte zerlegen, schreiben.
- `EndBasketEvent`: akzeptieren.
- `EndTransferEvent`: `finish()` auslösen.
- `close()` ohne `EndTransferEvent`: `finish()` nur dann, wenn bereits geschrieben und kein Fehlerzustand; sonst Temp-Dateien löschen. Besser: `close()` finalisiert nicht still, wenn der Transfer unvollständig ist.

`flush()`:

- Darf Channels flushen.
- Darf nicht behaupten, ein gültiges finales Shapefile sei vorhanden.
- Final gültig ist nur nach `EndTransferEvent`/`finish()`.

---

## 8. Tests und Testdaten

### 8.1 Allgemeine Testregeln

- Jede Phase ergänzt Tests vor oder zusammen mit der Implementierung.
- Kein Test darf von QGIS, GDAL, ogr2ogr, GeoTools oder externen Programmen abhängen.
- Test-Shapefiles werden entweder programmatisch in Test-TempDirs erzeugt oder als sehr kleine Binärfixtures committed.
- Wenn Binärfixtures committed werden, müssen sie minimal und dokumentiert sein.
- Für INTERLIS-Modelle gilt: `.ili` mit `ili2c` prüfen.
- Für erzeugte XTF/ITF/XML gilt: mit `ilivalidator` prüfen, wenn das Zielmodell Teil des Tests ist.

### 8.2 Unit-Tests

Neue Tests unter:

```text
src/test/java/guru/interlis/transformer/io/shp/
src/test/java/guru/interlis/transformer/io/shp/core/
src/test/java/guru/interlis/transformer/io/shp/geom/
src/test/java/guru/interlis/transformer/io/shp/mapping/
```

Pflichttests:

- `FormatIdResolverTest`
  - `.shp` → `shp` ist bereits vorhanden, Regression behalten/ergänzen.
  - Explizites `format: shapefile` wird vom Provider akzeptiert.

- `ShapefileFormatProviderTest`
  - `formatIds()` enthält `shp`, `shapefile`.
  - Reader wird für `shp` akzeptiert.
  - Output wird bis Writer-Phase abgelehnt, danach akzeptiert.
  - `IoxFormatRegistry.defaultRegistry()` enthält Provider.

- `ShapefileHeaderTest`
  - Header 100 Bytes.
  - Mixed endian korrekt.
  - File Code, Version, Shape Type validiert.
  - File Length in 16-bit words.

- `ShpReaderTest`
  - Point-Shapefile mit 1 Record.
  - Null Shape.
  - Header Shape Type mismatch.
  - Unvollständiger Record führt zu Fehler.

- `DbfReaderTest`
  - Character, Numeric, Float, Logical, Date.
  - Deleted-Record-Policy.
  - Unsupported field type.
  - Encoding via explizitem Charset.

- `ShpGeometryDecoderTest`
  - Point → JTS Point.
  - PolyLine single part → LineString.
  - PolyLine multi part → MultiLineString.
  - Polygon shell → Polygon.
  - Polygon shell + hole.
  - Multi shell → MultiPolygon.
  - Ungeschlossener Ring → Fehler.

- `DbfToIomMapperTest`
  - OID aus Feld.
  - deterministische OID ohne Feld.
  - `column.<DBF>`-Mapping.
  - unbekannte Spalte/Attribut-Fehler.

### 8.3 Integrationstests

Neue Integrationstests unter:

```text
src/integrationTest/java/guru/interlis/transformer/io/shp/
```

Beispiele unter:

```text
examples/shp-to-xtf/
examples/shp-spatial-to-xtf/
```

Pflichtszenarien:

1. Point-Shapefile → XTF
   - Source-Modell mit Klasse `Station` und `COORD`.
   - Zielmodell ähnlich oder transformiert.
   - `--validate` muss erfolgreich sein.

2. PolyLine-Shapefile → XTF
   - Source-Modell mit `POLYLINE`.
   - Validierung erfolgreich.

3. Polygon-Shapefile → XTF
   - Source-Modell mit `SURFACE`.
   - Validierung erfolgreich.

4. Fehlerfall unsupported Z-Shapefile
   - Transformation bricht mit klarer Fehlermeldung ab.

5. Fehlerfall fehlendes `.dbf`
   - Transformation bricht mit klarer Fehlermeldung ab.

### 8.4 Writer-Tests

Sobald Writer implementiert wird:

- `ShapefileDatasetWriterTest`
  - Schreibt Point-Shapefile.
  - `.shp`, `.shx`, `.dbf` existieren.
  - Header-Dateilängen korrekt.
  - `.shx` Offsets zeigen auf SHP-Records.
  - DBF record count korrekt gepatcht.

- `ShapefileRoundtripTest`
  - IOX → Shapefile → ShapefileIoxReader → IOX.
  - Prüft Attributwerte, Geometrie und Record-Anzahl.

- `ShapefileWriterPerformanceGuardTest`
  - Schreibt 10'000 einfache Point-Features.
  - Test soll nicht hart auf Sekundenasserts beruhen, aber prüfen:
    - kein OOM,
    - Heap-Verbrauch wächst nicht proportional zur Feature-Anzahl,
    - Output-Grössen plausibel,
    - Laufzeit wird geloggt.
  - Falls Timing zu flaky ist, als dokumentierter Opt-in-Performance-Test ausführen, nicht Teil von `check`.

---

## 9. Phasenplan

Jede Phase endet mit:

- kompilierendem Code,
- relevanten Tests,
- aktualisierter Dokumentation, falls Verhalten sichtbar ist,
- Abschlussbericht gemäss `AGENTS.md`: geänderte Dateien, tatsächlich ausgeführte Kommandos, Ergebnis, nicht geprüfte Risiken, Commit-Status.

### Phase 0: Orientierung und Repo-Verträge lesen

Ziel: Keine Codeänderung, nur Kontext herstellen.

Aufgaben:

1. Lies:
   - `AGENTS.md`
   - `docs/agent/DEFINITION_OF_DONE.md`
   - `docs/agent/COMMIT_POLICY.md`
   - `docs/agent/DECISIONS.md`
   - `.skills/java-test-gap/SKILL.md`
   - `.skills/gradle-verification/SKILL.md`
2. Lies die vorhandenen Formatklassen:
   - `IoxFormatProvider`
   - `IoxFormatRegistry`
   - `FormatIdResolver`
   - `FormatOptions`
   - `FormatOpenContext`
   - `CsvFormatProvider`
   - `WkfGeoPackageFormatProvider`
   - `JdbcFormatProvider`
   - `JdbcIoxReader`
   - `JdbcGeometryConverter`
3. Notiere die kleinste Codefläche für Phase 1.

Tests:

```bash
./gradlew test --tests '*Format*'
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md und die im Repo genannten Agent-/Skill-Dokumente. Ändere noch keinen Produktionscode. Ermittle die kleinste betroffene Codefläche für die Shapefile-Provider-Integration und berichte, welche bestehenden Tests Formatprovider, FormatIdResolver und Registry abdecken.

### Phase 1: Provider-Skeleton und Registry-Integration

Ziel: `shp` / `shapefile` ist ein bekannter Input-Formatprovider, aber Reader kann noch kontrolliert `not implemented` melden.

Produktionscode:

- Neue Klasse `ShapefileFormatProvider`.
- Neue Klasse `ShapefileMappingException`.
- `IoxFormatRegistry.defaultRegistry()` um Provider ergänzen.
- Optional `FormatCapabilities.readWritePathModelWithOptions()` vorbereiten, aber noch nicht verwenden.

Tests:

- `ShapefileFormatProviderTest`
- `IoxFormatRegistryTest` ergänzen, falls vorhanden.
- `FormatIdResolverTest` für `.shp`, falls nicht vorhanden.

Dokumentation:

- `docs/formats.md`: Shapefile von „reserviert“ zu „bekannter Provider, Reader in Arbeit“ nur dann ändern, wenn der Provider sichtbar wird. Alternativ erst in späterer Phase ändern.

Verifikation:

```bash
./gradlew test --tests '*ShapefileFormatProviderTest' --tests '*FormatIdResolverTest'
./gradlew test
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 1: ShapefileFormatProvider als Skeleton, Registry-Integration und fokussierte Tests. Keine Shapefile-Binärparser implementieren. Keine GeoTools-Dependency hinzufügen.

### Phase 2: Shapefile-Core für Header, Sidecars und DBF Reader

Ziel: `.shp`/`.dbf` Sidecars werden gefunden, Header und DBF-Records können isoliert gelesen werden.

Produktionscode:

- `ShapefileDataset`
- `ShapefileHeader`
- `ShapeType`
- `DbfHeader`
- `DbfField`
- `DbfFieldType`
- `DbfRecord`
- `DbfReader`
- `EndianByteBuffer` oder kleine ByteBuffer-Helfer

Tests:

- `ShapefileDatasetTest`
- `ShapefileHeaderTest`
- `DbfReaderTest`
- Minimalfixtures programmatisch erzeugen.

Verifikation:

```bash
./gradlew test --tests 'guru.interlis.transformer.io.shp.core.*'
./gradlew test
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 2: Core-Klassen für Sidecar-Auflösung, SHP-Header und DBF-Lesen. Der IOX-Reader bleibt noch unimplementiert. Verwende ByteBuffer/FileChannel und teste die Mixed-Endian-Regeln gründlich.

### Phase 3: SHP Record Reader und Geometrie-Decoder

Ziel: 2D Point, PolyLine und Polygon können aus `.shp` sequentiell gelesen und in JTS-Geometrien umgewandelt werden.

Produktionscode:

- `ShapeRecord`
- `ShpReader`
- `BoundsAccumulator`
- `ShpGeometryDecoder`
- `GeometryKind`

Tests:

- `ShpReaderTest`
- `ShpGeometryDecoderTest`
- Fehlerfälle: unsupported Shape Type, Mismatch Header/Record, kaputter Record, ungeschlossener Polygonring.

Verifikation:

```bash
./gradlew test --tests 'guru.interlis.transformer.io.shp.core.*'
./gradlew test --tests 'guru.interlis.transformer.io.shp.geom.*'
./gradlew test
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 3: sequentielles Lesen von SHP-Records und Geometrie-Decoding zu com.vividsolutions.jts-Geometrien. Implementiere keine IOX-Event-Erzeugung und keinen Writer.

### Phase 4: ShapefileIoxReader für Point-Geometrie

Ziel: Ein Point-Shapefile kann als IOX-Eventstream gelesen und in einer Transformation nach XTF verwendet werden.

Produktionscode:

- `ShapefileOptions.input(...)`
- `ShapefileReadPlan`
- `GeometryAttributeResolver` minimal für COORD
- `DbfToIomMapper`
- `ShapefileIoxReader`
- `ShapefileFormatProvider.openReader(...)` aktiviert

Tests:

- Unit: `ShapefileOptionsTest`, `DbfToIomMapperTest`, `ShapefileIoxReaderTest`
- Integration: `ShpPointToXtfIntegrationTest`

Beispiel:

- `examples/shp-to-xtf/`
- README mit CLI-Kommando

Verifikation:

```bash
./gradlew test --tests 'guru.interlis.transformer.io.shp.*'
./gradlew integrationTest --tests '*ShpPointToXtfIntegrationTest'
./gradlew check
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 4: ShapefileIoxReader für Point/COORD inklusive Provider-Aktivierung, Options, Mapping und einem validierten Point-Integrationstest. Keine Linien/Polygone und keinen Writer implementieren.

### Phase 5: Reader für PolyLine und Polygon

Ziel: PolyLine- und Polygon-Shapefiles werden als IOX-Geometrien gelesen.

Produktionscode:

- `GeometryAttributeResolver` für `polyline` und `surface` ergänzen.
- `ShapefileIoxReader.mapCurrentRecord(...)` um PolyLine/Surface ergänzen.
- `ShpGeometryDecoder` ggf. stabilisieren.

Tests:

- Integration: `ShpPolylineToXtfIntegrationTest`
- Integration: `ShpPolygonToXtfIntegrationTest`
- Unit: MultiPart Line, Polygon mit Hole, MultiPolygon.

Dokumentation:

- `docs/formats.md`: SHP Input mit Einschränkungen dokumentieren.
- `README.md`: Shapefile in Eingabeformate aufnehmen.
- `docs/cli.md`: Beispiel ergänzen.

Verifikation:

```bash
./gradlew test --tests 'guru.interlis.transformer.io.shp.*'
./gradlew integrationTest --tests '*Shp*ToXtfIntegrationTest'
./gradlew check
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 5: PolyLine- und Polygon-Unterstützung im Reader und aktualisierte Doku. Achte besonders auf Polygon-Ring-Gruppierung und klare Fehler bei ungültigen Ringen.

### Phase 6: Interoperabilität, Encoding und Sidecars

Ziel: Reader wird praxistauglich für reale Shapefile-Datasets.

Produktionscode:

- `.cpg` lesen und Charset ableiten.
- `dbfEncoding` überschreibt `.cpg`.
- `.prj` erkennen und optional als Diagnostic/Info melden.
- `.shx` optional validieren, wenn `requireShx=true`.
- Explizites `format: shp` mit `.zip` optional unterstützen:
  - Zip in TempDir entpacken.
  - Genau ein `.shp` oder per Option `member` auswählbar.
  - TempDir bei `close()` löschen.

Tests:

- CPG-Encoding UTF-8.
- Latin-1-Fallback.
- Fehlendes SHX mit `requireShx=false` akzeptiert.
- Fehlendes SHX mit `requireShx=true` Fehler.
- Optional ZIP-Test.

Verifikation:

```bash
./gradlew test --tests 'guru.interlis.transformer.io.shp.*'
./gradlew integrationTest --tests '*Shp*'
./gradlew check
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 6: Encoding, CPG/PRJ/SHX-Sidecar-Verhalten und optional ZIP-Input. Keine Writer-Implementierung beginnen.

### Phase 7: Low-Level Writer-Core

Ziel: Core kann `.shp`, `.shx`, `.dbf` schnell und streaming-basiert schreiben, aber noch ohne IOX-Adapter.

Produktionscode:

- `ShapefileDatasetWriter`
- `ShpWriter`
- `ShxWriter`
- `DbfWriter`
- `ShpGeometryEncoder`
- `IomToDbfMapper` noch nicht zwingend, falls Core mit JTS + Object[] getestet wird.

Tests:

- `ShapefileDatasetWriterTest`
- Header-Patching.
- SHX-Offsets.
- DBF-Recordcount.
- Point/Polyline/Polygon schreiben.
- Roundtrip mit eigenem `ShpReader`/`DbfReader`.

Performance-Regressionsschutz:

- Test oder Opt-in-Test für 10'000 Points.
- Dokumentieren, dass Writer keine Collections puffert.

Verifikation:

```bash
./gradlew test --tests 'guru.interlis.transformer.io.shp.core.*Writer*'
./gradlew test --tests 'guru.interlis.transformer.io.shp.geom.*Encoder*'
./gradlew test
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 7: Low-Level Writer-Core mit direktem Streaming in SHP/SHX/DBF, Header-Patching und Temp-Datei-Commit. Keine FeatureCollection, keine DataStore-Abstraktion, keine GeoTools-Dependency.

### Phase 8: ShapefileIoxWriter und Output-Integration

Ziel: ilitransformer kann eine einzelne IOX-Klasse als Shapefile-Output schreiben.

Produktionscode:

- `ShapefileIoxWriter`
- `ShapefileOptions.output(...)`
- `ShapefileWritePlan`
- `IomToDbfMapper`
- `DbfNameMapper`
- `ShapefileFormatProvider.supportsOutput(...)` aktivieren
- `FormatCapabilities.readWritePathModelWithOptions()` verwenden

Tests:

- IOX → Shapefile Writer Unit.
- XTF → SHP Integration, falls Engine Output-Formatwechsel direkt erlaubt.
- SHP Roundtrip: XTF/Input → SHP/Output → SHP/Input → XTF/Output und Vergleich.

Dokumentation:

- `docs/formats.md`: SHP Output ergänzen.
- `docs/cli.md`: Output-Beispiel ergänzen.
- `README.md`: Formatmatrix aktualisieren.

Verifikation:

```bash
./gradlew test --tests 'guru.interlis.transformer.io.shp.*Writer*'
./gradlew integrationTest --tests '*Shp*'
./gradlew check
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Implementiere nur Phase 8: IOX-Writer-Adapter und Provider-Output-Integration. Der Writer darf nur eine Klasse/einen Geometrietyp schreiben und muss mehrdeutige Situationen strikt ablehnen.

### Phase 9: Abschluss, Dokumentation, Feature-Matrix und Realitätscheck

Ziel: Shapefile-Support ist dokumentiert, getestet und in der Feature-Matrix sichtbar.

Aufgaben:

- `docs/formats.md` finalisieren.
- `docs/cli.md` aktualisieren.
- `README.md` aktualisieren.
- `docs/feature-matrix.md` bzw. Generator aktualisieren, falls im Repo generiert.
- `generateFeatureMatrix` ausführen, falls notwendig.
- Alle Beispiele prüfen.
- Optional reale kleine Shapefile-Fixture ergänzen, wenn lizenzrechtlich unproblematisch.

Verifikation:

```bash
./gradlew test
./gradlew integrationTest
./gradlew check
./gradlew installDist
./build/install/ilitransformer/bin/ilitransformer --help
./build/install/ilitransformer/bin/ilitransformer transform -m examples/shp-to-xtf/mapping.yaml --modeldir examples/shp-to-xtf/models --validate --report build/reports/shp
```

Agent Prompt:

> Analysiere zuerst diese Spezifikation ./docs/ilitransformer-shapefile-agent-spec.md . Führe Phase 9 aus: Dokumentation, Feature-Matrix, Beispiele und Abschlussverifikation. Ändere keine Produktionslogik mehr ausser für kleine Bugfixes, die durch die Abschlussverifikation entdeckt werden.

---

## 10. Fehler- und Diagnostic-Design

Fehler sollen präzise und nutzbar sein. Beispiele:

```text
SHP input 'parcels': missing required sidecar file 'parcels.dbf' next to 'parcels.shp'.
```

```text
SHP input 'parcels': unsupported shape type 'PolygonZ' (15). Supported in this phase: Point, PolyLine, Polygon, NullShape.
```

```text
SHP input 'parcels', record 42: DBF record is marked as deleted. Set option deletedRecordPolicy=skip to ignore deleted records.
```

```text
SHP input 'parcels': geometryAttribute is not configured and source class 'Demo.Data.Parcel' contains 0/2 geometry attributes. Configure option geometryAttribute.
```

```text
SHP output 'out': cannot write object of class 'Model.Topic.Other'. This Shapefile writer is configured for 'Model.Topic.Parcel'.
```

Keine Passwörter oder sensitiven Werte loggen. Für Shapefile gibt es normalerweise keine Secrets, aber Pfade und Optionen sollen trotzdem sauber behandelt werden.

---

## 11. DBF-Feldnamenstrategie für Writer

DBF-Feldnamen sind praktisch kurz und eingeschränkt. Writer darf nicht naiv INTERLIS-Attributnamen abschneiden, ohne Kollisionen zu erkennen.

### 11.1 `DbfNameMapper`

Package: `guru.interlis.transformer.io.shp.mapping`

```java
public final class DbfNameMapper {
    public static DbfNameMapping create(
        List<String> attributeNames,
        FieldNameStrategy strategy
    );
}
```

```java
public record DbfNameMapping(
    Map<String, String> attributeToDbf,
    Map<String, String> dbfToAttribute,
    List<String> warnings
) {}
```

Strategien:

- `strict`
  - Attributname muss DBF-kompatibel und eindeutig sein.
  - Maximal 10 Zeichen.
  - Keine Kollision case-insensitive.

- `truncate`
  - Kürzt auf 10 Zeichen.
  - Bei Kollision Fehler.

- `stable`
  - Deterministisch kürzen und nummerieren, z.B. `ATTRIBUTE`, `ATTRIBU_1`.
  - Zusätzlich Sidecar `*.iliattr.json` schreiben.

Sidecar-Beispiel:

```json
{
  "format": "ilitransformer-shapefile-attribute-map-v1",
  "fields": [
    { "dbf": "BFS_NR", "attribute": "bfsnr" },
    { "dbf": "GEMEIN_1", "attribute": "GemeindenameLang" }
  ]
}
```

---

## 12. Performance-Regeln als Code-Kommentare und Tests dokumentieren

Im Writer-Core soll ein Kommentar stehen, der die PR-#32-Erfahrung festhält:

```java
/*
 * Performance rule:
 * Do not introduce a FeatureCollection-like buffering layer here.
 * The former iox-wkf Shapefile writer was slow because each feature was
 * wrapped in a ListFeatureCollection and written through SimpleFeatureStore.
 * PR claeis/iox-wkf#32 switched to FeatureWriter and reduced 10k feature
 * export from roughly minutes to seconds. This implementation follows the
 * same low-level principle directly: one pass, open channels, reusable
 * buffers, header patching at finish().
 */
```

Zusätzlich muss ein Test oder ArchUnit-ähnlicher Check nicht zwingend sein, aber folgende Imports dürfen im Shapefile-Package nie auftauchen:

```text
org.geotools.*
org.opengis.*
```

Ein einfacher Test kann alle `src/main/java/guru/interlis/transformer/io/shp/**/*.java` lesen und diese Importstrings verbieten.

---

## 13. Dokumentationsänderungen

### 13.1 `docs/formats.md`

Von:

```text
SHP | reserviert | nein | — | — | — | Noch nicht implementiert
```

Zu Reader-MVP:

```text
SHP | ja | nein | ja (Point/Polyline/Polygon 2D) | nein | nein | Eine Shapefile-Datei → eine flache Quellklasse
```

Zu Writer-MVP später:

```text
SHP | ja | ja | ja (Point/Polyline/Polygon 2D) | nein | nein | Eine Klasse/ein Geometrietyp pro Shapefile
```

Ergänze Abschnitt:

- Optionen
- Beispiele YAML und `.ilimap`
- Einschränkungen
- Encoding/CPG
- PRJ ohne Reprojektion
- DBF-Feldnamen

### 13.2 `README.md`

Eingabeformate aktualisieren:

- `shp` / `shapefile` — flaches Simple-Feature-Eingabeformat; `.shp` + `.dbf`, optional `.shx`, `.prj`, `.cpg`; 2D Point/Polyline/Polygon.

Wenn Writer implementiert:

- Outputformate entsprechend ergänzen.

### 13.3 `docs/cli.md`

Ein Beispiel ergänzen:

```bash
ilitransformer transform \
  -m examples/shp-to-xtf/mapping.yaml \
  --modeldir examples/shp-to-xtf/models \
  --validate \
  --report build/reports/shp
```

---

## 14. Quellen und Kontext für den Agenten

Diese Quellen erklären, warum die Implementierung so geschnitten ist:

- ESRI Shapefile Technical Description: `https://www.esri.com/content/dam/esrisites/sitecore-archive/Files/Pdfs/library/whitepapers/pdfs/shapefile.pdf`
- iox-wkf PR #32: `https://github.com/claeis/iox-wkf/pull/32`
- iox-wkf Commit Patch #5911229: `https://github.com/claeis/iox-wkf/commit/5911229.patch`
- GeoTools `ShapefileFeatureWriter`: `https://raw.githubusercontent.com/geotools/geotools/main/modules/plugin/shapefile/src/main/java/org/geotools/data/shapefile/ShapefileFeatureWriter.java`
- GeoTools `ShapefileWriter`: `https://raw.githubusercontent.com/geotools/geotools/main/modules/plugin/shapefile/src/main/java/org/geotools/data/shapefile/shp/ShapefileWriter.java`
- ilitransformer Repo: `https://github.com/edigonzales/ilinexus`

Wichtig: GeoTools-Code darf nicht kopiert werden. Die Quellen dienen nur als Architektur- und Performance-Kontext.

---

## 15. Definition of Done für die gesamte Shapefile-Unterstützung

Reader gilt als fertig, wenn:

- `shp` und `shapefile` als Input funktionieren.
- `.shp`-Extension automatisch `shp` ergibt.
- Point, PolyLine und Polygon 2D gelesen werden.
- DBF-Attribute gemappt werden.
- Geometry-Attribute via Option oder eindeutigem Modell inferiert werden.
- Fehlende Sidecars und unsupported Shape Types klare Fehler liefern.
- Mindestens ein Point-, ein PolyLine- und ein Polygon-Integrationstest erfolgreich nach XTF transformiert und validiert.
- `docs/formats.md`, `docs/cli.md`, `README.md` aktualisiert sind.
- `./gradlew check` erfolgreich läuft.

Writer gilt als fertig, wenn:

- `shp` und `shapefile` als Output funktionieren.
- Der Writer strikt streaming-basiert ist.
- `.shp`, `.shx`, `.dbf` korrekt erzeugt werden.
- Header und Record Counts am Ende korrekt gepatcht werden.
- DBF-Feldnamen eindeutig und dokumentiert gemappt werden.
- Roundtrip-Tests erfolgreich sind.
- Performance-Regressionsschutz vorhanden ist.
- `./gradlew check` erfolgreich läuft.
