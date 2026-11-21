package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.state.SourceRecord;

import ch.interlis.iom_j.Iom_jObject;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ExpressionEngineTest {
    @Test
    void refOidProjectsAReferenceToText() {
        var source = new Iom_jObject("A.B", "1");
        var ref = source.addattrobj("Parent", Iom_jObject.REF);
        ref.setobjectrefoid("42");
        ref.setobjectrefbid("b2");
        var engine = new ExpressionEngine();

        assertThat(engine.evaluate("refOid(s.Parent)", Map.of("s", source))).isEqualTo(new TextValue("42"));
        assertThat(engine.evaluate("refOid(null)", Map.of())).isEqualTo(NullValue.INSTANCE);
        assertThat(engine.evaluate("refOid('42')", Map.of())).isEqualTo(NullValue.INSTANCE);
    }

    @Test
    void refOidIsTypedAsTextAndRejectsNonReferences() {
        var engine = new ExpressionEngine();
        var context =
                new ExpressionCompileContext("rule", Map.of(), TypeInfo.TEXT, engine.functionRegistry(), Map.of());
        var diagnostics = new DiagnosticCollector();
        var compiler = new ExpressionCompiler();

        assertThat(compiler.compile("refOid(null)", context, diagnostics).resultType())
                .isEqualTo(TypeInfo.TEXT);
        assertThat(diagnostics.all()).isEmpty();
        compiler.compile("refOid('42')", context, diagnostics);
        assertThat(diagnostics.all()).anyMatch(d -> d.code().equals(DiagnosticCode.EXPR_WRONG_ARG_TYPE));
    }

    @Test
    void refEqualsUsesTheReferencedBasketAndInput() {
        var engine = new ExpressionEngine();
        var child = new Iom_jObject("M.T.Child", "1");
        var parent = new Iom_jObject("M.T.Parent", "42");
        var local = child.addattrobj("LocalParent", Iom_jObject.REF);
        local.setobjectrefoid("42");
        var external = child.addattrobj("ExternalParent", Iom_jObject.REF);
        external.setobjectrefoid("42");
        external.setobjectrefbid("b2");
        var other = child.addattrobj("OtherParent", Iom_jObject.REF);
        other.setobjectrefoid("42");
        other.setobjectrefbid("b3");
        var context = EvalContext.forRecords(
                Map.of(
                        "child", new SourceRecord("input", "b1", child.getobjecttag(), child),
                        "local", new SourceRecord("input", "b1", parent.getobjecttag(), parent),
                        "otherBasket", new SourceRecord("input", "b2", parent.getobjecttag(), parent),
                        "otherInput", new SourceRecord("other", "b1", parent.getobjecttag(), parent)),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(engine.evaluate("refEquals(child.LocalParent, local)", context))
                .isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("refEquals(child.LocalParent, otherBasket)", context))
                .isEqualTo(BooleanValue.FALSE);
        assertThat(engine.evaluate("refEquals(child.LocalParent, otherInput)", context))
                .isEqualTo(BooleanValue.FALSE);
        assertThat(engine.evaluate("refEquals(child.ExternalParent, otherBasket)", context))
                .isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("refEquals(child.ExternalParent, child.OtherParent)", context))
                .isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void referencesWithoutTransferContextAreNotEqual() {
        var source = new Iom_jObject("A.B", "1");
        source.addattrobj("Parent", Iom_jObject.REF).setobjectrefoid("42");
        var engine = new ExpressionEngine();

        assertThat(engine.evaluate("refEquals(s.Parent, s.Parent)", Map.of("s", source)))
                .isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void matchingInputCannotResolveTwoUnknownLocalBaskets() {
        var source = new Iom_jObject("A.B", "1");
        source.addattrobj("Parent", Iom_jObject.REF).setobjectrefoid("42");
        var record = new SourceRecord("input", null, source.getobjecttag(), source);
        var context = EvalContext.forRecords(Map.of("s", record), Map.of(), null, null, null, null, null, null);

        assertThat(new ExpressionEngine().evaluate("refEquals(s.Parent, s.Parent)", context))
                .isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void evaluatesSourcePathAndIf() {
        ExpressionEngine engine = new ExpressionEngine();
        Iom_jObject src = new Iom_jObject("A.B", "1");
        src.setattrvalue("Name", "Alice");

        Value result = engine.evaluate("${s.Name}", Map.of("s", src));
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("Alice");

        Value ifResult = engine.evaluate("if(${s.Name} != null, 'ok', 'no')", Map.of("s", src));
        assertThat(ifResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) ifResult).value()).isEqualTo("ok");
    }

    @Test
    void evaluatesIfFalseBranch() {
        ExpressionEngine engine = new ExpressionEngine();
        Iom_jObject src = new Iom_jObject("A.B", "1");

        Value result = engine.evaluate("if(${s.Name} != null, 'ok', 'no')", Map.of("s", src));
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("no");
    }
}
