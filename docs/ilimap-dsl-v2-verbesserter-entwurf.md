# ilimap DSL v2 — verbesserter Review-Entwurf

Status: Sprachspezifikation als Review-Entwurf, nicht implementiert.

Dieses Dokument definiert eine mögliche eigene Mapping-DSL für `ilitransformer`.
Die DSL soll bestehende YAML-Mappings langfristig als primäres Autorenformat
ersetzen, ohne die Runtime-Semantik neu zu erfinden. Eine `.ilimap`-Datei
beschreibt denselben fachlichen Inhalt wie die heutige YAML-DSL und wird in den
bestehenden `JobConfig`- und `TransformPlan`-Pfad übersetzt.

Der Entwurf härtet besonders folgende Punkte:

1. Expressions werden eindeutig bis zum Statement-Semikolon gelesen.
2. Source-/Join-/Bag-Aliases werden von sonstigen Symbol-IDs getrennt.
3. Enum-Maps werden als echte Symbole referenzierbar.
4. Join-Semantik wird eindeutig auf bereits deklarierte Sources beschränkt.
5. Defaults, Nullwerte, `maxItems` und Referenzen erhalten klare Semantik.
6. Dateiformatversion und interne `JobConfig`-Semantikversion werden getrennt.
7. v2.0 bleibt bewusst klein: nur Ref-Langform, keine Includes, keine Makros.

## Zielbild

Die Sprache soll grosse, produktive Mapping-Profile besser lesbar machen als YAML.
Sie soll für Menschen geschrieben werden können, aber trotzdem streng genug sein,
damit Parser, Compiler und IDE-Unterstützung gute Fehlermeldungen liefern.

Die erste Version verfolgt diese Ziele:

- Eine `.ilimap`-Datei ist ein vollständiges Mapping-Profil.
- Die Sprache ist deklarativ. Sie beschreibt Transformationen, keine frei
  programmierbaren Abläufe.
- Die bestehende Expression-Sprache bleibt erhalten.
- Die generische Engine bleibt modellneutral. DM01/DMAV bleibt Profilinhalt, nicht
  Sprach- oder Engine-Sonderfall.
- Nicht erkannte Syntax ist ein Fehler. Es gibt keine still ignorierten Felder.
- Die Syntax ist so eindeutig, dass ein Language Server daraus Completion,
  Diagnostics, Hover, Go-to-definition und Rename ableiten kann.
- Die DSL ist ein Autorenformat. Die Runtime-Semantik bleibt im bestehenden
  `MappingCompiler` und `TransformPlan` verankert.

Nicht-Ziele für v2.0:

- Keine Makros.
- Keine Includes.
- Keine bedingte Kompilierung.
- Keine neue Runtime-Semantik gegenüber der heutigen YAML-DSL.
- Keine Änderung der Expression-Funktionen.
- Keine INTERLIS-Modellsyntax in `.ilimap`.
- Keine syntaktische YAML-Kompatibilität.
- Keine Ref-Kurzform in v2.0.
- Keine implizite Auswahl bei mehrfach passenden Bag-Objekten.

### Bemerkung

YAML war für den Aufbau des Systems eine gute Wahl: schnell parsbar, leicht
serialisierbar und nahe an `JobConfig`. Der Nachteil zeigt sich bei langen Profilen:
Einrückung, Listen, Strings mit Expressions und fachliche Struktur liegen optisch
eng beieinander. Eine eigene DSL lohnt sich nur, wenn sie den fachlichen Aufbau
sichtbarer macht und bessere Tooling-Anker bietet. Darum ersetzt dieser Entwurf nur
die äussere Mapping-Struktur, nicht die bewährte Expression-Sprache.

## Architekturprinzip

Eine `.ilimap`-Datei wird nicht direkt in einen `TransformPlan` kompiliert.
Stattdessen gilt zwingend:

```text
.ilimap -> IlimapAst -> JobConfig -> MappingCompiler -> TransformPlan
```

Der Loader erzeugt zuerst ein Syntaxmodell beziehungsweise einen AST. Daraus wird
anschliessend eine normale `JobConfig`-Struktur erzeugt. Erst danach greift der
bestehende `MappingCompiler`.

Dieses Prinzip verhindert zwei parallele Compilerpfade und reduziert das Risiko,
dass YAML- und `.ilimap`-Profile fachlich unterschiedlich ausgeführt werden.

## Versionierung

Die Dateiversion und die interne JobConfig-Semantikversion werden getrennt.

```ilimap
mapping v2 "dm01-to-dmav-lfp3" {
  // ...
}
```

`mapping v2` bezeichnet ausschliesslich die Version des `.ilimap`-Dateiformats.
Sie bedeutet nicht automatisch `JobConfig.version = 2`.

