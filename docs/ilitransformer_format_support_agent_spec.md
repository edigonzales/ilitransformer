# ilitransformer: Format-Support für CSV, GeoPackage, Shapefile und JDBC

**Zielpublikum:** LLM Coding Agent / autonomer Implementierungsagent  
**Repository:** `https://github.com/edigonzales/ilinexus`  
**Produktname im Repo:** `ilitransformer`  
**Stand der Spezifikation:** 2026-06-24  
**Primäres Ziel:** Die bestehende, modellbewusste INTERLIS-Transformationsengine soll schrittweise so erweitert werden, dass neben ITF/XTF/XML auch weitere Datenquellen über `IoxReader` gelesen und in valide XTF/ITF-Ausgaben transformiert werden können.

---

## 1. Kontext und Zielbild

`ilitransformer` ist eine Java/Gradle-Anwendung zur modellbewussten Transformation von INTERLIS-Transferdaten. Der aktuelle produktive Fokus liegt auf INTERLIS-zu-INTERLIS-Transformationen, die generische Engine ist aber bereits so gebaut, dass sie nicht fachlich an einzelne Modelle gebunden sein soll. Das wichtigste Architekturprinzip bleibt:

> Die Engine bleibt generisch, modellbewusst und formatneutral. Fachliche Speziallogik und formatbezogene I/O-Logik dürfen nicht in die generischen Laufzeitpfade einsickern.

Der heutige Transformationsablauf ist grob:

1. Mapping-Datei laden (`YAML` oder `.ilimap`).
2. INTERLIS-Modelle kompilieren (`TransferDescription`).
3. `TransformPlan` aus `JobConfig` erzeugen.
4. Pro Input einen `IoxReader` öffnen.
5. Pro Output einen `IoxWriter` öffnen.
6. Quellen indexieren (`SourceIndexingService`).
7. Rules ausführen (`TransformationEngine`).
8. Referenzen/Bags/Joins auflösen.
9. Outputs schreiben.
10. Optional Ausgaben validieren.
11. Transactional commit oder rollback.

Die zentrale Chance: Die Engine arbeitet intern bereits mit `IoxEvent`, `ObjectEvent` und `IomObject`. Dadurch können auch Nicht-INTERLIS-Quellen verwendet werden, wenn sie als IOX-Eventstrom bereitgestellt werden.

Das Ziel dieser Spezifikation ist ein phasenweiser Umbau:

- Zuerst eine saubere I/O-Format-Abstraktion einführen, ohne Verhalten zu ändern.
- Danach CSV als sehr risikoarmes erstes Zusatzformat aktivieren.
- Danach GeoPackage als starkes Showcase-Format integrieren.
- Danach Shapefile ergänzen.
- Danach eine generische JDBC-Quelle einführen.
- Jede Phase muss ein funktionierendes, getestetes Artefakt liefern.

**Wichtig:** In dieser Spezifikation wird kein anderes ETL-Werkzeug erwähnt oder vorausgesetzt. `ilitransformer` soll als eigenständiges Werkzeug funktionieren.

---

## 2. Relevante bestehende Projektstruktur

Vor der Umsetzung muss der Agent mindestens folgende Dateien lesen und verstehen:

```text
README.md
build.gradle
settings.gradle                       # falls vorhanden
docs/cli.md
docs/mapping-dsl.md
docs/ilimap-v2.md
docs/testing.md
src/main/java/guru/interlis/transformer/app/CliMain.java
src/main/java/guru/interlis/transformer/app/JobRunner.java
src/main/java/guru/interlis/transformer/interlis/InterlisIoFactory.java
src/main/java/guru/interlis/transformer/mapping/model/JobConfig.java
src/main/java/guru/interlis/transformer/mapping/plan/InputBinding.java
src/main/java/guru/interlis/transformer/mapping/plan/OutputBinding.java
src/main/java/guru/interlis/transformer/mapping/plan/TransferFormat.java
src/main/java/guru/interlis/transformer/model/ModelRegistry.java
src/main/java/guru/interlis/transformer/engine/TransformationEngine.java
src/main/java/guru/interlis/transformer/engine/SourceIndexingService.java
src/main/java/guru/interlis/transformer/diag/DiagnosticCode.java
```

Falls im Repository oder in der Arbeitsumgebung agentenspezifische Dateien vorhanden sind, müssen sie ebenfalls zuerst gelesen und befolgt werden:

```text
AGENTS.md
CLAUDE.md
CODEX.md
.skills/
skills/
SKILL.md
```

Wenn mehrere Anweisungsquellen existieren, gilt diese Priorität:

1. Sicherheits- und Systemanweisungen der Ausführungsumgebung.
2. Repository-spezifische Agentenanweisungen (`AGENTS.md`, `SKILL.md`, ähnliche Dateien).
3. Diese Spezifikation.
4. Bestehende README-/Docs-Konventionen.
5. Lokale Stilentscheidungen aus dem bestehenden Code.

---

## 3. Relevante externe Codebasis

Die gewünschten Formate liegen im Umfeld der IOX-/INTERLIS-Bibliotheken:

```text
https://github.com/claeis/iox-ili
https://github.com/claeis/iox-wkf
```

### 3.1 iox-ili

`iox-ili` stellt die Basis-IOX-/IOM-Infrastruktur und INTERLIS-Reader/Writer bereit. Für dieses Vorhaben besonders relevant:

```text
ch.interlis.iom_j.xtf.Xtf24Reader
ch.interlis.iom_j.xtf.XtfWriter
ch.interlis.iom_j.itf.ItfReader2
ch.interlis.iom_j.itf.ItfWriter
ch.interlis.iom_j.csv.CsvReader
ch.interlis.iom_j.csv.CsvWriter
ch.interlis.iox_j.utility.ReaderFactory
```

Der `CsvReader` kann mit einem gesetzten `TransferDescription` arbeiten. Er matched Header-Attribute gegen Modellattribute oder sucht ohne Header anhand der Attributanzahl eine passende Klasse. Das ist für einen ersten Zusatzformat-Smoke-Test ideal, weil `iox-ili` bereits im Projekt verwendet wird.

### 3.2 iox-wkf

`iox-wkf` enthält weitere Reader/Writer für verbreitete Work-Kopy-/GIS-Formate. Für dieses Vorhaben relevant:

```text
ch.interlis.ioxwkf.shp.ShapeReader
ch.interlis.ioxwkf.shp.ShapeWriter
ch.interlis.ioxwkf.gpkg.GeoPackageReader
ch.interlis.ioxwkf.gpkg.GeoPackageWriter
ch.interlis.ioxwkf.json.JsonReader
ch.interlis.ioxwkf.json.JsonWriter
ch.interlis.ioxwkf.json.GeoJsonReader
ch.interlis.ioxwkf.json.GeoJsonWriter
ch.interlis.ioxwkf.dbtools.IoxWkfConfig
```

Wichtige Einschränkungen:

- Shapefile und GeoPackage sind Simple-Feature-/Tabellenformate.
- Sie können INTERLIS-Strukturen, Referenzen, Associations und komplexe Modellsemantik nicht vollständig direkt ausdrücken.
- Das ist akzeptabel, weil `ilitransformer` aus flachen Quellen komplexere Zielobjekte erzeugen kann, wenn das Mapping die nötigen Informationen liefert.
- Nicht-INTERLIS-Quellen sollten grundsätzlich gegen ein explizites Source-ILI gelesen werden, damit `IomObject`-Tags und Attributnamen modellbewusst sind.

---

## 4. Architekturprinzipien

### 4.1 Keine Formatlogik in der Transformationsengine

`TransformationEngine`, `SourceIndexingService`, `RuleExecutionService`, `OutputWritingService` und die Mapping-Compiler dürfen keine spezifische Logik für CSV, GPKG, SHP oder JDBC enthalten. Sie arbeiten weiter nur mit:

```java
IoxReader
IoxWriter
IoxEvent
ObjectEvent
IomObject
TransformPlan
InputBinding
OutputBinding
```

### 4.2 Formatprovider statt Dateiendungslogik

Die heutige `InterlisIoFactory` entscheidet anhand von Dateiendungen. Das reicht nicht mehr, weil:

- CSV Optionen braucht (`separator`, `encoding`, `firstLineIsHeader`).
- GeoPackage eine Tabellenangabe braucht (`table`).
- JDBC gar keinen Dateipfad hat.
- Shapefile Encoding und Sidecar-Dateien braucht.
- Später Writer-Formate andere Einschränkungen haben.

Stattdessen wird eine Formatprovider-Architektur eingeführt.

### 4.3 Source-ILI ist Pflicht für Showcase-Qualität

Für Nicht-INTERLIS-Inputs gilt:

- Der Input muss ein `model` angeben.
- Dieses Modell beschreibt die flache Quellstruktur.
- Die Reader erhalten die `TransferDescription` via `setModel(...)`, wenn sie `IoxIliReader` implementieren.
- Der Output bleibt ein normales INTERLIS-Modell.
- `--validate` validiert die erzeugte XTF-/ITF-Ausgabe.

### 4.4 Formatoptionen sind Teil des Mapping-Profils

Formatoptionen gehören in die `input`-/`output`-Definition. Sie dürfen nicht als globale CLI-Flags verstreut werden.

YAML-Zielbild:

