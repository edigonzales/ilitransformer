package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class UnknownFunctionCompileTest {

    @Test
    void unknownFunctionDetectedAtCompileTime() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        ExpressionCompiler compiler = new ExpressionCompiler();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(),
                TypeInfo.UNKNOWN, registry, Map.of());

        compiler.compile("nonexistent(42)", ctx, diagnostics);
        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.EXPR_UNKNOWN_FUNC));
    }

    @Test
    void unknownFunctionStillCompiles() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        ExpressionCompiler compiler = new ExpressionCompiler();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(),
                TypeInfo.UNKNOWN, registry, Map.of());

        var result = compiler.compile("unknown('test')", ctx, diagnostics);
        assertThat(result).isNotNull();
        assertThat(result.resultType()).isEqualTo(TypeInfo.UNKNOWN);
    }
}
