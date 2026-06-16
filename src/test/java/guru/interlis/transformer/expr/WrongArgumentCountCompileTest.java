package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WrongArgumentCountCompileTest {

    @Test
    void definedWithZeroArgsDetected() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        ExpressionCompiler compiler = new ExpressionCompiler();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx =
                new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());

        compiler.compile("defined()", ctx, diagnostics);
        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all()).anyMatch(d -> d.code().equals(DiagnosticCode.EXPR_WRONG_ARG_COUNT));
    }

    @Test
    void definedWithTwoArgsDetected() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        ExpressionCompiler compiler = new ExpressionCompiler();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx =
                new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());

        compiler.compile("defined(a, b)", ctx, diagnostics);
        assertThat(diagnostics.errors()).isGreaterThan(0);
    }
}
