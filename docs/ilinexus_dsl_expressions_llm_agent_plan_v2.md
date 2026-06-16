# ilitransformer / ilinexus — DSL- und Expression-Plan für einen LLM-Coding-Agenten

Stand: Analyse des aktualisierten ZIP-Exports `ilinexus(1).zip`.

Dieses Dokument ist eine **phasenweise Umsetzungsanweisung** für einen LLM-Coding-Agenten. Es fokussiert ausschliesslich auf die Mapping-DSL und die Expression-Language. Jede Phase ist bewusst so geschnitten, dass sie in sich abgeschlossen, testbar und separat commitbar ist.

Die Umsetzung soll **nicht** in einem grossen Refactoring-Commit passieren. Arbeite Phase für Phase. Lies pro Phase die genannten Skills, prüfe die vorhandenen Tests, ergänze zuerst die kleinsten sinnvollen Tests und implementiere danach die Änderung.

---

## 0. Kontext und aktueller Befund

### 0.1 Aktueller Zustand der DSL

Die DSL ist bereits deutlich mehr als eine einfache Attribut-Mapping-Datei. Sie enthält unter anderem:

```yaml
version: 1

job:
  name: ...
  description: ...
  direction: ...
  failPolicy: strict | lenient | reportOnly
  modeldir: [...]
  inputs: [...]
  outputs: [...]

mapping:
  oidStrategy:
    default: integer | preserve | uuid | deterministicUuid | external
    namespace: ...
  basketStrategy:
    default: preserve | generateUuid | preserveOrGenerateUuid | byTopic | expression
  enums: {...}
  defaults: {...}
  compileMode: strict | compatible | report
  rules:
    - id: ...
      target: ...
      sources: ...
      where: ...
      identity: ...
      assign: ...
      defaults: ...
      refs: ...
      bags: ...
      joins: ...
      create: ...
      losses: ...
      metadata: ...
```

In den produktiven Profilen unter `profiles/` wurden im analysierten ZIP grob folgende DSL-Bausteine gefunden:

```text
Profile:      22
Rules:        92
Sources:      94
Assignments: 445
refs:         60
bags/nested:  57
joins:         2
create:        0
losses:        5
identity:     72
source.where: 30
bag.where:     4
rule.where:    0
```

Die meistgenutzten Expressions in produktiven Profilen sind:

```text
coalesce          122x
enumMap            91x
truncate           28x
div                16x
toXmlDateTime      14x
toDate             12x
lookup             10x
oid                10x
if                  8x
notDefined          5x
mul                 4x
refEquals           4x
defined             2x
eq                  2x
toInterlis1Date     2x
coordEquals         1x
```

Interpretation:

- Die DSL wird real genutzt und trägt produktive DM01↔DMAV-Profile.
- Der Ausdrucksbedarf ist relativ klar: Defaults, Enum-Mappings, Textkürzung, Datumskonvertierungen, einfache Mathematik, Lookups, OID-Zugriffe, einfache Bedingungen und Referenzvergleiche.
- Es gibt keinen Bedarf für eine grosse allgemeine Programmiersprache in YAML.
- Die nächste Entwicklungsstufe sollte **Stabilisierung des Sprachvertrags** sein, nicht ungezügelter Feature-Ausbau.

### 0.2 Gute bereits umgesetzte Verbesserungen

Gegenüber einer früheren Version sind bereits wichtige Verbesserungen sichtbar:

- `MappingCompiler` ist stark modularisiert.
- Java-Baseline ist auf Java 21 gesetzt.
- `--fail-policy` ist in `RunOptions` angekommen.
- DM01/DMAV-CLI ist besser separiert über `Dm01DmavCommand`.
- `create` ist in `docs/mapping-dsl.md` als experimentell beschrieben.
- `JoinCompiler` lehnt mehr als einen Join pro Rule explizit ab.
- `FunctionRegistry.all()` existiert und kann als Quelle für Funktionsdoku dienen.
- `InMemorySourceLookupIndex.lookup(...)` ist bereits O(1)-näher umgesetzt, weil es direkt `attrIndex.get(key.value())` nutzt.
- Boolean- und Comparison-Operatoren sind im Parser vorhanden und teilweise getestet.

### 0.3 Grösste verbleibende Probleme

Die wichtigsten noch offenen Punkte sind:

1. **Doku-Code-Drift bei Expressions**
   - `docs/expressions.md` listet `round` und `abs`, aber `MathFunctions` registriert nur `div` und `mul`.
   - `docs/expressions.md` sagt, Comparison-Operatoren seien unsupported/planned, aber `ExpressionParser` unterstützt `>`, `<`, `>=`, `<=` bereits.
   - `docs/expressions.md` nennt `enumMap` noch als Stub, obwohl es produktiv genutzt wird.
   - `docs/index.md` bezeichnet `enumMap()` ebenfalls als Stub/Pass-through.
   - `docs/architecture.md` erwähnt Math-Funktionen `round` und `abs`, die im Code nicht vorhanden sind.

2. **Doppelte Typprüfungslogik**
   - `ExpressionCompiler` enthält private Methoden wie `resolveType(...)`, `resolvePathType(...)`, `resolveFunctionType(...)`, `resolveConditionalType(...)`, `checkEnumMapValidation(...)`, `inferEnumMapReturnType(...)`.
   - `ExpressionTypeChecker` enthält sehr ähnliche Logik: `check(...)`, `checkPath(...)`, `checkFunction(...)`, `checkConditional(...)`, `checkEnumMapFunction(...)`, `inferEnumMapReturnType(...)`.
   - Das ist ein Wartungsrisiko.

3. **Nested Paths sind dokumentiert, aber technisch nicht sauber implementiert**
   - `PathExpr` ist aktuell `PathExpr(String alias, String attributeName)`.
   - `ExpressionParser.parseIdentifierExpr()` splittet `src.structure.attribute` nur in `alias=src`, `attributeName=structure.attribute`.
   - `ExpressionEngine.resolveSourcePath(...)` traversiert keine verschachtelten Strukturen, sondern sucht effektiv ein Attribut mit dem Namen `structure.attribute`.
   - Deshalb soll nested path support kurzfristig **nicht** als unterstützt dokumentiert werden.

4. **`lookup(...)` ist nützlich, aber string-basiert und input-übergreifend**
   - `LookupFunctions.lookup(...)` nutzt `new LookupKey(null, classPath, keyAttr, ...)`.
   - `inputId` ist damit unscoped.
   - Rückgabetyp ist `TypeInfo.UNKNOWN`.
   - Mehrdeutigkeit führt zu Warning und “first value wins”.

5. **Enum-Missing-Policy ist implizit**
   - `enumMap(...)` ist wichtig und produktiv genutzt.
   - Fehlender Map-Name ist compile-time ein Fehler, runtime aber Warning + Pass-through.
   - Fehlender Source-Wert ist Warning + `NullValue`.
   - Das sollte dokumentiert und später optional strenger steuerbar werden.

---

## 1. Zwingend zu lesende Repository-Skills

Der Agent muss vor jeder Phase die relevanten Skills lesen. Mindestens immer:

```text
.skills/mapping-dsl-change/SKILL.md
.skills/java-test-gap/SKILL.md
.skills/gradle-verification/SKILL.md
.skills/done-and-commit/SKILL.md
```

Zusätzlich je nach Phase:

| Phase / Änderung | Zusätzlich lesen |
|---|---|
| DSL-/Expression-Syntax, `JobConfig`, `MappingCompiler`, `TransformPlan`, `FunctionRegistry` | `.skills/mapping-dsl-change/SKILL.md` |
| Java-Produktionscode | `.skills/java-test-gap/SKILL.md` |
| DM01/DMAV-Profile, RealData-Regression, Mapping-Profile | `.skills/dm01-dmav-real-data-gate/SKILL.md` |
| ITF/XTF/ILI-Testdaten oder Validierung | `.skills/interlis-validation/SKILL.md`, ggf. `.skills/interlis1-testdata/SKILL.md` |
| Architekturgrenzen, Produkt-/Engine-Trennung | `.skills/architecture-boundary-review/SKILL.md` |
| Abschluss jeder Phase | `.skills/gradle-verification/SKILL.md`, `.skills/done-and-commit/SKILL.md` |

