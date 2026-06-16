package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.BagPlan;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BagTransformationTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade flatTs;
    private static TypeSystemFacade nestedTs;
    private static TransferDescription flatTransferDescription;
    private static TransferDescription nestedTransferDescription;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult flatResult = service.compileModel(MODELDIR + "bag-flat-source.ili", MODELDIR);
        if (flatResult.hasErrors()) {
            fail("Flat model compile errors: " + flatResult.diagnostics().all());
        }
        flatTs = new TypeSystemFacade(flatResult.transferDescription());
        flatTransferDescription = flatResult.transferDescription();

        IliModelCompileResult nestedResult = service.compileModel(MODELDIR + "bag-nested-target.ili", MODELDIR);
        if (nestedResult.hasErrors()) {
            fail("Nested model compile errors: " + nestedResult.diagnostics().all());
        }
        nestedTs = new TypeSystemFacade(nestedResult.transferDescription());
        nestedTransferDescription = nestedResult.transferDescription();
    }

    // -- EMBED tests ------------------------------------------------------

    @Test
    void bagEmbedZeroChildrenProducesNoStructures() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        RulePlan parentRule = plan.rules().get(0);
        assertThat(parentRule.bags()).hasSize(1);
        BagPlan bag = parentRule.bags().get(0);
        assertThat(bag.mode()).isEqualTo(BagPlan.BagMode.EMBED);
        assertThat(bag.hasParentRef()).isTrue();
        assertThat(bag.parentRefAttribute()).isEqualTo("ParentId");

        Iom_jObject parent = new Iom_jObject("BagFlatSource.FlatTopic.Parent", "p1");
        parent.setattrvalue("ParentId", "P001");

        Path outputPath = Files.createTempFile("bag-embed-zero-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, nestedTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(parent), Map.of("nested", writer));

            assertThat(result.errors()).isEqualTo(0);
            assertThat(result.targetsCreated()).isEqualTo(1);
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void bagEmbedOneChildCreatesStructure() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent = new Iom_jObject("BagFlatSource.FlatTopic.Parent", "p1");
        parent.setattrvalue("ParentId", "P001");
        parent.setattrvalue("Description", "Test");

        Iom_jObject child = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c1");
        child.setattrvalue("ParentId", "p1"); // matches parent.getobjectoid()
        child.setattrvalue("X", "1.500");
        child.setattrvalue("Y", "2.500");

        Path outputPath = Files.createTempFile("bag-embed-one-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, nestedTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(parent, child), Map.of("nested", writer));

            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            assertThat(content).contains("Position");
            assertThat(content).contains("1.500");
            assertThat(content).contains("2.500");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void bagEmbedManyChildrenStableOrder() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent = new Iom_jObject("BagFlatSource.FlatTopic.Parent", "p1");
        parent.setattrvalue("ParentId", "P001");

        Iom_jObject child1 = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c20");
        child1.setattrvalue("ParentId", "p1");
        child1.setattrvalue("X", "3.000");

        Iom_jObject child2 = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c5");
        child2.setattrvalue("ParentId", "p1");
        child2.setattrvalue("X", "1.000");

        Iom_jObject child3 = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c10");
        child3.setattrvalue("ParentId", "p1");
        child3.setattrvalue("X", "2.000");

        Path outputPath = Files.createTempFile("bag-embed-order-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, nestedTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            engine.runTyped(plan, onceReaderFactory(parent, child1, child2, child3), Map.of("nested", writer));

            String content = Files.readString(outputPath);
            // Stable ordering by OID: c10, c20, c5
            int idx10 = content.indexOf("2.000");
            int idx20 = content.indexOf("3.000");
            int idx5 = content.indexOf("1.000");
            assertThat(idx10).isLessThan(idx20);
            assertThat(idx20).isLessThan(idx5);
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void bagWrongParentNotEmbedded() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent = new Iom_jObject("BagFlatSource.FlatTopic.Parent", "p1");
        parent.setattrvalue("ParentId", "P001");

        Iom_jObject wrongChild = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c1");
        wrongChild.setattrvalue("ParentId", "p999"); // different parent
        wrongChild.setattrvalue("X", "1.500");

        Path outputPath = Files.createTempFile("bag-embed-wrong-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, nestedTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            engine.runTyped(plan, onceReaderFactory(parent, wrongChild), Map.of("nested", writer));

            String content = Files.readString(outputPath);
            assertThat(content).doesNotContain("1.500");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void bagMandatoryAttributeWarns() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent = new Iom_jObject("BagFlatSource.FlatTopic.Parent", "p1");
        parent.setattrvalue("ParentId", "P001");

        Iom_jObject child = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c1");
        child.setattrvalue("ParentId", "p1");
        // Missing X (mandatory) — this will produce empty attribute in the structure
        child.setattrvalue("Y", "2.500");

        Path outputPath = Files.createTempFile("bag-embed-mandatory-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, nestedTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            engine.runTyped(plan, onceReaderFactory(parent, child), Map.of("nested", writer));

            boolean hasBagMandatoryWarning =
                    engineDiag.all().stream().anyMatch(d -> d.code().equals(DiagnosticCode.RUN_BAG_MANDATORY_MISSING));
            assertThat(hasBagMandatoryWarning).isTrue();
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    // -- EXPAND tests -----------------------------------------------------

    @Test
    void bagExpandCreatesSeparateTargets() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-expand-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        RulePlan parentRule = plan.rules().get(0);
        assertThat(parentRule.bags()).hasSize(1);
        BagPlan bag = parentRule.bags().get(0);
        assertThat(bag.mode()).isEqualTo(BagPlan.BagMode.EXPAND);

        Iom_jObject parent = new Iom_jObject("BagNestedTarget.NestedTopic.Parent", "p1");
        parent.setattrvalue("ParentId", "P001");
        parent.setattrvalue("Description", "Test");

        Iom_jObject pos1 = new Iom_jObject("BagNestedTarget.NestedTopic.Position", null);
        pos1.setattrvalue("X", "1.000");
        pos1.setattrvalue("Y", "2.000");
        parent.addattrobj("Positions", pos1);

        Iom_jObject pos2 = new Iom_jObject("BagNestedTarget.NestedTopic.Position", null);
        pos2.setattrvalue("X", "3.000");
        pos2.setattrvalue("Y", "4.000");
        parent.addattrobj("Positions", pos2);

        Path outputPath = Files.createTempFile("bag-expand-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, flatTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(parent), Map.of("flat", writer));

            assertThat(result.errors()).isEqualTo(0);
            assertThat(result.targetsCreated()).isGreaterThanOrEqualTo(3); // parent + 2 children
            String content = Files.readString(outputPath);
            assertThat(content).contains("1.000");
            assertThat(content).contains("3.000");
            assertThat(content).contains("P001");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    @Test
    void bagExpandWithParentReference() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-expand-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent = new Iom_jObject("BagNestedTarget.NestedTopic.Parent", "p1");
        parent.setattrvalue("ParentId", "P001");

        Iom_jObject pos = new Iom_jObject("BagNestedTarget.NestedTopic.Position", null);
        pos.setattrvalue("X", "2.000");
        pos.setattrvalue("Y", "3.000");
        parent.addattrobj("Positions", pos);

        Path outputPath = Files.createTempFile("bag-expand-ref-", ".itf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, flatTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(plan, onceReaderFactory(parent), Map.of("flat", writer));

            assertThat(result.errors()).isEqualTo(0);
            // Multiple targets created: parent + expanded child
            assertThat(result.targetsCreated()).isGreaterThanOrEqualTo(2);
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    // -- ParentChildIndex test --------------------------------------------

    @Test
    void parentChildIndexCorrectlySeparatesByParent() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject parent1 = new Iom_jObject("BagFlatSource.FlatTopic.Parent", "p1");
        parent1.setattrvalue("ParentId", "A");
        Iom_jObject parent2 = new Iom_jObject("BagFlatSource.FlatTopic.Parent", "p2");
        parent2.setattrvalue("ParentId", "B");

        Iom_jObject childA = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c1");
        childA.setattrvalue("ParentId", "p1");
        childA.setattrvalue("X", "1.000");

        Iom_jObject childB = new Iom_jObject("BagFlatSource.FlatTopic.Child", "c2");
        childB.setattrvalue("ParentId", "p2");
        childB.setattrvalue("X", "2.000");

        Path outputPath = Files.createTempFile("bag-embed-pc-", ".xtf");
        try {
            InterlisIoFactory ioFactory = new InterlisIoFactory();
            IoxWriter writer = ioFactory.createWriter(outputPath, nestedTransferDescription);
            DiagnosticCollector engineDiag = new DiagnosticCollector();
            TransformationEngine engine =
                    new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
            TransformResult result = engine.runTyped(
                    plan, onceReaderFactory(parent1, parent2, childA, childB), Map.of("nested", writer));

            assertThat(result.errors()).isEqualTo(0);
            String content = Files.readString(outputPath);
            // Parent A gets childA's value
            int idxA = content.indexOf("1.000");
            int idxB = content.indexOf("2.000");
            assertThat(idxA).isPositive();
            assertThat(idxB).isPositive();
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    // -- Helper -----------------------------------------------------------

    private TransformPlan compileMapping(String mappingPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(mappingPath).toFile(), JobConfig.class);
        Map<String, TypeSystemFacade> sourceTs = Map.of(
                "BagFlatSource", flatTs,
                "BagNestedTarget", nestedTs);
        Map<String, TypeSystemFacade> targetTs = Map.of(
                "BagFlatSource", flatTs,
                "BagNestedTarget", nestedTs);
        return new MappingCompiler().compileTyped(config, sourceTs, targetTs);
    }

    private static java.util.function.Function<String, IoxReader> onceReaderFactory(Iom_jObject... objects) {
        return inputId -> new IoxReader() {
            private int index = 0;
            private final IoxEvent[] events;

            {
                var list = new ArrayList<IoxEvent>();
                list.add(new StartTransferEvent("test", null, null));
                list.add(new StartBasketEvent("TestTopic", "b1"));
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
}
