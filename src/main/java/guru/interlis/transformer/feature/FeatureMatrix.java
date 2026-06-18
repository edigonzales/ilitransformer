package guru.interlis.transformer.feature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class FeatureMatrix {

    private static final List<FeatureEntry> ENTRIES = buildEntries();

    public FeatureMatrix() {}

    public List<FeatureEntry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public void writeMarkdown(Path output) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Feature Matrix\n\n");
        sb.append("| Feature | Phase | Status | Description | Tests |\n");
        sb.append("|---|---|---|---|---|\n");

        for (var e : ENTRIES) {
            sb.append("| ")
                    .append(e.feature())
                    .append(" | ")
                    .append(e.phase())
                    .append(" | ")
                    .append(statusEmoji(e.status()))
                    .append(" ")
                    .append(e.status())
                    .append(" | ")
                    .append(e.description())
                    .append(" | ")
                    .append(String.join(", ", e.testReferences()))
                    .append(" |\n");
        }
        Files.writeString(output, sb.toString(), StandardCharsets.UTF_8);
    }

    public void writeJson(Path output) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(output.toFile(), ENTRIES);
    }

    private static String statusEmoji(FeatureStatus status) {
        return switch (status) {
            case SUPPORTED -> "✅";
            case EXPERIMENTAL -> "🔬";
            case PARTIAL -> "🟡";
            case CONFIG_ONLY -> "⚙️";
            case STUB -> "📌";
            case UNSUPPORTED -> "❌";
        };
    }

    private static List<FeatureEntry> buildEntries() {
        List<FeatureEntry> fromYaml = loadFromYaml();
        if (fromYaml != null) {
            return fromYaml;
        }
        return buildHardcodedEntries();
    }

    private static List<FeatureEntry> loadFromYaml() {
        try (InputStream in = FeatureMatrix.class.getResourceAsStream("/feature-matrix.yaml")) {
            if (in == null) {
                return null;
            }
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            FeatureMatrixYaml yaml = mapper.readValue(in, FeatureMatrixYaml.class);
            List<FeatureEntry> entries = new ArrayList<>();
            for (FeatureEntryYaml e : yaml.entries) {
                entries.add(e.toFeatureEntry());
            }
            validate(entries);
            return Collections.unmodifiableList(entries);
        } catch (IOException e) {
            return null;
        }
    }

    private static List<FeatureEntry> buildHardcodedEntries() {
        var entries = new ArrayList<FeatureEntry>();

        entries.add(FeatureEntry.of(
                "CLI transform",
                "0",
                FeatureStatus.SUPPORTED,
                "Transform command with --mapping and --modeldir",
                "CliMainTest"));

        entries.add(FeatureEntry.of(
                "CLI validate-mapping",
                "1",
                FeatureStatus.SUPPORTED,
                "Validate mapping YAML without executing",
                "CliMainTest",
                "MappingCompilerTest"));

        entries.add(FeatureEntry.of(
                "CLI inspect-model",
                "2",
                FeatureStatus.SUPPORTED,
                "Inspect INTERLIS model structure",
                "InspectModelCliTest"));

        entries.add(FeatureEntry.of(
                "IliModelService + TypeSystemFacade",
                "2",
                FeatureStatus.SUPPORTED,
                "Compile and query INTERLIS models",
                "IliModelServiceTest"));

        entries.add(FeatureEntry.of(
                "IliPath", "2", FeatureStatus.SUPPORTED, "Parse and resolve INTERLIS paths", "IliPathTest"));

        entries.add(FeatureEntry.of(
                "ModelInventory + InventorySerializer",
                "2",
                FeatureStatus.SUPPORTED,
                "Enumerate model classes and attributes",
                "InventorySerializerTest"));

        entries.add(FeatureEntry.of(
                "MappingCompiler.compileTyped()",
                "3",
                FeatureStatus.SUPPORTED,
                "Compile YAML config into typed TransformPlan",
                "TypedCompilerTest"));

        entries.add(FeatureEntry.of(
                "TransformPlan (typed plan)",
                "3",
                FeatureStatus.SUPPORTED,
                "Typed execution plan with RulePlan, AssignmentPlan, RefPlan, BagPlan",
                "TypedCompilerTest"));

        entries.add(FeatureEntry.of(
                "ExpressionEngine",
                "4",
                FeatureStatus.SUPPORTED,
                "Parse and evaluate mapping expressions",
                "ExpressionEngineTest",
                "ExpressionParserTest"));

        entries.add(FeatureEntry.of(
                "FunctionRegistry (Basic/String/Date/Enum/Ref/Math)",
                "4",
                FeatureStatus.SUPPORTED,
                "Builtin functions for expressions",
                "FunctionRegistryTest",
                "BuiltinFunctionsTest"));

        entries.add(FeatureEntry.of(
                "Value type system",
                "4",
                FeatureStatus.SUPPORTED,
                "Sealed interface: TextValue, NumberValue, BooleanValue, DateValue, EnumValue, CoordValue, GeometryObjectValue, ReferenceValue, NullValue",
                "ValueTest",
                "ExpressionEngineTest"));

        entries.add(FeatureEntry.of(
                "Two-pass execution engine",
                "5",
                FeatureStatus.SUPPORTED,
                "Pass 0 (index), Pass 1 (build), Pass 2 (refs), Write (stable sort)",
                "TransformationEngineIntegrationTest"));

        entries.add(FeatureEntry.of(
                "1:1 Scalar mapping",
                "5",
                FeatureStatus.SUPPORTED,
                "Copy attributes from source to target",
                "ScalarMappingIntegrationTest"));

        entries.add(FeatureEntry.of(
                "OID strategies (preserve/integer/uuid/deterministic)",
                "6",
                FeatureStatus.SUPPORTED,
                "preserve, integer, uuid, deterministicUuid (UUIDv3)",
                "OidStrategyTest",
                "OidStrategyGoldenTest"));

        entries.add(FeatureEntry.of(
                "OID strategy external", "6", FeatureStatus.STUB, "External OID source — pass-through stub", ""));

        entries.add(FeatureEntry.of(
                "Basket strategies",
                "6",
                FeatureStatus.SUPPORTED,
                "preserve, generateUuid, preserveOrGenerateUuid, byTopic",
                "BasketStrategyTest"));

        entries.add(FeatureEntry.of(
                "Basket strategy expression", "6", FeatureStatus.STUB, "Expression-based basket routing — stub", ""));

        entries.add(FeatureEntry.of(
                "Reference resolution + roles",
                "7",
                FeatureStatus.SUPPORTED,
                "DeferredRefs, type checking, cardinality, role-aware resolution",
                "ReferenceResolutionIntegrationTest"));

        entries.add(FeatureEntry.of(
                "ReferenceIndex + ReferenceResolutionService",
                "19",
                FeatureStatus.SUPPORTED,
                "Separate ReferenceIndex with SourceObjectKey/TargetReference; ReferenceResolutionService with per-owner cardinality, ambiguity detection, association-aware diagnostics",
                "ReferenceIndexTest",
                "ReferenceResolutionServiceTest"));

        entries.add(FeatureEntry.of(
                "Same OID different context resolution",
                "19",
                FeatureStatus.SUPPORTED,
                "Same OID in different baskets/inputs/classes correctly disambiguated; no global OID-only fallback by default",
                "ReferenceIndexTest",
                "ReferenceResolutionServiceTest",
                "ReferenceResolutionIntegrationTest"));

        entries.add(FeatureEntry.of(
                "Association-aware role resolution",
                "19",
                FeatureStatus.SUPPORTED,
                "requireRole() with AssociationDef; association name in diagnostics; XTF association serialization tested with ilivalidator",
                "AssociationXtfIntegrationTest"));

        entries.add(FeatureEntry.of(
                "failPolicy (strict/lenient/reportOnly)",
                "7",
                FeatureStatus.SUPPORTED,
                "Configurable error handling at plan level",
                "TransformationEngineIntegrationTest"));

        entries.add(FeatureEntry.of(
                "GeometryAdapter (Coord/Polyline/Surface/Area)",
                "13/24",
                FeatureStatus.SUPPORTED,
                "IoxGeometryAdapter with GeometryValueCopier, ItfGeometryWriter for ILI1 helper tables",
                "IoxGeometryAdapterTest",
                "GeometryIntegrationTest",
                "GeometryDeepCopyTest"));

        entries.add(FeatureEntry.of(
                "GeometryValueCopier",
                "24",
                FeatureStatus.SUPPORTED,
                "Deep copy IomObject geometries to prevent source-target aliasing",
                "GeometryDeepCopyTest"));

        entries.add(FeatureEntry.of(
                "GeometryCompatibilityService",
                "24",
                FeatureStatus.SUPPORTED,
                "Compile-time geometry compatibility checks: dimension, SURFACE/AREA, type matching",
                "GeometryCompatibilityServiceTest"));

        entries.add(FeatureEntry.of(
                "Geometry roundtrip (Read→Write→Read)",
                "24",
                FeatureStatus.SUPPORTED,
                "COORD, POLYLINE (straight+ARC), SURFACE, AREA with multiple boundaries; XTF and ITF validated with ilivalidator",
                "CoordRoundtripTest",
                "PolylineRoundtripTest",
                "XtfReadOwnOutputTest"));

        entries.add(FeatureEntry.of(
                "Real dataset geometry smoke test",
                "24",
                FeatureStatus.SUPPORTED,
                "Read full DM01 ITF and DMAV XTF, inventory geometry types",
                "RealDatasetGeometrySmokeTest"));

        entries.add(FeatureEntry.of(
                "FailPolicy STRICT – kein Commit bei Fehler",
                "25",
                FeatureStatus.SUPPORTED,
                "Compiler-Fehler, Runtime-Fehler oder Validator-Fehler verhindern Commit; Exit-Code != 0",
                "StrictRollbackTest"));

        entries.add(FeatureEntry.of(
                "FailPolicy LENIENT – herabstufbare Fehler",
                "25",
                FeatureStatus.SUPPORTED,
                "Explizit herabstufbare Fehler werden Warnings; strukturelle Fehler verhindern Commit",
                "LenientPolicyTest"));

        entries.add(FeatureEntry.of(
                "FailPolicy REPORT_ONLY",
                "25",
                FeatureStatus.SUPPORTED,
                "Modelle und Mapping kompilieren, Ausfuehrbarkeit analysieren, keine endgueltigen Outputs",
                "ReportOnlyTest"));

        entries.add(FeatureEntry.of(
                "TransactionalOutputManager",
                "25",
                FeatureStatus.SUPPORTED,
                "Temp-Dateien, atomarer Move bei Erfolg, Rollback bei Fehler, keep-temp fuer Debugging",
                "TransactionalOutputManagerTest"));

        entries.add(FeatureEntry.of(
                "TransformationReportWriter (JSON+MD)",
                "25",
                FeatureStatus.SUPPORTED,
                "Objektzahlen, Filterzahlen, Warnings/Errors, Referenzbericht, Validatorstatus, Laufzeit, Modelle",
                "ReportOptionCliTest"));

        entries.add(FeatureEntry.of(
                "TransferValidationService + InProcessIlivalidatorService",
                "25",
                FeatureStatus.SUPPORTED,
                "Gekapselter ilivalidator-Service mit strukturiertem ValidationResult (errorCount, warningCount)",
                "ValidateOptionCliTest",
                "ValidatorFailureExitCodeTest"));

        entries.add(FeatureEntry.of(
                "validate-transfer CLI command",
                "25",
                FeatureStatus.SUPPORTED,
                "Neuer Subcommand: validate-transfer --file/--modeldir/--model/--log",
                "ValidatorFailureExitCodeTest"));

        entries.add(FeatureEntry.of(
                "CLI --fail-policy / --keep-temp / --validate / --report",
                "25",
                FeatureStatus.SUPPORTED,
                "Alle CLI-Optionen haben Wirkung; modeldirs aus CLI und YAML mergen korrekt",
                "ValidateOptionCliTest",
                "ReportOptionCliTest",
                "JobModeldirMergeTest",
                "RelativeMappingPathCliTest"));

        entries.add(FeatureEntry.of(
                "validate-mapping mit compileTyped und modeldirs",
                "25",
                FeatureStatus.SUPPORTED,
                "validate-mapping fuehrt compileTyped mit CLI-modeldirs aus",
                "ValidateMappingTypedCliTest"));

        entries.add(FeatureEntry.of(
                "ITF/XTF I/O via iox-ili",
                "5",
                FeatureStatus.SUPPORTED,
                "Read and write INTERLIS transfer files",
                "SurfaceAreaItfIntegrationTest",
                "GeometryIntegrationTest"));

        entries.add(FeatureEntry.of(
                "XLSX correlation import",
                "8",
                FeatureStatus.SUPPORTED,
                "Parse DM01/DMAV correlation workbook",
                "CorrelationWorkbookImporterTest"));

        entries.add(FeatureEntry.of(
                "DM01→DMAV LFP3 pilot",
                "10",
                FeatureStatus.SUPPORTED,
                "LFP3 transformation DM01 to DMAV with golden test",
                "Dm01ToDmavLfp3IntegrationTest"));

        entries.add(FeatureEntry.of(
                "DMAV→DM01 LFP3 pilot",
                "11",
                FeatureStatus.SUPPORTED,
                "LFP3 transformation DMAV to DM01 with golden test",
                "DmavToDm01Lfp3IntegrationTest"));

        entries.add(FeatureEntry.of(
                "BAG OF STRUCTURE (Textpositionen)",
                "12",
                FeatureStatus.SUPPORTED,
                "Pos tables to BAG OF Textposition in both directions",
                "Dm01ToDmavLfp3IntegrationTest"));

        entries.add(FeatureEntry.of(
                "enumMap(), enumMapDefault(), enumMapStrict()",
                "15",
                FeatureStatus.SUPPORTED,
                "Enum mapping with configurable missing-value policies: warn+null, fallback, strict error",
                "BuiltinFunctionsTest",
                "EnumTargetValidationTest"));

        entries.add(FeatureEntry.of(
                "lookup(), lookupIn()",
                "16",
                FeatureStatus.PARTIAL,
                "SourceLookupIndex queries: unscoped lookup() searches all inputs; lookupIn() restricts to inputId. Return type UNKNOWN at compile time.",
                "LookupFunctionsTest"));

        entries.add(FeatureEntry.of(
                "Joins / Splits / Merge",
                "22",
                FeatureStatus.EXPERIMENTAL,
                "Multi-source equi-joins, create directives, rule dependency ordering",
                "JoinCompilationTest",
                "CreateCompilationTest",
                "CreateAdditionalObjectIntegrationTest"));

        entries.add(FeatureEntry.of(
                "RuleDispatchIndex",
                "26",
                FeatureStatus.SUPPORTED,
                "Pre-computed O(1) rule dispatch per (inputId, sourceClass); eliminates SourceRecord x Rule full scan",
                "RuleDispatchIndexTest"));

        entries.add(FeatureEntry.of(
                "ExecutionMetrics und Performance-Report",
                "26",
                FeatureStatus.SUPPORTED,
                "Laufzeitmessung, Join-/BAG-Lookup-Zähler, Targets-by-Class; integriert in JSON/Markdown-Report",
                "ExecutionMetricsTest"));

        entries.add(FeatureEntry.of(
                "Service-Dekomposition der TransformationEngine",
                "26",
                FeatureStatus.SUPPORTED,
                "SourceIndexingService, RuleExecutionService, TargetObjectFactory, AssignmentExecutionService, OutputWritingService",
                "TransformationEngineIntegrationTest"));

        entries.add(FeatureEntry.of(
                "Deterministic Output Order",
                "26",
                FeatureStatus.SUPPORTED,
                "Output sortiert nach basketId, class, oid; reproduzierbar über mehrere Runs",
                "DeterministicOutputOrderTest"));

        entries.add(FeatureEntry.of(
                "Real Dataset Smoke Tests (DM01+DMAV)",
                "26",
                FeatureStatus.SUPPORTED,
                "Vollständige Datensätze mit Modellen einlesen; Objektzahlen pro Topic/Klasse berichten",
                "FullDm01ReadSmokeTest",
                "FullDmavReadSmokeTest"));

        entries.add(FeatureEntry.of(
                "RealDatasetCatalog",
                "26",
                FeatureStatus.SUPPORTED,
                "Scan/Classify von Transfer-Dateien ohne harte Dateinamen; requireSingleItf/Xtf",
                "RealDatasetCatalogTest"));

        // Phase 27: Reale Datensatzinventarisierung und Testausschnitte
        entries.add(FeatureEntry.of(
                "TransferInventory & Inventory Service",
                "27",
                FeatureStatus.SUPPORTED,
                "Transfer-Inhaltsstatistiken (Objektzahlen pro Klasse, OID-Typen, Geometrietypen, Referenzen, LFP3-Erkennung)",
                "FullDatasetInventoryTest"));

        entries.add(FeatureEntry.of(
                "ConnectedSubgraphExtractor",
                "27",
                FeatureStatus.SUPPORTED,
                "Extrahiert fachlich zusammenhängende Teiltransfers inklusive Referenzen; BFS-Expansion, Bidirectional, maxDepth/maxObjects",
                "ConnectedSubgraphExtractorTest"));

        entries.add(FeatureEntry.of(
                "LFP3 Fixture Extraction (DMAV)",
                "27",
                FeatureStatus.SUPPORTED,
                "Aus realem DMAV-Datensatz LFP3-Fixtures extrahieren und mit ilivalidator validieren",
                "ExtractedDmavFixtureValidationTest"));

        entries.add(FeatureEntry.of(
                "DM01 ITF Fixture Extraction",
                "27",
                FeatureStatus.PARTIAL,
                "DM01-Daten können gelesen und inventarisiert werden; ITF-Geometrie-Hilfstabellen verhindern Write-Back",
                "ExtractedDm01FixtureValidationTest"));

        entries.add(FeatureEntry.of(
                "Persistent StateStore",
                "-",
                FeatureStatus.UNSUPPORTED,
                "Disk-backed or database-backed state store",
                ""));

        entries.add(FeatureEntry.of(
                "AREA topology repair", "-", FeatureStatus.UNSUPPORTED, "Topological repair of AREA geometries", ""));

        entries.add(FeatureEntry.of(
                "LINEATTR support", "-", FeatureStatus.UNSUPPORTED, "Line attribute geometry processing", ""));

        validate(entries);
        return Collections.unmodifiableList(entries);
    }

    private static void validate(List<FeatureEntry> entries) {
        for (var e : entries) {
            if (e.status() == FeatureStatus.SUPPORTED && e.testReferences().isEmpty()) {
                throw new IllegalStateException(
                        "Feature '" + e.feature() + "' is SUPPORTED but has no test references");
            }
        }
    }
}