Empfohlene interne Abbildung:

```text
IlimapFile.formatVersion = 2
JobConfig.semanticVersion = bestehende Mapping-/Runtime-Semantikversion
```

Falls die heutige YAML-DSL intern `JobConfig.version = 1` verwendet und `.ilimap`
keine neue Runtime-Semantik einführt, darf auch eine `.ilimap v2`-Datei weiterhin
in eine `JobConfig` mit derselben Semantikversion übersetzt werden.

### Entscheidung für v2.0

- `mapping v2` ist eine Formatversion.
- Die interne `JobConfig`-Semantikversion bleibt unabhängig.
- Ein späteres `.ilimap v3` darf weiterhin dieselbe Runtime-Semantik verwenden,
  sofern nur die Syntax erweitert wird.

## IDE-Unterstützung

IDE-Unterstützung ist mit dieser DSL gut möglich. Die Sprache ist bewusst auf feste
Keywords, explizite Blockgrenzen und semikolonbeendete Statements ausgelegt. Das
macht sie für Lexer, Parser und Language Server einfacher als YAML mit
eingebetteten Expressions.

Mögliche Features:

- Syntaxhighlighting für Keywords, Symbol-IDs, Aliases, Modellpfade, Expressions
  und Kommentare.
- Completion für Keywords, Inputs, Outputs, Rule-IDs, Aliases, Enum-Maps,
  Funktionen und INTERLIS-Klassen/Attribute.
- Diagnostics für Syntaxfehler, unbekannte IDs, doppelte Rule-IDs, unbekannte
  Aliases, ungültige Strategien und Typkonflikte.
- Hover für Rule, Source, Target, Enum-Map, Funktion und INTERLIS-Attribut.
- Go-to-definition für Rule-Referenzen, Input-/Output-IDs, Enum-Maps und Aliases.
- Rename für Rule-IDs, Enum-Map-IDs, Aliases und lokale Symbol-IDs.
- Formatter, der Blocks stabil einrückt und Assignments diff-freundlich formatiert.

### Empfehlung für die Implementierung

Die IDE-Erweiterung sollte möglichst den echten Parser oder eine gemeinsame
Parser-Bibliothek verwenden. Eine rein separate Grammatik für Syntaxhighlighting ist
als Ergänzung nützlich, sollte aber nicht die alleinige Quelle für Diagnostics sein.

## Grundstruktur

Eine Datei beginnt immer mit einer expliziten Formatversion:

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
      IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
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

Die Sprache verwendet `{ ... }` statt Einrückungssemantik. Das ist absichtlich
weniger YAML-artig, aber robuster für Parser und IDEs. Semikolons beenden einfache
Statements. Dadurch dürfen Expressions Leerzeichen, Funktionsaufrufe und
Zeilenumbrüche enthalten, ohne dass der Parser raten muss, wo ein Statement endet.

## Lexikalische Regeln

### Kommentare

```ilimap
// Zeilenkommentar
/* Blockkommentar */
```

`#` ist kein Kommentarzeichen, weil `#EnumValue` bereits ein Expression-Literal ist.

### Strings

```ilimap
"input/dm01.itf"
"DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3"
"Text mit ; Semikolon"
```

Strings verwenden doppelte Anführungszeichen. Escaping muss mindestens folgende
Sequenzen unterstützen:

```text
\"  doppeltes Anführungszeichen
\\  Backslash
\n  Zeilenumbruch
\t  Tabulator
```

### Symbol-IDs und Alias-IDs

Die DSL unterscheidet zwei Arten lokaler Namen.

#### Symbol-ID

Symbol-IDs bezeichnen langlebige DSL-Symbole:

- `input`-IDs
- `output`-IDs
- `rule`-IDs
- `enum`-IDs
- `bag`-Namen
- `ref`-Namen

Symbol-IDs dürfen Buchstaben, Ziffern, `_` und `-` enthalten. Sie müssen mit einem
Buchstaben beginnen.

Beispiele:

```text
dm01
dmav
lfp3-nachfuehrung
Bodenbedeckungsart_DM01_DMAV
Textposition
```

#### Alias-ID

Alias-IDs bezeichnen Namen, die innerhalb von Expressions verwendet werden:

- Source-Aliases
- Join-Aliases
- Bag-Source-Aliases

Alias-IDs dürfen Buchstaben, Ziffern und `_` enthalten. Sie müssen mit einem
Buchstaben beginnen. Sie dürfen keinen Bindestrich enthalten.

Beispiele:

```text
p
nf
pos
lfp3_pos
bb
```

Nicht erlaubt als Alias:

```text
lfp3-pos
alte-pos
```

