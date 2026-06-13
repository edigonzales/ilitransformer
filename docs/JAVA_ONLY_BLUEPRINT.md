# Java-only Blueprint für eine robuste INTERLIS-Transform-Engine

## 1. Zielbild (nach deinen Prioritäten)

**Priorität:** Robustheit + Funktionsumfang vor Peak-Performance und Polyglotismus.

### Muss-Kriterien
- Frühes, explizites Design für **2-Pass-Referenzauflösung**.
- Architektur, die **Geometrieverarbeitung** nicht verbaut.
- **Many-to-many Datei-Mapping**: mehrere Inputs → mehrere Outputs (inkl. n→1 und 1→n).
- Möglichst einfache, transparente DSL.
- Java-first, Truffle optional später als Add-on.

---

## 2. Empfohlene Gesamtarchitektur

```text
+--------------------------+
| CLI / API Orchestrator   |
+------------+-------------+
             |
             v
+--------------------------+      +---------------------------+
| Model & Type Services    |<---->| INTERLIS adapters (I/O)   |
| (ili2c metadata facade)  |      | ili1/ili2 reader/writer    |
+------------+-------------+      +---------------------------+
             |
             v
+--------------------------+
| Mapping Compiler         |
| DSL/YAML -> Typed Plan   |
+------------+-------------+
             |
             v
+--------------------------+
| Execution Engine         |
| Pass 1 + Pass 2          |
+------+---------+---------+
       |         |
       v         v
+-------------+  +----------------+
| State Store |  | Diagnostics     |
| (oid/index) |  | (errors/warns)  |
+-------------+  +----------------+
```

### Hauptprinzipien
1. **Compiler und Runtime trennen** (fehlerhafte Mappings früh erkennen).
2. **2-Pass als First-Class Konzept**, nicht als nachträglicher Patch.
3. **Adapter-Grenzen** sauber halten (INTERLIS I/O austauschbar).
4. **Deterministische Diagnostik** (Error Codes, Pfad, Kontext).

---

## 3. Konkrete Module / Packages

```text
com.company.transformer
  ├─ app
  │   ├─ CliMain
  │   └─ JobRunner
  ├─ model
  │   ├─ IliModelService
  │   ├─ TypeSystemFacade
  │   └─ RoleResolver
  ├─ mapping
  │   ├─ parser           (YAML/DSL parser)
  │   ├─ ast              (rohe Mapping-Definition)
  │   ├─ compiler         (AST -> TypedPlan)
  │   └─ plan             (TargetPlan, RulePlan, ExprPlan)
  ├─ engine
  │   ├─ pass1            (object creation, id map)
  │   ├─ pass2            (reference resolution)
  │   ├─ join             (join evaluators)
  │   └─ context          (ExecutionContext)
  ├─ state
  │   ├─ StateStore       (interface)
  │   ├─ InMemoryStateStore
  │   └─ PersistentStateStore (später)
  ├─ interlis
  │   ├─ reader           (ili1/ili2 adapters)
  │   ├─ writer           (ili1/ili2 adapters)
  │   └─ geometry         (helper table, split/merge adapters)
  ├─ expr
  │   ├─ ExpressionEngine
  │   ├─ FunctionRegistry
  │   └─ builtins         (if, coalesce, date, ref, ...)
  ├─ diag
  │   ├─ Diagnostic
  │   ├─ DiagnosticCollector
  │   └─ ErrorCode
  └─ test
      ├─ unit
      ├─ integration
      └─ golden
```

---

## 4. 2-Pass-Referenzauflösung (Kern)

## Pass 1: Materialisierung + ID-Mapping
Ziele:
- Pro gemapptem Zielobjekt eine **stabile targetOID** vergeben.
- `sourceOID -> targetOID` speichern.
- Referenzen nur als **DeferredRef** vormerken, nicht final setzen.

### Datenstrukturen
- `IdMap`: `(sourceClass, sourceOid, sourceFile, sourceBasket) -> (targetClass, targetOid, targetFile, targetBasket)`
- `ObjectIndex`: Lookup für Join/Ref-Hilfen.
- `DeferredRef`:
  - owner target oid
  - owner attribute/role
  - source reference token (oid/role/basket/file)
  - expected target class

## Pass 2: Finalisierung
Ziele:
- DeferredRef auflösen via `IdMap` + RoleResolver + Basket/File-Kontext.
- Referenz setzen oder diagnostizieren (unresolved/ambiguous/type mismatch).

### Auflösungsstrategie (Reihenfolge)
1. Exakter Match (sourceFile/sourceBasket/sourceOid).
2. Basket-weit fallback.
3. Global fallback (nur wenn explizit erlaubt).
4. Bei >1 Treffer: `AMBIGUOUS_REFERENCE`.
5. Bei 0 Treffern: `UNRESOLVED_REFERENCE`.

### Wichtige Designentscheidung
- **Fail policy konfigurierbar**:
  - `strict`: unresolved => Fehler (Abbruch)
  - `lenient`: unresolved => Warnung + null

