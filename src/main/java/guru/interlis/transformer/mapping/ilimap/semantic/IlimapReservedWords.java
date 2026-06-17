package guru.interlis.transformer.mapping.ilimap.semantic;

import java.util.Set;

public final class IlimapReservedWords {
    private IlimapReservedWords() {}

    private static final Set<String> WORDS = Set.of(
            "mapping", "v2", "job", "input", "output", "oid", "basket", "enum", "defaults",
            "rule", "target", "source", "from", "in", "class", "where", "join", "inner", "left",
            "to", "on", "identity", "assign", "bag", "structure", "mode", "embed", "expand",
            "maxItems", "parentRef", "attribute", "role", "ref", "association", "required",
            "sourceRef", "create", "loss", "sourcePath", "reasonCode", "description", "when",
            "metadata", "direction", "roundtrip", "lossiness",
            "name", "modeldir", "path", "model", "format", "namespace",
            "true", "false", "null"
    );

    public static boolean isReserved(String value) {
        return WORDS.contains(value);
    }

    public static Set<String> all() {
        return WORDS;
    }
}