```yaml
version: 1

job:
  name: csv-to-xtf-demo
  modeldir:
    - models
  inputs:
    - id: source
      path: input/municipalities.csv
      model: DemoCsvSource
      format: csv
      options:
        firstLineIsHeader: "true"
        separator: ";"
        encoding: UTF-8
  outputs:
    - id: target
      path: build/out/municipalities.xtf
      model: DemoTarget
      format: xtf

mapping:
  oidStrategy:
    default: deterministicUuid
    namespace: csv-to-xtf-demo
  basketStrategy:
    default: byTopic
  rules:
    - id: municipality
      target:
        output: target
        class: DemoTarget.Catalog.Municipality
      sources:
        - alias: src
          input: source
          class: DemoCsvSource.Data.Municipality
      identity:
        sourceKey: ["src.bfsnr"]
      assign:
        BfsNr: src.bfsnr
        Name: src.name
```

`.ilimap`-Zielbild:

```ilimap
mapping v2 "csv-to-xtf-demo" {
  job {
    modeldir "models";
    failPolicy strict;
  }

  input source {
    path "input/municipalities.csv";
    model "DemoCsvSource";
    format csv;
    option firstLineIsHeader "true";
    option separator ";";
    option encoding "UTF-8";
  }

  output target {
    path "build/out/municipalities.xtf";
    model "DemoTarget";
    format xtf;
  }

  oid deterministicUuid {
    namespace "csv-to-xtf-demo";
  }

  basket byTopic;

  rule municipality {
    target target class "DemoTarget.Catalog.Municipality";
    source src from source class "DemoCsvSource.Data.Municipality";
    identity src.bfsnr;
    assign {
      BfsNr = src.bfsnr;
      Name = src.name;
    }
  }
}
```

---

## 5. Zielarchitektur auf Klassenebene

### 5.1 Neues Package `guru.interlis.transformer.io`

Neue Klassen und Interfaces:

```text
src/main/java/guru/interlis/transformer/io/IoxFormatProvider.java
src/main/java/guru/interlis/transformer/io/IoxFormatRegistry.java
src/main/java/guru/interlis/transformer/io/FormatOpenContext.java
src/main/java/guru/interlis/transformer/io/FormatCapabilities.java
src/main/java/guru/interlis/transformer/io/FormatOptions.java
src/main/java/guru/interlis/transformer/io/EndTransferAwareReader.java
src/main/java/guru/interlis/transformer/io/BuiltInInterlisFormatProvider.java
src/main/java/guru/interlis/transformer/io/CsvFormatProvider.java
src/main/java/guru/interlis/transformer/io/WkfGeoPackageFormatProvider.java
src/main/java/guru/interlis/transformer/io/WkfShapeFormatProvider.java
src/main/java/guru/interlis/transformer/io/JdbcFormatProvider.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcIoxReader.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcGeometryConverter.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcDialect.java
src/main/java/guru/interlis/transformer/io/jdbc/DefaultJdbcDialect.java
src/main/java/guru/interlis/transformer/io/jdbc/PostgisJdbcDialect.java
src/main/java/guru/interlis/transformer/io/jdbc/DuckDbJdbcDialect.java
```

Die WKF- und JDBC-Klassen werden erst in späteren Phasen erstellt. Das Package kann aber von Anfang an so vorbereitet werden, dass die späteren Klassen logisch hineinpassen.

### 5.2 `IoxFormatProvider`

```java
package guru.interlis.transformer.io;

import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

public interface IoxFormatProvider {
    String id();

    FormatCapabilities capabilities();

    default boolean supportsInput(InputBinding binding) {
        return capabilities().canRead() && id().equalsIgnoreCase(binding.format());
    }

    default boolean supportsOutput(OutputBinding binding) {
        return capabilities().canWrite() && id().equalsIgnoreCase(binding.format());
    }

    IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception;

    IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) throws Exception;
}
```

Regeln:

- `id()` ist klein geschrieben (`xtf`, `itf`, `xml`, `csv`, `gpkg`, `shp`, `jdbc`).
- Provider dürfen Exceptions werfen; die Registry wandelt sie in gute Diagnostics um.
- Ein Provider darf für nicht unterstützte Richtungen `UnsupportedOperationException` werfen, aber besser ist eine klare Capability.

### 5.3 `FormatCapabilities`

```java
package guru.interlis.transformer.io;

public record FormatCapabilities(
        boolean canRead,
        boolean canWrite,
        boolean requiresPath,
        boolean requiresModel,
        boolean supportsOptions) {

    public static FormatCapabilities readWritePathModel() {
        return new FormatCapabilities(true, true, true, true, false);
    }

    public static FormatCapabilities readPathModelWithOptions() {
        return new FormatCapabilities(true, false, true, true, true);
    }
}
```

Später bei JDBC:

```java
public static FormatCapabilities readConnectionModelWithOptions() {
    return new FormatCapabilities(true, false, false, true, true);
}
```

### 5.4 `FormatOpenContext`

```java
package guru.interlis.transformer.io;

import guru.interlis.transformer.diag.DiagnosticCollector;
import ch.interlis.ili2c.metamodel.TransferDescription;
import java.nio.file.Path;

public record FormatOpenContext(
        Path baseDirectory,
        TransferDescription transferDescription,
        DiagnosticCollector diagnostics) {
}
```

Keine Optionen im Context speichern, weil Optionen in `InputBinding`/`OutputBinding` gehören.

### 5.5 `FormatOptions`

```java
package guru.interlis.transformer.io;

import java.util.Map;

public final class FormatOptions {
    private final Map<String, String> values;

    public FormatOptions(Map<String, String> values) { ... }

    public static FormatOptions of(Map<String, String> values) { ... }

    public String get(String key) { ... }

    public String getOrDefault(String key, String defaultValue) { ... }

    public boolean getBoolean(String key, boolean defaultValue) { ... }

    public int getInt(String key, int defaultValue) { ... }

    public char getChar(String key, char defaultValue) { ... }

    public String require(String key) { ... }

    public Map<String, String> asMap() { ... }
}
```

Validierungsregeln:

- Boolean akzeptiert `true`, `false`, `yes`, `no`, `1`, `0` case-insensitive.
- `getChar` akzeptiert genau ein Unicode-Zeichen oder die Escapes `tab`, `\t`, `semicolon`, `comma`.
- Fehler sollen als `IllegalArgumentException` mit präziser Meldung geworfen werden. Die Registry bzw. der Aufrufer erzeugt daraus eine Diagnostic.

### 5.6 `IoxFormatRegistry`

```java
package guru.interlis.transformer.io;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import java.util.*;

public final class IoxFormatRegistry {
    private final Map<String, IoxFormatProvider> providersById;

    public IoxFormatRegistry(List<IoxFormatProvider> providers) { ... }

    public static IoxFormatRegistry defaultRegistry() { ... }

    public Optional<IoxFormatProvider> find(String formatId) { ... }

    public IoxReader createReader(InputBinding binding, FormatOpenContext context) throws Exception { ... }

    public IoxWriter createWriter(OutputBinding binding, FormatOpenContext context) throws Exception { ... }
}
```

`defaultRegistry()` enthält anfangs nur:

```java
new BuiltInInterlisFormatProvider()
```

Später ergänzt:

```java
new CsvFormatProvider()
new WkfGeoPackageFormatProvider()
new WkfShapeFormatProvider()
new JdbcFormatProvider()
```

Die Registry soll bei unbekanntem Format eine gute Exception werfen:

```text
Unknown input format 'gpkg'. Available formats: itf, xtf, xml, csv
```

Die eigentliche Diagnostic-Erzeugung bleibt im `JobRunner`, damit bestehendes Fehlermanagement zentral bleibt.

### 5.7 `EndTransferAwareReader`

Der heutige private Wrapper in `InterlisIoFactory` wird in eine eigene Klasse verschoben:

```java
package guru.interlis.transformer.io;

public final class EndTransferAwareReader implements IoxReader {
    private final IoxReader delegate;
    private boolean endTransferSeen;

    public EndTransferAwareReader(IoxReader delegate) { ... }

    @Override
    public IoxEvent read() throws IoxException { ... }

    @Override
    public void close() throws IoxException { ... }

    @Override
    public void setFactory(IoxFactoryCollection factory) throws IoxException { ... }

    @Override
    public IoxFactoryCollection getFactory() throws IoxException { ... }

    @Override
    public IomObject createIomObject(String type, String oid) throws IoxException { ... }
}
```

Verhalten muss identisch bleiben:

- Nach `EndTransferEvent` liefert `read()` künftig `null`.
- Alle anderen Methoden delegieren.

### 5.8 `BuiltInInterlisFormatProvider`

Diese Klasse übernimmt die bisherige Logik aus `InterlisIoFactory`.

```java
package guru.interlis.transformer.io;

public final class BuiltInInterlisFormatProvider implements IoxFormatProvider {
    @Override
    public String id() { return "interlis"; }

    @Override
    public boolean supportsInput(InputBinding binding) { ... }

    @Override
    public boolean supportsOutput(OutputBinding binding) { ... }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception { ... }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) throws Exception { ... }
}
```

Wichtig: Obwohl `id()` optional `interlis` sein kann, muss `supportsInput` die konkreten Formate akzeptieren:

```text
itf
xtf
xml
```

Oder alternativ werden drei Provider gebaut:

```text
ItfFormatProvider
XtfFormatProvider
XmlFormatProvider
```

Die kompakte Variante `BuiltInInterlisFormatProvider` ist für den ersten Umbau einfacher.

---

## 6. Datenmodell-Anpassungen

### 6.1 `JobConfig.InputSpec`

Aktuell:

```java
public static final class InputSpec {
    public String id;
    public String path;
    public String model;
    public String format;
}
```

