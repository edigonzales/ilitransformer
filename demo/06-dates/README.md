# 06 – dates

Datums- und Zeitfunktionen.

## Abgedeckte INTERLIS-Elemente

- `INTERLIS.XMLDate` (Datum, ISO-Format `2024-03-15`)
- `INTERLIS.XMLDateTime` (Zeitstempel `2024-03-15T12:00:00`)

## Abgedeckte Expressions

- `toXmlDateTime(date)` → `INTERLIS.XMLDateTime`
- `toDate(date)` → kompaktes Datum `20240315`
- `toInterlis1Date(date)` → INTERLIS-1-Datum `20240315`
- `now()` → aktueller Zeitstempel (in `defaults`)
- direkte Kopie eines `XMLDate`-Attributs

## Wichtige Stolpersteine

- `compileMode compatible`, weil `now()` als nicht-deterministisch markiert ist und
  unter `strict` zu einem Fehler hochgestuft würde (Warnung `ILITRF-EXPR-NON-DETERMINISTIC`).
- `now()` liefert einen Zeitstempel **mit Mikrosekunden**
  (`2026-06-22T22:11:33.706438`). Dieses Format ist **kein** gültiges
  `INTERLIS.XMLDateTime`. Im Demo wird `Verarbeitet` deshalb als `TEXT` geführt.
- `toDate(...)` erzeugt das kompakte Format `20240315`; das passt **nicht** auf ein
  `INTERLIS.XMLDate`-Ziel (erwartet `2024-03-15`). Für eine echte `XMLDate`-Kopie wird
  das Quellattribut direkt zugewiesen (`DatumKopie = s.Datum`).
- Wegen `now()` ist die Ausgabe **nicht** reproduzierbar (kein Golden-Test geeignet).

## Aufrufe

```bash
demo/validate.sh demo/06-dates
```

## Erwartetes Ergebnis

Für `Datum=2024-03-15`: `Erfasst=2024-03-15T12:00:00`, `DatumKopie=2024-03-15`,
`DatumKompakt=20240315`, `DatumI1=20240315`, `Verarbeitet=<aktueller Zeitstempel>`.
