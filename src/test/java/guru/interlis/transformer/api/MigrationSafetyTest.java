package guru.interlis.transformer.api;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;

import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MigrationSafetyTest {
    @TempDir
    Path dir;

    Path input, output, mapping;
    MigrationService.Request request;

    @BeforeEach
    void prepare() throws Exception {
        Path fixture = Path.of("src/test/data/migration");
        Files.copy(fixture.resolve("MigrationDemo.ili"), dir.resolve("MigrationDemo.ili"));
        input = Files.copy(fixture.resolve("source.xtf"), dir.resolve("source.xtf"));
        output = dir.resolve("target.xtf");
        mapping = dir.resolve("mapping.ilimap");
        var draft = new MigrationDraftService()
                .create("MigrationDemo_V1", "MigrationDemo_V2", dir.toString(), input.toString(), output.toString());
        var document =
                new IlimapEditorDocument(draft.text().replace("migrationReviewed false", "migrationReviewed true"));
        String rule = MigrationDraftService.ruleId("MigrationDemo_V1.Data.Line");
        document.assign(rule, "", "Name", "s.Label");
        document.assign(rule, "", "Origin", "\"migration\"");
        document.assign(rule, "", "Status", "if(s.Status == #active, #operational, #closed)");
        Files.writeString(mapping, document.text());
        Files.writeString(output, "previous target");
        request = new MigrationService.Request(mapping, null, null, List.of(dir.toString()), true, true, null);
    }

    @Test
    void rejectsModelDriftAndKeepsPreviousOutput() throws Exception {
        Files.writeString(dir.resolve("MigrationDemo.ili"), "\n!! changed model\n", StandardOpenOption.APPEND);
        var result = new MigrationService().execute(request, s -> {});
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.all().toString()).containsIgnoringCase("fingerprint");
        unchangedOutputAndNoTemporaryFiles();
    }

    @Test
    void sourceChangeDuringExecutionPreventsPublication() {
        assertThatThrownBy(() -> new MigrationService().execute(request, phase -> {
                    if (phase.equals("Writing transfer")) {
                        try {
                            Files.writeString(input, "\n", StandardOpenOption.APPEND);
                        } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                    }
                }))
                .hasMessageContaining("Original transfer changed");
        unchangedOutputAndNoTemporaryFiles();
    }

    @Test
    void invalidTargetValuesKeepPreviousOutput() throws Exception {
        var document = new IlimapEditorDocument(Files.readString(mapping));
        document.assign(
                MigrationDraftService.ruleId("MigrationDemo_V1.Data.Line"),
                "",
                "Origin",
                IlimapEditorDocument.quote("x".repeat(100)));
        Files.writeString(mapping, document.text());
        var result = new MigrationService().execute(request, s -> {});
        assertThat(result.hasErrors()).isTrue();
        unchangedOutputAndNoTemporaryFiles();
    }

    @Test
    void cancellationPreventsPublicationAndCleansTemporaryFiles() throws Exception {
        try {
            try {
                var result = new MigrationService().execute(request, phase -> {
                    if (phase.equals("Writing transfer")) Thread.currentThread().interrupt();
                });
                assertThat(result.hasErrors()).isTrue();
            } catch (java.util.concurrent.CancellationException expected) {
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            }
        } finally {
            Thread.interrupted();
        }
        unchangedOutputAndNoTemporaryFiles();
    }

    @Test
    void truncatedTransferIsRejectedEvenWithValidationDisabled() throws Exception {
        String xml = Files.readString(input);
        Files.writeString(input, xml.substring(0, xml.lastIndexOf("</")));
        var result = new MigrationService()
                .execute(
                        new MigrationService.Request(mapping, null, null, List.of(dir.toString()), false, true, null),
                        s -> {});
        assertThat(result.hasErrors()).isTrue();
        unchangedOutputAndNoTemporaryFiles();
    }

    private void unchangedOutputAndNoTemporaryFiles() {
        assertThat(output).hasContent("previous target");
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("source.xtf", "target.xtf", "mapping.ilimap", "MigrationDemo.ili");
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }
}
