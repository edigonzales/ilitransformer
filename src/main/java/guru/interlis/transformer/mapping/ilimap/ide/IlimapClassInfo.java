package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record IlimapClassInfo(
        String qualifiedName, String kind, List<IlimapAttributeInfo> attributes, List<IlimapRoleInfo> roles) {

    public IlimapClassInfo(String qualifiedName, String kind, List<IlimapAttributeInfo> attributes) {
        this(qualifiedName, kind, attributes, List.of());
    }

    public IlimapClassInfo {
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(roles, "roles");
        attributes = List.copyOf(attributes);
        roles = List.copyOf(roles);
    }

    public Optional<IlimapAttributeInfo> findAttribute(String name) {
        return attributes.stream()
                .filter(attribute -> attribute.name().equals(name))
                .findFirst();
    }

    public Optional<IlimapRoleInfo> findRole(String name) {
        return roles.stream().filter(role -> role.name().equals(name)).findFirst();
    }

    public boolean hasSourceMember(String name) {
        return findAttribute(name).isPresent() || findRole(name).isPresent();
    }
}
