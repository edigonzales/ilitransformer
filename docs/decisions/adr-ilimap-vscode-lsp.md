# ADR: ILIMAP VS Code LSP-Architektur

**Status:** accepted  
**Datum:** 2026-06-20  
**Betrifft:** `.ilimap`-IDE-Integration, LSP4J, VS Code, Gradle-Build

## Kontext

Das Repository besitzt bereits einen vollständigen `.ilimap`-Verarbeitungspfad im bestehenden Java-Projekt:

```text
IlimapLoader
  -> IlimapParser
  -> IlimapDocument AST mit IlimapSourceRange
  -> IlimapSemanticValidator
  -> IlimapSymbolTable
  -> IlimapToJobConfigMapper
  -> IlimapFormatter
```

Die aktuelle `.ilimap`-Implementierung ist bereits durch Parser-, Fehlerpfad-, Symboltable-, Semantic-Validator- und Loader-Tests abgesichert. Für VSC-0 wird noch kein LSP-Code implementiert; die Phase dient nur dazu, Architekturgrenzen und MVP-Entscheidungen zu fixieren.

Der Build ist aktuell bewusst einfach gehalten:

- Single-Project-Gradle-Build
- Java-Toolchain 21
- keine produktiven LSP4J-Klassen unter `src/main/java`
- keine produktive VS-Code-Extension im Repository

Die aktuelle Verification-Gruppe im Build enthält zurzeit:

- `check`
- `integrationTest`
- `realDataTest`
- `spotlessCheck`
- `test`
- `validateFeatureMatrix`
- `validateTransfer`

Die bestehende allgemeine Diagnostic-Struktur ist für CLI-, Report- und Compiler-Zwecke geeignet, aber für LSP noch zu grob. `guru.interlis.transformer.diag.Diagnostic` enthält aktuell nur:

- `code`
- `severity`
- `message`
- `sourcePath`
- `suggestion`

Genaue Positionsdaten liegen heute stattdessen in Lexer-, AST- und Parse-Fehlerstrukturen wie `IlimapSourcePosition`, `IlimapSourceRange` und `IlimapParser.ParseException`.

## Entscheidung

### 1. LSP-Stack und Schichtung

Die VS-Code-Unterstützung für `.ilimap` wird mit einer klar getrennten Vier-Schichten-Architektur umgesetzt:

```text
existing ilimap core
  -> ilimap ide
  -> ilimap lsp
  -> vscode extension
```

- Der bestehende `.ilimap`-Core bleibt der einzige Ort für Sprache, Parsing, AST, Symbolauflösung, Semantik und Formatierung.
- Darüber wird eine editorunabhängige IDE-Schicht unter `guru.interlis.transformer.mapping.ilimap.ide` aufgebaut.
- Darüber liegt eine LSP-Schicht, die nur Protokolladapter und Transportlogik enthält.
- Die VS-Code-Extension bleibt ein dünner TypeScript-Client ohne `.ilimap`-Semantik.

### 2. LSP4J als Protokolladapter

- Der Java Language Server wird mit LSP4J umgesetzt.
- LSP4J dient nur als Protokoll- und Datenadapter für Language Server Protocol und `stdio`.
- Parser-, Formatter-, Semantic- oder Symbol-Logik wird nicht in das LSP-Package verschoben oder dort dupliziert.
- Eine konkrete LSP4J-Version wird in VSC-0 noch nicht im Build festgelegt; das erfolgt erst in der Implementierungsphase mit gepinnter Version und dokumentierter Begründung.

### 3. Kein zweiter Parser und kein zweiter Analysepfad

- Für LSP und IDE wird kein zweiter `.ilimap`-Parser gebaut.
- Die späteren IDE- und LSP-Phasen müssen den bestehenden `IlimapParser`, `IlimapSemanticValidator`, `IlimapSymbolTable` und `IlimapFormatter` wiederverwenden.
- Es wird kein paralleler Analysepfad eingeführt, der Syntax, Semantik oder Formatting unabhängig vom bestehenden `.ilimap`-Core neu implementiert.

### 4. Diagnostic-Strategie für Editor-Integrationen

- Die bestehende `guru.interlis.transformer.diag.Diagnostic`-Pipeline bleibt für CLI-, Loader-, Compiler- und Report-Zwecke bestehen.
- Für Editor-Integrationen darf später ein `IlimapIdeDiagnostic` eingeführt werden, das genaue Ranges für IDE- und LSP-Zwecke trägt.
- Dieses `IlimapIdeDiagnostic` ist eine editorseitige Projektion auf Basis der vorhandenen Parser-, AST- und Semantic-Ergebnisse.
- `IlimapIdeDiagnostic` ist ausdrücklich kein zweites allgemeines Compiler-Diagnostic-System und ersetzt die bestehende `Diagnostic`-Struktur nicht.

### 5. MVP-Grenzen

Für den ersten VS-Code-MVP gelten folgende verbindliche Grenzen:

- Full Document Sync wird verwendet.
- Webview ist nicht Teil des ersten MVP.
- Ein möglicher späterer Webview-Einsatz ist eine getrennte, nachgelagerte Erweiterung.
- Die VS-Code-Extension implementiert keine `.ilimap`-Semantik selbst.

### 6. Build- und Modulgrenzen

- Das Repository bleibt für den MVP vorerst ein Single-Project-Gradle-Build.
- VSC-0 verändert den Build nicht.
- Multi-Module-Refactoring ist ausdrücklich nicht Teil des MVP.
- Eine spätere Aufteilung in separate Module für Core, LSP und VS-Code kann erst nach stabilem MVP separat bewertet werden.

## Konsequenzen

- Spätere IDE- und LSP-Arbeit muss auf dem vorhandenen `.ilimap`-Pfad aufbauen, nicht daneben.
- Die editorunabhängige `ide`-Schicht wird die zentrale Wiederverwendungsstelle für Diagnostics, Formatting, Outline, Folding, Completion, Hover und Definition.
- Die LSP-Schicht bleibt klein und transportorientiert, weil sie nur IDE-Daten in LSP-Objekte übersetzt.
- Die VS-Code-Extension bleibt austauschbar, weil die eigentliche Editorlogik nicht im Client lebt.
- Range-genaue Editor-Diagnostics werden später über ein separates `IlimapIdeDiagnostic` möglich, ohne die bestehende CLI-/Compiler-Diagnostic-Pipeline zu verdrängen.
- Die bestehende `.ilimap`-Testabdeckung bleibt der Nachweis für den vorhandenen Parser-, Loader- und Semantic-Pfad; VSC-0 benötigt deshalb keine neuen Tests.
- VSC-0 ändert keine öffentliche API, keine DSL-Semantik, kein Transferformat und keine Laufzeitlogik.

## Referenzen

- `docs/ilimap-vscode-lsp4j-implementation-plan.md` — Umsetzungsplan für VS Code und LSP4J
- `docs/decisions/adr-ilimap-v2-loader-architecture.md` — bestehende `.ilimap`-Loader-Architektur
- `build.gradle` — aktueller Single-Project-Build und Verification-Tasks
- `AGENTS.md` — nicht verhandelbare Projektregeln
- `docs/agent/DECISIONS.md` — bestehende Architekturentscheidungen
