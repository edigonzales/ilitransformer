# ilimap DSL v2 — detaillierte Implementierungsanweisungen für einen LLM-Coding-Agenten

**Status:** Umsetzungsplan für einen LLM-Coding-Agenten  
**Ziel:** Die verbesserte `.ilimap`-DSL v2 phasenweise, testgetrieben und ohne Änderung der bestehenden Runtime-Semantik implementieren.  
**Primäres Prinzip:** `.ilimap` ist ein neues Autorenformat. Die bestehende Engine bleibt der Semantikanker.

```text
.ilimap -> IlimapAst -> JobConfig -> MappingCompiler -> TransformPlan
```

Diese Anweisungen sind absichtlich konkret formuliert. Ein Coding-Agent soll daraus einzelne Phasen abarbeiten können. Jede Phase muss ein funktionsfähiges Artefakt erzeugen, getestet sein und einen klaren Abschlussbericht liefern.

---

## 0. Verbindliche Ausgangsregeln für den Agenten

### 0.1 Vor jeder Phase zwingend lesen

Der Agent muss am Anfang **jeder Phase** explizit folgende Dateien lesen:

```text
AGENTS.md
docs/agent/DEFINITION_OF_DONE.md
docs/agent/COMMIT_POLICY.md
docs/agent/DECISIONS.md
.skills/mapping-dsl-change/SKILL.md
.skills/java-test-gap/SKILL.md
.skills/gradle-verification/SKILL.md
.skills/done-and-commit/SKILL.md
```

Falls die Phase INTERLIS-Artefakte, ITF/XTF/XML, DM01/DMAV-Profile, Geometrie, Baskets, OIDs oder Echtdaten betrifft, zusätzlich:

```text
.skills/interlis-validation/SKILL.md
.skills/dm01-dmav-real-data-gate/SKILL.md
```

Falls die Phase INTERLIS-1-Testdaten, ITF-Fixtures oder AREA/SURFACE-Testdaten erzeugt oder ändert, zusätzlich:

```text
.skills/interlis1-testdata/SKILL.md
```

Bei Package-/Architekturgrenzen zusätzlich:

```text
.skills/architecture-boundary-review/SKILL.md
```

**Wichtig für OpenCode / Codex / ähnliche Agenten:** Skills werden nicht immer automatisch geladen. Deshalb muss der konkrete Prompt pro Phase die benötigten Skill-Dateien explizit nennen.

### 0.2 Nicht verhandelbare Regeln

Der Agent darf:

- keine öffentlichen APIs, DSL-Semantik, Transferformatannahmen oder Testdatenverträge still ändern;
- keine nicht implementierten DSL-Felder still ignorieren;
- keine Tests als grün melden, wenn der exakte Befehl nicht wirklich ausgeführt wurde;
- keine INTERLIS-Modellsyntax oder Transferdaten aus dem Gedächtnis erfinden;
- keine DM01/DMAV-Sonderlogik in die generische Engine einbauen;
- nicht committen, bevor Definition of Done und Commit Policy erfüllt sind;
- keine opportunistischen Refactorings durchführen.

Der Agent muss:

- bei DSL-Änderungen Parser-/Loader-Tests, Compiler-Diagnostic-Tests und Doku aktualisieren;
- bei Semantikänderungen zusätzlich Runtime-/Integrationstests ergänzen;
- `.ili` mit `ili2c` validieren, falls `.ili` geändert wird;
- `.itf`, `.xtf`, `.xml` mit `ilivalidator` validieren, falls solche Artefakte geändert oder erzeugt werden;
- am Ende jeder Phase die tatsächlich geänderten Dateien, ausgeführten Befehle, Ergebnisse, nicht geprüften Risiken und Commit-Informationen melden.

---

## 1. Technisches Zielbild

### 1.1 Ziel der Umsetzung

Die bestehende YAML-Mapping-DSL bleibt weiterhin ladbar. `.ilimap` wird als neues, primäres Autorenformat ergänzt. Die Runtime-Semantik darf durch `.ilimap` nicht neu erfunden werden.

**Erlaubt:**

- neue Lexer-/Parser-/AST-Komponenten;
- neue statische Semantikvalidierung für `.ilimap`;
- neue Formatter- und Converter-Komponenten;
- neue Loader-Integration, die `.ilimap` in denselben `JobConfig`-Pfad überführt;
- neue Diagnostics für Syntax, Struktur, Symbole, Expressions und nicht unterstützte Konstrukte.

**Nicht erlaubt ohne separate Entscheidung:**

- neue Expression-Funktionen;
- neue Runtime-Semantik;
- Makros;
- Includes;
- bedingte Kompilierung;
- INTERLIS-Sonderfälle im Parser;
- DM01/DMAV-Sonderfälle in generischen Packages.

### 1.2 Die sieben Härtungspunkte, die zwingend umzusetzen sind

1. **Expression-Abgrenzung:** Expressions enden nur an einem Semikolon, das nicht in String-Literal, Kommentar oder Klammerung liegt.
2. **IDs und Aliases:** Source-/Join-/Bag-Aliases werden von sonstigen Symbol-IDs getrennt. Aliases dürfen nicht `-` enthalten, weil sie in Expressions vorkommen.
3. **Enum-Map-Symbole:** Enum-Maps sollen symbolisch referenzierbar sein, nicht nur als String-Literal.
4. **Join-Semantik:** Joins beziehen sich in v2.0 nur auf bereits deklarierte Sources. Nicht deklarierte Join-Aliases sind Fehler.
5. **Defaults, Null, `maxItems`, Refs:** Semantik muss präzise sein. Explizites `assign` gewinnt immer, auch wenn es `null` ergibt. `maxItems` ist Validierung, keine Auswahlregel. Ref-Langform ist v2.0-Standard.
6. **Formatversion vs. Config-Semantik:** `mapping v2` ist eine `.ilimap`-Formatversion. Sie setzt nicht automatisch `JobConfig.version = 2`.
7. **v2.0-Minimalumfang:** Keine Ref-Kurzform, keine Includes, keine Makros, keine qualifizierten INTERLIS-Namen als eigene Token. Klassen und Modelle bleiben Strings.

---

## 2. Vorgeschlagene Package- und Klassenstruktur

Der Agent muss zuerst prüfen, ob es im Repository bereits passende Packages oder Klassen gibt. Falls ja, vorhandene Strukturen nutzen und nur die Namen sinngemäss anpassen. Nicht blind doppelte Klassen erzeugen.

### 2.1 Neues Hauptpackage

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/
```

Unterpackages:

```text
mapping/ilimap/lexer
mapping/ilimap/parser
mapping/ilimap/ast
mapping/ilimap/semantic
mapping/ilimap/jobconfig
mapping/ilimap/format
mapping/ilimap/convert
```

### 2.2 Klassenübersicht

```text
guru.interlis.transformer.mapping.ilimap.IlimapLoader
guru.interlis.transformer.mapping.ilimap.IlimapLoadResult
guru.interlis.transformer.mapping.ilimap.IlimapFormatVersion

guru.interlis.transformer.mapping.ilimap.lexer.IlimapLexer
guru.interlis.transformer.mapping.ilimap.lexer.IlimapToken
guru.interlis.transformer.mapping.ilimap.lexer.IlimapTokenType
guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition
guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange

guru.interlis.transformer.mapping.ilimap.parser.IlimapParser
guru.interlis.transformer.mapping.ilimap.parser.IlimapParseException
guru.interlis.transformer.mapping.ilimap.parser.IlimapExpressionReader
guru.interlis.transformer.mapping.ilimap.parser.IlimapLiteralParser

guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument
guru.interlis.transformer.mapping.ilimap.ast.IlimapJobBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapInputBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapOutputBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapOidBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapBasketStmt
guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumEntry
guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt
guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt
guru.interlis.transformer.mapping.ilimap.ast.IlimapWhereStmt
guru.interlis.transformer.mapping.ilimap.ast.IlimapJoinStmt
guru.interlis.transformer.mapping.ilimap.ast.IlimapIdentityStmt
guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment
guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapParentRefStmt
guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapCreateBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapMetadataBlock
guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText
guru.interlis.transformer.mapping.ilimap.ast.IlimapLiteral

guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidator
guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolTable
guru.interlis.transformer.mapping.ilimap.semantic.IlimapScope
guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbol
guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind
guru.interlis.transformer.mapping.ilimap.semantic.IlimapIdentifierRules
guru.interlis.transformer.mapping.ilimap.semantic.IlimapReservedWords
guru.interlis.transformer.mapping.ilimap.semantic.IlimapExpressionNormalizer

guru.interlis.transformer.mapping.ilimap.jobconfig.IlimapToJobConfigMapper

guru.interlis.transformer.mapping.ilimap.format.IlimapFormatter
guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions
guru.interlis.transformer.mapping.ilimap.format.IlimapPrinter

guru.interlis.transformer.mapping.ilimap.convert.YamlToIlimapConverter
```

### 2.3 Bestehende Klassen, die voraussichtlich anzupassen sind

Vor Änderung prüfen, ob Namen und Packages tatsächlich so existieren:

```text
src/main/java/guru/interlis/transformer/mapping/model/MappingLoader.java
src/main/java/guru/interlis/transformer/mapping/model/JobConfig.java
src/main/java/guru/interlis/transformer/mapping/compiler/MappingCompiler.java
src/main/java/guru/interlis/transformer/mapping/plan/TransformPlan.java
src/main/java/guru/interlis/transformer/app/CliMain.java
src/main/java/guru/interlis/transformer/feature/FeatureMatrix.java
```

Optional neue allgemeine Hilfsklassen:

```text
guru.interlis.transformer.mapping.model.MappingFormat
guru.interlis.transformer.mapping.model.MappingFormatDetector
guru.interlis.transformer.mapping.model.MappingLoadOptions
```

---

## 3. Zentrale Semantikentscheidungen für die Implementierung

### 3.1 Formatversion

`mapping v2` bedeutet:

```text
IlimapFormatVersion.V2
```

Es bedeutet **nicht automatisch**:

```text
JobConfig.version = 2
```

Der Mapper `IlimapToJobConfigMapper` soll die interne Config-Semantik so setzen, wie die bestehende YAML-Runtime sie erwartet. Falls `JobConfig.version = 1` weiterhin der gültige interne Pfad ist, bleibt es dabei.

### 3.2 Symbol-IDs vs. Alias-IDs

```text
symbolId:
  Verwendung: input, output, rule, enum, bag-name, ref-name
  Regex:      [A-Za-z][A-Za-z0-9_-]*
  Darf:       Bindestrich enthalten

aliasId:
  Verwendung: source-alias, join-alias, bag-source-alias
  Regex:      [A-Za-z][A-Za-z0-9_]*
  Darf nicht: Bindestrich enthalten
