# Korrelationstabelle

Die Datei `DMAV_Korrelationstabelle_20260301.xlsx` ist die zentrale fachliche Referenz für die DM01↔DMAV-Transformation. Sie ist im Repository unter `docs/dm01-dmav/` abgelegt.

## Quelle und Version

- **Dateiname**: `DMAV_Korrelationstabelle_20260301.xlsx`
- **Stand**: 2026-03-01
- **Bezug**: DMAV Version 1.0 / 1.1

## Sheets

| Sheet | Zeilen | Spalten | Beschreibung |
|---|---|---|---|
| `Erklärungen` | 179 | 4 | Erläuterungen zur Tabelle |
| `Transformation` | 1204 | 35 | **Zentrales Sheet** — fachliche Übergangstabelle |
| `DMAV_Doku` | 483 | 34 | DMAV-Objektkatalog |
| `DMAV_Modell` | 838 | 54 | DMAV-Modellstruktur |
| `DMAV_Dienste` | 140 | 53 | DMAV-Dienste |
| `DM01-AV-CH` | 834 | 26 | DM01-Struktur |
| `Korrelation` | 1197 | 11 | Verstecktes Korrelationssheet |

## Sheet `Transformation`

### Spaltenstruktur

| Excel-Spalte | Index (0-basiert) | Bedeutung |
|---|---|---|
| A | 0 | Nr. |
| J | 9 | Link |
| K | 10 | DMAV-Typ |
| L | 11 | DMAV-Topic |
| M | 12 | DMAV-Class |
| N | 13 | DMAV-Attribut |
| O | 14 | DMAV-Structure |
| P | 15 | DMAV-Substructure |
| R | 17 | DMAV-Bemerkung |
| T | 19 | Bedingung/Voraussetzung in DM01 |
| U | 20 | Transformationscode DM01→DMAV |
| V | 21 | Zielklasse/-attribut DM01→DMAV |
| W | 22 | Ergänzung DM01→DMAV |
| Y | 24 | Bedingung/Voraussetzung in DMAV |
| Z | 25 | Transformationscode DMAV→DM01 |
| AA | 26 | Zieltabelle/-attribut DMAV→DM01 |
| AB | 27 | Ergänzung DMAV→DM01 |
| AD | 29 | Bemerkungen |
| AF | 31 | DM01-Link |
| AG | 32 | DM01-Topic |
| AH | 33 | DM01-Tabelle |
| AI | 34 | DM01-Attribut |

### Transformationscodes

| Code | Bedeutung | Confidence |
|---|---|---|
| `K` | Stabile Korrelation (direkt abbildbar) | 0.70 |
| `V` | Verschiebung (Zielattribut aus anderer Quelle) | 0.50 |
| `I` | Integration/Interpretation (Ableitung nötig) | 0.30 |
| sonstige | Unbekannt (→ Warning) | 0.10 |

### Parser-Verhalten

- Spalten werden über **feste Indizes** (0-basiert) gelesen
- Leere Zeilen werden übersprungen
- Richtungserkennung: DM01→DMAV wenn Code-Spalte U nicht leer, DMAV→DM01 wenn Code-Spalte Z nicht leer
- Unbekannte Codes → `ILITRF-DMAV-CORRELATION-PARSE` (WARNING)
- Zusammengeführte Zellen: aktuell nicht aufgelöst

### Generierte Artefakte

```text
build/generated/dm01-dmav/correlation-hints.json  (~137 kB, ~250 Hints)
build/reports/dm01-dmav/correlation-import-report.md
```

## Interpretation

Die XLSX ist **keine direkt ausführbare Mapping-Konfiguration**. Sie dient als **Mapping-Hint-Quelle**:

- Liefert Hinweise zu fachlich korrespondierenden Klassen/Attributen
- Enthält Bedingungen und Ergänzungen als Freitext
- Kodelliert Transformationsregeln als `K`/`V`/`I`-Klassen

Nicht vollständig abgedeckt:
- OID-Strategien
- Basket-Strategien
- Ausführungsreihenfolge
- Join-Kardinalitäten
- Vollständige Typkonvertierung
- Validator-konforme Defaults

## Kommandos

```bash
# Gradle-Task
./gradlew importDmavCorrelation

# CLI-Kommando
ili-transformer import-correlation --xlsx docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx
```
