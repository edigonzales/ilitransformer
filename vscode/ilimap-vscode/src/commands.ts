import * as vscode from 'vscode';

import { restartLanguageClient } from './client';

export function registerCommands(context: vscode.ExtensionContext, outputChannel: vscode.OutputChannel): void {
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
