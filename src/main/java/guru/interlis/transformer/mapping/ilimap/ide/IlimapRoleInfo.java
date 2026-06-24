package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapRoleInfo(String name, String association, String targetClass, String cardinality) {

    public IlimapRoleInfo {
        Objects.requireNonNull(name, "name");
        cardinality = cardinality != null ? cardinality : "";
    }
}
