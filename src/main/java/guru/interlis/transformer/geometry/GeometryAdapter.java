package guru.interlis.transformer.geometry;

import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import ch.interlis.iom.IomObject;

public interface GeometryAdapter {
    Value normalize(IomObject sourceGeometry, TypeInfo sourceType);

    IomObject denormalize(Value geometry, TypeInfo targetType);
}
