package guru.interlis.transformer.mapping.ilimap.semantic;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapExpressionNormalizerTest {

    private static final IlimapSourceRange DUMMY =
            new IlimapSourceRange(new IlimapSourcePosition(0, 1, 1), new IlimapSourcePosition(1, 1, 2));

    private final IlimapExpressionNormalizer normalizer = new IlimapExpressionNormalizer();

    private IlimapSymbolTable tableWithEnum(String enumName) {
        var table = new IlimapSymbolTable();
        var enumBlock = new IlimapEnumBlock(enumName, List.of(), DUMMY);
        table.topLevelScope()
                .define(new IlimapSymbol(IlimapSymbolKind.ENUM_MAP, enumName, enumBlock), new DiagnosticCollector());
        return table;
    }

    @Test
    void normalizesSymbolicEnumMapReference() {
        var symbols = tableWithEnum("StatusMap");
        var expr = new IlimapExpressionText("enumMap(p.Type, StatusMap)", DUMMY);
        String result = normalizer.normalizeForJobConfig(expr, symbols);
        assertThat(result).isEqualTo("enumMap(p.Type, \"StatusMap\")");
    }

    @Test
    void leavesQuotedEnumMapUntouched() {
        var symbols = tableWithEnum("StatusMap");
        var expr = new IlimapExpressionText("enumMap(p.Type, \"StatusMap\")", DUMMY);
        String result = normalizer.normalizeForJobConfig(expr, symbols);
        assertThat(result).isEqualTo("enumMap(p.Type, \"StatusMap\")");
    }

    @Test
    void leavesSingleQuotedEnumMapUntouched() {
        var symbols = tableWithEnum("StatusMap");
        var expr = new IlimapExpressionText("enumMap(p.Type, 'StatusMap')", DUMMY);
        String result = normalizer.normalizeForJobConfig(expr, symbols);
        assertThat(result).isEqualTo("enumMap(p.Type, 'StatusMap')");
    }

    @Test
    void leavesNonEnumMapExpressionsUntouched() {
        var symbols = tableWithEnum("StatusMap");
        var expr = new IlimapExpressionText("replace(p.Text, \";\", \",\")", DUMMY);
        String result = normalizer.normalizeForJobConfig(expr, symbols);
        assertThat(result).isEqualTo("replace(p.Text, \";\", \",\")");
    }

    @Test
    void leavesSimplePathUntouched() {
        var symbols = tableWithEnum("StatusMap");
        var expr = new IlimapExpressionText("p.Nummer", DUMMY);
        String result = normalizer.normalizeForJobConfig(expr, symbols);
        assertThat(result).isEqualTo("p.Nummer");
    }

    @Test
    void doesNotNormalizeUnknownSymbol() {
        var symbols = tableWithEnum("StatusMap");
        var expr = new IlimapExpressionText("enumMap(p.Type, UnknownMap)", DUMMY);
        String result = normalizer.normalizeForJobConfig(expr, symbols);
        assertThat(result).isEqualTo("enumMap(p.Type, UnknownMap)");
    }

    @Test
    void handlesNestedFunctionAsFirstArgument() {
        var symbols = tableWithEnum("StatusMap");
        var expr = new IlimapExpressionText("enumMap(coalesce(p.A, p.B), StatusMap)", DUMMY);
        String result = normalizer.normalizeForJobConfig(expr, symbols);
        assertThat(result).isEqualTo("enumMap(coalesce(p.A, p.B), \"StatusMap\")");
    }
}
