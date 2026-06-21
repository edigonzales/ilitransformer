package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;
import java.util.Objects;

public record IlimapModelInfo(String name, String version, String issuer, List<IlimapClassInfo> classes) {

    public IlimapModelInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(classes, "classes");
        classes = List.copyOf(classes);
    }
}
