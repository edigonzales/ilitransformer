package guru.interlis.transformer.model;

import java.util.List;

public record ModelInventory(String modelName, String modelVersion, String issuer, List<TopicInventory> topics) {
    public record TopicInventory(String name, String basketOidType, String oidType, List<ClassInventory> classes) {}

    public record ClassInventory(
            String name,
            String path,
            boolean isView,
            String baseClass,
            boolean isAbstract,
            List<AttributeInventory> attributes,
            List<RoleInventory> roles) {}

    public record AttributeInventory(String name, String typeString, String cardinality, boolean mandatory) {}

    public record RoleInventory(String name, String association, String targetClass, String cardinality) {}
}
