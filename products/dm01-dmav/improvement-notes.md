# DM01/DMAV: Hergeleitete Verbesserungsvorschlaege

Dieses Dokument sammelt Verbesserungsvorschlaege, die aus konkreten
Full-Run-Beobachtungen, Reports, Warnungen oder Nutzerfragen abgeleitet sind.
Jeder Eintrag nennt den Ausloeser und die Herleitung, damit spaeter klar bleibt,
warum ein Vorschlag existiert und welches Problem er loesen soll.

## Statuswerte

| Status | Bedeutung |
|---|---|
| `idea` | Beobachtet und als moegliche Verbesserung beschrieben. |
| `planned` | Zur Umsetzung vorgesehen, aber noch nicht implementiert. |
| `implemented` | Umgesetzt und verifiziert. |
| `rejected` | Bewusst verworfen; Begruendung im Eintrag. |

## Lookup-Warnungen fuer optionale Child-Objekte

**Status:** `idea`

### Ausloeser

Im SO-2546-Full-Run erscheinen Warnungen wie:

```text
ILITRF-LOOKUP-NO-MATCH:
lookup() found no match for
DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Symbol.LFP3Symbol_von=1074
```

und analog fuer `LFP3Symbol_von=1075`, jeweils in der Regel `lfp3-lfp3`.

### Herleitung

Die LFP3-Regel setzt `SymbolOri` ueber einen Lookup auf die optionale
DM01-Symboltabelle:

```ilimap
SymbolOri = lookup(
  'DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Symbol',
  'LFP3Symbol_von',
  oid(p),
  'Ori'
);
```

Die SO-2546-Quelle enthaelt die LFP3-Objekte `1074` und `1075`, aber kein
passendes `LFP3Symbol`-Objekt, dessen `LFP3Symbol_von` auf diese OIDs zeigt.
Die Tabelle `LFP3Symbol` ist im Datensatz nicht leer; die konkreten Child-
Objekte fehlen lediglich fuer diese beiden LFP3-Punkte.

### Problem

Das aktuelle `lookup()` meldet jeden fehlenden Treffer als Warnung. Das ist
sinnvoll, wenn eine Beziehung fachlich erwartet wird. Bei optionalen 0..1-Child-
Tabellen wie Symboltabellen kann das Fehlen aber ein normaler Datenzustand sein.
Solche erwartbaren Faelle erhoehen die Warning-Zahl und erschweren die Triage
echter Daten- oder Mapping-Probleme.

### Vorschlag

Eine explizite optionale Lookup-Semantik einfuehren:

- `lookupOptional(classPath, keyAttr, keyValue, returnAttr)`

Diese Variante soll bei fehlendem Treffer `null` liefern, aber keine
`ILITRF-LOOKUP-NO-MATCH`-Warnung erzeugen. Unerwartete oder verpflichtende
Beziehungen sollen weiterhin das bestehende `lookup()` mit Warnung verwenden.

### Nutzen

- Full-Run-Reports werden aussagekraeftiger.
- Echte Missing-Reference-Probleme gehen weniger in erwartbaren optionalen
  Lookup-Warnungen unter.
- Mapping-Autoren koennen fachlich sichtbar zwischen erwarteter und optionaler
  Beziehung unterscheiden.

### Risiko / offene Frage

Nicht alle Lookup-Verwendungen duerfen pauschal optional werden. Die Umstellung
bleibt deshalb auf fachlich optionale Symbol-, Nummern- und EGID-Child-Tabellen
in den DM01/DMAV-Profilen begrenzt.

## Warnende Lookup-Duplikatfilter fuer GEMNachfuehrung

**Status:** `implemented`

### Ausloeser

Im SO-2546-Full-Run erscheinen Warnungen wie:

```text
ILITRF-LOOKUP-NO-MATCH:
lookupIn() found no match for
DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung.Identifikator=Gemeindeimp
```

analog fuer `Identifikator=NF9` und `Identifikator=NULLMUT`, jeweils in der
Regel `gs-gs-nachfuehrung-gem-gueltig`.

### Herleitung

DM01 modelliert Hoheitsgrenzpunkte im Topic `Gemeindegrenzen`. Diese Objekte
verweisen mit `Hoheitsgrenzpunkt.Entstehung` auf
`Gemeindegrenzen.GEMNachfuehrung`. DMAV legt Hoheitsgrenzpunkte im Thema
`Grundstuecke` als `Grenzpunkt` ab; deren Entstehung zeigt auf
`Grundstuecke.GSNachfuehrung`. Deshalb muessen bestimmte
`GEMNachfuehrung`-Objekte als `GSNachfuehrung` ins Ziel uebernommen werden.

Die Regel prueft heute mit:

```ilimap
coalesce(
  lookupIn(
    'dm01',
    'DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung',
    'Identifikator',
    gem.Identifikator,
    'NBIdent'
  ),
  ''
) != gem.NBIdent
```

ob bereits eine passende `LSNachfuehrung` mit demselben `Identifikator` und
`NBIdent` existiert. In SO-2546 sind die betroffenen Daten:

```text
GEMNachfuehrung:
OBJE 16 SO0200002546 Gemeindeimp ...
OBJE 23 SO0200002546 NF9 ...
OBJE 24 SO0200002546 NULLMUT ...

LSNachfuehrung:
keine Eintraege mit Identifikator Gemeindeimp, NF9 oder NULLMUT
```

Der Kontrollfall `NULLMUTATION` existiert dagegen in beiden Tabellen:

```text
GEMNachfuehrung:
OBJE 25 SO0200002546 NULLMUTATION ...

LSNachfuehrung:
OBJE 251 SO0200002546 NULLMUTATION ...
```

