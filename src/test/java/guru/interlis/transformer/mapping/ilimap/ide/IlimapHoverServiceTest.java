package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class IlimapHoverServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));
    private static final IlimapAnalysisOptions MODEL_AWARE_OPTIONS = IlimapAnalysisOptions.modelAware(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapHoverService hoverService = new IlimapHoverService();

    @Test
    void hoverShowsRuleSummary() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapHover> hover =
                hoverService.hoverAt(analysis, positionAt(analysis, "rule r1", "rule r".length()));

        assertThat(hover).isPresent();
        assertThat(hover.get().markdown())
                .contains("**rule `r1`**")
                .contains("Target: `out`")
                .contains("Class: `M.A`")
                .contains("Sources: `s`")
                .contains("Assignments: 1")
                .contains("Bags: 1")
                .contains("Refs: 1");
    }

    @Test
    void hoverShowsInputSummary() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapHover> hover =
                hoverService.hoverAt(analysis, positionAt(analysis, "from src class", "from sr".length()));

        assertThat(hover).isPresent();
        assertThat(hover.get().markdown())
                .contains("**input `src`**")
                .contains("Path: `in.xtf`")
                .contains("Model: `M`")
                .contains("Format: `xtf`")
                .contains("Allowed fields: `path`, `model`, `format`, `option`, `connection`, `query`");
    }

    @Test
    void hoverShowsOutputSummary() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapHover> hover =
                hoverService.hoverAt(analysis, positionAt(analysis, "target out class", "target ou".length()));

        assertThat(hover).isPresent();
        assertThat(hover.get().markdown())
                .contains("**output `out`**")
                .contains("Path: `out.xtf`")
                .contains("Model: `M`")
                .contains("Format: `xtf`")
                .contains("Allowed fields: `path`, `model`, `format`, `option`");
    }

    @Test
    void hoverShowsJobAllowedFields() {
        IlimapAnalysis analysis = analyze(mappingWithJob());

        Optional<IlimapHover> hover = hoverService.hoverAt(analysis, positionAt(analysis, "job {", "job".length()));

        assertThat(hover).isPresent();
        assertThat(hover.get().markdown())
                .contains("**job**")
                .contains("Fail Policy: `strict`")
                .contains("Allowed fields:")
                .contains("`modeldir`");
    }

    @Test
    void hoverShowsEnumMapSummary() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapHover> hover = hoverService.hoverAt(
                analysis, positionAt(analysis, "enumMap(s.X, Quality)", "enumMap(s.X, Qual".length()));

        assertThat(hover).isPresent();
        assertThat(hover.get().markdown())
                .contains("**enum `Quality`**")
                .contains("Entries: 2")
                .contains("- `\"old\"` => `\"new\"`")
                .contains("- `\"bad\"` => `\"good\"`");
    }

    @Test
    void hoverShowsSourceAliasClassAndMembers() {
        IlimapAnalysis analysis =
                analysisService.analyze("file:///test.ilimap", associationMapping(), MODEL_AWARE_OPTIONS);

        Optional<IlimapHover> hover =
                hoverService.hoverAt(analysis, positionAt(analysis, "sourceRef c.ChildRole", "sourceRef c".length()));

        assertThat(hover).isPresent();
        assertThat(hover.get().markdown())
                .contains("**source `c`**")
                .contains("Input: `src`")
                .contains("Class: `AssocModel.AssocTopic.Child`")
                .contains("Attributes: `Name`, `Wert`")
                .contains("Roles:")
                .contains("`ChildRole`");
    }

    @Test
    void hoverShowsSourceRoleMember() {
        IlimapAnalysis analysis =
                analysisService.analyze("file:///test.ilimap", associationMapping(), MODEL_AWARE_OPTIONS);

        Optional<IlimapHover> hover = hoverService.hoverAt(
                analysis, positionAt(analysis, "sourceRef c.ChildRole", "sourceRef c.Child".length()));

        assertThat(hover).isPresent();
        assertThat(hover.get().markdown())
                .contains("**role `c.ChildRole`**")
                .contains("Association: `ParentChild`")
                .contains("Cardinality: `0..*`");
    }

    @Test
    void returnsEmptyForUnknownSymbol() {
        String source = validMapping().replace("enumMap(s.X, Quality)", "coalesce(s.X, Quality)");
        IlimapAnalysis analysis = analyze(source);

        Optional<IlimapHover> hover = hoverService.hoverAt(
                analysis, positionAt(analysis, "coalesce(s.X, Quality)", "coalesce(s.X, Qual".length()));

        assertThat(hover).isEmpty();
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static IlimapIdePosition positionAt(IlimapAnalysis analysis, String needle, int cursorDelta) {
        int offset = analysis.text().indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        return analysis.lineMap().toIdePosition(offset + cursorDelta);
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  enum Quality {
                    "old" -> "new";
                    "bad" -> "good";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = enumMap(s.X, Quality);
                    }
                    bag Parts {
                      from p in src class "M.Part";
                    }
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }

    private static String mappingWithJob() {
        return """
                mapping v2 {
                  job {
                    modeldir "models";
                    failPolicy strict;
                  }
                  input src { path "in.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String associationMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "AssocModel"; format xtf; }
                  output out { path "out.xtf"; model "AssocModel"; format xtf; }
                  rule rParent {
                    target out class "AssocModel.AssocTopic.Parent";
                    source p from src class "AssocModel.AssocTopic.Parent";
                  }
                  rule rChild {
                    target out class "AssocModel.AssocTopic.Child";
                    source c from src class "AssocModel.AssocTopic.Child";
                    ref ParentRole {
                      target rule rParent sourceRef c.ChildRole;
                    }
                  }
                }
                """;
    }
}
