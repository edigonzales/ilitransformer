package guru.interlis.transformer.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class InventorySerializer {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public void writeJson(List<ModelInventory> inventories, Path outputPath) throws IOException {
        JSON_MAPPER.writeValue(outputPath.toFile(), inventories);
    }

    public String toJson(List<ModelInventory> inventories) throws IOException {
        return JSON_MAPPER.writeValueAsString(inventories);
    }

    public void writeMarkdown(List<ModelInventory> inventories, Path outputPath) throws IOException {
        Files.writeString(outputPath, toMarkdown(inventories));
    }

    public String toMarkdown(List<ModelInventory> inventories) {
        StringBuilder sb = new StringBuilder();
        for (ModelInventory inv : inventories) {
            sb.append("# Model: ").append(escapeMd(inv.modelName())).append("\n\n");
            if (inv.modelVersion() != null) {
                sb.append("**Version:** ").append(escapeMd(inv.modelVersion())).append("\n\n");
            }
            if (inv.issuer() != null) {
                sb.append("**Issuer:** ").append(escapeMd(inv.issuer())).append("\n\n");
            }

            List<ModelInventory.TopicInventory> topics = inv.topics();
            if (topics != null) {
                for (ModelInventory.TopicInventory topic : topics) {
                    sb.append("## Topic: ").append(escapeMd(topic.name())).append("\n\n");
                    sb.append("- **Basket OID:** ")
                            .append(escapeMd(topic.basketOidType()))
                            .append("\n");
                    sb.append("- **OID:** ").append(escapeMd(topic.oidType())).append("\n\n");

                    List<ModelInventory.ClassInventory> classes = topic.classes();
                    if (classes != null) {
                        for (ModelInventory.ClassInventory cls : classes) {
                            appendClassMarkdown(sb, cls);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private void appendClassMarkdown(StringBuilder sb, ModelInventory.ClassInventory cls) {
        sb.append("### Class: ").append(escapeMd(cls.name()));
        if (cls.isAbstract()) sb.append(" *(abstract)*");
        if (cls.isView()) sb.append(" *(view)*");
        sb.append("\n\n");
        sb.append("- **Path:** `").append(escapeMd(cls.path())).append("`\n");
        if (cls.baseClass() != null) {
            sb.append("- **Extends:** ").append(escapeMd(cls.baseClass())).append("\n");
        }

        List<ModelInventory.AttributeInventory> attrs = cls.attributes();
        if (attrs != null && !attrs.isEmpty()) {
            sb.append("\n**Attributes:**\n\n");
            sb.append("| Name | Type | Cardinality | Mandatory |\n");
            sb.append("|------|------|-------------|-----------|\n");
            for (ModelInventory.AttributeInventory attr : attrs) {
                sb.append("| ")
                        .append(escapeMd(attr.name()))
                        .append(" | ")
                        .append(escapeMd(attr.typeString()))
                        .append(" | ")
                        .append(escapeMd(attr.cardinality()))
                        .append(" | ")
                        .append(attr.mandatory() ? "yes" : "no")
                        .append(" |\n");
            }
        }

        List<ModelInventory.RoleInventory> roles = cls.roles();
        if (roles != null && !roles.isEmpty()) {
            sb.append("\n**Roles:**\n\n");
            sb.append("| Name | Association | Target Class | Cardinality |\n");
            sb.append("|------|-------------|--------------|-------------|\n");
            for (ModelInventory.RoleInventory role : roles) {
                sb.append("| ")
                        .append(escapeMd(role.name()))
                        .append(" | ")
                        .append(escapeMd(role.association() != null ? role.association() : "-"))
                        .append(" | ")
                        .append(escapeMd(role.targetClass() != null ? role.targetClass() : "-"))
                        .append(" | ")
                        .append(escapeMd(role.cardinality()))
                        .append(" |\n");
            }
        }
        sb.append("\n");
    }

    private static String escapeMd(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
