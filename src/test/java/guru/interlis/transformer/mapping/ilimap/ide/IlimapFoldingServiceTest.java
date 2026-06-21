package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapFoldingServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapFoldingService foldingService = new IlimapFoldingService();

    @Test
    void foldingIncludesMappingBlock() {
        var ranges = foldingService.foldingRanges(analyze(validMapping()));

        assertThat(ranges)
                .extracting(IlimapFoldingRange::startLine, IlimapFoldingRange::endLine, IlimapFoldingRange::kind)
                .contains(tuple(0, 31, "region"));
    }

    @Test
    void foldingIncludesRuleBlocks() {
        var ranges = foldingService.foldingRanges(analyze(validMapping()));

        assertThat(ranges)
                .extracting(IlimapFoldingRange::startLine, IlimapFoldingRange::endLine, IlimapFoldingRange::kind)
                .contains(tuple(9, 30, "region"));
    }

    @Test
    void foldingIncludesAssignBlocks() {
        var ranges = foldingService.foldingRanges(analyze(validMapping()));

        assertThat(ranges)
                .extracting(IlimapFoldingRange::startLine, IlimapFoldingRange::endLine, IlimapFoldingRange::kind)
                .contains(tuple(12, 14, "region"), tuple(17, 19, "region"), tuple(22, 24, "region"));
    }

    @Test
    void foldingIncludesBagRefAndEnumBlocks() {
        var ranges = foldingService.foldingRanges(analyze(validMapping()));

        assertThat(ranges)
                .extracting(IlimapFoldingRange::startLine, IlimapFoldingRange::endLine, IlimapFoldingRange::kind)
                .contains(
                        tuple(6, 8, "region"),
                        tuple(15, 26, "region"),
                        tuple(20, 25, "region"),
                        tuple(27, 29, "region"));
    }

    @Test
    void singleLineRangesAreSkipped() {
        String source =
                "mapping v2 { input src { path \"in.xtf\"; model \"M\"; } output out { path \"out.xtf\"; model \"M\"; } rule r1 { target out class \"M.A\"; source s from src class \"M.A\"; assign { X = s.X; } } }";

        assertThat(foldingService.foldingRanges(analyze(source))).isEmpty();
    }

    @Test
    void invalidDocumentReturnsEmptyRanges() {
        assertThat(foldingService.foldingRanges(analyze(missingSemicolonMapping())))
                .isEmpty();
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static String validMapping() {
        return """
                mapping v2 "Profile" {
                  job {
                    name "demo";
                  }
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum Quality {
                    "old" -> "new";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                    }
                    bag Outer {
                      from o in src class "M.Outer";
                      assign {
                        O = o.O;
                      }
                      bag Inner {
                        from i in src class "M.Inner";
                        assign {
                          I = i.I;
                        }
                      }
                    }
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }

    private static String missingSemicolonMapping() {
        return """
                mapping v2 {
                  input src {
                    path "in.xtf"
                    model "M";
                  }
                }
                """;
    }
}
