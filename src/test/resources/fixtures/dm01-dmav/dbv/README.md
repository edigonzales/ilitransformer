# DBV (DauerndeBodenverschiebungen / Rutschgebiete) Minimal Fixtures

## dm01-minimal.itf
Curated minimal DM01 INTERLIS 1 transfer containing:
- 1 x Rutschung (SURFACE, mit Name, GueltigerEintrag) + Rutschung_Geometrie helper table
- 1 x RutschungPos (Textposition)

SURFACE pattern (INTERLIS 1): Main table (Rutschung) before geometry helper table (Rutschung_Geometrie), join via OID.

## dmav-minimal.xtf
Same data in DMAV INTERLIS 2 XTF format:
- 1 x DBVNachfuehrung (mit Perimeter SURFACE, GueltigerEintrag)
- 1 x DauerndeBodenverschiebung (SURFACE, mit Textposition BAG und Entstehung Ref)

## Round Trip Coverage

| Class | DM01→DMAV→DM01 | DMAV→DM01→DMAV |
|-------|:---:|:---:|
| DBVNachfuehrung | N/A | Yes (forward proxy only) |
| DauerndeBodenverschiebung | N/A | Yes |
| Rutschung | Yes | N/A |

Mapping note: DM01 Rutschung serves dual purpose (Nachfuehrung + Objekt). Forward direction creates both DBVNachfuehrung and DauerndeBodenverschiebung from the same Rutschung source. Reverse direction creates Rutschung from DauerndeBodenverschiebung, with GueltigerEintrag via Entstehung ref.

## Expected Losses (DMAV roundtrip)

- GueltigerEintrag time component lost (DM01 uses DATE, DMAV uses XMLDateTime)
- GenehmigtAm not mappable via DM01
- Textposition.DarstellungIn defaulted in forward direction
- Entstehung TIDs differ

## Usage
These fixtures are used by `DbvMinimalFixtureRoundtripTest` for bidirectional round-trip verification.
