package guru.interlis.transformer.model;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Enumeration;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class TypeSystemFacade {

    private final TransferDescription td;

    public TypeSystemFacade(TransferDescription td) {
        this.td = td;
    }

    public record ReferenceInfo(
            String name,
            RoleDef role,
            AssociationDef association,
            String targetClass,
            long minCardinality,
            long maxCardinality,
            boolean attributeReference
    ) {}

    public boolean classExists(String qualifiedPath) {
        return resolveClass(qualifiedPath) != null;
    }

    public Table resolveClass(String qualifiedPath) {
        List<String> parts = splitQualifiedPath(qualifiedPath);
        if (parts.size() == 2) {
            return resolveModelLevelClass(parts.get(0), parts.get(1));
        }
        if (parts.size() < 3) return null;

        IliPath path = IliPath.parse(qualifiedPath);

        Iterator<Model> modelIt = td.iterator();
        while (modelIt.hasNext()) {
            Model model = modelIt.next();
            Iterator<Element> elIt = model.iterator();
            while (elIt.hasNext()) {
                Element el = elIt.next();
                if (el instanceof Topic topic) {
                    if (!topicNameMatches(topic, path.topic())) continue;
                    if (!modelNameMatches(model, path.model())) continue;
                    Iterator<Element> telIt = topic.iterator();
                    while (telIt.hasNext()) {
                        Element tel = telIt.next();
                        if (tel instanceof Table table) {
                            if (table.getName() != null
                                    && table.getName().equals(path.className())) {
                                return table;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private Table resolveModelLevelClass(String modelName, String className) {
        Iterator<Model> modelIt = td.iterator();
        while (modelIt.hasNext()) {
            Model model = modelIt.next();
            if (!modelNameMatches(model, modelName)) continue;
            Iterator<Element> elIt = model.iterator();
            while (elIt.hasNext()) {
                Element el = elIt.next();
                if (el instanceof Table table
                        && table.getName() != null
                        && table.getName().equals(className)) {
                    return table;
                }
            }
        }
        return null;
    }

    public boolean attributeExists(String classPath, String attrName) {
        Table table = resolveClass(classPath);
        if (table == null) return false;
        return findAttribute(table, attrName) != null;
    }

    public AttributeDef findAttribute(Table table, String attrName) {
        AttributeDef found = table.findAttribute(attrName);
        if (found != null) {
            return found;
        }
        Iterator<Extendable> it = table.getAttributes();
        while (it.hasNext()) {
            Extendable ext = it.next();
            if (ext instanceof AttributeDef attr) {
                if (attr.getName() != null && attr.getName().equals(attrName)) {
                    return attr;
                }
            }
        }
        return null;
    }

    public AttributeDef findStructureAttribute(CompositionType structureType, String attrName) {
        Table component = structureType.getComponentType();
        if (component == null) return null;
        return findAttribute(component, attrName);
    }

    public CompositionType resolveStructureType(String qualifiedPath) {
        IliPath path = IliPath.parse(qualifiedPath);
        if (path.length() < 3) return null;

        Iterator<Model> modelIt = td.iterator();
        while (modelIt.hasNext()) {
            Model model = modelIt.next();
            if (!modelNameMatches(model, path.model())) continue;
            Iterator<Element> elIt = model.iterator();
            while (elIt.hasNext()) {
                Element el = elIt.next();
                if (el instanceof Topic topic) {
                    if (!topicNameMatches(topic, path.topic())) continue;
                    CompositionType ct = findStructureInTopic(topic, path.className());
                    if (ct != null) return ct;
                }
                // Also search model-level elements
                if (el instanceof CompositionType ct) {
                    if (ct.getName() != null && ct.getName().equals(path.className())) {
                        return ct;
                    }
                }
            }
            // Also try searching all topics for the structure
            Iterator<Element> modelElIt = model.iterator();
            while (modelElIt.hasNext()) {
                Element element = modelElIt.next();
                if (element instanceof Topic topic) {
                    CompositionType ct = findStructureInTopic(topic, path.className());
                    if (ct != null) {
                        // Verify model name matches (topic container)
                        Container container = topic.getContainer();
                        if (container instanceof Model m && modelNameMatches(m, path.model())) {
                            return ct;
                        }
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private CompositionType findStructureInTopic(Topic topic, String structureName) {
        Iterator<Element> it = topic.iterator();
        while (it.hasNext()) {
            Element el = it.next();
            if (el instanceof CompositionType ct) {
                if (ct.getName() != null && ct.getName().equals(structureName)) {
                    return ct;
                }
            }
        }
        return null;
    }

    public boolean roleExists(String classPath, String roleName) {
        return resolveReference(classPath, roleName) != null;
    }

    public RoleDef resolveRole(String classPath, String roleName) {
        ReferenceInfo ref = resolveReference(classPath, roleName);
        return ref != null ? ref.role() : null;
    }

    public ReferenceInfo resolveReference(String classPath, String roleName) {
        Table table = resolveClass(classPath);
        if (table == null) return null;
        AttributeDef attr = findAttribute(table, roleName);
        if (attr != null && attr.getDomain() instanceof ReferenceType refType) {
            Cardinality card = attr.getCardinality();
            return new ReferenceInfo(roleName, null, null,
                    className(refType.getReferred()),
                    card != null ? card.getMinimum() : 0,
                    card != null ? card.getMaximum() : 1,
                    true);
        }

        ReferenceInfo ownerSide = resolveOwnerSideAssociationRole(table, roleName);
        if (ownerSide != null) return ownerSide;

        return resolveLegacyTargetForRole(table, roleName);
    }

    private ReferenceInfo resolveOwnerSideAssociationRole(Table ownerClass, String roleName) {
        Container container = ownerClass.getContainer();
        if (!(container instanceof Topic topic)) return null;
        Iterator<Element> it = topic.iterator();
        while (it.hasNext()) {
            Element el = it.next();
            if (!(el instanceof AssociationDef assoc)) continue;
            for (RoleDef role : assoc.getRoles()) {
                if (role.getName() == null || !role.getName().equals(roleName)) continue;
                if (!hasOppositeDestination(assoc, role, ownerClass)) continue;
                Cardinality card = role.getCardinality();
                return new ReferenceInfo(roleName, role, assoc,
                        className(role.getDestination()),
                        card != null ? card.getMinimum() : 0,
                        card != null ? card.getMaximum() : Cardinality.UNBOUND,
                        false);
            }
        }
        return null;
    }

    private ReferenceInfo resolveLegacyTargetForRole(Table table, String roleName) {
        @SuppressWarnings("unchecked")
        Iterator<RoleDef> it = table.getTargetForRoles();
        if (it == null) return null;
        while (it.hasNext()) {
            RoleDef role = it.next();
            if (role.getName() != null && role.getName().equals(roleName)) {
                AssociationDef association = role.getContainer() instanceof AssociationDef assoc ? assoc : null;
                String targetClass = null;
                if (association != null) {
                    for (RoleDef other : association.getRoles()) {
                        if (other == role) continue;
                        targetClass = className(other.getDestination());
                        if (targetClass != null) break;
                    }
                }
                if (targetClass == null) {
                    targetClass = className(role.getDestination());
                }
                Cardinality card = role.getCardinality();
                return new ReferenceInfo(roleName, role, association, targetClass,
                        card != null ? card.getMinimum() : 0,
                        card != null ? card.getMaximum() : Cardinality.UNBOUND,
                        false);
            }
        }
        return null;
    }

    public String getRoleTargetClass(String classPath, String roleName) {
        ReferenceInfo ref = resolveReference(classPath, roleName);
        return ref != null ? ref.targetClass() : null;
    }

    public long getRoleCardinalityMin(String classPath, String roleName) {
        ReferenceInfo ref = resolveReference(classPath, roleName);
        return ref != null ? ref.minCardinality() : 0;
    }

    public long getRoleCardinalityMax(String classPath, String roleName) {
        ReferenceInfo ref = resolveReference(classPath, roleName);
        return ref != null ? ref.maxCardinality() : 0;
    }

    public String getRoleAssociation(String classPath, String roleName) {
        ReferenceInfo ref = resolveReference(classPath, roleName);
        return ref != null && ref.association() != null ? ref.association().getName() : null;
    }

    private boolean hasOppositeDestination(AssociationDef assoc, RoleDef role, Table ownerClass) {
        for (RoleDef other : assoc.getRoles()) {
            if (other == role) continue;
            if (sameClass(other.getDestination(), ownerClass)) return true;
        }
        return false;
    }

    private static boolean sameClass(AbstractClassDef candidate, Table table) {
        if (candidate == table) return true;
        if (candidate instanceof Table candidateTable) {
            return getScopedName(candidateTable).equals(getScopedName(table));
        }
        return candidate != null && candidate.getName() != null
                && candidate.getName().equals(table.getName());
    }

    private static String className(AbstractClassDef classDef) {
        if (classDef instanceof Table table) {
            return getScopedName(table);
        }
        return classDef != null ? classDef.getName() : null;
    }

    public static String getScopedName(Table table) {
        Container container = table.getContainer();
        if (container instanceof Topic topic) {
            Container modelContainer = topic.getContainer();
            if (modelContainer instanceof Model model) {
                return model.getName() + "." + topic.getName() + "." + table.getName();
            }
        }
        if (container instanceof Model model) {
            return model.getName() + "." + table.getName();
        }
        return table.getName();
    }

    public String getAttributeTypeString(String classPath, String attrName) {
        AttributeDef attr = resolveAttribute(classPath, attrName);
        if (attr == null) return null;
        ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
        return type != null ? type.toString() : null;
    }

    public String resolveEnumName(AttributeDef targetAttr, String bareName) {
        if (targetAttr == null || bareName == null) return bareName;
        Type type = Type.findReal(targetAttr.getDomain());
        if (!(type instanceof EnumerationType enumType)) return bareName;
        // Try exact match first
        for (String v : enumType.getValues()) {
            if (v.equals(bareName)) return v;
        }
        // Try suffix match: bareName like "Baurecht" matches "SelbstRecht.Baurecht"
        String suffix = "." + bareName;
        for (String v : enumType.getValues()) {
            if (v.endsWith(suffix)) return v;
        }
        return bareName;
    }

    public boolean isMandatory(String classPath, String attrName) {
        AttributeDef attr = resolveAttribute(classPath, attrName);
        if (attr == null) return false;
        var card = attr.getCardinality();
        return card != null && card.getMinimum() > 0;
    }

    public List<ModelInventory> listAllModels() {
        IliModelService service = new IliModelService();
        return List.of(service.buildInventory(td, null));
    }

    public TransferDescription getTransferDescription() {
        return td;
    }

    public String getOidType(String classPath) {
        Topic topic = resolveTopic(classPath);
        if (topic == null) return null;
        return formatOidDomainType(topic.getOid());
    }

    public String getBasketOidType(String classPath) {
        Topic topic = resolveTopic(classPath);
        if (topic == null) return null;
        return formatOidDomainType(topic.getBasketOid());
    }

    private Topic resolveTopic(String classPath) {
        IliPath path = IliPath.parse(classPath);
        if (path.length() < 2) return null;
        for (Iterator<Model> modelIt = td.iterator(); modelIt.hasNext(); ) {
            Model model = modelIt.next();
            if (!modelNameMatches(model, path.model())) continue;
            Iterator<Element> elIt = model.iterator();
            while (elIt.hasNext()) {
                Element el = elIt.next();
                if (el instanceof Topic topic) {
                    if (topicNameMatches(topic, path.topic())) {
                        return topic;
                    }
                }
            }
        }
        return null;
    }

    private static String formatOidDomainType(Domain oidDomain) {
        if (oidDomain == null) return "STANDARDOID";
        if (oidDomain.getName() != null && !oidDomain.getName().isBlank()) {
            return oidDomain.getName();
        }
        ch.interlis.ili2c.metamodel.Type type = oidDomain.getType();
        if (type != null) {
            String className = type.getClass().getSimpleName();
            return switch (className) {
                case "TextOIDType" -> "TEXT OID";
                case "NumericOIDType" -> "NUMERIC OID";
                case "AnyOIDType" -> "UUIDOID";
                case "NoOid" -> "NO OID";
                default -> className;
            };
        }
        return "STANDARDOID";
    }

    private AttributeDef resolveAttribute(String classPath, String attrName) {
        Table table = resolveClass(classPath);
        if (table == null) return null;
        return findAttribute(table, attrName);
    }

    private static List<String> splitQualifiedPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        String[] segments = path.trim().split("\\.");
        List<String> parts = new ArrayList<>(segments.length);
        for (String segment : segments) {
            String cleaned = segment.trim();
            if (cleaned.isEmpty()) {
                return List.of();
            }
            parts.add(cleaned);
        }
        return parts;
    }

    private static boolean modelNameMatches(Model model, String name) {
        String modelName = model.getName();
        return modelName != null && modelName.equals(name);
    }

    private static boolean topicNameMatches(Topic topic, String name) {
        String topicName = topic.getName();
        return topicName != null && topicName.equals(name);
    }
}
