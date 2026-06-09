package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class WrongArgumentTypeCompileTest {

    @Test
    void wrongArgTypeProducesWarning() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        EnumFunctions.registerAll(registry);
        ExpressionCompiler compiler = new ExpressionCompiler();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(),
                TypeInfo.UNKNOWN, registry, Map.of());

        compiler.compile("enumName(42)", ctx, diagnostics);
        assertThat(diagnostics.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.EXPR_WRONG_ARG_TYPE));
    }

    @Test
    void compatibleTypesDoNotWarn() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        EnumFunctions.registerAll(registry);
        ExpressionCompiler compiler = new ExpressionCompiler();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(),
                TypeInfo.UNKNOWN, registry, Map.of());

        compiler.compile("enumName(#LFP3)", ctx, diagnostics);
        assertThat(diagnostics.all()).noneMatch(d ->
                d.code().equals(DiagnosticCode.EXPR_WRONG_ARG_TYPE));
    }
}
