package guru.interlis.transformer;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.*;
import guru.interlis.transformer.app.IlivalidatorRunner;
import guru.interlis.transformer.app.IlivalidatorRunner.ValidationResult;
import guru.interlis.transformer.expr.*;
import guru.interlis.transformer.geometry.IoxGeometryAdapter;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.*;
import guru.interlis.transformer.support.TestGeometries;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class IlivalidatorIntegrationTest {

    @Test
    void validateDmavLfp3XtfOutput() throws Exception {
        var svc = new IliModelService();
        var r = svc.compileModel("DM01AVCH24LV95D",
                "src/test/data/av/models/;https://models.interlis.ch");
        if (r.hasErrors()) fail("DM01 compile: " + r.diagnostics().all().get(0).message());
        var td = r.transferDescription();

        // Write a minimal LFP3 XTF (simplified — just geometry passthrough)
        IoxGeometryAdapter adapter = new IoxGeometryAdapter();

        Iom_jObject lfp = new Iom_jObject("DM01AVCH24LV95D.FixpunkteKategorie3.LFP3", "1");
        lfp.setattrvalue("NBIdent", "LFP001");
        lfp.setattrvalue("Nummer", "12345");
        IomObject geom = adapter.denormalize(
                new CoordValue(2600000.0, 1200000.0), TypeInfo.COORD);
        lfp.addattrobj("Geometrie", geom);

        Path xtfPath = Files.createTempFile("dmav-lfp3-", ".xtf");
        try {
            // Write as DMAV XTF using the DM01 model as target (for validation)
            var writer = new ch.interlis.iom_j.itf.ItfWriter(xtfPath.toFile(), td);
            writer.write(new StartTransferEvent("t", null, null));
            writer.write(new StartBasketEvent("DM01AVCH24LV95D.FixpunkteKategorie3", "b1"));
            writer.write(new ObjectEvent(lfp));
            writer.write(new EndBasketEvent());
            writer.write(new EndTransferEvent());
            writer.flush(); writer.close();

            // Validate against DM01 model
            ValidationResult result = IlivalidatorRunner.validate(
                    xtfPath,
                    List.of("src/test/data/av/models/;https://models.interlis.ch"),
                    "DM01AVCH24LV95D",
                    null);
            System.out.println("LFP3 validation: " + result.success());
            if (!result.log().isBlank()) System.out.println(result.log());
        } finally { Files.deleteIfExists(xtfPath); }
    }

    @Test
    void validateSurfaceItfOutput() throws Exception {
        var svc = new IliModelService();
        var r = svc.compileModel("DM01AVCH24LV95D",
                "src/test/data/av/models/;https://models.interlis.ch");
        if (r.hasErrors()) fail("DM01 compile: " + r.diagnostics().all().get(0).message());
        var td = r.transferDescription();

        IoxGeometryAdapter adapter = new IoxGeometryAdapter();
        IomObject geom = adapter.denormalize(
                new GeometryObjectValue(TypeInfo.SURFACE, TestGeometries.surface(TestGeometries.boundary(
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
            itfWriter.flush(); itfWriter.close();

            Path logFile = Files.createTempFile("ilival-", ".log");
            try {
                ValidationResult result = IlivalidatorRunner.validate(
                        p,
                        List.of("src/test/data/av/models/;https://models.interlis.ch"),
                        "DM01AVCH24LV95D",
                        logFile);
                System.out.println("SURFACE validation: " + result.success());
                if (Files.exists(logFile)) {
                    System.out.println(Files.readString(logFile).substring(0,
                            Math.min(500, (int) Files.size(logFile))));
                }
            } finally { Files.deleteIfExists(logFile); }
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void validationRejectsInvalidFile() throws Exception {
        Path badFile = Files.createTempFile("bad-", ".xtf");
        try {
            Files.writeString(badFile, "this is not a valid XTF file");
            ValidationResult result = IlivalidatorRunner.validate(
                    badFile,
                    List.of("src/test/data/av/models/"),
                    "DM01AVCH24LV95D",
                    null);
            assertThat(result.success()).isFalse().as("Invalid file should fail validation");
        } finally { Files.deleteIfExists(badFile); }
    }
}
