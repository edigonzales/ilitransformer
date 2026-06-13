# Mapping DSL

Die Mapping-Datei ist eine YAML-Konfiguration, die die Transformation von INTERLIS-Transferdaten steuert. Sie wird durch den `MappingCompiler` in einen typisierten Ausführungsplan (`TransformPlan`) übersetzt.

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
| `mapping.compileMode` | `string` | Nein | `strict` (default) oder `allowTodos` |
| `mapping.rules` | `list[RuleSpec]` | Ja | Transformationsregeln |

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

## Nicht unterstützte Konstrukte

- `joins` — DSL-Feld vorbereitet, Runtime noch nicht (komplexe Joins)
- `create` — DSL-Feld vorbereitet, noch nicht implementiert
- Externe OID-Strategie (`external`) — Stub
- Expression-basierte Basket-Strategie — Stub

## Typisierte Expression-Auswertung

Seit Phase 4 werden Expressions typisiert ausgewertet. Der `MappingCompiler` inferiert Expression-Typen über `FunctionRegistry` und validiert gegen Zieltypen:

- `truncate()` → TEXT
- `toXmlDateTime()` → XML_DATE_TIME
- `round()` → NUMERIC

Typ-Inkompatibilitäten werden als `ILITRF-MAP-TYPE-MISMATCH` (WARNING) diagnostiziert, da die Runtime Typ-Koersion durchführen kann.

Siehe `docs/expressions.md` für die vollständige Expression-Referenz.
