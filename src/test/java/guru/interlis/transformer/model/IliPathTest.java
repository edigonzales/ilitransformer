package guru.interlis.transformer.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IliPathTest {

    @Test
    void parsesFullPath() {
        IliPath path = IliPath.parse("Model.Topic.Class.Attribute");
        assertThat(path.model()).isEqualTo("Model");
        assertThat(path.topic()).isEqualTo("Topic");
        assertThat(path.className()).isEqualTo("Class");
        assertThat(path.member()).isEqualTo("Attribute");
        assertThat(path.length()).isEqualTo(4);
    }

    @Test
    void parsesClassPath() {
        IliPath path = IliPath.parse("Model.Topic.Class");
        assertThat(path.model()).isEqualTo("Model");
        assertThat(path.topic()).isEqualTo("Topic");
        assertThat(path.className()).isEqualTo("Class");
        assertThat(path.member()).isNull();
        assertThat(path.length()).isEqualTo(3);
    }

    @Test
    void parsesDmafPath() {
        IliPath path = IliPath.parse("DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3.NBIdent");
        assertThat(path.model()).isEqualTo("DMAV_FixpunkteAVKategorie3_V1_1");
        assertThat(path.topic()).isEqualTo("FixpunkteAVKategorie3");
        assertThat(path.className()).isEqualTo("LFP3");
        assertThat(path.member()).isEqualTo("NBIdent");
    }

    @Test
    void parsesPathWithRole() {
        IliPath path = IliPath.parse("Model.Topic.Class.RoleName");
        assertThat(path.length()).isEqualTo(4);
        assertThat(path.member()).isEqualTo("RoleName");
    }

    @Test
    void trimsWhitespace() {
        IliPath path = IliPath.parse(" Model . Topic . Class ");
        assertThat(path.model()).isEqualTo("Model");
        assertThat(path.topic()).isEqualTo("Topic");
        assertThat(path.className()).isEqualTo("Class");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> IliPath.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> IliPath.parse("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> IliPath.parse("Model.Topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least Model.Topic.Class");
    }

    @Test
    void rejectsEmptySegment() {
        assertThatThrownBy(() -> IliPath.parse("Model..Class"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty segment");
    }

    @Test
    void qualifiedClassReturnsCorrectPath() {
        IliPath path = IliPath.parse("DMAV.Test.LFP3.NBIdent");
        assertThat(path.qualifiedClass()).isEqualTo("DMAV.Test.LFP3");
    }

    @Test
    void partsAreImmutable() {
        IliPath path = IliPath.parse("A.B.C.D");
        assertThat(path.parts()).containsExactly("A", "B", "C", "D");
        assertThatThrownBy(() -> path.parts().add("X")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalsAndHashCode() {
        IliPath a = IliPath.parse("M.T.C");
        IliPath b = IliPath.parse("M.T.C");
        IliPath c = IliPath.parse("M.T.X");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void toStringReturnsOriginalFormat() {
        assertThat(IliPath.parse("A.B.C").toString()).isEqualTo("A.B.C");
        assertThat(IliPath.parse("A.B.C.d").toString()).isEqualTo("A.B.C.d");
    }
}
