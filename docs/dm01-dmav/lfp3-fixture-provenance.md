# LFP3 Fixture Provenance

## DMAV LFP3 Fixture

### Source

- **Original dataset:** `src/test/data/DMAV_Version_1_1/DMAVTYM_Alles_V1_1.xtf`
- **Model:** DMAVTYM_Alles_V1_1 (umbrella, compiles all topic models)
- **Extraction tool:** `ConnectedSubgraphExtractor` (Phase 27)

### Extraction Parameters

```java
ExtractionRequest request = new ExtractionRequest(
    List.of("LFP3Nachfuehrung", "LFP3"),  // target classes
    List.of(MODEL_DIR, "https://models.interlis.ch"),  // model dirs
    2,    // maxDepth: follow references up to 2 levels
    200,  // maxObjects: at most 200 objects
    true, // includeBidirectional: also include objects referencing seeds
    fixtureDir
);
```

### Result

The generated fixture is validated in a temporary directory by default.
The checked-in fixture `src/test/resources/real-dm01-dmav/lfp3/dmav-input.xtf` is only refreshed with an explicit opt-in.

### Validation

```bash
./gradlew realDataTest --tests "guru.interlis.transformer.ExtractedDmavFixtureValidationTest"
./gradlew realDataTest -PupdateFixtures=true --tests "guru.interlis.transformer.ExtractedDmavFixtureValidationTest"
```

The fixture includes:
- LFP3Nachfuehrung objects (with NBIdent, Identifikator, Beschreibung, Perimeter, GueltigerEintrag)
- LFP3 objects (with NBIdent, Nummer, Geometrie, Lagegenauigkeit, etc.)
- Entstehung_LFP3 associations
- Textposition BAG structures (embedded in LFP3)
- SymbolOri attribute
- Related basket structure preserved

### Model Dependencies

The DMAV umbrella model requires these INTERLIS standard models:
- Units, CoordSys (from `https://models.interlis.ch`)
- These are cached locally by ili2c after first compilation

---

## DM01 LFP3 Fixture

### Source

- **Original dataset:** `src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf`
- **Model:** DM01AVCH24LV95D
- **Extraction tool:** `ConnectedSubgraphExtractor` (Phase 27)

### Extraction Parameters

```java
ExtractionRequest request = new ExtractionRequest(
    List.of("LFP3Nachfuehrung", "LFP3"),  // target classes
    List.of(MODEL_DIR),                   // model dirs
    2,                                    // maxDepth
    200,                                  // maxObjects
    true,                                 // includeBidirectional
    fixtureDir
);
```

### Result

The generated fixture is validated in a temporary directory by default.
The checked-in fixture `src/test/resources/real-dm01-dmav/lfp3/dm01-input.itf` is only refreshed with an explicit opt-in.

### Verified

- DM01 model `DM01AVCH24LV95D` compiles successfully
- DM01 ITF can be read (49,876 objects, 20 baskets)
- LFP3-related classes are present in the dataset
- The extracted ITF contains `TOPI FixpunkteKategorie3`
- The extracted ITF contains `TABL LFP3Nachfuehrung_Perimeter`
- The extracted ITF validates successfully

### Basket Context Fix

`ItfReader2` reports real DM01 baskets through `StartBasketEvent.getType()`
while `getTopicv()` is `null`. The extractor must preserve that type when
rewriting the subset; otherwise LFP3 objects can be written under the first
model topic and `ItfGeometryWriter` will try to write
`LFP3Nachfuehrung_Perimeter` while the underlying `ItfWriter` is using the
wrong topic table list.

---

## Provenance Chain

```
DM01-AV-CH.itf  ──RealDatasetCatalog──▶ TransferDatasetDescriptor
    │                                        │
    │                                        ▼
    │                           TransferInventoryService.inspect()
    │                                        │
    │                                        ▼
    │                              TransferInventory (stats)
    │
    ▼
ConnectedSubgraphExtractor.extract()
    │
    ▼
dm01-input.itf / dmav-input.xtf  ──ilivalidator──▶ ValidationResult (valid ✓)
```

## Test Coverage

| Test | Location | Status |
|------|----------|--------|
| FullDatasetInventoryTest.dm01DatasetInventory | realDataTest | ✓ |
| FullDatasetInventoryTest.dmavDatasetInventory | realDataTest | ✓ |
| ExtractedDm01FixtureValidationTest.dm01ModelCompiles | realDataTest | ✓ |
| ExtractedDm01FixtureValidationTest.extractAndValidateDm01Lfp3Fixture | realDataTest | ✓ |
| ExtractedDmavFixtureValidationTest.extractAndValidateDmavLfp3Fixture | realDataTest | ✓ |
| ConnectedSubgraphExtractorTest (7 tests) | unit test | ✓ |
