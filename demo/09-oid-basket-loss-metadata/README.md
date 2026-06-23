# 09 – oid-basket-loss-metadata

OID- und Basket-Strategien sowie die dokumentarischen DSL-Blöcke `loss`, `metadata`
und `create`.

## Abgedeckte INTERLIS-Elemente

- zwei `TOPIC`s in einem Modell (`Quelle` → `Ziel`)
- `BASKET OID AS INTERLIS.UUIDOID` / `OID AS INTERLIS.UUIDOID`
- mehrere Zielklassen (`Zielpunkt`, `Protokoll`)

## Abgedeckte DSL-Elemente

- `oid { strategy deterministicUuid; namespace "..."; }` – reproduzierbare OIDs
- `identity p.Nummer` – stabiler Quellschlüssel für deterministische OIDs
- `basket byTopic` – Ausgabe-Baskets nach Topic gruppiert
- `loss { sourcePath ...; reasonCode ...; description ...; when ...; }` – dokumentierter
  Informationsverlust
- `metadata { direction ...; roundtrip ...; lossiness ...; }`
- `create class "..." { assign { ... } }` – zusätzliche Zielobjekte pro Quellobjekt
- `default(p.AltAttribut, "kein Hinweis")`

## Hinweise

- `deterministicUuid` erzeugt bei gleichem Input/`namespace`/`identity` **stabile**
  OIDs → für Golden-Tests geeignet (im Gegensatz zu `uuid`).
- `loss` und `metadata` beeinflussen die Ausgabe nicht; sie dokumentieren Verhalten
  und werden in `JobConfig` abgelegt.
- `create` ist laut DSL-Doku experimentell; hier auf einfache `assign`-Zuweisungen
  beschränkt.

## Aufrufe

```bash
demo/validate.sh demo/09-oid-basket-loss-metadata
```

## Erwartetes Ergebnis

Aus 2 `Punkt` entstehen 4 Zielobjekte in einem `Ziel`-Basket: 2 `Zielpunkt`
(P1/Wert=500, P2/Wert=200) und 2 `Protokoll` aus dem `create`-Block
(`Legacy-Info-A` bzw. `kein Hinweis`). Die OIDs sind deterministisch.
