package guru.interlis.transformer.io.shp.mapping;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.ShapefileOptions.FieldNameStrategy;
import guru.interlis.transformer.io.shp.core.DbfField;
import guru.interlis.transformer.io.shp.core.DbfFieldType;
import guru.interlis.transformer.io.shp.core.ShapeType;
import guru.interlis.transformer.io.shp.geom.GeometryAttributeResolver;
import guru.interlis.transformer.io.shp.geom.GeometryKind;
import guru.interlis.transformer.io.shp.mapping.DbfNameMapper.DbfNameMapping;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.PrecisionDecimal;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.iom.IomObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Builds the {@link guru.interlis.transformer.io.shp.core.ShapefileSchema} for the writer from the
 * INTERLIS output class (preferred) or, as a fallback when the class cannot be resolved in the
 * model, from the first {@link IomObject} of the stream.
 *
 * <p>Exactly one geometry attribute becomes the {@code .shp} geometry. All remaining scalar
 * attributes become DBF fields. Non-scalar attributes (structures, references/roles, additional
 * geometry attributes, unmappable domains) are skipped and reported as a {@code WARNING}
 * diagnostic; they are never silently dropped.
 */
public final class ShapefileSchemaBuilder {

    private static final int DBF_CHAR_MAX = 254;
    private static final int DBF_NUMERIC_MAX = 18;
    private static final int DEFAULT_CHAR_LEN = 254;

    private final FieldNameStrategy fieldNameStrategy;
    private final DiagnosticCollector diagnostics;
    private final String outputId;

    public ShapefileSchemaBuilder(
            FieldNameStrategy fieldNameStrategy, DiagnosticCollector diagnostics, String outputId) {
        this.fieldNameStrategy = fieldNameStrategy;
        this.diagnostics = diagnostics;
        this.outputId = outputId;
    }

    /** A geometry-bearing field of the schema: its DBF descriptor and the originating IOX attribute. */
    public record WriteField(String iomAttribute, DbfField dbfField) {}

    public record WriteSchema(
            ShapeType shapeType, String geometryAttribute, List<WriteField> fields, DbfNameMapping nameMapping) {

        public List<DbfField> dbfFields() {
            return fields.stream().map(WriteField::dbfField).toList();
        }

        public List<String> iomAttributes() {
            return fields.stream().map(WriteField::iomAttribute).toList();
        }
    }

    /** Builds the schema from the model class. */
    public WriteSchema fromModel(
            TypeSystemFacade typeSystem,
            String className,
            Optional<String> configuredGeometryAttribute,
            Optional<GeometryKind> configuredKind,
            Optional<ShapeType> shapeTypeOverride)
            throws ShapefileMappingException {

        Table table = typeSystem.resolveClass(className);
        if (table == null) {
            throw new ShapefileMappingException(
                    "SHP output '" + outputId + "': output class '" + className + "' not found in model");
        }

        GeometryAttributeResolver resolver = new GeometryAttributeResolver();
        GeometryAttributeResolver.ResolvedGeometryAttribute geom =
                resolver.resolveForOutput(typeSystem, className, configuredGeometryAttribute, configuredKind);

        ShapeType shapeType = shapeTypeOverride.orElseGet(() -> shapeTypeFor(geom.kind()));

        List<String> iomAttrs = new ArrayList<>();
        List<DbfFieldSpec> pendingFields = new ArrayList<>();

        Iterator<Extendable> it = table.getAttributes();
        while (it.hasNext()) {
            Extendable ext = it.next();
            if (!(ext instanceof AttributeDef attr) || attr.getName() == null) {
                continue;
            }
            String attrName = attr.getName();
            if (attrName.equals(geom.attributeName())) {
                continue;
            }
            Type domain = Type.findReal(attr.getDomain());
            DbfFieldSpec spec = mapScalarDomain(domain);
            if (spec == null) {
                skip(attrName, describeDomain(domain));
                continue;
            }
            iomAttrs.add(attrName);
            pendingFields.add(spec);
        }

        return assemble(shapeType, geom.attributeName(), iomAttrs, pendingFields);
    }

