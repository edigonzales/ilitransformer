# ilimap DSL v2 Entwurf

Status: Sprachspezifikation als Review-Entwurf, nicht implementiert.

Dieses Dokument definiert eine moegliche eigene Mapping-DSL fuer ilitransformer. Die
DSL soll bestehende YAML-Mappings langfristig als primaeres Autorenformat ersetzen,
ohne die Runtime-Semantik neu zu erfinden. Eine `.ilimap`-Datei beschreibt denselben
fachlichen Inhalt wie die heutige YAML-DSL und wird in denselben `JobConfig`- und
`TransformPlan`-Pfad uebersetzt.

## Zielbild

Die Sprache soll grosse, produktive Mapping-Profile besser lesbar machen als YAML.
Sie soll fuer Menschen geschrieben werden koennen, aber trotzdem streng genug sein,
damit Parser, Compiler und IDE-Unterstuetzung gute Fehlermeldungen liefern.

Die erste Version verfolgt diese Ziele:

- Eine `.ilimap`-Datei ist ein vollstaendiges Mapping-Profil.
- Die Sprache ist deklarativ. Sie beschreibt Transformationen, keine frei
  programmierbaren Ablaufe.
- Die bestehende Expression-Sprache bleibt erhalten.
- Die generische Engine bleibt modellneutral. DM01/DMAV bleibt Profilinhalt, nicht
  Sprach- oder Engine-Sonderfall.
- Nicht erkannte Syntax ist ein Fehler. Es gibt keine still ignorierten Felder.
- Die Syntax ist so eindeutig, dass ein Language Server daraus Completion,
  Diagnostics, Hover, Go-to-definition und Rename ableiten kann.

Nicht-Ziele fuer v2:

- Keine Makros, Includes oder bedingte Kompilierung.
- Keine neue Runtime-Semantik gegenueber der heutigen YAML-DSL.
- Keine Aenderung der Expression-Funktionen.
- Keine INTERLIS-Modellsyntax in `.ilimap`.
- Kein Versuch, YAML direkt syntaktisch kompatibel zu halten.

### Bemerkung

YAML war fuer den Aufbau des Systems eine gute Wahl: schnell parsbar, leicht
serialisierbar und nahe an `JobConfig`. Der Nachteil zeigt sich bei langen Profilen:
Einrueckung, Listen, Strings mit Expressions und fachliche Struktur liegen optisch
eng beieinander. Eine eigene DSL lohnt sich nur, wenn sie den fachlichen Aufbau
sichtbarer macht und bessere Tooling-Anker bietet. Darum ersetzt dieser Entwurf nur
die aeussere Mapping-Struktur, nicht die bewahrte Expression-Sprache.

## IDE-Unterstuetzung

IDE-Unterstuetzung ist mit dieser DSL gut moeglich. Die Sprache ist bewusst auf
feste Keywords, explizite Blockgrenzen und semikolonbeendete Statements ausgelegt.
Das macht sie fuer Lexer, Parser und Language Server einfacher als YAML mit
eingebetteten Expressions.

Moegliche Features:

- Syntaxhighlighting fuer Keywords, lokale IDs, Modellpfade, Expressions und
  Kommentare.
- Completion fuer Keywords, Inputs, Outputs, Rule-IDs, Aliases, Enum-Maps,
  Funktionen und INTERLIS-Klassen/Attribute.
- Diagnostics fuer Syntaxfehler, unbekannte IDs, doppelte Rule-IDs, unbekannte
  Aliases, ungueltige Strategien und Typkonflikte.
- Hover fuer Rule, Source, Target, Enum-Map, Funktion und INTERLIS-Attribut.
- Go-to-definition fuer Rule-Referenzen, Input-/Output-IDs, Enum-Maps und Aliases.
- Rename fuer Rule-IDs, Aliases und lokale IDs.
- Formatter, der Blocks stabil einrueckt und Assignments ausrichtet.

### Bemerkung

JSON Schema kann YAML-Struktur gut pruefen, aber nur begrenzt fachliche Referenzen
verstehen. Eine eigene DSL erlaubt einen echten Symbolbaum: `input`, `output`,
`rule`, `source`, `bag`, `enum` und `ref` werden zu benannten Symbolen. Genau diese
Symbolik braucht eine IDE, um nicht nur Syntax, sondern Mapping-Absicht zu verstehen.

## Grundstruktur

Eine Datei beginnt immer mit einer expliziten Version:

