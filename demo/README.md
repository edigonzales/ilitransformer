# ilinexus Demo-Suite

Eine Sammlung von 9 in sich geschlossenen Beispielen für die modellbewusste
INTERLIS-Transformation mit der `.ilimap`-DSL (v2). Jedes Beispiel hat ein eigenes
INTERLIS-2.4-Modell, eigene Transferdaten, ein Mapping-Profil und ein README mit allen
Aufrufen. Die Beispiele steigern sich von sehr einfach (01) bis anspruchsvoll (09) und
decken zusammen einen Grossteil der INTERLIS-Sprachelemente und der Expression-Sprache ab.

Alle Modelle wurden mit `ili2c` kompiliert, alle Ein- und Ausgabedaten mit
`ilivalidator` validiert und alle Transformationen tatsächlich ausgeführt.

## Übersicht

| # | Beispiel | INTERLIS-Schwerpunkt | Expression-/DSL-Schwerpunkt |
|---|---|---|---|
| 01 | [hello-copy](01-hello-copy/) | `TEXT`, num. Bereich, `BOOLEAN`, `MANDATORY` | direkte Kopie, `trim`, `upper` |
| 02 | [string-functions](02-string-functions/) | optionale `TEXT*N` | `upper`, `lower`, `substring`, `truncate`, `replace`, `trim`, `default` |
| 03 | [numbers-units](03-numbers-units/) | `UNIT`, `DOMAIN`, `[m]` | `div`, `mul`, `add`, `sub`, `round`, `abs`, `min`, `max`, `toNumber` |
| 04 | [enumerations](04-enumerations/) | hierarchische `DOMAIN`-Aufzählungen | `enumMap`, `enumMapDefault`, `enumMapStrict`, `enumDefault`, `enumName`, `#Literal` |
| 05 | [conditionals-nulls](05-conditionals-nulls/) | optionale Attribute, Filter | `if`, `coalesce`, `default`, `defined`, `notDefined`, `and/or/not`, Vergleiche |
| 06 | [dates](06-dates/) | `XMLDate`, `XMLDateTime` | `toXmlDateTime`, `toDate`, `toInterlis1Date`, `now`, `defaults` |
| 07 | [bags-structures](07-bags-structures/) | `STRUCTURE`, `BAG OF`, `LIST OF`, `SURFACE`, `COORD` | `bag`/`parentRef`, synth. Bag, `bagFirst`, `pointOnSurface`, `coordEquals` |
| 08 | [associations-refs](08-associations-refs/) | `ASSOCIATION` + Rollen, Referenzen | `ref`, `join`, `lookup`, `lookupIn`, `existsIn`, `oid` |
| 09 | [oid-basket-loss-metadata](09-oid-basket-loss-metadata/) | 2 Topics, OID/Basket | `oid`/`basket`-Strategien, `loss`, `metadata`, `create`, `identity` |

## Voraussetzungen

Die CLI liegt in der Distribution unter `bin/ilitransformer`.

Werkzeuge (Pfade ggf. anpassen, siehe auch `demo/validate.sh`):

```bash
export CLI=bin/ilitransformer
export ILI2C=/pfad/zu/ili2c.jar
export ILIVALIDATOR=/pfad/zu/ilivalidator.jar
```

## Schnellstart

Ein einzelnes Beispiel komplett validieren (Modell → Eingabe → Transform → Ausgabe):

```bash
demo/validate.sh demo/01-hello-copy
```

Alle Beispiele auf einmal:

```bash
for d in demo/[0-9]*/; do demo/validate.sh "$d"; done
```

Nur transformieren (aus dem jeweiligen Beispielverzeichnis):

```bash
cd demo/01-hello-copy
  ../../bin/ilitransformer validate-mapping -m profile.ilimap
  ../../bin/ilitransformer transform -m profile.ilimap
```

## Aufbau eines Beispiels

```
demo/NN-name/
  models/<name>.ili    # INTERLIS-2.4-Modell (Quell- und Zielklassen)
  data/input.xtf       # Eingabe-Transferdaten
  profile.ilimap       # Mapping-Profil (.ilimap v2)
  output.xtf           # Ergebnis (wird beim Transform erzeugt)
  README.md            # Zweck, abgedeckte Elemente, Aufrufe
```

## Gesammelte Erkenntnisse / Stolpersteine

Diese Punkte wurden beim Erstellen der Demos verifiziert und gelten für die aktuelle
Build-Version:

- **INTERLIS-2.4-XTF-Format**: Datensektion mit Modell-Namespace-Präfix
  (`<Model:Topic ili:bid="...">`, `<Model:Class ili:tid="...">`, `<Model:Attr>`),
  Geometrie über den `geom`-Namespace. Das `ili:`-präfixierte Punktformat
  (`<ili:Model.Topic.Class>`) ist **nicht** lesbar.
- **OID-Syntax**: `oid { strategy <preserve|integer|uuid|deterministicUuid>; namespace "..."; }`.
  Die in `docs/ilimap-v2.md` skizzierte Kurzform `oid uuid;` wird vom Parser abgelehnt.
- **UUIDOID-Modelle** brauchen `strategy uuid` oder `deterministicUuid`; die
  Default-Strategie `integer` ist mit `UUIDOID`-Zielen inkompatibel.
- **`concat(...)`** ist als 0-stellige Funktion registriert und kann **nicht** mit
  Argumenten aufgerufen werden – in den Demos nicht verwendet.
- **Numerische Vergleiche**: Quell-Numerik kommt als Text an; `s.Wert >= 500` bricht ab.
  Operanden mit `toNumber(...)` vereinheitlichen (siehe 05).
- **Enum-Maps**: Map-Name nur in `enumMap` als Symbol; `enumMapStrict`/`enumMapDefault`
  brauchen ihn als String. Punktierte `#a.b`-Literale sind in `enum`-Blöcken nicht
  erlaubt → als String schreiben (`"x" => "befestigt.Strasse"`). Für `BOOLEAN`-Ziele
  `enumMap` verwenden (siehe 04).
- **`now()`** liefert Mikrosekunden → kein gültiges `INTERLIS.XMLDateTime`; im Demo als
  `TEXT` geführt. `compileMode compatible` nötig (Non-Determinismus, siehe 06).
- **`parentRef`** verknüpft Kind und Parent über **Wertgleichheit** mit der Parent-OID;
  ein einfaches Textattribut genügt (siehe 07).
- **`ref required`** verlangt eine `{1}`-Rollen-Kardinalität im Modell (siehe 08).
- **`bagFirst`** erwartet den Alias als String-Literal (siehe 07).
