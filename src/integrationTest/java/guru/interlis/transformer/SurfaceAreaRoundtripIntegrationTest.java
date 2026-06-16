package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import guru.interlis.transformer.app.IlivalidatorRunner;
import guru.interlis.transformer.app.IlivalidatorRunner.ValidationResult;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SurfaceAreaRoundtripIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String ILI1_MODEL = "Ili1SurfaceAreaTestModel";
    private static final String ILI2_MODEL = "DmavSurfaceAreaTestModel";
    private static final String ILI2_BASKET = "DmavSurfaceAreaTestModel.TestTopic";
    private static final String REAL_DMAV_MODEL = "DMAV_Grundstuecke_V1_0";
    private static final String REAL_DMAV_MODELDIR = "src/test/data/av/models/;https://models.interlis.ch";
    private static final Path REVERSE_MAPPING =
            Path.of("src/test/resources/mappings/ili2-to-ili1-surface-area-test.yaml");
    private static final Path FORWARD_MAPPING =
            Path.of("src/test/resources/mappings/ili1-to-ili2-surface-area-test.yaml");
    private static final Path REAL_DMAV_INPUT = Path.of("src/test/data/av/DMAV_Grundstuecke_V1_0.449.xtf");

    private static ch.interlis.ili2c.metamodel.TransferDescription ili1TransferDescription;
    private static ch.interlis.ili2c.metamodel.TransferDescription ili2TransferDescription;
    private static TypeSystemFacade ili1TypeSystem;
    private static TypeSystemFacade ili2TypeSystem;
    private static TransformPlan reversePlan;
    private static TransformPlan forwardPlan;

    @BeforeAll
    static void compileModelsAndPlans() throws Exception {
        IliModelService service = new IliModelService();

        IliModelCompileResult ili1Result = service.compileModel(ILI1_MODEL, MODELDIR);
        if (ili1Result.hasErrors()) {
            fail("ILI1 model compilation errors:\n  " + formatDiagnostics(ili1Result));
        }
        ili1TransferDescription = ili1Result.transferDescription();
        ili1TypeSystem = new TypeSystemFacade(ili1TransferDescription);

        IliModelCompileResult ili2Result = service.compileModel(ILI2_MODEL, MODELDIR);
        if (ili2Result.hasErrors()) {
            fail("ILI2 model compilation errors:\n  " + formatDiagnostics(ili2Result));
        }
        ili2TransferDescription = ili2Result.transferDescription();
        ili2TypeSystem = new TypeSystemFacade(ili2TransferDescription);

        reversePlan =
                compilePlan(REVERSE_MAPPING, Map.of(ILI2_MODEL, ili2TypeSystem), Map.of(ILI1_MODEL, ili1TypeSystem));
        forwardPlan =
                compilePlan(FORWARD_MAPPING, Map.of(ILI1_MODEL, ili1TypeSystem), Map.of(ILI2_MODEL, ili2TypeSystem));
    }

    @Test
    void surfaceRoundtripPreservesArcAndBoundaries() throws Exception {
        IomObject source = surfaceObject(
                "SRT1",
                surface(
                        boundary(
                                coord(2600000.000, 1200000.000),
                                arc(2600050.000, 1200100.000, 2600100.000, 1200000.000),
                                coord(2600000.000, 1200000.000)),
                        boundary(
                                coord(2600025.000, 1200025.000),
                                coord(2600075.000, 1200025.000),
                                coord(2600075.000, 1200075.000),
                                coord(2600025.000, 1200075.000),
                                coord(2600025.000, 1200025.000))));

        RoundtripResult roundtrip = runRoundtrip(source);

        assertThat(roundtrip.reverseDiagnostics().hasErrors())
                .as(roundtrip.reverseDiagnostics().all().toString())
                .isFalse();
        assertThat(roundtrip.forwardDiagnostics().hasErrors())
                .as(roundtrip.forwardDiagnostics().all().toString())
                .isFalse();
        assertThat(roundtrip.reverseValidation().success())
                .as(roundtrip.reverseValidation().log())
                .isTrue();
        assertThat(roundtrip.forwardValidation().success())
                .as(roundtrip.forwardValidation().log())
                .isTrue();
        assertThat(Files.readString(roundtrip.itfPath())).contains("ARCP");

        IomObject roundtripObject = findFirstBySuffix(roundtrip.forwardObjects(), ".SurfaceClass");
        assertThat(roundtripObject).isNotNull();
        assertThat(signature(source, "Perimeter")).isEqualTo(signature(roundtripObject, "Perimeter"));
    }

    @Test
    void areaRoundtripPreservesGeometryAndWritesPointOnSurface() throws Exception {
        IomObject source = areaObject(
                "ART1",
                surface(boundary(
                        coord(2601000.000, 1201000.000),
                        coord(2601100.000, 1201000.000),
                        coord(2601100.000, 1201100.000),
                        coord(2601000.000, 1201100.000),
                        coord(2601000.000, 1201000.000))));

        RoundtripResult roundtrip = runRoundtrip(source);
        String content = Files.readString(roundtrip.itfPath());

        assertThat(roundtrip.reverseDiagnostics().hasErrors())
                .as(roundtrip.reverseDiagnostics().all().toString())
                .isFalse();
        assertThat(roundtrip.forwardDiagnostics().hasErrors())
                .as(roundtrip.forwardDiagnostics().all().toString())
                .isFalse();
        assertThat(roundtrip.reverseValidation().success())
                .as(roundtrip.reverseValidation().log())
                .isTrue();
        assertThat(roundtrip.forwardValidation().success())
                .as(roundtrip.forwardValidation().log())
                .isTrue();
        assertThat(content).containsPattern("TABL AreaClass\\ROBJE \\d+ ART1 [0-9]+\\.[0-9]+ [0-9]+\\.[0-9]+");

        IomObject reverseArea = findFirstBySuffix(roundtrip.reverseObjects(), ".AreaClass");
        assertThat(reverseArea).isNotNull();
        assertThat(reverseArea.getattrvaluecount("Geometrie")).isEqualTo(1);
        assertThat(reverseArea.getattrvaluecount("_itf_Geometrie")).isEqualTo(1);

        IomObject forwardArea = findFirstBySuffix(roundtrip.forwardObjects(), ".AreaClass");
        assertThat(forwardArea).isNotNull();
        assertThat(signature(source, "Geometrie")).isEqualTo(signature(forwardArea, "Geometrie"));
    }

    @Test
    void areaWithoutInteriorPointProducesDiagnosticAndNoFakeGeometry() throws Exception {
        Path sourcePath = writeSourceXtf(areaObject(
                "ABAD", surface(boundary(coord(2602000.000, 1202000.000), coord(2602100.000, 1202100.000)))));

        Path outputPath = Files.createTempFile("ili1-area-bad-", ".itf");
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        TransformResult result;
        try {
            result = runTransfer(
                    reversePlan,
                    sourcePath,
                    ili2TransferDescription,
                    outputPath,
                    ili1TransferDescription,
                    "ili2",
                    "ili1",
                    diagnostics);
        } finally {
            Files.deleteIfExists(sourcePath);
        }

        assertThat(result.errors()).isGreaterThan(0);
        assertThat(diagnostics.all()).extracting(Diagnostic::code).contains(DiagnosticCode.GEOM_AREA_POINT_MISSING);

        List<IomObject> reverseObjects = readAllObjects(outputPath, ili1TransferDescription);
        IomObject reverseArea = findFirstBySuffix(reverseObjects, ".AreaClass");
        assertThat(reverseArea).isNotNull();
        assertThat(reverseArea.getattrvaluecount("Geometrie")).isZero();
    }

    @Test
    void realDmavSurfaceReverseSmoke() throws Exception {
        assumeTrue(Files.exists(REAL_DMAV_INPUT), "real DMAV fixture not present: " + REAL_DMAV_INPUT);
        IliModelService service = new IliModelService();
        IliModelCompileResult sourceResult = service.compileModel(REAL_DMAV_MODEL, REAL_DMAV_MODELDIR);
        if (sourceResult.hasErrors()) {
            fail("Real DMAV model compilation errors:\n  " + formatDiagnostics(sourceResult));
        }

        String mapping = """
                version: 1
                job:
                  name: real-dmav-surface-smoke
                  direction: dmav-to-dm01
                  failPolicy: strict
                  inputs:
                    - id: src
                      path: "input.xtf"
                      model: "DMAV_Grundstuecke_V1_0"
                      format: "xtf"
                  outputs:
                    - id: tgt
                      path: "output.itf"
                      model: "Ili1SurfaceAreaTestModel"
                      format: "itf"
                mapping:
                  oidStrategy:
                    default: integer
                  basketStrategy:
                    default: preserveOrGenerateUuid
                  rules:
                    - id: gs-surface
                      target:
                        output: tgt
                        class: "Ili1SurfaceAreaTestModel.TestTopic.SurfaceClass"
                      sources:
                        - alias: s
                          input: src
                          class: "DMAV_Grundstuecke_V1_0.Grundstuecke.GSNachfuehrung"
                      identity:
                        sourceKey: ["s.NBIdent", "s.Identifikator"]
                      assign:
                        NBIdent: "s.NBIdent"
                        Perimeter: "s.Perimeter"
                """;

        TransformPlan plan = compilePlan(
                mapping,
                Map.of(REAL_DMAV_MODEL, new TypeSystemFacade(sourceResult.transferDescription())),
                Map.of(ILI1_MODEL, ili1TypeSystem));

        Path outputPath = Files.createTempFile("real-dmav-surface-", ".itf");
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        TransformResult result = runTransfer(
                plan,
                REAL_DMAV_INPUT,
                sourceResult.transferDescription(),
                outputPath,
                ili1TransferDescription,
                "src",
                "tgt",
                diagnostics);
        ValidationResult validation = IlivalidatorRunner.validate(outputPath, List.of(MODELDIR), ILI1_MODEL, null);
        List<IomObject> reverseObjects = readAllObjects(outputPath, ili1TransferDescription);

        assertThat(result.errors()).isZero();
        assertThat(diagnostics.hasErrors()).as(diagnostics.all().toString()).isFalse();
        assertThat(validation.success()).as(validation.log()).isTrue();

        IomObject surface = findFirstBySuffix(reverseObjects, ".SurfaceClass");
        assertThat(surface).isNotNull();
        assertThat(surface.getattrvaluecount("Perimeter")).isEqualTo(1);
        assertThat(boundaryCount(surface.getattrobj("Perimeter", 0))).isGreaterThan(0);
    }

    private RoundtripResult runRoundtrip(IomObject sourceObject) throws Exception {
        Path sourcePath = writeSourceXtf(sourceObject);
        Path itfPath = Files.createTempFile("ili2-to-ili1-", ".itf");
        Path xtfPath = Files.createTempFile("ili1-back-to-ili2-", ".xtf");

        DiagnosticCollector reverseDiagnostics = new DiagnosticCollector();
        TransformResult reverseResult = runTransfer(
                reversePlan,
                sourcePath,
                ili2TransferDescription,
                itfPath,
                ili1TransferDescription,
                "ili2",
                "ili1",
                reverseDiagnostics);
        ValidationResult reverseValidation = IlivalidatorRunner.validate(itfPath, List.of(MODELDIR), ILI1_MODEL, null);
        List<IomObject> reverseObjects = readAllObjects(itfPath, ili1TransferDescription);

        DiagnosticCollector forwardDiagnostics = new DiagnosticCollector();
        TransformResult forwardResult = runTransfer(
                forwardPlan,
                itfPath,
                ili1TransferDescription,
                xtfPath,
                ili2TransferDescription,
                "ili1",
                "ili2",
                forwardDiagnostics);
        ValidationResult forwardValidation = IlivalidatorRunner.validate(xtfPath, List.of(MODELDIR), ILI2_MODEL, null);
        List<IomObject> forwardObjects = readAllObjects(xtfPath, ili2TransferDescription);

        Files.deleteIfExists(sourcePath);
        return new RoundtripResult(
                reverseResult,
                forwardResult,
                reverseDiagnostics,
                forwardDiagnostics,
                reverseValidation,
                forwardValidation,
                reverseObjects,
                forwardObjects,
                itfPath,
                xtfPath);
    }

    private TransformResult runTransfer(
            TransformPlan plan,
            Path inputPath,
            ch.interlis.ili2c.metamodel.TransferDescription inputTransferDescription,
            Path outputPath,
            ch.interlis.ili2c.metamodel.TransferDescription outputTransferDescription,
            String inputId,
            String outputId,
            DiagnosticCollector diagnostics)
            throws Exception {
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxWriter writer = ioFactory.createWriter(outputPath, outputTransferDescription, diagnostics);
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), diagnostics);
        return engine.runTyped(
                plan,
                ignored -> {
                    try {
                        return ioFactory.createReader(inputPath, inputTransferDescription);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                },
                Map.of(outputId, writer));
    }

    private static TransformPlan compilePlan(
            Path path, Map<String, TypeSystemFacade> sourceTypes, Map<String, TypeSystemFacade> targetTypes)
            throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(path.toFile(), JobConfig.class);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTypes, targetTypes);
        assertThat(plan.diagnostics().hasErrors())
                .as(plan.diagnostics().all().toString())
                .isFalse();
        return plan;
    }

    private static TransformPlan compilePlan(
            String yaml, Map<String, TypeSystemFacade> sourceTypes, Map<String, TypeSystemFacade> targetTypes)
            throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(yaml, JobConfig.class);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTypes, targetTypes);
        assertThat(plan.diagnostics().hasErrors())
                .as(plan.diagnostics().all().toString())
                .isFalse();
        return plan;
    }

    private Path writeSourceXtf(IomObject... objects) throws Exception {
        Path path = Files.createTempFile("ili2-surface-area-source-", ".xtf");
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxWriter writer = ioFactory.createWriter(path, ili2TransferDescription);
        try {
            writer.write(new StartTransferEvent("surface-area-rt", null, null));
            writer.write(new StartBasketEvent(ILI2_BASKET, "550e8400-e29b-41d4-a716-446655440000"));
            for (IomObject object : objects) {
                writer.write(new ch.interlis.iox_j.ObjectEvent(object));
            }
            writer.write(new EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();
        } catch (Exception ex) {
            Files.deleteIfExists(path);
            throw ex;
        }
        return path;
    }

    private List<IomObject> readAllObjects(
            Path path, ch.interlis.ili2c.metamodel.TransferDescription transferDescription) throws Exception {
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        List<IomObject> objects = new ArrayList<>();
        IoxReader reader = ioFactory.createReader(path, transferDescription);
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    objects.add(objectEvent.getIomObject());
                } else if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private static IomObject surfaceObject(String nbIdent, IomObject geometry) {
        Iom_jObject object = new Iom_jObject(
                "DmavSurfaceAreaTestModel.TestTopic.SurfaceClass", "00000000-0000-0000-0000-000000000001");
        object.setattrvalue("NBIdent", nbIdent);
        object.addattrobj("Perimeter", geometry);
        return object;
    }

    private static IomObject areaObject(String nbIdent, IomObject geometry) {
        Iom_jObject object =
                new Iom_jObject("DmavSurfaceAreaTestModel.TestTopic.AreaClass", "00000000-0000-0000-0000-000000000002");
        object.setattrvalue("NBIdent", nbIdent);
        object.addattrobj("Geometrie", geometry);
        return object;
    }

    private static IomObject surface(IomObject... boundaries) {
        Iom_jObject multiSurface = new Iom_jObject("MULTISURFACE", null);
        Iom_jObject surface = new Iom_jObject("SURFACE", null);
        for (IomObject boundary : boundaries) {
            surface.addattrobj("boundary", boundary);
        }
        multiSurface.addattrobj("surface", surface);
        return multiSurface;
    }

    private static IomObject boundary(IomObject... segments) {
        Iom_jObject boundary = new Iom_jObject("boundary", null);
        boundary.addattrobj("polyline", polyline(segments));
        return boundary;
    }

    private static IomObject polyline(IomObject... segments) {
        Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
        Iom_jObject sequence = new Iom_jObject("sequence", null);
        for (IomObject segment : segments) {
            sequence.addattrobj("segment", segment);
        }
        polyline.addattrobj("sequence", sequence);
        return polyline;
    }

    private static IomObject coord(double x, double y) {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", Double.toString(x));
        coord.setattrvalue("C2", Double.toString(y));
        return coord;
    }

    private static IomObject arc(double midX, double midY, double endX, double endY) {
        Iom_jObject arc = new Iom_jObject("ARC", null);
        arc.setattrvalue("A1", Double.toString(midX));
        arc.setattrvalue("A2", Double.toString(midY));
        arc.setattrvalue("C1", Double.toString(endX));
        arc.setattrvalue("C2", Double.toString(endY));
        return arc;
    }

    private static GeometrySignature signature(IomObject object, String attrName) {
        IomObject geometry =
                object != null && object.getattrvaluecount(attrName) > 0 ? object.getattrobj(attrName, 0) : null;
        return new GeometrySignature(boundaryCount(geometry), arcCount(geometry));
    }

    private static int boundaryCount(IomObject geometry) {
        if (geometry == null) {
            return 0;
        }
        if ("MULTISURFACE".equalsIgnoreCase(geometry.getobjecttag())) {
            int count = 0;
            for (int s = 0; s < geometry.getattrvaluecount("surface"); s++) {
                IomObject surface = geometry.getattrobj("surface", s);
                if (surface != null) {
                    count += surface.getattrvaluecount("boundary");
                }
            }
            return count;
        }
        return geometry.getattrvaluecount("boundary");
    }

    private static int arcCount(IomObject geometry) {
        if (geometry == null) {
            return 0;
        }
        int count = 0;
        for (IomObject polyline : polylinesOf(geometry)) {
            for (int s = 0; s < polyline.getattrvaluecount("sequence"); s++) {
                IomObject sequence = polyline.getattrobj("sequence", s);
                if (sequence == null) {
                    continue;
                }
                for (int i = 0; i < sequence.getattrvaluecount("segment"); i++) {
                    IomObject segment = sequence.getattrobj("segment", i);
                    if (segment != null && "ARC".equalsIgnoreCase(segment.getobjecttag())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static List<IomObject> polylinesOf(IomObject geometry) {
        List<IomObject> polylines = new ArrayList<>();
        if (geometry == null) {
            return polylines;
        }
        if ("MULTISURFACE".equalsIgnoreCase(geometry.getobjecttag())) {
            for (int s = 0; s < geometry.getattrvaluecount("surface"); s++) {
                IomObject surface = geometry.getattrobj("surface", s);
                if (surface == null) {
                    continue;
                }
                for (int b = 0; b < surface.getattrvaluecount("boundary"); b++) {
                    IomObject boundary = surface.getattrobj("boundary", b);
                    if (boundary == null) {
                        continue;
                    }
                    for (int p = 0; p < boundary.getattrvaluecount("polyline"); p++) {
                        IomObject polyline = boundary.getattrobj("polyline", p);
                        if (polyline != null) {
                            polylines.add(polyline);
                        }
                    }
                }
            }
        }
        return polylines;
    }

    private static IomObject findFirstBySuffix(List<IomObject> objects, String suffix) {
        return objects.stream()
                .filter(object ->
                        object.getobjecttag() != null && object.getobjecttag().endsWith(suffix))
                .findFirst()
                .orElse(null);
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((left, right) -> left + "\n  " + right)
                .orElse("<none>");
    }

    private record GeometrySignature(int boundaryCount, int arcCount) {}

    private record RoundtripResult(
            TransformResult reverseResult,
            TransformResult forwardResult,
            DiagnosticCollector reverseDiagnostics,
            DiagnosticCollector forwardDiagnostics,
            ValidationResult reverseValidation,
            ValidationResult forwardValidation,
            List<IomObject> reverseObjects,
            List<IomObject> forwardObjects,
            Path itfPath,
            Path xtfPath) {}
}
