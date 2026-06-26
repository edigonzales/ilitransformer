import * as crypto from 'crypto';
import * as vscode from 'vscode';

import { getLanguageClient } from '../client';
import { getMappingExplorerInstance } from '../overview/mappingExplorerProvider';
import { renderMappingOverviewHtml } from './mappingOverviewHtml';
import {
  mappingSummaryRequest,
  ruleDetailRequest,
  type IlimapLocation,
  type IlimapMappingSummary,
  type IlimapMappingSummaryParams,
  type IlimapRuleDetailParams,
  type IlimapRuleDetailSummary
} from './mappingOverviewMessages';
import type {
  MappingOverviewPanelState,
  MappingOverviewRefreshOptions,
  MappingOverviewRenderState
} from './mappingOverviewState';

const CHANGE_DEBOUNCE_MS = 500;

let currentPanelState: MappingOverviewPanelState | undefined;

export async function openMappingOverview(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  const editor = vscode.window.activeTextEditor;
  if (!editor || !isIlimapDocument(editor.document)) {
    vscode.window.showInformationMessage('Open an .ilimap document before opening the ilimap mapping overview.');
    return;
  }

  const client = getLanguageClient();
  if (!client) {
    vscode.window.showErrorMessage('ilimap language server is not running.');
    return;
  }

  const uri = editor.document.uri.toString();
  let summary: IlimapMappingSummary;
  try {
    summary = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Opening ilimap mapping overview'
      },
      () =>
        client.sendRequest<IlimapMappingSummary>(
          mappingSummaryRequest,
          { uri } satisfies IlimapMappingSummaryParams
        )
    );
  } catch (error) {
    outputChannel.appendLine(`Failed to load ilimap mapping overview: ${errorMessage(error)}`);
    vscode.window.showErrorMessage('Failed to load ilimap mapping overview.');
    return;
  }

  const state = createOrRevealPanelState(context, uri, outputChannel);
  state.summary = summary;
  state.documentVersion = editor.document.version;
  state.lastUpdated = formatTime(new Date());
  state.loading = false;
  renderPanel(state, { refreshState: 'idle', lastUpdated: state.lastUpdated });
  getMappingExplorerInstance()?.refresh(summary, uri);
}

function createOrRevealPanelState(
  context: vscode.ExtensionContext,
  uri: string,
  outputChannel: vscode.OutputChannel
): MappingOverviewPanelState {
  if (currentPanelState) {
    currentPanelState.uri = uri;
    currentPanelState.panel.reveal(vscode.ViewColumn.Beside);
    return currentPanelState;
  }

  const panel = vscode.window.createWebviewPanel(
    'ilimapMappingOverview',
    'ilimap Mapping Overview',
    vscode.ViewColumn.Beside,
    {
      enableScripts: true,
      retainContextWhenHidden: false,
      localResourceRoots: [context.extensionUri]
    }
  );

  const state: MappingOverviewPanelState = {
    panel,
    uri,
    loading: false,
    disposed: false,
    selectedRuleDetail: undefined,
    disposables: []
  };
  state.disposables.push(registerMessageHandler(state, outputChannel));
  registerDocumentListeners(state, outputChannel);
  panel.onDidDispose(() => {
    state.disposed = true;
    if (state.refreshTimer) {
      clearTimeout(state.refreshTimer);
      state.refreshTimer = undefined;
    }
    for (const disposable of state.disposables) {
      disposable?.dispose?.();
    }
    state.disposables.length = 0;
    currentPanelState = undefined;
  });

  currentPanelState = state;
  return state;
}

function registerMessageHandler(
  state: MappingOverviewPanelState,
  outputChannel: vscode.OutputChannel
): vscode.Disposable {
  return state.panel.webview.onDidReceiveMessage(async message => {
    if (isRefreshMessage(message)) {
      await refreshMappingOverview(state, outputChannel, { reason: 'manual' });
      return;
    }
    if (message && typeof message === 'object' && (message as { type?: unknown }).type === 'requestRuleDetail') {
      const ruleId = (message as { ruleId?: unknown }).ruleId;
      if (typeof ruleId === 'string') {
        await requestRuleDetail(state, ruleId, outputChannel);
      }
      return;
    }
    if (message && typeof message === 'object' && (message as { type?: unknown }).type === 'selectNode') {
      const nodeId = (message as { nodeId?: unknown }).nodeId;
      if (typeof nodeId === 'string' && nodeId.startsWith('rule:')) {
        await requestRuleDetail(state, nodeId.substring(5), outputChannel);
      }
      return;
    }
    const location = parseNavigationMessage(message);
    if (!location) {
      return;
    }
    try {
      await revealLocation(state.uri, location, outputChannel);
    } catch (error) {
      outputChannel.appendLine(`Failed to navigate from ilimap mapping overview: ${errorMessage(error)}`);
      vscode.window.showErrorMessage('Failed to navigate from ilimap mapping overview.');
    }
  });
}

