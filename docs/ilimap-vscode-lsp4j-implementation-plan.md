# ilimap VS Code Plugin mit LSP4J — detaillierter Umsetzungsplan für einen LLM-Coding-Agenten

**Status:** Spezifikation / Realisierungsplan  
**Ziel:** Schrittweise Umsetzung eines robusten VS-Code-Plugins für `.ilimap` mit einem Java Language Server auf Basis von **LSP4J**.  
**Repository-Kontext:** `ilinexus` / `ilitransformer`, Single-Gradle-Projekt, Java 21, bestehende `.ilimap`-Implementierung vorhanden.  
**Primäres Prinzip:** Der LSP implementiert **keinen zweiten Parser**. Er verwendet die vorhandenen `.ilimap`-Komponenten wieder.

```text
.ilimap text
  -> IlimapParser
  -> IlimapDocument AST
  -> IlimapSemanticValidator
  -> IlimapSymbolTable
  -> IlimapFormatter
  -> IlimapAnalysisService
  -> LSP4J Adapter
  -> VS Code Extension
```

---

## 1. Kurzfazit für die Architekturentscheidung

Für dieses Repository soll **nicht** auf Xtext/Ecore umgestellt werden, nur um ein VS-Code-Plugin zu bauen. Es gibt bereits eine eigene `.ilimap`-Toolchain:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/
  ast/
  lexer/
  parser/
  semantic/
  format/
  jobconfig/
  convert/
```

Daher ist der richtige nächste Schritt:

```text
bestehender ilimap-core
  + editorunabhängige IDE-Service-Schicht
  + LSP4J-Protokolladapter
  + dünne VS-Code-Extension
```

Nicht:

```text
neue Xtext-Grammatik
neues EMF-Modell
zweiter Parser
zweiter Formatter
zweite Symbolauflösung
```

Sirius Web bleibt später möglich, soll aber die VS-Code-MVP-Architektur nicht diktieren. Der wiederverwendbare Kern für spätere Sirius-Web- oder Webview-Arbeiten ist die geplante `ide`-Schicht.

---

## 2. Verbindliche Ausgangsregeln für den Coding-Agenten

### 2.1 Vor jeder Phase zwingend lesen

Der Agent muss am Anfang **jeder Phase** folgende Dateien lesen:

```text
AGENTS.md
docs/agent/DEFINITION_OF_DONE.md
docs/agent/COMMIT_POLICY.md
docs/agent/DECISIONS.md
.skills/java-test-gap/SKILL.md
.skills/gradle-verification/SKILL.md
.skills/done-and-commit/SKILL.md
```

Da die Arbeit `.ilimap` betrifft, zusätzlich immer:

```text
.skills/mapping-dsl-change/SKILL.md
```

Falls in einer Phase INTERLIS-Modelle, Testdaten, XTF/ITF/XML, DM01/DMAV oder reale Daten berührt werden, zusätzlich:

```text
.skills/interlis-validation/SKILL.md
.skills/dm01-dmav-real-data-gate/SKILL.md
.skills/interlis1-testdata/SKILL.md
```

### 2.2 Nicht verhandelbare Regeln

Der Agent darf:

- keinen zweiten `.ilimap`-Parser für den LSP bauen;
- keine `.ilimap`-Semantik im VS-Code-Client implementieren;
- keine nicht implementierten DSL-Felder still ignorieren;
- keine Runtime-Semantik ändern;
- keine DM01/DMAV-Sonderlogik in generische LSP-/IDE-Packages einbauen;
- keine opportunistischen Refactorings durchführen;
- Tests nicht als bestanden melden, wenn sie nicht exakt ausgeführt wurden;
- nicht committen, bevor Definition of Done und Commit Policy erfüllt sind.

Der Agent muss:

- bestehende Parser-/Semantic-/Formatter-Komponenten wiederverwenden;
- jede Phase mit einem ausführbaren, testbaren Artefakt abschliessen;
- Tests für positive und negative Fälle ergänzen;
- bei Produktionscode mindestens `./gradlew test` ausführen;
- bei Build-/Gradle-Änderungen passende Gradle-Tasks verifizieren;
- am Ende jeder Phase geänderte Dateien, ausgeführte Befehle, Ergebnisse, Risiken und Commit-Status melden.

---

## 3. Ist-Zustand des Repositories

Aus dem hochgeladenen Repository ergibt sich folgender relevanter Stand:

### 3.1 Build

```text
rootProject.name = 'ilitransformer'
```

Das Projekt ist aktuell ein **Single-Gradle-Projekt** mit:

```text
plugins:
  application
  java
  com.diffplug.spotless

Java:
  toolchain Java 21

mainClass:
  guru.interlis.transformer.app.CliMain
```

Relevante Tasks:

```bash
./gradlew test
./gradlew integrationTest
./gradlew realDataTest
./gradlew validateFeatureMatrix
./gradlew spotlessCheck
./gradlew check
```

`check` hängt bereits ab von:

```text
integrationTest
validateFeatureMatrix
spotlessCheck
```

### 3.2 Vorhandene `.ilimap`-Klassen

Die `.ilimap`-Toolchain existiert bereits:

```text
guru.interlis.transformer.mapping.ilimap.IlimapLoader
guru.interlis.transformer.mapping.ilimap.IlimapLoadResult
guru.interlis.transformer.mapping.ilimap.IlimapLoadException

guru.interlis.transformer.mapping.ilimap.lexer.IlimapLexer
guru.interlis.transformer.mapping.ilimap.lexer.IlimapToken
guru.interlis.transformer.mapping.ilimap.lexer.IlimapTokenType
guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition
guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange

guru.interlis.transformer.mapping.ilimap.parser.IlimapParser
guru.interlis.transformer.mapping.ilimap.parser.IlimapExpressionReader

guru.interlis.transformer.mapping.ilimap.ast.*
guru.interlis.transformer.mapping.ilimap.semantic.*
guru.interlis.transformer.mapping.ilimap.format.*
guru.interlis.transformer.mapping.ilimap.jobconfig.*
guru.interlis.transformer.mapping.ilimap.convert.*
```

Wichtige vorhandene APIs:

```java
new IlimapParser(source).parseDocument();
new IlimapSemanticValidator().validate(document);
new IlimapFormatter().format(document);
new IlimapLoader().loadDetailed(source, baseDirectory);
```

### 3.3 Vorhandenes Diagnostic-Modell

Aktuell gibt es:

```java
package guru.interlis.transformer.diag;

public record Diagnostic(
    String code,
    Severity severity,
    String message,
    String sourcePath,
    String suggestion
) {}
```

Dieses Modell ist gut für CLI/Reports, aber für LSP zu grob, weil LSP genaue `Range`-Objekte benötigt. Deshalb wird in diesem Plan ein separates IDE-Diagnostic-Modell eingeführt, ohne das bestehende globale Diagnostic-Modell sofort zu verändern.

---

## 4. Zielarchitektur

### 4.1 Paketstruktur im bestehenden Single-Project-Build

Kurzfristig bleibt das Repo ein Single-Gradle-Projekt. Ergänzt werden diese Packages:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/ide/
src/main/java/guru/interlis/transformer/mapping/ilimap/lsp/
```

Die VS-Code-Extension liegt separat unter:

```text
vscode/ilimap-vscode/
```

### 4.2 Schichten

```text
┌──────────────────────────────────────────────────────────────┐
│ VS Code Extension                                             │
│ TypeScript, package.json, TextMate grammar, commands          │
└───────────────────────▲──────────────────────────────────────┘
                        │ LSP over stdio
┌───────────────────────┴──────────────────────────────────────┐
│ ilimap/lsp                                                    │
│ LSP4J adapter: LanguageServer, TextDocumentService            │
└───────────────────────▲──────────────────────────────────────┘
                        │ Java method calls
┌───────────────────────┴──────────────────────────────────────┐
│ ilimap/ide                                                    │
│ Analysis, diagnostics, completion, definition, hover, symbols │
└───────────────────────▲──────────────────────────────────────┘
                        │ Java method calls
┌───────────────────────┴──────────────────────────────────────┐
│ existing ilimap core                                          │
│ parser, AST, semantic validator, symbol table, formatter      │
└──────────────────────────────────────────────────────────────┘
```

### 4.3 Warum eine `ide`-Schicht?

Die `lsp`-Schicht darf nicht zu viel wissen. Sie soll nur LSP-Protokollobjekte mappen. Die `ide`-Schicht ist editorunabhängig und später wiederverwendbar für:

```text
- VS-Code-LSP
- VS-Code-Webview
- Sirius-Web-Backend
- CLI-Reports
- HTML-Dokumentation
- Test-/Preview-Funktionen
```

---

## 5. Nicht-Ziele für den ersten VS-Code-MVP

Nicht in den ersten Phasen implementieren:

```text
- kein Xtext/Ecore
- keine Sirius-Web-Integration
- keine editierbare Webview
- keine vollständige INTERLIS-Modellcompletion
- keine inkrementelle Parserarchitektur
- keine persistente Workspace-Datenbank
- kein Multi-Module-Gradle-Refactoring
- keine neue Runtime-Semantik
```

Die ersten Versionen sollen bewusst klein sein:

```text
Diagnostics -> Formatting -> Outline/Folding -> Completion -> Hover/Definition
```

---

## 6. Externe technische Referenzen

Diese Quellen sind bei der Umsetzung zu berücksichtigen:

- VS Code Language Server Extension Guide: https://code.visualstudio.com/api/language-extensions/language-server-extension-guide
- VS Code Webview API und UX Guidelines: https://code.visualstudio.com/api/ux-guidelines/webviews
- VS Code Webview API Guide: https://code.visualstudio.com/api/extension-guides/webview
- Language Server Protocol Specification: https://microsoft.github.io/language-server-protocol/
- LSP4J GitHub: https://github.com/eclipse-lsp4j/lsp4j
- LSP4J Maven Coordinates: `org.eclipse.lsp4j:org.eclipse.lsp4j`
- Referenzprojekt für Entwicklungsworkflow und Packaging: https://github.com/edigonzales/interlis-lsp
  - Java-LSP als Shadow/Fat-JAR
  - VS-Code-Client in `client/`
  - Dev-Task `copyDevServerJar` kopiert den aktuellen Server-JAR in `client/server/`
  - Extension Development Host nutzt eine `preLaunchTask`, die Client und Server vorbereitet
  - `vscode-languageclient` startet den Server über `java -jar ...` und kommuniziert über stdio

Stand dieser Spezifikation: Die aktuelle LSP4J-Version muss beim Implementieren in Maven Central geprüft werden. Nach öffentlicher Maven-Indexierung war `org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0` am 10. Februar 2026 verfügbar. Falls es im Repository Kompatibilitätsgründe gibt, darf der Agent eine ältere Version wählen, muss dies aber im ADR begründen.

---

## 7. Ziel-Feature-Reihenfolge

| Version | Ziel | Artefakt | Hauptnutzen |
|---|---|---|---|
| VSC-0 | ADR und technische Grenzen | Dokumentation | Architektur abgesichert |
| VSC-1 | IDE-Service ohne LSP | Java-API + Tests | Wiederverwendbare Analysebasis |
| VSC-2 | LSP4J-Minimalserver | Java-LSP über stdio | Diagnostics im Editor möglich |
| VSC-3 | VS-Code-Extension-Skeleton | installierbare/dev Extension | `.ilimap` wird erkannt |
| VSC-3.5 | Developer Experience: Reloadability | Watch-/Launch-/Restart-Workflow | schnelles Entwickeln ohne kompletten VS-Code-Neustart |
| VSC-4 | Formatting | LSP formatting | stabiles Autorenformat |
| VSC-5 | Document Symbols + Folding | Outline/Folding | Navigation in grossen Profilen |
| VSC-6 | Completion MVP | Kontextcompletion | produktiver Editor |
| VSC-7 | Hover + Go-to-definition | Navigation | Symbolverständnis |
| VSC-8 | Code Actions | Quick Fixes | kleine Automatisierungen |
| VSC-9 | INTERLIS-Integration | model-aware IDE | grosser Fachnutzen |
| VSC-10 | Read-only Webview | Mapping Overview | visuelle Zusatzsicht |

---

# Phase VSC-0 — ADR und Umsetzungsrahmen fixieren

## Ziel

Vor Codeänderungen wird eine Architekturentscheidung dokumentiert. Noch kein LSP-Code.

## Zu lesende Dateien

```text
AGENTS.md
docs/agent/DEFINITION_OF_DONE.md
docs/agent/COMMIT_POLICY.md
docs/agent/DECISIONS.md
.skills/java-test-gap/SKILL.md
.skills/gradle-verification/SKILL.md
.skills/done-and-commit/SKILL.md
.skills/mapping-dsl-change/SKILL.md
build.gradle
settings.gradle
docs/ilimap-v2.md
docs/ilimap-v2-llm-coding-agent-implementation-plan-mit-grammatik.md
src/main/java/guru/interlis/transformer/mapping/ilimap/IlimapLoader.java
src/main/java/guru/interlis/transformer/mapping/ilimap/parser/IlimapParser.java
src/main/java/guru/interlis/transformer/mapping/ilimap/semantic/IlimapSemanticValidator.java
src/main/java/guru/interlis/transformer/mapping/ilimap/format/IlimapFormatter.java
```

