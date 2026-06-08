package guru.interlis.transformer.expr;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.Objects;

public record GeometryObjectValue(TypeInfo geometryType, IomObject geometryObject,
                                  CoordValue pointOnSurface) implements Value {

    public GeometryObjectValue {
        geometryType = Objects.requireNonNull(geometryType, "geometryType");
        geometryObject = deepCopy(Objects.requireNonNull(geometryObject, "geometryObject"));
    }

    public GeometryObjectValue(TypeInfo geometryType, IomObject geometryObject) {
        this(geometryType, geometryObject, null);
    }

    @Override
    public IomObject geometryObject() {
        return deepCopy(geometryObject);
    }

    public GeometryObjectValue withPointOnSurface(CoordValue coord) {
        return new GeometryObjectValue(geometryType, geometryObject, coord);
    }

    @Override
    public Object toNative() {
        return geometryObject.toString();
    }

    @Override
    public String asText() {
        return geometryObject.toString();
    }

    private static IomObject deepCopy(IomObject source) {
        return new Iom_jObject(source);
    }
}
