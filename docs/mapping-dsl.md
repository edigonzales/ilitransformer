# Mapping DSL

Die Mapping-Datei ist eine YAML-Konfiguration, die die Transformation von INTERLIS-Transferdaten steuert. Sie wird durch den `MappingCompiler` in einen typisierten Ausführungsplan (`TransformPlan`) übersetzt.

> **Hinweis:** Fuer neue Profile ist die [`.ilimap`-DSL (v2)](ilimap-v2.md) das bevorzugte
> Autorenformat. Bestehende YAML-Mappings koennen mit `ilitransformer convert-mapping`
> konvertiert werden.

Für DM01/DMAV gilt:

- produktive, versionierte Profile liegen unter `profiles/`
- synthetische Test-Mappings liegen unter `src/test/resources/mappings/`

## Version

```yaml
version: 1
```

Pflichtfeld. Muss bei Breaking Changes der DSL erhöht werden. Aktuell: `1`.

## Minimale Mapping-Datei

```yaml
version: 1

job:
  inputs:
    - id: in1
      path: "input.xtf"
      model: "SourceModel"
  outputs:
    - id: out1
      path: "output.xtf"
      model: "TargetModel"

mapping:
  rules:
    - id: my-rule
      target:
        output: out1
        class: "TargetModel.Topic.Class"
      sources:
        - alias: src
          input: in1
          class: "SourceModel.Topic.Class"
      assign:
        AttributeName: "${src.SourceAttribute}"
```

## Job-Sektion

| Feld | Typ | Pflicht | Beschreibung |
|---|---|---|---|
| `version` | `int` | Ja | DSL-Version (mind. 1) |
| `job.name` | `string` | Nein | Name des Jobs |
| `job.description` | `string` | Nein | Beschreibung |
| `job.direction` | `string` | Nein | Transformationsrichtung (z.B. `dm01-to-dmav`) |
| `job.failPolicy` | `string` | Nein | Fehlerpolitik: `strict` (default), `lenient`, `reportOnly` |
| `job.modeldir` | `list[string]` | Nein | Modellverzeichnisse (URLs oder Pfade) |
| `job.inputs` | `list[InputSpec]` | Ja | Eingabedateien |
| `job.outputs` | `list[OutputSpec]` | Ja | Ausgabedateien |

### InputSpec

```yaml
- id: in1          # Pflicht: eindeutige ID
  path: "in.xtf"   # Pflicht: Pfad zur Eingabedatei
  model: "Model"   # Pflicht: INTERLIS-Modellname
  format: "xtf"    # Optional: "itf" oder "xtf" (wird aus Dateiendung erkannt)
```

### OutputSpec

```yaml
- id: out1         # Pflicht: eindeutige ID
  path: "out.xtf"  # Pflicht: Pfad zur Ausgabedatei
  model: "Model"   # Pflicht: INTERLIS-Modellname
  format: "xtf"    # Optional: "itf" oder "xtf"
```

## Mapping-Sektion

| Feld | Typ | Pflicht | Beschreibung |
|---|---|---|---|
| `mapping.oidStrategy` | `OidStrategySpec` | Nein | OID-Strategie |
| `mapping.basketStrategy` | `BasketStrategySpec` | Nein | Basket-Strategie |
| `mapping.enums` | `map[string, map[string, string]]` | Nein | Enum-Mapping-Tabellen |
| `mapping.defaults` | `map[string, string]` | Nein | Default-Werte |
| `mapping.compileMode` | `string` | Nein | `strict` (default), `compatible`, `report` |
| `mapping.rules` | `list[RuleSpec]` | Ja | Transformationsregeln |

#### compileMode Semantik

- `strict` (Default): Warnungen aus der Mapping-Kompilierung werden als Fehler hochgestuft. Typ-Inkompatibilitäten, fehlende Enum-Coverage und Pflichtattributlücken verhindern die Ausführung.
- `compatible`: Erlaubt bewusst bekannte/kompatible Abweichungen, sofern sie nicht runtime-kritisch sind. Warnungen bleiben Warnungen.
- `report`: Plan nur zu Analysezwecken kompilieren (Inventarisierung, Abhängigkeitsprüfung), keine produktive Transformation. `TransformPlan.isReportOnly()` liefert `true`.