`NULLMUTATION` wird deshalb nicht doppelt aus `GEMNachfuehrung` uebernommen.
Fuer `NF9` gibt es in SO-2546 zudem Hoheitsgrenzpunkte, deren `Entstehung` auf
die `GEMNachfuehrung` mit OID `23` zeigt.

### Problem

Der fehlende `LSNachfuehrung`-Treffer ist hier kein fehlendes Child-Objekt,
sondern das erwartete Ergebnis einer Duplikatpruefung. `lookupIn()` erzeugt
trotzdem `ILITRF-LOOKUP-NO-MATCH`. Der Report klingt dadurch nach einem
Datenproblem, obwohl die Regel den Negativtreffer bewusst als
Filterbedingung nutzt.

### Vorschlag

Den Duplikatfilter als stille Existenzpruefung ausdruecken:

```ilimap
not(existsIn(
  'dm01',
  'DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung',
  'Identifikator',
  gem.Identifikator,
  'NBIdent',
  gem.NBIdent
))
```

`existsIn()` ist dafuer passender als `lookupIn()`, weil die Regel keinen Wert
uebernehmen will, sondern nur wissen muss, ob ein fachlich gleicher
LS-Nachfuehrungseintrag existiert.

### Nutzen

- Der Report verliert erwartbare Lookup-Warnungen aus reinen Filterausdruecken.
- Die Mapping-Absicht wird lesbarer: Existenzpruefung statt Wert-Lookup.
- Echte `LOOKUP_NO_MATCH`-Faelle bleiben besser sichtbar.

### Risiko / offene Frage

Die Umstellung muss zeigen, dass die Zielanzahlen stabil bleiben. Insbesondere
duerfen `GEMNachfuehrung`-Objekte mit passendem `LSNachfuehrung`-Gegenstueck
weiterhin nicht doppelt als `GSNachfuehrung` entstehen.

## Lookup-Warnungen nach Ursache klassifizieren

**Status:** `implemented`

### Ausloeser

Die normalisierten Full-Run-Summaries enthalten viele
`ILITRF-LOOKUP-NO-MATCH`-Warnungen:

- SO-2546: `12908` Warnungen insgesamt
- SO-2549: `204` Warnungen insgesamt

In SO-2546 verteilen sie sich unter anderem auf Regeln wie
`eo-einzelobjekt-gueltig`, `gs-grenzpunkt-gueltig` und `lfp3-lfp3`.

### Herleitung

Alle fehlenden Lookup-Treffer verwenden aktuell denselben Diagnostic-Code. Der
Report zeigt dadurch zwar, dass ein Lookup keinen Treffer gefunden hat, aber
nicht, ob der Fall fachlich erwartbar, datenqualitaetsrelevant oder ein
Mapping-Problem ist.

### Problem

Ein einzelner Warning-Code fuer sehr unterschiedliche Ursachen ist zu grob fuer
produktive Full-Run-Acceptance. Die Anzahl kann stabil sein, ohne dass klar ist,
welche Warnungen akzeptiert sind und welche untersucht werden muessen.

### Vorschlag

Lookup-Warnungen nach Ursache oder Optionalitaetsgrund klassifizieren, zum
Beispiel:

- `optional-child-missing`
- `reference-data-missing`
- `unexpected-missing`

Die Klassifikation koennte im Mapping explizit gesetzt oder aus einer spaeteren
Lookup-Variante abgeleitet werden.

### Nutzen

- Fachliche Triage wird schneller.
- Acceptance-Kriterien koennen stabiler formuliert werden.
- Reports zeigen nicht nur die Anzahl, sondern auch die Bedeutung der Warnungen.

### Risiko / offene Frage

Die Klassifikation muss fachlich gepflegt werden. Falsch als optional
klassifizierte Lookups koennen reale Datenprobleme verdecken.

## Dokumentation fuer Pflicht- und optionale Lookups schaerfen

**Status:** `implemented`

### Ausloeser

`lookup()` ist in der Expression-Dokumentation als Convenience-Funktion
dokumentiert, die bei fehlendem Treffer `null` liefert und eine Warnung erzeugt.
In den DM01/DMAV-Profilen wird `lookup()` aber auch fuer fachlich optionale
0..1-Beziehungen genutzt, beispielsweise Symbol- oder Nummerntabellen.

### Herleitung

Die aktuelle Semantik ist technisch konsistent: fehlender Treffer bedeutet
Warnung. Fachlich gibt es aber zwei verschiedene Bedeutungen:

1. Eine erwartete Beziehung fehlt.
2. Ein optionales Child-Objekt ist nicht vorhanden.

Beide Faelle erscheinen heute gleich im Report.

### Problem

Mapping-Autoren haben kein dokumentiertes Mittel, ihre fachliche Absicht
auszudruecken. Dadurch ist aus dem Mapping nicht erkennbar, ob ein fehlender
Lookup ein Problem oder ein normaler optionaler Fall ist.

### Vorschlag

Nach Einfuehrung einer optionalen Lookup-Semantik die Dokumentation in
`docs/expressions.md` schaerfen:

- `lookup()` fuer erwartete oder datenqualitaetsrelevante Beziehungen
- `lookupOptional()` fuer optionale Child- oder Hilfstabellen
- Beispiele aus DM01/DMAV aufnehmen, etwa `LFP3Symbol` und andere
  Symbol-/Nummerntabellen

### Nutzen

- Profile werden selbsterklaerender.
- Neue Mapping-Autoren treffen bewusstere Entscheidungen.
- Diagnostic-Reports werden fachlich besser interpretierbar.

### Risiko / offene Frage

Die Dokumentation muss mit der registrierten `FunctionRegistry` synchron
bleiben, damit neue Funktionen nicht undokumentiert bleiben.
