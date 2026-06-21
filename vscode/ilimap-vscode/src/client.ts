import * as vscode from 'vscode';
import { LanguageClient, type LanguageClientOptions, type ServerOptions } from 'vscode-languageclient/node';

import { resolveJavaRuntime, resolveJvmArgs, resolveServerJarPath } from './configuration';

let client: LanguageClient | undefined;
let starting: Promise<void> | undefined;

export async function startLanguageClient(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  if (starting) {
    outputChannel.appendLine('ilimap language server start is already in progress.');
    return starting;
  }
  if (client) {
    outputChannel.appendLine('ilimap language server is already running.');
    return;
  }

  starting = doStartLanguageClient(context, outputChannel);
  try {
    await starting;
  } finally {
    starting = undefined;
  }
}

async function doStartLanguageClient(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  const config = vscode.workspace.getConfiguration('ilimap');
  const serverJar = resolveServerJarPath(context, config.get<string>('server.jarPath'));
  const javaRuntime = resolveJavaRuntime(context, config.get<string>('java.path'));
  const jvmArgs = resolveJvmArgs(config.get<readonly string[]>('server.jvmArgs'));

  outputChannel.appendLine('Starting ilimap language server.');
  outputChannel.appendLine(`Java runtime: ${describeJavaRuntime(javaRuntime)}`);
  outputChannel.appendLine(`Java path: ${javaRuntime.command}`);
  outputChannel.appendLine(`Server JAR: ${serverJar}`);
  outputChannel.appendLine(`JVM args: ${jvmArgs.length === 0 ? '(none)' : jvmArgs.join(' ')}`);

  const serverOptions: ServerOptions = {
    command: javaRuntime.command,
    args: [...jvmArgs, '-jar', serverJar],
    options: {}
  };
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

  const nextClient = new LanguageClient('ilimapLanguageServer', 'ilimap Language Server', serverOptions, clientOptions);
  client = nextClient;

  try {
    await nextClient.start();
    outputChannel.appendLine('ilimap language server started.');
  } catch (error) {
    if (client === nextClient) {
      client = undefined;
    }
    outputChannel.appendLine(`ilimap language server failed to start: ${errorMessage(error)}`);
    throw error;
  }
}

export async function stopLanguageClient(outputChannel?: vscode.OutputChannel): Promise<void> {
  if (starting) {
    outputChannel?.appendLine('Waiting for ilimap language server start before stopping.');
    await starting.catch(() => undefined);
  }

  if (!client) {
    outputChannel?.appendLine('ilimap language server is not running.');
    return;
  }

  const runningClient = client;
  client = undefined;
  outputChannel?.appendLine('Stopping ilimap language server.');
  await runningClient.stop();
  outputChannel?.appendLine('ilimap language server stopped.');
}

export async function restartLanguageClient(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  outputChannel.appendLine('Restarting ilimap language server.');
  await stopLanguageClient(outputChannel);
  await startLanguageClient(context, outputChannel);
  outputChannel.appendLine('ilimap language server restart complete.');
}

export function getLanguageClient(): LanguageClient | undefined {
  return client;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function describeJavaRuntime(javaRuntime: { source: string; platformId?: string }): string {
  if (javaRuntime.source === 'configured') {
    return 'configured executable';
  }
  if (javaRuntime.source === 'bundled') {
    return `bundled runtime${javaRuntime.platformId ? ` (${javaRuntime.platformId})` : ''}`;
  }
  return 'system executable from PATH';
}
