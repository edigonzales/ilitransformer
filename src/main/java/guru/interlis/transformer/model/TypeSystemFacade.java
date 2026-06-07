package guru.interlis.transformer.model;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class TypeSystemFacade {

    private final TransferDescription td;

    public TypeSystemFacade(TransferDescription td) {
        this.td = td;
    }

    public boolean classExists(String qualifiedPath) {
        return resolveClass(qualifiedPath) != null;
    }

    public Table resolveClass(String qualifiedPath) {
        IliPath path = IliPath.parse(qualifiedPath);
        if (path.length() < 3) return null;

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

    public boolean attributeExists(String classPath, String attrName) {
        Table table = resolveClass(classPath);
        if (table == null) return false;
        return findAttribute(table, attrName) != null;
    }

    public AttributeDef findAttribute(Table table, String attrName) {
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
        return resolveRole(classPath, roleName) != null;
    }

    public RoleDef resolveRole(String classPath, String roleName) {
        Table table = resolveClass(classPath);
        if (table == null) return null;
        @SuppressWarnings("unchecked")
        Iterator<RoleDef> it = table.getTargetForRoles();
        if (it == null) return null;
        while (it.hasNext()) {
            RoleDef role = it.next();
            if (role.getName() != null && role.getName().equals(roleName)) {
                return role;
            }
        }
        return null;
    }

    public String getRoleTargetClass(String classPath, String roleName) {
        RoleDef role = resolveRole(classPath, roleName);
        if (role == null) return null;
        Container container = role.getContainer();
        if (container instanceof AssociationDef assoc) {
            for (RoleDef other : assoc.getRoles()) {
                if (other == role) continue;
                AbstractClassDef dest = other.getDestination();
                if (dest instanceof Table table) {
                    return getScopedName(table);
                }
                if (dest != null && dest.getName() != null) {
                    return dest.getName();
                }
            }
        }
        AbstractClassDef dest = role.getDestination();
        if (dest instanceof Table table) {
            return getScopedName(table);
        }
        if (dest != null && dest.getName() != null) {
            return dest.getName();
        }
        return null;
    }

    public long getRoleCardinalityMin(String classPath, String roleName) {
        RoleDef role = resolveRole(classPath, roleName);
        if (role == null) return 0;
        Cardinality card = role.getCardinality();
        return card != null ? card.getMinimum() : 0;
    }

    public long getRoleCardinalityMax(String classPath, String roleName) {
        RoleDef role = resolveRole(classPath, roleName);
        if (role == null) return 0;
        Cardinality card = role.getCardinality();
        return card != null ? card.getMaximum() : Cardinality.UNBOUND;
    }

    public String getRoleAssociation(String classPath, String roleName) {
        RoleDef role = resolveRole(classPath, roleName);
        if (role == null) return null;
        Container container = role.getContainer();
        if (container instanceof AssociationDef assoc) {
            return assoc.getName();
        }
        return null;
    }

    public static String getScopedName(Table table) {
        Container container = table.getContainer();
        if (container instanceof Topic topic) {
            Container modelContainer = topic.getContainer();
            if (modelContainer instanceof Model model) {
                return model.getName() + "." + topic.getName() + "." + table.getName();
            }
        }
        return table.getName();
    }

    public String getAttributeTypeString(String classPath, String attrName) {
        AttributeDef attr = resolveAttribute(classPath, attrName);
        if (attr == null) return null;
        ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
        return type != null ? type.toString() : null;
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

    private static boolean modelNameMatches(Model model, String name) {
        String modelName = model.getName();
        return modelName != null && modelName.equals(name);
    }

    private static boolean topicNameMatches(Topic topic, String name) {
        String topicName = topic.getName();
        return topicName != null && topicName.equals(name);
    }
}
