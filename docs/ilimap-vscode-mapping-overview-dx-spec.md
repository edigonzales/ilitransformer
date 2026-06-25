# Spec: ilimap VS Code Mapping Overview DX Roadmap

**File name in repository:** `./docs/ilimap-vscode-mapping-overview-dx-spec.md`  
**Target area:** VS Code extension `ilimap-vscode`, Java ilimap Language Server, read-only Mapping Overview  
**Status:** Implementation specification for an LLM coding agent  
**Primary goal:** Turn the current read-only Mapping Overview from a static report into an interactive mapping comprehension, navigation, coverage and debugging tool.

---

## 1. Context for the coding agent

### 1.1 What ilitransformer / ilimap is

`ilitransformer` is a model-aware transformation tool for INTERLIS transfer data and related tabular/geospatial source formats. Its mapping language, `.ilimap`, describes how objects from one or more source models are transformed into objects of one or more target models.

The `.ilimap` language is declarative. A mapping profile contains:

- `job` metadata and model directories.
- `input` declarations for source transfer files or source formats.
- `output` declarations for target transfers.
- optional OID and basket strategies.
- enum mapping tables.
- global defaults.
- transformation `rule` blocks.

A rule typically declares:

- a target output and target class,
- one or more source aliases/classes,
- optional `where` filters,
- optional joins,
- optional identity expressions,
- target attribute assignments,
- defaults,
- `bag` mappings for `BAG OF STRUCTURE`,
- `ref` mappings for references/associations,
- `create` blocks for additional target objects,
- `loss` blocks documenting known information loss,
- metadata such as direction, roundtrip and lossiness.

The VS Code extension supports `.ilimap` as a language and uses a Java Language Server. The current Mapping Overview is read-only and shows a static summary of a mapping profile.

The goal of this specification is **not** to create a graphical mapping editor. The `.ilimap` file remains the single source of truth. The Mapping Overview must stay read-only, but it should become interactive and useful for understanding, reviewing and debugging mappings.

### 1.2 Current VS Code extension structure

The current uploaded extension has this relevant structure:

```text
ilimap-vscode/
  package.json
  src/
    extension.ts
    client.ts
    commands.ts
    configuration.ts
    webview/
      mappingOverviewPanel.ts
      mappingOverviewMessages.ts
      mappingOverviewHtml.ts
  test/
    client.test.js
    commands.test.js
    configuration.test.js
    manifest.test.js
    mappingOverviewHtml.test.js
    mappingOverviewPanel.test.js
    versioning.test.js
    vsixContents.test.js
  server/
    ilimap-lsp-all.jar
```

Relevant current TypeScript files:

- `src/client.ts`
  - starts/stops/restarts the Java Language Server via `vscode-languageclient`.
  - exposes `getLanguageClient()`.
- `src/commands.ts`
  - registers commands:
    - `ilimap.restartLanguageServer`
    - `ilimap.showLanguageServerLogs`
    - `ilimap.format`
    - `ilimap.validate`
    - `ilimap.openMappingOverview`
- `src/webview/mappingOverviewPanel.ts`
  - opens the Mapping Overview webview.
  - sends custom LSP request `ilimap/mappingSummary`.
  - renders returned summary via `renderMappingOverviewHtml(...)`.
  - currently handles only `{ type: 'navigate', line, character }` messages.
- `src/webview/mappingOverviewMessages.ts`
  - contains the TypeScript interfaces for `IlimapMappingSummary` and related summary DTOs.
- `src/webview/mappingOverviewHtml.ts`
  - renders the full Mapping Overview as one HTML string.
  - uses a strict CSP with nonce.
  - avoids editable controls.
  - currently renders metrics, inputs, outputs, enum maps, rules, diagnostics, class coverage and rule coverage.

Current custom LSP request:

```ts
export const mappingSummaryRequest = 'ilimap/mappingSummary';
```

Current baseline summary DTO:

```ts
export interface IlimapMappingSummary {
  available: boolean;
  message: string;
  mappingName: string;
  inputCount: number;
  outputCount: number;
  ruleCount: number;
  enumMapCount: number;
  bagCount: number;
  refCount: number;
  errorCount: number;
  warningCount: number;
  informationCount: number;
  hintCount: number;
  inputs: IlimapMappingInputSummary[];
  outputs: IlimapMappingOutputSummary[];
  enumMaps: IlimapEnumMapSummary[];
  rules: IlimapRuleSummary[];
  diagnostics: IlimapDiagnosticSummary[];
  coverageAvailable?: boolean;
  coverageMessage?: string;
  classCoverage?: IlimapCoverageClassSummary[];
  ruleCoverage?: IlimapRuleCoverageSummary[];
}
```

Current `IlimapRuleCoverageSummary` already contains rule-level coverage, attributes, source usage, refs and navigation line/character:

```ts
export interface IlimapRuleCoverageSummary {
  ruleId: string;
  targetOutput: string;
  targetClass: string;
  attributes: IlimapCoverageAttributeSummary[];
  sources: IlimapSourceUsageSummary[];
  refs: string[];
  directAssignmentCount: number;
  bagAssignmentCount: number;
  line: number;
  character: number;
}
```

Current tests are plain Node tests and are executed via:

```bash
npm run test
```

The `package.json` script expands to:

```bash
npm run build && node --test test/*.test.js
```

### 1.3 Current server-side classes visible in the bundled JAR

The Java server source may live in another module of the same repository. Search the repository for these class names before editing. The bundled JAR contains these relevant classes:

```text
guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisService
guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummaryService
guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingInputSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingOutputSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapEnumMapSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapRuleSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapDiagnosticSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapCoverageClassSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapCoverageAttributeSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapRuleCoverageSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapSourceUsageSummary
guru.interlis.transformer.mapping.ilimap.ide.IlimapPositionResolver
guru.interlis.transformer.mapping.ilimap.ide.IlimapSymbolReferenceResolver
guru.interlis.transformer.mapping.ilimap.lsp.IlimapTextDocumentService
```

Known public methods from the current JAR:

```java
public final class IlimapMappingSummaryService {
    public IlimapMappingSummaryService();
    public IlimapMappingSummary summarize(IlimapAnalysis analysis);
}
```

```java
public final class IlimapPositionResolver {
    public Optional<IlimapTokenAtPosition> tokenAt(IlimapAnalysis analysis, IlimapIdePosition position);
    public Optional<IlimapAstNode> smallestNodeAt(IlimapAnalysis analysis, IlimapIdePosition position);
}
```

The agent must prefer editing the Java source files if present. Do not edit `server/ilimap-lsp-all.jar` directly. Rebuild the server JAR through the repository build and then copy/update the extension-bundled JAR according to the existing project conventions.

### 1.4 Current UX problem

The Mapping Overview currently answers:

- How many inputs/outputs/rules exist?
- Which rules exist?
- Are there diagnostics?
- What is the current class/rule coverage?

It does **not yet** answer well enough:

- Where is this item in the `.ilimap` file?
- Which target attributes are missing, mapped, defaulted, computed, enum-mapped or referenced?
- Where does this target attribute come from?
- Which source attributes are used, unused, used only in filters, or only documented as loss?
- Which diagnostics belong to which rule/input/output/attribute?
- How can a developer navigate a large mapping profile quickly?
- How can a reviewer understand mapping changes without mentally parsing the whole `.ilimap` file?

The desired direction is a **read-only, interactive Mapping Comprehension Tool**.

---

## 2. Non-negotiable implementation principles

### 2.1 Read-only by design

The Mapping Overview must never directly edit `.ilimap` files. It may:

- navigate to locations,
- highlight selections,
- filter what is displayed,
- export reports,
- trigger existing validate/format commands,
- show derived information.

It must not:

- mutate mapping text from the webview,
- offer form fields that change the mapping,
- implement a visual mapping editor,
- maintain a second source of truth.

### 2.2 `.ilimap` remains the source of truth

Every visual item must be traceable back to source ranges in the `.ilimap` document where possible. Derived nodes must carry a stable ID and, when available, a `location`.

### 2.3 Thin client, semantic server

The TypeScript extension should remain a thin client. Semantic interpretation belongs in the Java Language Server.

Client responsibilities:

- VS Code commands.
- Webview lifecycle.
- Tree View lifecycle.
- Message passing.
- Rendering and navigation.
- Calling custom LSP requests.

Server responsibilities:

- parsing `.ilimap`,
- semantic analysis,
- target coverage,
- source usage,
- lineage,
- symbol resolution,
- diagnostics ownership,
- rule details.

### 2.4 Stable DTOs and backward compatibility

Existing DTO fields should remain backward compatible where possible. Add optional fields first. Do not remove current fields unless all callers/tests are migrated in the same phase.

### 2.5 Strict CSP and safe rendering

