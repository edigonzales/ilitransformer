package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.state.InMemoryStateStore;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartTransferEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransformationEngineIntegrationTest {
    @Test
    void runsTwoPassFlowAndResolvesDeferredReferences() throws Exception {
        Iom_jObject source1 = new Iom_jObject("M.S.Topic.SourceClass", "s1");
        source1.setattrvalue("name", "first");
        IomObject ref = source1.addattrobj("parent", Iom_jObject.REF);
        ref.setobjectrefoid("s2");

        Iom_jObject source2 = new Iom_jObject("M.S.Topic.SourceClass", "s2");
        source2.setattrvalue("name", "second");

        IoxReader reader = mock(IoxReader.class);
        when(reader.read())
                .thenReturn(
                        new ch.interlis.iox_j.StartTransferEvent("test", null, null),
                        new ch.interlis.iox_j.StartBasketEvent("M.S.Topic", "b1"),
                        new ch.interlis.iox_j.ObjectEvent(source1),
                        new ch.interlis.iox_j.ObjectEvent(source2),
                        new ch.interlis.iox_j.EndBasketEvent(),
                        new ch.interlis.iox_j.EndTransferEvent(),
                        null);

        IoxWriter writer = mock(IoxWriter.class);

        JobConfig config = new JobConfig();
        config.version = 1;
        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.id = "in1";
        config.job.inputs.add(input);

        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.id = "out1";
        config.job.outputs.add(output);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.targetClass = "M.T.Topic.TargetClass";
        rule.output = "out1";
        JobConfig.SourceSpec source = new JobConfig.SourceSpec();
        source.alias = "s";
        source.input = "in1";
        source.clazz = "M.S.Topic.SourceClass";
        rule.sources.add(source);

        JobConfig.AttributeMapping attr = new JobConfig.AttributeMapping();
        attr.target = "name";
        attr.expr = "${s.name}";
        rule.attributes = java.util.List.of(attr);

        JobConfig.RefMapping refMapping = new JobConfig.RefMapping();
        refMapping.target = "parentRef";
        refMapping.expr = "ref('s','parent')";
        rule.refs = java.util.List.of(refMapping);

        config.mapping.rules.add(rule);

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), diagnostics);
        engine.run(config, ignored -> reader, Map.of("out1", writer));

        assertThat(diagnostics.all()).isEmpty();

        ArgumentCaptor<IoxEvent> events = ArgumentCaptor.forClass(IoxEvent.class);
        verify(writer).write(any(ch.interlis.iox_j.StartTransferEvent.class));
        verify(writer).write(any(ch.interlis.iox_j.StartBasketEvent.class));
        verify(writer).write(any(ch.interlis.iox_j.EndBasketEvent.class));
        verify(writer).write(any(ch.interlis.iox_j.EndTransferEvent.class));
        verify(writer, org.mockito.Mockito.atLeast(2)).write(events.capture());

        List<IomObject> writtenObjects = new ArrayList<>();
        for (IoxEvent event : events.getAllValues()) {
            if (event instanceof StartTransferEvent startTransferEvent) {
                assertThat(startTransferEvent.getSender()).isEqualTo("ilitransformer");
            }
            if (event instanceof ObjectEvent objectEvent) {
                writtenObjects.add(objectEvent.getIomObject());
            }
        }
        assertThat(writtenObjects).hasSize(2);
        IomObject parentRef = writtenObjects.stream()
                .map(obj -> obj.getattrobj("parentRef", 0))
                .filter(obj -> obj != null && obj.getobjectrefoid() != null)
                .findFirst()
                .orElse(null);
        assertThat(parentRef).isNotNull();
        assertThat(parentRef.getobjectrefoid())
                .isIn(writtenObjects.stream().map(IomObject::getobjectoid).toList());
    }
}
