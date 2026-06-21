import * as vscode from 'vscode';

import { restartLanguageClient } from './client';
import { openMappingOverview } from './webview/mappingOverviewPanel';

export function registerCommands(context: vscode.ExtensionContext, outputChannel: vscode.OutputChannel): void {
  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.restartLanguageServer', async () => {
      await restartLanguageClient(context, outputChannel);
      vscode.window.showInformationMessage('ILIMAP language server restarted.');
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
      await vscode.commands.executeCommand('workbench.action.problems.focus');
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.openMappingOverview', async () => {
      await openMappingOverview(context, outputChannel);
    })
  );
}
