import * as crypto from 'crypto';
import * as vscode from 'vscode';

import { getLanguageClient } from '../client';
import { renderMappingOverviewHtml } from './mappingOverviewHtml';
import {
  mappingSummaryRequest,
  type IlimapLocation,
  type IlimapMappingSummary,
  type IlimapMappingSummaryParams
} from './mappingOverviewMessages';

interface MappingOverviewPanelState {
  panel: vscode.WebviewPanel;
  uri: string;
  disposables: vscode.Disposable[];
}

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
  state.panel.webview.html = renderMappingOverviewHtml(summary, nonce());
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

  const state: MappingOverviewPanelState = { panel, uri, disposables: [] };
  state.disposables.push(registerNavigationHandler(state, outputChannel));
  panel.onDidDispose(() => {
    for (const disposable of state.disposables) {
      disposable?.dispose?.();
    }
    state.disposables.length = 0;
    currentPanelState = undefined;
  });

  currentPanelState = state;
  return state;
}

function registerNavigationHandler(
  state: MappingOverviewPanelState,
  outputChannel: vscode.OutputChannel
): vscode.Disposable {
  return state.panel.webview.onDidReceiveMessage(async message => {
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

async function revealLocation(
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

function isIlimapDocument(document: vscode.TextDocument): boolean {
  return document.languageId === 'ilimap' || document.uri.fsPath.endsWith('.ilimap');
}

function nonce(): string {
  return crypto.randomBytes(16).toString('base64');
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
