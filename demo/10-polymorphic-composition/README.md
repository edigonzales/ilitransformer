# 10 – polymorphic-composition

Dieses Demo zeigt eine polymorphe Kompositionsreferenz: `BaseObject` ist
abstrakt, `AreaObject`, `LineObject` und `PointObject` sind konkrete
Unterklassen und `ObjectText` ist über eine Komposition an ein beliebiges
`BaseObject` gebunden.

## Was wird geprüft?

- strukturell gleiche Quell- und Zielmodelle,
- OID-Übernahme mit `oid { strategy preserve; }`,
- drei konkrete 1:1-Regeln für die Unterklassen,
- Wiederherstellung der Komposition über die Child-Rolle `ParentRef`,
- polymorphe Referenzen von `ObjectText` auf `AreaObject`, `LineObject` und
  `PointObject`.

`oid(t)` wäre hier falsch: Es liefert die OID des Textobjekts. Die Parent-OID
kommt aus `t.ParentRef`. Die drei Textregeln verwenden `existsIn` zur Auswahl
der konkreten Parent-Klasse und verweisen anschliessend mit `sourceRef
t.ParentRef` auf die jeweilige Parent-Regel.

## Aufrufe

```bash
demo/validate.sh demo/10-polymorphic-composition
```

Oder einzeln:

```bash
cd demo/10-polymorphic-composition
../../bin/ilitransformer validate-mapping -m profile.ilimap
../../bin/ilitransformer transform -m profile.ilimap
```

## Erwartetes Ergebnis

Die Ausgabe enthält sechs Objekte: drei konkrete `BaseObject`-Unterklassen und
drei `ObjectText`-Objekte. Jeder Text verweist über `ParentRef` auf das
zugehörige konkrete Parentobjekt; die Quell-OIDs werden unverändert übernommen.
