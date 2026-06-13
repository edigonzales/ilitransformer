# EO (Einzelobjekte) Minimal Fixtures

## dm01-minimal.itf
Curated minimal DM01 INTERLIS 1 transfer containing:
- 1 x EONachfuehrung (gueltig)
- 1 x Einzelobjekt (Mauer, AV93)
- 1 x Flaechenelement (SURFACE) + Symbolposition
- 1 x Linienelement (POLYLINE) + Symbolposition
- 1 x Punktelement (point) + SymbolOri
- 1 x Objektname + Textposition
- 1 x Objektnummer + Textposition
- 1 x Einzelpunkt (Messpunkt equivalent)
- 1 x EinzelpunktPos (label for Einzelpunkt)

## dmav-minimal.xtf
Same data in DMAV INTERLIS 2 XTF format with BAG structures.

## Usage
These fixtures are used by `EoMinimalFixtureRoundtripTest` for bidirectional round-trip verification.