Wichtige Regeln:

- Keine Tests behaupten, die nicht exakt ausgeführt wurden.
- Keine generierten oder invaliden INTERLIS-Artefakte committen.
- Bei DSL-Änderungen immer Parser-/Loader-, Compiler- und ggf. Runtime-Tests ergänzen.
- Doku und Feature-/Status-Darstellung müssen bei DSL-Änderungen mitgezogen werden.
- Jede Phase endet mit einem eigenen Commit.

---

## Phase 0 — Baseline erfassen und DSL-/Expression-Inventar aktualisieren

### Ziel

Vor Änderungen einen reproduzierbaren Ausgangspunkt schaffen. Der Agent soll erfassen, welche Funktionen registriert sind, welche Expressions in Profilen genutzt werden und welche Tests bereits existieren. Diese Phase verändert möglichst keinen Produktionscode. Optional darf ein kleiner Report/Test ergänzt werden, wenn er rein beobachtend ist.

### Relevante Dateien

```text
build.gradle
settings.gradle
docs/expressions.md
docs/mapping-dsl.md
docs/index.md
docs/architecture.md
profiles/**/*.yaml
src/main/java/guru/interlis/transformer/expr/FunctionRegistry.java
src/main/java/guru/interlis/transformer/expr/builtins/*.java
src/test/java/guru/interlis/transformer/expr/*Test.java
src/test/java/guru/interlis/transformer/mapping/compiler/*Test.java
```

### Aufgaben

1. Arbeitsbaum prüfen:

```bash
git status --short
git diff --stat
```

2. Verfügbare Gradle-Tasks prüfen:

```bash
./gradlew tasks --group verification
```

3. Vorhandene Tests identifizieren:

```bash
find src/test/java/guru/interlis/transformer/expr -name '*Test.java' | sort
find src/test/java/guru/interlis/transformer/mapping/compiler -name '*Test.java' | sort
```

4. Registrierte Funktionen manuell oder per kurzem lokalen Skript inventarisieren. Quelle ist die Registrierung in:

```text
BasicFunctions.registerAll(FunctionRegistry registry)
StringFunctions.registerAll(FunctionRegistry registry)
DateFunctions.registerAll(FunctionRegistry registry)
EnumFunctions.registerAll(FunctionRegistry registry)
RefFunctions.registerAll(FunctionRegistry registry)
MathFunctions.registerAll(FunctionRegistry registry)
LookupFunctions.registerAll(FunctionRegistry registry)
GeometryFunctions.registerAll(FunctionRegistry registry)
```

5. Prüfen, ob Doku aktuell mit Code übereinstimmt:

```text
docs/expressions.md
docs/index.md
docs/architecture.md
docs/mapping-dsl.md
```

6. Optional einen temporären lokalen Report erzeugen, aber nicht committen, sofern er nicht explizit Teil eines Tests ist.

### Erwarteter Befund

Registrierte Funktionen sollten derzeit ungefähr sein:

```text
coalesce, defined, notDefined, isNull, default, null,
eq, neq, lt, lte, gt, gte, not,
concat, substring, trim, upper, lower, replace, truncate,
toXmlDateTime, toInterlis1Date, toDate, now,
enumMap, enumDefault, enumName,
refOid, refEquals,
div, mul,
oid, bagFirst, lookup,
coordEquals
```

Doku-Drift ist zu erwarten:

```text
round/abs dokumentiert, aber nicht registriert
Comparison operators als unsupported dokumentiert, aber implementiert
enumMap als stub dokumentiert, aber produktiv genutzt
nested paths dokumentiert, aber technisch nicht sauber unterstützt
lookupOne/lookupMany erwähnt, aber nicht vorhanden
```

### Tests / Verifikation

In Phase 0 genügt:

```bash
./gradlew test --tests "guru.interlis.transformer.expr.FunctionRegistryTest"
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionParserTest"
```

Wenn Gradle nicht läuft, nicht behaupten, dass Tests erfolgreich waren. Fehler dokumentieren.

### Definition of Done

- Ausgangszustand ist verstanden und notiert.
- Keine funktionalen Änderungen am Produktionscode.
- Offene Drift-Punkte sind bekannt.
- Exakte Testbefehle und Resultate sind dokumentiert.

---

## Phase 1 — Expression-Dokumentation mit aktuellem Code synchronisieren

### Ziel

Die Doku muss den aktuellen Sprachvertrag korrekt beschreiben. Diese Phase soll **keine Runtime-Semantik ändern**. Sie korrigiert nur Doku und ergänzt Tests, die Doku-Code-Drift künftig sichtbar machen.

### Warum diese Phase zuerst?

Ein LLM-Coding-Agent arbeitet stark anhand der Dokumentation. Solange `docs/expressions.md` falsche Informationen enthält, besteht ein hohes Risiko, dass spätere Phasen falsche Features implementieren oder falsche Tests erwarten.

### Relevante Dateien

```text
docs/expressions.md
docs/index.md
docs/architecture.md
docs/mapping-dsl.md
src/main/java/guru/interlis/transformer/expr/FunctionRegistry.java
src/main/java/guru/interlis/transformer/expr/builtins/BasicFunctions.java
src/main/java/guru/interlis/transformer/expr/builtins/StringFunctions.java
src/main/java/guru/interlis/transformer/expr/builtins/DateFunctions.java
src/main/java/guru/interlis/transformer/expr/builtins/EnumFunctions.java
src/main/java/guru/interlis/transformer/expr/builtins/RefFunctions.java
src/main/java/guru/interlis/transformer/expr/builtins/MathFunctions.java
src/main/java/guru/interlis/transformer/expr/builtins/LookupFunctions.java
src/main/java/guru/interlis/transformer/expr/builtins/GeometryFunctions.java
```

### Konkrete Doku-Korrekturen

#### 1. `docs/expressions.md`

Korrigiere die Funktionsliste auf exakt registrierte Builtins:

```text
Basic:
- coalesce(a, b, ...) -> any
- defined(value) -> boolean
- notDefined(value) -> boolean
- isNull(value) -> boolean
- default(value, fallback) -> text/any, siehe tatsächliche Semantik
- null() -> null
- eq(a, b) -> boolean
- neq(a, b) -> boolean
- lt(a, b) -> boolean
- lte(a, b) -> boolean
- gt(a, b) -> boolean
- gte(a, b) -> boolean
- not(value) -> boolean

Syntax sugar / Operatoren:
- a == b wird zu eq(a, b)
- a != b wird zu neq(a, b), ausser bei null-Spezialfällen
- a < b wird zu lt(a, b)
- a <= b wird zu lte(a, b)
- a > b wird zu gt(a, b)
- a >= b wird zu gte(a, b)
- a and b wird lazy als ConditionalExpr modelliert
- a or b wird lazy als ConditionalExpr modelliert
- not a wird zu not(a)

String:
- concat
- substring
- trim
- upper
- lower
- replace
- truncate

Date:
- toXmlDateTime
- toInterlis1Date
- toDate
- now

Enum:
- enumMap
- enumDefault
- enumName

Reference:
- refOid
- refEquals

Math:
- div
- mul

Lookup:
- oid
- bagFirst
- lookup

Geometry:
- coordEquals
```

Entferne oder verschiebe aus der Hauptliste:

```text
round
abs
lookupOne
lookupMany
```

Wenn diese Namen erwähnt werden, dann nur im Abschnitt “planned / not implemented”.

#### 2. Comparison Operators nicht mehr als unsupported deklarieren

Entferne aus `Currently unsupported`:

```text
Comparison operators (>, <, >=, <=) — planned
```

Stattdessen dokumentiere:

```text
Comparison operators are supported for values that can be compared by the built-in comparison functions. They are parsed into function calls: lt/lte/gt/gte.
```

#### 3. Arithmetic Operators korrekt dokumentieren

Aktuell sind `+`, `-`, `*`, `/` als Operatoren nicht implementiert. Behalte im unsupported-Abschnitt:

```text
Arithmetic operators (+, -, *, /) are not supported. Use div(...) and mul(...). Additional arithmetic functions may be added later.
```

#### 4. Nested Paths ehrlich dokumentieren

Ersetze die Aussage:

```text
Paths can be nested for structures: ${alias.structure.attribute}
```

