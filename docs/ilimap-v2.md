# ilimap DSL v2 Referenz

Dieses Dokument beschreibt die `.ilimap`-DSL (v2) fuer ilitransformer. Die DSL ist eine
Alternative zur bestehenden YAML-Konfiguration und wird in denselben `JobConfig`- und
`TransformPlan`-Pfad uebersetzt.

Status: Implementiert und nutzbar via CLI (`transform`, `validate-mapping`, `convert-mapping`).

## Zielbild

Die Sprache macht grosse, produktive Mapping-Profile besser lesbar als YAML. Sie ist
deklarativ, beschreibt Transformationen und verwendet die bestehende Expression-Sprache.

Ziele:

- Eine `.ilimap`-Datei ist ein vollstaendiges Mapping-Profil.
- Nicht erkannte Syntax ist ein Fehler. Es gibt keine still ignorierten Felder.
- Die bestehende Expression-Sprache bleibt erhalten.
- Die generische Engine bleibt modellneutral.
- Die Syntax ist eindeutig genug fuer Parser, Compiler und spaetere IDE-Unterstuetzung.

## Nicht-Ziele

- Keine Makros, Includes oder bedingte Kompilierung.
- Keine neue Runtime-Semantik gegenueber der YAML-DSL.
- Keine INTERLIS-Modellsyntax in `.ilimap`.
- Keine Ref-Kurzform (`->` / `using`) in v2.0.
- Kein Versuch, YAML direkt syntaktisch kompatibel zu halten.

## Grundstruktur

Eine `.ilimap`-Datei beginnt immer mit `mapping v2`:

```ilimap
mapping v2 "minimal-example" {
  job {
    description "Copy SourceClass to TargetClass with scalar attributes";
    modeldir "models/";
  }

  input src-input {
    path "data/input.xtf";
    model "ExampleModel";
  }

  output tgt-output {
    path "output.xtf";
    model "ExampleModel";
  }

  rule copy-rule {
    target tgt-output class "ExampleModel.ExampleTopic.TargetClass";
    source src from src-input class "ExampleModel.ExampleTopic.SourceClass";

    assign {
      Label = src.Name;
      Size = src.Count;
      Enabled = src.Active;
    }
  }
}
```

Der optionale String nach `v2` ist der Profilname. Die Sprache verwendet `{ ... }` als
Blockgrenzen und Semikolons als Statement-Terminatoren.

## Lexikalische Regeln

### Kommentare

```ilimap
// Zeilenkommentar
/* Blockkommentar */
```

`#` ist kein Kommentarzeichen, da `#EnumValue` ein Expression-Literal ist.

### Strings

```ilimap
"input/dm01.itf"
"DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3"
```

Strings verwenden doppelte Anfuehrungszeichen. Escape-Sequenzen (`\"`, `\\`) werden
vom Lexer erhalten.

### Lokale IDs (symbolId)

```text
dm01
dmav
lfp3-nachfuehrung
Textposition
copy-rule
```

Lokale IDs duerfen Buchstaben, Ziffern, `_` und `-` enthalten und muessen mit einem
Buchstaben beginnen. Sie werden fuer `input`, `output`, `rule`, `enum` und `bag`
Namen verwendet.

### Alias-IDs (aliasId)

```text
p
nf
src
bb
```

Alias-IDs folgen strengeren Regeln: sie duerfen kein `-` enthalten, da Bindestriche
in Expressions als Minus interpretiert werden koennten. Alias-IDs werden fuer
`source`-Aliases und `bag`/`join`-Aliases verwendet.

### Reservierte Woerter

Die folgenden Woerter sind als Keywords reserviert und duerfen nicht als IDs verwendet
werden: `mapping`, `job`, `input`, `output`, `oid`, `basket`, `enum`, `rule`,
`target`, `source`, `from`, `where`, `join`, `identity`, `assign`, `defaults`,
`bag`, `ref`, `create`, `loss`, `metadata`, `class`, `inner`, `left`, `mode`,
`structure`, `maxItems`, `parentRef`, `association`, `role`, `required`,
`sourcePath`, `reasonCode`, `description`, `when`, `direction`, `roundtrip`,
`lossiness`, `option`, `true`, `false`, `null`.

### Literals

```ilimap
true
false
null
42
3.14
#LFP3
"Text"
```

