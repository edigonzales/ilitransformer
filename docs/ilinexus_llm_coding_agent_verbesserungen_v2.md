# ilitransformer / ilinexus: priorisierte Verbesserungs- und Änderungsvorschläge für einen LLM-Coding-Agenten

**Zielgruppe:** LLM-Coding-Agent, der das Repository schrittweise umbauen und härten soll.  
**Ausgangslage:** Analyse der ZIP-Datei `ilinexus.zip` aus der ChatGPT-Unterhaltung. Der Clone von GitHub war in der Ausführungsumgebung wegen DNS-Problemen nicht möglich. Gradle-Tests konnten ebenfalls nicht ausgeführt werden, weil der Gradle Wrapper `gradle-9.0-bin.zip` von `services.gradle.org` herunterladen wollte und DNS ebenfalls fehlschlug.  
**Repo-Zustand laut statischer Analyse:** Java/Gradle-Projekt mit generischer INTERLIS-Transformationsengine, DM01↔DMAV-Profilen, CLI, DSL, Unit-/Integration-/RealData-Tests und umfangreicher Dokumentation.

---

## 0. Wichtiges Gesamtziel

Das Repository soll klar als **generische, modellbewusste INTERLIS-Transformationsengine** wahrgenommen werden. DM01↔DMAV ist ein wichtiges, produktionsnahes **Produktprofil / Referenzprodukt**, aber **nicht der generische Kern**.

Der aktuelle Code enthält bereits viele gute generische Komponenten:

- `guru.interlis.transformer.app.JobRunner`
- `guru.interlis.transformer.engine.TransformationEngine`
- `guru.interlis.transformer.mapping.compiler.MappingCompiler`
- `guru.interlis.transformer.model.ModelRegistry`
- `guru.interlis.transformer.model.TypeSystemFacade`
- `guru.interlis.transformer.expr.ExpressionEngine`
- `guru.interlis.transformer.state.StateStore`
- `guru.interlis.transformer.state.ReferenceIndex`
- `guru.interlis.transformer.engine.BagTransformationService`
- `guru.interlis.transformer.validation.InProcessIlivalidatorService`

Aber einige DM01/DMAV-spezifische Klassen und Commands hängen noch zu stark am generischen Kern.

---

## 1. Arbeitsweise für den Coding-Agenten

Bitte bei jeder Phase so arbeiten:

1. **Vor Änderung:** relevante bestehende Tests lesen.
2. **Kleine Commits:** pro Phase oder Teilphase einen Commit.
3. **Keine Funktionalität entfernen, ohne Tests anzupassen.**
4. **Bestehende produktive Profile nicht semantisch ändern**, ausser es ist explizit Teil der Phase.
5. **Doku und Feature-Matrix synchron halten.**
6. **Nach jeder Phase ausführen:**
   ```bash
   ./gradlew test
   ./gradlew integrationTest
   ./gradlew check
   ```
7. **Für Profil-/Echtdatenänderungen zusätzlich:**
   ```bash
   ./gradlew realDataTest
   ```
8. Wenn Java 25 lokal nicht verfügbar ist, zuerst Phase 7 beachten oder mit der vorhandenen Toolchain des Projekts arbeiten.


## 1.1 Repository-Skills zwingend berücksichtigen

Das Repository enthält eigene Agenten-Skills unter `.skills/`. Diese Datei ist für einen LLM-Coding-Agenten gedacht; deshalb muss der Agent **vor jeder Phase** zuerst die globalen Repo-Anweisungen und dann die relevanten Skill-Dateien lesen.

### Immer zuerst lesen

```text
AGENTS.md
```

Danach je nach Änderung mindestens eine der folgenden Skill-Dateien:

```text
.skills/java-test-gap/SKILL.md
.skills/gradle-verification/SKILL.md
.skills/done-and-commit/SKILL.md
.skills/mapping-dsl-change/SKILL.md
.skills/interlis-validation/SKILL.md
.skills/interlis1-testdata/SKILL.md
.skills/dm01-dmav-real-data-gate/SKILL.md
.skills/architecture-boundary-review/SKILL.md
```

### Skill-Trigger-Matrix

| Änderungstyp | Muss gelesen/angewendet werden |
|---|---|
| Jede Änderung an produktivem Java-Code | `.skills/java-test-gap/SKILL.md`, `.skills/gradle-verification/SKILL.md`, `.skills/done-and-commit/SKILL.md` |
| Änderung an `JobConfig`, `MappingLoader`, `MappingCompiler`, `TransformPlan`, Expression-Syntax, Function Registry, YAML-/`.ilimap`-Semantik | `.skills/mapping-dsl-change/SKILL.md`, zusätzlich Java-/Gradle-/Commit-Skills |
| Änderung an `.ili`, `.itf`, `.xtf`, `.xml`, Mapping-Profilen oder Tests, die INTERLIS-Transferdaten erzeugen | `.skills/interlis-validation/SKILL.md`, zusätzlich Java-/Gradle-/Commit-Skills |
| Änderung an INTERLIS-1-Modellen, ITF-Fixtures, AREA/SURFACE-Testdaten oder Java-Code, der ITF-Testdaten generiert | `.skills/interlis1-testdata/SKILL.md`, `.skills/interlis-validation/SKILL.md` |
| Änderung an DM01↔DMAV-Profilen, I/O, Geometrie, Referenzen, OID, Baskets, Mapping Compiler, Expression-Auswertung oder ilivalidator-Integration mit Bezug auf DM01↔DMAV | `.skills/dm01-dmav-real-data-gate/SKILL.md`, `.skills/interlis-validation/SKILL.md` |
| Änderung an Package-/Modulgrenzen, Verschieben von DM01/DMAV-Code, Entkopplung generischer Engine von Produktprofilen | `.skills/architecture-boundary-review/SKILL.md` |
| Vor jedem Commit | `.skills/done-and-commit/SKILL.md` |

### Verbindliche Agenten-Regeln aus den Skills

Der Agent darf **nicht** behaupten, Tests seien erfolgreich, wenn der exakte Befehl nicht ausgeführt wurde. Falls Tests wegen fehlendem Java, fehlendem Gradle-Download, DNS, fehlenden lokalen Tools oder fehlenden Testdaten nicht ausgeführt werden können, muss der Agent das offen als Blocker melden und darf keinen grünen Zustand behaupten.

Bei Java-Änderungen muss der Agent zuerst die bestehende Testabdeckung prüfen und die kleinsten sinnvollen Tests identifizieren. Für Änderungen an produktivem Java-Code sind mindestens ein Happy Path und, falls sinnvoll, ein Failure Path oder Boundary Case zu prüfen.

Bei DSL-Änderungen muss der Agent Loader-/Parser-Tests, Compiler-Diagnostic-Tests und bei Semantikänderungen Runtime-Tests ergänzen. Die Dokumentation (`docs/mapping-dsl.md`, Beispiele, Feature-Matrix) muss im selben Änderungspaket angepasst werden.

Bei INTERLIS-Artefakten gilt: `.ili` mit `ili2c` prüfen; `.itf`, `.xtf` und relevante XML-/Transfer-Artefakte mit `ilivalidator` prüfen. Für INTERLIS-1-/ITF-/AREA-Testdaten darf der Agent keine Syntax aus dem Gedächtnis erfinden, sondern muss bestehende validierte Fixtures, offizielle Modelle oder Snippets aus `.skills/interlis1-testdata/snippets/` verwenden.

Bei DM01↔DMAV-Änderungen darf der Agent Support nicht nur auf Basis synthetischer Tests behaupten. Für Änderungen an I/O, Geometrie, Referenzen, OID, Baskets, Mapping Compiler, Expression-Auswertung, ilivalidator-Integration oder DM01/DMAV-Profilen muss zusätzlich ein Real-Data-Gate geprüft werden, mindestens über die kleinste relevante `realDataTest`-Klasse; bei breiten Änderungen über `./gradlew realDataTest`.

Bei Architektur-/Boundary-Änderungen muss der Agent bestätigen, dass der generische Engine-Kern nicht von DM01/DMAV-spezifischen Packages abhängt. Falls stabile Grenzen existieren, soll der Agent ArchUnit- oder vergleichbare Architekturtests vorschlagen bzw. ergänzen.

### Skill-Zuordnung zu den Phasen dieses Dokuments

