# GeoPackage Spatial to XTF Demo

Transform a spatial GeoPackage table with point geometry into a valid INTERLIS 2.4 XTF transfer.

## Prerequisites

- Java 21+
- The GeoPackage must contain a `gpkg_contents`, `gpkg_geometry_columns` and
  `gpkg_spatial_ref_sys` metadata table alongside the user table.

## Run with YAML

```bash
./bin/ilitransformer transform -m examples/gpkg-spatial-to-xtf/mapping.yaml --validate --report build/reports/gpkg-spatial-demo
```

## Run with .ilimap

```bash
./bin/ilitransformer transform -m examples/gpkg-spatial-to-xtf/mapping.ilimap --validate --report build/reports/gpkg-spatial-demo
```

## Model overview

| File | Role |
|---|---|
| `models/DemoSpatialSource.ili` | Flat source model with COORD point geometry |
| `models/DemoSpatialTarget.ili` | Target model with COORD point geometry |

## Limitations

- GeoPackage is a **read-only** input format. Output must be XTF or ITF.
- Only simple point geometry (`COORD`) is demonstrated.
- Structures, references and associations cannot be expressed directly in a GeoPackage;
  they must be constructed by mapping rules.
- The `.gpkg` file is not committed; the integration test creates it programmatically
  with `GeoPackageWriter` from `iox-wkf`.