Neu:

```java
public static final class InputSpec {
    public String id;
    public String path;
    public String model;
    public String format;
    public Map<String, String> options;

    @JsonProperty("connection")
    public JdbcConnectionSpec connection;

    @JsonProperty("queries")
    public List<JdbcQuerySpec> queries;
}
```

`connection` und `queries` werden erst in der JDBC-Phase produktiv genutzt, können aber vorbereitet werden. Falls eine frühere Phase dadurch unnötig gross wird, dürfen diese Felder erst in Phase 6 ergänzt werden.

### 6.2 `JobConfig.OutputSpec`

Neu:

```java
public static final class OutputSpec {
    public String id;
    public String path;
    public String model;
    public String format;
    public Map<String, String> options;
}
```

### 6.3 `InputBinding` und `OutputBinding`

`TransferFormat` soll schrittweise durch einen String ersetzt werden. Zielzustand:

```java
public record InputBinding(
        String inputId,
        Path path,
        String declaredModelName,
        String format,
        Map<String, String> options,
        TransferDescription transferDescription,
        TypeSystemFacade typeSystem,
        JdbcConnectionSpec connection,
        List<JdbcQuerySpec> queries) {}
```

```java
public record OutputBinding(
        String outputId,
        Path path,
        String declaredModelName,
        String format,
        Map<String, String> options,
        TransferDescription transferDescription,
        TypeSystemFacade typeSystem) {}
```

Wenn der Umbau von `TransferFormat` auf `String` zu invasiv ist, darf der Agent eine Zwischenlösung verwenden:

```java
String formatId()
```

als zusätzliche Methode oder zusätzliches Record-Feld. Ziel ist aber: neue Formate dürfen nicht durch ein starres Enum blockiert werden.

### 6.4 Format-Ermittlung

Wenn `format` nicht gesetzt ist:

- `.xtf` -> `xtf`
- `.xml` -> `xml`
- `.itf` -> `itf`
- `.csv` -> `csv`
- `.gpkg` -> `gpkg`
- `.shp` -> `shp`

Bei `jdbc` muss `format` explizit gesetzt werden, weil es keinen Pfad geben muss.

Implementiere dafür:

```java
public final class FormatIdResolver {
    public static String resolveInputFormat(JobConfig.InputSpec input) { ... }
    public static String resolveOutputFormat(JobConfig.OutputSpec output) { ... }
    public static Optional<String> fromPath(Path path) { ... }
}
```

Paket:

```text
src/main/java/guru/interlis/transformer/io/FormatIdResolver.java
```

---

## 7. Diagnostics

Ergänze `DiagnosticCode` um mindestens:

```text
IO_FORMAT_UNKNOWN
IO_FORMAT_UNSUPPORTED_DIRECTION
IO_OPTION_INVALID
IO_READER_OPEN_FAILED
IO_WRITER_OPEN_FAILED
IO_DEPENDENCY_MISSING
IO_JDBC_CONNECTION_FAILED
IO_JDBC_QUERY_FAILED
IO_JDBC_MAPPING_INVALID
```

Meldungen müssen lösungsorientiert sein. Beispiele:

```text
[ERROR] IO_FORMAT_UNKNOWN: Unknown input format 'gpkg' for input 'source'. Available input formats: itf, xtf, xml, csv.
Suggestion: Add the format provider dependency or use one of the listed formats.
```

```text
[ERROR] IO_OPTION_INVALID: Invalid option 'separator' for input 'source': expected a single character, got '::'.
Suggestion: Use separator: ';' or separator: ','
```

```text
[ERROR] IO_JDBC_MAPPING_INVALID: JDBC input 'source' has no queries.
Suggestion: Add at least one query with class, topic and sql.
```

---

## 8. Testing-Strategie

Jede Phase muss mindestens ausführen:

```bash
./gradlew test
./gradlew integrationTest
./gradlew check
```

Falls `spotless` im Projekt aktiv ist:

```bash
./gradlew spotlessCheck
```

Wenn Format-Dependencies erst später hinzukommen und `check` dadurch länger wird, darf ein gezielterer Task ergänzt werden, aber `check` muss weiterhin grün bleiben.

### 8.1 Testkategorien

Unit-Tests:

```text
src/test/java/guru/interlis/transformer/io/*Test.java
```

Integrationstests:

```text
src/integrationTest/java/guru/interlis/transformer/formats/*IT.java
src/integrationTest/resources/examples/...
```

Beispiel-/Dokumentationstests:

- Alle neuen `examples/...` müssen per Integrationstest ausführbar sein.
- Wenn ein Beispiel in README oder Docs genannt wird, muss es existieren.
- Wenn `--validate` in der Doku gezeigt wird, muss der Integrationstest auch validieren.

### 8.2 Goldene Regel

Jede Phase liefert ein lauffähiges Artefakt:

- CLI startet.
- Bestehende ITF/XTF-Transformationen funktionieren weiter.
- Neue Tests sind grün.
- Die neue Funktion ist entweder produktiv nutzbar oder bewusst hinter einer dokumentierten Capability-Grenze.

---

## 9. Phasenplan

---

## Phase 0 — Baseline, Repo-Vertrag und Sicherheitsnetz

### Ziel

Vor dem Umbau wird das bestehende Verhalten festgehalten. Diese Phase ändert möglichst wenig Produktionscode, schafft aber ein Sicherheitsnetz für die I/O-Refaktorierung.

### Agentenaufgaben

1. Lies alle unter Abschnitt 2 genannten Dateien.
2. Prüfe, ob `AGENTS.md`, `SKILL.md` oder ähnliche Dateien vorhanden sind.
3. Führe aus:

   ```bash
   ./gradlew test
   ./gradlew integrationTest
   ./gradlew check
   ```

4. Dokumentiere im Commit oder in einer kurzen Notiz, welche Tests initial grün sind.
5. Ergänze Charakterisierungstests für die heutige I/O-Factory.

### Neue Tests

Erstelle:

```text
src/test/java/guru/interlis/transformer/interlis/InterlisIoFactoryCompatibilityTest.java
```

Tests:

```java
@Test
void createsXtfReaderForXtfFile()

@Test
void createsXtfReaderForXmlFile()

@Test
void createsItfReaderForItfFile()

@Test
void rejectsUnsupportedInputExtension()

@Test
void createsXtfWriterForXtfFile()

@Test
void createsItfWriterForItfFile()

@Test
void rejectsUnsupportedOutputExtension()
```

Wenn echte kleine Fixtures fehlen, erstelle minimal gültige Testdaten unter:

```text
src/test/resources/io/baseline/
```

Verwende vorhandene Test-Fixtures, wenn es bereits geeignete gibt. Keine grossen Echtdaten duplizieren.

### Akzeptanzkriterien

- Alle bestehenden Tests bleiben grün.
- Neue Charakterisierungstests sind grün.
- Keine neue Formatfunktion wird eingeführt.
- Kein bestehendes CLI-Verhalten ändert sich.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Lies danach README.md, build.gradle, docs/cli.md, docs/mapping-dsl.md, docs/ilimap-v2.md, docs/testing.md sowie alle vorhandenen AGENTS.md/SKILL.md-Dateien.
Setze Phase 0 um: Erzeuge nur ein Sicherheitsnetz für das bestehende I/O-Verhalten. Führe keine neue Formatarchitektur ein. Ergänze Charakterisierungstests für InterlisIoFactory und führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus. Beende die Phase nur, wenn alle Tests grün sind.
```

---

## Phase 1 — Formatprovider-Architektur ohne Verhaltensänderung

### Ziel

Die bestehende ITF/XTF/XML-I/O-Logik wird in eine Provider-/Registry-Architektur überführt. Das sichtbare Verhalten bleibt identisch.

### Neue Produktionsklassen

```text
src/main/java/guru/interlis/transformer/io/IoxFormatProvider.java
src/main/java/guru/interlis/transformer/io/IoxFormatRegistry.java
src/main/java/guru/interlis/transformer/io/FormatCapabilities.java
src/main/java/guru/interlis/transformer/io/FormatOpenContext.java
src/main/java/guru/interlis/transformer/io/EndTransferAwareReader.java
src/main/java/guru/interlis/transformer/io/BuiltInInterlisFormatProvider.java
src/main/java/guru/interlis/transformer/io/FormatIdResolver.java
```

### Änderungen an bestehendem Code

#### `InterlisIoFactory`

Option A, bevorzugt:

- Klasse als dünnen Backward-Compatible-Adapter behalten.
- Intern an `IoxFormatRegistry.defaultRegistry()` delegieren.
- Keine neue Logik mehr hier hinzufügen.

Option B:

- Klasse vollständig durch neue Registry ersetzen.
- Nur wählen, wenn keine Tests oder API-Nutzer brechen.

#### `JobRunner`

Aktuell öffnet `JobRunner` Reader/Writer über `InterlisIoFactory`. Neu:

```java
IoxFormatRegistry ioRegistry = IoxFormatRegistry.defaultRegistry();
FormatOpenContext context = new FormatOpenContext(prepared.baseDirectory(), binding.transferDescription(), engineDiag);
```

Beim Öffnen der Writer:

```java
writersByOutputId.put(outputId, ioRegistry.createWriter(tempBindingOrBinding, context));
```

Beim Öffnen der Reader:

```java
readerByInputId.put(inputId, ioRegistry.createReader(binding, context));
```

Achte darauf, dass bei Outputs der temporäre Pfad verwendet wird, nicht der finale Zielpfad.

#### `ModelRegistry`

Noch möglichst wenig ändern. Wenn `TransferFormat` bestehen bleibt, muss `FormatIdResolver` trotzdem bereits vorbereitet werden. Ziel dieser Phase ist nur, dass `xtf`, `xml`, `itf` weiter funktionieren.

### Tests

Erstelle:

```text
src/test/java/guru/interlis/transformer/io/IoxFormatRegistryTest.java
src/test/java/guru/interlis/transformer/io/BuiltInInterlisFormatProviderTest.java
src/test/java/guru/interlis/transformer/io/EndTransferAwareReaderTest.java
```

Testfälle:

```java
@Test
void defaultRegistryContainsInterlisProvider()

