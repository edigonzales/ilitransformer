# DM01 ↔ DMAV: Offene fachliche Fragen

Stand: Phase 14.

## Korrelationstabelle

1. Bezieht sich die XLSX auf DMAV Version 1.0 oder 1.1? Der Dateiname/Stand ist 2026, aber die Tabelle nennt teilweise „DMAV Version 1.0".
2. Sind die Transformationscodes vollständig dokumentiert? In der Erklärung werden `K` und `V` erwähnt; in den Daten können weitere Muster vorkommen.
3. Welche Spalten sind fachlich verbindlich und welche nur dokumentativ?
4. Soll das versteckte Sheet `Korrelation` (1197 Zeilen) als zusätzliche Quelle geparst werden?
5. Wie werden Mehrfachziele in einer Zelle formal interpretiert?
6. Sollen zusammengeführte Zellen (merged cells) aufgelöst werden?
7. Soll der Import bei künftigen Änderungen der XLSX-Spaltenstruktur über Header-Namen statt Indizes robuster werden?

## DM01 → DMAV

1. Welche Defaultwerte sind fachlich zulässig, wenn DMAV Mandatory-Attribute verlangt, DM01 aber keine Quelle hat?
2. Wie wird `GueltigerEintrag` gesetzt, wenn DM01 nur `Datum1` oder gar kein Datum hat?
3. Welche Objekte gelten als aktiv/gültig, wenn DM01 `Status`/`Gueltigkeit` anders modelliert?
4. Wie werden projektierte Objekte abgebildet?
5. Welche DM01-Objekte müssen in mehrere DMAV-Modelle kopiert werden?
6. Wie wird verhindert, dass identische Grenz-/Fixpunkte doppelt erzeugt werden?
7. Ist `AktiverUnterhalt = true` fachlich zulässig als Default?
8. Wie ist `Grenzpunktfunktion` zu bestimmen, wenn derselbe Punkt auch Grenz-/Hoheitsgrenzpunkt ist?
9. Muss LFP3 gleichzeitig nach `Grundstuecke.Grenzpunkt` kopiert werden, wenn geometrisch identisch mit Grenzpunkt/Hoheitsgrenzpunkt?
10. Wie wird `IstHoheitsgrenzsteinAlt` fachlich bestimmt?
11. Wie soll `Protokoll` abgebildet werden?
12. Was ist Default für DM01 `Protokoll`?

## DMAV → DM01

1. Welche DMAV-Informationen gehen beim Rückweg verloren?
2. Wie werden BAG OF STRUCTURE in DM01-Pos-/Symboltabellen zurückgeführt, wenn DM01 weniger Kardinalität erlaubt?
3. Wie werden DMAV Associations in DM01-Referenzen zurückgeführt?
4. Wie werden DMAV UUID-OIDs in DM01-ITF-OIDs übersetzt?
5. Ist Roundtrip ein Ziel oder nur fachlich plausible Rücktransformation?
6. Werden DMAV-Objekte mit `Grenzpunktfunktion != keine` zusätzlich nach DM01 `Gemeindegrenzen.Hoheitsgrenzpunkt` oder `Liegenschaften.Grenzpunkt` geschrieben?
7. Wie wird entschieden, welche DMAV-Objekte im Rückweg überhaupt in DM01-Fixpunkte gehen?

## Geometrie und Topologie

1. Wie sollen AREA/SURFACE-Unterschiede behandelt werden?
2. Müssen Flächentopologien repariert oder nur validiert werden?
3. Wie werden streitige Grenzen/LINEATTR in beide Richtungen behandelt?
4. Wie werden Kurven/ARCS erhalten?
5. Welche Toleranzen gelten bei geometrischer Identität?

## Tooling / Architektur

1. Soll `ilivalidator` als Java-Library oder externer Prozess eingebunden werden? (Aktuell: Java-Library)
2. Sollen Modellfiles aus dem Internet geladen oder lokal vendored werden?
3. Soll der Mapping-Generator generierte YAMLs ins Repo schreiben oder nur nach `build/generated`?
4. Welche Java-Version ist langfristig Ziel: 21 LTS oder 25?
5. Soll das Package `guru.interlis.ilinexus` → `guru.interlis.transformer` umbenannt werden? (Fertiger Code ist bereits `guru.interlis.transformer`)
