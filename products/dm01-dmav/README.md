# DM01 ↔ DMAV Use Case

Die produktiven, versionierten Profile für diesen Use Case liegen unter `profiles/`. Topic-Fixtures liegen unter `src/test/resources/fixtures/dm01-dmav/`: kuratierte `*-minimal`-Fixtures für Roundtrip-/Validator-Gates sowie `*-real-extract`-Fixtures, die aus `src/test/data/DMAV_Version_1_1/` extrahiert und separat evidenzgeführt werden. Reproduzierbare Voll-Läufe mit externen Originaldatensätzen liegen als manifestgesteuerte Bundles unter `products/dm01-dmav/full-runs/`.

Der primäre produktionsnahe Use Case des ilitransformer ist die Transformation von INTERLIS-Transferdaten zwischen **DM01** (AV93, INTERLIS 1, ITF) und **DMAV** (INTERLIS 2.4, XTF).

## Modelle

### DM01 (AV93)

- **Format**: INTERLIS 1, ITF
- **Modelldatei**: `DM01AVCH24LV95D.ili`
- **Quelle**: `https://models.geo.admin.ch/V_D/DM.01-AV-CH_LV95_24d_ili1.ili`
- **CRS**: LV95 (EPSG:2056)
- **OID-Typ**: STANDARDOID (fortlaufend)

### DMAV

- **Format**: INTERLIS 2.4, XTF
- **Umbrella-Modell**: `DMAVTYM_Alles_V1_1.ili`
- **Quelle**: `https://models.geo.admin.ch/V_D/DMAVTYM_Alles_V1_1.ili`
- **CRS**: LV95 (EPSG:2056)
- **OID-Typ**: UUIDOID
- **Basket**: pro Topic

Fachmodelle (u.a.):
- `DMAV_FixpunkteAVKategorie3_V1_1.ili`
- `DMAV_Grundstuecke_V1_1.ili`
- `DMAV_Bodenbedeckung_V1_1.ili`
- `DMAV_Einzelobjekte_V1_1.ili`
- `DMAV_Gebaeudeadressen_V1_1.ili`
- `DMAV_Nomenklatur_V1_1.ili`
- `DMAV_Rohrleitungen_V1_1.ili`
- `DMAV_HoheitsgrenzenAV_V1_0.ili`

## Korrelationstabelle

Die Datei `DMAV_Korrelationstabelle_20260301.xlsx` enthält die fachliche Korrelation zwischen DM01- und DMAV-Objekten. Sie ist im Repository unter `docs/dm01-dmav/` abgelegt.

Siehe `correlation-table.md` für Details zur Interpretation.

## Aktueller Unterstützungsstand

### Fixpunkte (LFP3)

| Richtung | Status | Validierung |
|---|---|---|
| DM01 → DMAV LFP3 | Funktioniert | ilivalidator OK |
| DMAV → DM01 LFP3 | Funktioniert | mit dokumentiertem Loss |
| Textpositionen (BAG) | Funktioniert | beide Richtungen |
| Symbolorientierung | Funktioniert | beide Richtungen |

### Weitere Topics

Siehe `status-matrix.md` für den detaillierten Status pro Topic/Klasse/Attribut.

## Wichtige Annahmen

- **Kein Roundtrip-Garantie**: DM01→DMAV→DM01 ist nicht bitidentisch. DMAV hat semantisch reichere oder anders normalisierte Objekte.
- **DM01 DATE → DMAV XMLDateTime**: Konvertierung mit dokumentiertem Änderungsrisiko (Tagesgenauigkeit).
- **DMAV UUID-OIDs**: Verwendet `deterministicUuid` (UUIDv3) für reproduzierbare Transformation.
- **Grenzpunktfunktion**: Zunächst Default `#keine`, spätere Ableitung aus Kontext.
- **AktiverUnterhalt**: Default `true` für DM01→DMAV.

## Nächste fachliche Slices

Gemäss SPEC §6:
1. HFP3 (FixpunkteKategorie3)
2. Grenzpunkt (Liegenschaften/Grundstücke)
3. Grundstück/Liegenschaft
4. Bodenbedeckung
5. Einzelobjekte

## Referenzen

- SPEC §5: Fachlicher Kern-Use-Case
- SPEC §20: DM01→DMAV LFP3 Pilot
- SPEC §21: DMAV→DM01 LFP3 Pilot
- `correlation-table.md`: XLSX-Interpretation
- `status-matrix.md`: Mapping-Status pro Attribut
- `lfp3-pilot.md`: LFP3-Pilot-Dokumentation
- `lossiness.md`: Informationsverlust-Dokumentation
- `improvement-notes.md`: Hergeleitete Verbesserungsvorschlaege aus Full-Run-Beobachtungen
- `open-questions.md`: Offene fachliche Fragen
