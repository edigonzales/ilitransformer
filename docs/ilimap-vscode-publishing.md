# ILIMAP VS Code Extension: Packaging und Publishing

Diese Notiz beschreibt, wie die VS-Code-Extension unter `vscode/ilimap-vscode/` gebaut, verpackt und veröffentlicht wird.

## Zielbild

- dieselbe `.vsix` wird in Visual Studio Marketplace und Open VSX veröffentlicht
- die Veröffentlichung läuft automatisiert nach einem erfolgreichen `main`-CI-Lauf
- `package.json` bleibt auf einer manuell gepflegten Basisversion wie `0.1.0`
- der Publish-Workflow ersetzt nur die Patch-Nummer durch die Quell-Run-Nummer und erzeugt damit `0.1.<run_number>`
- der Workflow schreibt diese Versionsänderung nicht zurück ins Repository

## Einmalige Voraussetzungen

### Visual Studio Marketplace

1. Publisher `ilinexus` anlegen oder übernehmen.
2. Ein Publishing-Token erzeugen und als Repository-Secret `VS_MARKETPLACE_TOKEN` hinterlegen.
3. Im Hinterkopf behalten: Laut offizieller VS-Code-Dokumentation laufen Azure-DevOps-PAT-basierte Veröffentlichungen ab **1. Dezember 2026** aus. Diese Pipeline bleibt kurzfristig PAT-basiert, sollte danach aber auf Microsoft Entra / OIDC umgestellt werden.

### Open VSX

1. Namespace `ilinexus` anlegen und claimen.
2. Publisher Agreement auf [open-vsx.org](https://open-vsx.org/) akzeptieren.
3. Token erzeugen und als Repository-Secret `OPEN_VSIX_MARKETPLACE_TOKEN` hinterlegen.

## Laufzeitmodell

- lokale Entwicklung bleibt Java-freundlich und setzt notfalls auf `ilimap.java.path` oder `java` auf dem `PATH`
- veröffentlichte Artefakte bündeln Laufzeiten für:
  - `darwin-arm64`
  - `linux-arm64`
  - `linux-x64`
  - `win32-x64`
- die Extension bevorzugt eine gebündelte Runtime nur dann, wenn kein expliziter `ilimap.java.path` gesetzt ist

## Relevante Dateien

- `vscode/ilimap-vscode/package.json`
- `vscode/ilimap-vscode/.vscodeignore`
- `vscode/ilimap-vscode/scripts/set-ci-version.js`
- `vscode/ilimap-vscode/scripts/assert-vsix-contents.js`
- `.github/workflows/vscode-extension-publish.yml`

## Workflow-Verhalten

Der Publish-Workflow wird ausgelöst durch:

- `workflow_run` nach erfolgreichem `CI`-Lauf auf `main`
- `workflow_dispatch` als manuellen Recovery-Pfad

Bei `workflow_dispatch` können Quell-Commit und Quell-Run-Nummer explizit angegeben werden. Ohne Angabe baut der Workflow den aktuellen Stand und verwendet seine eigene Run-Nummer.

Der Workflow:

1. baut und testet das Java-Projekt mit `./gradlew test shadowJar`
2. erzeugt den LSP-Fat-JAR
3. baut plattformspezifische JRE-Bundles mit `jlink`
4. staged JAR und Runtimes unter `vscode/ilimap-vscode/server/`
5. setzt lokal im Workflow die Publish-Version auf `major.minor.<run_number>`
6. baut die Extension
7. erzeugt genau eine `.vsix`
8. prüft das Paket auf erwartete und unerwünschte Inhalte
9. prüft pro Registry, ob genau diese Version bereits existiert
10. publiziert nur in die fehlende Registry, oder skippt komplett, wenn beide die Version schon haben

## Lokale Verifikation

Für lokale Änderungen an Doku, Packaging, Runtime-Auswahl oder Publish-Skripten:

```bash
./gradlew test
./gradlew shadowJar
./gradlew copyDevIlimapServerJar
cd vscode/ilimap-vscode
npm test
npm run package:vsix
npm run check:vsix
```

## Hinweise zur Versionierung

- `package.json` enthält die manuell gepflegte Basisversion, zum Beispiel `0.1.0`
- der Publish-Workflow liest nur Major und Minor aus dieser Basisversion
- die Patch-Nummer wird vollständig durch die Run-Nummer ersetzt
- dadurch bleibt die veröffentlichte Versionsfolge monoton steigend, ohne dass für jeden Patch ein Commit nötig ist

## Paketinhalt

Das veröffentlichte Paket soll enthalten:

- `dist/`
- `server/ilimap-lsp-all.jar`
- `server/jre/<plattform>/...`
- `images/icon.png`
- `README.md`
- `CHANGELOG.md`
- `LICENSE.txt`

Das veröffentlichte Paket soll nicht enthalten:

- `src/`
- `test/`
- `.vscode/`
- TypeScript-Sourcemaps
- lokale Dev-Artefakte ausserhalb der Laufzeit- und Server-Bundles
