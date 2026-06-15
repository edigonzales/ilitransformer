# ADR 0004: XLSX-Korrelationstabelle als Hint-Quelle

## Status

Accepted (Phase 8).

## Context

Die Datei `DMAV_Korrelationstabelle_20260301.xlsx` enthält ~1200 Zeilen mit fachlichen Korrespondenzen zwischen DM01- und DMAV-Attributen. Es war zu entscheiden, ob die XLSX direkt als ausführbare Mapping-Konfiguration interpretiert wird oder als Hint-Quelle für einen Mapping-Generator.

## Decision

Die XLSX dient ausschließlich als **Mapping-Hint-Quelle**. Sie wird nicht direkt in Runtime-Regeln übersetzt.

## Rationale

- **Keine vollständige Mapping-Spezifikation**: Die XLSX enthält Lücken (OID-Strategien, Basket-Strategien, Join-Kardinalitäten, Typkonvertierungen)
- **Freitext in Zellen**: Bedingungen und Ergänzungen sind natürliche Sprache, keine formale Logik
- **Mehrdeutigkeit**: Manche Zeilen haben mehrere Zielobjekte oder unklare Fallunterscheidungen
- **Qualitätssicherung**: Die Korrelationstabelle ist fachlich, nicht technisch validiert

## Consequences

- Mapping-Pipeline: XLSX → `CorrelationHint` → manuelle Prüfung → gültiges Mapping
- Kein direkter `JobConfig`-Export aus XLSX
- Confidence-Score pro Candidate (hoch für klare Korrelationen, niedrig für unklare)
- Generierte Mappings enthalten `TODO(...)` für unklare Fälle (im `compatible`-CompileMode als Warning, nicht als Fehler)
- Die XLSX kann jederzeit durch eine aktuellere Version ersetzt werden, ohne dass bestehende Mappings brechen
