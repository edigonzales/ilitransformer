# Befund: Gueltige und projektierte Liegenschaften im DMAV-zu-DM01-Full-Run

## Ausgangslage

Im DMAV-Modell ist die Sicht `Grundstueck_Gueltig` enger definiert als die fruehere Filterbedingung im Rueckwaertsprofil. Relevant ist nicht nur, dass ein `Grundstueck` eine nicht-fiktive `Liegenschaft` ist. Zwingend ist auch der Status der referenzierten Nachfuehrungen:

```ili
WHERE DEFINED(Grundstueck->Entstehung)
  AND DEFINED(Grundstueck->Entstehung->Grundbucheintrag)
  AND (
    NOT(DEFINED(Grundstueck->Untergang))
    OR NOT(DEFINED(Grundstueck->Untergang->Grundbucheintrag))
  );
```

Die fruehere Profilbedingung fuer `grundstueck-gueltig` war nur:

```yaml
where: "gs.Grundstuecksart == #Liegenschaft AND gs.Fiktiv == false"
```

Damit wurden auch Grundstuecke als rechtsgueltige DM01-`Grundstueck`/`Liegenschaft` transformiert, deren Entstehungsnachfuehrung noch keinen `Grundbucheintrag` hat. Das ist fuer `Grundstueck_Gueltig` falsch.

## Implementierte Bedingungen

Die Modellregel ist im Profil ueber `lookupIn(...)` umgesetzt, weil die Expression-DSL die Pfade `gs.Entstehung->Grundbucheintrag` und `gs.Untergang->Grundbucheintrag` nicht direkt als Objektpfad auswertet.

Rechtsgueltige Grundstuecke muessen sinngemaess diese Bedingung erfuellen:

```text
gs.Fiktiv == false
and defined(gs.Entstehung)
and defined(lookupIn(
  "dmav",
  "DMAV_Grundstuecke_V1_1.Grundstuecke.GSNachfuehrung",
  "__objectOid",
  oid(gs.Entstehung),
  "Grundbucheintrag"
))
and (
  notDefined(gs.Untergang)
  or notDefined(lookupIn(
    "dmav",
    "DMAV_Grundstuecke_V1_1.Grundstuecke.GSNachfuehrung",
    "__objectOid",
    oid(gs.Untergang),
    "Grundbucheintrag"
  ))
)
```

Projektierte Grundstuecke muessen sinngemaess diese Bedingung erfuellen:

```text
gs.Fiktiv == false
and defined(gs.Entstehung)
and notDefined(lookupIn(
  "dmav",
  "DMAV_Grundstuecke_V1_1.Grundstuecke.GSNachfuehrung",
  "__objectOid",
  oid(gs.Entstehung),
  "Grundbucheintrag"
))
and notDefined(gs.Untergang)
```

Diese Logik gilt fuer `Liegenschaft`, `SelbstaendigesDauerndesRecht` und `Bergwerk`. Die Child-Regeln joinen jeweils auf das zugehoerige `Grundstueck`, damit keine Child-Objekte ohne passenden Parent entstehen.

## Ergebnis nach der Implementierung

Die Transformation laeuft mit den neuen Bedingungen durch. Die fuenf frueher faelschlich rechtsgueltig transformierten Liegenschaften werden jetzt als projektierte DM01-Objekte geschrieben.

| DM01-Zielklasse | Vorher | Nachher | Differenz |
|---|---:|---:|---:|
| `DM01AVCH24LV95D.Liegenschaften.Grundstueck` | 1468 | 1463 | -5 |
| `DM01AVCH24LV95D.Liegenschaften.Liegenschaft` | 1458 | 1453 | -5 |
| `DM01AVCH24LV95D.Liegenschaften.ProjGrundstueck` | 0 | 5 | +5 |
| `DM01AVCH24LV95D.Liegenschaften.ProjLiegenschaft` | 0 | 5 | +5 |

Die relevanten Regelzaehler aus dem Full-Run bestaetigen die Zuordnung:

| Regel | Matches | Targets |
|---|---:|---:|
| `gs-grundstueck-gueltig` | 1451 | 1451 |
| `gs-liegenschaft` | 1453 | 1453 |
| `gs-proj-grundstueck` | 5 | 5 |
| `gs-proj-liegenschaft` | 5 | 5 |
| `gs-selbstrecht-grundstueck` | 12 | 12 |
| `gs-selbstrecht` | 12 | 12 |
| `gs-bergwerk-grundstueck` | 0 | 0 |
| `gs-bergwerk` | 0 | 0 |
| `gs-proj-selbstrecht-grundstueck` | 0 | 0 |
| `gs-proj-selbstrecht` | 0 | 0 |
| `gs-proj-bergwerk-grundstueck` | 0 | 0 |
| `gs-proj-bergwerk` | 0 | 0 |

