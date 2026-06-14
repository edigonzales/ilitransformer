> Historisches Arbeitsdokument. Nicht führend für den aktuellen Repo-Zustand.

# Open Questions

## Phase 1 (DSL-/Config-Modell stabilisieren)

### Resolved
- **YAML `class:` field**: Solved via `@JsonProperty("class")` + `@JsonAlias("clazz")` on `SourceSpec.clazz`.
- **Multi-input as list**: Solved via `inputs` List field + backward-compat `input` string.
- **Nested `target` vs flat `targetClass`**: Solved via helper `getEffectiveTargetClass()` + `normalize()`.

### Open (moved to Phase 2+)
- Soll der `MappingCompiler` in Phase 3 die flachen Felder (`targetClass`, `output`, `input`) als deprecated markieren und Warnungen ausgeben?
- Wie soll der Typed Plan (`TransformPlan`) genau strukturiert sein? (Phase 3)
- Soll `enumMap()` in Expressions bereits in Phase 4 implementiert werden oder erst in Phase 10?
- Welche Jackson-Konfiguration ist nötig für unbekannte Felder: ignorieren oder Fehler melden?

## Phase 2 (INTERLIS Model Service und Inventory)

### Resolved
- **OID-Typen-Erkennung**: Über `Topic.getOid()`/`Topic.getBasketOid()` → Domain → Type-Klassenname.
- **Mandatory-Detektion**: Über `AttributeDef.getCardinality().getMinimum() > 0`.
- **Rollen-Extraktion**: Über `Table.getTargetForRoles()` (Roles aus Associations).
- **INTERLIS-interne Modelle filtern**: `INTERLIS`, `GeometryCHLV95_V2`, `CoordSystem` werden ignoriert.
- **Enum-Typ-Darstellung**: `getDomain()` gibt bei Domain-Referenzen den Domain-Namen zurück (`DOMAIN Model.Topic.DomainName`). Enum-Werte-Auflösung via `EnumerationType.getEnumeration().getElement(i).getName()`.

### Open
- Sollen Structure-Definitionen als eigene `ClassInventory`-Einträge erscheinen (aktuell nicht)?
- Soll der `inspect-model` Output auch View-Klassen auflisten?
- Soll die Enum-Werte-Liste direkt im Typ-String erscheinen oder als separates Feld?
- Wie soll das `modeldir`-Handling bei `--modeldir`-Option mit Semikolon-Trennung mit ili2c-Settings interagieren?

## Phase 3 (Typed Mapping Compiler)

### Resolved
- **TypedPlan-Struktur**: `TransformPlan` → `RulePlan` → `SourcePlan`, `AssignmentPlan`, `RefPlan`. Runtime akzeptiert nur noch `TransformPlan`.
- **Typkompatibilität**: Einfache Heuristik (Literal-Klassifikation, SOURCE_PATH-Auflösung, STRUCTURE→STRUCTURE, ENUM↔TEXT). Funktionsaufrufe als UNKNOWN (→ Phase 4).
- **Mandatory-Coverage**: `AttributeDef.getCardinality().getMinimum() > 0` → Warning wenn nicht im assign.
- **Compiler-Report**: JSON + Markdown via `CompilerReport` Klasse.
- **DiagnosticCode-Präfix**: `ILITRF-MAP-*` für Compiler-Diagnostics.
- **Backward-Kompatibilität**: Engine behält `run(JobConfig)`-Methode, neue `runTyped(TransformPlan)` parallel.

### Open
- ~~Soll die Typ-Inferenz bereits in Phase 4 Funktionsrückgabetypen kennen? (z.B. `truncate()` → TEXT, `toXmlDateTime()` → XML_DATE_TIME)~~ **Erledigt in Phase 4**: `MappingCompiler` nutzt jetzt `FunctionRegistry` zur Typ-Inferenz für `FUNCTION_CALL`.
- Soll `isAbstract()`-Prüfung Error oder Warning sein? (aktuell Error)
- Sollen OID und Basket-Strategien bereits im `TransformPlan` repräsentiert werden?
- Wie granular sollen Enum-Mapping-Coverage-Checks sein? (aktuell keine)

## Phase 4 (Expression Engine und Function Registry)

