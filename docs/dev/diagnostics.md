# Diagnostic Codes

## Compiler / Mapping (ILITRF-MAP-*)

| Code | Severity | Bedeutung |
|---|---|---|
| `ILITRF-MAP-VERSION` | ERROR | `version`-Feld fehlt oder < 1 |
| `ILITRF-MAP-MISSING-ID` | ERROR | Rule hat kein `id`-Feld |
| `ILITRF-MAP-DUPLICATE-ID` | ERROR | Rule-`id` ist nicht eindeutig |
| `ILITRF-MAP-MISSING-TARGET-CLASS` | ERROR | Zielklasse (`target.class`) fehlt |
| `ILITRF-MAP-UNKNOWN-OUTPUT` | ERROR | Output-ID existiert nicht in `job.outputs` |
| `ILITRF-MAP-MISSING-SOURCE-CLASS` | ERROR | Source hat kein `class`-Feld |
| `ILITRF-MAP-MISSING-ALIAS` | ERROR | Source hat kein `alias`-Feld |
| `ILITRF-MAP-DUPLICATE-ALIAS` | ERROR | Source-`alias` ist nicht eindeutig pro Rule |
| `ILITRF-MAP-UNKNOWN-INPUT` | ERROR | Input-ID existiert nicht in `job.inputs` |
| `ILITRF-MAP-MISSING-INPUT` | ERROR | Source hat kein `input`/`inputs`-Feld |
| `ILITRF-MAP-UNKNOWN-TARGET-CLASS` | ERROR | Zielklasse existiert nicht im Modell |
| `ILITRF-MAP-UNKNOWN-SOURCE-CLASS` | ERROR | Quellklasse existiert nicht im Modell |
| `ILITRF-MAP-ABSTRACT-TARGET-CLASS` | ERROR | Zielklasse ist abstrakt |
| `ILITRF-MAP-UNKNOWN-TARGET-ATTRIBUTE` | ERROR | Zielattribut existiert nicht in der Zielklasse |
| `ILITRF-MAP-UNKNOWN-SOURCE-ATTRIBUTE` | WARNING | Quellattribut existiert nicht |
| `ILITRF-MAP-UNKNOWN-ROLE` | WARNING | Rolle existiert nicht in der Zielklasse |
| `ILITRF-MAP-TYPE-MISMATCH` | WARNING | Expression-Typ passt nicht zum Zieltyp |
| `ILITRF-MAP-MANDATORY-MISSING` | WARNING | Pflichtattribut wird nicht gesetzt |
| `ILITRF-MAP-DUPLICATE-TARGET-ASSIGN` | ERROR | Target-Attribut mehrfach zugewiesen |
| `ILITRF-MAP-CYCLIC-DEPENDENCY` | ERROR | Zyklische Rule-Referenz |
| `ILITRF-MAP-NON-TRANSFERABLE-TARGET` | WARNING | Zielklasse ist nicht transferierbar (View) |
| `ILITRF-MAP-OID-TYPE-MISMATCH` | ERROR | OID-Strategie passt nicht zum Ziel-OID-Typ (z.B. integer auf UUIDOID) |

## Model (ILITRF-MODEL-*)

| Code | Severity | Bedeutung |
|---|---|---|
| `ILITRF-MODEL-COMPILE-FAILED` | ERROR | Modell-Kompilierung fehlgeschlagen |

## Runtime (ILITRF-RUN-*)

| Code | Severity | Bedeutung |
|---|---|---|
| `ILITRF-RUN-REF-UNRESOLVED` | WARNING/ERROR | Referenz konnte nicht aufgelöst werden |
| `ILITRF-RUN-REF-AMBIGUOUS` | ERROR | Referenz ist mehrdeutig (mehrere Ziele) |
| `ILITRF-RUN-REF-TYPE-MISMATCH` | WARNING/ERROR | Referenziertes Objekt hat falsche Zielklasse |
| `ILITRF-RUN-REF-MISSING-MANDATORY` | WARNING/ERROR | Pflichtreferenz fehlt |
| `ILITRF-RUN-REF-CARDINALITY` | WARNING | Kardinalität verletzt |
| `ILITRF-RUN-OID-COLLISION` | ERROR | OID-Kollision |
| `ILITRF-RUN-BASKET` | ERROR | Basket-Zuordnung fehlerhaft |
| `ILITRF-RUN-SOURCE-READ` | ERROR | Fehler beim Lesen der Quelle |
| `ILITRF-RUN-TARGET-WRITE` | ERROR | Fehler beim Schreiben des Ziels |
| `ILITRF-RUN-EXPR` | ERROR | Expression-Auswertung fehlgeschlagen |
| `ILITRF-RUN-CARDINALITY` | ERROR | Kardinalität verletzt |

## Expression (ILITRF-EXPR-*)

| Code | Severity | Bedeutung |
|---|---|---|
| `ILITRF-EXPR-SYNTAX` | ERROR | Ausdruck konnte nicht geparst werden |
| `ILITRF-EXPR-UNKNOWN-FUNC` | ERROR | Unbekannte Funktion aufgerufen |
| `ILITRF-EXPR-TYPE` | ERROR | Typ-Fehler bei Expression-Auswertung |
| `ILITRF-EXPR-NON-DETERMINISTIC` | WARNING | Nicht-deterministische Funktion verwendet |
| `ILITRF-EXPR-UNSUPPORTED` | WARNING | Funktion/Feature noch nicht vollständig unterstützt |

## Geometrie (ILITRF-GEOM-*)

| Code | Severity | Bedeutung |
|---|---|---|
| `ILITRF-GEOM-TYPE-MISMATCH` | ERROR | Geometrietyp passt nicht |
| `ILITRF-GEOM-CRS-MISMATCH` | ERROR | CRS passt nicht |
| `ILITRF-GEOM-INVALID` | ERROR | Geometrie ungültig |
| `ILITRF-GEOM-TOPOLOGY` | WARNING | Topologiebedingung verletzt |
| `ILITRF-GEOM-LINEATTR-UNSUPPORTED` | WARNING | LINEATTR noch nicht unterstützt |

## DM01 / DMAV (ILITRF-DMAV-*)

| Code | Severity | Bedeutung |
|---|---|---|
| `ILITRF-DMAV-CORRELATION-PARSE` | WARNING | XLSX-Hint konnte nicht interpretiert werden |
| `ILITRF-DMAV-LOW-CONFIDENCE` | WARNING | Mapping-Kandidat hat niedrige Confidence |
| `ILITRF-DMAV-LOSSY` | WARNING | Mapping ist verlustbehaftet |
| `ILITRF-DMAV-OPEN-QUESTION` | WARNING | Fachliche offene Frage blockiert automatisches Mapping |

## Severity und Fail Policy

| failPolicy | ERROR | WARNING |
|---|---|---|
| `strict` | Abbruch mit Exit-Code 1 | Nur geloggt |
| `lenient` | Wird zu WARNING | Nur geloggt |
| `reportOnly` | Nur geloggt | Nur geloggt |

## Diagnostic-Modell

```java
public record Diagnostic(
    DiagnosticCode code,
    Severity severity,
    String message,
    String suggestion,
    String ruleId,
    String sourcePath,
    String targetPath,
    Map<String, Object> context
) {}
```