## Betroffene Liegenschaften

Diese fuenf DMAV-Liegenschaften haben `Fiktiv=false`, eine definierte `Entstehung`, keinen `Untergang`, aber keinen `Grundbucheintrag` auf der Entstehungsnachfuehrung. Sie sind deshalb nicht rechtsgueltig, sondern projektiert zu transformieren.

| Grundstueck TID | NBIdent | Nummer | EGRID | Entstehung | NF Identifikator | Grundbucheintrag | Untergang | Liegenschaft TID | Flaechenmass |
|---|---|---:|---|---|---|---|---|---|---:|
| `4778b766-3539-4f90-9ff1-74ec82afd573` | `BE0200000115` | 533 | `CH273542614644` | `71e7ff5e-3d2a-4719-a3cd-4743d3248098` | `202300005` | fehlt | fehlt | `18266512-d8de-4753-93ac-93404ed5cdb9` | 5839 |
| `5668dd79-1dfa-421f-9e6e-667714d6141c` | `BE0200000115` | 642 | `CH528461643618` | `71e7ff5e-3d2a-4719-a3cd-4743d3248098` | `202300005` | fehlt | fehlt | `7837bbca-001c-42fc-9691-d57b56d0b339` | 1444 |
| `c71c0691-9fc9-4a5c-9ec7-c67daf94fe59` | `BE0200000115` | 643 | `CH536484366138` | `71e7ff5e-3d2a-4719-a3cd-4743d3248098` | `202300005` | fehlt | fehlt | `d9946a86-4c55-44ce-851e-53454e85bc98` | 946 |
| `b44d9ae2-ff0e-4c39-baed-e46993f1b477` | `BE0200000108` | 739 | `CH424025463567` | `2aaf62ab-0283-4cb4-9d52-f2b5c7e3a84e` | `202410001` | fehlt | fehlt | `42f62faa-3c8d-496c-8c7a-befdaa872060` | 943467 |
| `c40bf458-ebb5-40ca-8cb7-c9122f0227a4` | `BE0200000108` | 744 | `CH464046352582` | `2aaf62ab-0283-4cb4-9d52-f2b5c7e3a84e` | `202410001` | fehlt | fehlt | `e6d7b5d3-1773-4dc3-bf28-5e5eb888328d` | 98023 |

## Offener Befund

Die Profilkorrektur entfernt die fruehere Doppelbelegung als rechtsgueltige DM01-AREA-Objekte. Der Full-Run scheitert aber weiterhin in der DM01-Validierung mit:

```text
DM01AVCH24LV95D.Liegenschaften.Liegenschaft.Geometrie:
no area-ref to polygon of lines 3049, 3135, ...
```

Damit ist die Statusableitung nicht mehr der unmittelbare Fehler. Offen bleibt die topologische AREA-Frage: Die erzeugte DM01-`Liegenschaft.Geometrie` enthaelt mindestens eine durch Linien gebildete Flaeche ohne `Liegenschaft`-Area-Referenz. Das muss separat analysiert werden, etwa ob die Linien aus der DMAV-Liegenschaftsgeometrie fuer rechtsgueltige und projektierte Flaechen getrennt oder anders in DM01-AREA-Strukturen geschrieben werden muessen.

## Nachweis

Ausgefuehrte Pruefungen:

```bash
java -jar /Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar --modeldir "src/test/data/av/models/;https://models.interlis.ch;https://models.geo.admin.ch;https://models.kgk-cgc.ch" --models DMAVTYM_Alles_V1_1 src/test/data/DMAV_Version_1_1/DMAVTYM_Alles_V1_1.xtf
```

```bash
./gradlew runDm01DmavFullRun -Pdm01DmavFullRunManifest=products/dm01-dmav/full-runs/dmav-tym-alles-v1-1/manifest.yaml
```

Ergebnis:

- DMAV-Quelle: gueltig.
- Transformation: erfolgreich, 0 Transformationsfehler.
- DM01-Validierung: fehlgeschlagen mit `no area-ref to polygon`.
- Transformationsreport: `20472` Source-Records gelesen, `23580` Targets geschrieben, `5` `ProjGrundstueck` und `5` `ProjLiegenschaft`.