| Phase | Zusätzlich verpflichtende Skills |
|---|---|
| P0 Runtime-/CLI-Konsistenz | `java-test-gap`, `gradle-verification`, `done-and-commit` |
| P1 DM01/DMAV als Produktmodul trennen | `architecture-boundary-review`, `java-test-gap`, `gradle-verification`, `done-and-commit` |
| P1 Doku-/Code-Drift bei DSL/Feature-Matrix | `mapping-dsl-change`, bei Profilen zusätzlich `interlis-validation` |
| P1 Performance / RuleDispatchIndex / StateStore | `java-test-gap`, bei DM01/DMAV-Effekten zusätzlich `dm01-dmav-real-data-gate` |
| P2 MappingCompiler modularisieren | `mapping-dsl-change`, `java-test-gap`, `gradle-verification`, `done-and-commit` |
| P2 Java 21 / CI / Build | `gradle-verification`, `done-and-commit` |
| P3 Doku-/Produktstruktur | `architecture-boundary-review`; bei DSL-Doku zusätzlich `mapping-dsl-change` |

Diese Skill-Anweisungen haben Vorrang vor generischen Empfehlungen in diesem Dokument, sofern sie konkretere Prüf- oder Stop-Regeln enthalten.

---

# Phase P0: Kritische Runtime- und CLI-Konsistenzfehler beheben

Diese Phase hat höchste Priorität, weil sie direkt Verhalten betrifft, das CLI/Doku/Feature-Matrix bereits versprechen.

---

## P0.1 `--fail-policy` wirksam machen oder entfernen

### Problem

In `CliMain.TransformCommand` existiert eine CLI-Option:

```java
@Option(
    names = {"--fail-policy"},
    description = "Error handling policy: strict, lenient, or report_only (default: strict)"
)
private String failPolicy;
```

Datei:

```text
src/main/java/guru/interlis/transformer/app/CliMain.java
```

Methode / Klasse:

```text
guru.interlis.transformer.app.CliMain.TransformCommand.call()
```

Aktuell wird `failPolicy` beim Bau von `RunOptions` nicht verwendet:

```java
RunOptions options = new RunOptions(
        modeldirs != null ? modeldirs : List.of(),
        validate,
        report,
        keepTemp);
```

`RunOptions` enthält auch kein Feld für eine Fail-Policy:

```java
public record RunOptions(
        List<String> modelDirectories,
        boolean validateOutput,
        Path reportDirectory,
        boolean keepTemporaryFiles
)
```

Datei:

```text
src/main/java/guru/interlis/transformer/app/RunOptions.java
```

Gleichzeitig behauptet `FeatureMatrix`, dass `--fail-policy` Wirkung hat:

```text
CLI --fail-policy / --keep-temp / --validate / --report
Alle CLI-Optionen haben Wirkung
```

Datei:

```text
src/main/java/guru/interlis/transformer/feature/FeatureMatrix.java
```

### Ziel

`--fail-policy` soll die YAML-Angabe `job.failPolicy` optional überschreiben. Alternativ soll die Option entfernt werden. Empfohlen ist **Überschreiben**, weil die Option bereits existiert und nützlich ist.

### Konkrete Änderung

#### 1. `RunOptions` erweitern

Datei:

```text
src/main/java/guru/interlis/transformer/app/RunOptions.java
```

Vorschlag:

```java
package guru.interlis.transformer.app;

import guru.interlis.transformer.mapping.plan.FailPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record RunOptions(
        List<String> modelDirectories,
        boolean validateOutput,
        Path reportDirectory,
        boolean keepTemporaryFiles,
        FailPolicy failPolicyOverride
) {
    public RunOptions {
        modelDirectories = modelDirectories != null
                ? List.copyOf(modelDirectories) : List.of();
    }

    public RunOptions() {
        this(List.of(), false, null, false, null);
    }

    public RunOptions(List<String> modelDirectories) {
        this(modelDirectories, false, null, false, null);
    }

    public RunOptions(List<String> modelDirectories,
                      boolean validateOutput,
                      Path reportDirectory,
                      boolean keepTemporaryFiles) {
        this(modelDirectories, validateOutput, reportDirectory, keepTemporaryFiles, null);
    }

    public Optional<FailPolicy> failPolicyOverrideOptional() {
        return Optional.ofNullable(failPolicyOverride);
    }
}
```

Wichtig: Die alten Konstruktoren beibehalten, damit bestehende Tests und Code nicht brechen.

#### 2. Parser für CLI-FailPolicy ergänzen

Der Parser liegt aktuell privat in `MappingCompiler`:

```java
private static FailPolicy parseFailPolicy(String failPolicy)
```

Datei:

```text
src/main/java/guru/interlis/transformer/mapping/compiler/MappingCompiler.java
```

Das ist für CLI-Wiederverwendung ungünstig. Bitte auslagern in eine kleine Utility-Klasse, z. B.:

```text
src/main/java/guru/interlis/transformer/mapping/plan/FailPolicyParser.java
```

Beispiel:

```java
package guru.interlis.transformer.mapping.plan;

public final class FailPolicyParser {
    private FailPolicyParser() {}

    public static FailPolicy parseOrDefault(String value, FailPolicy defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        if (value.equalsIgnoreCase("strict")) return FailPolicy.STRICT;
        if (value.equalsIgnoreCase("lenient")) return FailPolicy.LENIENT;
        if (value.equalsIgnoreCase("report_only") || value.equalsIgnoreCase("reportOnly")) {
            return FailPolicy.REPORT_ONLY;
        }
        throw new IllegalArgumentException("Unknown failPolicy: " + value
                + " (valid values: strict, lenient, report_only/reportOnly)");
    }
}
```

Dann `MappingCompiler.parseFailPolicy(...)` durch diese Klasse ersetzen oder delegieren.

#### 3. `CliMain.TransformCommand.call()` anpassen

Datei:

```text
src/main/java/guru/interlis/transformer/app/CliMain.java
```

Import ergänzen:

```java
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.FailPolicyParser;
```

In `call()`:

```java
FailPolicy override = null;
if (failPolicy != null && !failPolicy.isBlank()) {
    try {
        override = FailPolicyParser.parseOrDefault(failPolicy, null);
    } catch (IllegalArgumentException e) {
        System.err.println(e.getMessage());
        return 1;
    }
}

RunOptions options = new RunOptions(
        modeldirs != null ? modeldirs : List.of(),
        validate,
        report,
        keepTemp,
        override);
```

#### 4. `JobRunner.prepare()` oder `JobRunner.run()` muss Override anwenden

Datei:

```text
src/main/java/guru/interlis/transformer/app/JobRunner.java
```

Aktuell:

```java
TransformPlan plan = new MappingCompiler().compileTyped(config, modelRegistry);
return new PreparedJob(config, plan, modelRegistry, baseDirectory);
```

`TransformPlan` ist vermutlich ein Record oder Immutable-Klasse. Falls es ein Record ist, am besten eine Methode/Factory ergänzen:

Datei prüfen:

```text
src/main/java/guru/interlis/transformer/mapping/plan/TransformPlan.java
```

Ziel:

```java
if (options.failPolicyOverride() != null) {
    plan = plan.withFailPolicy(options.failPolicyOverride());
}
```

Falls `TransformPlan` ein Record ist, Methode ergänzen:

```java
public TransformPlan withFailPolicy(FailPolicy failPolicy) {
    return new TransformPlan(
            jobName,
            direction,
            failPolicy,
            compileMode,
            rules,
            inputsById,
            outputsById,
            diagnostics,
            oidPlan,
            basketPlan,
            enumMaps
    );
}
```

Wenn Feldnamen abweichen, entsprechend anpassen.

### Tests

Neue oder anzupassende Tests:

```text
src/integrationTest/java/guru/interlis/transformer/FailPolicyCliOverrideTest.java
src/test/java/guru/interlis/transformer/CliMainTest.java
src/test/java/guru/interlis/transformer/feature/FeatureMatrixTest.java
```

Testfälle:

1. YAML `job.failPolicy: strict`, CLI `--fail-policy lenient` → Plan/Run verwendet LENIENT.
2. YAML `job.failPolicy: lenient`, CLI `--fail-policy strict` → Plan/Run verwendet STRICT.
3. CLI `--fail-policy invalid` → Exit-Code 1, verständliche Fehlermeldung.
4. Ohne CLI-Option bleibt YAML-Verhalten unverändert.

### Definition of Done

- `--fail-policy` hat nachweislich Wirkung.
- Feature-Matrix-Aussage ist wahr.
- Alte Tests laufen weiterhin.
- README/CLI-Doku beschreibt die Override-Semantik.

---

## P0.2 `--keep-temp` wirklich funktionsfähig machen

### Problem

`TransactionalOutputManager.rollbackAll()` löscht alle temporären Dateien immer, auch wenn `keepTemporaryFiles == true` ist:

