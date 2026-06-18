# Minimales ilimap-Beispiel

Dieses Beispiel demonstriert eine einfache 1:1-Transformation mit skalaren Attributen
im `.ilimap`-Format.

## Dateien

| Datei | Zweck |
|---|---|
| `profile.ilimap` | Mapping-Konfiguration im `.ilimap`-Format |
| `models/example.ili` | INTERLIS-Modell (Source- und Target-Klasse) |
| `data/input.xtf` | Transferdaten (Source) |

## Ausfuehren

```bash
cd examples/ilimap/minimal/

# Installation (einmalig aus Repo-Root):
../../../gradlew installDist

# Transformation ausfuehren:
../../../build/install/ilitransformer/bin/ilitransformer transform -m profile.ilimap

# Oder mit vorgebautem Release:
../../../bin/ilitransformer transform -m profile.ilimap
```

Die Ausgabe `output.xtf` enthaelt die transformierten Target-Klassen.

## Was passiert?

Das Mapping kopiert jedes `SourceClass`-Objekt aus `input.xtf` nach `TargetClass` in `output.xtf`:

- `Name` -> `Label`
- `Count` -> `Size`
- `Active` -> `Enabled`

## Vergleich mit YAML

Das aequivalente YAML-Mapping liegt unter [`examples/minimal/mapping.yaml`](../../minimal/mapping.yaml).

## Weiterführend

- ilimap DSL v2 Referenz: [`docs/ilimap-v2.md`](../../../docs/ilimap-v2.md)
- YAML Mapping-DSL-Referenz: [`docs/mapping-dsl.md`](../../../docs/mapping-dsl.md)
- CLI-Referenz: [`docs/cli.md`](../../../docs/cli.md)
