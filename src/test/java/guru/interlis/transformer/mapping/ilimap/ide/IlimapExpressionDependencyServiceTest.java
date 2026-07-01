package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import org.junit.jupiter.api.Test;

class IlimapExpressionDependencyServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapExpressionDependencyService dependencyService = new IlimapExpressionDependencyService();

    @Test
    void dependenciesForSingleSourceMember() {
        List<IlimapExpressionDependencySummary> deps =
                dependencyService.dependencies("src.Name");

        assertThat(deps).singleElement().satisfies(dep -> {
            assertThat(dep.kind()).isEqualTo("sourceAttribute");
            assertThat(dep.alias()).isEqualTo("src");
            assertThat(dep.member()).isEqualTo("Name");
        });
    }

    @Test
    void dependenciesForMultipleSourceMembers() {
        List<IlimapExpressionDependencySummary> deps =
                dependencyService.dependencies("s.First + s.Last");

        assertThat(deps).hasSize(2);
        assertThat(deps).extracting(IlimapExpressionDependencySummary::alias)
                .containsExactly("s", "s");
        assertThat(deps).extracting(IlimapExpressionDependencySummary::member)
                .containsExactly("First", "Last");
    }

    @Test
    void ignoresEnumMapAsAlias() {
        List<IlimapExpressionDependencySummary> deps =
                dependencyService.dependencies("enumMap(Quality)");

        assertThat(deps).singleElement().satisfies(dep -> {
            assertThat(dep.kind()).isEqualTo("enumMap");
            assertThat(dep.enumMapId()).isEqualTo("Quality");
            assertThat(dep.alias()).isNull();
        });
    }

    @Test
    void detectsEnumMapSimpleCall() {
        List<IlimapExpressionDependencySummary> deps =
                dependencyService.dependencies("enumMap(MyEnum)");

        assertThat(deps).singleElement().satisfies(dep -> {
            assertThat(dep.kind()).isEqualTo("enumMap");
            assertThat(dep.enumMapId()).isEqualTo("MyEnum");
        });
    }

    @Test
    void matchesTextInStrings() {
        List<IlimapExpressionDependencySummary> deps =
                dependencyService.dependencies("\"something.else\"");

        assertThat(deps).singleElement().satisfies(dep -> {
            assertThat(dep.alias()).isEqualTo("something");
            assertThat(dep.member()).isEqualTo("else");
        });
    }

    @Test
    void emptyExpressionReturnsEmpty() {
        assertThat(dependencyService.dependencies("")).isEmpty();
        assertThat(dependencyService.dependencies((String) null)).isEmpty();
    }

    @Test
    void sourceForAliasReturnsSource() {
        IlimapAnalysis analysis = analyze(mappingWithSources());
        IlimapRuleBlock rule = analysis.document().rules().get(0);

        var source = dependencyService.sourceForAlias(rule, "s");

        assertThat(source).isPresent();
        assertThat(source.get().alias()).isEqualTo("s");
        assertThat(source.get().sourceClass()).isEqualTo("M.A");
    }

    @Test
    void sourceForUnknownAliasReturnsEmpty() {
        IlimapAnalysis analysis = analyze(mappingWithSources());
        IlimapRuleBlock rule = analysis.document().rules().get(0);

        var source = dependencyService.sourceForAlias(rule, "unknown");

        assertThat(source).isEmpty();
    }

    @Test
    void dependenciesWithLocationsReturnsTraceDependencies() {
        IlimapAnalysis analysis = analyze(mappingWithAssign());
        IlimapRuleBlock rule = analysis.document().rules().get(0);
        var assignBlock = rule.elements().stream()
                .filter(e -> e instanceof guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock)
                .findFirst()
                .orElseThrow();
        IlimapExpressionText expression =
                ((guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock) assignBlock)
                        .assignments().get(0).expression();

        List<IlimapTraceDependency> deps = dependencyService.dependenciesWithLocations(analysis, expression, rule);

        assertThat(deps).singleElement().satisfies(dep -> {
            assertThat(dep.kind()).isEqualTo("sourceAttribute");
            assertThat(dep.alias()).isEqualTo("s");
            assertThat(dep.member()).isEqualTo("X");
            assertThat(dep.location()).isNotNull();
            assertThat(dep.definitionLocation()).isNotNull();
        });
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static String mappingWithSources() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String mappingWithAssign() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                    }
                  }
                }
                """;
    }
}
