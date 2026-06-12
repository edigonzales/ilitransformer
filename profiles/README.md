# Profiles

`profiles/` ist die autoritative Ablage für produktive, versionierte DM01/DMAV-Mapping-Profile.

## Warum Root-Ebene

- Die Profile sind keine Klassenpfad-Ressourcen und keine Test-Fixtures.
- Sie sollen direkt per CLI oder Hilfstasks referenziert werden können.
- Die Versionierung nach Richtung und Modellstand ist auf Root-Ebene sichtbar:

```text
profiles/dm01-to-dmav/1.1/
profiles/dmav-to-dm01/1.1/
```

## Verhältnis zu Tests

- Tests mit synthetischen Modellen verwenden eigene `*-test.yaml` unter `src/test/resources/mappings/`.
- Tests gegen produktive Profile laden `profiles/...` direkt und materialisieren nur die Ein-/Ausgabepfade in temporäre Dateien oder Verzeichnisse.

## Inhalt

- `lfp3.yaml` und `bb.yaml` sind produktive Profile.
- Weitere Topics sollen demselben Schema folgen.
