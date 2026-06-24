package guru.interlis.transformer.model;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class IliModelServiceTest {

    private final IliModelService service = new IliModelService();
    private static final String MODELDIR = "src/test/data/models/";

    @Test
    void compilesMinimalModel() {
        IliModelCompileResult result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.transferDescription()).isNotNull();
    }

    @Test
    void buildInventoryForMinimalModel() {
        IliModelCompileResult result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();

        ModelInventory inv = service.buildInventory(result.transferDescription(), "minimal");
        assertThat(inv.modelName()).isEqualTo("TestModel");
        assertThat(inv.modelVersion()).isEqualTo("2026-06-07");
        assertThat(inv.issuer()).isEqualTo("http://test.ilitransformer.ch");
        assertThat(inv.topics()).isNotEmpty();

        ModelInventory.TopicInventory topic = inv.topics().get(0);
        assertThat(topic.name()).isEqualTo("TestTopic");
        assertThat(topic.basketOidType()).contains("UUID");
        assertThat(topic.oidType()).contains("STANDARD");
        assertThat(topic.classes()).hasSize(1);

        ModelInventory.ClassInventory cls = topic.classes().get(0);
        assertThat(cls.name()).isEqualTo("TestClass");
        assertThat(cls.path()).isEqualTo("TestModel.TestTopic.TestClass");
        assertThat(cls.isAbstract()).isFalse();
        assertThat(cls.isView()).isFalse();

        assertThat(cls.attributes()).hasSize(4);

        ModelInventory.AttributeInventory nameAttr = cls.attributes().get(0);
        assertThat(nameAttr.name()).isEqualTo("Name");
        assertThat(nameAttr.typeString()).contains("TEXT*60");
        assertThat(nameAttr.mandatory()).isTrue();
        assertThat(nameAttr.cardinality()).isEqualTo("1");

        ModelInventory.AttributeInventory beschrAttr = cls.attributes().get(1);
        assertThat(beschrAttr.name()).isEqualTo("Beschreibung");
        assertThat(beschrAttr.mandatory()).isFalse();
        assertThat(beschrAttr.cardinality()).isEqualTo("0..1");
    }

    @Test
    void compilesEnumModel() {
        IliModelCompileResult result = service.compileModel("src/test/data/models/with-enums.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();

        ModelInventory inv = service.buildInventory(result.transferDescription(), "enums");
        assertThat(inv.modelName()).isEqualTo("EnumModel");

        ModelInventory.ClassInventory cls = inv.topics().get(0).classes().get(0);
        assertThat(cls.name()).isEqualTo("StatusClass");

        ModelInventory.AttributeInventory statusAttr = cls.attributes().get(1);
        assertThat(statusAttr.name()).isEqualTo("AktuellerStatus");
        assertThat(statusAttr.mandatory()).isTrue();
        assertThat(statusAttr.typeString()).contains("Status");
    }

    @Test
    void compilesAssociationModelWithRoles() {
        IliModelCompileResult result = service.compileModel("src/test/data/models/with-associations.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();

        ModelInventory inv = service.buildInventory(result.transferDescription(), "assoc");
        assertThat(inv.modelName()).isEqualTo("AssocModel");

        assertThat(inv.topics()).hasSize(1);
        assertThat(inv.topics().get(0).classes()).hasSize(2);

        // Find Parent class
        ModelInventory.ClassInventory parent = findClass(inv, "Parent");
        assertThat(parent.roles()).hasSize(1);
        assertThat(parent.roles().get(0).name()).isEqualTo("ParentRole");
        assertThat(parent.roles().get(0).association()).isEqualTo("ParentChild");
        assertThat(parent.roles().get(0).targetClass()).isEqualTo("Parent");
        assertThat(parent.roles().get(0).cardinality()).isEqualTo("1");

        // Find Child class
        ModelInventory.ClassInventory child = findClass(inv, "Child");
        assertThat(child.roles()).extracting(ModelInventory.RoleInventory::name).contains("ParentRole", "ChildRole");
        assertThat(child.roles()).anySatisfy(role -> {
            assertThat(role.name()).isEqualTo("ChildRole");
            assertThat(role.association()).isEqualTo("ParentChild");
            assertThat(role.cardinality()).isEqualTo("0..*");
        });
    }

    @Test
    void inventoryIncludesTransferRolesFromIli1References() {
        IliModelCompileResult result = service.compileModel("DM01AVCH24LV95D", "src/test/data/av/models/");
        assertThat(result.hasErrors()).isFalse();

        ModelInventory inv = service.buildInventory(result.transferDescription(), "DM01AVCH24LV95D");
        ModelInventory.ClassInventory lfp3 = inv.topics().stream()
                .flatMap(topic -> topic.classes().stream())
                .filter(cls -> cls.path().equals("DM01AVCH24LV95D.FixpunkteKategorie3.LFP3"))
                .findFirst()
                .orElseThrow();

        assertThat(lfp3.roles()).extracting(ModelInventory.RoleInventory::name).contains("Entstehung");
    }

    @Test
    void compilesStructureModel() {
        IliModelCompileResult result = service.compileModel("src/test/data/models/with-structures.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();

        ModelInventory inv = service.buildInventory(result.transferDescription(), "struct");
        assertThat(inv.modelName()).isEqualTo("StructModel");

        ModelInventory.ClassInventory geoClass = findClass(inv, "GeoClass");
        assertThat(geoClass.attributes()).hasSize(3);

        // Pos attribute should show structure name
        var posAttr = geoClass.attributes().get(1);
        assertThat(posAttr.name()).isEqualTo("Pos");
        assertThat(posAttr.typeString()).isEqualTo("Position");
        assertThat(posAttr.cardinality()).isEqualTo("0..1");

        var bagAttr = geoClass.attributes().get(2);
        assertThat(bagAttr.name()).isEqualTo("MehrerePositionen");
        assertThat(bagAttr.typeString()).isEqualTo("Position");
        assertThat(bagAttr.cardinality()).isEqualTo("0..*");
    }

    @Test
    void compileNonexistentModelReturnsDiagnostic() {
        IliModelCompileResult result = service.compileModel("src/test/data/models/nonexistent.ili", MODELDIR);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.transferDescription()).isNull();
        assertThat(result.diagnostics().all()).isNotEmpty();
    }

    @Test
    void cachesCompileResultsForSameNormalizedModeldir() {
        AtomicInteger compileCalls = new AtomicInteger();
        IliModelService cachedService = new IliModelService((modelName, modelDirectories) -> {
            compileCalls.incrementAndGet();
            assertThat(modelDirectories).isEqualTo("https://models.geo.admin.ch");
            return null;
        });

        cachedService.compileModel("TestModel", "https://models.geo.admin.ch/");
        cachedService.compileModel("TestModel", " https://models.geo.admin.ch ");

        assertThat(compileCalls).hasValue(1);
    }

    @Test
    void classesSortedByNameDeterministically() {
        IliModelCompileResult result = service.compileModel("src/test/data/models/with-associations.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();

        ModelInventory inv = service.buildInventory(result.transferDescription(), "assoc");
        // Child should come before Parent alphabetically
        assertThat(inv.topics().get(0).classes().get(0).name()).isEqualTo("Child");
        assertThat(inv.topics().get(0).classes().get(1).name()).isEqualTo("Parent");
    }

    private static ModelInventory.ClassInventory findClass(ModelInventory inv, String name) {
        return inv.topics().get(0).classes().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