## Aufgaben

1. Prüfe, dass `.ilimap`-Parser, Semantic Validator, SymbolTable und Formatter existieren.
2. Prüfe, ob `Diagnostic` bereits Ranges unterstützt. Falls nicht, ADR hält fest: LSP verwendet eigenes `IlimapIdeDiagnostic`.
3. Prüfe aktuellen Gradle-Build und entscheide: kurzfristig Single Project behalten.
4. Lege ADR an:

```text
docs/decisions/adr-ilimap-vscode-lsp.md
```

## Inhalt des ADR

Das ADR muss festhalten:

```text
- VS-Code-Plugin wird mit LSP4J umgesetzt.
- Es wird kein zweiter Parser gebaut.
- LSP4J ist nur Protokolladapter.
- Editorlogik liegt in guru.interlis.transformer.mapping.ilimap.ide.
- VS-Code-Extension ist ein dünner TypeScript-Client.
- Im MVP wird Full Document Sync verwendet.
- Webview kommt später und initial nur read-only.
- Single-Gradle-Projekt bleibt vorerst erhalten.
- Multi-Module-Refactoring ist ausdrücklich nicht Teil des MVP.
```

## Verifikation

```bash
./gradlew tasks --group verification
./gradlew test
```

Falls das Repository lokal bereits langsam ist, darf zusätzlich notiert werden, welche Tests langsam sind. Tests dürfen aber nicht als bestanden behauptet werden, wenn sie nicht gelaufen sind.

## Funktionsfähiges Artefakt

```text
docs/decisions/adr-ilimap-vscode-lsp.md
```

## Definition of Done

- ADR existiert.
- Aktueller Build-/Testzustand ist dokumentiert.
- Keine Produktionssemantik geändert.
- Abschlussbericht enthält exakte Befehle und Ergebnisse.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Lies zuerst docs/ilimap-vscode-lsp4j-implementation-plan.md, AGENTS.md, docs/agent/DEFINITION_OF_DONE.md, docs/agent/COMMIT_POLICY.md und docs/agent/DECISIONS.md.

Verwende zusätzlich:
- .skills/java-test-gap/SKILL.md
- .skills/gradle-verification/SKILL.md
- .skills/done-and-commit/SKILL.md
- .skills/mapping-dsl-change/SKILL.md

Aufgabe Phase VSC-0:
Analysiere den bestehenden .ilimap-Codepfad, den Gradle-Build und die vorhandene Diagnostic-Struktur. Implementiere noch keinen LSP-Code. Lege docs/decisions/adr-ilimap-vscode-lsp.md an. Das ADR muss festhalten, dass der LSP mit LSP4J gebaut wird, keinen zweiten Parser enthält, eine editorunabhängige ide-Schicht verwendet und die VS-Code-Extension nur ein dünner Client ist. Full Document Sync ist für den MVP gesetzt. Webview ist nicht Teil des ersten MVP.

Führe ./gradlew tasks --group verification und ./gradlew test aus. Melde exakt, welche Befehle ausgeführt wurden und ob sie bestanden haben.
```

---

# Phase VSC-1 — Editorunabhängige IDE-Service-Schicht

## Ziel

Eine Java-API analysiert `.ilimap`-Text unabhängig von LSP und VS Code. Sie liefert AST, SymbolTable, Diagnostics und Position Mapping.

Diese Phase erzeugt noch keinen Language Server, aber ein voll testbares Artefakt.

## Neue Packages

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/ide/
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/
```

## Neue Klassen und Records

### `IlimapAnalysisOptions`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapAnalysisOptions.java
```

Vorschlag:

```java
package guru.interlis.transformer.mapping.ilimap.ide;

import java.nio.file.Path;

public record IlimapAnalysisOptions(
        Path baseDirectory,
        boolean includeSemanticDiagnostics,
        boolean includeExpressionDiagnostics,
        boolean includeModelDiagnostics
) {
    public static IlimapAnalysisOptions defaults(Path baseDirectory) {
        return new IlimapAnalysisOptions(baseDirectory, true, false, false);
    }
}
```

Hinweis: `includeExpressionDiagnostics` und `includeModelDiagnostics` dürfen im MVP intern noch ignoriert werden, müssen dann aber dokumentiert sein. Nicht behaupten, dass Modellvalidierung bereits funktioniert.

### `IlimapDocumentSnapshot`

```java
package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapDocumentSnapshot(
        String uri,
        String text,
        int version
) {}
```

### `IlimapLineMap`

Pfad:

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapLineMap.java
```

Aufgabe: Zentrale Abbildung zwischen Offsets, internen 1-basierten Positionen und LSP-0-basierten Positionen.

Methoden:

```java
public final class IlimapLineMap {
    public IlimapLineMap(String text);

    public int lineCount();

    public int offsetToZeroBasedLine(int offset);
    public int offsetToZeroBasedCharacter(int offset);

    public int positionToOffset(int zeroBasedLine, int zeroBasedCharacter);

    public IlimapIdePosition toIdePosition(int offset);
    public IlimapIdeRange toIdeRange(IlimapSourceRange sourceRange);
}
```

Wichtig:

- `IlimapSourcePosition.line()` und `.column()` sind 1-basiert.
- LSP ist 0-basiert.
- Die Umrechnung muss nur an einer Stelle passieren.
- EOF-Positionen, leere Dokumente und letzte Zeile ohne Newline müssen getestet werden.

### `IlimapIdePosition`

```java
public record IlimapIdePosition(int line, int character) {}
```

`line` und `character` sind 0-basiert, LSP-kompatibel.

### `IlimapIdeRange`

```java
public record IlimapIdeRange(IlimapIdePosition start, IlimapIdePosition end) {}
```

### `IlimapIdeSeverity`

```java
public enum IlimapIdeSeverity {
    ERROR,
    WARNING,
    INFORMATION,
    HINT
}
```

### `IlimapIdeDiagnostic`

```java
public record IlimapIdeDiagnostic(
        String code,
        IlimapIdeSeverity severity,
        String message,
        IlimapIdeRange range,
        String suggestion
) {}
```

### `IlimapAnalysis`

```java
public record IlimapAnalysis(
        String uri,
        String text,
        IlimapDocument document,
        IlimapSymbolTable symbols,
        List<IlimapIdeDiagnostic> diagnostics,
        IlimapLineMap lineMap
) {
    public boolean hasDocument() {
        return document != null;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == IlimapIdeSeverity.ERROR);
    }
}
```

### `IlimapAnalysisService`

```java
public final class IlimapAnalysisService {
    public IlimapAnalysis analyze(String uri, String text, IlimapAnalysisOptions options) {
        // 1. create lineMap
        // 2. parse with IlimapParser
        // 3. on parse error: return analysis with null document and syntax diagnostic
        // 4. validate with IlimapSemanticValidator
        // 5. map Diagnostics to IlimapIdeDiagnostic
        // 6. return IlimapAnalysis
    }
}
```

### `IlimapDiagnosticMapper`

```java
public final class IlimapDiagnosticMapper {
    public List<IlimapIdeDiagnostic> map(
            List<Diagnostic> diagnostics,
            IlimapLineMap lineMap,
            IlimapIdeRange fallbackRange
    );

    public IlimapIdeDiagnostic map(
            Diagnostic diagnostic,
            IlimapLineMap lineMap,
            IlimapIdeRange fallbackRange
    );
}
```

Problem: Das bestehende `Diagnostic.sourcePath()` enthält teilweise Text wie `line 3, column 4` oder `3:4`, aber kein echtes Range-Objekt. Für den MVP gilt:

1. Wenn ein Range aus AST/Exception verfügbar ist, diesen verwenden.
2. Wenn nur `sourcePath` mit line/column verfügbar ist, daraus eine 1-Zeichen-Range ableiten.
3. Wenn keine Position verfügbar ist, `fallbackRange` verwenden, typischerweise Dokumentanfang.

### `IlimapFormattingService`

```java
public final class IlimapFormattingService {
    public Optional<IlimapTextEdit> format(String uri, String text, IlimapFormatOptions options);
}
```

### `IlimapTextEdit`

```java
public record IlimapTextEdit(IlimapIdeRange range, String newText) {}
```

Für komplettes Dokumentformatieren ersetzt die Range das ganze Dokument.

## Tests

Neue Testklassen:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapLineMapTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapAnalysisServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapDiagnosticMapperTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapFormattingServiceTest.java
```

Testfälle `IlimapLineMapTest`:

```text
- mapsOffsetToZeroBasedPosition
- mapsInternalOneBasedSourceRangeToZeroBasedIdeRange
- mapsPositionToOffset
- handlesEmptyDocument
- handlesDocumentWithoutFinalNewline
- handlesLastLine
```

Testfälle `IlimapAnalysisServiceTest`:

```text
- analyzesValidMinimalIlimapWithoutErrors
- returnsSyntaxDiagnosticForMissingSemicolon
- returnsSemanticDiagnosticForUnknownInput
- returnsNullDocumentOnParserError
- returnsDocumentAndSymbolsOnValidFile
```

Testfälle `IlimapFormattingServiceTest`:

```text
- formatsValidDocumentWithFullDocumentEdit
- doesNotFormatInvalidDocument
- preservesFormatterOutputFromIlimapFormatter
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapLineMapTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisServiceTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingServiceTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Eine reine Java-API, die `.ilimap`-Text analysieren und formatieren kann. Noch kein LSP und noch keine VS-Code-Extension.

## Definition of Done

- `IlimapAnalysisService` verwendet vorhandenen Parser und Validator.
- Keine Parserlogik in der neuen IDE-Schicht.
- Diagnostics haben IDE-Ranges.
- Formatierung verwendet vorhandenen `IlimapFormatter`.
- Unit-Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Lies zuerst AGENTS.md, docs/agent/DEFINITION_OF_DONE.md, docs/agent/COMMIT_POLICY.md und docs/agent/DECISIONS.md.

Verwende zusätzlich:
- .skills/java-test-gap/SKILL.md
- .skills/gradle-verification/SKILL.md
- .skills/done-and-commit/SKILL.md
- .skills/mapping-dsl-change/SKILL.md

Aufgabe Phase VSC-1:
Implementiere eine editorunabhängige IDE-Service-Schicht unter guru.interlis.transformer.mapping.ilimap.ide. Baue IlimapAnalysisService, IlimapAnalysisOptions, IlimapAnalysis, IlimapLineMap, IlimapIdeDiagnostic, IlimapIdeRange, IlimapIdePosition, IlimapFormattingService und die nötigen Mapper. Verwende ausschliesslich den bestehenden IlimapParser, IlimapSemanticValidator, IlimapSymbolTable und IlimapFormatter. Implementiere keinen zweiten Parser.

Erstelle die genannten Unit-Tests. Führe die fokussierten Tests und danach ./gradlew test aus. Melde geänderte Dateien, exakte Befehle, Ergebnisse und Risiken.
```

---

# Phase VSC-2 — LSP4J-Minimalserver mit Diagnostics

## Ziel

Ein Java-Language-Server läuft über stdio und verarbeitet `didOpen`, `didChange`, `didClose`. Er publiziert Diagnostics an den Client.

## Gradle-Anpassung

In `build.gradle` ergänzen:

```groovy
implementation 'org.eclipse.lsp4j:org.eclipse.lsp4j:<version>'
```

Empfohlen: aktuelle Version in Maven Central prüfen. Falls `1.0.0` verfügbar und kompatibel ist:

```groovy
implementation 'org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0'
```

Gradle-Task ergänzen:

```groovy
tasks.register('runIlimapLanguageServer', JavaExec) {
    group = 'application'
    description = 'Runs the ILIMAP language server over stdio'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'guru.interlis.transformer.mapping.ilimap.lsp.IlimapLanguageServerMain'
}
```

Optional später eigener Server-JAR-Task. In dieser Phase reicht `JavaExec`.

## Neue Packages

```text
src/main/java/guru/interlis/transformer/mapping/ilimap/lsp/
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/
```

## Neue Klassen

### `IlimapLanguageServerMain`

```java
public final class IlimapLanguageServerMain {
    public static void main(String[] args) throws Exception {
        IlimapLanguageServer server = new IlimapLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
                server,
                System.in,
                System.out
        );
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
```

Wichtig: Logging nicht auf `System.out`, weil stdio für LSP-Protokoll verwendet wird. Für Logs `System.err` oder LSP `window/logMessage` nutzen.

### `IlimapLanguageServer`

```java
public final class IlimapLanguageServer implements LanguageServer, LanguageClientAware {
    private final IlimapTextDocumentService textDocumentService;
    private final IlimapWorkspaceService workspaceService;
    private LanguageClient client;
    private int shutdown;

