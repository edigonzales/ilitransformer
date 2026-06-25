# Shapefile Polygon to XTF

This example transforms a **Polygon Shapefile** into a valid **INTERLIS 2.4 XTF** transfer file.

Polygon shapefiles contain area geometries (parcels, land use, etc.). Each SHP record is read
as a Polygon shape type (5) and converted to an INTERLIS SURFACE attribute. The decoder groups
rings into shells (counter-clockwise) and holes (clockwise), assigning each hole to the smallest
containing shell.

## Files

| File | Purpose |
| --- | --- |
| `models/DemoShpPolygonSource.ili` | Source model with SURFACE geometry |
| `models/DemoPolygonTarget.ili` | Target INTERLIS model |
| `input/parcels.shp` | Input data (generated in the integration test) |
| `mapping.yaml` | Mapping in YAML form |

## Run

From this directory:

```bash
ilitransformer transform \
  --mapping mapping.yaml \
  --modeldir models \
  --validate \
  --report build/report
```

The input Shapefile is not committed to the repository. The integration test
`ShpPolygonToXtfIntegrationTest` creates a minimal Polygon Shapefile programmatically.
