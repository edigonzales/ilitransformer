package guru.interlis.transformer.expr;

import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.StringFunctions;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class NestedFunctionTypeTest {

    @Test
    void nestedFunctionReturnsCorrectType() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        StringFunctions.registerAll(registry);

        Expression expr = ExpressionParser.parse("trim(coalesce(${s.A}, 'fallback'))");

        assertThat(expr).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr outer = (FunctionCallExpr) expr;
        assertThat(outer.functionName()).isEqualTo("trim");
        assertThat(outer.arguments().get(0)).isInstanceOf(FunctionCallExpr.class);

        FunctionCallExpr inner = (FunctionCallExpr) outer.arguments().get(0);
        assertThat(inner.functionName()).isEqualTo("coalesce");
    }

    @Test
    void deeplyNestedFunctionsParse() {
        Expression expr = ExpressionParser.parse("upper(lower(concat('a', 'b', 'c')))");
        assertThat(expr).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr outer = (FunctionCallExpr) expr;
        assertThat(outer.functionName()).isEqualTo("upper");
        assertThat(outer.arguments().get(0)).isInstanceOf(FunctionCallExpr.class);
    }
}