    public CompletableFuture<InitializeResult> initialize(InitializeParams params);
    public CompletableFuture<Object> shutdown();
    public void exit();
    public TextDocumentService getTextDocumentService();
    public WorkspaceService getWorkspaceService();
    public void connect(LanguageClient client);
}
```

Capabilities im MVP:

```text
textDocumentSync = FULL
completionProvider = noch false oder nicht gesetzt
hoverProvider = false
definitionProvider = false
documentFormattingProvider = später in VSC-4
```

### `IlimapTextDocumentService`

```java
public final class IlimapTextDocumentService implements TextDocumentService {
    private final IlimapDocumentStore documentStore;
    private LanguageClient client;

    public void didOpen(DidOpenTextDocumentParams params);
    public void didChange(DidChangeTextDocumentParams params);
    public void didClose(DidCloseTextDocumentParams params);
    public void didSave(DidSaveTextDocumentParams params);

    private void analyzeAndPublish(String uri);
}
```

MVP-Verhalten:

- `didOpen`: Text speichern, analysieren, Diagnostics publizieren.
- `didChange`: Full Sync erwarten, Text ersetzen, analysieren, Diagnostics publizieren.
- `didClose`: Dokument entfernen, leere Diagnostics publizieren.

### `IlimapWorkspaceService`

```java
public final class IlimapWorkspaceService implements WorkspaceService {
    public void didChangeConfiguration(DidChangeConfigurationParams params);
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params);
}
```

Im MVP dürfen diese Methoden no-op sein, müssen aber vorhanden sein.

### `IlimapDocumentStore`

```java
public final class IlimapDocumentStore {
    public void open(String uri, String text, int version);
    public void updateFull(String uri, String text, int version);
    public void close(String uri);
    public Optional<IlimapDocumentSnapshot> get(String uri);
    public IlimapAnalysis analyze(String uri, IlimapAnalysisOptions options);
}
```

### `IlimapLspRangeMapper`

```java
public final class IlimapLspRangeMapper {
    public Position toLspPosition(IlimapIdePosition position);
    public Range toLspRange(IlimapIdeRange range);
}
```

### `IlimapLspDiagnosticMapper`

```java
public final class IlimapLspDiagnosticMapper {
    public List<org.eclipse.lsp4j.Diagnostic> map(List<IlimapIdeDiagnostic> diagnostics);
    public org.eclipse.lsp4j.Diagnostic map(IlimapIdeDiagnostic diagnostic);
}
```

Severity Mapping:

```text
IlimapIdeSeverity.ERROR       -> DiagnosticSeverity.Error
IlimapIdeSeverity.WARNING     -> DiagnosticSeverity.Warning
IlimapIdeSeverity.INFORMATION -> DiagnosticSeverity.Information
IlimapIdeSeverity.HINT        -> DiagnosticSeverity.Hint
```

## Tests

Neue Testklassen:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapDocumentStoreTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapLspRangeMapperTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapLspDiagnosticMapperTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapTextDocumentServiceTest.java
```

Testfälle:

```text
IlimapDocumentStoreTest
- openStoresDocument
- updateFullReplacesText
- closeRemovesDocument
- analyzeUsesStoredDocument

IlimapLspDiagnosticMapperTest
- mapsErrorSeverity
- mapsWarningSeverity
- mapsCodeAndMessage
- mapsRangeToZeroBasedLspRange

IlimapTextDocumentServiceTest
- didOpenPublishesDiagnostics
- didChangePublishesUpdatedDiagnostics
- didCloseClearsDiagnostics
```

Für `IlimapTextDocumentServiceTest` kann ein Fake `LanguageClient` verwendet werden, der publizierte Diagnostics sammelt.

## Manuelle Verifikation

```bash
./gradlew runIlimapLanguageServer
```

Der Prozess wartet dann auf LSP-stdio und ist ohne Client nicht sinnvoll nutzbar. Wichtig ist, dass er startet und keine ClassNotFound-Fehler wirft. Für echte End-to-End-Tests kommt später die VS-Code-Extension.

## Automatische Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lsp.IlimapDocumentStoreTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lsp.IlimapLspDiagnosticMapperTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lsp.IlimapTextDocumentServiceTest"
./gradlew test
```

## Funktionsfähiges Artefakt

Ein startbarer Java Language Server über stdio, der geöffnete `.ilimap`-Dokumente analysiert und Diagnostics publizieren kann.

## Definition of Done

- LSP4J-Abhängigkeit ist eingebunden.
- `runIlimapLanguageServer` existiert.
- Server nutzt `IlimapAnalysisService`.
- Keine Parserlogik im LSP-Package.
- Diagnostics werden auf LSP-Diagnostics gemappt.
- Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Lies zuerst AGENTS.md, docs/agent/DEFINITION_OF_DONE.md, docs/agent/COMMIT_POLICY.md und docs/agent/DECISIONS.md.

Verwende zusätzlich:
- .skills/java-test-gap/SKILL.md
- .skills/gradle-verification/SKILL.md
- .skills/done-and-commit/SKILL.md
- .skills/mapping-dsl-change/SKILL.md

Aufgabe Phase VSC-2:
Ergänze LSP4J als Gradle-Dependency und implementiere einen minimalen Java Language Server unter guru.interlis.transformer.mapping.ilimap.lsp. Implementiere IlimapLanguageServerMain, IlimapLanguageServer, IlimapTextDocumentService, IlimapWorkspaceService, IlimapDocumentStore, IlimapLspRangeMapper und IlimapLspDiagnosticMapper. Der Server soll Full Document Sync verwenden und bei didOpen/didChange Diagnostics publizieren. didClose soll Diagnostics leeren. Der LSP darf keinen Parsercode enthalten, sondern muss IlimapAnalysisService nutzen.

Ergänze die genannten Tests und einen Gradle-Task runIlimapLanguageServer. Führe fokussierte Tests und ./gradlew test aus. Melde exakte Befehle und Ergebnisse.
```

---

# Phase VSC-3 — VS-Code Extension Skeleton

## Ziel

Eine VS-Code-Extension erkennt `.ilimap`-Dateien, aktiviert sich, zeigt Syntaxhighlighting und startet den Java-Language-Server.

## Neue Verzeichnisstruktur

```text
vscode/ilimap-vscode/
  package.json
  tsconfig.json
  esbuild.js                optional
  README.md
  CHANGELOG.md
  language-configuration.json
  syntaxes/
    ilimap.tmLanguage.json
  src/
    extension.ts
    client.ts
    serverLauncher.ts
    commands.ts
  resources/
    icon.svg
  test/
    extension.test.ts
```

## `package.json`

Mindestinhalt:

```json
{
  "name": "ilimap-vscode",
  "displayName": "ILIMAP",
  "description": "Language support for ilimap mapping profiles",
  "version": "0.0.1",
  "publisher": "ilinexus",
  "engines": {
    "vscode": "^1.90.0"
  },
  "categories": ["Programming Languages", "Other"],
  "activationEvents": [
    "onLanguage:ilimap"
  ],
  "main": "./dist/extension.js",
  "contributes": {
    "languages": [
      {
        "id": "ilimap",
        "aliases": ["ILIMAP", "ilimap"],
        "extensions": [".ilimap"],
        "configuration": "./language-configuration.json"
      }
    ],
    "grammars": [
      {
        "language": "ilimap",
        "scopeName": "source.ilimap",
        "path": "./syntaxes/ilimap.tmLanguage.json"
      }
    ],
    "commands": [
      {
        "command": "ilimap.restartLanguageServer",
        "title": "ILIMAP: Restart Language Server"
      },
      {
        "command": "ilimap.validate",
        "title": "ILIMAP: Validate Mapping"
      }
    ],
    "configuration": {
      "title": "ILIMAP",
      "properties": {
        "ilimap.java.path": {
          "type": "string",
          "default": "java",
          "description": "Path to the Java executable used to start the ILIMAP language server."
        },
        "ilimap.server.jar": {
          "type": "string",
          "default": "",
          "description": "Path to the ILIMAP language server JAR. If empty, the extension tries to use the bundled server."
        },
        "ilimap.trace.server": {
          "type": "string",
          "enum": ["off", "messages", "verbose"],
          "default": "off"
        }
      }
    }
  },
  "scripts": {
    "compile": "tsc -p ./",
    "watch": "tsc -watch -p ./",
    "test": "vscode-test"
  },
  "devDependencies": {
    "@types/node": "latest",
    "@types/vscode": "latest",
    "typescript": "latest",
    "@vscode/test-electron": "latest"
  },
  "dependencies": {
    "vscode-languageclient": "latest"
  }
}
```

Der Agent darf konkrete Versionsnummern pinnen. Wenn er `latest` ersetzt, muss er aktuelle Versionen prüfen und dokumentieren.

## `language-configuration.json`

```json
{
  "comments": {
    "lineComment": "//",
    "blockComment": ["/*", "*/"]
  },
  "brackets": [
    ["{", "}"],
    ["(", ")"],
    ["[", "]"]
  ],
  "autoClosingPairs": [
    { "open": "{", "close": "}" },
    { "open": "(", "close": ")" },
    { "open": "[", "close": "]" },
    { "open": "\"", "close": "\"", "notIn": ["string", "comment"] }
  ],
  "surroundingPairs": [
    ["{", "}"],
    ["(", ")"],
    ["[", "]"],
    ["\"", "\""]
  ]
}
```

## TextMate-Grammar MVP

Datei:

```text
vscode/ilimap-vscode/syntaxes/ilimap.tmLanguage.json
```

Muss hervorheben:

```text
- keywords: mapping, v2, job, input, output, oid, basket, enum, defaults, rule, target, source, from, class, where, join, inner, left, identity, assign, bag, ref, association, role, required, create, loss, metadata
- strings
- numbers
- booleans/null
- comments
- hash literals wie #LFP3
- arrow =>
```

## TypeScript-Klassen

### `extension.ts`

```ts
import * as vscode from 'vscode';
import { startLanguageClient, stopLanguageClient, restartLanguageClient } from './client';
import { registerCommands } from './commands';

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const outputChannel = vscode.window.createOutputChannel('ILIMAP Language Server');
  context.subscriptions.push(outputChannel);

  registerCommands(context, outputChannel);
  await startLanguageClient(context, outputChannel);
}

export async function deactivate(): Promise<void> {
  await stopLanguageClient();
}
```

### `client.ts`

```ts
import * as vscode from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';
import { resolveServerOptions } from './serverLauncher';

let client: LanguageClient | undefined;

export async function startLanguageClient(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  const serverOptions: ServerOptions = resolveServerOptions(context, outputChannel);

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'ilimap' }],
    outputChannel,
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.ilimap')
    }
  };

  client = new LanguageClient(
    'ilimapLanguageServer',
    'ILIMAP Language Server',
    serverOptions,
    clientOptions
  );

  await client.start();
}

export async function stopLanguageClient(): Promise<void> {
  if (client) {
    await client.stop();
    client = undefined;
  }
}

export async function restartLanguageClient(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  await stopLanguageClient();
  await startLanguageClient(context, outputChannel);
}
```

### `serverLauncher.ts`

```ts
import * as vscode from 'vscode';
import { ServerOptions } from 'vscode-languageclient/node';

export function resolveServerOptions(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): ServerOptions {
  const config = vscode.workspace.getConfiguration('ilimap');
  const javaPath = config.get<string>('java.path') || 'java';
  const configuredJar = config.get<string>('server.jar') || '';

  const serverJar = configuredJar || context.asAbsolutePath('server/ilimap-language-server.jar');

  outputChannel.appendLine(`Starting ILIMAP language server: ${javaPath} -jar ${serverJar}`);

  return {
    command: javaPath,
    args: ['-jar', serverJar],
    options: {}
  };
}
```

### `commands.ts`

```ts
import * as vscode from 'vscode';
import { restartLanguageClient } from './client';

export function registerCommands(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): void {
  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.restartLanguageServer', async () => {
      await restartLanguageClient(context, outputChannel);
      vscode.window.showInformationMessage('ILIMAP language server restarted.');
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.validate', async () => {
      await vscode.commands.executeCommand('workbench.action.problems.focus');
    })
  );
}
```

## Server-JAR Packaging für Extension

Für den MVP gibt es zwei mögliche Wege:

### Variante A: Dev-Modus mit konfiguriertem JAR

Der Benutzer setzt:

```json
{
  "ilimap.server.jar": "/path/to/ilimap-language-server.jar"
}
```

Einfacher für die erste Phase.

### Variante B: Extension enthält Server-JAR

Pfad:

```text
vscode/ilimap-vscode/server/ilimap-language-server.jar
```

Dazu später Gradle-Task ergänzen, der JAR kopiert. Für VSC-3 reicht Variante A, sofern dokumentiert.

## Tests

TS-Test minimal:

```text
vscode/ilimap-vscode/test/extension.test.ts
```

Testfälle:

```text
- extension activates
- ilimap language contribution exists
- commands are registered
```