The current webview security posture is good and must be preserved:

- keep strict CSP,
- use nonces for scripts/styles,
- never use inline event handlers like `onclick`,
- escape all server-provided values,
- do not introduce `innerHTML` from untrusted data on the client side unless it is built from trusted escaped render helpers,
- do not add editable forms to the read-only overview.

### 2.6 Every phase must be shippable

Each phase must:

- compile,
- pass all existing tests,
- add meaningful new tests,
- not leave known failing tests,
- not leave TODO stubs in production paths,
- update README/docs when behavior changes,
- produce a working extension artifact.

A phase is not complete until the agent has run the required test commands and fixed all failures.

---

## 3. Standard agent workflow for every phase

For every phase in this spec, the agent must follow this process:

1. Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` completely.
2. Inspect the current repository state before editing.
3. Locate both extension sources and Java Language Server sources.
4. Implement the smallest coherent increment for the current phase only.
5. Add or update tests before considering the phase done.
6. Run formatting/build/tests.
7. Fix all failures.
8. Update README or docs if user-facing behavior changed.
9. Summarize changed files, tests run and any deferred items.

Required commands, adjusted to the actual repository layout:

```bash
# In the VS Code extension folder
npm ci
npm run build
npm run test
```

If the Java Language Server source is part of the repository, also run the relevant Java test suite. Use the existing build system. Examples:

```bash
# From repo root, if Gradle is used
./gradlew test

# Or more focused, if modules exist
./gradlew :<ilimap-lsp-module>:test
./gradlew :<extension-packaging-module>:test
```

If no Java source is present in the checkout and only `server/ilimap-lsp-all.jar` exists, do not attempt server-side phases. Implement only client-side-compatible work and report that server-side DTOs/requests require the Java source repository.

---

## 4. Proposed final architecture

### 4.1 Overview snapshot model

Long term, evolve `IlimapMappingSummary` into a versioned overview snapshot. Keep the current name initially for compatibility, but introduce reusable concepts.

Recommended TypeScript model additions in `src/webview/mappingOverviewMessages.ts`:

```ts
export interface IlimapLocation {
  line: number;
  character: number;
  endLine?: number;
  endCharacter?: number;
}

export interface IlimapNodeRef {
  nodeId: string;
  label: string;
  kind: string;
  location?: IlimapLocation;
}

export interface IlimapOverviewEdge {
  id: string;
  fromNodeId: string;
  toNodeId: string;
  kind: 'inputToSource' | 'sourceToRule' | 'ruleToTarget' | 'targetToOutput' | 'ref' | 'bag' | string;
  status: 'ok' | 'warning' | 'error' | string;
  label?: string;
}
```

Extend existing DTOs with stable IDs and locations:

```ts
export interface IlimapMappingInputSummary {
  id: string;
  path: string;
  model: string;
  format: string;
  nodeId?: string;
  location?: IlimapLocation;
  options?: Record<string, string>;
  limitations?: string[];
}

export interface IlimapMappingOutputSummary {
  id: string;
  path: string;
  model: string;
  format: string;
  nodeId?: string;
  location?: IlimapLocation;
}

export interface IlimapEnumMapSummary {
  id: string;
  entryCount: number;
  nodeId?: string;
  location?: IlimapLocation;
}

export interface IlimapRuleSummary {
  id: string;
  targetOutput: string;
  targetClass: string;
  sourceCount: number;
  assignmentCount: number;
  bagCount: number;
  refCount: number;
  status: 'ok' | 'warning' | 'error' | string;
  nodeId?: string;
  location?: IlimapLocation;
  diagnosticCount?: number;
  warningCount?: number;
  errorCount?: number;
  coverage?: IlimapRuleCoverageBadge;
}
```

Stable node ID conventions:

```text
input:<inputId>
output:<outputId>
enum:<enumMapId>
rule:<ruleId>
rule:<ruleId>:source:<alias>
rule:<ruleId>:assign:<targetAttribute>
rule:<ruleId>:bag:<bagName>
rule:<ruleId>:ref:<refName>
target:<outputId>:<fullyQualifiedClassName>
source:<inputId>:<fullyQualifiedClassName>
diagnostic:<code>:<line>:<character>
```

### 4.2 Webview message model

Replace the current one-message protocol with typed messages. Keep the old `navigate` shape working during migration.

```ts
type MappingOverviewToExtensionMessage =
  | { type: 'navigate'; line: number; character: number }
  | { type: 'navigateToLocation'; location: IlimapLocation }
  | { type: 'selectNode'; nodeId: string }
  | { type: 'requestRuleDetail'; ruleId: string }
  | { type: 'refresh' }
  | { type: 'exportReport' };

type ExtensionToMappingOverviewMessage =
  | { type: 'summary'; summary: IlimapMappingSummary }
  | { type: 'refreshState'; state: 'idle' | 'loading' | 'stale' | 'error'; message?: string }
  | { type: 'highlight'; nodeId: string }
  | { type: 'ruleDetail'; detail: IlimapRuleDetailSummary };
```

### 4.3 Client-side modules after refactoring

Recommended final layout:

```text
src/
  extension.ts
  client.ts
  commands.ts
  configuration.ts
  overview/
    mappingOverviewRequests.ts
    mappingOverviewTypes.ts
    mappingOverviewPanel.ts
    mappingOverviewState.ts
    mappingOverviewSelectionSync.ts
    mappingOverviewReporter.ts
    mappingOverviewTreeProvider.ts
  webview/
    mappingOverviewHtml.ts
    mappingOverviewMessages.ts
    render/
      renderShell.ts
      renderMetrics.ts
      renderInputs.ts
      renderOutputs.ts
      renderRules.ts
      renderDiagnostics.ts
      renderCoverage.ts
      renderFlowMap.ts
      renderRuleInspector.ts
      renderSourceUsage.ts
      renderFormatInspector.ts
```

Do not perform this entire refactoring in one step. Split it across phases.

### 4.4 Java server-side modules after expansion

Recommended final Java services/records:

```text
guru.interlis.transformer.mapping.ilimap.ide
  IlimapMappingSummaryService
  IlimapRuleDetailService
  IlimapLineageService
  IlimapCoverageAnalysisService
  IlimapSourceUsageAnalysisService
  IlimapOverviewNodeId
  IlimapOverviewLocation
  IlimapOverviewEdge
  IlimapRuleDetailSummary
  IlimapAssignmentSummary
  IlimapExpressionDependencySummary
  IlimapSourceClassUsageSummary
  IlimapSourceAttributeUsageSummary
  IlimapDiagnosticOwnerResolver
```

Recommended custom LSP requests:

```text
ilimap/mappingSummary       existing, extended
ilimap/ruleDetail           new, lazy detail for one rule
ilimap/nodeAtPosition       new, optional for editor-to-overview sync
ilimap/exportMappingReport  optional later; may also be client-only from summary
```

---

## 5. Phase plan overview

| Phase | Title | Main outcome |
|---:|---|---|
| 0 | Baseline hardening and docs correction | Fix panel lifecycle/message-handler issue, update docs, keep behavior stable |
| 1 | Navigation model and clickable overview | Inputs/outputs/enums/rules/coverage all navigable via stable locations |
| 2 | Live refresh and state management | Overview updates after edits/saves and shows stale/loading/error states |
| 3 | Rule Inspector | Clicking a rule shows detailed target/source/assignment/ref/bag/loss information |
| 4 | Coverage Matrix and Source Usage View | Coverage becomes readable; source usage becomes first-class |
| 5 | Flow Map and filters | Overview gets a compact visual data-flow map plus useful filters |
| 6 | Diagnostics ownership overlay | Diagnostics are attached to rules/nodes/attributes, not only listed globally |
| 7 | Native Mapping Explorer Tree View | Sidebar navigation for inputs/outputs/rules/coverage/problems |
| 8 | CodeLens and editor integration | Inline rule summaries and “Show in Overview” actions in editor |
| 9 | Exportable Mapping Report | Markdown report for review/documentation |

Each phase includes an agent prompt. The prompt must be copied exactly or adapted minimally, and it must reference this spec by file name: `./docs/ilimap-vscode-mapping-overview-dx-spec.md`.

---

# Phase 0: Baseline hardening and documentation correction

## Goal

Stabilize the current Mapping Overview before adding features. Fix the likely duplicate message-handler problem, improve lifecycle structure, and update documentation to match current reality.

## Current problem

`src/webview/mappingOverviewPanel.ts` has a module-level `currentPanel`. Every call to `openMappingOverview(...)` calls `registerNavigationHandler(panel, uri, outputChannel)`, even if the panel already exists. This can register multiple `onDidReceiveMessage` handlers on the same webview panel.

Also, the README may claim that the overview does not calculate coverage, while the code already renders Class Coverage and Rule Coverage.

## Required implementation

### 0.1 Introduce panel state

In `src/webview/mappingOverviewPanel.ts`, replace:

```ts
let currentPanel: vscode.WebviewPanel | undefined;
```

with:

```ts
interface MappingOverviewPanelState {
  panel: vscode.WebviewPanel;
  uri: string;
  disposables: vscode.Disposable[];
}

