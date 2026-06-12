# Status-Matrix DM01 ↔ DMAV

## Legende

| Symbol | Bedeutung |
|---|---|
| ✅ | Validiert (Profil + Tests + ilivalidator grün) |
| 🔧 | Profil vorhanden, ungetestet/nicht validiert |
| 🟡 | Teilweise / generierter Vorschlag |
| ❌ | Offen / nicht implementiert |
| ⚫ | Bewusst nicht unterstützt / fachlich unklar |

Stand: 2026-06-11

---

## FixpunkteKategorie3 / FixpunkteAVKategorie3

### LFP3 (FixpunkteAVKategorie3.LFP3/LFP3Nachfuehrung) – ✅

**Profile:** `profiles/{dm01-to-dmav,dmav-to-dm01}/1.1/lfp3.yaml`
**Tests:** `Dm01ToDmavLfp3IntegrationTest`, `DmavToDm01Lfp3IntegrationTest`
**Fixtures:** `src/test/resources/real-dm01-dmav/lfp3/`
**ilivalidator:** beide Richtungen validiert

#### LFP3Nachfuehrung

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Identifikator | Identifikator | ✅ | ✅ |
| Beschreibung | Beschreibung | ✅ | ✅ |
| Perimeter | Perimeter | ✅ | ✅ |
| GueltigerEintrag | GueltigerEintrag / Datum1 | ✅ | ✅ |

#### LFP3

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Nummer | Nummer | ✅ | ✅ |
| LFPArt | — | ✅ (Default #LFP3) | ⚫ (kein Äquivalent) |
| Geometrie | Geometrie | ✅ | ✅ |
| Hoehengeometrie | HoeheGeom | ✅ | ✅ |
| Lagegenauigkeit | LageGen | ✅ | ✅ |
| IstLagezuverlaessig | LageZuv | ✅ | ✅ |
| Hoehengenauigkeit | HoeheGen | ✅ | ✅ |
| IstHoehenzuverlaessig | HoeheZuv | ✅ | ✅ |
| Punktzeichen | Punktzeichen | ✅ | ✅ |
| Schutzart | — | ❌ (null) | ❌ |
| Grenzpunktfunktion | — | ✅ (Default #keine) | ⚫ (kein Äquivalent) |
| IstHoheitsgrenzsteinAlt | — | ❌ | ❌ |
| AktiverUnterhalt | — | ✅ (Default true) | ⚫ (kein Äquivalent) |
| SymbolOri | LFP3Symbol.Ori | ✅ | ✅ |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |
| Textposition (BAG) | LFP3Pos | ✅ | ✅ |

#### HFP3 (HFP3Nachfuehrung + HFP3) – 🟡

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |
| Generierte Kandidaten | 🟡 | ❌ |

---

## Bodenbedeckung / Bodenbedeckung – ✅

**Profile:** `profiles/{dm01-to-dmav,dmav-to-dm01}/1.1/bb.yaml`
**Testmodelle:** `dm01-bb-test.ili`, `dmav-bb-test.ili`
**Integrationstests:** `Dm01ToDmavBbIntegrationTest` (5 Tests ✅), `DmavToDm01BbIntegrationTest` (5 Tests ✅)
**Fixtures:** `real-dm01-dmav/bb/dm01-minimal.itf` ✅, `dmav-minimal.xtf` ✅
**ilivalidator:** beide Fixtures validiert

### BBNachfuehrung

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Identifikator | Identifikator | ✅ | ✅ |
| Beschreibung | Beschreibung | ✅ | ✅ |
| Perimeter | Perimeter | ✅ | ✅ |
| GueltigerEintrag | GueltigerEintrag / Datum1 | ✅ | ✅ |

### BoFlaeche / Bodenbedeckung

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Geometrie | Geometrie | ✅ | ✅ |
| Qualitaetsstandard | Qualitaet | ✅ | ✅ |
| Bodenbedeckungsart | Art | ✅ | ✅ |
| Fiktiv | — | ✅ (Default false) | ⚫ (kein Äquivalent) |
| Objektstatus | — | ✅ (Default #weitere) | ⚫ (kein Äquivalent) |
| EGID | lookup(GWR_EGID) | ✅ | ✅ |
| Objektnummer (BAG) | Gebaeudenummer | ✅ | ✅ |
| Objektname (BAG) | Objektname | ✅ | ✅ |
| Symbolposition (BAG) | BoFlaecheSymbol | ✅ | ✅ |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |

### Einzelpunkt / Messpunkt

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Nummer | Identifikator | ✅ | ✅ |
| Geometrie | Geometrie | ✅ | ✅ |
| Lagegenauigkeit | LageGen | ✅ | ✅ |
| IstLagezuverlaessig | LageZuv | ✅ | ✅ |
| Hoehengeometrie | — | ❌ | ❌ |
| Hoehengenauigkeit | — | ❌ | ❌ |
| IstHoehenzuverlaessig | — | ❌ | ❌ |
| IstExaktDefiniert | ExaktDefiniert | ✅ | ✅ |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |

---

## Liegenschaften / Grundstücke

### Grenzpunkt – 🟡

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |
| Generierte Kandidaten | 🟡 | ❌ |

### Grundstück / Liegenschaft – ❌

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |

---

## Einzelobjekte – ❌

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |

---

## Nomenklatur – ❌

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |

---

## Gebäudeadressen – ❌

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |

---

## Toleranzstufen – ❌

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |

---

## Rohrleitungen – ❌

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |

---

## Hoheitsgrenzen – ❌

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ❌ | ❌ |

---

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
