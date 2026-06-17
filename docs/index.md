# ilitransformer - Dokumentation

## Was ist ilitransformer?

ilitransformer ist ein Java/Gradle-Werkzeug zur modellbewussten Transformation von INTERLIS-Transferdaten.
Die Engine ist generisch und nimmt keine Annahmen uber konkrete INTERLIS-Modelle vor.
Der produktionsnahe Referenz-Use-Case ist DM01 ↔ DMAV, dokumentiert als Produktprofil.

## Schnellstart

**Per Release-Download:**

```bash
unzip ilitransformer-0.1.0.zip
cd ilitransformer-0.1.0/
./bin/ilitransformer --help
./bin/ilitransformer transform -m examples/minimal/mapping.yaml
```

**Per Build aus Source:**

```bash
./gradlew installDist
./build/install/ilitransformer/bin/ilitransformer --help
./build/install/ilitransformer/bin/ilitransformer transform -m examples/minimal/mapping.yaml
```

## Minimales Mapping

```yaml
version: 1
job:
  description: "Minimales Mapping"

inputs:
  myInput:
    source: input.xtf

outputs:
  myOutput:
    target: output.xtf

mapping:
  rules:
    - id: copy
      sources:
        - myInput:QuellKlasse
      target:
        class: ZielKlasse
      assign:
        - target: Attr
          value: "Konstante"
```

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [cli.md](cli.md) | CLI-Referenz (transform, validate-mapping, inspect-model, dm01-dmav) |
| [mapping-dsl.md](mapping-dsl.md) | Mapping-DSL-Syntax und Semantik |
| [ilimap-dsl-v2.md](ilimap-dsl-v2.md) | Entwurf fuer eine eigene `.ilimap` Mapping-DSL v2 |
| [expressions.md](expressions.md) | Expression-Sprache und Builtin-Funktionen |
| [architecture.md](architecture.md) | Architektur-Ubersicht (Komponenten, Datenfluss, Design-Entscheidungen) |
| [testing.md](testing.md) | Testsuiten, Gradle-Integration, CI |
| [runtime.md](runtime.md) | Runtime-Dokumentation |
| [performance.md](performance.md) | Performance-Charakteristiken |
| [extension-points.md](extension-points.md) | Erweiterungspunkte der Engine |

## Produktprofil: DM01 ↔ DMAV

Das produktionsnahe DM01↔DMAV-Produktprofil ist eigenstandig dokumentiert:

- [products/dm01-dmav/README.md](../products/dm01-dmav/README.md) - Produktuberblick
- [products/dm01-dmav/status-matrix.md](../products/dm01-dmav/status-matrix.md) - Mapping-Status pro Topic/Attribut
- [products/dm01-dmav/lossiness.md](../products/dm01-dmav/lossiness.md) - Informationsverlust-Dokumentation
- [products/dm01-dmav/correlation-table.md](../products/dm01-dmav/correlation-table.md) - Korrelationstabelle

Produktive Profile: `profiles/dm01-to-dmav/1.1/` und `profiles/dmav-to-dm01/1.1/`.

## Stabilitat

| Bereich | Status |
|---|---|
| CLI (transform, validate-mapping, inspect-model) | Stabil |
| Mapping-DSL (Core) | Stabil |
| Expression-Engine + FunctionRegistry | Stabil |
| ITF/XTF I/O | Stabil |
| DM01→DMAV LFP3 | Stabil (ilivalidator-gepruft) |
| DMAV→DM01 LFP3 | Stabil (mit dokumentiertem Loss) |
| BAG/Textpositionen | Stabil |
| Joins / Splits / Merge | Experimentell |
| create-DSL | Experimentell |
| enumMap() | Supported, with documented missing-value warning behavior |
| external OID-Strategie | Stub |
| expression Basket-Strategie | Stub |

## Historische Dokumente

Historische Phasen-Dokumentation, Arbeitsnotizen und Architektur-Schnappschusse befinden sich unter [`docs/dev/archive/`](dev/archive/).

Technische Entwicklungsdokumente (ADR, Diagnostic-Codes, StateStore, Typed-Plan) befinden sich unter [`docs/dev/`](dev/).
