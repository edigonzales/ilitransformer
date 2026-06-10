# Phase 27: DM01 ITF-Extraktionsproblem - Korrektur

## Ziel von Phase 27

Phase 27 (`Reale Datensatzinventarisierung und fachlich zusammenhängende Testausschnitte`) erzeugt aus den beiden vollständigen realen Datensätzen im Verzeichnis `src/test/data/DMAV_Version_1_1/` kleine, fachlich zusammenhängende und validierte LFP3-Testfixtures:

| Datensatz | Format | Ziel-Fixture |
|-----------|--------|--------------|
| `DM01-AV-CH.itf` | ITF (INTERLIS 1) | `src/test/resources/real-dm01-dmav/lfp3/dm01-input.itf` |
| `DMAVTYM_Alles_V1_1.xtf` | XTF (INTERLIS 2.4) | `src/test/resources/real-dm01-dmav/lfp3/dmav-input.xtf` |

Der `ConnectedSubgraphExtractor` liest den Quell-Transfer, indexiert alle Objekte, baut einen Referenzgraphen, selektiert LFP3-Seed-Objekte, expandiert per BFS entlang der Referenzen und schreibt die extrahierten Objekte wieder als Transfer.

## Ursprünglicher Fehler

Die DM01-Extraktion scheiterte beim Schreiben mit:

```text
ch.interlis.iox.IoxException: unknown ITF table LFP3Nachfuehrung_Perimeter
    at ch.interlis.iom_j.itf.ItfWriter.write(ItfWriter.java:292)
    at guru.interlis.transformer.geometry.ItfGeometryWriter.writeBufferedTable(ItfGeometryWriter.java:422)
    at guru.interlis.transformer.geometry.ItfGeometryWriter.flushBufferedObjects(ItfGeometryWriter.java:410)
```

Die erste Analyse vermutete ein grundsätzliches Problem beim Rückschreiben von INTERLIS-1-Geometrie-Hilfstabellen. Das war zu breit gefasst.

## Tatsächliche Ursache

`ItfReader2` liefert reale DM01-ITF-Baskets als `ch.interlis.iox_j.StartBasketEvent` mit:

- `getType() == "DM01AVCH24LV95D.FixpunkteKategorie3"`
- `getTopicv() == null`

Der `ConnectedSubgraphExtractor` speicherte nur `getTopicv()`. Dadurch ging der tatsächliche Basket-Typ verloren. Beim Schreiben fiel der Extractor auf das erste Topic des Modells zurück (`FixpunkteKategorie1`) und schrieb LFP3-Objekte in einem falschen Basket-Kontext.

`ItfGeometryWriter` erzeugte danach korrekt die Hilfstabelle `LFP3Nachfuehrung_Perimeter`, aber der darunterliegende `ItfWriter` hatte wegen des falschen Basket-Typs die ITF-Tabellenliste von `FixpunkteKategorie1` geöffnet. In dieser Tabellenliste gibt es `LFP3Nachfuehrung_Perimeter` nicht.

## Korrektur

Der Extractor bewahrt nun den writer-tauglichen Basket-Typ:

1. `StartBasketEvent.getType()` verwenden, wenn das Event `ch.interlis.iox_j.StartBasketEvent` ist und einen Typ enthält.
2. Sonst auf `getTopicv()` zurückfallen.
3. Nur ohne Basket-Typ den bisherigen Default-Topic-Fallback verwenden.
4. Beim Schreiben Baskets nach `basketId + basketType` gruppieren und mit dem ursprünglichen Basket-Typ öffnen.

Damit wird die DM01-LFP3-Teilmenge wieder unter `DM01AVCH24LV95D.FixpunkteKategorie3` geschrieben. `ItfGeometryWriter` kann die INTERLIS-1-Geometrie-Hilfstabelle `LFP3Nachfuehrung_Perimeter` normal schreiben.

## Ergebnis

- `dm01-input.itf` wird aus `DM01-AV-CH.itf` extrahiert.
- Die Fixture enthält `TOPI FixpunkteKategorie3`.
- Die Fixture enthält `TABL LFP3Nachfuehrung_Perimeter`.
- Die Fixture wird mit `ilivalidator` gegen `DM01AVCH24LV95D` validiert.

## Verwandte Code-Stellen

| Klasse | Rolle |
|--------|-------|
| `ConnectedSubgraphExtractor.readAll()` | liest und normalisiert den Basket-Typ |
| `ConnectedSubgraphExtractor.writeExtracted()` | schreibt nach Basket-ID und Basket-Typ gruppiert |
| `ItfGeometryWriter` | zerlegt `SURFACE`/`AREA` in INTERLIS-1-Hilfstabellen |
| `ItfReader2` | liest ITF und liefert den relevanten Basket-Typ über `getType()` |
