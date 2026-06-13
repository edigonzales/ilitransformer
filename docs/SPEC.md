> Historisches Arbeitsdokument. Nicht führend für den aktuellen Repo-Zustand.

# Spezifikation und Anforderungen für einen generischen INTERLIS-Transformer

**Arbeitsname:** `ilitransformer`  
**Ausgangsrepo:** `https://github.com/edigonzales/ilinexus/`  
**Bisheriger Codename:** `ilinexus`  
**Primärer Use Case:** generische Transformation von INTERLIS-Transferdaten von einem Quellmodell in ein Zielmodell, insbesondere **DM01 ↔ DMAV**  
**Stand dieser Spezifikation:** 2026-06-05
**Version dieser Datei:** phasenbasierte Neufassung  
**Zielgruppe:** LLM-Coding-Agent, der im bestehenden Java/Gradle-Repository iterativ implementiert, testet und dokumentiert.

---

## 1. Kurzfassung

Es soll ein generischer, modellbewusster INTERLIS-Transformer entstehen. Er liest Transferdaten in einem oder mehreren Quellmodellen, transformiert Objekte, Attribute, Strukturen, Referenzen, OIDs, Baskets und Geometrien gemäss einer Mapping-Konfiguration und schreibt Transferdaten in einem oder mehreren Zielmodellen.

Der Transformer ist **nicht** als ad-hoc-Konverter für ein einzelnes Modellpaar zu verstehen, sondern als wiederverwendbare Engine mit:

- Java-first Core,
- INTERLIS-Modellanalyse via `ili2c`,
- I/O via `iox-ili`,
- strengem Mapping-Compiler,
- typisierter Expression-/Funktionsschicht,
- deterministischer 2- bzw. Mehrpass-Ausführung,
- expliziter Referenzauflösung,
- guter Diagnostik,
- Validierung mit `ilivalidator`,
- und einem Spezialprofil für DM01 ↔ DMAV.

Der erste produktionsnahe fachliche Zielkorridor soll **DM01 nach DMAV und DMAV nach DM01** sein. Dafür wird die Datei `DMAV_Korrelationstabelle_20260301.xlsx` ins Repo kopiert und als **Mapping-Hint-Quelle** verwendet. Sie ist keine direkt ausführbare Mapping-Spezifikation, sondern eine fachliche Korrelationstabelle, aus der Mapping-Vorschläge generiert und manuell/maschinell validiert werden.


### 1.1 Verbindliches Phasenprinzip

Die Umsetzung muss strikt phasenweise erfolgen. Jede Phase ist ein in sich abgeschlossenes Inkrement mit einem lauffähigen Artefakt, automatisierten Tests und klaren Akzeptanzkriterien. Der LLM-Coding-Agent darf keine Phase als „fertig“ betrachten, wenn der Build oder die für diese Phase definierten Tests fehlschlagen.

Für jede Phase gilt:

- Der Hauptzweig des Repositories muss nach Abschluss der Phase kompilieren.
- Es muss mindestens ein nutzbares Artefakt entstehen: CLI-Befehl, Library-API, Report, generierte Datei, validierter Transformationslauf oder Dokumentation mit Testbezug.
- Die Phase muss Unit-Tests enthalten; ab Phase 5 zusätzlich Integrations- oder Golden-Tests.
- Fachlich riskante Annahmen müssen im Report oder in `docs/open-questions.md` dokumentiert werden.
- Nicht fertig implementierte Funktionalität darf nicht stillschweigend „halb funktionieren“, sondern muss explizit als `TODO`, `unsupported` oder Diagnostic sichtbar sein.
- Eine spätere Phase darf die Tests früherer Phasen nicht brechen.

Der detaillierte Phasenplan steht in Abschnitt 18. Dieser Abschnitt ist für den Coding-Agenten verbindlicher als lose formulierte Wunschlisten in anderen Abschnitten.

---

## 2. Namensvorschlag

Der aktuelle Name `ilinexus` ist als Codename brauchbar, aber für ein öffentlich auffindbares Werkzeug nicht selbsterklärend genug.

Empfohlener Name:

```text
ilitransformer
```

Begründung:

- sofort verständlich,
- gute Auffindbarkeit,
- passt zu CLI: `ilitransformer` oder kürzer `ili-transform`,
- neutral genug für generische Modelltransformationen,
- nicht auf DM01/DMAV beschränkt.

Alternative Namen:

| Name | Einschätzung |
|---|---|
| `ilitransformer` | bevorzugt, klar und generisch |
| `ili-transform` | gut als CLI-Name |
| `interlis-transformer` | sehr klar, aber länger |
| `ili-mapper` | gut, aber stärker DSL-/Mapping-fokussiert |
| `ili-bridge` | nett, aber weniger technisch präzise |
| `ili-crosswalk` | passend zu Korrelationstabellen, aber weniger generisch |
| `ilinexus` | als Codename ok, als Toolname weniger klar |

**Anforderung:** Das Tool- und Artefakt-Branding soll `ilitransformer` heissen. Das Java-Package soll `guru.interlis.transformer` bleiben. Ein externer Repository-Rename bleibt optional und getrennt von fachlichen Änderungen.

---

## 3. Ausgangslage im bestehenden Repository

Das bestehende Repository enthält bereits einen sinnvollen Scaffold:

- Gradle Java-Projekt,
- Java Toolchain 25,
- `ch.interlis:iox-ili`,
- `ch.interlis:ili2c-core`,
- `ch.interlis:ili2c-tool`,
- YAML-Konfiguration via Jackson,
- CLI-Einstieg,
- `JobRunner`,
- `InterlisModelLoader`,
- `InterlisIoFactory`,
- einfache `TransformationEngine`,
- einfache `ExpressionEngine`,
- `StateStore` und `InMemoryStateStore`,
- `DiagnosticCollector`,
- erste Unit Tests.

### 3.1 Positiv beizubehalten

Die folgenden Designentscheidungen sind richtig und sollen weitergeführt werden:

1. **Java-first statt Polyglot-first**  
   Robustheit, Testbarkeit und Nähe zu den bekannten INTERLIS-Werkzeugen sind wichtiger als maximale Sprachenvielfalt.

2. **Compiler und Runtime trennen**  
   Mappings sollen früh analysiert, typisiert und validiert werden, bevor grosse Transferdateien verarbeitet werden.

3. **2-Pass-Referenzauflösung als Kernkonzept**  
   Referenzen dürfen nicht „zufällig“ in Eingabereihenfolge funktionieren müssen. Quellobjekte, Zielobjekte und OID-Mappings müssen indiziert werden.

4. **I/O-Adapter kapseln**  
   ITF, XTF und spätere Formate sollen über eine gemeinsame Adapter-/Event-Schicht verarbeitet werden.

5. **StateStore abstrahieren**  
   Ein InMemory-StateStore ist für MVP und Tests gut. Ein persistenter StateStore soll später möglich bleiben.

6. **Diagnostik als First-Class-Konzept**  
   Fehler, Warnungen, Mapping-Regel, Source-Kontext, Target-Kontext und Verbesserungsvorschläge müssen strukturiert ausgegeben werden.

### 3.2 Kritische Schwächen im aktuellen Scaffold

Die folgenden Punkte müssen gezielt behoben werden:

1. **MappingCompiler validiert noch fast nur YAML-Struktur**  
   Er muss gegen das INTERLIS-Metamodell prüfen: Klassen, Attribute, Rollen, Typen, Kardinalitäten, Enums, Strukturen, Geometrien.

2. **ExpressionEngine ist nur Platzhalter**  
   Momentan werden einfache Literale, `${alias.attr}` und ein minimales `if(...)` unterstützt. Für DM01↔DMAV reicht das nicht.

3. **Alles wird als String gesetzt**  
   Zielwerte müssen typisiert werden: Text, Zahl, Boolean, Date, XMLDateTime, Enum, Coord, Polyline, Surface/Area, Reference, Structure, BAG OF STRUCTURE.

4. **YAML-Beispiel und Java-Modell sind nicht konsistent**  
   Im Blueprint wird `class:` verwendet, Java verwendet `clazz`. Das muss sauber über `@JsonProperty("class")` oder ein anderes DSL-Feld gelöst werden.

5. **Multi-Input über String-Contains ist falsch**  
   `av1|av2` und `contains(...)` ist fragil. Inputs müssen als Liste modelliert werden.

6. **Referenzauflösung ist nicht role-aware**  
   Die Engine muss wissen, welche Quellrolle auf welche Quellklasse zeigt und welche Zielrolle/Association daraus entsteht.

7. **OID-Strategie wird noch nicht wirklich umgesetzt**  
   DMAV verwendet UUID-OIDs. Fortlaufende Longs sind für DMAV-Ausgaben voraussichtlich nicht valide.

8. **Keine Join-/Merge-/Split-Semantik**  
   Viele echte Transformationen sind nicht 1:1. DM01↔DMAV benötigt Joins, Aufspaltungen, Zusammenführungen und abgeleitete Objekte.

---

## 4. Zielbild

Der Transformer soll langfristig folgende Arten von Transformationen unterstützen:

```text
1 Input  -> 1 Output
n Inputs -> 1 Output
1 Input  -> n Outputs
n Inputs -> m Outputs
```

Dabei können Input und Output unterschiedliche INTERLIS-Versionen und Transferformate verwenden:

```text
ITF / INTERLIS 1  -> XTF / INTERLIS 2.x
XTF / INTERLIS 2.x -> ITF / INTERLIS 1
XTF / INTERLIS 2.x -> XTF / INTERLIS 2.x
ITF / INTERLIS 1  -> ITF / INTERLIS 1
```

Die Transformation soll durch eine deklarative Mapping-Datei gesteuert werden. Diese Mapping-Datei soll durch einen Mapping-Compiler in einen typisierten Ausführungsplan übersetzt werden.

---

## 5. Fachlicher Kern-Use-Case: DM01 ↔ DMAV

### 5.1 Relevante Modellquellen

Die Spezifikation soll sich auf diese offiziellen Modellquellen beziehen:

```text
DM01:
https://models.geo.admin.ch/V_D/DM.01-AV-CH_LV95_24d_ili1.ili

DMAV Gesamtmodell / Umbrella:
https://models.geo.admin.ch/V_D/DMAVTYM_Alles_V1_1.ili
```

Das DMAV-Umbrella-Modell `DMAVTYM_Alles_V1_1` importiert die eigentlichen Fachmodelle, u.a.:

```text
DMAV_Bodenbedeckung_V1_1
DMAV_DauerndeBodenverschiebungen_V1_1
DMAV_Dienstbarkeitsgrenzen_V1_1
DMAV_Einzelobjekte_V1_1
DMAV_FixpunkteAVKategorie3_V1_1
DMAV_Gebaeudeadressen_V1_1
DMAV_Grundstuecke_V1_1
DMAV_HoheitsgrenzenAV_V1_0
DMAV_Nomenklatur_V1_1
DMAV_Rohrleitungen_V1_1
DMAV_Toleranzstufen_V1_1
DMAVSUP_UntereinheitGrundbuch_V1_1
FixpunkteLV_V1_0
KGKCGC_FPDS2_V1_1
HoheitsgrenzenLV_V1_0
OfficialIndexOfLocalities_V1_0
```

Wichtige Einzelmodelle für frühe Phasen:

```text
https://models.geo.admin.ch/V_D/DMAV_FixpunkteAVKategorie3_V1_1.ili
https://models.geo.admin.ch/V_D/DMAV_Grundstuecke_V1_1.ili
https://models.geo.admin.ch/V_D/DMAV_Bodenbedeckung_V1_1.ili
https://models.geo.admin.ch/V_D/DMAV_Einzelobjekte_V1_1.ili
```

### 5.2 Korrelationstabelle

Die Datei soll ins Repo kopiert werden, z.B. nach:

```text
src/main/resources/dmav/DMAV_Korrelationstabelle_20260301.xlsx
```

oder besser, weil sie fachliche Quellen-/Inputdaten darstellt:

```text
docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx
```

Zusätzlich soll ein maschinenlesbarer Export erzeugt werden:

```text
src/main/resources/dmav/correlation-hints.json
src/main/resources/dmav/correlation-hints.csv
```

Die XLSX enthält gemäss Analyse folgende Sheets:

| Sheet | Zeilen | Spalten | Bedeutung |
|---|---:|---:|---|
| `Erklärungen` | 179 | 4 | Erläuterungen zur Korrelationstabelle |
| `Transformation` | 1204 | 35 | zentrale fachliche Übergangstabelle |
| `DMAV_Doku` | 483 | 34 | DMAV-Objektkatalog/Dokumentation |
| `DMAV_Modell` | 838 | 54 | DMAV-Modellstruktur |
| `DMAV_Dienste` | 140 | 53 | DMAV-Dienste |
| `DM01-AV-CH` | 834 | 26 | DM01-Struktur |
| `Korrelation` | 1197 | 11 | verstecktes Korrelationssheet |

Das Sheet `Transformation` ist zentral. Relevante Spalten:

| Excel-Spalte | Index | Bedeutung |
|---|---:|---|
| A | 1 | Nr. |
| J | 10 | Link |
| K | 11 | DMAV-Typ |
| L | 12 | DMAV-Topic |
| M | 13 | DMAV-Class |
| N | 14 | DMAV-Attribut |
| O | 15 | DMAV-Structure |
| P | 16 | DMAV-Substructure |
| R | 18 | DMAV-Bemerkung |
| T | 20 | Bedingung/Voraussetzung in DM01 |
| U | 21 | Transformationscode DM01→DMAV |
| V | 22 | Zielklasse/-attribut DM01→DMAV |
| W | 23 | Ergänzung DM01→DMAV |
| Y | 25 | Bedingung/Voraussetzung in DMAV |
| Z | 26 | Transformationscode DMAV→DM01 |
| AA | 27 | Zieltabelle/-attribut DMAV→DM01 |
| AB | 28 | Ergänzung DMAV→DM01 |
| AD | 30 | Bemerkungen |
| AF | 32 | DM01-Link |
| AG | 33 | DM01-Topic |
| AH | 34 | DM01-Tabelle |
| AI | 35 | DM01-Attribut |

Achtung: Die Kopfzeilen sind in der XLSX teilweise verschoben/zusammengeführt. Die Implementierung soll die Spalten über feste Indizes und zusätzlich über Header-Heuristiken erkennen. Tests müssen sicherstellen, dass künftige kleine Strukturänderungen nicht unbemerkt zu falschem Parsing führen.

### 5.3 Interpretation der Korrelationstabelle