Durch:

```text
Only direct source attributes are currently supported as expression paths:
- alias.attribute
- ${alias.attribute}
- alias as reference/OID-bearing source object

Nested structure traversal such as alias.structure.attribute is reserved and not currently supported by the expression evaluator/type checker. Use the structural DSL features bags/nestedBags for BAG OF STRUCTURE mappings.
```

#### 5. `enumMap` nicht als Stub bezeichnen

Ersetze:

```text
enumMap ... Maps enum value using named map (stub)
```

Durch eine präzise Semantik:

```text
enumMap(value, mapName)
- mapName must name a table under mapping.enums.
- Source value is converted to text and looked up in that table.
- Target values true/false produce boolean values.
- Numeric target values produce numeric values.
- Other values produce enum values.
- Missing source mapping currently reports a warning and returns null.
```

#### 6. `lookup(...)` ehrlich dokumentieren

Dokumentiere:

```text
lookup(classPath, keyAttr, keyValue, returnAttr)
- Uses SourceLookupIndex.
- Searches across all inputs because inputId is currently not part of the function signature.
- Returns null and reports a warning if no match is found.
- Reports a warning if multiple matches have different return values, then uses the first match.
- The return type is currently UNKNOWN at compile time.
- Prefer structural sources + joins where possible.
```

#### 7. `docs/index.md`

Korrigiere mindestens:

```text
enumMap() | Stub (Pass-through)
```

zu etwas wie:

```text
enumMap() | Supported, with documented missing-value warning behavior
```

#### 8. `docs/architecture.md`

Korrigiere die Builtin-Funktionsliste. Wenn dort steht:

```text
Math (round, abs)
```

ersetze mit:

```text
Math (div, mul)
```

Oder ergänze, falls Phase 7 später `round`/`abs` implementiert.

### Tests ergänzen

Erstelle einen neuen Test:

```text
src/test/java/guru/interlis/transformer/expr/ExpressionDocumentationTest.java
```

Minimaler Testumfang:

```java
class ExpressionDocumentationTest {

    @Test
    void expressionsDocumentationMentionsAllRegisteredFunctions() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        StringFunctions.registerAll(registry);
        DateFunctions.registerAll(registry);
        EnumFunctions.registerAll(registry);
        RefFunctions.registerAll(registry);
        MathFunctions.registerAll(registry);
        LookupFunctions.registerAll(registry);
        GeometryFunctions.registerAll(registry);

        String docs = Files.readString(Path.of("docs/expressions.md"));
        assertThat(registry.all().keySet())
            .allSatisfy(fn -> assertThat(docs).contains("`" + fn + "`"));
    }

    @Test
    void expressionsDocumentationDoesNotClaimImplementedComparisonOperatorsAreUnsupported() {
        String docs = Files.readString(Path.of("docs/expressions.md"));
        assertThat(docs).doesNotContain("Comparison operators (`>`, `<`, `>=`, `<=`) — planned");
    }

    @Test
    void expressionsDocumentationDoesNotListUnregisteredMathFunctionsAsBuiltins() {
        FunctionRegistry registry = defaultRegistry();
        assertThat(registry.resolve("round")).isEmpty();
        assertThat(registry.resolve("abs")).isEmpty();

        String docs = Files.readString(Path.of("docs/expressions.md"));
        // Either not present as builtin table entries, or clearly marked as planned.
        assertThat(docs).doesNotContain("| `round` | `(value, scale) → number` | Rounds to given decimal scale |");
        assertThat(docs).doesNotContain("| `abs` | `(value) → number` | Absolute value |");
    }
}
```

Nutze bestehende Teststile und AssertJ.

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionDocumentationTest"
./gradlew test --tests "guru.interlis.transformer.expr.FunctionRegistryTest"
./gradlew test --tests "guru.interlis.transformer.expr.ComparisonOperatorsTest"
./gradlew test
```

### Definition of Done

- `docs/expressions.md` beschreibt nur aktuell implementierte Builtins als Builtins.
- Comparison-/Boolean-Operatoren sind korrekt dokumentiert.
- Arithmetic-Operatoren bleiben korrekt als unsupported dokumentiert.
- `enumMap` wird nicht mehr als Stub beschrieben.
- `lookup(...)` ist mit Grenzen dokumentiert.
- Nested Paths werden als unsupported/reserved dokumentiert.
- `ExpressionDocumentationTest` verhindert die wichtigsten Drift-Fälle.
- Alle ausgeführten Tests sind dokumentiert.

---

## Phase 2 — Default FunctionRegistry zentralisieren

### Ziel

Aktuell wird die Default-Registry an mehreren Stellen manuell aufgebaut, z. B. in `ExpressionEngine` und `MappingCompiler` bzw. über direkte `registerAll(...)`-Aufrufe. Das ist fehleranfällig. Diese Phase führt eine zentrale Factory-Methode ein, ohne Semantik zu ändern.

### Relevante Dateien

```text
src/main/java/guru/interlis/transformer/expr/FunctionRegistry.java
src/main/java/guru/interlis/transformer/expr/ExpressionEngine.java
src/main/java/guru/interlis/transformer/mapping/compiler/MappingCompiler.java
src/test/java/guru/interlis/transformer/expr/FunctionRegistryTest.java
src/test/java/guru/interlis/transformer/expr/ExpressionDocumentationTest.java
```

### Aufgaben

#### 1. `FunctionRegistry.defaultRegistry()` einführen

In `FunctionRegistry.java` ergänzen:

```java
public static FunctionRegistry defaultRegistry() {
    FunctionRegistry registry = new FunctionRegistry();
    BasicFunctions.registerAll(registry);
    StringFunctions.registerAll(registry);
    DateFunctions.registerAll(registry);
    EnumFunctions.registerAll(registry);
    RefFunctions.registerAll(registry);
    MathFunctions.registerAll(registry);
    LookupFunctions.registerAll(registry);
    GeometryFunctions.registerAll(registry);
    return registry;
}
```

Dazu Imports ergänzen:

```java
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.StringFunctions;
import guru.interlis.transformer.expr.builtins.DateFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.expr.builtins.RefFunctions;
import guru.interlis.transformer.expr.builtins.MathFunctions;
import guru.interlis.transformer.expr.builtins.LookupFunctions;
import guru.interlis.transformer.expr.builtins.GeometryFunctions;
```

#### 2. `ExpressionEngine` umstellen

In `ExpressionEngine` den Konstruktor oder Initialisierungscode ersetzen:

Vorher sinngemäss:

```java
this.functionRegistry = new FunctionRegistry();
BasicFunctions.registerAll(functionRegistry);
...
```

Nachher:

```java
this.functionRegistry = FunctionRegistry.defaultRegistry();
```

Falls `ExpressionEngine` bereits einen Konstruktor für injizierte Registry hat, beibehalten.

#### 3. `MappingCompiler` umstellen

In `MappingCompiler` gibt es vermutlich eine private `defaultRegistry()`-Methode. Diese ersetzen durch:

```java
this.functionRegistry = FunctionRegistry.defaultRegistry();
```

oder falls Konstruktoren:

```java
public MappingCompiler() {
    this(FunctionRegistry.defaultRegistry());
}
```

#### 4. Tests aktualisieren

In `ExpressionDocumentationTest` und anderen Tests ebenfalls `FunctionRegistry.defaultRegistry()` nutzen, damit nicht jeder Test eigene Registrierungslogik dupliziert.

### Tests

Ergänze oder aktualisiere `FunctionRegistryTest`:

```java
@Test
void defaultRegistryContainsAllBuiltinFunctionGroups() {
    FunctionRegistry registry = FunctionRegistry.defaultRegistry();

    assertThat(registry.resolve("coalesce")).isPresent();
    assertThat(registry.resolve("truncate")).isPresent();
    assertThat(registry.resolve("toXmlDateTime")).isPresent();
    assertThat(registry.resolve("enumMap")).isPresent();
    assertThat(registry.resolve("refEquals")).isPresent();
    assertThat(registry.resolve("div")).isPresent();
    assertThat(registry.resolve("lookup")).isPresent();
    assertThat(registry.resolve("coordEquals")).isPresent();
}
```

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.FunctionRegistryTest"
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionDocumentationTest"
./gradlew test
```

