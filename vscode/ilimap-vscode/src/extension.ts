import * as vscode from 'vscode';

import { registerCommands } from './commands';
import { startLanguageClient, stopLanguageClient } from './client';

let outputChannel: vscode.OutputChannel | undefined;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  outputChannel = vscode.window.createOutputChannel('ILIMAP Language Server');
  context.subscriptions.push(outputChannel);

  registerCommands(context, outputChannel);
  await startLanguageClient(context, outputChannel);
}

export async function deactivate(): Promise<void> {
  await stopLanguageClient(outputChannel);
  outputChannel = undefined;
}
