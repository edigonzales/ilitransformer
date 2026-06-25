package guru.interlis.transformer.io.shp.geom;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
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

    /**
     * Resolves the single geometry attribute that the Shapefile writer will encode into the {@code
     * .shp} geometry. Unlike {@link #resolveForInput}, the geometry kind is derived from the model
     * itself (the INTERLIS 2.4 built-in geometry type of the attribute), not from a Shapefile shape
     * type.
     */
    public ResolvedGeometryAttribute resolveForOutput(
            TypeSystemFacade typeSystem,
            String className,
            Optional<String> configuredAttribute,
            Optional<GeometryKind> configuredKind)
            throws ShapefileMappingException {

        Table table = typeSystem.resolveClass(className);
        if (table == null) {
            throw new ShapefileMappingException("Output class '" + className + "' not found in model");
        }

        if (configuredAttribute.isPresent()) {
            String attrName = configuredAttribute.get();
            AttributeDef attr = typeSystem.findAttribute(table, attrName);
            if (attr == null) {
                throw new ShapefileMappingException(
                        "geometryAttribute '" + attrName + "' not found in output class '" + className + "'");
            }
            GeometryKind kind = configuredKind.orElseGet(
                    () -> geometryKindOf(Type.findReal(attr.getDomain())).orElse(null));
            if (kind == null) {
                throw new ShapefileMappingException("geometryAttribute '" + attrName + "' in output class '" + className
                        + "' is not an INTERLIS geometry type (coord, polyline, surface/area or their multi types)."
                        + " Set option geometryType to override.");
            }
            return new ResolvedGeometryAttribute(attrName, kind);
        }

        List<String> candidates = findOutputGeometryAttributeNames(table);
        if (candidates.size() == 1) {
            String attrName = candidates.get(0);
            AttributeDef attr = typeSystem.findAttribute(table, attrName);
            GeometryKind kind = configuredKind.orElseGet(
                    () -> geometryKindOf(Type.findReal(attr.getDomain())).orElse(GeometryKind.COORD));
            return new ResolvedGeometryAttribute(attrName, kind);
        }
        if (candidates.isEmpty()) {
            throw new ShapefileMappingException("Output class '" + className + "' has no geometry attributes. "
                    + "Configure option geometryAttribute.");
        }
        throw new ShapefileMappingException("Output class '" + className + "' has " + candidates.size()
                + " geometry attributes. Configure option geometryAttribute to select one of: " + candidates);
    }

    /** Precise geometry-attribute scan for the writer: only true INTERLIS geometry domains qualify. */
    private static List<String> findOutputGeometryAttributeNames(Table table) {
        List<String> result = new ArrayList<>();
        Iterator<Extendable> it = table.getAttributes();
        while (it.hasNext()) {
            Extendable ext = it.next();
            if (ext instanceof AttributeDef attr && attr.getName() != null) {
                if (geometryKindOf(Type.findReal(attr.getDomain())).isPresent()) {
                    result.add(attr.getName());
                }
            }
        }
        return result;
    }

    /**
     * Maps an INTERLIS attribute domain to the writer's coarse {@link GeometryKind}. {@code COORD}
     * maps to {@link GeometryKind#COORD}, {@code MULTICOORD} to {@link GeometryKind#MULTICOORD},
     * polylines to {@link GeometryKind#POLYLINE} and surface/area domains to {@link
     * GeometryKind#SURFACE}. Returns empty for non-geometry domains.
     */
    public static Optional<GeometryKind> geometryKindOf(Type domain) {
        if (domain == null) {
            return Optional.empty();
        }
        if (domain instanceof MultiCoordType) {
            return Optional.of(GeometryKind.MULTICOORD);
        }
        if (domain instanceof CoordType) {
            return Optional.of(GeometryKind.COORD);
        }
        if (domain instanceof PolylineType || domain instanceof MultiPolylineType) {
            return Optional.of(GeometryKind.POLYLINE);
        }
        if (domain instanceof SurfaceOrAreaType || domain instanceof MultiSurfaceType) {
            return Optional.of(GeometryKind.SURFACE);
        }
        String simpleName = domain.getClass().getSimpleName();
        if (simpleName.contains("MultiCoord")) {
            return Optional.of(GeometryKind.MULTICOORD);
        }
        if (simpleName.contains("Coord")) {
            return Optional.of(GeometryKind.COORD);
        }
        if (simpleName.contains("Polyline") || simpleName.contains("PolyLine")) {
            return Optional.of(GeometryKind.POLYLINE);
        }
        if (simpleName.contains("Surface") || simpleName.contains("Area")) {
            return Optional.of(GeometryKind.SURFACE);
        }
        return Optional.empty();
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
            case MULTICOORD -> "multicoord";
            case POLYLINE -> "polyline";
            case SURFACE -> "surface";
        };
    }
}
