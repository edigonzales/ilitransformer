# Status-Matrix DM01 ↔ DMAV

## Legende

| Symbol | Bedeutung |
|---|---|
| ✅ | Unterstützt (Golden Test + Validierung) |
| 🟡 | Teilweise / generierter Vorschlag |
| ❌ | Offen / nicht implementiert |
| ⚫ | Bewusst nicht unterstützt / fachlich unklar |

## FixpunkteKategorie3 / FixpunkteAVKategorie3

### LFP3Nachfuehrung

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Identifikator | Identifikator | ✅ | ✅ |
| Beschreibung | Beschreibung | ✅ | ✅ |
| Perimeter | Perimeter | ✅ | ✅ |
| GueltigerEintrag | GueltigerEintrag / Datum1 | ✅ | ✅ |

### LFP3

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Nummer | Nummer | ✅ | ✅ |
| LFPArt | — | ✅ (Default #LFP3) | ❌ |
| Geometrie | Geometrie | ✅ | ✅ |
| Hoehengeometrie | HoeheGeom | ✅ | ✅ |
| Lagegenauigkeit | LageGen | ✅ | ✅ |
| IstLagezuverlaessig | LageZuv | ✅ | ✅ |
| Hoehengenauigkeit | HoeheGen | ✅ | ✅ |
| IstHoehenzuverlaessig | HoeheZuv | ✅ | ✅ |
| Punktzeichen | Punktzeichen | ✅ | ✅ |
| Schutzart | — | ❌ (null) | ❌ |
| Grenzpunktfunktion | — | ✅ (Default #keine) | ❌ |
| IstHoheitsgrenzsteinAlt | — | ❌ | ❌ |
| AktiverUnterhalt | — | ✅ (Default true) | ❌ |
| SymbolOri | LFP3Symbol.Ori | ✅ | ✅ |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |
| Textposition (BAG) | LFP3Pos | ✅ | ✅ |

### HFP3Nachfuehrung / HFP3

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Gesamt | 🟡 | ❌ |

## Liegenschaften / Grundstuecke

### Grenzpunkt

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Gesamt | | 🟡 | ❌ |

### Grundstueck / Liegenschaft

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Gesamt | ❌ | ❌ |

## Bodenbedeckung

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Gesamt | ❌ | ❌ |

Bemerkung: AREA-Topologie und SURFACE-Konvertierung sind komplex. Nicht in MVP.

## Einzelobjekte

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Gesamt | ❌ | ❌ |

## Nomenklatur, Gebäudeadressen, Toleranzstufen, Rohrleitungen, Hoheitsgrenzen

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Gesamt | ❌ | ❌ |

## Generierte Mapping-Kandidaten

Der Mapping Candidate Generator (Phase 9) hat folgende Kandidatenmengen erzeugt:

| Klassifizierung | Anzahl |
|---|---|
| high (≥0.85) | wert aus Report |
| medium (0.60–0.84) | wert aus Report |
| low (0.30–0.59) | wert aus Report |
| manual (<0.30) | wert aus Report |

Generierte YAML-Fragmente:
- `build/generated/dm01-dmav/dm01-to-dmav.generated.yaml`
- `build/generated/dm01-dmav/dmav-to-dm01.generated.yaml`

Siehe `build/reports/dm01-dmav/candidate-report.md` für Details.