```ilimap
mapping v2 "dm01-to-dmav-lfp3" {
  job {
    direction dm01-to-dmav;
    failPolicy strict;
    compileMode strict;
    modeldir "https://models.geo.admin.ch/";
    modeldir "models/";
  }

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

  oid deterministicUuid {
    namespace "dm01-to-dmav-lfp3";
  }

  basket byTopic;

  enum Zuverlaessigkeit_DM01_DMAV {
    "ja" => true;
    "nein" => false;
  }

  rule lfp3-nachfuehrung {
    target dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3Nachfuehrung";
    source nf from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung";
    identity nf.NBIdent, nf.Identifikator;

    assign {
      NBIdent = nf.NBIdent;
      Identifikator = nf.Identifikator;
      Beschreibung = truncate(nf.Beschreibung, 60);
      Perimeter = nf.Perimeter;
      GueltigerEintrag = toXmlDateTime(coalesce(nf.GueltigerEintrag, nf.Datum1));
    }
  }

  rule lfp3 {
    target dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3";
    source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
    identity p.NBIdent, p.Nummer;

    assign {
      NBIdent = p.NBIdent;
      Nummer = p.Nummer;
      LFPArt = #LFP3;
      Geometrie = p.Geometrie;
      Lagegenauigkeit = div(p.LageGen, 100.0);
      IstLagezuverlaessig = enumMap(p.LageZuv, "Zuverlaessigkeit_DM01_DMAV");
    }

    bag Textposition {
      from pos in dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos"
        where refEquals(pos.LFP3Pos_von, p);

      assign {
        Position = pos.Pos;
        Orientierung = coalesce(pos.Ori, 100.0);
        HReferenzpunkt = coalesce(pos.HAli, "Left");
        VReferenzpunkt = coalesce(pos.VAli, "Bottom");
      }
    }

    ref Entstehung {
      association "Entstehung_LFP3";
      role "Entstehung";
      required;
      target rule lfp3-nachfuehrung sourceRef p.Entstehung;
    }
  }
}
```

### Bemerkung

Die Sprache verwendet `{ ... }` statt Einrueckungssemantik. Das ist absichtlich
weniger YAML-artig, aber robuster fuer Parser und IDEs. Semikolons beenden einfache
Statements. Dadurch duerfen Expressions Leerzeichen, Funktionsaufrufe und
Zeilenumbrueche in kontrollierter Form enthalten, ohne dass der Parser raten muss,
wo ein Statement endet.

## Lexikalische Regeln

Kommentare:

```ilimap
// Zeilenkommentar
/* Blockkommentar */
```

`#` ist kein Kommentarzeichen, weil `#EnumValue` bereits ein Expression-Literal ist.

Strings:

```ilimap
"input/dm01.itf"
"DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3"
```

IDs:

```text
dm01
dmav
lfp3-nachfuehrung
Textposition
```

Lokale IDs duerfen Buchstaben, Ziffern, `_` und `-` enthalten. Sie muessen mit
einem Buchstaben beginnen. INTERLIS-Modell- und Klassenpfade werden in v2 immer als
Strings geschrieben.

Literals:

```ilimap
true
false
null
42
3.14
#LFP3
"Text"
```

### Bemerkung

INTERLIS-Namen koennen lang sein und Punkte enthalten. Sie als Strings zu schreiben
ist etwas ausfuehrlicher, verhindert aber Mehrdeutigkeiten zwischen DSL-Pfaden,
Expression-Pfaden und INTERLIS-Qualifikationen. Lokale IDs bleiben dagegen kurz und
unquoted, damit Rules und Aliases gut lesbar bleiben.

## Top-Level-Blöcke

Erlaubte Top-Level-Elemente innerhalb von `mapping v2`:

| Element | Anzahl | Zweck |
|---|---:|---|
| `job` | 0..1 | Metadaten, Policies, Modelldirs |
| `input` | 1..n | Eingabedateien |
| `output` | 1..n | Ausgabedateien |
| `oid` | 0..1 | OID-Strategie |
| `basket` | 0..1 | Basket-Strategie |
| `enum` | 0..n | Enum-/Wert-Mappingtabellen |
| `defaults` | 0..1 | globale Zielattribut-Defaults |
| `rule` | 1..n | Transformationsregeln |

### `job`

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

`name` und `description` sind optional. `direction`, `failPolicy` und
`compileMode` sind optional und verwenden dieselbe Semantik wie YAML v1.

Erlaubte `failPolicy`-Werte:

- `strict`
- `lenient`
- `reportOnly`

Erlaubte `compileMode`-Werte:

- `strict`
- `compatible`
- `report`

### `input` und `output`

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

`format` ist optional. Wenn es fehlt, darf die Implementierung das Format aus der
Dateiendung ableiten.

