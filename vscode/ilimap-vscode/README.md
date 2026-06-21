# ILIMAP VS Code Extension

This extension provides VS Code language registration and basic editor support for `.ilimap` mapping profiles.

## Language Server

The extension starts the ILIMAP Java language server with:

```text
java -jar <server-jar>
```

Set `ilimap.server.jar` to the language-server JAR path. If the setting is empty, the extension tries to use `server/ilimap-language-server.jar` inside the extension directory.

The VS Code client is intentionally thin. ILIMAP parsing, diagnostics, validation, and semantic behavior are implemented by the Java language server.

## Mapping Overview

Use `ILIMAP: Open Mapping Overview` while an `.ilimap` document is active to open a read-only Webview with Java-provided counts for inputs, outputs, rules, enum maps, bags, refs, and diagnostics. The Webview does not edit mappings and does not calculate coverage values.