Die Korrelationstabelle ist **keine direkt ausführbare Mapping-Datei**. Sie soll als Quelle für Mapping-Hints verwendet werden.

Sie liefert Hinweise zu:

- fachlich korrespondierenden Klassen/Attributen,
- Bedingungen,
- Kopier-/Verschiebe-/Integrationshinweisen,
- Defaults und Ergänzungen,
- Hinweisen auf Mehrfachziele,
- Hinweisen auf Strukturen/BAGs,
- Richtung DM01→DMAV,
- Richtung DMAV→DM01.

Sie liefert nicht vollständig:

- OID-Strategien,
- Basket-Strategien,
- Ausführungsreihenfolge,
- Join-Kardinalitäten,
- vollständige Referenzauflösung,
- vollständige Typkonvertierung,
- Validator-konforme Defaults,
- Verlust-/Rundtrip-Semantik,
- vollständige Geometrie-/Topologie-Operationen.

**Anforderung:** Der Coding-Agent soll eine Importkomponente bauen, die die XLSX in neutrale `CorrelationHint`-Objekte übersetzt. Diese Objekte dürfen nicht direkt ausgeführt werden. Sie dienen einem Mapping-Generator als Input.

Beispielmodell:

```java
public record CorrelationHint(
    int rowNumber,
    Direction direction,
    String sourceTopic,
    String sourceClass,
    String sourceAttribute,
    String targetPath,
    String conditionText,
    String transformCode,
    String additionText,
    String comment,
    double confidence,
    List<String> warnings
) {}
```

### 5.4 DM01→DMAV und DMAV→DM01 sind nicht symmetrisch

Es dürfen keine naiven inversen Mappings erzeugt werden. Die beiden Richtungen sind eigene Mapping-Profile:

```text
profiles/dm01-to-dmav/...
profiles/dmav-to-dm01/...
```

Begründung:

- DM01 ist INTERLIS 1 mit ITF und älterer Modellierungslogik.
- DMAV ist INTERLIS 2.4 mit UUID-OIDs, Associations, BAG OF STRUCTURE, Views, Constraints und neuen semantischen Feldern.
- DMAV enthält teilweise semantisch reichere oder anders normalisierte Objekte.
- DMAV→DM01 kann verlustbehaftet sein.
- DM01→DMAV braucht Defaults, Ableitungen und teilweise neue Objektbildung.

**Anforderung:** Jeder Mapping-Rule soll eine Eigenschaft `lossiness` bzw. `roundtrip` erhalten können:

```yaml
metadata:
  direction: dm01-to-dmav
  roundtrip: notGuaranteed
  lossiness: none | minor | significant | unknown
```

---

## 6. Empfohlener Scope der ersten fachlichen Phasen

### 6.1 Nicht mit allem starten

DM01↔DMAV vollständig ist zu gross für einen ersten robusten Schritt. Nicht zuerst starten mit:

- vollständiger Bodenbedeckung,
- vollständigen Grundstücken,
- vollständigen Flächentopologien,
- vollständigem Text-/Symbol-/Hilfslinienmodell,
- allen Dienstmodellen,
- vollständigem Roundtrip.

### 6.2 Empfohlener erster fachlicher Slice

Empfohlen wird der Slice:

```text
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Symbol

↔

DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3Nachfuehrung
DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3
DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.Entstehung_LFP3
```

Warum dieser Slice gut ist:

- überschaubar,
- enthält einfache Attribute,
- enthält MANDATORY-Felder,
- enthält Enums,
- enthält optionale Höhenattribute mit Constraints,
- enthält Referenz/Association `Entstehung_LFP3`,
- enthält Textposition-BAG,
- enthält Symbolorientierung,
- vermeidet zunächst komplexe Flächen-/AREA-Topologie.

### 6.3 Zweiter fachlicher Slice

Danach:

```text
DM01AVCH24LV95D.FixpunkteKategorie3.HFP3Nachfuehrung
DM01AVCH24LV95D.FixpunkteKategorie3.HFP3
DM01AVCH24LV95D.FixpunkteKategorie3.HFP3Pos

↔

DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.HFP3Nachfuehrung
DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.HFP3
DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.Entstehung_HFP3
```

### 6.4 Dritter fachlicher Slice

Danach gezielt:

```text
DM01 Liegenschaften.Grenzpunkt
↔
DMAV_Grundstuecke_V1_1.Grundstuecke.Grenzpunkt
```

Dieser Slice ist fachlich wichtig, weil Grenzpunkte sowohl für Grundstücke als auch für Hoheitsgrenzen und Fixpunkte relevant sind.

### 6.5 Spätere Slices

Weitere Slices:

1. `Grundstuecke.Grundstueck` + `Liegenschaft`
2. `SelbstaendigesDauerndesRecht`
3. `Bergwerk`
4. Bodenbedeckung ohne AREA-Roundtrip
5. Bodenbedeckung mit AREA/Topology
6. Einzelobjekte
7. Nomenklatur
8. Gebäudeadressen
9. Toleranzstufen
10. Rohrleitungen
11. Hoheitsgrenzen

---

## 7. Architektur

### 7.1 Zielarchitektur

```text
+---------------------------+
| CLI / API / Gradle Task   |
+------------+--------------+
             |
             v
+---------------------------+
| Job Orchestrator          |
+------------+--------------+
             |
             v
+---------------------------+        +----------------------------+
| Model Service             |<------>| INTERLIS ili2c facade      |
| Type System Facade        |        | TransferDescription        |
+------------+--------------+        +----------------------------+
             |
             v
+---------------------------+
| Mapping Loader            |
| YAML/JSON -> AST          |
+------------+--------------+
             |
             v
+---------------------------+
| Mapping Compiler          |
| AST -> TypedPlan          |
+------------+--------------+
             |
             v
+---------------------------+
| Execution Engine          |
| Pass 0 / Pass 1 / Pass 2  |
+------+----------+---------+
       |          |
       v          v
+-------------+  +----------------+
| StateStore  |  | Diagnostics    |
+------+------+  +----------------+
       |
       v
+---------------------------+
| INTERLIS I/O adapters     |
| ITF / XTF reader/writer   |
+---------------------------+
```

### 7.2 Empfohlene Package-Struktur

```text
src/main/java/guru/interlis/transformer/
  app/
    CliMain.java
    JobRunner.java
  cli/
    TransformCommand.java
    GenerateMappingCommand.java
    ValidateMappingCommand.java
    InspectModelCommand.java
    ImportCorrelationCommand.java
  config/
    JobConfig.java
    MappingConfig.java
    DslVersion.java
  mapping/
    ast/
      RawMapping.java
      RawRule.java
      RawExpression.java
    compiler/
      MappingCompiler.java
      CompilerContext.java
      CompilerDiagnostic.java
      TypeChecker.java
      RuleCompiler.java
      ExpressionCompiler.java
    plan/
      TransformPlan.java
      RulePlan.java
      SourcePlan.java
      TargetPlan.java
      AssignmentPlan.java
      RefPlan.java
      JoinPlan.java
      BasketPlan.java
  model/
    IliModelService.java
    TypeSystemFacade.java
    IliPath.java
    IliClassRef.java
    IliAttributeRef.java
    IliRoleRef.java
    IliType.java
    RoleResolver.java
    EnumResolver.java
    ConstraintInspector.java
  expr/
    ExpressionEngine.java
    ExpressionParser.java
    ExpressionPlan.java
    FunctionRegistry.java
    EvalContext.java
    Value.java
    ValueType.java
    builtins/
      StringFunctions.java
      DateFunctions.java
      EnumFunctions.java
      GeometryFunctions.java
      InterlisFunctions.java
  engine/
    ExecutionEngine.java
    ExecutionContext.java
    SourceScanner.java
    ObjectBuilder.java
    ReferenceResolver.java
    BasketRouter.java
    WriterCoordinator.java
  state/
    StateStore.java
    InMemoryStateStore.java
    PersistentStateStore.java
    SourceRecord.java
    TargetRecord.java
    IdMapping.java
    DeferredRef.java
    ObjectIndex.java
  interlis/
    InterlisModelLoader.java
    InterlisIoFactory.java
    IoxEventReader.java
    IoxEventWriter.java
    IomObjectFactory.java
    IomValueAccessor.java
  geometry/
    GeometryAdapter.java
    IoxGeometryAdapter.java
    CoordAdapter.java
    PolylineAdapter.java
    SurfaceAdapter.java
    AreaTopologyAdapter.java
    LineAttrAdapter.java
  dmav/
    CorrelationWorkbookImporter.java
    CorrelationHint.java
    CorrelationHintExporter.java
    MappingCandidateGenerator.java
    Dm01DmavProfiles.java
  diag/
    Diagnostic.java
    DiagnosticCollector.java
    DiagnosticCode.java
    Severity.java
    DiagnosticReporter.java
  validate/
    IlivalidatorRunner.java
    ValidationReport.java
  util/
```

---

## 8. Mapping-DSL

### 8.1 Prinzipien

Die Mapping-DSL soll:

- deklarativ sein,
- YAML als primäres Format verwenden,
- versioniert sein,
- typisiert kompiliert werden,
- keine versteckte Magie enthalten,
- Expressions nur dort verwenden, wo sie nötig sind,
- Joins, Filters, Object Creation, Defaults, Ref-Auflösung und BAG/STRUCTURE explizit modellieren,
- für generierte Mappings geeignet sein,
- für manuelle Korrekturen lesbar bleiben.

### 8.2 Beispiel eines Mapping-Jobs

```yaml
version: 1

job:
  name: dm01-to-dmav-lfp3
  description: "Pilot-Transformation DM01 FixpunkteKategorie3.LFP3 nach DMAV FixpunkteAVKategorie3.LFP3"
  direction: dm01-to-dmav
  failPolicy: strict

  modeldir:
    - "https://models.geo.admin.ch/"
    - "models/"

  inputs:
    - id: dm01
      path: "input/dm01.itf"
      model: "DM01AVCH24LV95D"
      format: "itf"

  outputs:
    - id: dmav
      path: "build/out/dmav-lfp3.xtf"
      model: "DMAV_FixpunkteAVKategorie3_V1_1"
      format: "xtf"

mapping:
  oidStrategy:
    default: deterministicUuid
    namespace: "dm01-to-dmav-lfp3"

  basketStrategy:
    default: preserveOrGenerateUuid

  rules:
    - id: lfp3-nachfuehrung
      target:
        output: dmav
        class: "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3Nachfuehrung"
      sources:
        - alias: nf
          input: dm01
          class: "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung"
      identity:
        sourceKey: ["nf.NBIdent", "nf.Identifikator"]
      assign:
        NBIdent: "nf.NBIdent"
        Identifikator: "nf.Identifikator"
        Beschreibung: "truncate(nf.Beschreibung, 60)"
        Perimeter: "nf.Perimeter"
        GueltigerEintrag: "toXmlDateTime(coalesce(nf.GueltigerEintrag, nf.Datum1, job.defaultDate))"

    - id: lfp3
      target:
        output: dmav
        class: "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3"
      sources:
        - alias: p
          input: dm01
          class: "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3"
      identity:
        sourceKey: ["p.NBIdent", "p.Nummer"]
      assign:
        NBIdent: "p.NBIdent"
        Nummer: "p.Nummer"
        LFPArt: "#LFP3"
        Geometrie: "p.Geometrie"
        Hoehengeometrie: "p.HoeheGeom"
        Lagegenauigkeit: "p.LageGen"
        IstLagezuverlaessig: "enumMap(p.LageZuv, 'Zuverlaessigkeit_DM01_DMAV')"
        Hoehengenauigkeit: "p.HoeheGen"
        IstHoehenzuverlaessig: "enumMap(p.HoeheZuv, 'Zuverlaessigkeit_DM01_DMAV')"
        Punktzeichen: "enumMap(p.Punktzeichen, 'Versicherungsart_DM01_DMAV')"
        Grenzpunktfunktion: "#keine"
        AktiverUnterhalt: "true"
        SymbolOri: "lookupOne('lfp3-symbol', p).Ori"
      bags:
        Textposition:
          from:
            input: dm01
            class: "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos"
            alias: pos
            where: "refEquals(pos.LFP3Pos_von, p)"
          structure: "DMAVTYM_Grafik_V1_0.Textposition"
          assign:
            Position: "pos.Pos"
            Orientierung: "pos.Ori"
            HReferenzpunkt: "enumMap(pos.HAli, 'HAlignment_DM01_DMAV')"
            VReferenzpunkt: "enumMap(pos.VAli, 'VAlignment_DM01_DMAV')"
      refs:
        - association: "Entstehung_LFP3"
          role: "Entstehung"
          targetObject:
            rule: lfp3-nachfuehrung
            sourceRef: "p.Entstehung"
```

### 8.3 DSL-Felder

#### `version`

Pflichtfeld. Muss bei Breaking Changes erhöht werden.

#### `job.inputs[]`

Pflichtfelder:

```yaml
id: dm01
path: input.itf
model: DM01AVCH24LV95D
format: itf
```

`format` kann optional aus Dateiendung erkannt werden, soll aber explizit erlaubt sein.

#### `job.outputs[]`

Pflichtfelder:

```yaml
id: dmav
path: output.xtf
model: DMAV_FixpunkteAVKategorie3_V1_1
format: xtf
```

#### `mapping.oidStrategy`

Mögliche Strategien:

| Strategie | Bedeutung |
|---|---|
| `preserve` | Source-OID wird übernommen, falls im Zieltyp erlaubt |
| `integer` | fortlaufende Integer-OIDs, nur wenn Zielmodell passend |
| `uuid` | zufällige UUIDs |
| `deterministicUuid` | stabile UUID aus Namespace + Source-Key |
| `external` | OID kommt aus Expression |

Für DMAV soll defaultmässig `deterministicUuid` verwendet werden.

#### `mapping.basketStrategy`

Mögliche Strategien:

| Strategie | Bedeutung |
|---|---|
| `preserve` | Basket-ID übernehmen, falls gültig |
| `generateUuid` | neue Basket-ID erzeugen |
| `preserveOrGenerateUuid` | übernehmen, wenn gültig, sonst generieren |
| `byTopic` | pro Zieltopic eigener Basket |
| `expression` | Basket-ID aus Expression |

#### `rules[]`

Eine Rule erzeugt Zielobjekte oder Zielstrukturen.

Pflichtfelder:

```yaml
id: eindeutige-id
target.output: output-id
target.class: qualifizierte INTERLIS-Klasse
sources: mindestens eine Source
```

Optionale Felder:

