# Shapefile Point to XTF

This example transforms a **Point Shapefile** into a valid **INTERLIS 2.4 XTF** transfer file.

Shapefile is a read-only source format. It reads a single Shapefile dataset (`*.shp` + `*.dbf`)
and maps DBF columns and SHP geometry to the attributes of one class of the source model. Only
Point shapefiles are supported in this phase; PolyLine and Polygon support will follow.

## Files

| File | Purpose |
| --- | --- |
| `models/DemoShpSource.ili` | Source model describing the flat DBF table and COORD geometry |
| `models/DemoTarget.ili` | Target INTERLIS model |
| `input/stations.shp` | Input data (generated in the integration test) |
| `mapping.yaml` | Mapping in YAML form |
| `mapping.ilimap` | Equivalent mapping in `.ilimap` form |

## Shapefile options

The Shapefile input declares its options in the `input` block of the mapping:

| Option | Default | Meaning |
| --- | --- | --- |
| `class` | *(required)* | Fully qualified source class, e.g. `Model.Topic.Class` |
| `topic` | from `class` | Basket topic |
| `basketId` | `b1` | Basket ID |
| `oidField` | `shp.<recordNumber>` | DBF column used as object OID |
| `geometryAttribute` | inferred | Source attribute for geometry |
| `geometryType` | from shape type | `coord`, `polyline`, `surface` |
| `dbfEncoding` | `ISO-8859-1` | DBF character encoding (use `UTF-8` for `.cpg`) |
| `column.<DBF>` | DBF name used as-is | Map DBF field name to INTERLIS attribute |
| `deletedRecordPolicy` | `error` | `error` or `skip` for DBF deleted records |
| `requireShx` | `false` | Require `.shx` sidecar file |

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
written to `build/out/stations.xtf` and validated with `ilivalidator` because of `--validate`.

The input Shapefile is not committed to the repository. The integration test
`ShpPointToXtfIntegrationTest` creates a minimal Point Shapefile programmatically before
running the transformation.
