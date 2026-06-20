import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';

export function resolveServerJarPath(context: vscode.ExtensionContext, configured: string | undefined): string {
  const override = configured?.trim();
  if (override) {
    return override;
  }

  const bundled = context.asAbsolutePath(path.join('server', 'ilimap-lsp-all.jar'));
  if (fs.existsSync(bundled)) {
    return bundled;
  }

  const message =
    'ILIMAP language server JAR not found. Configure ilimap.server.jarPath or run ./gradlew copyDevIlimapServerJar.';
  vscode.window.showErrorMessage(message);
  throw new Error(message);
}

export function resolveJavaPath(configured: string | undefined): string {
  return configured?.trim() || 'java';
}

export function resolveJvmArgs(configured: readonly string[] | undefined): string[] {
  return Array.isArray(configured) ? [...configured] : [];
}