```java
for (Path tempPath : tempPathsByOutputId.values()) {
    Files.deleteIfExists(tempPath);
}
tempPathsByOutputId.clear();
if (!keepTemporaryFiles) {
    Files.deleteIfExists(tempDir);
}
```

Datei:

```text
src/main/java/guru/interlis/transformer/app/TransactionalOutputManager.java
```

CLI-Doku/Option sagt aber:

```text
Keep temporary output files on failure for debugging
```

Datei:

```text
src/main/java/guru/interlis/transformer/app/CliMain.java
```

### Ziel

Bei `--keep-temp` müssen temporäre Output-Dateien erhalten bleiben und für den Benutzer auffindbar sein.

### Konkrete Änderung

#### 1. `rollbackAll()` ändern

Vorschlag:

```java
public void rollbackAll() {
    if (keepTemporaryFiles) {
        // Keep files and directory for debugging. Do not clear tempPathsByOutputId,
        // so callers can still inspect tempPath(outputId) before close completes.
        return;
    }

    for (Path tempPath : tempPathsByOutputId.values()) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException ignore) {
        }
    }
    tempPathsByOutputId.clear();
    try {
        Files.deleteIfExists(tempDir);
    } catch (IOException ignore) {
    }
}
```

Optional besser:

```java
public List<Path> retainedTemporaryFiles() { ... }
```

#### 2. `JobRunner` soll Pfade in Diagnostic oder stdout nennen

Datei:

```text
src/main/java/guru/interlis/transformer/app/JobRunner.java
```

Beim Rollback:

```java
engineDiag.add(new Diagnostic(DiagnosticCode.COMMIT_ROLLED_BACK,
        Severity.ERROR,
        "Output rolled back due to errors ...",
        null,
        options.keepTemporaryFiles()
                ? "Temporary files kept in " + txManager.tempDir()
                : "Fix errors and retry, or use --keep-temp to inspect temporary files"));
```

### Tests

Datei existiert bereits:

```text
src/test/java/guru/interlis/transformer/TransactionalOutputManagerTest.java
```

Ergänzen:

1. `rollbackAllDeletesTempFilesWhenKeepTempFalse()`
2. `rollbackAllKeepsTempFilesWhenKeepTempTrue()`
3. `closeKeepsTempFilesWhenKeepTempTrue()`
4. `tempDirIsReportedOrAccessibleAfterRollback()`

### Definition of Done

- Bei Fehler + `--keep-temp` bleiben temporäre `.xtf`/`.itf`-Dateien erhalten.
- Diagnostic oder stdout nennt das Temp-Verzeichnis.
- Test deckt das Verhalten ab.

---

## P0.3 `TransactionalOutputManager.commit()` robuster machen

### Problem

Aktueller Code:

```java
Files.createDirectories(targetPath.getParent());
Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
```

Risiken:

1. `targetPath.getParent()` kann `null` sein, wenn Ziel `out.xtf` im aktuellen Verzeichnis ist.
2. `ATOMIC_MOVE` kann fehlschlagen, wenn Temp-Datei und Ziel auf verschiedenen Filesystemen liegen.
3. Temp-Dateien werden aktuell im System-Temp erstellt, nicht im Zielverzeichnis. Dadurch ist `ATOMIC_MOVE` unnötig riskant.

### Ziel

Robuster transaktionaler Output, der auch für relative Dateien und unterschiedliche Filesysteme funktioniert.

### Konkrete Änderung

Datei:

```text
src/main/java/guru/interlis/transformer/app/TransactionalOutputManager.java
```

#### Variante A: Temp-Datei im Zielverzeichnis erzeugen

In `createTemporaryOutput(OutputBinding binding)`:

```java
Path targetPath = binding.path();
Path targetParent = targetPath != null ? targetPath.toAbsolutePath().getParent() : null;
Path tempParent = targetParent != null ? targetParent : tempDir;
Files.createDirectories(tempParent);
Path tempPath = Files.createTempFile(tempParent, baseName + ".", extension + ".tmp");
```

Vorteil: `ATOMIC_MOVE` funktioniert eher, weil Temp und Ziel auf demselben Filesystem liegen.

#### Variante B: Fallback bei nicht unterstütztem Atomic Move

```java
try {
    Files.move(tempPath, targetPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
} catch (AtomicMoveNotSupportedException e) {
    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
}
```

Import:

```java
import java.nio.file.AtomicMoveNotSupportedException;
```

#### Parent-Null behandeln

```java
Path parent = targetPath.toAbsolutePath().getParent();
if (parent != null) {
    Files.createDirectories(parent);
}
```

### Tests

Datei:

```text
src/test/java/guru/interlis/transformer/TransactionalOutputManagerTest.java
```

Ergänzen:

1. `commitSupportsRelativeTargetWithoutParent()`
2. `commitCreatesMissingParentDirectory()`
3. `commitRemovesTempPathAfterSuccess()`

### Definition of Done

- Commit nach `out.xtf` im aktuellen Verzeichnis funktioniert.
- Commit nach `build/out/out.xtf` erzeugt Parent-Verzeichnis.
- Fallback bei nicht unterstütztem `ATOMIC_MOVE` existiert.

---

## P0.4 `InMemoryStateStore` muss `outputId` im Target-Key berücksichtigen

### Problem

`TargetObjectKey` enthält vermutlich `outputId`, aber `InMemoryStateStore.targetKey(...)` ignoriert es:

```java
private static String targetKey(TargetObjectKey key) {
    return key.targetClass() + "::" + key.targetOid();
}
```

Datei:

```text
src/main/java/guru/interlis/transformer/state/InMemoryStateStore.java
```

Dadurch kollidieren gleiche Zielklasse/OID über verschiedene Outputs. Das ist gefährlich, wenn Multi-Output ein Feature ist.

### Ziel

Target-Registrierung muss pro Output eindeutig sein. Gleichzeitig darf Legacy-Code, der `outputId == null` verwendet, nicht unnötig brechen.

### Konkrete Änderung

```java
private static String targetKey(TargetObjectKey key) {
    return (key.outputId() == null ? "" : key.outputId())
            + "::" + key.targetClass()
            + "::" + key.targetOid();
}
```

Prüfen, ob `findTargetObject(String targetClass, String targetOid)` weiterhin korrekt funktioniert. Diese Methode baut aktuell:

```java
return findTarget(new TargetObjectKey(null, targetClass, targetOid));
```

Wenn Targets künftig mit OutputId registriert sind, findet `findTargetObject(...)` sie nicht mehr. Daher entweder:

#### Option A: `findTargetObject` nur legacy lassen

Dann prüfen, ob neue Runtime überall `findTarget(TargetObjectKey)` nutzt.

#### Option B: Fallback-Suche implementieren

```java
@Override
public Optional<IomObject> findTargetObject(String targetClass, String targetOid) {
    Optional<IomObject> exactLegacy = findTarget(new TargetObjectKey(null, targetClass, targetOid));
    if (exactLegacy.isPresent()) return exactLegacy;

    return targetIndex.entrySet().stream()
            .filter(e -> e.getKey().endsWith("::" + targetClass + "::" + targetOid))
            .map(Map.Entry::getValue)
            .findFirst();
}
```

Noch besser: Separate strukturierte Map statt String-Key.

### Tests

Datei existiert:

```text
src/test/java/guru/interlis/transformer/StateStoreTest.java
```

Ergänzen:

1. `allowsSameTargetClassAndOidInDifferentOutputs()`
2. `rejectsDuplicateTargetClassAndOidInSameOutput()`
3. `findTargetUsesOutputIdWhenProvided()`
4. `legacyFindTargetObjectStillWorksIfNeeded()`

### Definition of Done

- Multi-Output mit gleicher OID in verschiedenen Outputs kollidiert nicht.
- Duplicate innerhalb desselben Outputs wird weiterhin erkannt.
- Referenzauflösung bleibt grün.

---

# Phase P1: DM01↔DMAV sauber vom generischen Kern trennen

Diese Phase ist wichtig für die langfristige Verständlichkeit des Repos.

---

## P1.1 DM01/DMAV-Leak in `TransferInventoryService` entfernen

### Problem

Generische Klasse:

```text
src/main/java/guru/interlis/transformer/model/TransferInventoryService.java
```

importiert DM01/DMAV-spezifisch:

```java
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
```

und nutzt:

```java
if (Dm01DmavFixtures.isLfp3RelevantClass(tag)) {
    lfp3Classes.add(tag);
}
```

Das ist ein klarer Bruch der Architekturentscheidung: DM01/DMAV darf nicht in generische Engine-/Model-Pfade leaken.

### Ziel

