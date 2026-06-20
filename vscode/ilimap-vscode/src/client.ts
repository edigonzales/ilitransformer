import * as vscode from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';

import { resolveServerOptions } from './serverLauncher';

let client: LanguageClient | undefined;

export async function startLanguageClient(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  if (client) {
    return;
  }

  const serverOptions: ServerOptions = resolveServerOptions(context, outputChannel);
  const clientOptions: LanguageClientOptions = {
    documentSelector: [
      {
        scheme: 'file',
        language: 'ilimap'
      }
    ],
    outputChannel,
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.ilimap')
    }
  };

  client = new LanguageClient('ilimapLanguageServer', 'ILIMAP Language Server', serverOptions, clientOptions);
  await client.start();
}

export async function stopLanguageClient(): Promise<void> {
  if (!client) {
    return;
  }

  const runningClient = client;
  client = undefined;
  await runningClient.stop();
}

export async function restartLanguageClient(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  await stopLanguageClient();
  await startLanguageClient(context, outputChannel);
}
