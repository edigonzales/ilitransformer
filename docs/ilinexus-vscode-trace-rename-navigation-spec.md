# Coding-Agent-Spec: ilimap VS Code Extension

## Thema

Implementiere drei Poweruser-Features fuer `ilinexus` / `ilitransformer`:

1. Source-to-Target Trace
2. Rename Refactoring
3. Echte bidirektionale Navigation zwischen Editor, Mapping Overview und Mapping Explorer

Diese Spec ist als Arbeitsauftrag fuer einen LLM Coding Agent gedacht. Arbeite iterativ, halte die bestehende Architektur ein, und vermeide grosse Nebenrefactorings.

## Repository-Kontext

Repository: `https://github.com/edigonzales/ilinexus`

Aktuelle Architektur:

- Java CLI/Engine und Java Language Server im Root-Projekt.
- VS-Code-Extension unter `vscode/ilimap-vscode`.
- Die Extension ist bewusst duenn und delegiert Parsing, Validation, Formatting, Semantic Analysis und IDE-Features an den Java-LSP.
- Die Mapping Overview ist eine read-only Webview mit Summary, Diagnostics, Class Coverage, Rule Coverage Matrix, Source Usage, Flow Map und Rule Inspector.

Wichtige bestehende Dateien:

- `build.gradle`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapLanguageServer.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapTextDocumentService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapWorkspaceService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapMappingSummaryService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapRuleDetailService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapDefinitionService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapHoverService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapCodeActionService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapCodeLensService.java`
- `vscode/ilimap-vscode/src/extension.ts`
- `vscode/ilimap-vscode/src/commands.ts`
- `vscode/ilimap-vscode/src/overview/mappingExplorerProvider.ts`
- `vscode/ilimap-vscode/src/overview/mappingOverviewSelectionSync.ts`
- `vscode/ilimap-vscode/src/webview/mappingOverviewPanel.ts`
- `vscode/ilimap-vscode/src/webview/mappingOverviewHtml.ts`
- `vscode/ilimap-vscode/src/webview/mappingOverviewMessages.ts`

Bestehende LSP-Funktionen:

- Diagnostics on open/change/save
- Formatting
- Document symbols
- Folding ranges
- Completion
- Hover
- Go to Definition
- Code Actions
- CodeLens
- Custom requests:
  - `ilimap/mappingSummary`
  - `ilimap/validateMapping`
  - `ilimap/ruleDetail`

## Leitplanken

- Die `.ilimap`-Datei bleibt die einzige Quelle der Wahrheit.
- Die Mapping Overview bleibt grundsaetzlich read-only. Sie darf aber Aktionen ausloesen, die ueber VS Code / LSP saubere TextEdits im Editor anwenden.
- Kein HTML-Formular, das eine zweite Mapping-Repraesentation fuehrt.
- Bestehende Analysepfade wiederverwenden:
  - `IlimapAnalysis`
  - `IlimapDocumentStore`
  - AST-Klassen unter `mapping.ilimap.ast`
  - `IlimapPositionResolver`
  - `IlimapSymbolReferenceResolver`
  - `IlimapMappingSummaryService`
  - `IlimapRuleDetailService`
- Regex-basierte `alias.member`-Erkennung darf kurzfristig weiterverwendet werden, soll aber in einen eigenen Dependency-Service ausgelagert werden, damit Trace, Source Usage, Rename und Hover dieselbe Logik nutzen.
- Tests zuerst auf IDE-Service-Ebene, danach LSP-Mapping und VS-Code-Client-Tests.
- Achte auf unsaved documents. LSP-Requests muessen auf dem `DocumentStore` arbeiten, nicht direkt auf der gespeicherten Datei.

## Zielbild

Poweruser sollen in grossen `.ilimap`-Profilen sofort beantworten koennen:

- Woher kommt dieses Zielattribut?
- Welche Source-Attribute, Rollen, EnumMaps und Funktionen beeinflussen diesen Wert?
- Wo wird dieses Source-Attribut ueberall verwendet?
- Welche Regel erzeugt welches Zielobjekt?
- Welche Refs verbinden Regeln miteinander?
- Kann ich lokale Symbole sicher umbenennen?
- Kann ich vom Editor in die Overview und von der Overview zurueck zum exakten Editor-Ort springen?

---

# Feature 1: Source-to-Target Trace

## User Stories

1. Als Mapper klicke ich in der Rule Coverage Matrix auf ein Zielattribut und sehe, aus welchen Source-Attributen, Rollen, EnumMaps, Literalen und Funktionen der Wert entsteht.
2. Als Mapper klicke ich im Editor auf `src.Name` und sehe, welche Zielattribute dieses Source-Attribut verwenden.
3. Als Mapper klicke ich auf eine Rule und sehe einen kompakten Datenfluss:
   `input -> source class -> source member -> expression -> target attribute -> target class -> output`.
4. Als Reviewer sehe ich, ob ein Zielattribut direkt kopiert, berechnet, per Default gesetzt, per EnumMap abgeleitet, in einem Bag erzeugt oder als Loss dokumentiert wird.

## UI-Verhalten in der Mapping Overview

Erweitere die Rule Coverage Matrix:

- Spalte `Attribute` bleibt klickbar.
- Klick auf ein Zielattribut oeffnet einen Trace Inspector direkt unter der betreffenden Rule oder als sticky Detailbereich unterhalb der Matrix.
- Trace Inspector zeigt:
  - Target:
    - output id
    - target class
    - target attribute
    - type
    - cardinality
    - mandatory
    - assignment kind
  - Expression:
    - original expression
    - expression location
  - Dependencies:
    - source attributes
    - source roles
    - enum maps
    - functions
    - constants/literals
  - Reverse usage:
    - other target attributes using same source members
  - Navigation links:
    - jump to assignment
    - jump to source declaration
    - jump to enum map
    - jump to target rule

Erweitere Source Usage:

- Klick auf Source-Attribut oder Source-Rolle oeffnet Trace Inspector im Modus `sourceMember`.
- Zeigt alle Regeln und Zielattribute, die dieses Member verwenden.

Erweitere Flow Map:

- Klick auf Rule Node oeffnet Rule Trace.
- Klick auf Source Class Node filtert oder markiert alle Regeln, die diese Klasse verwenden.
- Klick auf Target Class Node filtert oder markiert alle Regeln, die diese Klasse erzeugen.

## Custom LSP Requests

Fuege folgende Requests hinzu.

### `ilimap/trace`

Parameter:

```java
package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapTraceParams(
        String uri,
        String mode,
        String ruleId,
        String targetAttribute,
        String sourceAlias,
        String sourceMember,
        IlimapIdePosition position
) {}
```

`mode`:

- `targetAttribute`
- `sourceMember`
- `rule`
- `position`

Rueckgabe:

```java
public record IlimapTraceSummary(
        boolean available,
        String message,
        String mode,
        String ruleId,
        IlimapTraceTarget target,
        IlimapTraceExpression expression,
        List<IlimapTraceDependency> dependencies,
        List<IlimapTraceUsage> usages,
        List<IlimapTraceStep> steps,
        List<IlimapDiagnosticSummary> diagnostics
) {
    public static IlimapTraceSummary unavailable(String mode, String message) { ... }
}
```

Zusatzrecords:

```java
public record IlimapTraceTarget(
        String outputId,
        String targetClass,
        String targetAttribute,
        String type,
        String cardinality,
        boolean mandatory,
        String assignmentKind,
        IlimapOverviewLocation location
) {}

