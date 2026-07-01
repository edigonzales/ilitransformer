package guru.interlis.transformer.io.shp.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Low-level write behaviour for {@link ShapefileDatasetWriter}: the {@code .dbf} character encoding,
 * an optional {@code .prj} WKT string and the policy for values that do not fit into a DBF field.
 *
 * <p>This is a format-only options object and does not know about INTERLIS, IOX or the mapping
 * layer. The richer, user-facing option parsing lives in {@code ShapefileOptions} (reader/writer
 * adapter level) and is not part of the Phase 7 core.
 */
public record ShapefileWriteOptions(Charset dbfCharset, Optional<String> prjWkt, OverflowPolicy overflow) {

    public ShapefileWriteOptions {
        if (dbfCharset == null) {
            throw new IllegalArgumentException("dbfCharset must not be null");
        }
        if (prjWkt == null) {
            throw new IllegalArgumentException("prjWkt must not be null");
        }
        if (overflow == null) {
            throw new IllegalArgumentException("overflow policy must not be null");
        }
    }

    public static ShapefileWriteOptions defaults() {
        return new ShapefileWriteOptions(StandardCharsets.UTF_8, Optional.empty(), OverflowPolicy.STRICT);
    }

    /** Policy applied when an attribute value does not fit into the target DBF field width. */
    public enum OverflowPolicy {
        /** Reject the value with a {@code ShapefileMappingException}. */
        STRICT,

        /** Truncate DBF character fields byte-safely; all non-character fields remain strict. */
        TRUNCATE
    }
}
