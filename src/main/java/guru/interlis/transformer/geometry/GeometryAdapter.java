package guru.interlis.transformer.geometry;

import ch.interlis.iom.IomObject;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

public interface GeometryAdapter {
    Value normalize(IomObject sourceGeometry, TypeInfo sourceType);
    IomObject denormalize(Value geometry, TypeInfo targetType);
    Value transform(Value geometry, GeometryOperation operation);

    enum GeometryOperation {
        PASSTHROUGH
    }
}