Grund: Ein Bindestrich in einem Alias kollidiert optisch und syntaktisch mit dem
Minus-Operator der Expression-Sprache. `alte-pos.Ori` könnte als Aliaszugriff oder
als Subtraktion gelesen werden.

### Literale

```ilimap
true
false
null
42
3.14
#LFP3
"Text"
```

### INTERLIS-Modell- und Klassenpfade

INTERLIS-Modell- und Klassenpfade werden in v2.0 immer als Strings geschrieben.

```ilimap
target dmav class "DMAV_Model.Topic.Class";
```

Das verhindert Mehrdeutigkeiten zwischen DSL-Pfaden, Expression-Pfaden und
INTERLIS-Qualifikationen.

### Reservierte Keywords

Folgende Wörter sind reserviert und dürfen nicht als unquoted Symbol-ID oder
Alias-ID verwendet werden:

```text
mapping, job, input, output, oid, basket, enum, defaults,
rule, target, source, from, in, class, where, join, inner, left,
identity, assign, bag, ref, association, role, required,
sourceRef, create, loss, metadata, true, false, null,
name, description, direction, failPolicy, compileMode, modeldir,
path, model, format, namespace, structure, mode, maxItems,
parentRef, attribute, reasonCode, roundtrip, lossiness
```

v2.0 führt kein Escaping für lokale IDs ein. Wenn ein Name mit einem Keyword
kollidiert, muss ein anderer lokaler Name gewählt werden.

## Expression-Abgrenzung

Expressions werden nicht als Strings geschrieben. Der `.ilimap`-Parser liest die
rechte Seite eines Expression-Statements als Expression-Text und übergibt diesen
Text danach dem bestehenden Expression-Compiler.

Eine Expression endet beim ersten Semikolon, das sich nicht innerhalb eines
String-Literals, nicht innerhalb eines Kommentars und nicht innerhalb einer
Klammerung befindet.

Beispiele gültiger Expressions:

```ilimap
Beschreibung = replace(p.Text, ";", ",");
Beschreibung = "Text mit ; Semikolon";
Wert = coalesce(
  p.Wert1,
  p.Wert2,
  "Fallback; mit Semikolon"
);
```

Der Parser darf eine Expression nicht beim Semikolon innerhalb eines Strings
beenden.

### Minimale Anforderungen an den Expression-Scanner

Der Expression-Scanner muss mindestens erkennen:

- String-Literale mit Escapes.
- Runde Klammern `(` und `)`.
- Zeilenkommentare `// ...`.
- Blockkommentare `/* ... */`.
- Semikolons nur auf Top-Level der Expression.

Wenn Klammern nicht geschlossen sind, erzeugt der Parser eine Syntaxdiagnostic an
der nächstmöglichen stabilen Stelle.

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

Unbekannte Top-Level-Elemente sind Fehler.

## `job`

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

`name` und `description` sind optional. `direction`, `failPolicy` und `compileMode`
sind optional und verwenden dieselbe Semantik wie YAML v1.

Erlaubte `failPolicy`-Werte:

- `strict`
- `lenient`
- `reportOnly`

Erlaubte `compileMode`-Werte:

- `strict`
- `compatible`
- `report`

## `input` und `output`

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

## `oid`

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

`external` ist syntaktisch reserviert, aber semantisch erst gültig, wenn die Engine
die Strategie implementiert. Bis dahin muss die Verwendung eine klare Diagnostic
erzeugen.

## `basket`

```ilimap
basket byTopic;
```

Erlaubte Strategien:

- `preserve`
- `generateUuid`
- `preserveOrGenerateUuid`
- `byTopic`
- `expression`

`expression` ist syntaktisch reserviert, aber semantisch erst gültig, wenn die
Engine die Strategie implementiert. Bis dahin muss die Verwendung eine klare
Diagnostic erzeugen.

## `enum`

```ilimap
enum Bodenbedeckungsart_DM01_DMAV {
  "Gebaeude" => "Gebaeude";
  "befestigt.Strasse_Weg" => "befestigt.Strasse_Weg";
  "Gewaesser.stehendes" => "Gewaesser.stehendes_Gewaesser";
}
```

Ein Enum-Block ist eine benannte Mappingtabelle. Die linke Seite ist der
kanonisierte Quellwert, die rechte Seite der Zielwert. Beide Seiten verwenden
Literals.

Enum-Mappings verwenden `=>` statt `=`, weil sie keine Attributzuweisung sind. Das
hilft beim Lesen: `=` bedeutet "Zielattribut bekommt Expression", `=>` bedeutet
"Quellwert wird auf Zielwert abgebildet".

### Enum-Map-Referenzen in Expressions

Enum-Maps sollen als echte Symbole referenzierbar sein:

