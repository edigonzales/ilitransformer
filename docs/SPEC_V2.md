> Historisches Arbeitsdokument. Nicht führend für den aktuellen Repo-Zustand.

# ilitransformer: Stabilisierung, Härtung und anschliessende DM01↔DMAV-Vervollständigung

## Verbindliche Spezifikation und Arbeitsauftrag für einen LLM-Coding-Agenten

**Projekt:** `edigonzales/ilinexus` / Produktname `ilitransformer`  
**Zielsprache:** Java 25  
**Build:** Gradle Groovy DSL  
**Primäre Plattform:** macOS auf Apple Silicon  
**Primärer fachlicher Referenzfall:** DM01 ↔ DMAV Version 1.1  
**Dokumentstatus:** verbindliche Implementierungsspezifikation  
**Grundsatz:** Zuerst vorhandene Engine-Funktionen reparieren, härten und realistisch testen; erst danach die fachliche DM01↔DMAV-Abdeckung systematisch erweitern.

---

## 1. Zweck dieses Dokuments

Dieses Dokument ist ein präziser Arbeitsauftrag für einen LLM-Coding-Agenten. Es soll nicht lediglich die gewünschte Zielarchitektur beschreiben, sondern die konkrete Weiterentwicklung des bestehenden Repositories steuern.

Der Agent muss:

1. den vorhandenen Code respektieren und gezielt weiterentwickeln;
2. keine parallele zweite Engine aufbauen;
3. die bestehenden Features zunächst stabilisieren;
4. vorhandene, aber nur teilweise umgesetzte DSL-Elemente entweder vollständig implementieren oder bis dahin ausdrücklich ablehnen;
5. jede Phase als abgeschlossenes, nutzbares und getestetes Artefakt liefern;
6. echte INTERLIS-Modelle und reale Transferdaten verwenden;
7. jedes selbst erstellte INTERLIS-Modell mit `ili2c` prüfen;
8. jede selbst erstellte oder transformierte Transferdatei mit `ilivalidator` prüfen;
9. erst nach einer belastbaren Engine-Stabilisierung die DM01↔DMAV-Transformation fachlich vervollständigen.

Dieses Dokument ersetzt keine fachliche Entscheidung, die nur ein Vermessungs- oder DMAV-Fachexperte treffen kann. Solche Punkte sind als offene Fragen zu dokumentieren und dürfen nicht durch erfundene Defaults verdeckt werden.

---

## 2. Ausgangslage

Das Repository enthält bereits eine fortgeschrittene Implementierung mit unter anderem:

- YAML-basierter Mapping-DSL;
- `JobConfig`;
- `MappingCompiler`;
- typisiertem `TransformPlan`;
- `TypeSystemFacade`;
- `IliModelService`;
- eigener Expression Engine mit AST;
- `Value`-Typsystem;
- OID- und Basket-Strategien;
- mehrphasiger Runtime;
- `StateStore`;
- Deferred-Reference-Auflösung;
- BAG-OF-STRUCTURE-Unterstützung;
- ITF- und XTF-I/O;
- Geometrieadapter;
- `ItfGeometryWriter`;
- Import der DM01/DMAV-Korrelationstabelle;
- Mapping-Kandidatengenerator;
- LFP3-orientierten Pilot-Mappings;
- Unit-, Integrations-, Golden- und Validator-Tests.

Der Code ist ein ernstzunehmendes MVP, enthält aber bekannte Inkonsistenzen und unvollständige Features. Dazu gehören insbesondere:

- Modell- und TypeSystem-Zuordnungen nach Modellname versus Input-/Output-ID;
- zu globale Referenzauflösung;
- nicht objektbezogene Prüfung obligatorischer Referenzen;
- kollisionsgefährdete deterministische UUIDs;
- unvollständige Semantik von `strict`, `lenient` und `reportOnly`;
- nur teilweise modellbewusste Expression-Kompilierung;
- vorhandene, aber nicht ausführbare DSL-Elemente wie `joins`, `create`, globale Defaults oder Rule-Level-Filter;
- teilweise synthetische DM01/DMAV-Tests statt echter End-to-End-Tests;
- CLI-Optionen, die aktuell keine Wirkung haben;
- Dokumentation, die an einzelnen Stellen mehr verspricht als der Code nachweist.

Diese Spezifikation ordnet die Behebung in eigenständige Phasen.

---

## 3. Verbindliche Zusatzinformationen

### 3.1 Vollständige reale Datensätze

Im Repository befindet sich der Ordner:

```text
./src/test/data/DMAV_Version_1_1
```

Darin liegen zwei vollständige Datensätze:

- ein DM01-Datensatz im ITF-Format;
- ein DMAV-Version-1.1-Datensatz im XTF-Format.

Der Coding-Agent darf die Dateinamen nicht erraten. Er muss den Ordner zu Beginn der entsprechenden Phase inventarisieren und die vorhandenen Dateien anhand von Dateiendung, Transferheader, Modellnamen, Dateigrösse und erfolgreicher Validierung klassifizieren.

Die vollständigen Datensätze sind für folgende Zwecke vorgesehen:

- Smoke Tests;
- Inventar- und Abdeckungsberichte;
- Performance-Messungen;
- fachliche Stichproben;
- Erkennung realer Reader-/Writer-/Referenz-/Geometrieprobleme;
- spätere End-to-End-Abnahme.

Sie dürfen nicht sofort als Grundlage eines vollständigen Golden-String-Vergleichs verwendet werden. Für deterministische, kleine Golden Tests sind aus den realen Datensätzen fachlich zusammenhängende, valide Teiltransfers abzuleiten.

### 3.2 INTERLIS 2.3 und 2.4

Für die Engine ist die konkrete XML-Ausprägung des XTF-Transferformats von INTERLIS 2.3 gegenüber INTERLIS 2.4 keine separate Fachlogik.

Verbindlicher Grundsatz:

> Die Engine arbeitet auf `IoxReader`, `IoxWriter`, `IoxEvent` und `IomObject`. Unterschiede der konkreten XTF-Serialisierung werden durch die bestehenden INTERLIS-Bibliotheken gekapselt.

Daraus folgen folgende Anforderungen:

- keine eigene XML-Parserlogik für XTF 2.3 oder XTF 2.4;
- keine Stringmanipulation an XTF;
- keine Versionsverzweigung in der Mapping Engine, sofern das Metamodell oder die Library diese Information nicht fachlich benötigt;
- Reader- und Writer-Auswahl ausschliesslich in der I/O-Abstraktionsschicht;
- Tests müssen semantische Objekte vergleichen, nicht XML-Whitespace oder Attributreihenfolgen;
- Ausgaben müssen mit dem zum Zielmodell passenden Writer erzeugt und mit `ilivalidator` geprüft werden.

### 3.3 Pflichtvalidierung

Selbst erzeugte INTERLIS-Modelle müssen mit `ili2c` kompiliert werden.

Selbst erzeugte oder transformierte ITF-/XTF-Dateien müssen mit `ilivalidator` geprüft werden.

Bevorzugte Abhängigkeiten:

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://jars.interlis.ch/")
    }
}

