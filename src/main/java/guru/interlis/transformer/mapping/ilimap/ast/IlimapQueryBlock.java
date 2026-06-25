package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC {@code query <id> { ... }} block inside an input block. One query maps to one source class. */
public record IlimapQueryBlock(
        String id,
        String topic,
        String sourceClass,
        String basketId,
        String oidColumn,
        String sql,
        Map<String, String> columns,
        List<IlimapGeometryBlock> geometry,
        IlimapSourceRange range)
        implements IlimapAstNode {

    public IlimapQueryBlock {
        columns = columns == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
        geometry = geometry == null ? List.of() : List.copyOf(geometry);
    }
}