```

Begründung: Aliases werden in Expressions verwendet. Ein Alias wie `alte-pos` wäre in `alte-pos.Ori` nicht eindeutig von Subtraktion unterscheidbar.

### 3.3 Expressions

Expressions werden als Text erfasst und später vom bestehenden Expression-Compiler verarbeitet.

Eine Expression endet beim ersten Semikolon, das:

- nicht in einem String-Literal liegt;
- nicht in einem Zeilenkommentar liegt;
- nicht in einem Blockkommentar liegt;
- nicht innerhalb runder Klammern `(...)` liegt;
- nicht innerhalb eckiger Klammern `[...]` liegt, falls die Expression-Sprache diese verwendet;
- nicht innerhalb geschweifter Klammern `{...}` liegt, falls die Expression-Sprache diese später verwendet.

Der Expression-Text wird ohne abschliessendes Semikolon gespeichert.

Beispiel muss funktionieren:

```ilimap
Beschreibung = replace(p.Text, ";", ",");
```

### 3.4 Enum-Map-Symbole

Die `.ilimap`-Syntax soll erlauben:

```ilimap
IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
```

Falls die bestehende Expression-Engine intern weiterhin einen String erwartet, muss `IlimapExpressionNormalizer` vor dem Schreiben in `JobConfig` normalisieren:

```text
enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV)
->
enumMap(p.LageZuv, "Zuverlaessigkeit_DM01_DMAV")
```

Die Normalisierung darf nur für `enumMap`-Aufrufe gelten und nur, wenn das zweite Argument ein bekanntes Enum-Map-Symbol ist.

### 3.5 Joins

Für v2.0 gilt:

- `join` erzeugt keine neue Source.
- Alle Join-Aliases müssen vorher durch `source` deklariert sein.
- `join inner os to plz on ...` bedeutet: verbinde bereits deklarierte Sources `os` und `plz`.
- Falls die Runtime aktuell nur einen Join pro Rule unterstützt, muss der Compiler mehrere Joins als nicht unterstütztes Feature ablehnen.
- Es darf keine DSL geben, die mehrere Joins akzeptiert, wenn die Runtime nur den ersten Join ausführt.

### 3.6 Defaults und Nullwerte

Explizites `assign` gewinnt immer.

```ilimap
assign {
  Beschreibung = p.Beschreibung;
}

defaults {
  Beschreibung = "";
}
```

Falls `p.Beschreibung` zu `null` evaluiert, bleibt das Ergebnis `null`. Der Default greift nicht. Wer Fallback will, muss ihn explizit formulieren:

```ilimap
Beschreibung = coalesce(p.Beschreibung, "");
```

### 3.7 `maxItems`

`maxItems` ist eine Kardinalitätsvalidierung, keine Auswahlregel.

Bei Überschreitung darf die Engine nicht still das erste Objekt nehmen. Verhalten je nach `failPolicy`:

```text
strict     -> ERROR / Abbruch
lenient    -> WARNING / Weiterlauf, falls bestehende Runtime das unterstützt
reportOnly -> Diagnostic / Report
```

Falls die bestehende Runtime noch keine saubere maxItems-Diagnostic kennt, muss der Agent entweder eine Diagnostic ergänzen oder `maxItems` für `.ilimap` vorerst als unsupported ablehnen. Nicht still ignorieren.

### 3.8 Referenzen

v2.0 unterstützt nur die Langform:

```ilimap
ref Entstehung {
  association "Entstehung_LFP3";
  role "Entstehung";
  required;
  target rule lfp3-nachfuehrung sourceRef p.Entstehung;
}
```

Die Kurzform mit `->` und `using` ist in v2.0 **nicht** zu implementieren. Falls sie im Input vorkommt, muss der Parser oder Semantic Validator eine klare Diagnostic liefern:

```text
Ref short form is reserved for a later version and is not supported in ilimap v2.0.
```


---

### 3.9 Verbindliche Grammatik- und Lexikquelle für Parser, Formatter und LSP

Diese Untersektion ist bewusst redundant zum späteren Dokument `docs/ilimap-v2.md`.
Sie ist für den Coding-Agenten die **verbindliche Grammatikquelle** für die Phasen P1
bis P5. Der Agent darf den Parser, Formatter oder LSP nicht nur aus den
Phasenbeschreibungen ableiten. Die hier definierte Lexik und EBNF müssen in Tests,
AST-Klassen, Parser-Methoden und Formatter-Regeln abgebildet werden.

#### 3.9.1 Kommentare

```ilimap
// Zeilenkommentar
/* Blockkommentar */
```

`#` ist kein Kommentarzeichen, weil `#EnumValue` ein Expression-Literal ist.

Pflicht für den Agenten:

- `//`-Kommentare laufen bis Zeilenende.
- `/* ... */`-Kommentare dürfen über mehrere Zeilen gehen.
- Kommentare müssen Positionen korrekt weiterzählen.
- Kommentare in Expressions dürfen das Statement-Ende nicht verfälschen.
- Nicht geschlossene Blockkommentare erzeugen eine Syntaxdiagnostic.

#### 3.9.2 Strings

Strings verwenden doppelte Anführungszeichen.

```ilimap
"input/dm01.itf"
"DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3"
"Text mit ; Semikolon"
```

Mindestens zu unterstützende Escapes:

```text
\"  doppeltes Anführungszeichen
\\  Backslash
\n  Zeilenumbruch
\t  Tabulator
```

Pflicht für den Agenten:

- Semikolons innerhalb von Strings beenden kein Statement.
- Nicht geschlossene Strings erzeugen eine Syntaxdiagnostic mit Zeile/Spalte.
- Der Lexer soll String-Rohtext und dekodierten Wert trennen können, falls der
  Formatter Originaltext erhalten soll.

#### 3.9.3 Symbol-ID und Alias-ID

Die DSL unterscheidet zwei lokale ID-Arten.

**Symbol-ID** bezeichnet langlebige DSL-Symbole:

```text
input-IDs
output-IDs
rule-IDs
enum-IDs
bag-Namen
ref-Namen
```

Regex:

```text
[A-Za-z][A-Za-z0-9_-]*
```

Beispiele:

```text
dm01
dmav
lfp3-nachfuehrung
Bodenbedeckungsart_DM01_DMAV
Textposition
```

**Alias-ID** bezeichnet Namen, die in Expressions vorkommen:

```text
source-alias
join-alias
bag-source-alias
```

Regex:

```text
[A-Za-z][A-Za-z0-9_]*
```

Alias-IDs dürfen **keinen Bindestrich** enthalten.

Beispiele gültiger Alias-IDs:

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

Begründung: `alte-pos.Ori` wäre für die Expression-Sprache mehrdeutig, weil der
Bindestrich auch als Minus-Operator gelesen werden kann.

Pflicht für den Agenten:

- Der Lexer darf alle ID-Texte zunächst als generische `IDENTIFIER`-Tokens lesen.
- Der Parser oder Semantic Validator muss je Kontext `symbolId` oder `aliasId`
  erzwingen.
- Alias-Verstösse sind Fehler, keine Warnungen.

#### 3.9.4 Literale

```ilimap
true
false
null
42
3.14
#LFP3
"Text"
```

`#LFP3` ist ein Hash-/Enum-Literal innerhalb der Expression-Sprache. Der äussere
Lexer darf `#` nicht als Kommentarstart behandeln.

#### 3.9.5 INTERLIS-Modell- und Klassenpfade

INTERLIS-Modell- und Klassenpfade werden in v2.0 immer als Strings geschrieben.

```ilimap
target dmav class "DMAV_Model.Topic.Class";
source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
```

Nicht in v2.0 erlaubt:

```ilimap
target dmav class DMAV_Model.Topic.Class;
```

Der Agent darf qualifizierte INTERLIS-Namen als eigene Token nicht implementieren,
ausser eine spätere explizite Entscheidung verlangt das.

#### 3.9.6 Reservierte Keywords

Diese Wörter sind reserviert und dürfen nicht als unquoted Symbol-ID oder Alias-ID
verwendet werden:

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
kollidiert, muss der Anwender einen anderen lokalen Namen wählen.

#### 3.9.7 Expression-Abgrenzung

Expressions werden nicht als Strings geschrieben. Der `.ilimap`-Parser liest die
rechte Seite eines Expression-Statements als Expression-Text und übergibt diesen
Text danach dem bestehenden Expression-Compiler.

Eine Expression endet beim ersten Semikolon, das sich nicht innerhalb eines
String-Literals, nicht innerhalb eines Kommentars und nicht innerhalb einer
Klammerung befindet.

Gültige Beispiele:

```ilimap
Beschreibung = replace(p.Text, ";", ",");
Beschreibung = "Text mit ; Semikolon";
Wert = coalesce(
  p.Wert1,
  p.Wert2,
  "Fallback; mit Semikolon"
);
```

Der Parser darf die Expression nicht beim Semikolon innerhalb eines Strings
beenden.

Der Expression-Scanner muss mindestens erkennen:

- String-Literale mit Escapes;
- runde Klammern `(` und `)`;
- Zeilenkommentare `// ...`;
- Blockkommentare `/* ... */`;
- Semikolons nur auf Top-Level der Expression.

Falls die bestehende Expression-Sprache eckige oder geschweifte Klammern verwendet,
muss der Scanner diese ebenfalls balancieren. Wenn Klammern nicht geschlossen sind,
erzeugt der Parser eine Syntaxdiagnostic an der nächstmöglichen stabilen Stelle.

#### 3.9.8 Verbindliche EBNF für v2.0

Diese Grammatik definiert die äussere DSL. `expression` ist kein normaler Token der
äusseren Grammatik. `expression` wird als Expression-Text gelesen und danach vom
bestehenden Expression-Compiler geprüft.

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

#### 3.9.9 Parser-Methoden, die aus der EBNF abzuleiten sind

Der Agent soll im rekursiven Parser mindestens folgende Methoden anlegen oder
sinngemäss vorhandene Methoden so strukturieren:

```java
IlimapDocument parseDocument();
IlimapJobBlock parseJobBlock();
IlimapInputBlock parseInputBlock();
IlimapOutputBlock parseOutputBlock();
IlimapOidBlock parseOidBlock();
IlimapBasketStmt parseBasketStmt();
IlimapEnumBlock parseEnumBlock();
IlimapDefaultsBlock parseDefaultsBlock();
IlimapRuleBlock parseRuleBlock();
IlimapTargetStmt parseTargetStmt();
IlimapSourceStmt parseSourceStmt();
IlimapWhereStmt parseWhereStmt();
IlimapJoinStmt parseJoinStmt();
IlimapIdentityStmt parseIdentityStmt();
IlimapAssignmentBlock parseAssignmentBlock();
IlimapBagBlock parseBagBlock();
IlimapParentRefStmt parseParentRefStmt();
IlimapRefBlock parseRefBlock();
IlimapCreateBlock parseCreateBlock();
IlimapLossBlock parseLossBlock();
IlimapMetadataBlock parseMetadataBlock();
IlimapExpressionText readExpressionUntilStatementSemicolon();
```

