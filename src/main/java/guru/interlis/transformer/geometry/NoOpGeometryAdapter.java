package guru.interlis.transformer.geometry;

import ch.interlis.iom.IomObject;

public final class NoOpGeometryAdapter implements GeometryAdapter {
    @Override
    public IomObject normalize(IomObject sourceGeometry) {
        return sourceGeometry;
    }

    @Override
    public IomObject transform(IomObject normalizedGeometry, String operation) {
        return normalizedGeometry;
    }

    @Override
    public IomObject denormalize(IomObject normalizedGeometry, String targetType) {
        return normalizedGeometry;
    }
}