function registerDocumentListeners(
  state: MappingOverviewPanelState,
  outputChannel: vscode.OutputChannel
): void {
  const saveDisposable = vscode.workspace.onDidSaveTextDocument?.(document => {
    if (!matchesPanelDocument(state, document)) {
      return;
    }
    state.documentVersion = document.version;
    void refreshMappingOverview(state, outputChannel, { reason: 'save' });
  });
  if (saveDisposable) {
    state.disposables.push(saveDisposable);
  }

  const changeDisposable = vscode.workspace.onDidChangeTextDocument?.(event => {
    const document = event?.document;
    if (!matchesPanelDocument(state, document)) {
      return;
    }
    state.documentVersion = document.version;
    scheduleDebouncedRefresh(state, outputChannel);
  });
  if (changeDisposable) {
    state.disposables.push(changeDisposable);
  }
}

function matchesPanelDocument(
  state: MappingOverviewPanelState,
  document: vscode.TextDocument | undefined
): document is vscode.TextDocument {
  if (!document || !isIlimapDocument(document)) {
    return false;
  }
  return document.uri?.toString() === state.uri;
}

function scheduleDebouncedRefresh(
  state: MappingOverviewPanelState,
  outputChannel: vscode.OutputChannel
): void {
  if (state.refreshTimer) {
    clearTimeout(state.refreshTimer);
  }
  state.refreshTimer = setTimeout(() => {
    state.refreshTimer = undefined;
    void refreshMappingOverview(state, outputChannel, { reason: 'change' });
  }, CHANGE_DEBOUNCE_MS);
}

async function refreshMappingOverview(
  state: MappingOverviewPanelState,
  outputChannel: vscode.OutputChannel,
  _options: MappingOverviewRefreshOptions
): Promise<void> {
  if (state.disposed) {
    return;
  }

  const client = getLanguageClient();
  if (!client) {
    renderPanel(state, {
      refreshState: 'error',
      errorMessage: 'ilimap language server is not running.',
      lastUpdated: state.lastUpdated
    });
    return;
  }

  state.loading = true;
  renderPanel(state, { refreshState: 'loading', lastUpdated: state.lastUpdated });

  try {
    const summary = await client.sendRequest<IlimapMappingSummary>(
      mappingSummaryRequest,
      { uri: state.uri } satisfies IlimapMappingSummaryParams
    );
    if (state.disposed) {
      return;
    }
    state.summary = summary;
    state.lastUpdated = formatTime(new Date());
    state.loading = false;
    renderPanel(state, { refreshState: 'idle', lastUpdated: state.lastUpdated });
    getMappingExplorerInstance()?.refresh(summary, state.uri);
  } catch (error) {
    state.loading = false;
    outputChannel.appendLine(`Failed to refresh ilimap mapping overview: ${errorMessage(error)}`);
    if (state.disposed) {
      return;
    }
    renderPanel(state, {
      refreshState: 'error',
      errorMessage: errorMessage(error),
      lastUpdated: state.lastUpdated
    });
  }
}

async function requestRuleDetail(
  state: MappingOverviewPanelState,
  ruleId: string,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  if (state.disposed) {
    return;
  }

  const client = getLanguageClient();
  if (!client) {
    state.selectedRuleDetail = {
      available: false,
      message: 'ilimap language server is not running.',
      ruleId,
      sources: [],
      joins: [],
      identity: [],
      assignments: [],
      defaults: [],
      bags: [],
      refs: [],
      losses: [],
      diagnostics: []
    };
    renderPanel(state, { refreshState: 'idle', lastUpdated: state.lastUpdated });
    return;
  }

  try {
    const detail = await client.sendRequest<IlimapRuleDetailSummary>(
      ruleDetailRequest,
      { uri: state.uri, ruleId } satisfies IlimapRuleDetailParams
    );
    if (state.disposed) {
      return;
    }
    state.selectedRuleDetail = detail;
    renderPanel(state, { refreshState: 'idle', lastUpdated: state.lastUpdated });
  } catch (error) {
    outputChannel.appendLine(`Failed to request rule detail: ${errorMessage(error)}`);
    if (state.disposed) {
      return;
    }
    state.selectedRuleDetail = {
      available: false,
      message: errorMessage(error),
      ruleId,
      sources: [],
      joins: [],
      identity: [],
      assignments: [],
      defaults: [],
      bags: [],
      refs: [],
      losses: [],
      diagnostics: []
    };
    renderPanel(state, {
      refreshState: 'error',
      errorMessage: 'Failed to load rule detail.',
      lastUpdated: state.lastUpdated
    });
  }
}