public record IlimapTraceExpression(
        String text,
        String kind,
        IlimapOverviewLocation location
) {}

public record IlimapTraceDependency(
        String kind,
        String alias,
        String member,
        String sourceClass,
        String enumMapId,
        String functionName,
        String literal,
        IlimapOverviewLocation location,
        IlimapOverviewLocation definitionLocation
) {}

public record IlimapTraceUsage(
        String ruleId,
        String targetOutput,
        String targetClass,
        String targetAttribute,
        String context,
        String expression,
        IlimapOverviewLocation location
) {}

public record IlimapTraceStep(
        String nodeId,
        String kind,
        String label,
        String detail,
        String status,
        IlimapOverviewLocation location
) {}
```

### `ilimap/usages`

Optional, falls `ilimap/trace` zu gross wird.

Parameter:

```java
public record IlimapUsageParams(
        String uri,
        String sourceAlias,
        String sourceMember,
        String sourceClass,
        String ruleId
) {}
```

Rueckgabe:

```java
public record IlimapUsageSummary(
        boolean available,
        String message,
        List<IlimapTraceUsage> usages
) {}
```

## Java Backend: neue Klassen

### `IlimapExpressionDependencyService`

Pfad:

`src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapExpressionDependencyService.java`

Zweck:

- Zentrale Extraktion von Dependencies aus Expressions.
- Ersetzt mittelfristig die duplizierten Regex-Methoden in `IlimapMappingSummaryService` und `IlimapRuleDetailService`.

Methoden:

```java
public final class IlimapExpressionDependencyService {
    public List<IlimapExpressionDependencySummary> dependencies(
            IlimapAnalysis analysis,
            IlimapExpressionText expression,
            IlimapRuleBlock rule);

    public List<IlimapExpressionDependencySummary> dependencies(
            String expressionText);

    public List<IlimapExpressionDependencySummary> dependenciesWithLocations(
            IlimapAnalysis analysis,
            IlimapExpressionText expression,
            IlimapRuleBlock rule);

    public Optional<SourceBinding> sourceForAlias(
            IlimapRuleBlock rule,
            String alias);

    public Optional<IlimapClassInfo> sourceClass(
            IlimapAnalysis analysis,
            SourceBinding source);
}
```

Hinweis:

- Kurzfristig darf `dependencies(String)` intern die bestehende Regex-Logik aus `IlimapRuleDetailService.extractDependencies` verwenden.
- `dependenciesWithLocations(...)` soll fuer jedes erkannte `alias.member` die Range des Members und wenn moeglich die Range der Source-Deklaration liefern.
- Lege `SourceBinding` als public package-private record im `ide`-Package an oder als eigenes Record:

```java
record SourceBinding(
        String alias,
        List<String> inputIds,
        String sourceClass,
        IlimapSourceRange range
) {}
```

Falls bereits interne `SourceBinding` Records existieren, nicht einfach global verschieben ohne Tests. Besser: neues `IlimapSourceBinding` Record einfuehren.

### `IlimapTraceService`

Pfad:

`src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceService.java`

Zweck:

- Erzeugt Source-to-Target- und Target-to-Source-Traces aus `IlimapAnalysis`.

Konstruktor:

```java
public IlimapTraceService() {
    this(new IlimapExpressionDependencyService(), new IlimapDiagnosticOwnerResolver());
}
```

Methoden:

```java
public IlimapTraceSummary trace(IlimapAnalysis analysis, IlimapTraceParams params);

public IlimapTraceSummary traceTargetAttribute(
        IlimapAnalysis analysis,
        String ruleId,
        String targetAttribute);

public IlimapTraceSummary traceSourceMember(
        IlimapAnalysis analysis,
        String ruleId,
        String sourceAlias,
        String sourceMember);

public IlimapTraceSummary traceRule(
        IlimapAnalysis analysis,
        String ruleId);

public IlimapTraceSummary tracePosition(
        IlimapAnalysis analysis,
        IlimapIdePosition position);

public List<IlimapTraceUsage> usagesOfSourceMember(
        IlimapAnalysis analysis,
        String sourceAlias,
        String sourceMember,
        String ruleIdOrNull);
```

Private Helper:

```java
private Optional<IlimapRuleBlock> findRule(IlimapAnalysis analysis, String ruleId);

private Optional<IlimapAssignment> findDirectAssignment(
        IlimapRuleBlock rule,
        String targetAttribute);

private List<IlimapAssignment> assignmentsInRule(IlimapRuleBlock rule);

private List<IlimapAssignment> assignmentsInBag(IlimapBagBlock bag);

private Optional<IlimapCoverageAttributeSummary> targetAttributeInfo(
        IlimapAnalysis analysis,
        IlimapRuleBlock rule,
        String targetAttribute);

private List<IlimapTraceDependency> toTraceDependencies(
        IlimapAnalysis analysis,
        IlimapRuleBlock rule,
        IlimapExpressionText expression);