`TransferInventoryService` bleibt vollständig generisch. DM01/DMAV-spezifische Klassifikation wird optional über eine Erweiterung oder einen Decorator eingebracht.

### Konkrete Änderung Option A: Klassifikations-Interface

Neue Datei:

```text
src/main/java/guru/interlis/transformer/model/TransferInventoryClassifier.java
```

```java
package guru.interlis.transformer.model;

import ch.interlis.iom.IomObject;
import java.util.Set;

@FunctionalInterface
public interface TransferInventoryClassifier {
    void classify(IomObject object, ClassificationSink sink);

    interface ClassificationSink {
        void addTag(String category, String value);
    }

    static TransferInventoryClassifier none() {
        return (object, sink) -> { };
    }
}
```

Dann `TransferInventoryService` erweitern:

```java
private final TransferInventoryClassifier classifier;

public TransferInventoryService(IliModelService modelService) {
    this(modelService, TransferInventoryClassifier.none());
}

public TransferInventoryService(IliModelService modelService,
                                TransferInventoryClassifier classifier) {
    this.modelService = modelService;
    this.classifier = classifier != null ? classifier : TransferInventoryClassifier.none();
}
```

Im Loop:

```java
Map<String, Set<String>> classifications = new LinkedHashMap<>();
...
classifier.classify(iom, (category, value) -> classifications
        .computeIfAbsent(category, ignored -> new LinkedHashSet<>())
        .add(value));
```

Dann `TransferInventory` so erweitern, dass generische Klassifikationen möglich sind. Aktuell enthält es offenbar `lfp3Classes`. Das ist ebenfalls DM01/DMAV-spezifisch. Besser:

```java
Map<String, List<String>> classifications
```

Für Backward Compatibility optional:

```java
@Deprecated
public List<String> lfp3Classes() {
    return classifications.getOrDefault("dm01-dmav.lfp3Classes", List.of());
}
```

### Konkrete Änderung Option B: DM01/DMAV-spezifischer Decorator

Wenn `TransferInventory` nicht geändert werden soll, erstelle:

```text
src/main/java/guru/interlis/transformer/dmav/Dm01DmavTransferInventoryService.java
```

Dieser ruft den generischen `TransferInventoryService` auf und ergänzt DM01/DMAV-Auswertungen in einem separaten DMAV-spezifischen Report. Das ist sauberer, wenn `lfp3Classes` ohnehin nur für DM01/DMAV gebraucht wird.

### Tests

Anpassen/ergänzen:

```text
src/test/java/guru/interlis/transformer/model/TransferInventoryServiceTest.java
src/test/java/guru/interlis/transformer/dmav/Dm01DmavTransferInventoryClassifierTest.java
```

Falls keine Tests existieren, neu anlegen.

Testfälle:

1. `TransferInventoryService` funktioniert ohne DM01/DMAV-Klassen.
2. Generischer Inventory-Code importiert nichts aus `guru.interlis.transformer.dmav`.
3. DM01/DMAV-Klassifikation funktioniert separat.

### Akzeptanzprüfung per grep

Nach der Änderung:

```bash
grep -R "guru.interlis.transformer.dmav" -n src/main/java/guru/interlis/transformer/model src/main/java/guru/interlis/transformer/engine src/main/java/guru/interlis/transformer/mapping
```

Sollte keine Treffer liefern.

### Definition of Done

- Generischer Kern importiert keine DM01/DMAV-Klassen.
- DM01/DMAV-Inventory-Funktionalität bleibt erhalten oder ist bewusst in ein Produktmodul verschoben.
- Tests belegen die Trennung.

---

## P1.2 DM01/DMAV CLI-Commands vom generischen CLI-Namespace trennen

### Problem

`CliMain` registriert `ImportCorrelationCommand` direkt als Top-Level-Command:

```java
subcommands = {
    CliMain.TransformCommand.class,
    CliMain.ValidateMappingCommand.class,
    CliMain.ValidateTransferCommand.class,
    InspectModelCommand.class,
    ImportCorrelationCommand.class
}
```

Datei:

```text
src/main/java/guru/interlis/transformer/app/CliMain.java
```

`ImportCorrelationCommand` ist DM01/DMAV-spezifisch. Generisches CLI sollte nicht so wirken, als gehöre DM01/DMAV zum Kern.

### Ziel

Top-Level-CLI bleibt generisch. DM01/DMAV-spezifische Commands werden unter einem Produkt-Subcommand oder in eine separate Produkt-CLI verschoben.

### Konkrete Änderung Variante A: Produkt-Subcommand `dm01-dmav`

Neue Datei:

```text
src/main/java/guru/interlis/transformer/cli/Dm01DmavCommand.java
```

```java
package guru.interlis.transformer.cli;

import picocli.CommandLine.Command;

@Command(
    name = "dm01-dmav",
    description = "DM01 ↔ DMAV product profile utilities",
    mixinStandardHelpOptions = true,
    subcommands = {
        ImportCorrelationCommand.class
    }
)
public final class Dm01DmavCommand implements Runnable {
    @Override
    public void run() {
        new picocli.CommandLine(this).usage(System.out);
    }
}
```

Dann in `CliMain`:

```java
subcommands = {
    CliMain.TransformCommand.class,
    CliMain.ValidateMappingCommand.class,
    CliMain.ValidateTransferCommand.class,
    InspectModelCommand.class,
    Dm01DmavCommand.class
}
```

`ImportCorrelationCommand` ist dann erreichbar als:

```bash
ilitransformer dm01-dmav import-correlation --xlsx ...
```

#### Backward Compatibility

Wenn der alte Top-Level-Command noch erhalten bleiben soll, dann deprecated:

```java
@Command(name = "import-correlation", description = "Deprecated: use dm01-dmav import-correlation")
```

und in der Ausgabe warnen.

### Doku anpassen

Datei:

```text
docs/cli.md
```

Abschnitt `import-correlation` verschieben unter:

```markdown
### dm01-dmav import-correlation
```

### Tests

Anpassen/ergänzen:

```text
src/test/java/guru/interlis/transformer/CliMainTest.java
src/integrationTest/java/guru/interlis/transformer/ImportCorrelationCliTest.java
```

Testfälle:

1. `ilitransformer --help` zeigt generische Commands und `dm01-dmav`.
2. `ilitransformer dm01-dmav --help` zeigt `import-correlation`.
3. Optional: altes `import-correlation` funktioniert deprecated oder ist bewusst entfernt.

### Definition of Done

- Generische CLI wirkt nicht DM01/DMAV-zentriert.
- DM01/DMAV-Tools bleiben erreichbar.
- CLI-Doku ist aktualisiert.

---

## P1.3 Produktprofile aus Root-`profiles/` verschieben oder klar kapseln

### Problem

Aktuell liegen produktive Profile hier:

```text
profiles/dm01-to-dmav/1.1/
profiles/dmav-to-dm01/1.1/
```

Das lässt DM01/DMAV wie den Kern des Repos wirken.

### Ziel

DM01/DMAV wird sichtbar als Produktprofil geführt.

### Konkrete Änderung Variante A: Verschieben

Neue Struktur:

```text
products/dm01-dmav/profiles/dm01-to-dmav/1.1/
products/dm01-dmav/profiles/dmav-to-dm01/1.1/
products/dm01-dmav/docs/
products/dm01-dmav/gradle/
```

Dann anpassen:

- `README.md`
- `docs/testing.md`
- `docs/cli.md`
- `products/dm01-dmav/*` verschieben oder verlinken
- `gradle/dm01-dmav-analysis.gradle`
- `gradle/dm01-dmav-scenarios.gradle`
- `Dm01DmavPaths`
- `Dm01DmavFixtures`
- alle Tests, die Profile referenzieren

### Konkrete Änderung Variante B: Root-`profiles/` behalten, aber generisch erklären

Wenn Verschieben zu invasiv ist, dann mindestens:

```text
profiles/
  README.md                 erklärt: Produktprofile, derzeit dm01-dmav
  dm01-dmav/
    dm01-to-dmav/1.1/
    dmav-to-dm01/1.1/
```

Diese Variante ist weniger sauber, aber schneller.

### Empfohlene Reihenfolge

1. Zuerst `Dm01DmavPaths` zentralisieren.
2. Alle Tests sollen Pfade nur noch über `Dm01DmavPaths` / `Dm01DmavFixtures` beziehen.
3. Dann Profile verschieben.
4. Danach Doku anpassen.

### Tests

Danach ausführen:

```bash
./gradlew test integrationTest
./gradlew realDataTest
```

### Definition of Done

- Ein neuer Leser erkennt sofort: Engine generisch, DM01/DMAV ein Produktprofil.
- Keine hartcodierten alten `profiles/dm01-to-dmav`-Pfade ausser optional in Migration-/Compatibility-Code.