### OidStrategySpec

```yaml
oidStrategy:
  default: deterministicUuid   # preserve | integer | uuid | deterministicUuid | external
  namespace: "my-namespace"    # für deterministicUuid
```

Unterstützte Strategien: `preserve`, `integer`, `uuid`, `deterministicUuid`. `external` ist als Stub vorbereitet.

### BasketStrategySpec

```yaml
basketStrategy:
  default: preserveOrGenerateUuid   # preserve | generateUuid | preserveOrGenerateUuid | byTopic | expression
```

Unterstützte Strategien: `preserve`, `generateUuid`, `preserveOrGenerateUuid`, `byTopic`. `expression` ist als Stub vorbereitet.

## RuleSpec

Jede Rule erzeugt Zielobjekte aus Quellobjekten.

| Feld | Typ | Pflicht | Beschreibung |
|---|---|---|---|
| `id` | `string` | Ja | Eindeutige Rule-ID |
| `target` | `TargetSpec` | Ja | Zielklasse und Output |
| `sources` | `list[SourceSpec]` | Ja | Quellklassen (mindestens eine) |
| `where` | `string` | Nein | Filter-Expression für Quellobjekte |
| `identity` | `IdentitySpec` | Nein | Schlüsselfelder für deterministische OID |
| `assign` | `map[string, string]` | Nein | Attributzuweisungen |
| `refs` | `list[RefMapping]` | Nein | Referenzen / Associations |
| `bags` | `map[string, BagSpec]` | Nein | BAG OF STRUCTURE |
| `metadata` | `MetadataSpec` | Nein | Metadaten (Direction, Roundtrip, Lossiness) |
| `defaults` | `map[string, string]` | Nein | Default-Werte für Zielattribute |

### TargetSpec

```yaml
target:
  output: out1     # Pflicht: Output-ID
  class: "M.T.C"   # Pflicht: qualifizierte INTERLIS-Klasse
```

Backward-Compat: Die flachen Felder `targetClass` und `output` werden weiterhin unterstützt.

### SourceSpec

```yaml
sources:
  - alias: src               # Pflicht: Alias für Expressions
    class: "M.T.SourceClass" # Pflicht: qualifizierte INTERLIS-Quellklasse
    inputs: [in1, in2]       # Pflicht: Input-IDs
    where: "src.Status == 'aktiv'" # Optional: Filter
```

Backward-Compat: Das flache Feld `input` (einzelner String) wird weiterhin unterstützt.

### IdentitySpec

```yaml
identity:
  sourceKey: ["src.NBIdent", "src.Nummer"]  # Felder für deterministische OID
```

### AttributeMapping (assign)

```yaml
assign:
  ZielAttribut: "Expression"     # Einfache Zuweisung
  Name: "${src.Name}"             # Quellattribut kopieren
  Status: "#aktiv"                # Literalwert
  Text: "truncate(src.Text, 60)"  # Funktion
```

### RefMapping

```yaml
refs:
  - association: "Entstehung_LFP3"    # Vollqualifizierte Association
    role: "Entstehung"                # Rollenname
    sourceRef: "src.Entstehung"       # Quellreferenz
    targetRule: "lfp3-nachfuehrung"   # Rule-ID des Zielobjekts
```

### BagSpec

```yaml
bags:
  Textposition:
    from:
      input: dm01
      class: "M.T.PosTable"
      alias: pos
      where: "refEquals(pos.Ref, src)"
    structure: "M.Grafik.Textposition"
    assign:
      Position: "pos.Pos"
```

### JoinSpec (unterstützt ab Phase 24)

Equi-Joins zwischen zwei Source-Aliases. Unterstützt `INNER` und `LEFT`.

