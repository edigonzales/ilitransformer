# 01 – hello-copy

Einfachste 1:1-Transformation: jedes `Source`-Objekt wird zu einem `Target`-Objekt
kopiert. Demonstriert den grundlegenden Aufbau eines `.ilimap`-Profils und ein paar
triviale String-Expressions.

## Abgedeckte INTERLIS-Elemente

- `MODEL` / `TOPIC` / `CLASS`
- `BASKET OID AS INTERLIS.UUIDOID` und `OID AS INTERLIS.UUIDOID`
- Attributtypen `TEXT*N`, numerischer Bereich `0 .. 9999`, `BOOLEAN`
- `MANDATORY`

## Abgedeckte Expressions

- Direkte Attributzuweisung (`s.Anzahl`, `s.Aktiv`)
- `trim(...)` – führende/folgende Leerzeichen entfernen
- `upper(...)` – Grossschreibung
- `truncate(...)` (siehe Beispiel 02 für mehr String-Funktionen)

## Dateien

| Datei | Zweck |
|---|---|
| `models/hello.ili` | INTERLIS-2.4-Modell mit `Source`- und `Target`-Klasse |
| `data/input.xtf` | Eingabe-Transferdaten (2 `Source`-Objekte) |
| `profile.ilimap` | Mapping-Profil |
| `output.xtf` | Ergebnis (wird beim Transform erzeugt) |

## Aufrufe

Pfade beziehen sich auf das Distributions-Root. Die CLI liegt unter `bin/ilitransformer`.

```bash
CLI=bin/ilitransformer
ILI2C=/pfad/zu/ili2c.jar
ILIVALIDATOR=/pfad/zu/ilivalidator.jar

cd demo/01-hello-copy

# 1. Modell kompilieren
java -jar "$ILI2C" models/hello.ili

# 2. Eingabe validieren
java -jar "$ILIVALIDATOR" --modeldir models data/input.xtf

# 3. Mapping pruefen (ohne Ausfuehrung)
../../$CLI validate-mapping -m profile.ilimap

# 4. Transformation ausfuehren
../../$CLI transform -m profile.ilimap

# 5. Ausgabe validieren
java -jar "$ILIVALIDATOR" --modeldir models output.xtf
```

Oder alles auf einmal (aus dem Repo-Root):

```bash
demo/validate.sh demo/01-hello-copy
```

## Erwartetes Ergebnis

`output.xtf` enthält zwei `Target`-Objekte. `Label` ist der getrimmte Name,
`LabelGross` der grossgeschriebene Name. Beispiel:

```
Label=Alice Mueller   LabelGross=ALICE MUELLER   Anzahl=42   Aktiv=true
```
