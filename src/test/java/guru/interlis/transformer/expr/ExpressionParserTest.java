package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExpressionParserTest {

    @Test
    void parsesStringLiteral() {
        Expression expr = ExpressionParser.parse("\"hello\"");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((LiteralExpr) expr).value()).isInstanceOf(TextValue.class);
        assertThat(((TextValue) ((LiteralExpr) expr).value()).value()).isEqualTo("hello");
    }

    @Test
    void parsesSingleQuotedString() {
        Expression expr = ExpressionParser.parse("'world'");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((TextValue) ((LiteralExpr) expr).value()).value()).isEqualTo("world");
    }

    @Test
    void parsesNumberLiteral() {
        Expression expr = ExpressionParser.parse("42");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((NumberValue) ((LiteralExpr) expr).value()).value()).isEqualTo(new java.math.BigDecimal("42"));
    }

    @Test
    void parsesNegativeNumber() {
        Expression expr = ExpressionParser.parse("-15");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((NumberValue) ((LiteralExpr) expr).value()).value()).isEqualTo(new java.math.BigDecimal("-15"));
    }

    @Test
    void parsesDecimalNumber() {
        Expression expr = ExpressionParser.parse("3.14");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((NumberValue) ((LiteralExpr) expr).value()).value()).isEqualTo(new java.math.BigDecimal("3.14"));
    }

    @Test
    void parsesBooleanTrue() {
        Expression expr = ExpressionParser.parse("true");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((BooleanValue) ((LiteralExpr) expr).value()).value()).isTrue();
    }

    @Test
    void parsesBooleanFalse() {
        Expression expr = ExpressionParser.parse("false");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((BooleanValue) ((LiteralExpr) expr).value()).value()).isFalse();
    }

    @Test
    void parsesNullLiteral() {
        Expression expr = ExpressionParser.parse("null");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((LiteralExpr) expr).value()).isInstanceOf(NullValue.class);
    }

    @Test
    void parsesEnumLiteral() {
        Expression expr = ExpressionParser.parse("#LFP3");
        assertThat(expr).isInstanceOf(LiteralExpr.class);
        assertThat(((EnumValue) ((LiteralExpr) expr).value()).name()).isEqualTo("LFP3");
    }

    @Test
    void parsesPathRef() {
        Expression expr = ExpressionParser.parse("${s.AttrName}");
        assertThat(expr).isInstanceOf(PathExpr.class);
        PathExpr path = (PathExpr) expr;
        assertThat(path.alias()).isEqualTo("s");
        assertThat(path.attributeName()).isEqualTo("AttrName");
    }

    @Test
    void parsesFunctionCallNoArgs() {
        Expression expr = ExpressionParser.parse("now()");
        assertThat(expr).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr call = (FunctionCallExpr) expr;
        assertThat(call.functionName()).isEqualTo("now");
        assertThat(call.arguments()).isEmpty();
    }

    @Test
    void parsesFunctionCallWithArgs() {
        Expression expr = ExpressionParser.parse("truncate(${s.Name}, 60)");
        assertThat(expr).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr call = (FunctionCallExpr) expr;
        assertThat(call.functionName()).isEqualTo("truncate");
        assertThat(call.arguments()).hasSize(2);
        assertThat(call.arguments().get(0)).isInstanceOf(PathExpr.class);
        assertThat(call.arguments().get(1)).isInstanceOf(LiteralExpr.class);
    }

    @Test
    void parsesCoalesceWithMultipleArgs() {
        Expression expr = ExpressionParser.parse("coalesce(${s.A}, ${s.B}, 'fallback')");
        assertThat(expr).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr call = (FunctionCallExpr) expr;
        assertThat(call.functionName()).isEqualTo("coalesce");
        assertThat(call.arguments()).hasSize(3);
    }

    @Test
    void parsesConditionalIf() {
        Expression expr = ExpressionParser.parse("if(true, 'yes', 'no')");
        assertThat(expr).isInstanceOf(ConditionalExpr.class);
    }

    @Test
    void parsesIfWithNotNullCondition() {
        Expression expr = ExpressionParser.parse("if(${s.X} != null, 'ok', 'missing')");
        assertThat(expr).isInstanceOf(ConditionalExpr.class);
        ConditionalExpr cond = (ConditionalExpr) expr;
        assertThat(cond.condition()).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr fc = (FunctionCallExpr) cond.condition();
        assertThat(fc.functionName()).isEqualTo("defined");
    }

    @Test
    void parsesIfWithIsNullCondition() {
        Expression expr = ExpressionParser.parse("if(${s.X} == null, 'missing', 'ok')");
        assertThat(expr).isInstanceOf(ConditionalExpr.class);
        ConditionalExpr cond = (ConditionalExpr) expr;
        FunctionCallExpr fc = (FunctionCallExpr) cond.condition();
        assertThat(fc.functionName()).isEqualTo("notDefined");
    }

    @Test
    void parsesNestedFunctionCalls() {
        Expression expr = ExpressionParser.parse("coalesce(trim(${s.A}), ${s.B})");
        assertThat(expr).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr outer = (FunctionCallExpr) expr;
        assertThat(outer.functionName()).isEqualTo("coalesce");
        assertThat(outer.arguments()).hasSize(2);
        assertThat(outer.arguments().get(0)).isInstanceOf(FunctionCallExpr.class);
        FunctionCallExpr inner = (FunctionCallExpr) outer.arguments().get(0);
        assertThat(inner.functionName()).isEqualTo("trim");
    }

    @Test
    void rejectsEmptyExpression() {
        assertThatThrownBy(() -> ExpressionParser.parse("")).isInstanceOf(ExpressionParseException.class);
        assertThatThrownBy(() -> ExpressionParser.parse("   ")).isInstanceOf(ExpressionParseException.class);
        assertThatThrownBy(() -> ExpressionParser.parse(null)).isInstanceOf(ExpressionParseException.class);
    }

    @Test
    void rejectsUnterminatedString() {
        assertThatThrownBy(() -> ExpressionParser.parse("\"unclosed")).isInstanceOf(ExpressionParseException.class);
    }

    @Test
    void rejectsUnterminatedFunctionCall() {
        assertThatThrownBy(() -> ExpressionParser.parse("func(1, 2")).isInstanceOf(ExpressionParseException.class);
    }

    @Test
    void reportsPositionInError() {
        try {
            ExpressionParser.parse("func(1, 2");
            fail("should have thrown");
        } catch (ExpressionParseException e) {
            assertThat(e.position()).isGreaterThanOrEqualTo(0);
            assertThat(e.expression()).isEqualTo("func(1, 2");
        }
    }
}