Diese Tests können in einer späteren Phase ausgebaut werden. Der Agent muss dokumentieren, ob Node/npm im Ausführungsumfeld vorhanden war.

## Verifikation

Java:

```bash
./gradlew test
```

VS Code Extension:

```bash
cd vscode/ilimap-vscode
npm install
npm run compile
```

Optional:

```bash
npm test
```

## Funktionsfähiges Artefakt

Eine VS-Code-Extension, die `.ilimap` erkennt, Syntaxhighlighting bietet und versucht, den Java-LSP zu starten.

## Definition of Done

- Extension-Struktur existiert.
- `.ilimap`-Language-ID ist registriert.
- TextMate-Grammar funktioniert grundsätzlich.
- Extension startet den Language Client.
- Serverpfad ist konfigurierbar.
- Compile läuft.
- Java-Tests laufen weiterhin.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Lies zuerst AGENTS.md, docs/agent/DEFINITION_OF_DONE.md, docs/agent/COMMIT_POLICY.md und docs/agent/DECISIONS.md.

Verwende zusätzlich:
- .skills/java-test-gap/SKILL.md
- .skills/gradle-verification/SKILL.md
- .skills/done-and-commit/SKILL.md
- .skills/mapping-dsl-change/SKILL.md

Aufgabe Phase VSC-3:
Lege unter vscode/ilimap-vscode eine VS-Code-Extension an. Registriere die Sprache ilimap für .ilimap-Dateien, lege language-configuration.json und eine TextMate-Grammar an, und implementiere einen dünnen TypeScript-Client, der den Java-Language-Server über java -jar startet. Der Server-JAR-Pfad muss über ilimap.server.jar konfigurierbar sein. Implementiere Commands ILIMAP: Restart Language Server und ILIMAP: Validate Mapping. Der VS-Code-Client darf keine .ilimap-Semantik implementieren.

Führe ./gradlew test aus. Falls npm verfügbar ist, führe in vscode/ilimap-vscode npm install und npm run compile aus. Dokumentiere, ob npm-Tests ausgeführt wurden.
```



# Phase VSC-3.5 — Developer Experience: Hot Reload / Live Reload / Restartability

## Ziel

Das VS-Code-Plugin und der Java-Language-Server müssen im Entwicklungsmodus schnell neu ladbar sein. Diese Phase macht den Entwicklungsworkflow explizit und reproduzierbar.

Wichtig: In diesem Plan bedeutet **hot reloadable / live reloadable** nicht, dass Java-Klassen im laufenden JVM-Prozess per HotSwap ersetzt werden. Das wäre unzuverlässig und für neue Klassen, neue Methoden, geänderte Records usw. nicht robust genug. Gemeint sind drei sauber getrennte Reload-Ebenen:

```text
1. Live-Reanalyse von .ilimap-Dateien
   User tippt im Editor -> didChange -> Analyse -> Diagnostics aktualisieren sich ohne Speichern.

2. Reload des TypeScript-Clients
   Extension-Code wird per Watch-Build neu gebaut -> Extension Development Host kann neu geladen werden.

3. Restart des Java-LSP-Servers
   Java-Code wird neu gebaut -> neuer Fat-JAR wird nach vscode/ilimap-vscode/server/ kopiert -> Language Server kann per Command neu gestartet werden.
```

Diese Phase orientiert sich bewusst am bewährten Muster aus `edigonzales/interlis-lsp`:

```text
Java LSP          -> shadowJar / Fat JAR
VS-Code Client   -> TypeScript + esbuild
Dev-JAR-Pfad     -> client/server/interlis-lsp-all.jar
Dev Host         -> .vscode/launch.json mit preLaunchTask
preLaunchTask    -> npm build + ./gradlew copyDevServerJar
Serverstart      -> java -jar <bundled-or-configured-jar>
```

Für `ilimap` soll daraus werden:

```text
vscode/ilimap-vscode/server/ilimap-lsp-all.jar
```

## Zu lesende Dateien

Zusätzlich zu den allgemeinen Pflichtdateien muss der Agent, falls vorhanden, folgende Dateien aus dem eigenen Repository prüfen:

```text
build.gradle
settings.gradle
vscode/ilimap-vscode/package.json
vscode/ilimap-vscode/src/extension.ts
vscode/ilimap-vscode/src/client.ts
vscode/ilimap-vscode/src/serverLauncher.ts
vscode/ilimap-vscode/.vscode/launch.json
vscode/ilimap-vscode/.vscode/tasks.json
```

Falls die Dateien noch nicht existieren, muss der Agent sie in dieser Phase anlegen oder die vorherige Phase entsprechend erweitern.

## Verbindliches Verhalten

### `.ilimap`-Änderungen müssen live analysiert werden

Wenn der Benutzer im Editor tippt, muss der LSP ohne Speichern reagieren:

```text
didOpen
  -> DocumentStore.open(uri, text)
  -> analyze(uri)
  -> publishDiagnostics(uri)

didChange
  -> DocumentStore.update(uri, changes)
  -> analyze(uri)
  -> publishDiagnostics(uri)

didClose
  -> publishDiagnostics(uri, emptyList)
  -> DocumentStore.close(uri)
```

Für den MVP reicht Full Document Sync. Wenn bereits Incremental Sync implementiert ist, darf dieser beibehalten werden. Entscheidend ist, dass `DocumentStore` immer den aktuellen, ungespeicherten Dokumentinhalt kennt.

### TypeScript-Client muss im Watch-Modus gebaut werden können

Der VS-Code-Client muss mindestens folgende Scripts haben:

```json
{
  "scripts": {
    "compile": "npm run build",
    "build": "esbuild src/extension.ts --bundle --platform=node --external:vscode --outfile=dist/extension.js",
    "watch": "esbuild src/extension.ts --bundle --platform=node --external:vscode --outfile=dist/extension.js --watch"
  }
}
```

Falls später eine Webview ergänzt wird, muss es separate Scripts geben:

```json
{
  "scripts": {
    "build": "npm run build:extension && npm run build:webview",
    "build:extension": "esbuild src/extension.ts --bundle --platform=node --external:vscode --outfile=dist/extension.js",
    "build:webview": "esbuild src/webview/mappingOverview.ts --bundle --platform=browser --format=iife --outfile=dist/mappingOverview.js",
    "watch:extension": "esbuild src/extension.ts --bundle --platform=node --external:vscode --outfile=dist/extension.js --watch",
    "watch:webview": "esbuild src/webview/mappingOverview.ts --bundle --platform=browser --format=iife --outfile=dist/mappingOverview.js --watch"
  }
}
```

Falls kein Webview vorhanden ist, darf `watch:webview` entfallen. Der Agent darf kein nicht existierendes Webview-Build erzwingen.

### Java-LSP muss ohne kompletten VS-Code-Neustart neu startbar sein

Es muss einen Command geben:

```text
ILIMAP: Restart Language Server
```

Command-ID:

```text
ilimap.restartLanguageServer
```

Der Command muss den Language Client stoppen und neu starten:

```ts
export async function restartLanguageClient(): Promise<void> {
  await stopLanguageClient();
  await startLanguageClient();
}
```

Wichtig:

- Der Command darf keinen neuen Client starten, solange der alte Client noch läuft.
- Der alte Java-Prozess muss sauber beendet werden.
- Nach dem Restart müssen geöffnete `.ilimap`-Dokumente wieder analysiert werden.
- Der Output Channel muss den Restart protokollieren.

## Gradle-Erweiterung

Falls noch kein Shadow/Fat-JAR existiert, soll in dieser Phase ein Dev-JAR-Workflow ergänzt werden. Der Agent muss prüfen, ob `shadowJar` bereits im Projekt vorhanden ist. Falls nicht, soll er eine minimale, projektverträgliche Variante einführen.

### Empfohlene Tasks

```groovy
tasks.register('ilimapLanguageServerJar', Jar) {
    group = 'build'
    description = 'Builds the ILIMAP language server JAR.'
    archiveClassifier = 'ilimap-lsp'
    from sourceSets.main.output
    manifest {
        attributes 'Main-Class': 'guru.interlis.transformer.mapping.ilimap.lsp.IlimapLanguageServerMain'
    }
}
```

Für echte Distribution ist ein Fat-JAR sinnvoller. Wenn Shadow eingeführt wird:

```groovy
plugins {
    id 'com.github.johnrengelman.shadow' version '<pinned-version>'
}

shadowJar {
    archiveClassifier = 'all'
    manifest {
        attributes 'Main-Class': 'guru.interlis.transformer.mapping.ilimap.lsp.IlimapLanguageServerMain'
    }
}
```

Dev-Copy-Task:

```groovy
tasks.register('copyDevIlimapServerJar', Copy) {
    group = 'vscode'
    description = 'Copies the current ILIMAP language server JAR into the VS Code extension dev server folder.'
    dependsOn tasks.named('shadowJar') // oder ilimapLanguageServerJar, falls noch kein shadowJar verwendet wird
    from(tasks.named('shadowJar').flatMap { it.archiveFile })
    into layout.projectDirectory.dir('vscode/ilimap-vscode/server')
    rename { 'ilimap-lsp-all.jar' }
}
```

Falls `shadowJar` nicht eingeführt wird, muss der Agent die Task entsprechend auf den tatsächlich gebauten JAR anpassen und dokumentieren, dass noch kein selbstständiger Fat-JAR erzeugt wird.

## VS-Code-Extension: Serverpfad-Auflösung

Die Extension soll zwei Modi unterstützen:

```text
1. Konfigurierter Server-JAR
   ilimap.server.jarPath ist gesetzt -> diesen JAR verwenden.

2. Dev-/Bundled-JAR
   ilimap.server.jarPath ist leer -> vscode/ilimap-vscode/server/ilimap-lsp-all.jar verwenden.
```

### Settings

In `package.json`:

```json
{
  "contributes": {
    "configuration": {
      "title": "ILIMAP",
      "properties": {
        "ilimap.server.jarPath": {
          "type": "string",
          "default": "",
          "markdownDescription": "Path to the ILIMAP language server fat JAR. Leave empty to use the JAR bundled with the extension."
        },
        "ilimap.java.path": {
          "type": "string",
          "default": "java",
          "markdownDescription": "Path to the Java executable used to start the ILIMAP language server."
        },
        "ilimap.server.jvmArgs": {
          "type": "array",
          "items": { "type": "string" },
          "default": [],
          "markdownDescription": "Additional JVM arguments passed to the ILIMAP language server."
        },
        "ilimap.server.restartOnJarChange": {
          "type": "boolean",
          "default": false,
          "markdownDescription": "Development option: automatically restart the ILIMAP language server when the bundled dev JAR changes. Disabled by default."
        }
      }
    }
  }
}
```

`restartOnJarChange` ist optional. Für den MVP muss der manuelle Restart-Command funktionieren. Automatischer Restart bei JAR-Änderung darf nur implementiert werden, wenn es sauber testbar und stabil ist.

### `configuration.ts`

Neue Datei:

```text
vscode/ilimap-vscode/src/configuration.ts
```

Methoden:

```ts
export function resolveServerJarPath(context: vscode.ExtensionContext, configured: string | undefined): string;
export function resolveJavaPath(configured: string | undefined): string;
export function resolveJvmArgs(configured: readonly string[] | undefined): string[];
```

Beispiel:

```ts
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';

export function resolveServerJarPath(context: vscode.ExtensionContext, configured: string | undefined): string {
  const override = configured?.trim();
  if (override) {
    return override;
  }

  const bundled = context.asAbsolutePath(path.join('server', 'ilimap-lsp-all.jar'));
  if (fs.existsSync(bundled)) {
    return bundled;
  }

  const message = 'ILIMAP language server JAR not found. Configure ilimap.server.jarPath or run ./gradlew copyDevIlimapServerJar.';
  vscode.window.showErrorMessage(message);
  throw new Error(message);
}

export function resolveJavaPath(configured: string | undefined): string {
  return configured?.trim() || 'java';
}

export function resolveJvmArgs(configured: readonly string[] | undefined): string[] {
  return Array.isArray(configured) ? [...configured] : [];
}
```

## VS-Code Extension Development Host

Lege an:

```text
vscode/ilimap-vscode/.vscode/launch.json
vscode/ilimap-vscode/.vscode/tasks.json
```

### `launch.json`

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "extensionHost",
      "request": "launch",
      "name": "Run ILIMAP Client",
      "runtimeExecutable": "${execPath}",
      "args": [
        "--extensionDevelopmentPath=${workspaceFolder}"
      ],
      "outFiles": [
        "${workspaceFolder}/dist/**/*.js"
      ],
      "preLaunchTask": "Prepare ILIMAP Dev Host"
    }
  ]
}
```

