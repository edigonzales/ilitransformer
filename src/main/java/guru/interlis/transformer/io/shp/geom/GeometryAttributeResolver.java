package guru.interlis.transformer.io.shp.geom;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class GeometryAttributeResolver {

    public record ResolvedGeometryAttribute(String attributeName, GeometryKind kind) {}

    public ResolvedGeometryAttribute resolveForInput(
            TypeSystemFacade typeSystem,
            String className,
            GeometryKind supportedKind,
            Optional<String> configuredAttribute,
            Optional<GeometryKind> configuredKind)
            throws ShapefileMappingException {

        Table table = typeSystem.resolveClass(className);
        if (table == null) {
            throw new ShapefileMappingException("Source class '" + className + "' not found in model");
        }

        GeometryKind kind = configuredKind.isPresent() ? configuredKind.get() : supportedKind;
        if (kind != supportedKind && kind != GeometryKind.COORD) {
            throw new ShapefileMappingException(
                    "geometryType '" + kindToOptionValue(kind) + "' does not match the shapefile shape type '"
                            + supportedKind + "'. Use 'coord', 'polyline' or 'surface'.");
        }

        if (configuredAttribute.isPresent()) {
            String attrName = configuredAttribute.get();
            AttributeDef attr = typeSystem.findAttribute(table, attrName);
            if (attr == null) {
                throw new ShapefileMappingException(
                        "geometryAttribute '" + attrName + "' not found in class '" + className + "'");
            }
            return new ResolvedGeometryAttribute(attrName, kind);
        }

        List<String> candidates = findGeometryAttributeNames(table);

        if (candidates.size() == 1) {
            return new ResolvedGeometryAttribute(candidates.get(0), kind);
        }

        if (candidates.isEmpty()) {
            throw new ShapefileMappingException("Source class '" + className + "' has no geometry attributes. "
                    + "Configure option geometryAttribute.");
        }

        throw new ShapefileMappingException("Source class '" + className + "' has " + candidates.size()
                + " geometry attributes. Configure option geometryAttribute to select one of: " + candidates);
    }

    private static List<String> findGeometryAttributeNames(Table table) {
        List<String> result = new ArrayList<>();
        Iterator<Extendable> it = table.getAttributes();
        while (it.hasNext()) {
            Extendable ext = it.next();
            if (ext instanceof AttributeDef attr) {
                String name = attr.getName();
                if (name == null) continue;
                Type domain = Type.findReal(attr.getDomain());
                if (domain instanceof CompositionType) {
                    result.add(name);
                } else if (isCoordLike(domain)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private static boolean isCoordLike(Type domain) {
        if (domain == null) return false;
        String name = domain.getClass().getSimpleName();
        return name.contains("Coord") || name.contains("COORD") || name.contains("Coordinate");
    }

    private static String kindToOptionValue(GeometryKind kind) {
        return switch (kind) {
            case COORD -> "coord";
            case POLYLINE -> "polyline";
            case SURFACE -> "surface";
        };
    }
}
