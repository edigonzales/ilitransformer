package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.state.InMemorySourceLookupIndex;
import guru.interlis.transformer.state.SourceRecord;

import ch.interlis.iom_j.Iom_jObject;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LookupFunctionsTest {

    @Test
    void lookupDoesNotWarnWhenAmbiguousHitsReturnSameValue() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord(
                "dm01", "DM01.BB.Gebaeudenummer", "child-1", "Gebaeudenummer_von", "2618", "GWR_EGID", "1319100"));
        index.index(sourceRecord(
                "dm01", "DM01.BB.Gebaeudenummer", "child-2", "Gebaeudenummer_von", "2618", "GWR_EGID", "1319100"));

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "test").withLookupIndex(index);

        Value value = new ExpressionEngine()
                .evaluate("lookup('DM01.BB.Gebaeudenummer', 'Gebaeudenummer_von', '2618', 'GWR_EGID')", ctx);

        assertThat(value.asText()).isEqualTo("1319100");
        assertThat(diagnostics.warnings()).isZero();
    }

    @Test
    void lookupWarnsWhenAmbiguousHitsReturnDifferentValues() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord(
                "dm01", "DM01.BB.Gebaeudenummer", "child-1", "Gebaeudenummer_von", "2618", "GWR_EGID", "1319100"));
        index.index(sourceRecord(
                "dm01", "DM01.BB.Gebaeudenummer", "child-2", "Gebaeudenummer_von", "2618", "GWR_EGID", "1319101"));

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "test").withLookupIndex(index);

        Value value = new ExpressionEngine()
                .evaluate("lookup('DM01.BB.Gebaeudenummer', 'Gebaeudenummer_von', '2618', 'GWR_EGID')", ctx);

        assertThat(value.asText()).isEqualTo("1319100");
        assertThat(diagnostics.all()).extracting(d -> d.code()).containsExactly(DiagnosticCode.LOOKUP_AMBIGUOUS);
    }

    @Test
    void lookupReturnsNullAndWarningOnNoMatch() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "test").withLookupIndex(index);

        Value value = new ExpressionEngine()
                .evaluate("lookup('DM01.BB.Gebaeudenummer', 'Gebaeudenummer_von', '9999', 'GWR_EGID')", ctx);

        assertThat(value).isEqualTo(NullValue.INSTANCE);
        assertThat(diagnostics.all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.LOOKUP_NO_MATCH)
                        && d.severity() == guru.interlis.transformer.diag.Severity.WARNING);
    }

    @Test
    void lookupReturnsNullAndErrorWhenLookupIndexMissing() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "test");

        Value value = new ExpressionEngine()
                .evaluate("lookup('DM01.BB.Gebaeudenummer', 'Gebaeudenummer_von', '2618', 'GWR_EGID')", ctx);

        assertThat(value).isEqualTo(NullValue.INSTANCE);
        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.LOOKUP_INDEX_MISSING)
                        && d.severity() == guru.interlis.transformer.diag.Severity.ERROR);
    }

    @Test
    void lookupInRestrictsLookupToInputId() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord(
                "dm01", "DM01.BB.Gebaeudenummer", "child-1", "Gebaeudenummer_von", "2618", "GWR_EGID", "1319100"));
        index.index(sourceRecord(
                "dm02", "DM01.BB.Gebaeudenummer", "child-2", "Gebaeudenummer_von", "2618", "GWR_EGID", "9999999"));

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "test").withLookupIndex(index);

        Value value = new ExpressionEngine()
                .evaluate("lookupIn('dm01', 'DM01.BB.Gebaeudenummer', 'Gebaeudenummer_von', '2618', 'GWR_EGID')", ctx);

        assertThat(value.asText()).isEqualTo("1319100");
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    void lookupInReturnsNullWhenMatchExistsOnlyInOtherInput() {
        InMemorySourceLookupIndex index = new InMemorySourceLookupIndex();
        index.index(sourceRecord(
                "dm01", "DM01.BB.Gebaeudenummer", "child-1", "Gebaeudenummer_von", "2618", "GWR_EGID", "1319100"));

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "test").withLookupIndex(index);

        Value value = new ExpressionEngine()
                .evaluate(
                        "lookupIn('nonexistent', 'DM01.BB.Gebaeudenummer', 'Gebaeudenummer_von', '2618', 'GWR_EGID')",
                        ctx);

        assertThat(value).isEqualTo(NullValue.INSTANCE);
        assertThat(diagnostics.all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.LOOKUP_NO_MATCH)
                        && d.severity() == guru.interlis.transformer.diag.Severity.WARNING);
    }

    @Test
    void lookupInReportsWrongArgCount() {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "test");

        new ExpressionEngine().evaluate("lookupIn('dm01')", ctx);

        assertThat(diagnostics.errors()).isGreaterThan(0);
        assertThat(diagnostics.all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.EXPR_WRONG_ARG_COUNT)
                        && d.severity() == guru.interlis.transformer.diag.Severity.ERROR);
    }

    private static SourceRecord sourceRecord(
            String sourceFileId,
            String tag,
            String oid,
            String refAttr,
            String refOid,
            String valueAttr,
            String value) {
        Iom_jObject object = new Iom_jObject(tag, oid);
        object.addattrobj(refAttr, Iom_jObject.REF).setobjectrefoid(refOid);
        object.setattrvalue(valueAttr, value);
        return new SourceRecord(sourceFileId, null, tag, object);
    }
}
