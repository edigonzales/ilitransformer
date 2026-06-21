import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import * as vscode from 'vscode';

export type JavaRuntimeSource = 'configured' | 'bundled' | 'system';

export interface JavaRuntime {
  command: string;
  source: JavaRuntimeSource;
  platformId?: string;
}

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
    'ilimap language server JAR not found. Configure ilimap.server.jarPath or run ./gradlew copyDevIlimapServerJar.';
  vscode.window.showErrorMessage(message);
  throw new Error(message);
}

export function resolveJavaRuntime(context: vscode.ExtensionContext, configured: string | undefined): JavaRuntime {
  const override = configured?.trim();
  if (override) {
    return {
      command: override,
      source: 'configured'
    };
  }

  const bundled = resolveBundledJavaPath(context);
  if (bundled) {
    return {
      command: bundled.command,
      source: 'bundled',
      platformId: bundled.platformId
    };
  }

  return {
    command: 'java',
    source: 'system'
  };
}

export function resolveJavaPath(context: vscode.ExtensionContext, configured: string | undefined): string {
  return resolveJavaRuntime(context, configured).command;
}

export function resolveJvmArgs(configured: readonly string[] | undefined): string[] {
  return Array.isArray(configured) ? [...configured] : [];
}

export function resolveBundledJavaPath(
  context: vscode.ExtensionContext
): { command: string; platformId: string } | undefined {
  const platformId = runtimePlatformId();
  if (!platformId) {
    return undefined;
  }

  const executable = os.platform() === 'win32' ? 'java.exe' : 'java';
  const command = context.asAbsolutePath(path.join('server', 'jre', platformId, 'bin', executable));
  if (!fs.existsSync(command)) {
    return undefined;
  }

  return {
    command,
    platformId
  };
}

export function runtimePlatformId(
  platform: NodeJS.Platform = os.platform(),
  arch: string = os.arch()
): string | undefined {
  if (platform === 'darwin' && arch === 'arm64') {
    return 'darwin-arm64';
  }
  if (platform === 'darwin' && arch === 'x64') {
    return 'darwin-x64';
  }
  if (platform === 'linux' && arch === 'arm64') {
    return 'linux-arm64';
  }
  if (platform === 'linux' && arch === 'x64') {
    return 'linux-x64';
  }
  if (platform === 'win32' && arch === 'x64') {
    return 'win32-x64';
  }
  return undefined;
}