---

# Phase P2: Doku, Feature-Matrix und DSL-Konsistenz herstellen

---

## P2.1 `compileMode` in Doku und Code synchronisieren

### Problem

Doku sagt:

```markdown
mapping.compileMode: strict (default) oder allowTodos
```

Datei:

```text
docs/mapping-dsl.md
```

Code akzeptiert aber:

```java
strict
compatible
report
```

Methode:

```text
guru.interlis.transformer.mapping.compiler.MappingCompiler.parseCompileMode(String, DiagnosticCollector)
```

Datei:

```text
src/main/java/guru/interlis/transformer/mapping/compiler/MappingCompiler.java
```

### Ziel

Doku, Code, Profile und Tests verwenden dieselben Werte.

### Konkrete Änderung

Empfehlung: Code beibehalten, Doku korrigieren, weil produktive Profile bereits `compileMode: compatible` verwenden.

In `docs/mapping-dsl.md` ändern:

```markdown
| `mapping.compileMode` | `string` | Nein | `strict` (default), `compatible`, `report` |
```

Zusatztext:

```markdown
- `strict`: Warnungen aus der Mapping-Kompilierung werden als Fehler behandelt.
- `compatible`: erlaubt bewusst bekannte/kompatible Abweichungen, sofern sie nicht runtime-kritisch sind.
- `report`: für Analyse-/Reportingläufe; keine produktive Transformation ohne Prüfung verwenden.
```

Falls `report` semantisch anders ist, bitte anhand Code korrigieren.

### Tests

Ergänzen:

```text
src/test/java/guru/interlis/transformer/mapping/compiler/CompileModeTest.java
```

Testfälle:

1. `strict` → `CompileMode.STRICT`
2. `compatible` → `CompileMode.COMPATIBLE`
3. `report` → `CompileMode.REPORT`
4. `allowTodos` → Warning `MAP_UNKNOWN_COMPILE_MODE` oder bewusst Alias, falls Backward Compatibility gewünscht.

### Definition of Done

- Keine aktive Doku nennt `allowTodos` als aktuellen Wert, ausser historische Doku/Archiv.
- Tests decken alle unterstützten Werte ab.

---

## P2.2 `create`-DSL korrekt dokumentieren

### Problem

`docs/mapping-dsl.md` sagt:

```markdown
- `create` — DSL-Feld vorbereitet, noch nicht implementiert
```

Code enthält aber:

- `JobConfig.CreateSpec`
- `CreatePlan`
- `MappingCompiler.compileCreates(...)`
- `RuleExecutionService.processCreatePlan(...)`
- `TargetObjectFactory.createTargetForCreatePlan(...)`
- Tests wie `CreateCompilationTest`

### Ziel

Doku soll den tatsächlichen Status beschreiben: unterstützt, experimentell oder teilweise unterstützt.

### Konkrete Änderung

In `docs/mapping-dsl.md` einen eigenen Abschnitt ergänzen:

```markdown
### CreateSpec / zusätzliche Zielobjekte

`create` erzeugt zusätzliche Zielobjekte im Kontext einer Rule. Der aktuelle Stand ist experimentell.

Unterstützt:
- Zielklasse via `class`
- einfache `assign`-Zuweisungen
- OID-Erzeugung gemäss internem CreatePlan

Einschränkungen:
- Referenzen in CreatePlan sind nur eingeschränkt unterstützt
- keine vollständige Join-Kombination über mehrere Create-Level
- Semantik muss pro Produktprofil getestet werden
```

Nicht unterstützte Konstrukte entsprechend bereinigen.

### Code prüfen

Methode:

```text
guru.interlis.transformer.mapping.compiler.MappingCompiler.compileCreates(...)
```

Aktuell erzeugt sie:

```java
IdentityPlan identity = new IdentityPlan(OidStrategy.INTEGER, null, List.of());
```

Das ist wahrscheinlich zu hartcodiert. Prüfen, ob `create` dieselbe OID-Strategie wie Parent-Rule oder Mapping nutzen soll.

### Tests

Vorhanden:

```text
src/test/java/guru/interlis/transformer/mapping/compiler/CreateCompilationTest.java
```

Zusätzlich Runtime-Test prüfen/ergänzen:

```text
src/integrationTest/java/guru/interlis/transformer/CreateAdditionalObjectIntegrationTest.java
```

### Definition of Done

- Doku beschreibt tatsächlichen Status.
- Feature-Matrix beschreibt `create` nicht als voll unterstützt, falls nur experimentell.
- Tests belegen mindestens einen funktionierenden End-to-End-Fall.

---

## P2.3 Feature-Matrix automatisch validieren

### Problem

`FeatureMatrix.java` enthält manuelle Testreferenzen. Statisch wurden Referenzen gesehen, die im Repo nicht als Testklasse vorhanden waren, z. B.:

```text
SameOidDifferentBasketTest
SameOidDifferentInputTest
SameOidDifferentClassTest
```

Datei:

```text
src/main/java/guru/interlis/transformer/feature/FeatureMatrix.java
```

### Ziel

Die Feature-Matrix darf keine nicht existierenden Testklassen referenzieren. Status `SUPPORTED` darf nicht durch falsche Evidenz gestützt werden.

### Konkrete Änderung Option A: Testreferenzen maschinenprüfen

Erweitere:

```text
src/test/java/guru/interlis/transformer/feature/FeatureMatrixTest.java
```

Um einen Test:

```java
@Test
void allReferencedTestsExist() throws Exception {
    FeatureMatrix matrix = FeatureMatrix.create();
    Set<String> existingTestNames = Files.walk(Path.of("src"))
            .filter(p -> p.getFileName().toString().endsWith("Test.java"))
            .map(p -> p.getFileName().toString().replace(".java", ""))
            .collect(Collectors.toSet());

    for (FeatureEntry entry : matrix.entries()) {
        for (String testRef : entry.testReferences()) {
            for (String name : splitTestRefs(testRef)) {
                assertThat(existingTestNames)
                    .as("Feature %s references missing test %s", entry.name(), name)
                    .contains(name);
            }
        }
    }
}
```

Falls API anders ist, entsprechend anpassen.

### Konkrete Änderung Option B: Feature-Matrix als YAML

Langfristig besser:

```text
src/main/resources/feature-matrix.yaml
```

Dann `FeatureMatrixTask` liest YAML und generiert Markdown/JSON. Diese Variante ist mehr Arbeit, aber sauberer.

### Sofortmassnahme

In `FeatureMatrix.java` alle falschen Testnamen korrigieren oder die entsprechenden Tests anlegen.

### Definition of Done

- Feature-Matrix-Test schlägt fehl, wenn Testreferenz nicht existiert.
- Keine fehlenden Testreferenzen mehr.
- `SUPPORTED` nur dort, wo Tests existieren und wirklich grün sind.

---

# Phase P3: Performance-Claims und Source-Dispatch verbessern

---

## P3.1 Full Scan in `RuleExecutionService.processSingleSourceRule()` beseitigen

### Problem

Feature-Matrix behauptet sinngemäss:

```text
RuleDispatchIndex ... eliminates SourceRecord x Rule full scan
```

Aber `processSingleSourceRule()` iteriert weiterhin über alle SourceRecords pro Rule:

```java
List<SourceRecord> records = stateStore.sourceRecords();
for (SourceRecord record : records) {
    List<RulePlan> matchingRules = dispatchIndex.rulesFor(record.sourceFileId(), record.sourceClass());
    for (RulePlan matchingRule : matchingRules) {
        if (!matchingRule.ruleId().equals(rule.ruleId())) continue;
        ...
    }
}
```

Datei:

```text
src/main/java/guru/interlis/transformer/engine/RuleExecutionService.java
```

Methode:

```text
processSingleSourceRule(...)
```

### Ziel

Für jede Rule sollen nur SourceRecords iteriert werden, die zu den SourcePlans der Rule passen.

### Konkrete Änderung

#### 1. `StateStore` um Record-Lookup erweitern

Datei:

```text
src/main/java/guru/interlis/transformer/state/StateStore.java
```

Ergänzen:

```java
List<SourceRecord> sourceRecords(String inputId, String sourceClass);
```

#### 2. `InMemoryStateStore` Index ergänzen

Datei:

```text
src/main/java/guru/interlis/transformer/state/InMemoryStateStore.java
```

Feld:

```java
private final Map<String, List<SourceRecord>> sourceRecordsByInputAndClass = new HashMap<>();
```

In `addSourceRecord(SourceRecord sourceRecord)`:

```java
sourceRecords.add(sourceRecord);
sourceRecordsByInputAndClass
        .computeIfAbsent(sourceRecord.sourceFileId() + "|" + sourceRecord.sourceClass(), ignored -> new ArrayList<>())
        .add(sourceRecord);
```

Methode:

```java
@Override
public List<SourceRecord> sourceRecords(String inputId, String sourceClass) {
    return List.copyOf(sourceRecordsByInputAndClass.getOrDefault(inputId + "|" + sourceClass, List.of()));
}
```

Besser: strukturierter Key-Record statt String.

#### 3. `RuleExecutionService.processSingleSourceRule()` ändern

Statt Full Scan:

```java
for (SourcePlan source : rule.sources()) {
    String scopedClass = TargetObjectFactory.getScopedName(source.sourceClass());
    for (String inputId : source.inputIds()) {
        for (SourceRecord record : stateStore.sourceRecords(inputId, scopedClass)) {
            ... process record for this source ...
        }
    }
}
```

Achtung: Prüfen, ob `record.sourceClass()` scoped oder unscoped gespeichert wird. `SourceIndexingService` verwendet:

```java
SourceRecord sr = new SourceRecord(inputId, basketId, source.getobjecttag(), source);
```

`source.getobjecttag()` ist bei XTF typischerweise scoped. Bei ITF kann es ggf. anders sein. Unbedingt Tests mit ITF und XTF laufen lassen.

### Tests

Bestehender Test:

```text
src/test/java/guru/interlis/transformer/engine/RuleDispatchIndexTest.java
```

Ergänzen:

```text
src/test/java/guru/interlis/transformer/state/InMemoryStateStoreTest.java
src/test/java/guru/interlis/transformer/engine/RuleExecutionDispatchPerformanceTest.java
```

Testidee:

- 1000 SourceRecords mit falscher Klasse
- 1 SourceRecord mit passender Klasse
- Rule darf nur passende Records verarbeiten
- Metrik/Spy zeigt keine Verarbeitung aller Records

Falls kein Spy möglich: `ExecutionMetrics` erweitern um `sourceRecordsVisited`.

### Definition of Done

- Keine Rule iteriert mehr pauschal über alle `stateStore.sourceRecords()`.
- Feature-Matrix-Aussage stimmt oder wird abgeschwächt.
- Tests mit synthetischem grossen Datensatz zeigen Verbesserung.

---

## P3.2 `InMemorySourceLookupIndex.lookup()` O(1) machen

### Problem

`InMemorySourceLookupIndex` indexiert per `CanonicalValue`, nutzt beim Lookup aber keinen direkten Map-Zugriff, sondern iteriert:

```java
for (var entry : attrIndex.entrySet()) {
    CanonicalValue indexedCv = entry.getKey();
    if (Objects.equals(key.value().canonicalText(), indexedCv.canonicalText())
            && key.value().defined() == indexedCv.defined()) {
        ...
    }
}
```

Datei:

```text
src/main/java/guru/interlis/transformer/state/InMemorySourceLookupIndex.java
```

### Ziel

Direkter Lookup per `attrIndex.get(key.value())`, falls `CanonicalValue` ein Record mit korrektem equals/hashCode ist.

### Konkrete Änderung

```java
@Override
public List<SourceRecord> lookup(LookupKey key) {
    String sourceClass = key.sourceClass();
    Map<String, Map<CanonicalValue, List<SourceRecord>>> classIndex = index.get(sourceClass);
    if (classIndex == null) return List.of();

    Map<CanonicalValue, List<SourceRecord>> attrIndex = classIndex.get(key.attribute());
    if (attrIndex == null) return List.of();

    List<SourceRecord> records = attrIndex.getOrDefault(key.value(), List.of());
    if (records.isEmpty()) return List.of();

    if (key.inputId() == null || key.inputId().isBlank()) {
        return List.copyOf(records);
    }
    return records.stream()
            .filter(r -> key.inputId().equals(r.sourceFileId()))
            .toList();
}
```

Prüfen:

```text
src/main/java/guru/interlis/transformer/state/CanonicalValue.java
```

Falls `CanonicalValue` nicht eindeutig genug normalisiert, dann Normalisierung dort sauber machen.

### Tests

Neu:

```text
src/test/java/guru/interlis/transformer/state/InMemorySourceLookupIndexTest.java
```

Testfälle:

1. Scalar lookup findet Record.
2. Reference-object lookup findet Record.
3. `inputId` filtert korrekt.
4. Nicht vorhandener Wert liefert leere Liste.
5. Mehrere Records gleicher Wert werden alle geliefert.

### Definition of Done

- Lookup nutzt direkten Map-Zugriff.
- Verhalten bleibt gleich.
- Test deckt Scalar, Reference und inputId ab.

---

## P3.3 Join-Semantik ehrlich machen oder mehrere Joins implementieren

### Problem

DSL erlaubt Liste von Joins:

```yaml
joins:
  - left: src1
    right: src2
    on: "eq(src1.attr, src2.attr)"
```

Runtime verwendet aber nur:

```java
JoinPlan join = rule.joins().get(0);
```

Datei:

```text
src/main/java/guru/interlis/transformer/engine/RuleExecutionService.java
```

Methode:

```text
processJoinedRule(...)
```

### Ziel

Entweder:

- Doku und Compiler validieren: maximal ein Join pro Rule.
- Oder Runtime unterstützt mehrere Joins korrekt.

### Empfehlung für nächste Iteration

Kurzfristig **maximal ein Join pro Rule** explizit validieren. Mehrere Joins sind komplexer und können später kommen.

### Konkrete Änderung kurzfristig

In `MappingCompiler.compileJoins(...)`:

```java
if (rule.joins != null && rule.joins.size() > 1) {
    diag.add(new Diagnostic(
            DiagnosticCode.MAP_UNSUPPORTED_FEATURE,
            Severity.ERROR,
            "Only one join per rule is currently supported",
            ruleId,
            "Split the mapping into multiple rules or wait for multi-join support"));
}
```

Falls `MAP_UNSUPPORTED_FEATURE` nicht existiert, passenden bestehenden Code verwenden oder neuen `DiagnosticCode` ergänzen.

Doku `docs/mapping-dsl.md` anpassen:

```markdown
Derzeit wird maximal ein Join pro Rule unterstützt. Mehrere Join-Einträge sind reserviert und werden vom Compiler abgelehnt.
```

Feature-Matrix von `Joins / Splits / Merge` auf `EXPERIMENTAL` lassen und Einschränkung nennen.

### Tests

Datei existiert:

```text
src/test/java/guru/interlis/transformer/mapping/compiler/JoinCompilationTest.java
```

Ergänzen:

```java
@Test
void rejectsMultipleJoinsForNow() { ... }
```

### Definition of Done

- Nutzer können nicht irrtümlich mehrere Joins konfigurieren, die Runtime nur teilweise ausführt.
- Doku beschreibt Einschränkung.

---

# Phase P4: Source-Metadaten nicht in `IomObject` mutieren

---

## P4.1 `_parentOid` und `_parentClass` aus Source-IOM entfernen

### Problem

`SourceIndexingService.expandBagStructures(...)` mutiert gelesene Struktur-Objekte:

```java
structure.setattrvalue("_parentOid", source.getobjectoid());
structure.setattrvalue("_parentClass", source.getobjecttag());
stateStore.addSourceRecord(new SourceRecord(
        inputId, basketId,
        structure.getobjecttag(),
        structure));
```

Datei:

```text
src/main/java/guru/interlis/transformer/engine/SourceIndexingService.java
```

Das vermischt Runtime-Metadaten mit fachlichen INTERLIS-Objekten. Später kann das zu Nebenwirkungen führen, z. B. wenn Expressions oder Writer diese Attribute sehen.

### Ziel

Parent-Kontext gehört in `SourceRecord` oder eine zusätzliche Metadata-Struktur, nicht in das `IomObject`.

### Konkrete Änderung

#### 1. `SourceRecord` erweitern

Datei:

```text
src/main/java/guru/interlis/transformer/state/SourceRecord.java
```

Vermutlich aktuell ein Record. Erweitern um optionalen Parent-Kontext:

```java
public record SourceRecord(
        String sourceFileId,
        String sourceBasketId,
        String sourceClass,
        IomObject sourceObject,
        ParentContext parentContext
) {
    public SourceRecord(String sourceFileId, String sourceBasketId,
                        String sourceClass, IomObject sourceObject) {
        this(sourceFileId, sourceBasketId, sourceClass, sourceObject, null);
    }

    public Optional<ParentContext> parentContextOptional() {
        return Optional.ofNullable(parentContext);
    }

    public record ParentContext(String parentOid, String parentClass) {}
}
```

