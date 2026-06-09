package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import guru.interlis.transformer.expr.GeometryObjectValue;
import guru.interlis.transformer.geometry.IoxGeometryAdapter;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.support.TestGeometries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SurfaceAreaGeometryIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String MAPPING_FILE = "src/test/resources/mappings/dm01-to-dmav-surface-area-test.yaml";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dm01Result = service.compileModel(
                "src/test/data/models/dm01-surface-area-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DM01 model compilation errors:\n  " + errors);
        }
        dm01Ts = new TypeSystemFacade(dm01Result.transferDescription());

        IliModelCompileResult dmavResult = service.compileModel(
                "src/test/data/models/dmav-surface-area-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(java.util.stream.Collectors.joining("\n  "));
            fail("DMAV model compilation errors:\n  " + errors);
        }
        dmavTs = new TypeSystemFacade(dmavResult.transferDescription());
    }

    @Test
    void compileDetectsSurfaceAndAreaTypes() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(MAPPING_FILE).toFile(), JobConfig.class);
        Map<String, TypeSystemFacade> src = Map.of("Dm01SurfaceAreaTestModel", dm01Ts);
        Map<String, TypeSystemFacade> tgt = Map.of("DmavSurfaceAreaTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, src, tgt);
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();

        var surfAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Perimeter"))
                .findFirst().orElseThrow();
        assertThat(surfAssign.expression().resultType()).isEqualTo(TypeInfo.SURFACE);

        var areaAssign = plan.rules().get(1).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Geometrie"))
                .findFirst().orElseThrow();
        assertThat(areaAssign.expression().resultType()).isEqualTo(TypeInfo.AREA);
    }

    @Test
    void ioxGeometryAdapterRoundtripSurface() {
        IoxGeometryAdapter adapter = new IoxGeometryAdapter();
        IomObject geom = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(0.0, 0.0),
                TestGeometries.coord(10.0, 0.0),
                TestGeometries.coord(10.0, 10.0),
                TestGeometries.coord(0.0, 10.0),
                TestGeometries.coord(0.0, 0.0)));
        assertThat(geom).isNotNull();
        assertThat(geom.getobjecttag()).isEqualTo("MULTISURFACE");

        assertThat(geom.getattrvaluecount("surface")).isEqualTo(1);
        var innerSurface = geom.getattrobj("surface", 0);
        assertThat(innerSurface).isNotNull();
        assertThat(innerSurface.getattrvaluecount("boundary")).isEqualTo(1);
        var boundary = innerSurface.getattrobj("boundary", 0);
        assertThat(boundary.getattrvaluecount("polyline")).isEqualTo(1);
        var poly = boundary.getattrobj("polyline", 0);
        assertThat(poly.getattrvaluecount("sequence")).isEqualTo(1);
        var dseq = poly.getattrobj("sequence", 0);
        assertThat(dseq.getattrvaluecount("segment")).isEqualTo(5);

        var back = adapter.normalize(geom, TypeInfo.SURFACE);
        assertThat(back).isInstanceOf(GeometryObjectValue.class);
        var backSv = (GeometryObjectValue) back;
        assertThat(backSv.geometryType()).isEqualTo(TypeInfo.SURFACE);
        IomObject copied = adapter.denormalize(backSv, TypeInfo.SURFACE);
        assertThat(copied.getattrobj("surface", 0).getattrvaluecount("boundary")).isEqualTo(1);
    }

    @Test
    void ioxGeometryAdapterRoundtripArea() {
        IoxGeometryAdapter adapter = new IoxGeometryAdapter();
        IomObject geom = TestGeometries.surface(TestGeometries.boundary(
                TestGeometries.coord(2600000.0, 1200000.0),
                TestGeometries.coord(2600100.0, 1200000.0),
                TestGeometries.coord(2600100.0, 1200100.0),
                TestGeometries.coord(2600000.0, 1200000.0)));

        var back = adapter.normalize(geom, TypeInfo.AREA);
        assertThat(back).isInstanceOf(GeometryObjectValue.class);
        assertThat(((GeometryObjectValue) back).geometryType()).isEqualTo(TypeInfo.AREA);
        assertThat(adapter.denormalize(back, TypeInfo.AREA)).isNotNull();
    }

    @Test
    void ioxGeometryAdapterMultipleRings() {
        IoxGeometryAdapter adapter = new IoxGeometryAdapter();
        IomObject geom = TestGeometries.surface(
                TestGeometries.boundary(
                        TestGeometries.coord(0.0, 0.0),
                        TestGeometries.coord(100.0, 0.0),
                        TestGeometries.coord(100.0, 100.0),
                        TestGeometries.coord(0.0, 100.0),
                        TestGeometries.coord(0.0, 0.0)),
                TestGeometries.boundary(
                        TestGeometries.coord(10.0, 10.0),
                        TestGeometries.coord(90.0, 10.0),
                        TestGeometries.coord(90.0, 90.0),
                        TestGeometries.coord(10.0, 90.0),
                        TestGeometries.coord(10.0, 10.0)));
        assertThat(geom.getattrobj("surface", 0).getattrvaluecount("boundary")).isEqualTo(2);

        var back = adapter.normalize(geom, TypeInfo.SURFACE);
        assertThat(back).isInstanceOf(GeometryObjectValue.class);
        IomObject copied = adapter.denormalize(back, TypeInfo.SURFACE);
        assertThat(copied.getattrobj("surface", 0).getattrvaluecount("boundary")).isEqualTo(2);
    }
}