### `oid`

```ilimap
oid deterministicUuid {
  namespace "dm01-to-dmav-lfp3";
}
```

Erlaubte Strategien:

- `preserve`
- `integer`
- `uuid`
- `deterministicUuid`
- `external`

`external` ist syntaktisch reserviert, aber semantisch erst gueltig, wenn die Engine
die Strategie implementiert.

### `basket`

```ilimap
basket byTopic;
```

Erlaubte Strategien:

- `preserve`
- `generateUuid`
- `preserveOrGenerateUuid`
- `byTopic`
- `expression`

`expression` ist syntaktisch reserviert, aber semantisch erst gueltig, wenn die
Engine die Strategie implementiert.

### `enum`

```ilimap
enum Bodenbedeckungsart_DM01_DMAV {
  "Gebaeude" => "Gebaeude";
  "befestigt.Strasse_Weg" => "befestigt.Strasse_Weg";
  "Gewaesser.stehendes" => "Gewaesser.stehendes_Gewaesser";
}
```

Ein Enum-Block ist eine benannte Mappingtabelle. Die linke Seite ist der
kanonisierte Quellwert, die rechte Seite der Zielwert. Beide Seiten verwenden
Literals. Werte mit Punkten sollten als Strings geschrieben werden, damit die
aeussere DSL keine neue Expression-Enum-Syntax einfuehrt.

### Bemerkung

Enum-Mappings verwenden `=>` statt `=`, weil sie keine Attributzuweisung sind. Das
hilft beim Lesen: `=` bedeutet "Zielattribut bekommt Expression", `=>` bedeutet
"Quellwert wird auf Zielwert abgebildet".

### `defaults`

```ilimap
defaults {
  GueltigerEintrag = now();
  AktiverUnterhalt = true;
}
```

Globale Defaults gelten wie in YAML v1 fuer Zielattribute, sofern eine Rule keinen
spezifischeren Default oder eine explizite Zuweisung definiert.

## Rules

Eine Rule erzeugt Zielobjekte aus einem oder mehreren Quellobjekten.

