package guru.interlis.transformer.model;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

class InventorySerializerTest {

    @Test
    void serializesToJson() throws IOException {
        var inv = createSampleInventory();
        List<ModelInventory> invs = List.of(inv);

        String json = new InventorySerializer().toJson(invs);

        assertThat(json).contains("\"modelName\" : \"TestModel\"");
        assertThat(json).contains("\"modelVersion\" : \"1.0\"");
        assertThat(json).contains("\"name\" : \"TestTopic\"");
        assertThat(json).contains("\"basketOidType\" : \"UUIDOID\"");
        assertThat(json).contains("\"oidType\" : \"STANDARDOID\"");
        assertThat(json).contains("\"name\" : \"TestClass\"");
        assertThat(json).contains("\"path\" : \"TestModel.TestTopic.TestClass\"");
        assertThat(json).contains("\"Name\"");
        assertThat(json).contains("\"TEXT*60\"");
        assertThat(json).contains("\"cardinality\" : \"1\"");
        assertThat(json).contains("\"mandatory\" : true");
    }

    @Test
    void serializesToMarkdown() {
        var inv = createSampleInventory();
        List<ModelInventory> invs = List.of(inv);

        String md = new InventorySerializer().toMarkdown(invs);

        assertThat(md).contains("# Model: TestModel");
        assertThat(md).contains("**Version:** 1.0");
        assertThat(md).contains("## Topic: TestTopic");
        assertThat(md).contains("- **Basket OID:** UUIDOID");
        assertThat(md).contains("- **OID:** STANDARDOID");
        assertThat(md).contains("### Class: TestClass");
        assertThat(md).contains("`TestModel.TestTopic.TestClass`");
        assertThat(md).contains("| Name | TEXT*60 | 1 | yes |");
        assertThat(md).contains("| Beschreibung | TEXT*200 | 0..1 | no |");
    }

    @Test
    void markdownIncludesRoles() {
        var role = new ModelInventory.RoleInventory("ParentRole", "ParentChild", "Parent", "1");
        var cls = new ModelInventory.ClassInventory("Child", "M.T.Child", false, null, false, List.of(), List.of(role));
        var topic = new ModelInventory.TopicInventory("T", "UUID", "STANDARD", List.of(cls));
        var inv = new ModelInventory("M", "1.0", null, List.of(topic));
        String md = new InventorySerializer().toMarkdown(List.of(inv));

        assertThat(md).contains("**Roles:**");
        assertThat(md).contains("| ParentRole | ParentChild | Parent | 1 |");
    }

    @Test
    void emptyAttributesAndRolesProduceCleanOutput() throws IOException {
        var cls = new ModelInventory.ClassInventory("Empty", "M.T.Empty", false, null, false, List.of(), List.of());
        var topic = new ModelInventory.TopicInventory("T", "UUID", "STANDARD", List.of(cls));
        var inv = new ModelInventory("M", "1.0", null, List.of(topic));

        String json = new InventorySerializer().toJson(List.of(inv));
        assertThat(json).contains("\"attributes\" : [ ]");
        assertThat(json).contains("\"roles\" : [ ]");
    }

    private static ModelInventory createSampleInventory() {
        var attr1 = new ModelInventory.AttributeInventory("Name", "TEXT*60", "1", true);
        var attr2 = new ModelInventory.AttributeInventory("Beschreibung", "TEXT*200", "0..1", false);
        var cls = new ModelInventory.ClassInventory(
                "TestClass", "TestModel.TestTopic.TestClass", false, null, false, List.of(attr1, attr2), List.of());
        var topic = new ModelInventory.TopicInventory("TestTopic", "UUIDOID", "STANDARDOID", List.of(cls));
        return new ModelInventory("TestModel", "1.0", null, List.of(topic));
    }
}