let currentPanelState: MappingOverviewPanelState | undefined;
```

### 0.2 Create or reveal panel without duplicate handlers

Replace `createOrRevealPanel(...)` with a function that returns `MappingOverviewPanelState`.

Suggested method:

```ts
function createOrRevealPanelState(
  context: vscode.ExtensionContext,
  uri: string,
  outputChannel: vscode.OutputChannel
): MappingOverviewPanelState
```

Behavior:

- If `currentPanelState` exists:
  - update `currentPanelState.uri = uri`;
  - reveal existing panel;
  - return existing state;
  - do **not** register another message handler.
- If no state exists:
  - create a new webview panel;
  - create `disposables` array;
  - register one message handler;
  - on dispose, dispose all state disposables and set `currentPanelState = undefined`.

### 0.3 Replace `registerNavigationHandler(...)`

Change signature:

```ts
function registerNavigationHandler(
  state: MappingOverviewPanelState,
  outputChannel: vscode.OutputChannel
): vscode.Disposable
```

The handler must use `state.uri` at message handling time, not a captured old `uri`.

```ts
const document = await vscode.workspace.openTextDocument(vscode.Uri.parse(state.uri));
```

### 0.4 Keep backward-compatible navigation message

Keep current message shape valid:

```ts
{ type: 'navigate', line: number, character: number }
```

Do not change HTML message generation in this phase except as needed for tests.

### 0.5 Documentation update

Update `README.md`:

- State that Mapping Overview is read-only.
- State that it currently shows summary, diagnostics, class coverage and rule coverage.
- Do not claim that coverage is not calculated.

Add a short “Developer notes” section referring to this spec:

```md
Further DX roadmap: see `./docs/ilimap-vscode-mapping-overview-dx-spec.md`.
```

## Tests required

### Update `test/mappingOverviewPanel.test.js`

Add a test:

```text
openMappingOverview reuses panel without registering duplicate message handlers
```

Test setup:

- Mock `createWebviewPanel` and count calls.
- Mock `webview.onDidReceiveMessage` and count handler registrations.
- Call `openMappingOverview(...)` twice for two active `.ilimap` documents or two different URIs.
- Assert:
  - only one panel is created,
  - only one message handler is registered,
  - the second call updates webview HTML,
  - navigation after the second call opens the second/current URI, not the first URI.

### Existing tests

All existing tests must still pass.

## Acceptance criteria

- No duplicate webview message handlers are registered for a reused panel.
- Navigating from a reused panel uses the latest document URI.
- README correctly describes current Mapping Overview coverage behavior.
- `npm run build` passes.
- `npm run test` passes.
- If Java server sources are present, Java tests still pass.

## Agent prompt for Phase 0

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 0 only.

Focus on baseline hardening of the ilimap VS Code Mapping Overview. In `src/webview/mappingOverviewPanel.ts`, replace the single `currentPanel` variable with a panel-state object that stores the panel, current URI and disposables. Ensure that re-opening the Mapping Overview reuses the panel without registering duplicate `onDidReceiveMessage` handlers. The navigation handler must use the latest URI from panel state, not a stale captured URI.

Update README documentation so it correctly says that the current read-only overview shows summary, diagnostics, class coverage and rule coverage, and add a reference to `./docs/ilimap-vscode-mapping-overview-dx-spec.md`.

Add or update tests, especially `test/mappingOverviewPanel.test.js`, to prove that opening the overview twice creates one panel, registers one message handler, updates the HTML and navigates using the latest URI.

Run `npm run build` and `npm run test`. If Java server sources are present in this checkout, also run the relevant Java test suite. Fix all failures before stopping. Do not implement later phases.
```

---

# Phase 1: Navigation model and clickable overview

## Goal

Make all overview elements navigable, not only diagnostics and some coverage entries. The user must be able to click inputs, outputs, enum maps, rules, coverage classes, rule coverage items and assigned attributes and jump to the relevant `.ilimap` location.

## Required implementation

### 1.1 Add shared location types in TypeScript

In `src/webview/mappingOverviewMessages.ts`, add:

```ts
export interface IlimapLocation {
  line: number;
  character: number;
  endLine?: number;
  endCharacter?: number;
}

export interface IlimapWithLocation {
  line?: number;
  character?: number;
  location?: IlimapLocation;
}
```

For backward compatibility, support both:

- existing `line`/`character`,
- new `location`.

Extend these interfaces with optional `nodeId` and `location`:

```ts
IlimapMappingInputSummary
IlimapMappingOutputSummary
IlimapEnumMapSummary
IlimapRuleSummary
IlimapDiagnosticSummary
IlimapCoverageClassSummary
IlimapCoverageAttributeSummary
IlimapSourceUsageSummary
IlimapRuleCoverageSummary
```

Example:

```ts
export interface IlimapRuleSummary {
  id: string;
  targetOutput: string;
  targetClass: string;
  sourceCount: number;
  assignmentCount: number;
  bagCount: number;
  refCount: number;
  status: 'ok' | 'warning' | 'error' | string;
  nodeId?: string;
  location?: IlimapLocation;
  line?: number;
  character?: number;
}
```

### 1.2 Add server-side locations

In Java server source, extend corresponding records/classes:

- `IlimapMappingInputSummary`
- `IlimapMappingOutputSummary`
- `IlimapEnumMapSummary`
- `IlimapRuleSummary`
- if useful, diagnostics and coverage records.

Recommended Java record:

```java
public record IlimapOverviewLocation(
    int line,
    int character,
    Integer endLine,
    Integer endCharacter
) {
    public static IlimapOverviewLocation point(int line, int character) {
        return new IlimapOverviewLocation(line, character, null, null);
    }
}
```

Add helper:

```java
public final class IlimapOverviewNodeIds {
    public static String input(String inputId) { return "input:" + inputId; }
    public static String output(String outputId) { return "output:" + outputId; }
    public static String enumMap(String enumMapId) { return "enum:" + enumMapId; }
    public static String rule(String ruleId) { return "rule:" + ruleId; }
    private IlimapOverviewNodeIds() {}
}
```

In `IlimapMappingSummaryService.summarize(...)`, populate:

- input node ID and location,
- output node ID and location,
- enum map node ID and location,
- rule node ID and location.

Use existing AST positions. If a location is not available, set `line = -1`, `character = -1` or omit `location` and keep rendering non-clickable.

### 1.3 Update HTML renderer navigation helpers

In `src/webview/mappingOverviewHtml.ts`, replace `renderNavName(label, line, character)` with helpers that accept a location-like object.

Suggested helpers:

```ts
function renderNavName(label: string, target: IlimapWithLocation): string

function navLocation(target: IlimapWithLocation): IlimapLocation | undefined

function isValidLocation(location: IlimapLocation | undefined): location is IlimapLocation
```

Behavior:

- Prefer `target.location`.
- Fallback to `target.line`/`target.character`.
- If no valid location, render escaped plain text.
- If valid, render an `<a>` with `data-nav-line`, `data-nav-character`, optional `data-nav-end-line`, `data-nav-end-character`.

### 1.4 Make all sections navigable

Update renderers:

- `renderInputs(...)`: clickable input ID.
- `renderOutputs(...)`: clickable output ID.
- `renderEnumMaps(...)`: clickable enum map ID.
- `renderRules(...)`: clickable rule ID.
- `renderDiagnostics(...)`: keep clickable diagnostics.
- `renderClassCoverage(...)`: keep clickable class names.
- `renderRuleCoverageItem(...)`: clickable rule ID, assigned attributes, source aliases when possible.

### 1.5 Extend navigation message to ranges

In `mappingOverviewPanel.ts`, support:

```ts
{ type: 'navigateToLocation', location: { line, character, endLine?, endCharacter? } }
```

Keep legacy `{ type: 'navigate', line, character }`.

Add helpers:

```ts
interface NavigateMessage {
  type: 'navigate' | 'navigateToLocation';
  line?: number;
  character?: number;
  location?: IlimapLocation;
}

function parseNavigationMessage(message: unknown): IlimapLocation | undefined
function isValidLineCharacter(line: unknown, character: unknown): boolean
async function revealLocation(uri: string, location: IlimapLocation, outputChannel: vscode.OutputChannel): Promise<void>
```

`revealLocation(...)` should:

- open document,
- show it in `ViewColumn.One`,
- create selection from start to end if end is present, otherwise point selection,
- reveal range in center if outside viewport.