### Definition of Done

- Es gibt genau eine zentrale Default-Registry-Factory.
- `ExpressionEngine` und `MappingCompiler` verwenden dieselbe Factory.
- Tests verwenden möglichst ebenfalls dieselbe Factory.
- Keine Funktionssemantik wurde geändert.

---

## Phase 3 — ExpressionTypeChecker als einzige Typprüfquelle etablieren

### Ziel

Doppelte Typprüfungslogik entfernen. `ExpressionCompiler` soll nur noch parsen, den `ExpressionTypeChecker` aufrufen, Determinismus bestimmen und ein `CompiledExpression` erzeugen.

### Warum wichtig?

Aktuell existieren ähnliche Methoden sowohl in `ExpressionCompiler` als auch in `ExpressionTypeChecker`. Wenn später `enumMap`, `lookup`, nested paths oder neue Math-Funktionen geändert werden, können Compiler und TypeChecker auseinanderlaufen.

### Relevante Klassen

```text
src/main/java/guru/interlis/transformer/expr/ExpressionCompiler.java
src/main/java/guru/interlis/transformer/expr/ExpressionTypeChecker.java
src/main/java/guru/interlis/transformer/expr/ExpressionParser.java
src/main/java/guru/interlis/transformer/mapping/plan/CompiledExpression.java
src/main/java/guru/interlis/transformer/mapping/plan/ResolvedPath.java
src/main/java/guru/interlis/transformer/mapping/plan/ExpressionCompileContext.java
```

### Bestehende Problemstellen

In `ExpressionCompiler` sollen langfristig verschwinden oder delegieren:

```java
private TypeInfo resolveType(...)
private TypeInfo resolvePathType(...)
private TypeInfo resolveFunctionType(...)
private TypeInfo resolveConditionalType(...)
private void checkEnumMapValidation(...)
private TypeInfo inferEnumMapReturnType(...)
private void checkArgTypeCompatibility(...)
private boolean isArgTypeCompatible(...)
private static AttributeDef findAttribute(...)
private static RoleDef findRole(...)
```

In `ExpressionTypeChecker` sind entsprechende Methoden bereits vorhanden:

```java
public TypeInfo check(Expression expr, Set<ResolvedPath> paths)
private TypeInfo checkPath(PathExpr path, Set<ResolvedPath> paths)
private TypeInfo checkFunction(FunctionCallExpr call, Set<ResolvedPath> paths)
private TypeInfo checkConditional(ConditionalExpr cond, Set<ResolvedPath> paths)
private void checkEnumMapFunction(...)
private TypeInfo inferEnumMapReturnType(...)
```

### Aufgaben

#### 1. Kleinen Result-Typ einführen

In `ExpressionTypeChecker` entweder als nested record oder eigenständige Klasse:

```java
public record Result(TypeInfo resultType, Set<ResolvedPath> resolvedPaths) {}
```

Dann öffentliche Methode ergänzen:

```java
public Result check(Expression expr) {
    Set<ResolvedPath> paths = new HashSet<>();
    TypeInfo type = check(expr, paths);
    return new Result(type, Set.copyOf(paths));
}
```

Die bestehende Methode `check(Expression expr, Set<ResolvedPath> paths)` kann vorerst erhalten bleiben, um Tests nicht unnötig stark zu ändern.

#### 2. `ExpressionCompiler.compile(...)` umstellen

Zielstruktur:

```java
public CompiledExpression compile(String expression, ExpressionCompileContext context, DiagnosticCollector diagnostics) {
    if (expression == null || expression.isBlank()) {
        return new CompiledExpression(expression, new LiteralExpr(NullValue.INSTANCE), TypeInfo.UNKNOWN, true, Set.of());
    }

    String trimmed = expression.trim();
    Expression ast;
    try {
        ast = ExpressionParser.parse(trimmed);
    } catch (ExpressionParseException e) {
        // bestehende Diagnose beibehalten
    }

    ExpressionTypeChecker checker = new ExpressionTypeChecker(context, diagnostics);
    ExpressionTypeChecker.Result result = checker.check(ast);
    boolean deterministic = isDeterministic(ast, context.functionRegistry());

    return new CompiledExpression(trimmed, ast, result.resultType(), deterministic, result.resolvedPaths());
}
```

#### 3. Doppelte Methoden entfernen

Nach Umstellung alle nicht mehr genutzten privaten Methoden aus `ExpressionCompiler` entfernen. Behalten darf `ExpressionCompiler.classifyIliAttr(...)`, falls andere Klassen es verwenden. Besser wäre langfristig, auch diese Methode in `ExpressionTypeChecker` oder `TypeResolver` zu verschieben, aber das ist nicht zwingend in dieser Phase.

Prüfe per IDE/grep:

```bash
grep -R "ExpressionCompiler.classifyIliAttr" -n src/main/java src/test/java
```

Wenn viele Aufrufer existieren, `classifyIliAttr` vorerst als public/static Kompatibilitätsmethode belassen und intern an `ExpressionTypeChecker.classifyIliAttr` oder `TypeResolver` delegieren.

#### 4. Tests vergleichen

Vor der Implementierung gezielt Tests prüfen:

```text
ExpressionCompilerTest
ExpressionTypeCheckerTest
UnknownFunctionCompileTest
WrongArgumentCountCompileTest
WrongArgumentTypeCompileTest
EnumTargetValidationTest
NestedFunctionTypeTest
BarePathValidationTest
```

Ergänze mindestens einen Test, der sicherstellt, dass `ExpressionCompiler` den TypeChecker verwendet. Nicht über Mocking, sondern über beobachtbares Verhalten:

```java
@Test
void compilerReportsSameDiagnosticsAsTypeCheckerForWrongArgumentType() { ... }
```

Oder einfacher: vorhandene Tests für falsche Argumenttypen müssen weiterhin über `ExpressionCompiler.compile(...)` laufen.

### Besondere Vorsicht

- `ExpressionCompiler` erzeugt `CompiledExpression` mit `resolvedPaths`. Diese dürfen nicht verloren gehen.
- Diagnostics-Codes müssen gleich bleiben:
  - `EXPR_UNKNOWN_FUNC`
  - `EXPR_WRONG_ARG_COUNT`
  - `EXPR_WRONG_ARG_TYPE`
  - `EXPR_ENUM_MAP_MISSING`
  - `EXPR_NON_DETERMINISTIC`
  - `MAP_UNKNOWN_SOURCE_ATTRIBUTE`
- Severity darf sich nicht unbeabsichtigt ändern.

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionCompilerTest"
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionTypeCheckerTest"
./gradlew test --tests "guru.interlis.transformer.expr.UnknownFunctionCompileTest"
./gradlew test --tests "guru.interlis.transformer.expr.WrongArgumentCountCompileTest"
./gradlew test --tests "guru.interlis.transformer.expr.WrongArgumentTypeCompileTest"
./gradlew test --tests "guru.interlis.transformer.expr.EnumTargetValidationTest"
./gradlew test --tests "guru.interlis.transformer.expr.NestedFunctionTypeTest"
./gradlew test
```

### Definition of Done

- `ExpressionCompiler` enthält keine eigene vollständige Typprüfungslogik mehr.
- `ExpressionTypeChecker` ist die zentrale Stelle für Expression-Typen und resolved paths.
- Alle bestehenden Compiler-/TypeChecker-Tests laufen.
- Diagnostics bleiben kompatibel.

---

## Phase 4 — `lookup(...)` dokumentieren, absichern und `lookupIn(...)` optional ergänzen

### Ziel

`lookup(...)` bleibt kompatibel, wird aber ehrlich als unscoped Convenience-Funktion dokumentiert und testmässig abgesichert. Optional wird `lookupIn(...)` als input-gescopte Variante ergänzt.

### Relevante Klassen

```text
src/main/java/guru/interlis/transformer/expr/builtins/LookupFunctions.java
src/main/java/guru/interlis/transformer/expr/FunctionRegistry.java
src/main/java/guru/interlis/transformer/expr/EvalContext.java
src/main/java/guru/interlis/transformer/state/SourceLookupIndex.java
src/main/java/guru/interlis/transformer/state/InMemorySourceLookupIndex.java
src/main/java/guru/interlis/transformer/state/LookupKey.java
src/main/java/guru/interlis/transformer/state/CanonicalValue.java
src/test/java/guru/interlis/transformer/expr/LookupFunctionsTest.java
docs/expressions.md
```

### Aktuelles Verhalten

`LookupFunctions.lookup(...)`:

```java
LookupKey key = new LookupKey(
        null, // inputId: search across all inputs
        classPath,
        keyAttr,
        new CanonicalValue("text", keyValue, true));