```yaml
where: Filterexpression
identity: Source-Key / Target-OID-Regel
join: Join-Definition
assign: Attributzuweisungen
refs: Referenzen / Associations
bags: BAG OF STRUCTURE
metadata: Dokumentation, Korrelationstabellenbezug, Lossiness
```

### 8.4 Regeln für generierte Mapping-Dateien

Der Mapping-Generator darf YAML mit TODOs erzeugen, aber solche Mappings dürfen nicht ohne Review im strict mode laufen.

Beispiel:

```yaml
assign:
  IstHoheitsgrenzsteinAlt:
    expr: "TODO('aus Korrelationstabelle unklar')"
    confidence: 0.2
    sourceHint:
      file: "DMAV_Korrelationstabelle_20260301.xlsx"
      sheet: "Transformation"
      row: 350
```

Der Compiler muss `TODO(...)` als Fehler behandeln, ausser der Job läuft explizit im Modus:

```yaml
compileMode: allowTodos
```

---

## 9. Expression- und Funktionsschicht

### 9.1 Grundsatz

Die Expression-Schicht soll keine unkontrollierte Programmiersprache sein. Sie soll klein, kontrolliert, testbar und typisiert sein.

Zwei mögliche Implementierungswege:

1. **Eigene kleine Expression-Engine ausbauen**  
   Vorteil: maximale Kontrolle. Nachteil: Aufwand.

2. **Apache Commons JEXL oder vergleichbare Java Expression Language einbetten**  
   Vorteil: viel Funktionalität schnell verfügbar. Nachteil: Security/Sandboxing und Typisierung müssen sauber gebaut werden.

Empfehlung:

- Kurzfristig: eigene minimale AST-basierte Expression-Engine erweitern.
- Mittelfristig: prüfen, ob JEXL sinnvoll ist.
- Unabhängig davon: alle INTERLIS-spezifischen Funktionen über eine eigene `FunctionRegistry` anbieten.

### 9.2 Muss-Funktionen

#### Basis

```text
defined(value)
notDefined(value)
coalesce(a, b, c, ...)
if(condition, a, b)
default(value, fallback)
null()
TODO(message)
```

#### String

```text
concat(a, b, ...)
substring(value, start, length)
left(value, length)
right(value, length)
trim(value)
normalizeWhitespace(value)
replace(value, pattern, replacement)
truncate(value, maxLength)
```

#### Zahlen

```text
toInteger(value)
toDecimal(value)
round(value, scale)
abs(value)
```

#### Boolean

```text
toBoolean(value)
not(value)
and(a, b)
or(a, b)
```

#### Datum/Zeit

```text
toDate(value)
toXmlDateTime(value)
dateAtStartOfDay(value)
now()
```

DM01 `DATE` nach DMAV `INTERLIS.XMLDateTime` muss explizit behandelt werden.

#### Enum

```text
enumMap(value, mapName)
enumDefault(value, fallback)
enumName(value)
```

Enum-Mappings sollen nicht hart in Expressions stehen, sondern in separaten Mapping-Tabellen:

```yaml
enums:
  Zuverlaessigkeit_DM01_DMAV:
    ja: true
    nein: false
```

oder bei Ziel-Enum:

```yaml
enums:
  Versicherungsart_DM01_DMAV:
    Stein: Stein
    Kunststoffzeichen: Kunststoffzeichen
    Bolzen: Bolzen
    Rohr: Rohr
    Pfahl: Pfahl
    Kreuz: Kreuz
    unversichert: unversichert
    weitere: TODO
```

#### Referenzen und Joins

```text
refOid(object.role)
refEquals(object.role, otherObject)
lookupOne(indexName, source)
lookupMany(indexName, source)
joinKey(values...)
```

#### Geometrie

```text
coord(value)
polyline(value)
surface(value)
area(value)
multiLine(value)
lineAttrs(value)
geomEquals(a, b, tolerance)
geomCovers(surface, line)
geomTransform(value, sourceCrs, targetCrs)
```

Initial sollen nur Pass-through-Operationen für gleiche LV95-Geometrien umgesetzt werden. Topologische Funktionen können später folgen.

### 9.3 Typisierte Values

Die Expression Engine darf nicht einfach `Object` oder `String` zurückgeben. Sie soll intern einen typisierten Value-Layer verwenden:

```java
sealed interface Value permits TextValue, NumberValue, BooleanValue, DateValue, XmlDateTimeValue,
    EnumValue, CoordValue, GeometryObjectValue, ReferenceValue,
    StructureValue, BagValue, NullValue {}
```

Der MappingCompiler soll prüfen:

```text
ExpressionType <= TargetAttributeType
```

Beispiel:

```text
TextValue -> TEXT*12: nur erlaubt, wenn Länge <= 12 oder truncate/explizite Policy vorhanden.
DateValue -> XMLDateTime: erlaubt nur mit impliziter oder expliziter Konvertierung? Offene Entscheidung.
EnumValue -> EnumType: nur erlaubt, wenn Zielwert existiert.
CoordValue -> Coord2: Koordinatendomain kompatibel.
```

---

## 10. INTERLIS Model Service und Type System

### 10.1 Aufgabe

Der `IliModelService` soll `TransferDescription` aus `ili2c` kapseln und eine stabile API für den MappingCompiler anbieten.

Er muss liefern können:

- Modelle,
- Topics,
- Klassen/Tables,
- Structures,
- Attribute,
- Rollen/Associations,
- Domains,
- Enums,
- Mandatory/Optional,
- Kardinalitäten,
- OID-Typen,
- Basket-OID-Typen,
- Geometrietypen,
- Textlängen,
- numerische Bereiche,
- Constraints soweit auswertbar,
- View-Klassen und ob sie als Transferziel zulässig sind.

### 10.2 Pfadauflösung

Es soll eine zentrale `IliPath`-Klasse geben:

```java
IliPath.parse("DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3.NBIdent")
```

Sie soll Modell, Topic, Klasse, Attribut, Structure-Pfade und Rollen erkennen.

### 10.3 Compiler-Prüfungen

Der Compiler muss mindestens prüfen:

1. Input-Modell existiert.
2. Output-Modell existiert.
3. Source-Klassen existieren.
4. Target-Klassen existieren.
5. Target-Klassen sind transferierbar.
6. Target-Attribute existieren.
7. Source-Attribute existieren.
8. Rollen existieren.
9. Ref-Ausdrücke passen zum Rollentyp.
10. Expressions liefern kompatiblen Typ.
11. Mandatory-Attribute werden gesetzt oder haben Default.
12. Enum-Mappings decken alle relevanten Werte ab oder deklarieren Default/TODO.
13. Zieltextlängen werden nicht verletzt oder sind mit `truncate()`/Policy behandelt.
14. Numerische Bereiche werden nicht verletzt.
15. Geometrie-Domains sind kompatibel oder es existiert eine Transformationsfunktion.
16. OID-Strategie passt zum Zielmodell.
17. Basket-Strategie passt zum Zielmodell.
18. BAG/STRUCTURE-Zuweisungen passen zum Zielstrukturtyp.
19. Keine doppelten Target-Assignments ohne explizite Merge-Policy.
20. Keine zyklischen oder unauflösbaren Rule-Abhängigkeiten.

### 10.4 TypedPlan

Nach erfolgreicher Kompilierung soll ein `TransformPlan` entstehen. Die Runtime soll nicht mehr mit rohem YAML arbeiten.

```java
public record TransformPlan(
    JobPlan job,
    List<RulePlan> rules,
    TypeSystemSnapshot sourceTypes,
    TypeSystemSnapshot targetTypes,
    DiagnosticSummary compileDiagnostics
) {}
```

---

## 11. Execution Engine

### 11.1 Phasen

Die Engine soll nicht nur 2 Passes kennen, sondern konzeptionell diese Phasen:

```text
Pass 0: Source Scan / Indexing
Pass 1: Target Object Creation + primitive Assignments
Pass 2: Reference Resolution + Associations + BAG finalization
Pass 3: Validation / Diagnostics / Writer ordering
Pass 4: Write Transfer
```

Für einfache Transformationen können Phasen zusammenfallen. Die Architektur soll sie aber trennen.

### 11.2 Pass 0: Source Scan

Aufgaben:

- alle Inputdateien lesen,
- Baskets erkennen,
- Source-Objekte speichern/indexieren,
- Source-OIDs speichern,
- Source-Objektpfade für Diagnostik erfassen,
- optionale Indexe für Joins aufbauen.

Kontext pro SourceRecord:

```java
public record SourceRecord(
    String inputId,
    String basketId,
    String topic,
    String className,
    String oid,
    IomObject object,
    SourcePath path
) {}
```

### 11.3 Pass 1: Zielobjekte erzeugen

Aufgaben:

- pro Rule passende Source-Kombinationen finden,
- `where` auswerten,
- Ziel-OID erzeugen,
- Zielobjekt erzeugen,
- primitive Attribute setzen,
- IdMapping speichern,
- DeferredRefs speichern,
- BAG/STRUCTURE je nach Typ sofort oder später erzeugen,
- Rule-Ausführung protokollieren.

### 11.4 Pass 2: Referenzen und Associations

Aufgaben:

- DeferredRefs auflösen,
- Associations schreiben,
- Rollen setzen,
- Ambiguitäten diagnostizieren,
- fehlende Pflichtreferenzen als Fehler oder Warnung behandeln,
- referenzierte Zielklasse prüfen.

Auflösungsreihenfolge:

1. Exakt: SourceClass + SourceOid + InputId + BasketId
2. Input-weit: SourceClass + SourceOid + InputId
3. Basket-weit: SourceClass + SourceOid + BasketId
4. Global: SourceClass + SourceOid, aber nur falls explizit erlaubt
5. Bei mehr als einem Treffer: `ILITRF-REF-AMBIGUOUS`
6. Bei keinem Treffer: `ILITRF-REF-UNRESOLVED`

### 11.5 Pass 3: Validierung und Writer Ordering

Vor dem Schreiben:

- Mandatory-Attribute prüfen,
- Zielobjekte gegen bekannte Type-Regeln prüfen,
- Schreibreihenfolge bestimmen,
- Basket-Zuordnung prüfen,
- Views nicht schreiben,
- Associations korrekt schreiben,
- Referenzintegrität prüfen.

### 11.6 Pass 4: Schreiben

Aufgaben:

- StartTransfer,
- StartBasket pro Zieltopic/-basket,
- Objekte in stabiler Reihenfolge,
- Associations in stabiler Reihenfolge,
- EndBasket,
- EndTransfer,
- Writer flush/close,
- optional `ilivalidator` ausführen.

---

## 12. StateStore

### 12.1 InMemoryStateStore

Für MVP und Tests.

Muss können:

- SourceRecords speichern,
- TargetRecords speichern,
- IdMappings speichern,
- DeferredRefs speichern,
- sekundäre Indexe verwalten,
- deterministische Iterationsreihenfolge gewährleisten.

### 12.2 Persistenter StateStore

Später optional, aber Architektur vorbereiten.

Mögliche Backend-Optionen:

- SQLite,
- H2,
- DuckDB,
- RocksDB,
- einfache temporäre Dateien.

**Nicht in MVP erforderlich**, aber Interfaces dürfen Persistenz nicht verbauen.

### 12.3 IdMapping

```java
public record IdMapping(
    SourceKey source,
    TargetKey target,
    String ruleId,
    String identityKey
) {}
```

```java
public record SourceKey(
    String inputId,
    String basketId,
    String className,
    String oid
) {}
```

```java
public record TargetKey(
    String outputId,
    String basketId,
    String className,
    String oid
) {}
```

---

## 13. OID-Strategien

### 13.1 Anforderungen

Der Transformer muss Ziel-OIDs erzeugen, die zum Zielmodell passen. Insbesondere DMAV verwendet UUID-OIDs.

### 13.2 Strategien

#### `preserve`

Source-OID übernehmen. Nur erlaubt, wenn:

- Source-OID vorhanden,
- Source-OID zum Ziel-OID-Typ passt,
- keine Kollision entsteht.

#### `integer`

Fortlaufende Integer-OIDs. Nur erlaubt, wenn Zielmodell Integer-/numerische OIDs erlaubt. Nicht für DMAV verwenden.

#### `uuid`

Zufällige UUID. Vorteil: einfach. Nachteil: nicht stabil bei wiederholter Transformation.

#### `deterministicUuid`

UUID aus Namespace + Source-Key + RuleId. Für DM01→DMAV bevorzugt.

Beispiel:

```text
UUIDv5(namespace="dm01-to-dmav-lfp3", name="LFP3|NBIdent|Nummer")
```

#### `external`

OID aus Expression.

```yaml
oid:
  expr: "uuidFrom(s.NBIdent, s.Nummer)"
```

### 13.3 Anforderungen an deterministische UUIDs

- gleiche Eingabe + gleiche Mapping-Version → gleiche Ziel-OID,
- unterschiedliche RuleIds → unterschiedliche Ziel-OIDs,
- Namespace in Mapping-Datei dokumentiert,
- Kollisionen diagnostizieren,
- Option für stabile Migration bei Mapping-Änderung.

---

## 14. Basket-Strategien

### 14.1 Problem

INTERLIS-Transferdaten sind in Baskets organisiert. DM01-ITF und DMAV-XTF können andere Topic-/Basket-Strukturen haben. Basket-OIDs können im Zielmodell typisiert sein.

### 14.2 Anforderungen

Der Transformer muss:

- Source-Basket-Kontext speichern,
- Zielbasket pro Zieltopic bestimmen,
- Basket-ID gemäss Zielmodell erzeugen,
- mehrere Source-Baskets auf einen Target-Basket mappen können,
- einen Source-Basket auf mehrere Target-Baskets mappen können,
- Basket-Mapping diagnostizieren.

### 14.3 DSL

```yaml
basketStrategy:
  default: byTargetTopic
  rules:
    - targetTopic: "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3"
      oidStrategy: deterministicUuid
      sourceKey: ["inputId", "sourceBasketId", "targetTopic"]
```

---

## 15. Referenzen, Rollen und Associations

### 15.1 Anforderungen

INTERLIS-Referenzen und Associations müssen modellbewusst behandelt werden.

Der RoleResolver muss:

- Quellrollen erkennen,
- Zielrollen erkennen,
- Association-Klassen erkennen,
- Rollenendennamen liefern,
- Kardinalitäten prüfen,
- Zielklasse prüfen,
- Embedded/Composition-Zusammenhänge erkennen,
- INTERLIS-1-Referenzen nach INTERLIS-2-Associations abbilden können.

### 15.2 Ref-DSL