```ilimap
IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
```

Der `.ilimap`-Loader darf diese Form intern in die bestehende Runtime-Form
normalisieren, falls die Runtime bisher einen String erwartet:

```text
enumMap(p.LageZuv, "Zuverlaessigkeit_DM01_DMAV")
```

Wichtig ist: Für die DSL ist `Zuverlaessigkeit_DM01_DMAV` ein Symbol. Dadurch sind
Diagnostics, Go-to-definition und Rename sauber möglich.

Kompatibilitätsregel:

- v2.0 soll die symbolische Form bevorzugen.
- Die String-Form darf optional für Migration akzeptiert werden.
- Wenn die String-Form akzeptiert wird, muss sie ebenfalls gegen bekannte
  `enum`-IDs validiert werden, sofern sie an einer bekannten `enumMap`-Position
  steht.

## `defaults`

```ilimap
defaults {
  GueltigerEintrag = now();
  AktiverUnterhalt = true;
}
```

Globale Defaults gelten für Zielattribute, sofern eine Rule keinen spezifischeren
Default oder eine explizite Zuweisung definiert.

### Null-Semantik

Ein explizites `assign` gewinnt immer, auch wenn die Expression `null` ergibt.
Defaults greifen nur, wenn für ein Zielattribut kein explizites Assignment
vorhanden ist.

Beispiel:

```ilimap
defaults {
  Beschreibung = "";
}

rule beispiel {
  target dmav class "M.T.C";
  source p from dm01 class "S.T.C";

  assign {
    Beschreibung = p.Beschreibung;
  }
}
```

Wenn `p.Beschreibung` `null` ergibt, bleibt das Ergebnis `null`. Der globale Default
wird nicht angewendet. Wer einen Null-Fallback will, muss ihn explizit formulieren:

```ilimap
Beschreibung = coalesce(p.Beschreibung, "");
```

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
    Bodenbedeckungsart = enumMap(bb.Art, Bodenbedeckungsart_DM01_DMAV);
  }
}
```

Erlaubte Rule-Elemente:

| Element | Anzahl | Zweck |
|---|---:|---|
| `target` | 1 | Zieloutput und Zielklasse |
| `source` | 1..n | Quellalias, Input und Quellklasse |
| `where` | 0..1 | Rule-Filter |
| `join` | 0..n | Source-Joins zwischen bereits deklarierten Sources |
| `identity` | 0..1 | Schlüsselfelder für deterministische OID |
| `assign` | 0..1 | Zielattribut-Zuweisungen |
| `defaults` | 0..1 | Rule-spezifische Defaults |
| `bag` | 0..n | BAG OF STRUCTURE Mapping |
| `ref` | 0..n | Referenzen / Associations |
| `create` | 0..n | zusätzliche Zielobjekte |
| `loss` | 0..n | dokumentierter Informationsverlust |
| `metadata` | 0..1 | fachliche Metadaten |

## `target`

```ilimap
target dmav class "DMAV_Model.Topic.Class";
```

Das erste Argument ist die `output`-ID. Die Klasse wird als String geschrieben.

## `source`

```ilimap
source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
source nf from dm01 class "M.T.Nachfuehrung" where nf.Gueltigkeit != #projektiert;
```

Das erste Argument ist ein Alias. Nach `from` steht in v2.0 genau eine Input-ID.
Der optionale Source-Filter steht direkt auf der Source.

Mehrere Inputs in einem `source`-Statement werden in v2.0 nicht empfohlen. Falls
die heutige YAML-Semantik mehrere Inputs pro Source kennt, soll diese Funktion erst
nach einer separaten Semantikentscheidung in `.ilimap` aufgenommen werden.

### Entscheidung für v2.0

- Ein `source` deklariert genau einen Alias.
- Der Alias muss eine Alias-ID sein und darf keinen Bindestrich enthalten.
- Der Alias ist im Rule-Scope sichtbar.
- Source-Aliases innerhalb einer Rule müssen eindeutig sein.

## `where`

```ilimap
where p.LFPArt == #LFP3;
```

Rule- und Source-Filter verwenden die bestehende Expression-Sprache.

## `join`

Joins verbinden bereits deklarierte Sources. `join` führt in v2.0 keine neue Source
ein.

```ilimap
source os from dm01 class "M.T.Ortschaft";
source plz from dm01 class "M.T.PLZ";

join inner os to plz on eq(os.PLZ, plz.PLZ);
```

Erlaubte Join-Arten:

- `inner`
- `left`

Semantik:

- Beide Aliases nach `join` und `to` müssen vorher in derselben Rule als `source`
  deklariert worden sein.
- `join` erzeugt keinen neuen Alias.
- Die Join-Bedingung ist eine Expression.
- Ein Ref-to-Object-Join bleibt über Funktionen wie `eq(leftRefPath, rightAlias)`
  oder `refEquals(leftRefPath, rightAlias)` ausdrückbar.

Beispiel:

```ilimap
source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
source pos from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos";

