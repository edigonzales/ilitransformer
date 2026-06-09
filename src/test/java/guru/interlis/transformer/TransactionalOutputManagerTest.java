package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.app.TransactionalOutputManager;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.TransferFormat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransactionalOutputManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createTemporaryOutputCreatesTempFile() throws Exception {
        Path target = tempDir.resolve("output.xtf");
        OutputBinding binding = new OutputBinding("out1", target, "TestModel",
                TransferFormat.XTF, null, null);

        try (TransactionalOutputManager tx = new TransactionalOutputManager(false)) {
            Path tempPath = tx.createTemporaryOutput(binding);
            assertThat(tempPath).exists();
            assertThat(tempPath).isNotEqualTo(target);
            assertThat(tempPath.getFileName().toString()).endsWith(".xtf");
        }
    }

    @Test
    void commitMovesTempToTarget() throws Exception {
        Path target = tempDir.resolve("committed.xtf");
        OutputBinding binding = new OutputBinding("out1", target, "TestModel",
                TransferFormat.XTF, null, null);

        try (TransactionalOutputManager tx = new TransactionalOutputManager(false)) {
            Path tempPath = tx.createTemporaryOutput(binding);
            Files.writeString(tempPath, "test content");
            tx.commit("out1");
        }

        assertThat(target).exists();
        assertThat(Files.readString(target)).isEqualTo("test content");
    }

    @Test
    void rollbackDeletesTempFiles() throws Exception {
        Path target = tempDir.resolve("rolled-back.xtf");
        OutputBinding binding = new OutputBinding("out1", target, "TestModel",
                TransferFormat.XTF, null, null);

        Path tempPath;
        try (TransactionalOutputManager tx = new TransactionalOutputManager(false)) {
            tempPath = tx.createTemporaryOutput(binding);
            Files.writeString(tempPath, "temp data");
        }

        assertThat(target).doesNotExist();
        assertThat(tempPath).doesNotExist();
    }

    @Test
    void closeRollsBackUncommitted() throws Exception {
        Path target = tempDir.resolve("uncommitted.xtf");
        OutputBinding binding = new OutputBinding("out1", target, "TestModel",
                TransferFormat.XTF, null, null);

        try (TransactionalOutputManager tx = new TransactionalOutputManager(false)) {
            tx.createTemporaryOutput(binding);
            // Don't commit
        }

        assertThat(target).doesNotExist();
    }

    @Test
    void keepTemporaryFilesPreservesTempDir() throws Exception {
        Path target = tempDir.resolve("kept.xtf");
        OutputBinding binding = new OutputBinding("out1", target, "TestModel",
                TransferFormat.XTF, null, null);

        Path tempDirPath;
        try (TransactionalOutputManager tx = new TransactionalOutputManager(true)) {
            Path tempPath = tx.createTemporaryOutput(binding);
            Files.writeString(tempPath, "debug data");
            tempDirPath = tx.tempDir();
        }

        assertThat(target).doesNotExist();
        assertThat(tempDirPath).exists();
    }

    @Test
    void commitWithoutTargetPathThrows() throws Exception {
        OutputBinding binding = new OutputBinding("out1", null, "TestModel",
                TransferFormat.XTF, null, null);

        try (TransactionalOutputManager tx = new TransactionalOutputManager(false)) {
            tx.createTemporaryOutput(binding);
            assertThatThrownBy(() -> tx.commit("out1"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void itfExtensionIsPreserved() throws Exception {
        Path target = tempDir.resolve("output.itf");
        OutputBinding binding = new OutputBinding("out1", target, "TestModel",
                TransferFormat.ITF, null, null);

        try (TransactionalOutputManager tx = new TransactionalOutputManager(false)) {
            Path tempPath = tx.createTemporaryOutput(binding);
            assertThat(tempPath.getFileName().toString()).endsWith(".itf");
        }
    }

    @Test
    void multipleOutputsAreTrackedIndependently() throws Exception {
        Path target1 = tempDir.resolve("out1.xtf");
        Path target2 = tempDir.resolve("out2.itf");

        try (TransactionalOutputManager tx = new TransactionalOutputManager(false)) {
            Path t1 = tx.createTemporaryOutput(
                    new OutputBinding("o1", target1, "M", TransferFormat.XTF, null, null));
            Path t2 = tx.createTemporaryOutput(
                    new OutputBinding("o2", target2, "M", TransferFormat.ITF, null, null));

            Files.writeString(t1, "data1");
            Files.writeString(t2, "data2");

            tx.commit("o1");
        }

        assertThat(target1).exists();
        assertThat(Files.readString(target1)).isEqualTo("data1");
        assertThat(target2).doesNotExist();
    }
}
