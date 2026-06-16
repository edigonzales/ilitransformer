package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BarePathValidationTest {

    private ExpressionCompiler compiler;
    private DiagnosticCollector diagnostics;
    private FunctionRegistry registry;

    @BeforeEach
    void setUp() {
        compiler = new ExpressionCompiler();
        registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        diagnostics = new DiagnosticCollector();
    }

    @Test
    void detectsBarePathWithUnknownAlias() {
        ExpressionCompileContext ctx =
                new ExpressionCompileContext("r1", Map.of(), TypeInfo.UNKNOWN, registry, Map.of());

        CompiledExpression result = compiler.compile("p.Unbekannt", ctx, diagnostics);
        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all()).anyMatch(d -> d.code().equals(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE));
    }

    @Test
    void detectsBarePathUnknownAttribute() {
        Map<String, SourcePlan> sources = Map.of("p", new SourcePlan("p", null, List.of(), null));

        ExpressionCompileContext ctx =
                new ExpressionCompileContext("r1", sources, TypeInfo.UNKNOWN, registry, Map.of());

        CompiledExpression result = compiler.compile("p.Unbekannt", ctx, diagnostics);
        assertThat(diagnostics.errors()).isGreaterThan(0);
    }

    @Test
    void parsesBarePathWithoutBrackets() {
        Map<String, SourcePlan> sources = Map.of("p", new SourcePlan("p", null, List.of(), null));

        ExpressionCompileContext ctx =
                new ExpressionCompileContext("r1", sources, TypeInfo.UNKNOWN, registry, Map.of());

        CompiledExpression result = compiler.compile("p.Unbekannt", ctx, diagnostics);
        assertThat(result.ast()).isInstanceOf(PathExpr.class);
        PathExpr path = (PathExpr) result.ast();
        assertThat(path.alias()).isEqualTo("p");
        assertThat(path.attributeName()).isEqualTo("Unbekannt");
    }

    @Test
    void nestedPathIsParsedAsSingleAttributeName() {
        Expression expr = ExpressionParser.parse("src.structure.attribute");
        assertThat(expr).isInstanceOf(PathExpr.class);
        PathExpr path = (PathExpr) expr;
        assertThat(path.alias()).isEqualTo("src");
        assertThat(path.attributeName()).isEqualTo("structure.attribute");
    }

    @Test
    void nestedPathProducesUnsupportedDiagnostic() {
        Map<String, SourcePlan> sources = Map.of("src", new SourcePlan("src", null, List.of(), null));

        ExpressionCompileContext ctx =
                new ExpressionCompileContext("r1", sources, TypeInfo.UNKNOWN, registry, Map.of());

        CompiledExpression result = compiler.compile("src.structure.attribute", ctx, diagnostics);
        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.EXPR_UNSUPPORTED)
                        && d.message().contains("Nested expression paths are not supported"));
    }
}
