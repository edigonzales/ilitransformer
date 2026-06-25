package guru.interlis.transformer.io.shp.mapping;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.geom.IoxToJtsGeometry;
import guru.interlis.transformer.io.shp.mapping.ShapefileSchemaBuilder.WriteSchema;

import ch.interlis.iom.IomObject;

import java.util.List;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Splits a single output {@link IomObject} into the JTS geometry written to {@code .shp} and the
 * ordered DBF attribute values written to {@code .dbf}, according to a {@link WriteSchema}.
 *
 * <p>All scalar values are passed to the DBF writer as trimmed strings; the low-level {@code
 * DbfWriter} performs the type-specific formatting and right-justification. A missing or empty
 * scalar is written as a blank field.
 */
public final class IomToDbfMapper {

    private final WriteSchema schema;
    private final List<String> iomAttributes;
    private final IoxToJtsGeometry geometryConverter = new IoxToJtsGeometry();

    public IomToDbfMapper(WriteSchema schema) {
        this.schema = schema;
        this.iomAttributes = schema.iomAttributes();
    }

    public Geometry extractGeometry(IomObject object) throws ShapefileMappingException {
        if (object.getattrvaluecount(schema.geometryAttribute()) == 0) {
            return null;
        }
        IomObject geom = object.getattrobj(schema.geometryAttribute(), 0);
        if (geom == null) {
            return null;
        }
        return geometryConverter.convert(geom);
    }

    public Object[] extractValues(IomObject object) {
        Object[] values = new Object[iomAttributes.size()];
        for (int i = 0; i < iomAttributes.size(); i++) {
            String raw = object.getattrvalue(iomAttributes.get(i));
            values[i] = raw == null ? "" : raw;
        }
        return values;
    }
}