```

`InMemorySourceLookupIndex.lookup(...)` filtert nur nach `inputId`, wenn diese im Key gesetzt ist:

```java
if (key.inputId() == null || key.inputId().isBlank()) {
    return List.copyOf(records);
}
return records.stream()
        .filter(r -> key.inputId().equals(r.sourceFileId()))
        .toList();
```

### Aufgaben

#### 1. Bestehende `lookup(...)`-Tests vervollständigen

In `LookupFunctionsTest` sicherstellen, dass diese Fälle abgedeckt sind:

```java
lookupReturnsAttributeForSingleMatch()
lookupReturnsNullAndReportsWarningWhenNoMatch()
lookupReportsWarningForMultipleMatchesWithDifferentReturnValues()
lookupDoesNotWarnForMultipleMatchesWithSameReturnValue()
lookupReturnsNullAndReportsErrorWhenLookupIndexMissing()
lookupSearchesAcrossInputsWhenInputIsUnscoped()
```

Falls Testnamen im Projektstil anders sind, bestehende Konventionen nutzen.

#### 2. `lookup(...)`-Dokumentation finalisieren

In `docs/expressions.md` exakt beschreiben:

```text
lookup(classPath, keyAttr, keyValue, returnAttr)

Compatibility function. It searches SourceLookupIndex across all inputs.
Use only when classPath/keyAttr/keyValue identify a unique source record across all inputs.
On no match: warning + null.
On multiple matches with different return values: warning + first value.
Return type is UNKNOWN at compile time.
Prefer structural joins for modelled relationships.
```

#### 3. Optional `lookupIn(...)` ergänzen

Wenn diese Phase bewusst erweitert werden soll, implementiere zusätzlich:

```text
lookupIn(inputId, classPath, keyAttr, keyValue, returnAttr)
```

In `LookupFunctions.registerAll(...)`:

```java
registry.register(
        "lookupIn",
        TypeInfo.UNKNOWN,
        List.of(
                new FunctionDef.FunctionParam("inputId", TypeInfo.TEXT),
                new FunctionDef.FunctionParam("classPath", TypeInfo.TEXT),
                new FunctionDef.FunctionParam("keyAttr", TypeInfo.TEXT),
                new FunctionDef.FunctionParam("keyValue", TypeInfo.TEXT),
                new FunctionDef.FunctionParam("returnAttr", TypeInfo.TEXT)),
        LookupFunctions::lookupIn);
```

Implementierung:

```java
static Value lookupIn(List<Value> args, EvalContext ctx) {
    if (args.size() < 5) { ... EXPR_WRONG_ARG_COUNT ... }
    String inputId = args.get(0).asText();
    String classPath = args.get(1).asText();
    String keyAttr = args.get(2).asText();
    String keyValue = args.get(3).asText();
    String returnAttr = args.get(4).asText();
    return lookupInternal(inputId, classPath, keyAttr, keyValue, returnAttr, ctx, "lookupIn");
}
```

Refactore bestehendes `lookup(...)` auf eine private Hilfsmethode:

```java
private static Value lookupInternal(
        String inputId,
        String classPath,
        String keyAttr,
        String keyValue,
        String returnAttr,
        EvalContext ctx,
        String functionName) { ... }
```

Wichtig: Bestehendes `lookup(...)` bleibt vollständig kompatibel.

#### 4. Tests für `lookupIn(...)`

Wenn `lookupIn(...)` implementiert wird:

```java
lookupInRestrictsLookupToInputId()
lookupInReturnsNullWhenMatchExistsOnlyInOtherInput()
lookupInReportsWrongArgCount()
```

#### 5. Keine produktiven Profile migrieren

In dieser Phase keine produktiven YAML-Profile automatisch von `lookup(...)` auf `lookupIn(...)` migrieren. Das wäre eine separate fachliche Profiländerung mit RealData-Gate.

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.LookupFunctionsTest"
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionDocumentationTest"
./gradlew test
```

Wenn `lookupIn(...)` in produktiven Profilen später genutzt wird, zusätzlich:

```bash
./gradlew integrationTest
./gradlew realDataTest
```

### Definition of Done

- Bestehendes `lookup(...)` ist dokumentiert und getestet.
- Keine bestehende Profilsemantik wurde gebrochen.
- Optional: `lookupIn(...)` ist implementiert, registriert, dokumentiert und getestet.
- `FunctionRegistry.defaultRegistry().resolve("lookupIn")` ist nur dann present, wenn die Funktion implementiert wurde.

---

## Phase 5 — Enum-Mapping-Semantik stabilisieren

### Ziel

`enumMap(...)` ist produktiv zentral. Diese Phase dokumentiert die aktuelle Semantik präzise und ergänzt optional kompatible strengere Varianten, ohne bestehende Profile zu brechen.

### Relevante Klassen

```text
src/main/java/guru/interlis/transformer/expr/builtins/EnumFunctions.java
src/main/java/guru/interlis/transformer/expr/ExpressionTypeChecker.java
src/main/java/guru/interlis/transformer/expr/ExpressionCompiler.java
src/main/java/guru/interlis/transformer/expr/FunctionRegistry.java
src/main/java/guru/interlis/transformer/diag/DiagnosticCode.java
src/test/java/guru/interlis/transformer/expr/BuiltinFunctionsTest.java
src/test/java/guru/interlis/transformer/expr/EnumTargetValidationTest.java
docs/expressions.md
```

### Aktuelles Verhalten

`EnumFunctions.enumMap(...)`:

```java
if (enumMaps == null || !enumMaps.containsKey(mapName)) {
    diagnostic EXPR_UNSUPPORTED WARNING
    return val; // pass-through
}

String targetValue = mapping.get(sourceKey);
if (targetValue == null) {
    diagnostic EXPR_TYPE WARNING
    return NullValue.INSTANCE;
}

if ("true"/"false") -> BooleanValue
if numeric -> NumberValue
else -> EnumValue
```

`ExpressionTypeChecker` bzw. `ExpressionCompiler` melden fehlende enum map compile-time mit:

```text
DiagnosticCode.EXPR_ENUM_MAP_MISSING
Severity.ERROR
```

### Aufgaben

#### 1. Doku präzisieren

In `docs/expressions.md`:

```text
enumMap(value, mapName)
- The named map must exist under mapping.enums. Missing map names are compile-time errors in typed compilation.
- The source value is converted to text.
- If the source key is not present in the map, enumMap reports a warning and returns null.
- Target strings "true" and "false" produce boolean values.
- Numeric target strings produce numeric values.
- Other target strings produce enum values.
```

#### 2. Tests für aktuelle Semantik ergänzen

In `BuiltinFunctionsTest` oder eigener `EnumFunctionsTest`:

```java
enumMapReturnsBooleanForBooleanMappingValue()
enumMapReturnsNumberForNumericMappingValue()
enumMapReturnsEnumForEnumMappingValue()
enumMapReturnsNullAndWarningForMissingSourceValue()
enumMapMissingTableIsCompileTimeErrorInTypedCompilation()
```

Falls bereits ähnlich vorhanden, nicht duplizieren, sondern ergänzen.

#### 3. Optional kompatible Varianten ergänzen

Wenn gewünscht, zusätzlich neue Funktionen implementieren:

```text
enumMapDefault(value, mapName, fallback)
enumMapStrict(value, mapName)
```

Empfohlene Semantik:

```text
enumMapDefault:
- Wie enumMap.
- Wenn sourceKey im Mapping fehlt: fallback zurückgeben, keine Warning oder höchstens INFO.

enumMapStrict:
- Wie enumMap.
- Wenn sourceKey im Mapping fehlt: ERROR diagnostic + null.
- Wenn mapName fehlt: compile-time error bleibt.
```

Registrierung in `EnumFunctions.registerAll(...)`:

```java
registry.register(
        "enumMapDefault",
        TypeInfo.ENUM,
        List.of(
                new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                new FunctionDef.FunctionParam("mapName", TypeInfo.TEXT),
                new FunctionDef.FunctionParam("fallback", TypeInfo.UNKNOWN)),
        EnumFunctions::enumMapDefault);

registry.register(
        "enumMapStrict",
        TypeInfo.ENUM,
        List.of(
                new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                new FunctionDef.FunctionParam("mapName", TypeInfo.TEXT)),
        EnumFunctions::enumMapStrict);
```

Wichtig: `ExpressionTypeChecker.inferEnumMapReturnType(...)` muss diese neuen Funktionen entweder ebenfalls behandeln oder bewusst `TypeInfo.ENUM` zurückgeben. Wenn `enumMapDefault` boolean/numeric maps unterstützen soll, muss Type-Inferenz analog `enumMap` laufen.

#### 4. Diagnostics sauber wählen

Wenn neue Diagnostics nötig sind, `DiagnosticCode` ergänzen. Bestehende Codes nicht zweckentfremden, wenn es semantisch unklar wird.

Mögliche neue Codes:

```text
ILITRF-EXPR-ENUM-MAP-MISSING-VALUE
```

Nur ergänzen, wenn bestehende Codes nicht reichen. Kleine Änderung bevorzugen.

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.BuiltinFunctionsTest"
./gradlew test --tests "guru.interlis.transformer.expr.EnumTargetValidationTest"
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionDocumentationTest"
./gradlew test
```

Wenn produktive Profile geändert werden:

```bash
./gradlew integrationTest
./gradlew realDataTest
```

### Definition of Done

- Aktuelle `enumMap(...)`-Semantik ist dokumentiert und getestet.
- Bestehende Profile bleiben kompatibel.
- Optional neue Funktionen sind registriert, dokumentiert und getestet.
- Type-Inferenz bleibt konsistent.

---

## Phase 6 — Nested Paths offiziell begrenzen oder bewusst implementieren

### Ziel

Kurzfristig soll der Sprachvertrag ehrlich sein: nested paths sind nicht unterstützt. Diese Phase entscheidet bewusst zwischen Dokumentationsbegrenzung und echter Implementierung. Empfehlung: **kurzfristig nur begrenzen**, nicht implementieren.

### Relevante Klassen

```text
src/main/java/guru/interlis/transformer/expr/PathExpr.java
src/main/java/guru/interlis/transformer/expr/ExpressionParser.java
src/main/java/guru/interlis/transformer/expr/ExpressionEngine.java
src/main/java/guru/interlis/transformer/expr/ExpressionTypeChecker.java
src/main/java/guru/interlis/transformer/expr/ExpressionCompiler.java
src/test/java/guru/interlis/transformer/expr/BarePathValidationTest.java
src/test/java/guru/interlis/transformer/expr/ExpressionParserTest.java
docs/expressions.md
```

### Aktuelles technisches Verhalten

`PathExpr`:

```java
public record PathExpr(String alias, String attributeName) implements Expression {}
```

`ExpressionParser.parseIdentifierExpr()`:

```java
if (name.contains(".")) {
    String[] parts = name.split("\\.", 2);
    return new PathExpr(parts[0], parts[1]);
}
return new PathExpr(name, null);
```

`ExpressionEngine.resolveSourcePath(...)`:

```java
String[] parts = path.split("\\.", 2);
String attrName = parts[1];
...
source.getattrvalue(attrName)
```

Damit ist `src.structure.attribute` kein echter Strukturpfad, sondern ein direkter Attributname `structure.attribute`.

### Empfohlene Umsetzung: Begrenzung und Test

#### 1. Doku anpassen

In `docs/expressions.md`:

```text
Nested structure paths are currently not supported by expressions.
Use bags/nestedBags in the Mapping DSL to transform BAG OF STRUCTURE content.
```

#### 2. Test ergänzen

In `ExpressionParserTest` oder `BarePathValidationTest` einen Test ergänzen, der den Status festhält:

```java
@Test
void nestedPathIsParsedAsSingleAttributeNameForNow() {
    Expression expr = ExpressionParser.parse("src.structure.attribute");
    assertThat(expr).isInstanceOf(PathExpr.class);
    PathExpr path = (PathExpr) expr;
    assertThat(path.alias()).isEqualTo("src");
    assertThat(path.attributeName()).isEqualTo("structure.attribute");
}
```

Dieser Test ist bewusst kein Wunschzustand, sondern dokumentiert den Ist-Zustand. Alternativ kann man einen Compiler-Test schreiben, der bei unbekanntem `structure.attribute` sauber `MAP_UNKNOWN_SOURCE_ATTRIBUTE` meldet.

#### 3. Optional: Unsupported Diagnostic statt stiller Interpretation

Wenn man strikter sein will, kann `ExpressionTypeChecker.checkPath(...)` prüfen:

```java
if (attrName != null && attrName.contains(".")) {
    diagnostics.add(new Diagnostic(
        DiagnosticCode.EXPR_UNSUPPORTED,
        Severity.ERROR,
        "Nested expression paths are not supported: " + alias + "." + attrName,
        context.ruleId(),
        "Use bags/nestedBags for structures or direct alias.attribute paths"));
    return TypeInfo.UNKNOWN;
}
```

Das wäre ein Breaking Change, falls Profile versehentlich solche Pfade nutzen. Vorher prüfen:

```bash
grep -R "[A-Za-z0-9_]\.[A-Za-z0-9_]\.[A-Za-z0-9_]" -n profiles src/test/resources
```

Nur umsetzen, wenn keine produktiven Profile betroffen sind oder Migration erfolgt.

### Nicht in dieser Phase implementieren

Nicht sofort umbauen auf:

```java
PathExpr(String alias, List<String> segments)
```

Das wäre ein grösserer Umbau mit Auswirkungen auf Parser, Engine, TypeChecker, ResolvedPath und TypeSystemFacade.

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionParserTest"
./gradlew test --tests "guru.interlis.transformer.expr.BarePathValidationTest"
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionDocumentationTest"
./gradlew test
```

### Definition of Done

- Doku behauptet keine echte nested-path-Unterstützung mehr.
- Parser-/Compiler-Verhalten ist durch Tests dokumentiert.
- Keine produktiven Profile brechen unerwartet.

---

## Phase 7 — MathFunctions vervollständigen oder bewusst klein halten

### Ziel

Die Math-Funktionen müssen zu Doku und Profilbedarf passen. Minimal: `div` und `mul` korrekt dokumentieren. Optional: `round`, `abs`, `add`, `sub`, `min`, `max`, `toNumber` implementieren.

### Relevante Klassen

```text
src/main/java/guru/interlis/transformer/expr/builtins/MathFunctions.java
src/main/java/guru/interlis/transformer/expr/FunctionRegistry.java
src/main/java/guru/interlis/transformer/expr/FunctionDef.java
src/test/java/guru/interlis/transformer/expr/BuiltinFunctionsTest.java
src/test/java/guru/interlis/transformer/expr/NumericPrecisionTest.java
src/test/java/guru/interlis/transformer/expr/WrongArgumentTypeCompileTest.java
docs/expressions.md
```

### Variante A — Minimal stabilisieren

Wenn keine neuen Funktionen gewünscht sind:

1. `docs/expressions.md` listet nur:

```text
div(value, divisor)
mul(value, factor)
```

2. `round` und `abs` bleiben unter “planned”.
3. `ExpressionDocumentationTest` stellt sicher, dass `round`/`abs` nicht als Builtins dokumentiert sind.

Diese Variante ist klein und sicher.

### Variante B — Fehlende Basisfunktionen implementieren

Wenn Math erweitert werden soll, implementiere in `MathFunctions.registerAll(...)`:

```text
add(value, addend) -> numeric
sub(value, subtrahend) -> numeric
round(value, scale) -> numeric
abs(value) -> numeric
min(a, b) -> numeric
max(a, b) -> numeric
toNumber(value) -> numeric
```

Konkrete Implementierungshinweise:

- Nutze vorhandene private Methode `toBigDecimal(Value v)`.
- Bei undefiniertem oder nicht numerischem Wert `NullValue.INSTANCE` zurückgeben.
- Bei Division durch 0 weiterhin `NullValue.INSTANCE`, kein Throw.
- `round(value, scale)` mit `BigDecimal.setScale(scale, RoundingMode.HALF_UP)`.
- `scale` aus `NumberValue` lesen; wenn keine Ganzzahl, `intValue()` verwenden oder Diagnostic? Für kleine Phase: `intValue()` dokumentieren.
- `abs` mit `value.abs()`.
- `toNumber(TextValue)` nutzt `new BigDecimal(text)`, bei Fehler `NullValue.INSTANCE`.

Beispiel:

```java
static Value round(List<Value> args, EvalContext ctx) {
    if (args.size() < 2 || !args.get(0).isDefined() || !args.get(1).isDefined()) return NullValue.INSTANCE;
    BigDecimal value = toBigDecimal(args.get(0));
    BigDecimal scaleValue = toBigDecimal(args.get(1));
    if (value == null || scaleValue == null) return NullValue.INSTANCE;
    int scale = scaleValue.intValue();
    return new NumberValue(value.setScale(scale, RoundingMode.HALF_UP));
}
```

### Tests

In `BuiltinFunctionsTest` oder neu `MathFunctionsTest`:

```java
divDividesNumbersWithScale()
divReturnsNullForZeroDivisor()
mulMultipliesNumbers()
addAddsNumbers()
subSubtractsNumbers()
roundRoundsHalfUpToScale()
absReturnsAbsoluteValue()
minReturnsSmallerNumber()
maxReturnsLargerNumber()
toNumberConvertsTextNumber()
toNumberReturnsNullForInvalidText()
```

In `FunctionRegistryTest`:

```java
defaultRegistryContainsMathFunctions()
```

In `ExpressionDocumentationTest` sicherstellen, dass neu implementierte Funktionen dokumentiert sind.

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.BuiltinFunctionsTest"
./gradlew test --tests "guru.interlis.transformer.expr.NumericPrecisionTest"
./gradlew test --tests "guru.interlis.transformer.expr.FunctionRegistryTest"
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionDocumentationTest"
./gradlew test
```

### Definition of Done

- Doku und `MathFunctions.registerAll(...)` stimmen überein.
- Entweder keine neuen Math-Funktionen oder alle neuen Funktionen sind vollständig getestet.
- Keine Arithmetic Operators `+ - * /` wurden eingeführt.

---

## Phase 8 — `create`, `metadata`, `joins` und Feature-Status final klassifizieren

### Ziel

Die DSL-Oberfläche soll ehrlich klassifiziert sein: stabil, partial, experimental oder reserved. Diese Phase ändert primär Doku/Feature-Matrix und nur dann Compiler-Diagnostics, wenn ein Feature aktuell stillschweigend zu viel akzeptiert.

### Relevante Dateien

```text
docs/mapping-dsl.md
docs/index.md
src/main/java/guru/interlis/transformer/mapping/model/JobConfig.java
src/main/java/guru/interlis/transformer/mapping/compiler/CreateCompiler.java
src/main/java/guru/interlis/transformer/mapping/compiler/JoinCompiler.java
src/main/java/guru/interlis/transformer/mapping/compiler/DslCapabilityValidator.java
src/main/java/guru/interlis/transformer/feature/FeatureMatrix.java
src/test/java/guru/interlis/transformer/mapping/compiler/CreateCompilationTest.java
src/test/java/guru/interlis/transformer/mapping/compiler/JoinCompilationTest.java
src/test/java/guru/interlis/transformer/feature/FeatureMatrixTest.java
```

### Status-Vorschlag

| DSL-Feature | Status | Begründung |
|---|---|---|
| `target`, `sources`, `assign`, `identity`, `refs`, `bags` | stable | Produktiv intensiv genutzt |
| `source.where`, `bag.where` | stable | Produktiv genutzt |
| `rule.where` | supported, wenig genutzt | Runtime/Compiler vorhanden, produktiv aktuell 0x |
| `joins` | partial | Genau 1 Join pro Rule; `JoinCompiler` lehnt mehrere ab |
| `losses` | supported, emerging | Wenig genutzt, aber sinnvoll und implementiert |
| `lookup(...)` | supported-but-limited | String-basiert, unscoped, UNKNOWN return type |
| `create` | experimental | Nicht produktiv genutzt; keine refs/bags/identity/registration vollständig |
| `metadata` | reserved oder documentation-only | In DTO vorhanden, aber nicht in `RulePlan` sichtbar genutzt |
| `external` OID strategy | unsupported/reserved | Stub |
| `expression` basket strategy | unsupported/reserved | Stub |
| nested expression paths | unsupported/reserved | Doku begrenzen |

### Aufgaben

#### 1. `docs/mapping-dsl.md` prüfen

Sicherstellen:

- `compileMode` ist `strict | compatible | report`.
- `joins` sagt klar: maximal ein Join pro Rule.
- `create` ist experimentell.
- Nicht unterstützte `create`-Unterfeatures werden aufgelistet.
- `metadata` wird entweder als reserved/documentation-only erklärt oder entfernt.

#### 2. `FeatureMatrix.java` aktualisieren

Prüfe Einträge zu:

```text
enumMap()
Multi-source joins
create directives
Expression language
Mapping DSL
lookup
```

Wenn `enumMap()` noch als `STUB` markiert ist, korrigieren auf passenden Status:

```java
FeatureStatus.SUPPORTED
```

oder falls die Missing-Policy noch problematisch ist:

```java
FeatureStatus.PARTIAL
```

Aber nicht mehr `STUB`, weil produktive Profile `enumMap` intensiv nutzen.

Für `create`:

```java
FeatureStatus.EXPERIMENTAL
```

Falls `FeatureStatus` keinen solchen Wert hat, vorhandene Statuswerte prüfen und ggf. nicht erweitern, wenn das zu gross wird. Alternative: Beschreibung deutlich “experimental” nennen.

#### 3. Tests anpassen

`FeatureMatrixTest` muss neue Statuswerte akzeptieren.

`JoinCompilationTest` sollte enthalten:

```java
multipleJoinsInOneRuleProduceUnsupportedFeatureDiagnostic()
singleEquiJoinCompiles()
nonEquiJoinProducesDiagnostic()
```

`CreateCompilationTest` sollte enthalten:

```java
createWithClassAndAssignCompilesAsExperimentalFeature()
createUnknownTargetClassProducesDiagnostic()
```

Wenn `metadata` reserved sein soll, ergänze Test in `DslCapabilityValidator`-Nähe:

```java
metadataIsAcceptedAsDocumentationOnly()
```

oder

```java
unsupportedMetadataFieldProducesDiagnostic()
```

je nach Entscheidung.

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.mapping.compiler.JoinCompilationTest"
./gradlew test --tests "guru.interlis.transformer.mapping.compiler.CreateCompilationTest"
./gradlew test --tests "guru.interlis.transformer.feature.FeatureMatrixTest"
./gradlew test
```

### Definition of Done

- DSL-Feature-Status ist in Doku und Feature-Matrix konsistent.
- `enumMap` wird nicht mehr als Stub dargestellt.
- `create` wird nicht als voll produktiv dargestellt.
- `joins` sind explizit auf einen Join pro Rule begrenzt.
- Tests sichern diese Statusaussagen ab.

---

## Phase 9 — Funktionsreferenz generieren oder maschinell validieren

### Ziel

Doku-Code-Drift dauerhaft verhindern. Phase 1 hat die Doku manuell korrigiert. Phase 9 macht die Funktionsreferenz wartbarer.

### Relevante Dateien

```text
build.gradle
src/main/java/guru/interlis/transformer/expr/FunctionRegistry.java
src/main/java/guru/interlis/transformer/expr/FunctionDef.java
src/main/java/guru/interlis/transformer/feature/ExpressionFunctionReferenceTask.java   (neu)
src/test/java/guru/interlis/transformer/expr/ExpressionDocumentationTest.java
docs/expressions.md
```

### Variante A — Nur Testvalidierung

Behalte `docs/expressions.md` manuell, aber baue `ExpressionDocumentationTest` aus:

```java
@Test
void documentationMentionsAllRegisteredFunctions() { ... }

@Test
void documentationDoesNotMentionUnregisteredFunctionsAsBuiltins() { ... }
```

