# 08 – associations-refs (Joins / Lookups)

Beziehungen zwischen Objekten: Associations/`ref`-Blöcke, Equi-Joins und die
Lookup-Funktionen.

## Abgedeckte INTERLIS-Elemente

- `ASSOCIATION` mit Rollen (`Mutation -- {1} ZielMutation; betroffenes_Gebaeude -- {0..*} ZielGebaeude;`)
- eingebettete Referenz (`<Mutation ili:ref="...">` im Zielobjekt)
- mehrere Quellklassen in einer Rule

## Abgedeckte Expressions / DSL

- `ref`-Block: `association` / `role` / `required` / `target rule ... sourceRef ...`
- `join inner g to q on eq(g.QualNummer, q.QNummer)`
- `lookup(classPath, keyAttr, keyValue, returnAttr)`
- `lookupIn(inputId, classPath, keyAttr, keyValue, returnAttr)`
- `existsIn(inputId, classPath, keyAttr, keyValue)`
- `oid(alias)` – OID des Quellobjekts
- `identity` + `oid { strategy deterministicUuid; }`

## Wie wird `ref` aufgelöst?

`sourceRef g.MutationRef` liefert die OID des referenzierten Quellobjekts. Die Engine
sucht das Zielobjekt, das die `target rule ziel-mutation` aus genau diesem Quellobjekt
erzeugt hat, und schreibt dessen Ziel-OID als eingebettete Referenz. Damit das
zuverlässig deterministisch ist, wird `oid deterministicUuid` mit `identity` verwendet.

## Wichtig

- Ein `ref` mit `required` setzt eine Rollen-Kardinalität `{1}` (nicht `{0..1}`) im
  Modell voraus, sonst meldet der Compiler `Reference ... is optional in model but ref
  is marked required`.
- Pro Rule ist **maximal ein** `join` erlaubt.

## Aufrufe

```bash
demo/validate.sh demo/08-associations-refs
```

## Erwartetes Ergebnis

4 Zielobjekte (2 `ZielMutation`, 2 `ZielGebaeude`). Jedes `ZielGebaeude` trägt eine
eingebettete `Mutation`-Referenz auf die passende `ZielMutation`, `Qualitaetsstandard`
aus dem Join sowie identische Werte via `lookup`/`lookupIn` und `HatQualitaet=true`
via `existsIn`.
