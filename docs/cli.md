# CLI Reference

## Installation

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

### import-correlation

Import DM01/DMAV correlation hints from XLSX.

```bash
ilitransformer import-correlation --xlsx <path> [--out <path>] [--report <path>]
```

| Option | Required | Description |
|---|---|---|
| `--xlsx` | Yes | Path to the correlation XLSX file |
| `--out` | No | Output path for `correlation-hints.json` (default: `build/generated/dm01-dmav/correlation-hints.json`) |
| `--report` | No | Output path for import report (default: `build/reports/dm01-dmav/correlation-import-report.md`) |

Example:

```bash
ilitransformer import-correlation --xlsx docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx
```

### generate-mapping

Generate DM01/DMAV mapping candidates from correlation hints and model inventory.

```bash
ilitransformer generate-mapping --hints <path> [options...]
```

| Option | Required | Description |
|---|---|---|
| `--hints` | Yes | Path to `correlation-hints.json` |
| `--synonyms` | No | Path to `synonyms.json` |
| `--out` | No | Output path for `mapping-candidates.json` (default: `build/generated/dm01-dmav/mapping-candidates.json`) |
| `--report` | No | Output path for candidate report (default: `build/reports/dm01-dmav/candidate-report.md`) |
| `--dm01-model` | No | DM01 model name (default: `DM01AVCH24LV95D`) |
| `--dm01-dir` | No | Model directory for DM01 resolution |
| `--dmav-model` | No | DMAV model name (default: `DMAV_FixpunkteAVKategorie3_V1_1`) |
| `--dmav-dir` | No | Model directory for DMAV resolution |
| `--yaml-dm01-dmav` | No | Output path for generated DM01→DMAV YAML |
| `--yaml-dmav-dm01` | No | Output path for generated DMAV→DM01 YAML |

Example:

```bash
ilitransformer generate-mapping \
  --hints build/generated/dm01-dmav/correlation-hints.json \
  --dm01-dir "src/test/data/av/models/;https://models.interlis.ch" \
  --dmav-dir "src/test/data/av/models/;https://models.interlis.ch"
```

## Gradle tasks

Equivalent Gradle tasks:

| CLI Command | Gradle Task |
|---|---|
| `transform` | `./gradlew run --args="transform ..."` |
| `validate-mapping` | `./gradlew run --args="validate-mapping ..."` |
| `inspect-model` | `./gradlew generateModelInventory` (pre-configured) |
| `import-correlation` | `./gradlew importDmavCorrelation` |
| `generate-mapping` | `./gradlew generateDm01DmavMappings` |

Additional Gradle-only tasks:

```bash
./gradlew topicGapReport
./gradlew validateGoldenTransfers
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