private List<IlimapTraceStep> buildTraceSteps(...);
```

Regeln:

- `traceTargetAttribute` betrachtet zuerst direkte `assign` und `defaults` innerhalb der Rule.
- Bag-Zuweisungen werden mit `context = "bag"` markiert.
- Refs werden mit `context = "ref"` markiert.
- Losses werden mit `context = "loss"` markiert.
- Wenn ein mandatory Target fehlt, muss der Trace trotzdem verfuegbar sein, aber `expression = null`, `dependencies = []`, `status = missing`.

### `IlimapTraceParams` und Records

Lege alle Trace-Records im `ide`-Package ab:

- `IlimapTraceParams.java`
- `IlimapTraceSummary.java`
- `IlimapTraceTarget.java`
- `IlimapTraceExpression.java`
- `IlimapTraceDependency.java`
- `IlimapTraceUsage.java`
- `IlimapTraceStep.java`

Die Records sollen JSON-freundlich sein:

- keine Optional-Felder in Records
- fuer fehlende Werte `null` oder leere Listen
- stabile Feldnamen, passend zu TypeScript Interfaces

## Java LSP Integration

### `IlimapLanguageServer`

Ergaenze Imports:

```java
import guru.interlis.transformer.mapping.ilimap.ide.IlimapTraceParams;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapTraceSummary;
```

Ergaenze Request:

```java
@JsonRequest(value = "ilimap/trace", useSegment = false)
public CompletableFuture<IlimapTraceSummary> trace(IlimapTraceParams params) {
    return textDocumentService.trace(params);
}
```

### `IlimapTextDocumentService`

Felder:

```java
private final IlimapTraceService traceService;
```

Konstruktoren:

- In Hauptkonstruktor `new IlimapTraceService()` initialisieren.
- Fuer Tests optional Constructor Injection anbieten.

Methode:

```java
public CompletableFuture<IlimapTraceSummary> trace(IlimapTraceParams params) {
    if (params == null || params.uri() == null || params.uri().isBlank()) {
        return CompletableFuture.completedFuture(
                IlimapTraceSummary.unavailable("unknown", "No ILIMAP document URI provided."));
    }
    String uri = params.uri();
    if (documentStore.get(uri).isEmpty()) {
        return CompletableFuture.completedFuture(
                IlimapTraceSummary.unavailable(params.mode(), "No open ILIMAP document for URI: " + uri));
    }
    IlimapAnalysis analysis = analysisForCompletion(uri);
    return CompletableFuture.completedFuture(traceService.trace(analysis, params));
}
```

Bei Modellinformationen:

- Wenn die Trace-Details Target-Attribut-Typen brauchen, `analysisForCompletion(uri)` verwenden, weil dort model-aware Cache genutzt wird, falls vorhanden.
- Kein zwangslaeufiges Model-Loading im Trace-Request starten. Wenn Models nicht geladen sind, Trace trotzdem auf AST/Expression-Ebene liefern und `type/cardinality` leer lassen.

## VS-Code Client: TypeScript Interfaces

Datei:

`vscode/ilimap-vscode/src/webview/mappingOverviewMessages.ts`

Ergaenze:

```ts
export const traceRequest = 'ilimap/trace';

export interface IlimapTraceParams {
  uri: string;
  mode: 'targetAttribute' | 'sourceMember' | 'rule' | 'position' | string;
  ruleId?: string;
  targetAttribute?: string;
  sourceAlias?: string;
  sourceMember?: string;
  position?: { line: number; character: number };
}

export interface IlimapTraceSummary {
  available: boolean;
  message: string;
  mode: string;
  ruleId?: string;
  target?: IlimapTraceTarget;
  expression?: IlimapTraceExpression;
  dependencies: IlimapTraceDependency[];
  usages: IlimapTraceUsage[];
  steps: IlimapTraceStep[];
  diagnostics: IlimapDiagnosticSummary[];
}

export interface IlimapTraceTarget {
  outputId?: string;
  targetClass?: string;
  targetAttribute?: string;
  type?: string;
  cardinality?: string;
  mandatory?: boolean;
  assignmentKind?: string;
  location?: IlimapLocation;
}

export interface IlimapTraceExpression {
  text: string;
  kind: string;
  location?: IlimapLocation;
}

export interface IlimapTraceDependency {
  kind: string;
  alias?: string;
  member?: string;
  sourceClass?: string;
  enumMapId?: string;
  functionName?: string;
  literal?: string;
  location?: IlimapLocation;
  definitionLocation?: IlimapLocation;
}

export interface IlimapTraceUsage {
  ruleId: string;
  targetOutput?: string;
  targetClass?: string;
  targetAttribute?: string;
  context: string;
  expression?: string;
  location?: IlimapLocation;
}

