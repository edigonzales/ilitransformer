# Phase 24: Geometrie- und Transfer-I/O-Härtung — Implementierungsplan

> Stand: Analyse abgeschlossen. Plan zur Ausführung bereit.

---

## Übersicht

**Ziel laut SPEC:** Gültige ITF-/XTF-Geometrien werden ohne Informationsverlust innerhalb der unterstützten Geometrietypen gelesen, transformiert, geschrieben und erneut gelesen.

**Bestehende Abdeckung:** ca. 70%. Die meisten Roundtrip-Tests existieren bereits. Fehlend:
- `GeometryValueCopier` (neue Klasse)
- `GeometryCompatibilityService` (neue Klasse)
- 6 neue Tests
- `IoxGeometryAdapter`-Anpassungen

---

## Schritt-für-Schritt

### A. Neue Produktionsklassen

#### 1. `GeometryValueCopier`
**Pfad:** `src/main/java/guru/interlis/transformer/geometry/GeometryValueCopier.java`

```java
package guru.interlis.transformer.geometry;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

public final class GeometryValueCopier {

    public IomObject deepCopy(IomObject geometry) {
        if (geometry == null) {
            return null;
        }
        return new Iom_jObject(geometry);
    }
}
```

`Iom_jObject(IomObject)` kopiert rekursiv alle Attribute und Unterobjekte. Dies ist die von INTERLIS-Bibliotheken empfohlene Deep-Copy-Methode.

---

#### 2. `GeometryCompatibility` (Record)
**Pfad:** `src/main/java/guru/interlis/transformer/geometry/GeometryCompatibility.java`

```java
package guru.interlis.transformer.geometry;

import java.util.Collections;
import java.util.List;

public record GeometryCompatibility(
    boolean compatible,
    List<String> incompatibilities
) {
    public static GeometryCompatibility compatible() {
        return new GeometryCompatibility(true, Collections.emptyList());
    }

    public static GeometryCompatibility incompatible(String reason) {
        return new GeometryCompatibility(false, List.of(reason));
    }

    public static GeometryCompatibility incompatible(List<String> reasons) {
        return new GeometryCompatibility(false, List.copyOf(reasons));
    }
}
```

---

#### 3. `GeometryCompatibilityService`
**Pfad:** `src/main/java/guru/interlis/transformer/geometry/GeometryCompatibilityService.java`

```java
package guru.interlis.transformer.geometry;

import ch.interlis.ili2c.metamodel.*;
import guru.interlis.transformer.mapping.plan.TypeInfo;

public final class GeometryCompatibilityService {

    public GeometryCompatibility check(
            TypeInfo sourceType,
            TypeInfo targetType,
            AttributeDef sourceAttribute,
            AttributeDef targetAttribute) {

        // 1. Gleicher Typ -> kompatibel
        if (sourceType == targetType) {
            return GeometryCompatibility.compatible();
        }

        // 2. SURFACE <-> AREA sind kreuzkompatibel
        if ((sourceType == TypeInfo.SURFACE || sourceType == TypeInfo.AREA)
                && (targetType == TypeInfo.SURFACE || targetType == TypeInfo.AREA)) {
            return GeometryCompatibility.compatible();
        }

        // 3. COORD 2D -> COORD 3D: prüfe Dimension
        if (sourceType == TypeInfo.COORD && targetType == TypeInfo.COORD) {
            return checkCoordDimension(sourceAttribute, targetAttribute);
        }

        // 4. POLYLINE -> POLYLINE: prüfe ARCS
        if (sourceType == TypeInfo.POLYLINE && targetType == TypeInfo.POLYLINE) {
            return checkPolylineCompatibility(sourceAttribute, targetAttribute);
        }

        return GeometryCompatibility.incompatible(
                "Geometry type mismatch: " + sourceType + " -> " + targetType);
    }

    private GeometryCompatibility checkCoordDimension(
            AttributeDef source, AttributeDef target) {
        boolean source3D = is3DCoord(source);
        boolean target3D = is3DCoord(target);
        if (source3D && !target3D) {
            return GeometryCompatibility.incompatible(
                    "3D COORD cannot be mapped to 2D COORD without explicit dimension reduction");
        }
        return GeometryCompatibility.compatible();
    }

    private GeometryCompatibility checkPolylineCompatibility(
            AttributeDef source, AttributeDef target) {
        // ARCS sind standardmässig kompatibel (IOM unterstützt sie kanonisch)
        return GeometryCompatibility.compatible();
    }

    private boolean is3DCoord(AttributeDef attribute) {
        if (attribute == null) return false;
        Type real = Type.findReal(attribute.getDomain());
        if (real instanceof CoordType coordType) {
            var dimensions = coordType.getDimensions();
            return dimensions != null && dimensions.length >= 3;
        }
        return false;
    }
}
```

