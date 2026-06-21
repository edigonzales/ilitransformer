# ilimap for VS Code

ilimap adds editor support for `.ilimap` mapping profiles in Visual Studio Code. The extension keeps the client intentionally thin and delegates parsing, validation, formatting, and semantic analysis to the Java ilimap language server that ships with this repository.

## Features

- Language registration for `.ilimap`
- Syntax highlighting through a TextMate grammar
- Diagnostics on open and on every document change
- Document formatting
- Document symbols for the Outline view
- Folding ranges for mappings, enum blocks, rules, bags, refs, and assignment blocks
- Context-aware completions for common ilimap keywords and model-backed symbols
- Hover information for inputs, outputs, rules, and enum maps
- Go to Definition for ilimap symbol references
- Quick fixes for selected enum map diagnostics
- Read-only mapping overview webview
- Language server log output and restart command

## Getting Started

1. Install the extension.
2. Open a folder that contains `.ilimap` files.
3. Open an `.ilimap` file.
4. If the published extension contains a bundled runtime for your platform, the language server starts immediately.
5. If no bundled runtime is available, configure `ilimap.java.path` or make sure `java` is available on your `PATH`.

The extension starts the server with:

```text
<java> -jar <server-jar>
```

By default the extension uses the bundled ilimap language-server JAR at `server/ilimap-lsp-all.jar`. You can override it with `ilimap.server.jarPath`.

## Commands

- `ilimap: Restart Language Server`
- `ilimap: Show Language Server Logs`
- `ilimap: Format Mapping`
- `ilimap: Validate Mapping`
- `ilimap: Open Mapping Overview`

`ilimap: Validate Mapping` currently opens the Problems view. It does not trigger a separate server-side validation pass beyond the diagnostics that are already kept up to date while you edit.

## Settings

- `ilimap.java.path`
  Overrides the Java executable path. Leave it empty to prefer a bundled runtime when present and otherwise fall back to `java` from `PATH`.
- `ilimap.server.jarPath`
  Overrides the language-server fat JAR. Leave it empty to use the bundled `server/ilimap-lsp-all.jar`.
- `ilimap.server.jvmArgs`
  Adds extra JVM arguments when starting the language server.

## Runtime Behavior

Published builds are expected to bundle runtimes for:

- `darwin-arm64`
- `linux-arm64`
- `linux-x64`
- `win32-x64`

Runtime resolution works like this:

1. Use `ilimap.java.path` when it is configured.
2. Otherwise use the bundled runtime for the current platform when available.
3. Otherwise fall back to `java` from `PATH`.

This keeps local development simple while allowing published builds to work without a separate Java installation on supported platforms.

## Mapping Overview

Run `ilimap: Open Mapping Overview` while an `.ilimap` editor is active to open a read-only summary beside the editor. The webview shows counts and lists for:

- inputs
- outputs
- enum maps
- rules
- bags and refs
- diagnostics

The overview does not edit mappings and does not calculate coverage values.

## Local Development

Use this workflow when working on the extension from this repository:

1. Build and stage the current server JAR:

   ```bash
   ./gradlew copyDevIlimapServerJar
   ```

2. Open `vscode/ilimap-vscode` as the VS Code workspace.
3. Install dependencies:

   ```bash
   npm install
   ```

4. Optionally keep the client rebuilt while editing:

   ```bash
   npm run watch
   ```

5. Run the VS Code task `Prepare ilimap Dev Host`.
6. Start the launch configuration `Run ilimap Client`.
7. After Java server changes, run `./gradlew copyDevIlimapServerJar` again and use `ilimap: Restart Language Server`.

## Current Scope

The current extension focuses on editing and understanding ilimap mappings. It does not currently provide:

- rename refactorings
- semantic tokens
- workspace-wide symbol search
- code actions beyond the currently implemented quick fixes and formatting source action
- editing inside the mapping overview webview

## Packaging and Publishing

The repository contains scripts and CI automation for packaging the extension as a `.vsix` and publishing the same artifact to Visual Studio Marketplace and Open VSX. Maintainer details live in [the repository publishing note](https://github.com/edigonzales/ilitransformer/blob/main/docs/ilimap-vscode-publishing.md).