export interface IlimapTraceStep {
  nodeId?: string;
  kind: string;
  label: string;
  detail?: string;
  status?: string;
  location?: IlimapLocation;
}
```

## VS-Code Client: Webview Panel

Datei:

`vscode/ilimap-vscode/src/webview/mappingOverviewPanel.ts`

State erweitern:

```ts
activeTrace?: IlimapTraceSummary;
```

Wenn `MappingOverviewPanelState` in eigener Datei liegt, dort erweitern.

Message Handler erweitern:

```ts
if (message && typeof message === 'object' && (message as { type?: unknown }).type === 'requestTrace') {
  await requestTrace(state, message as TraceMessage, outputChannel);
  return;
}
```

Neue Funktion:

```ts
async function requestTrace(
  state: MappingOverviewPanelState,
  message: {
    mode?: unknown;
    ruleId?: unknown;
    targetAttribute?: unknown;
    sourceAlias?: unknown;
    sourceMember?: unknown;
  },
  outputChannel: vscode.OutputChannel
): Promise<void> {
  const client = getLanguageClient();
  if (!client) {
    state.activeTrace = unavailableTrace('ilimap language server is not running.');
    renderPanel(state, { refreshState: 'idle', lastUpdated: state.lastUpdated });
    return;
  }

  const params: IlimapTraceParams = {
    uri: state.uri,
    mode: typeof message.mode === 'string' ? message.mode : 'targetAttribute',
    ruleId: typeof message.ruleId === 'string' ? message.ruleId : undefined,
    targetAttribute: typeof message.targetAttribute === 'string' ? message.targetAttribute : undefined,
    sourceAlias: typeof message.sourceAlias === 'string' ? message.sourceAlias : undefined,
    sourceMember: typeof message.sourceMember === 'string' ? message.sourceMember : undefined
  };

  try {
    state.activeTrace = await client.sendRequest<IlimapTraceSummary>(traceRequest, params);
    renderPanel(state, { refreshState: 'idle', lastUpdated: state.lastUpdated });
  } catch (error) {
    outputChannel.appendLine(`Failed to request ilimap trace: ${errorMessage(error)}`);
    state.activeTrace = unavailableTrace(errorMessage(error));
    renderPanel(state, { refreshState: 'error', errorMessage: 'Failed to load trace.', lastUpdated: state.lastUpdated });
  }
}
```

`renderPanel(...)` erweitern:

```ts
state.panel.webview.html = renderMappingOverviewHtml(
  summary,
  nonce(),
  renderState,
  Array.from(state.ruleDetailsById.values()),
  state.activeRuleId,
  state.activeTrace
);
```

## VS-Code Client: HTML Rendering

Datei:

`vscode/ilimap-vscode/src/webview/mappingOverviewHtml.ts`

Signatur erweitern:

```ts
export function renderMappingOverviewHtml(
  summary: IlimapMappingSummary,
  nonce: string,
  renderState?: MappingOverviewRenderState,
  ruleDetails?: IlimapRuleDetailSummary | IlimapRuleDetailSummary[],
  activeRuleId?: string,
  activeTrace?: IlimapTraceSummary
): string
```

Rule Coverage Row:

- `renderCoverageAttributeRow(...)` soll fuer jedes Attribute mit `ruleId` und `attribute.name` einen Trace-Link rendern.

Beispiel:

```ts
const traceLink = `<a href="#" class="trace-link" data-action="request-trace" data-trace-mode="targetAttribute" data-rule-id="${escapeAttribute(ruleId)}" data-target-attribute="${escapeAttribute(attribute.name)}">Trace</a>`;
```

Im Attribute-Cell:

```ts
<td>${renderNavName(attribute.name, attribute)} ... ${traceLink}</td>
```

Source Usage Member:

- `renderUsageMember(...)` braucht optional Kontext `sourceClass`, `aliases`, `inputIds`.
- Wenn nur mehrere Aliase vorhanden sind, zuerst ohne Alias trace request schicken oder einen kleinen Dropdown/Mehrfachliste vermeiden: nimm ersten Alias und zeige alle Usages ueber `sourceClass + member` im Backend.

Click Handler erweitern:

```js
const traceTarget = event.target.closest('[data-action="request-trace"]');
if (traceTarget) {
  event.preventDefault();
  vscode.postMessage({
    type: 'requestTrace',
    mode: traceTarget.getAttribute('data-trace-mode'),
    ruleId: traceTarget.getAttribute('data-rule-id'),
    targetAttribute: traceTarget.getAttribute('data-target-attribute'),
    sourceAlias: traceTarget.getAttribute('data-source-alias'),
    sourceMember: traceTarget.getAttribute('data-source-member')
  });
  return;
}
```

Neuer Renderer:

```ts
function renderTraceInspector(trace?: IlimapTraceSummary): string {
  if (!trace) {
    return '';
  }
  if (!trace.available) {
    return `<section class="trace-inspector"><h2>Trace</h2><p class="empty">${escapeHtml(trace.message)}</p></section>`;
  }
  return `<section class="trace-inspector">
    <h2>Trace</h2>
    ${renderTraceTarget(trace.target)}
    ${renderTraceExpression(trace.expression)}
    ${renderTraceDependencies(trace.dependencies)}
    ${renderTraceUsages(trace.usages)}
    ${renderTraceSteps(trace.steps)}
  </section>`;
}
```

Einbindung:

```ts
${renderTraceInspector(activeTrace)}
${renderRuleInspectors(...)}
```

CSS:

- `.trace-inspector`
- `.trace-grid`
- `.trace-step`
- `.trace-dependency`
- `.trace-usage`
- `.trace-link`

Stil:

- VS-Code Theme Variables verwenden.
- Keine grossen Cards; kompakte, scannbare Listen/Tables.

## Tests fuer Trace

Java Unit Tests:

- `IlimapExpressionDependencyServiceTest`
  - erkennt `src.Name`
  - erkennt mehrere Dependencies
  - ignoriert `enumMap(...)` als Source-Alias
  - erkennt `enumMap(foo, BarMap)`
  - ignoriert Text in Strings soweit aktuell moeglich
- `IlimapTraceServiceTest`
  - trace direct copy assignment
  - trace computed expression
  - trace enumMap expression
  - trace missing mandatory target attribute
  - trace source member reverse usages
  - trace bag assignment
  - trace ref sourceRef

LSP Tests:

- `IlimapTextDocumentServiceTraceTest`
  - request with missing uri returns unavailable
  - request for open doc returns trace
  - unsaved document changes are reflected

VS-Code Tests:

- `mappingOverviewHtml.test.js`
  - coverage row contains trace link
  - trace inspector renders target, dependencies, usages
  - click message shape is stable if existing test setup supports DOM execution

Akzeptanz:

- Von einer Coverage-Matrix-Zeile kann ein Trace geladen werden.
- Trace springt via bestehenden `navigateToLocation` Links an die richtige Stelle.
- Trace funktioniert ohne geladenes Modell teilweise, mit geladenem Modell inklusive Type/Cardinality.

---

# Feature 2: Rename Refactoring

## User Stories

1. Als Mapper benenne ich ein `input` um und alle `from <input>`-Referenzen werden angepasst.
2. Als Mapper benenne ich ein `output` um und alle `target <output>`-Referenzen werden angepasst.
3. Als Mapper benenne ich eine `rule` um und alle `target rule <ruleId>`-Referenzen werden angepasst.
4. Als Mapper benenne ich eine `enum` um und alle `enumMap(..., EnumId)`-Referenzen werden angepasst.
5. Als Mapper benenne ich einen Source-Alias innerhalb einer Rule um und alle `alias.member`-Verwendungen in dieser Rule werden angepasst.
6. Als Mapper bekomme ich kein Rename-Angebot fuer INTERLIS-Klassen, Modellnamen, Zielattribute oder Source-Attribute, weil diese nicht in der `.ilimap`-Datei definiert sind.

## Scope

Unterstuetzt:

- `input` IDs
- `output` IDs
- `rule` IDs
- `enum` IDs
- Source aliases
- Join aliases, falls bereits als Symbol im `IlimapSymbolReferenceResolver` vorhanden
- `bag` IDs und `ref` IDs optional, nur wenn Resolver und AST stabile Ranges liefern

Nicht unterstuetzt:

- INTERLIS model names
- INTERLIS class names
- INTERLIS target attributes
- INTERLIS source attributes
- File paths
- SQL query ids, ausser sie sind bereits als Symbol modelliert

## LSP Capabilities

### `IlimapLanguageServer.initialize(...)`

Ergaenze:

```java
capabilities.setRenameProvider(Either.forRight(new RenameOptions(true)));
```

Imports:

```java
import org.eclipse.lsp4j.RenameOptions;
```

## Java Backend: neue Klassen

### `IlimapRenameService`

Pfad:

`src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapRenameService.java`

Zweck:

- Validiert Rename-Ort und neuen Namen.
- Sammelt alle TextEdits.
- Arbeitet rein auf einer offenen `IlimapAnalysis`.

Konstruktor:

```java
public IlimapRenameService() {
    this(new IlimapSymbolReferenceResolver(), new IlimapReferenceSearchService());
}
```

Methoden:

```java
public Optional<IlimapRenamePrepareResult> prepareRename(
        IlimapAnalysis analysis,
        IlimapIdePosition position);

public IlimapRenameResult rename(
        IlimapAnalysis analysis,
        IlimapIdePosition position,
        String newName);

private IlimapRenameValidation validateNewName(
        IlimapResolvedSymbol resolved,
        String newName);

private List<IlimapTextEdit> editsFor(
        IlimapAnalysis analysis,
        IlimapResolvedSymbol resolved,
        String newName);
```

Records:

```java
public record IlimapRenamePrepareResult(
        IlimapIdeRange range,
        String placeholder
) {}

public record IlimapRenameResult(
        boolean available,
        String message,
        List<IlimapTextEdit> edits
) {
    public static IlimapRenameResult unavailable(String message) { ... }
}

