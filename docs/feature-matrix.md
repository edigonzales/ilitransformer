# Feature Matrix

| Feature | Phase | Status | Description | Tests |
|---|---|---|---|---|
| CLI transform | 0 | ✅ SUPPORTED | Transform command with --mapping and --modeldir | CliMainTest |
| CLI validate-mapping | 1 | ✅ SUPPORTED | Validate mapping YAML without executing | CliMainTest, MappingCompilerTest |
| CLI inspect-model | 2 | ✅ SUPPORTED | Inspect INTERLIS model structure | InspectModelCliTest |
| IliModelService + TypeSystemFacade | 2 | ✅ SUPPORTED | Compile and query INTERLIS models | IliModelServiceTest |
| IliPath | 2 | ✅ SUPPORTED | Parse and resolve INTERLIS paths | IliPathTest |
| ModelInventory + InventorySerializer | 2 | ✅ SUPPORTED | Enumerate model classes and attributes | InventorySerializerTest |
| MappingCompiler.compileTyped() | 3 | ✅ SUPPORTED | Compile YAML config into typed TransformPlan | TypedCompilerTest |
| TransformPlan (typed plan) | 3 | ✅ SUPPORTED | Typed execution plan with RulePlan, AssignmentPlan, RefPlan, BagPlan | TypedCompilerTest |
| ExpressionEngine | 4 | ✅ SUPPORTED | Parse and evaluate mapping expressions | ExpressionEngineTest, ExpressionParserTest |
| FunctionRegistry (Basic/String/Date/Enum/Ref/Math) | 4 | ✅ SUPPORTED | Builtin functions for expressions | FunctionRegistryTest, BuiltinFunctionsTest |
| Value type system | 4 | ✅ SUPPORTED | Sealed interface: TextValue, NumberValue, BooleanValue, DateValue, EnumValue, CoordValue, GeometryObjectValue, ReferenceValue, NullValue | ValueTest, ExpressionEngineTest |
| Two-pass execution engine | 5 | ✅ SUPPORTED | Pass 0 (index), Pass 1 (build), Pass 2 (refs), Write (stable sort) | TransformationEngineIntegrationTest |
| 1:1 Scalar mapping | 5 | ✅ SUPPORTED | Copy attributes from source to target | ScalarMappingIntegrationTest |
| OID strategies (preserve/integer/uuid/deterministic) | 6 | ✅ SUPPORTED | preserve, integer, uuid, deterministicUuid (UUIDv3) | OidStrategyTest, OidStrategyGoldenTest |
| OID strategy external | 6 | 📌 STUB | External OID source — pass-through stub |  |
| Basket strategies | 6 | ✅ SUPPORTED | preserve, generateUuid, preserveOrGenerateUuid, byTopic | BasketStrategyTest |
| Basket strategy expression | 6 | 📌 STUB | Expression-based basket routing — stub |  |
| Reference resolution + roles | 7 | ✅ SUPPORTED | DeferredRefs, type checking, cardinality, role-aware resolution | ReferenceResolutionIntegrationTest |
| ReferenceIndex + ReferenceResolutionService | 19 | ✅ SUPPORTED | Separate ReferenceIndex with SourceObjectKey/TargetReference; ReferenceResolutionService with per-owner cardinality, ambiguity detection, association-aware diagnostics | ReferenceIndexTest, ReferenceResolutionServiceTest |
| Same OID different context resolution | 19 | ✅ SUPPORTED | Same OID in different baskets/inputs/classes correctly disambiguated; no global OID-only fallback by default | ReferenceIndexTest, ReferenceResolutionServiceTest, ReferenceResolutionIntegrationTest |
| Association-aware role resolution | 19 | ✅ SUPPORTED | requireRole() with AssociationDef; association name in diagnostics; XTF association serialization tested with ilivalidator | AssociationXtfIntegrationTest |
| failPolicy (strict/lenient/reportOnly) | 7 | ✅ SUPPORTED | Configurable error handling at plan level | TransformationEngineIntegrationTest |
| GeometryAdapter (Coord/Polyline/Surface/Area) | 13/24 | ✅ SUPPORTED | IoxGeometryAdapter with GeometryValueCopier, ItfGeometryWriter for ILI1 helper tables | IoxGeometryAdapterTest, GeometryIntegrationTest, GeometryDeepCopyTest |
| GeometryValueCopier | 24 | ✅ SUPPORTED | Deep copy IomObject geometries to prevent source-target aliasing | GeometryDeepCopyTest |
| GeometryCompatibilityService | 24 | ✅ SUPPORTED | Compile-time geometry compatibility checks: dimension, SURFACE/AREA, type matching | GeometryCompatibilityServiceTest |
| Geometry roundtrip (Read→Write→Read) | 24 | ✅ SUPPORTED | COORD, POLYLINE (straight+ARC), SURFACE, AREA with multiple boundaries; XTF and ITF validated with ilivalidator | CoordRoundtripTest, PolylineRoundtripTest, XtfReadOwnOutputTest |
| Real dataset geometry smoke test | 24 | ✅ SUPPORTED | Read full DM01 ITF and DMAV XTF, inventory geometry types | RealDatasetGeometrySmokeTest |
| FailPolicy STRICT – kein Commit bei Fehler | 25 | ✅ SUPPORTED | Compiler-Fehler, Runtime-Fehler oder Validator-Fehler verhindern Commit; Exit-Code != 0 | StrictRollbackTest |
| FailPolicy LENIENT – herabstufbare Fehler | 25 | ✅ SUPPORTED | Explizit herabstufbare Fehler werden Warnings; strukturelle Fehler verhindern Commit | LenientPolicyTest |
| FailPolicy REPORT_ONLY | 25 | ✅ SUPPORTED | Modelle und Mapping kompilieren, Ausfuehrbarkeit analysieren, keine endgueltigen Outputs | ReportOnlyTest |
| TransactionalOutputManager | 25 | ✅ SUPPORTED | Temp-Dateien, atomarer Move bei Erfolg, Rollback bei Fehler, keep-temp fuer Debugging | TransactionalOutputManagerTest |
| TransformationReportWriter (JSON+MD) | 25 | ✅ SUPPORTED | Objektzahlen, Filterzahlen, Warnings/Errors, Referenzbericht, Validatorstatus, Laufzeit, Modelle | ReportOptionCliTest |
| TransferValidationService + InProcessIlivalidatorService | 25 | ✅ SUPPORTED | Gekapselter ilivalidator-Service mit strukturiertem ValidationResult (errorCount, warningCount) | ValidateOptionCliTest, ValidatorFailureExitCodeTest |
| validate-transfer CLI command | 25 | ✅ SUPPORTED | Neuer Subcommand: validate-transfer --file/--modeldir/--model/--log | ValidatorFailureExitCodeTest |
| CLI --fail-policy / --keep-temp / --validate / --report | 25 | ✅ SUPPORTED | Alle CLI-Optionen haben Wirkung; modeldirs aus CLI und YAML mergen korrekt | ValidateOptionCliTest, ReportOptionCliTest, JobModeldirMergeTest, RelativeMappingPathCliTest |
| validate-mapping mit compileTyped und modeldirs | 25 | ✅ SUPPORTED | validate-mapping fuehrt compileTyped mit CLI-modeldirs aus | ValidateMappingTypedCliTest |
| ITF/XTF I/O via iox-ili | 5 | ✅ SUPPORTED | Read and write INTERLIS transfer files | SurfaceAreaItfIntegrationTest, GeometryIntegrationTest |
| XLSX correlation import | 8 | ✅ SUPPORTED | Parse DM01/DMAV correlation workbook | CorrelationWorkbookImporterTest |
| DM01→DMAV LFP3 pilot | 10 | ✅ SUPPORTED | LFP3 transformation DM01 to DMAV with golden test | Dm01ToDmavLfp3IntegrationTest |
| DMAV→DM01 LFP3 pilot | 11 | ✅ SUPPORTED | LFP3 transformation DMAV to DM01 with golden test | DmavToDm01Lfp3IntegrationTest |
| BAG OF STRUCTURE (Textpositionen) | 12 | ✅ SUPPORTED | Pos tables to BAG OF Textposition in both directions | Dm01ToDmavLfp3IntegrationTest |
| enumMap() | 15 | 📌 STUB | Enum mapping pass-through with diagnostic warning |  |
| Joins / Splits / Merge | 22 | 🔬 EXPERIMENTAL | Multi-source equi-joins (max 1 join per rule), create directives, rule dependency ordering | JoinCompilationTest, CreateCompilationTest, CreateAdditionalObjectIntegrationTest |
| RuleDispatchIndex | 26 | ✅ SUPPORTED | Pre-computed O(1) rule dispatch per (inputId, sourceClass); eliminates SourceRecord x Rule full scan | RuleDispatchIndexTest |
| ExecutionMetrics und Performance-Report | 26 | ✅ SUPPORTED | Laufzeitmessung, Join-/BAG-Lookup-Zähler, Targets-by-Class; integriert in JSON/Markdown-Report | ExecutionMetricsTest |
| Service-Dekomposition der TransformationEngine | 26 | ✅ SUPPORTED | SourceIndexingService, RuleExecutionService, TargetObjectFactory, AssignmentExecutionService, OutputWritingService | TransformationEngineIntegrationTest |
| Deterministic Output Order | 26 | ✅ SUPPORTED | Output sortiert nach basketId, class, oid; reproduzierbar über mehrere Runs | DeterministicOutputOrderTest |
| Real Dataset Smoke Tests (DM01+DMAV) | 26 | ✅ SUPPORTED | Vollständige Datensätze mit Modellen einlesen; Objektzahlen pro Topic/Klasse berichten | FullDm01ReadSmokeTest, FullDmavReadSmokeTest |
| RealDatasetCatalog | 26 | ✅ SUPPORTED | Scan/Classify von Transfer-Dateien ohne harte Dateinamen; requireSingleItf/Xtf | RealDatasetCatalogTest |
| TransferInventory & Inventory Service | 27 | ✅ SUPPORTED | Transfer-Inhaltsstatistiken (Objektzahlen pro Klasse, OID-Typen, Geometrietypen, Referenzen, LFP3-Erkennung) | FullDatasetInventoryTest |
| ConnectedSubgraphExtractor | 27 | ✅ SUPPORTED | Extrahiert fachlich zusammenhängende Teiltransfers inklusive Referenzen; BFS-Expansion, Bidirectional, maxDepth/maxObjects | ConnectedSubgraphExtractorTest |
| LFP3 Fixture Extraction (DMAV) | 27 | ✅ SUPPORTED | Aus realem DMAV-Datensatz LFP3-Fixtures extrahieren und mit ilivalidator validieren | ExtractedDmavFixtureValidationTest |
| DM01 ITF Fixture Extraction | 27 | 🟡 PARTIAL | DM01-Daten können gelesen und inventarisiert werden; ITF-Geometrie-Hilfstabellen verhindern Write-Back | ExtractedDm01FixtureValidationTest |
| Persistent StateStore | - | ❌ UNSUPPORTED | Disk-backed or database-backed state store |  |
| AREA topology repair | - | ❌ UNSUPPORTED | Topological repair of AREA geometries |  |
| LINEATTR support | - | ❌ UNSUPPORTED | Line attribute geometry processing |  |
| ILIMAP parser + lexer | P1/P2 | ✅ SUPPORTED | Lexer, expression reader and recursive descent parser for .ilimap v2 DSL | IlimapLexerTest, IlimapExpressionReaderTest, IlimapParserMinimalTest, IlimapParserFullRuleTest |
| ILIMAP semantic validation | P3 | ✅ SUPPORTED | Symbol table, scope analysis, identifier rules, seven hardening points | IlimapSemanticValidatorTest, IlimapIdentifierRulesTest, IlimapSymbolTableTest |
| ILIMAP loader to JobConfig | P4 | ✅ SUPPORTED | Map parsed .ilimap AST to JobConfig via IlimapToJobConfigMapper | IlimapToJobConfigMapperTest, IlimapLoaderTest, IlimapYamlEquivalenceTest |
| ILIMAP formatter | P6 | ✅ SUPPORTED | Stable formatter with parse-format-parse idempotency | IlimapFormatterTest, IlimapFormatterRoundtripTest |
| YAML to ILIMAP converter | P7 | ✅ SUPPORTED | Convert existing YAML mappings to .ilimap format via convert-mapping CLI | YamlToIlimapConverterTest, ConvertMappingCliTest |
| ILIMAP CLI transform | P8 | ✅ SUPPORTED | transform --mapping accepts .ilimap files for end-to-end transformation | IlimapEndToEndTransformationTest |
| ILIMAP CLI validate-mapping | P8 | ✅ SUPPORTED | validate-mapping --mapping accepts .ilimap files with file:line:column diagnostics | IlimapValidateMappingCliTest, IlimapDiagnosticsCliTest |
| ILIMAP ref short form | - | ❌ UNSUPPORTED | Ref short form (-> / using) is reserved for a later version | IlimapRefSemanticTest |
| ILIMAP includes/macros | - | ❌ UNSUPPORTED | Macro and include system — not planned for v2 |  |
| ILIMAP qualified INTERLIS names as tokens | - | ❌ UNSUPPORTED | INTERLIS model/class paths are strings, not qualified tokens |  |
