# Nomenklatur (Flurnamen) Minimal Fixtures

## dm01-minimal.itf
Curated minimal DM01 INTERLIS 1 transfer containing:
- 1 x NKNachfuehrung (gueltig, mit Perimeter)
- 1 x Flurname (AREA) + Flurname_Geometrie helper table + FlurnamePos
- 1 x Ortsname (SURFACE) + Ortsname_Geometrie helper table + OrtsnamePos
- 1 x Gelaendename + GelaendenamePos (in der Fixture, aber nicht im Round Trip)

## dmav-minimal.xtf
Same data in DMAV INTERLIS 2 XTF format:
- 1 x NKNachfuehrung
- 1 x Flurname (AREA, mit Textposition BAG)
- 1 x Ortsname (SURFACE, mit Textposition BAG)
- 1 x Gelaendename (Coord2, mit Textposition BAG)

## Bekannte Einschränkungen

- **Gelaendename** ist wegen Engine-Limitation (NO IDENT parent-child `lookup()`) nicht im Round Trip enthalten. DM01 Gelaendename hat keine Geometrie; die Position steckt im Kind-Objekt GelaendenamePos, auf das per `lookup()` nicht zugegriffen werden kann.
- **Textposition BAG round trip** (`FlurnamePos` ↔ `Textposition`, `OrtsnamePos` ↔ `Textposition`) hat Count-Erhaltungsprobleme im DMAV→DM01→DMAV-Round-Trip wegen NO IDENT Parent-Referenzen. Die Mapping-Logik funktioniert für Forward/Reverse einzeln, aber der Round-Trip verliert BAG-Items.

## Usage
These fixtures are used by `NomenklaturMinimalFixtureRoundtripTest` for bidirectional round-trip verification.