## Expression-Abgrenzung

Expressions in `.ilimap` sind nicht in Strings eingeschlossen (im Gegensatz zu YAML).
Der Parser liest die rechte Seite einer Zuweisung bis zum Semikolon als Expression-Text.
Dabei beachtet der `IlimapExpressionReader`:

- Semikolons innerhalb von Strings (`"..."`) werden ignoriert.
- Semikolons innerhalb von Klammern (`(...)`) werden ignoriert.
- Semikolons innerhalb von Block-Kommentaren (`/* ... */`) werden ignoriert.
- Erst ein Semikolon auf oberster Verschachtelungsebene beendet die Expression.

Beispiel:

```ilimap
assign {
  Beschreibung = truncate(replace(nf.Beschreibung, ";", ","), 60);
}
```

## Top-Level-Bloecke

Erlaubte Elemente innerhalb von `mapping v2`:

| Element | Anzahl | Zweck |
|---|---:|---|
| `job` | 0..1 | Metadaten, Policies, Modelldirs |
| `input` | 1..n | Eingabedateien |
| `output` | 1..n | Ausgabedateien |
| `oid` | 0..1 | OID-Strategie |
| `basket` | 0..1 | Basket-Strategie |
| `enum` | 0..n | Enum-/Wert-Mappingtabellen |
| `defaults` | 0..1 | Globale Zielattribut-Defaults |
| `rule` | 1..n | Transformationsregeln |

### job

```ilimap
job {
  name "dm01-to-dmav-lfp3";
  description "Pilot-Transformation DM01 LFP3 nach DMAV LFP3";
  direction dm01-to-dmav;
  failPolicy strict;
  compileMode compatible;
  modeldir "https://models.geo.admin.ch/";
  modeldir "models/";
}
```

Alle Felder sind optional. `failPolicy`: `strict`, `lenient`, `reportOnly`.
`compileMode`: `strict`, `compatible`, `report`. `modeldir` darf mehrfach angegeben
werden.

### input und output

```ilimap
input dm01 {
  path "input/dm01.itf";
  model "DM01AVCH24LV95D";
  format itf;
}

output dmav {
  path "build/out/dmav-lfp3.xtf";
  model "DMAV_FixpunkteAVKategorie3_V1_1";
  format xtf;
}
```

`format` ist optional. Erlaubte Werte: `itf`, `xtf`, `csv`, `gpkg`, `jdbc`. `csv`, `gpkg` und `jdbc`
sind bewusst flache, nur lesbare Eingabeformate (siehe unten).

Optional koennen `input`- und `output`-Bloecke generische Formatoptionen deklarieren:

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

- Jede `option`-Zeile hat die Form `option <key> <value>;`. Der Schluessel ist ein Identifier
  oder String, der Wert ein String, eine Zahl oder ein Boolean.
- Alle Werte werden als Strings gespeichert (`true` wird zu `"true"`, `10000` zu `"10000"`).
- Die Optionen werden an den jeweiligen Formatprovider durchgereicht. Die nativen
  INTERLIS-Formate werten sie derzeit nicht aus und ignorieren sie.

Das `csv`-Format ist als Eingabeformat aktiv und wertet folgende Optionen aus:

| Option | Default | Bedeutung |
|---|---|---|
| `firstLineIsHeader` | `true` | Erste Zeile enthaelt die Spaltennamen |
| `separator` | `,` | Trennzeichen (ein Zeichen) |
| `delimiter` | `"` | Anfuehrungszeichen (ein Zeichen) |
| `encoding` | `UTF-8` | Zeichensatz der Datei |

CSV ist bewusst flach: eine Tabelle, deren Spalten auf die Attribute genau einer Klasse des
Quellmodells abgebildet werden. Strukturen, Referenzen und Geometrie kann CSV nicht ausdruecken.
Ein vollstaendiges Beispiel liegt unter `examples/csv-to-xtf/`.

Das `gpkg`-Format ist ebenfalls als tabellarisches Eingabeformat aktiv und wertet folgende Optionen aus:

| Option | Default | Bedeutung |
|---|---|---|
| `table` | *(Pflicht)* | Tabellenname in der GeoPackage-Datei |
| `fetchSize` | `10000` | Zeilen pro Datenbank-Roundtrip |

