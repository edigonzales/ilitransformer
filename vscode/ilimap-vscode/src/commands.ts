import * as vscode from 'vscode';

import { getLanguageClient, restartLanguageClient } from './client';
import { openMappingOverview } from './webview/mappingOverviewPanel';

const validateMappingRequest = 'ilimap/validateMapping';

interface IlimapValidateMappingParams {
  uri: string;
  text: string;
  version: number;
}

export function registerCommands(context: vscode.ExtensionContext, outputChannel: vscode.OutputChannel): void {
  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.restartLanguageServer', async () => {
      await restartLanguageClient(context, outputChannel);
      vscode.window.showInformationMessage('ilimap language server restarted.');
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.showLanguageServerLogs', async () => {
      outputChannel.show(true);
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.format', async () => {
      await vscode.commands.executeCommand('editor.action.formatDocument');
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.validate', async () => {
      if (await validateActiveMapping(outputChannel)) {
        await vscode.commands.executeCommand('workbench.action.problems.focus');
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.openMappingOverview', async () => {
      await openMappingOverview(context, outputChannel);
    })
  );
}

async function validateActiveMapping(outputChannel: vscode.OutputChannel): Promise<boolean> {
  const editor = vscode.window.activeTextEditor;
  if (!editor || !isIlimapDocument(editor.document)) {
    vscode.window.showInformationMessage('Open an .ilimap document before validating the ilimap mapping.');
    return false;
  }

  const client = getLanguageClient();
  if (!client) {
    vscode.window.showErrorMessage('ilimap language server is not running.');
    return false;
  }

  try {
    await client.sendRequest(validateMappingRequest, {
      uri: editor.document.uri.toString(),
      text: editor.document.getText(),
      version: editor.document.version
    } satisfies IlimapValidateMappingParams);
    return true;
  } catch (error) {
    outputChannel.appendLine(`Failed to validate ilimap mapping: ${errorMessage(error)}`);
    vscode.window.showErrorMessage('Failed to validate ilimap mapping.');
    return false;
  }
}

function isIlimapDocument(document: vscode.TextDocument): boolean {
  return document.languageId === 'ilimap' || document.uri.fsPath.endsWith('.ilimap');
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