join left pos to p on refEquals(pos.LFP3Pos_von, p);
```

Nicht erlaubt in v2.0:

```ilimap
join left pos from dm01 class "..." on refEquals(pos.Parent, p);
```

Diese Form würde `join` gleichzeitig zu einer Source-Deklaration machen und die
Semantik unnötig erweitern.

## `identity`

```ilimap
identity p.NBIdent, p.Nummer;
```

Die Werte sind Expressions. Sie bilden den stabilen Quellschlüssel für
deterministische OIDs.

Wenn die globale OID-Strategie `deterministicUuid` verwendet wird und eine Rule
keine `identity` besitzt, muss der Compiler entweder eine definierte
Fallback-Semantik anwenden oder eine Diagnostic erzeugen. Für v2.0 wird empfohlen:

> Bei `deterministicUuid` muss jede objektbildende Rule eine `identity` definieren,
> sofern die heutige YAML-Semantik keinen explizit dokumentierten Fallback kennt.

## `assign`

```ilimap
assign {
  Nummer = p.Nummer;
  LFPArt = #LFP3;
  Lagegenauigkeit = div(p.LageGen, 100.0);
  IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
}
```

Links steht immer ein Zielattribut. Rechts steht eine Expression.

Assignments sind nicht implizit null-sicher. Null-Fallbacks müssen mit der
Expression-Sprache explizit formuliert werden.

## `defaults` in Rules

```ilimap
defaults {
  Beschreibung = "";
}
```

Rule-Defaults überschreiben globale Defaults und werden nur verwendet, wenn das
Zielattribut nicht explizit in `assign` gesetzt wird.

Priorität:

```text
1. explizites assign
2. Rule-defaults
3. globale defaults
4. kein Wert / Runtime-Default gemäss bestehender Semantik
```

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
| `maxItems` | 0..1 | optionale Kardinalitätsbegrenzung |
| `parentRef` | 0..1 | Parent-Child-Beziehung |
| `assign` | 0..1 | Strukturattribut-Zuweisungen |
| `bag` | 0..n | Nested Bags |

### Semantik von `maxItems`

`maxItems` ist eine Kardinalitätsvalidierung, keine Auswahlregel.

Wenn mehr passende Quellobjekte gefunden werden als `maxItems` erlaubt, darf die
Engine nicht stillschweigend das erste Objekt auswählen.

Empfohlene Semantik:

- `failPolicy strict`: Fehler, Transformation schlägt fehl.
- `failPolicy lenient`: Diagnostic/Warnung, Verhalten gemäss bestehender Runtime-
  Semantik, aber nicht still ignorieren.
- `failPolicy reportOnly`: Diagnostic im Report, keine implizite Semantikänderung.

Wenn eine fachliche Auswahl benötigt wird, muss dafür später eine eigene Syntax wie
`orderBy`/`limit` oder eine explizite Expression eingeführt werden. Das ist kein
Bestandteil von v2.0.

## Referenzen

v2.0 unterstützt nur die Langform.

```ilimap
ref Entstehung {
  association "Entstehung_LFP3";
  role "Entstehung";
  required;
  target rule lfp3-nachfuehrung sourceRef p.Entstehung;
}
```

`required` ist ein Marker. Wenn er fehlt, ist die Referenz optional.

Semantik:

- `association` bezeichnet die Zielassociation oder einen bestehenden
  Association-Namen gemäss heutiger Semantik.
- `role` bezeichnet die Zielrolle.
- `target rule` referenziert eine bekannte Rule-ID.
- `sourceRef` ist eine Expression, die den Quellbezug beschreibt.
- Wenn `required` gesetzt ist und keine Zielinstanz aufgelöst werden kann, ist das
  je nach `failPolicy` mindestens eine Diagnostic, bei `strict` ein Fehler.

Nicht Bestandteil von v2.0:

```ilimap
ref Entstehung -> lfp3-nachfuehrung using p.Entstehung {
  association "Entstehung_LFP3";
  role "Entstehung";
  required;
}
```

Diese Kurzform kann später ergänzt werden, wenn die Langform stabil implementiert
ist.

## Create

```ilimap
create class "TargetModel.Topic.AdditionalClass" {
  assign {
    ExtraAttr = s.SomeField;
  }
}
```

`create` erzeugt wie in YAML v1 zusätzliche Zielobjekte im Kontext einer Rule.
Nicht unterstützte Create-Unterelemente müssen als Compiler-Diagnostic abgelehnt
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

Diese Blocks bleiben dokumentierend und auswertbar. Sie dürfen keine implizite
Runtime-Logik einführen.

## Statische Semantik

Ein `.ilimap`-Parser erzeugt zuerst ein Syntaxmodell. Danach folgen dieselben
Validierungsstufen wie heute.

Validierungsstufen:

1. Syntaxdiagnostics für ungültige Tokens, fehlende Semikolons, nicht geschlossene
   Blocks und ungültige Statementformen.
2. Strukturdiagnostics für fehlende Pflichtteile, doppelte IDs, unbekannte
   Top-Level-Elemente und ungültige Strategien.
3. Symboldiagnostics für unbekannte Inputs, Outputs, Rules, Aliases und Enum-Maps.
4. Modelldiagnostics gegen `ili2c`/`ModelRegistry`, sofern Modelldirs verfügbar
   sind.
5. Expressiondiagnostics durch den bestehenden Expression-Compiler.
6. Typ- und Runtime-Risiko-Diagnostics durch `MappingCompiler`.

### Diagnostic-Severity

Diagnostics sollen mindestens folgende Severity-Stufen unterstützen:

```text
ERROR   blockiert Kompilierung oder Ausführung
WARNING erlaubt Kompilierung, weist aber auf Risiko oder mögliche Datenprobleme hin
INFO    dokumentierend
```

Beispiele:

| Situation | Severity |
|---|---|
| Syntaxfehler | ERROR |
| unbekannter `input` in `source` | ERROR |
| unbekannte Rule in `ref target rule` | ERROR |
| unbekannte Enum-Map in `enumMap` | ERROR |
| `external` OID-Strategie noch nicht implementiert | ERROR oder WARNING gemäss CompileMode |
| `maxItems` potenziell verletzt | WARNING oder ERROR abhängig von Prüfbarkeit und `failPolicy` |
| dokumentierter Informationsverlust | INFO |

## Scopes

Scopes:

- Top-Level-Scope: Inputs, Outputs, Rules, Enum-Maps.
- Rule-Scope: Source-Aliases, Join-Beziehungen, Bag-Namen, Ref-Namen.
- Bag-Scope: Bag-Alias plus Parent-Aliases aus äusserem Scope.
- Nested-Bag-Scope: eigener Alias plus äussere Bag-/Rule-Aliases.

Eindeutigkeit:

- `input`-IDs sind eindeutig.
- `output`-IDs sind eindeutig.
- `rule`-IDs sind eindeutig.
- `enum`-IDs sind eindeutig.
- Source-Aliases innerhalb einer Rule sind eindeutig.
- Bag-Namen innerhalb desselben Blocks sind eindeutig.
- Ref-Namen innerhalb derselben Rule sind eindeutig.

Alias-Sichtbarkeit:

- Ein Rule-Source-Alias ist innerhalb der gesamten Rule sichtbar.
- Ein Bag-Alias ist innerhalb des jeweiligen Bag-Blocks sichtbar.
- Ein Nested-Bag sieht seine eigenen Aliases und die Aliases der äusseren Rule-/Bag-
  Scopes.
- Ein innerer Alias darf keinen äusseren Alias überschatten, sofern dadurch
  Expressions mehrdeutig würden. Für v2.0 wird empfohlen: Shadowing verbieten.

## Grammatikskizze

Diese Grammatik ist absichtlich kompakt. Sie definiert die äussere DSL. Expression
wird als Expression-Text gelesen und danach vom bestehenden Expression-Parser
verarbeitet.

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
                  | "direction" symbolId
                  | "failPolicy" symbolId
                  | "compileMode" symbolId
                  | "modeldir" string) ";" ;

inputDecl         = "input" symbolId ioBlock ;
outputDecl        = "output" symbolId ioBlock ;
ioBlock           = "{" ioStmt* "}" ;
ioStmt            = ("path" string | "model" string | "format" symbolId) ";" ;

oidDecl           = "oid" symbolId (";" | "{" oidStmt* "}") ;
oidStmt           = "namespace" string ";" ;
basketDecl        = "basket" symbolId ";" ;

enumDecl          = "enum" symbolId "{" enumEntry* "}" ;
enumEntry         = literal "=>" literal ";" ;

defaultsDecl      = "defaults" assignmentBlock ;

ruleDecl          = "rule" symbolId "{" ruleElement* "}" ;
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

targetStmt        = "target" symbolId "class" string ";" ;
sourceStmt        = "source" aliasId "from" symbolId "class" string whereTail? ";" ;
whereStmt         = "where" expression ";" ;
whereTail         = "where" expression ;
joinStmt          = "join" ("inner" | "left") aliasId "to" aliasId "on" expression ";" ;
identityStmt      = "identity" expressionList ";" ;

assignDecl        = "assign" assignmentBlock ;
assignmentBlock   = "{" assignment* "}" ;
assignment        = symbolId "=" expression ";" ;

bagDecl           = "bag" symbolId "{" bagElement* "}" ;
bagElement        = bagFromStmt
                  | structureStmt
                  | modeStmt
                  | maxItemsStmt
                  | parentRefStmt
                  | assignDecl
                  | bagDecl ;
bagFromStmt       = "from" aliasId "in" symbolId "class" string whereTail? ";" ;
structureStmt     = "structure" string ";" ;
modeStmt          = "mode" ("embed" | "expand") ";" ;
maxItemsStmt      = "maxItems" number ";" ;
parentRefStmt     = "parentRef" ("attribute" | "role") string "parent" aliasId ";" ;

refDecl           = "ref" symbolId refBody ;
refBody           = "{" refStmt* "}" ;
refStmt           = ("association" string
                  | "role" string
                  | "target" "rule" symbolId "sourceRef" expression
                  | "required") ";" ;

createDecl        = "create" "class" string "{" assignDecl? "}" ;
lossDecl          = "loss" "{" lossStmt* "}" ;
lossStmt          = ("sourcePath" expression
                  | "reasonCode" string
                  | "description" string
                  | "when" expression) ";" ;

metadataDecl      = "metadata" "{" metadataStmt* "}" ;
metadataStmt      = ("direction" symbolId
                  | "roundtrip" symbolId
                  | "lossiness" symbolId) ";" ;

expressionList    = expression ("," expression)* ;
```