GeoPackage ist in dieser Phase ein nur lesbares Eingabeformat mit optionaler Punktgeometrie.
Strukturen und Referenzen kann es nicht ausdruecken. Ein vollstaendiges Beispiel liegt unter
`examples/gpkg-to-xtf/`. Ein Beispiel mit Punktgeometrie liegt unter `examples/gpkg-spatial-to-xtf/`.

Das `jdbc`-Format liest tabellarisch aus einer beliebigen JDBC-Datenbank. Es hat keinen `path`,
sondern einen `connection`-Block und einen oder mehrere `query`-Bloecke. Jede `query` entspricht
genau einer flachen Quellklasse und wird zu einem Korb (`basket`) im Transferstrom:

```ilimap
input db {
  model "DemoJdbcSource";
  format jdbc;
  connection {
    driver "org.sqlite.JDBC";
    url "jdbc:sqlite:build/demo.sqlite";
    userEnv "PGUSER";
    passwordEnv "PGPW";
    property "ApplicationName" "ilitransformer";
  }
  query municipalities {
    topic "DemoJdbcSource.Data";
    class "DemoJdbcSource.Data.Municipality";
    basketId "b1";
    oidColumn "id";
    sql "select id, bfsnr, name, population from municipalities";
    column "gemeinde" "Name";
  }
}
```

- `connection` deklariert `driver` (optional), `url` (Pflicht) sowie Anmeldedaten. Anmeldedaten
  koennen inline (`user`/`password`) oder – bevorzugt – ueber Umgebungsvariablen (`userEnv`/
  `passwordEnv`) gesetzt werden. Zusaetzliche Treiber-Properties werden mit
  `property "key" "value";` (wiederholbar) gesetzt.
- `query` deklariert `class` (Pflicht, skopierter Klassenname) und `sql` (Pflicht). `topic`,
  `basketId` und `oidColumn` sind optional. Ohne `oidColumn` werden deterministische OIDs erzeugt.
  Mit `column "<db-Spalte>" "<Attribut>";` (wiederholbar) wird eine Spalte auf einen abweichenden
  Attributnamen abgebildet; die `oidColumn` wird nicht als Attribut gesetzt.
- Treiber ausser dem mitgelieferten SQLite-Treiber muessen auf dem Klassenpfad liegen. Passwoerter
  werden nie in Logs, Diagnostics oder Reports geschrieben.

JDBC ist bewusst flach (Phase 6, ohne Geometrie). Ein vollstaendiges Beispiel liegt unter
`examples/jdbc-to-xtf/`. Eine reale PostgreSQL/PostGIS-Instanz fuer Tests liegt unter
`dev/stack/compose.yml`.

### oid

```ilimap
oid deterministicUuid {
  namespace "dm01-to-dmav-lfp3";
}
```

Erlaubte Strategien: `preserve`, `integer`, `uuid`, `deterministicUuid`.

`external` ist syntaktisch reserviert, aber semantisch noch nicht implementiert.

### basket

```ilimap
basket byTopic;
```

Erlaubte Strategien: `preserve`, `generateUuid`, `preserveOrGenerateUuid`, `byTopic`.

`expression` ist syntaktisch reserviert, aber semantisch noch nicht implementiert.

### enum

```ilimap
enum Zuverlaessigkeit_DM01_DMAV {
  "ja" => true;
  "nein" => false;
}
```

Ein Enum-Block ist eine benannte Mappingtabelle. `=>` trennt Quellwert von Zielwert.
Beide Seiten verwenden Literals (Strings, Booleans, Zahlen, `null`, `#EnumValue`).

### defaults

```ilimap
defaults {
  GueltigerEintrag = now();
  AktiverUnterhalt = true;
}
```

Globale Defaults gelten fuer Zielattribute, sofern eine Rule keinen spezifischeren
Default oder eine explizite Zuweisung definiert.

## Rules

Eine Rule erzeugt Zielobjekte aus einem oder mehreren Quellobjekten.

```ilimap
rule lfp3 {
  target dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3";
  source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
  where p.LFPArt == #LFP3;
  identity p.NBIdent, p.Nummer;

  assign {
    Nummer = p.Nummer;
    LFPArt = #LFP3;
    Lagegenauigkeit = div(p.LageGen, 100.0);
    IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
  }
}
```

Erlaubte Rule-Elemente:

| Element | Anzahl | Zweck |
|---|---:|---|
| `target` | 1 | Zieloutput und Zielklasse |
| `source` | 1..n | Quellalias, Input und Quellklasse |
| `where` | 0..1 | Rule-Filter |
| `join` | 0..n | Source-Joins |
| `identity` | 0..1 | Schluesselfelder fuer deterministische OID |
| `assign` | 0..1 | Zielattribut-Zuweisungen |
| `defaults` | 0..1 | Rule-spezifische Defaults |
| `bag` | 0..n | BAG OF STRUCTURE Mapping |
| `ref` | 0..n | Referenzen / Associations |
| `create` | 0..n | Zusaetzliche Zielobjekte |
| `loss` | 0..n | Dokumentierter Informationsverlust |
| `metadata` | 0..1 | Fachliche Metadaten |

### target

```ilimap
target dmav class "DMAV_Model.Topic.Class";
```

Das erste Argument ist die `output`-ID. Die Klasse wird als String geschrieben.

### source

```ilimap
source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
source p from dm01, dm01_extra class "Source.Model.Topic.Class";
source nf from dm01 class "M.T.Nachfuehrung" where nf.Gueltigkeit != #projektiert;
```

Das erste Argument ist der Alias (aliasId). Nach `from` stehen eine oder mehrere
Input-IDs. Ein optionaler Source-Filter kann mit `where` direkt auf der Source-Zeile
stehen.

### where

```ilimap
where p.LFPArt == #LFP3;
```

Rule- und Source-Filter verwenden die bestehende Expression-Sprache.

### join

```ilimap
join inner os to plz on eq(os.PLZ, plz.PLZ);
join left pos to p on eq(pos.LFP3Pos_von, p);
```

`inner` und `left` entsprechen der Join-Semantik.

### identity

```ilimap
identity p.NBIdent, p.Nummer;
```

Expression-Pfade die den stabilen Quellschluessel fuer deterministische OIDs bilden.

### assign

```ilimap
assign {
  Nummer = p.Nummer;
  LFPArt = #LFP3;
  Lagegenauigkeit = div(p.LageGen, 100.0);
  IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
}
```

Links steht immer ein Zielattribut-Name. Rechts steht eine Expression.

### defaults in Rules

```ilimap
defaults {
  Beschreibung = "";
}
```

Rule-Defaults ueberschreiben globale Defaults und werden nur verwendet, wenn das
Zielattribut nicht explizit in `assign` gesetzt wird.

## Bags

```ilimap
bag Textposition {
  from pos in dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos"
    where refEquals(pos.LFP3Pos_von, p);

  structure "DMAVTYM_Grafik_V1_0.Textposition";
  mode embed;
  maxItems 1;

  assign {
    Position = pos.Pos;
    Orientierung = coalesce(pos.Ori, 100.0);
  }
}
```

Nested Bags werden als `bag` innerhalb eines `bag` geschrieben:

```ilimap
bag Objektnummer {
  from gn in dm01 class "DM01AVCH24LV95D.Bodenbedeckung.Gebaeudenummer";
  parentRef attribute "Gebaeudenummer_von" parent bb;

  assign {
    Nummer = gn.Nummer;
  }

  bag Textposition {
    from gnp in dm01 class "DM01AVCH24LV95D.Bodenbedeckung.GebaeudenummerPos";
    parentRef attribute "GebaeudenummerPos_von" parent gn;

    assign {
      Position = gnp.Pos;
      Orientierung = coalesce(gnp.Ori, 100.0);
    }
  }
}
```

Erlaubte Bag-Elemente:

| Element | Anzahl | Zweck |
|---|---:|---|
| `from` | 0..1 | Quellstruktur oder Quelltabelle; fehlt nur bei synthetischen Parent-Bags |
| `target` | 0..1 | Ziel-Bag-Attribut, wenn der Bag-Name nicht dem Zielattribut entspricht |
| `structure` | 0..1 | Zielstrukturklasse |
| `mode` | 0..1 | `embed` oder `expand` |
| `maxItems` | 0..1 | Kardinalitaetsbegrenzung (muss > 0 sein) |
| `parentRef` | 0..1 | Parent-Child-Beziehung |
| `where` | 0..1 | Bag-Filter; bei synthetischen Parent-Bags entscheidet er, ob genau ein Eintrag erzeugt wird |
| `assign` | 0..1 | Strukturattribut-Zuweisungen |
| `bag` | 0..n | Nested Bags |

