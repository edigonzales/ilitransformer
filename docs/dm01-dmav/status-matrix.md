# Status-Matrix DM01 ↔ DMAV

## Legende

| Symbol | Bedeutung |
|---|---|
| ✅ | Validiert (Profil + Tests + ilivalidator grün) |
| ⚠️ | Profil + Integrationstests + minimale Fixtures grün, aber Voll-Dataset- oder Reverse-Regression noch nicht komplett grün |
| 🔧 | Profil vorhanden, ungetestet/nicht validiert |
| 🟡 | Teilweise / generierter Vorschlag |
| ❌ | Offen / nicht implementiert |
| ⚫ | Bewusst nicht unterstützt / fachlich unklar |

Stand: 2026-06-14

---

## FixpunkteKategorie3 / FixpunkteAVKategorie3

### LFP3 (FixpunkteAVKategorie3.LFP3/LFP3Nachfuehrung) – ✅

**Profile:** `profiles/{dm01-to-dmav,dmav-to-dm01}/1.1/lfp3.yaml`
**Tests:** `Dm01ToDmavLfp3IntegrationTest`, `DmavToDm01Lfp3IntegrationTest`
**Real-Data-Tests:** `Lfp3MinimalFixtureRoundtripTest`, `RealDm01ToDmavLfp3EndToEndTest`, `RealDmavToDm01Lfp3EndToEndTest`, `Lfp3RealExtractRoundtripTest`, `ExtractedDm01FixtureValidationTest`, `ExtractedDmavFixtureValidationTest`
**Fixtures:** `src/test/resources/fixtures/dm01-dmav/lfp3/{dm01-minimal.itf,dmav-minimal.xtf,dm01-real-extract.itf,dmav-real-extract.xtf}`
**ilivalidator / realDataTest:** beide Richtungen validiert

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

#### HFP3 (HFP3Nachfuehrung + HFP3) – ✅

**Profile:** `profiles/{dm01-to-dmav,dmav-to-dm01}/1.1/hfp3.yaml`
**Tests:** `Dm01ToDmavHfp3IntegrationTest`, `DmavToDm01Hfp3IntegrationTest`
**Real-Data-Tests:** `Hfp3MinimalFixtureRoundtripTest`, `RealDm01ToDmavHfp3EndToEndTest`, `RealDmavToDm01Hfp3EndToEndTest`, `Hfp3RealExtractRoundtripTest`, `ExtractedHfp3Dm01FixtureValidationTest`, `ExtractedHfp3DmavFixtureValidationTest`
**Fixtures:** `src/test/resources/fixtures/dm01-dmav/hfp3/{dm01-minimal.itf,dmav-minimal.xtf,dm01-real-extract.itf,dmav-real-extract.xtf}`

##### HFP3Nachfuehrung

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Identifikator | Identifikator | ✅ | ✅ |
| Beschreibung | Beschreibung | ✅ | ✅ |
| Perimeter | Perimeter | ✅ | ✅ |
| GueltigerEintrag | GueltigerEintrag / Datum1 | ✅ | ✅ |

##### HFP3

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Nummer | Nummer | ✅ | ✅ |
| Geometrie | Geometrie | ✅ | ✅ |
| Hoehengeometrie | HoeheGeom | ✅ | ✅ |
| Lagegenauigkeit | LageGen | ✅ | ✅ |
| IstLagezuverlaessig | LageZuv | ✅ | ✅ |
| Hoehengenauigkeit | HoeheGen | ✅ | ✅ |
| IstHoehenzuverlaessig | HoeheZuv | ✅ | ✅ |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |
| Textposition (BAG) | HFP3Pos | ✅ | ✅ |

---

## Bodenbedeckung / Bodenbedeckung – ✅

**Profile:** `profiles/{dm01-to-dmav,dmav-to-dm01}/1.1/bb.yaml`
**Testmodelle:** `dm01-bb-test.ili`, `dmav-bb-test.ili`
**Integrationstests:** `Dm01ToDmavBbIntegrationTest` (5 Tests ✅), `DmavToDm01BbIntegrationTest` (5 Tests ✅)
**Real-Data-Tests:** `BbMinimalFixtureRoundtripTest`, `BbFullDatasetForwardGateTest`, `BbFullDatasetRoundtripSmokeTest`, `BbItfAreaReadDiagnosticTest`, `ExtractedBbDm01FixtureValidationTest`, `ExtractedBbDmavFixtureValidationTest`
**Fixtures:** `src/test/resources/fixtures/dm01-dmav/bb/{dm01-minimal.itf,dmav-minimal.xtf,dm01-real-extract.itf,dmav-real-extract.xtf}`
**ilivalidator / realDataTest:** Forward, Reverse und ITF-Readback grün

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
| Fiktiv | — | ✅ (Default false) | ✅ (Reverse filtert `Fiktiv=true` bewusst aus) |
| Objektstatus | Gueltigkeit / `Proj*`-Klassen | ✅ (aus `BBNachfuehrung.Gueltigkeit` + `BoFlaeche`/`ProjBoFlaeche`) | ✅ (steuert `BoFlaeche` vs. `ProjBoFlaeche`) |
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

