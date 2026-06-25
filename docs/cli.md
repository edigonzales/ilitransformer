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

`--version` prints the build version together with the full git commit hash, e.g.:

```
ilitransformer 0.1.0 (e5c8c58e2537bcd16906f4fb00a870c531fb351f)
```

Version and commit hash are generated at build time into
`build-info.properties` (Gradle task `generateBuildInfo`, parsed from `.git/HEAD`).
The same information is written as the INTERLIS transfer sender of every produced
transfer file, in the form `ilitransformer-<version>-<full-git-hash>`
(for example `<ili:sender>ilitransformer-0.1.0-e5c8c58e...</ili:sender>`). When the
generated resource is absent (e.g. an IDE run without the task), the commit hash
falls back to `unknown`.


## Commands

### transform

Run an INTERLIS transformation.

```bash
ilitransformer transform --mapping mapping.yaml [--modeldir <dir>] [--validate] [--report <dir>]
ilitransformer transform --mapping mapping.ilimap [--modeldir <dir>] [--validate] [--report <dir>]
```

| Option | Required | Description |
|---|---|---|
| `-m`, `--mapping` | Yes | Mapping configuration file (YAML or .ilimap) |
| `--modeldir` | No | Model directory path for INTERLIS model resolution |
| `--validate` | No | Run ilivalidator on the output after transformation |
| `--report` | No | Output directory for transformation reports |

Examples:

```bash
# Basic transformation with YAML
ilitransformer transform -m profiles/dm01-to-dmav/1.1/lfp3.yaml

# Basic transformation with .ilimap
ilitransformer transform -m profiles/dm01-to-dmav/1.1/lfp3.ilimap

# With model directory
ilitransformer transform -m profiles/dm01-to-dmav/1.1/lfp3.ilimap --modeldir "src/test/data/av/models/;https://models.interlis.ch"

# With validation
ilitransformer transform -m profiles/dm01-to-dmav/1.1/lfp3.ilimap --modeldir "src/test/data/av/models/" --validate --report build/reports/lfp3

# Transform a flat CSV file into a validated XTF (input-only format)
ilitransformer transform -m examples/csv-to-xtf/mapping.yaml --modeldir examples/csv-to-xtf/models --validate --report build/reports/csv

# Transform a tabular GeoPackage into a validated XTF (input-only format)
ilitransformer transform -m examples/gpkg-to-xtf/mapping.yaml --modeldir examples/gpkg-to-xtf/models --validate --report build/reports/gpkg

# Transform a tabular JDBC query result into a validated XTF (input-only format; create the SQLite DB first, see the example README)
ilitransformer transform -m examples/jdbc-to-xtf/mapping.yaml --modeldir examples/jdbc-to-xtf/models --validate --report build/reports/jdbc
```

The native INTERLIS formats (`itf`, `xtf`, `xml`) are selected from the file extension. Additional
input formats such as `csv`, `gpkg` and `jdbc` are declared with `format:` and configured via per-input
`options` (or, for `jdbc`, a `connection` block and `queries`; see [mapping-dsl.md](mapping-dsl.md)).
CSV, GeoPackage and JDBC are deliberately flat, input-only formats. `jdbc` has no path and must be
declared explicitly; passwords are never written to logs, diagnostics or reports.

Exit codes:
- `0` — Transformation successful, no errors
- `1` — Compilation errors or runtime errors

### validate-mapping

Validate a mapping configuration without executing a transformation.

```bash
ilitransformer validate-mapping --mapping mapping.yaml
ilitransformer validate-mapping --mapping mapping.ilimap
```

| Option | Required | Description |
|---|---|---|
| `-m`, `--mapping` | Yes | Mapping configuration file (YAML or .ilimap) |
| `--modeldir` | No | Model directory path for INTERLIS model resolution |

For `.ilimap` files, syntax and semantic errors are reported with file, line, and column information.

### convert-mapping

Convert a YAML mapping configuration to `.ilimap` format.

```bash
ilitransformer convert-mapping --from mapping.yaml --to mapping.ilimap
```

| Option | Required | Description |
|---|---|---|
| `--from`, `--input` | Yes | Source YAML mapping file |
| `--to`, `--output` | Yes | Target .ilimap output file |

Example:

```bash
ilitransformer convert-mapping --from profiles/dm01-to-dmav/1.1/lfp3.yaml --to profiles/dm01-to-dmav/1.1/lfp3.ilimap
```

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