## Tests required

### HTML tests

Update `test/mappingOverviewHtml.test.js`:

- Verify input/output/enum/rule names render with navigation metadata when locations are present.
- Verify old `line`/`character` still works.
- Verify invalid negative line/character renders plain text.
- Verify escaping still works.
- Verify no inline event handlers and strict CSP still hold.

### Panel tests

Update `test/mappingOverviewPanel.test.js`:

- Test `navigateToLocation` with `endLine/endCharacter` creates a range selection.
- Test legacy `navigate` still works.
- Test malformed messages are ignored.

### Server tests

If Java source exists:

- Add tests for `IlimapMappingSummaryService` proving input/output/enum/rule summaries include stable node IDs and locations.
- Add tests for unavailable/parse-error cases.

## Acceptance criteria

- Every top-level overview item is clickable when location data exists.
- The webview supports point and range navigation.
- Backward compatibility with existing summary DTOs is preserved.
- All tests pass.

## Agent prompt for Phase 1

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 1 only.

Add a shared navigation/location model to the ilimap Mapping Overview. Extend TypeScript DTOs in `src/webview/mappingOverviewMessages.ts` with optional `nodeId` and `location` fields while preserving existing `line`/`character` fields. Update the Java Language Server summary records and `IlimapMappingSummaryService` so inputs, outputs, enum maps and rules include stable node IDs and source locations when available.

Update `src/webview/mappingOverviewHtml.ts` so input IDs, output IDs, enum map IDs, rule IDs, diagnostics, class coverage entries, rule coverage entries, assigned attributes and source usage entries are clickable whenever they have a valid location. Keep strict escaping and CSP.

Update `src/webview/mappingOverviewPanel.ts` to support both legacy `{ type: 'navigate', line, character }` and new `{ type: 'navigateToLocation', location }` messages, including range selection when end positions are present.

Add tests for clickable rendering, backward-compatible navigation, range navigation and malformed-message handling. Add Java tests for node IDs/locations if Java source is present.

Run `npm run build` and `npm run test`; run Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 2: Live refresh and state management

## Goal

The Mapping Overview should not become stale silently. It should refresh when the active `.ilimap` document changes or is saved, and it should show loading/stale/error states.

## Required implementation

### 2.1 Introduce client-side panel controller/state

Create a new file:

```text
src/webview/mappingOverviewState.ts
```

Recommended types:

```ts
import * as vscode from 'vscode';
import type { IlimapMappingSummary } from './mappingOverviewMessages';

export interface MappingOverviewPanelState {
  panel: vscode.WebviewPanel;
  uri: string;
  documentVersion?: number;
  summary?: IlimapMappingSummary;
  refreshTimer?: NodeJS.Timeout;
  loading: boolean;
  disposed: boolean;
  disposables: vscode.Disposable[];
}

export interface MappingOverviewRefreshOptions {
  reason: 'open' | 'save' | 'change' | 'activeEditor' | 'manual';
  debounceMs?: number;
}
```

Move the state interface introduced in Phase 0 here.

### 2.2 Add refresh methods

In `mappingOverviewPanel.ts`, implement:

```ts
async function refreshMappingOverview(
  state: MappingOverviewPanelState,
  outputChannel: vscode.OutputChannel,
  options: MappingOverviewRefreshOptions
): Promise<void>
```

Behavior:

- If no Language Client exists, show error state in webview.
- Set loading state before request.
- Send `ilimap/mappingSummary` with current URI.
- Update `state.summary` and `state.documentVersion`.
- Re-render HTML or send a webview message with the new summary.
- On error, keep previous summary visible if available and show error banner.

A simple full re-render is acceptable in this phase. Later phases may move to client-side incremental updates.

### 2.3 Register workspace/editor events

When panel is created, register disposables:

```ts
vscode.workspace.onDidSaveTextDocument(document => { ... })
vscode.workspace.onDidChangeTextDocument(event => { ... })
vscode.window.onDidChangeActiveTextEditor(editor => { ... })
```

Rules:

- Refresh only if the document URI equals `state.uri` and document is `.ilimap`.
- For `onDidChangeTextDocument`, debounce 400-700 ms.
- For save, refresh immediately.
- For active editor change, update `state.uri` only if the new editor is `.ilimap` and the current panel is visible or active. Alternatively do not switch automatically; prefer a conservative implementation:
  - if active editor changes to another `.ilimap`, show a small info state: “Overview is for X. Run Open Mapping Overview to switch.”
  - The simpler and safer approach is: keep overview bound to the document used when opened.

Recommended for first implementation: bind the panel to the opened document and refresh only that URI.

### 2.4 Add manual refresh message

In the webview HTML header, add a non-editing link or button-like anchor:

```html
<a href="#" data-action="refresh">Refresh</a>
```

This is not an editing control. Tests currently assert no `<button>`, `<input>`, `<form>`. Keep that.

Webview script posts:

```ts
{ type: 'refresh' }
```

Panel handler calls `refreshMappingOverview(..., { reason: 'manual' })`.

### 2.5 Add visual state banner

At the top of the webview render:

```text
Loading mapping overview...
Last updated: 10:42:31
Stale: document changed; refreshing...
Failed to refresh: <message>
```

Add optional fields to render context rather than server DTO:

```ts
export interface MappingOverviewRenderState {
  refreshState: 'idle' | 'loading' | 'stale' | 'error';
  lastUpdated?: string;
  errorMessage?: string;
}
```

Change signature:

```ts
export function renderMappingOverviewHtml(
  summary: IlimapMappingSummary,
  nonce: string,
  renderState?: MappingOverviewRenderState
): string
```

Keep backward-compatible default for tests.

## Tests required

### Panel tests

Add tests for:

- Refresh request is sent on manual refresh message.
- Save event for same document triggers refresh.
- Save event for another document does not trigger refresh.
- Change event is debounced; multiple changes trigger one refresh.
- Failed refresh logs an error and shows an error message or error banner.

### HTML tests

Add tests for:

- Refresh action link exists and has no inline handler.
- Loading/error/stale banners render escaped messages.
- CSP remains strict.
- Still no editable controls.

## Acceptance criteria

- Overview refreshes on save and manual refresh.
- Change refresh is debounced.
- Errors are visible but do not break the panel.
- No duplicate event handlers or timers remain after dispose.
- All tests pass.

## Agent prompt for Phase 2

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 2 only.

Introduce proper Mapping Overview panel state and refresh management. Create `src/webview/mappingOverviewState.ts` for panel state and render state types. Update `mappingOverviewPanel.ts` so the overview can refresh its summary via `ilimap/mappingSummary` on manual refresh, document save and debounced document changes for the same `.ilimap` URI. Show loading/stale/error/last-updated state in the webview without adding editable controls.

Update `renderMappingOverviewHtml` to accept an optional render-state object and render a safe status banner plus a non-editing refresh link. Preserve strict CSP, escaping and existing behavior.

Add tests for manual refresh, save-triggered refresh, debounced change refresh, ignored unrelated documents, error handling and status rendering.

Run `npm run build` and `npm run test`; run Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 3: Rule Inspector

## Goal

When a user selects a rule, the Mapping Overview should show a detailed read-only Rule Inspector with target, sources, filters, joins, identity, assignments, refs, bags, losses and metadata.

This is the first major DX feature. It lets a developer understand one rule without reading the whole `.ilimap` file.

## Required implementation

### 3.1 Add rule detail DTOs in TypeScript

In `src/webview/mappingOverviewMessages.ts`, add:

```ts
export const ruleDetailRequest = 'ilimap/ruleDetail';

export interface IlimapRuleDetailParams {
  uri: string;
  ruleId: string;
}

export interface IlimapRuleDetailSummary {
  available: boolean;
  message: string;
  ruleId: string;
  nodeId?: string;
  location?: IlimapLocation;
  target?: IlimapTargetDetailSummary;
  sources: IlimapSourceDetailSummary[];
  joins: IlimapJoinSummary[];
  identity: IlimapExpressionSummary[];
  assignments: IlimapAssignmentSummary[];
  defaults: IlimapAssignmentSummary[];
  bags: IlimapBagSummary[];
  refs: IlimapRefSummary[];
  losses: IlimapLossSummary[];
  metadata?: IlimapMetadataSummary;
  diagnostics: IlimapDiagnosticSummary[];
}

export interface IlimapTargetDetailSummary {
  outputId: string;
  className: string;
  location?: IlimapLocation;
}

export interface IlimapSourceDetailSummary {
  alias: string;
  inputIds: string[];
  className: string;
  where?: string;
  location?: IlimapLocation;
}

export interface IlimapJoinSummary {
  type: 'inner' | 'left' | string;
  leftAlias: string;
  rightAlias: string;
  condition: string;
  location?: IlimapLocation;
}

export interface IlimapExpressionSummary {
  expression: string;
  location?: IlimapLocation;
}

export interface IlimapAssignmentSummary {
  targetAttribute: string;
  expression: string;
  kind: 'copy' | 'constant' | 'computed' | 'enumMap' | 'default' | 'null' | 'unknown' | string;
  dependencies: IlimapExpressionDependencySummary[];
  location?: IlimapLocation;
}

export interface IlimapExpressionDependencySummary {
  kind: 'sourceAttribute' | 'sourceRole' | 'enumMap' | 'function' | 'constant' | 'unknown' | string;
  alias?: string;
  member?: string;
  sourceClass?: string;
  enumMapId?: string;
  functionName?: string;
  literal?: string;
  location?: IlimapLocation;
}

export interface IlimapBagSummary {
  name: string;
  targetAttribute?: string;
  structureClass?: string;
  mode?: string;
  maxItems?: number;
  source?: IlimapSourceDetailSummary;
  assignments: IlimapAssignmentSummary[];
  nestedBags: IlimapBagSummary[];
  location?: IlimapLocation;
}

export interface IlimapRefSummary {
  name: string;
  association?: string;
  role?: string;
  required: boolean;
  targetRuleId?: string;
  sourceRef?: string;
  location?: IlimapLocation;
}

export interface IlimapLossSummary {
  sourcePath?: string;
  reasonCode?: string;
  description?: string;
  when?: string;
  location?: IlimapLocation;
}

export interface IlimapMetadataSummary {
  direction?: string;
  roundtrip?: string;
  lossiness?: string;
  location?: IlimapLocation;
}
```

### 3.2 Add server-side rule detail service

In Java server source, add:

```java
public final class IlimapRuleDetailService {
    public IlimapRuleDetailSummary detail(IlimapAnalysis analysis, String ruleId) {
        ...
    }
}
```

Add records matching the TypeScript DTOs.

Required behavior:

- If no analysis or no valid AST: return unavailable detail with message.
- If rule ID not found: return unavailable detail with message.
- Populate target, sources, joins, identity, assignments, defaults, bags, refs, losses, metadata and diagnostics as far as available.
- Every item should carry a location where available.
- Assignment `kind` classification must be conservative:
  - exact alias path like `p.Name` => `copy`
  - literal enum/string/number/boolean/null => `constant` or `null`
  - expression starting with `enumMap(` => `enumMap`
  - default assignment => `default`
  - otherwise `computed`
  - unknown/unparseable => `unknown`

### 3.3 Add LSP custom request

In `IlimapTextDocumentService` or the server class handling custom requests, add request:

```text
ilimap/ruleDetail
```

Params:

```java
public record IlimapRuleDetailParams(String uri, String ruleId) {}
```

Result:

```java
IlimapRuleDetailSummary
```

### 3.4 Update webview panel message handling

In `mappingOverviewPanel.ts`, handle:

```ts
{ type: 'requestRuleDetail', ruleId: string }
{ type: 'selectNode', nodeId: string }
```

When a rule is selected:

- call `ilimap/ruleDetail` lazily,
- post or re-render detail into the webview,
- if request fails, show an inspector error.

For simplicity in this phase, it is acceptable to full re-render the HTML with an optional selected detail:

```ts
renderMappingOverviewHtml(summary, nonce, renderState, selectedRuleDetail)
```

Alternative: post message to webview and let client-side script update an inspector container. If doing this, keep script small and tested.

### 3.5 Update HTML renderer

Create rendering helpers in `mappingOverviewHtml.ts` or split to `src/webview/render/renderRuleInspector.ts`:

```ts
function renderRuleInspector(detail?: IlimapRuleDetailSummary): string
function renderTargetDetail(target?: IlimapTargetDetailSummary): string
function renderSourceDetails(sources: IlimapSourceDetailSummary[]): string
function renderAssignments(assignments: IlimapAssignmentSummary[]): string
function renderBags(bags: IlimapBagSummary[]): string
function renderRefs(refs: IlimapRefSummary[]): string
function renderLosses(losses: IlimapLossSummary[]): string
function renderMetadata(metadata?: IlimapMetadataSummary): string
```

Add “Inspect” link to each rule item:

```html
<a href="#" data-rule-id="lfp3" data-action="inspect-rule">Inspect</a>
```

Keep rule ID itself navigable.

The inspector should render:

```text
Rule lfp3
Target
Sources
Joins
Identity
Assignments
Defaults
Bags
Refs
Loss
Metadata
Diagnostics
```

### 3.6 UX details

- Empty sections should say `None`.
- Long class names and expressions must wrap.
- Expressions should be rendered in `<code>` with escaping.
- Assignment kinds should be rendered as small tags.
- Required refs should be visually emphasized.
- Losses should have a warning-style marker.

## Tests required

### Server tests

If Java source is available:

- Detail for a simple rule with target/source/assignments.
- Detail for rule with where, identity and enumMap.
- Detail for refs, bags and losses if fixtures exist.
- Unknown rule returns unavailable.
- Assignment kind classification tests.
- Locations are populated.

### Client panel tests

- Clicking inspect sends `ilimap/ruleDetail` request.
- Detail result is rendered.
- Unknown/unavailable detail shows safe message.
- Failed request is logged and shown without crashing.

### HTML tests

- Rule inspector renders target/source/assignments/refs/bags/losses safely.
- Expressions are escaped.
- Inspect links do not use inline event handlers.
- CSP remains strict.

## Acceptance criteria

- User can inspect a rule from the Overview.
- Rule Inspector shows all available semantic sections.
- All values are escaped.
- All tests pass.

## Agent prompt for Phase 3

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 3 only.

Add a read-only Rule Inspector to the ilimap Mapping Overview. Extend `mappingOverviewMessages.ts` with `ilimap/ruleDetail` request/response DTOs for rule details: target, sources, joins, identity, assignments, defaults, bags, refs, losses, metadata and diagnostics. Implement a Java `IlimapRuleDetailService` and register the custom LSP request `ilimap/ruleDetail` if the Java server source is present.

Update the webview so each rule has an Inspect action. When selected, the extension requests rule detail lazily and renders an inspector. Keep rule names navigable to source locations. Render all expressions and server-provided labels safely; keep strict CSP and no editable controls.

Add tests for server rule detail generation, assignment kind classification, client request handling, inspector rendering and security escaping. Run `npm run build`, `npm run test` and Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 4: Coverage Matrix and Source Usage View

## Goal

Make coverage and source usage readable for real mappings. Replace long comma-separated coverage lines with structured matrices and make source usage a first-class section.

## Required implementation

### 4.1 Add coverage status model

In `mappingOverviewMessages.ts`, extend `IlimapCoverageAttributeSummary`:

```ts
export interface IlimapCoverageAttributeSummary {
  name: string;
  type: string;
  cardinality: string;
  mandatory: boolean;
  assigned: boolean;
  line: number;
  character: number;
  nodeId?: string;
  location?: IlimapLocation;
  status?: 'mapped' | 'constant' | 'computed' | 'enumMap' | 'default' | 'bag' | 'ref' | 'missing' | 'documentedLoss' | 'unknown' | string;
  expression?: string;
  sourceSummary?: string;
}
```

Server should populate `status`, `expression`, `sourceSummary` where possible. If not possible, client derives:

- `assigned === true` => `mapped`
- `mandatory && !assigned` => `missing`
- otherwise `unknown/open`

### 4.2 Render Target Coverage Matrix

Replace the current `Assigned: ...`, `Unassigned: ...`, `Missing mandatory: ...` text-heavy rendering with a table-like layout.

Recommended renderer:

```ts
function renderTargetCoverageMatrix(rule: IlimapRuleCoverageSummary): string
function renderCoverageAttributeRow(attribute: IlimapCoverageAttributeSummary): string
function coverageStatus(attribute: IlimapCoverageAttributeSummary): string
function coverageStatusClass(status: string): string
```

Columns:

```text
Attribute | Status | Type | Cardinality | Source / Expression
```

For each row:

- Attribute is navigable if location exists.
- Status rendered as tag.
- Missing mandatory is warning class.
- Expression rendered in `<code>`.

### 4.3 Add Source Usage View

Current source usage exists only inside rule coverage. Add a separate section:

```text
Source Usage
  input dm01 / class DM01...LFP3 / alias p
    used attributes: Nummer, NBIdent, LageGen
    used roles: Entstehung
```

Recommended DTO:

```ts
export interface IlimapSourceClassUsageSummary {
  inputIds: string[];
  sourceClass: string;
  aliases: string[];
  attributes: IlimapSourceAttributeUsageSummary[];
  roles: IlimapSourceAttributeUsageSummary[];
  location?: IlimapLocation;
}

export interface IlimapSourceAttributeUsageSummary {
  name: string;
  kind: 'attribute' | 'role';
  status: 'used' | 'unused' | 'identity' | 'where' | 'join' | 'loss' | 'unknown' | string;
  usedBy: IlimapUsageReferenceSummary[];
  location?: IlimapLocation;
}

export interface IlimapUsageReferenceSummary {
  ruleId: string;
  context: 'assign' | 'where' | 'join' | 'identity' | 'ref' | 'bag' | 'loss' | string;
  targetAttribute?: string;
  location?: IlimapLocation;
}
```

Add optional field to `IlimapMappingSummary`:

```ts
sourceUsage?: IlimapSourceClassUsageSummary[];
```

Server implementation:

- Start with used-only source usage if full model attribute enumeration is hard.
- If model index can enumerate all attributes, include `unused` too.
- Source usage must group by source class and input(s), not only by alias.

### 4.4 UX details

Add filters/toggles:

- `All attributes`
- `Missing only`
- `Mandatory only`
- `Used source only`
- `Unused source only` if available.

In this phase filters can be simple anchor links setting CSS classes or re-rendering. No persistent settings needed.

## Tests required

### HTML tests

- Coverage matrix renders rows and status tags.
- Missing mandatory attributes use warning class.
- Expressions are escaped.
- Source Usage section renders used attributes/roles.
- Empty source usage renders `None`.

### Server tests

If Java source is available:

- Attribute status populated for mapped/missing/default/enumMap if supported.
- Source usage groups source members by source class.
- Unused attributes included if model enumeration is implemented.

## Acceptance criteria

- Rule coverage is readable as a matrix.
- Source usage is visible as a dedicated section.
- Missing mandatory attributes are easy to spot.
- All tests pass.

## Agent prompt for Phase 4

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 4 only.

Improve Mapping Overview coverage rendering. Extend coverage DTOs with optional status, expression and source summary fields. Replace text-heavy assigned/unassigned/missing lists with a target coverage matrix per rule: Attribute, Status, Type, Cardinality and Source/Expression. Missing mandatory attributes must be visually obvious.

Add a dedicated Source Usage section. Reuse existing `IlimapSourceUsageSummary` and, if possible, add server-side grouped source usage DTOs. Start with used attributes/roles; include unused attributes if the server can enumerate model attributes reliably.

Add tests for coverage matrix rendering, status classes, safe expression escaping and source usage rendering. Add Java tests for server-side status/source usage if applicable.

Run `npm run build`, `npm run test` and Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 5: Flow Map and filters

## Goal

Add a compact visual data-flow view showing how inputs flow through source classes and rules into target classes and outputs. Add filters to make large mappings manageable.

## Required implementation

### 5.1 Add flow model

In `mappingOverviewMessages.ts`:

```ts
export interface IlimapFlowNode {
  nodeId: string;
  kind: 'input' | 'sourceClass' | 'rule' | 'targetClass' | 'output' | string;
  label: string;
  detail?: string;
  status: 'ok' | 'warning' | 'error' | string;
  location?: IlimapLocation;
}

export interface IlimapFlowEdge {
  id: string;
  fromNodeId: string;
  toNodeId: string;
  kind: 'inputToSource' | 'sourceToRule' | 'ruleToTarget' | 'targetToOutput' | 'ref' | string;
  label?: string;
  status: 'ok' | 'warning' | 'error' | string;
}
```

Add to `IlimapMappingSummary`:

```ts
flowNodes?: IlimapFlowNode[];
flowEdges?: IlimapFlowEdge[];
```

Server implementation:

- Build nodes for inputs, source classes, rules, target classes, outputs.
- Build edges:
  - input → source class,
  - source class → rule,
  - rule → target class,
  - target class → output,
  - optional ref edges rule → rule.

If server-side flow generation is too large for this phase, derive a first simple flow client-side from existing rules/inputs/outputs/ruleCoverage.

### 5.2 Render CSS-based Flow Map

Do not introduce a heavy graph library in this phase. Render a robust CSS grid.

Recommended layout:

```text
Inputs | Source Classes | Rules | Target Classes | Outputs
```

Each column contains node cards. Edges can initially be represented by grouped rows rather than SVG lines.

Example row:

```text
dm01.itf → DM01...LFP3 → lfp3 → DMAV...LFP3 → dmav.xtf
```

Renderer methods:

```ts
function renderFlowMap(summary: IlimapMappingSummary): string
function buildFlowRows(summary: IlimapMappingSummary): FlowRow[]
function renderFlowNode(node: FlowNodeLike): string
function renderFlowBadge(rule: IlimapRuleSummary): string
```

### 5.3 Add filters

Add a read-only filter bar:

```text
All | Errors | Warnings | Missing mandatory | Rules with refs | Rules with bags | Rules with loss
```

Client-side filtering can be implemented with `data-filter` attributes and a small script that toggles CSS classes.

No form inputs required. Use anchor elements:

```html
<a href="#" data-filter="errors">Errors</a>
```

Webview script:

- prevent default,
- set active filter,
- hide/show items with data attributes.

Ensure tests still assert no `<input>`, `<button>`, `<form>`, `contenteditable`, `onclick`.

## Tests required

### HTML tests

- Flow Map section renders.
- Flow rows include inputs/rules/targets/outputs.
- Filters render without editable controls.
- Filter links use `data-filter`, not inline handlers.
- Escaping is preserved.

### Server tests

If server creates flow nodes/edges:

- simple mapping creates expected nodes/edges.
- warning/error rule propagates status to node/edge.
- ref edges are included where possible.

## Acceptance criteria

- Overview contains a compact Flow Map.
- Filtering works client-side without re-requesting server data.
- No heavy graph dependency is introduced.
- All tests pass.

## Agent prompt for Phase 5

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 5 only.

Add a compact read-only Flow Map to the ilimap Mapping Overview. Prefer a CSS/HTML implementation over a heavy graph library. The Flow Map should show Inputs → Source Classes → Rules → Target Classes → Outputs. Add optional flow node/edge DTOs server-side if appropriate; otherwise derive a first flow map client-side from existing summary, rules, inputs, outputs and rule coverage.

Add a non-editing filter bar using anchor elements and `data-filter` attributes for filters such as All, Errors, Warnings, Missing mandatory, Rules with refs and Rules with bags. Keep strict CSP and no editable controls.

Add tests for flow rendering, filter link rendering, escaping and CSP. Add Java flow model tests if server-side flow generation is implemented.

Run `npm run build`, `npm run test` and Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 6: Diagnostics ownership overlay

## Goal

Diagnostics should not only be listed globally. They should be attached to the rule/input/output/attribute they belong to and displayed in context.

## Required implementation

### 6.1 Extend diagnostic DTO

In `mappingOverviewMessages.ts`:

```ts
export interface IlimapDiagnosticSummary {
  code: string;
  severity: string;
  message: string;
  line: number;
  character: number;
  location?: IlimapLocation;
  ownerNodeId?: string;
  ruleId?: string;
  inputId?: string;
  outputId?: string;
  enumMapId?: string;
  targetClass?: string;
  targetAttribute?: string;
}
```

### 6.2 Server-side owner resolver

Add Java service:

```java
public final class IlimapDiagnosticOwnerResolver {
    public IlimapDiagnosticOwner resolve(IlimapAnalysis analysis, Diagnostic diagnostic) {
        ...
    }
}
```

Suggested strategy:

1. Use diagnostic line/character.
2. Use `IlimapPositionResolver.smallestNodeAt(...)`.
3. Walk up the AST to find nearest rule/input/output/enum/bag/ref/assignment.
4. Return stable owner fields:
   - owner node ID,
   - rule ID if inside a rule,
   - target attribute if inside assignment,
   - input/output/enum ID if applicable.

DTO:

```java
public record IlimapDiagnosticOwner(
    String ownerNodeId,
    String ruleId,
    String inputId,
    String outputId,
    String enumMapId,
    String targetClass,
    String targetAttribute
) {}
```

### 6.3 Render diagnostics in context

Update renderers:

- Rule list: show error/warning count badges per rule.
- Rule Inspector: show diagnostics for selected rule.
- Coverage matrix: show diagnostic marker next to affected target attribute.
- Input/output sections: show diagnostics for affected input/output.
- Global diagnostics section remains as full list.

Helpers:

```ts
function diagnosticsForNode(summary: IlimapMappingSummary, nodeId: string): IlimapDiagnosticSummary[]
function diagnosticsForRule(summary: IlimapMappingSummary, ruleId: string): IlimapDiagnosticSummary[]
function diagnosticSeverityCounts(diagnostics: IlimapDiagnosticSummary[]): DiagnosticSeverityCounts
function renderDiagnosticBadges(diagnostics: IlimapDiagnosticSummary[]): string
```

