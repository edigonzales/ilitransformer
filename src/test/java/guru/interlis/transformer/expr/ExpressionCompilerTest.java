package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpressionCompilerTest {

    private ExpressionCompiler compiler;
    private FunctionRegistry registry;
    private DiagnosticCollector diagnostics;
    private ExpressionCompileContext context;

    @BeforeEach
    void setUp() {
        compiler = new ExpressionCompiler();
        registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        diagnostics = new DiagnosticCollector();
        context = new ExpressionCompileContext("testRule", Map.of(), TypeInfo.TEXT, registry, Map.of());
    }

    @Test
    void compilesStringLiteral() {
        CompiledExpression result = compiler.compile("\"hello\"", context, diagnostics);
        assertThat(result.sourceText()).isEqualTo("\"hello\"");
        assertThat(result.resultType()).isEqualTo(TypeInfo.TEXT);
        assertThat(result.deterministic()).isTrue();
        assertThat(result.ast()).isInstanceOf(LiteralExpr.class);
    }

    @Test
    void compilesNumberLiteral() {
        CompiledExpression result = compiler.compile("42", context, diagnostics);
        assertThat(result.resultType()).isEqualTo(TypeInfo.NUMERIC);
        assertThat(result.deterministic()).isTrue();
    }

    @Test
    void compilesBooleanLiteral() {
        CompiledExpression result = compiler.compile("true", context, diagnostics);
        assertThat(result.resultType()).isEqualTo(TypeInfo.BOOLEAN);
    }

    @Test
    void compilesNullLiteral() {
        CompiledExpression result = compiler.compile("null", context, diagnostics);
        assertThat(result.resultType()).isEqualTo(TypeInfo.UNKNOWN);
    }

    @Test
    void compilesEnumLiteral() {
        CompiledExpression result = compiler.compile("#LFP3", context, diagnostics);
        assertThat(result.resultType()).isEqualTo(TypeInfo.ENUM);
    }

    @Test
    void compilesCoalesceFunction() {
        CompiledExpression result = compiler.compile("coalesce(\"a\", \"b\")", context, diagnostics);
        assertThat(result.ast()).isInstanceOf(FunctionCallExpr.class);
        assertThat(result.resultType()).isEqualTo(TypeInfo.TEXT);
    }

    @Test
    void parsesPathExprCorrectly() {
        CompiledExpression result = compiler.compile("${s.AttrName}", context, diagnostics);
        assertThat(result.ast()).isInstanceOf(PathExpr.class);
        PathExpr path = (PathExpr) result.ast();
        assertThat(path.alias()).isEqualTo("s");
        assertThat(path.attributeName()).isEqualTo("AttrName");
    }

    @Test
    void compilesEmptyExpressionAsNull() {
        CompiledExpression result = compiler.compile("", context, diagnostics);
        assertThat(result.resultType()).isEqualTo(TypeInfo.UNKNOWN);
    }

    @Test
    void handlesParseError() {
        CompiledExpression result = compiler.compile("func(1, 2", context, diagnostics);
        assertThat(result.resultType()).isEqualTo(TypeInfo.UNKNOWN);
        assertThat(diagnostics.errors()).isGreaterThan(0);
    }

    @Test
    void deterministicForPureLiteral() {
        CompiledExpression result = compiler.compile("42", context, diagnostics);
        assertThat(result.deterministic()).isTrue();
    }

    @Test
    void nonDeterministicWithNow() {
        var reg = new FunctionRegistry();
        reg.registerNonDeterministic("now", TypeInfo.XML_DATE_TIME, List.of(), (a, c) -> NullValue.INSTANCE);
        ExpressionCompileContext ctx =
                new ExpressionCompileContext("testRule", Map.of(), TypeInfo.XML_DATE_TIME, reg, Map.of());
        CompiledExpression result = compiler.compile("now()", ctx, diagnostics);
        assertThat(result.deterministic()).isFalse();
    }
}
