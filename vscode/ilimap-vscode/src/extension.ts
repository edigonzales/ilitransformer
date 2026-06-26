import * as vscode from 'vscode';

import { registerCommands } from './commands';
import { startLanguageClient, stopLanguageClient, getLanguageClient } from './client';
import {
  MappingExplorerProvider,
  setMappingExplorerInstance
} from './overview/mappingExplorerProvider';
import {
  openMappingOverview,
  revealLocation,
  isIlimapDocument,
  getOpenOverviewSummary,
  revealRuleInOpenOverview
} from './webview/mappingOverviewPanel';
import { MappingOverviewSelectionSync } from './overview/mappingOverviewSelectionSync';
import type { IlimapLocation } from './webview/mappingOverviewMessages';

let outputChannel: vscode.OutputChannel | undefined;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  outputChannel = vscode.window.createOutputChannel('ilimap Language Server');
  context.subscriptions.push(outputChannel);

  const mappingExplorerProvider = new MappingExplorerProvider();
  setMappingExplorerInstance(mappingExplorerProvider);
  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('ilimap.mappingExplorer', mappingExplorerProvider)
  );

  registerCommands(context, outputChannel);
  registerExplorerCommands(context, outputChannel, mappingExplorerProvider);
  registerSelectionSync(context, outputChannel);
  await startLanguageClient(context, outputChannel);
}

function registerSelectionSync(context: vscode.ExtensionContext, outputChannel: vscode.OutputChannel): void {
  const sync = new MappingOverviewSelectionSync(
    () => getOpenOverviewSummary(),
    nodeId => {
      void revealRuleInOpenOverview(nodeId, outputChannel);
    }
  );

  let timer: ReturnType<typeof setTimeout> | undefined;
  const disposable = vscode.window.onDidChangeTextEditorSelection?.(event => {
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(() => {
      timer = undefined;
      sync.handleSelectionChange(event);
    }, 250);
  });
  if (disposable) {
    context.subscriptions.push(disposable);
  }
}

function registerExplorerCommands(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel,
  provider: MappingExplorerProvider
): void {
  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.mappingExplorer.refresh', async () => {
      await refreshMappingExplorer(provider, outputChannel);
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand(
      'ilimap.mappingExplorer.revealInEditor',
      async (uri: string, location: IlimapLocation) => {
        if (typeof uri !== 'string' || !location) {
          return;
        }
        try {
          await revealLocation(uri, location, outputChannel);
        } catch (error) {
          outputChannel.appendLine(`Failed to reveal in editor: ${errorMessage(error)}`);
        }
      }
    )
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.mappingExplorer.showInOverview', async () => {
      await openMappingOverview(context, outputChannel);
    })
  );
}

async function refreshMappingExplorer(
  provider: MappingExplorerProvider,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  const editor = vscode.window.activeTextEditor;
  if (!editor || !isIlimapDocument(editor.document)) {
    vscode.window.showInformationMessage('Open an .ilimap document before refreshing the mapping explorer.');
    return;
  }

  const client = getLanguageClient();
  if (!client) {
    vscode.window.showErrorMessage('ilimap language server is not running.');
    return;
  }

  const uri = editor.document.uri.toString();
  try {
    const summary = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Refreshing mapping explorer'
      },
      () =>
        client.sendRequest<any>('ilimap/mappingSummary', { uri })
    );
    provider.refresh(summary, uri);
  } catch (error) {
    outputChannel.appendLine(`Failed to refresh mapping explorer: ${errorMessage(error)}`);
    vscode.window.showErrorMessage('Failed to refresh mapping explorer.');
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export async function deactivate(): Promise<void> {
  await stopLanguageClient(outputChannel);
  outputChannel = undefined;
}