**Design-Entscheidung:** `GeometryCompatibilityService` arbeitet rein auf Metamodell-Informationen (`AttributeDef`) und `TypeInfo`. Keine Runtime-I/O. Die Methode `check()` wird vom Compiler (in `MappingCompiler`) aufgerufen, nicht zur Laufzeit in der Engine. Das entspricht dem SPEC-Grundsatz §4.4: Compiler und Runtime teilen dieselben kompilierten Artefakte.

---

#### 4. `IoxGeometryAdapter` anpassen
**Pfad:** `src/main/java/guru/interlis/transformer/geometry/IoxGeometryAdapter.java`

Änderungen:
- `GeometryValueCopier` einbinden (statt direktem `new Iom_jObject(source)` in `denormalize()`)
- `GeometryCompatibilityService` in `isCompatibleGeometryType()` konsultieren
- `GeometryObjectValue.geometryObject()` liefert bereits Deep-Copy → OK

Konkret:
```java
// Feld hinzufügen:
private final GeometryValueCopier copier = new GeometryValueCopier();
private final GeometryCompatibilityService compatibilityService = new GeometryCompatibilityService();

// In denormalize(), GeometryObjectValue-Zweig:
if (geometry instanceof GeometryObjectValue gov) {
    if (!isCompatibleGeometryType(gov.geometryType(), targetType)) {
        return null;
    }
    return copier.deepCopy(gov.geometryObject());
}
```

---

#### 5. `InterlisIoFactory` optional erweitern
**Änderung:** Falls SPEC-konform gewünscht, `TransferFormat` als alternativen Parameter akzeptieren:

```java
public IoxReader createReader(Path path, TransferFormat format, TransferDescription td);
public IoxWriter createWriter(Path path, TransferFormat format, TransferDescription td, DiagnosticCollector diag);
```

**Empfehlung:** Als optionale Überladung, nicht als Ersatz. Bestehende Signatur bleibt erhalten (Dateiendungserkennung).

---

### B. Neue Tests

Alle Tests mit `@TempDir` und `ilivalidator`-Validierung.

#### 6. `GeometryDeepCopyTest`
**Pfad:** `src/test/java/guru/interlis/transformer/geometry/GeometryDeepCopyTest.java`

Testfälle:
- `deepCopyNullReturnsNull` — null input → null
- `deepCopyCoordIsIndependent` — COORD kopieren, Original mutieren, Kopie unverändert
- `deepCopyPolylineIsIndependent` — POLYLINE kopieren, Sequence im Original entfernen, Kopie intakt
- `deepCopySurfaceIsIndependent` — SURFACE kopieren, Boundary im Original ändern
- `deepCopyAreaIsIndependent` — AREA kopieren, gleicher Test
- `deepCopyMultipleRoundsStillCorrect` — doppelte Tiefkopie liefert identische Struktur

---

#### 7. `GeometryCompatibilityServiceTest`
**Pfad:** `src/test/java/guru/interlis/transformer/geometry/GeometryCompatibilityServiceTest.java`

Testfälle:
- `sameTypeIsCompatible` — COORD→COORD, POLYLINE→POLYLINE, etc.
- `surfaceToAreaCompatible` — SURFACE→AREA
- `areaToSurfaceCompatible` — AREA→SURFACE
- `coordToPolylineIncompatible` — COORD→POLYLINE
- `polylineToSurfaceIncompatible` — POLYLINE→SURFACE
- `textToGeometryIncompatible` — TEXT→COORD

(Dimension- und Domain-Prüfung erfordert echte `AttributeDef`-Objekte aus kompilierten Modellen → Integrationstest)

---

#### 8. `CoordRoundtripTest`
**Pfad:** `src/integrationTest/java/guru/interlis/transformer/CoordRoundtripTest.java`

Ablauf:
1. COORD-IomObject bauen (LV95-Koordinaten)
2. In XTF schreiben mit `XtfWriter` + `dmav-geom-test`-Modell
3. Ausgabe mit `ilivalidator` prüfen
4. XTF wieder einlesen mit `Xtf24Reader`
5. COORD-Werte vergleichen (C1, C2)
6. Zweiten Durchlauf mit ITF (via `ItfGeometryWriter`)

---