@Test
void resolvesXtfInputByExplicitFormat()

@Test
void resolvesXtfInputByPathExtension()

@Test
void rejectsUnknownFormatWithHelpfulMessage()

@Test
void endTransferAwareReaderReturnsNullAfterEndTransfer()
```

Zusätzlich:

- Alle bestehenden Integrationstests müssen unverändert funktionieren.
- Phase-0-Tests müssen weiterhin grün sein.

### Akzeptanzkriterien

- CLI-Verhalten unverändert.
- ITF/XTF/XML funktionieren weiterhin.
- `InterlisIoFactory` enthält keine neue Formatlogik mehr.
- Neue Providerarchitektur ist durch Tests abgedeckt.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 1 um. Ziel ist eine neue Formatprovider-Architektur ohne sichtbare Verhaltensänderung. Lies besonders JobRunner, InterlisIoFactory, InputBinding, OutputBinding und ModelRegistry. Erstelle IoxFormatProvider, IoxFormatRegistry, FormatCapabilities, FormatOpenContext, EndTransferAwareReader, BuiltInInterlisFormatProvider und FormatIdResolver. Ersetze die direkte Nutzung von InterlisIoFactory im JobRunner durch die Registry, ohne ITF/XTF/XML-Verhalten zu verändern. Ergänze Unit-Tests und führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus.
```

---

## Phase 2 — Formatoptionen in YAML und `.ilimap`

### Ziel

Inputs und Outputs erhalten generische Formatoptionen. Diese Phase aktiviert noch kein neues Format, schafft aber die Konfigurationsbasis für CSV, GPKG, SHP und JDBC.

### Änderungen an `JobConfig`

Ergänze:

```java
public Map<String, String> options = new LinkedHashMap<>();
```

in:

```text
JobConfig.InputSpec
JobConfig.OutputSpec
```

Wichtig:

- `options` darf nie `null` sein, nachdem ein Mapping geladen/normalisiert wurde.
- Bestehende YAML-Dateien ohne `options` bleiben kompatibel.

### Änderungen an `InputBinding` und `OutputBinding`

Ergänze ein Optionsfeld:

```java
Map<String, String> options
```

Wenn dadurch viele Konstruktoraufrufe brechen, passe sie bewusst und vollständig an. Keine Hilfskonstruktoren, die später zu Inkonsistenzen führen.

### Änderungen an `ModelRegistry.Builder`

Beim Bau von Bindings:

```java
Map<String, String> options = input.options != null ? Map.copyOf(input.options) : Map.of();
```

Analog für Outputs.

### YAML-Parsing

Jackson sollte `Map<String, String>` direkt lesen. Prüfe aber:

- Zahlenwerte in YAML können als Integer gelesen werden.
- Booleans können als Boolean gelesen werden.

Robuste Lösung: `Map<String, Object>` in `JobConfig` und Normalisierung auf String. Einfachere Lösung: `Map<String, String>` und Tests mit gequoteten Werten. Bevorzugt ist robuste Lösung:

```java
public Map<String, Object> options;
```

und in `JobConfigNormalizer` oder `ModelRegistry.Builder`:

```java
private static Map<String, String> normalizeOptions(Map<String, Object> raw) { ... }
```

Wenn `JobConfigNormalizer` bereits passende Aufgaben übernimmt, dort integrieren.

### `.ilimap`-Syntax

Erweitere `input`- und `output`-Blöcke um:

```ilimap
option key "value";
option firstLineIsHeader true;
option fetchSize 10000;
```

Alle Werte werden im `JobConfig` als String gespeichert:

```text
true      -> "true"
10000     -> "10000"
"UTF-8"   -> "UTF-8"
```

Zu ändernde Klassen sind im ilimap-Package zu suchen, insbesondere Parser, AST und Converter in:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/
```

Der Agent muss die tatsächlichen Klassennamen im Repo ermitteln. Typischerweise braucht es Änderungen an:

```text
IlimapLexer / Tokenizer
IlimapParser
IlimapAst oder entsprechende AST-Klassen
IlimapToJobConfigConverter
YamlToIlimapConverter
```

Wenn die Implementierung andere Klassennamen nutzt, passe die analogen Stellen an.

### `FormatOptions`

Implementiere `FormatOptions` aus Abschnitt 5.5.

Unit-Tests:

```text
src/test/java/guru/interlis/transformer/io/FormatOptionsTest.java
```

Testfälle:

```java
@Test
void readsStringOption()

@Test
void readsBooleanOptionVariants()

@Test
void rejectsInvalidBoolean()

@Test
void readsCharOptionFromSingleCharacter()

@Test
void readsCharOptionFromNamedEscape()

@Test
void rejectsMultiCharacterCharOption()

@Test
void readsIntegerOption()

@Test
void rejectsInvalidInteger()
```

### Dokumentation

Aktualisiere:

```text
docs/mapping-dsl.md
docs/ilimap-v2.md
```

Dokumentiere `options` als generischen Block, aber noch ohne CSV-spezifische Versprechen.

### Akzeptanzkriterien

- YAML-Mappings ohne `options` funktionieren unverändert.
- YAML-Mappings mit `options` werden geladen und in Bindings übertragen.
- `.ilimap`-Mappings mit `option` werden geladen und in `JobConfig` übertragen.
- `convert-mapping` erhält Optionen beim Konvertieren YAML -> `.ilimap`.
- Alle Tests grün.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 2 um. Ziel ist generische Formatoptionen-Unterstützung in YAML und .ilimap, ohne ein neues Format produktiv zu aktivieren. Lies JobConfig, JobConfigNormalizer, ModelRegistry, InputBinding, OutputBinding sowie die ilimap-Parser-/Converter-Klassen. Ergänze options für Inputs und Outputs, implementiere FormatOptions, erweitere YAML- und .ilimap-Verarbeitung inklusive convert-mapping. Aktualisiere docs/mapping-dsl.md und docs/ilimap-v2.md. Ergänze Unit- und Integrationstests. Führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus.
```

---

## Phase 3 — CSV als erstes Zusatzformat

### Ziel

CSV wird als erstes Nicht-INTERLIS-Inputformat unterstützt. Diese Phase ist bewusst klein, weil `CsvReader` bereits aus `iox-ili` verfügbar ist und keine neue schwere Dependency benötigt.

### Produktionsklasse

```text
src/main/java/guru/interlis/transformer/io/CsvFormatProvider.java
```

### Verhalten

Unterstützte Richtung:

```text
CSV input -> ITF/XTF/XML output
```

CSV output ist in dieser Phase nicht Ziel. Wenn `CsvWriter` später unterstützt werden soll, braucht es eine eigene Phase.

### Format-ID

```text
csv
```

### Optionen

```text
firstLineIsHeader: true|false       default: true
separator: single char              default: ,
delimiter: single char              default: "
encoding: string                    default: JVM/default or UTF-8, siehe Entscheidung unten
```

Empfehlung: Für reproduzierbare Builds `UTF-8` als Default setzen, auch wenn der `CsvReader` selbst sonst den JVM-default verwenden würde.

### Implementierung

```java
public final class CsvFormatProvider implements IoxFormatProvider {
    @Override
    public String id() { return "csv"; }

    @Override
    public FormatCapabilities capabilities() {
        return FormatCapabilities.readPathModelWithOptions();
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        FormatOptions options = FormatOptions.of(binding.options());
        Settings settings = new Settings();
        settings.setValue(CsvReader.ENCODING, options.getOrDefault("encoding", "UTF-8"));

        CsvReader reader = new CsvReader(binding.path().toFile(), settings);
        reader.setFirstLineIsHeader(options.getBoolean("firstLineIsHeader", true));
        reader.setValueSeparator(options.getChar("separator", ','));
        reader.setValueDelimiter(options.getChar("delimiter", '"'));
        reader.setModel(binding.transferDescription());

        return new EndTransferAwareReader(reader);
    }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) {
        throw new UnsupportedOperationException("CSV output is not supported yet");
    }
}
```

Achte auf Imports:

```java
import ch.ehi.basics.settings.Settings;
import ch.interlis.iom_j.csv.CsvReader;
```

### Registry

`IoxFormatRegistry.defaultRegistry()` ergänzt:

```java
new CsvFormatProvider()
```

### Beispiel

Erstelle:

```text
examples/csv-to-xtf/README.md
examples/csv-to-xtf/models/DemoCsvSource.ili
examples/csv-to-xtf/models/DemoTarget.ili
examples/csv-to-xtf/input/municipalities.csv
examples/csv-to-xtf/mapping.yaml
examples/csv-to-xtf/mapping.ilimap
```

Minimales CSV:

```csv
bfsnr;name;population
2601;Solothurn;17000
2610;Olten;19000
```

Source-ILI:

```ili
INTERLIS 2.4;

MODEL DemoCsvSource AT "https://example.org/ilitransformer" VERSION "2026-06-24" =
  TOPIC Data =
    CLASS Municipality =
      bfsnr : 0 .. 9999;
      name : TEXT*80;
      population : 0 .. 999999;
    END Municipality;
  END Data;
END DemoCsvSource.
```

Target-ILI:

```ili
INTERLIS 2.4;

MODEL DemoTarget AT "https://example.org/ilitransformer" VERSION "2026-06-24" =
  TOPIC Catalog =
    CLASS Municipality =
      BfsNr : 0 .. 9999;
      Name : TEXT*80;
      Population : 0 .. 999999;
    END Municipality;
  END Catalog;
END DemoTarget.
```

Mapping soll `deterministicUuid` oder `integer` verwenden. Für einfache Validierung ist `integer` akzeptabel, für reproduzierbare Demos ist `deterministicUuid` besser, sofern das Zielmodell UUID-OIDs akzeptiert. Falls das Zielmodell keine UUID-OIDs definiert, nutze `integer`.

### Tests

Unit:

```text
src/test/java/guru/interlis/transformer/io/CsvFormatProviderTest.java
```

Integration:

```text
src/integrationTest/java/guru/interlis/transformer/formats/CsvToXtfIntegrationTest.java
```

Testfälle:

```java
@Test
void transformsCsvToValidXtfUsingYamlMapping()

@Test
void transformsCsvToValidXtfUsingIlimapMapping()

@Test
void failsWithHelpfulDiagnosticForInvalidSeparatorOption()
```

Integrationstest muss:

1. Beispiel in temporäres Verzeichnis kopieren.
2. CLI `transform -m mapping.yaml --modeldir models --validate --report build/report` ausführen.
3. Exit-Code 0 erwarten.
4. Output-XTF existiert.
5. Validation ok.
6. Report existiert.
7. Output enthält zwei Objekte.

### Dokumentation

Aktualisiere:

```text
README.md
docs/cli.md
docs/mapping-dsl.md
docs/ilimap-v2.md
```

Dokumentiere CSV als bewusst flaches Inputformat.

### Akzeptanzkriterien

- CSV -> XTF funktioniert über YAML.
- CSV -> XTF funktioniert über `.ilimap`.
- Output kann mit `--validate` validiert werden.
- Fehlerhafte Optionen erzeugen klare Diagnostik.
- Bestehende ITF/XTF/XML-Tests bleiben grün.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 3 um. Ziel ist CSV als erstes neues Inputformat. Lies CsvReader in iox-ili und die neue Formatprovider-Architektur aus Phase 1/2. Implementiere CsvFormatProvider, registriere ihn, unterstütze Optionen firstLineIsHeader, separator, delimiter und encoding. Erstelle ein vollständiges examples/csv-to-xtf mit Source-ILI, Target-ILI, CSV, YAML-Mapping und .ilimap-Mapping. Ergänze Unit- und Integrationstests inkl. --validate. Aktualisiere README.md, docs/cli.md, docs/mapping-dsl.md und docs/ilimap-v2.md. Führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus.
```

---

## Phase 4 — GeoPackage tabellarisch als Showcase-Format

### Ziel

GeoPackage wird als zweites Zusatzformat unterstützt. In dieser Phase reicht tabellarisches GeoPackage ohne Geometrie. Dadurch wird die WKF-Integration getestet, ohne sofort komplexe Geometrie-Fixtures zu benötigen.

### Dependency

Ergänze in `build.gradle` eine Dependency auf `iox-wkf`.

Bevorzugt:

```gradle
implementation('ch.interlis:iox-wkf:2.0.1') {
    exclude group: 'ch.interlis', module: 'iox-ili'
}
```

Falls die Version nicht auflösbar ist, prüfe die aktuell verfügbare Version über die im Projekt bereits verwendeten Repositories. Verwende keine beliebige neue Repository-Quelle ohne Begründung.

Achte besonders auf Konflikte mit:

```text
iox-ili
ili2c-core
ili2c-tool
GeoTools
JTS old package vs org.locationtech.jts
SQLite JDBC
```

### Produktionsklasse

```text
src/main/java/guru/interlis/transformer/io/WkfGeoPackageFormatProvider.java
```

### Format-ID

Akzeptiere beide IDs:

```text
gpkg
geopackage
```

Implementiere entweder:

- ein Provider mit `supportsInput`, der beide IDs akzeptiert, oder
- zwei Provider-Aliases.

### Optionen

```text
table: string          Pflicht
fetchSize: int         default: 10000
```

### Implementierung

```java
public final class WkfGeoPackageFormatProvider implements IoxFormatProvider {
    @Override
    public String id() { return "gpkg"; }

    @Override
    public boolean supportsInput(InputBinding binding) {
        return "gpkg".equalsIgnoreCase(binding.format())
            || "geopackage".equalsIgnoreCase(binding.format());
    }

    @Override
    public FormatCapabilities capabilities() {
        return FormatCapabilities.readPathModelWithOptions();
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        FormatOptions options = FormatOptions.of(binding.options());
        String table = options.require("table");
        int fetchSize = options.getInt("fetchSize", 10000);

        Settings settings = new Settings();
        settings.setValue(IoxWkfConfig.SETTING_GPKGTABLE, table);
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, table);
        settings.setValue(IoxWkfConfig.SETTING_FETCHSIZE, Integer.toString(fetchSize));

        GeoPackageReader reader = new GeoPackageReader(binding.path().toFile(), table, settings);
        reader.setModel(binding.transferDescription());
        return new EndTransferAwareReader(reader);
    }
}
```

Imports:

```java
import ch.ehi.basics.settings.Settings;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import ch.interlis.ioxwkf.gpkg.GeoPackageReader;
```

### Test-Fixture ohne Geometrie

Erzeuge das GeoPackage im Integrationstest programmatisch mit SQLite-JDBC. Minimal braucht der `GeoPackageReader`:

```sql
CREATE TABLE gpkg_contents (
  table_name TEXT NOT NULL PRIMARY KEY,
  data_type TEXT NOT NULL,
  identifier TEXT,
  description TEXT DEFAULT '',
  last_change DATETIME NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  min_x DOUBLE,
  min_y DOUBLE,
  max_x DOUBLE,
  max_y DOUBLE,
  srs_id INTEGER
);

CREATE TABLE gpkg_geometry_columns (
  table_name TEXT NOT NULL,
  column_name TEXT NOT NULL,
  geometry_type_name TEXT NOT NULL,
  srs_id INTEGER NOT NULL,
  z TINYINT NOT NULL,
  m TINYINT NOT NULL,
  PRIMARY KEY (table_name, column_name)
);

CREATE TABLE municipalities (
  bfsnr INTEGER,
  name TEXT,
  population INTEGER
);

INSERT INTO gpkg_contents(table_name, data_type, identifier, srs_id)
VALUES ('municipalities', 'attributes', 'municipalities', 2056);

INSERT INTO municipalities(bfsnr, name, population)
VALUES (2601, 'Solothurn', 17000), (2610, 'Olten', 19000);
```

### Beispiel

Erstelle:

```text
examples/gpkg-to-xtf/README.md
examples/gpkg-to-xtf/models/DemoGpkgSource.ili
examples/gpkg-to-xtf/models/DemoTarget.ili
examples/gpkg-to-xtf/mapping.yaml
examples/gpkg-to-xtf/mapping.ilimap
```

Binary `.gpkg` darf entweder:

- als kleines Fixture committed werden, wenn Repository-Konventionen das erlauben, oder
- durch ein kleines Java-Testutility im Integrationstest erzeugt werden.

Für die README ist ein Generator-Kommando hilfreich:

```bash
./gradlew createGpkgDemoData
```

Nur ergänzen, wenn es sauber in die bestehende Gradle-Struktur passt. Sonst im README beschreiben, dass die Datei im Beispielverzeichnis liegt.

### Tests

```text
src/test/java/guru/interlis/transformer/io/WkfGeoPackageFormatProviderTest.java
src/integrationTest/java/guru/interlis/transformer/formats/GeoPackageToXtfIntegrationTest.java
```

Testfälle:

```java
@Test
void requiresTableOption()

@Test
void transformsTabularGeoPackageToValidXtfUsingYamlMapping()

@Test
void transformsTabularGeoPackageToValidXtfUsingIlimapMapping()
```

### Akzeptanzkriterien

- `gpkg` Input funktioniert mit tabellarischer GeoPackage-Tabelle.
- `table` Option ist Pflicht und wird validiert.
- Integrationstest erzeugt valide XTF-Ausgabe.
- CSV und bestehende INTERLIS-Formate funktionieren weiterhin.
- Dependency-Konflikte sind gelöst.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 4 um. Ziel ist GeoPackage als tabellarisches Inputformat. Lies GeoPackageReader und IoxWkfConfig in iox-wkf sowie die bestehende Formatprovider-Architektur. Ergänze die iox-wkf-Dependency kontrolliert, implementiere WkfGeoPackageFormatProvider mit den Optionen table und fetchSize, registriere den Provider und erstelle ein examples/gpkg-to-xtf. Der Integrationstest soll ein minimales tabellarisches GeoPackage erzeugen oder ein sehr kleines Fixture verwenden und daraus eine valide XTF-Datei erzeugen. Aktualisiere die Dokumentation. Führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus.
```

---

## Phase 5 — GeoPackage mit Geometrie und Shapefile Input

### Ziel

Nach tabellarischem GeoPackage werden räumliche Simple-Feature-Quellen unterstützt:

- GeoPackage mit Geometriespalte.
- Shapefile als Inputformat.

### Produktionsklassen

```text
src/main/java/guru/interlis/transformer/io/WkfShapeFormatProvider.java
```

`WkfGeoPackageFormatProvider` wird um räumliche Tests und Dokumentation ergänzt, nicht zwingend um neue Produktionslogik, sofern der Reader Geometrien bereits korrekt liefert.

### Shapefile Format-ID

Akzeptiere:

```text
shp
shapefile
```

### Shapefile Optionen

```text
encoding: string       default: UTF-8
```

### Shapefile Implementierung

```java
public final class WkfShapeFormatProvider implements IoxFormatProvider {
    @Override
    public String id() { return "shp"; }

    @Override
    public boolean supportsInput(InputBinding binding) {
        return "shp".equalsIgnoreCase(binding.format())
            || "shapefile".equalsIgnoreCase(binding.format());
    }

    @Override
    public FormatCapabilities capabilities() {
        return FormatCapabilities.readPathModelWithOptions();
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        FormatOptions options = FormatOptions.of(binding.options());
        Settings settings = new Settings();
        settings.setValue(ShapeReader.ENCODING, options.getOrDefault("encoding", "UTF-8"));

        ShapeReader reader = new ShapeReader(binding.path().toFile(), settings);
        reader.setModel(binding.transferDescription());
        return new EndTransferAwareReader(reader);
    }
}
```

### Source-ILI für Geometrie

Für räumliche Demos braucht das Source-ILI eine einfache Geometrie. Verwende LV95-Koordinatenbereich.

Beispiel Punkt:

```ili
INTERLIS 2.4;

MODEL DemoSpatialSource AT "https://example.org/ilitransformer" VERSION "2026-06-24" =
  DOMAIN
    Coord2 = COORD 2460000.000 .. 2870000.000,
                   1045000.000 .. 1310000.000;

  TOPIC Data =
    CLASS Station =
      identifier : TEXT*40;
      name : TEXT*80;
      geom : Coord2;
    END Station;
  END Data;
END DemoSpatialSource.
```

Target-ILI kann identisch oder leicht anders sein.

### Fixture-Strategie

Bevorzugt: Testdaten programmatisch erzeugen, nicht grosse Binärdateien committen.

Option A für GeoPackage:

- Erzeuge ein kleines XTF mit Geometrie.
- Schreibe daraus mit `GeoPackageWriter` ein GeoPackage.
- Lies dieses GeoPackage mit `ilitransformer` und transformiere nach XTF.

Option B für Shapefile:

- Erzeuge mit `ShapeWriter` ein Shapefile aus IOM-Objekten.
- Lies es danach mit `WkfShapeFormatProvider`.

Option C:

- Committe sehr kleine Fixtures, wenn Option A/B zu aufwendig ist.
- Dokumentiere, wie die Fixtures erzeugt wurden.

### Tests

```text
src/integrationTest/java/guru/interlis/transformer/formats/GeoPackageSpatialToXtfIntegrationTest.java
src/integrationTest/java/guru/interlis/transformer/formats/ShapefileToXtfIntegrationTest.java
```

Testfälle:

```java
@Test
void transformsGeoPackagePointGeometryToValidXtf()

@Test
void transformsShapefilePointGeometryToValidXtf()

@Test
void rejectsShapefileWithoutMatchingSourceModelWithHelpfulDiagnostic()
```

### Dokumentation

Ergänze Format-Matrix:

```text
Format      Input   Output   Geometry   Structures   References   Notes
XTF         yes     yes      yes        yes          yes          Native INTERLIS
ITF         yes     yes      limited    model-dependent model-dependent INTERLIS 1
CSV         yes     no       no         no           no           Flat tables only
GPKG        yes     no       yes        no           no           Simple features / tables
SHP         yes     no       yes        no           no           One geometry attr, sidecar files
JDBC        planned no       planned    no           no           Queries as source classes
```

### Akzeptanzkriterien

- GeoPackage mit Punktgeometrie wird gelesen und in valides XTF transformiert.
- Shapefile mit Punktgeometrie wird gelesen und in valides XTF transformiert.
- Einschränkungen sind dokumentiert.
- Kein Engine-Code enthält SHP/GPKG-Sonderlogik.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 5 um. Ziel ist räumliches GeoPackage und Shapefile als Input. Lies ShapeReader, ShapeWriter, GeoPackageReader und GeoPackageWriter in iox-wkf. Implementiere WkfShapeFormatProvider, erweitere die GeoPackage-Tests um Geometrie und erstelle räumliche Demo-Modelle mit einfacher Punktgeometrie. Erzeuge kleine Testdaten programmatisch oder dokumentiert als kleine Fixtures. Aktualisiere die Format-Matrix in der Dokumentation. Führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus.
```

---

## Phase 6 — Generischer JDBC-Input, tabellarisch

### Ziel

Ein generischer JDBC-Input wird eingeführt. In dieser Phase ohne Geometrie. Jede Query entspricht einer flachen Source-Klasse.

### Konfigurationsmodell

Ergänze in `JobConfig`:

```java
public static final class JdbcConnectionSpec {
    public String driver;
    public String url;
    public String user;
    public String password;
    public String userEnv;
    public String passwordEnv;
    public Map<String, String> properties;
}

public static final class JdbcQuerySpec {
    public String id;

    @JsonProperty("class")
    @JsonAlias("clazz")
    public String clazz;

    public String topic;
    public String basketId;
    public String oidColumn;
    public String sql;
    public Map<String, String> columns;
}
```

In `InputSpec`:

```java
public JdbcConnectionSpec connection;
public List<JdbcQuerySpec> queries = new ArrayList<>();
```

YAML-Beispiel:

```yaml
job:
  inputs:
    - id: db
      format: jdbc
      model: DemoJdbcSource
      connection:
        driver: org.sqlite.JDBC
        url: jdbc:sqlite:build/demo.sqlite
      queries:
        - id: municipalities
          topic: DemoJdbcSource.Data
          class: DemoJdbcSource.Data.Municipality
          basketId: b1
          oidColumn: id
          sql: |
            select id, bfsnr, name, population
            from municipalities
```

`.ilimap`-Syntax für JDBC kann in dieser Phase minimal sein. Wenn die bestehende `.ilimap`-Grammatik für nested JDBC-Blöcke zu stark erweitert werden müsste, darf JDBC zunächst nur in YAML unterstützt werden. Dann muss die Dokumentation klar sagen:

```text
JDBC input is available in YAML mappings first. .ilimap support follows in a later phase.
```

Bevorzugt ist aber `.ilimap`-Support:

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

### Produktionsklassen

```text
src/main/java/guru/interlis/transformer/io/JdbcFormatProvider.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcIoxReader.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcQueryRuntime.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcValueMapper.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcResourceCloser.java
```

### `JdbcFormatProvider`

```java
public final class JdbcFormatProvider implements IoxFormatProvider {
    @Override
    public String id() { return "jdbc"; }

    @Override
    public FormatCapabilities capabilities() {
        return new FormatCapabilities(true, false, false, true, true);
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        validate(binding);
        return JdbcIoxReader.open(binding, context);
    }
}
```

### `JdbcIoxReader` Zustandsmaschine

Der Reader erzeugt IOX-Events:

```text
StartTransferEvent
StartBasketEvent for query 1
ObjectEvent row 1
ObjectEvent row 2
EndBasketEvent
StartBasketEvent for query 2
...
EndTransferEvent
null
```

Klassenstruktur:

```java
public final class JdbcIoxReader implements IoxReader {
    private enum State {
        START,
        BEFORE_QUERY,
        INSIDE_QUERY,
        END_BASKET,
        END_TRANSFER,
        END
    }

    private final InputBinding binding;
    private final TransferDescription td;
    private final Connection connection;
    private final List<JdbcQuerySpec> queries;
    private int queryIndex;
    private Statement currentStatement;
    private ResultSet currentResultSet;
    private ResultSetMetaData currentMetaData;
    private JdbcQuerySpec currentQuery;
    private State state;
    private IoxFactoryCollection factory;

    public static JdbcIoxReader open(InputBinding binding, FormatOpenContext context) throws Exception { ... }

    @Override
    public IoxEvent read() throws IoxException { ... }

    private StartBasketEvent startNextBasket() throws IoxException { ... }

    private ObjectEvent readNextObjectOrEndBasket() throws IoxException { ... }

    private IomObject mapCurrentRow() throws SQLException, IoxException { ... }

    private String resolveOid(ResultSet rs, JdbcQuerySpec query) throws SQLException { ... }

    private String resolveAttributeName(String columnLabel, JdbcQuerySpec query) { ... }

    @Override
    public void close() throws IoxException { ... }
}
```

### Wertmapping tabellarisch

`JdbcValueMapper`:

```java
public final class JdbcValueMapper {
    public void applyScalarValue(IomObject target, String attrName, Object value) { ... }