```yaml
refs:
  - id: lfp3-entstehung
    association: "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.Entstehung_LFP3"
    role: "Entstehung"
    sourceRef: "p.Entstehung"
    targetRule: "lfp3-nachfuehrung"
    required: true
```

### 15.3 Fehlerfälle

| Code | Bedeutung |
|---|---|
| `ILITRF-REF-UNRESOLVED` | Quellreferenz konnte nicht auf Zielobjekt abgebildet werden |
| `ILITRF-REF-AMBIGUOUS` | Mehrere Zielobjekte passen |
| `ILITRF-REF-TYPE-MISMATCH` | Zielobjekt hat falsche Zielklasse |
| `ILITRF-REF-MISSING-MANDATORY` | Pflichtreferenz fehlt |
| `ILITRF-REF-CARDINALITY` | Kardinalität verletzt |

---

## 16. BAG OF STRUCTURE und Text-/Symbolpositionen

DMAV verwendet BAG OF STRUCTURE an Stellen, an denen DM01 oft separate Pos-/Symboltabellen hat.

Beispiele:

```text
DM01 LFP3Pos -> DMAV LFP3.Textposition BAG OF Textposition
DM01 LFP3Symbol.Ori -> DMAV LFP3.SymbolOri
DM01 ObjektnamePos -> DMAV Objektname.Textposition
```

### 16.1 Anforderungen

Die Engine muss können:

- Source-Tabellen via Referenz joinen,
- pro Source-Zielobjekt 0..n Structures erzeugen,
- nested Structures erzeugen,
- BAG-Kardinalitäten prüfen,
- optionale Pos-/Symbolobjekte behandeln,
- Defaultwerte für Ausrichtung/Orientierung anwenden.

### 16.2 DSL

```yaml
bags:
  Textposition:
    from:
      input: dm01
      class: "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos"
      alias: pos
      where: "refEquals(pos.LFP3Pos_von, p)"
    structure: "DMAVTYM_Grafik_V1_0.Textposition"
    assign:
      Position: "pos.Pos"
      Orientierung: "coalesce(pos.Ori, 100.0)"
      HReferenzpunkt: "enumMap(coalesce(pos.HAli, 'Left'), 'HAlignment_DM01_DMAV')"
      VReferenzpunkt: "enumMap(coalesce(pos.VAli, 'Bottom'), 'VAlignment_DM01_DMAV')"
```

---

## 17. Geometrie

### 17.1 Grundsatz

Geometrie darf nicht als Sonderfall überall im Code verteilt werden. Es braucht eine zentrale `GeometryAdapter`-API.

```java
public interface GeometryAdapter {
    Value normalize(IomObject rawGeometry, IliType sourceType);
    IomObject denormalize(Value geometry, IliType targetType);
}
```

`COORD` bleibt als eigener skalare `Value`. `POLYLINE`/`SURFACE`/`AREA` werden im Runtime-Pfad als kanonische IOM-Geometrie in `GeometryObjectValue` getragen.

### 17.2 MVP-Geometrie

Für die ersten Slices:

- Coord2 pass-through LV95,
- Coord3 / Höhe separat behandeln,
- Surface-Perimeter pass-through, wenn gleicher Typ möglich,
- keine Topologie-Reparatur.

### 17.3 Spätere Geometrieanforderungen

Für Grundstücke/Bodenbedeckung später:

- AREA vs SURFACE,
- WITHOUT OVERLAPS Toleranzen,
- MULTILINE für streitige Grenzen,
- LINEATTR aus DMAV zurück nach DM01,
- Topologie-Constraints wie `INTERLIS.areAreas`,
- `covers(surface, line)`-Prüfung,
- Kurven/ARCS erhalten,
- Hilfstabellen/Segmentlogik aus ITF korrekt behandeln.

### 17.4 Geometrie-Diagnostik

| Code | Bedeutung |
|---|---|
| `ILITRF-GEOM-TYPE-MISMATCH` | Geometrietyp passt nicht |
| `ILITRF-GEOM-CRS-MISMATCH` | CRS passt nicht |
| `ILITRF-GEOM-INVALID` | Geometrie ungültig |
| `ILITRF-GEOM-TOPOLOGY` | Topologiebedingung verletzt |
| `ILITRF-GEOM-LINEATTR-UNSUPPORTED` | LINEATTR noch nicht unterstützt |

---

## 18. Phasenplan der Umsetzung

Dieser Abschnitt ist der verbindliche Umsetzungsplan für den LLM-Coding-Agenten. Jede Phase muss ein lauffähiges, überprüfbares Inkrement liefern. Eine Phase ist erst abgeschlossen, wenn alle Akzeptanzkriterien erfüllt sind, der Build grün ist und die definierten Tests automatisiert laufen.

### 18.1 Allgemeine Regeln für alle Phasen

Für jede Phase gilt:

- Die Implementierung muss auf dem aktuellen Stand des Repositories aufbauen und darf keine früheren Tests brechen.
- Neue Funktionalität muss mit Unit-Tests abgedeckt werden.
- Ab Phase 5 müssen zusätzlich Integrations- oder Golden-Tests vorhanden sein, sofern Transferdaten gelesen oder geschrieben werden.
- Jede Phase muss eine kurze Dokumentation oder einen Report erzeugen, wenn fachliche Annahmen getroffen werden.
- Nicht unterstützte Fälle müssen explizit diagnostiziert werden; stilles Ignorieren ist nicht zulässig.
- Fehlerfälle müssen getestet werden, nicht nur der Erfolgsfall.
- Die CLI muss bei Fehlern einen Nicht-Null-Exit-Code liefern, sobald die Phase CLI-Funktionalität betrifft.
- Der Agent soll kleine, reviewbare Commits erzeugen: möglichst ein Commit pro klarer Arbeitseinheit.
- Die Datei `docs/open-questions.md` muss ergänzt werden, wenn während der Phase eine fachliche oder technische Frage offen bleibt.

Empfohlene Standardbefehle pro Phase:

```bash
./gradlew clean test
./gradlew check
./gradlew run --args="--help"
```

Sobald Integrations- oder Golden-Tests vorhanden sind:

```bash
./gradlew integrationTest
./gradlew goldenTest
```

Falls diese Tasks noch nicht existieren, müssen sie in der dafür vorgesehenen Phase eingeführt werden.

### 18.2 Phasenübersicht

| Phase | Titel | Hauptartefakt | Testniveau |
|---:|---|---|---|
| 0 | Baseline, Repository-Hygiene und Namensentscheid | grüner Build, dokumentierter Ausgangszustand | Unit |
| 1 | DSL-/Config-Modell stabilisieren | validierbare YAML-Konfiguration, CLI-Skeleton | Unit |
| 2 | INTERLIS Model Service und Inventory | Modellinventar als JSON/Markdown | Unit + kleine Integration |
| 3 | Typed Mapping Compiler | typisierter Mapping-Plan und Compiler-Diagnostics | Unit |
| 4 | Expression Engine und Function Registry | typisierte Expression-Auswertung | Unit |
| 5 | Runtime MVP für 1:1 Scalar Mapping | funktionierender Transformationslauf XTF/ITF → XTF/ITF für einfache Modelle | Integration + Golden |
| 6 | OID-, Basket- und Writer-Strategien | deterministische OIDs, Basket-Routing, geordnete Ausgabe | Integration + Golden |
| 7 | Referenzen, Rollen und Associations | DeferredRef-Auflösung mit RoleResolver | Integration + Golden |
| 8 | XLSX-Korrelation importieren | `correlation-hints.json` und Import-Report | Unit + Integration |
| 9 | Mapping-Kandidatengenerator | generierte Mapping-Vorschläge mit Confidence/TODOs | Unit + Snapshot |
| 10 | DM01→DMAV LFP3 Minimalpilot | validierbarer DMAV-LFP3-XTF-Output | Golden + ilivalidator |
| 11 | DMAV→DM01 LFP3 Minimalpilot | validierbarer DM01-ITF-Output | Golden + ilivalidator |
| 12 | BAG OF STRUCTURE und Textpositionen | LFP3-Text-/Symbolpositionen in beide Richtungen | Golden + ilivalidator |
| 13 | Geometrie-MVP | Coord/Polyline/Surface-Passthrough und Diagnostik | Integration + Golden |
| 14 | Erweiterter DM01↔DMAV-Analysebericht | Gap-Analyse für weitere Topics | Report + Snapshot |
| 15 | Stabilisierung, CLI-UX und Dokumentation | nutzbare Alpha-Version | Vollständiger Testlauf |

Die Phasen 0 bis 7 bilden den generischen Kern. Die Phasen 8 bis 14 kümmern sich spezifisch um DM01↔DMAV. Phase 15 bündelt Stabilisierung und Benutzbarkeit.

---

### 18.3 Phase 0: Baseline, Repository-Hygiene und Namensentscheid

#### Ziel

Den Ausgangszustand reproduzierbar machen, Build und Tests stabilisieren und die Namensentscheidung für Package `guru.interlis.transformer` und Deliverable `ilitransformer` festziehen.

#### Artefakt

- Grüner Gradle-Build.
- Dokumentierter Ausgangszustand in `docs/dev/baseline.md`.
- Optionaler Rename-Plan in `docs/dev/rename-plan.md`.

#### Scope

- Bestehende Tests ausführen und reparieren, falls sie wegen trivialer Inkonsistenzen fehlschlagen.
- Gradle-Konfiguration vereinheitlichen.
- README mit aktuellem Status abgleichen.
- CLI-/Artefaktname auf `ilitransformer` festlegen, ohne den externen Repository-Namen zu ändern.

#### Nicht-Scope

- Noch keine fachliche DM01↔DMAV-Transformation.
- Noch kein Umbau der Engine.
- Noch kein Expression-Sprachwechsel.

#### Tests

- `./gradlew clean test`
- Ein Test, der die CLI mit `--help` oder ohne Argumente startet und einen stabilen Usage-Text erwartet.

#### Akzeptanzkriterien

- Der Build ist grün.
- Die Java-Version und Abhängigkeiten sind dokumentiert.
- Der CLI-Start ist reproduzierbar.
- Offene Umbenennungsfragen sind dokumentiert, nicht im Code verstreut.

---

### 18.4 Phase 1: DSL-/Config-Modell stabilisieren

#### Ziel

Die Mapping-Konfiguration muss eine stabile, dokumentierte und testbare Struktur erhalten. YAML-Beispiele und Java-Modell müssen exakt zusammenpassen.

#### Artefakt

- `JobConfig` oder neues Config-AST mit sauberem YAML-Mapping.
- JSON Schema oder mindestens dokumentierte YAML-Spezifikation in `docs/mapping-dsl.md`.
- CLI-Befehl `validate-mapping`, der syntaktische Config-Fehler erkennt.

#### Scope

- `class` im YAML sauber mappen, z.B. mit `@JsonProperty("class")`.
- `sources[].input` durch `sources[].inputs` als Liste ersetzen oder zusätzlich unterstützen.
- `version`-Feld für Mapping-Dateien einführen.
- `rules[].id` verpflichtend machen.
- Felder für `where`, `assign`, `refs`, `create`, `joins`, `defaults`, `unsupportedPolicy` vorbereiten.
- Fehlerhafte YAML-Strukturen mit klaren Diagnostics melden.

#### Nicht-Scope

- Noch keine vollständige Typprüfung gegen INTERLIS.
- Noch keine Runtime-Transformation.

#### Tests

- Gültige Minimal-YAML wird geladen.
- YAML mit `class:` wird korrekt gelesen.
- Mehrere Inputs werden als Liste gelesen.
- Doppelte Rule-IDs werden abgelehnt.
- Fehlende Pflichtfelder erzeugen deterministische Fehlermeldungen.

#### Akzeptanzkriterien

- Mindestens drei Beispiel-Mappings liegen unter `src/test/resources/mappings/`.
- `validate-mapping` erkennt reine Config-Fehler ohne Modellkompilierung.
- Die Dokumentation enthält ein vollständiges Minimalbeispiel.

---

### 18.5 Phase 2: INTERLIS Model Service und Inventory

#### Ziel

Ein zentraler Model Service muss INTERLIS-Modelle kompilieren, Metadaten abstrahieren und ein maschinenlesbares Modellinventar erzeugen können.

#### Artefakt

- `ModelService` / `TypeSystemFacade`.
- CLI-Befehl `inspect-model`.
- JSON- und Markdown-Inventory für ein kleines Testmodell.

#### Scope

- Modelle via `ili2c` und konfigurierbare `--modeldir` kompilieren.
- Klassen, Topics, Attribute, Rollen, Domains, Enums, Kardinalitäten, Mandatory-Status, OID-Typen und Geometrietypen extrahieren.
- Eindeutige Pfade definieren: `Model.Topic.Class.Attribute`.
- Fehler beim Modellkompilieren als Diagnostics ausgeben.

#### Nicht-Scope

- Noch keine Mapping-Generierung.
- Noch keine DM01/DMAV-Speziallogik, ausser dass die Modelle kompilierbar sein sollen, wenn online/lokal verfügbar.

#### Tests

- Unit-Tests mit kleinen ILI-Testmodellen.
- Integrationstest: ein lokales Testmodell kompilieren und Inventory prüfen.
- Negativtest: unbekanntes Modell erzeugt klare Diagnostic.

#### Akzeptanzkriterien

- `inspect-model <model>` erzeugt ein stabiles JSON.
- Das Inventory enthält OID-Typen und Rolleninformationen.
- Die Ausgabe ist deterministisch sortiert.

---

### 18.6 Phase 3: Typed Mapping Compiler

#### Ziel

Aus der YAML-Konfiguration soll ein typisierter `TypedPlan` entstehen. Fehlerhafte Mappings müssen vor der Runtime erkannt werden.

#### Artefakt

- `MappingCompiler` mit Zugriff auf `TypeSystemFacade`.
- `TypedPlan`, `RulePlan`, `SourcePlan`, `AssignmentPlan`, `ReferencePlan`.
- Compiler-Report als Markdown/JSON.

#### Scope

- Source- und Target-Klassen prüfen.
- Zielattribute prüfen.
- Source-Attribute und Pfade prüfen.
- Rollen prüfen.
- Typkompatibilität einfach prüfen: Text, Numeric, Boolean, Date, Enum, Reference, Geometry.
- Mandatory-Zielattribute ohne Assignment/Default warnen oder als Fehler behandeln, je nach Policy.
- Enum-Coverage-Report erzeugen.

#### Nicht-Scope

- Noch keine vollständige Geometry-Topologie.
- Noch kein automatisches Mapping.

#### Tests