#### 2. `SourceIndexingService.expandBagStructures(...)` ändern

Statt Mutation:

```java
SourceRecord.ParentContext parentContext =
        new SourceRecord.ParentContext(source.getobjectoid(), source.getobjecttag());

stateStore.addSourceRecord(new SourceRecord(
        inputId,
        basketId,
        structure.getobjecttag(),
        structure,
        parentContext));
```

#### 3. Alle Stellen anpassen, die `_parentOid` oder `_parentClass` lesen

Suchen:

```bash
grep -R "_parentOid\|_parentClass" -n src/main/java src/test/java src/integrationTest/java src/realDataTest/java
```

Diese Stellen auf `SourceRecord.parentContext()` umstellen.

### Tests

Neu/ergänzen:

```text
src/test/java/guru/interlis/transformer/engine/SourceIndexingServiceTest.java
src/test/java/guru/interlis/transformer/engine/BagTransformationServiceTest.java
```

Testfälle:

1. Expandierte BAG-Struktur bekommt ParentContext.
2. `IomObject` enthält keine `_parentOid` / `_parentClass` Attribute.
3. Bestehende BAG-Transformationen funktionieren weiterhin.

### Definition of Done

- Kein Main-Code setzt `_parentOid` oder `_parentClass` auf `IomObject`.
- BAG-Tests und RealData-Tests bleiben grün.

---

# Phase P5: `MappingCompiler` modularisieren

---

## P5.1 `MappingCompiler.java` aufteilen

### Problem

`MappingCompiler.java` hat ca. 1733 Zeilen und vereint sehr viele Verantwortlichkeiten:

- DSL-Strukturvalidierung
- CompileMode
- Capability-Validierung
- Rule-Kompilierung
- Source-Kompilierung
- Assignment-Kompilierung
- Ref-Kompilierung
- Bag-Kompilierung
- Join-Kompilierung
- Create-Kompilierung
- Identity-Key-Validierung
- OID/Basket-Strategien
- Mandatory-Coverage
- Dependency-Checks

Datei:

```text
src/main/java/guru/interlis/transformer/mapping/compiler/MappingCompiler.java
```

### Ziel

`MappingCompiler` wird Orchestrator. Fachliche Teilkompilation wird in kleinere Klassen ausgelagert.

### Empfohlene Zielstruktur

```text
src/main/java/guru/interlis/transformer/mapping/compiler/
  MappingCompiler.java
  RuleCompiler.java
  SourceCompiler.java
  AssignmentCompiler.java
  RefCompiler.java
  BagCompiler.java
  JoinCompiler.java
  CreateCompiler.java
  IdentityCompiler.java
  MandatoryCoverageValidator.java
  CompileModeParser.java
  FailPolicyParser.java       (oder package mapping.plan)
  CompilerContext.java
```

### Vorgehen in kleinen Schritten

#### Schritt 1: `CompilerContext` einführen

Neue Klasse:

```java
public record CompilerContext(
        ModelRegistry modelRegistry,
        FunctionRegistry functionRegistry,
        ExpressionCompiler expressionCompiler,
        Map<String, Map<String, String>> enumMaps,
        DiagnosticCollector diagnostics
) {}
```

#### Schritt 2: `SourceCompiler` extrahieren

Aus `MappingCompiler` extrahieren:

```text
compileSource(...)
compileWhereFilters(...)
```

Neue Klasse:

```text
src/main/java/guru/interlis/transformer/mapping/compiler/SourceCompiler.java
```

#### Schritt 3: `AssignmentCompiler` extrahieren

Extrahieren:

```text
compileAssignment(...)
compileDefaultAssignment(...)
isTypeCompatible(...)
```

#### Schritt 4: `JoinCompiler` extrahieren

Extrahieren:

```text
compileJoins(...)
isEquiJoin(...)
```

#### Schritt 5: `CreateCompiler` extrahieren

Extrahieren:

```text
compileCreates(...)
```

#### Schritt 6: `BagCompiler` extrahieren

Extrahieren:

```text
compileBags(...)
```

### Tests

Nach jedem Extraktionsschritt:

```bash
./gradlew test --tests "*MappingCompiler*"
./gradlew test --tests "*JoinCompilationTest"
./gradlew test --tests "*CreateCompilationTest"
```

### Definition of Done

- `MappingCompiler.java` idealerweise < 600 Zeilen.
- Keine semantischen Änderungen an DSL-Kompilierung.
- Alle bestehenden Compiler-Tests grün.
- Neue Klassen haben klare Verantwortlichkeiten.

---

# Phase P6: `JobConfig` von validiertem Domain-Modell trennen

---

## P6.1 Raw-Jackson-DTO und validierte Config trennen

### Problem

`JobConfig` ist public mutable und dient gleichzeitig als Jackson-DTO und interne Struktur:

```java
public final class JobConfig {
    public int version;
    public JobSection job = new JobSection();
    public MappingSection mapping = new MappingSection();
    ...
}
```

Datei:

```text
src/main/java/guru/interlis/transformer/mapping/model/JobConfig.java
```

Das ist einfach, aber für DSL-Versionierung und robuste interne Verarbeitung fragil.

### Ziel

Trennung:

```text
RawJobConfig       mutable Jackson DTO
JobConfig          validierte, möglichst immutable Domain-Konfiguration
TransformPlan      kompiliertes Runtime-Modell
```

### Vorgehen

Diese Phase ist grösser. Nicht zusammen mit P0/P1 machen.

#### Schritt 1: Klasse umbenennen oder neue Raw-Klasse einführen

Variante risikoarm:

- Bestehendes `JobConfig` behalten.
- Neue Klasse `RawJobConfig` einführen und `MappingLoader` zunächst noch `JobConfig` liefern lassen.
- Später migrieren.

Variante sauberer, aber invasiver:

- `JobConfig` zu `RawJobConfig` umbenennen.
- Neue immutable `JobConfig` einführen.

#### Schritt 2: `MappingLoader` umbauen

Datei:

```text
src/main/java/guru/interlis/transformer/mapping/model/MappingLoader.java
```

Ziel:

```java
RawJobConfig raw = objectMapper.readValue(..., RawJobConfig.class);
return JobConfigValidator.validateAndNormalize(raw, baseDirectory);
```

#### Schritt 3: Normalisierung zentralisieren

Aktuell gibt es in `JobConfig.RuleSpec` Methoden:

```java
getEffectiveTargetClass()
getEffectiveTargetOutput()
getAllAttributes()
getEffectiveRefs()
```

Diese Backward-Compatibility-Logik sollte in einen Normalisierungsschritt wandern, damit der Compiler nicht ständig alte und neue DSL-Formate kennen muss.

Neue Klasse:

```text
src/main/java/guru/interlis/transformer/mapping/model/JobConfigNormalizer.java
```

### Tests

Neu:

```text
src/test/java/guru/interlis/transformer/mapping/model/JobConfigNormalizerTest.java
src/test/java/guru/interlis/transformer/mapping/model/MappingLoaderCompatibilityTest.java
```

Testfälle:

1. Neue DSL-Form wird korrekt geladen.
2. Alte `targetClass`/`output`-Form wird korrekt normalisiert.
3. Alte `attributes`-Liste und neue `assign`-Map werden korrekt zusammengeführt.
4. `input` wird zu `inputs` normalisiert.

### Definition of Done

- Compiler arbeitet möglichst nur mit normalisierter Config.
- Backward Compatibility bleibt getestet.
- Public mutable DTOs sind nicht mehr zentrale Domain-Objekte.

---

# Phase P7: Build, Java-Version und CI produktiver machen

---

## P7.1 Java 21 als Baseline prüfen

### Problem

`build.gradle` setzt:

```gradle
languageVersion = JavaLanguageVersion.of(25)
```

Datei:

```text
build.gradle
```

CI nutzt ebenfalls Java 25:

```yaml
java: ['25']
```

Datei:

```text
.github/workflows/ci.yml
```

Für ein Verwaltung-/INTERLIS-Tool ist Java 21 LTS als Baseline realistischer.

### Ziel

Prüfen, ob Java 21 genügt. Wenn ja, Toolchain auf 21 setzen und optional Java 25 zusätzlich testen.

### Konkrete Änderung

In `build.gradle`:

```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

In CI:

```yaml
strategy:
  matrix:
    java: ['21', '25']
```

Falls Java 25 aus konkretem Grund nötig ist, Doku ergänzen:

```markdown
## Requirements

