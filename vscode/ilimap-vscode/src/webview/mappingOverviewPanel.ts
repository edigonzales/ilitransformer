import * as crypto from 'crypto';
import * as vscode from 'vscode';

import { getLanguageClient } from '../client';
import { renderMappingOverviewHtml } from './mappingOverviewHtml';
import {
  mappingSummaryRequest,
  type IlimapMappingSummary,
  type IlimapMappingSummaryParams
} from './mappingOverviewMessages';

let currentPanel: vscode.WebviewPanel | undefined;

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

  const panel = createOrRevealPanel(context);
  registerNavigationHandler(panel, uri, outputChannel);
  panel.webview.html = renderMappingOverviewHtml(summary, nonce());
}

function createOrRevealPanel(context: vscode.ExtensionContext): vscode.WebviewPanel {
  if (currentPanel) {
    currentPanel.reveal(vscode.ViewColumn.Beside);
    return currentPanel;
  }

  currentPanel = vscode.window.createWebviewPanel(
    'ilimapMappingOverview',
    'ilimap Mapping Overview',
    vscode.ViewColumn.Beside,
    {
      enableScripts: true,
      retainContextWhenHidden: false,
      localResourceRoots: [context.extensionUri]
    }
  );
  currentPanel.onDidDispose(() => {
    currentPanel = undefined;
  });
  return currentPanel;
}

function registerNavigationHandler(
  panel: vscode.WebviewPanel,
  uri: string,
  outputChannel: vscode.OutputChannel
): void {
  panel.webview.onDidReceiveMessage(async message => {
    if (!isNavigationMessage(message)) {
      return;
    }
    try {
      const document = await vscode.workspace.openTextDocument(vscode.Uri.parse(uri));
      const editor = await vscode.window.showTextDocument(document, vscode.ViewColumn.One, false);
      const position = new vscode.Position(message.line, message.character);
      editor.selection = new vscode.Selection(position, position);
      editor.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenterIfOutsideViewport);
    } catch (error) {
      outputChannel.appendLine(`Failed to navigate from ilimap mapping overview: ${errorMessage(error)}`);
      vscode.window.showErrorMessage('Failed to navigate from ilimap mapping overview.');
    }
  });
}

function isNavigationMessage(message: unknown): message is { type: 'navigate'; line: number; character: number } {
  if (!message || typeof message !== 'object') {
    return false;
  }
  const candidate = message as { type?: unknown; line?: unknown; character?: unknown };
  return candidate.type === 'navigate'
    && typeof candidate.line === 'number'
    && Number.isInteger(candidate.line)
    && candidate.line >= 0
    && typeof candidate.character === 'number'
    && Number.isInteger(candidate.character)
    && candidate.character >= 0;
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