### 6.4 UX behavior

- Red badge for errors.
- Yellow/orange badge for warnings.
- Clicking a diagnostic still navigates to source.
- The global Diagnostics section should group by owner if available:

```text
Rule lfp3
  warning CODE: message
Input dm01
  error CODE: message
Unowned
  info CODE: message
```

## Tests required

### Server tests

- Diagnostic inside rule assignment resolves to rule and target attribute.
- Diagnostic inside input resolves to input.
- Unknown location remains unowned.

### HTML tests

- Rule badges render counts.
- Diagnostics render inside Rule Inspector.
- Coverage row diagnostic marker renders.
- Global diagnostics grouped by owner.
- Escaping preserved.

## Acceptance criteria

- Diagnostics are visible where the developer needs them.
- Global diagnostics still exist.
- Navigation still works.
- All tests pass.

## Agent prompt for Phase 6

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 6 only.

Attach diagnostics to Mapping Overview nodes. Extend `IlimapDiagnosticSummary` with optional owner fields such as `ownerNodeId`, `ruleId`, `inputId`, `outputId`, `enumMapId`, `targetClass` and `targetAttribute`. Implement a Java `IlimapDiagnosticOwnerResolver` using diagnostic positions and existing AST position resolution if Java source is available.

Update webview rendering so rules, inputs, outputs, coverage rows and the Rule Inspector show relevant diagnostic badges/messages in context. Keep the global Diagnostics section but group diagnostics by owner when possible.

Add tests for server-side owner resolution, contextual rendering, grouped diagnostics and navigation. Run `npm run build`, `npm run test` and Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 7: Native Mapping Explorer Tree View

## Goal

Add a native VS Code sidebar Tree View for fast keyboard-friendly navigation. The Webview remains the rich report; the Tree View becomes the everyday explorer.

## Required implementation

### 7.1 Add package contributions

In `package.json`, add a view container and view:

```json
"viewsContainers": {
  "activitybar": [
    {
      "id": "ilimap",
      "title": "ilimap",
      "icon": "images/icon.png"
    }
  ]
},
"views": {
  "ilimap": [
    {
      "id": "ilimap.mappingExplorer",
      "name": "Mapping Explorer"
    }
  ]
}
```

Add commands:

```json
{
  "command": "ilimap.mappingExplorer.refresh",
  "title": "ilimap: Refresh Mapping Explorer"
},
{
  "command": "ilimap.mappingExplorer.revealInEditor",
  "title": "ilimap: Reveal in Editor"
},
{
  "command": "ilimap.mappingExplorer.showInOverview",
  "title": "ilimap: Show in Mapping Overview"
}
```

### 7.2 Add Tree Provider

Create:

```text
src/overview/mappingExplorerProvider.ts
```

Suggested classes:

```ts
export type MappingExplorerItemKind =
  | 'root'
  | 'inputs'
  | 'input'
  | 'outputs'
  | 'output'
  | 'rules'
  | 'rule'
  | 'coverage'
  | 'coverageClass'
  | 'problems'
  | 'diagnostic';

export class MappingExplorerItem extends vscode.TreeItem {
  constructor(
    public readonly kind: MappingExplorerItemKind,
    public readonly label: string,
    public readonly nodeId?: string,
    public readonly location?: IlimapLocation,
    collapsibleState?: vscode.TreeItemCollapsibleState
  ) {
    super(label, collapsibleState);
  }
}

export class MappingExplorerProvider implements vscode.TreeDataProvider<MappingExplorerItem> {
  private readonly onDidChangeTreeDataEmitter = new vscode.EventEmitter<MappingExplorerItem | undefined>();
  readonly onDidChangeTreeData = this.onDidChangeTreeDataEmitter.event;

  private summary?: IlimapMappingSummary;

  refresh(summary?: IlimapMappingSummary): void;
  getTreeItem(element: MappingExplorerItem): vscode.TreeItem;
  getChildren(element?: MappingExplorerItem): Thenable<MappingExplorerItem[]>;
}
```

### 7.3 Connect Tree Provider to overview refresh

In `extension.ts` or a new activation helper:

```ts
const mappingExplorerProvider = new MappingExplorerProvider();
vscode.window.registerTreeDataProvider('ilimap.mappingExplorer', mappingExplorerProvider);
```

When Mapping Overview summary refreshes, update the provider.

If the Webview is not open, the Explorer refresh command should request `ilimap/mappingSummary` for the active `.ilimap` editor.

### 7.4 Navigation

Tree items with a location should have command:

```ts
command: {
  command: 'ilimap.mappingExplorer.revealInEditor',
  title: 'Reveal in Editor',
  arguments: [item]
}
```

Implement command using the same `revealLocation(...)` helper as the Webview.

### 7.5 UX structure

Tree root:

```text
Mapping
  Inputs
    dm01 · itf
  Outputs
    dmav · xtf
  Rules
    lfp3 · warning
  Coverage
    DMAV...LFP3 · 12/14 · 1 missing
  Problems
    warning CODE ...
```

## Tests required

Current tests mock VS Code via Node module replacement. Add `test/mappingExplorerProvider.test.js` if feasible.

Test:

- Provider creates expected root groups.
- Inputs/outputs/rules/coverage/problems appear from summary.
- Items with locations receive commands.
- Refresh emits change event.
- Empty summary yields helpful empty nodes.

Update manifest test:

- Verify view container and view contributions exist.
- Verify commands are contributed.

## Acceptance criteria

- Native Mapping Explorer appears in VS Code Activity Bar/sidebar.
- It navigates to `.ilimap` locations.
- It updates from current summary.
- All tests pass.

## Agent prompt for Phase 7

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 7 only.

Add a native VS Code Mapping Explorer Tree View. Contribute an `ilimap` activity bar container and an `ilimap.mappingExplorer` view in `package.json`. Implement `MappingExplorerProvider` as a `TreeDataProvider` that displays Inputs, Outputs, Rules, Coverage and Problems from the current `IlimapMappingSummary`. Tree items with locations must reveal the corresponding `.ilimap` source position.

Wire the provider into extension activation and update it when the Mapping Overview summary refreshes. Add a refresh command for the active `.ilimap` editor.

Add tests for the provider structure, refresh event, item commands and package manifest contributions. Run `npm run build`, `npm run test` and Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 8: CodeLens and editor integration

## Goal

Add inline developer assistance in `.ilimap` files. Above each rule, show a concise CodeLens summary and actions such as “Show in Overview”.

## Required implementation

### 8.1 Prefer server-side CodeLens

Implement CodeLens in Java Language Server if possible, because the server knows rule ranges and semantic details.

In `IlimapTextDocumentService`, provide CodeLens support if not already present.

CodeLens examples:

```text
Show in Overview | Coverage 12/14 | 2 refs | 1 bag | 1 warning
```

Each rule should get one CodeLens at its rule declaration range.

### 8.2 Commands

Add commands in `package.json`:

```json
{
  "command": "ilimap.showRuleInOverview",
  "title": "ilimap: Show Rule in Mapping Overview"
},
{
  "command": "ilimap.showRuleCoverage",
  "title": "ilimap: Show Rule Coverage"
}
```

Implement in `commands.ts` or a new `overviewCommands.ts`:

```ts
async function showRuleInOverview(uri: string, ruleId: string): Promise<void>
async function showRuleCoverage(uri: string, ruleId: string): Promise<void>
```

Behavior:

- Open Mapping Overview for the URI if not open.
- Select/highlight the rule.
- Request/render Rule Inspector if Phase 3 exists.

### 8.3 Client-side fallback

If server-side CodeLens is not practical, implement a client-side `CodeLensProvider` in TypeScript using current summary and document text. This is less ideal and should be used only if Java source is unavailable.

### 8.4 Selection sync

Add optional editor-to-overview sync:

- When user cursor enters a rule range, post `highlight` to webview if open.
- If tree view exists, reveal/select the corresponding tree item where feasible.

Create:

```text
src/overview/mappingOverviewSelectionSync.ts
```

Suggested class:

```ts
export class MappingOverviewSelectionSync {
  constructor(
    private readonly getCurrentSummary: () => IlimapMappingSummary | undefined,
    private readonly revealInWebview: (nodeId: string) => void
  ) {}

  handleSelectionChange(event: vscode.TextEditorSelectionChangeEvent): void;
  findNodeAtPosition(uri: string, position: vscode.Position): string | undefined;
}
```

Initial implementation can use summary ranges. Later it can call `ilimap/nodeAtPosition`.

## Tests required

### Manifest tests

- CodeLens-related commands are contributed.

### Client tests

- `showRuleInOverview` opens/reuses overview and selects rule.
- Selection sync finds correct rule from summary ranges.

### Server tests

If Java CodeLens implemented:

- Rule CodeLens is produced for each rule.
- Titles include coverage/ref/bag/warning counts when available.
- Commands include URI and rule ID arguments.

## Acceptance criteria

- Rules show useful CodeLens summaries/actions.
- Command opens Mapping Overview and selects/highlights the rule.
- Tests pass.

## Agent prompt for Phase 8

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 8 only.

Add editor integration for the ilimap Mapping Overview. Prefer implementing server-side CodeLens in the Java Language Server: above each `rule`, show a concise read-only summary/action such as `Show in Overview | Coverage 12/14 | 2 refs | 1 bag | 1 warning`. Add VS Code commands `ilimap.showRuleInOverview` and `ilimap.showRuleCoverage` and wire them so the overview opens/reuses its panel and selects the given rule.

If Java CodeLens is not practical in this checkout, implement a client-side fallback using the current summary ranges. Optionally add selection sync so moving the cursor inside a rule highlights that rule in the open overview.

Add tests for commands, manifest contributions, selection/range matching and Java CodeLens generation if applicable. Run `npm run build`, `npm run test` and Java tests if applicable. Fix all failures. Do not implement later phases.
```

---

# Phase 9: Exportable Mapping Report

## Goal

Allow users to export the current mapping overview as Markdown for reviews, documentation or pull requests.

## Required implementation

### 9.1 Add command

In `package.json`:

```json
{
  "command": "ilimap.exportMappingReport",
  "title": "ilimap: Export Mapping Report"
}
```

Register in `commands.ts` or `overviewCommands.ts`.

### 9.2 Add report generator

Create:

```text
src/overview/mappingOverviewReporter.ts
```

Suggested API:

```ts
import type { IlimapMappingSummary } from '../webview/mappingOverviewMessages';

export interface MappingReportOptions {
  includeMermaid?: boolean;
  includeDiagnostics?: boolean;
  includeCoverage?: boolean;
  includeSourceUsage?: boolean;
}

export function renderMappingReportMarkdown(
  summary: IlimapMappingSummary,
  options?: MappingReportOptions
): string;
```

Markdown sections:

```md
# ilimap Mapping Report: <mappingName>

## Summary

- Inputs: ...
- Outputs: ...
- Rules: ...
- Enum maps: ...
- Bags: ...
- Refs: ...
- Diagnostics: ...

## Inputs

## Outputs

## Rules

## Flow

```mermaid
flowchart LR
...
```

## Coverage

## Source Usage

## Diagnostics
```

All Markdown content must be safely escaped for Markdown table contexts where needed.

### 9.3 Export workflow

When command is invoked:

- Use active `.ilimap` editor URI.
- Request `ilimap/mappingSummary`.
- Ask user for save location with `vscode.window.showSaveDialog`.
- Default file name:

```text
<mappingName-or-profile>.mapping-report.md
```

- Write file with `vscode.workspace.fs.writeFile`.
- Show information message with “Open” action.

### 9.4 Optional webview action

Add an `Export report` link to the Mapping Overview header that posts `{ type: 'exportReport' }` and calls the same command.

## Tests required

### Reporter tests

Add `test/mappingOverviewReporter.test.js`:

- Markdown report contains summary.
- Inputs/outputs/rules render.
- Diagnostics render.
- Coverage renders if available.
- Mermaid flow escapes/sanitizes node IDs.
- Dangerous labels do not produce raw HTML execution vectors.

### Command tests

- Command requests summary.
- Save dialog is called with expected default name.
- File write receives Markdown bytes.
- Unavailable summary shows error and does not write file.

### HTML tests

- Export link renders without inline handler.

## Acceptance criteria

- User can export a Markdown report from the active mapping.
- Report is useful for review/documentation.
- Tests pass.

## Agent prompt for Phase 9

```text
Read `./docs/ilimap-vscode-mapping-overview-dx-spec.md` and implement Phase 9 only.

Add an exportable Markdown Mapping Report. Create `src/overview/mappingOverviewReporter.ts` with `renderMappingReportMarkdown(summary, options)` and include summary, inputs, outputs, rules, optional Mermaid flow, coverage, source usage and diagnostics. Add VS Code command `ilimap.exportMappingReport` that requests the active `.ilimap` summary, asks for a save location, writes the Markdown file and offers to open it. Optionally add an Export link in the Mapping Overview header that triggers the same command.

Add tests for Markdown rendering, escaping/sanitization, command behavior, save-dialog handling and unavailable summary handling. Run `npm run build`, `npm run test` and Java tests if applicable. Fix all failures. Do not implement unrelated features.
```

---

## 6. Detailed implementation notes

### 6.1 Keep tests framework-consistent

The current project uses plain Node tests with `node:test` and module mocking via `Module._load`. Continue using that style unless the project has changed.

Do not introduce a heavy test framework unless absolutely necessary.

### 6.2 Keep webview dependency-free for now

Do not add React/Vue/Svelte/D3 in these phases. The current webview is simple and safe. Use:

- TypeScript render helpers,
- escaped template strings,
- CSS grid/flex,
- small nonce-protected script for click delegation/filtering.

A graph library can be evaluated later, but it is not necessary for the proposed Flow Map.

### 6.3 Accessibility

When adding links/actions:

- Use clear link text.
- Use `aria-label` when visible text is ambiguous.
- Preserve keyboard navigation.
- Avoid relying on color only for status; use text badges too.

### 6.4 Large mapping performance

Avoid rendering thousands of rows in Phase 3-5 if server payloads become large. Reasonable first limits:

- Render all rules initially if mappings are modest.
- For very large assignment lists, render grouped details in the inspector only after selection.
- Prefer lazy `ilimap/ruleDetail` for heavy details.

### 6.5 Error handling patterns

Every custom LSP request must be wrapped:

```ts
try {
  ...
} catch (error) {
  outputChannel.appendLine(`...: ${errorMessage(error)}`);
  vscode.window.showErrorMessage('...');
}
```

Server custom requests should return `available=false` DTOs where possible for semantic unavailability, and reserve exceptions for unexpected failures.

### 6.6 Versioning of DTOs

If DTO shape grows significantly, add optional schema version:

```ts
schemaVersion?: number;
```

Do not require the client to fail if `schemaVersion` is missing. Treat missing as version 0/current legacy.

### 6.7 Package and publishing checks

The project has VSIX scripts:

```bash
npm run package:vsix
npm run check:vsix
```

Run these when a phase changes package contributions, images, bundled files or command/view declarations, especially Phase 7-9.

---

## 7. Final desired user experience

After all phases, the developer workflow should be:

1. Open a `.ilimap` file.
2. Open `ilimap: Open Mapping Overview`.
3. See a compact summary, diagnostics, coverage and flow map.
4. Click a rule and see a detailed Rule Inspector.
5. Click a target attribute and understand whether it is mapped, constant, computed, defaulted, missing or referenced.
6. See which source attributes are used and where.
7. See diagnostics directly on affected rules/attributes.
8. Use the sidebar Mapping Explorer for fast navigation.
9. Use CodeLens above rules for quick overview actions.
10. Export a Markdown report for reviews and documentation.

The result should feel like a professional ETL/mapping development environment, not just syntax highlighting.

---

## 8. Out of scope for this roadmap

Do not implement these unless a later spec explicitly requests them:

- Full graphical mapping editor.
- Drag-and-drop mapping creation.
- Automatic modification of `.ilimap` source from the webview.
- Heavy graph rendering framework.
- Runtime transform execution dashboard.
- Data preview of actual input rows.
- Database connection testing UI.
- Secrets/password display.

These may be useful later, but they are outside this read-only Mapping Overview DX roadmap.

---

## 9. Recommended commit structure

Use one commit per phase or one PR per phase.

Suggested commit messages:

```text
vscode: harden mapping overview panel lifecycle
vscode: add mapping overview navigation locations
vscode: refresh mapping overview on document changes
vscode: add read-only rule inspector
vscode: improve coverage and source usage views
vscode: add mapping flow overview and filters
vscode: attach diagnostics to overview nodes
vscode: add native mapping explorer view
vscode: add ilimap rule codelens actions
vscode: export mapping overview reports
```

Each commit/PR must include tests.

---

## 10. Phase completion checklist

Before finishing any phase, verify:

```text
[ ] I read `./docs/ilimap-vscode-mapping-overview-dx-spec.md`.
[ ] I implemented only the requested phase.
[ ] I inspected existing code before changing it.
[ ] I preserved read-only behavior of the Mapping Overview.
[ ] I preserved strict CSP and escaping.
[ ] I added or updated tests.
[ ] `npm run build` passes.
[ ] `npm run test` passes.
[ ] Java tests pass if Java server source was changed.
[ ] README/docs updated if user-facing behavior changed.
[ ] No known failing tests remain.
[ ] No production TODO stubs remain.
```
