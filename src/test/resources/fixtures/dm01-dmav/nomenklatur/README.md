# Nomenklatur (Flurnamen) Minimal Fixtures

## dm01-minimal.itf
Curated minimal DM01 INTERLIS 1 transfer containing:
- 1 x NKNachfuehrung (gueltig, mit GueltigerEintrag)
- 1 x Flurname (AREA) + Flurname_Geometrie helper table + FlurnamePos
- 1 x Ortsname (SURFACE) + Ortsname_Geometrie helper table + OrtsnamePos
- 1 x Gelaendename + GelaendenamePos

## dmav-minimal.xtf
Same data in DMAV INTERLIS 2 XTF format:
- 1 x NKNachfuehrung
- 1 x Flurname (AREA, mit Textposition BAG)
- 1 x Ortsname (SURFACE, mit Textposition BAG)
- 1 x Gelaendename (Coord2, mit Textposition BAG)

## Round Trip Coverage

| Class | DM01→DMAV→DM01 | DMAV→DM01→DMAV |
|-------|:---:|:---:|
| NKNachfuehrung | ✅ | ✅ |
| Flurname | ✅ | ✅ |
| Ortsname | ✅ | ✅ |
| Gelaendename | ✅ (per Join) | ✅ |

Gelaendename DM01→DMAV verwendet einen Ref-to-Object-Join:
```yaml
joins:
  - left: gn
    right: gnp
    on: "eq(gnp.GelaendenamePos_von, gn)"
```

## Usage
These fixtures are used by `NomenklaturMinimalFixtureRoundtripTest` for bidirectional round-trip verification.
