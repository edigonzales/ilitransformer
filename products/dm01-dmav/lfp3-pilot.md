# LFP3 Pilot

Der LFP3-Pilot ist der erste fachliche Slice der DM01↔DMAV-Transformation. Er umfasst Fixpunkte der Kategorie 3 (LFP3) in beide Richtungen.

## Slice

```
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Symbol

↔

DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3Nachfuehrung
DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3
DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.Entstehung_LFP3
```

## DM01 → DMAV

### LFP3Nachfuehrung

| DMAV | DM01 | Regel |
|---|---|---|
| NBIdent | NBIdent | Kopieren |
| Identifikator | Identifikator | Kopieren |
| Beschreibung | Beschreibung | Kopieren, truncate auf 60 |
| Perimeter | Perimeter | Kopieren |
| GueltigerEintrag | GueltigerEintrag, fallback Datum1 | DATE → XMLDateTime |

### LFP3

| DMAV | DM01 | Regel |
|---|---|---|
| NBIdent | NBIdent | Kopieren |
| Nummer | Nummer | Kopieren |
| LFPArt | — | Literal `#LFP3` |
| Geometrie | Geometrie | Kopieren via GeometryAdapter |
| Lagegenauigkeit | LageGen | Kopieren |
| IstLagezuverlaessig | LageZuv | Enum-Mapping `Zuverlaessigkeit` |
| Hoehengenauigkeit | HoeheGen | Kopieren |
| IstHoehenzuverlaessig | HoeheZuv | Enum-Mapping |
| Punktzeichen | Punktzeichen | Enum-Mapping `Versicherungsart` |
| Grenzpunktfunktion | — | Default `#keine` |
| AktiverUnterhalt | — | Default `true` |
| SymbolOri | LFP3Symbol.Ori | Join via lookup |
| Textposition (BAG) | LFP3Pos | BAG OF Textposition |
| Entstehung (Ref) | Entstehung | Referenz auf LFP3Nachfuehrung |

### OID-Strategie

`deterministicUuid` (UUIDv3) aus Namespace `dm01-to-dmav-lfp3` + Source-Key (NBIdent, Nummer).

### Tests

- Golden-Test mit minimalem DM01-Input
- Validierung via ilivalidator auf DMAV-XTF
- Test mit fehlendem optionalem DM01-Datum
- Referenztest Nachführung ↔ LFP3

## DMAV → DM01

### LFP3Nachfuehrung

| DM01 | DMAV | Regel |
|---|---|---|
| NBIdent | NBIdent | Kopieren |
| Identifikator | Identifikator | Kopieren |
| Beschreibung | Beschreibung | Kopieren |
| Perimeter | Perimeter | Kopieren |
| GueltigerEintrag | GueltigerEintrag | XMLDateTime → DATE |

### LFP3

| DM01 | DMAV | Regel |
|---|---|---|
| NBIdent | NBIdent | Kopieren |
| Nummer | Nummer | Kopieren |
| Geometrie | Geometrie | Kopieren |
| HoeheGeom | Hoehengeometrie | Kopieren |
| LageGen | Lagegenauigkeit | Kopieren |
| LageZuv | IstLagezuverlaessig | Boolean zurückmappen |
| HoeheGen | Hoehengenauigkeit | Kopieren |
| HoeheZuv | IstHoehenzuverlaessig | Zurückmappen |
| Punktzeichen | Punktzeichen | Enum zurückmappen |
| Protokoll | — | Default/offen |
| LFP3Pos | Textposition (BAG) | Structure in Tabelle materialisieren |
| LFP3Symbol.Ori | SymbolOri | Tabelle materialisieren |
| Entstehung (Ref) | Entstehung_LFP3 | Association zurück in Referenz |

### OID-Strategie

`preserve` oder `integer` (je nach DM01 OID-Typ).

### Informationsverlust

Siehe `lossiness.md` für dokumentierte Verluste.

## Validierung

Beide Richtungen werden mit `ilivalidator` validiert. Die Validierung ist im Integrationstest integriert.

```bash
./gradlew validateTransfer \
  -Ptransfer=build/out/dmav-lfp3.xtf \
  -Pmodel=DMAV_FixpunkteAVKategorie3_V1_1
```

## Einschränkungen

- `enumMap()` ist ein Stub (Pass-through mit Diagnostic-Warnung)
- `IstHoheitsgrenzsteinAlt` nicht unterstützt
- DM01 `Protokoll` auf Default/offen gesetzt
- Kein Roundtrip-Garantie

## Referenzen

- Mapping-Dateien: `profiles/dm01-to-dmav/1.1/lfp3.yaml`, `profiles/dmav-to-dm01/1.1/lfp3.yaml`
- Testdaten: `src/test/data/av/models/so_2549.itf`
- SPEC §20-21: Fachliche Mindestregeln