### Resolved
- **Expression Parser**: Eigener rekursiver Abstiegs-Parser (kein JEXL). Unterstützt Literale (String, Number, Boolean, null, Enum), Pfadreferenzen (`${alias.attr}`), Funktionsaufrufe, `if()`-Konditionale, `!= null`/`== null`-Vergleiche.
- **Value Layer**: `sealed interface Value` mit `TextValue`, `NumberValue`, `BooleanValue`, `DateValue`, `XmlDateTimeValue`, `EnumValue`, `CoordValue`, `ReferenceValue`, `NullValue`.
- **Function Registry**: `FunctionRegistry` mit Builtins: `coalesce`, `defined`, `notDefined`, `isNull`, `default`, `null` (Basic); `concat`, `substring`, `trim`, `upper`, `lower`, `replace`, `truncate` (String); `toXmlDateTime`, `now` (Date, `now` ist @NonDeterministic); `enumMap` (Stub, Phase 10), `enumDefault`, `enumName`; `refOid`, `refEquals` (Reference).
- **Compiler-Integration**: `MappingCompiler` inferiert Funktionsrückgabetypen über `FunctionRegistry` (z.B. `truncate()` → TEXT, `toXmlDateTime()` → XML_DATE_TIME).
- **TransformationEngine**: Nutzt neue `ExpressionEngine` mit `EvalContext` und typisierten `Value`-Rückgaben.
- **`enumMap()`**: Als Stub implementiert (Pass-through mit Diagnostic-Warnung). Vollständige Enum-Mapping-Logik in Phase 10.

### Open
- Soll der Parser auch arithmetische Ausdrücke (`+`, `-`, `*`, `/`) unterstützen? Aktuell nicht.
- Sollen Vergleichsoperatoren (`>`, `<`, `>=`, `<=`) unterstützt werden? Aktuell nur `!= null`/`== null`.
- Sollen `lookupOne`/`lookupMany` (StateStore-Lookups) bereits in Phase 4 implementiert werden aber Zugriff auf StateStore erst in Phase 5?

## Phase 5 (Runtime MVP für 1:1 Scalar Mapping)

### Resolved
- **`where`-Filter aktiviert**: `SourcePlan.where` wird jetzt in `pass2BuildTargets()` via `ExpressionEngine.evaluate()` ausgewertet. Null/leer/false-Werte filtern SourceRecords aus.
- **`getScopedName(Table)`-Bugfix**: `Table.getName()` liefert den einfachen Namen (`"SourceClass"`), aber `IomObject.getobjecttag()` den vollqualifizierten Namen (`"P5Model.P5Topic.SourceClass"`). Die Engine nutzt jetzt `getScopedName()` zum Matchen von SourceRecords und zum Erstellen von Zielobjekten.
- **`TransformResult`**: Neue Record-Klasse mit Summary-Statistiken (sourceRecordsRead, sourceRecordsFiltered, targetsCreated, targetsWritten, errors, warnings). Wird von `runTyped()` und `run()` zurückgegeben.
- **Integration-Test**: `Phase5IntegrationTest` mit echten ILI-Modellen (`p5-test.ili`), Mock-Readern für Source-Objekte und realen `XtfWriter`s für Output.
- **Output-Verifikation**: Der XTF-Output wird auf Existenz und Inhalt geprüft (String-basierte Verifikation). XTF-Reader mit Modellkontext kann die eigenen Output-Dateien nicht zurücklesen (IoxSyntaxException "Unexpected XML event transfer found").

### Open
- Warum kann der `XtfReader` mit `TransferDescription`-Modellkontext die vom `XtfWriter` geschriebenen Dateien nicht lesen? "Unexpected XML event transfer found" deutet auf eine Namespace-Erwartungshaltung des Xtf23Readers hin.
- Soll die Engine die Output-Objekte zum Verifizieren zurückgeben, statt auf XTF-Readback angewiesen zu sein?
- Soll `Iom_jObject.setattrvalue()` typisierte Werte erhalten (nicht nur Strings)? Derzeit nutzt die Engine `value.toNative().toString()`, was für skalare Typen funktioniert.

## Phase 6 (OID-, Basket- und Writer-Strategien)