record IlimapRenameValidation(boolean valid, String message) {}
```

### `IlimapReferenceSearchService`

Pfad:

`src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapReferenceSearchService.java`

Zweck:

- Findet alle Referenzranges fuer ein resolved symbol.
- Wird spaeter auch fuer "Find All References" verwendbar.

Methoden:

```java
public final class IlimapReferenceSearchService {
    public List<IlimapIdeRange> references(
            IlimapAnalysis analysis,
            IlimapResolvedSymbol resolved);

    public List<IlimapIdeRange> inputReferences(
            IlimapAnalysis analysis,
            String inputId);

    public List<IlimapIdeRange> outputReferences(
            IlimapAnalysis analysis,
            String outputId);

    public List<IlimapIdeRange> ruleReferences(
            IlimapAnalysis analysis,
            String ruleId);

    public List<IlimapIdeRange> enumMapReferences(
            IlimapAnalysis analysis,
            String enumMapId);

    public List<IlimapIdeRange> sourceAliasReferences(
            IlimapAnalysis analysis,
            IlimapRuleBlock scopeRule,
            String alias);
}
```

Erwartete Referenzen:

- `input`:
  - Deklaration `input <id>`
  - `source <alias> from <id>`
  - `bag ... from <alias> in <id>` falls Syntax so modelliert ist
- `output`:
  - Deklaration `output <id>`
  - `target <id> class "..."`
- `rule`:
  - Deklaration `rule <id>`
  - `ref ... target rule <id> ...`
- `enum`:
  - Deklaration `enum <id>`
  - `enumMap(..., <id>)`
  - Optional: `enumMap(..., "<id>")` nur dann, wenn Diagnose/Quickfix bereits String-Refs erkennt und Range exakt ist
- Source alias:
  - Deklaration `source <alias>`
  - `where`, `join`, `identity`, `assign`, `defaults`, `bag`, `ref.sourceRef`, `loss.sourcePath`, `loss.when` innerhalb derselben Rule
  - Nur `alias.member`, nicht zufaellige Substrings

Wichtig:

- Fuer Source-Alias-Rename muss der Scope auf die Rule begrenzt sein.
- Wenn zwei Source-Aliase in verschiedenen Rules gleich heissen, darf nur die aktuelle Rule geaendert werden.
- Wenn ein neuer Name mit bestehendem Symbol im selben Scope kollidiert, Rename ablehnen.

### `IlimapRenameValidator`

Optional eigene Klasse, wenn `IlimapRenameService` zu gross wird.

Methoden:

```java
public IlimapRenameValidation validate(
        IlimapAnalysis analysis,
        IlimapResolvedSymbol resolved,
        String newName);

private boolean isValidSymbolId(String newName);
private boolean isValidAliasId(String newName);
private boolean collidesInScope(...);
```

Nutze vorhandene Regeln:

- `IlimapIdentifierRules.isValidSymbolId(...)`
- `IlimapIdentifierRules.isValidAliasId(...)`

## Java LSP Integration

### `IlimapTextDocumentService`

Feld:

```java
private final IlimapRenameService renameService;
```

Imports fuer LSP:

```java
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.RenameParams;
```

Methoden:

```java
@Override
public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
        PrepareRenameParams params) {
    String uri = params.getTextDocument().getUri();
    if (documentStore.get(uri).isEmpty()) {
        return CompletableFuture.completedFuture(null);
    }
    IlimapAnalysis analysis = analysisForCompletion(uri);
    IlimapIdePosition position = new IlimapIdePosition(
            params.getPosition().getLine(),
            params.getPosition().getCharacter());
    return CompletableFuture.completedFuture(
            renameService.prepareRename(analysis, position)
                    .map(result -> Either3.<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>forSecond(
                            new PrepareRenameResult(
                                    rangeMapper.toLspRange(result.range()),
                                    result.placeholder())))
                    .orElse(null));
}
```

Je nach LSP4J-Version ist die genaue `Either3`-Signatur anders. Implementiere passend zur vorhandenen Dependency `org.eclipse.lsp4j:1.0.0`.

Rename:

```java
@Override
public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    String uri = params.getTextDocument().getUri();
    if (documentStore.get(uri).isEmpty()) {
        return CompletableFuture.completedFuture(new WorkspaceEdit());
    }
    IlimapAnalysis analysis = analysisForCompletion(uri);
    IlimapIdePosition position = new IlimapIdePosition(
            params.getPosition().getLine(),
            params.getPosition().getCharacter());
    IlimapRenameResult result = renameService.rename(analysis, position, params.getNewName());
    if (!result.available()) {
        return CompletableFuture.failedFuture(new IllegalArgumentException(result.message()));
    }
    List<TextEdit> edits = result.edits().stream().map(this::toLspTextEdit).toList();
    return CompletableFuture.completedFuture(new WorkspaceEdit(Map.of(uri, edits)));
}
```

## VS-Code Client

Kein expliziter Command notwendig, wenn LSP-Rename korrekt registriert ist. VS Code nutzt `F2` / `editor.action.rename`.

Optional Commands in `package.json`:

- Nicht noetig fuer MVP.

Optional Context Menu:

- Nicht noetig.

## Tests fuer Rename

Java Unit Tests:

- `IlimapRenameServiceTest`
  - prepare rename on input id
  - prepare rename on output id
  - prepare rename on rule id
  - prepare rename on enum id
  - prepare rename on source alias
  - no prepare rename on target class string
  - no prepare rename on target attribute
  - reject invalid symbol id with spaces
  - reject invalid alias id with hyphen for source alias
  - reject collision with existing input/output/rule/enum in same namespace
  - source alias rename only touches current rule
  - enum map rename updates symbolic refs

- `IlimapReferenceSearchServiceTest`
  - input references
  - output references
  - rule references
  - enumMap references
  - sourceAlias references in expressions
  - sourceAlias references in join/ref/loss expressions

LSP Tests:

- `IlimapTextDocumentServiceRenameTest`
  - `prepareRename` returns range and placeholder
  - `rename` returns WorkspaceEdit for one document
  - invalid rename returns failed future or empty edit according to chosen behavior

Akzeptanz:

- `F2` funktioniert fuer lokale `.ilimap`-Symbole.
- Keine accidental renames in Strings, Kommentaren oder anderen Rules.
- Rename funktioniert auf ungespeicherten Dokumenten.

---

# Feature 3: Echte bidirektionale Navigation

## Problem

Heute existiert bereits:

- Overview -> Editor Navigation ueber `data-nav-line`.
- Editor -> Overview fuer Rules via `MappingOverviewSelectionSync`.
- Mapping Explorer -> Editor.
- CodeLens -> Overview.

Das ist gut, aber noch grob. Es synchronisiert primar Rules. Poweruser brauchen Navigation fuer:

- Target attributes
- Source aliases
- Source members
- Enum maps
- Refs
- Bags
- Diagnostics
- Flow Map nodes
- Trace nodes

## Zielbild

Jeder relevante Knoten hat eine stabile `nodeId`. Die `nodeId` kann in beide Richtungen aufgeloest werden:

- Editor-Position -> nodeId(s)
- nodeId -> Editor Location
- nodeId -> Overview Section
- nodeId -> related nodeIds

## NodeId-Konvention

Nutze und erweitere vorhandene `IlimapOverviewNodeIds`.

Vorgeschlagene IDs:

```text
input:<inputId>
output:<outputId>
enum:<enumId>
rule:<ruleId>
rule:<ruleId>:target
rule:<ruleId>:target:<targetAttribute>
rule:<ruleId>:source:<alias>
rule:<ruleId>:source:<alias>:member:<member>
rule:<ruleId>:assign:<targetAttribute>
rule:<ruleId>:bag:<bagId>
rule:<ruleId>:bag:<bagId>:assign:<targetAttribute>
rule:<ruleId>:ref:<refId>
rule:<ruleId>:loss:<index>
diagnostic:<code>:<line>:<character>
target:<outputId>:<qualifiedClassName>
source:<inputId>:<qualifiedClassName>
```

Regel:

- Bestehende NodeIds nicht brechen.
- Falls alte IDs existieren, kompatibel halten.
- Escape fuer `:` in qualified names vermeiden, indem qualified names am Ende stehen oder URL-encodiert werden.

## Java Backend: Navigation Index

### `IlimapNavigationService`

Pfad:

`src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapNavigationService.java`

Zweck:

- Liefert feingranulare Node-Informationen.
- Macht Editor <-> Overview stabiler.

Requests:

### `ilimap/nodeAtPosition`

Parameter:

```java
public record IlimapNodeAtPositionParams(
        String uri,
        IlimapIdePosition position
) {}
```

Rueckgabe:

```java
public record IlimapNavigationNode(
        String nodeId,
        String kind,
        String label,
        String detail,
        IlimapOverviewLocation location,
        List<String> relatedNodeIds
) {}
```

Methode:

```java
public Optional<IlimapNavigationNode> nodeAtPosition(
        IlimapAnalysis analysis,
        IlimapIdePosition position);