    /** Fallback: builds the schema from the first object; the geometry attribute must be configured. */
    public WriteSchema fromFirstObject(
            IomObject sample,
            Optional<String> configuredGeometryAttribute,
            Optional<GeometryKind> configuredKind,
            Optional<ShapeType> shapeTypeOverride)
            throws ShapefileMappingException {

        if (configuredGeometryAttribute.isEmpty()) {
            throw new ShapefileMappingException("SHP output '" + outputId
                    + "': option 'geometryAttribute' is required when the output class cannot be resolved in a model.");
        }
        String geometryAttribute = configuredGeometryAttribute.get();

        ShapeType shapeType =
                shapeTypeOverride.orElseGet(() -> shapeTypeFor(configuredKind.orElse(GeometryKind.COORD)));
        if (shapeTypeOverride.isEmpty() && configuredKind.isEmpty()) {
            // Without a model and without an explicit shapeType/geometryType we cannot guess reliably.
            throw new ShapefileMappingException("SHP output '" + outputId + "': option 'shapeType' (or 'geometryType') "
                    + "is required when the output class cannot be resolved in a model.");
        }

        List<String> iomAttrs = new ArrayList<>();
        List<DbfFieldSpec> pendingFields = new ArrayList<>();
        for (int i = 0; i < sample.getattrcount(); i++) {
            String attrName = sample.getattrname(i);
            if (attrName == null || attrName.equals(geometryAttribute)) {
                continue;
            }
            boolean hasScalar = sample.getattrvalue(attrName) != null;
            boolean hasObject = sample.getattrvaluecount(attrName) > 0 && sample.getattrobj(attrName, 0) != null;
            if (!hasScalar || hasObject) {
                skip(attrName, "non-scalar attribute");
                continue;
            }
            iomAttrs.add(attrName);
            pendingFields.add(new DbfFieldSpec(DbfFieldType.CHARACTER, DEFAULT_CHAR_LEN, 0));
        }

        return assemble(shapeType, geometryAttribute, iomAttrs, pendingFields);
    }

    private WriteSchema assemble(
            ShapeType shapeType, String geometryAttribute, List<String> iomAttrs, List<DbfFieldSpec> pendingFields)
            throws ShapefileMappingException {

        DbfNameMapping mapping = DbfNameMapper.create(iomAttrs, fieldNameStrategy);
        for (String warning : mapping.warnings()) {
            warn(DiagnosticCode.IO_SHP_FIELD_NAME_MAPPED, "SHP output '" + outputId + "': " + warning);
        }

        List<WriteField> fields = new ArrayList<>();
        for (int i = 0; i < iomAttrs.size(); i++) {
            String iomAttr = iomAttrs.get(i);
            String dbfName = mapping.attributeToDbf().get(iomAttr);
            DbfFieldSpec spec = pendingFields.get(i);
            fields.add(new WriteField(iomAttr, new DbfField(dbfName, spec.type(), spec.length(), spec.decimals())));
        }
        return new WriteSchema(shapeType, geometryAttribute, fields, mapping);
    }

    private static ShapeType shapeTypeFor(GeometryKind kind) {
        return switch (kind) {
            case COORD -> ShapeType.POINT;
            case MULTICOORD -> ShapeType.MULTIPOINT;
            case POLYLINE -> ShapeType.POLYLINE;
            case SURFACE -> ShapeType.POLYGON;
        };
    }

    private DbfFieldSpec mapScalarDomain(Type domain) {
        if (domain == null) {
            return null;
        }
        if (GeometryAttributeResolver.geometryKindOf(domain).isPresent()) {
            return null; // an additional geometry attribute
        }
        if (domain instanceof CompositionType || domain instanceof ReferenceType) {
            return null;
        }
        if (domain instanceof TextType text) {
            int max = text.getMaxLength();
            int len = max > 0 ? Math.min(max, DBF_CHAR_MAX) : DEFAULT_CHAR_LEN;
            return new DbfFieldSpec(DbfFieldType.CHARACTER, len, 0);
        }
        if (domain instanceof EnumerationType enumeration) {
            if (isBooleanEnum(enumeration)) {
                return new DbfFieldSpec(DbfFieldType.LOGICAL, 1, 0);
            }
            return new DbfFieldSpec(DbfFieldType.CHARACTER, enumWidth(enumeration), 0);
        }
        if (domain instanceof NumericType numeric) {
            return numericSpec(numeric);
        }
        if (domain instanceof FormattedType formatted) {
            return formattedSpec(formatted);
        }
        return null;
    }