- Gültiges Mapping kompiliert zu `TypedPlan`.
- Unbekannte Zielklasse wird abgelehnt.
- Unbekanntes Zielattribut wird abgelehnt.
- Unbekanntes Source-Attribut wird abgelehnt.
- Typinkompatibilität wird gemeldet.
- Fehlendes Mandatory-Ziel wird gemeldet.

#### Akzeptanzkriterien

- Runtime darf nur noch `TypedPlan` ausführen, nicht rohes YAML.
- Compiler-Diagnostics enthalten Rule-ID, Source-Pfad, Target-Pfad und Vorschlag.

---

### 18.7 Phase 4: Expression Engine und Function Registry

#### Ziel

Expressions müssen typisiert, sicher und erweiterbar ausgewertet werden. Es darf keine beliebige Codeausführung geben.

#### Artefakt

- Expression AST oder kontrollierte Integration einer Expression-Library.
- `FunctionRegistry` mit INTERLIS-spezifischen Funktionen.
- Unit-Test-Suite für Expressions.

#### Scope

- Basisfunktionen: `if`, `coalesce`, `default`, `isNull`, `isDefined`.
- Stringfunktionen: `concat`, `substring`, `trim`, `upper`, `lower`, `replace`, `truncate`.
- Datumsfunktionen: `date`, `dateTime`, `xmlDateTime`, `today` nur optional und klar markiert nicht-deterministisch.
- Enumfunktionen: `enumMap`, `enumDefault`, `enumName`.
- Referenz-/Join-Hilfsfunktionen vorbereiten: `ref`, `join`, `lookup`.
- Expressions müssen erwarteten Zieltyp kennen.

#### Nicht-Scope

- Keine freie Java-, Groovy- oder JavaScript-Ausführung.
- Keine Netzwerkanfragen aus Expressions.

#### Tests

- Jede Muss-Funktion erhält Unit-Tests.
- Fehlerhafte Funktionsaufrufe erzeugen Diagnostic.
- Typkonversionen werden getestet.
- Nicht deterministische Funktionen werden entweder deaktiviert oder explizit markiert.

#### Akzeptanzkriterien

- Expressions können isoliert getestet werden.
- Expressions melden Fehler mit Quellposition oder mindestens Rule-ID und Ausdruck.
- Kein Ausdruck kann beliebige Dateien lesen oder Code ausführen.

---

### 18.8 Phase 5: Runtime MVP für 1:1 Scalar Mapping

#### Ziel

Die Engine soll einfache 1:1-Transformationen mit skalaren Attributen tatsächlich ausführen und Transferdaten schreiben können.

#### Artefakt

- CLI-Befehl `transform` für einfache Mappings.
- Golden-Test mit kleinem Quell-XTF/ITF und erwarteter Ziel-XTF/ITF.

#### Scope

- Ein Input, ein Output.
- Einfache Klassenabbildung.
- Skalare Attribute: Text, Zahl, Boolean, Date als String/TypedValue je nach Writer-Erfordernis.
- `where`-Filter für Source-Objekte.
- Ausgabe von Diagnostics und Summary.

#### Nicht-Scope

- Noch keine Referenzen.
- Noch keine komplexen Geometrien.
- Noch keine DM01↔DMAV-Fachlogik.

#### Tests

- Integrationstest mit kleinem Testmodell.
- Golden-Test: Output muss exakt oder normalisiert identisch sein.
- Negativtest: `where` filtert alle Objekte und Report zeigt 0 erzeugte Objekte.

#### Akzeptanzkriterien

- `transform mapping.yaml` schreibt eine gültige Ausgabedatei für das Testmodell.
- Anzahl gelesener, erzeugter und geschriebener Objekte wird berichtet.
- Früher implementierte Compiler-Tests bleiben grün.

---

### 18.9 Phase 6: OID-, Basket- und Writer-Strategien

#### Ziel

OIDs, Basket-IDs und Writer-Reihenfolge müssen deterministisch und konfigurierbar sein. Das ist Voraussetzung für DMAV mit UUID-OIDs.

#### Artefakt

- Implementierte `oidStrategy` und `basketStrategy`.
- Golden-Tests für deterministische Ausgabe.

#### Scope

- OID-Strategien: `preserve`, `integer`, `uuid`, `deterministicUuid`.
- Für DMAV: `deterministicUuid` als bevorzugte Strategie.
- Basket-Strategien: `preserve`, `fixed`, `perTopic`, `fromExpression`.
- Writer-Reihenfolge nach Modell-/Topic-/Klassenabhängigkeiten vorbereiten.
- Stable sorting, damit Golden-Tests reproduzierbar sind.

#### Nicht-Scope

- Noch keine vollständige Association-Auflösung.
- Noch keine produktive DMAV-Ausgabe.

#### Tests

- Gleicher Input erzeugt bei `deterministicUuid` identische OIDs.
- Unterschiedliche Namespaces erzeugen unterschiedliche UUIDs.
- `fixed` Basket-ID wird korrekt gesetzt.
- Golden-Output ist über mehrere Testläufe identisch.

#### Akzeptanzkriterien

- Keine fortlaufenden Long-OIDs für UUIDOID-Zielmodelle.
- OID-Strategie ist im Report sichtbar.
- Basket-ID-Strategie ist im Report sichtbar.

---

### 18.10 Phase 7: Referenzen, Rollen und Associations

#### Ziel

Referenzen dürfen nicht nur als OID-Strings kopiert werden. Die Engine muss Rollen und Associations modellbewusst auflösen.

#### Artefakt

- `RoleResolver`.
- DeferredRef-Auflösung gegen `IdMapping` und erwartete Zielklasse.
- Integrationstest mit zwei referenzierten Klassen.

#### Scope

- Source-Rolle analysieren: referenzierte Source-Klasse bestimmen.
- Target-Rolle/Association analysieren: erwartete Target-Klasse bestimmen.
- DeferredRefs mit Source- und Target-Typinformationen speichern.
- Fehlerfälle unterscheiden: unresolved, ambiguous, type mismatch, cardinality violation.
- Konfigurierbare Fehlerpolitik: `strict`, `lenient`.

#### Nicht-Scope

- Noch keine n:m-Association mit komplexen Join-Bedingungen, ausser einfache 1:n-Referenzen.
- Noch kein LINEATTR-Spezialfall.

#### Tests

- Referenz wird korrekt von Source-OID auf Target-OID gemappt.
- Fehlende referenzierte Objekte erzeugen Diagnostic.
- Mehrdeutige Referenzen erzeugen Fehler.
- Erwartete Zielklasse wird geprüft.

#### Akzeptanzkriterien

- Referenzauflösung ist unabhängig von Objekt-Reihenfolge im Input.
- Diagnostics enthalten Owner-Objekt, Rolle, Quell-OID und erwartete Zielklasse.

---

### 18.11 Phase 8: XLSX-Korrelation importieren

#### Ziel

Die Datei `docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx` soll reproduzierbar in maschinenlesbare Correlation Hints importiert werden.

#### Artefakt

- CLI-Befehl `import-correlation`.
- `build/generated/dm01-dmav/correlation-hints.json`.
- `build/reports/dm01-dmav/correlation-import-report.md`.

#### Scope

- Workbook öffnen.
- Sheets erkennen: insbesondere `Transformation`, `DMAV_Modell`, `DM01-AV-CH`, `Korrelation`.
- Spalten anhand Header oder stabiler Indizes lesen.
- Richtung DM01→DMAV und DMAV→DM01 extrahieren.
- Transformationsarten `K`, `V`, `I` importieren; unbekannte Codes als Warning.
- Original-Zellpositionen speichern: Sheet, Zeile, Spalte.
- Kommentare/Ergänzungen als Text übernehmen.

#### Nicht-Scope

- Noch keine automatische Transformation.
- Noch keine semantische Garantie, dass ein Hint korrekt ist.

#### Tests

- Unit-Test mit kleinem künstlichem XLSX.
- Integrationstest mit der echten XLSX, falls sie im Repo liegt.
- Snapshot-Test für Anzahl gelesener Hints und unbekannte Codes.

#### Akzeptanzkriterien

- Import bricht bei unbekannten Codes nicht ab, sondern diagnostiziert sie.
- Jede Hint-Zeile ist zur Original-XLSX-Zeile rückverfolgbar.
- Report enthält Anzahl Hints pro Richtung und Transformationsart.

---

### 18.12 Phase 9: Mapping-Kandidatengenerator

#### Ziel

Aus Model Inventory und Correlation Hints sollen Mapping-Vorschläge generiert werden, damit DM01↔DMAV nicht komplett manuell geschrieben werden muss.

#### Artefakt

- CLI-Befehl `generate-mapping`.
- `mapping-candidates.json`.
- `candidate-report.md`.
- Erste generierte YAML-Fragmente für LFP3.

#### Scope

- Kandidaten aus XLSX-Hints erzeugen.
- Kandidaten aus Namens-/Synonymähnlichkeit ergänzen.
- Typkompatibilität prüfen.
- Confidence Score berechnen.
- `high`, `medium`, `low`, `manual`, `rejected` klassifizieren.
- TODOs im YAML ausgeben, wenn Bedingungen/Geometrie/Default unklar sind.

#### Nicht-Scope

- Kein blindes Überschreiben handgeschriebener Mappings.
- Keine automatische Freigabe von Low-Confidence-Kandidaten.

#### Tests

- Unit-Tests für Score-Berechnung.
- Tests für Synonymlisten.
- Snapshot-Test für generierte Kandidaten bei kleinem Modellpaar.
- Test, dass nicht existierende Pfade zu `rejected` oder `manual` werden.

#### Akzeptanzkriterien

- Generator erzeugt validierbares YAML oder klar markierte TODO-Fragmente.
- Jeder Kandidat enthält Herkunft: XLSX-Zeile, Heuristik oder Synonym.
- Report trennt sichere Vorschläge von manuellen Entscheidungen.

---

### 18.13 Phase 10: DM01→DMAV LFP3 Minimalpilot

#### Ziel

Ein erster fachlicher DM01→DMAV-Transformationslauf für `LFP3Nachfuehrung` und `LFP3` soll funktionieren und durch `ilivalidator` validierbar sein.

#### Artefakt

- Handgeprüftes Mapping `mappings/dm01-dmav/lfp3/dm01-to-dmav-lfp3.yaml`.
- Kleine DM01-Test-ITF-Datei.
- DMAV-Ziel-XTF als Golden Output oder normalisierter Golden Output.
- Validierungsreport.

#### Scope

- DM01 `FixpunkteKategorie3.LFP3Nachfuehrung` → DMAV `FixpunkteAVKategorie3.LFP3Nachfuehrung`.
- DM01 `FixpunkteKategorie3.LFP3` → DMAV `FixpunkteAVKategorie3.LFP3`.
- Attribute kopieren/konvertieren, soweit fachlich eindeutig.
- Deterministische UUIDs für DMAV.
- Referenz/Association `Entstehung_LFP3` abbilden.
- Mandatory Defaults nur mit expliziter Policy.

#### Nicht-Scope

- Noch keine vollständigen Textpositionen.
- Noch keine Bodenbedeckung oder Grundstücke.
- Noch keine allgemeine DM01→DMAV-Konvertierung.

#### Tests

- Golden-Test mit minimalem gültigem DM01-Input.
- Test mit fehlendem optionalem DM01-Datum und definierter Default-Policy.
- Referenztest Nachführung ↔ LFP3.
- `ilivalidator` auf dem erzeugten DMAV-XTF, falls im Testumfeld verfügbar; sonst optionaler, dokumentierter Integrationstest.

#### Akzeptanzkriterien

- Der erzeugte DMAV-XTF ist für den Slice validierbar.
- Nicht abgebildete Felder sind im Report aufgeführt.
- Jede Default-Setzung ist nachvollziehbar dokumentiert.

---

### 18.14 Phase 11: DMAV→DM01 LFP3 Minimalpilot

#### Ziel

Die Gegenrichtung für denselben fachlichen Slice soll funktionieren. Dabei sind Informationsverluste explizit zu dokumentieren.

#### Artefakt

- Mapping `mappings/dm01-dmav/lfp3/dmav-to-dm01-lfp3.yaml`.
- Kleine DMAV-Test-XTF-Datei.
- DM01-Ziel-ITF als Golden Output oder normalisierter Golden Output.
- Loss-Report.

#### Scope

- DMAV `LFP3Nachfuehrung` → DM01 `LFP3Nachfuehrung`.
- DMAV `LFP3` → DM01 `LFP3`.
- UUID-OIDs auf DM01-kompatible OIDs abbilden.
- XMLDateTime nach DM01 DATE konvertieren, falls nötig mit dokumentiertem Informationsverlust.
- DMAV-Associations auf DM01-Rollen zurückführen.

#### Nicht-Scope

- Kein Anspruch, dass DMAV→DM01→DMAV bitidentisch ist.
- Noch keine LINEATTR- oder komplexe Geometrie-Rückabbildung.

#### Tests

- Golden-Test DMAV→DM01.
- Roundtrip-Test für bewusst roundtrip-fähige Felder.
- Loss-Report-Test: verlorene Zeitanteile oder zusätzliche DMAV-Felder werden dokumentiert.
- `ilivalidator` auf dem erzeugten DM01-ITF, falls verfügbar.

#### Akzeptanzkriterien

- Die DM01-Ausgabe ist für den Slice validierbar.
- Informationsverluste werden nicht verschwiegen.
- Die Gegenrichtung verwendet ein eigenes Mapping, keine naive automatische Inversion.

---

### 18.15 Phase 12: BAG OF STRUCTURE und Textpositionen

#### Ziel

Text- und Symbolpositionen sowie BAG OF STRUCTURE sollen für den LFP3-Slice unterstützt werden.

#### Artefakt

- Erweiterte LFP3-Mappings in beide Richtungen.
- Tests für Pos-Tabellen ↔ BAG OF STRUCTURE.

#### Scope

- DM01-Positionstabellen wie `LFP3Pos` analysieren.
- DMAV-BAGs wie `Pos` oder strukturierte Textpositionen erzeugen.
- Umgekehrte Richtung: DMAV-BAG in DM01-Positionstabellen materialisieren.
- Orientierung, H-/V-Alignment, Hilfslinie, Schriftgrösse soweit fachlich klar abbilden.

#### Nicht-Scope

- Noch keine vollständige Behandlung aller Pos-Tabellen aller DM01-Topics.
- Noch keine kartografische Optimierung.

#### Tests

- Input mit 0, 1 und mehreren Positionen.
- Orientierung wird erhalten.
- Fehlende optionale Positionen erzeugen keine falschen leeren Strukturen.
- Golden-Test in beide Richtungen.

