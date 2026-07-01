package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class IlimapNavigationServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));
    private static final String URI = "file:///test.ilimap";

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapNavigationService navigationService = new IlimapNavigationService();

    @Test
    void nodeAtPosition_onRuleId_returnsRuleNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "rule r1", "rule ".length()));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("rule:r1");
        assertThat(node.get().kind()).isEqualTo("rule");
    }

    @Test
    void nodeAtPosition_onInputId_returnsInputNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "input src", "input ".length()));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("input:src");
        assertThat(node.get().kind()).isEqualTo("input");
    }

    @Test
    void nodeAtPosition_onOutputId_returnsOutputNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "output out", "output ".length()));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("output:out");
        assertThat(node.get().kind()).isEqualTo("output");
    }

    @Test
    void nodeAtPosition_onEnumMapId_returnsEnumNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "enum Quality", "enum ".length()));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("enum:Quality");
        assertThat(node.get().kind()).isEqualTo("enum");
    }

    @Test
    void nodeAtPosition_onSourceAliasDeclaration_returnsSourceNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "source s from", "source ".length()));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("rule:r1:source:s");
        assertThat(node.get().kind()).isEqualTo("source");
    }

    @Test
    void nodeAtPosition_onSourceMemberExpression_returnsSourceMemberNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "s.X", 0));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("rule:r1:source:s:member:X");
        assertThat(node.get().kind()).isEqualTo("sourceMember");
    }

    @Test
    void nodeAtPosition_onTargetAttributeAssignmentLeftSide_returnsAssignmentNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "X = s.X", "X".length() / 2));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("rule:r1:assign:X");
        assertThat(node.get().kind()).isEqualTo("assignment");
    }

    @Test
    void nodeAtPosition_onRefBlock_returnsRefNode() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node =
                navigationService.nodeAtPosition(analysis, positionAt(analysis, "ref Parent", "ref ".length()));

        assertThat(node).isPresent();
        assertThat(node.get().nodeId()).isEqualTo("rule:r1:ref:Parent");
        assertThat(node.get().kind()).isEqualTo("ref");
    }

    @Test
    void targetForNodeId_ruleNode_returnsLocation() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapNavigationTarget target = navigationService.targetForNodeId(analysis, "rule:r1");

        assertThat(target.available()).isTrue();
        assertThat(target.nodeId()).isEqualTo("rule:r1");
        assertThat(target.location()).isNotNull();
        assertThat(target.location().line()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void targetForNodeId_inputNode_returnsLocation() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapNavigationTarget target = navigationService.targetForNodeId(analysis, "input:src");

        assertThat(target.available()).isTrue();
        assertThat(target.location()).isNotNull();
    }

    @Test
    void targetForNodeId_outputNode_returnsLocation() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapNavigationTarget target = navigationService.targetForNodeId(analysis, "output:out");

        assertThat(target.available()).isTrue();
        assertThat(target.location()).isNotNull();
    }

    @Test
    void targetForNodeId_enumNode_returnsLocation() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapNavigationTarget target = navigationService.targetForNodeId(analysis, "enum:Quality");

        assertThat(target.available()).isTrue();
        assertThat(target.location()).isNotNull();
    }

    @Test
    void targetForNodeId_unknownNode_returnsUnavailable() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapNavigationTarget target = navigationService.targetForNodeId(analysis, "unknown:nosuch");

        assertThat(target.available()).isFalse();
    }

    @Test
    void targetForNodeId_ruleSourceNode_returnsLocation() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapNavigationTarget target = navigationService.targetForNodeId(analysis, "rule:r1:source:s");

        assertThat(target.available()).isTrue();
        assertThat(target.location()).isNotNull();
    }

    @Test
    void targetForNodeId_ruleRefNode_returnsLocation() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapNavigationTarget target = navigationService.targetForNodeId(analysis, "rule:r1:ref:Parent");

        assertThat(target.available()).isTrue();
        assertThat(target.location()).isNotNull();
    }

    @Test
    void nodeAtPosition_outsideOfAnyNode_returnsEmpty() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapNavigationNode> node = navigationService.nodeAtPosition(analysis, new IlimapIdePosition(999, 0));

        assertThat(node).isEmpty();
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze(URI, source, OPTIONS);
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
                      X = s.X;
                    }
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }
}
