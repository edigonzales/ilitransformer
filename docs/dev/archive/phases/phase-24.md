# Phase 24: Geometrie- und Transfer-I/O-Härtung

## Implemented

Gültige ITF-/XTF-Geometrien werden ohne Informationsverlust innerhalb der unterstützten Geometrietypen gelesen, transformiert, geschrieben und erneut gelesen.

## Changed classes

| Class | Change |
|---|---|
| `IoxGeometryAdapter` | `GeometryValueCopier`-Feld integriert |
| `DiagnosticCode` | `GEOM_DIMENSION_MISMATCH`, `GEOM_COORD_DOMAIN_MISMATCH` hinzugefügt |
| `FeatureMatrix` | Phase-24-Einträge ergänzt |
| `GeometryIntegrationTest` | `expectedType()` → `expression().resultType()` (Pre-Existing Fix) |
| `SurfaceAreaGeometryIntegrationTest` | `expectedType()` → `expression().resultType()` (Pre-Existing Fix) |

## Added classes

| Class | Package | Description |
|---|---|---|
| `GeometryValueCopier` | `geometry` | Deep copy für IomObject-Geometrien |
| `GeometryCompatibility` | `geometry` | Record: `compatible`, `incompatibilities` |
| `GeometryCompatibilityService` | `geometry` | Compile-time Kompatibilitätsprüfung: Dimension, SURFACE/AREA, Typ-Matching |

## Tests

### Unit Tests
- `GeometryDeepCopyTest` — 6 Tests: null, COORD, POLYLINE, SURFACE, AREA, Mehrfach-Kopie
- `GeometryCompatibilityServiceTest` — 10 Tests: gleicher Typ, SURFACE↔AREA, inkompatible Typen

### Integration Tests
- `CoordRoundtripTest` — XTF COORD Write→Validate→Read; ITF COORD Write→Read
- `PolylineRoundtripTest` — XTF POLYLINE mit Geraden + ARC Write→Validate→Read
- `XtfReadOwnOutputTest` — XTF-Output mit Modellkontext wieder einlesen, inkl. Löcher
- `GeometryTypeMismatchTest` — Compiler erkennt kompatible/inkompatible Geometrie-Typen
- `RealDatasetGeometrySmokeTest` — Liest echte DM01-ITF und DMAV-XTF, zählt Objekte/Baskets

## Validation commands

```bash
./gradlew test                          # Unit Tests (grün)
./gradlew integrationTest               # Integration Tests
./gradlew realDataTest                  # Real Dataset Smoke Tests (@Tag("real-data"))
```

## Known limitations

- `GeometryCompatibilityService` prüft derzeit nur Typ-Level-Kompatibilität. Dimensionscheck (COORD 2D/3D) ist vorbereitet, benötigt aber echte `AttributeDef`-Objekte aus kompilierten Modellen.
- `COORD 3D` wird noch nicht vollständig unterstützt (laut SPEC optional).
- LINEATTR wird als `GEOM_LINEATTR_UNSUPPORTED` diagnostiziert.
- Keine Topologiereparatur, Koordinatentransformation, Generalisierung oder räumliche Operationen in dieser Phase.
- `RealDatasetGeometrySmokeTest` ist `@Tag("real-data")` und benötigt lokale Modelle + Netzwerkzugriff für abhängige Modelle.

## Open questions

1. Soll `GeometryCompatibilityService` bei jeder Transformation aufgerufen werden oder nur bei explizitem `--validate`?
2. Soll `COORD 3D` in einer späteren Phase voll unterstützt werden?
3. Soll `InterlisIoFactory` auf `TransferFormat`-Enum umgestellt werden?

## Migration notes

Keine Breaking Changes. Bestehende APIs unverändert. Neue Klassen sind additiv.
