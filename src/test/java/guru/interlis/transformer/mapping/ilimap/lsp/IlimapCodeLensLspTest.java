package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapCodeLensService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapValidateMappingParams;

import java.util.List;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

class IlimapCodeLensLspTest {

    private static final String URI = "file:///mapping.ilimap";

    @Test
    void serverAdvertisesCodeLensCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getCodeLensProvider()).isNotNull();
        assertThat(result.getCapabilities().getCodeLensProvider().getResolveProvider()).isFalse();
    }

    @Test
    void lspMapsRuleCodeLensesWithCommands() {
        IlimapTextDocumentService service = new IlimapTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, validMapping())));

        List<? extends CodeLens> lenses =
                service.codeLens(new CodeLensParams(new TextDocumentIdentifier(URI))).join();

        assertThat(lenses)
                .anySatisfy(lens -> {
                    assertThat(lens.getCommand().getCommand()).isEqualTo("ilimap.showRuleInOverview");
                    assertThat(lens.getCommand().getTitle()).isEqualTo("Show in Overview");
                    assertThat(lens.getCommand().getArguments()).containsExactly(URI, "r1");
                });
        assertThat(lenses)
                .anySatisfy(lens -> {
                    assertThat(lens.getCommand().getCommand()).isEqualTo("ilimap.showRuleCoverage");
                    assertThat(lens.getCommand().getArguments()).containsExactly(URI, "r1");
                });
    }

    @Test
    void returnsNoCodeLensesForUnopenedDocument() {
        IlimapTextDocumentService service = new IlimapTextDocumentService();

        var lenses = service.codeLens(new CodeLensParams(new TextDocumentIdentifier(URI))).join();

        assertThat(lenses).isEmpty();
    }

    @Test
    void includesCoverageSegmentAfterValidation() {
        IlimapTextDocumentService service = new IlimapTextDocumentService();
        String source = modelAwareMappingWithPartialAssignments();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
        service.validateMapping(new IlimapValidateMappingParams(URI, source, 1)).join();

        List<? extends CodeLens> lenses =
                service.codeLens(new CodeLensParams(new TextDocumentIdentifier(URI))).join();

        assertThat(lenses)
                .filteredOn(lens -> lens.getCommand().getCommand().equals("ilimap.showRuleCoverage"))
                .singleElement()
                .satisfies(lens -> assertThat(lens.getCommand().getTitle()).contains("Coverage"));
    }

    private static String validMapping() {
        return """
                mapping v2 "Profile" {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String modelAwareMappingWithPartialAssignments() {
        return """
                mapping v2 "Profile" {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                    assign {
                      Name = s.Name;
                    }
                  }
                }
                """;
    }
}
