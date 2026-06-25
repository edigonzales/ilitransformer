# JDBC → XTF demo

Transforms a flat JDBC query result into a valid INTERLIS 2.4 XTF transfer.

JDBC is a deliberately flat, input-only source: each `query` maps to one class of the declared
source model and becomes one basket in the transfer. Structures, references and geometry are not
expressed by a tabular query result; complex INTERLIS targets are produced by mapping rules.

## Files

- `models/DemoJdbcSource.ili` — flat source model (one `Municipality` class).
- `models/DemoTarget.ili` — INTERLIS 2.4 target model.
- `mapping.yaml` / `mapping.ilimap` — equivalent mappings (YAML and `.ilimap`).

## 1. Create the demo SQLite database

The mapping reads from `build/demo.sqlite` (a relative `jdbc:sqlite:` url is resolved against the
mapping directory). Create it with the bundled SQLite JDBC driver, e.g. via `sqlite3`:

```bash
mkdir -p build
sqlite3 build/demo.sqlite <<'SQL'
CREATE TABLE municipalities (
  id INTEGER PRIMARY KEY,
  bfsnr INTEGER,
  name TEXT,
  population INTEGER
);
INSERT INTO municipalities (id, bfsnr, name, population) VALUES
  (1, 2601, 'Solothurn', 17000),
  (2, 2610, 'Olten', 19000);
SQL
```

## 2. Run the transformation

```bash
ilitransformer transform -m mapping.yaml --modeldir models --validate --report build/report
```

or with the `.ilimap` mapping:

```bash
ilitransformer transform -m mapping.ilimap --modeldir models --validate --report build/report
```

The output `build/out/municipalities.xtf` is validated with ilivalidator and contains two
`Municipality` objects.

## Connection options

- Inline credentials: `connection.user` / `connection.password`.
- Indirect credentials (preferred): `connection.userEnv` / `connection.passwordEnv` read the named
  environment variables. Passwords are never written to logs, diagnostics or reports.
- Extra driver properties: `connection.properties` (YAML) or repeated `property "key" "value";`
  (`.ilimap`).

Drivers other than the bundled SQLite driver must be on the classpath. For a real PostgreSQL/PostGIS
database, see `dev/stack/compose.yml`.