#### Akzeptanzkriterien

- LFP3 mit Textpositionen ist in beide Richtungen transformierbar.
- Mehrfachpositionen bleiben stabil und geordnet.

---

### 18.16 Phase 13: Geometrie-MVP

#### Ziel

Ein erster robuster Geometrie-Layer soll vorhanden sein. Er muss noch nicht alle AV-Topologieprobleme lösen, darf aber Geometrien nicht als untypisierte Strings behandeln.

#### Artefakt

- `GeometryAdapter`-API.
- Tests für Coord, Polyline und einfache Surface/Area-Passthroughs.
- Geometrie-Diagnostics.

#### Scope

- Geometrietypen aus dem TypeSystem erkennen.
- Coord/Polyline lesen und schreiben.
- Surface/Area zunächst nur passthrough oder klar `unsupported`, abhängig von Writer-Unterstützung.
- CRS-/LV95-Annahmen dokumentieren.
- Diagnostik für nicht unterstützte Geometrieoperationen.

#### Nicht-Scope

- Noch keine vollständige Area-Topologie-Konvertierung.
- Noch keine automatische Split/Merge-Operation für Grundstücke/Bodenbedeckung.
- Noch keine LINEATTR-Integration als produktive Funktion.

#### Tests

- Coord bleibt erhalten.
- Polyline bleibt erhalten.
- Nicht unterstützte Surface/Area-Konvertierung erzeugt klare Diagnostic.
- Kein stilles Weglassen von Geometrien.

#### Akzeptanzkriterien

- Geometrien sind im TypedValue-System sichtbar.
- Der Transformationsreport weist Geometriefelder und Behandlung aus.

---

### 18.17 Phase 14: Erweiterter DM01↔DMAV-Analysebericht

#### Ziel

Nach dem LFP3-Pilot soll nicht sofort alles implementiert werden. Zuerst soll ein systematischer Gap-Report für weitere Topics entstehen.

#### Artefakt

- `build/reports/dm01-dmav/topic-gap-report.md`.
- Priorisierte Liste weiterer Slices.
- Risiko-/Aufwandsklassifikation pro Topic.

#### Scope

- Kandidatenanalyse für weitere DM01/DMAV-Topics.
- Einstufung: einfach, mittel, schwierig, sehr schwierig.
- Markierung von Geometrie-/Topologie-/LINEATTR-Problemen.
- Vorschlag der nächsten 2–3 fachlichen Slices.

#### Nicht-Scope

- Noch keine vollständige Umsetzung der neuen Slices.

#### Tests

- Snapshot-Test für Report-Struktur.
- Test, dass bekannte Hochrisikothemen wie Bodenbedeckung/Grundstücke als hochriskant markiert werden, falls entsprechende Hints erkannt werden.

#### Akzeptanzkriterien

- Der Bericht ist für Projektplanung nutzbar.
- Er trennt generisch lösbare Aufgaben von fachlich offenen Fragen.

---

### 18.18 Phase 15: Stabilisierung, CLI-UX und Dokumentation

#### Ziel

Die bis dahin entstandene Alpha-Version soll für Entwickler nutzbar sein und reproduzierbar ausgeführt werden können.

#### Artefakt

- Alpha-Release oder Release-Kandidat.
- Vollständiges README.
- Mapping-DSL-Dokumentation.
- DM01↔DMAV-LFP3-Tutorial.
- Beispielbefehle und Testdaten.

#### Scope

- CLI-Hilfe überarbeiten.
- Reports vereinheitlichen.
- Fehlercodes dokumentieren.
- Beispiel-Workflows dokumentieren.
- Gradle Tasks konsolidieren.
- CI-Workflow ergänzen, falls gewünscht.

#### Nicht-Scope

- Keine Garantie auf vollständige DM01↔DMAV-Abdeckung.
- Keine Performance-Optimierung für sehr grosse AV-Datensätze, ausser grobe Bottleneck-Dokumentation.

#### Tests

- Vollständiger Testlauf: Unit, Integration, Golden.
- Smoke-Test aller CLI-Kommandos.
- Dokumentierte Beispielbefehle müssen ausführbar sein oder klar als Pseudobeispiel markiert werden.

#### Akzeptanzkriterien

- Ein Entwickler kann das Projekt klonen, Tests ausführen und den LFP3-Pilot nachvollziehen.
- Offene Fragen sind zentral dokumentiert.
- Nicht implementierte Features sind als solche erkennbar.

---

### 18.19 Phasenabhängigkeiten

```text
Phase 0
  -> Phase 1
    -> Phase 2
      -> Phase 3
        -> Phase 4
          -> Phase 5
            -> Phase 6
              -> Phase 7
                -> Phase 8
                  -> Phase 9
                    -> Phase 10
                      -> Phase 11
                        -> Phase 12
                          -> Phase 13
                            -> Phase 14
                              -> Phase 15
```

Phase 8 kann teilweise parallel zu Phase 4 bis 7 vorbereitet werden, sofern sie nur den XLSX-Import betrifft und keine Runtime-Entscheidungen vorwegnimmt. Phase 10 darf aber nicht vor Abschluss von Phase 7 als fertig gelten, weil DM01↔DMAV-LFP3 Referenzen, OIDs und Baskets benötigt.

### 18.20 Minimale Definition of Done pro Phase

Eine Phase ist nur dann abgeschlossen, wenn alle folgenden Punkte erfüllt sind:

- Code kompiliert.
- Tests der Phase laufen automatisiert.
- Frühere Tests laufen weiterhin.
- Artefakte der Phase sind im Repository oder unter `build/` reproduzierbar erzeugbar.
- Neue CLI-Befehle haben Help-Text.
- Neue Diagnostics haben dokumentierte Codes.
- Offene Fragen sind in `docs/open-questions.md` erfasst.
- README oder passende Dokumentationsdatei verweist auf die neue Funktion, sofern sie benutzbar ist.


## 19. DM01↔DMAV-spezifische Mapping-Generierung

### 19.1 Ziel

Aus Modellmetadaten und Korrelationstabelle sollen Mapping-Vorschläge generiert werden, damit möglichst wenig manuell geschrieben werden muss.

### 19.2 Pipeline

```text
1. DM01 Modell kompilieren
2. DMAV Modelle kompilieren
3. Metamodel Inventory DM01 erzeugen
4. Metamodel Inventory DMAV erzeugen
5. XLSX Korrelationstabelle importieren
6. CorrelationHints normalisieren
7. Kandidaten erzeugen
8. Kandidaten gegen TypeSystem prüfen
9. Confidence Score berechnen
10. YAML-Vorschlag erzeugen
11. TODOs und offene Fragen markieren
12. Golden Test manuell ergänzen
```

### 19.3 Metamodel Inventory

Für jedes Modell erzeugen:

```json
{
  "model": "DMAV_FixpunkteAVKategorie3_V1_1",
  "topics": [
    {
      "name": "FixpunkteAVKategorie3",
      "basketOidType": "INTERLIS.UUIDOID",
      "oidType": "INTERLIS.UUIDOID",
      "classes": [
        {
          "name": "LFP3",
          "path": "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3",
          "attributes": [
            {
              "name": "NBIdent",
              "type": "TEXT*12",
              "mandatory": true
            }
          ],
          "roles": [],
          "constraints": []
        }
      ]
    }
  ]
}
```

### 19.4 Kandidatengenerierung

Ein Kandidat kann entstehen aus:

- explizitem XLSX-Hint,
- gleicher oder ähnlicher Attributname,
- Synonymliste,
- identischer Domain,
- kompatiblem Typ,
- identischer Kardinalität,
- bekannter DM01↔DMAV-Konvention.

Synonymbeispiele:

| DM01 | DMAV |
|---|---|
| `HoeheGeom` | `Hoehengeometrie` |
| `LageGen` | `Lagegenauigkeit` |
| `LageZuv` | `IstLagezuverlaessig` |
| `HoeheGen` | `Hoehengenauigkeit` |
| `HoeheZuv` | `IstHoehenzuverlaessig` |
| `Ori` | `SymbolOri` oder `Orientierung` |
| `Pos` | `Position` |
| `HALIGNMENT` | `HReferenzpunkt` |
| `VALIGNMENT` | `VReferenzpunkt` |
| `GueltigerEintrag` | `GueltigerEintrag` |
| `Datum1` | Fallback für `GueltigerEintrag` |

### 19.5 Confidence Score

Vorschlag:

| Signal | Score-Beitrag |
|---|---:|
| Expliziter XLSX-Hint | +0.40 |
| Source/Target existieren im Modell | +0.20 |
| Typ kompatibel | +0.15 |
| Name identisch/normalisiert ähnlich | +0.10 |
| Synonymliste trifft | +0.10 |
| Mandatory-Ziel mit Default erklärt | +0.05 |
| Widerspruch in Richtung/Typ | -0.30 |
| Mehrfachziel ohne klare Split-Regel | -0.20 |
| Geometrie/Topologie nicht unterstützt | -0.20 |
| TODO in Ergänzung | -0.10 |

Klassen:

```text
>= 0.85  high
0.60-0.84 medium
0.30-0.59 low
< 0.30   rejected / manual
```

### 19.6 Output des Generators

```text
build/generated/dm01-dmav/correlation-hints.json
build/generated/dm01-dmav/mapping-candidates.json
build/generated/dm01-dmav/dm01-to-dmav-lfp3.generated.yaml
build/generated/dm01-dmav/dmav-to-dm01-lfp3.generated.yaml
build/reports/dm01-dmav/candidate-report.md
```

---

## 20. DM01→DMAV LFP3 Pilot: Fachliche Mindestregeln

### 20.1 LFP3Nachfuehrung

DM01:

```text
FixpunkteKategorie3.LFP3Nachfuehrung
NBIdent
Identifikator
Beschreibung
Perimeter
GueltigerEintrag OPTIONAL DATE
Datum1 OPTIONAL DATE
```

DMAV:

```text
FixpunkteAVKategorie3.LFP3Nachfuehrung
NBIdent MANDATORY TEXT*12
Identifikator MANDATORY TEXT*12
Beschreibung MANDATORY TEXT*60
Perimeter SURFACE ...
GueltigerEintrag MANDATORY INTERLIS.XMLDateTime
```

Mapping-Ansatz:

| DMAV | DM01 | Regel |
|---|---|---|
| `NBIdent` | `NBIdent` | kopieren |
| `Identifikator` | `Identifikator` | kopieren |
| `Beschreibung` | `Beschreibung` | kopieren/truncate auf 60; falls leer: Default policy |
| `Perimeter` | `Perimeter` | kopieren, falls vorhanden; Pflichtstatus prüfen |
| `GueltigerEintrag` | `GueltigerEintrag`, fallback `Datum1` | DATE zu XMLDateTime |

Offene Frage: Was tun, wenn DM01 weder `GueltigerEintrag` noch `Datum1` hat, DMAV aber mandatory ist?

### 20.2 LFP3

DM01:

```text
FixpunkteKategorie3.LFP3
Entstehung -> LFP3Nachfuehrung
NBIdent
Nummer
Geometrie
HoeheGeom OPTIONAL
LageGen
LageZuv
HoeheGen OPTIONAL
HoeheZuv OPTIONAL
Punktzeichen
Protokoll
```

DMAV:

```text
FixpunkteAVKategorie3.LFP3
NBIdent MANDATORY
Nummer MANDATORY
LFPArt MANDATORY
Geometrie MANDATORY
Hoehengeometrie optional
Lagegenauigkeit MANDATORY
IstLagezuverlaessig MANDATORY
Hoehengenauigkeit optional
IstHoehenzuverlaessig optional
Punktzeichen MANDATORY
Schutzart optional
Grenzpunktfunktion MANDATORY
IstHoheitsgrenzsteinAlt optional abhängig von Grenzpunktfunktion
AktiverUnterhalt MANDATORY
SymbolOri optional
Textposition BAG OF Textposition
```

Mapping-Ansatz:

| DMAV | DM01 | Regel |
|---|---|---|
| `NBIdent` | `NBIdent` | kopieren |
| `Nummer` | `Nummer` | kopieren |
| `LFPArt` | - | Default `#LFP3` |
| `Geometrie` | `Geometrie` | kopieren |
| `Hoehengeometrie` | `HoeheGeom` | kopieren |
| `Lagegenauigkeit` | `LageGen` | kopieren |
| `IstLagezuverlaessig` | `LageZuv` | `ja -> true`, `nein -> false` oder Zieldomain prüfen |
| `Hoehengenauigkeit` | `HoeheGen` | kopieren |
| `IstHoehenzuverlaessig` | `HoeheZuv` | enum/boolean mapping |
| `Punktzeichen` | `Punktzeichen` | enum mapping |
| `Schutzart` | - | offen / null |
| `Grenzpunktfunktion` | evtl. Gemeindegrenzen/Liegenschaften | zunächst Default `#keine`, später aus Kontext ableiten |
| `IstHoheitsgrenzsteinAlt` | - | nur bei Grenzpunktfunktion != keine |
| `AktiverUnterhalt` | - | Default `true`, fachlich prüfen |
| `SymbolOri` | `LFP3Symbol.Ori` | via Join |
| `Textposition` | `LFP3Pos` | BAG aus Pos-Tabelle |
| Association `Entstehung_LFP3` | `Entstehung` | Ref-Auflösung |

Offene Fragen:

1. Wie soll `Protokoll` abgebildet werden?
2. Ist `AktiverUnterhalt = true` fachlich zulässig als Default?
3. Wie ist `Grenzpunktfunktion` zu bestimmen, wenn derselbe Punkt auch Grenz-/Hoheitsgrenzpunkt ist?
4. Muss LFP3 gleichzeitig nach `Grundstuecke.Grenzpunkt` kopiert werden, wenn geometrisch identisch mit Grenzpunkt/Hoheitsgrenzpunkt?
5. Wie wird `IstHoheitsgrenzsteinAlt` fachlich bestimmt?

---

## 21. DMAV→DM01 LFP3 Pilot

DMAV→DM01 ist nicht automatisch die Inversion. Es muss ein eigenes Profil geben.

### 21.1 Grundregeln

