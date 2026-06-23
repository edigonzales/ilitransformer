# 05 – conditionals-nulls

Bedingte Logik, Null-Behandlung und boolesche Verknüpfungen.

## Abgedeckte INTERLIS-Elemente

- `MANDATORY` vs. optionale Attribute (`Schwelle`, `Notiz`, `Alt`)
- numerische Bereiche, `BOOLEAN`-Zielattribute

## Abgedeckte Expressions

- `if(condition, then, else)`
- `coalesce(a, b, c, ...)` – erster nicht-`null`-Wert (variadisch)
- `default(value, fallback)`
- `defined(...)` / `notDefined(...)`
- Vergleiche `>=`, `>`, `==`, `!=`
- Verknüpfungen `and`, `or`, `not(...)`
- `where`-Filter auf Rule-Ebene (`s.Kategorie != "X"`)

## Wichtiger Stolperstein: numerische Vergleiche

Quell-Numerikattribute kommen zur Laufzeit als Text an. Ein Vergleich wie
`s.Wert >= 500` mischt dann Text und Zahl und bricht ab
(`Not a text value: NumberValue`). Numerische Vergleichsoperanden deshalb mit
`toNumber(...)` vereinheitlichen:

```ilimap
Klasse = if(toNumber(s.Wert) >= 500, "hoch", "tief");
```

Vergleiche von Enum-/Textwerten (`s.Kategorie == "A"`, `s.Prio == #hoch`) sind
unproblematisch.

## Aufrufe

```bash
demo/validate.sh demo/05-conditionals-nulls
```

## Erwartetes Ergebnis

3 Eingänge, 1 per `where` gefiltert (`Kategorie=X`). Objekt `Kategorie=A, Wert=750`:
`Klasse=hoch`, `HatNotiz=true`, `Text=wichtig`, `Bewertung=ueber`, `Spezial=true`,
`Relevant=true`, `NichtA=false`. Objekt `Kategorie=B, Wert=200` nutzt die Fallbacks
(`Text=alternativ` via coalesce auf `Alt`, `TextOrDefault=kein Text`).
