# ADR 0003: Deterministic UUIDs (UUIDv3)

## Status

Accepted (Phase 6).

## Context

DMAV verwendet UUID-OIDs (`INTERLIS.UUIDOID`), während DM01 fortlaufende numerische OIDs verwendet. Für die Transformation DM01 → DMAV müssen stabile, reproduzierbare UUIDs erzeugt werden.

Alternativen:
- `uuid` (random UUID) — nicht reproduzierbar
- `deterministicUuid` via UUIDv5 (SHA-1) — moderner
- `deterministicUuid` via UUIDv3 (MD5) — einfacher, ausreichend

## Decision

UUIDv3 (`java.util.UUID.nameUUIDFromBytes()`, MD5-basiert) als deterministische UUID-Strategie.

## Rationale

- **Reproduzierbarkeit**: Gleiche Eingabe + Namespace → gleiche UUID. Essenziell für Golden Tests.
- **Einfachheit**: `java.util.UUID.nameUUIDFromBytes()` ist in der Standardbibliothek verfügbar.
- **MD5 ist ausreichend**: Für diesen Use Case (keine kryptografischen Anforderungen) reicht MD5. Die UUID dient nur als Objektidentifikator.
- **Namespace-Isolation**: Verschiedene Mapping-Jobs mit unterschiedlichen Namespaces erzeugen garantiert unterschiedliche UUIDs für das gleiche Quellobjekt.

## Consequences

- UUIDs sind abhängig vom Namespace und vom Source-Key
- Mapping-Änderung (Namespace-Änderung) → alle UUIDs ändern sich
- Mapping-Konfiguration muss den Namespace dokumentieren
- Bei Bedarf kann später auf UUIDv5 migriert werden (wäre ein Mapping-Version-Break)
