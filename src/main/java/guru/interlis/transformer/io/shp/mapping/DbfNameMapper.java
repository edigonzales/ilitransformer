package guru.interlis.transformer.io.shp.mapping;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.ShapefileOptions.FieldNameStrategy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Derives DBF field names (max 10 characters, ASCII, unique case-insensitively) from INTERLIS
 * attribute names according to a {@link FieldNameStrategy}.
 *
 * <ul>
 *   <li>{@code STRICT}: the attribute name must already be DBF-compatible and unique, otherwise an
 *       error is raised.
 *   <li>{@code TRUNCATE}: names are sanitised and truncated to 10 characters; a collision is an
 *       error.
 *   <li>{@code STABLE}: names are sanitised, truncated and deterministically de-conflicted with a
 *       numeric suffix; a {@code *.iliattr.json} sidecar can be written via {@link
 *       #writeSidecar(Path, DbfNameMapping)}.
 * </ul>
 */
public final class DbfNameMapper {

    public static final String SIDECAR_FORMAT = "ilitransformer-shapefile-attribute-map-v1";
    private static final int MAX_LEN = 10;

    private DbfNameMapper() {}

    public record DbfNameMapping(
            Map<String, String> attributeToDbf, Map<String, String> dbfToAttribute, List<String> warnings) {}

    public static DbfNameMapping create(List<String> attributeNames, FieldNameStrategy strategy)
            throws ShapefileMappingException {
        return switch (strategy) {
            case STRICT -> strict(attributeNames);
            case TRUNCATE -> truncate(attributeNames);
            case STABLE -> stable(attributeNames);
        };
    }

    private static DbfNameMapping strict(List<String> attributeNames) throws ShapefileMappingException {
        Map<String, String> attributeToDbf = new LinkedHashMap<>();
        Map<String, String> dbfToAttribute = new LinkedHashMap<>();
        for (String attr : attributeNames) {
            if (!isDbfSafe(attr)) {
                throw new ShapefileMappingException("DBF field name strategy 'strict': attribute '" + attr
                        + "' is not a valid DBF field name (allowed: A-Z, a-z, 0-9, underscore). "
                        + "Use fieldNameStrategy=truncate or stable.");
            }
            if (attr.length() > MAX_LEN) {
                throw new ShapefileMappingException("DBF field name strategy 'strict': attribute '" + attr
                        + "' exceeds the DBF field name limit of " + MAX_LEN
                        + " characters. Use fieldNameStrategy=truncate or stable.");
            }
            String lower = attr.toLowerCase(Locale.ROOT);
            if (dbfToAttribute.containsKey(lower)) {
                throw new ShapefileMappingException("DBF field name strategy 'strict': attributes '"
                        + dbfToAttribute.get(lower) + "' and '" + attr + "' collide case-insensitively. "
                        + "Use fieldNameStrategy=stable.");
            }
            attributeToDbf.put(attr, attr);
            dbfToAttribute.put(lower, attr);
        }
        return build(attributeToDbf, List.of());
    }

    private static DbfNameMapping truncate(List<String> attributeNames) throws ShapefileMappingException {
        Map<String, String> attributeToDbf = new LinkedHashMap<>();
        Map<String, String> usedLowerToAttr = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (String attr : attributeNames) {
            String dbf = sanitizeAndClamp(attr);
            String lower = dbf.toLowerCase(Locale.ROOT);
            if (usedLowerToAttr.containsKey(lower)) {
                throw new ShapefileMappingException("DBF field name strategy 'truncate': attributes '"
                        + usedLowerToAttr.get(lower) + "' and '" + attr + "' both map to '" + dbf
                        + "'. Use fieldNameStrategy=stable.");
            }
            if (!dbf.equals(attr)) {
                warnings.add("attribute '" + attr + "' written as DBF field '" + dbf + "'");
            }
            attributeToDbf.put(attr, dbf);
            usedLowerToAttr.put(lower, attr);
        }
        return build(attributeToDbf, warnings);
    }

    private static DbfNameMapping stable(List<String> attributeNames) {
        Map<String, String> attributeToDbf = new LinkedHashMap<>();
        Map<String, String> usedLower = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (String attr : attributeNames) {
            String base = sanitizeAndClamp(attr);
            String dbf = base;
            int suffix = 1;
            while (usedLower.containsKey(dbf.toLowerCase(Locale.ROOT))) {
                String suffixStr = "_" + suffix++;
                int keep = Math.max(1, MAX_LEN - suffixStr.length());
                dbf = base.substring(0, Math.min(base.length(), keep)) + suffixStr;
            }
            if (!dbf.equals(attr)) {
                warnings.add("attribute '" + attr + "' written as DBF field '" + dbf + "'");
            }
            attributeToDbf.put(attr, dbf);
            usedLower.put(dbf.toLowerCase(Locale.ROOT), attr);
        }
        return build(attributeToDbf, warnings);
    }

    private static DbfNameMapping build(Map<String, String> attributeToDbf, List<String> warnings) {
        Map<String, String> dbfToAttribute = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : attributeToDbf.entrySet()) {
            dbfToAttribute.put(entry.getValue(), entry.getKey());
        }
        return new DbfNameMapping(
                Collections.unmodifiableMap(attributeToDbf),
                Collections.unmodifiableMap(dbfToAttribute),
                Collections.unmodifiableList(warnings));
    }

    /** Whether any DBF field name differs from its source attribute name. */
    public static boolean hasRenames(DbfNameMapping mapping) {
        return mapping.attributeToDbf().entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(e.getValue()));
    }

    /** Writes the {@code *.iliattr.json} sidecar describing the DBF&rarr;attribute name mapping. */
    public static void writeSidecar(Path sidecarPath, DbfNameMapping mapping) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode root = mapper.createObjectNode();
        root.put("format", SIDECAR_FORMAT);
        ArrayNode fields = root.putArray("fields");
        for (Map.Entry<String, String> entry : mapping.attributeToDbf().entrySet()) {
            ObjectNode field = fields.addObject();
            field.put("dbf", entry.getValue());
            field.put("attribute", entry.getKey());
        }
        Files.writeString(sidecarPath, mapper.writeValueAsString(root), StandardCharsets.UTF_8);
    }

    private static boolean isDbfSafe(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeAndClamp(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            sb.append(ok ? c : '_');
        }
        String sanitized = sb.length() == 0 ? "FIELD" : sb.toString();
        return sanitized.length() > MAX_LEN ? sanitized.substring(0, MAX_LEN) : sanitized;
    }
}
