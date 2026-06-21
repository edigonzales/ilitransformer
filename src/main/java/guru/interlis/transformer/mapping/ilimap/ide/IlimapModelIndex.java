package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class IlimapModelIndex {

    private static final IlimapModelIndex EMPTY =
            new IlimapModelIndex(List.of(), Map.of(), Map.of(), Map.of(), List.of());

    private final List<IlimapModelInfo> models;
    private final Map<String, String> inputModelsById;
    private final Map<String, String> outputModelsById;
    private final Map<String, IlimapClassInfo> classesByQualifiedName;
    private final List<IlimapIdeDiagnostic> diagnostics;

    public IlimapModelIndex(
            List<IlimapModelInfo> models,
            Map<String, String> inputModelsById,
            Map<String, String> outputModelsById,
            Map<String, IlimapClassInfo> classesByQualifiedName,
            List<IlimapIdeDiagnostic> diagnostics) {
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
        this.inputModelsById = Map.copyOf(Objects.requireNonNull(inputModelsById, "inputModelsById"));
        this.outputModelsById = Map.copyOf(Objects.requireNonNull(outputModelsById, "outputModelsById"));
        this.classesByQualifiedName =
                Map.copyOf(Objects.requireNonNull(classesByQualifiedName, "classesByQualifiedName"));
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public static IlimapModelIndex empty() {
        return EMPTY;
    }

    public List<IlimapModelInfo> models() {
        return models;
    }

    public Optional<IlimapClassInfo> findClass(String qualifiedName) {
        return Optional.ofNullable(classesByQualifiedName.get(qualifiedName));
    }

    public List<IlimapClassInfo> classesForModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return List.of();
        }
        return models.stream()
                .filter(model -> model.name().equals(modelName))
                .flatMap(model -> model.classes().stream())
                .sorted(Comparator.comparing(IlimapClassInfo::qualifiedName))
                .toList();
    }

    public Optional<String> modelNameForInput(String inputId) {
        return Optional.ofNullable(inputModelsById.get(inputId));
    }

    public Optional<String> modelNameForOutput(String outputId) {
        return Optional.ofNullable(outputModelsById.get(outputId));
    }

    public boolean isModelLoaded(String modelName) {
        return models.stream().anyMatch(model -> model.name().equals(modelName));
    }

    public List<IlimapClassInfo> classesForInput(String inputId) {
        return modelNameForInput(inputId).map(this::classesForModel).orElseGet(List::of);
    }

    public List<IlimapClassInfo> classesForOutput(String outputId) {
        return modelNameForOutput(outputId).map(this::classesForModel).orElseGet(List::of);
    }

    public List<IlimapIdeDiagnostic> diagnostics() {
        return diagnostics;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, IlimapModelInfo> modelsByName = new LinkedHashMap<>();
        private final Map<String, String> inputModelsById = new LinkedHashMap<>();
        private final Map<String, String> outputModelsById = new LinkedHashMap<>();
        private final Map<String, IlimapClassInfo> classesByQualifiedName = new LinkedHashMap<>();
        private final java.util.ArrayList<IlimapIdeDiagnostic> diagnostics = new java.util.ArrayList<>();

        void putInputModel(String inputId, String modelName) {
            if (inputId != null && modelName != null && !modelName.isBlank()) {
                inputModelsById.put(inputId, modelName);
            }
        }

        void putOutputModel(String outputId, String modelName) {
            if (outputId != null && modelName != null && !modelName.isBlank()) {
                outputModelsById.put(outputId, modelName);
            }
        }

        void putModel(IlimapModelInfo model) {
            modelsByName.putIfAbsent(model.name(), model);
            for (IlimapClassInfo classInfo : model.classes()) {
                classesByQualifiedName.putIfAbsent(classInfo.qualifiedName(), classInfo);
            }
        }

        void addDiagnostic(IlimapIdeDiagnostic diagnostic) {
            diagnostics.add(diagnostic);
        }

        IlimapModelIndex build() {
            return new IlimapModelIndex(
                    List.copyOf(modelsByName.values()),
                    inputModelsById,
                    outputModelsById,
                    classesByQualifiedName,
                    diagnostics);
        }
    }
}
