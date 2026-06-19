# SO-2549 Full-Run Bundle

Dieses Bundle beschreibt den reproduzierbaren DM01 → DMAV-Voll-Lauf für den externen Originaldatensatz `2549.ch.so.agi.av.dm01_ch.itf`.

## Source

- Default-Pfad-Hinweis im Manifest: `./source/2549.ch.so.agi.av.dm01_ch.itf`
- Erwarteter SHA-256-Fingerprint:
  `a24986c97092b1fbd1bbba1aa76c82c2e1976a2e719bd045eb27087f582128fd`

Der Datensatz selbst wird nicht eingecheckt. Entweder:

- Datei lokal unter `products/dm01-dmav/full-runs/so-2549/source/` ablegen, oder
- beim Lauf explizit mit `-Pdm01DmavFullRunSource=...` bzw. als zweites Argument an `run-full-run.sh` übergeben.

## Inhalt

- `.ilimap`-first über das Manifest: `lfp3` läuft über `profiles/dm01-to-dmav/1.1/lfp3.ilimap`
- übrige Topics bleiben bis zur Migration explizit als YAML-Fallback referenziert
- ausgeschlossene Topics und Gründe sind im Manifest dokumentiert

## Lauf

```bash
./products/dm01-dmav/full-runs/run-full-run.sh so-2549 /abs/pfad/zur/2549.ch.so.agi.av.dm01_ch.itf
```

Der Lauf erzeugt:

- `combined.generated.yaml`
- `transformation-report.json`
- `normalized-summary.yaml`
- `*.log` aus `ilivalidator`

unter `build/reports/dm01-dmav/full-runs/so-2549/`. Nur `expected-summary.yaml` ist Teil des Repo-Artefakts.