Nicht jede Methode muss `public` sein. Entscheidend ist, dass Tests und
Fehlermeldungen die EBNF-Struktur widerspiegeln.

#### 3.9.10 Nicht unterstützte, aber reservierte Syntax

Die Ref-Kurzform ist in v2.0 nicht Bestandteil der Grammatik:

```ilimap
ref Entstehung -> lfp3-nachfuehrung using p.Entstehung {
  association "Entstehung_LFP3";
  role "Entstehung";
  required;
}
```

Der Lexer darf `->` als Token erkennen, damit die Fehlermeldung gut wird. Der Parser
oder Semantic Validator muss die Kurzform aber als nicht unterstütztes Feature
ablehnen.

`oid external` und `basket expression` sind syntaktisch reserviert. Falls die Engine
sie noch nicht unterstützt, muss die Semantic- oder Compiler-Phase eine klare
Diagnostic erzeugen. Sie dürfen nicht still ignoriert und nicht halb implementiert
werden.

#### 3.9.11 Tests, die direkt aus der Grammatik folgen

Der Agent muss die Grammatik durch Tests absichern. Mindestumfang:

```text
IlimapLexerTest
- lexesLineAndBlockComments
- doesNotTreatHashLiteralAsComment
- lexesStringsWithEscapes
- reportsUnclosedString
- reportsUnclosedBlockComment
- tracksLineAndColumn

IlimapExpressionReaderTest
- stopsAtTopLevelSemicolon
- ignoresSemicolonInsideString
- ignoresSemicolonInsideFunctionCall
- ignoresSemicolonInsideLineComment
- ignoresSemicolonInsideBlockComment
- reportsUnbalancedParentheses

IlimapParserMinimalTest
- parsesMappingVersionAndName
- parsesJobInputOutputOidBasketEnumRule
- rejectsUnknownTopLevelElement
- rejectsMissingSemicolon
- rejectsUnclosedBlock

IlimapParserFullGrammarTest
- parsesRuleWithSourceWhereJoinIdentityAssign
- parsesNestedBags
- parsesRefLongForm
- rejectsRefShortFormForV20
- parsesCreateLossMetadata

IlimapIdentifierRulesTest
- acceptsHyphenInRuleSymbolId
- rejectsHyphenInSourceAlias
- rejectsReservedKeywordAsSymbolId
- rejectsReservedKeywordAsAliasId
```

Diese Tests sind nicht optional. Wenn der Agent einzelne Tests umbenennt, muss die
inhaltliche Abdeckung gleichwertig bleiben.

---

## 4. Phasenplan

Jede Phase ist so formuliert, dass ein funktionsfähiges Artefakt entsteht. Der Agent soll pro Phase einen eigenen Branch oder mindestens einen eigenen Commit verwenden.

---

# Phase P0 — Repository-Analyse und Umsetzungsrahmen fixieren

## Ziel

Der Agent soll vor Codeänderungen die betroffene Codefläche verstehen und eine kleine technische Entscheidungsvorlage im Repository ablegen. Noch keine produktive Parser-Implementierung.

## Zu lesende Dateien

```text
AGENTS.md
docs/agent/DEFINITION_OF_DONE.md
docs/agent/COMMIT_POLICY.md
docs/agent/DECISIONS.md
.skills/mapping-dsl-change/SKILL.md
.skills/java-test-gap/SKILL.md
.skills/gradle-verification/SKILL.md
.skills/done-and-commit/SKILL.md
docs/mapping-dsl.md
src/main/java/guru/interlis/transformer/mapping/model/MappingLoader.java
src/main/java/guru/interlis/transformer/mapping/model/JobConfig.java
src/main/java/guru/interlis/transformer/mapping/compiler/MappingCompiler.java
src/main/java/guru/interlis/transformer/app/CliMain.java
```

## Aufgaben

1. Prüfe vorhandene Mapping-Loader-Struktur.
2. Prüfe, ob `JobConfig` mutable Jackson-DTO, Domain-Objekt oder beides ist.
3. Prüfe, wo Diagnostics erzeugt und gesammelt werden.
4. Prüfe bestehende Compiler- und Loader-Tests.
5. Lege ein kurzes ADR an:

```text
docs/decisions/adr-ilimap-v2-loader-architecture.md
```

Inhalt:

- `.ilimap -> IlimapAst -> JobConfig -> MappingCompiler -> TransformPlan`;
- `.ilimap`-Formatversion getrennt von `JobConfig.version`;
- keine neue Runtime-Semantik;
- Parser/Formatter teilen dieselbe AST-Bibliothek;
- nicht unterstützte Felder erzeugen Diagnostics.

## Tests / Verifikation

In dieser Phase werden keine produktiven Klassen geändert. Trotzdem muss der Agent die aktuelle Testbasis prüfen:

```bash
./gradlew tasks --group verification
./gradlew test
```

Falls `integrationTest` existiert:

```bash
./gradlew integrationTest
```

## Funktionsfähiges Artefakt

```text
docs/decisions/adr-ilimap-v2-loader-architecture.md
```

## Definition of Done

- ADR existiert.
- Aktuelle Testlage ist dokumentiert.
- Der Agent hat keine produktive Semantik geändert.
- Abschlussbericht enthält ausgeführte Befehle und Ergebnisse.

## Copy/Paste-Prompt für den Agenten

```text
Lies zuerst AGENTS.md, docs/agent/DEFINITION_OF_DONE.md, docs/agent/COMMIT_POLICY.md und docs/agent/DECISIONS.md.

Verwende zusätzlich:
- .skills/mapping-dsl-change/SKILL.md
- .skills/java-test-gap/SKILL.md
- .skills/gradle-verification/SKILL.md
- .skills/done-and-commit/SKILL.md

Aufgabe Phase P0:
Analysiere die bestehende Mapping-Loader-, JobConfig- und MappingCompiler-Struktur. Implementiere noch keinen Parser. Lege docs/decisions/adr-ilimap-v2-loader-architecture.md an. Das ADR muss festhalten, dass .ilimap über IlimapAst nach JobConfig und erst danach über den bestehenden MappingCompiler nach TransformPlan geht. Formatversion und JobConfig-Semantikversion bleiben getrennt. Nicht unterstützte DSL-Felder dürfen nicht still ignoriert werden.

Führe ./gradlew tasks --group verification und ./gradlew test aus. Falls integrationTest vorhanden ist, führe auch ./gradlew integrationTest aus. Melde exakt, welche Befehle ausgeführt wurden und ob sie bestanden haben.
```

---

# Phase P1 — Lexer, Tokens, Positionsmodell und Expression-Abgrenzung

## Ziel

Ein robuster Lexer und ein Expression-Reader existieren. Noch kein vollständiger Parser. Die schwierigste Grundlage — Expression-Ende am korrekten Semikolon — ist getestet.

## Neue Klassen

### `IlimapSourcePosition`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/lexer/IlimapSourcePosition.java
```

Vorschlag:

```java
package guru.interlis.transformer.mapping.ilimap.lexer;

public record IlimapSourcePosition(int offset, int line, int column) {
    public static IlimapSourcePosition start() {
        return new IlimapSourcePosition(0, 1, 1);
    }
}
```

### `IlimapSourceRange`

```java
package guru.interlis.transformer.mapping.ilimap.lexer;

public record IlimapSourceRange(IlimapSourcePosition start, IlimapSourcePosition end) {
}
```

### `IlimapTokenType`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/lexer/IlimapTokenType.java
```

Mindestwerte:

```java
public enum IlimapTokenType {
    IDENTIFIER,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    HASH_LITERAL,

    LBRACE,
    RBRACE,
    LPAREN,
    RPAREN,
    COMMA,
    SEMICOLON,
    EQUALS,
    ARROW,

    KEYWORD,
    EOF
}
```

`=>` ist `ARROW`. `->` darf im Lexer ebenfalls erkannt werden, aber der Parser muss Ref-Kurzform als unsupported ablehnen.

### `IlimapToken`

```java
package guru.interlis.transformer.mapping.ilimap.lexer;

public record IlimapToken(
        IlimapTokenType type,
        String text,
        IlimapSourceRange range
) {
    public boolean isKeyword(String keyword) {
        return type == IlimapTokenType.KEYWORD && text.equals(keyword);
    }
}
```

### `IlimapReservedWords`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapReservedWords.java
```

Methode:

```java
public final class IlimapReservedWords {
    public static boolean isReserved(String value) { ... }
    public static Set<String> all() { ... }
}
```

Reservierte Wörter mindestens:

```text
mapping, v2, job, input, output, oid, basket, enum, defaults,
rule, target, source, from, class, where, join, inner, left,
identity, assign, bag, structure, mode, embed, expand, maxItems,
parentRef, attribute, role, ref, association, required, create,
loss, sourcePath, reasonCode, description, when, metadata,
direction, roundtrip, lossiness, name, modeldir, path, model,
format, namespace, true, false, null
```

### `IlimapLexer`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/lexer/IlimapLexer.java
```

Öffentliche Methoden:

```java
public final class IlimapLexer {
    public IlimapLexer(String source);
    public IlimapToken peek();
    public IlimapToken next();
    public boolean hasNext();
    public List<IlimapToken> tokenize();
}
```

Interne Methoden:

```java
private void skipWhitespaceAndComments();
private IlimapToken readIdentifierOrKeyword();
private IlimapToken readString();
private IlimapToken readNumber();
private IlimapToken readHashLiteral();
private IlimapToken singleChar(IlimapTokenType type);
private boolean isIdentifierStart(char c);
private boolean isIdentifierPart(char c);
```

Lexing-Regeln:

- `//` Zeilenkommentar;
- `/* ... */` Blockkommentar;
- `#` ist kein Kommentarzeichen;
- `#LFP3` ist ein Hash-Literal;
- Strings unterstützen mindestens `\"`, `\\`, `\n`, `\r`, `\t`;
- unbekannte Zeichen erzeugen Diagnose/Exception mit Position.

### `IlimapExpressionReader`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/parser/IlimapExpressionReader.java
```

Öffentliche Methode:

```java
public final class IlimapExpressionReader {
    public IlimapExpressionText readUntilStatementSemicolon(String source, int startOffset);
}
```

`IlimapExpressionText`:

```java
package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapExpressionText(String text, IlimapSourceRange range) {
}
```

Der Reader muss zählen/erkennen:

- String-Literal-Zustand;
- Escape-Zeichen in Strings;
- Zeilenkommentar;
- Blockkommentar;
- runde Klammern;
- optional eckige/geschweifte Klammern;
- Fehlermeldung bei nicht geschlossenen Strings, Kommentaren oder Klammern.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/lexer/IlimapLexerTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/parser/IlimapExpressionReaderTest.java
```