    /** INTERLIS {@code BOOLEAN} is an enumeration of {@code true}/{@code false}; map it to DBF Logical. */
    private static boolean isBooleanEnum(EnumerationType enumeration) {
        List<String> values = enumeration.getValues();
        if (values == null || values.size() != 2) {
            return false;
        }
        boolean hasTrue = false;
        boolean hasFalse = false;
        for (String value : values) {
            if ("true".equalsIgnoreCase(value)) {
                hasTrue = true;
            } else if ("false".equalsIgnoreCase(value)) {
                hasFalse = true;
            }
        }
        return hasTrue && hasFalse;
    }

    /** Width derived from the longest enumeration value (clamped to the DBF character limit). */
    private static int enumWidth(EnumerationType enumeration) {
        int max = 1;
        List<String> values = enumeration.getValues();
        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    max = Math.max(max, value.length());
                }
            }
        }
        return Math.min(max, DBF_CHAR_MAX);
    }

    private DbfFieldSpec numericSpec(NumericType numeric) {
        int decimals = 0;
        int intDigits = 1;
        PrecisionDecimal max = numeric.getMaximum();
        PrecisionDecimal min = numeric.getMinimum();
        boolean negative = false;
        if (max != null) {
            decimals = Math.max(decimals, Math.max(0, max.getAccuracy()));
            intDigits = Math.max(intDigits, integerDigits(max.doubleValue()));
        }
        if (min != null) {
            decimals = Math.max(decimals, Math.max(0, min.getAccuracy()));
            intDigits = Math.max(intDigits, integerDigits(min.doubleValue()));
            negative = min.doubleValue() < 0;
        }
        int width = intDigits + (decimals > 0 ? decimals + 1 : 0) + (negative ? 1 : 0);
        if (width < 1) {
            width = 1;
        }
        if (width > DBF_NUMERIC_MAX) {
            width = DBF_NUMERIC_MAX;
            if (decimals > width - 2) {
                decimals = Math.max(0, width - 2);
            }
        }
        return new DbfFieldSpec(DbfFieldType.NUMERIC, width, decimals);
    }

    private DbfFieldSpec formattedSpec(FormattedType formatted) {
        if (isDateOnly(formatted)) {
            return new DbfFieldSpec(DbfFieldType.DATE, 8, 0);
        }
        // Date-time, time and other formatted types stay textual; width is taken from the declared
        // maximum string (e.g. an ISO date-time) and only falls back to the default when absent.
        return new DbfFieldSpec(DbfFieldType.CHARACTER, formattedWidth(formatted), 0);
    }

    /**
     * A pure date (DBF {@code D}, {@code yyyyMMdd}). INTERLIS {@code XMLDate} is a {@link
     * FormattedType} based on the built-in {@code GregorianDate} structure; matching the base
     * structure also covers user-defined date domains and never misclassifies {@code XMLDateTime}.
     */
    private static boolean isDateOnly(FormattedType formatted) {
        if (formatted.getBaseStruct() != null
                && "GregorianDate".equals(formatted.getBaseStruct().getName())) {
            return true;
        }
        return formatted.getDefinedBaseDomain() != null
                && "XMLDate".equals(formatted.getDefinedBaseDomain().getName());
    }

    /** Width of a formatted field from the declared maximum string, else the DBF character default. */
    private static int formattedWidth(FormattedType formatted) {
        String max = formatted.getMaximum();
        if (max != null && !max.isBlank()) {
            return Math.min(Math.max(max.trim().length(), 1), DBF_CHAR_MAX);
        }
        return DEFAULT_CHAR_LEN;
    }

    private static int integerDigits(double value) {
        long abs = (long) Math.floor(Math.abs(value));
        return Long.toString(abs).length();
    }

    private static String describeDomain(Type domain) {
        if (domain == null) {
            return "unknown domain";
        }
        if (GeometryAttributeResolver.geometryKindOf(domain).isPresent()) {
            return "additional geometry attribute";
        }
        if (domain instanceof CompositionType) {
            return "structure attribute";
        }
        if (domain instanceof ReferenceType) {
            return "reference attribute";
        }
        return "unsupported domain " + domain.getClass().getSimpleName();
    }

    private void skip(String attrName, String reason) {
        warn(
                DiagnosticCode.IO_SHP_ATTRIBUTE_SKIPPED,
                "SHP output '" + outputId + "': attribute '" + attrName + "' is not written to the Shapefile (" + reason
                        + ").");
    }

    private void warn(String code, String message) {
        if (diagnostics != null) {
            diagnostics.add(new Diagnostic(code, Severity.WARNING, message, null, null));
        }
    }

    private record DbfFieldSpec(DbfFieldType type, int length, int decimals) {}
}
