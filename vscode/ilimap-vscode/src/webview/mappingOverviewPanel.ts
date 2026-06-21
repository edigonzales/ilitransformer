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
    summary = await client.sendRequest<IlimapMappingSummary>(
      mappingSummaryRequest,
      { uri } satisfies IlimapMappingSummaryParams
    );
  } catch (error) {
    outputChannel.appendLine(`Failed to load ilimap mapping overview: ${errorMessage(error)}`);
    vscode.window.showErrorMessage('Failed to load ilimap mapping overview.');
    return;
  }

  const panel = createOrRevealPanel(context);
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
      enableScripts: false,
      retainContextWhenHidden: false,
      localResourceRoots: [context.extensionUri]
    }
  );
  currentPanel.onDidDispose(() => {
    currentPanel = undefined;
  });
  return currentPanel;
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