Testfälle `IlimapLexerTest`:

- `tokenizesMappingHeader`
- `tokenizesCommentsAndSkipsThem`
- `hashIsEnumLiteralNotComment`
- `stringEscapesArePreserved`
- `reportsUnterminatedString`
- `reportsUnterminatedBlockComment`

Testfälle `IlimapExpressionReaderTest`:

- `stopsAtPlainStatementSemicolon`
- `doesNotStopAtSemicolonInsideString`
- `doesNotStopAtSemicolonInsideFunctionArgumentString`
- `doesNotStopAtSemicolonInsideBlockComment`
- `doesNotStopAtSemicolonInsideNestedParentheses`
- `reportsUnbalancedParentheses`
- `reportsUnterminatedStringLiteral`

Beispieltest:

```java
@Test
void doesNotStopAtSemicolonInsideString() {
    var reader = new IlimapExpressionReader();
    var expression = reader.readUntilStatementSemicolon(
            "replace(p.Text, \";\", \",\"); next", 0);
    assertThat(expression.text()).isEqualTo("replace(p.Text, \";\", \",\")");
}
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lexer.IlimapLexerTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.parser.IlimapExpressionReaderTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Lexer und Expression-Reader sind produktiv nutzbar und vollständig unit-getestet.

## Definition of Done

- Lexer behandelt Kommentare, Strings, Hash-Literals und Positionen korrekt.
- Expression-Reader behandelt Semikolons in Strings/Funktionen korrekt.
- Keine Integration in `MappingLoader` in dieser Phase.
- Tests grün oder Blocker ehrlich dokumentiert.

---

# Phase P2 — AST und Parser für Minimalprofil

## Ziel

Ein `.ilimap`-Minimalprofil kann in ein AST geparst werden. Der Parser unterstützt Top-Level, Job/Input/Output/Oid/Basket/Enum/Rule mit `target`, `source`, `where`, `identity`, `assign`, `defaults`. Noch keine `bag`, `ref`, `create`, `loss`, `metadata`.

## Neue AST-Klassen / Records

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/ast/
```

### `IlimapDocument`

```java
public record IlimapDocument(
        IlimapFormatVersion formatVersion,
        String name,
        IlimapJobBlock job,
        List<IlimapInputBlock> inputs,
        List<IlimapOutputBlock> outputs,
        IlimapOidBlock oid,
        IlimapBasketStmt basket,
        List<IlimapEnumBlock> enums,
        IlimapDefaultsBlock defaults,
        List<IlimapRuleBlock> rules,
        IlimapSourceRange range
) {}
```

### `IlimapFormatVersion`

```java
public enum IlimapFormatVersion {
    V2
}
```

### Basis-Interface

```java
public interface IlimapAstNode {
    IlimapSourceRange range();
}
```

### Weitere Minimal-AST-Records

```java
public record IlimapJobBlock(
        String name,
        String description,
        String direction,
        String failPolicy,
        String compileMode,
        List<String> modeldirs,
        IlimapSourceRange range
) implements IlimapAstNode {}

public record IlimapInputBlock(
        String id,
        String path,
        String model,
        String format,
        IlimapSourceRange range
) implements IlimapAstNode {}

public record IlimapOutputBlock(...)
public record IlimapOidBlock(String strategy, String namespace, IlimapSourceRange range) implements IlimapAstNode {}
public record IlimapBasketStmt(String strategy, IlimapSourceRange range) implements IlimapAstNode {}
public record IlimapEnumBlock(String id, List<IlimapEnumEntry> entries, IlimapSourceRange range) implements IlimapAstNode {}
public record IlimapEnumEntry(IlimapLiteral source, IlimapLiteral target, IlimapSourceRange range) implements IlimapAstNode {}
public record IlimapDefaultsBlock(List<IlimapAssignment> assignments, IlimapSourceRange range) implements IlimapAstNode {}
public record IlimapRuleBlock(String id, List<IlimapRuleElement> elements, IlimapSourceRange range) implements IlimapAstNode {}
public sealed interface IlimapRuleElement extends IlimapAstNode permits IlimapTargetStmt, IlimapSourceStmt, IlimapWhereStmt, IlimapIdentityStmt, IlimapAssignmentBlock, IlimapDefaultsBlock {}
public record IlimapTargetStmt(String outputId, String targetClass, IlimapSourceRange range) implements IlimapRuleElement {}
public record IlimapSourceStmt(String alias, List<String> inputIds, String sourceClass, IlimapExpressionText where, IlimapSourceRange range) implements IlimapRuleElement {}
public record IlimapWhereStmt(IlimapExpressionText expression, IlimapSourceRange range) implements IlimapRuleElement {}
public record IlimapIdentityStmt(List<IlimapExpressionText> expressions, IlimapSourceRange range) implements IlimapRuleElement {}
public record IlimapAssignmentBlock(List<IlimapAssignment> assignments, IlimapSourceRange range) implements IlimapRuleElement {}
public record IlimapAssignment(String targetAttribute, IlimapExpressionText expression, IlimapSourceRange range) implements IlimapAstNode {}
```

Falls die Java-Version im Projekt keine sealed interfaces erlaubt oder nicht gewünscht sind, normale Interfaces verwenden.

## `IlimapParser`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/parser/IlimapParser.java
```

Öffentliche Methoden:

```java
public final class IlimapParser {
    public IlimapParser(String source);
    public IlimapDocument parseDocument();
}
```

Wichtige private Methoden:

```java
private IlimapDocument parseMappingDecl();
private IlimapJobBlock parseJobBlock();
private IlimapInputBlock parseInputBlock();
private IlimapOutputBlock parseOutputBlock();
private IlimapOidBlock parseOidBlock();
private IlimapBasketStmt parseBasketStmt();
private IlimapEnumBlock parseEnumBlock();
private IlimapDefaultsBlock parseDefaultsBlock();
private IlimapRuleBlock parseRuleBlock();
private IlimapTargetStmt parseTargetStmt();
private IlimapSourceStmt parseSourceStmt();
private IlimapWhereStmt parseWhereStmt();
private IlimapIdentityStmt parseIdentityStmt();
private IlimapAssignmentBlock parseAssignmentBlock();
private IlimapAssignment parseAssignment();
private IlimapLiteral parseLiteral();
private String expectIdentifier();
private String expectString();
private void expectKeyword(String keyword);
private void expect(IlimapTokenType type);
```

Parser-Regeln:

- `mapping v2` ist Pflicht.
- Optionaler Mapping-Name als String ist erlaubt.
- Unbekannte Top-Level-Elemente sind Fehler.
- Unbekannte Rule-Elemente sind Fehler.
- `format` ist optional.
- `job` ist optional.
- `input` mindestens 1.
- `output` mindestens 1.
- `rule` mindestens 1.
- Pflichtanzahlen werden in P3 durch Semantic Validator geprüft, nicht zwingend im Parser.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/parser/IlimapParserMinimalTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/parser/IlimapParserErrorTest.java
```

Testfälle:

- `parsesMinimalMappingWithOneRule`
- `parsesJobInputOutputOidBasketEnum`
- `parsesRuleWithSourceWhereIdentityAssign`
- `parsesAssignmentExpressionWithSemicolonInString`
- `rejectsUnknownTopLevelElement`
- `rejectsUnknownRuleElement`
- `rejectsMissingSemicolon`
- `rejectsNonV2Mapping`

## Beispiel-Fixture

Datei:

```text
src/test/resources/mapping/ilimap/minimal-lfp3.ilimap
```

Inhalt gekürzt:

```ilimap
mapping v2 "minimal-lfp3" {
  job {
    direction dm01-to-dmav;
    failPolicy strict;
    compileMode compatible;
    modeldir "https://models.geo.admin.ch/";
  }

  input dm01 {
    path "input/dm01.itf";
    model "DM01AVCH24LV95D";
    format itf;
  }

  output dmav {
    path "build/out/dmav.xtf";
    model "DMAV_FixpunkteAVKategorie3_V1_1";
    format xtf;
  }

  enum Zuverlaessigkeit_DM01_DMAV {
    "ja" => true;
    "nein" => false;
  }

  rule lfp3 {
    target dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3";
    source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
    where p.LFPArt == #LFP3;
    identity p.NBIdent, p.Nummer;

    assign {
      Nummer = p.Nummer;
      Beschreibung = replace(p.Beschreibung, ";", ",");
      IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
    }
  }
}
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.parser.IlimapParserMinimalTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.parser.IlimapParserErrorTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Ein Minimalprofil kann vollständig in AST geparst werden.

## Definition of Done

- Parser erzeugt AST mit Source Ranges.
- Parser lehnt unbekannte Syntax ab.
- Keine Integration in Runtime.
- Tests für positive und negative Fälle existieren.

---

# Phase P3 — Statische Semantik, Symboltabelle und die sieben Härtungen

## Ziel

Der AST wird semantisch validiert. Die sieben Härtungspunkte sind als konkrete Diagnostics und Tests abgedeckt.

## Neue Klassen

### `IlimapIdentifierRules`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapIdentifierRules.java
```

Methoden:

```java
public final class IlimapIdentifierRules {
    public static boolean isValidSymbolId(String value);
    public static boolean isValidAliasId(String value);
    public static void requireSymbolId(String value, DiagnosticCollector diagnostics, IlimapSourceRange range);
    public static void requireAliasId(String value, DiagnosticCollector diagnostics, IlimapSourceRange range);
}
```

Validierung:

```text
symbolId = [A-Za-z][A-Za-z0-9_-]*
aliasId  = [A-Za-z][A-Za-z0-9_]*
```

Reserved Words sind für beide verboten.

### `IlimapSymbolKind`

```java
public enum IlimapSymbolKind {
    INPUT,
    OUTPUT,
    RULE,
    ENUM_MAP,
    SOURCE_ALIAS,
    JOIN_ALIAS,
    BAG,
    REF
}
```

### `IlimapSymbol`

```java
public record IlimapSymbol(
        IlimapSymbolKind kind,
        String name,
        IlimapAstNode node
) {}
```

### `IlimapScope`

Methoden:

```java
public final class IlimapScope {
    public IlimapScope(IlimapScope parent);
    public Optional<IlimapSymbol> resolve(String name);
    public Optional<IlimapSymbol> resolveLocal(String name);
    public boolean define(IlimapSymbol symbol, DiagnosticCollector diagnostics);
}
```

### `IlimapSymbolTable`

Methoden:

```java
public final class IlimapSymbolTable {
    public IlimapScope topLevelScope();
    public IlimapScope scopeFor(IlimapRuleBlock rule);
    public IlimapScope scopeFor(IlimapBagBlock bag);
    public Optional<IlimapSymbol> resolveRule(String id);
    public Optional<IlimapSymbol> resolveInput(String id);
    public Optional<IlimapSymbol> resolveOutput(String id);
    public Optional<IlimapSymbol> resolveEnumMap(String id);
}
```

### `IlimapSemanticValidator`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapSemanticValidator.java
```

Öffentliche Methode:

```java
public final class IlimapSemanticValidator {
    public IlimapSemanticResult validate(IlimapDocument document);
}
```

Result:

```java
public record IlimapSemanticResult(
        IlimapDocument document,
        IlimapSymbolTable symbols,
        List<Diagnostic> diagnostics
) {
    public boolean hasErrors() { ... }
}
```

Falls das Projekt bereits `DiagnosticCollector` verwendet, daran anschliessen statt parallele Diagnostic-Struktur zu bauen.

## Semantikprüfungen

### Top-Level

- Mindestens ein `input`.
- Mindestens ein `output`.
- Mindestens eine `rule`.
- `input`-IDs eindeutig.
- `output`-IDs eindeutig.
- `rule`-IDs eindeutig.
- `enum`-IDs eindeutig.
- `job` maximal einmal.
- `oid` maximal einmal.
- `basket` maximal einmal.
- `defaults` maximal einmal.

### Strategien

Erlaubte `failPolicy`:

```text
strict, lenient, reportOnly, report_only
```

Erlaubte `compileMode`:

```text
strict, compatible, report
```

Erlaubte `oid`-Strategien:

```text
preserve, integer, uuid, deterministicUuid
```

`external` ist reserviert. Wenn nicht implementiert:

```text
ERROR: oid strategy external is reserved but not implemented
```

Erlaubte `basket`-Strategien:

```text
preserve, generateUuid, preserveOrGenerateUuid, byTopic
```

`expression` ist reserviert. Wenn nicht implementiert:

```text
ERROR: basket strategy expression is reserved but not implemented
```

### Rule-Scope

- Genau ein `target` pro Rule.
- Mindestens eine `source` pro Rule.
- Source-Aliases eindeutig pro Rule.
- Source-Aliases müssen `aliasId` sein, also ohne `-`.
- `target`-Output-ID muss existieren.
- `source`-Input-IDs müssen existieren.
- `join`-Aliase müssen bereits deklarierte Source-Aliases sein.
- Falls Runtime nur einen Join unterstützt: mehr als ein `join` ist ERROR.
- `identity` maximal einmal.
- `assign` maximal einmal.
- Rule-`defaults` maximal einmal.

### Enum-Map-Referenzen

- In `enumMap(x, EnumName)` muss `EnumName` im Top-Level als `enum` existieren.
- Wenn zweites Argument String ist, z. B. `enumMap(x, "EnumName")`, soll v2.0 aus Kompatibilität erlaubt sein, aber eine Style-Warning erzeugen können:

```text
WARNING: enumMap uses string literal; prefer symbolic enum map reference
```

Diese Warning darf nicht brechen, falls bestehende Profile Strings verwenden.

### Defaults/Assign

- Doppelte Zuweisungen innerhalb desselben `assign`-Blocks sind Fehler.
- Doppelte Defaults innerhalb desselben `defaults`-Blocks sind Fehler.
- Dasselbe Attribut darf in `assign` und `defaults` vorkommen. Semantik: `assign` gewinnt immer.

### Ref-Langform

- Ref-Kurzform ist nicht unterstützt.
- `target rule <id>` muss existierende Rule referenzieren.
- `sourceRef`-Expression wird erst durch bestehenden Expression-Compiler geprüft, aber Alias-Namen können optional bereits syntaktisch gesucht werden.
- `required` ist Marker ohne Wert.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapSemanticValidatorTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapIdentifierRulesTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapSymbolTableTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapEnumMapReferenceTest.java
```

Testfälle:

- `acceptsHyphenInRuleId`
- `rejectsHyphenInSourceAlias`
- `rejectsReservedWordAsRuleId`
- `rejectsDuplicateInputIds`
- `rejectsDuplicateRuleIds`
- `rejectsUnknownTargetOutput`
- `rejectsUnknownSourceInput`
- `rejectsUnknownEnumMapReference`
- `acceptsSymbolicEnumMapReference`
- `acceptsStringEnumMapReferenceWithWarning`
- `rejectsJoinWithUnknownLeftAlias`
- `rejectsJoinWithUnknownRightAlias`
- `rejectsMultipleJoinsIfRuntimeSupportsOnlyOne`
- `rejectsRefShortForm`
- `rejectsUnknownTargetRuleInRef`
- `assignOverridesDefaultEvenIfSameAttribute`
- `rejectsDuplicateAssignmentsInSameBlock`

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.semantic.IlimapIdentifierRulesTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidatorTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.semantic.IlimapEnumMapReferenceTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Ein `.ilimap`-AST kann vollständig statisch validiert werden. Die sieben Härtungspunkte sind als Tests sichtbar.

## Definition of Done

- Keine unbekannten IDs bleiben unentdeckt.
- Alias-Regeln sind getestet.
- Enum-Map-Symbole sind getestet.
- Join-Semantik ist getestet.
- Defaults-/Null-Semantik ist dokumentiert und getestet, soweit statisch möglich.
- Unsupported/reservierte Features erzeugen Diagnostics.

---

# Phase P4 — Mapping nach `JobConfig` und Loader-Integration

## Ziel

Ein `.ilimap`-Minimalprofil kann über den normalen Loader-Pfad in `JobConfig` geladen und durch den bestehenden `MappingCompiler` kompiliert werden. Die Runtime bleibt unverändert.

## Neue Klassen

### `IlimapToJobConfigMapper`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/jobconfig/IlimapToJobConfigMapper.java
```

Öffentliche Methode:

```java
public final class IlimapToJobConfigMapper {
    public JobConfig map(IlimapDocument document, IlimapSymbolTable symbols, Path baseDirectory);
}
```

Aufgaben:

- `mapping v2` in passende interne Config-Version übersetzen;
- `job` nach `JobConfig.JobSection`;
- `input`/`output` nach bestehenden Input-/Output-Specs;
- `oid` nach bestehender OID-Strategie;
- `basket` nach bestehender Basket-Strategie;
- `enum` nach bestehender Enum-Map-Struktur;
- `rule` nach bestehenden RuleSpecs;
- `target`/`source`/`where`/`identity`/`assign`/`defaults` abbilden;
- Expressions unverändert oder normalisiert übergeben;
- keine Runtime-Objekte erzeugen.

### `IlimapExpressionNormalizer`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapExpressionNormalizer.java
```

Methoden:

```java
public final class IlimapExpressionNormalizer {
    public String normalizeForJobConfig(IlimapExpressionText expression, IlimapSymbolTable symbols);
}
```

Mindestfunktion:

- `enumMap(expr, EnumSymbol)` nach `enumMap(expr, "EnumSymbol")` normalisieren, falls bestehende Expression-Engine String erwartet.
- Nicht versuchen, die gesamte Expression-Sprache neu zu parsen.
- String-Literals respektieren.
- Keine Ersetzungen innerhalb von Strings oder Kommentaren.

Wenn die bestehende Expression-Engine symbolische Enum-Referenzen direkt akzeptieren kann, darf diese Klasse no-op sein, muss aber trotzdem Tests besitzen.

### `IlimapLoader`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/IlimapLoader.java
```

Methoden:

```java
public final class IlimapLoader {
    public JobConfig load(Path path);
    public JobConfig load(String source, Path baseDirectory);
    public IlimapLoadResult loadDetailed(Path path);
}
```

`loadDetailed`:

```java
public record IlimapLoadResult(
        IlimapDocument document,
        IlimapSymbolTable symbols,
        JobConfig jobConfig,
        List<Diagnostic> diagnostics
) {
    public boolean hasErrors() { ... }
}
```

### `MappingFormat` und `MappingFormatDetector`

Falls nicht vorhanden:

```java
public enum MappingFormat {
    YAML,
    ILIMAP
}
```

```java
public final class MappingFormatDetector {
    public MappingFormat detect(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".ilimap")) return MappingFormat.ILIMAP;
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) return MappingFormat.YAML;
        throw new IllegalArgumentException("Unsupported mapping file extension: " + path);
    }
}
```

### Anpassung `MappingLoader`

Bestehende Klasse nicht brechen. Möglichst so erweitern:

```java
public JobConfig load(Path path) {
    MappingFormat format = MappingFormatDetector.detect(path);
    return switch (format) {
        case YAML -> loadYaml(path);
        case ILIMAP -> new IlimapLoader().load(path);
    };
}
```

Falls `MappingLoader` aktuell andere Signaturen hat, alte Signaturen erhalten und intern delegieren.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/jobconfig/IlimapToJobConfigMapperTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/IlimapLoaderTest.java
src/test/java/guru/interlis/transformer/mapping/model/MappingFormatDetectorTest.java
src/test/java/guru/interlis/transformer/mapping/model/MappingLoaderIlimapIntegrationTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapExpressionNormalizerTest.java
```

Testfälle:

- `mapsMinimalIlimapToJobConfig`
- `doesNotSetJobConfigVersionToIlimapFormatVersionUnlessExplicitlyRequired`
- `normalizesSymbolicEnumMapForExistingExpressionEngine`
- `loadPathDispatchesIlimapByExtension`
- `loadPathStillDispatchesYamlByExtension`
- `ilimapConfigCompilesWithExistingMappingCompiler`
- `unknownIlimapSyntaxDoesNotReachMappingCompilerAsIgnoredField`

## Golden-Test YAML vs. ILIMAP

Erstelle ein kleines YAML-Mapping und ein semantisch gleiches `.ilimap`-Mapping:

```text
src/test/resources/mapping/equivalence/minimal.yaml
src/test/resources/mapping/equivalence/minimal.ilimap
```

Test:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/IlimapYamlEquivalenceTest.java
```

Testlogik:

1. YAML laden -> `JobConfig yamlConfig`.
2. ILIMAP laden -> `JobConfig ilimapConfig`.
3. Beide normalisieren, falls Reihenfolge/Defaults variieren.
4. Relevante Felder vergleichen.
5. Beide mit `MappingCompiler` kompilieren.
6. Wichtige `TransformPlan`-Eigenschaften vergleichen.

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.jobconfig.IlimapToJobConfigMapperTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.IlimapLoaderTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.IlimapYamlEquivalenceTest"
./gradlew test
```

## Funktionsfähiges Artefakt

`.ilimap`-Minimalprofile können über den offiziellen Loader geladen und vom bestehenden Compiler verarbeitet werden.

## Definition of Done

