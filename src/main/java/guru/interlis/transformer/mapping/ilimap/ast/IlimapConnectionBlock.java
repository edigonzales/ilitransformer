package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** JDBC {@code connection { ... }} block inside an input block. */
public record IlimapConnectionBlock(
        String driver,
        String url,
        String user,
        String password,
        String userEnv,
        String passwordEnv,
        Map<String, String> properties,
        IlimapSourceRange range)
        implements IlimapAstNode {

    public IlimapConnectionBlock {
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
