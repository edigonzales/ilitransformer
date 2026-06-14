# GS (Grundstuecke/Liegenschaften) Minimal Fixtures

## dm01-minimal.itf
Curated minimal DM01 INTERLIS 1 transfer containing:
- 1 x LSNachfuehrung (gueltig)
- 4 x Grenzpunkt (forming a 50x50m rectangle)
- 4 x GrenzpunktPos (text labels for Grenzpunkte)
- 4 x GrenzpunktSymbol (symbol orientations)
- 1 x Grundstueck (Liegenschaft, rechtskraeftig, vollstaendig)
- 1 x GrundstueckPos (text label for Grundstueck)
- 1 x Liegenschaft (AREA geometry via Liegenschaft_Geometrie helper table, 2500 m²)

## dmav-minimal.xtf
Same data in DMAV INTERLIS 2 XTF format with:
- SURFACE geometry for Liegenschaft
- BAG Textposition for Grundstueck
- Entstehung_Grenzpunkt and Entstehung_Grundstueck associations
- GrundstueckLiegenschaft association

## Usage
These fixtures are used by `GsMinimalFixtureRoundtripTest` for bidirectional round-trip verification.