Hinweis: `expression` ist kein normaler Token der äusseren Grammatik. Es wird durch
den Expression-Scanner bis zum Statement-Semikolon gelesen und anschliessend vom
bestehenden Expression-Compiler geprüft.

## Mapping nach `JobConfig`

Die `.ilimap`-DSL bildet ohne neue Runtime-Semantik auf die heutige Modellstruktur
ab:

| `.ilimap` | `JobConfig` |
|---|---|
| `mapping v2` | `IlimapFile.formatVersion = 2` |
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

Wichtig: `mapping v2` setzt nicht automatisch `JobConfig.version = 2`.

## Migration aus YAML v1

YAML v1 kann während einer Übergangsphase weiter geladen werden. Für neue produktive
Profile ist `.ilimap` v2 das bevorzugte Autorenformat.

Ein Converter sollte später bereitgestellt werden:

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
- Enum-Map-Stringreferenzen können in symbolische Enum-Map-Referenzen umgeschrieben
  werden.
- Kommentare müssen nicht zuverlässig automatisch migriert werden.

### Kommentar-Migration

Der Converter muss YAML-Kommentare nicht erhalten. Kommentarerhaltung ist kein
Akzeptanzkriterium für v2.0.

Begründung: YAML-Kommentare lassen sich häufig nicht eindeutig einem fachlichen
Konstrukt zuordnen. Der Converter soll zuerst eine syntaktisch korrekte,
formatierte und semantisch äquivalente `.ilimap`-Datei erzeugen.