dependencies {
    implementation "ch.interlis:iox-ili:<festgelegte Version>"
    implementation "ch.interlis:ili2c-core:<festgelegte Version>"
    implementation "ch.interlis:ili2c-tool:<festgelegte Version>"
    testImplementation "ch.interlis:ilivalidator:1.15.0"
}
```

Lokale Fallbacks auf Stefans Entwicklungsrechner:

```text
/Users/stefan/apps/ili2c-5.6.8/ili2c.jar
/Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar
```

Die lokalen absoluten Pfade dürfen nicht fest in Java-Quellcode geschrieben werden und nicht als zwingende Buildvoraussetzung gelten. Sie dürfen nur über Gradle-Property, Umgebungsvariable oder einen gekapselten Test-Tool-Locator genutzt werden.

Empfohlene Properties:

```text
-Pili2cJar=/Users/stefan/apps/ili2c-5.6.8/ili2c.jar
-PilivalidatorJar=/Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar
```

oder:

```text
ILI2C_JAR
ILIVALIDATOR_JAR
```

Maven-Abhängigkeiten haben Vorrang. Der lokale Jar-Fallback dient nur der robusten lokalen Ausführung.

---

## 4. Nicht verhandelbare Entwicklungsgrundsätze

### 4.1 Jede Phase ist abgeschlossen und nutzbar

Jede Phase muss:

- kompilieren;
- alle bisherigen Tests weiterhin bestehen lassen;
- eigene Unit- und Integrationstests enthalten;
- mindestens einen Negativtest enthalten;
- Dokumentation aktualisieren;
- eine klar benannte Funktion liefern;
- keine absichtlich defekten Zwischenzustände hinterlassen.

Eine Phase gilt nicht als abgeschlossen, wenn:

- Tests deaktiviert wurden;
- Assertions durch Logging ersetzt wurden;
- Fehler nur in Kommentaren dokumentiert sind;
- eine CLI-Option existiert, aber keine Wirkung hat;
- ein Stub still ein anderes Verhalten ausführt;
- generierte Testdaten nicht validiert wurden;
- nur synthetische Mock-Reader statt echter Dateien getestet wurden, obwohl die Phase I/O betrifft.

### 4.2 Keine versteckten Fallbacks

Nicht implementierte Strategien müssen beim Mapping-Compile als Fehler abgelehnt oder ausdrücklich als experimentell aktiviert werden.

Unzulässig sind zum Beispiel:

- `external` OID gewählt, aber Integer-OID erzeugt;
- unbekannte Basket-Strategie gewählt, aber `preserve` verwendet;
- Join deklariert, aber ignoriert;
- Rule-Level-`where` angegeben, aber nicht ausgeführt;
- `--validate` angegeben, aber nicht verwendet.

### 4.3 Keine DM01/DMAV-Sonderlogik im Engine-Kern

DM01-/DMAV-spezifische Regeln gehören in Mapping-Profile, fachliche Funktionsbibliotheken, DM01/DMAV-spezifische Analyse- und Reportklassen, Tests und dokumentierte Adapter nur dann, wenn eine generische INTERLIS-Abstraktion nicht genügt.

Nicht in `TransformationEngine` gehören Bedingungen wie:

```java
if (modelName.contains("DM01")) { ... }
```

oder:

```java
if (targetClass.endsWith(".LFP3")) { ... }
```

Ausnahmen sind ausschliesslich generische INTERLIS-1-/INTERLIS-2-I/O-Eigenschaften.

### 4.4 Compiler und Runtime teilen dieselben kompilierten Artefakte

Expressions, Pfade, Rollen, Join-Bedingungen und Defaults dürfen nicht im Compiler nur grob analysiert und in der Runtime erneut unabhängig interpretiert werden.

Ziel:

```text
YAML
→ JobConfig
→ vollständig validierter TransformPlan
→ Runtime ohne erneutes Struktur-Raten
```

---

## 5. Zielarchitektur

Die bestehende Paketstruktur ist weiterzuverwenden. Neue Klassen sollen möglichst in folgende Bereiche eingeordnet werden:

```text
guru.interlis.transformer
├── app
├── cli
├── diag
├── dmav
├── engine
├── expr
├── geometry
├── interlis
├── mapping
│   ├── compiler
│   ├── model
│   └── plan
├── model
├── state
├── validation
└── testutil
```

### 5.1 Schichten

#### Konfigurationsschicht

Verantwortliche Klassen:

- `JobConfig`
- `MappingLoader`
- optionale Schema-/Migrationsklassen

Aufgabe: YAML laden und syntaktisch modellieren, aber keine Runtime-Logik enthalten.

#### Modellschicht

Verantwortliche Klassen:

- `IliModelService`
- `InterlisModelLoader`
- `TypeSystemFacade`
- `IliPath`
- `RoleResolver`
- neue Binding-Klassen

Aufgabe: Modelle kompilieren, Klassen, Attribute, Rollen, Domains, OIDs und Kardinalitäten auflösen und `TransferDescription` kapseln.

#### Compiler

Verantwortliche Klassen:

- `MappingCompiler`
- neue `ExpressionCompiler`
- neue `PlanValidator`
- neue Join-/Identity-/Reference-Compiler

Aufgabe: aus Konfiguration und Modellen einen vollständigen `TransformPlan` erzeugen, alle statisch erkennbaren Fehler melden und keine nur dekorativen DSL-Elemente durchlassen.

#### Runtime

Verantwortliche Klassen:

- `TransformationEngine`
- neue kleinere Services für Identität, Referenzen, Rule Dispatch, BAGs und Output
- `StateStore`

Aufgabe: den kompilierten Plan effizient ausführen, ohne Modellpfade neu zu erraten oder YAML-Strukturen auszuwerten.

#### I/O

Verantwortliche Klassen:

- `InterlisIoFactory`
- `ItfReader2`
- XTF-Reader der Library
- `ItfGeometryWriter`
- XTF-Writer der Library

Aufgabe: ITF/XTF abstrahieren, Transferevents bereitstellen und Transferdateien schreiben.

#### Validierung

Neue Schicht:

- `TransferValidationService`
- `ModelValidationService`
- `ValidationResult`
- `InterlisToolLocator`

Aufgabe: Modelle und Transfers reproduzierbar validieren, Maven-/In-Process-Nutzung bevorzugen, lokalen Jar-Fallback kapseln und Logs als Testartefakt verfügbar machen.

---

## 6. Verbindliche Verwendung der INTERLIS-Bibliotheken

### 6.1 `ili2c`

Zu verwenden sind insbesondere:

```java
ch.interlis.ili2c.Ili2c
ch.interlis.ili2c.Ili2cSettings
ch.interlis.ili2c.config.Configuration
ch.interlis.ili2c.config.FileEntry
ch.interlis.ili2c.config.FileEntryKind
ch.interlis.ili2c.metamodel.TransferDescription
```

Metamodellklassen:

```java
ch.interlis.ili2c.metamodel.Model
ch.interlis.ili2c.metamodel.Topic
ch.interlis.ili2c.metamodel.Table
ch.interlis.ili2c.metamodel.AttributeDef
ch.interlis.ili2c.metamodel.RoleDef
ch.interlis.ili2c.metamodel.AssociationDef
ch.interlis.ili2c.metamodel.Domain
ch.interlis.ili2c.metamodel.Type
ch.interlis.ili2c.metamodel.Cardinality
ch.interlis.ili2c.metamodel.CompositionType
ch.interlis.ili2c.metamodel.ReferenceType
ch.interlis.ili2c.metamodel.CoordType
ch.interlis.ili2c.metamodel.PolylineType
ch.interlis.ili2c.metamodel.SurfaceType
ch.interlis.ili2c.metamodel.AreaType
ch.interlis.ili2c.metamodel.EnumerationType
ch.interlis.ili2c.metamodel.NumericType
ch.interlis.ili2c.metamodel.TextType
```

Verbindliche Regeln:

- Kein Parsen von `.ili` mit regulären Ausdrücken.
- Alle Typinformationen stammen aus `TransferDescription`.
- Domain-Aliase müssen mit den von ili2c angebotenen Auflösungsmethoden dereferenziert werden.
- Geerbte Attribute und Rollen müssen berücksichtigt werden.
- Importierte Modelle müssen im `TransferDescription` korrekt aufgelöst werden.
- Compilerfehler müssen in `Diagnostic`-Objekte übertragen werden.

### 6.2 `iox-ili` und IOM

Zu verwenden sind:

```java
ch.interlis.iox.IoxReader
ch.interlis.iox.IoxWriter
ch.interlis.iox.IoxEvent
ch.interlis.iox.StartTransferEvent
ch.interlis.iox.StartBasketEvent
ch.interlis.iox.ObjectEvent
ch.interlis.iox.EndBasketEvent
ch.interlis.iox.EndTransferEvent

ch.interlis.iom.IomObject
ch.interlis.iom_j.Iom_jObject
```

Für ITF:

```java
ch.interlis.iom_j.itf.ItfReader2
ch.interlis.iom_j.itf.ItfWriter
```

Für XTF ist die bestehende, zur Library-Version passende Reader-/Writer-Abstraktion zu verwenden. Die konkrete Readerklasse darf in `InterlisIoFactory` gekapselt bleiben.

Verbindliche Regeln:

- Die Engine darf nur `IoxReader` und `IoxWriter` sehen.
- Keine direkte XML-Verarbeitung.
- `IomObject`-Geometrien dürfen nicht über `toString()` serialisiert werden.
- Objektattribute und Objektattribute mit Unterobjekten sind klar zu unterscheiden.
- Referenzen sind über `getobjectrefoid()` beziehungsweise entsprechende IOM-Referenzobjekte zu behandeln.
- Source-`IomObject`s dürfen nicht unkontrolliert als mutable Target-Objekte wiederverwendet werden.

### 6.3 `ilivalidator`

Zu verwenden ist bevorzugt die Java-API:

```java
org.interlis2.validator.Validator
ch.ehi.basics.settings.Settings
```

Es soll eine einzige Anwendungsklasse geben, welche alle Einstellungen kapselt.

Empfohlene Signatur:

```java
public interface TransferValidationService {
    ValidationResult validate(
        Path transferFile,
        List<String> modelDirectories,
        List<String> modelNames,
        Path logFile
    );
}
```

`ValidationResult` soll mindestens enthalten:

```java
public record ValidationResult(
    boolean valid,
    int errorCount,
    int warningCount,
    Path logFile,
    String logText
) {}
```

Der Agent darf nicht nur `boolean` zurückgeben, wenn strukturierte Informationen verfügbar sind.

---

## 7. Testdatenstrategie

### 7.1 Teststufen

Es sind folgende Teststufen verbindlich:

1. **Unit Test:** einzelne Klasse, keine Datei-I/O soweit nicht Testgegenstand.
2. **Component Test:** mehrere Klassen, kleine synthetische Modelle oder IOM-Objekte möglich.
3. **Transfer Integration Test:** echte ITF-/XTF-Datei, echter Reader und Writer, Output erneut einlesen.
4. **Model Integration Test:** offizielles oder reales INTERLIS-Modell, keine vereinfachte Nachbildung als Ersatz.
5. **Validator Test:** erzeugte Datei wird mit `ilivalidator` geprüft und das Resultat asserted.
6. **Real Dataset Smoke Test:** vollständiger Datensatz aus `src/test/data/DMAV_Version_1_1`, ohne vollständigen Golden-String-Vergleich.
7. **Golden Test:** kleiner, stabiler und validierter Teiltransfer mit semantischem Vergleich.

### 7.2 Kein selbst erzeugtes Modell ohne Compiler-Test

Jede unter `src/test/data/models` hinzugefügte `.ili`-Datei benötigt einen Test, der:

1. das Modell mit `IliModelService` beziehungsweise ili2c kompiliert;
2. `hasErrors() == false` asserted;
3. den erwarteten Modellnamen im `TransferDescription` findet.

### 7.3 Keine selbst erzeugte Transferdatei ohne Validator-Test

Jede dauerhaft eingecheckte `.itf`- oder `.xtf`-Testdatei muss bei Erstellung validiert worden sein, in einem automatischen Test erneut validiert werden können und eine dokumentierte Modell- und Modellverzeichniszuordnung besitzen.

### 7.4 Testmanifest für reale Datensätze

Neu anzulegen:

```java
package guru.interlis.transformer.testutil;