```yaml
joins:
  - left: src1        # Pflicht: Alias der linken Source
    right: src2       # Pflicht: Alias der rechten Source
    on: "eq(src1.attr, src2)"  # Pflicht: equi-join condition
    type: inner       # Optional: "inner" (default) oder "left"
```

Die `on`-Condition muss ein `eq()`-Aufruf sein:
- `eq(leftPath, rightPath)` — beide PathExpr mit unterschiedlichen Aliases
- `eq(leftPath, rightAlias)` — **Ref-to-Object-Join**: rechte Seite ist ein Bare-Alias. Die linke Attribut-Referenz-OID wird mit der rechten Objekt-OID verglichen. Nützlich für INTERLIS 1 Parent-Child-Beziehungen (z.B. `eq(gnp.GelaendenamePos_von, gn)`).

Derzeit wird **maximal ein Join pro Rule** unterstützt. Mehrere Join-Einträge werden vom Compiler mit einem Fehler abgelehnt.

### MetadataSpec

```yaml
metadata:
  direction: dm01-to-dmav
  roundtrip: notGuaranteed
  lossiness: none   # none | minor | significant | unknown
```

## Enum-Mappings

```yaml
mapping:
  enums:
    Zuverlaessigkeit:
      ja: true
      nein: false
```

## Kompilierung

Der `MappingCompiler` validiert die Mapping-Datei und erzeugt einen `TransformPlan`. Fehlerhafte Mappings werden diagnostiziert:

```bash
ilitransformer validate-mapping --mapping my-mapping.yaml
```

### create / CreateSpec (experimentell)

`create` erzeugt zusätzliche Zielobjekte im Kontext einer Rule. Pro SourceRecord wird ein Create-Zielobjekt erzeugt.

```yaml
mapping:
  rules:
    - id: my-rule
      target:
        output: out1
        class: "TargetModel.Topic.MainClass"
      sources:
        - alias: s
          input: in1
          class: "SourceModel.Topic.Class"
      assign:
        Name: "${s.Name}"
      create:
        - class: "TargetModel.Topic.AdditionalClass"
          assign:
            ExtraAttr: "${s.SomeField}"
```

| Feld | Typ | Pflicht | Beschreibung |
|---|---|---|---|
| `create[].class` | `string` | Ja | Qualifizierte Zielklasse |
| `create[].assign` | `map[string, string]` | Nein | Attributzuweisungen (Expressions) |

**Unterstützt:**
- Zielklasse aus registriertem Modell
- Einfache `assign`-Zuweisungen mit Expressions
- OID-Strategie aus `mapping.oidStrategy` (die globale OID-Strategie gilt auch für Create-Objekte)
- Diagnostik: `MAP_CREATE_UNKNOWN_CLASS`, `MAP_CREATE_INVALID`, `MAP_CREATE_DUPLICATE`

**Noch nicht unterstützt:**
- `where`-Filter für Create-Objekte
- Referenzen (`refs`) in Create-Objekten
- BAGs in Create-Objekten
- OID-Mapping-Registrierung (Source→Target-Verknüpfung)
- Duplikat-OID-Erkennung

Die Semantik ist experimentell und muss pro Produktprofil getestet werden.

## Nicht unterstützte Konstrukte

- Externe OID-Strategie (`external`) — Stub
- Expression-basierte Basket-Strategie — Stub

## Typisierte Expression-Auswertung

Seit Phase 4 werden Expressions typisiert ausgewertet. Der `MappingCompiler` inferiert Expression-Typen über `FunctionRegistry` und validiert gegen Zieltypen:

- `truncate()` → TEXT
- `toXmlDateTime()` → XML_DATE_TIME
- `div()` → NUMERIC

Typ-Inkompatibilitäten werden als `ILITRF-MAP-TYPE-MISMATCH` (WARNING) diagnostiziert, da die Runtime Typ-Koersion durchführen kann.

Siehe `docs/expressions.md` für die vollständige Expression-Referenz.
