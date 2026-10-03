package guru.interlis.transformer.mapping.ilimap.editor;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IlimapEditorDocumentTest {
    private static final String TEXT = """
        // migration: retain this comment
        mapping v2 {
          input old { path "in.xtf"; model "Old"; }
          output new { path "out.xtf"; model "New"; }
          rule r {
            target new class "New.T.C";
            source s from old class "Old.T.C";
            assign {
              // explanation
              Name = s.Label; // keep me
            }
          }
        }
        """;

    @Test
    void changesOnlyExpressionAndSupportsUndoRedo() {
        var doc = new IlimapEditorDocument(TEXT);
        doc.assign("r", "", "Name", "trim(s.Label)");
        assertThat(doc.text()).isEqualTo(TEXT.replace("s.Label;", "trim(s.Label);"));
        doc.undo();
        assertThat(doc.text()).isEqualTo(TEXT);
        doc.redo();
        assertThat(doc.text()).contains("trim(s.Label)");
    }

    @Test
    void addsAssignmentWithoutReplacingExistingContent() {
        var doc = new IlimapEditorDocument(TEXT);
        doc.assign("r", "", "Count", "42");
        assertThat(doc.document().rules().getFirst().elements()).isNotEmpty();
        assertThat(doc.text()).contains("// explanation", "Name = s.Label; // keep me", "Count = 42;");
    }

    @Test
    void rejectsStaleEditsAndMalformedGraphicalExpressionsWithoutChangingDocument() {
        var doc = new IlimapEditorDocument(TEXT);
        long old = doc.revision();
        doc.setText(TEXT + "\n");
        assertThatThrownBy(() -> doc.edit(old, 0, 0, "x")).isInstanceOf(IllegalStateException.class);
        String before = doc.text();
        assertThatThrownBy(() -> doc.assign("r", "", "Name", "trim(")).isInstanceOf(RuntimeException.class);
        assertThat(doc.text()).isEqualTo(before);
    }

    @Test
    void graphicalCommandIsAtomicAndHasOneUndoStep() {
        var doc = new IlimapEditorDocument(TEXT);
        assertThatThrownBy(() -> doc.atomicEdit(() -> {
                    doc.assign("r", "", "Name", "trim(s.Label)");
                    doc.assign("r", "", "Bad", "concat(");
                }))
                .isInstanceOf(RuntimeException.class);
        assertThat(doc.text()).isEqualTo(TEXT);
        doc.atomicEdit(() -> {
            doc.assign("r", "", "Name", "trim(s.Label)");
            doc.assign("r", "", "Count", "4");
        });
        doc.undo();
        assertThat(doc.text()).isEqualTo(TEXT);
        doc.redo();
        assertThat(doc.text()).contains("Count = 4", "trim(s.Label)");
    }

    @Test
    void changesClassAndReferenceWithoutChangingIdsCommentsOrDuplicatingRules() {
        var doc = new IlimapEditorDocument(TEXT);
        doc.targetClass("r", "New.T.Renamed");
        assertThat(doc.text()).isEqualTo(TEXT.replace("New.T.C", "New.T.Renamed"));
        doc.insertRuleElement(
                "r", "ref owner { role \"Owner\"; target rule originalRule /* retain */ sourceRef s.Owner; }");
        doc.reference("r", "Owner", "newRule", "s.NewOwner");
        assertThat(doc.text()).contains("target rule newRule /* retain */ sourceRef s.NewOwner;");
        assertThat(doc.rule("r").elements().stream()
                        .filter(guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock.class::isInstance))
                .hasSize(1);
    }

    @Test
    void enumerationIsStrictSourcePreservingAndOneUndoStep() {
        var doc = new IlimapEditorDocument(TEXT);
        doc.enumeration("r", "", "Name", "s.Label", "Labels", java.util.Map.of("old", "new"));
        assertThat(doc.text())
                .contains(
                        "Name = enumMapStrict(s.Label, \"Labels\"); // keep me",
                        "  enum Labels {\n    \"old\" => \"new\";\n  }\n  rule r {");
        doc.undo();
        assertThat(doc.text()).isEqualTo(TEXT);
    }

    @Test
    void highlightsIncompleteStringsCommentsAndExpressionsWithoutParsing() {
        String text = "rule r { // comment\n Name = concat(s.Name, \"ü\"); /* open";
        var spans = new IlimapHighlighter().highlight(text);
        assertThat(spans)
                .anyMatch(s -> s.kind() == IlimapHighlighter.Kind.COMMENT
                        && text.substring(s.start(), s.end()).equals("/* open"));
        assertThat(spans)
                .anyMatch(s -> s.kind() == IlimapHighlighter.Kind.FUNCTION
                        && text.substring(s.start(), s.end()).equals("concat"));
        assertThat(new IlimapHighlighter().highlight("\"unfinished\\")).hasSize(1);
        assertThat(spans).allMatch(s -> s.start() >= 0 && s.end() <= text.length() && s.end() > s.start());
    }
}