public record TransferDatasetDescriptor(
    String id,
    Path transferFile,
    TransferFormat format,
    List<String> declaredModels,
    List<String> modelDirectories,
    long sizeBytes
) {}
```

```java
public enum TransferFormat {
    ITF,
    XTF
}
```

```java
public final class RealDatasetCatalog {
    public static List<TransferDatasetDescriptor> scan(Path root);
    public static TransferDatasetDescriptor requireSingleItf(Path root);
    public static TransferDatasetDescriptor requireSingleXtf(Path root);
}
```

`RealDatasetCatalog` darf Dateinamen nicht hart codieren. Es muss rekursiv oder gezielt im Datenordner suchen, Nicht-Transferdateien ignorieren, Transferheader lesen, Modellnamen erfassen und mehrdeutige Ergebnisse als Testfehler melden.

---

# Teil A – Stabilisierung der bestehenden Engine

## Phase 16: Reproduzierbare Baseline, CI und ehrliche Feature-Matrix

### Ziel

Eine belastbare Ausgangsbasis schaffen. Der aktuelle Code muss reproduzierbar gebaut, getestet und dokumentiert werden können. Keine neue Transformationsfunktionalität.

### Klassen und Dateien

#### `build.gradle`

Umsetzen:

- getrennte Test-Suites oder SourceSets `test`, `integrationTest` und optional `realDataTest`;
- `check` hängt von `test` und `integrationTest` ab;
- `realDataTest` separat ausführbar;
- Testreports und Validatorlogs nach `build/reports`;
- Java 25 Toolchain;
- einheitliche Versionen;
- keine Compile-only-Abhängigkeit für Runtimecode;
- lokaler Jar-Fallback nur konfigurationsgesteuert.

Empfohlene Tasks:

```text
./gradlew clean check
./gradlew integrationTest
./gradlew realDataTest
./gradlew validateCheckedInTransfers
```

#### Neue Klasse `FeatureStatus`

```java
public enum FeatureStatus {
    SUPPORTED,
    EXPERIMENTAL,
    PARTIAL,
    CONFIG_ONLY,
    STUB,
    UNSUPPORTED
}
```

#### Neue Klasse `FeatureMatrix`

```java
public final class FeatureMatrix {
    public List<FeatureEntry> entries();
    public void writeMarkdown(Path output);
    public void writeJson(Path output);
}
```

Sie kann zunächst statisch gepflegt werden. Jede Aussage `SUPPORTED` benötigt mindestens einen referenzierten Test.

### Tests

```text
FeatureMatrixTest
BuildLayoutTest
CheckedInModelsCompileTest
CheckedInTransfersValidateTest
RealDatasetCatalogTest
```

`CheckedInTransfersValidateTest` darf für bewusst ungültige Negativdateien eine Allowlist verlangen. Ungültige Dateien müssen klar als negative Fixtures gekennzeichnet sein.

### Akzeptanzkriterien

- `./gradlew clean check` ist grün.
- CI ist eingerichtet.
- Kein Test loggt nur ein Ergebnis ohne Assertion.
- Alle Validator-Tests prüfen `valid`.
- Leere Test-SourceSets sind entfernt oder befüllt.
- Dokumentation behauptet keine unbewiesenen Features.

### Nicht Bestandteil

- keine neuen Joins;
- keine DM01/DMAV-Fachthemen;
- keine Performanceoptimierung.

---

## Phase 17: Modell-, Input-, Output- und TypeSystem-Bindings korrigieren

### Ziel

Compiler und Runtime müssen dieselben eindeutig adressierbaren Modellinformationen verwenden. Eine Output-ID darf nie versehentlich als Modellname interpretiert werden.

### Neue Plan-Klassen

#### `InputBinding`

```java
public record InputBinding(
    String inputId,
    Path path,
    String declaredModelName,
    TransferFormat format,
    TransferDescription transferDescription,
    TypeSystemFacade typeSystem
) {}
```

#### `OutputBinding`

```java
public record OutputBinding(
    String outputId,
    Path path,
    String declaredModelName,
    TransferFormat format,
    TransferDescription transferDescription,
    TypeSystemFacade typeSystem
) {}
```

#### `ModelRegistry`

```java
public final class ModelRegistry {
    public InputBinding requireInput(String inputId);
    public OutputBinding requireOutput(String outputId);
    public TypeSystemFacade requireSourceTypeSystem(String inputId);
    public TypeSystemFacade requireTargetTypeSystem(String outputId);
    public Optional<TransferDescription> findByModelName(String modelName);
}
```

Die Registry darf intern nach Modellname cachen. Die Runtime-Schnittstelle muss Input-/Output-IDs verwenden.

### `TransformPlan` ändern

```java
public record TransformPlan(
    String name,
    String direction,
    FailPolicy failPolicy,
    List<RulePlan> rules,
    Map<String, InputBinding> inputsById,
    Map<String, OutputBinding> outputsById,
    DiagnosticCollector diagnostics,
    OidPlan oidPlan,
    BasketPlan basketPlan,
    Map<String, Map<String, String>> enumMaps
) {}
```

`failPolicy` wird zu:

```java
public enum FailPolicy {
    STRICT,
    LENIENT,
    REPORT_ONLY
}
```

### `JobRunner` ändern

Verantwortung:

1. `JobConfig` laden;
2. Pfade relativ zur Mapping-Datei auflösen;
3. `job.modeldir` und CLI-Modelldirs zusammenführen;
4. Modelle genau einmal kompilieren;
5. `InputBinding` und `OutputBinding` bauen;
6. `MappingCompiler.compileTyped(...)` mit `ModelRegistry` aufrufen;
7. Runtime mit dem vollständigen Plan starten.

Empfohlene API:

```java
public PreparedJob prepare(Path mappingFile, RunOptions options);
```

```java
public record PreparedJob(
    JobConfig config,
    TransformPlan plan,
    ModelRegistry modelRegistry,
    Path baseDirectory
) {}
```

### `MappingCompiler` ändern

Neue Hauptsignatur:

```java
public TransformPlan compileTyped(
    JobConfig config,
    ModelRegistry modelRegistry
);
```

Alte Signaturen dürfen vorübergehend delegieren, sind aber `@Deprecated` zu markieren.

### `TransformationEngine` ändern

Target-TypeSystem:

```java
plan.outputsById()
    .get(rule.outputId())
    .typeSystem()
```

Source-TypeSystem:

```java
plan.inputsById()
    .get(inputId)
    .typeSystem()
