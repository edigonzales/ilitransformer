package guru.interlis.transformer.geometry;

import ch.interlis.iom.IomObject;

public interface GeometryAdapter {
    IomObject normalize(IomObject sourceGeometry);
    IomObject transform(IomObject normalizedGeometry, String operation);
    IomObject denormalize(IomObject normalizedGeometry, String targetType);
}
