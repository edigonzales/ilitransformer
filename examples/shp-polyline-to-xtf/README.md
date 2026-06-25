# Shapefile PolyLine to XTF

This example transforms a **PolyLine Shapefile** into a valid **INTERLIS 2.4 XTF** transfer file.

PolyLine shapefiles contain linear geometries (roads, rivers, etc.). Each SHP record is read
as a PolyLine shape type (3) and converted to an INTERLIS POLYLINE attribute.

## Files

| File | Purpose |
| --- | --- |
| `models/DemoShpPolylineSource.ili` | Source model with POLYLINE geometry |
| `models/DemoPolylineTarget.ili` | Target INTERLIS model |
| `input/roads.shp` | Input data (generated in the integration test) |
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
`ShpPolylineToXtfIntegrationTest` creates a minimal PolyLine Shapefile programmatically.
