package guru.interlis.transformer.io.jdbc;

import ch.interlis.iom.IomObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Maps scalar JDBC column values to INTERLIS/IOX attribute string values.
 *
 * <p>Rules (phase 6, tabular):
 *
 * <ul>
 *   <li>{@code null} is not set on the target object.
 *   <li>{@code String} stays as-is.
 *   <li>{@code Integer}/{@code Long}/{@code BigInteger}/other integral numbers -&gt; plain decimal string.
 *   <li>{@code BigDecimal} -&gt; {@link BigDecimal#toPlainString()}; {@code Double}/{@code Float} -&gt; locale-invariant string.
 *   <li>{@code Boolean} -&gt; {@code true}/{@code false}.
 *   <li>{@code java.sql.Date}/{@code LocalDate} -&gt; {@code yyyy-MM-dd}.
 *   <li>{@code java.sql.Time}/{@code LocalTime} -&gt; ISO time.
 *   <li>{@code java.sql.Timestamp}/{@code LocalDateTime}/{@code OffsetDateTime} -&gt; ISO date-time.
 *   <li>{@code byte[]} -&gt; error, unless {@code blobEncoding=base64} was configured.
 * </ul>
 */
public final class JdbcValueMapper {

    private final boolean allowBase64Blob;

    public JdbcValueMapper() {
        this(false);
    }

    public JdbcValueMapper(boolean allowBase64Blob) {
        this.allowBase64Blob = allowBase64Blob;
    }

    /** Sets {@code value} on {@code target} as {@code attrName}, skipping {@code null}. */
    public void applyScalarValue(IomObject target, String attrName, Object value) {
        String scalar = toIoxScalar(value, attrName);
        if (scalar != null) {
            target.setattrvalue(attrName, scalar);
        }
    }

    public String toIoxScalar(Object value) {
        return toIoxScalar(value, null);
    }

    private String toIoxScalar(Object value, String attrName) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        if (value instanceof BigInteger bi) {
            return bi.toString();
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Double || value instanceof Float) {
            return value.toString();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof LocalTime
                || value instanceof OffsetDateTime) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            if (allowBase64Blob) {
                return Base64.getEncoder().encodeToString(bytes);
            }
            throw new JdbcMappingException("Binary/BLOB value for column '" + describe(attrName)
                    + "' is not supported in tabular JDBC input. Set option 'blobEncoding' to 'base64' to encode it.");
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return value.toString();
    }

    private static String describe(String attrName) {
        return attrName != null ? attrName : "<unknown>";
    }
}
