package guru.interlis.transformer.io.shp;

import guru.interlis.transformer.io.FormatOptions;
import guru.interlis.transformer.io.shp.core.ShapeType;
import guru.interlis.transformer.io.shp.geom.GeometryKind;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Typed accessor for Shapefile input/output options declared in an {@link FormatOptions} map.
 *
 * <p>All option keys, defaults and value interpretations are defined here so that neither the
 * reader, writer nor mapper need to work with raw string keys.
 */
public final class ShapefileOptions {

    private static final String CLASS_KEY = "class";
    private static final String TOPIC_KEY = "topic";
    private static final String BASKET_ID_KEY = "basketId";
    private static final String OID_FIELD_KEY = "oidField";
    private static final String GEOMETRY_ATTRIBUTE_KEY = "geometryAttribute";
    private static final String GEOMETRY_TYPE_KEY = "geometryType";
    private static final String DBF_ENCODING_KEY = "dbfEncoding";
    private static final String COLUMN_PREFIX = "column.";
    private static final String DELETED_RECORD_POLICY_KEY = "deletedRecordPolicy";
    private static final String REQUIRE_SHX_KEY = "requireShx";
    private static final String SHAPE_TYPE_KEY = "shapeType";
    private static final String FIELD_NAME_STRATEGY_KEY = "fieldNameStrategy";
    private static final String WRITE_SIDECAR_MAPPING_KEY = "writeSidecarMapping";
    private static final String PRJ_KEY = "prj";
    private static final String FAIL_ON_MULTIPLE_BASKETS_KEY = "failOnMultipleBaskets";

    private static final String DEFAULT_BASKET_ID = "b1";
    private static final Charset DEFAULT_INPUT_DBF_CHARSET = StandardCharsets.ISO_8859_1;
    private static final Charset DEFAULT_OUTPUT_DBF_CHARSET = StandardCharsets.UTF_8;

    private final FormatOptions options;
    private final boolean output;

    private ShapefileOptions(FormatOptions options, boolean output) {
        this.options = options;
        this.output = output;
    }

    public static ShapefileOptions input(FormatOptions options) {
        return new ShapefileOptions(options, false);
    }

    public static ShapefileOptions output(FormatOptions options) {
        return new ShapefileOptions(options, true);
    }

    public Optional<String> className() {
        return Optional.ofNullable(options.get(CLASS_KEY)).filter(s -> !s.isBlank());
    }

    public Optional<String> topicName() {
        return Optional.ofNullable(options.get(TOPIC_KEY)).filter(s -> !s.isBlank());
    }

    public String basketId() {
        return options.getOrDefault(BASKET_ID_KEY, DEFAULT_BASKET_ID);
    }

    public Optional<String> oidField() {
        return Optional.ofNullable(options.get(OID_FIELD_KEY)).filter(s -> !s.isBlank());
    }

    public Optional<String> geometryAttribute() {
        return Optional.ofNullable(options.get(GEOMETRY_ATTRIBUTE_KEY)).filter(s -> !s.isBlank());
    }

    public Optional<GeometryKind> geometryType() {
        String value = options.get(GEOMETRY_TYPE_KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(GeometryKind.fromOptionValue(value.trim()));
    }

    public Charset dbfCharset(Optional<Charset> cpgCharset) throws ShapefileMappingException {
        String encoding = options.get(DBF_ENCODING_KEY);
        if (encoding != null && !encoding.isBlank()) {
            try {
                return Charset.forName(encoding.trim());
            } catch (IllegalArgumentException e) {
                throw new ShapefileMappingException("SHP option 'dbfEncoding': unsupported charset '" + encoding + "'");
            }
        }
        return cpgCharset.orElse(output ? DEFAULT_OUTPUT_DBF_CHARSET : DEFAULT_INPUT_DBF_CHARSET);
    }

    public Optional<String> zipMember() {
        return Optional.ofNullable(options.get("member")).filter(s -> !s.isBlank());
    }

    public Map<String, String> columnMappings() throws ShapefileMappingException {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> lowerKeys = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : options.asMap().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(COLUMN_PREFIX) && key.length() > COLUMN_PREFIX.length()) {
                String dbfField = key.substring(COLUMN_PREFIX.length()).trim();
                if (!dbfField.isEmpty()) {
                    String previous = result.put(dbfField, entry.getValue());
                    if (previous != null) {
                        throw new ShapefileMappingException(
                                "SHP option 'column." + dbfField + "' is declared multiple times");
                    }
                    String lower = dbfField.toLowerCase(Locale.ROOT);
                    String prevLower = lowerKeys.put(lower, dbfField);
                    if (prevLower != null) {
                        throw new ShapefileMappingException("SHP option 'column." + dbfField
                                + "' conflicts with 'column." + prevLower + "' (case-insensitive match)");
                    }
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public DeletedRecordPolicy deletedRecordPolicy() throws ShapefileMappingException {
        String value = options.get(DELETED_RECORD_POLICY_KEY);
        if (value == null || value.isBlank()) {
            return DeletedRecordPolicy.ERROR;
        }
        return DeletedRecordPolicy.fromOptionValue(value.trim());
    }

    public boolean requireShx() {
        return options.getBoolean(REQUIRE_SHX_KEY, false);
    }

    public Optional<ShapeType> shapeTypeOverride() throws ShapefileMappingException {
        String value = options.get(SHAPE_TYPE_KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return Optional.of(
                switch (lower) {
                    case "point" -> ShapeType.POINT;
                    case "polyline", "line" -> ShapeType.POLYLINE;
                    case "polygon", "surface", "area" -> ShapeType.POLYGON;
                    default ->
                        throw new ShapefileMappingException(
                                "SHP option 'shapeType': expected 'point', 'polyline' or 'polygon', got '" + value
                                        + "'");
                });
    }

    public FieldNameStrategy fieldNameStrategy() throws ShapefileMappingException {
        String value = options.get(FIELD_NAME_STRATEGY_KEY);
        if (value == null || value.isBlank()) {
            return FieldNameStrategy.STRICT;
        }
        return FieldNameStrategy.fromOptionValue(value.trim());
    }

    public boolean writeSidecarMapping() {
        return options.getBoolean(WRITE_SIDECAR_MAPPING_KEY, true);
    }

    public Optional<String> prj() {
        return Optional.ofNullable(options.get(PRJ_KEY)).filter(s -> !s.isBlank());
    }

    public boolean failOnMultipleBaskets() {
        return options.getBoolean(FAIL_ON_MULTIPLE_BASKETS_KEY, true);
    }

    public enum FieldNameStrategy {
        STRICT,
        TRUNCATE,
        STABLE;

        static FieldNameStrategy fromOptionValue(String value) throws ShapefileMappingException {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "strict" -> STRICT;
                case "truncate" -> TRUNCATE;
                case "stable" -> STABLE;
                default ->
                    throw new ShapefileMappingException("SHP option 'fieldNameStrategy': expected 'strict', 'truncate'"
                            + " or 'stable', got '" + value + "'");
            };
        }
    }

    public enum DeletedRecordPolicy {
        ERROR,
        SKIP;

        static DeletedRecordPolicy fromOptionValue(String value) throws ShapefileMappingException {
            String lower = value.toLowerCase(Locale.ROOT);
            return switch (lower) {
                case "error" -> ERROR;
                case "skip" -> SKIP;
                default ->
                    throw new ShapefileMappingException(
                            "SHP option 'deletedRecordPolicy': expected 'error' or 'skip', got '" + value + "'");
            };
        }
    }
}
