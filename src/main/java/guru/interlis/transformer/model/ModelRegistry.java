package guru.interlis.transformer.model;

import guru.interlis.transformer.interlis.InterlisModelLoader;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.TransferFormat;

import ch.interlis.ili2c.metamodel.TransferDescription;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModelRegistry {

    private final Map<String, InputBinding> inputsById = new HashMap<>();
    private final Map<String, OutputBinding> outputsById = new HashMap<>();
    private final Map<String, TransferDescription> tdByModel = new HashMap<>();
    private final Map<String, TypeSystemFacade> tsByModel = new HashMap<>();

    private ModelRegistry() {}

    public InputBinding requireInput(String inputId) {
        InputBinding binding = inputsById.get(inputId);
        if (binding == null) {
            throw new IllegalArgumentException("Unknown input ID: " + inputId);
        }
        return binding;
    }

    public OutputBinding requireOutput(String outputId) {
        OutputBinding binding = outputsById.get(outputId);
        if (binding == null) {
            throw new IllegalArgumentException("Unknown output ID: " + outputId);
        }
        return binding;
    }

    public TypeSystemFacade requireSourceTypeSystem(String inputId) {
        return requireInput(inputId).typeSystem();
    }

    public TypeSystemFacade requireTargetTypeSystem(String outputId) {
        return requireOutput(outputId).typeSystem();
    }

    public Optional<TransferDescription> findByModelName(String modelName) {
        return Optional.ofNullable(tdByModel.get(modelName));
    }

    public Map<String, InputBinding> inputsById() {
        return Map.copyOf(inputsById);
    }

    public Map<String, OutputBinding> outputsById() {
        return Map.copyOf(outputsById);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private JobConfig config;
        private final List<String> modelDirs = new java.util.ArrayList<>();
        private Path baseDirectory;

        private Builder() {}

        public Builder config(JobConfig config) {
            this.config = config;
            return this;
        }

        public Builder modelDir(String modelDir) {
            this.modelDirs.add(modelDir);
            return this;
        }

        public Builder modelDirs(List<String> modelDirs) {
            if (modelDirs != null) {
                this.modelDirs.addAll(modelDirs);
            }
            return this;
        }

        public Builder baseDirectory(Path baseDirectory) {
            this.baseDirectory = baseDirectory;
            return this;
        }

        public ModelRegistry build() {
            if (config == null) {
                throw new IllegalStateException("JobConfig must be set");
            }

            String modelDirString = modelDirs.isEmpty() ? null : String.join(";", modelDirs);

            ModelRegistry registry = new ModelRegistry();
            InterlisModelLoader modelLoader = new InterlisModelLoader();

            // Compile each unique model once
            for (JobConfig.InputSpec input : config.job.inputs) {
                registry.tdByModel.computeIfAbsent(input.model, m -> loadModel(modelLoader, m, modelDirString));
            }
            for (JobConfig.OutputSpec output : config.job.outputs) {
                registry.tdByModel.computeIfAbsent(output.model, m -> loadModel(modelLoader, m, modelDirString));
            }

            // Build TypeSystemFacades
            for (var entry : registry.tdByModel.entrySet()) {
                registry.tsByModel.put(entry.getKey(), new TypeSystemFacade(entry.getValue()));
            }

            // Build InputBindings
            for (JobConfig.InputSpec input : config.job.inputs) {
                TransferDescription td = registry.tdByModel.get(input.model);
                TypeSystemFacade ts = registry.tsByModel.get(input.model);
                Path path = input.path != null ? Path.of(input.path) : null;
                TransferFormat format = parseFormat(input.format);
                registry.inputsById.put(input.id, new InputBinding(input.id, path, input.model, format, td, ts));
            }

            // Build OutputBindings
            for (JobConfig.OutputSpec output : config.job.outputs) {
                TransferDescription td = registry.tdByModel.get(output.model);
                TypeSystemFacade ts = registry.tsByModel.get(output.model);
                Path path = output.path != null ? Path.of(output.path) : null;
                TransferFormat format = parseFormat(output.format);
                registry.outputsById.put(output.id, new OutputBinding(output.id, path, output.model, format, td, ts));
            }

            return registry;
        }

        private static TransferFormat parseFormat(String format) {
            if (format == null) return null;
            try {
                return TransferFormat.valueOf(format.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        private static TransferDescription loadModel(
                InterlisModelLoader modelLoader, String modelName, String modelDir) {
            try {
                return modelLoader.compileModel(modelName, modelDir);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to compile model " + modelName, ex);
            }
        }

        /**
         * Builds a ModelRegistry from pre-compiled TypeSystemFacade maps.
         * For use by the deprecated {@code MappingCompiler.compileTyped(JobConfig, Map, Map)} only.
         */
        public ModelRegistry buildWithSuppliedTypeSystems(
                Map<String, TypeSystemFacade> sourceTypeSystems, Map<String, TypeSystemFacade> targetTypeSystems) {
            if (config == null) {
                throw new IllegalStateException("JobConfig must be set");
            }

            ModelRegistry registry = new ModelRegistry();

            // Build InputBindings from source type systems
            for (JobConfig.InputSpec input : config.job.inputs) {
                TypeSystemFacade ts = sourceTypeSystems.get(input.model);
                TransferDescription td = ts != null ? ts.getTransferDescription() : null;
                Path path = input.path != null ? Path.of(input.path) : null;
                TransferFormat format = parseFormat(input.format);
                registry.inputsById.put(input.id, new InputBinding(input.id, path, input.model, format, td, ts));
                if (td != null) {
                    registry.tdByModel.putIfAbsent(input.model, td);
                    registry.tsByModel.putIfAbsent(input.model, ts);
                }
            }

            // Build OutputBindings from target type systems
            for (JobConfig.OutputSpec output : config.job.outputs) {
                TypeSystemFacade ts = targetTypeSystems.get(output.model);
                TransferDescription td = ts != null ? ts.getTransferDescription() : null;
                Path path = output.path != null ? Path.of(output.path) : null;
                TransferFormat format = parseFormat(output.format);
                registry.outputsById.put(output.id, new OutputBinding(output.id, path, output.model, format, td, ts));
                if (td != null) {
                    registry.tdByModel.putIfAbsent(output.model, td);
                    registry.tsByModel.putIfAbsent(output.model, ts);
                }
            }

            return registry;
        }
    }
}
