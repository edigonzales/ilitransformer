import * as vscode from 'vscode';

import { getLanguageClient, restartLanguageClient } from './client';
import { renderMappingReportMarkdown } from './overview/mappingOverviewReporter';
import { openMappingOverview, showRuleCoverage, showRuleInOverview } from './webview/mappingOverviewPanel';
import { mappingSummaryRequest, type IlimapMappingSummary, type IlimapMappingSummaryParams } from './webview/mappingOverviewMessages';

const validateMappingRequest = 'ilimap/validateMapping';

interface IlimapValidateMappingParams {
  uri: string;
  text: string;
  version: number;
}

interface IlimapValidateMappingResult {
  available: boolean;
  message: string;
  diagnosticCount: number;
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

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.showRuleInOverview', async (uri?: string, ruleId?: string) => {
      await showRuleInOverview(context, outputChannel, uri, ruleId);
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.showRuleCoverage', async (uri?: string, ruleId?: string) => {
      await showRuleCoverage(context, outputChannel, uri, ruleId);
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('ilimap.exportMappingReport', async () => {
      await exportMappingReportCommand(outputChannel);
    })
  );
}

export async function exportMappingReportCommand(outputChannel: vscode.OutputChannel): Promise<void> {
  const editor = vscode.window.activeTextEditor;
  if (!editor || !isIlimapDocument(editor.document)) {
    vscode.window.showInformationMessage('Open an .ilimap document before exporting the ilimap mapping report.');
    return;
  }

  const client = getLanguageClient();
  if (!client) {
    vscode.window.showErrorMessage('ilimap language server is not running.');
    return;
  }

  let summary: IlimapMappingSummary;
  try {
    summary = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Exporting ilimap mapping report'
      },
      () =>
        client.sendRequest<IlimapMappingSummary>(
          mappingSummaryRequest,
          { uri: editor.document.uri.toString() } satisfies IlimapMappingSummaryParams
        )
    );
  } catch (error) {
    outputChannel.appendLine(`Failed to export ilimap mapping report: ${errorMessage(error)}`);
    vscode.window.showErrorMessage('Failed to export ilimap mapping report.');
    return;
  }

  await writeMappingReport(summary, outputChannel);
}

export async function writeMappingReport(
  summary: IlimapMappingSummary,
  outputChannel: vscode.OutputChannel
): Promise<void> {
  if (!summary.available) {
    const message = summary.message || 'No ilimap mapping summary is available.';
    vscode.window.showErrorMessage(message);
    return;
  }

  const mappingName = summary.mappingName || 'mapping';
  const safeName = mappingName.replace(/[\\/:\0]/g, '_');
  const defaultUri = vscode.Uri.file(`${safeName}.mapping-report.md`);

  const uri = await vscode.window.showSaveDialog({
    defaultUri,
    filters: { 'Markdown': ['md'] }
  });

  if (!uri) {
    return;
  }

  const markdown = renderMappingReportMarkdown(summary);
  try {
    await vscode.workspace.fs.writeFile(uri, Buffer.from(markdown, 'utf8'));
  } catch (error) {
    outputChannel.appendLine(`Failed to write ilimap mapping report: ${errorMessage(error)}`);
    vscode.window.showErrorMessage('Failed to write ilimap mapping report.');
    return;
  }

  const openAction = 'Open';
  const choice = await vscode.window.showInformationMessage(
    `Mapping report saved to ${uri.fsPath}`,
    openAction
  );
  if (choice === openAction) {
    try {
      const document = await vscode.workspace.openTextDocument(uri);
      await vscode.window.showTextDocument(document);
    } catch (error) {
      outputChannel.appendLine(`Failed to open ilimap mapping report: ${errorMessage(error)}`);
    }
  }
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
    const result = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Validating ilimap mapping'
      },
      () =>
        client.sendRequest<IlimapValidateMappingResult>(validateMappingRequest, {
          uri: editor.document.uri.toString(),
          text: editor.document.getText(),
          version: editor.document.version
        } satisfies IlimapValidateMappingParams)
    );
    if (!result.available) {
      const message = result.message || 'No ilimap validation result is available.';
      outputChannel.appendLine(`ilimap validation unavailable: ${message}`);
      vscode.window.showErrorMessage(message);
      return false;
    }
    outputChannel.appendLine(`ilimap validation completed with ${result.diagnosticCount} diagnostics.`);
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