    public String toIoxScalar(Object value) { ... }
}
```

Regeln:

- `null` wird nicht gesetzt.
- `String` bleibt String.
- `Integer`, `Long`, `BigInteger` -> Dezimalstring ohne Gruppenzeichen.
- `BigDecimal`, `Double`, `Float` -> String mit invariantem Format; bei `BigDecimal` `toPlainString()`.
- `Boolean` -> `true`/`false`.
- `java.sql.Date` -> `yyyy-MM-dd`.
- `java.sql.Timestamp` / `OffsetDateTime` -> ISO-ähnlicher String, ohne lokale Seiteneffekte.
- `byte[]` in Phase 6 nicht unterstützen ausser als Base64 mit Option `blobEncoding: base64`. Default: Fehler oder Skip? Empfehlung: Fehler mit Diagnostic, wenn Spalte gemappt ist.

### JDBC Connection

`JdbcConnectionFactory` optional als eigene Klasse:

```text
src/main/java/guru/interlis/transformer/io/jdbc/JdbcConnectionFactory.java
```

Methoden:

```java
public Connection open(JobConfig.JdbcConnectionSpec spec) throws SQLException, ClassNotFoundException
private Properties buildProperties(JobConfig.JdbcConnectionSpec spec)
private String resolveEnv(String envName)
```

Regeln:

- `driver` optional, aber wenn gesetzt: `Class.forName(driver)`.
- `userEnv`/`passwordEnv` lesen aus `System.getenv`.
- Niemals Passwort in Diagnostics, Reports oder Logs schreiben.
- `properties` in JDBC-Properties übernehmen.

### Tests

Nutze SQLite für tabellarische Tests, weil es self-contained ist. Falls `sqlite-jdbc` durch `iox-wkf` bereits runtime verfügbar ist, kann es in Tests verwendet werden. Sonst als `testImplementation` ergänzen.

```text
src/test/java/guru/interlis/transformer/io/jdbc/JdbcValueMapperTest.java
src/test/java/guru/interlis/transformer/io/jdbc/JdbcConnectionFactoryTest.java
src/integrationTest/java/guru/interlis/transformer/formats/JdbcToXtfIntegrationTest.java
```

Testfälle:

```java
@Test
void mapsScalarJdbcRowsToIomObjects()

@Test
void usesOidColumnWhenConfigured()

@Test
void generatesSyntheticOidWhenNoOidColumnConfigured()

@Test
void transformsSqliteTableToValidXtf()

@Test
void failsIfJdbcInputHasNoQueries()

@Test
void doesNotLeakPasswordInDiagnostics()
```

### Akzeptanzkriterien

- `format: jdbc` funktioniert für flache Tabellen/Queries.
- Mehrere Queries in einem Input erzeugen mehrere Baskets oder klar definierte Basket-Gruppen.
- Output-XTF ist validierbar.
- Passwörter werden nie geloggt.
- Fehlerhafte SQL erzeugt klare Diagnostics.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 6 um. Ziel ist generischer JDBC-Input ohne Geometrie. Lies JobConfig, InputBinding, ModelRegistry, Formatprovider und SourceIndexingService. Ergänze JDBC-Konfigurationsklassen, implementiere JdbcFormatProvider, JdbcIoxReader, JdbcConnectionFactory und JdbcValueMapper. Verwende SQLite für Integrationstests. Unterstütze mehrere Queries, oidColumn, basketId, scalar type mapping und sichere Behandlung von Passwörtern. Aktualisiere die Dokumentation. Führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus.
```

---

## Phase 7 — JDBC mit Geometrie und Dialekt-Hooks

### Ziel

JDBC unterstützt räumliche Quellen über explizite Geometriespalten. Die SQL-Abfrage soll die Geometrie idealerweise bereits als WKT oder WKB liefern. Dadurch bleibt der Reader generisch.

### Konfigurationsmodell

Ergänze:

```java
public static final class JdbcGeometrySpec {
    public String attribute;
    public String column;
    public String encoding; // wkt | wkb | ewkb
    public String type;     // coord | polyline | surface, optional auto-detect via target/source model
    public Integer srid;
}
```

In `JdbcQuerySpec`:

```java
public List<JdbcGeometrySpec> geometry;
```

YAML-Beispiel:

```yaml
queries:
  - id: stations
    topic: DemoSpatialSource.Data
    class: DemoSpatialSource.Data.Station
    basketId: b1
    oidColumn: id
    sql: |
      select id, identifier, name, ST_AsBinary(geom) as geom_wkb
      from stations
    geometry:
      - attribute: geom
        column: geom_wkb
        encoding: wkb
        type: coord
        srid: 2056
```

### Produktionsklassen

```text
src/main/java/guru/interlis/transformer/io/jdbc/JdbcGeometryConverter.java
src/main/java/guru/interlis/transformer/io/jdbc/JdbcDialect.java
src/main/java/guru/interlis/transformer/io/jdbc/DefaultJdbcDialect.java
src/main/java/guru/interlis/transformer/io/jdbc/PostgisJdbcDialect.java
src/main/java/guru/interlis/transformer/io/jdbc/DuckDbJdbcDialect.java
```

### `JdbcGeometryConverter`

Methoden:

```java
public IomObject fromWkt(String wkt, AttributeDef attrDef) throws IoxException

public IomObject fromWkb(byte[] wkb, AttributeDef attrDef) throws IoxException

private IomObject pointToCoord(Geometry geometry) throws IoxException

private IomObject lineToPolyline(Geometry geometry, AttributeDef attrDef) throws IoxException

private IomObject polygonToSurface(Geometry geometry, AttributeDef attrDef) throws IoxException
```

Verwende bestehende IOX/JTS-Konverter, wenn möglich:

```text
ch.interlis.iox_j.jts.Jts2iox
ch.interlis.iox_j.jts.Iox2jts
```

Achte auf alte JTS-Package-Namen (`com.vividsolutions.jts`) versus neue (`org.locationtech.jts`). Im Projekt existieren bereits Brücken in `iox-wkf`, z.B. `JtsPackageConverter`. Verwende vorhandene Konverter statt neue fragile Konvertierungen zu schreiben.

### Dialekte

Dialekte sind in Phase 7 klein zu halten. Sie sollen nicht SQL generieren, sondern nur Default-Annahmen kapseln.

```java
public interface JdbcDialect {
    String id();

    default void configureConnection(Connection connection) throws SQLException {}

    default boolean supportsEncoding(String encoding) { ... }
}
```

`DefaultJdbcDialect` reicht für `wkt` und `wkb`.

`PostgisJdbcDialect` dokumentiert empfohlene SQL:

```sql
ST_AsBinary(geom) as geom_wkb
ST_AsText(geom) as geom_wkt
```

`DuckDbJdbcDialect` dokumentiert empfohlene SQL je nach verfügbarer Spatial Extension. Der Reader soll aber nicht selbst Extensions laden.

### Tests

Integrationstest mit WKT ist am einfachsten:

```sql
CREATE TABLE stations (
  id TEXT PRIMARY KEY,
  identifier TEXT,
  name TEXT,
  geom_wkt TEXT
);

INSERT INTO stations VALUES (
  's1', 'SOLOTHURN', 'Solothurn', 'POINT (2607600 1228500)'
);
```

Test:

```java
@Test
void transformsJdbcWktPointToValidXtf()
```

Falls WKB robust implementiert ist:

```java
@Test
void transformsJdbcWkbPointToValidXtf()
```

### Akzeptanzkriterien