---

## 5. Geometrie-fähige Architektur

## Regeln
- Geometrie nie als „special case“ überall verteilen.
- Zentrale `GeometryAdapter`-API:
  - `normalize(sourceGeom)`
  - `denormalize(targetType)`

## Warum
- So bleiben ITF-Helpertabellen, Surface/Area-Splitting, zukünftige Geometrieoperationen kapselbar.
- DSL bleibt einfach; Geometrie-I/O und ITF-Hilfstabellen bleiben im Adapter bzw. Writer gekapselt.

---

## 6. DSL-Minimum (einfach, robust)

### Vorschlag: deklaratives YAML-Profil (später optional Text-DSL)

```yaml
job:
  inputs:
    - id: av1
      path: in/part1.xtf
      model: DMAV_Grundstuecke_V1_0
    - id: av2
      path: in/part2.xtf
      model: DMAV_Grundstuecke_V1_0
  outputs:
    - id: out_main
      path: out/main.xtf
      model: DM01AVCH24LV95D
    - id: out_aux
      path: out/aux.itf
      model: DM01AVCH24LV95D

mapping:
  basketIdStrategy: preserve
  oidStrategy: integer

  rules:
    - targetClass: DM01AVCH24LV95D.Liegenschaften.LSNachfuehrung
      output: out_main
      sources:
        - alias: s
          input: av1|av2
          class: DMAV_Grundstuecke_V1_0.Grundstuecke.GSNachfuehrung
      attributes:
        - target: NBIdent
          expr: "${s.NBIdent}"
        - target: Gueltigkeit
          expr: "if(${s.Grundbucheintrag} != null, 'gueltig', 'projektiert')"
      refs:
        - target: someRole
          expr: "ref('s','targetRole')"
```

### DSL-Prinzip
- Wenige primitive Bausteine.
- Keine versteckte Magie.
- Strikter Compiler mit aussagekräftigen Diagnostics.

---

## 7. Many-to-many Datei-Mapping

## Anforderungen
- Mehrere Inputdateien auf ein Ziel bündeln (n→1).
- Eine Inputdatei auf mehrere Ziele verteilen (1→n).
- n→m mit Routing-Regeln.

## Design
- `input id` / `output id` als First-Class im Plan.
- Jede Regel hat optionalen `output` Router.
- Optionales `partitionBy` (z. B. Topic/Basket/Kanton/Objekttyp).

### Intern
- Engine verarbeitet ein logisches Event-Stream-Modell mit `(fileId, basketId, object)` als Kontext.
- IdMap enthält `fileId` explizit (wichtig für Referenzen).

---

## 8. Compiler-Validierungen (früh und streng)

1. Klassen/Attribute/Rollen existieren.
2. Ausdruckstypen kompatibel (String, Number, Date, Ref, Geometry).
3. `ref(...)`-Role auf Source-Typ gültig.
4. Join-Aliase vollständig und eindeutig.
5. Output-Routing valid (existierende output ids).
6. Enum-Mappings: Coverage-Bericht (gemappt/ungemappt/default).

---

## 9. Diagnostikstandard (produktionsreif)

Jeder Fehler/Warnung mit:
- `code` (z. B. `ILIMAP-REF-UNRESOLVED`)
- `severity`
- `mappingRuleId`
- `sourcePath` (Datei/Basket/OID/Attribut)
- `message`
- `suggestion`

Beispiele:
- `ILIMAP-REF-UNRESOLVED`
- `ILIMAP-REF-AMBIGUOUS`
- `ILIMAP-TYPE-MISMATCH`
- `ILIMAP-GEOM-INVALID`

---

## 10. Implementierungsplan in 4 Iterationen

## Iteration 1 (MVP robust)
- Java-only Core.
- Einfaches YAML + Compiler-Validierung.
- Pass 1/Pass 2 Referenzauflösung.
- 1 Input, 1 Output.

## Iteration 2
- Multi-input (n→1), Join stabilisieren.
- Diagnostik standardisieren + Report.
- Performance nur dort optimieren, wo nötig.

## Iteration 3
- Multi-output (1→n, n→m) + Routing.
- Geometrieadapter erweitern (split/merge/helpers).

## Iteration 4
- Optional: Persistenter StateStore.
- Optional: Truffle-Instrumentierung (Debug/Coverage), ohne Kernlogik zu ersetzen.

---

## 11. Risiko- und Komplexitätsbewertung

### Hochrisiko (früh angehen)
- Referenzauflösung über Dateien/Baskets hinweg.
- Geometrie-Sonderfälle (itf helper tables, area/surface semantics).
- Unklare DSL-Semantik (führt zu schwer wartbaren Regeln).

### Mittel
- Join-Kardinalität und Speicherverbrauch.
- Fehlerdiagnostik/UX.

### Niedrig
- Truffle/Polyglot später als optionale Ergänzung.

**Fazit:** Das Vorhaben ist gut, aber nur als **stufenweiser Java-first Ausbau** realistisch robust.
