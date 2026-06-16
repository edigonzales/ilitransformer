package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.app.IlivalidatorRunner;
import guru.interlis.transformer.app.IlivalidatorRunner.ValidationResult;
import guru.interlis.transformer.expr.*;
import guru.interlis.transformer.geometry.IoxGeometryAdapter;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.*;
import guru.interlis.transformer.support.TestGeometries;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox_j.*;

import java.nio.file.*;
import java.util.List;

import org.junit.jupiter.api.*;

class IlivalidatorIntegrationTest {

    @Test
    void validateDmavLfp3XtfOutput() throws Exception {
        var svc = new IliModelService();
        var r = svc.compileModel("DM01AVCH24LV95D", "src/test/data/av/models/;https://models.interlis.ch");
        if (r.hasErrors()) fail("DM01 compile: " + r.diagnostics().all().get(0).message());
        var td = r.transferDescription();

        Iom_jObject nf = new Iom_jObject("DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung", "nf1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test");

        Path filePath = Files.createTempFile("dm01-output-", ".itf");
        try {
            var writer = new ch.interlis.iom_j.itf.ItfWriter(filePath.toFile(), td);
            writer.write(new StartTransferEvent("t", null, null));
            writer.write(new StartBasketEvent("DM01AVCH24LV95D.FixpunkteKategorie3", "b1"));
            writer.write(new ObjectEvent(nf));
            writer.write(new EndBasketEvent());
            writer.write(new EndTransferEvent());
            writer.flush();
            writer.close();

            ValidationResult result = IlivalidatorRunner.validate(
                    filePath, List.of("src/test/data/av/models/;https://models.interlis.ch"), "DM01AVCH24LV95D", null);
            assertThat(result.success()).as("DM01 ITF output validation").isTrue();
        } finally {
            Files.deleteIfExists(filePath);
        }
    }

    @Test
    void validateSurfaceItfOutput() throws Exception {
        var svc = new IliModelService();
        var r = svc.compileModel("DM01AVCH24LV95D", "src/test/data/av/models/;https://models.interlis.ch");
        if (r.hasErrors()) fail("DM01 compile: " + r.diagnostics().all().get(0).message());
        var td = r.transferDescription();

        IoxGeometryAdapter adapter = new IoxGeometryAdapter();
        IomObject geom = adapter.denormalize(
                new GeometryObjectValue(
                        TypeInfo.SURFACE,
                        TestGeometries.surface(TestGeometries.boundary(
                                TestGeometries.coord(2600000.0, 1200000.0),
                                TestGeometries.coord(2600100.0, 1200000.0),
                                TestGeometries.coord(2600100.0, 1200100.0),
                                TestGeometries.coord(2600000.0, 1200100.0),
                                TestGeometries.coord(2600000.0, 1200000.0)))),
                TypeInfo.SURFACE);

        Iom_jObject nf = new Iom_jObject("DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung", "1");
        nf.setattrvalue("NBIdent", "NF001");
        nf.setattrvalue("Identifikator", "ID001");
        nf.setattrvalue("Beschreibung", "Test");
        nf.addattrobj("Perimeter", geom);

        Path p = Files.createTempFile("dm01-surface-val-", ".itf");
        try {
            var itfWriter = new ch.interlis.iom_j.itf.ItfWriter(p.toFile(), td);
            itfWriter.write(new StartTransferEvent("t", null, null));
            itfWriter.write(new StartBasketEvent("DM01AVCH24LV95D.FixpunkteKategorie3", "b1"));
            itfWriter.write(new ObjectEvent(nf));
            itfWriter.write(new EndBasketEvent());
            itfWriter.write(new EndTransferEvent());
            itfWriter.flush();
            itfWriter.close();

            Path logFile = Files.createTempFile("ilival-", ".log");
            try {
                ValidationResult result = IlivalidatorRunner.validate(
                        p, List.of("src/test/data/av/models/;https://models.interlis.ch"), "DM01AVCH24LV95D", logFile);
                assertThat(result.success()).as("Surface ITF validation").isTrue();
            } finally {
                Files.deleteIfExists(logFile);
            }
        } finally {
            Files.deleteIfExists(p);
        }
    }

    @Test
    void validationRejectsInvalidFile() throws Exception {
        Path badFile = Files.createTempFile("bad-", ".xtf");
        try {
            Files.writeString(badFile, "this is not a valid XTF file");
            ValidationResult result =
                    IlivalidatorRunner.validate(badFile, List.of("src/test/data/av/models/"), "DM01AVCH24LV95D", null);
            assertThat(result.success()).isFalse().as("Invalid file should fail validation");
        } finally {
            Files.deleteIfExists(badFile);
        }
    }
}