Ein Bag ohne `from` ist ein synthetischer Parent-Bag. Er erzeugt pro Parent-Zielobjekt
hoechstens ein Struktur-Element aus den Parent-Quellattributen. Dafuer muss `target`
gesetzt sein, damit ein vergessenes `from` nicht still akzeptiert wird.

```ilimap
bag SyntheticSymbolposition {
  target Symbolposition;
  where not(existsIn("dm01", "DM01.Topic.Symbol", "Symbol_von", oid(bb)));
  assign {
    Position = pointOnSurface(bb.Geometrie);
    Orientierung = 100.0;
  }
}
```

### maxItems-Semantik

`maxItems` begrenzt die Anzahl der Strukturelemente, die fuer ein Zielobjekt erzeugt
werden. Ein Wert von 0 wird vom Semantic Validator abgelehnt. Wenn mehr Quellobjekte
gefunden werden als `maxItems` erlaubt, werden ueberzaehlige Eintraege nicht uebernommen.

## Referenzen (Langform)

```ilimap
ref Entstehung {
  association "Entstehung_LFP3";
  role "Entstehung";
  required;
  target rule lfp3-nachfuehrung sourceRef p.Entstehung;
}
```

`required` ist ein Marker. Wenn er fehlt, ist die Referenz optional. `target rule`
referenziert die Zielregel. `sourceRef` gibt den Quellpfad zur Referenzaufloesung an.

`association` und `role` sind optional. `target rule` mit `sourceRef` koennen ebenfalls
weggelassen werden, falls nur die Assoziation dokumentiert wird.

Die Ref-Kurzform (`ref Name -> rule using expr`) ist syntaktisch reserviert, aber in
v2.0 **nicht implementiert**. Der Parser lehnt die Kurzform aktiv ab.

## Create

```ilimap
create class "TargetModel.Topic.AdditionalClass" {
  assign {
    ExtraAttr = s.SomeField;
  }
}
```

`create` erzeugt zusaetzliche Zielobjekte im Kontext einer Rule. Der Block wird ueber
`IlimapToJobConfigMapper` auf `CreateSpec` in `JobConfig` abgebildet.

## Loss

```ilimap
loss {
  sourcePath p.SymbolOri;
  reasonCode "not-representable";
  description "DM01 Symbolorientierung ist in dieser Zielstruktur nicht abbildbar.";
  when defined(p.SymbolOri);
}
```

Loss-Blocks dokumentieren bekannten Informationsverlust. Sie werden ueber
`IlimapToJobConfigMapper` auf `LossSpec` abgebildet. Alle Felder (`sourcePath`,
`reasonCode`, `description`, `when`) werden gemappt und sind in `JobConfig` verfuegbar.
Bei Single-Source-Rules werden passende Loss-Blocks auch fuer durch `where` gefilterte
Quellobjekte aufgezeichnet. Damit koennen bewusst verworfene Quellobjekte dokumentiert
werden.

## Metadata

```ilimap
metadata {
  direction dmav-to-dm01;
  roundtrip notGuaranteed;
  lossiness minor;
}
```

Metadata-Blocks dokumentieren fachliche Metadaten und werden auf `MetadataSpec`
abgebildet. Alle Felder (`direction`, `roundtrip`, `lossiness`) werden gemappt.

## Defaults- und Null-Semantik

Defaults koennen auf zwei Ebenen definiert werden:

1. **Globale Defaults** (`defaults { ... }` auf Top-Level): gelten fuer alle Rules.
2. **Rule-Defaults** (`defaults { ... }` innerhalb einer Rule): ueberschreiben globale
   Defaults fuer diese Rule.

Ein Default-Wert wird nur verwendet, wenn das Zielattribut **nicht** explizit in
`assign` gesetzt wird. Explizite Zuweisungen (auch `= null`) haben immer Vorrang.

## Mapping nach JobConfig

Die `.ilimap`-DSL bildet ohne neue Runtime-Semantik auf die bestehende Modellstruktur ab:

| `.ilimap` | `JobConfig` |
|---|---|
| `mapping v2` | `version = 1` (Formatversion lebt nur im Loader) |
| Profilname (String nach `v2`) | `job.name` (Fallback wenn kein `job.name`) |
| `job` | `JobSection` |
| `input` | `job.inputs[]` |
| `output` | `job.outputs[]` |
| `oid` | `mapping.oidStrategy` |
| `basket` | `mapping.basketStrategy` |
| `enum` | `mapping.enums` |
| `defaults` | `mapping.defaults` |
| `rule` | `mapping.rules[]` |
| `target` | `RuleSpec.target` |
| `source` | `RuleSpec.sources[]` |
| `assign` | `RuleSpec.assign` |
| `identity` | `RuleSpec.identity` |
| `join` | `RuleSpec.joins` |
| `bag` | `RuleSpec.bags` / `BagSpec.nestedBags` |
| `ref` | `RuleSpec.refs` |
| `create` | `RuleSpec.create` |
| `loss` | `RuleSpec.losses` |
| `metadata` | `RuleSpec.metadata` |

Der Pipeline-Pfad ist: `.ilimap` -> `IlimapLoader` -> `JobConfig` -> `MappingCompiler`
-> `TransformPlan`.

Symbolische Enum-Map-Referenzen (z.B. `enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV)`)
werden vom `IlimapExpressionNormalizer` beim Mapping nach `JobConfig` in
String-Referenzen normalisiert (z.B. `enumMap(p.LageZuv, "Zuverlaessigkeit_DM01_DMAV")`).

## Migration aus YAML

YAML v1 kann weiterhin geladen werden. Fuer neue Profile ist `.ilimap` v2 das
bevorzugte Autorenformat.

### Converter

```bash
ilitransformer convert-mapping --from mapping.yaml --to mapping.ilimap
```

### Migrationsregeln

- `job.inputs[]` wird zu `input`-Blocks.
- `job.outputs[]` wird zu `output`-Blocks.
- `mapping.enums` wird zu `enum`-Blocks.
- `mapping.rules[]` wird zu `rule`-Blocks.
- `assign`-Maps werden zu `assign { target = expression; }`.
- YAML-Strings mit Expressions (`"${expr}"`) werden in `.ilimap` zu unquoted
  Expression-Statements.
- Kommentare koennen nicht automatisch migriert werden.

## Bekannte Einschraenkungen

| Einschraenkung | Status |
|---|---|
| Ref-Kurzform (`->` / `using`) | Syntaktisch reserviert, nicht implementiert |
| Includes/Makros | Nicht geplant fuer v2 |
| Qualifizierte INTERLIS-Namen als Tokens | Nicht unterstuetzt; Modell-/Klassenpfade werden als Strings geschrieben |
| `oid external` | Syntaktisch reserviert, semantisch nicht implementiert |
| `basket expression` | Syntaktisch reserviert, semantisch nicht implementiert |

## Grammatik

Referenzgrammatik in EBNF-Notation:

```ebnf
file              = mappingDecl ;
mappingDecl       = "mapping" "v2" string? block ;

block             = "{" element* "}" ;
element           = jobDecl
                  | inputDecl
                  | outputDecl
                  | oidDecl
                  | basketDecl
                  | enumDecl
                  | defaultsDecl
                  | ruleDecl ;

jobDecl           = "job" "{" jobStmt* "}" ;
jobStmt           = ("name" string
                  | "description" string
                  | "direction" id
                  | "failPolicy" id
                  | "compileMode" id
                  | "modeldir" string) ";" ;

inputDecl         = "input" id ioBlock ;
outputDecl        = "output" id ioBlock ;
ioBlock           = "{" ioStmt* "}" ;
ioStmt            = scalarIoStmt | connectionDecl | queryDecl ;
scalarIoStmt      = ("path" string
                  | "model" string
                  | "format" id
                  | "option" (id | string) optionValue) ";" ;
optionValue       = string | number | boolean ;

# connectionDecl and queryDecl are only valid inside input blocks (JDBC).
connectionDecl    = "connection" "{" connectionStmt* "}" ;
connectionStmt    = ("driver" string
                  | "url" string
                  | "user" string
                  | "password" string
                  | "userEnv" string
                  | "passwordEnv" string
                  | "property" string string) ";" ;
queryDecl         = "query" id "{" queryStmt* "}" ;
queryStmt         = ("topic" string
                  | "class" string
                  | "basketId" string
                  | "oidColumn" string
                  | "sql" string
                  | "column" string string) ";" ;

oidDecl           = "oid" id (";" | "{" oidStmt* "}") ;
oidStmt           = "namespace" string ";" ;
basketDecl        = "basket" id ";" ;

enumDecl          = "enum" id "{" enumEntry* "}" ;
enumEntry         = literal "=>" literal ";" ;

defaultsDecl      = "defaults" assignmentBlock ;

ruleDecl          = "rule" id "{" ruleElement* "}" ;
ruleElement       = targetStmt
                  | sourceStmt
                  | whereStmt
                  | joinStmt
                  | identityStmt
                  | assignDecl
                  | defaultsDecl
                  | bagDecl
                  | refDecl
                  | createDecl
                  | lossDecl
                  | metadataDecl ;

targetStmt        = "target" id "class" string ";" ;
sourceStmt        = "source" id "from" idList "class" string whereTail? ";" ;
whereStmt         = "where" expression ";" ;
whereTail         = "where" expression ;
joinStmt          = "join" ("inner" | "left") id "to" id "on" expression ";" ;
identityStmt      = "identity" expressionList ";" ;

assignDecl        = "assign" assignmentBlock ;
assignmentBlock   = "{" assignment* "}" ;
assignment        = id "=" expression ";" ;

bagDecl           = "bag" id "{" bagElement* "}" ;
bagElement        = bagFromStmt
                  | targetBagStmt
                  | structureStmt
                  | modeStmt
                  | maxItemsStmt
                  | parentRefStmt
                  | whereStmt
                  | assignDecl
                  | bagDecl ;
bagFromStmt       = "from" id "in" id "class" string whereTail? ";" ;
targetBagStmt     = "target" id ";" ;
structureStmt     = "structure" string ";" ;
modeStmt          = "mode" ("embed" | "expand") ";" ;
maxItemsStmt      = "maxItems" number ";" ;
parentRefStmt     = "parentRef" ("attribute" | "role") string "parent" id ";" ;

refDecl           = "ref" id refBody ;
refBody           = "{" refStmt* "}" ;
refStmt           = ("association" string
                  | "role" string
                  | "target" "rule" id "sourceRef" expression
                  | "required") ";" ;

createDecl        = "create" "class" string "{" assignDecl? "}" ;
lossDecl          = "loss" "{" lossStmt* "}" ;
lossStmt          = ("sourcePath" expression
                  | "reasonCode" string
                  | "description" string
                  | "when" expression) ";" ;

metadataDecl      = "metadata" "{" metadataStmt* "}" ;
metadataStmt      = ("direction" id
                  | "roundtrip" id
                  | "lossiness" id) ";" ;

idList            = id ("," id)* ;
expressionList    = expression ("," expression)* ;
```

`expression` ist ein Terminal der aeusseren Grammatik. Der Parser liest den Text bis zum Statement-Semikolon und uebergibt ihn an den Expression-Parser. Die formale EBNF der Expression-Language ist in [expressions.md](expressions.md) definiert.

## Statische Semantik

Ein `.ilimap`-Parser erzeugt ein AST (`IlimapDocument`). Danach folgen Validierungsstufen:

1. **Syntaxdiagnostics**: ungueltige Tokens, fehlende Semikolons, nicht geschlossene
   Blocks, ungueltige Statementformen.
2. **Strukturdiagnostics**: fehlende Pflichtteile (input, output, rule, target, source),
   doppelte IDs, ungueltige Strategien.
3. **Symboldiagnostics**: unbekannte Inputs, Outputs, Rules, Aliases, Enum-Maps.

Scopes:

- **Top-Level-Scope**: Inputs, Outputs, Rules, Enum-Maps.
- **Rule-Scope**: Source-Aliases, Join-Aliases, Bag-Namen, Ref-Namen.
- **Bag-Scope**: Bag-Alias plus Parent-Aliases aus aeusserem Scope.
- **Nested-Bag-Scope**: eigener Alias plus aeussere Bag-/Rule-Aliases.

Eindeutigkeit:

- `input`-IDs sind eindeutig.
- `output`-IDs sind eindeutig.
- `rule`-IDs sind eindeutig.
- Source-Aliases innerhalb einer Rule sind eindeutig.
- Bag-Namen innerhalb desselben Blocks sind eindeutig.

Diagnostics enthalten Datei, Zeile und Spalte.
