# INTERLIS Transformer Dokumentation

Quarto-Projekt für `https://transformer.interlis.guru`.

## Lokal rendern

```bash
quarto render
```

Für die lokale Vorschau:

```bash
quarto preview
```

## Bilder ersetzen

Die Titelseite verwendet `assets/img/ilimap-placeholder.svg` als Platzhalter. Das Editor-Kapitel (`editor.qmd`) verwendet mehrere Platzhalter-SVGs und enthält im Text konkrete Hinweise (`:::{.note}`-Blöcke), welche Screenshots ergänzt werden sollten.

Benötigte SVG-Platzhalter unter `assets/img/`:

| Datei | Verwendung |
|---|---|
| `vscode-install-placeholder.svg` | Erweiterungssuche / Installation |
| `vscode-editor-placeholder.svg` | Editor mit `.ilimap`, Autocomplete |
| `vscode-codelens-placeholder.svg` | CodeLens über Regel |
| `vscode-diagnostics-placeholder.svg` | Diagnostics + Problems-Ansicht |
| `vscode-overview-placeholder.svg` | Mapping Overview Gesamtansicht |
| `vscode-trace-placeholder.svg` | Trace Inspector Detail |
| `ilimap-placeholder.svg` | Startseite (Code-Ausschnitt) |

Empfohlene finale Bilder:

- Startseite: ruhiger Ausschnitt einer realen `.ilimap`-Datei, idealerweise mit sichtbarer Mapping-Struktur.
- Editor: Screenshots gemäss den `:::{.note}`-Hinweisen im Editor-Kapitel ersetzen.

## Gestaltung

Die Gestaltung ist bewusst weiss, reduziert und dokumentationsnah gehalten. Die Einstiegsnavigation verwendet nur Blau. Rot ist nur sparsam in Code-Syntax und technischen Akzenten vorgesehen.