```

### `ilimap/navigationTarget`

Parameter:

```java
public record IlimapNavigationTargetParams(
        String uri,
        String nodeId
) {}
```

Rueckgabe:

```java
public record IlimapNavigationTarget(
        boolean available,
        String message,
        String nodeId,
        IlimapOverviewLocation location,
        List<IlimapNavigationNode> related
) {}
```

Methode:

```java
public IlimapNavigationTarget navigationTarget(
        IlimapAnalysis analysis,
        String nodeId);
```

### `IlimapNavigationService` Methoden

```java
public final class IlimapNavigationService {
    public Optional<IlimapNavigationNode> nodeAtPosition(
            IlimapAnalysis analysis,
            IlimapIdePosition position);

    public IlimapNavigationTarget targetForNodeId(
            IlimapAnalysis analysis,
            String nodeId);

    public List<IlimapNavigationNode> relatedNodes(
            IlimapAnalysis analysis,
            String nodeId);

    private Optional<IlimapNavigationNode> targetAttributeNodeAt(...);
    private Optional<IlimapNavigationNode> sourceMemberNodeAt(...);
    private Optional<IlimapNavigationNode> enumMapNodeAt(...);
    private Optional<IlimapNavigationNode> refNodeAt(...);
    private Optional<IlimapNavigationNode> bagNodeAt(...);
}
```

Implementation:

- Nutze `IlimapPositionResolver.smallestNodeAt(...)`.
- Wenn Position innerhalb `IlimapAssignment`:
  - links vom `=` -> target attribute node
  - rechts vom `=` -> expression/source member node, falls `alias.member`
- Wenn Position innerhalb `IlimapSourceStmt`:
  - alias -> source alias node
  - input id -> input node
  - class string -> source class pseudo-node
- Wenn Position innerhalb `IlimapTargetStmt`:
  - output id -> output node
  - class string -> target class pseudo-node
- Wenn Position innerhalb `IlimapRefBlock`:
  - ref id -> ref node
  - target rule id -> rule node
  - sourceRef expression -> source member node

## Java LSP Integration

### `IlimapLanguageServer`

Custom Requests:

```java
@JsonRequest(value = "ilimap/nodeAtPosition", useSegment = false)
public CompletableFuture<IlimapNavigationNode> nodeAtPosition(IlimapNodeAtPositionParams params) {
    return textDocumentService.nodeAtPosition(params);
}

@JsonRequest(value = "ilimap/navigationTarget", useSegment = false)
public CompletableFuture<IlimapNavigationTarget> navigationTarget(IlimapNavigationTargetParams params) {
    return textDocumentService.navigationTarget(params);
}
```

### `IlimapTextDocumentService`

Feld:

```java
private final IlimapNavigationService navigationService;
```

Methoden:

```java
public CompletableFuture<IlimapNavigationNode> nodeAtPosition(IlimapNodeAtPositionParams params) { ... }

