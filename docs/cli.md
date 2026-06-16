# CLI Reference

## Installation

### Per Release-Download

```bash
unzip ilitransformer-0.1.0.zip
cd ilitransformer-0.1.0/
./bin/ilitransformer --help
```

### Per Build aus Source

```bash
./gradlew installDist
```

The CLI entry point is `ilitransformer` (via `guru.interlis.transformer.app.CliMain`).

## Global options

All commands support:

```
-h, --help      Show help message
-V, --version   Print version information
```

## Commands

### transform

Run an INTERLIS transformation.

```bash
ilitransformer transform --mapping mapping.yaml [--modeldir <dir>] [--validate] [--report <dir>]
```

| Option | Required | Description |
|---|---|---|
| `-m`, `--mapping` | Yes | Mapping YAML configuration file |
| `--modeldir` | No | Model directory path for INTERLIS model resolution |
| `--validate` | No | Run ilivalidator on the output after transformation |
| `--report` | No | Output directory for transformation reports |

Examples:

```bash
# Basic transformation
ilitransformer transform -m profiles/dm01-to-dmav/1.1/lfp3.yaml

# With model directory
ilitransformer transform -m profiles/dm01-to-dmav/1.1/lfp3.yaml --modeldir "src/test/data/av/models/;https://models.interlis.ch"

# With validation
ilitransformer transform -m profiles/dm01-to-dmav/1.1/lfp3.yaml --modeldir "src/test/data/av/models/" --validate --report build/reports/lfp3
```

Exit codes:
- `0` — Transformation successful, no errors
- `1` — Compilation errors or runtime errors

### validate-mapping

Validate a mapping configuration without executing a transformation.

```bash
ilitransformer validate-mapping --mapping mapping.yaml
```

| Option | Required | Description |
|---|---|---|
| `-m`, `--mapping` | Yes | Mapping YAML configuration file |

### inspect-model

Compile an INTERLIS model and output its inventory as JSON and/or Markdown.

```bash
ilitransformer inspect-model --model <model> [--modeldir <dir>] [--output <path>] [--format <fmt>]
```

| Option | Required | Description |
|---|---|---|
| `--model` | Yes | INTERLIS model name (file path or qualified model name) |
| `--modeldir` | No | Model directory path(s) (semicolon-separated) |
| `--output` | No | Output path base (`.json` and `.md` appended) |
| `--format` | No | Output format: `json`, `markdown`, or `both` (default: `both`) |

Examples:

```bash
# Print inventory to stdout
ilitransformer inspect-model --model DMAV_FixpunkteAVKategorie3_V1_1 --modeldir "src/test/data/av/models/"

# Write to files
ilitransformer inspect-model --model DMAV_FixpunkteAVKategorie3_V1_1 --modeldir "src/test/data/av/models/" --output build/reports/dmav-fp3 --format both
```

### dm01-dmav

DM01 ↔ DMAV product profile utilities.

```bash
ilitransformer dm01-dmav [command]
```

#### import-correlation

Import DM01/DMAV correlation hints from XLSX.

```bash
ilitransformer dm01-dmav import-correlation --xlsx <path> [--out <path>] [--report <path>]
```

| Option | Required | Description |
|---|---|---|
| `--xlsx` | Yes | Path to the correlation XLSX file |
| `--out` | No | Output path for `correlation-hints.json` (default: `build/generated/dm01-dmav/correlation-hints.json`) |
| `--report` | No | Output path for import report (default: `build/reports/dm01-dmav/correlation-import-report.md`) |

Example:

```bash
ilitransformer dm01-dmav import-correlation --xlsx docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx
```

## Gradle tasks

Equivalent Gradle tasks:

| CLI Command | Gradle Task |
|---|---|
| `transform` | `./gradlew run --args="transform ..."` |
| `validate-mapping` | `./gradlew run --args="validate-mapping ..."` |
| `inspect-model` | `./gradlew generateModelInventory` (pre-configured) |
| `dm01-dmav import-correlation` | `./gradlew importDmavCorrelation` |

Additional Gradle-only tasks:

```bash
./gradlew validateTransfer -Ptransfer=build/out/dmav-lfp3.xtf -Pmodel=DMAV_FixpunkteAVKategorie3_V1_1
```

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success (no errors) |
| 1 | Error (compilation, runtime, or model error) |

## Path conventions

- Multiple model directories: semicolon-separated (`dir1;dir2;https://...`)
- Output directories are created automatically
- Generated files go to `build/generated/`
- Reports go to `build/reports/`
- Produktive DM01/DMAV-Profile liegen unter `profiles/`
