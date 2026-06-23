# 07 – bags-structures (BAG / LIST OF, Geometrie)

Anspruchsvollstes Struktur-Beispiel: `BAG OF` und `LIST OF` Strukturen, ein
synthetischer Parent-Bag sowie Geometrie-Funktionen.

## Abgedeckte INTERLIS-Elemente

- `UNIT` + `DOMAIN ... = COORD ...` (LV95-Koordinatendomäne)
- `STRUCTURE` (mehrere)
- `SURFACE WITH (STRAIGHTS) VERTEX ... WITHOUT OVERLAPS`
- `BAG {0..*} OF`, `BAG {0..1} OF`
- `LIST {0..*} OF` (geordnet)
- eingebettete Quell-`BAG`

## Abgedeckte Expressions / DSL

- `bag` mit `from ... parentRef attribute "..." parent ...`
- synthetischer Parent-Bag (`bag` ohne `from`, mit `target` + `where`)
- `bagFirst("alias", "BagAttr", "ValueAttr")` – Alias als **String**
- `pointOnSurface(surface)` – innerer Punkt einer Fläche
- `coordEquals(coord1, coord2, tolerance)`
- `coalesce(...)` für fehlende Strukturattribute

## Wie funktioniert `parentRef`?

`parentRef attribute "ParzelleRef" parent s` verknüpft Kindobjekte mit dem Parent über
**Wertgleichheit**: das Kindattribut `ParzelleRef` muss die **OID (tid)** des
Parent-Objekts enthalten. Es ist **kein** INTERLIS-`->`-Verweis nötig – ein einfaches
Textattribut mit der Parent-tid genügt.

## Stolpersteine

- `bagFirst` erwartet den Quell-Alias als **String-Literal** (`"s"`), nicht als
  bare Alias.
- Geometrie-XTF folgt dem `geom`-Namespace
  (`<geom:surface><geom:exterior><geom:polyline><geom:coord>...`).

## Aufrufe

```bash
demo/validate.sh demo/07-bags-structures
```

## Erwartetes Ergebnis

Eine `ParzelleZiel` mit: kopierter SURFACE-Geometrie, `ErstesSchlagwort=Bauland`
(`bagFirst`), `Eigentuemer`-BAG mit 2 Einträgen (Bob mit `Anteil=0` via `coalesce`),
`Eckpunkte`-LIST mit 3 Punkten (`IstAufFlaeche=true` via `coordEquals`+`pointOnSurface`),
`Schwerpunkt`-BAG mit dem Flächen-Innenpunkt (synthetischer Bag).