public CompletableFuture<IlimapNavigationTarget> navigationTarget(IlimapNavigationTargetParams params) { ... }
```

Fallback:

- Wenn kein feingranularer Node gefunden wird, Rule Node zurueckgeben.
- Wenn kein Node gefunden wird, `null` fuer `nodeAtPosition`.

## VS-Code Client: Selection Sync

Datei:

`vscode/ilimap-vscode/src/overview/mappingOverviewSelectionSync.ts`

Aktuell:

- Sucht Rule anhand der Start/End-Zeilen aus `summary.rules`.

Neu:

- Erst LSP Request `ilimap/nodeAtPosition` nutzen.
- Fallback auf alte Rule-Line-Heuristik behalten.

Neue Constructor-Signatur:

```ts
export class MappingOverviewSelectionSync {
  constructor(
    private readonly getCurrentSummary: () => IlimapMappingSummary | undefined,
    private readonly requestNodeAtPosition: (uri: string, line: number, character: number) => Promise<string | undefined>,
    private readonly revealInWebview: (nodeId: string) => void
  ) {}
}
```

Methode:

```ts
async handleSelectionChange(event: vscode.TextEditorSelectionChangeEvent): Promise<void> {
  ...
  const nodeId = await this.requestNodeAtPosition(uri, selection.active.line, selection.active.character)
      ?? this.findNodeAtPosition(uri, selection.active);
  ...
}
```

In `extension.ts`:

```ts
const sync = new MappingOverviewSelectionSync(
  () => getOpenOverviewSummary(),
  async (uri, line, character) => {
    const client = getLanguageClient();
    if (!client) return undefined;
    const node = await client.sendRequest<IlimapNavigationNode>('ilimap/nodeAtPosition', {
      uri,
      position: { line, character }
    });
    return node?.nodeId;
  },
  nodeId => {
    void revealNodeInOpenOverview(nodeId, outputChannel);
  }
);
```

## VS-Code Client: Overview Reveal

Datei:

`vscode/ilimap-vscode/src/webview/mappingOverviewPanel.ts`

Aktuell:

- `revealRuleInOpenOverview(nodeId)` behandelt nur `rule:...`.

Neu:

```ts
export async function revealNodeInOpenOverview(
  nodeId: string,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  const state = currentPanelState;
  if (!state || state.disposed) return;

  state.activeNodeId = nodeId;

  if (nodeId.startsWith('rule:')) {
    const ruleId = extractRuleId(nodeId);
    if (ruleId) {
      await requestRuleDetail(state, ruleId, outputChannel);
    }
  }

  renderPanel(state, { refreshState: 'idle', lastUpdated: state.lastUpdated });
  postRevealMessage(state, nodeId);
}
```

Da `renderPanel` die Webview neu setzt, kann `postRevealMessage` erst nach Render passieren:

```ts
function postRevealMessage(state: MappingOverviewPanelState, nodeId: string): void {
  void state.panel.webview.postMessage({ type: 'revealNode', nodeId });
}
```

## VS-Code Client: HTML Node Markup

Datei:

`vscode/ilimap-vscode/src/webview/mappingOverviewHtml.ts`

Alle relevanten Elemente erhalten:

```html
data-node-id="..."
```

Beispiele:

- Rule list item
- Coverage attribute row
- Source usage member
- Flow node
- Diagnostic list item
- Trace step

JS Handler:

```js
window.addEventListener('message', event => {
  const message = event.data;
  if (!message || message.type !== 'revealNode') {
    return;
  }
  const nodeId = message.nodeId;
  const target = document.querySelector('[data-node-id="' + cssEscape(nodeId) + '"]');
  if (target) {
    document.querySelectorAll('.is-active-node').forEach(el => el.classList.remove('is-active-node'));
    target.classList.add('is-active-node');
    target.scrollIntoView({ block: 'center' });
  }
});
```

Da `CSS.escape` nicht ueberall sicher ist, helper:

```js
function cssEscape(value) {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return CSS.escape(value);
  }
  return String(value).replace(/"/g, '\\"');
}
```

CSS:

```css
.is-active-node {
  outline: 1px solid var(--vscode-focusBorder);
  outline-offset: 2px;
  background: color-mix(in srgb, var(--vscode-focusBorder) 12%, transparent);
}
```

## Mapping Explorer Integration

Datei:

`vscode/ilimap-vscode/src/overview/mappingExplorerProvider.ts`

Erweitere Tree:

- Unter Rule:
  - Target
  - Sources
  - Assignments, wenn Summary/RuleDetail vorhanden
  - Bags
  - Refs
  - Problems

Minimaler MVP:

- Root bleibt wie heute.
- Rule Item Context Menu / Command:
  - Reveal in Editor
  - Show in Overview
  - Show Trace

Commands in `package.json`:

```json
{
  "command": "ilimap.mappingExplorer.showTrace",
  "title": "ilimap: Show Trace"
}
```

Activation Event:

```json
"onCommand:ilimap.mappingExplorer.showTrace"
```

Command Registration:

```ts
vscode.commands.registerCommand(
  'ilimap.mappingExplorer.showTrace',
  async (nodeId?: string) => {
    await openMappingOverview(context, outputChannel);
    await requestTraceForNode(nodeId);
  }
);
```

Falls `requestTraceForNode` zu gross ist, erst nur Rule Trace implementieren.

## Tests fuer Navigation

Java Unit Tests:

- `IlimapNavigationServiceTest`
  - nodeAtPosition on rule id
  - nodeAtPosition on source alias declaration
  - nodeAtPosition on source member expression
  - nodeAtPosition on target attribute assignment
  - nodeAtPosition on enumMap reference
  - targetForNodeId returns location
  - relatedNodes for source member includes target assignment

VS-Code Tests:

- `mappingOverviewSelectionSync.test.js`
  - uses LSP node when available
  - falls back to rule heuristic
  - avoids duplicate reveal for same node

- `mappingOverviewHtml.test.js`
  - renders `data-node-id`
  - revealNode message highlights and scrolls when DOM harness exists

Akzeptanz:

- Cursor im Editor auf Assignment-Ziel markiert passende Coverage-Zeile.
- Cursor auf `alias.member` markiert Source Usage / Trace-relevanten Node.
- Klick in Overview springt weiterhin in Editor.
- Mapping Explorer kann Rule in Overview markieren.

---

# Umsetzungsplan

## Phase 0: Orientierung und Sicherheitsnetz

Agent-Aufgaben:

1. Repo lokal oeffnen.
2. `./gradlew test` ausfuehren.
3. In `vscode/ilimap-vscode`: `npm install` falls notwendig, dann `npm test`.
4. Relevante Klassen lesen:
   - `IlimapTextDocumentService`
   - `IlimapLanguageServer`
   - `IlimapMappingSummaryService`
   - `IlimapRuleDetailService`
   - `IlimapDefinitionService`
   - `IlimapSymbolReferenceResolver`
   - `IlimapPositionResolver`
   - `mappingOverviewPanel.ts`
   - `mappingOverviewHtml.ts`
   - `mappingOverviewMessages.ts`
5. Keine Formatierungs- oder Refactoring-Aenderungen ausserhalb des Scopes.

## Phase 1: Rename Refactoring

Warum zuerst:

- Rename ist klar abgrenzbar.
- Nutzt vorhandenen Symbol Resolver.
- Liefert schnell sichtbaren Poweruser-Nutzen.

Deliverables:

- `IlimapRenameService`
- `IlimapReferenceSearchService`
- LSP `prepareRename` und `rename`
- Tests

Akzeptanz:

- `F2` funktioniert fuer Input, Output, Rule, Enum, Source Alias.

## Phase 2: Navigation Node Index

Warum vor Trace:

- Trace und Overview-Sync brauchen stabile NodeIds.

Deliverables:

- `IlimapNavigationService`
- Records fuer Node Requests
- Custom Requests `ilimap/nodeAtPosition` und `ilimap/navigationTarget`
- Erweiterter `MappingOverviewSelectionSync`
- `data-node-id` in Overview HTML
- Tests

Akzeptanz:

- Editor Cursor synchronisiert nicht nur Rule, sondern auch Target Attribute und Source Member.

## Phase 3: Source-to-Target Trace

Warum nach Navigation:

- Trace kann dieselben NodeIds und Location-Mechanismen verwenden.

Deliverables:

- `IlimapExpressionDependencyService`
- `IlimapTraceService`
- Trace Records
- Custom Request `ilimap/trace`
- Trace Inspector in Webview
- Trace Links in Coverage Matrix und Source Usage
- Tests

Akzeptanz:

- Klick auf Coverage Attribute zeigt Trace.
- Klick auf Source Usage Member zeigt Reverse Usage.

## Phase 4: Polishing

Deliverables:

- bessere leere Zustaende
- Fehlertexte
- README-Update
- Changelog-Update
- optional GIF/Screenshot nur falls Projekt das bereits so macht

---

# Konkrete Datei-Aenderungen

## Java

Neu:

- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapExpressionDependencyService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceParams.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceSummary.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceTarget.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceExpression.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceDependency.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceUsage.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceStep.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapRenameService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapReferenceSearchService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapRenamePrepareResult.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapRenameResult.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapNavigationService.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapNodeAtPositionParams.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapNavigationNode.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapNavigationTargetParams.java`
- `src/main/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapNavigationTarget.java`

