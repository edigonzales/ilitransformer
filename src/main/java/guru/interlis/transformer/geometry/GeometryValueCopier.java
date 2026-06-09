package guru.interlis.transformer.geometry;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

public final class GeometryValueCopier {

    public IomObject deepCopy(IomObject geometry) {
        if (geometry == null) {
            return null;
        }
        return new Iom_jObject(geometry);
    }
}
