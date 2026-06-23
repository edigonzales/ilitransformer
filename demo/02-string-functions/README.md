# 02 – string-functions

Textaufbereitung mit den String-Funktionen der Expression-Sprache.

## Abgedeckte INTERLIS-Elemente

- `MANDATORY` vs. optionale `TEXT`-Attribute
- unterschiedliche `TEXT*N`-Längen (`TEXT*10`, `TEXT*20`, `TEXT*200`)

## Abgedeckte Expressions

- `upper(...)`, `lower(...)`
- `substring(value, start, length)` – **0-basiert**
- `truncate(value, maxLength)`
- `replace(value, pattern, replacement)` – ersetzt alle Vorkommen
- `trim(...)`
- `default(value, fallback)` – Ersatzwert bei `null`
- Verschachtelung: `truncate(trim(...), 20)`, `default(trim(...), "...")`

> Hinweis: `concat(...)` ist in dieser Build-Version als 0-stellige Funktion
> registriert und kann **nicht** mit Argumenten aufgerufen werden. Die Demos
> verwenden daher keine String-Konkatenation.

## Dateien

| Datei | Zweck |
|---|---|
| `models/strings.ili` | Modell mit `RawText` (Quelle) und `CleanText` (Ziel) |
| `data/input.xtf` | 2 Rohtext-Objekte (eines ohne `Bemerkung`) |
| `profile.ilimap` | Mapping-Profil |

## Aufrufe

```bash
demo/validate.sh demo/02-string-functions
```

Einzeln (aus `demo/02-string-functions`, CLI-Pfad relativ zum Repo-Root):

```bash
java -jar "$ILI2C" models/strings.ili
java -jar "$ILIVALIDATOR" --modeldir models data/input.xtf
../../build/install/ilitransformer/bin/ilitransformer validate-mapping -m profile.ilimap
../../build/install/ilitransformer/bin/ilitransformer transform -m profile.ilimap
java -jar "$ILIVALIDATOR" --modeldir models output.xtf
```

## Erwartetes Ergebnis

- `Code` "LFP-300-AB" → `CodeUpper`=LFP-300-AB, `CodeLower`=lfp-300-ab, `Praefix`=LFP
- `Kurz` = erste 20 Zeichen der getrimmten Bemerkung
- `TelefonNormalisiert` = Bindestriche durch Leerzeichen ersetzt
- Zweites Objekt ohne `Bemerkung` → `Bemerkung`="keine Bemerkung", `Kurz` fehlt
