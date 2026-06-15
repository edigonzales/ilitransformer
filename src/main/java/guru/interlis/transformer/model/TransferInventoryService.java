package guru.interlis.transformer.model;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.EndBasketEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.testutil.TransferDatasetDescriptor;
import ch.interlis.iom_j.itf.ItfReader2;
import ch.interlis.iom_j.xtf.Xtf24Reader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TransferInventoryService {

    private final IliModelService modelService;
    private final TransferInventoryClassifier classifier;

    public TransferInventoryService(IliModelService modelService) {
        this(modelService, TransferInventoryClassifier.none());
    }

    public TransferInventoryService(IliModelService modelService,
                                    TransferInventoryClassifier classifier) {
        this.modelService = modelService;
        this.classifier = classifier != null ? classifier : TransferInventoryClassifier.none();
    }

    public TransferInventory inspect(TransferDatasetDescriptor descriptor) {
        java.util.Optional<TypeSystemFacade> facade = compileModels(descriptor);

        List<String> collectedModelNames = new ArrayList<>(descriptor.declaredModels());
        List<TransferInventory.BasketSummary> baskets = new ArrayList<>();
        long totalObjects = 0;

        String currentBasketId = null;
        String currentTopic = null;
        long currentBasketCount = 0;

        Map<String, Long> classCounts = new LinkedHashMap<>();
        Set<String> geometryObservation = new LinkedHashSet<>();
        Map<String, Integer> refCounts = new LinkedHashMap<>();
        Map<String, Set<String>> classifications = new LinkedHashMap<>();

        IoxReader reader = null;
        try {
            if (facade.isPresent()) {
                InterlisIoFactory ioFactory = new InterlisIoFactory();
                reader = ioFactory.createReader(descriptor.transferFile(),
                        facade.get().getTransferDescription());
            } else {
                reader = createPlainReader(descriptor);
            }

            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ch.interlis.iox.StartTransferEvent) {
                } else if (event instanceof StartBasketEvent basket) {
                    if (currentBasketId != null) {
                        baskets.add(new TransferInventory.BasketSummary(
                                currentBasketId, currentTopic, currentBasketCount));
                    }
                    currentBasketId = basket.getBid();
                    String[] topics = basket.getTopicv();
                    currentTopic = topics != null && topics.length > 0 ? String.join(",", topics) : null;
                    currentBasketCount = 0;
                } else if (event instanceof ObjectEvent obj) {
                    totalObjects++;
                    currentBasketCount++;
                    IomObject iom = obj.getIomObject();
                    String tag = iom.getobjecttag();
                    if (tag != null) {
                        classCounts.merge(tag, 1L, Long::sum);
                        classifier.classify(iom, (category, value) -> classifications
                                .computeIfAbsent(category, ignored -> new LinkedHashSet<>())
                                .add(value));
                        if (collectedModelNames.isEmpty()) {
                            String modelPart = extractModelName(tag);
                            if (modelPart != null) {
                                collectedModelNames.add(modelPart);
                            }
                        }
                    }

                    if (facade.isPresent() && tag != null) {
                        inspectWithModel(tag, iom, facade.get(), geometryObservation,
                                refCounts);
                    }
                } else if (event instanceof ch.interlis.iox.EndBasketEvent) {
                } else if (event instanceof ch.interlis.iox.EndTransferEvent) {
                    if (currentBasketId != null) {
                        baskets.add(new TransferInventory.BasketSummary(
                                currentBasketId, currentTopic, currentBasketCount));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to inspect transfer: " + descriptor.transferFile(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }

        List<TransferInventory.ClassStats> classStats = buildClassStats(
                classCounts, facade, geometryObservation, refCounts);

        return new TransferInventory(
                descriptor.transferFile(),
                descriptor.format().name(),
                collectedModelNames,
                totalObjects,
                baskets.size(),
                baskets,
                classStats,
                List.copyOf(geometryObservation),
                refCounts,
                toImmutableClassifications(classifications)
        );
    }

    private static Map<String, List<String>> toImmutableClassifications(
            Map<String, Set<String>> classifications) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var entry : classifications.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static String extractModelName(String qualifiedClassName) {
        if (qualifiedClassName == null) return null;
        int firstDot = qualifiedClassName.indexOf('.');
        if (firstDot <= 0) return null;
        return qualifiedClassName.substring(0, firstDot);
    }

    private static IoxReader createPlainReader(TransferDatasetDescriptor descriptor) throws Exception {
        String lowerName = descriptor.transferFile().getFileName().toString().toLowerCase();
        if (lowerName.endsWith(".itf")) {
            return new ItfReader2(descriptor.transferFile().toFile(), false);
        }
        return Xtf24Reader.createReader(descriptor.transferFile().toFile());
    }

    private java.util.Optional<TypeSystemFacade> compileModels(TransferDatasetDescriptor descriptor) {
        List<String> modelNames = descriptor.declaredModels();
        List<String> dirs = descriptor.modelDirectories();
        if (modelNames.isEmpty()) return java.util.Optional.empty();

        String modelDirs = dirs != null && !dirs.isEmpty()
                ? String.join(";", dirs) : null;
        if (modelDirs == null || modelDirs.isBlank()) return java.util.Optional.empty();

        for (String modelName : modelNames) {
            try {
                IliModelCompileResult result = modelService.compileModel(modelName, modelDirs);
                if (!result.hasErrors() && result.transferDescription() != null) {
                    return java.util.Optional.of(new TypeSystemFacade(result.transferDescription()));
                }
            } catch (Exception ignored) {
            }
        }
        return java.util.Optional.empty();
    }

    private void inspectWithModel(String className, IomObject iom, TypeSystemFacade facade,
                                   Set<String> geometryObservation, Map<String, Integer> refCounts) {
        String scopedPath = findScopedPath(className, facade);
        if (scopedPath == null) return;

        Table table = facade.resolveClass(scopedPath);
        if (table == null) return;

        int refsForObj = 0;
        Iterator<ch.interlis.ili2c.metamodel.Extendable> attrsIt = table.getAttributes();
        while (attrsIt.hasNext()) {
            ch.interlis.ili2c.metamodel.Extendable ext = attrsIt.next();
            if (!(ext instanceof AttributeDef attr)) continue;
            String attrName = attr.getName();
            if (attrName == null) continue;

            ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
            if (type instanceof CoordType) {
                geometryObservation.add("COORD: " + attrName);
            } else if (type instanceof PolylineType) {
                geometryObservation.add("POLYLINE: " + attrName);
            } else if (type instanceof SurfaceOrAreaType) {
                geometryObservation.add("SURFACE/AREA: " + attrName);
            } else if (type instanceof ReferenceType) {
                IomObject refObj = iom.getattrobj(attrName, 0);
                if (refObj != null && refObj.getobjectrefoid() != null) {
                    refsForObj++;
                }
            }
        }
        if (refsForObj > 0) {
            refCounts.merge(className, refsForObj, Integer::sum);
        }
    }

    private List<TransferInventory.ClassStats> buildClassStats(
            Map<String, Long> classCounts,
            java.util.Optional<TypeSystemFacade> facade,
            Set<String> geometryObservation,
            Map<String, Integer> refCounts) {
        List<TransferInventory.ClassStats> result = new ArrayList<>();
        for (var entry : classCounts.entrySet()) {
            String className = entry.getKey();
            long count = entry.getValue();

            String oidType = null;
            List<String> geomAttrs = new ArrayList<>();
            boolean hasRefs = false;

            if (facade.isPresent()) {
                String scopedPath = findScopedPath(className, facade.get());
                if (scopedPath != null) {
                    oidType = facade.get().getOidType(scopedPath);
                    geomAttrs = findGeometryAttributes(scopedPath, facade.get());
                    hasRefs = refCounts.getOrDefault(className, 0) > 0;
                }
            } else {
                for (String obs : geometryObservation) {
                    geomAttrs.add(obs);
                }
                hasRefs = refCounts.getOrDefault(className, 0) > 0;
            }

            result.add(new TransferInventory.ClassStats(
                    className, count, oidType, geomAttrs, hasRefs));
        }
        return result;
    }

    private List<String> findGeometryAttributes(String classPath, TypeSystemFacade facade) {
        List<String> result = new ArrayList<>();
        Table table = facade.resolveClass(classPath);
        if (table == null) return result;

        Iterator<ch.interlis.ili2c.metamodel.Extendable> it = table.getAttributes();
        while (it.hasNext()) {
            ch.interlis.ili2c.metamodel.Extendable ext = it.next();
            if (!(ext instanceof AttributeDef attr)) continue;
            String attrName = attr.getName();
            if (attrName == null) continue;

            ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
            if (type instanceof CoordType) {
                result.add(attrName + " (COORD)");
            } else if (type instanceof PolylineType) {
                result.add(attrName + " (POLYLINE)");
            } else if (type instanceof SurfaceOrAreaType) {
                result.add(attrName + " (SURFACE/AREA)");
            }
        }
        return result;
    }

    private String findScopedPath(String className, TypeSystemFacade facade) {
        TransferDescription td = facade.getTransferDescription();
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
                            if (className.equals(table.getName()) ||
                                    getScoped(table).equals(className)) {
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

}
