import * as vscode from 'vscode';

import { registerCommands } from './commands';
import { startLanguageClient, stopLanguageClient } from './client';

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const outputChannel = vscode.window.createOutputChannel('ILIMAP Language Server');
  context.subscriptions.push(outputChannel);

  registerCommands(context, outputChannel);
  await startLanguageClient(context, outputChannel);
}

export async function deactivate(): Promise<void> {
  await stopLanguageClient();
}