```

Keine Suche nach dem ersten TypeSystem in einer Map.

### Tests

```text
ModelRegistryTest
TransformPlanBindingTest
JobRunnerRelativePathTest
MultipleInputsSameModelTest
MultipleOutputsDifferentModelsTest
OutputIdDifferentFromModelNameTest
InputIdDifferentFromModelNameTest
RoleResolverUsesCorrectOutputModelTest
```

Mindestens ein Test deckt ab:

```text
output id = dmav
model name = DMAV_FixpunkteAVKategorie3_V1_1
```

### Akzeptanzkriterien

- kein Runtimezugriff auf TypeSystems über einen Modellnamen, wenn eine Input-/Output-ID gemeint ist;
- kein `.values().stream().findFirst()` zur Modellwahl;
- zwei unterschiedliche Zielmodelle im selben Job funktionieren;
- bestehende Tests migriert und grün.

---

## Phase 18: Objektidentität, OID-Strategien und Duplicate Detection

### Ziel

Jedes Zielobjekt erhält eine korrekte, reproduzierbare und kollisionsfreie OID, die zum Zielmodell passt.

### Neue Klassen

#### `OidGenerationService`

```java
public interface OidGenerationService {
    String generate(OidGenerationRequest request);
}
```

#### `OidGenerationRequest`

```java
public record OidGenerationRequest(
    OidStrategy strategy,
    String namespace,
    String ruleId,
    String inputId,
    String sourceBasketId,
    String sourceClass,
    String sourceOid,
    LinkedHashMap<String, CanonicalValue> identityValues,
    String targetOidType
) {}
```

#### `CanonicalValue`

```java
public record CanonicalValue(
    String type,
    String canonicalText,
    boolean defined
) {}
```

#### `DefaultOidGenerationService`

Implementiert `INTEGER`, `PRESERVE`, `UUID` und `DETERMINISTIC_UUID`. `EXTERNAL` muss bis zu einer echten Implementation mit Compilerfehler abgelehnt werden.

#### `TargetObjectKey`

```java
public record TargetObjectKey(
    String outputId,
    String targetClass,
    String targetOid
) {}
```

### Deterministische UUID

Wenn Identity Keys vorhanden und alle definiert sind:

```text
namespace
ruleId
targetClass
ordered identity key names and canonical values
```

Falls keine Identity Keys vorhanden:

```text
namespace
ruleId
inputId
sourceBasketId
sourceClass
sourceOid
```

Falls auch `sourceOid` fehlt:

- strict: Fehler;
- lenient: kontrollierter, dokumentierter Fallback mit internem Sequenzschlüssel;
- niemals identische UUID für mehrere Objekte.

### `StateStore` erweitern

```java
void registerTarget(TargetObjectKey key, IomObject object);
boolean targetExists(TargetObjectKey key);
Optional<IomObject> findTarget(TargetObjectKey key);
```

`registerTarget` muss bei Duplikat eine definierte Exception oder Diagnostic erzeugen:

```java
public final class DuplicateTargetOidException extends RuntimeException
```

Kein stilles Überschreiben.

### `MappingCompiler` erweitern

Identity Keys prüfen:

- Alias existiert;
- Attribut existiert;
- Attribut ist als stabiler skalarer Schlüssel verwendbar;
- keine Geometrie oder BAG;
- nicht leer;
- keine Duplikate.

OID-Typ streng prüfen:

- Numeric OID ↔ Integer;
- UUIDOID ↔ UUID oder deterministic UUID;
- TextOID ↔ Preserve oder explizite Textstrategie;
- inkompatible Kombination als Fehler.

### Tests

```text
DeterministicOidTest
DeterministicOidFallbackTest
DuplicateTargetOidTest
IdentityKeyCompilerTest
OidTypeCompatibilityTest
PreserveOidTest
MissingSourceOidTest
OidStabilityGoldenTest
```

Pflichtfälle:

- zwei Objekte ohne Identity Keys;
- zwei Runs mit identischem Input;
- leere Identity-Werte;
- gleiche Source-OID in zwei Inputs;
- gleiche Source-OID in zwei Baskets;
- verschiedene Rules;
- UUIDOID-Ziel;
- Numeric-OID-Ziel.

### Akzeptanzkriterien

- keine OID-Kollision im Test;
- Duplicate Detection aktiv;
- `EXTERNAL` wird nicht still umgedeutet;
- OID-Generierung aus `TransformationEngine` ausgelagert;
- deterministische OIDs dokumentiert.

---

## Phase 19: Referenzen und Associations robust auflösen

### Ziel

Referenzen müssen auch bei mehreren Inputs, Baskets, Klassen und gleichen OIDs korrekt und typisiert aufgelöst werden.

### Neue Schlüsselklassen

#### `SourceObjectKey`

```java
public record SourceObjectKey(
    String inputId,
    String basketId,
    String sourceClass,
    String sourceOid
) {}
```

#### `TargetReference`

```java
public record TargetReference(
    String outputId,
    String targetClass,
    String targetOid,
    String producingRuleId
) {}
```

#### `DeferredReference`

```java
public record DeferredReference(
    TargetObjectKey owner,
    String targetRoleName,
    String associationName,
    SourceReferenceSelector sourceSelector,
    String targetRuleId,
    String expectedTargetClass,
    Cardinality expectedCardinality,
    boolean required
) {}
```

#### `SourceReferenceSelector`

```java
public record SourceReferenceSelector(
    String inputId,
    String basketId,
    String expectedSourceClass,
    String referencedSourceOid
) {}
```

### Neuer `ReferenceIndex`

```java
public interface ReferenceIndex {
    void add(SourceObjectKey source, TargetReference target);
    List<TargetReference> find(SourceReferenceSelector selector);
}
```

Implementierung:

```java
public final class InMemoryReferenceIndex implements ReferenceIndex
```

Suche in absteigender Strenge:

1. exakte Input-ID, Basket, Klasse, OID;
2. exakte Input-ID, Klasse und OID, falls Cross-Basket erlaubt;
3. weitere Fallbacks nur bei expliziter Planoption.

Kein standardmässiger globaler OID-only-Fallback.

### Neuer `ReferenceResolutionService`

```java
public final class ReferenceResolutionService {
    public ReferenceResolutionReport resolveAll(
        TransformPlan plan,
        StateStore stateStore,
        ReferenceIndex referenceIndex,
        DiagnosticCollector diagnostics
    );
}
```

Er muss:

- Ambiguitäten erkennen;
- erwartete Zielklasse prüfen;
- Rolle auf dem korrekten Zielmodell auflösen;
- Kardinalität pro Owner-Objekt prüfen;
- Referenzobjekt korrekt am Owner setzen;
- fehlende optionale und obligatorische Referenzen unterscheiden;
- `targetRuleId` berücksichtigen;
- Association-Namen für Diagnostik ausgeben.

### `RoleResolver` verbessern

```java
public ResolvedRole requireRole(
    TypeSystemFacade targetTypeSystem,
    String ownerClass,
    String roleName,
    String associationName
);
```

```java
public record ResolvedRole(
    RoleDef role,
    AssociationDef association,
    String destinationClass,
    long minCardinality,
    long maxCardinality
) {}
```

### `MappingCompiler` verbessern

Für jede `RefMapping` prüfen:

- Rolle existiert;
- Association existiert, falls angegeben;
- Rolle gehört zur Association;
- `targetRuleId` existiert;
- Zielklasse der referenzierten Rule ist kompatibel;
- Source-Referenzpfad existiert;
- Mandatory-Status aus Modell und Mapping widerspricht sich nicht.

### Tests

```text
ReferenceIndexTest
ReferenceResolutionServiceTest
SameOidDifferentBasketTest
SameOidDifferentInputTest
SameOidDifferentClassTest
ForwardReferenceTest
BackwardReferenceTest
MissingOptionalReferenceTest
MissingMandatoryReferenceTest
AmbiguousReferenceTest
WrongTargetClassReferenceTest
AssociationXtfIntegrationTest
Ili1RoleItfIntegrationTest
PerOwnerCardinalityTest
```

Der Association-XTF-Test muss eine echte, mit ilivalidator geprüfte XTF-Datei verwenden.

### Akzeptanzkriterien

- Pflichtreferenzen werden pro Objekt geprüft;
- gleiche OIDs in verschiedenen Kontexten werden nicht verwechselt;
- echte Association-Serialisierung ist getestet;
- keine globale OID-Abkürzung im Normalfall;
- vollständiger Reference Resolution Report.

---

## Phase 20: Expression Compiler und vollständige statische Typprüfung

### Ziel

Expressions werden genau einmal kompiliert. Compiler und Runtime verwenden dieselbe AST. Alle statisch erkennbaren Pfad-, Funktions- und Typfehler werden vor dem Lauf gefunden.

### Neue Klassen

#### `CompiledExpression`

```java
public record CompiledExpression(
    String sourceText,
    Expression ast,
    TypeInfo resultType,
    boolean deterministic,
    Set<ResolvedPath> referencedPaths
) {}
```

#### `ResolvedPath`

```java
public record ResolvedPath(
    String alias,
    String attributeOrRole,
    Table sourceClass,
    AttributeDef attribute,
    RoleDef role,
    TypeInfo type
) {}
```

#### `ExpressionCompiler`

```java
public final class ExpressionCompiler {
    public CompiledExpression compile(
        String expression,
        ExpressionCompileContext context,
        DiagnosticCollector diagnostics
    );
}
```

#### `ExpressionCompileContext`

```java
public record ExpressionCompileContext(
    String ruleId,
    Map<String, SourcePlan> sourcesByAlias,
    TypeInfo expectedTargetType,
    FunctionRegistry functionRegistry,
    Map<String, Map<String, String>> enumMaps
) {}
```

#### `ExpressionTypeChecker`

Besucht rekursiv `LiteralExpr`, `PathExpr`, `FunctionCallExpr` und `ConditionalExpr`.

### `AssignmentPlan` ändern

Statt eines Expression-Strings:

```java
CompiledExpression expression
```

Analog für Source-Filter, Rule-Filter, BAG-Filter, BAG-Assignments, Join-Bedingungen und Defaults.

### `FunctionDef` erweitern

```java
public record FunctionDef(
    String name,
    List<FunctionParam> parameters,
    boolean variadic,
    TypeResolver returnTypeResolver,
    boolean deterministic,
    EvaluationMode evaluationMode,
    FunctionImplementation implementation
) {}
```

```java
public enum EvaluationMode {
    EAGER,
    LAZY
}
```

`if`, `coalesce`, `and` und `or` müssen lazy evaluieren.

### Sprachumfang ergänzen

Mindestens:

- `==`
- `!=`
- `<`
- `<=`
- `>`
- `>=`
- `and`
- `or`
- `not`
- `defined`
- `notDefined`
- `coalesce`
- `if`

Null-Semantik schriftlich definieren.

### Enum-Prüfung

`enumMap(value, mapName)`:

- Mapping-Tabelle existiert;
- Zielwerte existieren im Ziel-Enum oder passen zum Boolean-/Number-Ziel;
- unvollständige Source-Abdeckung erzeugt je nach Compile Mode Warning oder Error;
- Defaultstrategie explizit.

### Numerik

Empfehlung:

```java
public record NumberValue(BigDecimal value) implements Value
```

### Tests

```text
ExpressionCompilerTest
ExpressionTypeCheckerTest
BarePathValidationTest
NestedFunctionTypeTest
LazyCoalesceTest
LazyIfTest
ComparisonOperatorsTest
BooleanOperatorsTest
EnumTargetValidationTest
NumericPrecisionTest
UnknownFunctionCompileTest
WrongArgumentCountCompileTest
WrongArgumentTypeCompileTest
```

### Akzeptanzkriterien

- Runtime parst keine Assignment-Expression erneut;
- `p.Unbekannt` wird beim Compile erkannt, auch ohne `${...}`;
- falsche Funktionssignaturen verhindern den Lauf;
- `coalesce(..., now())` ruft `now()` nicht auf, wenn ein früherer Wert definiert ist;
- deterministische Eigenschaft wird im Plan geführt.

---

## Phase 21: DSL-Konsistenz – Filter, Defaults, Stubs und Schema

### Ziel

Jedes in `JobConfig` öffentlich angebotene Feld hat definierte Semantik, Compilerunterstützung und Runtimeverhalten oder wird explizit abgelehnt.

### Neue Klasse `DslCapabilityValidator`

```java
public final class DslCapabilityValidator {
    public void validateSupportedFeatures(
        JobConfig config,
        DiagnosticCollector diagnostics
    );
}
```

### Rule-Level-Filter implementieren

`RulePlan` erhält:

```java
Optional<CompiledExpression> predicate
```

Auswertungsreihenfolge:

1. Source passt zu Input und Klasse;
2. Source-Level-Filter;
3. Rule-Level-Filter;
4. Identity;
5. Assignments;
6. BAGs;
7. Refs.

### Defaults implementieren

Priorität:

```text
explizites assign
→ rule.defaults
→ mapping.defaults
→ kein Wert
```

Defaults sind Expressions und werden kompiliert. Mandatory Coverage muss Assignments und Defaults berücksichtigen.

### `compileMode`

```java
public enum CompileMode {
    STRICT,
    COMPATIBLE,
    REPORT
}
```

Semantik:

- `STRICT`: unsichere Typen, fehlende Enum-Coverage und Pflichtattributlücken sind Errors;
- `COMPATIBLE`: klar definierte Punkte werden Warnings;
- `REPORT`: Plan nur zu Analysezwecken, nicht ausführbar.

Nicht mit Runtime-`FailPolicy` vermischen.

### Noch nicht implementierte Features

`joins` und `create` werden bis Phase 22 bei Vorkommen als klarer Compilerfehler abgelehnt. Dasselbe gilt für `external` OID, `expression` Basket Strategy und unbekannte BAG-Modi.

### Tests

```text
RuleLevelFilterTest
DefaultPrecedenceTest
MandatoryCoveredByDefaultTest
CompileModeTest
UnsupportedJoinRejectedTest
UnsupportedCreateRejectedTest
UnsupportedOidStrategyRejectedTest
UnsupportedBasketStrategyRejectedTest
```

### Akzeptanzkriterien

- kein DSL-Feld wird still ignoriert;
- Rule-Level-Filter funktioniert;
- Defaults funktionieren typisiert;
- Config und Dokumentation stimmen überein.

---

## Phase 22: Joins, Multi-Source-Regeln, Create, Split und Merge

### Ziel

Die Engine kann die für DM01↔DMAV notwendigen Objektbildungsoperationen generisch ausdrücken.

### Join-Plan

```java
public record JoinPlan(
    String id,
    JoinType type,
    SourcePlan left,
    SourcePlan right,
    CompiledExpression condition,
    JoinCardinality expectedCardinality
) {}
```

```java
public enum JoinType {
    INNER,
    LEFT
}
```

```java
public enum JoinCardinality {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
}
```

### Join-Indizes

```java
public interface SourceLookupIndex {
    void index(SourceRecord record);
    List<SourceRecord> lookup(LookupKey key);
}
```

```java
public record LookupKey(
    String inputId,
    String sourceClass,
    String attribute,
    CanonicalValue value
) {}
```

Für die erste Implementation nur indexierbare Equi-Joins zulassen. Komplexe nicht indexierbare Bedingungen diagnostizieren.

### Multi-Source Evaluation Context

```java
Map<String, BoundSourceObject> sources
```

```java
public record BoundSourceObject(
    SourcePlan sourcePlan,
    SourceRecord sourceRecord
) {}
```

### Create/Split/Merge

```java
public record CreatePlan(
    String id,
    String outputId,
    Table targetClass,
    Optional<CompiledExpression> predicate,
    List<AssignmentPlan> assignments,
    List<RefPlan> references,
    List<BagPlan> bags,
    IdentityPlan identity
) {}
```

Semantik:

- Eine Rule kann ein primäres oder mehrere benannte Zielobjekte erzeugen.
- Referenzen können auf benannte Create-Pläne zeigen.
- Mehrere Source-Bindings können ein Zielobjekt erzeugen.
- Gruppierung benötigt explizite Grouping-Logik.

### Rule Dependencies

```java
public final class RuleDependencyGraph {
    public List<String> topologicalOrder();
    public List<List<String>> cycles();
}
```

### Tests

```text
InnerJoinTest
LeftJoinTest
OneToOneJoinTest
OneToManyJoinTest
ManyToOneJoinTest
AmbiguousJoinTest
MissingJoinTargetTest
CreateAdditionalObjectTest
SplitOneSourceToMultipleTargetsTest
MergeMultipleSourcesToOneTargetTest
RuleDependencyCycleTest
JoinPerformanceComponentTest
```

### Akzeptanzkriterien

- Joins sind nicht mehr nur Config;
- RulePlan enthält Join-/Create-Pläne;
- Engine-Kern enthält keine DM01-spezifischen Joins;
- fehlende und mehrdeutige Join-Treffer werden sauber diagnostiziert.

---

## Phase 23: BAG OF STRUCTURE und ILI1-Tabellenmaterialisierung härten

### Ziel

BAG-OF-STRUCTURE kann zuverlässig zwischen separaten ILI1-Tabellenobjekten und eingebetteten ILI2-Strukturen transformiert werden.

### Neue Klassen

#### `BagTransformationService`

```java
public final class BagTransformationService {
    public void embed(BagExecutionContext context);
    public void expand(BagExecutionContext context);
}
```

#### `BagExecutionContext`

```java
public record BagExecutionContext(
    BagPlan plan,
    BoundSourceObject parent,
    IomObject target,
    TransformPlan transformPlan,
    StateStore stateStore,
    SourceLookupIndex lookupIndex,
    DiagnosticCollector diagnostics
) {}
```

#### `ParentChildIndex`

```java
public interface ParentChildIndex {
    void index(
        String sourceClass,
        String referenceAttribute,
        String parentOid,
        SourceRecord child
    );

