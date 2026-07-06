package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapInputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapOutputBlock;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.ModelInventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class IlimapModelIndexService {

    private final IliModelService modelService;

    public IlimapModelIndexService() {
        this(new IliModelService());
    }

    IlimapModelIndexService(IliModelService modelService) {
        this.modelService = Objects.requireNonNull(modelService, "modelService");
    }

    public void invalidateModelCache() {
        modelService.clearCompileCache();
    }

    public IlimapModelIndex buildIndex(IlimapAnalysis analysis, Path workspaceRoot) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");

        if (!analysis.hasDocument()) {
            return IlimapModelIndex.empty();
        }

        IlimapModelIndex.Builder builder = IlimapModelIndex.builder();
        Set<String> modelNames = new LinkedHashSet<>();
        Map<String, IlimapIdeRange> modelRanges = new LinkedHashMap<>();

        for (IlimapInputBlock input : analysis.document().inputs()) {
            builder.putInputModel(input.id(), input.model());
            addModelName(input.model(), analysis.lineMap().toIdeRange(input.range()), modelNames, modelRanges);
        }
        for (IlimapOutputBlock output : analysis.document().outputs()) {
            builder.putOutputModel(output.id(), output.model());
            addModelName(output.model(), analysis.lineMap().toIdeRange(output.range()), modelNames, modelRanges);
        }

        Path baseDir = workspaceRoot.toAbsolutePath().normalize();
        Path ilimapDir = ilimapDirectory(analysis, baseDir);
        List<String> modeldirs =
                normalizeModeldirs(analysis, baseDir, ilimapDir, builder);
        String modeldirString = modeldirs.isEmpty() ? null : String.join(";", modeldirs);

        for (String modelName : modelNames) {
            var result = modelService.compileModel(modelName, modeldirString);
            if (result.hasErrors() || result.transferDescription() == null) {
                IlimapIdeRange fallbackRange = modelRanges.getOrDefault(modelName, fallbackRange(analysis));
                for (Diagnostic diagnostic : result.diagnostics().all()) {
                    builder.addDiagnostic(toIdeDiagnostic(diagnostic, fallbackRange));
                }
                continue;
            }

            ModelInventory inventory = modelService.buildInventory(result.transferDescription(), modelName);
            builder.putModel(toModelInfo(inventory, modelName));
        }

        return builder.build();
    }

    private static void addModelName(
            String modelName, IlimapIdeRange range, Set<String> modelNames, Map<String, IlimapIdeRange> modelRanges) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        modelNames.add(modelName);
        modelRanges.putIfAbsent(modelName, range);
    }

    private static List<String> normalizeModeldirs(
            IlimapAnalysis analysis, Path workspaceRoot, Path ilimapDir, IlimapModelIndex.Builder builder) {
        if (analysis.document().job() == null) {
            return List.of();
        }

        return analysis.document().job().modeldirs().stream()
                .map(modeldir -> normalizeModeldir(modeldir, workspaceRoot, ilimapDir, analysis, builder))
                .toList();
    }

    private static String normalizeModeldir(
            String modeldir, Path workspaceRoot, Path ilimapDir, IlimapAnalysis analysis, IlimapModelIndex.Builder builder) {
        if (isRemoteModeldir(modeldir)) {
            return modeldir;
        }

        Path path = Path.of(modeldir);
        Path normalized = path.isAbsolute()
                ? path.normalize()
                : ilimapDir.resolve(path).normalize();
        if (!Files.isDirectory(normalized)) {
            builder.addDiagnostic(new IlimapIdeDiagnostic(
                    DiagnosticCode.MODEL_COMPILE_FAILED,
                    IlimapIdeSeverity.ERROR,
                    "Model directory does not exist: " + modeldir,
                    analysis.lineMap().toIdeRange(analysis.document().job().range()),
                    "Check the modeldir path"));
        }
        return normalized.toString();
    }

    private static Path ilimapDirectory(IlimapAnalysis analysis, Path workspaceRoot) {
        String uri = analysis.uri();
        if (uri == null || uri.isBlank()) {
            return workspaceRoot;
        }
        try {
            java.net.URI parsed = java.net.URI.create(uri);
            if ("file".equals(parsed.getScheme())) {
                Path ilimapPath = Path.of(parsed).toAbsolutePath().normalize();
                Path parent = ilimapPath.getParent();
                if (parent != null && !parent.equals(parent.getRoot())) {
                    return parent;
                }
            }
        } catch (IllegalArgumentException e) {
            // ignore
        }
        return workspaceRoot;
    }

    private static boolean isRemoteModeldir(String modeldir) {
        return modeldir.startsWith("http://") || modeldir.startsWith("https://");
    }

    private static IlimapModelInfo toModelInfo(ModelInventory inventory, String fallbackModelName) {
        String modelName = inventory.modelName() != null ? inventory.modelName() : fallbackModelName;
        List<IlimapClassInfo> classes = inventory.topics().stream()
                .flatMap(topic -> topic.classes().stream())
                .map(IlimapModelIndexService::toClassInfo)
                .toList();
        return new IlimapModelInfo(modelName, inventory.modelVersion(), inventory.issuer(), classes);
    }

    private static IlimapClassInfo toClassInfo(ModelInventory.ClassInventory classInventory) {
        List<IlimapAttributeInfo> attributes = classInventory.attributes().stream()
                .map(attribute -> new IlimapAttributeInfo(
                        attribute.name(), attribute.typeString(), attribute.mandatory(), attribute.cardinality()))
                .toList();
        List<IlimapRoleInfo> roles = classInventory.roles().stream()
                .map(role ->
                        new IlimapRoleInfo(role.name(), role.association(), role.targetClass(), role.cardinality()))
                .toList();
        return new IlimapClassInfo(classInventory.path(), classKind(classInventory), attributes, roles);
    }

    private static String classKind(ModelInventory.ClassInventory classInventory) {
        if (classInventory.isView()) {
            return "view";
        }
        if (classInventory.isAbstract()) {
            return "abstract class";
        }
        return "class";
    }

    private static IlimapIdeDiagnostic toIdeDiagnostic(Diagnostic diagnostic, IlimapIdeRange fallbackRange) {
        return new IlimapIdeDiagnostic(
                diagnostic.code(),
                toIdeSeverity(diagnostic.severity()),
                diagnostic.message(),
                fallbackRange,
                diagnostic.suggestion());
    }

    private static IlimapIdeSeverity toIdeSeverity(Severity severity) {
        return switch (severity) {
            case ERROR -> IlimapIdeSeverity.ERROR;
            case WARNING -> IlimapIdeSeverity.WARNING;
            case INFO -> IlimapIdeSeverity.INFORMATION;
        };
    }

    private static IlimapIdeRange fallbackRange(IlimapAnalysis analysis) {
        if (analysis.hasDocument()) {
            return analysis.lineMap().toIdeRange(analysis.document().range());
        }
        IlimapIdePosition start = new IlimapIdePosition(0, 0);
        return new IlimapIdeRange(start, start);
    }
}
