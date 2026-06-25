package guru.interlis.transformer.io.shp.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.ShapefileOptions.FieldNameStrategy;
import guru.interlis.transformer.io.shp.mapping.DbfNameMapper.DbfNameMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbfNameMapperTest {

    @Test
    void strictKeepsValidNames() throws Exception {
        DbfNameMapping mapping = DbfNameMapper.create(List.of("bfsnr", "name"), FieldNameStrategy.STRICT);

        assertThat(mapping.attributeToDbf()).containsEntry("bfsnr", "bfsnr").containsEntry("name", "name");
        assertThat(mapping.warnings()).isEmpty();
        assertThat(DbfNameMapper.hasRenames(mapping)).isFalse();
    }

    @Test
    void strictRejectsNameLongerThanTenChars() {
        assertThatThrownBy(() -> DbfNameMapper.create(List.of("GemeindenameLang"), FieldNameStrategy.STRICT))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("strict")
                .hasMessageContaining("GemeindenameLang");
    }

    @Test
    void strictRejectsInvalidCharacters() {
        assertThatThrownBy(() -> DbfNameMapper.create(List.of("bad name"), FieldNameStrategy.STRICT))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("valid DBF field name");
    }

    @Test
    void strictRejectsCaseInsensitiveCollision() {
        assertThatThrownBy(() -> DbfNameMapper.create(List.of("Name", "NAME"), FieldNameStrategy.STRICT))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("collide");
    }

    @Test
    void truncateShortensToTenChars() throws Exception {
        DbfNameMapping mapping = DbfNameMapper.create(List.of("GemeindenameLang"), FieldNameStrategy.TRUNCATE);

        assertThat(mapping.attributeToDbf().get("GemeindenameLang")).isEqualTo("Gemeindena");
        assertThat(mapping.warnings()).hasSize(1);
        assertThat(DbfNameMapper.hasRenames(mapping)).isTrue();
    }

    @Test
    void truncateRejectsCollision() {
        assertThatThrownBy(() -> DbfNameMapper.create(
                        List.of("GemeindenameKurz", "GemeindenameLang"), FieldNameStrategy.TRUNCATE))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("stable");
    }

    @Test
    void stableDeconflictsWithNumericSuffix() throws Exception {
        DbfNameMapping mapping =
                DbfNameMapper.create(List.of("GemeindenameKurz", "GemeindenameLang"), FieldNameStrategy.STABLE);

        List<String> dbfNames = mapping.attributeToDbf().values().stream().toList();
        assertThat(dbfNames).containsExactly("Gemeindena", "Gemeinde_1");
        assertThat(dbfNames.get(0)).hasSizeLessThanOrEqualTo(10);
        assertThat(dbfNames.get(1)).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void writesSidecarJson(@TempDir Path dir) throws Exception {
        DbfNameMapping mapping = DbfNameMapper.create(List.of("GemeindenameLang"), FieldNameStrategy.STABLE);
        Path sidecar = dir.resolve("parcels.iliattr.json");

        DbfNameMapper.writeSidecar(sidecar, mapping);

        String json = Files.readString(sidecar);
        assertThat(json).contains(DbfNameMapper.SIDECAR_FORMAT);
        assertThat(json).contains("\"attribute\" : \"GemeindenameLang\"");
        assertThat(json).contains("\"dbf\" : \"Gemeindena\"");
    }
}