### `tasks.json`

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Build ILIMAP Client",
      "type": "npm",
      "script": "build",
      "problemMatcher": []
    },
    {
      "label": "Copy ILIMAP Server JAR",
      "type": "shell",
      "command": "./gradlew",
      "windows": {
        "command": "gradlew.bat"
      },
      "args": [
        "copyDevIlimapServerJar"
      ],
      "options": {
        "cwd": "${workspaceFolder}/../.."
      },
      "problemMatcher": []
    },
    {
      "label": "Prepare ILIMAP Dev Host",
      "dependsOrder": "sequence",
      "dependsOn": [
        "Build ILIMAP Client",
        "Copy ILIMAP Server JAR"
      ],
      "problemMatcher": []
    }
  ]
}
```

Der `cwd`-Pfad muss zur tatsächlichen Repo-Struktur passen. Wenn `vscode/ilimap-vscode` direkt unter dem Repo-Root liegt, ist `${workspaceFolder}/../..` korrekt. Der Agent muss den Pfad anhand der finalen Struktur verifizieren.

## TypeScript-Klassen und Methoden

### `client.ts`

Pfad:

```text
vscode/ilimap-vscode/src/client.ts
```

Empfohlene API:

```ts
import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

let client: LanguageClient | undefined;
let starting: Promise<void> | undefined;

export async function startLanguageClient(context: vscode.ExtensionContext, output: vscode.OutputChannel): Promise<void>;
export async function stopLanguageClient(output: vscode.OutputChannel): Promise<void>;
export async function restartLanguageClient(context: vscode.ExtensionContext, output: vscode.OutputChannel): Promise<void>;
export function getLanguageClient(): LanguageClient | undefined;
```

Verhalten:

```text
startLanguageClient:
  - wenn client bereits läuft oder starting gesetzt ist: nicht doppelt starten
  - Konfiguration lesen
  - Server-JAR und Java-Pfad auflösen
  - Executable für java -jar bauen
  - LanguageClient erzeugen
  - client.start() ausführen
  - Output Channel protokolliert Java/JAR/JVM-Args

stopLanguageClient:
  - wenn kein client existiert: no-op
  - client.stop() ausführen
  - client auf undefined setzen
  - Output Channel protokolliert Stop

restartLanguageClient:
  - stopLanguageClient
  - startLanguageClient
  - offene Dokumente werden durch VS Code / LanguageClient erneut synchronisiert oder über force reanalysis behandelt
```

Falls nach einem Restart offene Dokumente nicht automatisch erneut Diagnostics erhalten, muss der Agent eine explizite Revalidierung implementieren:

```ts
for (const document of vscode.workspace.textDocuments) {
  if (document.languageId === 'ilimap' && document.uri.scheme === 'file') {
    // optional command/request triggern oder TextDocumentService erhält didOpen erneut über Client-Neustart
  }
}
```

In der Regel sendet der Language Client geöffnete Dokumente beim Neustart neu an den Server. Der Agent muss dieses Verhalten manuell prüfen.

### `commands.ts`

Pfad:

```text
vscode/ilimap-vscode/src/commands.ts
```

Methoden:

```ts
export function registerCommands(context: vscode.ExtensionContext, output: vscode.OutputChannel): void;
```

Commands:

```ts
vscode.commands.registerCommand('ilimap.restartLanguageServer', async () => {
  await restartLanguageClient(context, output);
  vscode.window.showInformationMessage('ILIMAP language server restarted.');
});

vscode.commands.registerCommand('ilimap.showLanguageServerLogs', async () => {
  output.show(true);
});
```

Zusätzlicher Command in `package.json`:

```json
{
  "command": "ilimap.showLanguageServerLogs",
  "title": "ILIMAP: Show Language Server Logs"
}
```

### `extension.ts`

`activate` soll nur orchestrieren:

```ts
export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const output = vscode.window.createOutputChannel('ILIMAP Language Server');
  context.subscriptions.push(output);

  registerCommands(context, output);
  await startLanguageClient(context, output);
}

export async function deactivate(): Promise<void> {
  await stopLanguageClient(/* output optional */);
}
```

Keine `.ilimap`-Semantik in `extension.ts` implementieren.

## Optional: JAR-Watcher für automatischen Restart

Diese Funktion ist ausdrücklich optional und soll nur umgesetzt werden, wenn der manuelle Restart bereits stabil ist.

Klasse/Datei:

```text
vscode/ilimap-vscode/src/jarWatcher.ts
```

API:

```ts
export function maybeStartJarWatcher(
  context: vscode.ExtensionContext,
  jarPath: string,
  output: vscode.OutputChannel,
  restart: () => Promise<void>
): vscode.Disposable | undefined;
```

Regeln:

- Nur aktiv, wenn `ilimap.server.restartOnJarChange = true`.
- Debounce mindestens 1000 ms.
- Nicht während bereits laufendem Restart erneut starten.
- Nur den tatsächlich verwendeten JAR beobachten.
- Jede automatische Aktion im Output Channel protokollieren.

Für MVP genügt es, diese Option als offene spätere Aufgabe zu dokumentieren.

## Tests

### Java-Tests

Falls in dieser Phase `DocumentStore` oder Sync-Verhalten verändert wird:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapDocumentStoreTest.java
```

Testfälle:

```text
- openStoresTextAndVersion
- fullChangeReplacesText
- closeRemovesText
- didChangePublishesDiagnosticsForUnsavedContent
```

Falls Incremental Sync implementiert wird:

```text
- incrementalChangeReplacesRange
- incrementalChangeHandlesMultiLineEdits
- incrementalChangeClampsInvalidRanges
```

### TypeScript-Tests

```text
vscode/ilimap-vscode/test/client.test.ts
vscode/ilimap-vscode/test/configuration.test.ts
```

Testfälle:

```text
configuration:
- resolveServerJarPathUsesConfiguredOverride
- resolveServerJarPathUsesBundledJarWhenOverrideEmpty
- resolveServerJarPathThrowsHelpfulErrorWhenMissing
- resolveJavaPathDefaultsToJava

client:
- startLanguageClientDoesNotStartTwice
- stopLanguageClientIsNoopWhenNotStarted
- restartLanguageClientStopsThenStarts
```

Für die `LanguageClient`-Tests darf der Agent Mocks verwenden. Es muss kein echter Java-Prozess in Unit-Tests gestartet werden.

### Manuelle Verifikation

```bash
./gradlew copyDevIlimapServerJar
cd vscode/ilimap-vscode
npm install
npm run build
```

Dann in VS Code:

```text
1. vscode/ilimap-vscode als Extension-Projekt öffnen.
2. Run and Debug -> Run ILIMAP Client starten.
3. Eine .ilimap-Datei öffnen.
4. Syntaxfehler einfügen.
5. Prüfen, dass Diagnostics ohne Speichern erscheinen.
6. Java-Code ändern.
7. ./gradlew copyDevIlimapServerJar ausführen.
8. Command Palette -> ILIMAP: Restart Language Server.
9. Prüfen, dass der Output Channel den neuen Start protokolliert.
10. TypeScript-Code ändern.
11. npm run watch laufen lassen.
12. Extension Development Host mit Developer: Reload Window neu laden.
```

## Verifikation

Root:

```bash
./gradlew test
./gradlew copyDevIlimapServerJar
```

VS-Code-Client:

```bash
cd vscode/ilimap-vscode
npm install
npm run build
npm run watch
```

`npm run watch` muss nicht endlos in CI laufen. Der Agent soll nur dokumentieren, dass der Watch-Prozess startet. In automatisierten Tests genügt `npm run build`.

## Funktionsfähiges Artefakt

Nach dieser Phase existiert ein reproduzierbarer Entwicklungsworkflow:

```text
- Java-LSP kann als Dev-JAR nach vscode/ilimap-vscode/server/ kopiert werden.
- VS-Code Extension Development Host baut Client und Server vor dem Start.
- Extension kann den Java-LSP starten.
- Command ILIMAP: Restart Language Server funktioniert.
- Command ILIMAP: Show Language Server Logs funktioniert.
- .ilimap-Dateien werden live analysiert.
```

## Definition of Done

- `copyDevIlimapServerJar` oder äquivalenter Task existiert und ist dokumentiert.
- `vscode/ilimap-vscode/.vscode/launch.json` existiert.
- `vscode/ilimap-vscode/.vscode/tasks.json` existiert.
- `npm run build` funktioniert.
- `npm run watch` ist definiert und startet im Entwicklungsmodus.
- `ILIMAP: Restart Language Server` stoppt und startet den Client/Server sauber.
- Der Output Channel zeigt verwendeten Java-Pfad, Server-JAR und Restart-Ereignisse.
- Live-Diagnostics bei `didChange` sind manuell oder automatisiert verifiziert.
- Kein Java-HotSwap wird als garantiertes Feature behauptet.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Lies zuerst AGENTS.md, docs/agent/DEFINITION_OF_DONE.md, docs/agent/COMMIT_POLICY.md und docs/agent/DECISIONS.md.

Verwende zusätzlich:
- .skills/java-test-gap/SKILL.md
- .skills/gradle-verification/SKILL.md
- .skills/done-and-commit/SKILL.md
- .skills/mapping-dsl-change/SKILL.md

Aufgabe Phase VSC-3.5:
Ergänze einen expliziten Development-Experience-Workflow für das ILIMAP VS-Code-Plugin. Das Plugin muss im Entwicklungsmodus schnell reloadbar sein: .ilimap-Dateien müssen live über didChange analysiert werden, der TypeScript-Client muss über npm run watch neu gebaut werden können, und der Java-LSP muss per Command ILIMAP: Restart Language Server ohne kompletten VS-Code-Neustart neu startbar sein. Orientiere dich am Muster aus edigonzales/interlis-lsp: shadowJar/Fat-JAR, copyDevServerJar nach client/server, VS-Code launch.json mit preLaunchTask, tasks.json mit npm build und Gradle copy task, Serverstart über java -jar.

Implementiere oder ergänze:
- Gradle-Task copyDevIlimapServerJar oder äquivalent.
- vscode/ilimap-vscode/.vscode/launch.json mit Run ILIMAP Client.
- vscode/ilimap-vscode/.vscode/tasks.json mit Prepare ILIMAP Dev Host.
- package.json Scripts build und watch.
- Settings ilimap.server.jarPath, ilimap.java.path, ilimap.server.jvmArgs und optional ilimap.server.restartOnJarChange.
- configuration.ts mit resolveServerJarPath, resolveJavaPath und resolveJvmArgs.
- client.ts mit startLanguageClient, stopLanguageClient, restartLanguageClient und getLanguageClient.
- commands.ts mit ILIMAP: Restart Language Server und ILIMAP: Show Language Server Logs.
- Output Channel Logging für Server-JAR, Java-Pfad, Start, Stop und Restart.

Der VS-Code-Client darf keine .ilimap-Semantik implementieren. Java-HotSwap ist kein Ziel; der robuste Workflow ist: Java ändern, JAR bauen/kopieren, Language Server restart.

Führe ./gradlew test und ./gradlew copyDevIlimapServerJar aus. Falls npm verfügbar ist, führe in vscode/ilimap-vscode npm install und npm run build aus. Dokumentiere, ob npm run watch manuell gestartet wurde. Verifiziere manuell oder per Test, dass didChange Diagnostics aktualisiert und dass der Restart-Command den Server neu startet.
```

---

# Phase VSC-4 — Document Formatting über LSP

## Ziel

VS Code kann `.ilimap`-Dateien über den LSP formatieren. Der LSP verwendet den bestehenden `IlimapFormatter`.

## Java-Erweiterungen

### Capabilities ergänzen

In `IlimapLanguageServer.initialize(...)`:

```java
ServerCapabilities capabilities = new ServerCapabilities();
capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
capabilities.setDocumentFormattingProvider(true);
```

### `IlimapTextDocumentService.formatting(...)`

```java
@Override
public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    String uri = params.getTextDocument().getUri();
    Optional<IlimapDocumentSnapshot> snapshot = documentStore.get(uri);
    if (snapshot.isEmpty()) {
        return CompletableFuture.completedFuture(List.of());
    }

    Optional<IlimapTextEdit> edit = formattingService.format(
            uri,
            snapshot.get().text(),
            IlimapFormatOptions.defaults()
    );

    return CompletableFuture.completedFuture(edit.map(this::toLspTextEdit).stream().toList());
}
```

### Whole-document Range

`IlimapFormattingService` muss eine Range liefern, die das ganze Dokument ersetzt:

```text
start = line 0, char 0
end = last line, last char
```

## Verhalten bei Syntaxfehlern

Wenn Parserfehler vorhanden sind:

- Keine Formatierung ausführen.
- Leere Edit-Liste zurückgeben.
- Optional `window/showMessage` oder `window/logMessage`: `Cannot format invalid ilimap document.`

Nicht versuchen, halb kaputten Text per Regex zu formatieren.

## VS-Code-Extension

`package.json` ergänzen:

```json
{
  "contributes": {
    "commands": [
      {
        "command": "ilimap.format",
        "title": "ILIMAP: Format Mapping"
      }
    ]
  }
}
```

`commands.ts`:

```ts
vscode.commands.registerCommand('ilimap.format', async () => {
  await vscode.commands.executeCommand('editor.action.formatDocument');
});
```

Optional Setting:

```json
"ilimap.format.enable": {
  "type": "boolean",
  "default": true
}
```

## Tests

Java-Tests:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapFormattingLspTest.java
```

