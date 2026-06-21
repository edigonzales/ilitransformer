package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record IlimapClassInfo(String qualifiedName, String kind, List<IlimapAttributeInfo> attributes) {

    public IlimapClassInfo {
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(attributes, "attributes");
        attributes = List.copyOf(attributes);
    }

    public Optional<IlimapAttributeInfo> findAttribute(String name) {
        return attributes.stream()
                .filter(attribute -> attribute.name().equals(name))
                .findFirst();
    }
}