| DM01 | DMAV | Regel |
|---|---|---|
| `NBIdent` | `NBIdent` | kopieren |
| `Nummer` | `Nummer` | kopieren |
| `Geometrie` | `Geometrie` | kopieren |
| `HoeheGeom` | `Hoehengeometrie` | kopieren |
| `LageGen` | `Lagegenauigkeit` | kopieren |
| `LageZuv` | `IstLagezuverlaessig` | boolean/domain zurückmappen |
| `HoeheGen` | `Hoehengenauigkeit` | kopieren |
| `HoeheZuv` | `IstHoehenzuverlaessig` | zurückmappen |
| `Punktzeichen` | `Punktzeichen` | enum zurückmappen |
| `Protokoll` | - | Default/offen |
| `LFP3Pos` | `Textposition` | Structure in Tabelle materialisieren |
| `LFP3Symbol.Ori` | `SymbolOri` | Tabelle materialisieren |
| `Entstehung` | `Entstehung_LFP3` | Association zurück in Referenz |

Offene Fragen:

1. Was ist Default für DM01 `Protokoll`?
2. Wie werden mehrere DMAV-Textpositionen in DM01 behandelt, wenn DM01 `LFP3Pos` 1-1 ist?
3. Werden DMAV-Objekte mit `Grenzpunktfunktion != keine` zusätzlich nach DM01 `Gemeindegrenzen.Hoheitsgrenzpunkt` oder `Liegenschaften.Grenzpunkt` geschrieben?
4. Wie wird entschieden, welche DMAV-Objekte im Rückweg überhaupt in DM01-Fixpunkte gehen?

---

## 22. Validierung

### 22.1 Pflicht

Jede Integrationstest-Transformation muss optional/konfigurierbar mit `ilivalidator` geprüft werden.

### 22.2 Validierungsmodi

```yaml
validation:
  enabled: true
  validator: ilivalidator
  modeldir:
    - "https://models.geo.admin.ch/"
    - "models/"
  failOnWarnings: false
```

### 22.3 Berichte

```text
build/reports/ilitransformer/<job-name>/compile-report.json
build/reports/ilitransformer/<job-name>/compile-report.md
build/reports/ilitransformer/<job-name>/transform-report.json
build/reports/ilitransformer/<job-name>/validation.log
```

---

## 23. Diagnostik

### 23.1 Diagnostic-Modell

```java
public record Diagnostic(
    DiagnosticCode code,
    Severity severity,
    String message,
    String suggestion,
    String ruleId,
    String sourcePath,
    String targetPath,
    Map<String, Object> context
) {}
```

### 23.2 Severity

```text
ERROR
WARNING
INFO
DEBUG
```

### 23.3 Codes

#### Mapping/Compiler

| Code | Bedeutung |
|---|---|
| `ILITRF-MAP-UNKNOWN-INPUT` | Input-ID unbekannt |
| `ILITRF-MAP-UNKNOWN-OUTPUT` | Output-ID unbekannt |
| `ILITRF-MAP-UNKNOWN-CLASS` | Klasse existiert nicht |
| `ILITRF-MAP-UNKNOWN-ATTRIBUTE` | Attribut existiert nicht |
| `ILITRF-MAP-UNKNOWN-ROLE` | Rolle existiert nicht |
| `ILITRF-MAP-TYPE-MISMATCH` | Expression-Typ passt nicht zum Zieltyp |
| `ILITRF-MAP-MANDATORY-MISSING` | Mandatory-Zielattribut wird nicht gesetzt |
| `ILITRF-MAP-ENUM-INCOMPLETE` | Enum-Mapping unvollständig |
| `ILITRF-MAP-TODO` | TODO im Mapping |
| `ILITRF-MAP-AMBIGUOUS-PATH` | Pfad nicht eindeutig |

#### Runtime

| Code | Bedeutung |
|---|---|
| `ILITRF-RUN-SOURCE-READ` | Fehler beim Lesen |
| `ILITRF-RUN-TARGET-WRITE` | Fehler beim Schreiben |
| `ILITRF-RUN-EXPR` | Expression-Auswertung fehlgeschlagen |
| `ILITRF-RUN-REF-UNRESOLVED` | Referenz nicht auflösbar |
| `ILITRF-RUN-REF-AMBIGUOUS` | Referenz mehrdeutig |
| `ILITRF-RUN-CARDINALITY` | Kardinalität verletzt |
| `ILITRF-RUN-OID-COLLISION` | OID-Kollision |
| `ILITRF-RUN-BASKET` | Basket-Zuordnung fehlerhaft |

#### DM01/DMAV

| Code | Bedeutung |
|---|---|
| `ILITRF-DMAV-CORRELATION-PARSE` | XLSX-Hint konnte nicht interpretiert werden |
| `ILITRF-DMAV-LOW-CONFIDENCE` | Mapping-Kandidat hat niedrige Confidence |
| `ILITRF-DMAV-LOSSY` | Mapping ist verlustbehaftet |
| `ILITRF-DMAV-OPEN-QUESTION` | fachliche offene Frage blockiert automatisches Mapping |

---

## 24. CLI-Anforderungen

### 24.1 Hauptbefehle

```bash
ilitransformer transform --mapping mapping.yaml
ilitransformer validate-mapping --mapping mapping.yaml
ilitransformer inspect-model --model DMAV_FixpunkteAVKategorie3_V1_1 --modeldir https://models.geo.admin.ch/
ilitransformer import-correlation --xlsx docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx
ilitransformer generate-mapping --profile dm01-to-dmav-lfp3
```

### 24.2 Transform

```bash
ilitransformer transform \
  --mapping profiles/dm01-to-dmav/lfp3.yaml \
  --modeldir "https://models.geo.admin.ch/;models" \
  --validate \
  --report build/reports/lfp3
```

### 24.3 Validate Mapping

Soll nur Mapping kompilieren und Reports erzeugen, ohne Transferdaten zu lesen.

### 24.4 Inspect Model

Soll Modellinventar als JSON/Markdown ausgeben.

```bash
ilitransformer inspect-model \
  --model DMAV_FixpunkteAVKategorie3_V1_1 \
  --output build/model-inventory/dmav-fp3.json
```

### 24.5 Import Correlation

```bash
ilitransformer import-correlation \
  --xlsx docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx \
  --out build/generated/dm01-dmav/correlation-hints.json
```

### 24.6 Generate Mapping

```bash
ilitransformer generate-mapping \
  --direction dm01-to-dmav \
  --slice lfp3 \
  --correlation build/generated/dm01-dmav/correlation-hints.json \
  --out profiles/dm01-to-dmav/lfp3.generated.yaml
```

---

## 25. Gradle-Anforderungen

### 25.1 Tasks

```bash
./gradlew test
./gradlew integrationTest
./gradlew generateModelInventory
./gradlew importDmavCorrelation
./gradlew generateDm01DmavMappings
./gradlew validateGoldenTransfers
```

### 25.2 Source Sets

```groovy
sourceSets {
    integrationTest {
        java.srcDir file('src/integrationTest/java')
        resources.srcDir file('src/integrationTest/resources')
    }
}
```

### 25.3 Test Fixtures

```text
src/test/resources/models/
src/test/resources/mappings/
src/test/resources/transfers/
src/integrationTest/resources/dm01-dmav/lfp3/input/
src/integrationTest/resources/dm01-dmav/lfp3/expected/
```

---

## 26. Tests

### 26.1 Unit Tests

Pflichtbereiche:

- YAML Loading,
- DSL-Versionierung,
- IliPath Parsing,
- Model Service,
- TypeChecker,
- Expression Parser,
- Builtin Functions,
- Enum Mapping,
- OID Strategy,
- Basket Strategy,
- CorrelationWorkbookImporter,
- MappingCandidateGenerator,
- StateStore,
- ReferenceResolver,
- IomObjectFactory.

### 26.2 Integration Tests

Pflichtbereiche:

1. Minimal ITF→XTF ohne Referenzen.
2. ITF→XTF mit Referenzen.
3. XTF→ITF mit Referenzen.
4. BAG OF STRUCTURE aus separater Pos-Tabelle.
5. DM01→DMAV LFP3 Golden Test.
6. DMAV→DM01 LFP3 Golden Test.
7. Validator erfolgreich.
8. Validator schlägt bei bewusst falschem Mapping fehl.

### 26.3 Golden Tests

Golden Tests sollen so aufgebaut sein:

```text
src/integrationTest/resources/golden/dm01-to-dmav-lfp3/
  input/dm01.itf
  mapping.yaml
  expected/dmav.xtf
  expected/report.json
```

Vergleich:

- nicht blind XML-String vergleichen,
- XTF normalisieren,
- OIDs bei deterministischer Strategie stabil halten,
- Objektmengen vergleichen,
- Attribute vergleichen,
- Referenzen vergleichen,
- optional canonical XML.

### 26.4 Testdaten

Für LFP3 braucht es mindestens:

1. LFP3 mit Höhe.
2. LFP3 ohne Höhe.
3. LFP3 mit Position.
4. LFP3 ohne Position.
5. LFP3 mit Symbol.
6. LFP3 ohne Symbol.
7. LFP3 mit vollständiger Nachführung.
8. LFP3 mit fehlendem `GueltigerEintrag`, aber `Datum1`.
9. Fehlerfall: fehlende Nachführungsreferenz.
10. Fehlerfall: unversichertes Punktzeichen bei LFPArt LFP3, falls Constraint greift.

---

## 27. Repository-Struktur

Empfohlene Struktur:

```text
.
├── README.md
├── SPEC.md
├── build.gradle
├── settings.gradle
├── docs/
│   ├── architecture.md
│   ├── mapping-dsl.md
│   ├── dm01-dmav/
│   │   ├── README.md
│   │   ├── DMAV_Korrelationstabelle_20260301.xlsx
│   │   ├── interpretation.md
│   │   └── open-questions.md
│   └── adr/
│       ├── 0001-java-first.md
│       ├── 0002-yaml-dsl.md
│       ├── 0003-deterministic-uuid.md
│       └── 0004-correlation-table-as-hints.md
├── profiles/
│   ├── dm01-to-dmav/
│   │   ├── lfp3.yaml
│   │   ├── hfp3.yaml
│   │   └── grundstuecke-grenzpunkt.yaml
│   └── dmav-to-dm01/
│       ├── lfp3.yaml
│       ├── hfp3.yaml
│       └── grundstuecke-grenzpunkt.yaml
├── src/main/java/...
├── src/main/resources/
│   └── dmav/
│       ├── enum-mappings.yaml
│       ├── synonyms.yaml
│       └── correlation-schema.json
├── src/test/java/...
├── src/test/resources/...
├── src/integrationTest/java/...
└── src/integrationTest/resources/...
```

---

## 28. Dokumentation

Die Dokumentation ist Teil des Lieferumfangs und muss mit jeder fachlichen oder technischen Erweiterung mitgepflegt werden. Sie soll nicht erst am Ende geschrieben werden, sondern phasenweise mitwachsen. Jede Phase muss mindestens dort dokumentiert werden, wo neue CLI-Kommandos, DSL-Elemente, Compiler-Diagnostics, Runtime-Verhalten oder fachliche DM01↔DMAV-Annahmen entstehen.

### 28.1 README

Die Datei `README.md` ist die Einstiegseite des Repositories. Sie muss für Entwicklerinnen und Entwickler verständlich machen, was der Transformer kann, was noch nicht unterstützt wird und wie man die wichtigsten Kommandos ausführt.

`README.md` soll enthalten:

- Zweck des Projekts,
- aktueller Status,
- verwendeter Arbeitsname und CLI-Name,
- Installation und Build,
- Voraussetzungen,
- wichtigste Gradle-Kommandos,
- CLI-Beispiele,
- minimaler Mapping-Job,
- Beispiel für einen Analyse- oder Inventory-Lauf,
- DM01↔DMAV-Statusmatrix,
- bekannte Einschränkungen,
- Link auf die ausführliche DSL-Dokumentation,
- Link auf die DM01↔DMAV-Dokumentation,
- Link auf offene fachliche Fragen.

Der README darf keine falsche Produktreife suggerieren. Solange DM01↔DMAV nur teilweise unterstützt wird, muss das klar sichtbar sein.

### 28.2 Entwicklerdokumentation

Unter `docs/dev/` sollen technische Entscheidungen und Entwicklungsinformationen geführt werden.

Empfohlene Dateien:

- `docs/dev/baseline.md`  
  Dokumentiert den Ausgangszustand, verwendete Java-/Gradle-Versionen, wichtige Abhängigkeiten und bekannte technische Einschränkungen.

- `docs/dev/architecture.md`  
  Beschreibt die Architektur mit CLI/API, Model Service, Mapping Compiler, Execution Engine, StateStore, Diagnostics, I/O-Adaptern und GeometryAdapter.

- `docs/dev/typed-plan.md`  
  Beschreibt den kompilierten Mapping-Plan, Plan-Objekte, Typinformationen, Source-/Target-Pfade und Diagnostic-Kontext.

- `docs/dev/diagnostics.md`  
  Dokumentiert Error Codes, Warning Codes, Fail Policies und Beispiele für Fehlermeldungen.

- `docs/dev/state-store.md`  
  Beschreibt SourceRecord, SourceKey, TargetKey, IdMap, DeferredRef, ObjectIndex und spätere Persistenzoptionen.

- `docs/dev/adr/`  
  Enthält Architecture Decision Records. Jede wichtige Entscheidung muss als ADR dokumentiert werden.

### 28.3 Mapping-DSL-Dokumentation

Die Datei `docs/mapping-dsl.md` ist die verbindliche Dokumentation der Mapping-Konfiguration.

Sie soll enthalten:

- vollständiges YAML-Schema,
- Beschreibung von `job`, `inputs`, `outputs`, `models`, `mapping`, `rules`, `sources`, `target`, `assign`, `refs`, `joins`, `where`, `defaults`, `enumMaps`, `oidStrategy`, `basketStrategy` und `failPolicy`,
- Unterschied zwischen rohem YAML und kompiliertem Typed Plan,
- Beispiele für einfache 1:1-Attributzuweisungen,
- Beispiele für Literalwerte,
- Beispiele für `where`-Filter,
- Beispiele für Join-Regeln,
- Beispiele für Referenzen und Associations,
- Beispiele für BAG OF STRUCTURE,
- Beispiele für Enum-Mappings,
- Beispiele für Defaultwerte,
- Beispiele für DM01→DMAV,
- Beispiele für DMAV→DM01,
- Hinweise auf bewusst nicht unterstützte Konstrukte.

Jedes DSL-Beispiel muss entweder durch einen Test abgedeckt sein oder explizit als illustrativ markiert werden. Produktiv dokumentierte Beispiele müssen ausführbar sein.

### 28.4 Expression-Dokumentation

Die Datei `docs/expressions.md` dokumentiert die Expression-Sprache und alle registrierten Builtins.

Sie soll enthalten:

- Grundsyntax,
- Zugriff auf Source-Attribute,
- Zugriff auf Strukturen und BAG-Werte,
- Null- und Missing-Semantik,
- Typkonvertierungen,
- String-Funktionen,
- numerische Funktionen,
- Datums- und Zeitfunktionen,
- Enum-Funktionen,
- Referenzfunktionen,
- Geometrie-Funktionen,
- Fehlerfälle,
- deterministische Auswertung,
- Sicherheitsmodell.

Die Dokumentation muss klar unterscheiden zwischen:

- Funktionen, die bereits implementiert sind,
- Funktionen, die geplant sind,
- Funktionen, die bewusst nicht unterstützt werden.

### 28.5 DM01↔DMAV-Dokumentation

Unter `docs/dm01-dmav/` wird die fachliche Dokumentation für den DM01↔DMAV-Use-Case geführt.

Empfohlene Dateien:

- `docs/dm01-dmav/README.md`  
  Einstieg in den Use Case, verwendete Modelle, Versionen, Datenquellen und aktueller Unterstützungsstand.

- `docs/dm01-dmav/correlation-table.md`  
  Beschreibt die Interpretation der XLSX-Korrelationstabelle. Die Datei `DMAV_Korrelationstabelle_20260301.xlsx` soll im Repository abgelegt und aus dieser Dokumentation referenziert werden. Die XLSX ist als Mapping-Hint-Quelle zu behandeln, nicht als direkt ausführbare Mapping-Konfiguration.

- `docs/dm01-dmav/status-matrix.md`  
  Dokumentiert pro Topic/Klasse/Attribut den Mapping-Status: unterstützt, teilweise unterstützt, generierter Vorschlag vorhanden, offen, fachlich unklar, nicht abbildbar oder bewusst nicht unterstützt.

- `docs/dm01-dmav/lfp3-pilot.md`  
  Dokumentiert den LFP3-Pilot in beide Richtungen inklusive Inputdaten, erwarteter Outputdaten, Validierungsresultate und fachlicher Annahmen.

- `docs/dm01-dmav/lossiness.md`  
  Dokumentiert Informationsverlust, nicht invertierbare Transformationen und fachliche Kompromisse.

- `docs/dm01-dmav/open-questions.md`  
  Sammelt offene fachliche Fragen und verweist auf ADRs, sobald eine Frage entschieden wurde.

### 28.6 Dokumentation der Korrelationstabellen-Auswertung

Der Import der XLSX-Korrelationstabelle muss reproduzierbar dokumentiert werden.

Die Dokumentation soll mindestens enthalten:

- Pfad zur XLSX-Datei im Repository,
- erwartete Sheet-Namen,
- verwendetes Hauptsheet,
- relevante Spaltenbereiche,
- Bedeutung bekannter Transformationscodes,
- Umgang mit leeren Zellen,
- Umgang mit zusammengeführten Zellen,
- Umgang mit mehrzeiligen Zellinhalten,
- Umgang mit versteckten Sheets,
- bekannte Inkonsistenzen,
- generierte Artefakte,
- Zeitpunkt und Version des Imports.

Der Importer muss einen Report erzeugen, der in der Dokumentation referenziert werden kann, zum Beispiel:

```text
build/reports/dm01-dmav/correlation-import-report.md
build/generated/dm01-dmav/correlation-hints.json
```

### 28.7 CLI-Dokumentation

Sobald CLI-Kommandos implementiert sind, muss `docs/cli.md` gepflegt werden.

Sie soll enthalten:

- `transform`,
- `inventory`,
- `compile-mapping`,
- `import-correlation`,
- `generate-mapping`,
- `validate-output`,
- gemeinsame Optionen,
- Exit Codes,
- Pfadkonventionen,
- Beispiele.

Jeder dokumentierte CLI-Befehl muss durch mindestens einen automatisierten Test oder Snapshot-Test abgesichert sein.

### 28.8 Testdokumentation

Die Datei `docs/testing.md` beschreibt die Teststrategie.

Sie soll enthalten:

- Unit-Tests,
- Integrationstests,
- Golden-Tests,
- Snapshot-Tests,
- ilivalidator-basierte Validierung,
- Testdatenstruktur,
- Umgang mit grossen Testdaten,
- Umgang mit fachlichen Testfällen,
- erwartete Gradle-Tasks,
- Hinweise für lokale Ausführung auf macOS ARM64.

### 28.9 Dokumentations-Akzeptanzkriterien

Eine Phase darf nur abgeschlossen werden, wenn die Dokumentation zum neuen Verhalten vorhanden ist.

Akzeptanzkriterien:

- Neue DSL-Elemente sind in `docs/mapping-dsl.md` dokumentiert.
- Neue Expression-Funktionen sind in `docs/expressions.md` dokumentiert.
- Neue CLI-Kommandos sind in `docs/cli.md` dokumentiert.
- Neue Diagnostics sind in `docs/dev/diagnostics.md` dokumentiert.
- Neue DM01↔DMAV-Annahmen sind in `docs/dm01-dmav/` dokumentiert.
- Offene Fragen sind in `docs/dm01-dmav/open-questions.md` oder `docs/open-questions.md` erfasst.
- Wichtige Entscheidungen sind als ADR dokumentiert.
- Dokumentierte Beispiele sind ausführbar oder klar als nicht ausführbares Beispiel markiert.

---
## 29. Sicherheits- und Robustheitsanforderungen

### 29.1 Keine beliebige Codeausführung

Mapping-Dateien dürfen keine beliebigen Java-Methoden oder Shell-Kommandos ausführen.

### 29.2 Determinismus

Bei gleicher Eingabe und gleichem Mapping muss der Output stabil sein:

- gleiche OIDs,
- gleiche Objekt-Reihenfolge,
- gleiche Reports,
- gleiche Diagnosen.

### 29.3 Fehlerpolitik

Konfigurierbar:

```yaml
failPolicy: strict | lenient | reportOnly
```

- `strict`: Fehler brechen ab.
- `lenient`: bestimmte Fehler werden Warnungen, falls explizit erlaubt.
- `reportOnly`: für Mapping-Generierung/Analyse, nicht für produktive Transformation.

### 29.4 Logging

- SLF4J verwenden.
- Keine riesigen Objektinhalte standardmässig loggen.
- Debug-Logs für Rule-Ausführung optional.

---

## 30. Performance-Anforderungen

### 30.1 MVP

Für MVP darf alles in Memory laufen.

### 30.2 Ziel

Mittelfristig soll die Engine mit typischen AV-Gemeindedaten umgehen können.

Anforderungen:

- Streaming-Lesen,
- StateStore abstrahiert,
- Indexe gezielt aufbauen,
- keine unnötigen Deep Copies von Geometrien,
- Reports begrenzen/paginieren,
- grosse Diagnostik optional in Datei schreiben.

### 30.3 Persistenz später

Wenn InMemory nicht reicht:

- StateStore in SQLite/H2/DuckDB,
- temporäre Objektserialisierung,
- Indexe auf SourceKey/TargetKey,
- Batch-Schreiben.

---

## 31. Offene fachliche Fragen

Diese Fragen müssen in `docs/dm01-dmav/open-questions.md` geführt und bei Entscheidungen als ADR dokumentiert werden.

### 31.1 Korrelationstabelle

1. Bezieht sich die XLSX auf DMAV Version 1.0 oder 1.1? Der Dateiname/Stand ist 2026, aber die Tabelle nennt teilweise „DMAV Version 1.0“.
2. Sind die Transformationscodes vollständig dokumentiert? In der Erklärung werden `K` und `V` erwähnt; in den Daten können weitere Muster vorkommen.
3. Welche Spalten sind fachlich verbindlich und welche nur dokumentativ?
4. Darf die versteckte Tabelle `Korrelation` als Quelle verwendet werden oder nur das Sheet `Transformation`?
5. Wie werden Mehrfachziele in einer Zelle formal interpretiert?

### 31.2 DM01→DMAV

1. Welche Defaultwerte sind fachlich zulässig, wenn DMAV Mandatory-Attribute verlangt, DM01 aber keine Quelle hat?
2. Wie wird `GueltigerEintrag` gesetzt, wenn DM01 nur `Datum1` oder gar kein Datum hat?
3. Welche Objekte gelten als aktiv/gültig, wenn DM01 `Status`/`Gueltigkeit` anders modelliert?
4. Wie werden projektierte Objekte abgebildet?
5. Welche DM01-Objekte müssen in mehrere DMAV-Modelle kopiert werden?
6. Wie wird verhindert, dass identische Grenz-/Fixpunkte doppelt erzeugt werden?

### 31.3 DMAV→DM01

1. Welche DMAV-Informationen gehen beim Rückweg verloren?
2. Wie werden BAG OF STRUCTURE in DM01-Pos-/Symboltabellen zurückgeführt, wenn DM01 weniger Kardinalität erlaubt?
3. Wie werden DMAV Associations in DM01-Referenzen zurückgeführt?
4. Wie werden DMAV UUID-OIDs in DM01-ITF-OIDs übersetzt?
5. Ist Roundtrip ein Ziel oder nur fachlich plausible Rücktransformation?

### 31.4 Geometrie und Topologie

1. Wie sollen AREA/SURFACE-Unterschiede behandelt werden?
2. Müssen Flächentopologien repariert oder nur validiert werden?
3. Wie werden streitige Grenzen/LINEATTR in beide Richtungen behandelt?
4. Wie werden Kurven/ARCS erhalten?
5. Welche Toleranzen gelten bei geometrischer Identität?

### 31.5 Tooling

1. Soll `ilivalidator` als Java-Library oder externer Prozess eingebunden werden?
2. Sollen Modellfiles aus dem Internet geladen oder lokal vendored werden?
3. Soll der Mapping-Generator generierte YAMLs ins Repo schreiben oder nur nach `build/generated`?
4. Welche Java-Version ist langfristig Ziel: 21 LTS oder 25?

---

## 32. Nicht-Ziele für die ersten Phasen

Nicht im MVP:

- vollständige DM01↔DMAV-Konvertierung,
- vollständige Topologiereparatur,
- generische GUI,
- Webservice,
- parallele Verarbeitung,
- persistenter StateStore,
- vollständiger Roundtrip,
- beliebige Scripting-Sprache,
- automatische fachliche Entscheidung bei unklaren Korrelationen.

---

## 33. Konkrete Arbeitsanweisungen an den LLM-Coding-Agenten

### 33.1 Allgemeine Regeln

1. Arbeite iterativ in kleinen, getesteten Schritten.
2. Nach jeder Änderung muss `./gradlew test` laufen.
3. Für grössere Features Integrationstest ergänzen.
4. Keine grossen Refactorings zusammen mit fachlichen Änderungen.
5. Keine stillen Fallbacks bei Mapping-Fehlern.
6. Jede unklare fachliche Entscheidung in `docs/dm01-dmav/open-questions.md` dokumentieren.
7. Jede Architekturentscheidung als ADR dokumentieren.
8. Generierte Dateien klar als generiert markieren.
9. Keine Mapping-TODOs im strict mode erlauben.
10. Keine DM01/DMAV-Spezialfälle in generische Engine-Schichten einbauen; dafür Profile/Funktionen/Adapter verwenden.

### 33.2 Erste konkrete Tasks

#### Task A: DSL- und Config-Modell bereinigen

- `class:` im YAML unterstützen.
- `inputs:` in SourceSpec als Liste unterstützen.
- Backward-Kompatibilität für `input:` optional.
- Tests ergänzen.

#### Task B: IliPath und ModelService

- `IliPath` implementieren.
- `IliModelService` implementieren.
- Klassen und Attribute aus `TransferDescription` auflösen.
- Tests mit kleinen Testmodellen.

#### Task C: Compiler gegen TypeSystem

- MappingCompiler auf `TransformPlan` umbauen.
- Unknown class/attribute Fehler.
- Mandatory Coverage Report.
- Compile Report JSON/Markdown.

#### Task D: Correlation Importer

- `CorrelationWorkbookImporter` mit Apache POI oder bereits vorhandener XLSX-Library implementieren.
- Sheet `Transformation` parsen.
- Spaltenstruktur testen.
- JSON Export.
- Report mit Counts und Warnungen.

#### Task E: Deterministic UUID

- UUIDv5 oder stabile UUID-Implementierung.
- Namespace in Config.
- OID-Kollisionstest.
- DMAV-OID-Test.

#### Task F: LFP3 Minimal Mapping

- Minimal DM01→DMAV ohne Pos/Symbol.
- Nachführung + LFP3 + Entstehung Association.
- Golden Test.
- Validator.

#### Task G: LFP3 Textposition und Symbol

- `LFP3Pos` zu `Textposition` BAG.
- `LFP3Symbol.Ori` zu `SymbolOri`.
- Kardinalität und Defaults testen.

#### Task H: DMAV→DM01 LFP3

- Eigenes Rückmapping.
- Textposition zurück nach `LFP3Pos`.
- SymbolOri zurück nach `LFP3Symbol`.
- Lossiness dokumentieren.

---

## 34. Definition of Done

Ein Feature ist fertig, wenn:

1. Code implementiert ist.
2. Unit Tests existieren.
3. Integrationstest existiert, falls Feature I/O betrifft.
4. MappingCompiler prüft Fehler früh.
5. Diagnosen strukturiert sind.
6. README/Dokumentation aktualisiert ist.
7. `./gradlew clean test` erfolgreich ist.
8. Bei DM01/DMAV: offizielle Modellquellen dokumentiert sind.
9. Bei DM01/DMAV: offene fachliche Fragen dokumentiert sind.
10. Keine TODOs im produktiven strict Mapping verbleiben.

---

## 35. Schlussbewertung

Das Projekt ist fachlich und technisch sinnvoll. Die bisherige Umsetzung ist ein guter Scaffold, aber noch kein belastbarer Transformer. Die entscheidende nächste Ausbaustufe ist nicht mehr Runtime-Code, sondern ein **modellbewusster MappingCompiler** mit TypeSystem, RoleResolver, typisierten Expressions und deterministischer OID/Basket-Strategie.

Für DM01↔DMAV ist der richtige Weg:

```text
Korrelationstabelle + ili2c-Metamodell + Heuristiken
-> Mapping-Kandidaten
-> strenger Compiler
-> manuelle Klärung offener Fragen
-> Golden Tests
-> ilivalidator
```

Nicht zielführend wäre, die XLSX direkt in Runtime-Regeln zu übersetzen. Sie soll als fachliche Hint-Quelle dienen. Erst der Compiler und die Tests entscheiden, ob daraus ein robustes Mapping wird.
