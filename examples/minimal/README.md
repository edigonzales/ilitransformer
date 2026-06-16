# Minimales Beispiel

Dieses Beispiel demonstriert eine einfache 1:1-Transformation mit skalaren Attributen.

## Dateien

| Datei | Zweck |
|---|---|
| `mapping.yaml` | Mapping-DSL-Konfiguration |
| `models/example.ili` | INTERLIS-Modell (Source- und Target-Klasse) |
| `data/input.xtf` | Transferdaten (Source) |

## Ausfuhren

```bash
cd examples/minimal/

# Installation (einmalig aus Repo-Root):
../../gradlew installDist

# Transformation ausfuhren:
../../build/install/ilitransformer/bin/ilitransformer transform -m mapping.yaml

# Oder mit vorgebautem Release:
../../bin/ilitransformer transform -m mapping.yaml
```

Die Ausgabe `output.xtf` enthalt die transformierten Target-Klassen.

## Was passiert?

Das Mapping kopiert jedes `SourceClass`-Objekt aus `input.xtf` nach `TargetClass` in `output.xtf`:

- `Name` → `Label`
- `Count` → `Size`
- `Active` → `Enabled`

## Nachster Schritt

- Mapping-DSL-Referenz: [`docs/mapping-dsl.md`](../../docs/mapping-dsl.md)
- CLI-Referenz: [`docs/cli.md`](../../docs/cli.md)
