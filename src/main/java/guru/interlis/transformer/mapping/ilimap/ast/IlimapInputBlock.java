package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record IlimapInputBlock(
        String id, String path, String model, String format, Map<String, String> options, IlimapSourceRange range)
        implements IlimapAstNode {

    public IlimapInputBlock {
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }
}
