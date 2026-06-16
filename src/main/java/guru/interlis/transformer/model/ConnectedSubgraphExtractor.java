package guru.interlis.transformer.model;

import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.testutil.TransferDatasetDescriptor;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConnectedSubgraphExtractor {

    private final IliModelService modelService;

    public ConnectedSubgraphExtractor(IliModelService modelService) {
        this.modelService = modelService;
    }

    public ExtractedTransfer extract(TransferDatasetDescriptor source, ExtractionRequest request) {
        TypeSystemFacade facade = compileModels(source, request);

        List<BasketEntry> entries = readAll(source, facade);
        if (entries.isEmpty()) {
            throw new IllegalStateException("No objects read from " + source.transferFile());
        }

        ReferenceGraph graph = buildReferenceGraph(entries, facade);
        Set<String> selectedKeys = selectAndExpand(entries, graph, request);

        Path outputPath = writeExtracted(source, request, entries, selectedKeys, facade);

        Set<String> includedClasses = new LinkedHashSet<>();
        Set<String> includedBaskets = new LinkedHashSet<>();
        for (BasketEntry entry : entries) {
            if (selectedKeys.contains(makeKey(entry))) {
                includedClasses.add(entry.classTag);
                includedBaskets.add(entry.basketId);
            }
        }

        String provenance = buildProvenance(source, request, selectedKeys.size(), includedClasses);

        return new ExtractedTransfer(
                outputPath,
                source.format().name(),
                includedClasses,
                selectedKeys.size(),
                List.copyOf(includedBaskets),
                provenance);
    }

    private TypeSystemFacade compileModels(TransferDatasetDescriptor source, ExtractionRequest request) {
        List<String> modelNames = source.declaredModels();
        List<String> dirs = new ArrayList<>(source.modelDirectories());
        dirs.addAll(request.modelDirs());

        String modelDirs = String.join(";", dirs);
        if (modelDirs.isBlank()) return null;

        for (String modelName : modelNames) {
            try {
                IliModelCompileResult result = modelService.compileModel(modelName, modelDirs);
                if (!result.hasErrors() && result.transferDescription() != null) {
                    return new TypeSystemFacade(result.transferDescription());
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<BasketEntry> readAll(TransferDatasetDescriptor source, TypeSystemFacade facade) {
        List<BasketEntry> entries = new ArrayList<>();
        String currentBasketId = null;
        String currentBasketType = null;

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        try {
            IoxReader reader;
            if (facade != null) {
                reader = ioFactory.createReader(source.transferFile(), facade.getTransferDescription());
            } else {
                String lowerName =
                        source.transferFile().getFileName().toString().toLowerCase();
                if (lowerName.endsWith(".itf")) {
                    reader = new ch.interlis.iom_j.itf.ItfReader2(
                            source.transferFile().toFile(), false);
                } else {
                    reader = ch.interlis.iom_j.xtf.Xtf24Reader.createReader(
                            source.transferFile().toFile());
                }
            }

            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof StartBasketEvent basket) {
                    currentBasketId = basket.getBid();
                    currentBasketType = resolveBasketType(basket);
                } else if (event instanceof ObjectEvent obj) {
                    IomObject iom = obj.getIomObject();
                    entries.add(new BasketEntry(
                            currentBasketId, currentBasketType, iom.getobjecttag(), iom.getobjectoid(), iom));
                } else if (event instanceof ch.interlis.iox.EndBasketEvent) {
                } else if (event instanceof ch.interlis.iox.EndTransferEvent) {
                    break;
                }
            }
            reader.close();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to read transfer: " + source.transferFile() + " - "
                            + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        }
        return entries;
    }

    private ReferenceGraph buildReferenceGraph(List<BasketEntry> entries, TypeSystemFacade facade) {
        ReferenceGraph graph = new ReferenceGraph();

        Map<String, List<String>> oidToKeys = new HashMap<>();
        for (BasketEntry entry : entries) {
            String key = makeKey(entry);
            oidToKeys.computeIfAbsent(entry.oid, k -> new ArrayList<>()).add(key);
        }

        for (BasketEntry entry : entries) {
            String fromKey = makeKey(entry);

            if (facade != null) {
                String scopedPath = findScopedPath(entry.classTag, facade);
                if (scopedPath != null) {
                    Table table = facade.resolveClass(scopedPath);
                    if (table != null) {
                        extractReferencesFromModel(entry, fromKey, table, oidToKeys, graph);
                    }
                }
            }
        }

        return graph;
    }

    private void extractReferencesFromModel(
            BasketEntry entry, String fromKey, Table table, Map<String, List<String>> oidToKeys, ReferenceGraph graph) {
        Iterator<ch.interlis.ili2c.metamodel.Extendable> attrsIt = table.getAttributes();
        while (attrsIt.hasNext()) {
            ch.interlis.ili2c.metamodel.Extendable ext = attrsIt.next();
            if (ext instanceof AttributeDef attr) {
                String attrName = attr.getName();
                if (attrName == null) continue;

                if (attr.getDomain() instanceof ReferenceType) {
                    IomObject refObj = entry.iom.getattrobj(attrName, 0);
                    if (refObj != null) {
                        String refOid = refObj.getobjectrefoid();
                        if (refOid != null) {
                            List<String> targetKeys = oidToKeys.getOrDefault(refOid, List.of());
                            for (String toKey : targetKeys) {
                                graph.addForward(fromKey, toKey);
                            }
                        }
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        Iterator<RoleDef> rolesIt = table.getTargetForRoles();
        if (rolesIt != null) {
            while (rolesIt.hasNext()) {
                RoleDef role = rolesIt.next();
                String roleName = role.getName();
                if (roleName == null) continue;

                IomObject refObj = entry.iom.getattrobj(roleName, 0);
                if (refObj != null) {
                    String refOid = refObj.getobjectrefoid();
                    if (refOid != null) {
                        List<String> targetKeys = oidToKeys.getOrDefault(refOid, List.of());
                        for (String toKey : targetKeys) {
                            graph.addForward(fromKey, toKey);
                        }
                    }
                }
            }
        }
    }

    private Set<String> selectAndExpand(List<BasketEntry> entries, ReferenceGraph graph, ExtractionRequest request) {
        Set<String> selected = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        Map<String, Integer> depths = new HashMap<>();

        for (BasketEntry entry : entries) {
            if (matchesTarget(entry.classTag, request.targetClasses())) {
                String key = makeKey(entry);
                selected.add(key);
                queue.add(key);
                depths.put(key, 0);
            }
        }

        while (!queue.isEmpty() && selected.size() < request.maxObjects()) {
            String current = queue.poll();
            int currentDepth = depths.getOrDefault(current, 0);
            int nextDepth = currentDepth + 1;
            if (nextDepth > request.maxDepth()) continue;

            for (String forward : graph.forward(current)) {
                if (!selected.contains(forward) && selected.size() < request.maxObjects()) {
                    selected.add(forward);
                    queue.add(forward);
                    depths.put(forward, nextDepth);
                }
            }

            if (request.includeBidirectional()) {
                for (String reverse : graph.reverse(current)) {
                    if (!selected.contains(reverse) && selected.size() < request.maxObjects()) {
                        selected.add(reverse);
                        queue.add(reverse);
                        depths.put(reverse, nextDepth);
                    }
                }
            }
        }

        return selected;
    }

    private Path writeExtracted(
            TransferDatasetDescriptor source,
            ExtractionRequest request,
            List<BasketEntry> entries,
            Set<String> selectedKeys,
            TypeSystemFacade facade) {
        String baseName = source.transferFile().getFileName().toString();
        int dotIdx = baseName.lastIndexOf('.');
        String stem = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
        String ext = dotIdx > 0 ? baseName.substring(dotIdx) : ".itf";
        Path outputFile = request.targetDir().resolve(stem + "-extracted" + ext);

        try {
            Files.createDirectories(request.targetDir());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create target directory: " + request.targetDir(), e);
        }

        InterlisIoFactory ioFactory = new InterlisIoFactory();
        try {
            IoxWriter writer;
            if (facade != null) {
                writer = ioFactory.createWriter(outputFile, facade.getTransferDescription());
            } else {
                throw new IllegalStateException("Model is required for writing extracted transfers");
            }

            Set<String> modelNames = new LinkedHashSet<>(source.declaredModels());
            String modelName =
                    modelNames.isEmpty() ? "Unknown" : modelNames.iterator().next();
            writer.write(new ch.interlis.iox_j.StartTransferEvent(modelName, null));

            String currentBasketKey = null;
            boolean basketOpen = false;

            for (BasketEntry entry : entries) {
                String key = makeKey(entry);
                if (!selectedKeys.contains(key)) continue;

                String basketKey = basketKey(entry);
                if (!basketKey.equals(currentBasketKey)) {
                    if (basketOpen) {
                        writer.write(new ch.interlis.iox_j.EndBasketEvent());
                    }
                    String basketType = entry.basketType != null ? entry.basketType : resolveDefaultTopic(facade);
                    writer.write(new ch.interlis.iox_j.StartBasketEvent(basketType, entry.basketId));
                    currentBasketKey = basketKey;
                    basketOpen = true;
                }

                writer.write(new ch.interlis.iox_j.ObjectEvent(entry.iom));
            }

            if (basketOpen) {
                writer.write(new ch.interlis.iox_j.EndBasketEvent());
            }
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write extracted transfer: " + outputFile, e);
        }

        return outputFile;
    }

    private static String buildProvenance(
            TransferDatasetDescriptor source, ExtractionRequest request, int objectCount, Set<String> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extracted from ").append(source.transferFile().getFileName()).append("\n");
        sb.append("Target classes: ").append(request.targetClasses()).append("\n");
        sb.append("Max depth: ").append(request.maxDepth()).append("\n");
        sb.append("Bidirectional: ").append(request.includeBidirectional()).append("\n");
        sb.append("Result: ")
                .append(objectCount)
                .append(" objects in ")
                .append(classes.size())
                .append(" classes: ")
                .append(classes);
        return sb.toString();
    }

    private static boolean matchesTarget(String classTag, List<String> targets) {
        for (String target : targets) {
            if (classTag.equals(target) || classTag.endsWith("." + target) || classTag.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private static String makeKey(BasketEntry entry) {
        return basketKey(entry) + "::" + entry.classTag + "::" + entry.oid;
    }

    private static String basketKey(BasketEntry entry) {
        return nullToKey(entry.basketId) + "::" + nullToKey(entry.basketType);
    }

    private static String nullToKey(String value) {
        return value == null ? "" : value;
    }

    private static String resolveBasketType(StartBasketEvent basket) {
        if (basket instanceof ch.interlis.iox_j.StartBasketEvent concreteBasket) {
            String type = trimToNull(concreteBasket.getType());
            if (type != null) {
                return type;
            }
        }

        String[] topics = basket.getTopicv();
        if (topics != null) {
            for (String topic : topics) {
                String normalized = trimToNull(topic);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String findScopedPath(String className, TypeSystemFacade facade) {
        ch.interlis.ili2c.metamodel.TransferDescription td = facade.getTransferDescription();
        Iterator<ch.interlis.ili2c.metamodel.Model> modelIt = td.iterator();
        while (modelIt.hasNext()) {
            ch.interlis.ili2c.metamodel.Model model = modelIt.next();
            Iterator<ch.interlis.ili2c.metamodel.Element> elIt = model.iterator();
            while (elIt.hasNext()) {
                ch.interlis.ili2c.metamodel.Element el = elIt.next();
                if (el instanceof ch.interlis.ili2c.metamodel.Topic topic) {
                    Iterator<ch.interlis.ili2c.metamodel.Element> telIt = topic.iterator();
                    while (telIt.hasNext()) {
                        ch.interlis.ili2c.metamodel.Element tel = telIt.next();
                        if (tel instanceof Table table) {
                            if (className.equals(table.getName())
                                    || getScoped(table).equals(className)) {
                                String modelName = model.getName() != null ? model.getName() : "";
                                String topicName = topic.getName() != null ? topic.getName() : "";
                                return modelName + "." + topicName + "." + table.getName();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String getScoped(Table table) {
        ch.interlis.ili2c.metamodel.Container container = table.getContainer();
        if (container instanceof ch.interlis.ili2c.metamodel.Topic topic) {
            ch.interlis.ili2c.metamodel.Container modelContainer = topic.getContainer();
            if (modelContainer instanceof ch.interlis.ili2c.metamodel.Model model) {
                return model.getName() + "." + topic.getName() + "." + table.getName();
            }
        }
        return table.getName();
    }

    private static String resolveDefaultTopic(TypeSystemFacade facade) {
        if (facade == null) return "Topic";
        ch.interlis.ili2c.metamodel.TransferDescription td = facade.getTransferDescription();
        Iterator<ch.interlis.ili2c.metamodel.Model> modelIt = td.iterator();
        while (modelIt.hasNext()) {
            ch.interlis.ili2c.metamodel.Model model = modelIt.next();
            Iterator<ch.interlis.ili2c.metamodel.Element> elIt = model.iterator();
            while (elIt.hasNext()) {
                ch.interlis.ili2c.metamodel.Element el = elIt.next();
                if (el instanceof ch.interlis.ili2c.metamodel.Topic topic) {
                    return model.getName() + "." + topic.getName();
                }
            }
        }
        return "Topic";
    }

    private record BasketEntry(String basketId, String basketType, String classTag, String oid, IomObject iom) {}

    private static final class ReferenceGraph {
        private final Map<String, Set<String>> forward = new LinkedHashMap<>();
        private final Map<String, Set<String>> reverse = new LinkedHashMap<>();

        void addForward(String from, String to) {
            forward.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
            reverse.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
        }

        Set<String> forward(String key) {
            return forward.getOrDefault(key, Set.of());
        }

        Set<String> reverse(String key) {
            return reverse.getOrDefault(key, Set.of());
        }
    }
}