```ilimap
rule boflaeche-gueltig {
  target dmav class "DMAV_Bodenbedeckung_V1_1.Bodenbedeckung.Bodenbedeckung";
  source bb from dm01 class "DM01AVCH24LV95D.Bodenbedeckung.BoFlaeche";
  where bb.Art != null;
  identity bb.NBIdent, bb.Identifikator;

  assign {
    Geometrie = bb.Geometrie;
    Qualitaetsstandard = bb.Qualitaet;
    Bodenbedeckungsart = enumMap(bb.Art, "Bodenbedeckungsart_DM01_DMAV");
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
| `create` | 0..n | zusaetzliche Zielobjekte |
| `loss` | 0..n | dokumentierter Informationsverlust |
| `metadata` | 0..1 | fachliche Metadaten |

### `target`

```ilimap
target dmav class "DMAV_Model.Topic.Class";
```

Das erste Argument ist die `output`-ID. Die Klasse wird als String geschrieben.

### `source`

```ilimap
source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
source p from dm01, dm01_extra class "Source.Model.Topic.Class";
source nf from dm01 class "M.T.Nachfuehrung" where nf.Gueltigkeit != #projektiert;
```

Das erste Argument ist der Alias. Nach `from` stehen eine oder mehrere Input-IDs.
Der optionale Source-Filter steht direkt auf der Source.

### `where`

```ilimap
where p.LFPArt == #LFP3;
```

Rule- und Source-Filter verwenden die bestehende Expression-Sprache.

### `join`

```ilimap
join inner os to plz on eq(os.PLZ, plz.PLZ);
join left pos to p on eq(pos.LFP3Pos_von, p);
```

`inner` und `left` entsprechen der heutigen Join-Semantik. Ein Ref-to-Object-Join
bleibt ueber `eq(leftRefPath, rightAlias)` ausdrueckbar.

### `identity`

```ilimap
identity p.NBIdent, p.Nummer;
```

Die Werte sind Expression-Pfade. Sie bilden den stabilen Quellschluessel fuer
deterministische OIDs.

### `assign`

```ilimap
assign {
  Nummer = p.Nummer;
  LFPArt = #LFP3;
  Lagegenauigkeit = div(p.LageGen, 100.0);
  IstLagezuverlaessig = enumMap(p.LageZuv, "Zuverlaessigkeit_DM01_DMAV");
}
```

Links steht immer ein Zielattribut. Rechts steht eine Expression.

### Bemerkung

Expressions sind in `.ilimap` nicht mehr in Strings eingeschlossen. Das ist einer
der groessten Lesbarkeitsgewinne gegenueber YAML. Der Parser kann die rechte Seite
bis zum Semikolon als Expression-Text an den bestehenden Expression-Compiler
weitergeben. Dadurch bleibt die Expression-Semantik stabil, aber die Datei liest
sich weniger wie eine Datenstruktur und mehr wie eine Mapping-Spezifikation.

### `defaults` in Rules

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
| `from` | 1 | Quellstruktur oder Quelltabelle |
| `structure` | 0..1 | Zielstrukturklasse |
| `mode` | 0..1 | `embed` oder `expand` |
| `maxItems` | 0..1 | optionale Kardinalitaetsbegrenzung |
| `parentRef` | 0..1 | Parent-Child-Beziehung |
| `assign` | 0..1 | Strukturattribut-Zuweisungen |
| `bag` | 0..n | Nested Bags |

### Bemerkung

YAML verschachtelt Bags korrekt, aber bei mehreren Ebenen werden die Strukturen
optisch schwer unterscheidbar. Eigene `bag`-Blocks machen die fachliche
Wiederholung sichtbar: Quelle, Parent-Bezug, Assignments, optional weitere Bags.
Das ist auch fuer IDE-Folding und Breadcrumbs hilfreich.

## Referenzen

```ilimap
ref Entstehung {
  association "Entstehung_LFP3";
  role "Entstehung";
  required;
  target rule lfp3-nachfuehrung sourceRef p.Entstehung;
}
```

`required` ist ein Marker. Wenn er fehlt, ist die Referenz optional.

Alternative Kurzform fuer einfache Zielregel-Referenzen:

```ilimap
ref Entstehung -> lfp3-nachfuehrung using p.Entstehung {
  association "Entstehung_LFP3";
  role "Entstehung";
  required;
}
```

### Bemerkung

Die Langform ist eindeutiger und besser fuer Tools. Die Kurzform ist lesbarer bei
haeufigen einfachen Referenzen. Falls der Parser nur eine Form implementieren soll,
sollte v2.0 mit der Langform starten und die Kurzform spaeter ergaenzen.

## Create

```ilimap
create class "TargetModel.Topic.AdditionalClass" {
  assign {
    ExtraAttr = s.SomeField;
  }
}
```

`create` erzeugt wie in YAML v1 zusaetzliche Zielobjekte im Kontext einer Rule.
Nicht unterstuetzte Create-Unterelemente muessen als Compiler-Diagnostic abgelehnt
werden.

## Loss und Metadata

```ilimap
loss {
  sourcePath p.SymbolOri;
  reasonCode "not-representable";
  description "DM01 Symbolorientierung ist in dieser Zielstruktur nicht verlustfrei abbildbar.";
  when defined(p.SymbolOri);
}