### Resolved
- **OID-Strategien**: `preserve`, `integer`, `uuid`, `deterministicUuid` (UUIDv3 via `java.util.UUID.nameUUIDFromBytes()`), `external` (Stub). Implementiert in `InMemoryStateStore.nextOid(OidStrategy, ...)`.
- **Basket-Strategien**: `preserve`, `generateUuid`, `preserveOrGenerateUuid`, `byTopic`, `expression` (Stub). Implementiert via `BasketRouter.determineTargetBasket()`.
- **`TransformPlan`**: Enthält jetzt `oidStrategy`, `oidNamespace`, `basketStrategy` Felder.
- **`RulePlan`**: Enthält jetzt `identitySourceKeys` für `deterministicUuid`.
- **`TypeSystemFacade.getOidType()`**: Neue Methode zur OID-Typ-Abfrage (UUIDOID vs STANDARDOID).
- **OID-Typ-Validierung**: Compiler emittiert `ILITRF-MAP-OID-TYPE-MISMATCH` Error wenn `integer`-Strategie auf UUIDOID-Zielmodell.
- **Stable Sorting**: `writeOutputs()` sortiert Target-Objekte nach `getobjecttag()` → `getobjectoid()`.
- **`TransformResult`**: Enthält jetzt `oidStrategy`/`basketStrategy` im Summary-String.

### Open
- Soll `deterministicUuid` UUIDv5 (SHA-1) statt UUIDv3 (MD5) verwenden? Aktuell UUIDv3, was für den Use Case ausreicht.
- Soll `external`-OID-Strategie als Expression evaluiert werden? Aktuell Stub (gibt null zurück, Engine fällt auf Integer zurück).
- Soll die Basket-Strategie `byTopic` auch Basket-OID-Typen des Zielmodells berücksichtigen?
- Wie soll mit OID-Kollisionen bei `preserve` umgegangen werden?
- Status der Phasen 0-3 OID/Basket-Strategie-Fragen als gelöst markieren?

## Phase 7 (Referenzen, Rollen und Associations)

### Resolved
- **`RoleResolver`**: Erstellt als Wrapper um `TypeSystemFacade`. Löst `expectedTargetClass` für ein `RefPlan` gegen das Target-TypeSystem auf (findet den anderen Assoziationspartner).
- **`TypeSystemFacade` erweitert**: Neue Methoden `resolveRole()`, `getRoleTargetClass()`, `getRoleCardinalityMin()`/`Max()`, `getRoleAssociation()`.
- **`getRoleTargetClass()`**: Navigiert über `RoleDef.getContainer()` → `AssociationDef.getRoles()` → andere Rolle → `getDestination()` → `getScopedName()`.
- **Type-Check in Runtime**: `resolveDeferredRefs()` prüft jetzt `deferredRef.expectedTargetClass()` vs `resolved.targetClass()` und emittiert `ILITRF-RUN-REF-TYPE-MISMATCH`.
- **`failPolicy`-Integration**: `resolveDeferredRefs()` und `checkRequiredRefs()` werten `plan.failPolicy()` aus: `strict` → ERROR, `lenient` → WARNING.
- **Cardinality/Mandatory-Check**: `checkRequiredRefs()` iteriert alle `RefPlan` mit `required=true` und prüft ob ein aufgelöster DeferredRef existiert → `ILITRF-RUN-REF-MISSING-MANDATORY`.
- **Cross-Class IdMapping**: `putIdMapping()` speichert zusätzlich einen globalen Eintrag mit `sourceClass=null, sourceFileId=null, sourceBasketId=null`, damit referenzierte Objekte über Klassengrenzen hinweg gefunden werden.
- **`sourceRef`-Alias-Auflösung**: In `pass2BuildTargets()` wird der Alias-Präfix (z.B. `p.RefToB` → `RefToB`) vor dem Aufruf von `readSourceReferenceOid()` entfernt.
- **Neue Diagnostic-Codes**: `ILITRF-RUN-REF-TYPE-MISMATCH`, `ILITRF-RUN-REF-MISSING-MANDATORY`, `ILITRF-RUN-REF-CARDINALITY`.
- **Testmodell**: `src/test/data/models/with-references.ili` mit `ClassA` (Referenz-Attribut `RefToB`), `ClassB` und Assoziation `AtoB`.

### Open
- Soll `checkRequiredRefs()` bei `required=true` und fehlender Referenz das Zielobjekt trotzdem schreiben oder verwerfen?
- Soll die Auflösung von `expectedTargetClass` auch für Modelle ohne Assoziationen funktionieren (z.B. INTERLIS-1-REFERENCE-Attribute)?
- Sollen Associations als eigene `IomObject`-Knoten im Output geschrieben werden (nicht nur als REF-Attribute auf dem Owner-Objekt)?
- Wie granular soll der Cardinality-Check sein: reicht Prüfung auf `min > 0` (=required) oder soll die tatsächliche Anzahl der aufgelösten Refs pro Rolle gezählt werden?
- Soll `checkRequiredRefs()` auch bei `DeferredRef`s prüfen, die aufgrund `sourceRef==null` nie erstellt wurden (aktuell: ja)?