- YAML-Loader bleibt kompatibel.
- `.ilimap` nutzt denselben `JobConfig`-/`MappingCompiler`-Pfad.
- Formatversion und Config-Semantikversion bleiben getrennt.
- Symbolische EnumMap wird korrekt verarbeitet.
- Tests beweisen YAML/ILIMAP-Äquivalenz für Minimalfall.

---

# Phase P5 — Vollständige v2.0-Regelstruktur: Bags, Refs, Create, Loss, Metadata

## Ziel

Die komplette v2.0-Grammatik ohne Ref-Kurzform wird geparst, semantisch validiert und nach `JobConfig` abgebildet.

## Zusätzliche AST-Records

```java
public record IlimapJoinStmt(String joinType, String leftAlias, String rightAlias, IlimapExpressionText on, IlimapSourceRange range) implements IlimapRuleElement {}

public record IlimapBagBlock(
        String name,
        IlimapBagFromStmt from,
        String structure,
        String mode,
        Integer maxItems,
        IlimapParentRefStmt parentRef,
        IlimapAssignmentBlock assign,
        List<IlimapBagBlock> nestedBags,
        IlimapSourceRange range
) implements IlimapRuleElement, IlimapBagElement {}

public record IlimapBagFromStmt(String alias, String inputId, String sourceClass, IlimapExpressionText where, IlimapSourceRange range) implements IlimapAstNode {}
public record IlimapParentRefStmt(String kind, String name, String parentAlias, IlimapSourceRange range) implements IlimapBagElement {}

public record IlimapRefBlock(
        String name,
        String association,
        String role,
        boolean required,
        String targetRuleId,
        IlimapExpressionText sourceRef,
        IlimapSourceRange range
) implements IlimapRuleElement {}

public record IlimapCreateBlock(String targetClass, IlimapAssignmentBlock assign, IlimapSourceRange range) implements IlimapRuleElement {}
public record IlimapLossBlock(List<IlimapLossStmt> statements, IlimapSourceRange range) implements IlimapRuleElement {}
public record IlimapMetadataBlock(Map<String, String> values, IlimapSourceRange range) implements IlimapRuleElement {}
```

Bei Bedarf feinere Records für `lossStmt` und `metadataStmt` verwenden, damit Source Ranges erhalten bleiben.

## Parser-Erweiterungen

Zusätzliche Methoden in `IlimapParser`:

```java
private IlimapJoinStmt parseJoinStmt();
private IlimapBagBlock parseBagBlock();
private IlimapBagFromStmt parseBagFromStmt();
private IlimapParentRefStmt parseParentRefStmt();
private IlimapRefBlock parseRefBlock();
private IlimapCreateBlock parseCreateBlock();
private IlimapLossBlock parseLossBlock();
private IlimapMetadataBlock parseMetadataBlock();
```

## Semantik-Erweiterungen

### Bags

- Bag-Namen innerhalb desselben Blocks eindeutig.
- Bag-Source-Alias ist `aliasId`, also ohne `-`.
- Bag-Source-Input existiert.
- `parentRef parent <alias>` muss in äußerem Scope sichtbar sein.
- Nested-Bag-Scope enthält eigenen Alias und äußere Aliases.
- `maxItems <= 0` ist Fehler.
- `mode` nur `embed` oder `expand`.
- Falls `maxItems` von Runtime nicht umgesetzt wird: ERROR statt ignorieren.

### Refs

- Nur Langform.
- `target rule` muss existieren.
- `required` mehrfach ist Fehler oder Warning; empfohlen: Fehler.
- `association` und `role` müssen, falls bestehende Runtime sie benötigt, vorhanden sein.
- Falls bestehende Runtime optional association/role erlaubt, Semantik aus YAML beibehalten.

### Create

- `create class` wird nur so weit unterstützt, wie YAML/Runtime es bereits unterstützt.
- Nicht unterstützte Create-Unterelemente sind Fehler.
- OID-Strategie von `create` darf nicht hartcodiert verändert werden, wenn bestehende Runtime andere Semantik vorsieht.
- Falls Create in bestehender Runtime experimentell ist, Doku entsprechend markieren.

### Loss/Metadata

- Dokumentierende Blöcke dürfen keine implizite Runtime-Logik einführen.
- Mapping nach `JobConfig` nur, wenn dort bereits Felder existieren.
- Falls `JobConfig` keine Entsprechung besitzt, als dokumentierende Struktur in `JobConfig.metadata` oder ähnlicher bestehender Struktur ablegen. Nicht ignorieren.
- Falls gar keine Ablage existiert: Phase mit klarer Diagnostic `unsupported field` abschliessen und Doku entsprechend markieren.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/parser/IlimapParserFullRuleTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapBagSemanticTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapRefSemanticTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/jobconfig/IlimapFullRuleToJobConfigTest.java
```

Testfälle:

- `parsesBagWithFromStructureModeMaxItemsAssign`
- `parsesNestedBags`
- `rejectsBagAliasWithHyphen`
- `rejectsUnknownParentAlias`
- `rejectsMaxItemsZero`
- `maxItemsIsMappedOrRejectedButNotIgnored`
- `parsesRefLongForm`
- `rejectsRefShortForm`
- `rejectsUnknownTargetRule`
- `parsesCreateBlock`
- `unsupportedCreateSubelementsProduceDiagnostics`
- `parsesLossAndMetadata`
- `lossAndMetadataMappedOrRejectedButNotIgnored`

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.parser.IlimapParserFullRuleTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.semantic.IlimapBagSemanticTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.semantic.IlimapRefSemanticTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.jobconfig.IlimapFullRuleToJobConfigTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Die vollständige v2.0-Struktur kann geparst, validiert und soweit semantisch vorhanden nach `JobConfig` gemappt werden. Nicht unterstützte Teile werden explizit abgelehnt.

## Definition of Done

- Keine v2.0-Struktur wird still ignoriert.
- Bags und Refs sind mit Scopes getestet.
- Ref-Kurzform wird abgelehnt.
- `maxItems` wird umgesetzt oder explizit diagnostiziert.
- Create/Loss/Metadata sind ehrlich dokumentiert.

---

# Phase P6 — Formatter und Print-Stabilität

## Ziel

Ein stabiler Formatter existiert. `parse -> format -> parse` ist idempotent auf AST-Ebene. Der Formatter ist diff-freundlich und richtet Assignments nicht aggressiv spaltenweise aus.

## Neue Klassen

### `IlimapFormatOptions`

```java
public record IlimapFormatOptions(
        int indentSize,
        boolean finalNewline,
        boolean alignAssignments
) {
    public static IlimapFormatOptions defaults() {
        return new IlimapFormatOptions(2, true, false);
    }
}
```

### `IlimapFormatter`

```java
public final class IlimapFormatter {
    public String format(IlimapDocument document);
    public String format(IlimapDocument document, IlimapFormatOptions options);
}
```

### `IlimapPrinter`

Interne Methoden:

```java
private void printDocument(IlimapDocument document);
private void printJob(IlimapJobBlock job);
private void printInput(IlimapInputBlock input);
private void printOutput(IlimapOutputBlock output);
private void printEnum(IlimapEnumBlock enumBlock);
private void printRule(IlimapRuleBlock rule);
private void printAssignmentBlock(IlimapAssignmentBlock block);
private void printBag(IlimapBagBlock bag);
private void printRef(IlimapRefBlock ref);
private void line(String text);
private void indent(Runnable block);
```

## Formatierungsregeln

- Einrückung: 2 Spaces.
- Keine Tabs.
- Einfache Statements enden mit `;`.
- Leere Zeile zwischen Top-Level-Blöcken.
- Leere Zeile zwischen Rule-Hauptteilen und `assign`/`bag`/`ref`, falls sinnvoll.
- Assignments standardmässig nicht spaltenweise alignen.
- Strings werden mit vorhandener Escaping-Logik ausgegeben.
- Expressions werden getrimmt, aber intern nicht neu formatiert.
- Kommentare müssen in v2.0 nicht erhalten bleiben, falls AST sie nicht speichert. Das muss dokumentiert sein.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/format/IlimapFormatterTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/format/IlimapFormatterRoundtripTest.java
```

Testfälle:

- `formatsMinimalMapping`
- `formatsFullRuleWithBagAndRef`
- `parseFormatParseKeepsEquivalentAst`
- `formatIsStableWhenRunTwice`
- `doesNotAlignAssignmentsByDefault`
- `preservesExpressionTextExceptTrimming`

Roundtrip:

```java
IlimapDocument doc1 = parser.parse(source);
String formatted1 = formatter.format(doc1);
IlimapDocument doc2 = parser.parse(formatted1);
String formatted2 = formatter.format(doc2);
assertThat(formatted2).isEqualTo(formatted1);
```

## CLI-Integration optional in dieser Phase

Falls `CliMain` einfach erweiterbar ist, ergänzen:

```bash
ilitransformer format-mapping path/to/profile.ilimap
ilitransformer format-mapping --check path/to/profile.ilimap
ilitransformer format-mapping --write path/to/profile.ilimap
```

Wenn CLI-Integration zu gross ist, erst P7 machen. P6 bleibt dann trotzdem funktionsfähig über Java-API und Tests.

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.format.IlimapFormatterTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.format.IlimapFormatterRoundtripTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Formatter-API mit stabilen Roundtrip-Tests.

## Definition of Done

- Formatter erzeugt stabile Ausgabe.
- `parse -> format -> parse -> format` ist stabil.
- Keine aggressive Ausrichtung, die Diffs verschlechtert.
- Kommentarverlust ist dokumentiert, falls Kommentare nicht erhalten bleiben.

---

# Phase P7 — YAML-zu-ILIMAP-Converter

## Ziel

Ein bestehendes YAML-Mapping kann in eine syntaktisch korrekte `.ilimap`-Datei konvertiert werden. Kommentare müssen nicht erhalten bleiben.

## Neue Klasse `YamlToIlimapConverter`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/convert/YamlToIlimapConverter.java
```

Methoden:

```java
public final class YamlToIlimapConverter {
    public String convert(JobConfig config);
    public String convert(Path yamlPath);
}
```

Umsetzung:

- YAML mit bestehendem `MappingLoader` laden.
- `JobConfig` in `IlimapDocument` oder direkt in Formatter-Modell überführen.
- Bevorzugt: `JobConfigToIlimapAstMapper` ergänzen.
- Dann `IlimapFormatter` verwenden.

Neue Klasse optional:

```text
guru.interlis.transformer.mapping.ilimap.convert.JobConfigToIlimapAstMapper
```

Methoden:

```java
public IlimapDocument map(JobConfig config);
```

## CLI-Command

Bestehenden CLI-Bereich prüfen. Zielsyntax:

```bash
ilitransformer convert-mapping --from profiles/dm01-to-dmav/1.1/lfp3.yaml --to profiles/dm01-to-dmav/1.1/lfp3.ilimap
```

oder:

```bash
ilitransformer convert-mapping --input profile.yaml --output profile.ilimap
```

Der Agent soll sich an bestehende CLI-Konventionen halten.

## Regeln

- Kommentare müssen nicht erhalten bleiben.
- Ausgabe muss durch `IlimapParser` parsebar sein.
- Ausgabe muss durch `IlimapSemanticValidator` validierbar sein.
- Ausgabe muss wieder nach `JobConfig` ladbar sein.
- Nicht unterstützte YAML-Felder dürfen nicht weggelassen werden. Wenn keine `.ilimap`-Entsprechung existiert, muss der Converter mit Fehler abbrechen.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/convert/YamlToIlimapConverterTest.java
src/integrationTest/java/guru/interlis/transformer/mapping/ilimap/convert/ConvertMappingCliTest.java
```

