# GeoPackage to XTF

This example transforms a flat **tabular GeoPackage** table into a valid **INTERLIS 2.4 XTF**
transfer file.

GeoPackage is a read-only source format in this phase. It reads a single table from a GeoPackage
file and maps its columns to the attributes of one class of the source model. No geometry is
required; a purely tabular GeoPackage with `data_type=attributes` in `gpkg_contents` works.
Structures, references and geometry are not expressed in this phase.

## Files

| File | Purpose |
| --- | --- |
| `models/DemoGpkgSource.ili` | Source model describing the flat GeoPackage table |
| `models/DemoTarget.ili` | Target INTERLIS model |
| `input/municipalities.gpkg` | Input data (generated in the integration test) |
| `mapping.yaml` | Mapping in YAML form |
| `mapping.ilimap` | Equivalent mapping in `.ilimap` form |

## GeoPackage options

The GeoPackage input declares its options in the `input` block of the mapping:

| Option | Default | Meaning |
| --- | --- | --- |
| `table` | *(required)* | Database table name to read |
| `fetchSize` | `10000` | Rows fetched per database round-trip |

The input `.gpkg` file must contain at minimum a `gpkg_contents` metadata table and a data table
matching the source model. If `data_type` is `attributes` no geometry columns are needed.

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

The input `.gpkg` file is not committed to the repository. The integration test
`GeoPackageToXtfIntegrationTest` creates a minimal tabular GeoPackage programmatically before
running the transformation.