## Formatter-Regeln

Der Formatter soll früh eingeplant werden. Ziel sind stabile Diffs und einheitliche
Lesbarkeit.

Empfehlungen:

- Zwei Leerzeichen Einrückung.
- Jedes Statement auf eigener Zeile.
- Keine aggressive Ausrichtung von `=` über mehrere Zeilen hinweg.
- Leerzeile zwischen grösseren Blöcken wie `source`, `identity`, `assign`, `bag`,
  `ref`.
- Lange Expressions dürfen nach Funktionsargumenten umgebrochen werden.

Bevorzugt:

```ilimap
assign {
  NBIdent = p.NBIdent;
  Nummer = p.Nummer;
  Lagegenauigkeit = div(p.LageGen, 100.0);
  IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
}
```

Nicht bevorzugt als Formatter-Default:

```ilimap
assign {
  NBIdent             = p.NBIdent;
  Nummer              = p.Nummer;
  Lagegenauigkeit     = div(p.LageGen, 100.0);
  IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
}
```

Die zweite Form ist visuell schön, erzeugt aber unnötige Diffs, wenn später lange
Attributnamen hinzukommen.

## Akzeptanzkriterien für die Implementierung

Diese Kriterien eignen sich als Grundlage für einen LLM-Coding-Agent oder eine
klassische Implementierungsplanung.

### Parser

- `.ilimap`-Dateien mit `mapping v2` werden erkannt.
- Blöcke, Statements und Semikolons werden korrekt geparst.
- Kommentare werden ignoriert, ohne Positionsinformationen für Diagnostics zu
  zerstören.
