package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.mapping.plan.CompileMode;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MandatoryCoveredByBagTest {
    private static final String MODELDIR = "src/test/data/models/";
    private static TransferDescription td;
    private static TypeSystemFacade ts;

    @TempDir
    Path temp;

    @BeforeAll
    static void compileModel() {
        var result = new IliModelService().compileModel(MODELDIR + "mandatory-bag.ili", MODELDIR);
        assertThat(result.hasErrors()).as("%s", result.diagnostics().all()).isFalse();
        td = result.transferDescription();
        ts = new TypeSystemFacade(td);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ilimap", "yaml"})
    void mandatoryStructuresCoveredByDirectAndNestedBags(String extension) throws Exception {
        var plan = compile(config(extension));
        assertThat(plan.compileMode()).isEqualTo(CompileMode.STRICT);
        assertThat(plan.diagnostics().all()).noneMatch(d -> d.code().equals(DiagnosticCode.MAP_MANDATORY_MISSING));
        assertThat(plan.diagnostics().hasErrors())
                .as("%s", plan.diagnostics().all())
                .isFalse();
        var bags = plan.rules().getFirst().bags();
        assertThat(bags).extracting(b -> b.bagAttrName()).containsExactly("Metaattribute", "Entries", "Nested");
        assertThat(bags.get(2).nestedBags()).extracting(b -> b.bagAttrName()).containsExactly("Detail");
    }

    @Test
    void absentBagStillReportsMandatory() throws Exception {
        var config = config("ilimap");
        config.mapping.rules.getFirst().bags.remove("meta-bag");
        assertMissing(compile(config), "Metaattribute in Parent");
    }

    @Test
    void missingScalarInsideBagStillReportsMandatory() throws Exception {
        var config = config("ilimap");
        config.mapping.rules.getFirst().bags.get("meta-bag").assign.clear();
        assertMissing(compile(config), "Value in structure Metadata");
    }

    @Test
    void missingNestedBagStillReportsMandatory() throws Exception {
        var config = config("ilimap");
        config.mapping.rules.getFirst().bags.get("Nested").nestedBags.clear();
        assertMissing(compile(config), "Detail in structure Envelope");
    }

    @Test
    void explicitOtherTargetDoesNotCoverBagId() throws Exception {
        var config = config("ilimap");
        var bags = config.mapping.rules.getFirst().bags;
        var metadata = bags.remove("meta-bag");
        metadata.target = "OptionalMetadata";
        bags.put("Metaattribute", metadata);
        assertMissing(compile(config), "Metaattribute in Parent");
    }

    @Test
    void expandDoesNotCoverParentAttribute() throws Exception {
        var config = config("ilimap");
        var bag = config.mapping.rules.getFirst().bags.get("meta-bag");
        bag.mode = "expand";
        bag.structure = "MandatoryBag.TestTopic.Source";
        bag.assign = Map.of("Name", "s.Name");
        var plan = compile(config);
        assertThat(plan.rules().getFirst().bags()).anyMatch(b -> b.isExpand());
        assertMissing(plan, "Metaattribute in Parent");
    }

    @Test
    void rejectedBagKeepsCompilerErrorAndMandatoryDiagnostic() throws Exception {
        var config = config("ilimap");
        config.mapping.rules.getFirst().bags.get("meta-bag").target = "Unknown";
        var plan = compile(config);
        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE));
        assertMissing(plan, "Metaattribute in Parent");
    }

    @Test
    void ilimapTransformsAndProducesValidatorProvenMandatoryStructures() throws Exception {
        var plan = compile(config("ilimap"));
        assertThat(plan.diagnostics().hasErrors())
                .as("%s", plan.diagnostics().all())
                .isFalse();
        var io = new InterlisIoFactory();
        Path input = temp.resolve("input.xtf");
        Path output = temp.resolve("output.xtf");
        var source = new Iom_jObject("MandatoryBag.TestTopic.Source", "12345678-1234-1234-1234-123456789012");
        source.setattrvalue("Name", "Henry");
        var inputWriter = io.createWriter(input, td);
        inputWriter.write(new StartTransferEvent("test", null, null));
        inputWriter.write(new StartBasketEvent("MandatoryBag.TestTopic", "12345678-1234-1234-1234-123456789013"));
        inputWriter.write(new ObjectEvent(source));
        inputWriter.write(new EndBasketEvent());
        inputWriter.write(new EndTransferEvent());
        inputWriter.close();
        validate(input);

        var diagnostics = new DiagnosticCollector();
        var engine = new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), diagnostics);
        var result = engine.runTyped(
                plan,
                id -> {
                    try {
                        return io.createReader(input, td);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                Map.of("out", io.createWriter(output, td)));
        assertThat(result.errors()).as("%s", diagnostics.all()).isZero();
        assertThat(result.targetsCreated()).isEqualTo(1);
        var reader = io.createReader(output, td);
        int count = 0;
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    var parent = objectEvent.getIomObject();
                    assertThat(parent.getobjecttag()).isEqualTo("MandatoryBag.TestTopic.Parent");
                    assertThat(parent.getattrvaluecount("Metaattribute")).isEqualTo(1);
                    assertThat(parent.getattrobj("Metaattribute", 0).getattrvalue("Value"))
                            .isEqualTo("Henry");
                    assertThat(parent.getattrvaluecount("Entries")).isEqualTo(1);
                    assertThat(parent.getattrobj("Nested", 0)
                                    .getattrobj("Detail", 0)
                                    .getattrvalue("Value"))
                            .isEqualTo("Henry");
                    count++;
                }
            }
        } finally {
            reader.close();
        }
        assertThat(count).isEqualTo(1);
        validate(output);
    }

    private void validate(Path file) {
        var validation = new InProcessIlivalidatorService()
                .validate(file, List.of(MODELDIR), List.of("MandatoryBag"), temp.resolve(file.getFileName() + ".log"));
        assertThat(validation.valid()).as("%s", validation.logText()).isTrue();
    }

    private static JobConfig config(String extension) throws Exception {
        return new MappingLoader().load(Path.of("src/test/resources/mappings/mandatory-bag." + extension));
    }

    private static TransformPlan compile(JobConfig config) {
        var registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(Map.of("MandatoryBag", ts), Map.of("MandatoryBag", ts));
        return new MappingCompiler().compileTyped(config, registry);
    }

    private static void assertMissing(TransformPlan plan, String message) {
        assertThat(plan.diagnostics().hasErrors()).isTrue();
        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_MANDATORY_MISSING)
                        && d.message().contains(message));
    }
}
