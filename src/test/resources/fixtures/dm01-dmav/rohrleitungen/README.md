# Rohrleitungen Minimal Fixtures

## dm01-minimal.itf
Curated minimal DM01 INTERLIS 1 transfer containing:
- 1 x RLNachfuehrung (mit NBIdent, Identifikator, Beschreibung, Gueltigkeit, GueltigerEintrag)
- 1 x Leitungsobjekt (mit Betreiber, Qualitaet, Art)
- 1 x Punktelement (LKoord, Ori)
- 1 x LeitungsobjektPos (Textposition)
- 1 x Signalpunkt (mit Nummer, Betreiber, Geometrie, Qualitaet, Art, Punktart)
- 1 x SignalpunktPos
- 1 x Einzelpunkt (mit Identifikator, Geometrie, LageGen, LageZuv, ExaktDefiniert)
- 1 x EinzelpunktPos

SURFACE note: Perimeter (OPTIONAL SURFACE) uses @ placeholder. Flaechenelement/Linienelement omitted due to INTERLIS 1 ITF parser limitations with NO IDENT SURFACE/POLYLINE tables. The DMAV XTF includes full SURFACE, Linienelement, and Flaechenelement data.

## dmav-minimal.xtf
Same data in DMAV INTERLIS 2 XTF format plus:
- RLNachfuehrung mit SURFACE Perimeter
- Leitungsobjekt mit Flaechenelement (SURFACE), Linienelement (POLYLINE), Punktelement (Coord2), Textposition
- Signal mit Entstehung ref, Textposition BAG
- Messpunkt mit Entstehung ref

## Round Trip Coverage

| Class | DM01→DMAV→DM01 | DMAV→DM01→DMAV |
|-------|:---:|:---:|
| RLNachfuehrung | Yes | Yes |
| Leitungsobjekt | Yes | Yes |
| Punktelement | Yes (BAG) | Yes (expand) |
| LeitungsobjektPos / Textposition | Yes (BAG) | Yes (expand) |
| Signalpunkt / Signal | Yes (BAG) | Yes (expand) |
| Einzelpunkt / Messpunkt | Yes | Yes |

## Expected Losses

- GueltigerEintrag time component lost (DM01 uses DATE, DMAV uses XMLDateTime)
- DM01 Qualitaetsstandard PV74/PEP mapped to DMAV PN
- DM01 Status gueltig/projektiert mapped to DMAV Objektstatus real
- Flaechenelement SURFACE not in DM01 minimal fixture (only in DMAV)
- Linienelement POLYLINE not in DM01 minimal fixture (only in DMAV)
- Textposition.DarstellungIn defaulted in forward direction
- Entstehung TIDs differ between directions

## Usage
These fixtures are used by `RohrleitungenMinimalFixtureRoundtripTest` for bidirectional round-trip verification.