## Einzelobjekte – ⚠️

**Profile:** `profiles/{dm01-to-dmav,dmav-to-dm01}/1.1/eo.yaml`
**Real-Data-Tests:** `EoMinimalFixtureRoundtripTest`, `ExtractedEoDm01FixtureValidationTest`, `ExtractedEoDmavFixtureValidationTest`
**Fixtures:** `src/test/resources/fixtures/dm01-dmav/eo/{dm01-minimal.itf,dmav-minimal.xtf,README.md}`
**ilivalidator / realDataTest:** Round trip (SemanticTransferComparator + count verification) beide Richtungen grün, Fixtures validiert

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ✅ | ✅ |

### EONachfuehrung

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Identifikator | Identifikator | ✅ | ✅ |
| Beschreibung | Beschreibung | ✅ | ✅ |
| Perimeter | Perimeter | ✅ | ✅ |
| GueltigerEintrag | GueltigerEintrag | ✅ | ✅ |

### Einzelobjekt

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Qualitaetsstandard | Qualitaet | ✅ | ✅ |
| Einzelobjektart | Art | ✅ | ✅ |
| Objektstatus | Gueltigkeit (via EONachfuehrung) | ✅ (statisch #real) | ✅ (filtert #projektiert) |
| EGID | lookup(GWR_EGID) | ✅ | ✅ |
| Flaechenelement (BAG) | Flaechenelement | ✅ | ✅ |
| Linienelement (BAG) | Linienelement | ✅ | ✅ |
| Punktelement (BAG) | Punktelement | ✅ | ✅ |
| Objektname (BAG) | Objektname | ✅ | ✅ |
| Objektnummer (BAG) | Objektnummer | ✅ | ✅ |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |

### Einzelpunkt / Messpunkt

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Nummer | Identifikator | ✅ | ✅ |
| Geometrie | Geometrie | ✅ | ✅ |
| Lagegenauigkeit | LageGen | ✅ | ✅ |
| IstLagezuverlaessig | LageZuv | ✅ | ✅ |
| IstExaktDefiniert | ExaktDefiniert | ✅ | ✅ |
| Hoehengeometrie | — | ❌ | ❌ |
| Hoehengenauigkeit | — | ❌ | ❌ |
| IstHoehenzuverlaessig | — | ❌ | ❌ |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |

---

## Nomenklatur – ⚠️

**Profile:** `profiles/{dm01-to-dmav,dmav-to-dm01}/1.1/nomenklatur.yaml`
**Real-Data-Tests:** `NomenklaturMinimalFixtureRoundtripTest`
**Fixtures:** `src/test/resources/fixtures/dm01-dmav/nomenklatur/{dm01-minimal.itf,dmav-minimal.xtf,README.md}`
**ilivalidator / realDataTest:** Round trip (SemanticTransferComparator) beide Richtungen validiert. Gelaendename wegen Engine-Limitation (NO IDENT parent-child lookup) nicht im Round Trip enthalten.

| Status | DM01→DMAV | DMAV→DM01 |
|---|---|---|
| Profil | ✅ | ✅ |

### NKNachfuehrung

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| NBIdent | NBIdent | ✅ | ✅ |
| Identifikator | Identifikator | ✅ | ✅ |
| Beschreibung | Beschreibung | ✅ | ✅ |
| Perimeter | Perimeter | ✅ | ✅ |
| GueltigerEintrag | GueltigerEintrag / Datum1 | ✅ | ✅ |

### Flurname

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Name | Name | ✅ | ✅ |
| Geometrie | Geometrie (AREA) | ✅ | ✅ |
| Fiktiv | — | ✅ (Default false) | ⚫ (DM01 hat kein Fiktiv) |
| Textposition (BAG) | FlurnamePos | ✅ (Expand-Mode) | ⚠️ (Round Trip Count, NO IDENT Limitation) |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |

### Ortsname

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Name | Name | ✅ | ✅ |
| Geometrie | Geometrie (SURFACE) | ✅ | ✅ |
| Typ | Typ | ✅ | ✅ |
| Textposition (BAG) | OrtsnamePos | ✅ (Expand-Mode) | ⚠️ (Round Trip Count, NO IDENT Limitation) |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |

### Gelaendename

| DMAV-Attribut | DM01-Attribut | DM01→DMAV | DMAV→DM01 |
|---|---|---|---|
| Name | Name | ✅ | ✅ |
| Geometrie (Coord2) | GelaendenamePos.Pos | ✅ (per Ref→Object Join) | ✅ |
| Textposition (BAG) | GelaendenamePos | ✅ (Expand-Mode) | ⚠️ (Round Trip Count, NO IDENT Limitation) |
| Entstehung (Ref) | Entstehung | ✅ | ✅ |

> **Hinweis:** Gelaendename DM01→DMAV verwendet einen Ref-to-Object-Join (`eq(gnp.GelaendenamePos_von, gn)`) um die Geometrie aus GelaendenamePos zu ziehen. Dieses Join-Feature ist ab Phase 24 implementiert (`processJoinedRule()` mit OID-Map für Bare-Alias rechte Seite).

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