Testfälle:

- `convertsMinimalYamlToParseableIlimap`
- `convertedIlimapLoadsToEquivalentJobConfig`
- `convertedIlimapFormatsStably`
- `converterFailsOnUnsupportedYamlFeature`
- `cliConvertsFile`
- `cliDoesNotOverwriteWithoutExplicitOptionIfThatIsExistingCliStyle`

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.convert.YamlToIlimapConverterTest"
./gradlew integrationTest --tests "guru.interlis.transformer.mapping.ilimap.convert.ConvertMappingCliTest"
./gradlew test
./gradlew integrationTest
```

Falls `integrationTest` nicht existiert, passenden Testtask verwenden und dokumentieren.

## Funktionsfähiges Artefakt

YAML kann nach `.ilimap` konvertiert werden. Die resultierende Datei ist parsebar, validierbar und wieder ladbar.

## Definition of Done

- Kein stiller Verlust von Mapping-Feldern.
- Kommentare nicht erhalten ist akzeptiert und dokumentiert.
- Converter nutzt Formatter.
- CLI-Command ist dokumentiert.

---

# Phase P8 — End-to-End-Integration mit TransformationEngine

## Ziel

Eine `.ilimap`-Datei kann in einem echten Transformationslauf verwendet werden. Die Runtime bleibt identisch zum YAML-Pfad.

## Aufgaben

1. `MappingLoader` oder `JobRunner` so integrieren, dass `.ilimap` als Mapping-Datei akzeptiert wird.
2. Bestehende CLI `transform` muss `.ilimap` akzeptieren.
3. Bestehende CLI `validate-mapping` muss `.ilimap` akzeptieren.
4. Diagnostics müssen Syntax-/Semantikfehler mit Datei/Zeile/Spalte melden.
5. Feature-Matrix und Doku aktualisieren.

## Integration in bestehende Klassen

### `CliMain.TransformCommand`

Prüfen, ob der Mapping-Pfad nur YAML annimmt. Falls Extension-agnostisch, keine Änderung. Sonst auf `.ilimap` erweitern.

Erwartetes Verhalten:

```bash
ilitransformer transform --mapping examples/minimal/profile.ilimap
```

### `CliMain.ValidateMappingCommand`

Erwartetes Verhalten:

```bash
ilitransformer validate-mapping examples/minimal/profile.ilimap
```

Erfolg:

```text
Mapping valid: examples/minimal/profile.ilimap
```

Fehler:

```text
ERROR ILIMAP_UNKNOWN_SYMBOL at examples/minimal/profile.ilimap:23:15
Unknown output id: dmav_wrong
```

## Tests

Neue Tests:

```text
src/integrationTest/java/guru/interlis/transformer/mapping/ilimap/IlimapEndToEndTransformationTest.java
src/integrationTest/java/guru/interlis/transformer/mapping/ilimap/IlimapValidateMappingCliTest.java
src/integrationTest/java/guru/interlis/transformer/mapping/ilimap/IlimapDiagnosticsCliTest.java
```

Testfälle:

- `transformsMinimalSyntheticDatasetWithIlimap`
- `validateMappingAcceptsIlimap`
- `validateMappingReportsLineAndColumnForSyntaxError`
- `validateMappingReportsUnknownSymbol`
- `yamlAndIlimapProduceEquivalentOutputForMinimalFixture`

## Testdaten

Bevorzugt synthetische, kleine Modelle und Transfers nutzen, die bereits im Repository validiert sind. Keine neuen INTERLIS-1-Rohdaten aus dem Gedächtnis schreiben.

Falls neue `.ili` oder `.xtf` nötig sind:

- `.ili` mit `ili2c` validieren;
- `.xtf` mit `ilivalidator` validieren;
- Testdatenvertrag dokumentieren.

## DM01/DMAV-Gate

Wenn in dieser Phase DM01/DMAV-Profile oder DM01/DMAV-spezifische Mapping-Effekte berührt werden, zusätzlich:

```bash
./gradlew realDataTest
```

Oder mindestens die kleinste relevante RealData-Testklasse. Der Agent muss zuerst die vorhandenen RealData-Tests entdecken.

## Verifikation

```bash
./gradlew test
./gradlew integrationTest
./gradlew check
```

Zusätzlich bei DM01/DMAV-Berührung:

```bash
./gradlew realDataTest
```

## Funktionsfähiges Artefakt

CLI und JobRunner akzeptieren `.ilimap` produktiv für Minimal-/synthetische Transformationen.

## Definition of Done

- `.ilimap` ist im Transform-CLI nutzbar.
- Diagnostics haben Datei/Zeile/Spalte.
- YAML-Pfad bleibt unverändert.
- End-to-End-Test mit `.ilimap` ist grün.
- Doku und Feature-Matrix sind aktualisiert.

---

# Phase P9 — Dokumentation, Beispiele und Feature-Matrix

## Ziel

Die neue `.ilimap`-DSL ist für Entwickler und Anwender dokumentiert. Beispiele sind ausführbar und Tests schützen die Dokumentation vor Drift.

## Dateien

Aktualisieren oder neu anlegen:

```text
docs/mapping-dsl.md
docs/ilimap-v2.md
docs/cli.md
docs/feature-matrix.md
examples/ilimap/minimal/profile.ilimap
examples/ilimap/minimal/README.md
```

Falls Produktprofile getrennt dokumentiert sind:

```text
products/dm01-dmav/docs/ilimap.md
```

## Inhalt `docs/ilimap-v2.md`

Muss enthalten:

- Zielbild;
- Nicht-Ziele;
- Grundstruktur;
- Lexikalische Regeln;
- `symbolId` vs. `aliasId`;
- Expression-Abgrenzung;
- Top-Level-Blöcke;
- Rules;
- Bags;
- Referenzen Langform;
- Create/Loss/Metadata mit tatsächlichem Implementierungsstand;
- Defaults-/Null-Semantik;
- `maxItems`-Semantik;
- Mapping nach `JobConfig`;
- Migration aus YAML;
- bekannte Einschränkungen.

## Feature-Matrix

Feature-Matrix muss mindestens unterscheiden:

```text
ILIMAP parser: supported
ILIMAP semantic validation: supported
ILIMAP loader to JobConfig: supported
ILIMAP formatter: supported
YAML -> ILIMAP converter: supported/experimental
ILIMAP end-to-end transform: supported/experimental
Ref short form: not supported/reserved
Includes/macros: not supported
Qualified INTERLIS names as tokens: not supported; class/model as strings
```

Wenn Feature-Matrix Testreferenzen enthält, muss es einen Test geben, der prüft, dass referenzierte Tests existieren.

## Tests

Neue oder bestehende Tests:

```text
src/test/java/guru/interlis/transformer/feature/FeatureMatrixTest.java
src/test/java/guru/interlis/transformer/docs/DocumentationExampleTest.java
```

Testfälle:

- `featureMatrixReferencesExistingTests`
- `allIlimapExamplesParse`
- `allIlimapExamplesValidateSemantically`
- `minimalExampleLoadsToJobConfig`

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.feature.FeatureMatrixTest"
./gradlew test --tests "guru.interlis.transformer.docs.DocumentationExampleTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Dokumentation und Beispiele sind synchron mit dem Code und werden durch Tests geschützt.

## Definition of Done

- Doku beschreibt nur implementierte Features als unterstützt.
- Reservierte/nicht unterstützte Features sind explizit markiert.
- Beispiele werden automatisch geparst/validiert.
- Feature-Matrix verweist nicht auf nicht existierende Tests.

---

# Phase P10 — LSP-/IDE-MVP auf Basis von Parser und Symboltabelle

## Ziel

Ein minimaler Language-Server oder zumindest eine LSP-fähige Bibliothek liefert Diagnostics, Completion-Grundlagen und Go-to-definition-Daten aus demselben Parser/AST/Symboltable-Pfad.

Diese Phase darf erst beginnen, wenn Parser, Semantic Validator und Formatter stabil sind.

## Architekturentscheidung

Der LSP darf **keinen eigenen Parser** implementieren. Er muss dieselben Klassen verwenden:

```text
IlimapParser
IlimapSemanticValidator
IlimapSymbolTable
IlimapFormatter
```

## Variante A: LSP im Java-Projekt mit LSP4J

Neue Packages:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/lsp/
```

Klassen:

```text
IlimapLanguageServer
IlimapTextDocumentService
IlimapWorkspaceService
IlimapDocumentStore
IlimapDiagnosticMapper
IlimapCompletionService
IlimapDefinitionService
IlimapHoverService
```

### `IlimapLanguageServer`

Methoden:

```java
public CompletableFuture<InitializeResult> initialize(InitializeParams params);
public CompletableFuture<Object> shutdown();
public void exit();
public TextDocumentService getTextDocumentService();
public WorkspaceService getWorkspaceService();
```

### `IlimapTextDocumentService`

Methoden:

```java
public void didOpen(DidOpenTextDocumentParams params);
public void didChange(DidChangeTextDocumentParams params);
public void didClose(DidCloseTextDocumentParams params);
public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position);
public CompletableFuture<List<? extends Location>> definition(DefinitionParams params);
public CompletableFuture<Hover> hover(HoverParams params);
public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params);
```

### `IlimapDocumentStore`

```java
public final class IlimapDocumentStore {
    public void open(String uri, String text);
    public void update(String uri, String text);
    public void close(String uri);
    public Optional<IlimapAnalysis> analyze(String uri);
}
```

`IlimapAnalysis`:

```java
public record IlimapAnalysis(
        IlimapDocument document,
        IlimapSymbolTable symbols,
        List<Diagnostic> diagnostics
) {}
```

## Variante B: Parser-Bibliothek plus VS-Code-Extension später

Falls LSP4J-Abhängigkeit zu gross ist, in dieser Phase nur Services bauen:

```text
IlimapAnalysisService
IlimapCompletionService
IlimapDefinitionService
IlimapHoverService
```

