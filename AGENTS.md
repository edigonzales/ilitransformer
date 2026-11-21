# AGENTS.md

## Zweck

Dieses Repository verwendet lokale Agent-Instruktionen für Java-, Gradle- und INTERLIS-Arbeiten.

Ein Coding Agent muss diese Datei lesen, bevor er Code, Tests, Build-Dateien, Mapping-Profile, INTERLIS-Modelle oder Transferdaten ändert.

## Nicht verhandelbare Regeln

- Arbeite eng am Auftrag. Keine opportunistischen Refactorings.
- Während der Vorproduktion sind API-Breaks zulässig. Dokumentiere betroffene Verträge und aktualisiere ihre Verbraucher.
- Entferne ersetzte Runtime-Pfade und APIs vollständig.
- Referenzen löst ausschliesslich `ReferenceResolutionService` aus `TransformationEngine.runTyped()` auf.
- Die Suche bleibt im durch Quelle und Transferformat bestimmten Kontext. Ein ausgebliebener Treffer rechtfertigt keinen Wechsel von Basket oder Input.
- Behaupte nie, Tests seien erfolgreich, wenn du sie nicht wirklich ausgeführt hast.
- Committe nie, bevor Definition of Done und Commit Policy erfüllt sind.
- Erfinde keine INTERLIS-Modellsyntax und keine INTERLIS-Transferdaten.
- Prüfe `.ili` mit `ili2c`.
- Prüfe `.itf`, `.xtf` und `.xml` mit `ilivalidator`.
- Nicht implementierte DSL-Felder dürfen nicht still ignoriert werden.
- DM01/DMAV-spezifische Logik darf nicht in die generische Engine wandern.

## Vor jeder Änderung lesen

Immer:

- `docs/agent/DEFINITION_OF_DONE.md`
- `docs/agent/COMMIT_POLICY.md`
- `docs/agent/DECISIONS.md`

Bei Java-Codeänderungen:

- `.skills/java-test-gap/SKILL.md`
- `.skills/gradle-verification/SKILL.md`

Vor einem Commit:

- `.skills/done-and-commit/SKILL.md`

Bei INTERLIS-Arbeiten:

- `.skills/interlis-validation/SKILL.md`

Bei INTERLIS-1-Testdaten:

- `.skills/interlis1-testdata/SKILL.md`

Bei DM01/DMAV:

- `.skills/dm01-dmav-real-data-gate/SKILL.md`

Bei Mapping-DSL/YAML/.ilimap:

- `.skills/mapping-dsl-change/SKILL.md`

## Arbeitsstil

1. Verstehe zuerst die kleinste betroffene Codefläche.
2. Suche vorhandene Tests.
3. Ergänze möglichst zuerst einen Regressionstest.
4. Implementiere die kleinste korrekte Änderung.
5. Führe den kleinsten relevanten Test aus.
6. Führe breitere Verifikation aus.
7. Aktualisiere Dokumentation nur, wenn Verhalten, CLI, DSL oder Testdatenvertrag betroffen sind.
8. Committe nur nach erfüllter Commit Policy.

## Abschlussbericht

Am Ende jeder Aufgabe berichte:

- geänderte Dateien,
- Tests/Kommandos, die tatsächlich ausgeführt wurden,
- Ergebnis,
- nicht geprüfte Risiken,
- ob committet wurde,
- Commit-Message, falls committet.
