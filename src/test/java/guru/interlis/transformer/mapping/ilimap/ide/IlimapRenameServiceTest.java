package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class IlimapRenameServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));
    private static final String URI = "file:///test.ilimap";

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapRenameService renameService = new IlimapRenameService();

    @Test
    void prepareRename_onInputId() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "input src", "input s".length());

        Optional<IlimapRenamePrepareResult> result = renameService.prepareRename(analysis, pos);

        assertThat(result).isPresent();
        assertThat(textAt(analysis, result.get().range())).isEqualTo("src");
        assertThat(result.get().placeholder()).isEqualTo("input id");
    }

    @Test
    void prepareRename_onOutputId() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "output out", "output o".length());

        Optional<IlimapRenamePrepareResult> result = renameService.prepareRename(analysis, pos);

        assertThat(result).isPresent();
        assertThat(textAt(analysis, result.get().range())).isEqualTo("out");
        assertThat(result.get().placeholder()).isEqualTo("output id");
    }

    @Test
    void prepareRename_onRuleId() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "rule r1", "rule r".length());

        Optional<IlimapRenamePrepareResult> result = renameService.prepareRename(analysis, pos);

        assertThat(result).isPresent();
        assertThat(textAt(analysis, result.get().range())).isEqualTo("r1");
        assertThat(result.get().placeholder()).isEqualTo("rule id");
    }

    @Test
    void prepareRename_onEnumId() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "enum Quality", "enum Q".length());

        Optional<IlimapRenamePrepareResult> result = renameService.prepareRename(analysis, pos);

        assertThat(result).isPresent();
        assertThat(textAt(analysis, result.get().range())).isEqualTo("Quality");
        assertThat(result.get().placeholder()).isEqualTo("enum id");
    }

    @Test
    void prepareRename_onSourceAlias() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "source s from", "source s".length());

        Optional<IlimapRenamePrepareResult> result = renameService.prepareRename(analysis, pos);

        assertThat(result).isPresent();
        assertThat(textAt(analysis, result.get().range())).isEqualTo("s");
        assertThat(result.get().placeholder()).isEqualTo("alias");
    }

    @Test
    void prepareRename_noResultOnTargetClassString() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "class \"M.A\"", "class \"M".length());

        Optional<IlimapRenamePrepareResult> result = renameService.prepareRename(analysis, pos);

        assertThat(result).isEmpty();
    }

    @Test
    void prepareRename_noResultOnTargetAttribute() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "X =", "X ".length());

        Optional<IlimapRenamePrepareResult> result = renameService.prepareRename(analysis, pos);

        assertThat(result).isEmpty();
    }

    @Test
    void renameInputUpdatesDeclarationAndReferences() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "input src", "input s".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "myinput");

        assertThat(result.available()).isTrue();
        assertThat(result.edits()).hasSize(2);
        for (IlimapTextEdit edit : result.edits()) {
            assertThat(edit.newText()).isEqualTo("myinput");
        }
    }

    @Test
    void renameOutputUpdatesDeclarationAndReferences() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "output out", "output o".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "destination");

        assertThat(result.available()).isTrue();
        assertThat(result.edits()).hasSize(2);
        for (IlimapTextEdit edit : result.edits()) {
            assertThat(edit.newText()).isEqualTo("destination");
        }
    }

    @Test
    void renameRuleUpdatesDeclarationAndTargetRuleRefs() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "rule r1", "rule r".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "primary");

        assertThat(result.available()).isTrue();
        assertThat(result.edits()).hasSize(2);
        for (IlimapTextEdit edit : result.edits()) {
            assertThat(edit.newText()).isEqualTo("primary");
        }
    }

    @Test
    void renameEnumUpdatesDeclarationAndEnumMapRefs() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "enum Quality", "enum Q".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "Grade");

        assertThat(result.available()).isTrue();
        assertThat(result.edits()).hasSize(2);
        for (IlimapTextEdit edit : result.edits()) {
            assertThat(edit.newText()).isEqualTo("Grade");
        }
    }

    @Test
    void renameSourceAliasUpdatesAliasAndExpressionsInRule() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "source s from", "source s".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "src2");

        assertThat(result.available()).isTrue();
        assertThat(result.edits()).hasSize(3);
        for (IlimapTextEdit edit : result.edits()) {
            assertThat(edit.newText()).isEqualTo("src2");
        }
    }

    @Test
    void renameSourceAlias_onlyAffectsCurrentRule() {
        IlimapAnalysis analysis = analyze(mappingWithTwoRulesSameAlias());
        IlimapIdePosition pos = positionAt(analysis, "source s from src class", "source s".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "t");

        assertThat(result.available()).isTrue();
        assertThat(result.edits()).hasSize(3);
    }

    @Test
    void rejectInvalidSymbolId_withSpaces() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "input src", "input s".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "new name");

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("not a valid symbol ID");
    }

    @Test
    void rejectInvalidAliasId_withHyphen() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "source s from", "source s".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "my-alias");

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("not a valid alias ID");
    }

    @Test
    void rejectCollision_withExistingInput() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "input src", "input s".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "other");

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("already used");
    }

    @Test
    void rejectSameName() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "input src", "input s".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "src");

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("same as the current name");
    }

    @Test
    void renameUnknownSymbol_returnsUnavailable() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = new IlimapIdePosition(0, 0);

        IlimapRenameResult result = renameService.rename(analysis, pos, "test");

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("No renamable symbol");
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze(URI, source, OPTIONS);
    }

    private static IlimapIdePosition positionAt(IlimapAnalysis analysis, String needle, int cursorDelta) {
        int offset = analysis.text().indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        return analysis.lineMap().toIdePosition(offset + cursorDelta);
    }

    private static String textAt(IlimapAnalysis analysis, IlimapIdeRange range) {
        int start = analysis.lineMap().positionToOffset(range.start().line(), range.start().character());
        int end = analysis.lineMap().positionToOffset(range.end().line(), range.end().character());
        return analysis.text().substring(start, end);
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  input other { path "in2.xtf"; model "M"; format xtf; }
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
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }

    private static String mappingWithTwoRulesSameAlias() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  input other { path "in2.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.Name;
                      Y = s.Value;
                    }
                  }
                  rule r2 {
                    target out class "M.B";
                    source s from other class "M.B";
                    assign {
                      Z = s.Code;
                    }
                  }
                }
                """;
    }
}
