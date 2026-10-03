package guru.interlis.transformer.api;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.*;

import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationVerticalTest {
    @TempDir
    Path dir;

    @Test
    void migrationPreservesReferencesCurvesXyzAndNestedOccurrences() throws Exception {
        String models = Path.of("src/test/data/migration").toAbsolutePath().toString();
        Path input = dir.resolve("source.xtf"),
                output = dir.resolve("target.xtf"),
                mapping = dir.resolve("migration.ilimap");
        var draft = new MigrationDraftService()
                .create("MigrationDemo_V1", "MigrationDemo_V2", models, input.toString(), output.toString());
        var document =
                new IlimapEditorDocument(draft.text().replace("migrationReviewed false", "migrationReviewed true"));
        String rule = MigrationDraftService.ruleId("MigrationDemo_V1.Data.Line");
        document.assign(rule, "", "Name", "trim(s.Label)");
        document.assign(rule, "", "Origin", "\"migration\"");
        var statuses = new LinkedHashMap<String, String>();
        statuses.put("active", "operational");
        statuses.put("retired", "closed");
        document.enumeration(rule, "", "Status", "s.Status", "Statuses", statuses);
        document.assign(rule, "Controls", "Office", "trim(sc.Office)");
        Files.writeString(mapping, document.text());
        var writer = new InterlisIoFactory()
                .createWriter(input, draft.source().types().getTransferDescription());
        writer.write(new StartTransferEvent("test", null, null));
        writer.write(new StartBasketEvent("MigrationDemo_V1.Data", "b1"));
        var facility = new Iom_jObject("MigrationDemo_V1.Data.Facility", "f1");
        facility.setattrvalue("Name", "Facility");
        writer.write(new ObjectEvent(facility));
        var line = new Iom_jObject("MigrationDemo_V1.Data.Line", "l1");
        line.setattrvalue("Label", "  First  ");
        line.setattrvalue("Status", "active");
        line.addattrobj("Facility", "REF").setobjectrefoid("f1");
        var location = line.addattrobj("Location", "COORD");
        location.setattrvalue("C1", "1.000");
        location.setattrvalue("C2", "2.000");
        location.setattrvalue("C3", "3.000");
        var polyline = line.addattrobj("Geometry", "POLYLINE");
        var sequence = polyline.addattrobj("sequence", "SEGMENTS");
        var start = sequence.addattrobj("segment", "COORD");
        start.setattrvalue("C1", "1.000");
        start.setattrvalue("C2", "2.000");
        var arc = sequence.addattrobj("segment", "ARC");
        arc.setattrvalue("A1", "2.000");
        arc.setattrvalue("A2", "3.000");
        arc.setattrvalue("C1", "3.000");
        arc.setattrvalue("C2", "2.000");
        for (String office : List.of(" Second ", " First ", " Second ")) {
            var child = line.addattrobj("Controls", "MigrationDemo_V1.Data.Control");
            child.setattrvalue("Office", office);
            for (int i = 0; i < 2; i++)
                child.addattrobj("Details", "MigrationDemo_V1.Data.Detail").setattrvalue("Note", "duplicate");
        }
        writer.write(new ObjectEvent(line));
        writer.write(new EndBasketEvent());
        writer.write(new EndTransferEvent());
        writer.close();
        var result = new MigrationService()
                .execute(
                        new MigrationService.Request(
                                mapping, null, null, List.of(models), true, false, dir.resolve("report")),
                        s -> {});
        assertThat(result.all())
                .filteredOn(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                .isEmpty();
        var reader = new InterlisIoFactory()
                .createReader(output, draft.target().types().getTransferDescription());
        IomObject migrated = null;
        try {
            IoxEvent event;
            while ((event = reader.read()) != null)
                if (event instanceof ObjectEvent obj
                        && obj.getIomObject().getobjectoid().equals("l1")) migrated = obj.getIomObject();
        } finally {
            reader.close();
        }
        assertThat(migrated).isNotNull();
        assertThat(migrated.getobjecttag()).isEqualTo("MigrationDemo_V2.Data.Line");
        assertThat(migrated.getattrvalue("Name")).isEqualTo("First");
        assertThat(migrated.getattrvalue("Status")).isEqualTo("operational");
        assertThat(migrated.getattrobj("Facility", 0).getobjectrefoid()).isEqualTo("f1");
        assertThat(Double.parseDouble(migrated.getattrobj("Location", 0).getattrvalue("C3")))
                .isEqualTo(3.0);
        assertThat(migrated.getattrobj("Geometry", 0)
                        .getattrobj("sequence", 0)
                        .getattrobj("segment", 1)
                        .getobjecttag())
                .isEqualTo("ARC");
        assertThat(migrated.getattrvaluecount("Controls")).isEqualTo(3);
        assertThat(migrated.getattrobj("Controls", 0).getattrvalue("Office")).isEqualTo("Second");
        assertThat(migrated.getattrobj("Controls", 1).getattrvalue("Office")).isEqualTo("First");
        assertThat(migrated.getattrobj("Controls", 0).getattrvaluecount("Details"))
                .isEqualTo(2);
        assertThat(migrated.getattrobj("Controls", 0).getattrobj("Details", 0).getobjecttag())
                .isEqualTo("MigrationDemo_V2.Data.Detail");
        // Keep reusable evidence for the installed-plugin example without committing generated build output.
        Path example = Path.of("build/migration-example");
        Files.createDirectories(example);
        Files.copy(input, example.resolve("source.xtf"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(example.resolve("migration.ilimap"), document.text());
    }
}
