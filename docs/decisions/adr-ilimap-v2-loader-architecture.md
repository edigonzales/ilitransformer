# ADR: ilimap v2 Loader-Architektur

**Status:** accepted  
**Datum:** 2026-06-17  
**Betrifft:** `.ilimap`-DSL v2, MappingLoader, JobConfig, MappingCompiler

## Kontext

Die bestehende YAML-Mapping-DSL soll um ein neues Autorenformat `.ilimap` ergänzt werden. Die Runtime-Semantik bleibt unverändert. Die neue DSL benötigt einen eigenen Lexer, Parser, AST, Semantic Validator und einen Mapper nach `JobConfig`, der den bestehenden `MappingCompiler`-Pfad weiternutzt.

## Entscheidung

### 1. Pipeline

```
.ilimap → IlimapAst → JobConfig → MappingCompiler → TransformPlan
```

- `.ilimap`-Dateien werden durch einen neuen `IlimapLoader` geparst und semantisch validiert.
- Der `IlimapLoader` erzeugt einen `IlimapAst` (Lexer → Parser → Semantic Validator → SymbolTable).
- Ein `IlimapToJobConfigMapper` überführt den validierten AST in eine `JobConfig`-Instanz.
- Die `JobConfig` wird unverändert durch den bestehenden `MappingCompiler` in einen `TransformPlan` kompiliert.
- Es wird **kein** neuer Runtime-Pfad oder zweiter Compiler-Pfad eingeführt.

### 2. Formatversion vs. Config-Semantikversion

- `mapping v2` bezeichnet ausschliesslich die `.ilimap`-Formatversion (`IlimapFormatVersion.V2`).
- `mapping v2` setzt **nicht** automatisch `JobConfig.version = 2`.
- Der `IlimapToJobConfigMapper` entscheidet, welche interne `JobConfig.version` gesetzt wird, basierend auf der Semantik, die die bestehende YAML-Runtime erwartet.
- Solange die bestehende Runtime `JobConfig.version = 1` verwendet, bleibt dieser Wert erhalten.

### 3. Keine neue Runtime-Semantik

- `.ilimap` ist ein reines Autorenformat. Die Semantik wird ausschliesslich durch den bestehenden `MappingCompiler` und die Runtime definiert.
- Neue Expression-Funktionen, Makros, Includes, bedingte Kompilierung oder INTERLIS-Sonderfälle im Parser sind ohne separate Entscheidung nicht erlaubt.
- DM01/DMAV-spezifische Logik verbleibt in Produktprofilen und darf nicht in generische `mapping/ilimap/`-Packages wandern.

### 4. Parser und Formatter teilen denselben AST

- `IlimapParser` und `IlimapFormatter` arbeiten auf demselben `IlimapAst` (Package `mapping/ilimap/ast/`).
- Alle AST-Knoten implementieren `IlimapAstNode` mit `IlimapSourceRange range()` für positionsgenaue Fehlermeldungen.
- Der Formatter nutzt ausschliesslich die AST-Daten; es gibt keinen zweiten Parser oder separaten Parsebaum.

### 5. Nicht unterstützte Felder erzeugen Diagnostics

Im Einklang mit AGENTS.md ("Nicht implementierte DSL-Felder dürfen nicht still ignoriert werden"):

- Unbekannte Syntaxelemente sind **ERROR** (Parser).
- Reservierte, aber nicht implementierte Features (z. B. Ref-Kurzform, `oid external`, `basket expression`) sind **ERROR** mit klarem Code (z. B. `ILIMAP_UNSUPPORTED_RESERVED_FEATURE`).
- Features, die in `JobConfig` keine Entsprechung haben, werden entweder gemappt (falls Felder existieren) oder als **ERROR** diagnostiziert.
- Die `DslCapabilityValidator`-Praxis aus dem bestehenden Compiler dient als Vorbild: unbekannte/unsupported Werte sind Fehler, keine stillen Fallbacks.

### 6. Diagnostic-Integration

- Neue Diagnostic-Codes werden im bestehenden `DiagnosticCode`-System ergänzt (Präfix `ILIMAP-`).
- Es wird **kein** paralleles Diagnostic-System eingeführt.
- `DiagnosticCollector` wird durch die gesamte `.ilimap`-Pipeline gereicht.
- Diagnostics enthalten `code`, `severity`, `message`, `sourcePath`, `line`, `column`.

### 7. Loader-Integration

- `MappingLoader` wird um einen `MappingFormatDetector` erweitert, der anhand der Dateiendung (`.ilimap` vs. `.yaml`/`.yml`) das Format erkennt.
- Für `.ilimap` delegiert `MappingLoader` an `IlimapLoader`.
- Die bestehende YAML-Lade-Logik bleibt unverändert.
- Neue Hilfsklassen (`MappingFormat`, `MappingFormatDetector`) werden im Package `mapping/model/` abgelegt.

## Konsequenzen

- `JobConfig` bleibt stabil; Änderungen sind nur für Felder nötig, die in `.ilimap` neu sind und in YAML nicht existieren.
- Bestehende YAML-Profile laden weiterhin unverändert.
- Die Package-Struktur `mapping/ilimap/` ist vollständig neu und hat keine Abhängigkeiten zu DM01/DMAV.
- Tests für `.ilimap`-Komponenten werden parallel zu bestehenden Tests ausgeführt, ohne bestehende Tests zu brechen.

## Referenzen

- `docs/mapping-dsl.md` — bestehende YAML-DSL-Dokumentation
- `docs/ilimap-v2-llm-coding-agent-implementation-plan-mit-grammatik.md` — Implementierungsplan mit Grammatik
- `AGENTS.md` — nicht verhandelbare Regeln
- `docs/agent/DECISIONS.md` — bestehende Architekturentscheidungen (D001–D006)
