package guru.interlis.transformer.model;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxWriter;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.testutil.TransferDatasetDescriptor;
import guru.interlis.transformer.testutil.TransferFormat;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;
import guru.interlis.transformer.validation.TransferValidationService;
import guru.interlis.transformer.validation.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ConnectedSubgraphExtractorTest {

    private static final String MODEL_DIR = "src/test/data/models/";
    private static final String MODEL_NAME = "ExtractTest";

    private static final String OID_P1 = "550e8400-e29b-41d4-a716-446655440001";
    private static final String OID_P2 = "550e8400-e29b-41d4-a716-446655440002";
    private static final String OID_C1 = "550e8400-e29b-41d4-a716-446655440101";
    private static final String OID_C2 = "550e8400-e29b-41d4-a716-446655440102";
    private static final String OID_C3 = "550e8400-e29b-41d4-a716-446655440103";
    private static final String BID = "550e8400-e29b-41d4-a716-446655440999";

    private static IliModelService modelService;
    private static TransferDescription transferDescription;
    private static TypeSystemFacade facade;

    private ConnectedSubgraphExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpClass() {
        modelService = new IliModelService();
        IliModelCompileResult result = modelService.compileModel(MODEL_NAME, MODEL_DIR);
        assertThat(result.hasErrors())
                .as("Model compilation: " + result.diagnostics())
                .isFalse();
        transferDescription = result.transferDescription();
        facade = new TypeSystemFacade(transferDescription);
    }

    @BeforeEach
    void setUp() {
        extractor = new ConnectedSubgraphExtractor(modelService);
    }

    private Path createTestXtf() throws Exception {
        Path xtfPath = tempDir.resolve("test-extraction.xtf");
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxWriter writer = ioFactory.createWriter(xtfPath, transferDescription);

        writer.write(new ch.interlis.iox_j.StartTransferEvent(MODEL_NAME, null, null));
        writer.write(new ch.interlis.iox_j.StartBasketEvent("ExtractTest.ExtractTopic", BID));

        writeParent(writer, OID_P1, "Parent One");
        writeParent(writer, OID_P2, "Parent Two");
        writeChild(writer, OID_C1, "Child One", OID_P1);
        writeChild(writer, OID_C2, "Child Two", OID_P2);
        writeChild(writer, OID_C3, "Child Three", null);

        writer.write(new ch.interlis.iox_j.EndBasketEvent());
        writer.write(new ch.interlis.iox_j.EndTransferEvent());
        writer.flush();
        writer.close();
        return xtfPath;
    }

    private void writeParent(IoxWriter writer, String oid, String name) throws Exception {
        Iom_jObject obj = new Iom_jObject("ExtractTest.ExtractTopic.Parent", oid);
        obj.setattrvalue("Name", name);
        writer.write(new ch.interlis.iox_j.ObjectEvent(obj));
    }

    private void writeChild(IoxWriter writer, String oid, String name, String parentOid) throws Exception {
        Iom_jObject obj = new Iom_jObject("ExtractTest.ExtractTopic.Child", oid);
        obj.setattrvalue("Name", name);
        if (parentOid != null) {
            Iom_jObject ref = new Iom_jObject(Iom_jObject.REF, null);
            ref.setobjectrefoid(parentOid);
            obj.addattrobj("ParentRef", ref);
        }
        writer.write(new ch.interlis.iox_j.ObjectEvent(obj));
    }

    private TransferDatasetDescriptor descriptorFor(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase();
        TransferFormat fmt = name.endsWith(".itf") ? TransferFormat.ITF : TransferFormat.XTF;
        return new TransferDatasetDescriptor(
                filePath.getFileName().toString(),
                filePath.toAbsolutePath(),
                fmt,
                List.of(MODEL_NAME),
                List.of(MODEL_DIR),
                filePath.toFile().length()
        );
    }

    private ExtractedTransfer runExtraction(List<String> targets, int depth, int maxObjs,
                                              boolean bidirectional) throws Exception {
        Path srcPath = createTestXtf();
        TransferDatasetDescriptor source = descriptorFor(srcPath);
        ExtractionRequest request = new ExtractionRequest(
                targets, List.of(MODEL_DIR), depth, maxObjs, bidirectional, tempDir);
        return extractor.extract(source, request);
    }

    @Test
    void extractsTargetClassWithReferences() throws Exception {
        ExtractedTransfer result = runExtraction(List.of("Child"), 1, 50, true);

        assertThat(result.totalObjects()).isGreaterThanOrEqualTo(4);
        assertThat(result.includedClasses()).contains("ExtractTest.ExtractTopic.Child");
        assertThat(result.includedClasses()).contains("ExtractTest.ExtractTopic.Parent");
        assertThat(result.transferFile()).exists();
    }

    @Test
    void respectsMaxDepth() throws Exception {
        ExtractedTransfer result = runExtraction(List.of("Child"), 0, 50, false);

        assertThat(result.totalObjects()).isEqualTo(3);
        assertThat(result.includedClasses()).contains("ExtractTest.ExtractTopic.Child");
        assertThat(result.includedClasses()).doesNotContain("ExtractTest.ExtractTopic.Parent");
    }

    @Test
    void bidirectionalIncludesReverseReferences() throws Exception {
        ExtractedTransfer result = runExtraction(List.of("Child"), 1, 50, true);

        assertThat(result.includedClasses()).contains("ExtractTest.ExtractTopic.Parent");
    }

    @Test
    void provenanceContainsDetails() throws Exception {
        ExtractedTransfer result = runExtraction(List.of("Child"), 2, 100, false);

        assertThat(result.provenance()).contains("Extracted from", "Child", "Max depth: 2");
    }

    @Test
    void noMatchingSeedReturnsZeroObjects() throws Exception {
        ExtractedTransfer result = runExtraction(List.of("NonExistentClass"), 2, 100, false);

        assertThat(result.totalObjects()).isEqualTo(0);
    }

    @Test
    void outputIsValidTransfer() throws Exception {
        ExtractedTransfer result = runExtraction(List.of("Child"), 1, 50, true);

        TransferValidationService validator = new InProcessIlivalidatorService();
        Path logFile = tempDir.resolve("validation.log");
        ValidationResult validation = validator.validate(
                result.transferFile(),
                List.of(MODEL_DIR),
                List.of(MODEL_NAME),
                logFile
        );

        assertThat(validation.valid())
                .as("Extracted transfer must be valid. Log: " + validation.logText())
                .isTrue();
    }
}