Testfälle:

```text
- formattingReturnsSingleFullDocumentEdit
- formattingUsesIlimapFormatterOutput
- formattingReturnsNoEditForInvalidDocument
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lsp.IlimapFormattingLspTest"
./gradlew test
cd vscode/ilimap-vscode && npm run compile
```

## Funktionsfähiges Artefakt

Eine in VS Code formatierbare `.ilimap`-Datei.

## Definition of Done

- LSP Capability `documentFormattingProvider` gesetzt.
- Formatierung ersetzt komplettes Dokument.
- Kein Regex-Formatter.
- Ungültige Dokumente werden nicht formatiert.
- Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Aufgabe Phase VSC-4:
Implementiere Document Formatting im LSP. Der LSP muss den bestehenden IlimapFormatter über IlimapFormattingService verwenden. Bei ungültigem Dokument keine Formatierung durchführen. Ergänze den VS-Code-Command ILIMAP: Format Mapping, der editor.action.formatDocument ausführt. Implementiere Tests für erfolgreiche Formatierung und ungültige Dokumente. Führe fokussierte Tests, ./gradlew test und npm run compile aus.
```

---

# Phase VSC-5 — Document Symbols und Folding

## Ziel

VS Code zeigt eine nützliche Outline und erlaubt Folding für `.ilimap`-Strukturen.

## Neue IDE-Services

### `IlimapDocumentSymbolService`

```java
public final class IlimapDocumentSymbolService {
    public List<IlimapDocumentSymbol> symbols(IlimapAnalysis analysis);
}
```

### `IlimapDocumentSymbol`

```java
public record IlimapDocumentSymbol(
        String name,
        IlimapSymbolDisplayKind kind,
        IlimapIdeRange range,
        IlimapIdeRange selectionRange,
        List<IlimapDocumentSymbol> children
) {}
```

### `IlimapSymbolDisplayKind`

```java
public enum IlimapSymbolDisplayKind {
    FILE,
    MODULE,
    CLASS,
    METHOD,
    PROPERTY,
    ENUM,
    FIELD,
    OBJECT
}
```

Mapping später auf LSP `SymbolKind`.

### `IlimapFoldingService`

```java
public final class IlimapFoldingService {
    public List<IlimapFoldingRange> foldingRanges(IlimapAnalysis analysis);
}
```

### `IlimapFoldingRange`

```java
public record IlimapFoldingRange(
        int startLine,
        int endLine,
        String kind
) {}
```

## Symbolstruktur

Für ein Dokument:

```text
mapping <name>
├─ job
├─ input dm01
├─ output dmav
├─ oid
├─ basket
├─ enum Zuverlaessigkeit_DM01_DMAV
└─ rule lfp3
   ├─ source p
   ├─ assign
   ├─ bag Textposition
   └─ ref Entstehung
```

## LSP-Erweiterungen

Capabilities:

```java
capabilities.setDocumentSymbolProvider(true);
capabilities.setFoldingRangeProvider(true);
```

In `IlimapTextDocumentService`:

```java
public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params);
public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params);
```

## Tests

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapDocumentSymbolServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapFoldingServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapDocumentSymbolLspTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapFoldingLspTest.java
```

Testfälle:

```text
- createsTopLevelSymbols
- createsRuleChildSymbols
- createsBagAndRefSymbols
- foldingIncludesMappingBlock
- foldingIncludesRuleBlocks
- foldingIncludesAssignBlocks
- lspMapsSymbolsToDocumentSymbol
- lspMapsFoldingRanges
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapDocumentSymbolServiceTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapFoldingServiceTest"
./gradlew test
cd vscode/ilimap-vscode && npm run compile
```

## Funktionsfähiges Artefakt

VS Code Outline und Folding funktionieren für `.ilimap`-Dateien.

## Definition of Done

- Outline basiert auf AST, nicht auf Regex.
- Folding basiert auf AST-Ranges.
- Nested Bags erscheinen korrekt.
- Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Aufgabe Phase VSC-5:
Implementiere Document Symbols und Folding für .ilimap. Die Implementierung muss AST-basiert sein und IlimapAnalysisService nutzen. Ergänze IlimapDocumentSymbolService, IlimapFoldingService und die LSP-Adapter. Die Outline soll mapping, job, input, output, enum, rule, source, assign, bag und ref anzeigen. Folding soll mapping-, rule-, assign-, bag-, ref- und enum-Blöcke abdecken. Implementiere Tests und führe fokussierte Tests sowie ./gradlew test aus.
```

---

## Manuelle VSCodium-Verifikation ab VSC-6

Ab VSC-6 betreffen die Phasen sichtbare Editorfunktionen. Zusätzlich zu Unit-,
LSP- und Compile-Tests soll deshalb eine manuelle VSCodium-Smoke-Prüfung
durchgeführt werden, wenn VSCodium lokal verfügbar ist. Diese Prüfung ersetzt
`./gradlew test` nicht.

Vorgehen:

```bash
VSCODIUM="/Applications/VSCodium.app/Contents/MacOS/VSCodium"

if [ -x "$VSCODIUM" ]; then
  ./gradlew shadowJar

  TMP_USER=$(mktemp -d /tmp/ilimap-vscodium-user.XXXXXX)
  TMP_EXT=$(mktemp -d /tmp/ilimap-vscodium-ext.XXXXXX)
  mkdir -p "$TMP_USER/User"
  printf '{ "ilimap.server.jarPath": "/Users/stefan/sources/ilinexus/build/libs/ilitransformer-0.1.0-all.jar" }\n' > "$TMP_USER/User/settings.json"

  "$VSCODIUM" \
    --new-window \
    --user-data-dir "$TMP_USER" \
    --extensions-dir "$TMP_EXT" \
    --extensionDevelopmentPath /Users/stefan/sources/ilinexus/vscode/ilimap-vscode \
    /Users/stefan/sources/ilinexus/profiles/dm01-to-dmav/1.1/lfp3.ilimap
fi
```

Allgemeine Checks:

```text
- Output-Panel "ILIMAP Language Server" zeigt den Start des Java-LSP.
- Der verwendete Server-JAR ist build/libs/ilitransformer-0.1.0-all.jar.
- Positive Smoke-Datei: profiles/dm01-to-dmav/1.1/lfp3.ilimap.
- Negative Smoke-Datei: src/test/resources/mapping/ilimap/syntax-error.ilimap.
- Bei Features mit Edits zuerst eine temporäre Kopie der .ilimap-Datei öffnen.
- Ergebnis im Abschlussbericht dokumentieren; wenn VSCodium fehlt, das Fehlen dokumentieren.
```

Phase-spezifische Checks:

```text
VSC-6 Completion:
- Completion in lfp3.ilimap für Top-Level, Rule-Block, target, source ... from,
  target rule und enumMap(..., <cursor>) prüfen.

VSC-7 Hover/Definition:
- Hover auf input dm01, output dmav, rule lfp3 und Enum-Map prüfen.
- Go-to-definition für dm01, dmav, lfp3-nachfuehrung und Enum-Map-Referenzen prüfen.

VSC-8 Code Actions:
- Temporäre Kopie einer .ilimap-Datei öffnen.
- Quick Fixes im Problems-/Lightbulb-Menü prüfen und minimale lokale Edits verifizieren.

VSC-9 Model-aware Features:
- Model-aware Completion/Diagnostics mit validierten Testmodellen prüfen.
- Dokumentieren, ob Modeldir-Auflösung lokal, HTTP oder bewusst nur testlokal geprüft wurde.

VSC-10 Webview:
- Command "ILIMAP: Open Mapping Overview" prüfen.
- Prüfen, dass die Webview read-only ist, echte Counts zeigt, theme-kompatibel wirkt
  und keine ungeprüften Inhalte sichtbar als HTML rendert.
```

---

# Phase VSC-6 — Completion MVP

## Ziel

Kontextuelle Completion für die wichtigsten `.ilimap`-Symbole und Keywords.

Noch keine vollständige INTERLIS-Attributcompletion.

## Neue IDE-Klassen

### `IlimapCompletionService`

```java
public final class IlimapCompletionService {
    public List<IlimapCompletionItem> complete(IlimapAnalysis analysis, IlimapIdePosition position);
}
```

### `IlimapCompletionItem`

```java
public record IlimapCompletionItem(
        String label,
        IlimapCompletionKind kind,
        String detail,
        String documentation,
        String insertText
) {}
```

### `IlimapCompletionKind`

```java
public enum IlimapCompletionKind {
    KEYWORD,
    INPUT,
    OUTPUT,
    RULE,
    ENUM_MAP,
    SOURCE_ALIAS,
    FUNCTION,
    ATTRIBUTE,
    VALUE
}
```

### `IlimapCompletionContext`

```java
public record IlimapCompletionContext(
        IlimapCompletionContextKind kind,
        String prefix,
        IlimapRuleBlock currentRule,
        IlimapAstNode currentNode
) {}
```

### `IlimapCompletionContextKind`

```java
public enum IlimapCompletionContextKind {
    TOP_LEVEL,
    JOB_BLOCK,
    INPUT_BLOCK,
    OUTPUT_BLOCK,
    RULE_BLOCK,
    TARGET_OUTPUT,
    SOURCE_INPUT,
    REF_TARGET_RULE,
    ENUM_MAP_ARGUMENT,
    EXPRESSION,
    UNKNOWN
}
```

### `IlimapCompletionContextResolver`

```java
public final class IlimapCompletionContextResolver {
    public IlimapCompletionContext resolve(IlimapAnalysis analysis, IlimapIdePosition position);
}
```

## Completion-Kontexte

### Top-Level

Vorschläge:

```text
job
input
output
oid
basket
enum
defaults
rule
```

### Innerhalb `rule { ... }`

Vorschläge:

```text
target
source
where
join
identity
assign
defaults
bag
ref
create
loss
metadata
```

### Nach `target`

```ilimap
target <completion>
```

Vorschläge: Output-IDs aus SymbolTable.

### Nach `source ... from`

```ilimap
source p from <completion>
```

Vorschläge: Input-IDs aus SymbolTable.

### In Ref-Langform

```ilimap
target rule <completion> sourceRef p.Entstehung;
```

Vorschläge: Rule-IDs aus SymbolTable.

### `enumMap` zweites Argument

```ilimap
IstLagezuverlaessig = enumMap(p.LageZuv, <completion>);
```

Vorschläge: Enum-Map-IDs aus SymbolTable.

Für den MVP genügt eine heuristische Erkennung des `enumMap(`-Kontexts, solange sie getestet und auf diese Funktion begrenzt ist. Keine vollständige Expression-Parser-Neuimplementierung.

## LSP-Adapter

Capabilities:

```java
CompletionOptions options = new CompletionOptions();
options.setResolveProvider(false);
capabilities.setCompletionProvider(options);
```

In `IlimapTextDocumentService`:

```java
public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position);
```

Mapper:

```java
public final class IlimapLspCompletionMapper {
    public CompletionItem map(IlimapCompletionItem item);
}
```

## Tests

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapCompletionContextResolverTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapCompletionServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapCompletionLspTest.java
```

Testfälle:

```text
- completesTopLevelKeywords
- completesRuleKeywords
- completesOutputIdsAfterTarget
- completesInputIdsAfterSourceFrom
- completesRuleIdsInRefTargetRule
- completesEnumMapsInEnumMapSecondArgument
- doesNotSuggestEnumMapsOutsideEnumMapContext
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapCompletionServiceTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lsp.IlimapCompletionLspTest"
./gradlew test
cd vscode/ilimap-vscode && npm run compile
```

Manuelle VSCodium-Verifikation gemäss Abschnitt "Manuelle VSCodium-Verifikation ab VSC-6"
durchführen und Ergebnis dokumentieren.

## Funktionsfähiges Artefakt

VS Code bietet kontextuelle Completion für Keywords und `.ilimap`-Symbole.

## Definition of Done