function renderPanel(state: MappingOverviewPanelState, renderState: MappingOverviewRenderState): void {
  if (state.disposed) {
    return;
  }
  const summary = state.summary ?? unavailableSummary('No mapping summary is available.');
  state.panel.webview.html = renderMappingOverviewHtml(summary, nonce(), renderState, state.selectedRuleDetail);
}

function unavailableSummary(message: string): IlimapMappingSummary {
  return {
    available: false,
    message,
    mappingName: '',
    inputCount: 0,
    outputCount: 0,
    ruleCount: 0,
    enumMapCount: 0,
    bagCount: 0,
    refCount: 0,
    errorCount: 0,
    warningCount: 0,
    informationCount: 0,
    hintCount: 0,
    inputs: [],
    outputs: [],
    enumMaps: [],
    rules: [],
    diagnostics: []
  };
}

function isRefreshMessage(message: unknown): boolean {
  return !!message
    && typeof message === 'object'
    && (message as { type?: unknown }).type === 'refresh';
}

function parseNavigationMessage(message: unknown): IlimapLocation | undefined {
  if (!message || typeof message !== 'object') {
    return undefined;
  }
  const candidate = message as { type?: unknown; line?: unknown; character?: unknown; location?: unknown };

  if (candidate.type === 'navigate') {
    return parseLegacyNavigate(candidate);
  }

  if (candidate.type === 'navigateToLocation') {
    return parseLocationNavigate(candidate);
  }

  return undefined;
}

function parseLegacyNavigate(candidate: { line?: unknown; character?: unknown }): IlimapLocation | undefined {
  if (!isValidLineCharacter(candidate.line, candidate.character)) {
    return undefined;
  }
  return {
    line: candidate.line as number,
    character: candidate.character as number
  };
}

function parseLocationNavigate(candidate: { location?: unknown }): IlimapLocation | undefined {
  if (!candidate.location || typeof candidate.location !== 'object') {
    return undefined;
  }
  const loc = candidate.location as { line?: unknown; character?: unknown; endLine?: unknown; endCharacter?: unknown };
  if (!isValidLineCharacter(loc.line, loc.character)) {
    return undefined;
  }
  const result: IlimapLocation = {
    line: loc.line as number,
    character: loc.character as number
  };
  if (isValidLineCharacter(loc.endLine, loc.endCharacter)) {
    result.endLine = loc.endLine as number;
    result.endCharacter = loc.endCharacter as number;
  }
  return result;
}

function isValidLineCharacter(line: unknown, character: unknown): boolean {
  return typeof line === 'number'
    && Number.isInteger(line)
    && line >= 0
    && typeof character === 'number'
    && Number.isInteger(character)
    && character >= 0;
}

export async function revealLocation(
  uri: string,
  location: IlimapLocation,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  const document = await vscode.workspace.openTextDocument(vscode.Uri.parse(uri));
  const editor = await vscode.window.showTextDocument(document, vscode.ViewColumn.One, false);
  const start = new vscode.Position(location.line, location.character);
  const end = location.endLine !== undefined && location.endCharacter !== undefined
    ? new vscode.Position(location.endLine, location.endCharacter)
    : start;
  editor.selection = new vscode.Selection(start, end);
  editor.revealRange(new vscode.Range(start, end), vscode.TextEditorRevealType.InCenterIfOutsideViewport);
}

export function isIlimapDocument(document: vscode.TextDocument): boolean {
  return document.languageId === 'ilimap' || document.uri.fsPath.endsWith('.ilimap');
}

function nonce(): string {
  return crypto.randomBytes(16).toString('base64');
}

function formatTime(date: Date): string {
  return date.toLocaleTimeString();
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
