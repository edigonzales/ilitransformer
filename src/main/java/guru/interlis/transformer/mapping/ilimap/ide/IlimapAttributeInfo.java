package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapAttributeInfo(String name, String type, boolean mandatory, String cardinality) {

    public IlimapAttributeInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(cardinality, "cardinality");
    }
}