- Completion verwendet SymbolTable.
- Keine neue Parserlogik im VS-Code-Client.
- Completion-Kontexte sind getestet.
- EnumMap-Completion ist auf `enumMap` begrenzt.
- Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Aufgabe Phase VSC-6:
Implementiere Completion MVP. Baue IlimapCompletionService, IlimapCompletionContextResolver und LSP-Mapping. Unterstütze Top-Level-Keywords, Rule-Keywords, Output-IDs nach target, Input-IDs nach source ... from, Rule-IDs in target rule und Enum-Map-IDs im zweiten enumMap-Argument. Verwende AST und SymbolTable; implementiere keine .ilimap-Semantik im VS-Code-Client. Ergänze Tests und führe fokussierte Tests sowie ./gradlew test aus. Wenn VSCodium unter /Applications/VSCodium.app/Contents/MacOS/VSCodium verfügbar ist, führe zusätzlich die manuelle VSCodium-Verifikation aus und dokumentiere das Ergebnis.
```

---

# Phase VSC-7 — Hover und Go-to-definition

## Ziel

Nutzer können von Referenzen zu Definitionen springen und über Symbolen kurze Informationen sehen.

## Neue IDE-Klassen

### `IlimapDefinitionService`

```java
public final class IlimapDefinitionService {
    public Optional<IlimapDefinition> definitionAt(IlimapAnalysis analysis, IlimapIdePosition position);
}
```

### `IlimapDefinition`

```java
public record IlimapDefinition(
        String uri,
        IlimapIdeRange range,
        String label
) {}
```

### `IlimapHoverService`

```java
public final class IlimapHoverService {
    public Optional<IlimapHover> hoverAt(IlimapAnalysis analysis, IlimapIdePosition position);
}
```

### `IlimapHover`

```java
public record IlimapHover(
        IlimapIdeRange range,
        String markdown
) {}
```

### `IlimapTokenAtPosition`

```java
public record IlimapTokenAtPosition(
        String text,
        IlimapIdeRange range,
        IlimapAstNode surroundingNode
) {}
```

### `IlimapPositionResolver`

```java
public final class IlimapPositionResolver {
    public Optional<IlimapTokenAtPosition> tokenAt(IlimapAnalysis analysis, IlimapIdePosition position);
    public Optional<IlimapAstNode> smallestNodeAt(IlimapAnalysis analysis, IlimapIdePosition position);
}
```

## Definition-Ziele

Unterstützen:

```text
- input-ID in source ... from dm01
- output-ID in target dmav
- enum-map ID in enumMap(..., EnumName)
- rule-ID in target rule lfp3-nachfuehrung
```

Später:

```text
- source alias p in Expressions
- INTERLIS-Klasse
- INTERLIS-Attribut
```

## Hover-Inhalte

### Rule

```markdown
**rule `lfp3`**

Target: `DMAV...LFP3`  
Sources: `p`  
Assignments: 7  
Bags: 1  
Refs: 1
```

### Input

```markdown
**input `dm01`**

Path: `input/dm01.itf`  
Model: `DM01AVCH24LV95D`  
Format: `itf`
```

### Output

```markdown
**output `dmav`**

Path: `build/out/dmav.xtf`  
Model: `DMAV_FixpunkteAVKategorie3_V1_1`  
Format: `xtf`
```

### Enum Map

```markdown
**enum `Zuverlaessigkeit_DM01_DMAV`**

Entries: 2

- `"ja"` => `true`
- `"nein"` => `false`
```

## LSP-Adapter

Capabilities:

```java
capabilities.setDefinitionProvider(true);
capabilities.setHoverProvider(true);
```

Methoden:

```java
public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params);
public CompletableFuture<Hover> hover(HoverParams params);
```

## Tests

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapDefinitionServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapHoverServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapDefinitionLspTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapHoverLspTest.java
```

Testfälle:

```text
- findsDefinitionOfInputId
- findsDefinitionOfOutputId
- findsDefinitionOfTargetRule
- findsDefinitionOfEnumMapSymbol
- returnsEmptyForUnknownSymbol
- hoverShowsRuleSummary
- hoverShowsInputSummary
- hoverShowsEnumMapSummary
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapDefinitionServiceTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapHoverServiceTest"
./gradlew test
cd vscode/ilimap-vscode && npm run compile
```

Manuelle VSCodium-Verifikation gemäss Abschnitt "Manuelle VSCodium-Verifikation ab VSC-6"
durchführen und Ergebnis dokumentieren.

## Funktionsfähiges Artefakt

Go-to-definition und Hover für zentrale `.ilimap`-Symbole funktionieren.

## Definition of Done

- Definition verwendet SymbolTable und AST-Ranges.
- Hover ist markdownfähig und knapp.
- Keine INTERLIS-Metadaten vortäuschen.
- Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Aufgabe Phase VSC-7:
Implementiere Hover und Go-to-definition für input, output, rule und enum-map Symbole. Baue IlimapDefinitionService, IlimapHoverService und die nötigen Position-/Token-Resolver. Der LSP soll definitionProvider und hoverProvider aktivieren. Hover-Inhalte sollen kurz, markdown-formatiert und aus AST/SymbolTable abgeleitet sein. Ergänze Tests und führe fokussierte Tests sowie ./gradlew test aus. Wenn VSCodium unter /Applications/VSCodium.app/Contents/MacOS/VSCodium verfügbar ist, führe zusätzlich die manuelle VSCodium-Verifikation aus und dokumentiere das Ergebnis.
```

---

# Phase VSC-8 — Code Actions und Quick Fixes

## Ziel

Erste kleine Korrekturhilfen für häufige `.ilimap`-Probleme.

## Neue IDE-Klassen

### `IlimapCodeActionService`

```java
public final class IlimapCodeActionService {
    public List<IlimapCodeAction> codeActions(IlimapAnalysis analysis, IlimapIdeRange range);
}
```

### `IlimapCodeAction`

```java
public record IlimapCodeAction(
        String title,
        String kind,
        List<IlimapTextEdit> edits,
        String diagnosticCode
) {}
```

## Erste Code Actions

### 1. `enumMap` String-Referenz in Symbolreferenz umwandeln

Ausgang:

```ilimap
IstLagezuverlaessig = enumMap(p.LageZuv, "Zuverlaessigkeit_DM01_DMAV");
```

Aktion:

```text
Use symbolic enum map reference
```

Ergebnis:

```ilimap
IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
```

Voraussetzungen:

- String-Wert entspricht existierender Enum-Map.
- Edit ersetzt nur die Quotes, nicht die ganze Expression.
- Nicht innerhalb anderer Strings ersetzen.

### 2. Missing enum map block erzeugen

Bei unbekannter Enum-Map in `enumMap(..., SomeMap)`:

Aktion:

```text
Create enum map 'SomeMap'
```

Erzeugt vor erster Rule oder nach letztem Enum-Block:

```ilimap
enum SomeMap {
}
```

Nur anbieten, wenn `SomeMap` gültige Symbol-ID ist.

### 3. Format document

Aktion:

```text
Format ILIMAP document
```

Delegiert an `textDocument/formatting`.

## LSP-Adapter

Capabilities:

```java
CodeActionOptions options = new CodeActionOptions(List.of(CodeActionKind.QuickFix, CodeActionKind.Source));
capabilities.setCodeActionProvider(options);
```

Methoden:

```java
public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params);
```

## Tests

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapCodeActionServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapCodeActionLspTest.java
```

Testfälle:

```text
- offersEnumMapStringToSymbolQuickFix
- doesNotOfferQuickFixForUnknownStringEnumMap
- createsMissingEnumMapBlock
- doesNotCreateInvalidEnumMapId
- mapsCodeActionToLsp
```

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapCodeActionServiceTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.lsp.IlimapCodeActionLspTest"
./gradlew test
cd vscode/ilimap-vscode && npm run compile
```

Manuelle VSCodium-Verifikation gemäss Abschnitt "Manuelle VSCodium-Verifikation ab VSC-6"
durchführen und Ergebnis dokumentieren.

## Funktionsfähiges Artefakt

VS Code bietet erste Quick Fixes für `.ilimap` an.

## Definition of Done

- Code Actions sind klein und sicher.
- Keine Regex-Massenänderungen ohne Range-Sicherheit.
- Edits sind minimal.
- Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Aufgabe Phase VSC-8:
Implementiere erste Code Actions. Unterstütze enumMap String-Referenz zu Symbolreferenz, Erzeugen eines fehlenden Enum-Blocks und Format Document. Edits müssen minimal und range-basiert sein. Kein globales Regex-Rewrite. Ergänze Tests und führe fokussierte Tests sowie ./gradlew test aus. Wenn VSCodium unter /Applications/VSCodium.app/Contents/MacOS/VSCodium verfügbar ist, führe zusätzlich die manuelle VSCodium-Verifikation aus und dokumentiere das Ergebnis.
```

---

# Phase VSC-9 — INTERLIS-Modellintegration für Completion und Diagnostics

## Ziel

Der Editor kennt INTERLIS-Modelle, Klassen und Attribute. Dies ist die erste fachlich grosse Ausbaustufe und darf erst nach stabilem LSP-MVP umgesetzt werden.

## Nicht im MVP enthaltene Risiken

Diese Phase ist bewusst später, weil sie komplex ist:

```text
- modeldir-Auflösung
- lokale und HTTP-Modelldirs
- ili2c/ModelRegistry-Caching
- Fehlertoleranz bei fehlenden Modellen
- Performance im Editor
- Offline-Betrieb
- Klassenpfade als Strings
- Attributcompletion innerhalb Expression-Text
```

## Neue IDE-Services

### `IlimapModelIndexService`

```java
public final class IlimapModelIndexService {
    public IlimapModelIndex buildIndex(IlimapAnalysis analysis, Path workspaceRoot);
}
```

### `IlimapModelIndex`

```java
public final class IlimapModelIndex {
    public List<IlimapModelInfo> models();
    public Optional<IlimapClassInfo> findClass(String qualifiedName);
    public List<IlimapClassInfo> classesForModel(String modelName);
}
```

### `IlimapModelInfo`

```java
public record IlimapModelInfo(
        String name,
        String version,
        String issuer,
        List<IlimapClassInfo> classes
) {}
```

### `IlimapClassInfo`

```java
public record IlimapClassInfo(
        String qualifiedName,
        String kind,
        List<IlimapAttributeInfo> attributes
) {}
```

### `IlimapAttributeInfo`

```java
public record IlimapAttributeInfo(
        String name,
        String type,
        boolean mandatory,
        String cardinality
) {}
```

## Completion-Ziele

### `target ... class "..."`

Completion für Zielklassen aus Output-Modell.

### `source ... class "..."`

Completion für Quellklassen aus Input-Modell.

### Assign-Zielattribute

```ilimap
assign {
  <completion> = p.Nummer;
}
```

Vorschläge: Attribute der Target-Klasse.

### Source-Alias-Attribute in Expressions

```ilimap
Nummer = p.<completion>
```

Vorschläge: Attribute der Source-Klasse von Alias `p`.

## Diagnostics

```text
- unbekannte target class
- unbekannte source class
- unbekanntes Zielattribut in assign
- unbekanntes Source-Attribut in einfacher alias.attribute-Expression
```

Komplexe Expressions müssen nicht vollständig semantisch analysiert werden. Für den Anfang reicht Attributprüfung für klare `alias.attribute`-Vorkommen.

## Caching

Einfacher MVP:

```text
- pro Dokumentanalyse lazy laden
- Fehler sauber diagnostizieren
```

Später:

```text
- Workspace Model Cache
- Invalidation bei modeldir/input/output Änderung
- Hintergrundindexierung
```

## Tests

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapModelIndexServiceTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapModelAwareCompletionTest.java
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapModelDiagnosticTest.java
```

Testfälle:

```text
- loadsClassesFromLocalModeldir
- completesSourceClasses
- completesTargetClasses
- completesTargetAttributesInAssign
- completesSourceAliasAttributes
- reportsUnknownTargetClass
- reportsUnknownSourceClass
- reportsUnknownTargetAttribute
```

INTERLIS-Testdaten müssen gemäss Repo-Regeln mit `ili2c` validiert werden.

## Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapModelIndexServiceTest"
./gradlew test --tests "guru.interlis.transformer.mapping.ilimap.ide.IlimapModelAwareCompletionTest"
./gradlew test
```

Falls `.ili`-Testmodelle erzeugt oder geändert werden:

```bash
ili2c <model.ili>
```

oder vorhandener Gradle-/CLI-Weg gemäss Repo verwenden.

Manuelle VSCodium-Verifikation gemäss Abschnitt "Manuelle VSCodium-Verifikation ab VSC-6"
durchführen und Ergebnis dokumentieren.

## Funktionsfähiges Artefakt

Model-aware Completion und erste Model-Diagnostics.

## Definition of Done

