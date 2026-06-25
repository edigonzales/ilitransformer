# CSV to XTF

This example transforms a flat **CSV** table into a valid **INTERLIS 2.4 XTF** transfer file.

CSV is a deliberately flat, **input-only** format: a single table whose columns map to the
attributes of one class of the source model. CSV cannot express INTERLIS structures, references or
geometry. The transformation engine reads the CSV through an INTERLIS *source* model and writes a
normal INTERLIS *target* model.

## Files

| File | Purpose |
| --- | --- |
| `models/DemoCsvSource.ili` | Source model describing the flat CSV table |
| `models/DemoTarget.ili` | Target INTERLIS model |
| `input/municipalities.csv` | Input data (`;`-separated, with header) |
| `mapping.yaml` | Mapping in YAML form |
| `mapping.ilimap` | Equivalent mapping in `.ilimap` form |

## CSV options

The CSV input declares its parsing options in the `input` block of the mapping:

| Option | Default | Meaning |
| --- | --- | --- |
| `firstLineIsHeader` | `true` | First line holds the column names |
| `separator` | `,` | Value separator character |
| `delimiter` | `"` | Value delimiter (quote) character |
| `encoding` | `UTF-8` | Character set of the file |

With `firstLineIsHeader: true` the column names are matched against the attributes of the source
class. Without a header the columns are matched by position against the attribute count.

## Run

From this directory:

```bash
ilitransformer transform \
  --mapping mapping.yaml \
  --modeldir models \
  --validate \
  --report build/report
```

Use `mapping.ilimap` instead of `mapping.yaml` for the `.ilimap` form. The transformed file is
written to `build/out/municipalities.xtf` and validated with `ilivalidator` because of `--validate`.
