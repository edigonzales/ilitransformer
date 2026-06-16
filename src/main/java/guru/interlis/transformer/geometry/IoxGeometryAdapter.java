package guru.interlis.transformer.geometry;

import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.GeometryObjectValue;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

public final class IoxGeometryAdapter implements GeometryAdapter {

    private final GeometryValueCopier copier = new GeometryValueCopier();

    @Override
    public Value normalize(IomObject sourceGeometry, TypeInfo sourceType) {
        if (sourceGeometry == null) return NullValue.INSTANCE;
        return switch (sourceType) {
            case COORD -> parseCoord(sourceGeometry);
            case POLYLINE, SURFACE, AREA -> new GeometryObjectValue(sourceType, sourceGeometry);
            default -> new TextValue(sourceGeometry.toString());
        };
    }

    @Override
    public IomObject denormalize(Value geometry, TypeInfo targetType) {
        if (geometry == null || geometry.isNull()) return null;
        if (geometry instanceof GeometryObjectValue gov) {
            if (!isCompatibleGeometryType(gov.geometryType(), targetType)) {
                return null;
            }
            return gov.geometryObject();
        }
        if (geometry instanceof TextValue tv) {
            if (targetType == TypeInfo.COORD) {
                CoordValue parsed = parseCoordText(tv.value());
                return parsed != null ? buildCoord(parsed) : null;
            }
            return null;
        }
        if (geometry instanceof CoordValue cv) {
            return buildCoord(cv);
        }
        return null;
    }

    private boolean isCompatibleGeometryType(TypeInfo sourceType, TypeInfo targetType) {
        if (sourceType == targetType) return true;
        return (sourceType == TypeInfo.SURFACE || sourceType == TypeInfo.AREA)
                && (targetType == TypeInfo.SURFACE || targetType == TypeInfo.AREA);
    }

    // -- COORD ---------------------------------------------------------

    private CoordValue parseCoord(IomObject geom) {
        if (geom == null) return null;
        String c1 = geom.getattrvalue("C1");
        String c2 = geom.getattrvalue("C2");
        if (c1 != null && c2 != null) {
            return new CoordValue(Double.parseDouble(c1), Double.parseDouble(c2));
        }
        return parseCoordText(geom.getattrvalue("value"));
    }

    private CoordValue parseCoordText(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 2) return null;
        return new CoordValue(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }

    private IomObject buildCoord(CoordValue cv) {
        Iom_jObject obj = new Iom_jObject("COORD", null);
        obj.setattrvalue("C1", Double.toString(cv.x()));
        obj.setattrvalue("C2", Double.toString(cv.y()));
        return obj;
    }
}
