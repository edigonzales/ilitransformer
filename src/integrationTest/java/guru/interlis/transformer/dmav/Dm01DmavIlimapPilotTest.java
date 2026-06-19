package guru.interlis.transformer.dmav;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.ilimap.IlimapLoader;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Dm01DmavIlimapPilotTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static final String YAML_MAPPING = "src/test/resources/mappings/dm01-to-dmav-lfp3-test.yaml";
    private static final String ILIMAP_MAPPING = "src/test/resources/mappings/dm01-to-dmav-lfp3-test.ilimap";
    private static final String PRODUCTION_YAML = "profiles/dm01-to-dmav/1.1/lfp3.yaml";
    private static final String PRODUCTION_ILIMAP = "profiles/dm01-to-dmav/1.1/lfp3.ilimap";

    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavTs;
    private static TransferDescription dmavTransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dm01Result = service.compileModel(MODELDIR + "dm01-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            String errors = dm01Result.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(Collectors.joining("\n  "));
            fail("DM01 model compilation errors:\n  " + errors);
        }
        dm01Ts = new TypeSystemFacade(dm01Result.transferDescription());

        IliModelCompileResult dmavResult = service.compileModel(MODELDIR + "dmav-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            String errors = dmavResult.diagnostics().all().stream()
                    .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                    .collect(Collectors.joining("\n  "));
            fail("DMAV model compilation errors:\n  " + errors);
        }
        dmavTransferDescription = dmavResult.transferDescription();
        dmavTs = new TypeSystemFacade(dmavTransferDescription);
    }

    @Test
    void lfp3YamlAndIlimapCompileToEquivalentPlan() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig yamlConfig = mapper.readValue(Path.of(YAML_MAPPING).toFile(), JobConfig.class);

        IlimapLoader ilimapLoader = new IlimapLoader();
        JobConfig ilimapConfig = ilimapLoader.load(Path.of(ILIMAP_MAPPING));

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);

        TransformPlan yamlPlan = new MappingCompiler().compileTyped(yamlConfig, sourceTs, targetTs);
        TransformPlan ilimapPlan = new MappingCompiler().compileTyped(ilimapConfig, sourceTs, targetTs);

        assertThat(yamlPlan.diagnostics().hasErrors())
                .as(
                        "YAML plan should compile without errors: %s",
                        yamlPlan.diagnostics().all())
                .isFalse();
        assertThat(ilimapPlan.diagnostics().hasErrors())
                .as(
                        "ILIMAP plan should compile without errors: %s",
                        ilimapPlan.diagnostics().all())
                .isFalse();

        assertThat(ilimapPlan.rules()).hasSameSizeAs(yamlPlan.rules());
        assertThat(ilimapPlan.enumMaps()).isEqualTo(yamlPlan.enumMaps());

        for (int i = 0; i < yamlPlan.rules().size(); i++) {
            RulePlan yamlRule = yamlPlan.rules().get(i);
            RulePlan ilimapRule = ilimapPlan.rules().get(i);
            assertThat(ilimapRule.ruleId()).isEqualTo(yamlRule.ruleId());
            assertThat(ilimapRule.outputId()).isEqualTo(yamlRule.outputId());
            assertThat(ilimapRule.sources()).hasSameSizeAs(yamlRule.sources());
            assertThat(ilimapRule.assignments()).hasSameSizeAs(yamlRule.assignments());
            assertThat(ilimapRule.bags()).hasSameSizeAs(yamlRule.bags());
            assertThat(ilimapRule.refs()).hasSameSizeAs(yamlRule.refs());
            assertThat(ilimapRule.identitySourceKeys()).isEqualTo(yamlRule.identitySourceKeys());
        }
    }

    @Test
    void lfp3IlimapRunsSmallFixture() throws Exception {
        IlimapLoader ilimapLoader = new IlimapLoader();
        JobConfig config = ilimapLoader.load(Path.of(ILIMAP_MAPPING));

        Map<String, TypeSystemFacade> sourceTs = Map.of("Dm01TestModel", dm01Ts);
        Map<String, TypeSystemFacade> targetTs = Map.of("DmavTestModel", dmavTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject nf = new Iom_jObject("Dm01TestModel.Fixpunkte.LFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test-Nachfuehrung");
        nf.setattrvalue("GueltigerEintrag", "2025-01-15");

        Iom_jObject lfp = new Iom_jObject("Dm01TestModel.Fixpunkte.LFP3", "10");
        lfp.setattrvalue("Entstehung", "1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        lfp.setattrvalue("Geometrie", "2600000.000 1200000.000");
        lfp.setattrvalue("Lagegenauigkeit", "5.0");
        lfp.setattrvalue("IstLagezuverlaessig", "ja");
        lfp.setattrvalue("Hoehengenauigkeit", "2.0");
        lfp.setattrvalue("IstHoehenzuverlaessig", "nein");
        lfp.setattrvalue("Punktzeichen", "Stein");
        Iom_jObject symbol = lfp3Symbol("200", "10", "15.0");

        Path outputPath = Files.createTempFile("dmav-lfp3-ilimap-pilot-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, dmavTransferDescription);

            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(nf, lfp, symbol), Map.of("dmav", writer));

            assertThat(result.sourceRecordsRead()).isEqualTo(3);
            assertThat(result.targetsCreated()).isEqualTo(2);
            assertThat(result.targetsWritten()).isEqualTo(2);
            assertThat(result.errors()).isEqualTo(0);

            String content = Files.readString(outputPath);
            assertThat(content).contains("LFP3Nachfuehrung");
            assertThat(content).contains("NF001");
            assertThat(content).contains("LFP001");
            assertThat(content).contains("true");
            assertThat(content).contains("false");
            assertThat(content).contains("Stein");
            assertThat(content).contains("0.05");
            assertThat(content).contains("0.02");
            assertThat(content).contains("keine");

            assertThat(engineDiag.all()).isEmpty();
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void lfp3IlimapDoesNotIntroduceDm01DmavLogicIntoCore() throws IOException {
        Path[] corePaths = {
            Path.of("src/main/java/guru/interlis/transformer/mapping"),
            Path.of("src/main/java/guru/interlis/transformer/engine"),
            Path.of("src/main/java/guru/interlis/transformer/model"),
        };

        for (Path coreDir : corePaths) {
            if (!Files.isDirectory(coreDir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(coreDir)) {
                var violations = walk.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> {
                            try {
                                String content = Files.readString(p);
                                return content.contains("import guru.interlis.transformer.dmav");
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .map(p -> p.toString())
                        .collect(Collectors.toList());
                assertThat(violations)
                        .as("Core package %s must not import DM01/DMAV-specific classes", coreDir)
                        .isEmpty();
            }
        }
    }

    @Test
    void productionProfileYamlAndIlimapProduceEquivalentJobConfig() throws Exception {
        MappingLoader yamlLoader = new MappingLoader();
        JobConfig yamlConfig = yamlLoader.load(Path.of(PRODUCTION_YAML));

        IlimapLoader ilimapLoader = new IlimapLoader();
        JobConfig ilimapConfig = ilimapLoader.load(Path.of(PRODUCTION_ILIMAP));

        assertThat(ilimapConfig.version).isEqualTo(yamlConfig.version);
        assertThat(ilimapConfig.job.name).isEqualTo(yamlConfig.job.name);
        assertThat(ilimapConfig.job.description).isEqualTo(yamlConfig.job.description);
        assertThat(ilimapConfig.job.direction).isEqualTo(yamlConfig.job.direction);
        assertThat(ilimapConfig.job.failPolicy).isEqualTo(yamlConfig.job.failPolicy);

        assertThat(ilimapConfig.mapping.oidStrategy.defaultStrategy)
                .isEqualTo(yamlConfig.mapping.oidStrategy.defaultStrategy);
        assertThat(ilimapConfig.mapping.oidStrategy.namespace).isEqualTo(yamlConfig.mapping.oidStrategy.namespace);
        assertThat(ilimapConfig.mapping.basketStrategy.defaultStrategy)
                .isEqualTo(yamlConfig.mapping.basketStrategy.defaultStrategy);

        assertThat(ilimapConfig.job.inputs).hasSameSizeAs(yamlConfig.job.inputs);
        assertThat(ilimapConfig.job.inputs.get(0).id).isEqualTo(yamlConfig.job.inputs.get(0).id);
        assertThat(ilimapConfig.job.inputs.get(0).model).isEqualTo(yamlConfig.job.inputs.get(0).model);

        assertThat(ilimapConfig.job.outputs).hasSameSizeAs(yamlConfig.job.outputs);
        assertThat(ilimapConfig.job.outputs.get(0).id).isEqualTo(yamlConfig.job.outputs.get(0).id);
        assertThat(ilimapConfig.job.outputs.get(0).model).isEqualTo(yamlConfig.job.outputs.get(0).model);

        assertThat(ilimapConfig.mapping.enums).isEqualTo(yamlConfig.mapping.enums);

        assertThat(ilimapConfig.mapping.rules).hasSameSizeAs(yamlConfig.mapping.rules);
        for (int i = 0; i < yamlConfig.mapping.rules.size(); i++) {
            JobConfig.RuleSpec yamlRule = yamlConfig.mapping.rules.get(i);
            JobConfig.RuleSpec ilimapRule = ilimapConfig.mapping.rules.get(i);
            assertThat(ilimapRule.id).isEqualTo(yamlRule.id);
            assertThat(ilimapRule.target.output).isEqualTo(yamlRule.target.output);
            assertThat(ilimapRule.target.clazz).isEqualTo(yamlRule.target.clazz);
            assertThat(ilimapRule.sources).hasSameSizeAs(yamlRule.sources);
            assertThat(ilimapRule.identity.sourceKey).isEqualTo(yamlRule.identity.sourceKey);
            assertThat(ilimapRule.assign.keySet()).isEqualTo(yamlRule.assign.keySet());
            for (String key : yamlRule.assign.keySet()) {
                String yamlExpr = normalizeQuotes(yamlRule.assign.get(key));
                String ilimapExpr = normalizeQuotes(ilimapRule.assign.get(key));
                assertThat(ilimapExpr).as("assign[%s]", key).isEqualTo(yamlExpr);
            }
        }
    }

    private static String normalizeQuotes(String expression) {
        if (expression == null) return null;
        return expression.replace('\'', '"');
    }

    private static IoxReader createMockReader(Iom_jObject... objects) {
        return new IoxReader() {
            private int index = 0;
            private final IoxEvent[] events;

            {
                var list = new ArrayList<IoxEvent>();
                list.add(new StartTransferEvent("test", null, null));
                list.add(new StartBasketEvent("Dm01TestModel.Fixpunkte", "b1"));
                for (Iom_jObject obj : objects) {
                    list.add(new ObjectEvent(obj));
                }
                list.add(new EndBasketEvent());
                list.add(new EndTransferEvent());
                events = list.toArray(new IoxEvent[0]);
            }

            @Override
            public IoxEvent read() {
                if (index < events.length) return events[index++];
                return null;
            }

            @Override
            public void close() {}

            @Override
            public IomObject createIomObject(String tag, String oid) {
                return new Iom_jObject(tag, oid);
            }

            @Override
            public ch.interlis.iox.IoxFactoryCollection getFactory() {
                return null;
            }

            @Override
            public void setFactory(ch.interlis.iox.IoxFactoryCollection factory) {}
        };
    }

    private static java.util.function.Function<String, IoxReader> onceReaderFactory(Iom_jObject... objects) {
        return new java.util.function.Function<>() {
            private boolean used = false;

            @Override
            public IoxReader apply(String inputId) {
                if (used) {
                    return new IoxReader() {
                        @Override
                        public IoxEvent read() {
                            return null;
                        }

                        @Override
                        public void close() {}

                        @Override
                        public IomObject createIomObject(String tag, String oid) {
                            return new Iom_jObject(tag, oid);
                        }

                        @Override
                        public ch.interlis.iox.IoxFactoryCollection getFactory() {
                            return null;
                        }

                        @Override
                        public void setFactory(ch.interlis.iox.IoxFactoryCollection f) {}
                    };
                }
                used = true;
                return createMockReader(objects);
            }
        };
    }

    private static Iom_jObject lfp3Symbol(String oid, String lfpOid, String ori) {
        Iom_jObject symbol = new Iom_jObject("Dm01TestModel.Fixpunkte.LFP3Symbol", oid);
        IomObject symbolRef = symbol.addattrobj("LFP3Symbol_von", Iom_jObject.REF);
        symbolRef.setobjectrefoid(lfpOid);
        symbol.setattrvalue("Ori", ori);
        return symbol;
    }
}
