# JDBC Spatial → XTF

Transforms a JDBC query result with **WKT point geometry** into a valid INTERLIS 2.4 XTF transfer.

## What it demonstrates

- JDBC input with a spatial column encoded as WKT text
- `JdbcGeometrySpec` mapping: the `geom_wkt` column is declared as `encoding: wkt` with `type: coord`
- Point geometry (WKT `POINT (x y)`) → INTERLIS `COORD` → XTF `<coord><C1>...</C1><C2>...</C2></coord>`
- Output is validated with `--validate`

## Run

```bash
./bin/ilitransformer transform -m examples/jdbc-spatial-to-xtf/mapping.yaml --validate --report build/reports/jdbc-spatial-demo
```

The integration test creates the SQLite database programmatically. To create it manually:

```sql
CREATE TABLE stations (
  id TEXT PRIMARY KEY,
  identifier TEXT,
  name TEXT,
  geom_wkt TEXT
);

INSERT INTO stations VALUES ('s1', 'SOLOTHURN', 'Solothurn', 'POINT (2607600 1228500)');
INSERT INTO stations VALUES ('s2', 'OLTEN', 'Olten', 'POINT (2635000 1242000)');
```

## Recommended SQL for spatial databases

### SQLite / Default

No spatial extension needed. Store geometry as WKT text:

```sql
SELECT id, identifier, name, geom_wkt FROM stations
```

### PostGIS

```sql
SELECT id, identifier, name, ST_AsText(geom) AS geom_wkt FROM stations
```

Or with WKB:

```sql
SELECT id, identifier, name, ST_AsBinary(geom) AS geom_wkb FROM stations
```

### DuckDB with Spatial

```sql
SELECT id, identifier, name, ST_AsText(geom) AS geom_wkt FROM stations
```
