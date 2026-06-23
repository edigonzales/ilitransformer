# 04 – enumerations

Aufzählungen (inkl. hierarchischer Enums) und die Enum-Funktionen.

## Abgedeckte INTERLIS-Elemente

- `DOMAIN` mit einfacher Aufzählung (`Status`, `Groesse`, `Prioritaet`)
- **hierarchische Aufzählung** `Art = ( befestigt ( Strasse, Trottoir ), humusiert ( Acker, Wiese ), Gewaesser )`
- Enum-Attribute als Quell- und Zieltyp

## Abgedeckte Expressions

- `enumMap(value, MapSymbol)` – Map-Name als **Symbol**; leitet Zieltyp aus der Map ab (hier `BOOLEAN`)
- `enumMapStrict(value, "MapName")` – Map-Name als **String**; Fehler bei fehlender Quellzuordnung
- `enumMapDefault(value, "MapName", fallback)` – Ersatzwert bei fehlender Zuordnung
- `enumName(enum)` – Klartextname eines Enum-Werts
- `enumDefault(value, fallback)` – Enum-Wert oder Ersatz-Enum bei `null`
- `#Literal`-Vergleich im `if(...)` und im `where`-Filter

## Wichtige Stolpersteine

- In `enum`-Blöcken sind **keine** punktierten `#a.b`-Literale erlaubt. Hierarchische
  Zielwerte werden als **String** geschrieben: `"strasse" => "befestigt.Strasse";`.
- Nur `enumMap` akzeptiert den Map-Namen als Symbol; `enumMapStrict`/`enumMapDefault`
  erwarten den Map-Namen als String-Literal.
- `enumMapStrict`/`enumMapDefault` liefern den Compile-Typ `ENUM`. Für `BOOLEAN`-Ziele
  `enumMap` verwenden (leitet `BOOLEAN` aus den Map-Zielwerten ab).

## Aufrufe

```bash
demo/validate.sh demo/04-enumerations
```

## Erwartetes Ergebnis

3 Quellobjekte, 1 per `where s.Prio != #tief` gefiltert, 2 Zielobjekte. Erstes Objekt:
`Art=befestigt.Strasse`, `Groesse=gross`, `Aktiv=true`, `PrioName=hoch`, `Status=aktiv`,
`IstHoch=true`. Zweites Objekt nutzt die Fallbacks `Groesse=mittel`, `Status=inaktiv`.
