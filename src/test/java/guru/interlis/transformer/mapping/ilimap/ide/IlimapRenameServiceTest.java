package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
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

    @Test
    void renameSourceAlias_inLfp3_renamesAllReferencesInScope() {
        IlimapAnalysis analysis = analyze(dmavToDm01Lfp3());
        // cursor on 'p' in "source p from dmav" in rule lfp3
        IlimapIdePosition pos = positionAt(analysis, "source p from dmav class \"DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3\" where p.LFPArt == #LFP3;",
                "source p".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "q");

        assertThat(result.available())
                .as("rename should succeed for source alias p in lfp3")
                .isTrue();

        assertThat(result.edits())
                .as("should rename all p references in rule lfp3")
                .isNotEmpty();

        // all edits should replace with "q"
        for (IlimapTextEdit edit : result.edits()) {
            assertThat(edit.newText()).isEqualTo("q");
        }

        // declaration must be in the edits
        boolean hasDeclaration = result.edits().stream()
                .anyMatch(edit -> textAt(analysis, edit.range()).equals("p")
                        && isInRule(analysis, edit.range(), "lfp3"));
        assertThat(hasDeclaration)
                .as("should rename the source alias declaration")
                .isTrue();
    }

    @Test
    void renameSourceAlias_inLfp3_doesNotAffectOtherRules() {
        IlimapAnalysis analysis = analyze(dmavToDm01Lfp3());
        // cursor on 'p' in "source p from dmav" in rule lfp3
        IlimapIdePosition pos = positionAt(analysis, "source p from dmav class \"DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3\" where p.LFPArt == #LFP3;",
                "source p".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "q");

        assertThat(result.available()).isTrue();

        // no edit should be in rule lfp3-symbol (which also uses alias p)
        var lfp3SymbolRule = analysis.document().rules().stream()
                .filter(r -> "lfp3-symbol".equals(r.id()))
                .findFirst().orElseThrow();
        int lfp3SymbolStart = lfp3SymbolRule.range().start().offset();
        int lfp3SymbolEnd = lfp3SymbolRule.range().end().offset();

        for (IlimapTextEdit edit : result.edits()) {
            int editStart = analysis.lineMap()
                    .positionToOffset(edit.range().start().line(), edit.range().start().character());
            boolean inLfp3Symbol = editStart >= lfp3SymbolStart && editStart <= lfp3SymbolEnd;
            assertThat(inLfp3Symbol)
                    .as("edit @" + edit.range().start().line() + ":" + edit.range().start().character()
                            + " should not be in rule lfp3-symbol")
                    .isFalse();
        }
    }

    @Test
    void renameSourceAlias_inLfp3_traceTest() {
        IlimapAnalysis analysis = analyze(dmavToDm01Lfp3());
        IlimapIdePosition pos = positionAt(analysis, "source p from dmav class \"DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3\" where p.LFPArt == #LFP3;",
                "source p".length());

        IlimapRenameResult result = renameService.rename(analysis, pos, "newp");

        assertThat(result.available()).isTrue();

        // emit actual edit texts for debugging
        System.out.println("=== Rename p -> newp edits (" + result.edits().size() + " total) ===");
        int idx = 0;
        for (IlimapTextEdit edit : result.edits()) {
            String original = textAt(analysis, edit.range());
            int offset = analysis.lineMap()
                    .positionToOffset(edit.range().start().line(), edit.range().start().character());
            int endOffset = analysis.lineMap()
                    .positionToOffset(edit.range().end().line(), edit.range().end().character());
            String context = analysis.text().substring(Math.max(0, offset - 5), Math.min(analysis.text().length(), offset + 15));
            System.out.println("  [" + idx + "] " + original + " -> " + edit.newText()
                    + " @line" + edit.range().start().line() + ":" + edit.range().start().character()
                    + " offset[" + offset + "," + endOffset + ")"
                    + " context: " + context.replace("\n", "\\n").replace("\r", "\\r"));
            idx++;
        }

        // Collect the edit ranges and verify they don't overlap
        List<IlimapIdeRange> ranges = result.edits().stream()
                .map(IlimapTextEdit::range)
                .sorted((a, b) -> {
                    int aStart = analysis.lineMap()
                            .positionToOffset(a.start().line(), a.start().character());
                    int bStart = analysis.lineMap()
                            .positionToOffset(b.start().line(), b.start().character());
                    return Integer.compare(aStart, bStart);
                }).toList();

        // Edits should not overlap (sorted by start offset)
        for (int i = 1; i < ranges.size(); i++) {
            int prevEnd = analysis.lineMap()
                    .positionToOffset(ranges.get(i - 1).end().line(), ranges.get(i - 1).end().character());
            int currStart = analysis.lineMap()
                    .positionToOffset(ranges.get(i).start().line(), ranges.get(i).start().character());
            assertThat(currStart)
                    .as("edit[" + i + "] at offset " + currStart + " overlaps with edit[" + (i - 1) + "] ending at offset " + prevEnd)
                    .isGreaterThanOrEqualTo(prevEnd);
        }
    }

    private boolean isInRule(IlimapAnalysis analysis, IlimapIdeRange range, String ruleId) {
        var rule = analysis.document().rules().stream()
                .filter(r -> ruleId.equals(r.id()))
                .findFirst().orElse(null);
        if (rule == null) return false;
        int offset = analysis.lineMap()
                .positionToOffset(range.start().line(), range.start().character());
        return offset >= rule.range().start().offset() && offset <= rule.range().end().offset();
    }

    private static String dmavToDm01Lfp3() {
        return """
                mapping v2 "dmav-to-dm01-lfp3" {
                  job {
                    description "Pilot-Transformation";
                    direction dmav-to-dm01;
                    failPolicy strict;
                    compileMode compatible;
                    modeldir "https://models.geo.admin.ch/";
                    modeldir "https://models.interlis.ch";
                  }

                  input dmav {
                    path "input/dmav.xtf";
                    model "DMAV_FixpunkteAVKategorie3_V1_1";
                    format xtf;
                  }

                  output dm01 {
                    path "build/out/dm01-lfp3.itf";
                    model "DM01AVCH24LV95D";
                    format itf;
                  }

                  basket byTopic;

                  enum Zuverlaessigkeit_DMAV_DM01 {
                    true => "ja";
                    false => "nein";
                  }

                  enum Versicherungsart_DMAV_DM01 {
                    "Stein" => "Stein";
                    "Kunststoffzeichen" => "Kunststoffzeichen";
                    "Bolzen" => "Bolzen";
                    "Rohr" => "Rohr";
                    "Pfahl" => "Pfahl";
                    "Kreuz" => "Kreuz";
                    "unversichert" => "unversichert";
                    "weitere" => "weitere";
                  }

                  rule lfp3-nachfuehrung {
                    target dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung";
                    source nf from dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3Nachfuehrung";
                    identity nf.NBIdent, nf.Identifikator;

                    assign {
                      NBIdent = nf.NBIdent;
                      Identifikator = nf.Identifikator;
                      Beschreibung = truncate(nf.Beschreibung, 30);
                      Perimeter = nf.Perimeter;
                      GueltigerEintrag = toDate(nf.GueltigerEintrag);
                    }
                  }

                  rule lfp3 {
                    target dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
                    source p from dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3" where p.LFPArt == #LFP3;
                    identity p.NBIdent, p.Nummer;

                    assign {
                      NBIdent = p.NBIdent;
                      Nummer = p.Nummer;
                      Geometrie = p.Geometrie;
                      HoeheGeom = p.Hoehengeometrie;
                      LageGen = mul(p.Lagegenauigkeit, 100.0);
                      LageZuv = enumMap(p.IstLagezuverlaessig, Zuverlaessigkeit_DMAV_DM01);
                      HoeheGen = mul(p.Hoehengenauigkeit, 100.0);
                      HoeheZuv = enumMap(p.IstHoehenzuverlaessig, Zuverlaessigkeit_DMAV_DM01);
                      Punktzeichen = enumMap(p.Punktzeichen, Versicherungsart_DMAV_DM01);
                      Protokoll = #nein;
                    }

                    bag Textposition {
                      from txt in dmav class "DMAVTYM_Grafik_V1_0.Textposition";
                      structure "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos";
                      mode expand;
                      maxItems 1;
                      parentRef role "LFP3Pos_von" parent p;
                      assign {
                        Pos = txt.Position;
                        Ori = txt.Orientierung;
                        HAli = txt.HReferenzpunkt;
                        VAli = txt.VReferenzpunkt;
                      }
                    }

                    ref Entstehung {
                      role "Entstehung";
                      required;
                      target rule lfp3-nachfuehrung sourceRef p.Entstehung;
                    }

                    loss {
                      sourcePath p.LFPArt;
                      reasonCode "DMAV_ONLY";
                      description "DM01 LFP3 kennt nur LFP3.";
                    }

                    loss {
                      sourcePath p.Schutzart;
                      reasonCode "DMAV_ONLY";
                      description "DM01 LFP3 hat kein Attribut fuer Schutzart.";
                      when defined(p.Schutzart);
                    }

                    loss {
                      sourcePath p.Grenzpunktfunktion;
                      reasonCode "DMAV_ONLY";
                      description "DM01 LFP3 speichert die Grenzpunktfunktion nicht.";
                    }

                    loss {
                      sourcePath p.IstHoheitsgrenzsteinAlt;
                      reasonCode "DMAV_ONLY";
                      description "DM01 LFP3 hat kein Attribut fuer IstHoheitsgrenzsteinAlt.";
                      when defined(p.IstHoheitsgrenzsteinAlt);
                    }

                    loss {
                      sourcePath p.AktiverUnterhalt;
                      reasonCode "DMAV_ONLY";
                      description "DM01 LFP3 hat kein Attribut fuer AktiverUnterhalt.";
                    }
                  }

                  rule lfp3-symbol {
                    target dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Symbol";
                    source p from dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3" where if(p.LFPArt == #LFP3, defined(p.SymbolOri), false);
                    identity p.NBIdent, p.Nummer;

                    assign {
                      Ori = p.SymbolOri;
                    }

                    ref LFP3Symbol_von {
                      role "LFP3Symbol_von";
                      required;
                      target rule lfp3 sourceRef p;
                    }
                  }
                }
                """;
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
        int start = analysis.lineMap()
                .positionToOffset(range.start().line(), range.start().character());
        int end = analysis.lineMap()
                .positionToOffset(range.end().line(), range.end().character());
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