## Phase 8 (XLSX-Korrelation importieren)

### Resolved
- **Apache POI als Build-Time-Dependency**: `compileOnly` (Compile) + `xlsx`-Konfiguration (nur für Gradle-Task, nicht im Runtime-Distribution).
- **Gradle-Task `importCorrelation`**: `JavaExec`-Task mit separatem Classpath via `configurations.xlsx`. Kein CLI-Befehl in `CliMain`.
- **`CorrelationWorkbookImporter`**: Parst Sheet `Transformation` über feste Spalten-Indizes (0-basiert). Erkennt Richtungen anhand der Code-Spalten U (DM01→DMAV) und Z (DMAV→DM01). Überspringt leere Zeilen.
- **`CorrelationHint`-Record**: Enthält `rowNumber`, `sheetName`, `cellPosition`, `Direction`, Quell-/Ziel-Informationen, `transformCode`, `confidence`, `warnings`.
- **`Direction`-Enum**: `DM01_TO_DMAV`, `DMAV_TO_DM01`.
- **`CorrelationHintExporter`**: Schreibt `correlation-hints.json` (Jackson) und `correlation-import-report.md` (Statistiken pro Richtung/Code).
- **Transformationscodes**: `K` (confidence 0.7), `V` (0.5), `I` (0.3). Unbekannte Codes → `ILITRF-DMAV-CORRELATION-PARSE` Warning.
- **Generierte Artefakte**: `build/generated/dm01-dmav/correlation-hints.json` (137 kB, 250 Hints) und `build/reports/dm01-dmav/correlation-import-report.md`.
- **Snapshot-Test**: Validiert Anzahl Hints >100, 95%+ valide Codes, beide Richtungen vorhanden.

### Open
- Sollen die 3 Warnings (Zeile 5 mit Code "T" = vermutlich Legende/Kopfzeile, Zeile 708 mit mehrzeiligem Code "1) K 2) V") durch spezifischeres Parsing eliminiert werden, oder sind sie als dokumentierte Datenqualitäts-Hinweise akzeptabel?
- Soll das versteckte Sheet `Korrelation` (1197 Zeilen) als zusätzliche Quelle geparst werden?
- Sollen zusammengeführte Zellen (merged cells) aufgelöst werden?
- Soll der Import bei künftigen Änderungen der XLSX-Spaltenstruktur über Header-Namen statt Indizes robuster werden?

## Phase 10 (DM01→DMAV LFP3 Minimalpilot)

### Resolved
- **DM01→DMAV LFP3**: Erstes handgeprüftes Mapping `dm01-to-dmav-lfp3.yaml` funktioniert.
- **Golden Test**: `Dm01ToDmavLfp3IntegrationTest` mit realem Test-ITF (`so_2549.itf`).
- **ilivalidator**: Bestanden auf erzeugtem DMAV-XTF.
- **Deterministische UUIDs**: Reproduzierbare OIDs via Namespace + Source-Key.

### Open
- Bleiben als dokumentierte Lossiness/Einschränkungen im DM01/DMAV-Doc (siehe `docs/dm01-dmav/lossiness.md`).

## Phase 15 (Stabilisierung, CLI-UX und Dokumentation)

### Resolved
- **CLI-Kommandos**: Alle 4 Commands sind picocli-Subcommands: `transform`, `validate-mapping`, `inspect-model`, `import-correlation`.
- **`transform --validate`**: Flag für optionalen ilivalidator-Lauf nach Transformation.
- **`transform --report`**: Flag für Report-Ausgabepfad.
- **Gradle-Tasks**: `importDmavCorrelation`, `generateModelInventory`, `validateGoldenTransfers`, `integrationTest`.
- **Dokumentation**: ~18 Dokumentationsdateien erstellt/aktualisiert.

### Offene Fragen (als bekannte Limitationen akzeptiert)

- XTF-Reader kann eigene Output-Dateien nicht zurücklesen (IoxSyntaxException). Engine gibt Output-Objekte nicht direkt zur Verifizierung zurück.
- `enumMap()` ist weiterhin Stub. Vollständige Enum-Mapping-Logik bleibt für spätere DM01↔DMAV-Slices.
- `external` OID-Strategie und `expression` Basket-Strategie bleiben Stubs.
- OID-Kollisionen bei `preserve`-Strategie sind nicht behandelt.
- `lookupOne`/`lookupMany` (StateStore-Lookups aus Expressions) nicht implementiert.
