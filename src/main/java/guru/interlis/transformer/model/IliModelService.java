package guru.interlis.transformer.model;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.interlis.InterlisModelLoader;

import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class IliModelService {

    @FunctionalInterface
    interface ModelCompiler {
        TransferDescription compileModel(String modelName, String modelDirectories) throws Ili2cFailure;
    }

    private final ModelCompiler modelCompiler;
    private final Map<CompileCacheKey, IliModelCompileResult> compileCache = new ConcurrentHashMap<>();

    public IliModelService() {
        this(new InterlisModelLoader()::compileModel);
    }

    IliModelService(ModelCompiler modelCompiler) {
        this.modelCompiler = Objects.requireNonNull(modelCompiler, "modelCompiler");
    }

    public IliModelCompileResult compileModel(String modelName, String modelDirectories) {
        String normalizedModelDirectories = InterlisModelLoader.normalizeModelDirectoryString(modelDirectories);
        CompileCacheKey cacheKey = new CompileCacheKey(
                modelName.trim(), normalizedModelDirectories, fingerprintModelDirectories(normalizedModelDirectories));
        return compileCache.computeIfAbsent(
                cacheKey, ignored -> compileModelUncached(modelName, normalizedModelDirectories));
    }

    public void clearCompileCache() {
        compileCache.clear();
    }

    private IliModelCompileResult compileModelUncached(String modelName, String normalizedModelDirectories) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        TransferDescription td;
        try {
            td = modelCompiler.compileModel(modelName, normalizedModelDirectories);
            if (td == null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MODEL_COMPILE_FAILED,
                        Severity.ERROR,
                        "Failed to compile model: " + modelName,
                        modelName,
                        "Check that the model file exists and model directories are correct"));
                return new IliModelCompileResult(null, diagnostics);
            }
        } catch (Ili2cFailure e) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.MODEL_COMPILE_FAILED,
                    Severity.ERROR,
                    "Failed to compile model: " + modelName + " - " + e.getMessage(),
                    modelName,
                    "Check that the model file exists and model directories are correct"));
            return new IliModelCompileResult(null, diagnostics);
        } catch (Throwable e) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.MODEL_COMPILE_FAILED,
                    Severity.ERROR,
                    "Unexpected error compiling model '" + modelName + "': " + e.getMessage(),
                    modelName,
                    null));
            return new IliModelCompileResult(null, diagnostics);
        }
        return new IliModelCompileResult(td, diagnostics);
    }

    private static String fingerprintModelDirectories(String normalizedModelDirectories) {
        if (normalizedModelDirectories == null || normalizedModelDirectories.isBlank()) {
            return "";
        }
        List<String> fingerprints = new ArrayList<>();
        for (String part : normalizedModelDirectories.split(";")) {
            if (isRemoteModelDirectory(part)) {
                fingerprints.add("remote:" + part);
            } else {
                fingerprints.add("local:" + part + ":" + fingerprintLocalModelDirectory(part));
            }
        }
        return String.join("|", fingerprints);
    }

    private static String fingerprintLocalModelDirectory(String modelDirectory) {
        try {
            Path path = Path.of(modelDirectory);
            if (!Files.isDirectory(path)) {
                return "missing";
            }
            try (var stream = Files.walk(path)) {
                LocalModelDirectoryFingerprint fingerprint = stream.filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().endsWith(".ili"))
                        .map(IliModelService::fileFingerprint)
                        .reduce(new LocalModelDirectoryFingerprint(0, 0), LocalModelDirectoryFingerprint::plus);
                return fingerprint.count() + ":" + fingerprint.latestModifiedMillis();
            }
        } catch (IOException | InvalidPathException e) {
            return "unavailable:" + e.getClass().getSimpleName();
        }
    }

    private static LocalModelDirectoryFingerprint fileFingerprint(Path path) {
        try {
            return new LocalModelDirectoryFingerprint(
                    1, Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            return new LocalModelDirectoryFingerprint(1, 0);
        }
    }

    private static boolean isRemoteModelDirectory(String modelDirectory) {
        return modelDirectory.startsWith("http://") || modelDirectory.startsWith("https://");
    }

    private record CompileCacheKey(String modelName, String normalizedModelDirectories, String modeldirFingerprint) {}

    private record LocalModelDirectoryFingerprint(long count, long latestModifiedMillis) {
        LocalModelDirectoryFingerprint plus(LocalModelDirectoryFingerprint other) {
            return new LocalModelDirectoryFingerprint(
                    count + other.count, Math.max(latestModifiedMillis, other.latestModifiedMillis));
        }
    }

    public ModelInventory buildInventory(TransferDescription td, String modelName) {
        List<ModelInventory.TopicInventory> topics = new ArrayList<>();
        String foundName = modelName;
        String foundVersion = null;
        String foundIssuer = null;

        Iterator<Model> modelIt = td.iterator();
        while (modelIt.hasNext()) {
            Model model = modelIt.next();
            String currentModelName = model.getName() != null ? model.getName() : modelName;
            if (isInternalModel(currentModelName)) continue;

            foundName = currentModelName;
            foundVersion = model.getModelVersion();
            foundIssuer = model.getIssuer();

            Iterator<Element> elIt = model.iterator();
            while (elIt.hasNext()) {
                Element element = elIt.next();
                if (element instanceof Topic topic) {
                    topics.add(buildTopicInventory(topic, foundName));
                }
            }
        }
        return new ModelInventory(foundName, foundVersion, foundIssuer, topics);
    }

    private ModelInventory.TopicInventory buildTopicInventory(Topic topic, String modelName) {
        String name = topic.getName() != null ? topic.getName() : "";

        Domain oidDomain = topic.getOid();
        String oidType = formatOidType(oidDomain, "STANDARDOID");

        Domain basketOidDomain = topic.getBasketOid();
        String basketOidType = formatOidType(basketOidDomain, "UUIDOID");

        List<ModelInventory.ClassInventory> classes = new ArrayList<>();
        Map<String, AssociationDef> associations = collectAssociations(topic);

        Iterator<Element> elIt = topic.iterator();
        while (elIt.hasNext()) {
            Element element = elIt.next();
            if (element instanceof Table table) {
                if (!table.isImplicit()) {
                    classes.add(buildClassInventory(table, modelName, name, associations));
                }
            }
        }

        List<ModelInventory.ClassInventory> sortedClasses = classes.stream()
                .sorted(Comparator.comparing(ModelInventory.ClassInventory::name))
                .toList();

        return new ModelInventory.TopicInventory(name, basketOidType, oidType, sortedClasses);
    }

    private Map<String, AssociationDef> collectAssociations(Topic topic) {
        Map<String, AssociationDef> map = new LinkedHashMap<>();
        Iterator<Element> elIt = topic.iterator();
        while (elIt.hasNext()) {
            Element el = elIt.next();
            if (el instanceof AssociationDef assoc) {
                String assocName = assoc.getName();
                if (assocName != null) {
                    map.put(assocName, assoc);
                }
            }
        }
        return map;
    }

    private ModelInventory.ClassInventory buildClassInventory(
            Table table, String modelName, String topicName, Map<String, AssociationDef> associations) {
        String className = table.getName() != null ? table.getName() : "";
        String path = modelName + "." + topicName + "." + className;
        boolean isAbstract = table.isAbstract();
        boolean isView = false;

        Table baseTable = findBaseTable(table);
        String baseClass = baseTable != null ? baseTable.getName() : null;

        List<ModelInventory.AttributeInventory> attributes = buildAttributes(table);
        List<ModelInventory.RoleInventory> roles = buildRoles(table, associations);

        return new ModelInventory.ClassInventory(className, path, isView, baseClass, isAbstract, attributes, roles);
    }

    private List<ModelInventory.AttributeInventory> buildAttributes(Table table) {
        Map<String, ModelInventory.AttributeInventory> result = new LinkedHashMap<>();
        Iterator<Extendable> it = table.getAttributes();
        while (it.hasNext()) {
            Extendable ext = it.next();
            if (ext instanceof AttributeDef attr) {
                putAttribute(result, attr);
            }
        }
        Iterator<ViewableTransferElement> transferElements = table.getAttributesAndRoles2();
        while (transferElements.hasNext()) {
            ViewableTransferElement element = transferElements.next();
            if (element.obj instanceof AttributeDef attr) {
                putAttribute(result, attr);
            }
        }
        return List.copyOf(result.values());
    }

    private void putAttribute(Map<String, ModelInventory.AttributeInventory> result, AttributeDef attr) {
        String name = attr.getName();
        if (name == null) return;

        String typeString = buildTypeString(attr);
        String cardinality = buildCardinalityString(attr);
        boolean mandatory = isMandatory(attr);

        result.putIfAbsent(name, new ModelInventory.AttributeInventory(name, typeString, cardinality, mandatory));
    }

    private List<ModelInventory.RoleInventory> buildRoles(Table table, Map<String, AssociationDef> associations) {
        Map<String, ModelInventory.RoleInventory> result = new LinkedHashMap<>();
        Iterator<ViewableTransferElement> transferElements = table.getAttributesAndRoles2();
        while (transferElements.hasNext()) {
            ViewableTransferElement element = transferElements.next();
            if (element.obj instanceof RoleDef role) {
                putRole(result, role, associations);
            }
        }

        @SuppressWarnings("unchecked")
        Iterator<RoleDef> it = table.getTargetForRoles();
        if (it != null) {
            while (it.hasNext()) {
                putRole(result, it.next(), associations);
            }
        }
        return List.copyOf(result.values());
    }

    private void putRole(
            Map<String, ModelInventory.RoleInventory> result, RoleDef role, Map<String, AssociationDef> associations) {
        String name = role.getName();
        if (name == null) return;

        String assocName = findAssociationName(role, associations);
        String targetClass = findRoleTargetClass(role);
        String cardinality = buildRoleCardinalityString(role);

        result.putIfAbsent(name, new ModelInventory.RoleInventory(name, assocName, targetClass, cardinality));
    }

    private String buildTypeString(AttributeDef attr) {
        ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
        if (type == null) return "???";
        return formatTypeString(type);
    }

    static String formatTypeString(ch.interlis.ili2c.metamodel.Type type) {
        if (type instanceof ch.interlis.ili2c.metamodel.CompositionType comp) {
            var component = comp.getComponentType();
            if (component != null && component.getName() != null) {
                return component.getName();
            }
        }
        if (type instanceof ch.interlis.ili2c.metamodel.EnumerationType en) {
            var enumeration = en.getEnumeration();
            if (enumeration != null) {
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < enumeration.size(); i++) {
                    if (i > 0) sb.append(", ");
                    var elem = enumeration.getElement(i);
                    sb.append(elem.getName());
                }
                sb.append(")");
                return sb.toString();
            }
        }
        return type.toString();
    }

    private String buildCardinalityString(AttributeDef attr) {
        Cardinality card = attr.getCardinality();
        if (card == null) return "1";
        long min = card.getMinimum();
        long max = card.getMaximum();
        if (max == Cardinality.UNBOUND) {
            return min + "..*";
        }
        if (min == max) {
            return String.valueOf(min);
        }
        return min + ".." + max;
    }

    private String buildRoleCardinalityString(RoleDef role) {
        Cardinality card = role.getCardinality();
        if (card == null) return "1";
        long min = card.getMinimum();
        long max = card.getMaximum();
        if (max == Cardinality.UNBOUND) {
            return min + "..*";
        }
        if (min == max) {
            return String.valueOf(min);
        }
        return min + ".." + max;
    }

    private boolean isMandatory(AttributeDef attr) {
        Cardinality card = attr.getCardinality();
        return card != null && card.getMinimum() > 0;
    }

    private Table findBaseTable(Table table) {
        Element extending = table.getExtending();
        if (extending instanceof Table base) {
            return base;
        }
        return null;
    }

    private String findAssociationName(RoleDef role, Map<String, AssociationDef> associations) {
        for (var entry : associations.entrySet()) {
            for (RoleDef r : entry.getValue().getRoles()) {
                if (r == role || (r.getName() != null && r.getName().equals(role.getName()))) {
                    return entry.getKey();
                }
            }
        }
        Element container = role.getContainer();
        if (container instanceof AssociationDef assoc) {
            String name = assoc.getName();
            return name != null ? name : null;
        }
        return null;
    }

    private String findRoleTargetClass(RoleDef role) {
        AbstractClassDef dest = role.getDestination();
        if (dest != null && dest.getName() != null) {
            return dest.getName();
        }
        return null;
    }

    private static String formatOidType(Domain domain, String defaultType) {
        if (domain == null) return defaultType;
        if (domain.getName() != null && !domain.getName().isBlank()) {
            return domain.getName();
        }
        ch.interlis.ili2c.metamodel.Type type = domain.getType();
        if (type != null) {
            String className = type.getClass().getSimpleName();
            return switch (className) {
                case "TextOIDType" -> "TEXT OID";
                case "NumericOIDType" -> "NUMERIC OID";
                case "AnyOIDType" -> "UUID";
                case "NoOid" -> "NO OID";
                default -> className;
            };
        }
        return defaultType;
    }

    private static boolean isInternalModel(String modelName) {
        return "INTERLIS".equals(modelName) || "GeometryCHLV95_V2".equals(modelName) || "CoordSystem".equals(modelName);
    }
}