metadata {
  direction dmav-to-dm01;
  roundtrip notGuaranteed;
  lossiness minor;
}
```

Diese Blocks bleiben dokumentierend und auswertbar. Sie duerfen keine implizite
Runtime-Logik einfuehren.

## Statische Semantik

Ein `.ilimap`-Parser erzeugt zunaechst ein Syntaxmodell. Danach folgen dieselben
Validierungsstufen wie heute:

1. Syntaxdiagnostics fuer ungueltige Tokens, fehlende Semikolons, nicht geschlossene
   Blocks und ungueltige Statementformen.
2. Strukturdiagnostics fuer fehlende Pflichtteile, doppelte IDs, unbekannte
   Top-Level-Elemente und ungueltige Strategien.
3. Symboldiagnostics fuer unbekannte Inputs, Outputs, Rules, Aliases und Enum-Maps.
4. Modelldiagnostics gegen `ili2c`/`ModelRegistry`, sofern Modelldirs verfuegbar
   sind.
5. Expressiondiagnostics durch den bestehenden Expression-Compiler.
6. Typ- und Runtime-Risiko-Diagnostics durch `MappingCompiler`.

Scopes:

- Top-Level-Scope: Inputs, Outputs, Rules, Enum-Maps.
- Rule-Scope: Source-Aliases, Join-Aliases, Bag-Namen, Ref-Namen.
- Bag-Scope: Bag-Alias plus Parent-Aliases aus aeusserem Scope.
- Nested-Bag-Scope: eigener Alias plus aeussere Bag-/Rule-Aliases.

Eindeutigkeit:

- `input`-IDs sind eindeutig.
- `output`-IDs sind eindeutig.
- `rule`-IDs sind eindeutig.
- Source-Aliases innerhalb einer Rule sind eindeutig.
- Bag-Namen innerhalb desselben Blocks sind eindeutig.

### Bemerkung

Diese Scope-Regeln sind fuer IDE-Unterstuetzung zentral. Sie machen klar, welche
Namen wo sichtbar sind und welche Referenzen umbenannt werden duerfen. Sie
verhindern auch, dass DM01/DMAV-spezifische Konventionen in die Engine wandern.

## Grammatikskizze

Diese Grammatik ist absichtlich kompakt. Sie definiert die aeussere DSL. Expression
wird als Text bis zum Statement-Semikolon gelesen und danach vom bestehenden
Expression-Parser verarbeitet.

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
ioStmt            = ("path" string | "model" string | "format" id) ";" ;

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
                  | structureStmt
                  | modeStmt
                  | maxItemsStmt
                  | parentRefStmt
                  | assignDecl
                  | bagDecl ;
bagFromStmt       = "from" id "in" id "class" string whereTail? ";" ;
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

## Mapping nach `JobConfig`

Die `.ilimap`-DSL bildet ohne neue Runtime-Semantik auf die heutige Modellstruktur
ab:

| `.ilimap` | `JobConfig` |
|---|---|
| `mapping v2` | `version = 2` |
| `job` | `JobSection` |
| `input` | `job.inputs[]` |
| `output` | `job.outputs[]` |
| `oid` | `mapping.oidStrategy` |
| `basket` | `mapping.basketStrategy` |
| `enum` | `mapping.enums` |
| `rule` | `mapping.rules[]` |
| `target` | `RuleSpec.target` |
| `source` | `RuleSpec.sources[]` |
| `assign` | `RuleSpec.assign` |
| `bag` | `RuleSpec.bags` / `BagSpec.nestedBags` |
| `ref` | `RuleSpec.refs` |
| `create` | `RuleSpec.create` |
| `metadata` | `RuleSpec.metadata` |

### Bemerkung

Dieser Zwischenschritt ist wichtig. Wenn `.ilimap` direkt einen `TransformPlan`
erzeugen wuerde, entstuenden zwei Compilerpfade. Das wuerde Tests verdoppeln und
Risiken fuer unterschiedliche Semantik schaffen. Der richtige Schnitt ist:
`.ilimap` -> `JobConfig` -> `MappingCompiler` -> `TransformPlan`.

## Migration aus YAML v1

YAML v1 kann waehrend einer Uebergangsphase weiter geladen werden. Fuer neue
produktive Profile ist `.ilimap` v2 das bevorzugte Autorenformat.

Ein Converter sollte spaeter bereitgestellt werden:

```bash
ilitransformer convert-mapping --from profiles/dm01-to-dmav/1.1/lfp3.yaml --to profiles/dm01-to-dmav/1.1/lfp3.ilimap
```

Migrationsregeln:

- `job.inputs[]` wird zu `input`-Blocks.
- `job.outputs[]` wird zu `output`-Blocks.
- `mapping.enums` wird zu `enum`-Blocks.
- `mapping.rules[]` wird zu `rule`-Blocks.
- `assign`-Maps werden zu `assign { target = expression; }`.
- YAML-Strings, die Expressions enthalten, werden in `.ilimap` unquoted
  Expression-Statements.
- Kommentare koennen nicht verlaesslich automatisch migriert werden und muessen
  bei Bedarf manuell nachgezogen werden.

## Review-Fragen

Diese Punkte sollten vor Implementierung bewusst entschieden werden:

- Soll die Ref-Kurzform bereits in v2.0 enthalten sein oder zuerst nur die
  eindeutigere Langform?
- Soll `model`/`class` immer String sein, oder sollen qualifizierte INTERLIS-Namen
  spaeter als eigene Token erlaubt werden?
- Soll der Converter Kommentare erhalten muessen, oder reicht eine syntaktisch
  korrekte Ausgabe?
- Soll `mapping v2` intern wirklich `JobConfig.version = 2` setzen, oder bleibt
  `JobConfig.version = 1` und die Formatversion lebt nur im Loader?
- Soll eine IDE Extension zuerst aus einer festen Grammatik arbeiten oder direkt
  den spaeteren Parser als Language-Server-Bibliothek wiederverwenden?

### Empfehlung

Fuer v2.0 sollte die Sprache klein bleiben: Langform fuer Referenzen, Strings fuer
Modelle/Klassen, kein Include-System, kein Makro-System, keine neuen Expressions.
Das ergibt eine klare erste Spezifikation und vermeidet, dass die Syntaxdiskussion
zur Semantikdiskussion wird.
