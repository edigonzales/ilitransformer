# Befund: Gueltige und projektierte Liegenschaften im DMAV-zu-DM01-Full-Run

## Stand

Dieser Befund ersetzt die fruehere Zwischenanalyse, die `Grundbucheintrag`
als Kriterium fuer gueltige bzw. projektierte DM01-Liegenschaften verwendet
hat. Das war fuer das DMAV-zu-DM01-Profil zu eng und hat die produktive
Full-Run-Transformation fachlich beeinflusst.

Die aktuelle Profilentscheidung ist:

- `GSNachfuehrung.Mutationsart = #Normal` wird als DM01-`#gueltig`
  transformiert.
- `GSNachfuehrung.Mutationsart = #Projektmutation` und
  `#AbschlussProjektmutation` werden als DM01-`#projektiert` transformiert.
- `Grundbucheintrag` bleibt auf `LSNachfuehrung.GBEintrag` gemappt, entscheidet
  aber nicht mehr ueber gueltig/projektiert.

Die Regel gilt konsistent fuer `Grundstueck`, `Liegenschaft`,
`SelbstRecht` und `Bergwerk` sowie fuer die jeweiligen `Proj*`-Regeln.

## Umsetzung im Profil

Die DMAV-`Mutationsart` der referenzierten Entstehungs- bzw.
Untergangs-Nachfuehrung wird ueber `lookupIn(...)` gelesen und mit
`Mutationsart_Status` auf den DM01-Status abgebildet.

Sinngemaess fuer rechtsgueltige Grundstuecke:

```text
gs.Fiktiv == false
and defined(gs.Entstehung)
and enumMap(coalesce(lookupIn(... oid(gs.Entstehung) ..., "Mutationsart"), #Normal), "Mutationsart_Status") == #gueltig
and (
  notDefined(gs.Untergang)
  or enumMap(coalesce(lookupIn(... oid(gs.Untergang) ..., "Mutationsart"), #Normal), "Mutationsart_Status") != #gueltig
)
```

Sinngemaess fuer projektierte Grundstuecke:

```text
defined(gs.Entstehung)
and enumMap(coalesce(lookupIn(... oid(gs.Entstehung) ..., "Mutationsart"), #Normal), "Mutationsart_Status") == #projektiert
and notDefined(gs.Untergang)
```

Die projektierte Klassifikation haengt damit nicht mehr von
`gs.Fiktiv == false` und nicht mehr vom fehlenden `Grundbucheintrag` ab.

## Ergebnis des Full-Runs

Ausgefuehrt am 2026-06-29:

```bash
./gradlew runDm01DmavFullRun -Pdm01DmavFullRunManifest=products/dm01-dmav/full-runs/dmav-tym-alles-v1-1/manifest.yaml
```

Die Transformation selbst laeuft mit 0 Transformationsfehlern durch:

| Metrik | Wert |
|---|---:|
| Source records read | 20472 |
| Source records filtered | 20019 |
| Targets created | 23578 |
| Targets written | 23578 |
| Warnings | 4 |

Die relevanten GS-Zielklassen aus dem Transformationsreport:

| DM01-Zielklasse | Targets |
|---|---:|
| `DM01AVCH24LV95D.Liegenschaften.Grenzpunkt` | 9203 |
| `DM01AVCH24LV95D.Liegenschaften.Grundstueck` | 1467 |
| `DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung` | 11 |
| `DM01AVCH24LV95D.Liegenschaften.Liegenschaft` | 1457 |
| `DM01AVCH24LV95D.Liegenschaften.SelbstRecht` | 12 |
| `DM01AVCH24LV95D.Liegenschaften.ProjGrundstueck` | 0 |
| `DM01AVCH24LV95D.Liegenschaften.ProjLiegenschaft` | 0 |

Die relevanten Regelzaehler:

| Regel | Matches | Targets |
|---|---:|---:|
| `gs-proj-grundstueck` | 0 | 0 |
| `gs-proj-liegenschaft` | 0 | 0 |
| `gs-proj-selbstrecht-grundstueck` | 0 | 0 |
| `gs-proj-selbstrecht` | 0 | 0 |
| `gs-proj-bergwerk-grundstueck` | 0 | 0 |
| `gs-proj-bergwerk` | 0 | 0 |
| `gs-selbstrecht-grundstueck` | 12 | 12 |
| `gs-selbstrecht` | 12 | 12 |
| `gs-bergwerk-grundstueck` | 0 | 0 |
| `gs-bergwerk` | 0 | 0 |
| `gs-grundstueck-gueltig` | 1455 | 1455 |
| `gs-liegenschaft` | 1457 | 1457 |

Die frueher dokumentierten fuenf Liegenschaften ohne `Grundbucheintrag`
werden nach der aktuellen Fachlogik nicht allein deshalb als projektiert
behandelt. Entscheidend ist die `Mutationsart` der Entstehungsnachfuehrung.

## Offener Befund

Der Full-Run scheitert weiterhin in der DM01-Validierung, aber nicht wegen
der Statusklassifikation:

```text
DM01AVCH24LV95D.Liegenschaften.Liegenschaft.Geometrie:
no area-ref to polygon of lines 18, 21, 19
```

Damit ist die alte `Grundbucheintrag`-Ableitung als unmittelbare Ursache
ausgeschlossen. Offen bleibt eine separate topologische AREA-Frage bei
`Liegenschaft.Geometrie`.

## Nachweisdateien

Die aktuellen Reports liegen unter:

```text
build/reports/dm01-dmav/full-runs/dmav-tym-alles-v1-1/
```

Relevant sind insbesondere:

- `transformation-report.md`
- `transformation-report.json`
- `dm01-validation.log`
