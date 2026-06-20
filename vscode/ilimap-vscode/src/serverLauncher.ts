import * as vscode from 'vscode';
import { ServerOptions } from 'vscode-languageclient/node';

export function resolveServerOptions(
  context: vscode.ExtensionContext,
  outputChannel: vscode.OutputChannel
): ServerOptions {
  const config = vscode.workspace.getConfiguration('ilimap');
  const javaPath = config.get<string>('java.path')?.trim() || 'java';
  const configuredJar = config.get<string>('server.jar')?.trim() || '';
  const serverJar = configuredJar || context.asAbsolutePath('server/ilimap-language-server.jar');

  outputChannel.appendLine(`Starting ILIMAP language server: ${javaPath} -jar ${serverJar}`);

  return {
    command: javaPath,
    args: ['-jar', serverJar],
    options: {}
  };
}