Für den zweiten Test kann man bekannte problematische Namen prüfen:

```java
List.of("round", "abs", "lookupOne", "lookupMany")
```

Wenn später `round` implementiert wird, Test anpassen.

### Variante B — Gradle-Task zur Generierung

Neue Klasse:

```text
src/main/java/guru/interlis/transformer/feature/ExpressionFunctionReferenceTask.java
```

Aufgabe:

```java
public final class ExpressionFunctionReferenceTask {
    public static void main(String[] args) throws IOException {
        // parse args: --markdown path, --json path optional
        FunctionRegistry registry = FunctionRegistry.defaultRegistry();
        // write markdown table sorted by name or group
    }
}
```

Output:

```text
build/reports/expression-functions.md
build/reports/expression-functions.json
```

Gradle-Task in `build.gradle`:

```gradle
tasks.register('generateExpressionReference', JavaExec) {
    group = 'documentation'
    description = 'Generate expression function reference as Markdown and JSON'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'guru.interlis.transformer.feature.ExpressionFunctionReferenceTask'
    args = [
        '--markdown', 'build/reports/expression-functions.md',
        '--json', 'build/reports/expression-functions.json'
    ]
}
```

Optional Doku-Hinweis in `docs/expressions.md`:

```text
The authoritative list of built-in functions is generated from FunctionRegistry by ./gradlew generateExpressionReference.
```

### Tests

Neue Tests:

```text
src/test/java/guru/interlis/transformer/feature/ExpressionFunctionReferenceTaskTest.java
```

Testfälle:

```java
generatesMarkdownContainingRegisteredFunctions()
generatesJsonContainingNameReturnTypeParametersAndDeterminism()
```

### Verifikation

```bash
./gradlew test --tests "guru.interlis.transformer.expr.ExpressionDocumentationTest"
./gradlew test --tests "guru.interlis.transformer.feature.ExpressionFunctionReferenceTaskTest"
./gradlew generateExpressionReference
./gradlew test
```

### Definition of Done

- Entweder Doku wird maschinell gegen `FunctionRegistry` geprüft oder Funktionsreferenz wird generiert.
- Neue Funktionen können künftig nicht mehr unbemerkt undokumentiert bleiben.
- Nicht registrierte Funktionen erscheinen nicht als Builtins.

---

## Phase 10 — Struktur-DSL langfristig gegenüber `lookup(...)` stärken

### Ziel

Diese Phase ist konzeptionell grösser und sollte erst nach Stabilisierung durchgeführt werden. Ziel ist, häufige `lookup(...)`-Fälle langfristig durch strukturelle `sources` + `joins` ausdrückbar zu machen.

### Warum später?

Aktuell gibt es nur 2 produktive `joins`, aber 10 `lookup(...)`-Nutzungen. Das zeigt: `lookup(...)` löst reale Fälle. Gleichzeitig ist es string-basiert und weniger modellbewusst. Eine vorschnelle Migration kann fachlich gefährlich sein.

### Relevante Klassen

```text
src/main/java/guru/interlis/transformer/mapping/compiler/JoinCompiler.java
src/main/java/guru/interlis/transformer/mapping/plan/JoinPlan.java
src/main/java/guru/interlis/transformer/mapping/plan/JoinType.java
src/main/java/guru/interlis/transformer/mapping/plan/JoinCardinality.java
src/main/java/guru/interlis/transformer/engine/RuleExecutionService.java
src/main/java/guru/interlis/transformer/state/SourceLookupIndex.java
src/test/java/guru/interlis/transformer/mapping/compiler/JoinCompilationTest.java
src/integrationTest/java/... join-related integration tests
profiles/**/*.yaml
```

### Kurzfristiger Zielzustand

Nicht sofort Multi-Join bauen. Stattdessen:

1. `lookup(...)`-Fälle in Profilen inventarisieren.
2. Pro Fall entscheiden, ob ein struktureller Join klarer wäre.
3. Einen einzigen risikoarmen Profilfall als Experiment migrieren.
4. IntegrationTest und ggf. RealDataTest laufen lassen.

### Mögliche spätere DSL-Erweiterung

```yaml
sources:
  - alias: bb
    input: dm01
    class: DM01AVCH24LV95D.Bodenbedeckung.BoFlaeche
  - alias: gn
    input: dm01
    class: DM01AVCH24LV95D.Bodenbedeckung.Gebaeudenummer
joins:
  - left: bb
    right: gn
    on: "eq(gn.Gebaeudenummer_von, bb)"
    type: left
    cardinality: manyToOne
    onMissing: warning
    onMultiple: error
assign:
  EGID: "gn.GWR_EGID"
```

Dafür wären nötig:

- `JobConfig.JoinSpec` um `cardinality`, `onMissing`, `onMultiple` erweitern.
- `JoinCompiler.compileJoins(...)` validiert Werte.
- `JoinPlan` nimmt Cardinality/Policies auf.
- `RuleExecutionService.processJoinedRule(...)` wertet Policies aus.
- Tests für Missing/Multiple-Verhalten.

### Verifikation

Bei Profiländerungen:

```bash
./gradlew test
./gradlew integrationTest
./gradlew realDataTest
```

Nutze zwingend:

```text
.skills/dm01-dmav-real-data-gate/SKILL.md
.skills/interlis-validation/SKILL.md
```

### Definition of Done

- Kein pauschaler Ersatz von `lookup(...)`.
- Mindestens ein gut verstandener Fall ist strukturell modelliert oder bewusst zurückgestellt.
- RealData-Gate wurde bei Profiländerung ausgeführt.

---

## Gesamtpriorisierung

Empfohlene Reihenfolge:

```text
P0 / sofort:
1. Phase 0 — Baseline erfassen
2. Phase 1 — Expression-Doku synchronisieren
3. Phase 2 — Default FunctionRegistry zentralisieren

P1 / Stabilisierung:
4. Phase 3 — ExpressionTypeChecker als einzige Typprüfquelle
5. Phase 4 — lookup(...) dokumentieren/absichern, optional lookupIn(...)
6. Phase 5 — enumMap-Semantik stabilisieren

P2 / Sprachvertrag abrunden:
7. Phase 6 — Nested Paths begrenzen
8. Phase 7 — MathFunctions bewusst klein halten oder ergänzen
9. Phase 8 — create/metadata/joins/FeatureStatus final klassifizieren
10. Phase 9 — Funktionsreferenz generieren/validieren

P3 / später:
11. Phase 10 — Struktur-DSL gegenüber lookup(...) stärken
```

---

## Nicht-Ziele für diese Umsetzungsrunde

Nicht in diesen Phasen einführen:

```text
- Freie Script-Sprachen wie Groovy, JavaScript, JEXL, MVEL
- User-defined functions in YAML
- Loops oder Collection-Comprehensions in Expressions
- Vollständige Arithmetic Operators + - * /
- Komplexe Geometry-GIS-Funktionen in Expressions
- Multi-Join-Engine ohne eigenes Design
- Breaking Changes an produktiven Profilen ohne RealData-Gate
```

Die Expression-Language soll klein, typisierbar und sandboxed bleiben.

---

## Abschliessendes Fazit für den Agenten

Die aktuelle Mapping-DSL ist fachlich stark und trägt bereits produktive DM01↔DMAV-Profile. Die Expression-Language ist grundsätzlich gut gewählt: AST-basiert, sandboxed, typisierbar und begrenzt. Der nächste Schritt ist nicht maximale Ausdrucksmacht, sondern ein verlässlicher Sprachvertrag.

Die wichtigste Arbeit besteht darin, Doku und Code zu synchronisieren, die Typprüfung zu konsolidieren und riskante Convenience-Funktionen wie `lookup(...)` und `enumMap(...)` ehrlich zu dokumentieren und testmässig abzusichern.

Arbeite klein, testgetrieben und phasenweise. Nach jeder Phase:

```bash
git status --short
git diff --stat
./gradlew test
```

Bei Profil-, INTERLIS- oder RealData-relevanten Änderungen zusätzlich:

```bash
./gradlew integrationTest
./gradlew realDataTest
```

Commit erst nach bestandener Verifikation gemäss `.skills/done-and-commit/SKILL.md`.
