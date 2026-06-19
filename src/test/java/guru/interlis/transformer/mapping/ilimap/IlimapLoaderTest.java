package guru.interlis.transformer.mapping.ilimap;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapLoaderTest {

    private final IlimapLoader loader = new IlimapLoader();

    @Test
    void loadsMinimalIlimapFile() {
        Path path = Path.of("src/test/resources/mapping/ilimap/minimal-lfp3.ilimap");
        JobConfig config = loader.load(path);

        assertThat(config).isNotNull();
        assertThat(config.version).isEqualTo(1);
        assertThat(config.job.name).isEqualTo("minimal-lfp3");
        assertThat(config.job.direction).isEqualTo("dm01-to-dmav");
        assertThat(config.job.failPolicy).isEqualTo("strict");
        assertThat(config.job.inputs).hasSize(1);
        assertThat(config.job.inputs.get(0).id).isEqualTo("dm01");
        assertThat(config.job.outputs).hasSize(1);
        assertThat(config.job.outputs.get(0).id).isEqualTo("dmav");
        assertThat(config.mapping.rules).hasSize(1);
        assertThat(config.mapping.rules.get(0).id).isEqualTo("lfp3");
    }

    @Test
    void loadsFromString() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = loader.load(source, Path.of("."));

        assertThat(config).isNotNull();
        assertThat(config.mapping.rules).hasSize(1);
        assertThat(config.mapping.rules.get(0).assign).containsEntry("X", "s.X");
    }

    @Test
    void loadDetailedReturnsDiagnosticsForWarnings() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  enum StatusMap { "a" => "b"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = enumMap(s.Type, "StatusMap"); }
                  }
                }
                """;
        IlimapLoadResult result = loader.loadDetailed(source, Path.of("."));
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.jobConfig()).isNotNull();
        assertThat(result.document()).isNotNull();
        assertThat(result.symbols()).isNotNull();
    }

    @Test
    void rejectsSyntaxError() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf" model }
                }
                """;
        assertThatThrownBy(() -> loader.load(source, Path.of("."))).isInstanceOf(IlimapLoadException.class);
    }

    @Test
    void loadDetailedReturnsDiagnosticsForSyntaxError() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf" model }
                }
                """;
        IlimapLoadResult result = loader.loadDetailed(source, Path.of("."));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics())
                .anyMatch(d -> d.code().equals("ILITRF-ILIMAP-SYNTAX-ERROR") && d.severity() == Severity.ERROR);
        assertThat(result.jobConfig()).isNull();
    }

    @Test
    void loadDetailedFromPathIncludesFilePathInDiagnostics() {
        Path path = Path.of("src/test/resources/mapping/ilimap/minimal-lfp3.ilimap");
        IlimapLoadResult result = loader.loadDetailed(path);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.jobConfig()).isNotNull();
    }

    @Test
    void rejectsSemanticError() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target unknown_output class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        assertThatThrownBy(() -> loader.load(source, Path.of(".")))
                .isInstanceOf(IlimapLoadException.class)
                .hasMessageContaining("unknown output");
    }

    @Test
    void loadDetailedReturnsNullJobConfigOnSemanticError() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target unknown_output class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        IlimapLoadResult result = loader.loadDetailed(source, Path.of("."));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.jobConfig()).isNull();
    }

    @Test
    void normalizesEnumMapInLoadedConfig() {
        Path path = Path.of("src/test/resources/mapping/ilimap/minimal-lfp3.ilimap");
        JobConfig config = loader.load(path);

        String expr = config.mapping.rules.get(0).assign.get("IstLagezuverlaessig");
        assertThat(expr).isEqualTo("enumMap(p.LageZuv, \"Zuverlaessigkeit_DM01_DMAV\")");
    }
}
