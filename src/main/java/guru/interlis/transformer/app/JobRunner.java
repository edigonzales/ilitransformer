package guru.interlis.transformer.app;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.interlis.InterlisModelLoader;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.state.InMemoryStateStore;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class JobRunner {
    public DiagnosticCollector run(Path configPath, String modelDir) throws Exception {
        MappingLoader loader = new MappingLoader();
        JobConfig config = loader.load(configPath);
        new MappingCompiler().validate(config);

        InterlisModelLoader modelLoader = new InterlisModelLoader();
        InterlisIoFactory ioFactory = new InterlisIoFactory();

        Map<String, TransferDescription> tdByModel = new HashMap<>();
        for (JobConfig.InputSpec input : config.job.inputs) {
            tdByModel.computeIfAbsent(input.model, m -> loadModel(modelLoader, m, modelDir));
        }
        for (JobConfig.OutputSpec output : config.job.outputs) {
            tdByModel.computeIfAbsent(output.model, m -> loadModel(modelLoader, m, modelDir));
        }

        Map<String, IoxWriter> writersByOutputId = new HashMap<>();
        for (JobConfig.OutputSpec output : config.job.outputs) {
            writersByOutputId.put(output.id, ioFactory.createWriter(Path.of(output.path), tdByModel.get(output.model)));
        }

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        TransformationEngine engine = new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), diagnostics);
        engine.run(
                config,
                (input) -> createReader(ioFactory, input, tdByModel),
                writersByOutputId
        );
        return diagnostics;
    }

    private TransferDescription loadModel(InterlisModelLoader modelLoader, String modelName, String modelDir) {
        try {
            return modelLoader.compileModel(modelName, modelDir);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compile model " + modelName, ex);
        }
    }

    private IoxReader createReader(InterlisIoFactory ioFactory, JobConfig.InputSpec input, Map<String, TransferDescription> tdByModel) {
        try {
            return ioFactory.createReader(Path.of(input.path), tdByModel.get(input.model));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create reader for " + input.path, ex);
        }
    }
}