Geaendert:

- `IlimapLanguageServer.java`
- `IlimapTextDocumentService.java`
- `IlimapMappingSummaryService.java` optional fuer shared dependency service
- `IlimapRuleDetailService.java` optional fuer shared dependency service
- `IlimapOverviewNodeIds.java` falls vorhanden

Tests neu:

- `src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapExpressionDependencyServiceTest.java`
- `src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapTraceServiceTest.java`
- `src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapRenameServiceTest.java`
- `src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapReferenceSearchServiceTest.java`
- `src/test/java/guru/interlis/transformer/mapping/ilimap/ide/IlimapNavigationServiceTest.java`
- `src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapTextDocumentServiceRenameTest.java`
- `src/test/java/guru/interlis/transformer/mapping/ilimap/lsp/IlimapTextDocumentServiceTraceTest.java`

## TypeScript

Geaendert:

- `vscode/ilimap-vscode/package.json`
- `vscode/ilimap-vscode/src/extension.ts`
- `vscode/ilimap-vscode/src/commands.ts`
- `vscode/ilimap-vscode/src/overview/mappingOverviewSelectionSync.ts`
- `vscode/ilimap-vscode/src/overview/mappingExplorerProvider.ts`
- `vscode/ilimap-vscode/src/webview/mappingOverviewPanel.ts`
- `vscode/ilimap-vscode/src/webview/mappingOverviewMessages.ts`
- `vscode/ilimap-vscode/src/webview/mappingOverviewHtml.ts`
- `vscode/ilimap-vscode/src/webview/mappingOverviewState.ts`

Tests:

- `vscode/ilimap-vscode/test/mappingOverviewSelectionSync.test.js`
- `vscode/ilimap-vscode/test/mappingOverviewHtml.test.js`
- bestehende Tests anpassen, falls Snapshot-/String-Erwartungen brechen

Docs:

- `vscode/ilimap-vscode/README.md`
- `vscode/ilimap-vscode/CHANGELOG.md`

---

# Edge Cases

## Unsaved Documents

Alle Features muessen auf `documentStore` arbeiten.

- Trace muss unsaved Assignments sehen.
- Rename muss unsaved TextEdit-Ranges liefern.
- Navigation muss Cursorposition im aktuellen Buffer verwenden.

## Model Coverage nicht geladen

Trace:

- Muss trotzdem AST-basierte Dependencies liefern.
- `type`, `cardinality`, `mandatory` duerfen leer sein.
- Message: `Model coverage is not loaded; trace uses syntax-level information only.`

Navigation:

- Darf nicht davon abhaengen, dass INTERLIS-Modelle geladen sind.

Rename:

- Darf nicht model-aware sein muessen.

## Ambiguitaeten

Source Alias:

- Scope ist die aktuelle Rule.
- Gleicher Alias in anderer Rule ist unabhaengig.

EnumMap:

- Symbolischer `enumMap(..., MyMap)` Rename ist sicher.
- String-basierter `enumMap(..., "MyMap")` Rename nur, wenn exakte Argument-Range bekannt ist. Sonst nicht anfassen.

Qualified Class Names:

- Keine Rename-Unterstuetzung.
- Navigation darf darauf zeigen, aber nicht refactoren.

## Performance

- Kein volles Modell-Reload bei jeder Cursorbewegung.
- `nodeAtPosition` muss schnell sein und fast analysis nutzen koennen.
- `trace` darf model-aware Cache nutzen, aber keine langen blockierenden Loads starten.
- Debounce im VS-Code-Client beibehalten.

## Fehlerverhalten

- Custom Requests liefern `available=false` statt Exceptions, wenn fachlich nichts gefunden wird.
- Exceptions nur fuer echte technische Fehler.
- VS-Code-Webview zeigt kurze, konkrete Fehler.

---

# Definition of Done

## Rename

- `F2` funktioniert fuer lokale `.ilimap`-Symbole.
- Keine falschen Edits in Kommentaren, Strings oder anderen Scopes.
- Tests decken Input, Output, Rule, Enum und Source Alias ab.

## Bidirektionale Navigation

- Overview -> Editor bleibt stabil.
- Editor -> Overview markiert Rule, Target Attribute und Source Member.
- Mapping Explorer kann Rule oder Node in Overview markieren.
- NodeIds sind stabil und dokumentiert.

## Source-to-Target Trace

- Coverage Matrix bietet Trace fuer Zielattribute.
- Source Usage bietet Reverse Trace fuer Source Members.
- Trace zeigt Dependencies und Usages.
- Navigation aus Trace funktioniert.

## Gesamt

- `./gradlew test` gruen.
- `./gradlew check` gruen, falls nicht zu langsam.
- In `vscode/ilimap-vscode`: `npm test` gruen.
- README und Changelog aktualisiert.

---

# Agenten-Anweisung

Arbeite so:

1. Lies zuerst die oben genannten Dateien und bestaetige intern die vorhandenen APIs.
2. Implementiere nicht alle drei Features gleichzeitig. Beginne mit Rename.
3. Nach jeder Phase Tests schreiben und ausfuehren.
4. Nutze bestehende Patterns fuer Records, Services und LSP-Mapping.
5. Vermeide UI-Komplexitaet in der Webview. Die Overview ist Analyse- und Navigationsflaeche, kein zweiter Editor.
6. Wenn eine Range nicht exakt bestimmbar ist, lieber keine Aktion anbieten als einen unsicheren Edit erzeugen.
7. Keine grossen Refactorings an Parser oder AST, ausser sie sind fuer korrekte Ranges zwingend noetig.
8. Bei Unsicherheit kleine, gut getestete Services einfuehren statt Logik in `IlimapTextDocumentService` oder `mappingOverviewHtml.ts` wachsen zu lassen.

Empfohlene Reihenfolge der Commits:

1. `Add ilimap rename support`
2. `Add ilimap navigation node index`
3. `Sync mapping overview with fine-grained editor nodes`
4. `Add source-to-target trace service`
5. `Render trace inspector in mapping overview`
6. `Document ilimap VS Code poweruser features`

