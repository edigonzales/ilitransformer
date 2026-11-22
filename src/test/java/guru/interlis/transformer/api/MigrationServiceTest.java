package guru.interlis.transformer.api;

import static guru.interlis.transformer.api.MigrationDraftService.Status.*;
import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.interlis.InterlisModelLoader;
import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;

import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox_j.*;

import java.nio.file.*;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationServiceTest {
    @TempDir
    Path dir;

    @Test
    void draftExcludesIli1LineAttributeHelperTables() throws Exception {
        String models = Path.of("src/test/data/av/models").toAbsolutePath().toString();
        String model = "DM01AVCH24LV95D";
        String className = model + ".Liegenschaften.ProjLiegenschaft";
        var td = new InterlisModelLoader().compileModel(model, models);
        assertThat(td).isNotNull();
        var table = (Table) td.getElement(className);
        var geometry = (SurfaceOrAreaType) table.findAttribute("Geometrie").getDomainResolvingAliases();
        Table helper = geometry.getLineAttributeStructure();
        assertThat(helper).as("ili2c-generated LINEATTR table").isNotNull();
        assertThat(helper.isIli1LineAttrStruct()).isTrue();
        assertThat(helper.isImplicit()).isFalse();

        var drafts = new MigrationDraftService();
        String helperName = helper.getScopedName(null);
        assertThat(drafts.schema(model, td).entities())
                .extracting(MigrationDraftService.Entity::name)
                .contains(className)
                .doesNotContain(helperName);
        var draft = drafts.create(model, model, models, "source.itf", "target.itf");
        assertThat(draft.text()).contains("class \"" + className + "\"").doesNotContain("class \"" + helperName + "\"");
    }

    @Test
    void draftReportsUnmappedContentAndKeepsOpenDecisions() throws Exception {
        var draft = new MigrationDraftService()
                .create("P43Source", "P43Target", "src/test/data/migration", "source.xtf", "target.xtf");

        assertThat(draft.differences())
                .extracting(
                        MigrationDraftService.Difference::sourceClass,
                        MigrationDraftService.Difference::attribute,
                        MigrationDraftService.Difference::status)
                .contains(
                        tuple("P43Source.Data.Obsolete", "", NOT_MAPPED),
                        tuple("P43Source.Data.Item", "LegacyCode", NOT_MAPPED),
                        tuple("P43Source.Data.Item", "LegacyItem", NOT_MAPPED),
                        tuple("P43Source.Data.ChangesKind", "", DECISION),
                        tuple("P43Source.Data.Item", "Origin", DECISION),
                        tuple("P43Source.Data.Item", "Kind", DECISION),
                        tuple("P43Source.Data.Item", "Name", COMPATIBLE));
        assertThat(draft.text())
                .contains(
                        "// REVIEW P43Source.Data.Obsolete: Add a mapping rule or an explicit class loss rule",
                        "// REVIEW P43Source.Data.Item.LegacyCode: Map this source attribute or declare a loss",
                        "Name = s.Name;")
                .doesNotContain(
                        "loss {", "reasonCode", "Kind =", "LegacyCode =", "modelFingerprint", "migrationReviewed");
        assertThat(draft.source().entity("P43Source.Data.Details").structure()).isTrue();
    }

    @Test
    void draftReportsMissingStandaloneAssociationAndDirectReference() throws Exception {
        var draft = new MigrationDraftService()
                .create(
                        "P42Source",
                        "P43Target",
                        "src/test/data/standalone-associations;src/test/data/migration",
                        "source.xtf",
                        "target.xtf");

        assertThat(draft.differences())
                .extracting(MigrationDraftService.Difference::sourceClass, MigrationDraftService.Difference::status)
                .contains(tuple("P42Source.Data.Anonymous", NOT_MAPPED))
                .doesNotContain(tuple("P42Source.Data.Embedded", NOT_MAPPED));
        assertThat(draft.source().entity("P42Source.Data.Pointer").fields())
                .extracting(MigrationDraftService.Field::name, MigrationDraftService.Field::referenceTarget)
                .contains(tuple("Destination", "P42Source.Data.Site"), tuple("RemoteSite", "P42Source.Data.Site"));
    }

    @Test
    void preparedDraftMigratesEmbeddedOccurrencesAndValidates() throws Exception {
        String models = Path.of("src/test/data/models").toAbsolutePath().toString();
        Path input = dir.resolve("source.xtf"),
                output = dir.resolve("target.xtf"),
                mapping = dir.resolve("migration.ilimap");
        var draft = new MigrationDraftService()
                .create("BagNestedTarget", "BagNestedTarget", models, input.toString(), output.toString());
        new IlimapEditorDocument(draft.text()).document();
        var writer = new InterlisIoFactory()
                .createWriter(input, draft.source().types().getTransferDescription());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent("BagNestedTarget.NestedTopic", "550e8400-e29b-41d4-a716-446655440000"));
        var parent = new Iom_jObject("BagNestedTarget.NestedTopic.Parent", "ch00000000000001");
        parent.setattrvalue("ParentId", "parent");
        for (String x : List.of("2.000", "1.000", "2.000")) {
            var child = new Iom_jObject("BagNestedTarget.NestedTopic.Position", null);
            child.setattrvalue("X", x);
            child.setattrvalue("Y", "3.000");
            parent.addattrobj("Positions", child);
        }
        writer.write(new ObjectEvent(parent));
        writer.write(new EndBasketEvent());
        writer.write(new EndTransferEvent());
        writer.close();
        Files.writeString(mapping, draft.text());
        var request =
                new MigrationService.Request(mapping, null, null, List.of(models), true, false, dir.resolve("report"));
        var result = new MigrationService().execute(request, s -> {});
        assertThat(result.all())
                .filteredOn(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                .isEmpty();
        assertThat(output).exists();
        String xml = Files.readString(output);
        assertThat(xml).contains("Positions", "ch00000000000001");
        assertThatThrownBy(() -> new MigrationService().execute(request, s -> {}))
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsAliasesOfOriginalAndUnresolvedPaths() throws Exception {
        Path input = dir.resolve("original.xtf");
        Files.writeString(input, "original");
        Path link = dir.resolve("hard.xtf");
        Files.createLink(link, input);
        assertThatThrownBy(() -> MigrationService.requireDistinct(input, link)).hasMessageContaining("different");
        Path sym = dir.resolve("sym.xtf");
        Files.createSymbolicLink(sym, input);
        assertThatThrownBy(() -> MigrationService.requireDistinct(input, sym)).hasMessageContaining("different");
    }
}