Diese Services können später von einem LSP oder einer VS-Code-Extension verwendet werden.

## Completion-MVP

Completion soll liefern:

- Top-Level-Keywords;
- Rule-Keywords innerhalb Rule;
- bekannte `input`-IDs nach `from`;
- bekannte `output`-IDs nach `target`;
- bekannte Rule-IDs in `target rule`;
- bekannte Enum-IDs im zweiten Argument von `enumMap`;
- Aliases aus Scope in Expressions optional später.

## Diagnostics-MVP

Diagnostics aus:

- Parser;
- Semantic Validator;
- optional bestehendem Expression-Compiler;
- optional ModelRegistry/ili2c, wenn Modeldirs verfügbar sind.

## Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapAnalysisServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapCompletionServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapDefinitionServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapFormattingServiceTest.java
```

Testfälle:

- `publishesDiagnosticsForUnknownInput`
- `completesTopLevelKeywords`
- `completesInputIdsInSourceFrom`
- `completesOutputIdsInTarget`
- `completesEnumMapSymbols`
- `goToDefinitionForEnumMap`
- `goToDefinitionForTargetRuleRef`
- `formattingUsesIlimapFormatter`

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lsp.*"
./gradlew test
```

## Funktionsfähiges Artefakt

Ein LSP-MVP oder eine LSP-fähige Analysebibliothek existiert und verwendet Parser/AST/Symboltabelle wieder.

## Definition of Done

- Kein zweiter Parser.
- Diagnostics stammen aus bestehenden Parser-/Semantik-Komponenten.
- Completion/Definition nutzen Symboltabelle.
- Formatter wird wiederverwendet.

---

# Phase P11 — DM01/DMAV-Profil-Pilot mit `.ilimap`

## Ziel

Ein kleines, produktnahes DM01→DMAV-Profil wird als `.ilimap`-Pilot bereitgestellt und gegen bestehende Tests/Echtdaten geprüft. Diese Phase ist erst sinnvoll, wenn End-to-End-Integration stabil ist.

## Zusätzliche Skills

```text
.skills/interlis-validation/SKILL.md
.skills/dm01-dmav-real-data-gate/SKILL.md
```

Falls INTERLIS-1-ITF-Testdaten oder AREA betroffen sind:

```text
.skills/interlis1-testdata/SKILL.md
```

## Aufgaben

1. Wähle ein kleines bestehendes YAML-Profil, z. B. LFP3-Pilot.
2. Konvertiere es mit `convert-mapping` nach `.ilimap`.
3. Formatiere es mit `IlimapFormatter`.
4. Prüfe YAML/ILIMAP-Äquivalenz auf `JobConfig`- und `TransformPlan`-Ebene.
5. Führe kleinste relevante Transformation aus.
6. Falls DM01/DMAV-Echtdaten vorhanden sind, RealData-Gate ausführen.

## Dateien

Beispielstruktur, an tatsächliches Repo anpassen:

```text
products/dm01-dmav/profiles/dm01-to-dmav/1.1/lfp3.ilimap
src/integrationTest/java/guru/interlis/transformer/dmav/Dm01DmavIlimapPilotTest.java
```

Falls Profile im Root bleiben:

```text
profiles/dm01-to-dmav/1.1/lfp3.ilimap
```

## Tests

```text
src/integrationTest/java/guru/interlis/transformer/dmav/Dm01DmavIlimapPilotTest.java
```

Testfälle:

- `lfp3YamlAndIlimapCompileToEquivalentPlan`
- `lfp3IlimapRunsSmallFixture`
- `lfp3IlimapDoesNotIntroduceDm01DmavLogicIntoCore`

Zusätzlich Architekturprüfung, falls vorhanden:

```bash
grep -R "guru.interlis.transformer.dmav" -n src/main/java/guru/interlis/transformer/mapping src/main/java/guru/interlis/transformer/engine src/main/java/guru/interlis/transformer/model
```

Es soll keine Treffer geben.

## Verifikation

```bash
./gradlew test
./gradlew integrationTest
./gradlew check
```

Bei Echtdaten:

```bash
./gradlew realDataTest
```

Und falls `.ili`, `.itf`, `.xtf`, `.xml` geändert oder erzeugt wurden:

```bash
# konkrete Befehle aus .skills/interlis-validation/SKILL.md bzw. lokaler Doku verwenden
ili2c ...
ilivalidator ...
```

## Funktionsfähiges Artefakt

Ein erstes reales oder produktnahes `.ilimap`-Profil existiert und läuft durch Compiler/Transformation.

## Definition of Done

- Pilotprofil ist formatiert.
- YAML/ILIMAP-Äquivalenz ist getestet.
- DM01/DMAV bleibt Produktprofil, nicht generischer Kern.
- RealData-Gate wurde ausgeführt oder ehrlicher Blocker dokumentiert.

---

## 5. Diagnostik-Konzept

Falls bestehende `DiagnosticCode`-Struktur existiert, neue Codes dort ergänzen. Keine neue parallele Fehlerwelt einführen, wenn vermeidbar.

Vorschläge:

```text
ILIMAP_SYNTAX_ERROR
ILIMAP_UNTERMINATED_STRING
ILIMAP_UNTERMINATED_COMMENT
ILIMAP_UNBALANCED_EXPRESSION
ILIMAP_UNKNOWN_TOP_LEVEL_ELEMENT
ILIMAP_UNKNOWN_RULE_ELEMENT
ILIMAP_DUPLICATE_SYMBOL
ILIMAP_UNKNOWN_INPUT
ILIMAP_UNKNOWN_OUTPUT
ILIMAP_UNKNOWN_RULE
ILIMAP_UNKNOWN_ENUM_MAP
ILIMAP_INVALID_SYMBOL_ID
ILIMAP_INVALID_ALIAS_ID
ILIMAP_RESERVED_KEYWORD
ILIMAP_UNSUPPORTED_RESERVED_FEATURE
ILIMAP_REF_SHORT_FORM_UNSUPPORTED
ILIMAP_MULTIPLE_JOINS_UNSUPPORTED
ILIMAP_MAX_ITEMS_UNSUPPORTED
ILIMAP_DUPLICATE_ASSIGNMENT
```

Jede Diagnostic sollte enthalten:

```text
code
severity
message
file/path
line
column
optional hint
```

Severity-Regeln:

```text
ERROR   blockiert Laden/Kompilierung
WARNING erlaubt Laden, weist auf Risiko oder veralteten Stil hin
INFO    dokumentierend
```

---

## 6. Akzeptanzkriterien über alle Phasen

Am Ende der vollständigen Umsetzung muss gelten:

1. YAML-Mappings laden weiterhin unverändert.
2. `.ilimap`-Mappings laden über denselben `JobConfig`-/`MappingCompiler`-Pfad.
3. `mapping v2` ist Formatversion, nicht automatische Runtime-Semantikversion.
4. Unbekannte Syntax ist Fehler.
5. Nicht unterstützte Felder werden diagnostiziert, nicht ignoriert.
6. Source-/Join-/Bag-Aliases mit `-` werden abgelehnt.
7. Rule-IDs mit `-` werden akzeptiert.
8. Expressions mit Semikolons in Strings funktionieren.
9. Symbolische Enum-Map-Referenzen funktionieren oder werden sauber normalisiert.
10. Joins referenzieren nur deklarierte Sources.
11. Ref-Kurzform wird v2.0 abgelehnt.
12. Defaults/Null-Semantik ist dokumentiert und getestet.
13. `maxItems` ist Validierung oder wird explizit als unsupported diagnostiziert.
14. Formatter ist stabil.
15. Converter verliert keine Mapping-Felder still.
16. CLI akzeptiert `.ilimap` dort, wo Mapping-Dateien akzeptiert werden.
17. Doku und Feature-Matrix sind aktuell.
18. LSP/IDE-Komponenten verwenden denselben Parser, keinen zweiten.
19. DM01/DMAV-Sonderlogik bleibt ausserhalb generischer Engine-Packages.
20. Tests wurden wirklich ausgeführt oder Blocker wurden ehrlich dokumentiert.

---

## 7. Abschlussbericht-Vorlage pro Phase

Der Agent muss am Ende jeder Phase exakt dieses Format verwenden:

```markdown
## Abschlussbericht Phase PX

### Geänderte Dateien
- ...

### Implementiertes Artefakt
- ...

### Ausgeführte Tests / Befehle
- `...`: passed/failed/skipped mit Begründung

### Ergebnis
- ...

### Nicht geprüfte Risiken
- ...

### Abweichungen vom Plan
- ...

### Commit
- Commit erstellt: ja/nein
- Commit-Message, falls ja:
  ```text
  ...
  ```
```

Wenn Tests nicht ausgeführt werden konnten, darf der Agent nicht schreiben „alles grün“. Er muss den Blocker benennen, z. B. fehlender Gradle Wrapper, fehlende JDK-Version, DNS/Download-Problem, fehlende Echtdaten oder fehlende lokale `ili2c`/`ilivalidator`-Installation.

---

## 8. Empfohlene Commit-Messages

Format gemäss `done-and-commit`-Skill:

```text
ilimap: add lexer and expression reader

Why:
- Prepare ilimap v2 parsing with robust statement expression boundaries.

What:
- Add token model, lexer, source positions and expression reader.
- Cover strings, comments, hash literals and semicolon handling.

Verification:
- ./gradlew test --tests "...IlimapLexerTest": passed
- ./gradlew test --tests "...IlimapExpressionReaderTest": passed
- ./gradlew test: passed
```

Weitere Vorschläge:

```text
ilimap: parse minimal mapping profiles
ilimap: validate symbols and hardened v2 semantics
ilimap: map parsed profiles to JobConfig
ilimap: support full v2 rule structure
ilimap: add stable formatter
ilimap: add yaml to ilimap converter
ilimap: enable ilimap mappings in cli
ilimap: document v2 dsl and examples
ilimap: add language service foundation
```

---

## 9. Reihenfolge in Kurzform

```text
P0  Analyse und ADR
P1  Lexer + ExpressionReader
P2  Minimal-AST + Minimal-Parser
P3  Semantic Validator + SymbolTable + sieben Härtungen
P4  IlimapToJobConfigMapper + MappingLoader-Integration
P5  Full Rule Structure: Bags, Refs, Create, Loss, Metadata
P6  Formatter
P7  YAML -> ILIMAP Converter
P8  End-to-End CLI/Transformation
P9  Doku, Beispiele, Feature-Matrix
P10 LSP-/IDE-MVP
P11 DM01/DMAV-Pilotprofil
```

Die Phasen P10 und P11 können bei Bedarf nach hinten geschoben werden. P1 bis P8 bilden den Kern, damit `.ilimap` produktiv als Autorenformat nutzbar wird.