- JDBC-WKT Punkt wird in `COORD` transformiert.
- Mindestens ein validiertes XTF mit Geometrie entsteht.
- Geometrie-Konfiguration ist explizit und dokumentiert.
- Kein DB-spezifischer SQL-Generator ist nötig.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 7 um. Ziel ist JDBC-Geometrie über explizite WKT/WKB-Spalten und kleine Dialekt-Hooks. Lies bestehende Geometrieadapter und IOX/JTS-Konverter im Projekt sowie die Konverter in iox-wkf. Ergänze JdbcGeometrySpec, JdbcGeometryConverter und Dialekt-Klassen. Implementiere zuerst WKT Point -> INTERLIS COORD und teste es mit SQLite. WKB kann ergänzt werden, wenn es robust und getestet ist. Aktualisiere die Dokumentation mit SQL-Beispielen. Führe ./gradlew test, ./gradlew integrationTest und ./gradlew check aus.
```

---

## Phase 8 — Dokumentation, Demo-Matrix und Release-Artefakt

### Ziel

Die neuen Formate werden sauber dokumentiert, Beispiele sind konsistent, und die Distribution enthält alles Nötige für den Showcase.

### Dokumentation

Aktualisiere oder erstelle:

```text
docs/formats.md
docs/cli.md
docs/mapping-dsl.md
docs/ilimap-v2.md
README.md
```

`docs/formats.md` soll enthalten:

1. Überblick Formatprovider-Architektur.
2. Format-Matrix.
3. CSV Input.
4. GeoPackage Input.
5. Shapefile Input.
6. JDBC Input.
7. Einschränkungen von Simple-Feature-Quellen.
8. Validierungsworkflow.
9. Troubleshooting.

### Beispiele

Alle Beispiele müssen lauffähig sein:

```text
examples/csv-to-xtf/
examples/gpkg-to-xtf/
examples/shp-to-xtf/
examples/jdbc-to-xtf/
examples/jdbc-spatial-to-xtf/
```

Jedes Beispiel enthält:

```text
README.md
models/*.ili
mapping.yaml
mapping.ilimap                 # falls unterstützt
input/...                      # kleine Eingabedaten oder Generator
expected/README.md             # optional: erklärt erwartete Ausgabe
```

### CLI-Doku

Zeige pro Format ein minimales Kommando:

```bash
./bin/ilitransformer transform -m examples/csv-to-xtf/mapping.yaml --validate --report build/reports/csv-demo
```

```bash
./bin/ilitransformer transform -m examples/gpkg-to-xtf/mapping.yaml --validate --report build/reports/gpkg-demo
```

```bash
./bin/ilitransformer transform -m examples/jdbc-to-xtf/mapping.yaml --validate --report build/reports/jdbc-demo
```

### Distribution

Prüfe `distributions` in `build.gradle`. Die Beispiele und neuen Docs müssen in `installDist`, `distZip`, `distTar` landen.

Ergänze falls nötig:

```gradle
from('docs/formats.md') { into 'docs' }
from('examples/csv-to-xtf') { into 'examples/csv-to-xtf' }
from('examples/gpkg-to-xtf') { into 'examples/gpkg-to-xtf' }
from('examples/shp-to-xtf') { into 'examples/shp-to-xtf' }
from('examples/jdbc-to-xtf') { into 'examples/jdbc-to-xtf' }
```

### Tests

Erstelle einen Beispiel-Vertragstest:

```text
src/integrationTest/java/guru/interlis/transformer/formats/ExamplesContractTest.java
```

Testfälle:

```java
@Test
void allDocumentedExampleMappingsExist()

@Test
void allYamlExamplesTransformSuccessfully()

@Test
void allIlimapExamplesTransformSuccessfullyWhereSupported()

@Test
void distributionContainsFormatDocsAndExamples()
```

### Akzeptanzkriterien

- `./gradlew installDist` erzeugt eine Distribution mit Docs und Beispielen.
- Alle dokumentierten Beispiele laufen.
- Format-Matrix ist korrekt und nicht überversprechend.
- README enthält einen kurzen, überzeugenden Showcase-Abschnitt.
- `./gradlew distZip distTar` funktioniert.

### Prompt für den Coding Agent

```text
Analysiere zuerst dieses Dokument vollständig: docs/agent/ilitransformer_format_support_agent_spec.md.
Setze Phase 8 um. Ziel ist Dokumentation, Beispiel-Matrix und Release-Artefakt. Lies README.md, docs/cli.md, docs/mapping-dsl.md, docs/ilimap-v2.md und build.gradle. Erstelle docs/formats.md, konsolidiere alle Beispiele, stelle sicher, dass installDist/distZip/distTar die neuen Docs und Beispiele enthalten, und ergänze Integrationstests, die alle dokumentierten Beispiele ausführen. Achte darauf, die Formatgrenzen ehrlich zu dokumentieren. Führe ./gradlew test, ./gradlew integrationTest, ./gradlew check und ./gradlew distZip distTar aus.
```

---

## 10. Qualitätsregeln für alle Phasen

### 10.1 Keine stillen Fallbacks

Wenn ein Format oder eine Option unbekannt ist, muss das sichtbar fehlschlagen. Keine still ignorierten Felder.

### 10.2 Keine Passwörter in Logs

JDBC-Passwörter dürfen nie erscheinen in:

```text
stdout
stderr
Diagnostics
Reports
Exception messages
Test snapshots
```

### 10.3 Keine Formatlogik in Expressions

Die Expression-Sprache bleibt unabhängig von CSV/GPKG/SHP/JDBC. Keine Funktionen wie `csvValue(...)` oder `jdbcGeom(...)` in dieser Umsetzung.

### 10.4 Kein Brechen bestehender Profile

Produktive bestehende Mappings müssen weiter funktionieren. Neue Felder sind optional und rückwärtskompatibel.

### 10.5 Kein Überversprechen bei Simple Features

Dokumentation muss klar sagen:

- CSV/GPKG/SHP/JDBC liefern flache Quellobjekte.
- Komplexe INTERLIS-Zielstrukturen entstehen durch Mappingregeln, nicht durch magische Formatkonvertierung.
- Referenzen und Strukturen müssen über Datenmodell, Query, Keys und Mapping explizit beschrieben werden.

### 10.6 Tests vor Refactoring-Abschluss

Eine Phase gilt erst als abgeschlossen, wenn:

```bash
./gradlew test
./gradlew integrationTest
./gradlew check
```

grün sind.

Wenn ein Test wegen externer Dependency instabil ist, muss der Agent die Ursache dokumentieren und eine deterministic Lösung wählen, z.B. kleine lokale Fixture statt Netzwerkzugriff.

---

## 11. Empfohlene Commit-Struktur

Pro Phase mindestens ein Commit, bei grossen Phasen mehrere kleine Commits:

```text
Phase 1:
  refactor(io): introduce format provider registry
  test(io): characterize built-in interlis formats

Phase 2:
  feat(mapping): support input and output format options
  feat(ilimap): parse format options in input/output blocks

Phase 3:
  feat(csv): add CSV input format provider
  docs(csv): add CSV to XTF example

Phase 4:
  feat(gpkg): add tabular GeoPackage input provider
  test(gpkg): add GeoPackage to XTF integration test

Phase 5:
  feat(shp): add Shapefile input provider
  test(spatial): validate simple point geometry transformations

Phase 6:
  feat(jdbc): add tabular JDBC input reader
  test(jdbc): add SQLite to XTF integration test

Phase 7:
  feat(jdbc): support explicit WKT/WKB geometry columns
  docs(jdbc): document spatial JDBC usage

Phase 8:
  docs(formats): add format support guide and matrix
  build(dist): include format examples in distribution
```

---

## 12. Offene Designentscheidungen

Diese Punkte soll der Agent nicht unüberlegt nebenbei entscheiden. Wenn eine Entscheidung nötig ist, im Commit/PR klar begründen.

### 12.1 `TransferFormat` entfernen oder behalten?

Ziel ist String-basierte Format-IDs. Ein starres Enum blockiert neue Formate. Wenn die Entfernung zu gross ist, darf temporär ein zusätzliches `formatId` verwendet werden. Spätestens vor Phase 4 sollte die starre Enum-Abhängigkeit aus dem I/O-Pfad entfernt sein.

### 12.2 `.ilimap` für JDBC sofort oder später?

YAML ist für verschachtelte JDBC-Konfiguration einfacher. `.ilimap` ist aber das bevorzugte Autorenformat. Empfehlung:

- Phase 6 darf YAML-only für JDBC sein, wenn die Grammatikänderung sonst zu gross wird.
- Phase 8 sollte dokumentieren, ob `.ilimap` für JDBC unterstützt ist.

### 12.3 GeoJSON jetzt oder später?

GeoJSON wird bewusst nicht als frühes Showcase-Format gewählt. Der vorhandene `GeoJsonReader` ist stark auf eine IOX-nahe GeoJSON-Struktur mit INTERLIS-Metadaten in `properties` ausgerichtet. Beliebiges Web-GeoJSON braucht zusätzliche Mapping-Konventionen. GeoJSON sollte später separat spezifiziert werden.

### 12.4 Writer für Nicht-INTERLIS-Formate?

Diese Spezifikation fokussiert Inputs. Writer für CSV/GPKG/SHP/GeoJSON sind möglich, aber nicht Ziel der ersten Showcase-Umsetzung. Grund: Das primäre Versprechen ist “aus verbreiteten Quellen valides INTERLIS-XTF erzeugen”.

### 12.5 Multi-Modul-Build?

Langfristig wäre ein separates Modul für schwere WKF-Dependencies sauber. Kurzfristig darf die bestehende Single-Module-Struktur beibehalten werden, wenn die Provider sauber isoliert sind. Wenn der Agent auf Multi-Modul umbaut, muss das in einer eigenen Phase passieren und alle Distributionstests müssen angepasst werden.

---

## 13. Definition of Done für das Gesamtvorhaben

Das Vorhaben ist abgeschlossen, wenn:

1. ITF/XTF/XML weiterhin funktionieren.
2. CSV Input funktioniert und validierte XTF-Ausgaben erzeugt.
3. GeoPackage Input funktioniert, mindestens tabellarisch und idealerweise mit einfacher Geometrie.
4. Shapefile Input funktioniert mit einfacher Punktgeometrie.
5. JDBC Input funktioniert tabellarisch.
6. JDBC Input unterstützt mindestens WKT-Punktgeometrie oder ist sauber für Phase 7 dokumentiert.
7. Alle neuen Formate über dieselbe Providerarchitektur integriert sind.
8. Keine Formatlogik in der Transformationsengine liegt.
9. YAML- und soweit sinnvoll `.ilimap`-Mappings unterstützt werden.
10. Alle Beispiele in der Distribution enthalten und getestet sind.
11. Die Dokumentation die Grenzen der Formate ehrlich beschreibt.
12. `./gradlew test`, `./gradlew integrationTest`, `./gradlew check`, `./gradlew distZip` und `./gradlew distTar` grün sind.

---

## 14. Kurzes Zielbild für README / Showcase

Nach Abschluss kann der Showcase sinngemäss so beschrieben werden:

```text
ilitransformer is not limited to INTERLIS transfer files as input. It can read flat or simple-feature sources through IOX readers, map them model-aware to an INTERLIS target model, and validate the resulting XTF. Supported inputs include XTF/ITF, CSV, GeoPackage, Shapefile and JDBC queries. Complex INTERLIS structures are created explicitly by mapping rules, keeping the engine deterministic and transparent.
```

Für die deutsche Doku:

```text
ilitransformer kann nicht nur INTERLIS-Transferdateien transformieren. Flache Tabellen und Simple-Feature-Quellen können über IOX-Reader gelesen, modellbewusst auf ein INTERLIS-Zielmodell gemappt und als valide XTF-Datei ausgegeben werden. Unterstützte Quellen sind XTF/ITF, CSV, GeoPackage, Shapefile und JDBC-Queries. Komplexe INTERLIS-Strukturen entstehen explizit durch Mappingregeln; dadurch bleibt die Transformation nachvollziehbar und validierbar.
```
