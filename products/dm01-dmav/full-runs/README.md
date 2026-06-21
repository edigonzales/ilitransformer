# DM01 → DMAV Full-Run Bundles

Diese Bundles versionieren reproduzierbare Voll-Läufe als Produktartefakte unter `products/dm01-dmav/full-runs/`.

Versioniert werden nur:

- das Bundle-Rezept (`manifest.yaml`),
- die Datensatz-Dokumentation,
- der normalisierte Soll-Report (`expected-summary.yaml`).

Nicht versioniert werden:

- Original-Datensätze,
- erzeugte Voll-Output-Transfers,
- rohe Logs,
- handgepflegte kombinierte `full.generated.yaml`- oder `full.ilimap`-Dateien.

## Struktur

Jeder Datensatz lebt unter `products/dm01-dmav/full-runs/<dataset-slug>/` und enthält mindestens:

- `manifest.yaml`: manifestgesteuertes Rezept für Source, Modeldirs, Topic-Mappings und erwartete Summary
- `expected-summary.yaml`: normalisierte Counts-, Targets- und Warning-Signatur
- `README.md`: Datensatz-spezifische Herkunft und Laufhinweise

Topic-Mappings sind `.ilimap`-first. Jeder Topic referenziert seine Mapping-Datei über das einzelne Feld `mapping:`; das Format (`.ilimap` oder `.yaml`) wird an der Dateiendung erkannt. Bereits migrierte Topics zeigen auf `.ilimap`, noch nicht migrierte auf die `.yaml`-Variante.

## Ausführung

```bash
./products/dm01-dmav/full-runs/run-full-run.sh so-2549 /abs/pfad/zur/2549.ch.so.agi.av.dm01_ch.itf
```

Alternativ direkt über Gradle:

```bash
./gradlew runDm01DmavFullRun \
  -Pdm01DmavFullRunManifest=products/dm01-dmav/full-runs/so-2549/manifest.yaml \
  -Pdm01DmavFullRunSource=/abs/pfad/zur/2549.ch.so.agi.av.dm01_ch.itf
```

Der Lauf erzeugt Reports unter `build/reports/dm01-dmav/full-runs/<dataset-slug>/` und bricht ab, wenn:

- der Source-Fingerprint nicht zum Manifest passt,
- die Transformation Fehler produziert,
- `ilivalidator` fehlschlägt,
- die normalisierte Summary von `expected-summary.yaml` abweicht.
