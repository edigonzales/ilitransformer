# Informationsverlust (Lossiness)

DM01 ↔ DMAV sind nicht symmetrisch. Vor allem DMAV → DM01 ist verlustbehaftet.

## Klassifikation

In der Mapping-DSL kann die Lossiness pro Rule deklariert werden:

```yaml
metadata:
  direction: dmav-to-dm01
  roundtrip: notGuaranteed
  lossiness: none | minor | significant | unknown
```

| Stufe | Bedeutung |
|---|---|
| `none` | Kein Informationsverlust. Hin- und Rückrichtung sind identisch. |
| `minor` | Geringer Verlust (z.B. Zeitanteil bei DateTime → Date). |
| `significant` | Signifikanter Verlust (z.B. Wegfall ganzer Attribute). |
| `unknown` | Noch nicht evaluiert. |

## DMAV → DM01: Bekannte Verluste

### Zeitinformationen

DMAV `INTERLIS.XMLDateTime` → DM01 `DATE`:
- **Verlust**: Uhrzeit und Zeitzone gehen verloren.
- **Strategie**: `dateAtStartOfDay()` — Zeit wird auf Mitternacht UTC gesetzt.
- **Lossiness**: `minor`

### LFPArt

DM01 hat kein Äquivalent zu DMAV `LFPArt`. Beim Rückweg geht diese Information verloren.

- **Verlust**: LFPArt-Wert (#LFP3, #HFP3, etc.)
- **Strategie**: Kein Mapping, Information entfällt.
- **Lossiness**: `minor`

### Grenzpunktfunktion

DMAV `Grenzpunktfunktion` hat kein direktes DM01-Äquivalent:
- **Verlust**: Funktion des Punktes (Grenzpunkt, Hoheitsgrenzpunkt, etc.)
- **Strategie**: Kein Mapping.
- **Lossiness**: `significant`

### AktiverUnterhalt

DM01 hat kein `AktiverUnterhalt`-Attribut:
- **Verlust**: Kenntnis über Unterhaltsstatus
- **Strategie**: Kein Mapping.
- **Lossiness**: `minor`

### Schutzart

DM01 hat kein `Schutzart`-Attribut:
- **Verlust**: Schutzart-Information
- **Strategie**: Kein Mapping.
- **Lossiness**: `minor`

### Textposition → LFP3Pos

DMAV BAG OF Textposition zurück in DM01 LFP3Pos-Tabelle:
- **Problem**: DM01 LFP3Pos ist 1:1, DMAV Textposition kann 0..n haben
- **Strategie**: Erste Position wird gemappt, zusätzliche entfallen
- **Lossiness**: `significant`

### SymbolOri → LFP3Symbol

DMAV `SymbolOri` ist ein Attribut auf LFP3, DM01 modelliert `Ori` in separater `LFP3Symbol`-Tabelle:
- **Strategie**: Tabelle materialisieren (1 Zeile)
- **Lossiness**: `none` (Strukturwechsel, kein Wertverlust)

## DM01 → DMAV: Bekannte Verluste

### Protokoll

DM01 `Protokoll` hat kein DMAV-Äquivalent:
- **Verlust**: Protokoll-Information
- **Strategie**: Kein Mapping.
- **Lossiness**: `minor`

### GueltigerEintrag

DM01 `GueltigerEintrag` optional → DMAV `GueltigerEintrag` mandatory:
- **Problem**: Was tun, wenn DM01 weder GueltigerEintrag noch Datum1 hat?
- **Aktuelle Lösung**: `coalesce(GueltigerEintrag, Datum1, job.defaultDate)`
- **Lossiness**: `none` (Default ersetzt fehlenden Wert)

## Roundtrip

DM01 → DMAV → DM01 ist **nicht bitidentisch**. Die Architektur garantiert keinen Roundtrip. Folgende Felder sind besonders betroffen:

1. Datum/Zeit: roundtrip führt immer zu Datum ohne Uhrzeit
2. UUID-OIDs: DMAV UUIDs können nicht in DM01 fortlaufende OIDs zurückgeführt werden
3. DMAV-spezifische Attribute (LFPArt, Grenzpunktfunktion, etc.): gehen beim Rückweg verloren
4. BAG ↔ Table: Strukturänderung kann Kardinalitätsverluste verursachen

## Prinzipien

- Jeder Verlust wird dokumentiert
- Roundtrip-Tests nur für explizit roundtrip-fähige Felder
- Keine falsche Symmetrie vortäuschen
- Lossiness-Metadaten im Mapping sind Pflicht für DMAV↔DM01
