# 10 – sia405-lkmap-komposition

Dieses Demo bildet den relevanten SIA-405-LKMap-Fall nach: `LKObjekt` ist
abstrakt, `LKFlaeche`, `LKLinie` und `LKPunkt` sind konkrete Unterklassen und
`LKObjekt_Text` ist über eine Komposition an ein beliebiges `LKObjekt` gebunden.

## Was wird geprüft?

- strukturell gleiche Quell- und Zielmodelle als vereinfachte 2015/2025-Modelle,
- OID-Übernahme mit `oid { strategy preserve; }`,
- drei konkrete 1:1-Regeln für die LKObjekt-Unterklassen,
- Wiederherstellung der Komposition über die Child-Rolle `LKObjektRef`,
- polymorphe Referenzen von `LKObjekt_Text` auf `LKFlaeche`, `LKLinie` und `LKPunkt`.

`oid(t)` wäre hier falsch: Es liefert die OID des Textobjekts. Die Parent-OID
kommt aus `t.LKObjektRef`. Die drei Textregeln verwenden `existsIn` zur Auswahl
der konkreten Parent-Klasse und verweisen anschliessend mit `sourceRef
t.LKObjektRef` auf die jeweilige Parent-Regel.

## Aufrufe

```bash
demo/validate.sh demo/10-sia405-lkmap-komposition
```

Oder einzeln:

```bash
cd demo/10-sia405-lkmap-komposition
../../bin/ilitransformer validate-mapping -m profile.ilimap
../../bin/ilitransformer transform -m profile.ilimap
```

## Erwartetes Ergebnis

Die Ausgabe enthält sechs Objekte: drei LKObjekte und drei Textobjekte. Jeder
Text verweist über `LKObjektRef` auf das zugehörige konkrete Parentobjekt; die
Quell-OIDs werden unverändert übernommen.