- Keine INTERLIS-Modellsyntax erfunden.
- Testmodelle validiert.
- Fehlende Modeldirs führen zu Diagnostics, nicht zu Server-Absturz.
- Completion bleibt performant genug.
- Tests bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Aufgabe Phase VSC-9:
Implementiere INTERLIS-Modellintegration für den ILIMAP-LSP. Baue einen model-aware Index-Service, der modeldir/input/output-Kontext aus dem IlimapAnalysis ableitet und INTERLIS-Klassen/Attribute für Completion und Diagnostics bereitstellt. Unterstütze Completion für source/target class Strings, Zielattribute in assign und Source-Alias-Attribute in einfachen Expressions. Fehlende oder ungültige Modelle müssen Diagnostics erzeugen, nicht Server-Abstürze. Falls .ili-Testmodelle geändert oder erzeugt werden, validiere sie gemäss Repo-Regeln. Ergänze Tests und führe fokussierte Tests sowie ./gradlew test aus. Wenn VSCodium unter /Applications/VSCodium.app/Contents/MacOS/VSCodium verfügbar ist, führe zusätzlich die manuelle VSCodium-Verifikation aus und dokumentiere das Ergebnis.
```

---

# Phase VSC-10 — Read-only VS-Code-Webview: Mapping Overview

## Ziel

Eine optionale Webview zeigt eine visuelle Übersicht über das aktuelle `.ilimap`-Mapping. Sie ist initial read-only.

## Warum erst jetzt?

VS-Code-Webviews sollen nur verwendet werden, wenn normale VS-Code-UI nicht reicht. Diagnostics, Outline, Folding, Completion, Hover und Definition gehören zuerst in den LSP. Die Webview ist danach eine Zusatzsicht.

## VS-Code-Struktur

```text
vscode/ilimap-vscode/src/webview/
  mappingOverviewPanel.ts
  mappingOverviewHtml.ts
  mappingOverviewMessages.ts
```

## Command

```json
{
  "command": "ilimap.openMappingOverview",
  "title": "ILIMAP: Open Mapping Overview"
}
```

## Message Flow

```text
Webview öffnen
  -> Extension liest aktives .ilimap-Dokument
  -> Extension fragt LSP oder lokalen Request nach Analysis Summary
  -> Webview rendert Cards
```

Es gibt zwei mögliche Wege:

### Variante A: Webview fragt Extension, Extension fragt LSP

Sauberer, aber benötigt Custom LSP Request.

### Variante B: Extension berechnet Summary nicht selbst, sondern zeigt nur bereits verfügbare LSP-Daten

Für den Anfang begrenzt.

Empfehlung: **Custom Request** erst definieren, wenn nötig. In Phase VSC-10 kann die Extension zunächst über LSP Standarddaten und Problems/Document Symbols begrenzt arbeiten. Falls eine vollständige Summary nötig ist, Phase VSC-10.1 als Custom Request.

## Optionaler Custom LSP Request

Name:

```text
ilimap/mappingSummary
```

Request Params:

```java
public record IlimapMappingSummaryParams(String uri) {}
```

Response:

```java
public record IlimapMappingSummary(
        int inputCount,
        int outputCount,
        int ruleCount,
        int enumMapCount,
        int bagCount,
        int refCount,
        int errorCount,
        int warningCount,
        List<IlimapRuleSummary> rules
) {}
```

Rule Summary:

```java
public record IlimapRuleSummary(
        String id,
        String targetClass,
        int sourceCount,
        int assignmentCount,
        int bagCount,
        int refCount,
        String status
) {}
```

## Webview Inhalt MVP

```text
ILIMAP Mapping Overview

Inputs       1
Outputs      1
Rules        12
Enum maps     4
Bags          3
Refs          8
Diagnostics   2 warnings
```

Rule-Liste:

```text
✓ lfp3
✓ lfp3-nachfuehrung
! boflaeche
```

Coverage später nur anzeigen, wenn echte Coverage berechnet wird. Keine Fantasiewerte.

## Tests

```text
vscode/ilimap-vscode/test/mappingOverview.test.ts
```

Testfälle:

```text
- command is registered
- webview html contains strict CSP
- webview escapes user-provided labels
```

Java-Test bei Custom Request:

```text
src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapMappingSummaryServiceTest.java
```

## Sicherheit

Die Webview muss:

```text
- CSP verwenden
- keine externen Skripte laden
- keine ungeprüften HTML-Strings aus .ilimap direkt einfügen
- Theme-Farben von VS Code nutzen
- read-only starten
```

## Verifikation

```bash
./gradlew test
cd vscode/ilimap-vscode && npm run compile
```

Optional:

```bash
npm test
```

Manuelle VSCodium-Verifikation gemäss Abschnitt "Manuelle VSCodium-Verifikation ab VSC-6"
durchführen und Ergebnis dokumentieren.

## Funktionsfähiges Artefakt

Read-only Mapping Overview in VS Code.

## Definition of Done

- Webview ist optional und read-only.
- Keine Semantik im Webview-JavaScript.
- Sicherheitsregeln eingehalten.
- Keine erfundenen Coverage-Werte.
- Tests/Compile bestanden.

## Copy/Paste-Prompt für den Coding-Agenten

```text
Aufgabe Phase VSC-10:
Implementiere eine read-only VS-Code-Webview 'ILIMAP: Open Mapping Overview'. Die Webview soll eine Zusammenfassung des aktiven .ilimap-Dokuments anzeigen: Inputs, Outputs, Rules, Enum Maps, Bags, Refs und Diagnostics. Verwende, falls nötig, einen kleinen Java-Service für IlimapMappingSummary, aber implementiere keine Parserlogik in TypeScript. Die Webview muss CSP verwenden, Werte escapen und themenkompatibel sein. Keine editierbare Webview und keine erfundenen Coverage-Werte. Ergänze Tests/Compile-Verifikation. Wenn VSCodium unter /Applications/VSCodium.app/Contents/MacOS/VSCodium verfügbar ist, führe zusätzlich die manuelle VSCodium-Verifikation aus und dokumentiere das Ergebnis.
```

---

## 8. Langfristige mögliche Multi-Module-Struktur

Nicht im MVP umsetzen. Nach stabiler VS-Code-Unterstützung kann ein späteres Refactoring geprüft werden:

```text
settings.gradle
  include 'ilitransformer-core'
  include 'ilitransformer-cli'
  include 'ilimap-language-server'
  include 'vscode:ilimap-vscode'
```

Mögliche Zielstruktur:

```text
ilitransformer-core/
  src/main/java/guru/interlis/transformer/...

ilitransformer-cli/
  src/main/java/guru/interlis/transformer/app/CliMain.java

ilimap-language-server/
  src/main/java/guru/interlis/transformer/mapping/ilimap/ide/
  src/main/java/guru/interlis/transformer/mapping/ilimap/lsp/

vscode/ilimap-vscode/
  TypeScript Extension
```

Aber: Dieses Refactoring ist riskant und soll nicht mit dem Plugin-MVP vermischt werden.

---

## 9. Offene Fragen

Diese Fragen müssen vor oder während der Umsetzung entschieden und im ADR oder in Folge-ADRs dokumentiert werden.

### 9.1 LSP4J-Version

- Wird `org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0` verwendet?
- Gibt es API-Änderungen gegenüber `0.24.0`, die im Projekt relevant sind?
- Soll die Version strikt gepinnt werden?

Empfehlung: Version pinnen, keine dynamischen Versionen.

### 9.2 Server-Packaging

- Wird der Language Server als separater JAR gebaut?
- Wird der JAR in die VS-Code-Extension eingebettet?
- Oder verlangt die Extension initial einen konfigurierten `ilimap.server.jar`-Pfad?

Empfehlung MVP: konfigurierter JAR-Pfad, später eingebetteter JAR.

### 9.3 Fat JAR / Distribution

- Wird `shadowJar` eingeführt?
- Oder wird eine Gradle Application Distribution verwendet?
- Wie wird die Extension paketiert, damit Benutzer keinen lokalen Build brauchen?

Empfehlung: Erst Dev-Modus, später sauberes Packaging.

### 9.4 Ranges im bestehenden Diagnostic-Modell

- Soll `guru.interlis.transformer.diag.Diagnostic` langfristig Range-Objekte erhalten?
- Oder bleibt `IlimapIdeDiagnostic` dauerhaft separat?

Empfehlung MVP: Separat lassen. Später vereinheitlichen, wenn mehrere Tools Ranges brauchen.

### 9.5 Document Sync

- Reicht Full Sync dauerhaft?
- Braucht es bei sehr grossen Profilen Incremental Sync?

Empfehlung MVP: Full Sync. Erst messen, dann ändern.

### 9.6 Workspace-übergreifende Symbole

- Soll der LSP nur das aktuelle Dokument analysieren?
- Oder sollen mehrere `.ilimap`-Dateien im Workspace indexiert werden?
- Wie funktionieren zukünftige Includes, falls sie später kommen?

Empfehlung MVP: aktuelles Dokument. Workspace-Index erst später.

### 9.7 Kommentarerhalt im Formatter

- Der vorhandene Formatter dokumentiert, dass Kommentare nicht erhalten bleiben, weil der AST Kommentare nicht speichert.
- Soll VS Code Format-on-Save standardmässig deaktiviert bleiben, solange Kommentare verloren gehen?

Empfehlung: Format-on-Save nicht automatisch aktivieren. Benutzer kann manuell formatieren.

### 9.8 Model-aware Features

- Soll der LSP lokale und HTTP-Modeldirs laden?
- Wie werden HTTP-Modelle gecacht?
- Wie werden Fehler beim Modellladen in Diagnostics übersetzt?
- Wie verhindert man, dass der Editor bei langsamen Modeldirs blockiert?

Empfehlung: Model-aware Features erst nach stabilem LSP-MVP.

### 9.9 Webview

- Soll die Webview direkt über Custom LSP Requests Daten holen?
- Oder soll die Extension eine eigene Summary aus Document Symbols und Diagnostics bauen?
- Soll die Webview später editierbar werden?

Empfehlung: read-only starten; Custom Request nur, wenn nötig.

### 9.10 Sirius Web

- Soll später eine EMF-Projektion ergänzt werden?
- Soll `IlimapAnalysisService` als Grundlage für Sirius-Web-Diagnostics dienen?
- Bleibt `.ilimap` kanonisch?

Empfehlung: `.ilimap` bleibt kanonisch. Sirius Web später als zusätzliche Projektion.

### 9.11 Reloadability / Developer Experience

- Soll für den MVP nur ein manueller Restart-Command umgesetzt werden?
- Oder soll die Extension zusätzlich den Dev-JAR beobachten und den Language Server automatisch neu starten?
- Wird ein Fat-JAR via Shadow eingeführt oder reicht vorerst eine Application-Distribution?
- Soll die Extension langfristig eine gebündelte JRE enthalten oder immer `java` aus dem System verwenden?
- Soll `npm run watch` nur den Extension-Client beobachten oder auch spätere Webview-Bundles parallel bauen?

Empfehlung MVP: manueller Restart-Command, `npm run watch` für den TypeScript-Client, Dev-JAR-Copy-Task für Java. Automatischer JAR-Watcher und gebündelte JRE erst später.

---

## 10. Akzeptanzkriterien für ein erstes brauchbares Plugin

Ein erstes produktiv nutzbares Plugin ist erreicht, wenn folgende Punkte funktionieren:

```text
- .ilimap-Dateien werden erkannt.
- Syntaxhighlighting funktioniert.
- Java-LSP startet aus VS Code.
- Syntax- und Semantic-Diagnostics erscheinen im Problems Panel.
- Diagnostics aktualisieren sich live bei `didChange`, auch ohne Speichern.
- `ILIMAP: Restart Language Server` stoppt und startet den Java-LSP sauber.
- `npm run watch` ist für den VS-Code-Client dokumentiert und funktionsfähig.
- Document Formatting funktioniert manuell.
- Outline zeigt mapping/input/output/enum/rule/bag/ref.
- Folding funktioniert.
- Completion für Keywords, input/output/rule/enum IDs funktioniert.
- Go-to-definition für input/output/rule/enum funktioniert.
- Hover zeigt sinnvolle Kurzinfos.
```

Nicht erforderlich für 0.1/1.0-MVP:

```text
- vollständige INTERLIS-Attributcompletion
- editierbare Webview
- Sirius Web
- Multi-Module-Gradle
- Marketplace Packaging
```

---

## 11. Empfohlene Commit-Reihenfolge

Jede Phase soll ein eigener Commit sein:

```text
1. docs: decide ilimap vscode lsp architecture
2. ilimap-ide: add analysis service for editor integrations
3. ilimap-lsp: add minimal lsp4j diagnostics server
4. vscode: add ilimap extension skeleton
5. vscode: add reloadable development workflow
6. ilimap-lsp: support document formatting
7. ilimap-lsp: add document symbols and folding
8. ilimap-lsp: add completion MVP
9. ilimap-lsp: add hover and definition
10. ilimap-lsp: add quick fixes
11. ilimap-ide: add model-aware completion
12. vscode: add read-only mapping overview webview
```

Commit-Message muss gemäss `docs/agent/COMMIT_POLICY.md` erfolgen:

```text
<area>: <imperative summary>

Why:
- ...

What:
- ...

Verification:
- <exact command>: passed
```

---

## 12. Schlussbemerkung

Der wichtigste technische Erfolgsfaktor ist nicht LSP4J selbst, sondern die saubere Trennung:

```text
ilimap-core  = Sprache und Semantik
ilimap-ide   = editorunabhängige IDE-Funktionen
ilimap-lsp   = LSP-Protokolladapter
vscode       = dünner Client und UI
```

Wenn diese Trennung eingehalten wird, entsteht kein Wegwerf-Plugin, sondern eine wiederverwendbare Grundlage für VS Code, Webview, CLI-Reports und später Sirius Web.
