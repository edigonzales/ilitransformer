package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record IlimapInputBlock(
        String id,
        String path,
        String model,
        String format,
        Map<String, String> options,
        IlimapConnectionBlock connection,
        List<IlimapQueryBlock> queries,
        IlimapSourceRange range)
        implements IlimapAstNode {

    public IlimapInputBlock {
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
        queries = queries == null ? List.of() : List.copyOf(queries);
    }

    /** Backward-compatible constructor for non-JDBC inputs (no connection/queries). */
    public IlimapInputBlock(
            String id, String path, String model, String format, Map<String, String> options, IlimapSourceRange range) {
        this(id, path, model, format, options, null, List.of(), range);
    }
}