    List<SourceRecord> children(
        String sourceClass,
        String referenceAttribute,
        String parentOid
    );
}
```

### `BagPlan` erweitern

Muss enthalten:

- Quellklasse;
- Parent-Referenzattribut;
- Parent-Alias;
- Structure-Typ;
- Cardinality;
- Modus `EMBED` oder `EXPAND`;
- Assignment ASTs;
- Identity/OID-Plan für expandierte Tabellenobjekte;
- Rückreferenzplan zum Parent.

### Verbindliche Semantik

#### EMBED

- Child-Objekte über Index finden;
- nur zum aktuellen Parent gehörende Elemente;
- stabile Reihenfolge;
- Structure ohne OID;
- Pflichtattribute prüfen.

#### EXPAND

- eingebettete Structures lesen;
- je Structure ein identifizierbares Zieltabellenobjekt erzeugen;
- stabile OID;
- Rückreferenz auf erzeugtes Parent-Zielobjekt;
- korrekter Basket;
- keine losgelösten synthetischen Source Records ohne Parentkontext.

### Tests

```text
BagEmbedZeroTest
BagEmbedOneTest
BagEmbedManyTest
BagWrongParentTest
BagExpandTest
BagExpandCreatesParentReferenceTest
BagMandatoryAttributeTest
BagStableOrderTest
ImportedStructureTypeTest
RealDmavTextpositionIntegrationTest
RealDm01PositionTableIntegrationTest
```

### Akzeptanzkriterien

- keine quadratische Vollsuche über alle Source Records;
- Parentreferenzen beim EXPAND korrekt;
- reale Textpositionen können semantisch gelesen und geschrieben werden;
- Outputs werden validiert.

---

## Phase 24: Geometrie- und Transfer-I/O-Härtung

### Ziel

Gültige ITF-/XTF-Geometrien werden ohne Informationsverlust innerhalb der unterstützten Geometrietypen gelesen, transformiert, geschrieben und erneut gelesen.

### Bestehende Klassen prüfen und verbessern

- `InterlisIoFactory`
- `IoxGeometryAdapter`
- `ItfGeometryWriter`
- `GeometryAdapter`
- `ExpressionEngine`-Geometriepfade

### Neue Klassen

#### `GeometryValueCopier`

```java
public final class GeometryValueCopier {
    public IomObject deepCopy(IomObject geometry);
}
```

Source-Geometrien dürfen nicht direkt als mutable Targetobjekte angehängt werden.

#### `GeometryCompatibilityService`

```java
public final class GeometryCompatibilityService {
    public GeometryCompatibility check(
        TypeInfo sourceType,
        TypeInfo targetType,
        AttributeDef sourceAttribute,
        AttributeDef targetAttribute
    );
}
```

Prüfen:

- Dimension;
- Coord-Domain;
- erlaubte Linienformen;
- ARCS;
- SURFACE/AREA-Kompatibilität;
- Genauigkeit soweit aus Metamodell ableitbar.

### Unterstützungsumfang

Verbindlich:

- COORD 2D;
- optional COORD 3D;
- POLYLINE mit Geraden;
- POLYLINE mit Kreisbögen;
- SURFACE;
- AREA inklusive Point-on-Surface-/ITF-Hilfsinformation;
- mehrere Boundaries;
- Löcher, soweit IOM unterstützt;
- Read → Write → Read.

Noch nicht zwingend:

- Topologiereparatur;
- Koordinatentransformation;
- Generalisierung;
- räumliche Operationen;
- LINEATTR, sofern noch nicht sicher implementierbar.

Nicht unterstützte Fälle müssen Diagnostics erzeugen.

### INTERLIS 2.3/2.4

Keine separate XML-Implementierung. `InterlisIoFactory` entscheidet anhand von Modell-/Transferkontext, Dateiformat und Library, welcher Reader/Writer verwendet wird. Die Engine bleibt versionsneutral.

### Tests

```text
CoordRoundtripTest
PolylineStraightRoundtripTest
PolylineArcRoundtripTest
SurfaceRoundtripTest
AreaRoundtripTest
AreaPointOnSurfaceTest
MultipleBoundaryTest
GeometryDeepCopyTest
GeometryTypeMismatchTest
XtfReadOwnOutputTest
Ili1ToIli2GeometryIntegrationTest
Ili2ToIli1GeometryIntegrationTest
RealDatasetGeometrySmokeTest
```

Alle erzeugten Transfers mit `ilivalidator` prüfen.

### Akzeptanzkriterien

- eigener XTF-Output kann mit Modellkontext wieder eingelesen werden;
- keine `IoxSyntaxException` für gültigen eigenen Output;
- Geometrieobjekte werden tief kopiert;
- reale AREA-/SURFACE-Daten aus dem DM01-Datensatz können gelesen werden;
- bekannte Nichtunterstützung ist explizit.

---

## Phase 25: Fehlerpolitik, transaktionale Ausgaben, CLI und Validierung

### Ziel

Die CLI darf keine ungültige oder halbfertige Ausgabedatei als erfolgreichen Lauf hinterlassen.

### Neue Klassen

#### `RunOptions`

```java
public record RunOptions(
    List<String> modelDirectories,
    boolean validateOutput,
    Path reportDirectory,
    boolean keepTemporaryFiles
) {}
```

#### `TransactionalOutputManager`

```java
public final class TransactionalOutputManager implements AutoCloseable {
    public Path createTemporaryOutput(OutputBinding binding);
    public void commit(OutputBinding binding);
    public void rollbackAll();
}
```

Ablauf:

1. Ausgaben in temporäre Dateien schreiben;
2. Writer schliessen;
3. optional validieren;
4. nur bei Erfolg atomar auf Zielpfad verschieben;
5. bei Fehlern löschen oder bei Debugoption behalten.

#### `TransformationReportWriter`

Schreibt JSON und Markdown mit:

- Objektzahlen;
- Filterzahlen;
- Warnings/Errors;
- Referenzbericht;
- Joinbericht;
- Validatorstatus;
- Laufzeit;
- verwendeten Modellen und Versionen.

### FailPolicy vollständig umsetzen

#### `STRICT`

- Compilererror: kein Lauf;
- Runtimeerror: kein Commit;
- Validatorerror: kein Commit;
- Exit Code ungleich 0.

#### `LENIENT`

- nur explizit herabstufbare Fehler werden Warnings;
- strukturelle Fehler bleiben Errors;
- Ausgabe nur ohne nicht herabstufbare Errors;
- Report enthält alle Abweichungen.

#### `REPORT_ONLY`

- Modelle und Mapping kompilieren;
- Input scannen;
- Ausführbarkeit analysieren;
- keine endgültigen Outputs.

### CLI korrigieren

`transform`:

```text
--mapping
--modeldir
--validate
--report
--fail-policy
--keep-temp
```

Alle Optionen müssen Wirkung haben.

`validate-mapping` lädt Modelle und führt `compileTyped` aus.

Neuer Command:

```text
ilitransformer validate-transfer \
  --file ... \
  --modeldir ... \
  --model ...