- Expressions werden bis zum korrekten Statement-Semikolon gelesen.
- Semikolons innerhalb von Strings oder Klammern beenden keine Expression.
- Syntaxfehler erzeugen verständliche Diagnostics mit Zeile und Spalte.

### AST und Symbolmodell

- Der Parser erzeugt ein Syntaxmodell/AST.
- Top-Level-Symbole werden gesammelt: Inputs, Outputs, Rules, Enums.
- Rule-Symbole werden gesammelt: Sources, Bags, Refs.
- Alias-IDs und Symbol-IDs werden unterschiedlich validiert.
- Bindestriche in Aliases werden abgelehnt.
- Doppelte IDs werden erkannt.
- Shadowing von Aliases wird für v2.0 abgelehnt.

### Semantikvalidierung

- Unbekannte Inputs, Outputs, Rules und Enum-Maps werden als Fehler gemeldet.
- `join` darf nur bereits deklarierte Source-Aliases verwenden.
- `ref target rule` muss auf eine bekannte Rule zeigen.
- `enumMap(..., EnumSymbol)` muss auf eine bekannte Enum-Map zeigen.
- Reservierte, aber nicht implementierte Strategien wie `external` oder
  `basket expression` erzeugen klare Diagnostics.
- Defaults folgen der definierten Priorität: `assign` vor Rule-Default vor globalem
  Default.

### JobConfig-Abbildung

- `.ilimap` wird in dieselbe `JobConfig`-Struktur übersetzt wie YAML.
- `.ilimap` erzeugt keinen zweiten direkten `TransformPlan`-Pfad.
- `mapping v2` wird als Dateiformatversion behandelt, nicht automatisch als
  `JobConfig.version = 2`.
- Symbolische Enum-Map-Referenzen werden bei Bedarf in die bestehende interne
  Runtime-Form normalisiert.

### Migration und Regression

Für vorhandene YAML-Beispiele sollen Golden Tests erstellt werden:

```text
YAML -> JobConfig A
YAML -> ILIMAP -> JobConfig B
normalisiere A und B
vergleiche A und B
```

Zusätzlich:

```text
ILIMAP -> parse -> format -> parse
```

muss stabil sein.

Ungültige `.ilimap`-Beispiele müssen gezielt getestet werden:

- fehlendes Semikolon
- unbekannte Rule in `ref`
- unbekannte Enum-Map
- Alias mit Bindestrich
- `join` auf nicht deklarierte Source
- `maxItems` mit ungültiger Zahl
- nicht geschlossener String
- nicht geschlossener Block
- Semikolon innerhalb eines Strings

## Review-Entscheide für v2.0

| Frage | Entscheidung für v2.0 |
|---|---|
| Ref-Kurzform enthalten? | Nein, nur Langform |
| Modell-/Klassenpfade als Strings? | Ja |
| YAML-Kommentare migrieren? | Nein, nicht als Muss |
| `mapping v2` = `JobConfig.version = 2`? | Nein, Formatversion separat |
| IDE Extension mit fester Grammatik oder Parser? | Parser/AST als Bibliothek wiederverwenden |
| Includes/Makros? | Nein |
| EnumMap-Referenz als String? | Symbolische Form bevorzugen |
| Bindestriche in IDs? | Ja für Symbol-IDs, nein für Alias-IDs |
| `maxItems` als Auswahlregel? | Nein, nur Validierung |
| Defaults bei Null? | Explizites `assign` gewinnt immer |
| Join führt neue Source ein? | Nein, Join verbindet deklarierte Sources |

## Zusammenfassung

`.ilimap` v2 ist ein besseres Autorenformat für grosse Mappingprofile. Es ersetzt
YAML dort, wo YAML schwach ist: äussere Struktur, Lesbarkeit, Tooling und
Diagnostics. Es ersetzt nicht die bestehende Runtime-Semantik.

Die wichtigste Leitlinie lautet:

```text
Neue Syntax, gleicher fachlicher Transformationspfad.
```

Für v2.0 soll die Sprache klein und streng bleiben. Die sieben zentralen
Härtungen sind:

1. Expressions werden robust und eindeutig abgegrenzt.
2. Aliases und Symbol-IDs haben unterschiedliche Regeln.
3. Enum-Maps sind echte Symbole.
4. Joins verbinden nur bereits deklarierte Sources.
5. Defaults, Nullwerte, `maxItems` und Referenzen sind präzise definiert.
6. Formatversion und `JobConfig`-Semantikversion sind getrennt.
7. Die Ref-Langform ist die einzige Referenzsyntax in v2.0.

Damit entsteht eine robuste Grundlage für Parser, Compiler, Formatter,
Language-Server und YAML-Migration.
