package guru.interlis.transformer.io.shp;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.io.shp.ShapefileOptions.FieldNameStrategy;
import guru.interlis.transformer.io.shp.core.ShapeType;
import guru.interlis.transformer.io.shp.geom.GeometryKind;
import guru.interlis.transformer.io.shp.mapping.ShapefileSchemaBuilder;
import guru.interlis.transformer.io.shp.mapping.ShapefileSchemaBuilder.WriteSchema;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.iom.IomObject;

import java.nio.charset.Charset;
import java.util.Optional;

/**
 * Holds the static Shapefile writer configuration parsed from {@link ShapefileOptions} and lazily
 * builds the {@link WriteSchema} when the output class is known (either from a configured option
 * or from the first {@code ObjectEvent}).
 */
public final class ShapefileWritePlan {

    private final String outputId;
    private final Optional<String> className;
    private final Optional<String> geometryAttribute;
    private final Optional<GeometryKind> geometryKind;
    private final Optional<ShapeType> shapeTypeOverride;
    private final Charset charset;
    private final Optional<String> prjWkt;
    private final FieldNameStrategy fieldNameStrategy;
    private final boolean writeSidecarMapping;
    private final boolean failOnMultipleBaskets;

    private WriteSchema schema;

    public ShapefileWritePlan(
            String outputId,
            Optional<String> className,
            Optional<String> geometryAttribute,
            Optional<GeometryKind> geometryKind,
            Optional<ShapeType> shapeTypeOverride,
            Charset charset,
            Optional<String> prjWkt,
            FieldNameStrategy fieldNameStrategy,
            boolean writeSidecarMapping,
            boolean failOnMultipleBaskets) {
        this.outputId = outputId;
        this.className = className;
        this.geometryAttribute = geometryAttribute;
        this.geometryKind = geometryKind;
        this.shapeTypeOverride = shapeTypeOverride;
        this.charset = charset;
        this.prjWkt = prjWkt;
        this.fieldNameStrategy = fieldNameStrategy;
        this.writeSidecarMapping = writeSidecarMapping;
        this.failOnMultipleBaskets = failOnMultipleBaskets;
    }

    public static ShapefileWritePlan create(String outputId, ShapefileOptions options)
            throws ShapefileMappingException {
        return new ShapefileWritePlan(
                outputId,
                options.className(),
                options.geometryAttribute(),
                options.geometryType(),
                options.shapeTypeOverride(),
                options.dbfCharset(Optional.empty()),
                options.prj(),
                options.fieldNameStrategy(),
                options.writeSidecarMapping(),
                options.failOnMultipleBaskets());
    }

    /**
     * Resolves the {@link WriteSchema} from the effective class (configured option or the first
     * object's tag) when it exists in the model; otherwise, from the first object's attributes.
     * Must be called at most once, before the first object is written.
     */
    public WriteSchema buildSchema(
            TypeSystemFacade typeSystem,
            DiagnosticCollector diagnostics,
            Optional<String> effectiveClassName,
            Optional<IomObject> firstObject)
            throws ShapefileMappingException {

        if (schema != null) {
            return schema;
        }
        ShapefileSchemaBuilder builder = new ShapefileSchemaBuilder(fieldNameStrategy, diagnostics, outputId);

        if (effectiveClassName.isPresent() && typeSystem != null && typeSystem.classExists(effectiveClassName.get())) {
            schema = builder.fromModel(
                    typeSystem, effectiveClassName.get(), geometryAttribute, geometryKind, shapeTypeOverride);
        } else if (firstObject.isPresent()) {
            schema = builder.fromFirstObject(firstObject.get(), geometryAttribute, geometryKind, shapeTypeOverride);
        } else {
            throw new ShapefileMappingException("SHP output '" + outputId
                    + "': cannot determine output schema. Configure option 'class' or write at least one object.");
        }
        return schema;
    }

    public Optional<String> configuredClassName() {
        return className;
    }

    public Optional<String> geometryAttribute() {
        return geometryAttribute;
    }

    public Charset charset() {
        return charset;
    }

    public Optional<String> prjWkt() {
        return prjWkt;
    }

    public boolean writeSidecarMapping() {
        return writeSidecarMapping;
    }

    public boolean failOnMultipleBaskets() {
        return failOnMultipleBaskets;
    }

    public String outputId() {
        return outputId;
    }

    public WriteSchema schema() {
        return schema;
    }
}
