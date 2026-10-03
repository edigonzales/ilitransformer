package guru.interlis.transformer.api;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;

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
        assertThatThrownBy(() -> new MigrationService().execute(request, s -> {}))
                .hasMessageContaining("Review");
        Files.writeString(mapping, draft.text().replace("migrationReviewed false", "migrationReviewed true"));
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
