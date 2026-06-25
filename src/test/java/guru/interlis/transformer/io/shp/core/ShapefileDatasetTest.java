package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileDatasetTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesAllSidecarsWhenAllPresent() throws Exception {
        createFile("parcels.shp");
        createFile("parcels.dbf");
        createFile("parcels.shx");
        createFile("parcels.prj");
        createFile("parcels.cpg");

        ShapefileDataset dataset = ShapefileDataset.fromPath(path("parcels.shp"), false);

        assertThat(dataset.shp()).isEqualTo(path("parcels.shp"));
        assertThat(dataset.dbf()).isEqualTo(path("parcels.dbf"));
        assertThat(dataset.shx()).isEqualTo(path("parcels.shx"));
        assertThat(dataset.prj()).hasValue(path("parcels.prj"));
        assertThat(dataset.cpg()).hasValue(path("parcels.cpg"));
    }

    @Test
    void resolvesOnlyRequiredSidecarsWhenOptionalMissing() throws Exception {
        createFile("data.shp");
        createFile("data.dbf");

        ShapefileDataset dataset = ShapefileDataset.fromPath(path("data.shp"), false);

        assertThat(dataset.shp()).isEqualTo(path("data.shp"));
        assertThat(dataset.dbf()).isEqualTo(path("data.dbf"));
        assertThat(dataset.shx()).isNull();
        assertThat(dataset.prj()).isEmpty();
        assertThat(dataset.cpg()).isEmpty();
    }

    @Test
    void returnsBaseName() throws Exception {
        createFile("parcels.shp");
        createFile("parcels.dbf");

        ShapefileDataset dataset = ShapefileDataset.fromPath(path("parcels.shp"), false);
        assertThat(dataset.baseName()).isEqualTo("parcels");
    }

    @Test
    void returnsBaseNameWithoutDotsInPath() throws Exception {
        Path dir = tempDir.resolve("sub.dir");
        Files.createDirectories(dir);
        createFile("sub.dir/mydata.shp");
        createFile("sub.dir/mydata.dbf");

        ShapefileDataset dataset = ShapefileDataset.fromPath(dir.resolve("mydata.shp"), false);
        assertThat(dataset.baseName()).isEqualTo("mydata");
    }

    @Test
    void rejectsMissingShpFile() {
        assertThatThrownBy(() -> ShapefileDataset.fromPath(path("nonexistent.shp"), false))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsMissingDbfFile() throws Exception {
        createFile("alone.shp");

        assertThatThrownBy(() -> ShapefileDataset.fromPath(path("alone.shp"), false))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("dbf")
                .hasMessageContaining("alone.shp");
    }

    @Test
    void acceptsMissingShxWhenNotRequired() throws Exception {
        createFile("nosx.shp");
        createFile("nosx.dbf");

        ShapefileDataset dataset = ShapefileDataset.fromPath(path("nosx.shp"), false);
        assertThat(dataset.shx()).isNull();
    }

    @Test
    void rejectsMissingShxWhenRequired() throws Exception {
        createFile("needsx.shp");
        createFile("needsx.dbf");

        assertThatThrownBy(() -> ShapefileDataset.fromPath(path("needsx.shp"), true))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("shx")
                .hasMessageContaining("needsx.shp");
    }

    @Test
    void findsSidecarsCaseInsensitively() throws Exception {
        createFile("UPPER.SHP");
        createFile("upper.DBF");
        createFile("Upper.SHX");
        createFile("upper.CPG");

        ShapefileDataset dataset = ShapefileDataset.fromPath(path("UPPER.SHP"), false);

        assertThat(dataset.shp()).isEqualTo(path("UPPER.SHP"));
        assertThat(dataset.dbf().getFileName().toString().toLowerCase()).isEqualTo("upper.dbf");
        assertThat(dataset.shx().getFileName().toString().toLowerCase()).isEqualTo("upper.shx");
        assertThat(dataset.cpg()).isPresent();
        assertThat(dataset.cpg().get().getFileName().toString().toLowerCase()).isEqualTo("upper.cpg");
    }

    @Test
    void exactSidecarMatchSucceedsWhenSimilarNamedFilesExist() throws Exception {
        createFile("data.shp");
        createFile("data.dbf");
        createFile("data2.shp");
        createFile("data2.dbf");

        ShapefileDataset dataset = ShapefileDataset.fromPath(path("data.shp"), false);
        assertThat(dataset.dbf().getFileName().toString()).isEqualTo("data.dbf");
    }

    @Test
    void resolvesPathRelativeToWorkingDirectory() throws Exception {
        Path shp = tempDir.resolve("workdir.shp");
        createFile("workdir.shp");
        createFile("workdir.dbf");

        ShapefileDataset dataset = ShapefileDataset.fromPath(shp, false);
        assertThat(dataset.dbf()).isNotNull();
    }

    @Test
    void errorMessageForMissingDbfIncludesSidecarName() throws Exception {
        createFile("mydata.shp");

        assertThatThrownBy(() -> ShapefileDataset.fromPath(path("mydata.shp"), false))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("mydata.dbf")
                .hasMessageContaining("mydata.shp");
    }

    @Test
    void errorMessageForMissingRequiredShxIncludesSidecarName() throws Exception {
        createFile("mydata.shp");
        createFile("mydata.dbf");

        assertThatThrownBy(() -> ShapefileDataset.fromPath(path("mydata.shp"), true))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("mydata.shx")
                .hasMessageContaining("mydata.shp");
    }

    private void createFile(String relativePath) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }

    private Path path(String relativePath) {
        return tempDir.resolve(relativePath);
    }
}