#### 9. `PolylineRoundtripTest`
**Pfad:** `src/integrationTest/java/guru/interlis/transformer/PolylineRoundtripTest.java`

Ablauf:
1. POLYLINE mit Geraden-Segmenten bauen
2. ITF schreiben → validieren → lesen → Geometrie vergleichen
3. ITF schreiben mit ARC → validieren → lesen → ARC erhalten
4. XTF schreiben → validieren → lesen

---

#### 10. `XtfReadOwnOutputTest`
**Pfad:** `src/integrationTest/java/guru/interlis/transformer/XtfReadOwnOutputTest.java`

Ablauf:
1. Oberflächen-Objekt mit SURFACE bauen
2. Via TransformationEngine in XTF transformieren
3. XTF-Output mit `ilivalidator` prüfen
4. XTF-Output mit `Xtf24Reader` + Modellkontext wieder einlesen
5. Sicherstellen: keine `IoxSyntaxException`
6. Gelesene Objekte semantisch vergleichen

---

#### 11. `RealDatasetGeometrySmokeTest`
**Pfad:** `src/integrationTest/java/guru/interlis/transformer/RealDatasetGeometrySmokeTest.java`

Ablauf:
1. DM01-Modell (`DM01AVSO24LV95`) kompilieren
2. `DM01-AV-CH.itf` einlesen mit `ItfReader2`
3. Objekte zählen, die Geometrie-Attribute haben
4. Geometrietypen pro Klasse inventarisieren (COORD, POLYLINE, SURFACE, AREA)
5. DMAV-Modell kompilieren
6. `DMAVTYM_Alles_V1_1.xtf` einlesen mit `Xtf24Reader`
7. Gleiche Inventarisierung
8. Keine `IoxException` beim Lesen — Smoke-Test bestanden

Tag: `@Tag("real-data")`

---

#### 12. `GeometryTypeMismatchTest`
**Pfad:** `src/integrationTest/java/guru/interlis/transformer/GeometryTypeMismatchTest.java`

Testfälle (mit echten Modellen + Mapping-Compiler):
- COORD-Quelle auf SURFACE-Ziel → Compiler-Diagnostic erwartet
- POLYLINE-Quelle auf AREA-Ziel → Compiler-Diagnostic erwartet
- SURFACE-Quelle auf AREA-Ziel → OK (kompatibel)
- AREA-Quelle auf SURFACE-Ziel → OK (kompatibel)

---

### C. Änderungen an bestehenden Dateien

| Datei | Änderung |
|---|---|
| `IoxGeometryAdapter.java` | `GeometryValueCopier` + `GeometryCompatibilityService` einbinden |
| `DiagnosticCode.java` | Neue Codes: `GEOM_DIMENSION_MISMATCH`, `GEOM_COORD_DOMAIN_MISMATCH` |
| `FeatureMatrix.java` (falls `FeatureEntry` statisch) | Phase-24-Einträge ergänzen |

---

### D. Abnahmekriterien (laut SPEC)

- ✅ Eigener XTF-Output kann mit Modellkontext wieder eingelesen werden
- ✅ Keine `IoxSyntaxException` für gültigen eigenen Output
- ✅ Geometrieobjekte werden tief kopiert (`GeometryValueCopier.deepCopy()`)
- ✅ Reale AREA-/SURFACE-Daten aus dem DM01-Datensatz können gelesen werden
- ✅ Bekannte Nichtunterstützung ist explizit (Diagnostics für LINEATTR, Multi-Sequence, etc.)
- ✅ Alle Tests mit `ilivalidator` geprüft
- ✅ `./gradlew clean check` grün

---

### E. Ausführungsreihenfolge

```
1. GeometryValueCopier         (main + test)
2. GeometryCompatibility       (Record)
3. GeometryCompatibilityService (main + test)
4. IoxGeometryAdapter anpassen
5. DiagnosticCode ergänzen
6. CoordRoundtripTest
7. PolylineRoundtripTest
8. GeometryTypeMismatchTest
9. XtfReadOwnOutputTest
10. RealDatasetGeometrySmokeTest
11. FeatureMatrix aktualisieren
12. ./gradlew clean check
13. docs/dev/phases/phase-24.md schreiben
```

---

### F. Nicht in dieser Phase

- Topologiereparatur
- Koordinatentransformation
- Generalisierung
- Räumliche Operationen
- LINEATTR (bereits als `GEOM_LINEATTR_UNSUPPORTED` diagnostiziert)
- COORD 3D vollständig (nur Kompatibilitätserkennung)
- Separate INTERLIS 2.3/2.4 XML-Implementierung (explizit verboten)
