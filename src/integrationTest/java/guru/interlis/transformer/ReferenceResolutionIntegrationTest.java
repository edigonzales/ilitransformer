package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.PreparedJob;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.io.FormatIdResolver;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReferenceResolutionIntegrationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade p7Ts;
    private static TransferDescription p7TransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel("src/test/data/models/with-references.ili", MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors: %s", result.diagnostics())
                .isFalse();
        p7TransferDescription = result.transferDescription();
        p7Ts = new TypeSystemFacade(p7TransferDescription);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resolvesReferenceOnlyInTheSelectedSourceBasket(boolean wrongBasket) throws Exception {
        JobConfig config = p7Config(true);
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors())
                .as("Compiler errors: %s", plan.diagnostics().all())
                .isFalse();
        assertThat(plan.rules()).hasSize(2);

        Iom_jObject srcB = new Iom_jObject("P7Model.P7Topic.ClassB", "b1");
        srcB.setattrvalue("Name", "TargetB");

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "SourceA");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("b1");
        refAttr.setobjectrefbid(wrongBasket ? "b2" : "b1");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        var capturedOids = new ArrayList<String>();
        var capturedRefs = new ArrayList<String>();
        IoxWriter writer = new CapturingRefWriter(capturedOids, capturedRefs);

        TransformResult result = engine.runTyped(
                plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader(srcB);
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(result.sourceRecordsRead()).isEqualTo(2);
        assertThat(result.targetsCreated()).isEqualTo(2);
        assertThat(result.targetsWritten()).isEqualTo(2);
        if (wrongBasket) {
            assertThat(capturedRefs).isEmpty();
            assertThat(engineDiag.all())
                    .anyMatch(d -> d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY)
                            && d.message().contains("in-classb/b2"));
            return;
        }
        assertThat(result.errors()).isZero();

        assertThat(capturedOids).hasSize(2);
        assertThat(capturedRefs).hasSize(1);
        assertThat(capturedRefs.get(0)).isIn(capturedOids);
    }

    @Test
    void emitsUnresolvedDiagnosticWhenRefNotFound() throws Exception {
        JobConfig config = p7Config(true);
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "Orphan");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("NONEXISTENT");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(
                plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(result.targetsCreated()).isEqualTo(1);
        assertThat(engineDiag.all()).isNotEmpty();
        assertThat(engineDiag.all()).anyMatch(d -> d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY));
    }

    @Test
    void emitsMissingMandatoryWhenRequiredRefHasNoSourceValue() throws Exception {
        JobConfig config = p7Config(true);
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "NoRef");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(
                plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(result.targetsCreated()).isEqualTo(1);
        assertThat(engineDiag.all()).isNotEmpty();
        assertThat(engineDiag.all()).anyMatch(d -> d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY));
    }

    @Test
    void strictReportsMissingMandatoryTarget() throws Exception {
        JobConfig config = p7Config(true);
        config.job.failPolicy = "strict";
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.failPolicy()).isEqualTo(guru.interlis.transformer.mapping.plan.FailPolicy.STRICT);

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "SourceA");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("MISSING");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(
                plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(engineDiag.all()).isNotEmpty();
        assertThat(result.errors()).isGreaterThan(0);
    }

    @Test
    void lenientStillRejectsMissingRequiredReference() throws Exception {
        JobConfig config = p7Config(true);
        config.job.failPolicy = "lenient";
        Map<String, TypeSystemFacade> tsMap = Map.of("P7Model", p7Ts);
        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.failPolicy()).isEqualTo(guru.interlis.transformer.mapping.plan.FailPolicy.LENIENT);

        Iom_jObject srcA = new Iom_jObject("P7Model.P7Topic.ClassA", "a1");
        srcA.setattrvalue("Name", "SourceA");
        IomObject refAttr = srcA.addattrobj("RefToB", Iom_jObject.REF);
        refAttr.setobjectrefoid("MISSING");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);

        IoxWriter writer = new CapturingRefWriter(new ArrayList<>(), new ArrayList<>());

        TransformResult result = engine.runTyped(
                plan,
                id -> {
                    if ("in-classa".equals(id)) return createMockReader(srcA);
                    if ("in-classb".equals(id)) return createMockReader();
                    throw new IllegalStateException("Unknown input: " + id);
                },
                Map.of("out1", writer));

        assertThat(engineDiag.all()).anyMatch(d -> d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY));
        assertThat(result.errors()).isGreaterThan(0);
    }

    // -- Helpers -------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4", "2.4-local", "2.4-xml"})
    void sourceReferenceSurvivesXtfRoundTrip(String version, @TempDir Path dir) throws Exception {
        boolean xtf23 = "2.3".equals(version);
        boolean local = "2.4-local".equals(version);
        String iliVersion = xtf23 ? "2.3" : "2.4";
        Path modelFile = dir.resolve("BasketRefs.ili");
        Files.writeString(modelFile, """
                INTERLIS %s;
                MODEL BasketRefs (en) AT "https://example.org/models" VERSION "2026-11-21" =
                  TOPIC Data =
                    BASKET OID AS INTERLIS.UUIDOID;
                    OID AS INTERLIS.UUIDOID;
                    CLASS Facility = Name : TEXT*40; END Facility;
                    CLASS Line = Name : TEXT*40; END Line;
                    ASSOCIATION LineFacility =
                      Facility %s-- {1} Facility;
                      Lines -- {0..*} Line;
                    END LineFacility;
                  END Data;
                END BasketRefs.
                """.formatted(iliVersion, local ? "" : "(EXTERNAL) "));
        var compiled = new IliModelService().compileModel(modelFile.toString(), dir.toString());
        assertThat(compiled.diagnostics().all()).isEmpty();
        var td = compiled.transferDescription();
        var tsMap = Map.of("BasketRefs", new TypeSystemFacade(td));
        String suffix = "2.4-xml".equals(version) ? ".xml" : ".xtf";
        Path source = dir.resolve("source" + suffix);
        Path output = dir.resolve("output" + suffix);
        String facilityOid = UUID.randomUUID().toString();
        String lineOid = UUID.randomUUID().toString();
        String facilityBid = UUID.randomUUID().toString();
        String lineBid = local ? facilityBid : UUID.randomUUID().toString();

        var io = new InterlisIoFactory();
        var facility = new Iom_jObject("BasketRefs.Data.Facility", facilityOid);
        facility.setattrvalue("Name", "Referenced site");
        var line = new Iom_jObject("BasketRefs.Data.Line", lineOid);
        var reference = line.addattrobj("Facility", Iom_jObject.REF);
        reference.setobjectrefoid(facilityOid);
        if (xtf23) reference.setobjectrefbid(facilityBid);
        IoxWriter writer = io.createWriter(source, td);
        try {
            writer.write(new ch.interlis.iox_j.StartTransferEvent("test", null, null));
            writer.write(new ch.interlis.iox_j.StartBasketEvent("BasketRefs.Data", facilityBid));
            writer.write(new ch.interlis.iox_j.ObjectEvent(facility));
            if (!local) {
                writer.write(new ch.interlis.iox_j.EndBasketEvent());
                writer.write(new ch.interlis.iox_j.StartBasketEvent("BasketRefs.Data", lineBid));
            }
            writer.write(new ch.interlis.iox_j.ObjectEvent(line));
            writer.write(new ch.interlis.iox_j.EndBasketEvent());
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
        } finally {
            writer.close();
        }

        if (xtf23) {
            // Detect the IOX 1.24.4 writer limitation, then make the source fixture
            // conform to XTF 2.3 and validate it before the migration.
            var document = DocumentBuilderFactory.newDefaultInstance()
                    .newDocumentBuilder()
                    .parse(source.toFile());
            var role = (org.w3c.dom.Element)
                    document.getElementsByTagName("Facility").item(0);
            assertThat(role.getAttribute("EXTREF")).isEqualTo(facilityOid);
            assertThat(role.getAttribute("BID")).isEqualTo(facilityBid);
            role.removeAttribute("EXTREF");
            role.setAttribute("REF", facilityOid);
            TransformerFactory.newDefaultInstance()
                    .newTransformer()
                    .transform(new DOMSource(document), new StreamResult(source.toFile()));
        }
        assertValidXtf(source, dir);
        var sourceBinding = new InputBinding(
                "source",
                source,
                "BasketRefs",
                FormatIdResolver.fromPath(source).orElseThrow(),
                Map.of(),
                td,
                tsMap.get("BasketRefs"));
        var context = EvalContext.forRecords(
                Map.of(
                        "line", new SourceRecord("source", lineBid, line.getobjecttag(), line),
                        "site", new SourceRecord("source", facilityBid, facility.getobjecttag(), facility)),
                Map.of("source", sourceBinding),
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(new ExpressionEngine().evaluate("refEquals(line.Facility, site)", context))
                .isEqualTo(BooleanValue.TRUE);

        JobConfig config = p7Config(true);
        config.job.inputs.remove(1);
        var input = config.job.inputs.getFirst();
        input.model = "BasketRefs";
        input.path = source.toString();
        config.job.outputs.getFirst().model = "BasketRefs";
        config.job.outputs.getFirst().path = output.toString();
        config.mapping.basketStrategy.defaultStrategy = xtf23 ? "byTopic" : "generateUuid";
        config.mapping.oidStrategy.defaultStrategy = "uuid";
        var lineRule = config.mapping.rules.getFirst();
        lineRule.sources.getFirst().clazz = "BasketRefs.Data.Line";
        lineRule.target.clazz = "BasketRefs.Data.Line";
        lineRule.assign.clear();
        var ref = lineRule.refs.getFirst();
        ref.role = "Facility";
        ref.sourceRef = "p.Facility";
        var facilityRule = config.mapping.rules.get(1);
        facilityRule.sources.getFirst().clazz = "BasketRefs.Data.Facility";
        facilityRule.sources.getFirst().inputs = List.of(input.id);
        facilityRule.target.clazz = "BasketRefs.Data.Facility";

        TransformPlan plan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
        assertThat(plan.diagnostics().hasErrors())
                .as("%s", plan.diagnostics().all())
                .isFalse();
        var runner = new JobRunner(ignored -> {});
        var options = new RunOptions(List.of(dir.toString()));
        var diagnostics = runner.runPrepared(new PreparedJob(plan, null, dir), options);
        assertThat(diagnostics.hasErrors()).as("%s", diagnostics.all()).isFalse();

        String basket = null;
        String targetFacilityOid = null;
        String targetFacilityBid = null;
        String targetLineBid = null;
        IomObject targetReference = null;
        var reader = io.createReader(output, td);
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ch.interlis.iox.StartTransferEvent start) {
                    assertThat(start.getSender()).matches("ilitransformer-.+-([0-9a-f]{40}|unknown)");
                } else if (event instanceof ch.interlis.iox.StartBasketEvent start) {
                    basket = start.getBid();
                } else if (event instanceof ch.interlis.iox.ObjectEvent objectEvent) {
                    IomObject object = objectEvent.getIomObject();
                    if (object.getobjecttag().equals("BasketRefs.Data.Facility")) {
                        targetFacilityOid = object.getobjectoid();
                        targetFacilityBid = basket;
                        assertThat(object.getattrvalue("Name")).isEqualTo("Referenced site");
                    } else if (object.getobjecttag().equals("BasketRefs.Data.Line")) {
                        targetLineBid = basket;
                        targetReference = object.getattrobj("Facility", 0);
                    }
                }
            }
        } finally {
            reader.close();
        }
        assertThat(targetFacilityBid).isNotNull().isNotEqualTo(facilityBid);
        assertThat(targetLineBid).isNotNull().isNotEqualTo(lineBid);
        if (xtf23 || local) assertThat(targetLineBid).isEqualTo(targetFacilityBid);
        else assertThat(targetLineBid).isNotEqualTo(targetFacilityBid);
        assertThat(targetReference).isNotNull();
        assertThat(targetFacilityOid).isNotNull().isNotEqualTo(facilityOid);
        assertThat(targetReference.getobjectrefoid()).isEqualTo(targetFacilityOid);
        assertThat(targetReference.getobjectrefbid()).isNull();
        assertValidXtf(output, dir);

        if (xtf23) {
            Path rejected = dir.resolve("unrepresentable.xtf");
            config.job.outputs.getFirst().path = rejected.toString();
            config.job.failPolicy = "lenient";
            config.mapping.basketStrategy.defaultStrategy = "generateUuid";
            var rejectedPlan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
            var errors = runner.runPrepared(new PreparedJob(rejectedPlan, null, dir), options);
            assertThat(errors.all())
                    .anyMatch(d -> d.code().equals(DiagnosticCode.RUN_REF_UNREPRESENTABLE)
                            && d.message().contains("XTF 2.3")
                            && d.message().contains(facilityBid));
            assertThat(rejected).doesNotExist();
        } else {
            Path missing = dir.resolve("missing-target.xtf");
            config.job.outputs.getFirst().path = missing.toString();
            config.job.failPolicy = "lenient";
            facilityRule.sources.getFirst().where = "false";
            var missingPlan = new MappingCompiler().compileTyped(config, tsMap, tsMap);
            var errors = runner.runPrepared(new PreparedJob(missingPlan, null, dir), options);
            assertThat(errors.all()).anyMatch(d -> d.code().equals(DiagnosticCode.RUN_REF_MISSING_MANDATORY));
            assertThat(missing).doesNotExist();
        }
    }

    private static void assertValidXtf(Path file, Path models) {
        var result = new InProcessIlivalidatorService()
                .validate(
                        file,
                        List.of(models.toString()),
                        List.of("BasketRefs"),
                        file.resolveSibling(file.getFileName() + ".log"));
        assertThat(result.valid()).as("%s", result.logText()).isTrue();
    }

    private JobConfig p7Config(boolean required) {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec inA = new JobConfig.InputSpec();
        inA.id = "in-classa";
        inA.model = "P7Model";
        inA.path = "inputA.xtf";
        config.job.inputs.add(inA);

        JobConfig.InputSpec inB = new JobConfig.InputSpec();
        inB.id = "in-classb";
        inB.model = "P7Model";
        inB.path = "inputB.xtf";
        config.job.inputs.add(inB);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "P7Model";
        out.path = "output.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec ruleA = new JobConfig.RuleSpec();
        ruleA.id = "r-classa";
        ruleA.target = new JobConfig.TargetSpec();
        ruleA.target.clazz = "P7Model.P7Topic.ClassA";
        ruleA.target.output = "out1";
        JobConfig.SourceSpec srcA = new JobConfig.SourceSpec();
        srcA.alias = "p";
        srcA.clazz = "P7Model.P7Topic.ClassA";
        srcA.inputs = List.of("in-classa");
        ruleA.sources.add(srcA);
        ruleA.assign = new java.util.LinkedHashMap<>();
        ruleA.assign.put("Name", "${p.Name}");

        JobConfig.RuleSpec ruleB = new JobConfig.RuleSpec();
        ruleB.id = "r-classb";
        ruleB.target = new JobConfig.TargetSpec();
        ruleB.target.clazz = "P7Model.P7Topic.ClassB";
        ruleB.target.output = "out1";
        JobConfig.SourceSpec srcB = new JobConfig.SourceSpec();
        srcB.alias = "b";
        srcB.clazz = "P7Model.P7Topic.ClassB";
        srcB.inputs = List.of("in-classb");
        ruleB.sources.add(srcB);
        ruleB.assign = new java.util.LinkedHashMap<>();
        ruleB.assign.put("Name", "${b.Name}");

        JobConfig.RefMapping refMapping = new JobConfig.RefMapping();
        refMapping.role = "A_Role";
        refMapping.sourceRef = "p.RefToB";
        refMapping.required = required;
        refMapping.targetRule = "r-classb";
        ruleA.refs = List.of(refMapping);
        config.mapping.rules.add(ruleA);
        config.mapping.rules.add(ruleB);
        return config;
    }

    private IoxReader createMockReader(Iom_jObject... objects) {
        return new IoxReader() {
            private final IoxEvent[] events;

            {
                var list = new ArrayList<IoxEvent>();
                list.add(new ch.interlis.iox_j.StartTransferEvent("test", null, null));
                list.add(new ch.interlis.iox_j.StartBasketEvent("P7Model.P7Topic", "b1"));
                for (Iom_jObject obj : objects) {
                    list.add(new ch.interlis.iox_j.ObjectEvent(obj));
                }
                list.add(new ch.interlis.iox_j.EndBasketEvent());
                list.add(new ch.interlis.iox_j.EndTransferEvent());
                events = list.toArray(new IoxEvent[0]);
            }

            private int index;

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

    private static class CapturingRefWriter implements IoxWriter {
        final List<String> capturedOids;
        final List<String> capturedRefs;

        CapturingRefWriter(List<String> capturedOids, List<String> capturedRefs) {
            this.capturedOids = capturedOids;
            this.capturedRefs = capturedRefs;
        }

        @Override
        public void write(IoxEvent event) {
            if (event instanceof ch.interlis.iox.ObjectEvent oe) {
                IomObject obj = oe.getIomObject();
                capturedOids.add(obj.getobjectoid());
                for (int i = 0; i < obj.getattrcount(); i++) {
                    var attrName = obj.getattrname(i);
                    IomObject attrValue = obj.getattrobj(attrName, 0);
                    if (attrValue != null && attrValue.getobjectrefoid() != null) {
                        capturedRefs.add(attrValue.getobjectrefoid());
                    }
                }
            }
        }

        @Override
        public void flush() {}

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
    }
}
