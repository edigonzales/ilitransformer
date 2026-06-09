package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ExpressionTypeCheckerTest {

    private FunctionRegistry registry;
    private DiagnosticCollector diagnostics;

    @BeforeEach
    void setUp() {
        registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        diagnostics = new DiagnosticCollector();
    }

    @Test
    void checksLiteralExprType() {
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());
        ExpressionTypeChecker checker = new ExpressionTypeChecker(ctx, diagnostics);

        TypeInfo result = checker.check(new LiteralExpr(new TextValue("hello")), new HashSet<>());
        assertThat(result).isEqualTo(TypeInfo.TEXT);
    }

    @Test
    void checksNumberLiteralType() {
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());
        ExpressionTypeChecker checker = new ExpressionTypeChecker(ctx, diagnostics);

        TypeInfo result = checker.check(new LiteralExpr(NumberValue.of(42)), new HashSet<>());
        assertThat(result).isEqualTo(TypeInfo.NUMERIC);
    }

    @Test
    void checksBooleanLiteralType() {
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());
        ExpressionTypeChecker checker = new ExpressionTypeChecker(ctx, diagnostics);

        TypeInfo result = checker.check(new LiteralExpr(BooleanValue.TRUE), new HashSet<>());
        assertThat(result).isEqualTo(TypeInfo.BOOLEAN);
    }

    @Test
    void detectsUnknownFunction() {
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());
        ExpressionTypeChecker checker = new ExpressionTypeChecker(ctx, diagnostics);

        FunctionCallExpr call = new FunctionCallExpr("nonexistent", List.of());
        checker.check(call, new HashSet<>());
        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.EXPR_UNKNOWN_FUNC));
    }

    @Test
    void detectsWrongArgCount() {
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());
        ExpressionTypeChecker checker = new ExpressionTypeChecker(ctx, diagnostics);

        FunctionCallExpr call = new FunctionCallExpr("defined", List.of());
        checker.check(call, new HashSet<>());
        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.EXPR_WRONG_ARG_COUNT));
    }

    @Test
    void resetsDiagnosticsPerCheck() {
        ExpressionCompileContext ctx = new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());
        ExpressionTypeChecker checker = new ExpressionTypeChecker(ctx, diagnostics);

        checker.check(new LiteralExpr(new TextValue("ok")), new HashSet<>());
        assertThat(diagnostics.errors()).isZero();
    }
}
