# 03 – numbers-units

Numerische Umrechnungen (z. B. cm → m) mit den Mathematik-Funktionen.

## Abgedeckte INTERLIS-Elemente

- `UNIT Meter [m] EXTENDS INTERLIS.LENGTH;`
- `DOMAIN Laenge_m = 0.000 .. 100000.000 [m];`
- numerische Bereiche mit und ohne Nachkommastellen, negative Bereiche

## Abgedeckte Expressions

- `div(value, divisor)` – Division (cm → m)
- `round(value, scale)` – Runden auf n Nachkommastellen
- `add`, `sub`, `mul`
- `abs`
- `min`, `max`
- `toNumber(text)` – Text → Zahl
- Verschachtelung: `mul(div(s.LageGenCm, 100.0), toNumber(s.FaktorText))`

## Dateien

| Datei | Zweck |
|---|---|
| `models/numbers.ili` | Modell mit `Rohmessung` (cm) und `Resultat` |
| `data/input.xtf` | 2 Rohmessungen |
| `profile.ilimap` | Mapping-Profil |

## Aufrufe

```bash
demo/validate.sh demo/03-numbers-units
```

## Erwartetes Ergebnis (erstes Objekt)

| Ziel | Ausdruck | Wert |
|---|---|---|
| `LageGenM` | `div(3, 100.0)` | 0.03 |
| `HoeheGenM` | `round(div(5, 100.0), 2)` | 0.05 |
| `Summe` | `add(3, 5)` | 8 |
| `Differenz` | `sub(3, 5)` | -2 |
| `AbsOffset` | `abs(-12)` | 12 |
| `MinWert` / `MaxWert` | `min/max(3, 5)` | 3 / 5 |
| `Skaliert` | `mul(div(3,100.0), toNumber("2.5"))` | 0.075 |
