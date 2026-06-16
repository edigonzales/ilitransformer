package guru.interlis.transformer.geometry;

import guru.interlis.transformer.mapping.plan.TypeInfo;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Type;

import java.util.ArrayList;
import java.util.List;

public final class GeometryCompatibilityService {

    public GeometryCompatibility check(
            TypeInfo sourceType, TypeInfo targetType, AttributeDef sourceAttribute, AttributeDef targetAttribute) {

        List<String> issues = new ArrayList<>();

        if (sourceType != targetType) {
            if ((sourceType == TypeInfo.SURFACE || sourceType == TypeInfo.AREA)
                    && (targetType == TypeInfo.SURFACE || targetType == TypeInfo.AREA)) {
                return GeometryCompatibility.success();
            }
            issues.add("Geometry type mismatch: " + sourceType + " -> " + targetType);
            return GeometryCompatibility.failure(issues);
        }

        switch (sourceType) {
            case COORD -> checkCoordDimension(sourceAttribute, targetAttribute, issues);
            case POLYLINE -> checkPolylineCompatibility(issues);
        }

        if (issues.isEmpty()) {
            return GeometryCompatibility.success();
        }
        return GeometryCompatibility.failure(issues);
    }

    private void checkCoordDimension(AttributeDef source, AttributeDef target, List<String> issues) {
        boolean source3D = is3DCoord(source);
        boolean target3D = is3DCoord(target);
        if (source3D && !target3D) {
            issues.add("3D COORD cannot be mapped to 2D COORD without explicit dimension reduction");
        }
    }

    private void checkPolylineCompatibility(List<String> issues) {}

    private boolean is3DCoord(AttributeDef attribute) {
        if (attribute == null) {
            return false;
        }
        Type real = Type.findReal(attribute.getDomain());
        if (real instanceof CoordType coordType) {
            ch.interlis.ili2c.metamodel.NumericalType[] dimensions = coordType.getDimensions();
            return dimensions != null && dimensions.length >= 3;
        }
        return false;
    }
}
