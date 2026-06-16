# Topic-Workflow: DM01 ↔ DMAV Transformation

Für jedes Topic wird dieser Workflow durchlaufen. Eine Transformation gilt als **erledigt**, wenn die synthetischen Integrationstests, die minimalen validierten Fixtures und die relevanten `realDataTest`-Regressionen für beide Richtungen grün sind.

## Voraussetzungen

- ilivalidator verfügbar unter `/Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar`
- Modelle unter `src/test/data/av/models/` (oder `https://models.interlis.ch`)
- `./gradlew build` läuft erfolgreich

## Workflow pro Topic

### Schritt 1: Profil prüfen oder erstellen

```
profiles/dm01-to-dmav/1.1/<topic>.yaml
profiles/dmav-to-dm01/1.1/<topic>.yaml
```

Referenz: existierende Profile für LFP3 und BB.

### Schritt 2: Testmodelle erstellen (falls noch nicht vorhanden)

```
src/test/data/models/dm01-<topic>-test.ili    (INTERLIS 2.4, vereinfacht)
src/test/data/models/dmav-<topic>-test.ili    (INTERLIS 2.4, vereinfacht)
```

Die Testmodelle enthalten nur die relevanten Klassen/Structs, vereinfachte Typen (z.B. `TEXT*30` statt `GeometryCHLV95_V2.Coord2` für Geometrie im Test).

### Schritt 3: Test-Mappings erstellen

```
src/test/resources/mappings/dm01-to-dmav-<topic>-test.yaml
src/test/resources/mappings/dmav-to-dm01-<topic>-test.yaml
```

Basierend auf dem Profil, aber mit Testmodell-Klassennamen und vereinfachten Ausdrücken.

### Schritt 4: Integrationstests schreiben

```
src/integrationTest/java/guru/interlis/transformer/Dm01ToDmav<Topic>IntegrationTest.java
src/integrationTest/java/guru/interlis/transformer/DmavToDm01<Topic>IntegrationTest.java
```

Tests bauen IOM-Objekte programmatisch, führen Transformation aus, prüfen Ergebnis-Assertions und validieren Output via `InProcessIlivalidatorService`.

### Schritt 5: Transformation ausführen

```bash
# DM01 → DMAV
./gradlew run --args="transform \
  --mapping profiles/dm01-to-dmav/1.1/<topic>.yaml \
  --modeldir 'src/test/data/av/models/;https://models.interlis.ch' \
  --validate"

# DMAV → DM01
./gradlew run --args="transform \
  --mapping profiles/dmav-to-dm01/1.1/<topic>.yaml \
  --modeldir 'src/test/data/av/models/;https://models.interlis.ch' \
  --validate"
```

### Schritt 6: Output mit ilivalidator prüfen

```bash
# DM01 Output prüfen (ITF)
java -jar /Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar \
  --modeldir 'src/test/data/av/models/;https://models.interlis.ch' \
  build/out/<topic>.itf

# DMAV Output prüfen (XTF)
java -jar /Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar \
  --modeldir 'src/test/data/av/models/;https://models.interlis.ch' \
  build/out/<topic>.xtf
```

### Schritt 7: Topic-Fixtures anlegen

```
src/test/resources/fixtures/dm01-dmav/<topic>/dm01-minimal.itf
src/test/resources/fixtures/dm01-dmav/<topic>/dmav-minimal.xtf
src/test/resources/fixtures/dm01-dmav/<topic>/dm01-real-extract.itf
src/test/resources/fixtures/dm01-dmav/<topic>/dmav-real-extract.xtf
```

`*-minimal` sind kuratierte, kleine Roundtrip-/Validator-Fixtures. `*-real-extract` werden aus den vollständigen realen Datensätzen extrahiert und über eigene Extractor-Tests evidenzgeführt.

Jeder Fixture muss mit ilivalidator geprüft sein (Schritt 6). Minimal bedeutet: 1-2 Objekte pro Klasse, die alle Mapping-Regeln abdecken. Die zugehörigen Fixture-Extraktionstests validieren standardmässig nur im Temp-Verzeichnis; ein Update der eingecheckten `real-extract`-Dateien erfolgt bewusst mit `-PupdateFixtures=true`.

### Schritt 8: Matrix aktualisieren

In `docs/dm01-dmav/status-matrix.md` das Topic erst dann auf ✅ setzen, wenn auch die relevanten `realDataTest`-Gates grün sind. Andernfalls `⚠️` verwenden.

### Schritt 9: Lossiness dokumentieren (falls Rückrichtung)

In `docs/dm01-dmav/lossiness.md` dokumentieren, welche Informationen bei DMAV→DM01 verloren gehen.

## Checkliste pro Topic

- [ ] **Profil** dm01-to-dmav vorhanden und kompilierbar
- [ ] **Profil** dmav-to-dm01 vorhanden und kompilierbar
- [ ] **Testmodell** dm01 vorhanden
- [ ] **Testmodell** dmav vorhanden
- [ ] **Test-Mapping** dm01-to-dmav vorhanden
- [ ] **Test-Mapping** dmav-to-dm01 vorhanden
- [ ] **Integrationstest** DM01→DMAV grün
- [ ] **Integrationstest** DMAV→DM01 grün
- [ ] **ilivalidator** DM01-Output validiert
- [ ] **ilivalidator** DMAV-Output validiert
- [ ] **Minimal-Fixture** DM01-ITF valide
- [ ] **Minimal-Fixture** DMAV-XTF valide
- [ ] **Real-Extract-Fixture** DM01-ITF valide
- [ ] **Real-Extract-Fixture** DMAV-XTF valide
- [ ] **realDataTest** Minimal-Roundtrip-Gate grün
- [ ] **realDataTest** Extractor-/Fixture-Validation grün
- [ ] **realDataTest** Forward-/End-to-End-Gate grün
- [ ] **realDataTest** Reverse-/Roundtrip-Gate grün
- [ ] **Status-Matrix** aktualisiert
- [ ] **Lossiness** dokumentiert (DMAV→DM01)

## Übersicht Topics

| Topic | DM01→DMAV | DMAV→DM01 | Tests | ITF-Fixture | XTF-Fixture |
|---|---|---|---|---|
| LFP3 | ✅ | ✅ | ✅ | ✅ | ✅ |
| BB | ✅ | ✅ | ✅ | ✅ | ✅ |
| HFP3 | ✅ | ✅ | ✅ | ✅ | ✅ |
| Grenzpunkt | ❌ | ❌ | ❌ | ❌ | ❌ |
| Grundstück/Liegenschaft | ❌ | ❌ | ❌ | ❌ | ❌ |
| Einzelobjekte | ❌ | ❌ | ❌ | ❌ | ❌ |

Legende: ✅=Profil + Integration + Fixtures + reale Regression grün, ⚠️=Integration/Fixtures grün aber reale Regression noch unvollständig, 🔧=Profil vorhanden, ❌=offen

## Bekannte Einschränkungen

### XTF: Topic/Klassen-Namenskollision "Bodenbedeckung"
Da Topic und Klasse denselben Namen haben, muss die Klasse im XTF als `Bodenbedeckung.Bodenbedeckung` geschrieben werden (Topic.Class-Notation). `BBNachfuehrung` und `Messpunkt` sind nicht betroffen.
