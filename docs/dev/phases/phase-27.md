# Phase 27: Reale Datensatzinventarisierung und fachlich zusammenhängende Testausschnitte

## Implemented

- `TransferInventory` record: transfer content statistics (object counts, OID types, geometry types, LFP3-related classes)
- `TransferInventoryService`: reads transfers via `IoxReader`, aggregates per-basket and per-class statistics, uses model for richer type detection
- `ExtractionRequest` record: extraction parameters (target classes, max depth, max objects, bidirectional)
- `ExtractedTransfer` record: extraction result (file, classes, provenance)
- `ConnectedSubgraphExtractor`: reads source transfer, builds reference graph, selects seeds, BFS-expands subgraph, writes extracted transfer via `IoxWriter`

## Changed Classes

- `ConnectedSubgraphExtractor.readAll()`: break on `EndTransferEvent` instead of `null` return (fixes `IoxSyntaxException` on EOF)
- `ConnectedSubgraphExtractor.readAll()`: preserve the writer-ready basket type from `StartBasketEvent.getType()` before falling back to `getTopicv()`
- `ConnectedSubgraphExtractor.writeExtracted()`: group output baskets by basket ID and basket type so DM01 LFP3 objects are written under `DM01AVCH24LV95D.FixpunkteKategorie3`
- `TransferInventoryService`: break on `EndTransferEvent`, use model for type analysis

## Added Classes

| Class | Package |
|-------|---------|
| `TransferInventory` | `guru.interlis.transformer.model` |
| `TransferInventoryService` | `guru.interlis.transformer.model` |
| `ExtractionRequest` | `guru.interlis.transformer.model` |
| `ExtractedTransfer` | `guru.interlis.transformer.model` |
| `ConnectedSubgraphExtractor` | `guru.interlis.transformer.model` |

## Tests

| Test | Type | Status |
|------|------|--------|
| `ConnectedSubgraphExtractorTest` (7 tests) | Unit | ✓ |
| `FullDatasetInventoryTest` (2 tests) | realDataTest | ✓ |
| `ExtractedDm01FixtureValidationTest` (2 tests) | realDataTest | ✓ |
| `ExtractedDmavFixtureValidationTest` (1 test) | realDataTest | ✓ |

## Validation Commands

```bash
# Run unit tests
./gradlew test --tests "guru.interlis.transformer.model.ConnectedSubgraphExtractorTest"

# Run real data tests
./gradlew realDataTest

# Validate DM01 model
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar \
  --modeldir src/test/data/av/models/ \
  src/test/data/av/models/DM.01-AV-CH_LV95_24d_ili1.ili

# Validate test model
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar \
  --modeldir src/test/data/models/ \
  src/test/data/models/extract-test.ili
```

## Known Limitations

- DMAV inventory requires `https://models.interlis.ch` for `Units`/`CoordSys` model dependencies
- `ConnectedSubgraphExtractor` requires model availability for reference tracking

## Open Questions

- Can the umbrella model dependencies be cached locally to avoid remote repository dependency?

## DM01 ITF Fixture Note

The former `unknown ITF table LFP3Nachfuehrung_Perimeter` failure was caused by losing the ITF basket type. `ItfReader2` reports real DM01 baskets through `StartBasketEvent.getType()` while `getTopicv()` is `null`; writing selected LFP3 objects with a fallback first topic opened the wrong ITF table list. Preserving `DM01AVCH24LV95D.FixpunkteKategorie3` lets `ItfGeometryWriter` emit `LFP3Nachfuehrung_Perimeter` normally.

## Migration Notes

None. All new classes, no existing API changes.
