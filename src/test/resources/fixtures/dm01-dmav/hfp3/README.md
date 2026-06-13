# HFP3 Fixtures

## Dateien

- `dm01-minimal.itf`: kuratierter DM01-Minimalinput fuer HFP3-Roundtrip-Gates.
- `dmav-minimal.xtf`: kuratierter DMAV-Minimalinput fuer HFP3-Roundtrip-Gates.
- `dm01-real-extract.itf`: extractor-owned DM01-Fixture aus `src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf`.
- `dmav-real-extract.xtf`: extractor-owned DMAV-Fixture aus `src/test/data/DMAV_Version_1_1/DMAVTYM_Alles_V1_1.xtf`.

## Ownership

- `Hfp3MinimalFixtureRoundtripTest` verwendet die `*-minimal`-Fixtures.
- `ExtractedHfp3Dm01FixtureValidationTest`, `ExtractedHfp3DmavFixtureValidationTest`, `RealDm01ToDmavHfp3EndToEndTest`, `RealDmavToDm01Hfp3EndToEndTest` und `Hfp3RealExtractRoundtripTest` verwenden die `*-real-extract`-Fixtures.
