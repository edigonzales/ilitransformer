# Agent/Skills Template für Java-, Gradle- und INTERLIS-Projekte

Diese Vorlage ist für Repositories gedacht, die mit Coding Agents wie **OpenCode** und **Codex** bearbeitet werden.

Die Struktur ist bewusst portabel:

- `AGENTS.md` ist der zentrale Projektvertrag.
- `docs/agent/*` enthält Definition of Done, Commit-Regeln, Entscheidungen und bekannte Risiken.
- `.skills/*/SKILL.md` enthält wiederverwendbare Arbeitsabläufe.
- `opencode/README.md` enthält Prompt-Vorlagen für OpenCode.
- `codex/README.md` enthält Prompt-Vorlagen für Codex.
- `.skills/interlis1-testdata/snippets/*` enthält INTERLIS-1-Snippets, inklusive AREA-Hinweisen.

## Installation

Kopiere den Inhalt dieses Ordners in die Wurzel deines Git-Repositories:

```bash
cp -R agent-skill-template-interlis-java-codex-opencode/* /path/to/repo/
cp -R agent-skill-template-interlis-java-codex-opencode/.skills /path/to/repo/
```

Danach anpassen:

- Projektname in `AGENTS.md`
- Gradle-Kommandos in `.skills/gradle-verification/SKILL.md`
- lokale INTERLIS-Toolpfade in `.skills/interlis-validation/SKILL.md`
- bekannte Issues in `docs/agent/KNOWN_ISSUES.md`
- Entscheidungen in `docs/agent/DECISIONS.md`

## OpenCode / Codex

Bei beiden Clients explizit im Prompt referenzieren:

```text
Lies zuerst AGENTS.md.

Verwende diese Skills:
- .skills/java-test-gap/SKILL.md
- .skills/gradle-verification/SKILL.md
- .skills/done-and-commit/SKILL.md

Aufgabe:
...

Committe nur, wenn die Commit Policy erfüllt ist.
```

Für INTERLIS-1-AREA-Testdaten zusätzlich:

```text
Verwende zusätzlich:
- .skills/interlis-validation/SKILL.md
- .skills/interlis1-testdata/SKILL.md

Wichtig:
AREA-Roh-ITF nicht frei erfinden.
Geometrie-Hilfstabelle vor Haupttabelle.
Gemeinsame AREA-Kanten nur einmal.
Alles mit ili2c/ilivalidator prüfen.
```
