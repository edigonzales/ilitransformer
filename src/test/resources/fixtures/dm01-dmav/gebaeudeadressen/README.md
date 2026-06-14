# Gebaeudeadressen Minimal Fixtures

## dm01-minimal.itf
Curated minimal DM01 INTERLIS 1 transfer containing:
- 1 x GEBNachfuehrung (gueltig, mit GueltigerEintrag)
- 1 x Lokalisation (Art=Strasse, Status=real)
- 1 x LokalisationsName (Text="Musterstrasse", Sprache=de)
- 1 x Strassenstueck (POLYLINE, IstAchse=ja)
- 1 x Gebaeudeeingang (Lage=Coord, Hausnummer=42, Im_Gebaeude=BB)
- 1 x HausnummerPos (Textposition)
- 1 x GebaeudeName (Text="Schulhaus", Sprache=de)
- 1 x GebaeudeNamePos (Textposition)

## dmav-minimal.xtf
Same data in DMAV INTERLIS 2 XTF format:
- 1 x GANachfuehrung
- 1 x Lokalisation (mit Lokalisationsname und Strassenstueck STRUCTURE)
- 1 x Gebaeudeeingang (mit Gebaeudename STRUCTURE)

## Round Trip Coverage

| Class | DM01->DMAV->DM01 | DMAV->DM01->DMAV |
|-------|:---:|:---:|
| G(E)BNachfuehrung | yes | yes |
| Lokalisation | yes | yes |
| LokalisationsName | yes (via BAG) | yes (via expand) |
| Strassenstueck | yes (via BAG) | no (LIST expand not yet implemented) |
| Gebaeudeeingang | yes | yes |
| HausnummerPos | no (DM01 only) | no |
| GebaeudeName | yes (via BAG) | yes (via expand) |
| GebaeudeNamePos | no (DM01 only) | no |

## Usage
These fixtures are used by `GebaeudeadressenMinimalFixtureRoundtripTest` for bidirectional round-trip verification.
