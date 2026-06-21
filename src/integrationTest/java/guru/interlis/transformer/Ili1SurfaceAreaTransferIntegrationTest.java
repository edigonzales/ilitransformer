package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
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
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Ili1SurfaceAreaTransferIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final Path VALID_INPUT = Path.of("src/test/resources/transfers/ili1-surface-area/input.itf");
    private static final Path INVALID_INPUT =
            Path.of("src/test/resources/transfers/ili1-surface-area/input-missing-area-geom.itf");
    private static final Path MAPPING_FILE = Path.of("src/test/resources/mappings/ili1-to-ili2-surface-area-test.yaml");
    private static final Path REAL_DM01_INPUT =
            Dm01DmavPaths.fullRunBundleDir("so-2549").resolve("source/2549.ch.so.agi.av.dm01_ch.itf");

    private static ch.interlis.ili2c.metamodel.TransferDescription ili1Td;
    private static ch.interlis.ili2c.metamodel.TransferDescription ili2Td;
    private static TypeSystemFacade ili1Ts;
    private static TypeSystemFacade ili2Ts;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult ili1Result = service.compileModel("Ili1SurfaceAreaTestModel", MODELDIR);
        if (ili1Result.hasErrors()) {
            fail("ILI1 model compilation errors:\n  " + formatDiagnostics(ili1Result));
        }
        ili1Td = ili1Result.transferDescription();
        ili1Ts = new TypeSystemFacade(ili1Td);

        IliModelCompileResult ili2Result = service.compileModel("DmavSurfaceAreaTestModel", MODELDIR);
        if (ili2Result.hasErrors()) {
            fail("ILI2 model compilation errors:\n  " + formatDiagnostics(ili2Result));
        }
        ili2Td = ili2Result.transferDescription();
        ili2Ts = new TypeSystemFacade(ili2Td);
    }

    @Test
    void ili1ItfToIli2XtfPreservesAreaAndSurfaceIncludingArcs() throws Exception {
        TransferRun run = runTransfer(VALID_INPUT);

        assertThat(run.result().errors()).isZero();
        assertThat(run.diagnostics().hasErrors())
                .as("Diagnostics: %s", run.diagnostics().all())
                .isFalse();

        List<IomObject> outputObjects = readAllObjects(run.outputPath(), ili2Td);
        IomObject surface = findFirstBySuffix(outputObjects, ".SurfaceClass");
        IomObject area = findFirstBySuffix(outputObjects, ".AreaClass");

        assertThat(surface).isNotNull();
        assertThat(surface.getattrvaluecount("Perimeter")).isGreaterThan(0);
        assertThat(area).isNotNull();
        assertThat(area.getattrvaluecount("Geometrie")).isGreaterThan(0);

        String xml = Files.readString(run.outputPath());
        assertThat(xml).contains("2600050");
        assertThat(xml).contains("2601050");
    }

    @Test
    void missingAreaGeometryProducesDiagnosticAndNoFakePolygon() throws Exception {
        TransferRun run = runTransfer(INVALID_INPUT);

        assertThat(run.diagnostics().all()).extracting(Diagnostic::code).contains(DiagnosticCode.GEOM_INVALID);

        List<IomObject> outputObjects = readAllObjects(run.outputPath(), ili2Td);
        IomObject area = findFirstBySuffix(outputObjects, ".AreaClass");
        assertThat(area).isNotNull();
        assertThat(area.getattrvaluecount("Geometrie")).isZero();
    }

    @Test
    void realDm01ItfReaderMergesAreaAndSurfaceGeometry() throws Exception {
        assertThat(REAL_DM01_INPUT)
                .as("Expected checked-in real DM01 fixture for so-2549 full run")
                .exists()
                .isRegularFile();
        IliModelService service = new IliModelService();
        IliModelCompileResult dm01Result =
                service.compileModel(Dm01DmavPaths.DM01_MODEL, Dm01DmavPaths.LOCAL_AND_REMOTE_MODEL_DIRS);
        if (dm01Result.hasErrors()) {
            fail("DM01 model compilation errors:\n  " + formatDiagnostics(dm01Result));
        }

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        List<IomObject> objects = new ArrayList<>();
        IoxReader reader = ioFactory.createReader(REAL_DM01_INPUT, dm01Result.transferDescription());
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent obj) {
                    objects.add(obj.getIomObject());
                }
                if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }

        IomObject flurname = findFirstBySuffix(objects, ".Flurname");
        IomObject ortsname = findFirstBySuffix(objects, ".Ortsname");

        assertThat(flurname).isNotNull();
        assertThat(flurname.getattrvaluecount("Geometrie")).isGreaterThan(0);
        assertThat(flurname.getattrobj("Geometrie", 0)).isNotNull();

        assertThat(ortsname).isNotNull();
        assertThat(ortsname.getattrvaluecount("Geometrie")).isGreaterThan(0);
        assertThat(ortsname.getattrobj("Geometrie", 0)).isNotNull();
    }

    private TransferRun runTransfer(Path inputPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(MAPPING_FILE.toFile(), JobConfig.class);
        TransformPlan plan = new MappingCompiler()
                .compileTyped(
                        config, Map.of("Ili1SurfaceAreaTestModel", ili1Ts), Map.of("DmavSurfaceAreaTestModel", ili2Ts));

        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler diagnostics: %s", plan.diagnostics().all())
                .isFalse();

        Path outputPath = Files.createTempFile("ili1-surface-area-", ".xtf");
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), diagnostics);

        try {
            TransformResult result = engine.runTyped(
                    plan,
                    freshReaderFactory(inputPath, ioFactory, ili1Td),
                    Map.of("ili2", ioFactory.createWriter(outputPath, ili2Td)));
            return new TransferRun(result, diagnostics, outputPath);
        } catch (Exception ex) {
            Files.deleteIfExists(outputPath);
            throw ex;
        }
    }

    private static Function<String, IoxReader> freshReaderFactory(
            Path inputPath, InterlisIoFactory ioFactory, ch.interlis.ili2c.metamodel.TransferDescription td) {
        return ignored -> {
            try {
                return ioFactory.createReader(inputPath, td);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static List<IomObject> readAllObjects(Path path, ch.interlis.ili2c.metamodel.TransferDescription td)
            throws Exception {
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        List<IomObject> objects = new ArrayList<>();
        IoxReader reader = ioFactory.createReader(path, td);
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent obj) {
                    objects.add(obj.getIomObject());
                } else if (event instanceof EndTransferEvent) {
                    break;
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private static IomObject findFirstBySuffix(List<IomObject> objects, String tagSuffix) {
        return objects.stream()
                .filter(obj -> obj.getobjecttag() != null && obj.getobjecttag().endsWith(tagSuffix))
                .findFirst()
                .orElse(null);
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((a, b) -> a + "\n  " + b)
                .orElse("<none>");
    }

    private record TransferRun(TransformResult result, DiagnosticCollector diagnostics, Path outputPath) {}
}
