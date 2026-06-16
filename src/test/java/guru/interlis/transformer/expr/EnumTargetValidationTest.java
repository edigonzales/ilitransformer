package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnumTargetValidationTest {

    private ExpressionCompiler compiler;
    private FunctionRegistry registry;

    @BeforeEach
    void setUp() {
        compiler = new ExpressionCompiler();
        registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        EnumFunctions.registerAll(registry);
    }

    @Test
    void enumMapWithExistingTableDoesNotError() {
        Map<String, Map<String, String>> enumMaps = Map.of("MyMap", Map.of("A", "X", "B", "Y"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.ENUM, registry, enumMaps);

        compiler.compile("enumMap(${s.Status}, \"MyMap\")", ctx, diagnostics);
        assertThat(diagnostics.all()).noneMatch(d -> d.code().equals(DiagnosticCode.EXPR_ENUM_MAP_MISSING));
    }

    @Test
    void enumMapWithMissingTableReportsError() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.ENUM, registry, Map.of());

        compiler.compile("enumMap(${s.Status}, \"NonExistent\")", ctx, diagnostics);
        assertThat(diagnostics.all()).anyMatch(d -> d.code().equals(DiagnosticCode.EXPR_ENUM_MAP_MISSING));
    }

    @Test
    void enumDefaultPassesThroughWhenDefined() {
        ExpressionEngine engine = new ExpressionEngine();
        guru.interlis.transformer.expr.EvalContext evalCtx =
                new guru.interlis.transformer.expr.EvalContext(Map.of(), null, "r1");

        Value result = engine.evaluate("enumDefault(#LFP3, 'Default')", evalCtx);
        assertThat(result).isInstanceOf(EnumValue.class);
        assertThat(((EnumValue) result).name()).isEqualTo("LFP3");
    }

    @Test
    void enumMapStrictWithExistingTableDoesNotError() {
        Map<String, Map<String, String>> enumMaps = Map.of("MyMap", Map.of("A", "X", "B", "Y"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.ENUM, registry, enumMaps);

        compiler.compile("enumMapStrict(${s.Status}, \"MyMap\")", ctx, diagnostics);
        assertThat(diagnostics.all()).noneMatch(d -> d.code().equals(DiagnosticCode.EXPR_ENUM_MAP_MISSING));
    }

    @Test
    void enumMapDefaultWithExistingTableDoesNotError() {
        Map<String, Map<String, String>> enumMaps = Map.of("MyMap", Map.of("A", "X", "B", "Y"));
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.ENUM, registry, enumMaps);

        compiler.compile("enumMapDefault(${s.Status}, \"MyMap\", 'Default')", ctx, diagnostics);
        assertThat(diagnostics.all()).noneMatch(d -> d.code().equals(DiagnosticCode.EXPR_ENUM_MAP_MISSING));
    }
}