```

### `IlivalidatorRunner` refaktorieren

CLI-Wrapper und Service trennen:

```text
TransferValidationService
InProcessIlivalidatorService
ValidateTransferCommand
```

Keine `System.exit()`-Logik in Serviceklassen.

### Tests

```text
StrictRollbackTest
LenientPolicyTest
ReportOnlyTest
TransactionalOutputManagerTest
ValidateOptionCliTest
ReportOptionCliTest
ValidateMappingTypedCliTest
ValidatorFailureExitCodeTest
RelativeMappingPathCliTest
JobModeldirMergeTest
```

### Akzeptanzkriterien

- `--validate` validiert tatsächlich;
- ungültiger Output wird in strict nicht übernommen;
- `--report` erzeugt Report;
- CLI-Exitcodes dokumentiert und getestet;
- Mapping-interne Modelldirs funktionieren.

---

## Phase 26: Performance, Indizes und grosse Transfers

### Ziel

Die Engine verarbeitet realistische vollständige AV-Datensätze ohne offensichtliche quadratische Laufzeit oder unnötige Mehrfachhaltung.

### Neue Klassen

#### `RuleDispatchIndex`

```java
public final class RuleDispatchIndex {
    public List<RulePlan> rulesFor(String inputId, String sourceClass);
}
```

#### `ExecutionMetrics`

```java
public final class ExecutionMetrics {
    public void recordRead(...);
    public void recordRuleMatch(...);
    public void recordJoinLookup(...);
    public void recordBagLookup(...);
    public void recordTarget(...);
    public ExecutionMetricsSnapshot snapshot();
}
```

### `TransformationEngine` zerlegen

```text
TransformationEngine
SourceIndexingService
RuleExecutionService
TargetObjectFactory
AssignmentExecutionService
BagTransformationService
ReferenceResolutionService
OutputWritingService
```

### Indizes

Mindestens:

- Rule Dispatch nach Input+Klasse;
- Source Object Index;
- Reference Index;
- Parent/Child BAG Index;
- Join Lookup Index;
- Target Object Index.

### Persistenter StateStore

Noch nicht zwingend. Es ist aber ein belastbarer Entscheidungsbericht zu liefern. Eine optionale `DuckDbStateStore`-Implementation nur nach Messung.

### Reale Datentests

Mit `src/test/data/DMAV_Version_1_1`:

- vollständigen DM01-Datensatz einlesen;
- vollständigen DMAV-Datensatz einlesen;
- Objektzahlen nach Topic/Klasse berichten;
- Basketanzahl;
- Referenzanzahl;
- Geometrietypen;
- Laufzeit;
- Peak Memory soweit messbar;
- kein vollständiger Transformationsanspruch in dieser Phase.

### Tests

```text
RuleDispatchIndexTest
BagLookupComplexityTest
JoinLookupComplexityTest
LargeSyntheticTransferTest
FullDm01ReadSmokeTest
FullDmavReadSmokeTest
ExecutionMetricsTest
DeterministicOutputOrderTest
```

### Akzeptanzkriterien

- keine SourceRecord×Rule-Vollschleife mehr;
- keine Parent×AllChildren-Vollsuche;
- vollständige reale Transfers können mindestens gelesen und inventarisiert werden;
- Performancebericht vorhanden.

---

# Teil B – Belastbarer DM01↔DMAV-Pilot

## Phase 27: Reale Datensatzinventarisierung und fachlich zusammenhängende Testausschnitte

### Ziel

Aus den beiden vollständigen Datensätzen belastbare, kleine und valide Testfixtures ableiten.

### Neue Klassen

#### `TransferInventoryService`

```java
public final class TransferInventoryService {
    public TransferInventory inspect(
        TransferDatasetDescriptor descriptor
    );
}
```

`TransferInventory` enthält Transfermodellnamen, Baskets, Objektzahlen je Klasse, OID-Typen, Referenzen, Geometrietypen, Topics und mögliche LFP3-Zusammenhänge.

#### `ConnectedSubgraphExtractor`

```java
public final class ConnectedSubgraphExtractor {
    public ExtractedTransfer extract(
        TransferDatasetDescriptor source,
        ExtractionRequest request
    );
}
```

Der Extractor muss einen fachlich zusammenhängenden Teiltransfer inklusive abhängiger Objekte und Referenzen erzeugen.

### LFP3-Testfixture

Aus jedem vollständigen Datensatz ist ein kleiner Teiltransfer abzuleiten, der möglichst enthält:

DM01:

- LFP3Nachfuehrung;
- mindestens zwei LFP3;
- Entstehungsreferenzen;
- ein Objekt mit Höhe;
- ein Objekt ohne Höhe, falls modellkonform;
- LFP3Pos;
- LFP3Symbol;
- Perimeter, falls vorhanden.

DMAV:

- entsprechende LFP3Nachfuehrung;
- mindestens zwei LFP3;
- Association;
- Textposition BAG;
- SymbolOri;
- Geometrie;
- optionale Attribute.

### Validierung

Jeder extrahierte Teiltransfer muss unmittelbar mit ilivalidator geprüft werden.

Bei nicht generisch extrahierbaren Transfers ist ein einmalig fachlich kuratierter Teiltransfer erlaubt, aber Herkunft, Schritte und Validierung sind zu dokumentieren. Keine manuelle XML-Bearbeitung ohne anschliessende Validierung.

### Artefakte

```text
src/test/resources/real-dm01-dmav/lfp3/dm01-input.itf
src/test/resources/real-dm01-dmav/lfp3/dmav-input.xtf
docs/dm01-dmav/real-dataset-inventory.md
docs/dm01-dmav/lfp3-fixture-provenance.md
```

### Tests

```text
FullDatasetInventoryTest
ConnectedSubgraphExtractorTest
ExtractedDm01FixtureValidationTest
ExtractedDmavFixtureValidationTest
```

### Akzeptanzkriterien

- reale valide LFP3-Fixtures liegen vor;
- keine synthetischen ILI2-Ersatzmodelle als Hauptnachweis;
- vollständige Datensatzinventare dokumentiert.

---

## Phase 28: Echter DM01→DMAV-LFP3-End-to-End-Pilot

### Ziel

Nachweisen, dass eine echte DM01-ITF mit dem offiziellen DM01-Modell in eine validator-konforme DMAV-Version-1.1-XTF transformiert werden kann.

### Produktives Mapping

```text
profiles/dm01-to-dmav/1.1/lfp3.yaml
```

### Mindestumfang

#### `LFP3Nachfuehrung`

- `NBIdent`
- `Identifikator`
- `Beschreibung`
- `Perimeter`
- `GueltigerEintrag`

Kein stilles `now()`. Fallbackstrategie muss konfigurierbar und fachlich dokumentiert sein.

#### `LFP3`

- `NBIdent`
- `Nummer`
- `LFPArt`
- `Geometrie`
- `Hoehengeometrie`
- `Lagegenauigkeit`
- `IstLagezuverlaessig`
- `Hoehengenauigkeit`
- `IstHoehenzuverlaessig`
- `Punktzeichen`
- `Schutzart`, falls ableitbar, sonst undefiniert und dokumentiert
- `Grenzpunktfunktion`
- `IstHoheitsgrenzsteinAlt` entsprechend Constraint
- `AktiverUnterhalt`
- `SymbolOri`
- `Textposition`
- `Entstehung_LFP3`

### Constraints

Die Mappinglogik muss die Constraints des echten DMAV-Modells berücksichtigen. Gekoppelte Höhenattribute dürfen nicht inkonsistent erzeugt werden. Anschliessender ilivalidator ist verbindlich.

### Tests

#### `RealDm01ToDmavLfp3EndToEndTest`

Ablauf:

1. offizielles DM01-Modell kompilieren;
2. offizielles DMAV-Modell kompilieren;
3. reale DM01-Fixture lesen;
4. produktives Mapping kompilieren;
5. transformieren;
6. strict + validate;
7. `validation.valid() == true`;
8. Output wieder einlesen;
9. Objektzahlen, Werte, UUID-Stabilität, Associations, Textpositionen und Geometrien prüfen;
10. zweiten Lauf ausführen und deterministische OIDs vergleichen.

Negativtests:

- fehlender obligatorischer Nachführungsbezug;
- unbekannter Enum;
- inkonsistente Höhenattribute;
- duplicate NBIdent+Nummer;
- fehlende Geometrie;
- unzulässiger Punktzeichen-/LFPArt-Zustand.

### Abnahme

Erst wenn dieser Test grün ist, darf README sagen:

```text
DM01 → DMAV 1.1: LFP3 real-data pilot supported
```

---

## Phase 29: Echter DMAV→DM01-LFP3-End-to-End-Pilot

### Ziel

Nachweisen, dass eine echte DMAV-Version-1.1-XTF in eine validator-konforme DM01-ITF transformiert werden kann.

### Produktives Mapping

```text
profiles/dmav-to-dm01/1.1/lfp3.yaml
```

### Mindestumfang

Nachführung:

- `NBIdent`
- `Identifikator`
- `Beschreibung`
- `Perimeter`
- gültiges Datum gemäss DM01-Zielmodell

LFP3:

- `NBIdent`
- `Nummer`
- Geometrie;
- Höhengeometrie;
- Lagegenauigkeit;
- Lagezuverlässigkeit;
- Höhengenauigkeit;
- Höhenzuverlässigkeit;
- Punktzeichen;
- Protokollstrategie;
- Entstehungsreferenz;
- `Textposition` → `LFP3Pos`;
- `SymbolOri` → `LFP3Symbol`.

### Verlustigkeit

DMAV-only-Informationen müssen in einem strukturierten Lossiness Report erscheinen.

```java
public record LossEvent(
    String ruleId,
    String sourceClass,
    String sourceOid,
    String sourcePath,
    String reasonCode,
    String description
) {}
```

```java
public final class LossinessCollector {
    public void record(LossEvent event);
    public void writeJson(Path path);
    public void writeMarkdown(Path path);
}
```

Mindestens zu dokumentieren:

- `LFPArt`
- `Schutzart`
- `Grenzpunktfunktion`
- `IstHoheitsgrenzsteinAlt`
- `AktiverUnterhalt`
- weitere nicht darstellbare Informationen.

### Tests

```text
RealDmavToDm01Lfp3EndToEndTest
DmavToDm01TextpositionExpansionTest
DmavToDm01SymbolMaterializationTest
DmavToDm01AssociationTest
DmavToDm01LossinessReportTest
DmavToDm01ValidatorTest
```

### Abnahme

Erst dann:

```text
DMAV 1.1 → DM01: LFP3 real-data pilot supported
```

---

## Phase 30: LFP3-Roundtrip- und Äquivalenzanalyse

### Ziel

Nicht bitweise Gleichheit, sondern fachlich dokumentierte Äquivalenz prüfen.

### Neuer `SemanticTransferComparator`

```java
public final class SemanticTransferComparator {
    public ComparisonReport compare(
        TransferInventory expected,
        TransferInventory actual,
        ComparisonProfile profile
    );
}
```

Vergleich:

- Business Keys;
- skalare Werte;
- Enum-Mappings;
- Geometrien mit definierter Toleranz;
- Referenzgraph;
- BAG-Inhalte;
- erwartete Verluste;
- OIDs über Mapping statt blindem Stringvergleich.

### Roundtrips

- DM01 → DMAV → DM01
- DMAV → DM01 → DMAV

Beide mit realen LFP3-Fixtures.

### Akzeptanz

- alle nicht als Verlust dokumentierten Informationen bleiben erhalten;
- jeder Verlust ist im Profil und Report genannt;
- keine unerwarteten neuen Objekte;
- keine verlorenen Referenzen;
- keine ungültigen Transfers.

---

# Teil C – Systematische DM01↔DMAV-Vervollständigung

## Phase 31: Automatischer Abdeckungs- und Gap-Report auf echten Modellen

### Ziel

Die weitere fachliche Arbeit datenbasiert planen.

### Bestehende Klassen weiterverwenden

- `CorrelationWorkbookImporter`
- `MappingCandidateGenerator`
- `TopicGapReportGenerator`
- `IliModelService`
- `ModelInventory`

### Verbesserungen

Der Gap Report muss unterscheiden:

- Klasse existiert auf beiden Seiten;
- Attribut direkt kopierbar;
- Enum-Mapping nötig;
- Typkonversion nötig;
- Join nötig;
- Split nötig;
- Merge nötig;
- BAG/Tabelle nötig;
- Geometriekonversion nötig;
- nicht darstellbar;
- fachlich offen;
- bereits mit Real-Data-Test abgedeckt.

### Confidence ist kein Freigabestatus

Jeder Kandidat benötigt Status:

```text
generated
reviewed
implemented
unit-tested
real-data-tested
validator-tested
accepted
```

### Artefakte

- maschinenlesbares JSON;
- Markdown-Statusmatrix;
- pro Topic offene Fragen;
- Verweis auf XLSX-Zeile.

---

## Phase 32 ff.: Ein Fachthema pro abgeschlossener Phase

Jedes DM01/DMAV-Thema erhält eine eigene Phase. Reihenfolge nach Risiko, nicht nur Modellreihenfolge.

Empfohlene Reihenfolge:

1. HFP3 und weitere einfache Fixpunktobjekte;
2. weitere scalar-/point-lastige Klassen;
3. Gebäudeadressen;
4. Nomenklatur;
5. einfache Linienobjekte;
6. Hoheitsgrenzen;
7. Rohrleitungen;
8. Einzelobjekte;
9. Bodenbedeckung;
10. Grundstücke und Liegenschaften;
11. komplexe Historisierung und topicübergreifende Abhängigkeiten.

Jede Fachphase muss enthalten:

- fachliche Mappingentscheidung;
- produktives Mappingprofil in beide Richtungen, sofern möglich;
- reale valide Testfixture;
- Unit Tests;
- echten End-to-End-Test;
- ilivalidator;
- Lossiness Report;
- Statusmatrix;
- Performance-Stichprobe;
- offene Fragen;
- klare Nichtunterstützung.

---

## 8. Codequalitätsanforderungen

### Klassenverantwortung

- keine neue God Class;
- `TransformationEngine` orchestriert;
- Modelauflösung nicht duplizieren;
- kein statischer globaler Zustand;
- Services über Konstruktor injizieren;
- immutable Plan-Records bevorzugen;
- defensive Kopien für Collections;
- keine `null`-Maps im Plan.

### Diagnostics

Jede Diagnostic benötigt:

- stabilen Code;
- Severity;
- Rule-ID;
- Source- und/oder Target-Pfad;
- Objektkontext, falls Runtime;
- konkrete Empfehlung;
- keine unstrukturierten `System.out.println` als einzige Fehlerausgabe.

Empfohlene zusätzliche Codes:

```text
PLAN_INPUT_BINDING_MISSING
PLAN_OUTPUT_BINDING_MISSING
OID_DUPLICATE_TARGET
OID_IDENTITY_VALUE_MISSING
REF_AMBIGUOUS_CONTEXT
REF_CARDINALITY_VIOLATION
EXPR_ARGUMENT_COUNT
EXPR_ARGUMENT_TYPE
DSL_UNSUPPORTED_FEATURE
OUTPUT_VALIDATION_FAILED
OUTPUT_ROLLBACK
LOSS_INFORMATION_DROPPED
```

### Exceptions

Exceptions nur für Programmierfehler, nicht fortsetzbare Infrastrukturfehler und kontrollierten Abbruch. Fachliche Datenfehler sollen Diagnostics erzeugen und der FailPolicy folgen.

### Reproduzierbarkeit

- sortierte Ausgabereihenfolge;
- stabile OIDs;
- keine Uhrzeit im fachlichen Ergebnis ohne explizite Mappingentscheidung;
- Reports dürfen Zeitstempel enthalten, Golden-Vergleiche müssen sie normalisieren.

---

## 9. Gradle- und Tooling-Anforderungen

### Empfohlene Konfigurationen

```groovy
configurations {
    iliTools
    validatorTools
}
```

Die normale Java-API soll Maven-Abhängigkeiten verwenden. Separate Tool-Konfigurationen sind für CLI-Fallbacks oder externe Validierungstasks zulässig.

### Tool Locator

```java
public final class InterlisToolLocator {
    public Optional<Path> findIli2cJar();
    public Optional<Path> findIlivalidatorJar();
}
```

Suchreihenfolge:

1. explizite Gradle-/CLI-Option;
2. Environment Variable;
3. bekannte lokale Pfade nur als optionaler Development-Fallback;
4. kein Treffer.

Der Java-In-Process-Weg über Dependencies hat Vorrang.

### Validierungs-Tasks

```text
validateCheckedInModels
validateCheckedInTransfers
validateRealDm01Dataset
validateRealDmavDataset
```

### Offline-Verhalten

Die CI soll nach Möglichkeit keine externen Modellserver benötigen. Alle für Tests benötigten offiziellen Modelle sind lokal abzulegen, mit Herkunft und Version zu dokumentieren und unverändert zu lassen.

---

## 10. Verbindliche Testkonventionen

### Assertions

Unzulässig:

```java
System.out.println(result.success());
```

ohne:

```java
assertThat(result.success()).isTrue();
```

### Temporäre Dateien

JUnit `@TempDir` verwenden.

### Semantische Transfervergleiche

Nicht:

```java
assertThat(xml1).isEqualTo(xml2);
```

Stattdessen Transfer lesen, Objekte nach Business Key sortieren, Attribute, Referenzen und Geometrien semantisch vergleichen.

### Negative Fixtures

Namenskonvention:

```text
invalid-*.itf
invalid-*.xtf
```

mit Manifest zum erwarteten Validatorfehler.

### Reale Datentests

JUnit-Tag:

```java
@Tag("real-data")
```

Gradle-Task `realDataTest` führt diese Tests aus.

---

## 11. Agenten-Arbeitsweise

Der Coding-Agent muss pro Phase:

1. aktuellen Code und relevante Tests lesen;
2. eine Implementierungsnotiz unter `docs/dev/phases/phase-XX.md` anlegen;
3. bestehende Fehler zuerst reproduzieren;
4. mindestens einen roten Regressionstest schreiben;
5. Implementation durchführen;
6. Unit Tests ausführen;
7. Integrationstests ausführen;
8. relevante Modelle mit ili2c prüfen;
9. relevante Transfers mit ilivalidator prüfen;
10. Dokumentation aktualisieren;
11. Feature-Matrix aktualisieren;
12. keine nächste Phase beginnen, bevor die Akzeptanzkriterien erfüllt sind.

Am Ende jeder Phase dokumentieren:

```text
Implemented
Changed classes
Added classes
Tests
Validation commands
Known limitations
Open questions
Migration notes
```

---

## 12. Verbotene Abkürzungen

Der Agent darf nicht:

- Tests löschen, weil sie fehlschlagen;
- Assertions abschwächen;
- reale Tests durch Mocks ersetzen;
- offizielle Modelle vereinfachen und das als echten Test bezeichnen;
- XTF mit DOM/SAX/Jackson XML selbst parsen;
- Transferdateien per String ersetzen;
- ungültige Transfers einchecken, ohne sie als Negativfixture zu kennzeichnen;
- fachliche Werte erfinden;
- `now()` als Default für fachliche Gültigkeitsdaten verwenden, sofern nicht ausdrücklich entschieden;
- unbekannte Enumwerte still durchreichen;
- Modellfehler als Runtime-Warning verbergen;
- DM01/DMAV-Sonderfälle in die generische Engine einbauen;
- vollständige DM01↔DMAV-Unterstützung behaupten, bevor sie pro Topic mit realen Daten nachgewiesen ist.

---

## 13. Definition of Done für die Stabilisierung

Teil A ist abgeschlossen, wenn:

- alle öffentlichen DSL-Felder implementiert oder abgelehnt werden;
- Compiler und Runtime denselben Plan verwenden;
- TypeSystems korrekt an Input/Output gebunden sind;
- OIDs kollisionsfrei sind;
- Referenzen kontext- und objektbezogen aufgelöst werden;
- Expressions vorkompiliert und typgeprüft sind;
- Rule-Level-Filter und Defaults funktionieren;
- Joins/Create generisch unterstützt sind;
- BAG-Transformation indexiert und parent-aware ist;
- Geometrie-Read/Write/Read funktioniert;
- strict/lenient/reportOnly korrekt sind;
- CLI-Optionen Wirkung haben;
- Outputs transaktional geschrieben und optional validiert werden;
- vollständige reale DM01- und DMAV-Datensätze eingelesen werden können;
- CI grün ist.

---

## 14. Definition of Done für den LFP3-Pilot

Der LFP3-Pilot gilt nur dann als unterstützt, wenn:

- echtes DM01-ILI1-Modell;
- echtes DMAV-1.1-Modell;
- reale valide ITF-/XTF-Fixtures;
- beide Richtungen;
- vollständige Mindestattributliste;
- echte Referenzen/Associations;
- echte Textpositionen;
- echte Symbolmaterialisierung;
- echte Geometrien;
- validierte Outputs;
- semantischer Roundtrip-Vergleich;
- dokumentierte Lossiness;
- deterministische OIDs;
- keine unerwarteten Diagnostics.

---

## 15. Definition of Done für vollständiges DM01↔DMAV

Eine vollständige Unterstützung darf erst behauptet werden, wenn:

- alle relevanten Topics in einer Statusmatrix erfasst sind;
- jedes unterstützte Topic reale End-to-End-Tests besitzt;
- alle Outputs validiert sind;
- nicht umkehrbare Informationen dokumentiert sind;
- alle fachlich offenen Punkte entschieden oder als nicht unterstützt markiert sind;
- die vollständigen Datensätze verarbeitet werden können;
- Performance und Speicherverbrauch dokumentiert sind;
- die Transformation keine stillen Datenverluste erzeugt.

---

## 16. Offene Fragen

Diese Fragen sind unter `docs/open-questions.md` beziehungsweise `docs/dm01-dmav/open-questions.md` weiterzuführen.

### Architektur

1. Soll `StateStore` langfristig eine persistente DuckDB-Implementation erhalten?
2. Sollen Joins ausschliesslich Equi-Joins bleiben?
3. Soll `CompileMode.REPORT` einen nicht ausführbaren Plan erzeugen?
4. Wie werden Cross-Basket-Referenzen explizit konfiguriert?
5. Wie werden Cross-File-Referenzen erlaubt oder verboten?
6. Sollen OID-Strategien pro Rule oder CreatePlan überschreibbar sein?
7. Soll es eine SPI für projektspezifische Funktionen geben?

### DM01/DMAV

1. Welcher fachliche Ersatz ist zulässig, wenn `GueltigerEintrag` in DM01 fehlt?
2. Wie wird `Schutzart` abgeleitet?
3. Wie wird `IstHoheitsgrenzsteinAlt` bestimmt?
4. Welche DMAV-only-Attribute dürfen bei Rücktransformation verworfen werden?
5. Welche Business Keys sind pro Fachklasse stabil genug für deterministische UUIDs?
6. Wie werden historische beziehungsweise untergegangene Objekte abgebildet?
7. Welche maschinelle Semantik haben `K`, `V` und `I` exakt?
8. Welche Topics sind tatsächlich bidirektional transformierbar?
9. Welche Koordinaten- und Genauigkeitsumrechnungen sind verbindlich?
10. Wie sollen AREA-Topologiefehler behandelt werden?
11. Ist LINEATTR für einzelne Themen zwingend?
12. Welche Roundtrip-Toleranzen gelten für Geometrien und Zahlen?

### Testdaten

1. Dürfen die vollständigen Datensätze in öffentlicher CI verwendet werden?
2. Enthalten sie sensible Daten?
3. Wie gross dürfen extrahierte Fixtures sein?
4. Soll die Fixture-Extraktion reproduzierbar automatisiert werden?
5. Welche Objekte eignen sich als repräsentative Testausschnitte?

---

## 17. Verbindliche Reihenfolge

```text
Phase 16  Baseline, CI, Feature-Matrix
Phase 17  Model-/Input-/Output-Bindings
Phase 18  Identity und OIDs
Phase 19  Referenzen und Associations
Phase 20  Expression Compiler
Phase 21  DSL-Konsistenz, Filter und Defaults
Phase 22  Joins, Create, Split und Merge
Phase 23  BAG OF STRUCTURE / Tabellenmaterialisierung
Phase 24  Geometrie und I/O
Phase 25  Fehlerpolitik, Transaktionen, CLI, Validierung
Phase 26  Performance und vollständige Datensatz-Smoke-Tests
Phase 27  Reale LFP3-Fixtures
Phase 28  Echter DM01→DMAV-LFP3-Pilot
Phase 29  Echter DMAV→DM01-LFP3-Pilot
Phase 30  Roundtrip und Äquivalenzanalyse
Phase 31  Erweiterter Gap Report
Phase 32+ Fachthemen einzeln vervollständigen
```

Eine spätere Phase darf nur vorgezogen werden, wenn keine Abhängigkeit zu offenen früheren Phasen besteht, die frühere Phase nicht umgangen wird und die Abweichung dokumentiert ist.

---

## 18. Abschlussauftrag an den Coding-Agenten

Beginne mit Phase 16.

Führe nicht sofort neue DM01/DMAV-Mappings ein.

Nutze DM01↔DMAV während der Stabilisierung als Referenzfall, Regressionstest und Architekturprüfung. Verwende die vollständigen Datensätze aus:

```text
src/test/data/DMAV_Version_1_1
```

zunächst für Inventar-, Reader-, Validator- und Performance-Smoke-Tests. Erzeuge erst nach den Stabilitätsphasen kleine, fachlich zusammenhängende und validierte LFP3-Fixtures.

Jede Phase muss in sich abgeschlossen sein. Liefere keine breite, oberflächliche Implementation über mehrere Phasen hinweg. Bevorzuge eine kleine, korrekt getestete und validierte Funktion gegenüber vielen nur scheinbar vorhandenen Features.

Die wichtigste Qualitätsregel lautet:

> Ein Feature gilt nicht als unterstützt, weil Konfigurationsklassen oder Methoden dafür existieren. Es gilt erst dann als unterstützt, wenn Compiler, Runtime, echte Transferdateien, Wieder-Einlesen und ilivalidator gemeinsam erfolgreich getestet sind.
