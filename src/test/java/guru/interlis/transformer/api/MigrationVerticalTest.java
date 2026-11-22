package guru.interlis.transformer.api;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.loss.LossEvent;
import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.*;

import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationVerticalTest {
    @TempDir
    Path dir;

    @Test
    void migratesXtfAndReportsExplicitClassAndConditionalAttributeLosses() throws Exception {
        String models = Path.of("src/test/data/migration").toAbsolutePath().toString();
        Path input = dir.resolve("source.xtf"), output = dir.resolve("target.xtf");
        Path mapping = dir.resolve("migration.ilimap"), report = dir.resolve("report");
        var draft = new MigrationDraftService()
                .create("P43Source", "P43Target", models, input.toString(), output.toString());
        Files.writeString(mapping, """
                mapping v2 {
                  input original { path "source.xtf"; model "P43Source"; }
                  output migrated { path "target.xtf"; model "P43Target"; }
                  oid { strategy preserve; }
                  basket preserve;
                  rule migrate {
                    source s from original class "P43Source.Data.Item";
                    target migrated class "P43Target.Data.Item";
                    assign { Name = s.Name; Origin = "original"; }
                    loss {
                      sourcePath s.LegacyCode;
                      reasonCode "ATTRIBUTE_REMOVED";
                      description "Legacy codes were retired";
                      when defined(s.LegacyCode);
                    }
                  }
                  rule discard {
                    source s from original, original class "P43Source.Data.Obsolete";
                    loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Obsolete class"; }
                  }
                  rule discardEmpty {
                    source s from original class "P43Source.Data.Empty";
                    loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Class without records"; }
                  }
                }
                """);

        var writer = new InterlisIoFactory()
                .createWriter(input, draft.source().types().getTransferDescription());
        try {
            writer.write(new StartTransferEvent("test", null, null));
            writer.write(new StartBasketEvent("P43Source.Data", "items"));
            var first = new Iom_jObject("P43Source.Data.Item", "i1");
            first.setattrvalue("Name", "First");
            first.setattrvalue("LegacyCode", "retired");
            var second = new Iom_jObject("P43Source.Data.Item", "i2");
            second.setattrvalue("Name", "Second");
            writer.write(new ObjectEvent(first));
            writer.write(new ObjectEvent(second));
            writer.write(new EndBasketEvent());
            writer.write(new StartBasketEvent("P43Source.Data", "discarded"));
            writer.write(new ObjectEvent(new Iom_jObject("P43Source.Data.Obsolete", "old1")));
            writer.write(new ObjectEvent(new Iom_jObject("P43Source.Data.Obsolete", "old2")));
            writer.write(new EndBasketEvent());
            writer.write(new EndTransferEvent());
        } finally {
            writer.close();
        }
        var validation = new InProcessIlivalidatorService()
                .validate(input, List.of(models), List.of("P43Source"), dir.resolve("source-validation.log"));
        assertThat(validation.valid()).as("%s", validation.logText()).isTrue();

        var request = new MigrationService.Request(mapping, null, null, List.of(models), true, false, report);
        var result = new MigrationService().execute(request, ignored -> {});
        assertThat(result.hasErrors()).as("%s", result.all()).isFalse();
        var reader = new InterlisIoFactory()
                .createReader(output, draft.target().types().getTransferDescription());
        var objects = new ArrayList<IomObject>();
        var baskets = new ArrayList<String>();
        try {
            for (IoxEvent event; (event = reader.read()) != null; ) {
                if (event instanceof ObjectEvent object) objects.add(object.getIomObject());
                if (event instanceof StartBasketEvent basket) baskets.add(basket.getBid());
                if (event instanceof EndTransferEvent) break;
            }
        } finally {
            reader.close();
        }
        assertThat(baskets).containsExactly("items");
        assertThat(objects)
                .extracting(
                        IomObject::getobjecttag,
                        IomObject::getobjectoid,
                        obj -> obj.getattrvalue("Name"),
                        obj -> obj.getattrvalue("Origin"))
                .containsExactlyInAnyOrder(
                        tuple("P43Target.Data.Item", "i1", "First", "original"),
                        tuple("P43Target.Data.Item", "i2", "Second", "original"));

        var losses = new ObjectMapper()
                .readValue(report.resolve("lossiness-report.json").toFile(), LossEvent[].class);
        assertThat(losses)
                .containsExactlyInAnyOrder(
                        new LossEvent(
                                "discard", "P43Source.Data.Obsolete", "old1", "s", "CLASS_REMOVED", "Obsolete class"),
                        new LossEvent(
                                "discard", "P43Source.Data.Obsolete", "old2", "s", "CLASS_REMOVED", "Obsolete class"),
                        new LossEvent(
                                "migrate",
                                "P43Source.Data.Item",
                                "i1",
                                "s.LegacyCode",
                                "ATTRIBUTE_REMOVED",
                                "Legacy codes were retired"));
        assertThat(Files.readString(report.resolve("lossiness-report.md")))
                .contains(
                        "| discard | P43Source.Data.Obsolete | old1 | s | CLASS_REMOVED | Obsolete class |",
                        "| discard | P43Source.Data.Obsolete | old2 | s | CLASS_REMOVED | Obsolete class |",
                        "| migrate | P43Source.Data.Item | i1 | s.LegacyCode | ATTRIBUTE_REMOVED | Legacy codes were retired |");
    }

    @Test
    void migrationPreservesReferencesCurvesXyzAndNestedOccurrences() throws Exception {
        String models = Path.of("src/test/data/migration").toAbsolutePath().toString();
        Path input = dir.resolve("source.xtf"),
                output = dir.resolve("target.xtf"),
                mapping = dir.resolve("migration.ilimap");
        var draft = new MigrationDraftService()
                .create("MigrationDemo_V1", "MigrationDemo_V2", models, input.toString(), output.toString());
        var document = new IlimapEditorDocument(draft.text());
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