Requires Java 25 because ...
```

### Tests

CI Matrix muss grün sein.

### Definition of Done

- Java-Version ist bewusst entschieden und dokumentiert.
- CI testet mindestens die unterstützte Baseline.

---

## P7.2 Nightly/Manual `realDataTest` Workflow ergänzen

### Problem

`check` hängt an `integrationTest`, aber nicht an `realDataTest`. Das ist lokal sinnvoll. Produktive DM01/DMAV-Profile brauchen aber regelmässige RealData-Evidenz.

### Ziel

Separater CI-Workflow für RealData-Tests.

### Neue Datei

```text
.github/workflows/real-data.yml
```

Beispiel:

```yaml
name: Real data tests

on:
  workflow_dispatch:
  schedule:
    - cron: '0 3 * * 1'

jobs:
  real-data-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Run realDataTest
        run: ./gradlew realDataTest
      - name: Upload reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: real-data-test-reports
          path: build/reports/
```

### Definition of Done

- RealData-Tests können manuell in GitHub gestartet werden.
- Optional laufen sie wöchentlich.
- Reports werden als Artefakte hochgeladen.

---

# Phase P8: Dokumentationsstruktur produktisieren

---

## P8.1 Aktive Doku von historischer Doku trennen

### Problem

`docs/dev/architecture.md` beginnt z. B. mit:

```text
Stand nach Phase 14.
```

Das ist als historische Doku okay, aber nicht als aktuelle Architekturreferenz.

### Ziel

Neue klare Doku-Struktur:

```text
docs/
  index.md
  cli.md
  mapping-dsl.md
  expressions.md
  architecture.md
  runtime.md
  testing.md
  performance.md
  extension-points.md
  dev/
    adr/
    archive/
    agent/
products/
  dm01-dmav/
    README.md
    docs/
      status-matrix.md
      lossiness.md
      correlation-table.md
```

### Konkrete Änderung

1. Aktuelle Architektur aus `docs/dev/architecture.md` neu schreiben nach `docs/architecture.md`.
2. Alte Phasen-Doku nach `docs/dev/archive/` verschieben.
3. README so kürzen, dass sie nur Einstieg und Links enthält.
4. `docs/testing.md` auf neue Pfade aktualisieren.
5. DM01/DMAV-Doku unter Produktstruktur verschieben oder klar verlinken.

### Definition of Done

- Ein neuer Nutzer findet in 2 Minuten:
  - Was ist die Engine?
  - Wie installiere ich die CLI?
  - Wie sieht ein minimales Mapping aus?
  - Wo ist DM01↔DMAV dokumentiert?
  - Was ist stabil vs experimentell?

---

# Phase P9: Release- und Qualitäts-Hygiene

---

## P9.1 Formatting / static checks ergänzen

### Ziel

Einheitlicher Code-Stil und weniger zufällige Diffs.

### Optionen

- Spotless Gradle Plugin
- Checkstyle
- ErrorProne optional

Empfehlung für Start:

```gradle
plugins {
    id 'com.diffplug.spotless' version '...'
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}
```

Aber Vorsicht: Ein globaler Formatting-Commit kann sehr gross sein. Als eigener Commit machen.

### Definition of Done

- `./gradlew spotlessCheck` oder vergleichbarer Task läuft in CI.
- Formatting-Commit getrennt von funktionalen Änderungen.

---

## P9.2 Release-Artefakt definieren

### Ziel

Benutzer können das Tool ohne Gradle-Kenntnisse verwenden.

### Konkrete Änderungen

1. `installDist` bleibt.
2. GitHub Release Workflow ergänzen.
3. Distribution enthält:
   ```text
   bin/ilitransformer
   lib/*.jar
   examples/minimal/
   docs/cli.md
   docs/mapping-dsl.md
   products/dm01-dmav/profiles/   optional
   ```
4. README Quickstart:
   ```bash
   unzip ilitransformer-0.1.0.zip
   ./bin/ilitransformer --help
   ./bin/ilitransformer transform -m examples/minimal/mapping.yaml
   ```

### Definition of Done

- Release-ZIP/TAR wird in GitHub Actions erzeugt.
- Smoke-Test führt installierte CLI aus.

---

# Phase P10: Langfristige Architekturverbesserungen

Diese Punkte sind nicht dringend, aber wichtig, wenn das Projekt grösser wird.

---

## P10.1 Plugin-/Extension-Konzept für Funktionen

### Problem

`MappingCompiler.defaultRegistry()` registriert Builtins direkt:

```java
BasicFunctions.registerAll(registry);
StringFunctions.registerAll(registry);
DateFunctions.registerAll(registry);
EnumFunctions.registerAll(registry);
RefFunctions.registerAll(registry);
MathFunctions.registerAll(registry);
LookupFunctions.registerAll(registry);
GeometryFunctions.registerAll(registry);
```

Datei:

```text
src/main/java/guru/interlis/transformer/mapping/compiler/MappingCompiler.java
```

### Ziel

Später sollen Produktprofile oder Benutzer eigene Funktionen registrieren können, ohne Core-Code zu ändern.

### Vorschlag

- `FunctionProvider` Interface:
  ```java
  public interface FunctionProvider {
      void register(FunctionRegistry registry);
  }
  ```
- Java `ServiceLoader<FunctionProvider>` optional.
- CLI-Option später:
  ```bash
  --function-provider-class ...
  ```
  oder Plugin-JAR-Verzeichnis.

### Nicht sofort umsetzen, aber Architektur vorbereiten.

---

## P10.2 Persistenter StateStore / Spill-to-disk

### Problem

Aktuell ist Engine stark In-Memory:

- `InMemoryStateStore`
- `InMemoryReferenceIndex`
- `InMemorySourceLookupIndex`
- alle relevanten `SourceRecord`s im Speicher

Für sehr grosse Transfers kann das limitieren.

### Ziel

Mittelfristig alternatives StateStore-Backend:

```text
StateStore
  InMemoryStateStore
  SqliteStateStore oder DuckDbStateStore
```

### Empfehlung

Nicht sofort implementieren. Zuerst Performance-Metriken und Grenzen dokumentieren:

```text
docs/performance.md
```

Inhalt:

- Speicherverbrauch pro Objekt grob messen
- maximale getestete Objektzahl
- typische AV-Datensatzgrössen
- bekannte Bottlenecks
- Empfehlungen für JVM Heap

---

# Konkrete Reihenfolge für phasenweise Umsetzung

Empfohlener Ablauf:

```text
1. P0.1 --fail-policy fixen
2. P0.2/P0.3 TransactionalOutputManager fixen
3. P0.4 TargetObjectKey/outputId fixen
4. P2.1/P2.2 Doku-Drift compileMode/create beheben
5. P2.3 FeatureMatrix-Testreferenzen validieren
6. P1.1 DM01/DMAV-Leak aus TransferInventoryService entfernen
7. P1.2 CLI-Produktnamespace dm01-dmav einführen
8. P3.2 SourceLookupIndex O(1)
9. P3.1 RuleExecutionService Full Scan reduzieren
10. P3.3 Join-Einschränkung validieren oder implementieren
11. P4 Parent-Kontext nicht in IomObject mutieren
12. P5 MappingCompiler modularisieren
13. P7 Java/CI/realData Workflow
14. P8 Doku-Struktur
15. P9 Release-Hygiene
```

---

# Zusätzliche grep-Checks für den Agenten

Nach den Phasen regelmässig ausführen:

```bash
# DM01/DMAV darf nicht in generische Engine/Model/Mapping-Pfade leaken
grep -R "guru.interlis.transformer.dmav" -n \
  src/main/java/guru/interlis/transformer/engine \
  src/main/java/guru/interlis/transformer/model \
  src/main/java/guru/interlis/transformer/mapping || true

# Keine alten Phasenkommentare im produktiven Main-Code
grep -R "Phase [0-9]" -n src/main/java || true

# Keine internen Parent-Attribute mehr auf IomObject
grep -R "_parentOid\|_parentClass" -n src/main/java || true

# Feature-Matrix verweist nicht auf fehlende Tests
./gradlew test --tests "guru.interlis.transformer.feature.FeatureMatrixTest"
```

---

# Schlussbemerkung für den Coding-Agenten

Das Projekt ist fachlich wertvoll und bereits weit fortgeschritten. Die wichtigste Leitlinie bei allen Änderungen:

> Den generischen INTERLIS-Transformationskern stabilisieren und DM01↔DMAV als darauf aufbauendes Produktprofil klarer kapseln.

Nicht vorschnell neue Mapping-Features hinzufügen. Zuerst bestehende Versprechen korrekt machen: CLI-Optionen, transaktionaler Output, Doku-Konsistenz, Feature-Matrix, Produktgrenzen und Performance-Realität.
