package guru.interlis.transformer.io.shp;

import guru.interlis.transformer.io.shp.core.DbfField;
import guru.interlis.transformer.io.shp.core.ShapeType;
import guru.interlis.transformer.io.shp.geom.GeometryAttributeResolver;
import guru.interlis.transformer.io.shp.geom.GeometryKind;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ShapefileReadPlan {

    private final String className;
    private final String topicName;
    private final String basketId;
    private final Optional<String> oidField;
    private final ResolvedGeometry geometry;
    private final Map<String, String> fieldMapping;
    private final Charset charset;
    private final ShapefileOptions.DeletedRecordPolicy deletedRecordPolicy;
    private final boolean requireShx;

    private ShapefileReadPlan(
            String className,
            String topicName,
            String basketId,
            Optional<String> oidField,
            ResolvedGeometry geometry,
            Map<String, String> fieldMapping,
            Charset charset,
            ShapefileOptions.DeletedRecordPolicy deletedRecordPolicy,
            boolean requireShx) {
        this.className = className;
        this.topicName = topicName;
        this.basketId = basketId;
        this.oidField = oidField;
        this.geometry = geometry;
        this.fieldMapping = fieldMapping;
        this.charset = charset;
        this.deletedRecordPolicy = deletedRecordPolicy;
        this.requireShx = requireShx;
    }

    public record ResolvedGeometry(String attributeName, GeometryKind kind) {}

    public static ShapefileReadPlan create(
            ShapefileOptions options, TypeSystemFacade typeSystem, ShapeType shapeType, String inputId)
            throws ShapefileMappingException {

        GeometryKind supportedKind = shapeType.defaultGeometryKind();

        String className = resolveClassName(options, typeSystem, inputId);

        String topicName = options.topicName().orElseGet(() -> extractTopic(className));

        String basketId = options.basketId();

        Optional<String> oidField = options.oidField().filter(oid -> !oid.isBlank());

        GeometryAttributeResolver geometryResolver = new GeometryAttributeResolver();
        GeometryAttributeResolver.ResolvedGeometryAttribute resolved = geometryResolver.resolveForInput(
                typeSystem, className, supportedKind, options.geometryAttribute(), options.geometryType());
        ResolvedGeometry geometry = new ResolvedGeometry(resolved.attributeName(), resolved.kind());

        Map<String, String> fieldMapping = buildFieldMapping(options, className, typeSystem, inputId);

        Charset charset = options.dbfCharset();

        ShapefileOptions.DeletedRecordPolicy deletedRecordPolicy = options.deletedRecordPolicy();

        boolean requireShx = options.requireShx();

        return new ShapefileReadPlan(
                className,
                topicName,
                basketId,
                oidField,
                geometry,
                fieldMapping,
                charset,
                deletedRecordPolicy,
                requireShx);
    }

    public String className() {
        return className;
    }

    public String topicName() {
        return topicName;
    }

    public String basketId() {
        return basketId;
    }

    public Optional<String> oidField() {
        return oidField;
    }

    public ResolvedGeometry geometry() {
        return geometry;
    }

    public Map<String, String> fieldMapping() {
        return fieldMapping;
    }

    public Charset charset() {
        return charset;
    }

    public ShapefileOptions.DeletedRecordPolicy deletedRecordPolicy() {
        return deletedRecordPolicy;
    }

    public boolean requireShx() {
        return requireShx;
    }

    public int oidFieldIndex(List<DbfField> fields) {
        if (oidField.isEmpty()) {
            return -1;
        }
        String oid = oidField.get();
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name().equalsIgnoreCase(oid)) {
                return i;
            }
        }
        return -1;
    }

    private static String resolveClassName(ShapefileOptions options, TypeSystemFacade typeSystem, String inputId)
            throws ShapefileMappingException {
        Optional<String> configured = options.className();
        if (configured.isPresent()) {
            String name = configured.get();
            if (!typeSystem.classExists(name)) {
                throw new ShapefileMappingException("SHP input '" + inputId + "': class '" + name
                        + "' configured via option 'class' not found in model");
            }
            return name;
        }
        throw new ShapefileMappingException(
                "SHP input '" + inputId
                        + "': option 'class' is required. Set it to the fully qualified source class name, e.g. 'Model.Topic.Class'.");
    }

    private static String extractTopic(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : className;
    }

    private static Map<String, String> buildFieldMapping(
            ShapefileOptions options, String className, TypeSystemFacade typeSystem, String inputId)
            throws ShapefileMappingException {
        Map<String, String> declared = options.columnMappings();
        if (declared.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String attrName = entry.getValue();
            if (!typeSystem.attributeExists(className, attrName)) {
                throw new ShapefileMappingException("SHP input '" + inputId + "': attribute '" + attrName
                        + "' (from option 'column." + entry.getKey() + "') not found in class '" + className + "'");
            }
            resolved.put(entry.getKey(), attrName);
        }
        return Collections.unmodifiableMap(resolved);
    }
}
